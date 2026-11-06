/**
 * Which role sees which page: one place, used by the menu, the router and the login redirect.
 * Each role answers one question:
 *   Employee   "Did I clock in properly?"          Supervisor "Is my team here, who is missing?"
 *   Guard      "Are the gates working?"            HR         "Are the records right, sent to payroll?"
 *   Executive  "How are we doing overall?"         Admin (IT) "Is the system running and safe?"
 * The server enforces the same rules; the menu only mirrors them.
 */
export const NAV = [
  { to: "/kiosk", label: "Kiosk simulator", roles: ["EMPLOYEE", "SUPERVISOR", "HR", "ADMIN"] },
  { to: "/me", label: "My attendance", roles: ["EMPLOYEE", "SUPERVISOR", "HR"] },
  { to: "/supervisor", label: "Live attendance", roles: ["SUPERVISOR", "SECURITY", "HR", "EXECUTIVE", "ADMIN"] },
  { to: "/evacuation", label: "Evacuation list", roles: ["SECURITY", "HR", "ADMIN"] },
  { to: "/reports", label: "Reports & export", roles: ["SUPERVISOR", "HR", "EXECUTIVE", "ADMIN"] },
  { to: "/employees", label: "Employees", roles: ["HR"] },
  { to: "/admin", label: "Administration", roles: ["ADMIN"] },
];

/** Where each role lands after signing in: its main job. */
const HOME: Record<string, string> = {
  EMPLOYEE: "/kiosk",
  SUPERVISOR: "/supervisor",
  SECURITY: "/supervisor",
  HR: "/employees",
  EXECUTIVE: "/reports",
  ADMIN: "/admin",
};

export const ROLE_LABEL: Record<string, string> = {
  ADMIN: "System administrator (IT)",
  HR: "HR staff",
  SUPERVISOR: "Production supervisor",
  SECURITY: "Security guard",
  EXECUTIVE: "Executive management",
  EMPLOYEE: "Employee",
};

/** Roles allowed to see the list of HR/Payroll export batches (matches the server's rule). */
export const EXPORT_HISTORY_ROLES = ["HR", "EXECUTIVE", "ADMIN"];

export function canOpen(role: string, path: string): boolean {
  return NAV.some((item) => item.to === path && item.roles.includes(role));
}

export function homeFor(role: string): string {
  return HOME[role] ?? NAV.find((item) => item.roles.includes(role))?.to ?? "/login";
}
