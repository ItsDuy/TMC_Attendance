#!/usr/bin/env python3
"""Turn one k6 run folder into a single self-contained report.html (stdlib only).

    python3 perf/report.py perf/results/<run-folder>

Reads raw.csv (every sample), summary.json (k6's own percentiles), console.log
(the teardown verification lines), run.env and exit-code.
"""
import csv
import html
import json
import math
import re
import sys
from collections import defaultdict
from pathlib import Path

LIMIT_MS = 2000
FACTORIES = [("binh_duong", "Binh Duong", "--series-1"), ("long_an", "Long An", "--series-2")]


def tags_of(extra: str) -> dict:
    out = {}
    for part in (extra or "").split("&"):
        if "=" in part:
            k, v = part.split("=", 1)
            out[k] = v
    return out


def percentile(values, p):
    """Linear interpolation, same method k6 uses."""
    if not values:
        return None
    s = sorted(values)
    k = (len(s) - 1) * p / 100
    lo, hi = math.floor(k), math.ceil(k)
    return s[lo] + (s[hi] - s[lo]) * (k - lo)


def load(run: Path):
    samples = defaultdict(list)          # scenario -> [(ts, ms)]
    recorded = []                        # 0/1 per scan
    outcomes = defaultdict(int)
    dropped = 0
    teardown_checks = {}                 # name -> all passed?
    with open(run / "raw.csv", newline="") as fh:
        for row in csv.DictReader(fh):
            metric, tags = row["metric_name"], tags_of(row.get("extra_tags", ""))
            if metric == "http_req_duration" and row.get("name") == "kiosk_scan":
                samples[row.get("scenario", "")].append((int(row["timestamp"]), float(row["metric_value"])))
            elif metric == "scan_recorded":
                recorded.append(float(row["metric_value"]))
            elif metric == "scan_outcomes":
                outcomes[tags.get("outcome", "?")] += int(float(row["metric_value"]))
            elif metric == "dropped_iterations":
                dropped += int(float(row["metric_value"]))
            elif metric == "checks" and tags.get("phase") == "teardown":
                name = row.get("check", "")
                teardown_checks[name] = teardown_checks.get(name, True) and float(row["metric_value"]) == 1.0

    summary = json.loads((run / "summary.json").read_text()) if (run / "summary.json").exists() else {}
    console = (run / "console.log").read_text(errors="replace") if (run / "console.log").exists() else ""
    messages = [m.replace('\\"', '"') for m in re.findall(r'msg="((?:[^"\\]|\\.)*)"', console)]
    env = {}
    if (run / "run.env").exists():
        for line in (run / "run.env").read_text().splitlines():
            if "=" in line:
                k, v = line.split("=", 1)
                env[k] = v
    exit_code = int((run / "exit-code").read_text().strip()) if (run / "exit-code").exists() else None
    return samples, recorded, outcomes, dropped, teardown_checks, summary, messages, env, exit_code


def k6_stat(summary, metric, stat):
    m = summary.get("metrics", {}).get(metric)
    return None if not m else m.get(stat)


def fmt_ms(v):
    return "–" if v is None else (f"{v:,.0f} ms" if v >= 10 else f"{v:.1f} ms")


