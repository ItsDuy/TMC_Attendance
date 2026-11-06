#!/usr/bin/env bash
# ONE command for the load-test showcase. Starts everything, runs the test, builds the report,
# then stops what it started (the database is left running).
#
#   perf/showcase.sh             # burst: 800 scans/factory in 2 min, both factories at once (~5 min)
#   perf/showcase.sh smoke       # quick sanity run (~2 min, most of it is the backend starting)
#   perf/showcase.sh peak        # the exact NFR: 800 scans/factory over 30 min
#
#   GATE_DELAY_MS=2500 perf/showcase.sh smoke   # gate slower than the 1.5 s timeout:
#                                               # scans still answer < 2 s and go to the guard queue
#
# Ports busy? SERVER_PORT=18080 POSTGRES_PORT=55432 perf/showcase.sh
set -euo pipefail

PROFILE="${1:-burst}"
SERVER_PORT="${SERVER_PORT:-8080}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
TURNSTILE_PORT="${TURNSTILE_PORT:-9099}"
GATE_DELAY_MS="${GATE_DELAY_MS:-300}"

PERF_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$PERF_DIR")"
LOGS="$PERF_DIR/results/logs"
mkdir -p "$LOGS"

say() { printf '\n\033[1m▶ %s\033[0m\n' "$*"; }
die() { printf '\n✗ %s\n' "$*"; exit 1; }
port_busy() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

TURNSTILE_PID=""
cleanup() {
  say "Cleaning up"
  if [ -n "$TURNSTILE_PID" ]; then kill "$TURNSTILE_PID" 2>/dev/null || true; fi
  # spring-boot:run forks a JVM; stop whatever listens on our backend port.
  if [ "${STARTED_BACKEND:-0}" = 1 ]; then
    lsof -nP -t -iTCP:"$SERVER_PORT" -sTCP:LISTEN 2>/dev/null | xargs kill 2>/dev/null || true
    [ -n "${MVN_PID:-}" ] && kill "$MVN_PID" 2>/dev/null || true
  fi
  echo "backend and fake turnstile stopped (database left running: 'docker compose stop db' to stop it)"
}
trap cleanup EXIT

say "0/4 Checking tools and ports"
command -v docker >/dev/null || die "Docker is not installed."
docker info >/dev/null 2>&1 || die "Docker is not running. Start Docker Desktop and retry."
command -v java >/dev/null || die "Java 21+ is not installed."
command -v python3 >/dev/null || die "python3 is not installed."
port_busy "$SERVER_PORT" && die "Port $SERVER_PORT is busy. Stop that app, or run: SERVER_PORT=18080 $0 $PROFILE"
port_busy "$TURNSTILE_PORT" && die "Port $TURNSTILE_PORT is busy. Run with TURNSTILE_PORT=19099."
echo "✓ docker, java, python3 · ports $SERVER_PORT and $TURNSTILE_PORT are free"

say "1/4 Database (PostgreSQL on :$POSTGRES_PORT)"
(cd "$ROOT" && POSTGRES_PORT="$POSTGRES_PORT" docker compose up -d --wait db) \
  || die "Database did not start. Is port $POSTGRES_PORT taken? Try POSTGRES_PORT=55432 $0 $PROFILE"

say "2/4 Fake turnstile on :$TURNSTILE_PORT (gate delay ${GATE_DELAY_MS} ms)"
python3 "$PERF_DIR/fake-turnstile.py" --port "$TURNSTILE_PORT" --delay-ms "$GATE_DELAY_MS" \
  > "$LOGS/turnstile.log" 2>&1 &
TURNSTILE_PID=$!
disown "$TURNSTILE_PID" 2>/dev/null || true
sleep 1
curl -fsS "http://localhost:$TURNSTILE_PORT/health" >/dev/null || die "Fake turnstile failed, see $LOGS/turnstile.log"
echo "✓ turnstile up"

say "3/4 Backend on :$SERVER_PORT, talking to the turnstile over HTTP (first run downloads Maven deps)"
(
  cd "$ROOT/backend"
  SERVER_PORT="$SERVER_PORT" \
  SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$POSTGRES_PORT/tmc" \
  TMC_TURNSTILE_MODE=http \
  TMC_TURNSTILE_URL="http://localhost:$TURNSTILE_PORT" \
  exec ./mvnw -q spring-boot:run
) > "$LOGS/backend.log" 2>&1 &
MVN_PID=$!
STARTED_BACKEND=1
for i in $(seq 1 300); do
  if curl -fsS -m 2 "http://localhost:$SERVER_PORT/actuator/health" >/dev/null 2>&1; then break; fi
  kill -0 "$MVN_PID" 2>/dev/null || die "Backend exited during start-up. Last lines of $LOGS/backend.log:
$(tail -20 "$LOGS/backend.log")"
  [ $((i % 15)) -eq 0 ] && echo "  …still starting (${i}s)"
  sleep 1
done
curl -fsS -m 2 "http://localhost:$SERVER_PORT/actuator/health" >/dev/null || die "Backend not healthy after 300 s."
echo "✓ backend up"

say "4/4 Load test"
set +e
BASE_URL="http://localhost:$SERVER_PORT" \
TURNSTILE_URL="http://localhost:$TURNSTILE_PORT" \
GATE_DELAY_MS="$GATE_DELAY_MS" \
TURNSTILE_MODE_LABEL="http → fake turnstile (${GATE_DELAY_MS} ms)" \
  "$PERF_DIR/run-load-test.sh" "$PROFILE"
STATUS=$?
set -e
exit "$STATUS"
