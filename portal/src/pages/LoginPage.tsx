import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { login } from "../api";
import { useAuth } from "../auth";

const DEMO_ACCOUNTS = [
  { username: "admin", role: "Administrator" },
  { username: "hr", role: "HR staff" },
  { username: "supervisor", role: "Production supervisor" },
  { username: "guard", role: "Security guard" },
  { username: "executive", role: "Executive" },
  { username: "employee", role: "Employee" },
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
      navigate("/kiosk");
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
          <p className="muted">Demo accounts — password <code>Password@123</code></p>
          <div className="chips">
            {DEMO_ACCOUNTS.map((account) => (
              <button
                key={account.username}
                className="chip"
                onClick={() => {
                  setUsername(account.username);
                  setPassword("Password@123");
                }}
              >
                {account.username} · {account.role}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
