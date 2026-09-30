#!/usr/bin/env bash
# Start the one Duka Postgres, or leave the existing one alone.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"

running="$(docker inspect -f '{{.State.Running}}' duka-pg 2>/dev/null || true)"
if [[ "$running" == "true" ]]; then
  echo "PostgreSQL already running: container duka-pg on localhost:5434. Not starting another."
  exit 0
fi

if docker inspect duka-pg >/dev/null 2>&1; then
  docker start duka-pg >/dev/null
  echo "Started the existing container duka-pg on localhost:5434."
  exit 0
fi

if ss -ltn | grep -q ':5434'; then
  echo "Port 5434 is already in use by something other than duka-pg. Not starting a second database." >&2
  exit 1
fi

docker compose -f "$root/docker-compose.yml" up -d postgres
echo "Started PostgreSQL (duka-pg) on localhost:5434."
