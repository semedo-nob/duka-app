#!/usr/bin/env bash
# Check the three development processes. Starts Postgres when it is missing.
# Does not start a second API or Vite. Run those yourself so each has one terminal.
set -euo pipefail
dir="$(cd "$(dirname "$0")" && pwd)"

"$dir/dev-db.sh"

if curl -sf -m 2 http://localhost:4000/api/health >/dev/null; then
  echo "Spring Boot is already healthy on http://localhost:4000."
elif ss -ltn | grep -q ':4000'; then
  echo "Port 4000 is in use, but /api/health did not respond. Stop that process before npm run server." >&2
  exit 1
else
  echo "Spring Boot is not running. In this terminal: npm run server"
fi

if curl -sf -m 2 -o /dev/null http://localhost:5173/ || curl -sf -m 2 -o /dev/null http://[::1]:5173/; then
  echo "Vite is already running on http://localhost:5173."
elif ss -ltn | grep -q ':5173'; then
  echo "Port 5173 is in use, but the page did not respond. Stop that process before npm run dev." >&2
  exit 1
else
  echo "Vite is not running. In another terminal: npm run dev"
fi
