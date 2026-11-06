import { useEffect, useState } from "react";
import { api } from "../api";
import { useAuth } from "../auth";
import { Link } from "react-router-dom";

/* Live site map: a lightweight digital-twin view of both factories.
 * Turnstiles have no open/closed sensor, so a door shows OPEN for a few seconds after a
 * completed scan, STUCK while scans wait for a guard, OFFLINE when no kiosk heartbeat arrived. */

type GateState = {
  gateId: string;
  name: string;
  direction: "IN" | "OUT" | "BIDIRECTIONAL";
  online: boolean;
  lastHeartbeatAt: string | null;
  lastOpenedAt: string | null;
  justOpened: boolean;
  waitingForGuard: number;
};
type Person = { employeeCode: string; fullName: string; since: string };
type Site = {
  factoryId: string;
  code: string;
  name: string;
  employees: number;
  inside: number;
  people: Person[] | null;
  peopleNotShown: number;
  gates: GateState[];
};
type FeedItem = {
  at: string;
  factoryCode: string | null;
  gateName: string | null;
  direction: string;
  status: string;
  employeeCode: string | null;
  employeeName: string | null;
  manual?: boolean;
};
type LiveSites = { generatedAt: string; namesHidden: boolean; scope?: string | null; sites: Site[]; feed: FeedItem[] };

const POLL_MS = 2000;
const MAX_DOTS = 60;

function gateLook(gate: GateState) {
  if (gate.waitingForGuard > 0) return { cls: "stuck", icon: "!", word: `STUCK · ${gate.waitingForGuard} waiting` };
  if (!gate.online) return { cls: "offline", icon: "×", word: "OFFLINE" };
  if (gate.justOpened) return { cls: "open", icon: "✓", word: "OPEN" };
  return { cls: "idle", icon: "–", word: "closed" };
}

const arrow = (direction: string) => (direction === "IN" ? "▲ IN" : direction === "OUT" ? "▼ OUT" : "⇅ IN/OUT");
const shortName = (name: string) => name.replace(" Factory", "");
const time = (iso: string) => new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });

function FloorPlan({ site, namesHidden }: { site: Site; namesHidden: boolean }) {
  const W = 420, H = 230, x0 = 16, y0 = 14, w = W - 32, h = 150;
  const gates = site.gates;
  const slot = w / (gates.length + 1);
  const dots = Math.min(site.inside, MAX_DOTS);
  const cols = 15;

  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="floor" role="img"
      aria-label={`${shortName(site.name)}: ${site.inside} people inside, ${gates.length} gates`}>
      {/* building outline with a gap in the bottom wall for each gate */}
      <rect x={x0} y={y0} width={w} height={h} rx="8" className="wall" />
      <text x={x0 + 12} y={y0 + 20} className="floor-label">Factory floor</text>

      {/* one dot per person inside */}
      {Array.from({ length: dots }).map((_, i) => {
        const person = site.people?.[i];
        return (
          <circle key={i} cx={x0 + 22 + (i % cols) * 23} cy={y0 + 44 + Math.floor(i / cols) * 22} r="7" className="person">
            <title>{person ? `${person.fullName} (${person.employeeCode}) · in since ${time(person.since)}` : "Employee inside"}</title>
          </circle>
        );
      })}
      {site.inside > MAX_DOTS && (
        <text x={x0 + w - 12} y={y0 + h - 12} textAnchor="end" className="floor-label">+{site.inside - MAX_DOTS} more</text>
      )}
      {site.inside === 0 && (
        <text x={x0 + w / 2} y={y0 + h / 2 + 4} textAnchor="middle" className="floor-label">Nobody inside</text>
      )}

      {gates.map((gate, i) => {
        const cx = x0 + slot * (i + 1);
        const look = gateLook(gate);
        return (
          <g key={gate.gateId} className={`gate ${look.cls}`}>
            <title>
              {`${gate.name} · ${look.word}`}
              {gate.lastOpenedAt ? ` · last opened ${time(gate.lastOpenedAt)}` : ""}
              {gate.lastHeartbeatAt ? ` · kiosk seen ${time(gate.lastHeartbeatAt)}` : " · kiosk never seen"}
            </title>
            <line x1={cx - 22} x2={cx + 22} y1={y0 + h} y2={y0 + h} className="door-gap" />
            <rect x={cx - 20} y={y0 + h - 9} width="40" height="18" rx="5" className="door" />
            <text x={cx} y={y0 + h + 4} textAnchor="middle" className="door-icon">{look.icon}</text>
            <text x={cx} y={y0 + h + 28} textAnchor="middle" className="gate-name">{gate.name}</text>
            <text x={cx} y={y0 + h + 43} textAnchor="middle" className="gate-dir">{arrow(gate.direction)}</text>
            <text x={cx} y={y0 + h + 59} textAnchor="middle" className="gate-state">{look.word}</text>
          </g>
        );
      })}
      {namesHidden && <desc>Names hidden for this role</desc>}
    </svg>
  );
}

