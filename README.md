# TMC_Attendance

QR-based attendance and access control for **TMC Manufacturing JSC** (Binh Duong and Long An
factories). This repository is the M2 implementation of the architecture described in the course
report: a modular monolith with an internal event-driven core, edge kiosk buffering, dynamic QR
credentials and a tamper-evident audit trail.

![Kiosk simulator](docs/screenshots/kiosk-simulator.png)

## What it demonstrates

| Concern | How it is implemented |
|---|---|
| Record before unlock | `ScanOrchestrator` persists a `PENDING` row, then calls the turnstile, then marks it `COMPLETED`. A crashed gate can never lose a scan. |
| No duplicates | A kiosk-generated `clientEventId` is the primary key of `processed_events`, so a retry returns the original result instead of opening the gate twice. |
| Transactional outbox | `attendance_records` and `outbox_events` are written in one transaction; `OutboxRelay` delivers later. |
| Network loss | The kiosk buffers scans locally and replays them through `POST /api/kiosk/scans/sync`, keeping the **original** scan time. |
| Static QR exposure | Tokens are HMAC-signed, contain no PII, expire in 60 s, and are bound to a credential version that can be rotated or revoked. |
| Buddy punching | Anti-passback: a repeated scan in the same direction inside the cooldown is rejected; an `OUT` without an `IN` is rejected. |
| Gate failure | 1.5 s timeout + circuit breaker; the record lands in a recovery queue and a guard can open the gate manually. |
| HR deadline | Export runs at 06:00 on the 1st, is idempotent per `period + factory`, and records a checksum. |
| RBAC + audit | Six roles, JWT for staff and per-kiosk HMAC for devices; every sensitive action appends to a hash-chained audit log. |
| Privacy | QR payload carries only `employeeId|jti|exp|version`; phone numbers and kiosk secrets are AES-GCM encrypted at rest. |

## Architecture at a glance

```
portal (React)  ──JWT──▶  ┌──────────────────── modular monolith ────────────────────┐
                          │ identity   gate   attendance   reporting   admin        │
kiosk ──HMAC─────────────▶│   │         │         │            │          │          │
                          │   └── api ──┴── api ──┘            │          │          │
                          │              security (JWT, RBAC, HMAC, audit)           │
                          │              integration (turnstile, payroll, outbox)    │
                          └───────────────────────┬─────────────────────────────────┘
                                                  ▼
                                        PostgreSQL 16 (partitioned)
```

Full description: [docs/architecture.md](docs/architecture.md).
API reference: [docs/api.md](docs/api.md) or Swagger UI at `/swagger-ui.html`.

## Quick start

Requirements: Docker, JDK 21, Node 18+.

```bash
# 1. Database
docker compose up -d db

# 2. Backend  (http://localhost:8080)
cd backend
./mvnw spring-boot:run

# 3. Portal   (http://localhost:5173)
cd ../portal
npm install
npm run dev
```

Flyway creates the schema and `DataSeeder` inserts demo data on the first run. The startup log
prints the **kiosk id and secret** pairs — copy them into the kiosk simulator page.

Demo accounts (password `Password@123`):

| Username | Role |
|---|---|
| `admin` | Administrator |
| `hr` | HR staff |
| `supervisor` | Production supervisor |
| `guard` | Security guard |
| `executive` | Executive management |
| `employee` | Employee (linked to BD001) |

### Optional: run the backend in a container

```bash
docker compose --profile app up --build
```

## Demo script

1. Sign in as `admin` → **Administration** → copy a kiosk id, click **Rotate secret**.
2. Go to **Kiosk simulator**, paste the id and secret, click **Remember on this device**.
3. Pick an employee → **Mint QR** (note the 60-second countdown) → **Scan at gate**.
   → `GRANTED`, gate opened, record `COMPLETED`.
4. Scan the same token again → the anti-passback policy returns `DENIED`.
5. Wait for the countdown to reach zero and scan → `EXPIRED`. This is the static-QR fix.
6. Enable **Offline mode**, scan twice, then **Sync** → records appear with `source = OFFLINE`
   and their original timestamps.
7. Restart the backend with `TMC_TURNSTILE_MODE=chaos ./mvnw spring-boot:run` and scan.
   → `GATE_UNAVAILABLE` with `manualFallback: true`; the record survives.
8. Sign in as `guard` → **Live attendance** → **Open gate** on the recovery row.
9. Sign in as `hr` → **Reports & export** → **Export to HR/Payroll**, then press it again:
   the same batch is returned (idempotent).
10. As `admin` → **Administration** → **Verify hash chain** → `valid`.

## Tests

```bash
cd backend
./mvnw verify        # unit + Testcontainers integration + ArchUnit rules
```

30 tests:

- `QrTokenServiceTest` — expiry, tampering, malformed input, nonce uniqueness.
- `AntiPassbackPolicyTest` — the IN/OUT state matrix.
- `ScanOrchestratorTest` — Mockito `InOrder` proves persist → actuate → complete; idempotent replay; offline replay.
- `AttendanceFlowIT` — Testcontainers PostgreSQL: end-to-end scan, duplicate suppression, offline timestamps, audit chain, unauthenticated access.
- `ArchitectureTest` — ArchUnit rules for module boundaries (controllers never touch repositories, domain never imports adapters, only `integration` speaks HTTP).

## Load test

```bash
k6 run perf/load-test.js                      # 800 scans over 30 minutes
k6 run -e RATE=15 -e DURATION=2m perf/load-test.js   # quick burst
```

The threshold asserts `p95 < 2000 ms` on the kiosk scan endpoint.

## Repository layout

```
backend/    Spring Boot 3.5 · Java 21 · PostgreSQL · Flyway · Resilience4j
  com.tmc.identity     employees, QR credentials, token minting
  com.tmc.gate         gates, kiosks, provisioning bundles
  com.tmc.attendance   scan pipeline (the core)
  com.tmc.reporting    monthly report, HR export
  com.tmc.admin        config, seeding, partition maintenance
  com.tmc.security     JWT, RBAC, kiosk HMAC, audit chain
  com.tmc.integration  turnstile + payroll adapters, outbox relay
  com.tmc.shared       outbox, idempotency, crypto, errors
portal/     React + Vite + TypeScript (kiosk simulator, supervisor, reports, admin)
docs/       architecture and API notes
perf/       k6 load test
```

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `TMC_TURNSTILE_MODE` | `stub` | `stub`, `http` or `chaos` |
| `TMC_TURNSTILE_URL` | `http://localhost:9099` | Vendor turnstile API base URL |
| `TMC_SEED_ENABLED` | `true` | Insert demo data on an empty database |
| `TMC_SECURITY_JWT_SECRET` | demo value | HS256 signing key (base64, 32+ bytes) |
| `TMC_SECURITY_QR_SECRET` | demo value | Root key for per-employee QR derivation |
| `TMC_SECURITY_DATA_KEY` | demo value | AES-GCM key for PII and kiosk secrets |

> The defaults are development values. Supply real secrets through the environment in any
> shared deployment.
