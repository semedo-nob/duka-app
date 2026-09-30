#!/usr/bin/env bash
# Start one Vite instance on port 5173, or leave the existing one alone.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"

if curl -sf -m 2 -o /dev/null http://localhost:5173/ || curl -sf -m 2 -o /dev/null http://[::1]:5173/; then
  echo "Vite is already running on http://localhost:5173. Not starting a second instance."
  exit 0
fi

if ss -ltn | grep -q ':5173'; then
  echo "Port 5173 is in use, but the Vite page did not respond. Not starting another process." >&2
  exit 1
fi

cd "$root"
exec npm exec vite -- --host localhost --port 5173 --strictPort
