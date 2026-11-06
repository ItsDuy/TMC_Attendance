import { useCallback, useEffect, useState } from "react";
import { api, type ExportBatch, type Factory, type MonthlyRow } from "../api";
import { useAuth } from "../auth";
import { EXPORT_HISTORY_ROLES } from "../roles";

function currentPeriod() {
  const now = new Date();
  return `${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, "0")}`;
}

type ManualEntryLine = {
  at: string; factoryCode: string; gateName: string; direction: string; employeeCode: string;
  employeeName: string; reason: string; note: string | null; enteredBy: string;
};

type FactorySummary = {
  factoryId: string; code: string; name: string; employees: number; employeesWhoAttended: number;
  personDaysPresent: number; averageDaysPerAttendee: number; checkIns: number; checkOuts: number;
};

export default function ReportsPage() {
  const { user } = useAuth();
  // Supervisors may read the monthly report but not the HR/Payroll export history.
  const seesExports = !!user && EXPORT_HISTORY_ROLES.includes(user.role);
  // Executive management gets company-level numbers per factory, never names.
  const summaryOnly = user?.role === "EXECUTIVE";
  const [summary, setSummary] = useState<FactorySummary[]>([]);
  // Guards act, HR reviews: manual entries are listed for HR (and admin) to check.
  const reviewsManual = user?.role === "HR" || user?.role === "ADMIN";
  const [manual, setManual] = useState<ManualEntryLine[]>([]);
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
      if (summaryOnly) {
        const [totals, history] = await Promise.all([
          api.get<FactorySummary[]>(`/api/reports/summary?period=${period}`),
          api.get<ExportBatch[]>(`/api/reports/exports?period=${period}`),
        ]);
        setSummary(totals);
        setExports(history);
        setError(null);
        return;
      }
      const [report, history] = await Promise.all([
        api.get<MonthlyRow[]>(`/api/reports/attendance?period=${period}&factoryId=${factoryId}`),
        seesExports
          ? api.get<ExportBatch[]>(`/api/reports/exports?period=${period}`)
          : Promise.resolve([] as ExportBatch[]),
      ]);
      setRows(report);
      if (reviewsManual) {
        api.get<ManualEntryLine[]>(`/api/reports/manual-entries?period=${period}`).then(setManual).catch(() => setManual([]));
      }
      setExports(history.filter((batch) => batch.factoryId === factoryId));
      setError(null);
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Could not load the report");
    }
  }, [factoryId, period, seesExports, summaryOnly, reviewsManual]);

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
            {summaryOnly
              ? "Company-level numbers per factory. No names: individual records stay with HR and supervisors."
              : user?.role === "SUPERVISOR"
                ? "Your team's month. Other teams are not shown."
                : "Monthly aggregation feeds the HR/Payroll export. The export runs on the 1st from 06:00, before the 12:00 deadline."}
          </p>
        </div>
        <div className="row">
          <input type="month" value={period} onChange={(event) => setPeriod(event.target.value)} />
          {!summaryOnly && <select value={factoryId} onChange={(event) => setFactoryId(event.target.value)}>
            {factories.map((factory) => (
              <option key={factory.id} value={factory.id}>{factory.name}</option>
            ))}
          </select>}
          {user?.role === "HR" && (
            <button className="btn primary" onClick={exportNow}>Export to HR/Payroll</button>
          )}
        </div>
      </header>

      {error && <p className="error">{error}</p>}
      {notice && <p className="notice">{notice}</p>}

      {summaryOnly && (
        <section className="card">
          <h2>Overview · {period}</h2>
          <div className="grid two">
            {summary.map((f) => (
              <div key={f.factoryId} className="site">
                <div className="site-head"><strong>{f.name.replace(" Factory", "")}</strong>
                  <span className="muted small">{f.employees} employees</span></div>
                <p><b className="headcount">{f.employeesWhoAttended}</b> <span className="muted small">came at least once this month</span></p>
                <p className="muted small">
                  {f.personDaysPresent} person-days present · on average {f.averageDaysPerAttendee} {f.averageDaysPerAttendee === 1 ? "day" : "days"} per person
                  · {f.checkIns} check-ins / {f.checkOuts} check-outs
                </p>
              </div>
            ))}
          </div>
        </section>
      )}

      {!summaryOnly && (
      <section className="card">
        <div className="row spread">
          <h2>{user?.role === "SUPERVISOR" ? "My team" : "Attendance"} · {period}</h2>
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
                <th>First check-in</th><th>Last check-out</th><th title="Entries a guard recorded by hand">Manual</th>
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
                  <td>{row.manualEntries ? <span className="tag manual">{row.manualEntries}</span> : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      )}

      {reviewsManual && (
        <section className="card">
          <h2>Manual entries to review · {period}</h2>
          <p className="muted small">
            Entries a security guard recorded by hand (phone, kiosk or network down). Guards act, HR checks:
            look for unusual patterns, e.g. the same guard and the same person again and again.
          </p>
          {manual.length === 0 ? (
            <p className="muted small">No manual entries this month.</p>
          ) : (
            <table>
              <thead><tr><th>When</th><th>Employee</th><th>Gate</th><th>Dir</th><th>Reason</th><th>Note</th><th>Guard</th></tr></thead>
              <tbody>
                {manual.map((m, i) => (
                  <tr key={`${m.at}-${i}`}>
                    <td>{new Date(m.at).toLocaleString()}</td>
                    <td>{m.employeeCode} · {m.employeeName}</td>
                    <td className="small">{m.factoryCode} · {m.gateName}</td>
                    <td><span className={`tag ${m.direction.toLowerCase()}`}>{m.direction}</span></td>
                    <td className="small">{m.reason?.toLowerCase().replace(/_/g, " ")}</td>
                    <td className="small">{m.note ?? "—"}</td>
                    <td className="mono small">{m.enteredBy}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}

      {seesExports && (
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
      )}
    </div>
  );
}
