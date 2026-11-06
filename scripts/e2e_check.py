#!/usr/bin/env python3
"""End-to-end check of the RUNNING app, one story per actor. Standard library only.

    python3 scripts/e2e_check.py                          # normal mode, ~1.5 min (waits for a QR to expire)
    python3 scripts/e2e_check.py --base-url http://localhost:18080
    python3 scripts/e2e_check.py --chaos                  # backend started with TMC_TURNSTILE_MODE=chaos

Normal mode: 55 checks. Chaos mode: 6 checks (broken gate -> record kept -> guard resolves).
Creates fresh employees on every run, so it can be re-run any time.
"""
import argparse
import datetime
import hashlib
import hmac
import json
import sys
import time
import urllib.error
import urllib.request
import uuid

PASSWORD = "Password@123"
BASE = "http://localhost:8080"
results = []


def call(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            txt = r.read().decode()
            return r.status, json.loads(txt) if txt else None
    except urllib.error.HTTPError as e:
        txt = e.read().decode()
        try:
            return e.code, json.loads(txt)
        except ValueError:
            return e.code, txt


def kiosk(kid, secret, path, body):
    """Sign exactly like a real kiosk: HMAC-SHA256 over id \\n timestamp \\n body."""
    raw = json.dumps(body, separators=(",", ":"))
    ts = str(int(time.time() * 1000))
    sig = hmac.new(secret.encode(), f"{kid}\n{ts}\n{raw}".encode(), hashlib.sha256).hexdigest()
    req = urllib.request.Request(BASE + path, data=raw.encode(), method="POST")
    for k, v in {"Content-Type": "application/json", "X-Kiosk-Id": kid,
                 "X-Kiosk-Timestamp": ts, "X-Kiosk-Signature": sig}.items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, {"raw": e.read().decode()}


def check(actor, story, ok, detail=""):
    results.append(bool(ok))
    print(f"{'PASS' if ok else 'FAIL'}  [{actor:10}] {story}" + ("" if ok else f"   -> {detail}"))


def login(user):
    s, b = call("POST", "/api/auth/login", body={"username": user, "password": PASSWORD})
    check(user, "can sign in", s == 200, b)
    if s != 200:
        sys.exit(f"Cannot sign in as {user}. Is the backend running at {BASE}?")
    return b["token"]


def prepare(admin):
    """Register a throwaway '[test]' kiosk on an IN gate and sign as it.

    It never rotates the real kiosks' secrets, so a browser you set up for the
    demo keeps working after this script runs.
    """
    _, kiosks = call("GET", "/api/kiosks", admin)
    gate = next(x for x in kiosks if " In" in x["name"] and "Out" not in x["name"]
                and not x["name"].startswith("[test]"))
    s, created = call("POST", "/api/kiosks", admin,
                      {"gateId": gate["gateId"], "name": f"[test] e2e {uuid.uuid4().hex[:6]}"})
    return created, created, s


def new_employee(hr, factory_id, prefix="E2E"):
    """HR owns employee records, so only HR can register people."""
    code = prefix + uuid.uuid4().hex[:6].upper()
    return call("POST", "/api/employees", hr, {
        "employeeCode": code, "fullName": f"E2E {code}", "email": f"{code.lower()}@e2e.tmc.example",
        "phone": "0900000000", "department": "Assembly", "factoryId": factory_id})


def normal_mode():
    admin = login("admin")
    hr = login("hr")
    k, rotated, s = prepare(admin)
    check("admin", "register a kiosk on a gate (gate station mgmt)", s in (200, 201) and rotated.get("secret"), rotated)
    kid, secret, fid = k["id"], rotated["secret"], k["factoryId"]

    s, emps = call("GET", "/api/employees", admin)
    check("admin", "employee list for the demo tool: code + name, no email/phone",
          s == 200 and len(emps) > 0 and all(not e.get("email") and not e.get("phone") for e in emps), emps[:1])
    s, _ = new_employee(admin, fid)
    check("admin", "cannot register employees (HR owns records) -> 403", s == 403, s)
    s, emp = new_employee(hr, fid)
    check("hr", "register a new employee", s in (200, 201), emp)

    s, qr = call("GET", f"/api/employees/{emp['id']}/qr", admin)
    check("admin", "mint a dynamic QR (valid 60 s)", s == 200 and qr["token"].startswith("TMC1."), qr)
    tok = qr["token"]

    eid = str(uuid.uuid4())
    s, r = kiosk(kid, secret, "/api/kiosk/scans", {"clientEventId": eid, "token": tok})
    check("employee", "scan at gate -> GRANTED, gate opens",
          r.get("outcome") == "GRANTED" and r.get("gateOpened"), r)
    s, r2 = kiosk(kid, secret, "/api/kiosk/scans", {"clientEventId": eid, "token": tok})
    check("kiosk", "retry same event -> DUPLICATE, no 2nd record",
          r2.get("outcome") == "DUPLICATE" and r2.get("recordId") == r.get("recordId"), r2)
    s, r3 = kiosk(kid, secret, "/api/kiosk/scans", {"clientEventId": str(uuid.uuid4()), "token": tok})
    check("employee", "scan IN twice (buddy punch) -> DENIED", r3.get("outcome") == "DENIED", r3)

    bad = tok[:-3] + ("AAA" if not tok.endswith("AAA") else "BBB")
    s, r4 = kiosk(kid, secret, "/api/kiosk/scans", {"clientEventId": str(uuid.uuid4()), "token": bad})
    check("attacker", "edited QR -> INVALID_TOKEN", r4.get("outcome") == "INVALID_TOKEN", r4)
    s, _ = kiosk(kid, "wrong-secret", "/api/kiosk/scans", {"clientEventId": str(uuid.uuid4()), "token": tok})
    check("attacker", "fake kiosk (wrong secret) -> 401", s == 401, s)

    _, emp2 = new_employee(hr, fid)
    _, qr2 = call("GET", f"/api/employees/{emp2['id']}/qr", admin)
    scanned_at = datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z")
    s, sync = kiosk(kid, secret, "/api/kiosk/scans/sync", {"events": [
        {"clientEventId": str(uuid.uuid4()), "token": qr2["token"], "scannedAt": scanned_at}]})
    check("kiosk", "offline buffer + sync -> GRANTED, record kept",
          s == 200 and sync["results"][0]["outcome"] == "GRANTED", (s, sync))

    # Rotate QR (HR): every QR already shown stops working at once, even inside its 60 s.
    _, emp4 = new_employee(hr, fid)
    _, qr4 = call("GET", f"/api/employees/{emp4['id']}/qr", admin)
    s, _ = call("POST", f"/api/employees/{emp4['id']}/qr/rotate", admin)
    check("admin", "cannot rotate QR (HR owns records) -> 403", s == 403, s)
    s, _ = call("POST", f"/api/employees/{emp4['id']}/qr/rotate", hr)
    check("hr", "rotate a leaked QR", s == 200, s)
    s, r7 = kiosk(kid, secret, "/api/kiosk/scans", {"clientEventId": str(uuid.uuid4()), "token": qr4["token"]})
    check("attacker", "QR shown before the rotate -> DENIED", r7.get("outcome") == "DENIED", r7)

    emp_tok = login("employee")
    s, me = call("GET", "/api/me/qr", emp_tok)
    check("employee", "get my own QR on my phone", s == 200 and me["token"].startswith("TMC1."), me)
    s, _ = call("GET", "/api/employees", emp_tok)
    check("employee", "cannot see other employees (RBAC) -> 403", s == 403, s)
    period = datetime.date.today().strftime("%Y-%m")
    s, _ = call("GET", f"/api/reports/attendance?period={period}&factoryId={fid}", emp_tok)
    check("employee", "cannot open reports (RBAC) -> 403", s == 403, s)
    s, mine = call("GET", f"/api/me/attendance?period={period}", emp_tok)
    check("employee", "My attendance: only my own scans",
          s == 200 and all(r.get("employeeCode") == "BD001" for r in mine), (s, mine[:1] if isinstance(mine, list) else mine))

    sup = login("supervisor")   # BD002, team = Binh Duong · Quality
    team = {"BD002", "BD005", "BD008"}
    s, rows = call("GET", "/api/attendance", sup)
    check("supervisor", "today's scans: own team only",
          s == 200 and all(r.get("employeeCode") in team for r in rows), (s, [r.get("employeeCode") for r in rows][:5]))
    s, people = call("GET", "/api/employees", sup)
    check("supervisor", "employee list: own team only",
          s == 200 and {p["employeeCode"] for p in people} <= team and "BD002" in {p["employeeCode"] for p in people},
          [p.get("employeeCode") for p in people] if isinstance(people, list) else people)
    s, _ = call("GET", "/api/attendance/gate-unavailable", sup)
    check("supervisor", "gate recovery queue is the guard's job -> 403", s == 403, s)
    s, _ = call("GET", "/api/kiosks", sup)
    check("supervisor", "cannot manage kiosks (RBAC) -> 403", s == 403, s)

    guard = login("guard")
    s, _ = call("GET", "/api/attendance/gate-unavailable", guard)
    check("guard", "see the recovery queue", s == 200, s)
    s, _ = call("POST", f"/api/gates/{k['gateId']}/open", admin)
    check("admin", "cannot open a gate by hand (guard's job) -> 403", s == 403, s)

    # Human fallback: phone dead -> the guard checks the ID card and records the entry by hand.
    _, emp5 = new_employee(hr, fid)
    s, chk = call("GET", f"/api/attendance/manual/check?code={emp5['employeeCode'].lower()}", guard)
    check("guard", "look up an employee by ID-card code (case-insensitive)",
          s == 200 and chk.get("fullName") == emp5["fullName"] and chk.get("inside") is False, chk)
    entry = {"employeeCode": emp5["employeeCode"], "gateId": k["gateId"], "reason": "PHONE_UNAVAILABLE",
             "note": "phone battery dead, checked ID card"}
    s, man = call("POST", "/api/attendance/manual", guard, entry)
    check("guard", "manual check-in (phone dead) -> GRANTED, marked as recorded by guard",
          s == 200 and man.get("outcome") == "GRANTED" and "guard" in man.get("message", ""), man)
    s, again = call("POST", "/api/attendance/manual", guard, entry)
    check("guard", "same manual check-in again -> DENIED (anti-passback applies)", again.get("outcome") == "DENIED", again)
    s, _ = call("POST", "/api/attendance/manual", guard, {**entry, "reason": "OTHER", "note": ""})
    check("guard", "reason OTHER without a note -> refused", s == 400, s)
    for who, tok in (("admin", admin), ("supervisor", sup)):
        s, _ = call("POST", "/api/attendance/manual", tok, entry)
        check(who, "manual entry is the guard's job -> 403", s == 403, s)
    s, evac = call("GET", "/api/live/evacuation", guard)
    check("guard", "evacuation list includes the person let in by hand",
          s == 200 and any(p["employeeCode"] == emp5["employeeCode"] for site in evac["sites"] for p in site["people"]),
          [site["inside"] for site in evac.get("sites", [])] if isinstance(evac, dict) else evac)

    s, _ = call("GET", f"/api/reports/attendance?period={period}&factoryId={fid}", hr)
    check("hr", "monthly attendance report", s == 200, s)
    s, log = call("GET", f"/api/reports/manual-entries?period={period}", hr)
    check("hr", "manual entries to review: who, why and which guard",
          s == 200 and any(m["employeeCode"] == emp5["employeeCode"] and m["enteredBy"] == "guard"
                           and m["reason"] == "PHONE_UNAVAILABLE" for m in log), log[:1] if isinstance(log, list) else log)
    s1, b1 = call("POST", "/api/reports/exports", hr, {"period": period, "factoryId": fid})
    s2, b2 = call("POST", "/api/reports/exports", hr, {"period": period, "factoryId": fid})
    check("hr", "export to HR/Payroll", s1 in (200, 201), b1)
    check("hr", "export again -> same batch (idempotent)",
          isinstance(b1, dict) and isinstance(b2, dict) and b1.get("id") == b2.get("id"), (b1, b2))

    ex = login("executive")
    s, summary = call("GET", f"/api/reports/summary?period={period}", ex)
    check("executive", "company summary per factory, no names",
          s == 200 and len(summary) == 2 and "employeeName" not in json.dumps(summary), summary)
    s, _ = call("GET", f"/api/reports/attendance?period={period}&factoryId={fid}", ex)
    check("executive", "named per-employee report -> 403", s == 403, s)
    s, _ = call("GET", "/api/attendance", ex)
    check("executive", "named scan list -> 403", s == 403, s)
    s, _ = call("GET", "/api/live/evacuation", ex)
    check("executive", "evacuation list has names -> 403", s == 403, s)
    s, _ = call("POST", "/api/reports/exports", ex, {"period": period, "factoryId": fid})
    check("executive", "cannot trigger export (RBAC) -> 403", s == 403, s)

    # Who am I? Every login must say which person it is, and nobody shares a person.
    expected = {"bd001": ("BD001", "Nguyen Van An"), "employee": ("BD001", "Nguyen Van An"),
                "supervisor": ("BD002", "Tran Thi Binh"), "hr": ("BD003", "Le Quang Cuong"),
                "la004": ("LA004", "Ly Thi Phuong")}
    for username, (code, name) in expected.items():
        st, b = call("POST", "/api/auth/login", body={"username": username, "password": PASSWORD})
        st, me = call("GET", "/api/me", b.get("token") if isinstance(b, dict) else None)
        check(username, f"/api/me says {name} ({code})",
              st == 200 and me.get("employeeCode") == code and me.get("fullName") == name, me)
    st, me = call("GET", "/api/me", guard)
    check("guard", "/api/me: staff account, no employee record", st == 200 and me.get("employeeCode") is None, me)

    s, v = call("GET", "/api/audit/verify", admin)
    check("admin", "audit hash chain is valid", s == 200 and v.get("valid") is True, v)
    s, _ = call("GET", "/api/attendance")
    check("anonymous", "no login -> 401", s == 401, s)

    print("… waiting 62 s so a fresh QR expires (the static-QR fix)")
    _, emp3 = new_employee(hr, fid)
    _, qr3 = call("GET", f"/api/employees/{emp3['id']}/qr", admin)
    time.sleep(62)
    s, r6 = kiosk(kid, secret, "/api/kiosk/scans", {"clientEventId": str(uuid.uuid4()), "token": qr3["token"]})
    check("attacker", "screenshot of a QR after 60 s -> EXPIRED", r6.get("outcome") == "EXPIRED", r6)


def chaos_mode():
    admin = login("admin")
    hr = login("hr")
    k, rotated, _ = prepare(admin)
    _, emp = new_employee(hr, k["factoryId"], "CHAOS")
    _, qr = call("GET", f"/api/employees/{emp['id']}/qr", admin)

    t0 = time.time()
    _, r = kiosk(k["id"], rotated["secret"], "/api/kiosk/scans",
                 {"clientEventId": str(uuid.uuid4()), "token": qr["token"]})
    took = time.time() - t0
    check("employee", f"gate down -> GATE_UNAVAILABLE + manualFallback in {took:.2f}s (< 2 s)",
          r.get("outcome") == "GATE_UNAVAILABLE" and r.get("manualFallback") is True and took < 2, r)

    guard = login("guard")
    _, queue = call("GET", "/api/attendance/gate-unavailable", guard)
    check("guard", "the scan is in the recovery queue (not lost)",
          any(x.get("id") == r.get("recordId") for x in queue), r)
    s, res = call("POST", f"/api/gates/records/{r.get('recordId')}/resolve", guard)
    check("guard", "mark 'Admitted manually'", s == 200, (s, res))
    s, v = call("GET", "/api/audit/verify", admin)
    check("admin", "audit chain still valid", v.get("valid") is True, v)


def main():
    global BASE
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--base-url", default=BASE)
    p.add_argument("--chaos", action="store_true", help="backend runs with TMC_TURNSTILE_MODE=chaos")
    a = p.parse_args()
    BASE = a.base_url.rstrip("/")
    (chaos_mode if a.chaos else normal_mode)()
    passed = sum(results)
    print(f"\n{'✅' if passed == len(results) else '❌'} {passed}/{len(results)} checks passed")
    sys.exit(0 if passed == len(results) else 1)


if __name__ == "__main__":
    main()
