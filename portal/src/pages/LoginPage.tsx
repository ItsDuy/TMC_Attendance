import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { login } from "../api";
import { useAuth } from "../auth";
import { homeFor } from "../roles";

const STAFF_ACCOUNTS = [
  { username: "admin", label: "Administrator" },
  { username: "hr", label: "HR staff · BD003" },
  { username: "supervisor", label: "Supervisor · BD002" },
  { username: "guard", label: "Security guard" },
  { username: "executive", label: "Executive" },
];

// One login per seeded employee: the username is the employee code in lower case.
const EMPLOYEE_ACCOUNTS = [
  ["bd001", "Nguyen Van An"], ["bd002", "Tran Thi Binh"], ["bd003", "Le Quang Cuong"],
  ["bd004", "Pham Thi Dung"], ["bd005", "Hoang Van Em"], ["bd006", "Vu Thi Giang"],
  ["bd007", "Do Van Hai"], ["bd008", "Bui Thi Lan"], ["la001", "Dang Van Minh"],
  ["la002", "Nguyen Thi Nga"], ["la003", "Truong Van Oanh"], ["la004", "Ly Thi Phuong"],
];

export default function LoginPage() {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("Password@123");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const { setUser } = useAuth();
  const navigate = useNavigate();

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const user = await login(username, password);
      setUser(user);
      navigate(homeFor(user.role));
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : "Sign in failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="brand large">
          <span className="brand-mark">TMC</span>
          <span className="brand-sub">Attendance &amp; Access Control</span>
        </div>
        <form onSubmit={submit} className="stack">
          <label>
            Username
            <input value={username} onChange={(event) => setUsername(event.target.value)} autoFocus />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          {error && <p className="error">{error}</p>}
          <button className="btn primary" disabled={busy}>
            {busy ? "Signing in…" : "Sign in"}
          </button>
        </form>

        <div className="demo-accounts">
          <p className="muted">Demo accounts. Password for all: <code>Password@123</code></p>
          <p className="muted chips-label">Staff</p>
          <div className="chips">
            {STAFF_ACCOUNTS.map((account) => (
              <button key={account.username} type="button" className="chip"
                onClick={() => { setUsername(account.username); setPassword("Password@123"); }}>
                {account.username} · {account.label}
              </button>
            ))}
          </div>
          <p className="muted chips-label">Employees (Binh Duong = bd…, Long An = la…)</p>
          <div className="chips">
            {EMPLOYEE_ACCOUNTS.map(([username, name]) => (
              <button key={username} type="button" className="chip"
                onClick={() => { setUsername(username); setPassword("Password@123"); }}>
                {username} · {name}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
