import { useCallback, useEffect, useState } from "react";
import { api, type AuditEntry, type Kiosk } from "../api";

export default function AdminPage() {
  const [kiosks, setKiosks] = useState<Kiosk[]>([]);
  const [audit, setAudit] = useState<AuditEntry[]>([]);
  const [chain, setChain] = useState<{ valid: boolean; checked: number; brokenAt: number | null } | null>(null);
  const [revealed, setRevealed] = useState<{ name: string; secret: string } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [kioskList, auditEntries] = await Promise.all([
        api.get<Kiosk[]>("/api/kiosks"),
        api.get<AuditEntry[]>("/api/audit?size=25"),
      ]);
      setKiosks(kioskList.filter((k) => !k.name.startsWith("[test]")));
      setAudit(auditEntries);
      setError(null);
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Could not load admin data");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function rotate(kiosk: Kiosk) {
    try {
      const updated = await api.post<Kiosk>(`/api/kiosks/${kiosk.id}/rotate-secret`);
      setRevealed({ name: updated.name, secret: updated.secret ?? "" });
      load();
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Could not rotate the secret");
    }
  }

  async function verifyChain() {
    try {
      const result = await api.get<{ valid: boolean; checked: number; brokenAt: number | null }>(
        "/api/audit/verify",
      );
      setChain(result);
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Could not verify the chain");
    }
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Administration</h1>
          <p className="muted">
            IT administration: gate kiosks and the tamper-evident audit log. Employee records belong
            to HR, and admin cannot change attendance data, so the person who checks the log is not
            the person who edits the data (separation of duties).
          </p>
        </div>
        <button className="btn ghost" onClick={load}>Refresh</button>
      </header>

      {error && <p className="error">{error}</p>}

      {revealed && (
        <div className="card highlight">
          <h2>New secret for {revealed.name}</h2>
          <p className="muted small">Shown once. Paste it into the kiosk simulator.</p>
          <code className="secret">{revealed.secret}</code>
          <button className="btn ghost" onClick={() => setRevealed(null)}>Dismiss</button>
        </div>
      )}

      <section className="card">
        <h2>Kiosks</h2>
        <table>
          <thead>
            <tr><th>Name</th><th>Status</th><th>Config</th><th>Last heartbeat</th><th /></tr>
          </thead>
          <tbody>
            {kiosks.map((kiosk) => (
              <tr key={kiosk.id}>
                <td>{kiosk.name}<div className="mono small muted">{kiosk.id}</div></td>
                <td><span className="tag">{kiosk.status}</span></td>
                <td>v{kiosk.configVersion}</td>
                <td>{kiosk.lastHeartbeatAt ? new Date(kiosk.lastHeartbeatAt).toLocaleString() : "—"}</td>
                <td><button className="btn small ghost" onClick={() => rotate(kiosk)}>Rotate secret</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card">
        <div className="row spread">
          <h2>Audit log</h2>
          <div className="row">
            <button className="btn ghost" onClick={verifyChain}>Verify hash chain</button>
            {chain && (
              <span className={`tag ${chain.valid ? "status-completed" : "status-gate_unavailable"}`}>
                {chain.valid ? `valid · ${chain.checked} entries` : `broken at #${chain.brokenAt}`}
              </span>
            )}
          </div>
        </div>
        <table>
          <thead>
            <tr><th>#</th><th>When</th><th>Actor</th><th>Action</th><th>Resource</th><th>Hash</th></tr>
          </thead>
          <tbody>
            {audit.map((entry) => (
              <tr key={entry.id}>
                <td>{entry.id}</td>
                <td>{new Date(entry.occurredAt).toLocaleString()}</td>
                <td>{entry.actor}</td>
                <td><span className="tag">{entry.action}</span></td>
                <td className="small">{entry.resourceType} {entry.resourceId?.slice(0, 8)}</td>
                <td className="mono small muted">{entry.hash.slice(0, 12)}…</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
