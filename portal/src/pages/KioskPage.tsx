import { useEffect, useMemo, useRef, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { api, ApiError, loginAsKiosk, type Employee, type Factory, type Kiosk, type ScanResult } from "../api";
import { useAuth } from "../auth";

type BufferedScan = {
  clientEventId: string;
  token: string;
  scannedAt: string;
};

const KIOSK_KEY = "tmc.kiosk";        // id of the gate this browser is scanning at
const GATES_KEY = "tmc.gates";        // every gate set up in this browser
type SavedGate = { id: string; secret: string; label: string };

function loadGates(): { gates: SavedGate[]; current: string } {
  const gates = JSON.parse(localStorage.getItem(GATES_KEY) ?? "[]") as SavedGate[];
  const raw = localStorage.getItem(KIOSK_KEY) ?? "";
  if (raw.startsWith("{")) {
    // older single-gate format: { id, secret, label? }
    const old = JSON.parse(raw) as { id: string; secret: string; label?: string };
    if (old.id && !gates.some((g) => g.id === old.id)) {
      gates.push({ id: old.id, secret: old.secret, label: old.label || "pasted by hand" });
    }
    return { gates, current: old.id };
  }
  return { gates, current: raw || gates[0]?.id || "" };
}

function saveGates(gates: SavedGate[], current: string) {
  localStorage.setItem(GATES_KEY, JSON.stringify(gates));
  localStorage.setItem(KIOSK_KEY, current);
}
const BUFFER_KEY = "tmc.buffer";

function loadBuffered(): BufferedScan[] {
  const raw = localStorage.getItem(BUFFER_KEY);
  return raw ? (JSON.parse(raw) as BufferedScan[]) : [];
}

function saveBuffered(buffer: BufferedScan[]) {
  localStorage.setItem(BUFFER_KEY, JSON.stringify(buffer));
}

/** Which way people walk through this gate, read from the seeded gate names. */
function directionOf(name: string): string {
  if (/\bOut\b/i.test(name)) return "check-OUT";
  if (/\bIn\b/i.test(name)) return "check-IN";
  return "IN or OUT (alternates)";
}

/** Turn kiosk errors into what the person should actually do. */
function explainKioskError(problem: unknown): string {
  if (problem instanceof ApiError) {
    switch (problem.code) {
      case "KIOSK_BAD_SIGNATURE":
        return "This browser's gate password (secret) is out of date or mistyped.\n"
          + "Someone probably pressed \"Rotate secret\" for this gate since it was set up.\n"
          + "Fix: sign in as admin → Kiosk simulator → \"Set up all gates\".";
      case "KIOSK_UNKNOWN":
        return "The server doesn't know this gate (was the database reset?).\n"
          + "Fix: sign in as admin → Kiosk simulator → \"Set up all gates\".";
      case "KIOSK_CLOCK_SKEW":
        return "This computer's clock is more than 60 s off the server's. Fix the clock and retry.";
      default:
        return problem.message;
    }
  }
  return problem instanceof Error ? problem.message : "Kiosk request failed";
}

export default function KioskPage() {
  const { user } = useAuth();
  const initial = useMemo(loadGates, []);
  const [gates, setGates] = useState<SavedGate[]>(initial.gates);
  const [currentGate, setCurrentGate] = useState(initial.current);
  const [pasteId, setPasteId] = useState("");
  const [pasteSecret, setPasteSecret] = useState("");
  const [gateOptions, setGateOptions] = useState<{ kiosk: Kiosk; label: string }[]>([]);
  const active = gates.find((g) => g.id === currentGate);
  const kioskId = active?.id ?? "";
  const secret = active?.secret ?? "";
  const kioskLabel = active?.label ?? "";
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
    if (user && !user.employeeId) { // only the admin demo tool needs the list
      api.get<Employee[]>("/api/employees")
        .then((list) => {
          setEmployees(list.filter((e) => !/^(LT|E2E|CHAOS)/.test(e.employeeCode)));
          if (list.length > 0) setSelectedEmployee(list[0].id);
        })
        .catch(() => undefined);
    }
  }, [user]);

  // Admins get a one-click gate picker (the kiosk list is admin-only on the server).
  useEffect(() => {
    if (user?.role !== "ADMIN") return;
    Promise.all([api.get<Kiosk[]>("/api/kiosks"), api.get<Factory[]>("/api/factories")])
      .then(([kiosks, factories]) => {
        const factoryName = (id: string) => factories.find((f) => f.id === id)?.name.replace(" Factory", "") ?? "?";
        const options = kiosks
          .filter((k) => !k.name.startsWith("[test]"))
          .map((k) => ({ kiosk: k, label: `${factoryName(k.factoryId)} · ${k.name.replace(" Kiosk", "")} · ${directionOf(k.name)}` }))
          .sort((a, b) => a.label.localeCompare(b.label));
        setGateOptions(options);
      })
      .catch(() => undefined);
  }, [user]);

  // Heartbeat: while this page is open, every gate set up here tells the server "I'm alive".
  // The live site map greys out a gate whose kiosk has not checked in for 45 s.
  useEffect(() => {
    if (gates.length === 0) return;
    const beat = () => gates.forEach((g) =>
      loginAsKiosk(g.id, g.secret, "/api/kiosk/heartbeat", {}).catch(() => undefined));
    beat();
    const timer = setInterval(beat, 15000);
    return () => clearInterval(timer);
  }, [gates]);

  function chooseGate(id: string) {
    setCurrentGate(id);
    saveGates(gates, id);
  }

  function pasteGate() {
    if (!pasteId || !pasteSecret) return;
    const next = [...gates.filter((g) => g.id !== pasteId), { id: pasteId, secret: pasteSecret, label: "pasted by hand" }];
    setGates(next);
    setCurrentGate(pasteId);
    saveGates(next, pasteId);
  }

  async function setUpAllGates() {
    setBusy(true);
    setResult(null);
    try {
      // Each gate gets a brand-new secret, handed straight to this browser.
      // Any older copy of those secrets (another browser, the startup log) stops working.
      const next: SavedGate[] = [];
      for (const option of gateOptions) {
        const rotated = await api.post<Kiosk>(`/api/kiosks/${option.kiosk.id}/rotate-secret`);
        next.push({ id: option.kiosk.id, secret: rotated.secret ?? "", label: option.label });
      }
      const keep = next.find((g) => g.id === currentGate) ? currentGate : next[0]?.id ?? "";
      setGates(next);
      setCurrentGate(keep);
      saveGates(next, keep);
      setResult({ kind: "ok", text: `${next.length} gates are set up in this browser.\nAnyone signed in here can now pick a gate under "Scanning at".` });
    } catch (problem) {
      setResult({ kind: "bad", text: problem instanceof Error ? problem.message : "Could not set up the gates" });
    } finally {
      setBusy(false);
    }
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
      setResult({ kind: "bad", text: "No gate set up yet (step 1). Sign in as admin and click \"Set up all gates\"." });
      return;
    }
    if (!token.trim()) {
      setResult({ kind: "bad", text: "Show a QR code first (step 2)" });
      return;
    }
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
      setResult({ kind: "bad", text: explainKioskError(problem) });
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
      setResult({ kind: "bad", text: explainKioskError(problem) });
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
      text: [
        response.outcome,
        response.employeeName ? `${response.employeeName} (${response.employeeCode})` : "QR not recognised",
        response.direction ? (response.direction === "IN" ? "check-IN" : "check-OUT") : null,
      ].filter(Boolean).join(" · ") + `\n${response.message}`,
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
          <h2>1 · Set up the gates</h2>
          <p className="muted small">
            This page pretends to be a gate scanner. Real scanners are set up once by a technician;
            here an admin does it once per browser.
          </p>
          {user?.role === "ADMIN" ? (
            <button className="btn primary" onClick={setUpAllGates} disabled={busy || gateOptions.length === 0}>
              {gates.length > 0 ? "Set up all gates again" : "Set up all gates"}
            </button>
          ) : (
            gates.length === 0 && (
              <p className="muted small">No gates here yet. Ask an admin to sign in on this browser and click “Set up all gates”.</p>
            )
          )}
          {gates.length > 0 && (
            <label>
              Scanning at
              <select value={currentGate} onChange={(event) => chooseGate(event.target.value)}>
                {gates.map((g) => (
                  <option key={g.id} value={g.id}>{g.label}</option>
                ))}
              </select>
            </label>
          )}
          <details>
            <summary className="muted small">Advanced: paste a kiosk id and secret by hand</summary>
            <p className="muted small">
              From the backend startup log, or from Administration → Kiosks → Rotate secret.
            </p>
            <label>
              Kiosk id
              <input value={pasteId} onChange={(event) => setPasteId(event.target.value.trim())} placeholder="uuid" />
            </label>
            <label>
              Shared secret
              <input value={pasteSecret} onChange={(event) => setPasteSecret(event.target.value.trim())}
                placeholder="base64 secret" />
            </label>
            <button className="btn ghost" onClick={pasteGate}>Add this gate</button>
          </details>
        </section>

        <section className="card">
          <h2>2 · Employee's phone shows a QR</h2>
          <p className="muted small">
            In real life the employee opens the TMC app and the phone shows this code.
            It changes every 60 s, so a screenshot is useless.
          </p>
          {user?.employeeId ? (
            <div className="row wrap">
              <button className="btn primary" onClick={mintForMe} disabled={busy}>
                Show my QR code
              </button>
              <span className="muted small">
                QR of <strong>{user.fullName ?? "you"}</strong>{user.employeeCode ? ` (${user.employeeCode})` : ""}
              </span>
            </div>
          ) : (
            <>
            <p className="muted small">
              <span className="tag">DEMO TOOL</span> Admin can play <em>any</em> employee's phone here, so
              one laptop can demo everyone. Real employees can only show their own QR.
            </p>
            <div className="row">
              <select value={selectedEmployee} onChange={(event) => setSelectedEmployee(event.target.value)}>
                {employees.map((employee) => (
                  <option key={employee.id} value={employee.id}>
                    {employee.employeeCode} · {employee.fullName}
                  </option>
                ))}
              </select>
              <button className="btn primary" onClick={mintForEmployee} disabled={busy}>
                Show QR code
              </button>
            </div>
            </>
          )}
          {remaining !== null && (
            <p className={remaining > 0 ? "pill live" : "pill expired"}>
              {remaining > 0 ? `valid for ${remaining}s` : "expired: show a new one"}
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
        <h2>3 · The gate scans it</h2>
        <p className="muted small">
          {kioskId ? <>Scanning at <strong>{kioskLabel}</strong>.</> : "No gate set up yet (step 1)."}{" "}
          The box below is what the scanner reads from the QR.
        </p>
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
