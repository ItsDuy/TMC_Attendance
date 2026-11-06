import { useCallback, useEffect, useState } from "react";
import { api, type AttendanceRecord } from "../api";
import { useAuth } from "../auth";

export default function SupervisorPage() {
  const { user } = useAuth();
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [recovery, setRecovery] = useState<AttendanceRecord[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [today, stuck] = await Promise.all([
        api.get<AttendanceRecord[]>("/api/attendance"),
        api.get<AttendanceRecord[]>("/api/attendance/gate-unavailable"),
      ]);
      setRecords(today);
      setRecovery(stuck);
      setError(null);
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Could not load attendance");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function openGate(record: AttendanceRecord) {
    try {
      const response = await api.post<{ opened: boolean; detail: string }>(
        `/api/gates/${record.gateId}/open?recordId=${record.id}`,
      );
      setNotice(response.opened
        ? `Gate opened for ${record.employeeName ?? record.employeeId}`
        : `Gate still unavailable: ${response.detail}`);
      load();
    } catch (problem) {
      setNotice(problem instanceof Error ? problem.message : "Manual open failed");
    }
  }

  async function confirmAdmitted(record: AttendanceRecord) {
    try {
      await api.post(`/api/gates/records/${record.id}/resolve`);
      setNotice(`Recorded manual admission for ${record.employeeName ?? record.employeeId}`);
      load();
    } catch (problem) {
      setNotice(problem instanceof Error ? problem.message : "Could not confirm admission");
    }
  }

  const canOpen = user?.role === "SECURITY" || user?.role === "ADMIN";

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Live attendance</h1>
          <p className="muted">Last 24 hours. Records are written before the gate is actuated.</p>
        </div>
        <button className="btn ghost" onClick={load}>Refresh</button>
      </header>

      {error && <p className="error">{error}</p>}
      {notice && <p className="notice">{notice}</p>}

      <section className="card">
        <h2>Recovery queue · gate did not open</h2>
        {recovery.length === 0 ? (
          <p className="muted small">Nothing waiting for a guard.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Employee</th><th>Direction</th><th>Scanned</th><th>Reason</th><th />
              </tr>
            </thead>
            <tbody>
              {recovery.map((record) => (
                <tr key={record.id}>
                  <td>{record.employeeCode} · {record.employeeName}</td>
                  <td><span className={`tag ${record.direction.toLowerCase()}`}>{record.direction}</span></td>
                  <td>{new Date(record.scannedAt).toLocaleString()}</td>
                  <td className="muted small">{record.reason}</td>
                  <td>
                    {canOpen && (
                      <>
                      <button className="btn small" onClick={() => openGate(record)}>Open gate</button>{" "}
                      <button className="btn small ghost" onClick={() => confirmAdmitted(record)}>Admitted manually</button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="card">
        <h2>Today&apos;s transactions</h2>
        {records.length === 0 ? (
          <p className="muted small">No scans yet. Use the kiosk simulator.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Employee</th><th>Direction</th><th>Status</th><th>Source</th><th>Scanned</th>
              </tr>
            </thead>
            <tbody>
              {records.map((record) => (
                <tr key={record.id}>
                  <td>{record.employeeCode} · {record.employeeName}</td>
                  <td><span className={`tag ${record.direction.toLowerCase()}`}>{record.direction}</span></td>
                  <td><span className={`tag status-${record.status.toLowerCase()}`}>{record.status}</span></td>
                  <td>{record.source}</td>
                  <td>{new Date(record.scannedAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