def build(run: Path) -> str:
    samples, recorded, outcomes, dropped, tchecks, summary, messages, env, exit_code = load(run)
    all_ms = [ms for s in samples.values() for _, ms in s]
    if not all_ms:
        raise SystemExit(f"No kiosk_scan samples in {run}/raw.csv. Did the test run?")

    key = "http_req_duration{name:kiosk_scan}"
    p95 = k6_stat(summary, key, "p(95)") or percentile(all_ms, 95)
    p99 = k6_stat(summary, key, "p(99)") or percentile(all_ms, 99)
    mx = k6_stat(summary, key, "max") or max(all_ms)
    med = k6_stat(summary, key, "med") or percentile(all_ms, 50)
    under = sum(1 for v in all_ms if v < LIMIT_MS)
    rec_rate = (sum(recorded) / len(recorded)) if recorded else 0.0

    verify = [m for m in messages if m.startswith("verify ")]
    run_line = next((m for m in messages if m.startswith("run ")), "")
    stored = {m.split()[1].rstrip(":"): m for m in verify if " stored " in m}

    # ---- checklist rows: (requirement, target, measured, ok)
    rows = [
        ("Kiosk response time · p95 (both factories)", "< 2 000 ms", fmt_ms(p95), p95 < LIMIT_MS),
        ("Kiosk response time · p99", "< 2 000 ms", fmt_ms(p99), p99 < LIMIT_MS),
    ]
    for scen, label, _ in FACTORIES:
        v = percentile([ms for _, ms in samples.get(scen, [])], 95)
        if v is not None:
            rows.append((f"Kiosk response time · p95 {label}", "< 2 000 ms", fmt_ms(v), v < LIMIT_MS))
    rows.append(("Scans answered under 2 s", "all", f"{under:,} / {len(all_ms):,}", under == len(all_ms)))
    rows.append(("Every scan returned a stored record", "100 %", f"{rec_rate * 100:.2f} %", rec_rate == 1.0))
    for name, ok in sorted(tchecks.items()):
        detail = ""
        code = name.split(":")[0]
        if "stored record" in name and code in stored:
            m = re.search(r"stored (\d+) / sent (\d+)", stored[code])
            detail = f"{m.group(1)} stored / {m.group(2)} sent" if m else ""
        elif "audit" in name:
            m = next((x for x in verify if "audit chain" in x), "")
            mm = re.search(r"entries=(\d+)", m)
            detail = f"valid, {int(mm.group(1)):,} entries re-hashed" if (mm and ok) else ("broken" if not ok else "valid")
        elif "gate openings" in name:
            m = next((x for x in verify if "gate opened" in x), "")
            mm = re.search(r"opened (\d+) times, COMPLETED records (\d+)", m)
            detail = f"{mm.group(1)} opens = {mm.group(2)} records" if mm else ""
        rows.append((name[0].upper() + name[1:], "true", detail or ("yes" if ok else "no"), ok))
    rows.append(("Load generator kept the planned rate (dropped iterations)", "0", str(dropped), dropped == 0))

    passed = exit_code == 0 and all(r[3] for r in rows)
    total = len(all_ms)
    t0 = min(ts for s in samples.values() for ts, _ in s)
    t1 = max(ts for s in samples.values() for ts, _ in s)
    span = max(1, t1 - t0 + 1)
    bucket = max(5, math.ceil(span / 60))

    # ---- buckets: p95 per factory per time slot
    nb = math.ceil(span / bucket)
    series = {}
    for scen, label, var in FACTORIES:
        b = defaultdict(list)
        for ts, ms in samples.get(scen, []):
            b[(ts - t0) // bucket].append(ms)
        series[scen] = [(i, percentile(b[i], 95), len(b[i])) for i in range(nb) if b[i]]

    peak = max([p for s in series.values() for _, p, _ in s] + [0])
    y_max = max(2400, math.ceil(peak * 1.15 / 400) * 400)
    W, H, L, R, T, B = 760, 300, 56, 104, 16, 34
    pw, ph = W - L - R, H - T - B

    def x(i):
        return L + (pw * i / max(1, nb - 1))

    def y(v):
        return T + ph - ph * v / y_max

    grid = []
    step = 400 if y_max <= 2400 else (800 if y_max <= 6000 else 2000)
    for v in range(0, y_max + 1, step):
        grid.append(f'<line x1="{L}" x2="{L + pw}" y1="{y(v):.1f}" y2="{y(v):.1f}" class="grid"/>'
                    f'<text x="{L - 8}" y="{y(v) + 4:.1f}" class="tick" text-anchor="end">{v:,}</text>')
    xt = []
    for i in range(0, nb, max(1, nb // 6)):
        sec = i * bucket
        xt.append(f'<text x="{x(i):.1f}" y="{H - 10}" class="tick" text-anchor="middle">{sec // 60}:{sec % 60:02d}</text>')
    lim = (f'<line x1="{L}" x2="{L + pw}" y1="{y(LIMIT_MS):.1f}" y2="{y(LIMIT_MS):.1f}" class="limit"/>'
           f'<text x="{L + pw + 8}" y="{y(LIMIT_MS) + 4:.1f}" class="lim-label">2 s limit</text>')
    lines, labels = [], []
    for scen, label, var in FACTORIES:
        pts = series[scen]
        if not pts:
            continue
        d = " ".join(f"{'M' if j == 0 else 'L'}{x(i):.1f},{y(v):.1f}" for j, (i, v, _) in enumerate(pts))
        lines.append(f'<path d="{d}" fill="none" stroke="var({var})" stroke-width="2" '
                     f'stroke-linejoin="round" stroke-linecap="round"/>')
        i, v, _ = pts[-1]
        labels.append((y(v), label, var, x(i)))
    # direct end labels, nudged apart if they collide
    labels.sort()
    for k in range(1, len(labels)):
        if labels[k][0] - labels[k - 1][0] < 14:
            labels[k] = (labels[k - 1][0] + 14,) + labels[k][1:]
    end = "".join(f'<circle cx="{lx:.1f}" cy="{ly:.1f}" r="4" fill="var({var})" class="ring"/>'
                  f'<text x="{L + pw + 8}" y="{ly + 4:.1f}" class="end-label">{html.escape(lab)}</text>'
                  for ly, lab, var, lx in labels)

    chart_data = json.dumps({
        "bucket": bucket, "nb": nb,
        "series": {scen: {str(i): [round(v), n] for i, v, n in series[scen]} for scen, _, _ in FACTORIES},
        "labels": {scen: label for scen, label, _ in FACTORIES},
        "geom": {"L": L, "pw": pw, "T": T, "ph": ph, "W": W, "H": H},
    })

    table_rows = []
    for i in range(nb):
        sec = i * bucket
        cells = []
        for scen, _, _ in FACTORIES:
            hit = next(((v, n) for j, v, n in series[scen] if j == i), None)
            cells.append(f"<td>{fmt_ms(hit[0])}</td><td>{hit[1]}</td>" if hit else "<td>–</td><td>0</td>")
        table_rows.append(f"<tr><td>{sec // 60}:{sec % 60:02d}</td>{''.join(cells)}</tr>")

    checklist = "".join(
        f'<tr><td>{html.escape(r[0])}</td><td>{html.escape(r[1])}</td><td class="num">{html.escape(r[2])}</td>'
        f'<td><span class="status {"good" if r[3] else "bad"}">{"✓ pass" if r[3] else "✗ fail"}</span></td></tr>'
        for r in rows)
    outcome_txt = " · ".join(f"{html.escape(k)} {v:,}" for k, v in sorted(outcomes.items(), key=lambda kv: -kv[1]))
    meta = [
        ("Profile", env.get("profile", "?")),
        ("Plan", run_line.replace("run ", "run ", 1) or "–"),
        ("Gate", env.get("turnstile_mode", "unknown")),
        ("Started", env.get("started", "?")),
        ("Machine", env.get("host", "?")),
    ]
    meta_html = "".join(f"<dt>{html.escape(k)}</dt><dd>{html.escape(v)}</dd>" for k, v in meta)

    return f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>TMC load test · {html.escape(env.get('profile', ''))} · {'PASS' if passed else 'FAIL'}</title>
<style>
.viz-root {{
  color-scheme: light;
  --page: #f9f9f7; --surface-1: #fcfcfb; --text-primary: #0b0b0b; --text-secondary: #52514e;
  --muted: #898781; --grid: #e1e0d9; --axis: #c3c2b7; --border: rgba(11,11,11,0.10);
  --series-1: #2a78d6; --series-2: #eb6834; --good: #0ca30c; --good-text: #006300; --critical: #d03b3b;
}}
@media (prefers-color-scheme: dark) {{
  :root:where(:not([data-theme="light"])) .viz-root {{
    color-scheme: dark;
    --page: #0d0d0d; --surface-1: #1a1a19; --text-primary: #f0efec; --text-secondary: #c3c2b7;
    --muted: #898781; --grid: #2c2c2a; --axis: #383835; --border: rgba(255,255,255,0.10);
    --series-1: #3987e5; --series-2: #d95926; --good-text: #0ca30c;
  }}
}}
* {{ box-sizing: border-box; }}
body {{ margin: 0; }}
.viz-root {{ background: var(--page); color: var(--text-primary); min-height: 100vh;
  font: 15px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; padding: 32px 20px; }}
main {{ max-width: 880px; margin: 0 auto; }}
h1 {{ font-size: 24px; margin: 0 0 4px; }}
h2 {{ font-size: 16px; margin: 32px 0 10px; }}
.sub {{ color: var(--text-secondary); margin: 0 0 20px; }}
.verdict {{ display: flex; gap: 12px; align-items: center; padding: 14px 18px; border-radius: 10px;
  background: var(--surface-1); border: 1px solid var(--border); margin-bottom: 20px; }}
.verdict .icon {{ width: 28px; height: 28px; border-radius: 50%; display: grid; place-items: center;
  color: #fff; font-weight: 700; }}
.verdict.pass .icon {{ background: var(--good); }} .verdict.fail .icon {{ background: var(--critical); }}
.tiles {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; }}
.tile {{ background: var(--surface-1); border: 1px solid var(--border); border-radius: 10px; padding: 12px 14px; }}
.tile .k {{ color: var(--text-secondary); font-size: 13px; }}
.tile .v {{ font-size: 26px; font-weight: 650; font-variant-numeric: tabular-nums; }}
.tile .n {{ color: var(--muted); font-size: 12px; }}
table {{ width: 100%; border-collapse: collapse; background: var(--surface-1); border: 1px solid var(--border);
  border-radius: 10px; overflow: hidden; font-size: 14px; }}
th, td {{ text-align: left; padding: 8px 12px; border-bottom: 1px solid var(--grid); }}
th {{ color: var(--text-secondary); font-weight: 600; font-size: 13px; }}
td.num {{ font-variant-numeric: tabular-nums; }}
.status {{ white-space: nowrap; font-weight: 600; }}
.status.good {{ color: var(--good-text); }} .status.bad {{ color: var(--critical); }}
.card {{ background: var(--surface-1); border: 1px solid var(--border); border-radius: 10px; padding: 14px; position: relative; }}
.legend {{ display: flex; gap: 18px; color: var(--text-secondary); font-size: 13px; margin: 0 0 6px 4px; }}
.legend i {{ display: inline-block; width: 14px; height: 2px; vertical-align: middle; margin-right: 6px; border-radius: 2px; }}
.legend .lim {{ border-top: 2px dashed var(--muted); height: 0; }}
svg {{ width: 100%; height: auto; display: block; }}
.grid {{ stroke: var(--grid); stroke-width: 1; }}
.tick {{ fill: var(--muted); font-size: 11px; font-variant-numeric: tabular-nums; }}
.limit {{ stroke: var(--muted); stroke-width: 1.5; stroke-dasharray: 5 4; }}
.lim-label, .end-label {{ fill: var(--text-secondary); font-size: 12px; }}
.ring {{ stroke: var(--surface-1); stroke-width: 2; }}
.axis-title {{ fill: var(--muted); font-size: 11px; }}
#xhair {{ stroke: var(--axis); stroke-width: 1; visibility: hidden; }}
#tip {{ position: absolute; pointer-events: none; background: var(--surface-1); border: 1px solid var(--border);
  border-radius: 8px; padding: 8px 10px; font-size: 13px; box-shadow: 0 4px 14px rgba(0,0,0,.12); display: none;
  font-variant-numeric: tabular-nums; min-width: 170px; }}
#tip b {{ display: block; margin-bottom: 4px; }}
#tip .row {{ display: flex; justify-content: space-between; gap: 14px; color: var(--text-secondary); }}
#tip .row span:last-child {{ color: var(--text-primary); }}
#tip i {{ display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; }}
dl {{ display: grid; grid-template-columns: max-content 1fr; gap: 4px 16px; margin: 0; font-size: 14px; }}
dt {{ color: var(--text-secondary); }} dd {{ margin: 0; }}
details summary {{ cursor: pointer; color: var(--text-secondary); margin: 10px 0; }}
.note {{ color: var(--text-secondary); font-size: 13px; }}
</style></head>
<body><div class="viz-root"><main>
<h1>TMC kiosk load test</h1>
<p class="sub">Shift-change peak, both factories at the same time. Requirement: kiosk response ≤ 2 s,
no attendance record lost.</p>

<div class="verdict {'pass' if passed else 'fail'}"><span class="icon">{'✓' if passed else '✗'}</span>
<div><strong>{'PASS: every check met' if passed else 'FAIL: at least one check missed (see table)'}</strong><br>
<span class="note">{total:,} kiosk scans · {outcome_txt}</span></div></div>

<div class="tiles">
  <div class="tile"><div class="k">p95 response</div><div class="v">{fmt_ms(p95)}</div><div class="n">limit 2 000 ms</div></div>
  <div class="tile"><div class="k">p99 response</div><div class="v">{fmt_ms(p99)}</div><div class="n">median {fmt_ms(med)}</div></div>
  <div class="tile"><div class="k">slowest scan</div><div class="v">{fmt_ms(mx)}</div><div class="n">{under:,} of {total:,} under 2 s</div></div>
  <div class="tile"><div class="k">records stored</div><div class="v">{rec_rate * 100:.1f}%</div><div class="n">of scans sent</div></div>
</div>

<h2>Response time over the run (p95 per {bucket}-second slot)</h2>
<div class="card" id="chart">
  <div class="legend"><span><i style="background:var(--series-1)"></i>Binh Duong</span>
  <span><i style="background:var(--series-2)"></i>Long An</span><span><i class="lim"></i>2 s limit</span></div>
  <svg viewBox="0 0 {W} {H}" role="img" aria-label="p95 kiosk response time per time slot for both factories, against the 2 second limit">
    {''.join(grid)}
    <line x1="{L}" x2="{L + pw}" y1="{T + ph}" y2="{T + ph}" stroke="var(--axis)"/>
    {''.join(xt)}
    <text x="{L}" y="{T - 4}" class="axis-title">ms</text>
    {lim}{''.join(lines)}{end}
    <line id="xhair" x1="0" x2="0" y1="{T}" y2="{T + ph}"/>
    <rect id="hit" x="{L}" y="{T}" width="{pw}" height="{ph}" fill="transparent"/>
  </svg>
  <div id="tip"></div>
  <details><summary>Show as table</summary>
  <table><thead><tr><th>Time</th><th>Binh Duong p95</th><th>scans</th><th>Long An p95</th><th>scans</th></tr></thead>
  <tbody>{''.join(table_rows)}</tbody></table></details>
</div>

<h2>Checklist</h2>
<table><thead><tr><th>Check</th><th>Target</th><th>Measured</th><th>Result</th></tr></thead>
<tbody>{checklist}</tbody></table>
<p class="note">Latency is the kiosk's own request (<code>POST /api/kiosk/scans</code>): verify QR → save record →
call the turnstile → mark complete. Minting the QR (the phone's job) is measured separately and not counted.
Record counts come from the database after the run, not from the load generator.</p>

<h2>Run details</h2>
<div class="card"><dl>{meta_html}</dl></div>
<details><summary>Server-side verification log</summary><pre class="note">{html.escape(chr(10).join(verify))}</pre></details>
</main></div>
<script>
(() => {{
  const D = {chart_data};
  const svg = document.querySelector('#chart svg'), hit = document.getElementById('hit');
  const xh = document.getElementById('xhair'), tip = document.getElementById('tip'), card = document.getElementById('chart');
  const g = D.geom, colors = {{binh_duong: 'var(--series-1)', long_an: 'var(--series-2)'}};
  const fmt = (v) => v.toLocaleString() + ' ms';
  hit.addEventListener('mousemove', (e) => {{
    const pt = svg.createSVGPoint(); pt.x = e.clientX; pt.y = e.clientY;
    const p = pt.matrixTransform(svg.getScreenCTM().inverse());
    const i = Math.max(0, Math.min(D.nb - 1, Math.round((p.x - g.L) / (g.pw / Math.max(1, D.nb - 1)))));
    const x = g.L + g.pw * i / Math.max(1, D.nb - 1);
    xh.setAttribute('x1', x); xh.setAttribute('x2', x); xh.style.visibility = 'visible';
    const s = i * D.bucket;
    let rows = '';
    for (const k of Object.keys(D.series)) {{
      const v = D.series[k][String(i)];
      rows += `<div class="row"><span><i style="background:${{colors[k]}}"></i>${{D.labels[k]}}</span>`
        + `<span>${{v ? fmt(v[0]) + ' · ' + v[1] + ' scans' : '–'}}</span></div>`;
    }}
    tip.innerHTML = `<b>${{Math.floor(s / 60)}}:${{String(s % 60).padStart(2, '0')}} – p95</b>` + rows;
    tip.style.display = 'block';
    const box = card.getBoundingClientRect(), sb = svg.getBoundingClientRect();
    const px = sb.left - box.left + (x / g.W) * sb.width;
    tip.style.left = Math.min(px + 12, box.width - tip.offsetWidth - 8) + 'px';
    tip.style.top = (sb.top - box.top + 24) + 'px';
  }});
  hit.addEventListener('mouseleave', () => {{ tip.style.display = 'none'; xh.style.visibility = 'hidden'; }});
}})();
</script>
</body></html>
"""


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    run = Path(sys.argv[1])
    out = run / "report.html"
    out.write_text(build(run), encoding="utf-8")
    print(f"report written: {out}")


if __name__ == "__main__":
    main()
