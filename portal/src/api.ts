const BASE = import.meta.env.VITE_API_BASE ?? "http://localhost:8080";

const TOKEN_KEY = "tmc.token";
const USER_KEY = "tmc.user";

export type SessionUser = {
  username: string;
  role: string;
  employeeId: string | null;
};

export class ApiError extends Error {
  status: number;
  code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser(): SessionUser | null {
  const raw = localStorage.getItem(USER_KEY);
  return raw ? (JSON.parse(raw) as SessionUser) : null;
}

export function storeSession(token: string, user: SessionUser) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body) {
    headers.set("Content-Type", "application/json");
  }
  const token = getToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(BASE + path, { ...init, headers });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;

  if (!response.ok) {
    if (response.status === 401 && token) {
      clearSession();
    }
    throw new ApiError(
      response.status,
      payload?.code ?? "ERROR",
      payload?.message ?? `Request failed (${response.status})`,
    );
  }
  return payload as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) =>
    request<T>(path, { method: "PATCH", body: JSON.stringify(body) }),
};

export async function login(username: string, password: string) {
  const response = await request<{
    token: string;
    username: string;
    role: string;
    employeeId: string | null;
  }>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
  const user: SessionUser = {
    username: response.username,
    role: response.role,
    employeeId: response.employeeId,
  };
  storeSession(response.token, user);
  return user;
}

export async function loginAsKiosk(kioskId: string, secret: string, path: string, body: unknown) {
  const raw = JSON.stringify(body);
  const timestamp = Date.now().toString();
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(`${kioskId}\n${timestamp}\n${raw}`),
  );
  const hex = Array.from(new Uint8Array(signature))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");

  const response = await fetch(BASE + path, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Kiosk-Id": kioskId,
      "X-Kiosk-Timestamp": timestamp,
      "X-Kiosk-Signature": hex,
    },
    body: raw,
  });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new ApiError(response.status, payload?.code ?? "KIOSK_ERROR",
      payload?.message ?? "Kiosk request failed");
  }
  return payload;
}

export type Employee = {
  id: string;
  employeeCode: string;
  fullName: string;
  factoryId: string;
  department: string;
  email: string;
  phone: string | null;
  status: string;
  credentialVersion: number;
  credentialStatus: string;
};

export type Factory = { id: string; code: string; name: string };

export type Kiosk = {
  id: string;
  gateId: string;
  factoryId: string;
  name: string;
  status: string;
  configVersion: number;
  lastHeartbeatAt: string | null;
  secret?: string;
};

export type AttendanceRecord = {
  id: string;
  employeeId: string;
  employeeCode: string | null;
  employeeName: string | null;
  factoryId: string;
  gateId: string;
  kioskId: string;
  direction: "IN" | "OUT";
  scannedAt: string;
  recordedAt: string;
  status: string;
  gateOutcome: string | null;
  source: "ONLINE" | "OFFLINE";
  reason: string | null;
};

export type ScanResult = {
  outcome: string;
  recordId?: string;
  employeeCode?: string;
  employeeName?: string;
  direction?: string;
  scannedAt?: string;
  recordStatus?: string;
  gateOpened: boolean;
  manualFallback: boolean;
  message: string;
};

export type MonthlyRow = {
  employeeId: string;
  employeeCode: string;
  employeeName: string;
  daysPresent: number;
  checkIns: number;
  checkOuts: number;
  firstCheckIn: string | null;
  lastCheckOut: string | null;
};

export type ExportBatch = {
  id: string;
  period: string;
  factoryId: string;
  idempotencyKey: string;
  recordCount: number;
  checksum: string;
  status: string;
  attempts: number;
  sentAt: string | null;
};

export type AuditEntry = {
  id: number;
  occurredAt: string;
  actor: string;
  action: string;
  resourceType: string;
  resourceId: string | null;
  hash: string;
};
