import { useCallback, useEffect, useState } from "react";
import { api, type AuditEntry, type Employee, type Factory, type Kiosk } from "../api";

const EMPTY_FORM = {
  employeeCode: "",
  fullName: "",
  factoryId: "",
  department: "Assembly",
  email: "",
  phone: "",
};

export default function AdminPage() {
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [factories, setFactories] = useState<Factory[]>([]);
  const [kiosks, setKiosks] = useState<Kiosk[]>([]);
  const [audit, setAudit] = useState<AuditEntry[]>([]);
  const [chain, setChain] = useState<{ valid: boolean; checked: number; brokenAt: number | null } | null>(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [revealed, setRevealed] = useState<{ name: string; secret: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [people, factoryList, kioskList, auditEntries] = await Promise.all([
        api.get<Employee[]>("/api/employees"),
        api.get<Factory[]>("/api/factories"),
        api.get<Kiosk[]>("/api/kiosks"),
        api.get<AuditEntry[]>("/api/audit?size=25"),
      ]);
      setEmployees(people);
      setFactories(factoryList);
      setKiosks(kioskList);
      setAudit(auditEntries);
      setForm((current) => ({
        ...current,
        factoryId: current.factoryId || factoryList[0]?.id || "",
      }));
      setError(null);
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Could not load admin data");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function createEmployee(event: React.FormEvent) {
    event.preventDefault();
    setNotice(null);
    try {
      await api.post("/api/employees", form);
      setNotice(`Created ${form.employeeCode}`);
      setForm({ ...EMPTY_FORM, factoryId: form.factoryId });
      load();
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Could not create employee");
    }
  }

  async function rotate(kiosk: Kiosk) {
    try {
      const updated = await api.post<Kiosk>(`/api/kiosks/${kiosk.id}/rotate-secret`);
      setRevealed({ name: updated.name, secret: updated.secret ?? "" });
      load();
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Could not rotate the secret");
    }
  }

  async function rotateQr(employee: Employee) {
    try {
      await api.post(`/api/employees/${employee.id}/qr/rotate`);
      setNotice(`Rotated the QR credential for ${employee.employeeCode} (v${employee.credentialVersion + 1})`);
      load();
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Could not rotate the credential");
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
          <p className="muted">Employees, kiosk provisioning and the tamper-evident audit log.</p>
        </div>
        <button className="btn ghost" onClick={load}>Refresh</button>
      </header>

      {error && <p className="error">{error}</p>}
      {notice && <p className="notice">{notice}</p>}

      {revealed && (
        <div className="card highlight">
          <h2>New secret for {revealed.name}</h2>
          <p className="muted small">Shown once. Paste it into the kiosk simulator.</p>
          <code className="secret">{revealed.secret}</code>
          <button className="btn ghost" onClick={() => setRevealed(null)}>Dismiss</button>
        </div>
      )}

      <div className="grid two">
        <section className="card">
          <h2>Employees</h2>
          <table>
            <thead>
              <tr><th>Code</th><th>Name</th><th>Credential</th><th /></tr>
            </thead>
            <tbody>
              {employees.map((employee) => (
                <tr key={employee.id}>
                  <td className="mono">{employee.employeeCode}</td>
                  <td>{employee.fullName}</td>
                  <td>
                    <span className={`tag status-${employee.credentialStatus.toLowerCase()}`}>
                      v{employee.credentialVersion} {employee.credentialStatus}
                    </span>
                  </td>
                  <td>
                    <button className="btn small ghost" onClick={() => rotateQr(employee)}>Rotate QR</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="card">
          <h2>Register an employee</h2>
          <form className="stack" onSubmit={createEmployee}>
            <div className="row">
              <label>
                Code
                <input required value={form.employeeCode}
                  onChange={(event) => setForm({ ...form, employeeCode: event.target.value })} />
              </label>
              <label>
                Full name
                <input required value={form.fullName}
                  onChange={(event) => setForm({ ...form, fullName: event.target.value })} />
              </label>
            </div>
            <label>
              Factory
              <select value={form.factoryId}
                onChange={(event) => setForm({ ...form, factoryId: event.target.value })}>
                {factories.map((factory) => (
                  <option key={factory.id} value={factory.id}>{factory.name}</option>
                ))}
              </select>
            </label>
            <div className="row">
              <label>
                Department
                <input value={form.department}
                  onChange={(event) => setForm({ ...form, department: event.target.value })} />
              </label>
              <label>
                Email
                <input type="email" required value={form.email}
                  onChange={(event) => setForm({ ...form, email: event.target.value })} />
              </label>
            </div>
            <label>
              Phone
              <input value={form.phone}
                onChange={(event) => setForm({ ...form, phone: event.target.value })} />
            </label>
            <button className="btn primary">Create employee</button>
          </form>
        </section>
      </div>

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
