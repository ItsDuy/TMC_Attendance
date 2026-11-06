import { useCallback, useEffect, useState } from "react";
import { api } from "../api";

type Person = { employeeCode: string; fullName: string; department: string | null; inSince: string; enteredThrough: string | null };
type Site = { code: string; name: string; inside: number; people: Person[] };
type Evacuation = { generatedAt: string; sites: Site[] };

const time = (iso: string) => new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

/**
 * Fire drill / evacuation: everyone still inside, per factory, sorted by name, with a tick
 * column for the assembly point. Printable. Same rule as the live map's head count.
 */
export default function EvacuationPage() {
  const [data, setData] = useState<Evacuation | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setData(await api.get<Evacuation>("/api/live/evacuation"));
      setError(null);
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Could not load the list");
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const total = data?.sites.reduce((sum, s) => sum + s.inside, 0) ?? 0;

  return (
    <div className="page evacuation">
      <header className="page-head">
        <div>
          <h1>Evacuation list</h1>
          <p className="muted">
            Everyone the system believes is <strong>inside right now</strong>. At the assembly point,
            tick each person who is safe; anyone left unticked may still be in the building.
          </p>
        </div>
        <div className="row no-print">
          <button className="btn ghost" onClick={load}>Refresh</button>
          <button className="btn primary" onClick={() => window.print()}>Print</button>
        </div>
      </header>

      {error && <p className="error">{error}</p>}
      {data && (
        <p className="evac-total">
          <b className="headcount">{total}</b> people inside · list made at {time(data.generatedAt)}
          <span className="muted small"> · people let in by a guard (manual entry) are included</span>
        </p>
      )}

      {data?.sites.map((site) => (
        <section key={site.code} className="card evac-site">
          <div className="row spread">
            <h2>{site.name}</h2>
            <span><b className="headcount">{site.inside}</b> <span className="muted small">inside</span></span>
          </div>
          {site.people.length === 0 ? (
            <p className="muted small">Nobody inside.</p>
          ) : (
            <table>
              <thead>
                <tr><th className="tick">Safe</th><th>Name</th><th>Code</th><th>Team</th><th>In since</th><th>Came in through</th></tr>
              </thead>
              <tbody>
                {site.people.map((p) => (
                  <tr key={p.employeeCode}>
                    <td className="tick"><span className="box" aria-label="tick when safe" /></td>
                    <td><strong>{p.fullName}</strong></td>
                    <td className="mono">{p.employeeCode}</td>
                    <td>{p.department ?? ""}</td>
                    <td>{time(p.inSince)}</td>
                    <td className="small">{p.enteredThrough ?? ""}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      ))}
    </div>
  );
}
