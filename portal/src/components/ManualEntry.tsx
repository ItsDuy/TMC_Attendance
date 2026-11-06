import { useEffect, useState } from "react";
import { api } from "../api";

/* Human fallback for the security guard: record an entry by hand when the employee's phone,
 * the kiosk or the network is down. Same rules as a scan; reason required; audited; HR reviews. */

type Check = {
  employeeCode: string; fullName: string; department: string; factoryId: string;
  inside: boolean; lastScanAt: string | null; lastDirection: string | null;
};
type Gate = { gateId: string; name: string; direction: "IN" | "OUT" | "BIDIRECTIONAL"; factory: string; factoryId: string };
type Result = { outcome: string; employeeName?: string; employeeCode?: string; direction?: string; message: string };

const REASONS = [
  ["PHONE_UNAVAILABLE", "Phone dead / forgotten (checked ID card)"],
  ["KIOSK_DOWN", "Kiosk / scanner not working"],
  ["NETWORK_DOWN", "Network down"],
  ["OTHER", "Other (explain below)"],
] as const;

const when = (iso: string) => new Date(iso).toLocaleString([], { hour: "2-digit", minute: "2-digit", day: "2-digit", month: "2-digit" });

export default function ManualEntry({ onRecorded }: { onRecorded: () => void }) {
  const [gates, setGates] = useState<Gate[]>([]);
  const [code, setCode] = useState("");
  const [check, setCheck] = useState<Check | null>(null);
  const [gateId, setGateId] = useState("");
  const [direction, setDirection] = useState<"IN" | "OUT">("IN");
  const [reason, setReason] = useState<string>("PHONE_UNAVAILABLE");
  const [note, setNote] = useState("");
  const [result, setResult] = useState<{ kind: "ok" | "bad"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<{ sites: { factoryId: string; name: string; gates: Omit<Gate, "factory" | "factoryId">[] }[] }>("/api/live/sites")
      .then((live) => setGates(live.sites.flatMap((s) =>
        s.gates.map((g) => ({ ...g, factory: s.name.replace(" Factory", ""), factoryId: s.factoryId })))))
      .catch(() => undefined);
  }, []);

  const gate = gates.find((g) => g.gateId === gateId);
  const gatesForPerson = check ? gates.filter((g) => g.factoryId === check.factoryId) : gates;

  async function lookUp(event: React.FormEvent) {
    event.preventDefault();
    setResult(null);
    setCheck(null);
    try {
      const found = await api.get<Check>(`/api/attendance/manual/check?code=${encodeURIComponent(code.trim())}`);
      setCheck(found);
      // Suggest the natural next step: inside -> going out, outside -> coming in.
      const next = found.inside ? "OUT" : "IN";
      setDirection(next);
      const suggested = gates.find((g) => g.factoryId === found.factoryId && g.direction === next)
        ?? gates.find((g) => g.factoryId === found.factoryId);
      setGateId(suggested?.gateId ?? "");
    } catch (problem) {
      setResult({ kind: "bad", text: problem instanceof Error ? problem.message : "Employee not found" });
    }
  }

  async function record() {
    if (!check || !gate) return;
    setBusy(true);
    try {
      const r = await api.post<Result>("/api/attendance/manual", {
        employeeCode: check.employeeCode,
        gateId: gate.gateId,
        direction: gate.direction === "BIDIRECTIONAL" ? direction : null,
        reason,
        note: note.trim() || null,
      });
      const ok = r.outcome === "GRANTED";
      setResult({
        kind: ok ? "ok" : "bad",
        text: `${r.outcome} · ${r.employeeName ?? check.fullName} (${r.employeeCode ?? check.employeeCode})`
          + (r.direction ? ` · check-${r.direction}` : "") + `\n${r.message}`,
      });
      if (ok) {
        setCheck(null); setCode(""); setNote("");
        onRecorded();
      }
    } catch (problem) {
      setResult({ kind: "bad", text: problem instanceof Error ? problem.message : "Could not record the entry" });
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card">
      <h2>Manual check-in / check-out</h2>
      <p className="muted small">
        When a phone, kiosk or the network is down, let the person through and record it here.
        Same rules as a scan. A reason is required, your name is stored, and HR reviews every manual entry.
      </p>

      <form className="row wrap" onSubmit={lookUp}>
        <label>
          1 · Employee code from the ID card
          <input value={code} onChange={(event) => setCode(event.target.value)} placeholder="BD004" required />
        </label>
        <button className="btn ghost">Check</button>
      </form>

      {check && (
        <div className="manual-step">
          <p>
            <strong>{check.fullName}</strong> · {check.employeeCode} · {check.department}
            {" — "}
            {check.inside
              ? <span className="tag in">inside since {check.lastScanAt ? when(check.lastScanAt) : "?"}</span>
              : <span className="tag out">outside{check.lastScanAt ? ` (last ${check.lastDirection} ${when(check.lastScanAt)})` : ""}</span>}
          </p>
          <p className="muted small">Does the name match the ID card? If not, do not continue.</p>

          <div className="row wrap">
            <label>
              2 · Gate
              <select value={gateId} onChange={(event) => setGateId(event.target.value)}>
                {gatesForPerson.map((g) => (
                  <option key={g.gateId} value={g.gateId}>{g.factory} · {g.name}</option>
                ))}
              </select>
            </label>
            {gate?.direction === "BIDIRECTIONAL" && (
              <label>
                Direction
                <select value={direction} onChange={(event) => setDirection(event.target.value as "IN" | "OUT")}>
                  <option value="IN">IN (arriving)</option>
                  <option value="OUT">OUT (leaving)</option>
                </select>
              </label>
            )}
            <label>
              3 · Reason
              <select value={reason} onChange={(event) => setReason(event.target.value)}>
                {REASONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </label>
          </div>
          <label>
            Note {reason === "OTHER" ? "(required)" : "(optional)"}
            <input value={note} maxLength={160} onChange={(event) => setNote(event.target.value)}
              placeholder="e.g. phone battery dead, checked company ID" />
          </label>
          <button className="btn primary" onClick={record} disabled={busy || !gate}>
            Record {gate?.direction === "BIDIRECTIONAL" ? direction : gate?.direction ?? ""} for {check.fullName}
          </button>
        </div>
      )}

      {result && <div className={`result ${result.kind}`}><pre>{result.text}</pre></div>}
    </section>
  );
}
