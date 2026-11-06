import { useCallback, useEffect, useMemo, useState } from "react";
import { api, type AttendanceRecord } from "../api";
import { useAuth } from "../auth";

type Day = { date: string; firstIn: string | null; lastOut: string | null; scans: AttendanceRecord[] };

const time = (iso: string) => new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

/** "Did I clock in properly?": every account that belongs to an employee sees its own month, only its own. */
export default function MyAttendancePage() {
  const { user } = useAuth();
  const [period, setPeriod] = useState(() => new Date().toISOString().slice(0, 7));
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setRecords(await api.get<AttendanceRecord[]>(`/api/me/attendance?period=${period}`));
      setError(null);
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Could not load your attendance");
    }
  }, [period]);

  useEffect(() => { load(); }, [load]);

  // Group by the viewer's local calendar day; only completed scans count as attendance.
  const days = useMemo<Day[]>(() => {
    const byDay = new Map<string, Day>();
    for (const r of [...records].sort((a, b) => a.scannedAt.localeCompare(b.scannedAt))) {
      const date = new Date(r.scannedAt).toLocaleDateString("en-CA");
      const day = byDay.get(date) ?? { date, firstIn: null, lastOut: null, scans: [] };
      day.scans.push(r);
      if (r.status === "COMPLETED") {
        if (r.direction === "IN" && !day.firstIn) day.firstIn = r.scannedAt;
        if (r.direction === "OUT") day.lastOut = r.scannedAt;
      }
      byDay.set(date, day);
    }
    return [...byDay.values()].reverse();
  }, [records]);

  const present = days.filter((d) => d.firstIn).length;
  const missingOut = days.filter((d) => d.firstIn && !d.lastOut).length;

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>My attendance</h1>
          <p className="muted">
            {user?.fullName} ({user?.employeeCode}). Only you see this page's data.
          </p>
        </div>
        <label className="row">
          Month
          <input type="month" value={period} onChange={(event) => setPeriod(event.target.value)} />
        </label>
      </header>

      {error && <p className="error">{error}</p>}

      <div className="grid two">
        <section className="card">
          <h2>This month</h2>
          <p><b className="headcount">{present}</b> <span className="muted">days present</span></p>
          <p className="muted small">
            {missingOut > 0
              ? `${missingOut} day(s) with a check-in but no check-out. Tell your supervisor if you forgot to scan out.`
              : "Every day you checked in also has a check-out."}
          </p>
        </section>
        <section className="card">
          <h2>How it's counted</h2>
          <p className="muted small">
            A day counts when the gate recorded your check-IN. "In" is your first check-in of the day,
            "Out" your last check-out. Scans that waited for a guard count once the guard confirms them.
          </p>
        </section>
      </div>

      <section className="card">
        <h2>Day by day</h2>
        {days.length === 0 ? (
          <p className="muted small">No scans this month.</p>
        ) : (
          <table>
            <thead>
              <tr><th>Day</th><th>In</th><th>Out</th><th>All scans</th></tr>
            </thead>
            <tbody>
              {days.map((day) => (
                <tr key={day.date}>
                  <td>{new Date(day.date + "T00:00").toLocaleDateString([], { weekday: "short", day: "2-digit", month: "2-digit" })}</td>
                  <td>{day.firstIn ? time(day.firstIn) : "–"}</td>
                  <td>{day.lastOut ? time(day.lastOut) : <span className="muted">missing</span>}</td>
                  <td className="small">
                    {day.scans.map((s) => (
                      <span key={s.id} className={`tag ${s.direction.toLowerCase()}`} title={s.status}>
                        {s.direction} {time(s.scannedAt)}{s.status !== "COMPLETED" ? " ⚠" : ""}
                      </span>
                    ))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
