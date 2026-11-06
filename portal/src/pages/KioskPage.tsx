import { useEffect, useMemo, useRef, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { api, loginAsKiosk, type Employee, type ScanResult } from "../api";
import { useAuth } from "../auth";

type BufferedScan = {
  clientEventId: string;
  token: string;
  scannedAt: string;
};

const KIOSK_KEY = "tmc.kiosk";
const BUFFER_KEY = "tmc.buffer";

function loadBuffered(): BufferedScan[] {
  const raw = localStorage.getItem(BUFFER_KEY);
  return raw ? (JSON.parse(raw) as BufferedScan[]) : [];
}

function saveBuffered(buffer: BufferedScan[]) {
  localStorage.setItem(BUFFER_KEY, JSON.stringify(buffer));
}

export default function KioskPage() {
  const { user } = useAuth();
  const saved = useMemo(() => {
    const raw = localStorage.getItem(KIOSK_KEY);
    return raw ? (JSON.parse(raw) as { id: string; secret: string }) : { id: "", secret: "" };
  }, []);

  const [kioskId, setKioskId] = useState(saved.id);
  const [secret, setSecret] = useState(saved.secret);
  const [token, setToken] = useState("");
  const [expiresAt, setExpiresAt] = useState<number | null>(null);
  const [now, setNow] = useState(Date.now());
  const [offline, setOffline] = useState(false);
  const [buffer, setBuffer] = useState<BufferedScan[]>(loadBuffered);
  const [result, setResult] = useState<{ kind: "ok" | "warn" | "bad"; text: string; detail?: ScanResult } | null>(null);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [selectedEmployee, setSelectedEmployee] = useState("");
  const [busy, setBusy] = useState(false);
  const tokenRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    if (user && user.role !== "EMPLOYEE") {
      api.get<Employee[]>("/api/employees")
        .then((list) => {
          setEmployees(list);
          if (list.length > 0) setSelectedEmployee(list[0].id);
        })
        .catch(() => undefined);
    }
  }, [user]);

  function persistKiosk() {
    localStorage.setItem(KIOSK_KEY, JSON.stringify({ id: kioskId, secret }));
  }

  async function mintForMe() {
    setBusy(true);
    setResult(null);
    try {
      const qr = await api.get<{ token: string; expiresAt: string }>("/api/me/qr");
      applyQr(qr.token, qr.expiresAt);
    } catch (problem) {
      setResult({ kind: "bad", text: problem instanceof Error ? problem.message : "Could not mint QR" });
    } finally {
      setBusy(false);
    }
  }

  async function mintForEmployee() {
    if (!selectedEmployee) return;
    setBusy(true);
    setResult(null);
    try {
      const qr = await api.get<{ token: string; expiresAt: string }>(`/api/employees/${selectedEmployee}/qr`);
      applyQr(qr.token, qr.expiresAt);
    } catch (problem) {
      setResult({ kind: "bad", text: problem instanceof Error ? problem.message : "Could not mint QR" });
    } finally {
      setBusy(false);
    }
  }

  function applyQr(mintedToken: string, expiry: string) {
    setToken(mintedToken);
    setExpiresAt(new Date(expiry).getTime());
    tokenRef.current?.focus();
  }

  async function scan() {
    if (!kioskId || !secret) {
      setResult({ kind: "bad", text: "Enter the kiosk id and secret first" });
      return;
    }
    if (!token.trim()) {
      setResult({ kind: "bad", text: "Paste or mint a QR token first" });
      return;
    }
    persistKiosk();
    const clientEventId = crypto.randomUUID();

    if (offline) {
      const entry = { clientEventId, token: token.trim(), scannedAt: new Date().toISOString() };
      const next = [...buffer, entry];
      setBuffer(next);
      saveBuffered(next);
      setResult({ kind: "warn", text: `Buffered locally (${next.length} pending). The gate did not open.` });
      return;
    }

    setBusy(true);
    setResult(null);
    try {
      const response = await loginAsKiosk(kioskId, secret, "/api/kiosk/scans", {
        clientEventId,
        token: token.trim(),
      }) as ScanResult;
      present(response);
    } catch (problem) {
      setResult({ kind: "bad", text: problem instanceof Error ? problem.message : "Scan failed" });
    } finally {
      setBusy(false);
    }
  }

  async function sync() {
    if (buffer.length === 0) return;
    setBusy(true);
    try {
      const response = await loginAsKiosk(kioskId, secret, "/api/kiosk/scans/sync", {
        events: buffer,
      }) as { results: ScanResult[] };
      const lines = response.results.map(
        (entry) => `${entry.outcome}: ${entry.employeeCode ?? "unknown"} — ${entry.message}`,
      );
      setBuffer([]);
      saveBuffered([]);
      setResult({
        kind: "ok",
        text: `Synced ${response.results.length} event(s)\n${lines.join("\n")}`,
        detail: response.results[0],
      });
    } catch (problem) {
      setResult({ kind: "bad", text: problem instanceof Error ? problem.message : "Sync failed" });
    } finally {
      setBusy(false);
    }
  }

  function present(response: ScanResult) {
    const kind = response.outcome === "GRANTED"
      ? "ok"
      : response.outcome === "DUPLICATE"
        ? "warn"
        : "bad";
    setResult({
      kind,
      text: `${response.outcome} — ${response.employeeName ?? "unknown"} ${response.direction ?? ""}\n${response.message}`,
      detail: response,
    });
  }

  const remaining = expiresAt ? Math.max(0, Math.ceil((expiresAt - now) / 1000)) : null;

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Kiosk simulator</h1>
          <p className="muted">
            Validates a dynamic QR, records the transaction, then opens the gate. Toggle offline mode to
            exercise the store-and-forward path.
          </p>
        </div>
      </header>

      <div className="grid two">
        <section className="card">
          <h2>1 · Kiosk credentials</h2>
          <p className="muted small">
            Copy an id and secret from the backend startup log, or rotate one in Administration.
          </p>
          <label>
            Kiosk id
            <input value={kioskId} onChange={(event) => setKioskId(event.target.value.trim())}
              placeholder="uuid" />
          </label>
          <label>
            Shared secret
            <input value={secret} onChange={(event) => setSecret(event.target.value.trim())}
              placeholder="base64 secret" />
          </label>
          <div className="row">
            <button className="btn ghost" onClick={persistKiosk}>Remember on this device</button>
            <span className="pill">{kioskId ? "configured" : "not configured"}</span>
          </div>
        </section>

        <section className="card">
          <h2>2 · Get a QR token</h2>
          {user?.employeeId ? (
            <button className="btn primary" onClick={mintForMe} disabled={busy}>
              Mint my dynamic QR
            </button>
          ) : (
            <div className="row">
              <select value={selectedEmployee} onChange={(event) => setSelectedEmployee(event.target.value)}>
                {employees.map((employee) => (
                  <option key={employee.id} value={employee.id}>
                    {employee.employeeCode} · {employee.fullName}
                  </option>
                ))}
              </select>
              <button className="btn primary" onClick={mintForEmployee} disabled={busy}>
                Mint QR
              </button>
            </div>
          )}
          {remaining !== null && (
            <p className={remaining > 0 ? "pill live" : "pill expired"}>
              {remaining > 0 ? `valid for ${remaining}s` : "expired — mint a new one"}
            </p>
          )}
          {token.trim() && (
            <div className="qr-frame">
              <QRCodeSVG value={token} size={180} level="M" marginSize={2}
                bgColor="#ffffff" fgColor="#0c0f12" />
            </div>
          )}
        </section>
      </div>

      <section className="card">
        <h2>3 · Scan</h2>
        <textarea
          ref={tokenRef}
          rows={3}
          value={token}
          onChange={(event) => setToken(event.target.value)}
          placeholder="TMC1.…"
        />
        <div className="row wrap">
          <button className="btn primary" onClick={scan} disabled={busy}>
            {offline ? "Buffer scan" : "Scan at gate"}
          </button>
          <label className="toggle">
            <input type="checkbox" checked={offline} onChange={(event) => setOffline(event.target.checked)} />
            Offline mode
          </label>
          <button className="btn ghost" onClick={sync} disabled={busy || buffer.length === 0}>
            Sync {buffer.length} buffered
          </button>
          {buffer.length > 0 && (
            <button
              className="btn danger"
              onClick={() => {
                setBuffer([]);
                saveBuffered([]);
              }}
            >
              Discard buffer
            </button>
          )}
        </div>

        {result && (
          <div className={`result ${result.kind}`}>
            <pre>{result.text}</pre>
            {result.detail && (
              <dl className="facts">
                <div><dt>Record</dt><dd>{result.detail.recordId ?? "—"}</dd></div>
                <div><dt>Gate opened</dt><dd>{String(result.detail.gateOpened)}</dd></div>
                <div><dt>Manual fallback</dt><dd>{String(result.detail.manualFallback)}</dd></div>
                <div><dt>Record status</dt><dd>{result.detail.recordStatus ?? "—"}</dd></div>
              </dl>
            )}
          </div>
        )}
      </section>
    </div>
  );
}
