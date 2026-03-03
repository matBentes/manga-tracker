#!/usr/bin/env bash
set -euo pipefail

# Integration E2E tests for Manga Tracker
# Starts backend services, waits for health, runs Playwright tests, then cleans up.
#
# Usage:
#   ./run-e2e-integration.sh          # run tests (keep services up after)
#   ./run-e2e-integration.sh --down   # run tests then tear down services

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/frontend"
TEARDOWN=false

if [[ "${1:-}" == "--down" ]]; then
  TEARDOWN=true
fi

cleanup() {
  if $TEARDOWN; then
    echo "==> Tearing down services..."
    docker compose -f "$SCRIPT_DIR/docker-compose.yml" down
  fi
}
trap cleanup EXIT

echo "==> Starting backend services..."
docker compose -f "$SCRIPT_DIR/docker-compose.yml" up -d

echo "==> Waiting for backend to be healthy (port 8080)..."
for i in $(seq 1 60); do
  if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "    Backend healthy!"
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "    ERROR: Backend did not become healthy within 60s"
    docker compose -f "$SCRIPT_DIR/docker-compose.yml" logs backend
    exit 1
  fi
  sleep 1
done

echo "==> Waiting for frontend (port 4200)..."
for i in $(seq 1 30); do
  if curl -sf http://localhost:4200 > /dev/null 2>&1; then
    echo "    Frontend ready!"
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "    ERROR: Frontend did not become ready within 30s"
    exit 1
  fi
  sleep 1
done

echo "==> Running Playwright integration tests..."
cd "$FRONTEND_DIR"
npx playwright test e2e/integration.spec.ts --reporter=list

echo "==> Done!"
