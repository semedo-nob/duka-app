#!/usr/bin/env bash
# Start one Spring Boot instance on port 4000, or leave the healthy one alone.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"

if curl -sf -m 2 http://localhost:4000/api/health >/dev/null; then
  echo "Spring Boot is already healthy on http://localhost:4000. Not starting a second instance."
  exit 0
fi

if ss -ltn | grep -q ':4000'; then
  echo "Port 4000 is in use, but http://localhost:4000/api/health did not respond. Not starting another process." >&2
  exit 1
fi

cd "$root/backend"
exec ./gradlew bootRun --no-daemon
