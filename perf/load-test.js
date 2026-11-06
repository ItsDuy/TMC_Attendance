/*
 * TMC kiosk load test (k6).
 *
 * Proves the two NFRs that matter at shift change:
 *   1. Kiosk response time <= 2 s at peak (~800 access transactions / 30 min / factory).
 *   2. No loss of attendance records.
 *
 * What one run does
 *   setup     : log in as admin (kiosks) and HR (employees), register a '[test]' kiosk on each factory's IN gate,
 *               register fresh employees (one per scan, so every scan is a real first check-in).
 *   scenarios : Binh Duong and Long An kiosks are hammered AT THE SAME TIME.
 *               Each iteration = phone mints a 60 s QR, kiosk signs + sends the scan.
 *   teardown  : count the attendance records stored for this run (must equal scans sent),
 *               check for duplicates, verify the audit hash chain, and compare
 *               gate openings with the fake turnstile's counter.
 *
 * Profiles (PROFILE env var)
 *   smoke : 20 scans / factory in 30 s      (sanity check, ~1 min total)
 *   burst : 800 scans / factory in 2 min    (15x the real arrival rate, ~3 min total)
 *   peak  : 800 scans / factory in 30 min   (exactly the requirement)
 * Override with SCANS=<n> DURATION=<seconds>.
 *
 * Run it through perf/run-load-test.sh (no k6 install needed) or perf/showcase.sh.
 */
import http from 'k6/http';
import crypto from 'k6/crypto';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const TURNSTILE = (__ENV.TURNSTILE_URL || '').replace(/\/$/, '');
const PASSWORD = __ENV.ADMIN_PASSWORD || 'Password@123';

const PROFILES = {
  smoke: { scans: 20, seconds: 30 },
  burst: { scans: 800, seconds: 120 },
  peak: { scans: 800, seconds: 1800 },
};
const PROFILE_NAME = __ENV.PROFILE || 'burst';
const PROFILE = PROFILES[PROFILE_NAME];
if (!PROFILE) {
  throw new Error(`Unknown PROFILE "${PROFILE_NAME}". Use smoke, burst or peak.`);
}
const SCANS = parseInt(__ENV.SCANS || PROFILE.scans, 10);
const SECONDS = parseInt(__ENV.DURATION || PROFILE.seconds, 10);
// One scan every INTERVAL ms per factory -> exactly SCANS scans in SECONDS.
const INTERVAL_MS = Math.max(1, Math.floor((SECONDS * 1000) / SCANS));

const FACTORIES = [
  { scenario: 'binh_duong', code: 'BD' },
  { scenario: 'long_an', code: 'LA' },
];

function scenario(name) {
  return {
    executor: 'constant-arrival-rate',
    exec: 'shiftChange',
    rate: 1,
    timeUnit: `${INTERVAL_MS}ms`,
    duration: `${SECONDS}s`,
    preAllocatedVUs: 20,
    maxVUs: 200,
    tags: { factory: name },
  };
}

