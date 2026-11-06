# SOLID in TMC Attendance: where, and how it is proven

Each principle has **concrete classes** you can open, and wherever possible an **automatic check** that
fails the build if the principle is broken. Paths are relative to `backend/src/main/java/com/tmc/`.

Run everything: `cd backend && ./mvnw verify`: 39 behaviour tests + 10 architecture rules in
`backend/src/test/java/com/tmc/architecture/ArchitectureTest.java` = **49**, all green.

---

## S: Single Responsibility (one reason to change)

| Class | Its one job | Why that matters |
|---|---|---|
| `attendance/core/ScanOrchestrator` | Coordinate one scan: verify → decide → save → open gate | It *calls* the specialists below, it doesn't do their work |
| `attendance/core/QrTokenVerifier` | Is this QR genuine, unexpired, current version? | QR rules change without touching the pipeline |
| `attendance/core/AccessPolicyChain` | Run the access rules, return the first "no" | The only place rules are executed |
| `attendance/core/AttendanceRecorder` | Persist records + outbox event in one transaction | Storage and durability live in one place |
| `attendance/core/ScanResultAssembler` | Turn a decision into the response the kiosk shows | Wording changes don't touch logic |
| `attendance/core/ManualEntryService` | A guard's manual entry (validate reason, then same rules) | Human fallback separate from the kiosk path |
| `identity/api/AccessScopeService` | **What each role may see** (whom, names, contact details) | Visibility rules decided once; no endpoint checks roles itself |
| `reporting/service/Occupancy` | The rule "who is inside" | Live map and evacuation list can never disagree |
| `reporting/service/LiveSiteService` · `EvacuationService` · `ManualEntryReviewService` · `ReportingService` | Live map · fire drill list · HR review of manual entries · monthly report | Four screens, four reasons to change, four classes |
| `security/service/LoginService` | Check credentials, issue a token | Web layer stays a thin adapter |
| `*Controller` classes | Translate HTTP ↔ service call, nothing else | Rule `services_are_not_in_web_packages`, `controllers_never_touch_repositories` |

**Check it yourself:** search the backend for role branching. Apart from the demo-data seeder, the
only place that decides by role is `AccessScopeService`:
`grep -rn "Role\.\(ADMIN\|HR\|EXECUTIVE\)" backend/src/main/java | grep -v DataSeeder` → no results.

---

## O: Open/Closed (add behaviour by adding code, not editing it)

| Extension point | To add something new you… | Proof |
|---|---|---|
| **Access rules** (`AccessPolicy`) | write one new `@Component` class (e.g. "no entry on public holidays"). `AccessPolicyChain` picks it up in `@Order`; nobody else changes | ArchUnit `callers_never_depend_on_concrete_access_rules`: no caller may name a concrete rule. The **guard's manual entry reused all existing rules without editing any of them** |
| **Turnstile vendors** (`TurnstilePort`) | add an adapter class + one `case` in `integration/turnstile/TurnstileConfig` | `Stub`, `Http`, `Chaos` adapters exist side by side |
| **Payroll systems** (`reporting/api/PayrollPort`) | add an adapter in `integration/payroll` | `StubPayrollAdapter` today |
| **Visibility per role** (`AccessScope`) | add a case in `AccessScopeService` | executive "counts only" and supervisor "own team" were added without touching endpoints' logic |

---

## L: Liskov Substitution (any implementation works where the interface is expected)

| Interface | Interchangeable implementations | Proof |
|---|---|---|
| `TurnstilePort` | `StubTurnstileAdapter`, `HttpTurnstileAdapter`, `ChaosTurnstileAdapter`, and `ResilientTurnstileAdapter` **wrapping any of them** (decorator) | The scan pipeline is unchanged between `stub`, `http` and `chaos` modes. The load test runs on `http`, the demo on `stub`, the failure demo on `chaos` |
| `DirectionPolicy` | `EntryDirectionPolicy`, `ExitDirectionPolicy`, `BidirectionalPolicy` | `AntiPassbackPolicyTest`, `ScanOrchestratorTest` |
| `AccessPoint` (kiosk **or** guard at a gate) | `AccessPoint.kiosk(...)`, `AccessPoint.guard(...)` | Every `AccessPolicy` judges a guard's manual entry exactly like a kiosk scan: `ManualEntryServiceTest` (wrong factory → denied), `AttendanceFlowIT.guardManualEntry…` (anti-passback → denied) |

> Design note: an earlier version faked a kiosk with `id = null` so a manual entry could pass through
> the rules: a classic Liskov smell. It was replaced by `AccessPoint`, which carries only what rules need.

---

## I: Interface Segregation (clients depend only on what they use)

| Narrow interface | Who uses it | Instead of… |
|---|---|---|
| `gate/api/KioskLookup`, `security/api/KioskAuthenticator`, `gate/api/GateStatusLookup` | scan pipeline · security filter · live map | one fat `KioskService`. `KioskService` implements all three, and each client sees only its own slice |
| `identity/api/EmployeeLookup` (returns `EmployeeView`, no email/phone) | attendance, reporting, gate | the full employee entity with personal data (privacy by design) |
| `attendance/api/AttendanceReportingPort` | reporting module | the attendance repository |
| `attendance/core/AccessPoint` | every `AccessPolicy` | a whole `KioskView`: rules need gate, factory and direction only |

**Proof:** ArchUnit `lookups_are_interfaces_published_in_api_packages`.

---

## D: Dependency Inversion (depend on abstractions, wired at the edge)

- **Domain → ports, adapters at the edge:** the scan pipeline depends on `TurnstilePort`; reporting on
  `PayrollPort`. The concrete adapters live in `integration/` and are chosen in configuration.
  ArchUnit `domain_modules_never_reach_into_integration`, `only_integration_calls_http_clients`.
- **Modules talk through published ports, never each other's tables:**
  ArchUnit `reporting_reads_other_modules_only_through_their_ports`,
  `attendance_reads_other_modules_only_through_their_ports`, `ports_live_in_api_packages`.
- **Inverted direction between modules:** `security` defines `KioskAuthenticator`; `gate` implements it.
  Security never depends on the gate module.
- **Constructor injection everywhere**, so every service is unit-tested with fakes
  (e.g. `ScanOrchestratorTest` proves *save before open gate* with Mockito `InOrder`).

---

## The rules are real: they fail when broken

We deliberately broke two rules and ran `./mvnw test -Dtest=ArchitectureTest`:

| Deliberate violation | Rule that failed the build |
|---|---|
| `ScanOrchestrator` holds a `new EmployeeAccessPolicy()` | `callers_never_depend_on_concrete_access_rules` (OCP) |
| `EvacuationService` gets an `AttendanceRecordRepository` field | `reporting_reads_other_modules_only_through_their_ports` (DIP) |

After reverting, the build was green again.

---

## Where we bend the principles (on purpose)

Being honest about trade-offs is part of the architecture:

| Where | What | Why it's acceptable |
|---|---|---|
| `ScanOrchestrator` | 12 collaborators in the constructor | It is the *coordinator* of the most important flow; each step is delegated. Splitting further would hide the order "save before open", which is the key design decision |
| `admin/seed/DataSeeder` | Writes other modules' repositories directly | Demo data only; switched off with `TMC_SEED_ENABLED=false`; not used by any business flow |
| `attendance/web/AttendanceQueryService` | A read service inside the web package | Read-only projection for the web layer; no business rules |
| Portal (`portal/src/roles.ts`) | The menu repeats the server's role rules | It only *mirrors* them for usability; the server enforces them. `roles.ts` is the single place in the frontend |
