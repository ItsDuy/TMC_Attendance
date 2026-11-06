import { Navigate, NavLink, Route, Routes, useNavigate } from "react-router-dom";
import { useAuth } from "./auth";
import LoginPage from "./pages/LoginPage";
import KioskPage from "./pages/KioskPage";
import SupervisorPage from "./pages/SupervisorPage";
import ReportsPage from "./pages/ReportsPage";
import AdminPage from "./pages/AdminPage";

const NAV = [
  { to: "/kiosk", label: "Kiosk simulator", roles: ["ADMIN", "EMPLOYEE", "SECURITY", "SUPERVISOR"] },
  { to: "/supervisor", label: "Live attendance", roles: ["SUPERVISOR", "SECURITY", "ADMIN", "HR"] },
  { to: "/reports", label: "Reports & export", roles: ["HR", "EXECUTIVE", "ADMIN", "SUPERVISOR"] },
  { to: "/admin", label: "Administration", roles: ["ADMIN"] },
];

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
          <div className="who">
            <strong>{user.username}</strong>
            <span className="pill">{user.role}</span>
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
          <Route path="/kiosk" element={<KioskPage />} />
          <Route path="/supervisor" element={<SupervisorPage />} />
          <Route path="/reports" element={<ReportsPage />} />
          <Route path="/admin" element={<AdminPage />} />
          <Route path="*" element={<Navigate to="/kiosk" replace />} />
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
