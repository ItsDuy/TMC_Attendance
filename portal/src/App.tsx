import { Navigate, NavLink, Route, Routes, useNavigate } from "react-router-dom";
import { useAuth } from "./auth";
import LoginPage from "./pages/LoginPage";
import KioskPage from "./pages/KioskPage";
import SupervisorPage from "./pages/SupervisorPage";
import ReportsPage from "./pages/ReportsPage";
import AdminPage from "./pages/AdminPage";
import EmployeesPage from "./pages/EmployeesPage";
import MyAttendancePage from "./pages/MyAttendancePage";
import EvacuationPage from "./pages/EvacuationPage";
import { canOpen, homeFor, NAV, ROLE_LABEL } from "./roles";
import type { ReactElement } from "react";

/** A page the role may not open (e.g. typed in the address bar) sends it to its own home. */
function Guarded({ path, role, children }: { path: string; role: string; children: ReactElement }) {
  return canOpen(role, path) ? children : <Navigate to={homeFor(role)} replace />;
}

function Shell() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">TMC</span>
          <span className="brand-sub">Attendance &amp; Access</span>
        </div>
        <nav>
          {NAV.filter((item) => item.roles.includes(user.role)).map((item) => (
            <NavLink key={item.to} to={item.to} className="nav-link">
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <div className="who-card">
            <span className="muted small">Signed in as</span>
            <strong>{user.fullName ?? ROLE_LABEL[user.role] ?? user.role}</strong>
            {user.employeeCode && (
              <span className="muted small">
                {user.employeeCode}{user.factoryName ? ` · ${user.factoryName.replace(" Factory", "")}` : ""}
              </span>
            )}
            <span className="who-meta">
              {user.fullName ? <span className="pill">{ROLE_LABEL[user.role] ?? user.role}</span> : <span />}
              <span className="muted small">login: {user.username}</span>
            </span>
          </div>
          <button
            className="btn ghost"
            onClick={() => {
              signOut();
              navigate("/login");
            }}
          >
            Sign out
          </button>
        </div>
      </aside>
      <main className="content">
        <Routes>
          <Route path="/kiosk" element={<Guarded path="/kiosk" role={user.role}><KioskPage /></Guarded>} />
          <Route path="/supervisor" element={<Guarded path="/supervisor" role={user.role}><SupervisorPage /></Guarded>} />
          <Route path="/reports" element={<Guarded path="/reports" role={user.role}><ReportsPage /></Guarded>} />
          <Route path="/admin" element={<Guarded path="/admin" role={user.role}><AdminPage /></Guarded>} />
          <Route path="/employees" element={<Guarded path="/employees" role={user.role}><EmployeesPage /></Guarded>} />
          <Route path="/me" element={<Guarded path="/me" role={user.role}><MyAttendancePage /></Guarded>} />
          <Route path="/evacuation" element={<Guarded path="/evacuation" role={user.role}><EvacuationPage /></Guarded>} />
          <Route path="*" element={<Navigate to={homeFor(user.role)} replace />} />
        </Routes>
      </main>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/*" element={<Shell />} />
    </Routes>
  );
}