export default function SiteMap() {
  const { user } = useAuth();
  const canSeeEvacuation = !!user && ["SECURITY", "HR", "ADMIN"].includes(user.role);
  const [live, setLive] = useState<LiveSites | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let stopped = false;
    const tick = () =>
      api.get<LiveSites>("/api/live/sites")
        .then((data) => { if (!stopped) { setLive(data); setError(null); } })
        .catch((problem) => { if (!stopped) setError(problem instanceof Error ? problem.message : "Map unavailable"); });
    tick();
    const timer = setInterval(tick, POLL_MS);
    return () => { stopped = true; clearInterval(timer); };
  }, []);

  if (!live) {
    return <section className="card"><h2>Live site map</h2><p className="muted small">{error ?? "Loading…"}</p></section>;
  }

  return (
    <section className="card site-map">
      <div className="row spread">
        <h2>Live site map{live.scope ? ` · ${live.scope}` : ""}</h2>
        <span className="muted small">updates every 2 s · {time(live.generatedAt)}{error ? ` · ${error}` : ""}</span>
      </div>

      <div className="map-legend muted small">
        <span><i className="sw open">✓</i> OPEN: someone just passed</span>
        <span><i className="sw idle">–</i> closed, kiosk online</span>
        <span><i className="sw stuck">!</i> STUCK: waiting for a guard</span>
        <span><i className="sw offline">×</i> OFFLINE: no kiosk heartbeat</span>
        <span><i className="sw person" /> one person inside</span>
        {canSeeEvacuation && <Link className="btn small ghost" to="/evacuation">Evacuation list</Link>}
      </div>

      <div className="grid two">
        {live.sites.map((site) => (
          <div key={site.factoryId} className="site">
            <div className="site-head">
              <strong>{shortName(site.name)}</strong>
              <span><b className="headcount">{site.inside}</b> <span className="muted small">inside · of {site.employees} {live.scope ? "in your team" : "employees"}</span></span>
            </div>
            <FloorPlan site={site} namesHidden={live.namesHidden} />
            {site.people && site.people.length > 0 && (
              <div className="chips">
                {site.people.map((p) => (
                  <span key={p.employeeCode} className="chip static" title={`in since ${time(p.since)}`}>
                    {p.employeeCode} · {p.fullName}
                  </span>
                ))}
                {site.peopleNotShown > 0 && <span className="chip static">+{site.peopleNotShown} more</span>}
              </div>
            )}
          </div>
        ))}
      </div>

      <h3 className="feed-title">Latest scans</h3>
      {live.namesHidden && <p className="muted small">Names are hidden for your role: head counts only.</p>}
      {live.feed.length === 0 ? (
        <p className="muted small">No scans yet. Use the kiosk simulator.</p>
      ) : (
        <table className="feed">
          <tbody>
            {live.feed.map((item, i) => (
              <tr key={`${item.at}-${i}`}>
                <td className="mono small">{time(item.at)}</td>
                <td>{item.factoryCode} · {item.gateName}</td>
                <td><span className={`tag ${item.direction.toLowerCase()}`}>{item.direction}</span></td>
                <td>
                  <span className={`tag status-${item.status.toLowerCase()}`}>{item.status}</span>
                  {item.manual && <span className="tag manual" title="Recorded by a security guard">MANUAL</span>}
                </td>
                <td>{live.namesHidden ? <span className="muted">(hidden)</span> : `${item.employeeCode} · ${item.employeeName}`}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
