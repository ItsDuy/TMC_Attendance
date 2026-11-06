#!/usr/bin/env bash
# Run the k6 load test against an ALREADY RUNNING backend and build an HTML report.
#
#   perf/run-load-test.sh            # burst profile (default)
#   perf/run-load-test.sh smoke      # 20 scans/factory, ~1 min
#   perf/run-load-test.sh peak       # 800 scans/factory over 30 min (the exact NFR)
#
# Env: BASE_URL (default http://localhost:8080)
#      TURNSTILE_URL (default http://localhost:9099; checked only if it answers)
# Uses a local `k6` if installed, otherwise the grafana/k6 Docker image. No install needed.
set -euo pipefail

PROFILE="${1:-burst}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
TURNSTILE_URL="${TURNSTILE_URL:-http://localhost:9099}"
PERF_DIR="$(cd "$(dirname "$0")" && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$PERF_DIR/results/$STAMP-$PROFILE"
mkdir -p "$OUT"

say() { printf '\n\033[1m▶ %s\033[0m\n' "$*"; }

say "Checking the backend at $BASE_URL"
if ! curl -fsS -m 5 "$BASE_URL/actuator/health" >/dev/null; then
  echo "✗ Backend not reachable at $BASE_URL/actuator/health."
  echo "  Start it first (see README), or run perf/showcase.sh which starts everything."
  exit 1
fi
echo "✓ backend is up"

if curl -fsS -m 3 "$TURNSTILE_URL/health" >/dev/null 2>&1; then
  echo "✓ fake turnstile answers at $TURNSTILE_URL (gate openings will be cross-checked)"
else
  echo "• no fake turnstile at $TURNSTILE_URL (skipping the gate-opening cross-check)"
  TURNSTILE_URL=""
fi

cat > "$OUT/run.env" <<EOF
profile=$PROFILE
base_url=$BASE_URL
turnstile_url=$TURNSTILE_URL
gate_delay_ms=${GATE_DELAY_MS:-unknown}
turnstile_mode=${TURNSTILE_MODE_LABEL:-unknown}
started=$(date '+%Y-%m-%d %H:%M:%S %Z')
host=$(uname -sm)
EOF

say "Running k6 (profile: $PROFILE). Results → ${OUT#$PERF_DIR/}"
set +e
if command -v k6 >/dev/null 2>&1; then
  k6 run --summary-mode=full \
    -e PROFILE="$PROFILE" -e BASE_URL="$BASE_URL" -e TURNSTILE_URL="$TURNSTILE_URL" \
    ${SCANS:+-e SCANS="$SCANS"} ${DURATION:+-e DURATION="$DURATION"} \
    --out csv="$OUT/raw.csv" --summary-export="$OUT/summary.json" \
    "$PERF_DIR/load-test.js" 2>&1 | tee "$OUT/console.log"
  STATUS=${PIPESTATUS[0]}
else
  echo "(k6 not installed, using Docker image grafana/k6)"
  # Inside the container "localhost" is the container itself: point at the host instead.
  to_host() { echo "$1" | sed -e 's#//localhost#//host.docker.internal#' -e 's#//127\.0\.0\.1#//host.docker.internal#'; }
  docker run --rm -i \
    --add-host=host.docker.internal:host-gateway \
    -v "$PERF_DIR:/perf" \
    -e PROFILE="$PROFILE" \
    -e BASE_URL="$(to_host "$BASE_URL")" \
    -e TURNSTILE_URL="$( [ -n "$TURNSTILE_URL" ] && to_host "$TURNSTILE_URL" )" \
    ${SCANS:+-e SCANS="$SCANS"} ${DURATION:+-e DURATION="$DURATION"} \
    grafana/k6:latest run --summary-mode=full \
    --out csv="/perf/results/$STAMP-$PROFILE/raw.csv" \
    --summary-export="/perf/results/$STAMP-$PROFILE/summary.json" \
    /perf/load-test.js 2>&1 | tee "$OUT/console.log"
  STATUS=${PIPESTATUS[0]}
fi
set -e
echo "$STATUS" > "$OUT/exit-code"

say "Building the report"
python3 "$PERF_DIR/report.py" "$OUT"

echo
if [ "$STATUS" -eq 0 ]; then
  echo "✅ PASS: all thresholds met."
else
  echo "❌ FAIL: k6 exit code $STATUS (99 = a threshold failed). See the report for which one."
fi
echo "📄 Report: $OUT/report.html"
exit "$STATUS"
