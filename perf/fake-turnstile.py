#!/usr/bin/env python3
"""Fake vendor turnstile API for load tests and demos.

The real turnstile system exposes `POST /gates/{gateId}/open`. The backend's `stub`
mode answers instantly, which would make a load test look better than reality.
This server answers after a realistic delay, so the measured kiosk latency
includes a real network hop to the gate controller.

    python3 perf/fake-turnstile.py                  # port 9099, 300 ms delay
    python3 perf/fake-turnstile.py --delay-ms 2500  # slower than the 1.5 s timeout -> fallback path
    curl localhost:9099/stats                       # how many times each gate opened

Standard library only.
"""
import argparse
import json
import random
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

lock = threading.Lock()
opens = {"total": 0, "byGate": {}}


def make_handler(delay_ms: int, jitter_ms: int):
    class Handler(BaseHTTPRequestHandler):
        def _send(self, status: int, body: dict) -> None:
            raw = json.dumps(body).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        def do_POST(self):  # noqa: N802 (http.server naming)
            parts = self.path.strip("/").split("/")
            if len(parts) == 3 and parts[0] == "gates" and parts[2] == "open":
                time.sleep(max(0, delay_ms + random.randint(-jitter_ms, jitter_ms)) / 1000)
                with lock:
                    opens["total"] += 1
                    opens["byGate"][parts[1]] = opens["byGate"].get(parts[1], 0) + 1
                self._send(200, {"gateId": parts[1], "opened": True})
            else:
                self._send(404, {"error": "unknown path"})

        def do_GET(self):  # noqa: N802
            if self.path == "/stats":
                with lock:
                    self._send(200, opens)
            elif self.path == "/health":
                self._send(200, {"status": "UP", "delayMs": delay_ms})
            else:
                self._send(404, {"error": "unknown path"})

        def do_DELETE(self):  # noqa: N802
            if self.path == "/stats":
                with lock:
                    opens["total"] = 0
                    opens["byGate"] = {}
                self._send(200, opens)
            else:
                self._send(404, {"error": "unknown path"})

        def log_message(self, *args):  # keep the console quiet under load
            pass

    return Handler


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--port", type=int, default=9099)
    parser.add_argument("--host", default="127.0.0.1",
                        help="use 0.0.0.0 on Linux so the k6 Docker container can reach it")
    parser.add_argument("--delay-ms", type=int, default=300, help="simulated gate controller latency")
    parser.add_argument("--jitter-ms", type=int, default=100, help="random +/- spread on the delay")
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), make_handler(args.delay_ms, args.jitter_ms))
    server.daemon_threads = True
    print(f"fake turnstile listening on http://{args.host}:{args.port} "
          f"(delay {args.delay_ms}±{args.jitter_ms} ms)", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
