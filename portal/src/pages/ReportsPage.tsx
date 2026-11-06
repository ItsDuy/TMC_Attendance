import { useCallback, useEffect, useState } from "react";
import { api, type ExportBatch, type Factory, type MonthlyRow } from "../api";
import { useAuth } from "../auth";

function currentPeriod() {
  const now = new Date();
  return `${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, "0")}`;
}

export default function ReportsPage() {
  const { user } = useAuth();
  const [factories, setFactories] = useState<Factory[]>([]);
  const [factoryId, setFactoryId] = useState("");
  const [period, setPeriod] = useState(currentPeriod());
  const [rows, setRows] = useState<MonthlyRow[]>([]);
  const [exports, setExports] = useState<ExportBatch[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    api.get<Factory[]>("/api/factories")
      .then((list) => {
        setFactories(list);
        if (list.length > 0) setFactoryId(list[0].id);
      })
      .catch((problem) => setError(problem.message));
  }, []);

  const load = useCallback(async () => {
    if (!factoryId) return;
    try {
      const [report, history] = await Promise.all([
        api.get<MonthlyRow[]>(`/api/reports/attendance?period=${period}&factoryId=${factoryId}`),
        api.get<ExportBatch[]>(`/api/reports/exports?period=${period}`),
      ]);
      setRows(report);
      setExports(history.filter((batch) => batch.factoryId === factoryId));
      setError(null);
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Could not load the report");
    }
  }, [factoryId, period]);

  useEffect(() => {
    load();
  }, [load]);

  async function exportNow() {
    setNotice(null);
    try {
      const batch = await api.post<ExportBatch>("/api/reports/exports", { period, factoryId });
      setNotice(`Export ${batch.status.toLowerCase()} · checksum ${batch.checksum.slice(0, 12)}…`);
      load();
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Export failed");
    }
  }

  const totals = rows.reduce(
    (accumulator, row) => ({
      days: accumulator.days + row.daysPresent,
      ins: accumulator.ins + row.checkIns,
      outs: accumulator.outs + row.checkOuts,
    }),
    { days: 0, ins: 0, outs: 0 },
  );

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Reports &amp; HR export</h1>
          <p className="muted">
            Monthly aggregation feeds the HR/Payroll export. Delivered before 06:00 on the 1st, well
            ahead of the 12:00 deadline.
          </p>
        </div>
        <div className="row">
          <input type="month" value={period} onChange={(event) => setPeriod(event.target.value)} />
          <select value={factoryId} onChange={(event) => setFactoryId(event.target.value)}>
            {factories.map((factory) => (
              <option key={factory.id} value={factory.id}>{factory.name}</option>
            ))}
          </select>
          {user?.role === "HR" && (
            <button className="btn primary" onClick={exportNow}>Export to HR/Payroll</button>
          )}
        </div>
      </header>

      {error && <p className="error">{error}</p>}
      {notice && <p className="notice">{notice}</p>}

      <section className="card">
        <div className="row spread">
          <h2>Attendance · {period}</h2>
          <div className="row">
            <span className="pill">{rows.length} employees</span>
            <span className="pill">{totals.days} person-days</span>
            <span className="pill">{totals.ins} in / {totals.outs} out</span>
          </div>
        </div>
        {rows.length === 0 ? (
          <p className="muted small">No completed transactions in this period.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Employee</th><th>Days present</th><th>Check-ins</th><th>Check-outs</th>
                <th>First check-in</th><th>Last check-out</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.employeeId}>
                  <td>{row.employeeCode} · {row.employeeName}</td>
                  <td>{row.daysPresent}</td>
                  <td>{row.checkIns}</td>
                  <td>{row.checkOuts}</td>
                  <td>{row.firstCheckIn ? new Date(row.firstCheckIn).toLocaleString() : "—"}</td>
                  <td>{row.lastCheckOut ? new Date(row.lastCheckOut).toLocaleString() : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="card">
        <h2>Export batches</h2>
        {exports.length === 0 ? (
          <p className="muted small">No export for this period yet.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Status</th><th>Records</th><th>Checksum</th><th>Attempts</th><th>Sent</th>
              </tr>
            </thead>
            <tbody>
              {exports.map((batch) => (
                <tr key={batch.id}>
                  <td><span className={`tag status-${batch.status.toLowerCase()}`}>{batch.status}</span></td>
                  <td>{batch.recordCount}</td>
                  <td className="mono small">{batch.checksum.slice(0, 20)}…</td>
                  <td>{batch.attempts}</td>
                  <td>{batch.sentAt ? new Date(batch.sentAt).toLocaleString() : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="muted small">
          The idempotency key is <code>period + factory</code>: pressing export again returns the same
          batch instead of duplicating it.
        </p>
      </section>
    </div>
  );
}
