import { useCallback, useEffect, useState } from "react";
import { api, type Employee, type Factory } from "../api";

const DEPARTMENTS = ["Assembly", "Quality", "Logistics"];
const EMPTY_FORM = { employeeCode: "", fullName: "", factoryId: "", department: "Assembly", email: "", phone: "" };

/** HR owns employee records: register people, and kill a leaked QR (Rotate QR). */
export default function EmployeesPage() {
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [factories, setFactories] = useState<Factory[]>([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [people, factoryList] = await Promise.all([
        api.get<Employee[]>("/api/employees"),
        api.get<Factory[]>("/api/factories"),
      ]);
      // Only real staff: hide the throwaway people created by test scripts.
      setEmployees(people.filter((e) => !/^(LT|E2E|CHAOS)/.test(e.employeeCode)));
      setFactories(factoryList);
      setForm((current) => ({ ...current, factoryId: current.factoryId || factoryList[0]?.id || "" }));
      setError(null);
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Could not load employees");
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const factoryName = (id: string) => factories.find((f) => f.id === id)?.name.replace(" Factory", "") ?? "";

  async function createEmployee(event: React.FormEvent) {
    event.preventDefault();
    setNotice(null);
    try {
      await api.post("/api/employees", form);
      setNotice(`Registered ${form.employeeCode} · ${form.fullName}`);
      setForm({ ...EMPTY_FORM, factoryId: form.factoryId });
      load();
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Could not register the employee");
    }
  }

  async function rotateQr(employee: Employee) {
    try {
      await api.post(`/api/employees/${employee.id}/qr/rotate`);
      setNotice(`Every QR already shown by ${employee.fullName} (${employee.employeeCode}) stopped working. `
        + "They just show a new one.");
      load();
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Could not rotate the QR");
    }
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Employees</h1>
          <p className="muted">Employee records are owned by HR. Every change is written to the audit log.</p>
        </div>
        <button className="btn ghost" onClick={load}>Refresh</button>
      </header>

      {error && <p className="error">{error}</p>}
      {notice && <p className="notice">{notice}</p>}

      <div className="grid two">
        <section className="card">
          <h2>All employees</h2>
          <p className="muted small">
            <strong>Rotate QR</strong>: use it when a QR may have been photographed or shared.
            Every QR that person already showed stops working at once.
          </p>
          <table>
            <thead>
              <tr><th>Code</th><th>Name</th><th>Factory · Dept</th><th>QR</th><th /></tr>
            </thead>
            <tbody>
              {employees.map((employee) => (
                <tr key={employee.id}>
                  <td className="mono">{employee.employeeCode}</td>
                  <td>{employee.fullName}</td>
                  <td className="small">{factoryName(employee.factoryId)} · {employee.department}</td>
                  <td>
                    <span className={`tag status-${employee.credentialStatus.toLowerCase()}`}>
                      v{employee.credentialVersion} {employee.credentialStatus}
                    </span>
                  </td>
                  <td><button className="btn small ghost" onClick={() => rotateQr(employee)}>Rotate QR</button></td>
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
                <input required value={form.employeeCode} placeholder="BD009"
                  onChange={(event) => setForm({ ...form, employeeCode: event.target.value })} />
              </label>
              <label>
                Full name
                <input required value={form.fullName}
                  onChange={(event) => setForm({ ...form, fullName: event.target.value })} />
              </label>
            </div>
            <div className="row">
              <label>
                Factory
                <select value={form.factoryId} onChange={(event) => setForm({ ...form, factoryId: event.target.value })}>
                  {factories.map((factory) => <option key={factory.id} value={factory.id}>{factory.name}</option>)}
                </select>
              </label>
              <label>
                Department (team)
                <select value={form.department} onChange={(event) => setForm({ ...form, department: event.target.value })}>
                  {DEPARTMENTS.map((d) => <option key={d} value={d}>{d}</option>)}
                </select>
              </label>
            </div>
            <div className="row">
              <label>
                Email
                <input type="email" required value={form.email}
                  onChange={(event) => setForm({ ...form, email: event.target.value })} />
              </label>
              <label>
                Phone
                <input value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} />
              </label>
            </div>
            <button className="btn primary">Register employee</button>
          </form>
        </section>
      </div>
    </div>
  );
}