export const options = {
  setupTimeout: '10m',
  teardownTimeout: '5m',
  scenarios: Object.fromEntries(FACTORIES.map((f) => [f.scenario, scenario(f.scenario)])),
  thresholds: {
    // NFR 1: kiosk response time, measured on the kiosk's own request only.
    'http_req_duration{name:kiosk_scan}': ['p(95)<2000', 'p(99)<2000'],
    'http_req_duration{name:kiosk_scan,factory:binh_duong}': ['p(95)<2000'],
    'http_req_duration{name:kiosk_scan,factory:long_an}': ['p(95)<2000'],
    // NFR 2: every scan produced a stored record, and the server-side count agrees.
    scan_recorded: ['rate==1'],
    'checks{phase:teardown}': ['rate==1'],
    // The test itself kept up with the planned arrival rate.
    dropped_iterations: ['count==0'],
  },
  summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const scanRecorded = new Rate('scan_recorded');
const gateOpened = new Rate('gate_opened');
const outcomes = new Counter('scan_outcomes');

function json(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function uuidv4() {
  const b = new Uint8Array(crypto.randomBytes(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

function auth(token, name) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, tags: { name } };
}

function fail(message) {
  throw new Error(message);
}

// ---------------------------------------------------------------- setup
export function setup() {
  const login = http.post(`${BASE}/api/auth/login`,
    JSON.stringify({ username: 'admin', password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup' } });
  if (login.status !== 200) {
    fail(`Admin login failed (${login.status}). Is the backend running at ${BASE}?`);
  }
  const admin = json(login).token;
  // HR owns employee records, so the test's employees are registered by HR.
  const hrLogin = http.post(`${BASE}/api/auth/login`,
    JSON.stringify({ username: 'hr', password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup' } });
  if (hrLogin.status !== 200) {
    fail(`HR login failed (${hrLogin.status}).`);
  }
  const hr = json(hrLogin).token;

  const factories = json(http.get(`${BASE}/api/factories`, auth(admin, 'setup')));
  const kiosks = json(http.get(`${BASE}/api/kiosks`, auth(admin, 'setup')));
  const runId = Date.now().toString(36).toUpperCase();
  const startedAt = new Date(Date.now() - 5000).toISOString();

  if (TURNSTILE) {
    http.del(`${TURNSTILE}/stats`, null, { tags: { name: 'setup' } });
  }

  const plan = {};
  for (const f of FACTORIES) {
    const factory = factories.find((x) => x.code === f.code) || fail(`Factory ${f.code} not found`);
    const gateKiosk = kiosks.find((k) => k.factoryId === factory.id && / In\b/.test(k.name)
      && !/Out/.test(k.name) && !k.name.startsWith('[test]'))
      || fail(`No "In" kiosk for factory ${f.code}`);
    // Our own kiosk on the same IN gate: never rotates the secret of a kiosk someone set up for a demo.
    const created = http.post(`${BASE}/api/kiosks`,
      JSON.stringify({ gateId: gateKiosk.gateId, name: `[test] load ${runId} ${f.code}` }), auth(admin, 'setup'));
    if (created.status !== 201 && created.status !== 200) {
      fail(`Creating the test kiosk failed (${created.status}): ${created.body}`);
    }
    const kiosk = json(created);

    // Fresh employees: anti-passback would (correctly) deny a second check-in.
    const employees = [];
    for (let start = 0; start < SCANS; start += 25) {
      const batch = [];
      for (let i = start; i < Math.min(start + 25, SCANS); i++) {
        const code = `LT${runId}-${f.code}-${String(i + 1).padStart(4, '0')}`;
        batch.push(['POST', `${BASE}/api/employees`, JSON.stringify({
          employeeCode: code,
          fullName: `Load Test ${f.code} ${i + 1}`,
          factoryId: factory.id,
          department: 'Load Test',
          email: `${code.toLowerCase()}@loadtest.tmc.example`,
          phone: '0900000000',
        }), auth(hr, 'setup')]);
      }
      for (const res of http.batch(batch)) {
        if (res.status !== 200 && res.status !== 201) {
          fail(`Creating employee failed (${res.status}): ${res.body}`);
        }
        employees.push(json(res).id);
      }
    }

    plan[f.scenario] = {
      code: f.code, factoryId: factory.id, kioskId: kiosk.id, kioskName: kiosk.name,
      secret: kiosk.secret, employees,
    };
    console.log(`setup ${f.code}: ${employees.length} employees, kiosk "${kiosk.name}"`);
  }

  console.log(`run ${runId}: ${SCANS} scans/factory over ${SECONDS}s `
    + `(one every ${INTERVAL_MS} ms per factory), profile=${PROFILE_NAME}`);
  return { admin, runId, startedAt, plan };
}

// ---------------------------------------------------------------- one scan
export function shiftChange(data) {
  const p = data.plan[exec.scenario.name];
  const idx = exec.scenario.iterationInTest;
  if (idx >= p.employees.length) {
    return; // the arrival-rate executor may start one extra iteration at the very end
  }

  // 1. The employee's phone fetches a fresh 60-second QR (not part of the kiosk NFR).
  const qr = http.get(`${BASE}/api/employees/${p.employees[idx]}/qr`, auth(data.admin, 'mint_qr'));
  const token = qr.status === 200 ? json(qr).token : null;

  // 2. The kiosk signs the exact body with its HMAC secret and sends the scan.
  const body = JSON.stringify({ clientEventId: uuidv4(), token });
  const ts = Date.now().toString();
  const signature = crypto.hmac('sha256', p.secret, `${p.kioskId}\n${ts}\n${body}`, 'hex');
  const res = http.post(`${BASE}/api/kiosk/scans`, body, {
    headers: {
      'Content-Type': 'application/json',
      'X-Kiosk-Id': p.kioskId,
      'X-Kiosk-Timestamp': ts,
      'X-Kiosk-Signature': signature,
    },
    tags: { name: 'kiosk_scan' },
    timeout: '10s',
  });

  const r = json(res) || {};
  const recorded = res.status === 200 && !!r.recordId
    && (r.outcome === 'GRANTED' || (r.outcome === 'GATE_UNAVAILABLE' && r.manualFallback === true));

  scanRecorded.add(recorded);
  gateOpened.add(r.gateOpened === true);
  outcomes.add(1, { outcome: r.outcome || `HTTP_${res.status}` });

  check(res, {
    'scan answered under 2 s': (x) => x.timings.duration < 2000,
    'scan stored as a record': () => recorded,
  });
}

// ---------------------------------------------------------------- verification
export function teardown(data) {
  const tags = { phase: 'teardown' };
  let completed = 0;
  let fallbacks = 0;

  for (const p of Object.values(data.plan)) {
    const url = `${BASE}/api/attendance?from=${encodeURIComponent(data.startedAt)}&factoryId=${p.factoryId}`;
    const rows = (json(http.get(url, auth(data.admin, 'verify'))) || [])
      .filter((x) => x.employeeCode && x.employeeCode.startsWith(`LT${data.runId}-`));
    const unique = new Set(rows.map((x) => x.employeeId)).size;
    completed += rows.filter((x) => x.status === 'COMPLETED').length;
    fallbacks += rows.filter((x) => x.status === 'GATE_UNAVAILABLE').length;
    const sources = {};
    rows.forEach((x) => { sources[x.status] = (sources[x.status] || 0) + 1; });

    console.log(`verify ${p.code}: stored ${rows.length} / sent ${p.employees.length} `
      + `(unique employees ${unique}) by status ${JSON.stringify(sources)}`);
    check(rows, {
      [`${p.code}: every scan has a stored record`]: () => rows.length === p.employees.length,
      [`${p.code}: no duplicate records`]: () => unique === rows.length,
    }, tags);
  }

  const chain = json(http.get(`${BASE}/api/audit/verify`, auth(data.admin, 'verify'))) || {};
  console.log(`verify audit chain: valid=${chain.valid} entries=${chain.checked}`);
  check(chain, { 'audit hash chain still valid after the load': (c) => c.valid === true }, tags);

  if (TURNSTILE) {
    const stats = json(http.get(`${TURNSTILE}/stats`, { tags: { name: 'verify' } })) || {};
    console.log(`verify turnstile: gate opened ${stats.total} times, COMPLETED records ${completed}`);
    if (fallbacks === 0) {
      check(stats, { 'gate openings == COMPLETED records': (s) => s.total === completed }, tags);
    } else {
      // Slow-gate demo: the backend gave up after 1.5 s, the fake gate still counts late opens.
      console.log(`verify turnstile: ${fallbacks} scans fell back to the guard queue (expected in slow-gate mode)`);
    }
  }
}
