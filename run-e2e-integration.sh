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

echo "==> Running proxy/auth/health smoke checks..."
HEALTH_BODY="$(curl -fsS http://localhost:4200/actuator/health)"
if [[ "$HEALTH_BODY" != *'"status":"UP"'* ]]; then
  echo "    ERROR: proxied /actuator/health did not report UP"
  exit 1
fi
if [[ "$HEALTH_BODY" == *'"components"'* || "$HEALTH_BODY" == *'"db"'* || "$HEALTH_BODY" == *'"diskSpace"'* ]]; then
  echo "    ERROR: anonymous /actuator/health exposed component details"
  exit 1
fi

COOKIE_JAR="$(mktemp)"
CSRF_BODY="$(curl -fsS -c "$COOKIE_JAR" http://localhost:4200/api/auth/csrf)"
CSRF_TOKEN="$(node -e "const fs = require('fs'); process.stdout.write(JSON.parse(fs.readFileSync(0, 'utf8')).token || '')" <<< "$CSRF_BODY")"
if [[ -z "$CSRF_TOKEN" ]]; then
  echo "    ERROR: CSRF token was not returned through the nginx proxy"
  rm -f "$COOKIE_JAR"
  exit 1
fi
curl -fsS -b "$COOKIE_JAR" -H "X-XSRF-TOKEN: $CSRF_TOKEN" -X POST http://localhost:4200/api/auth/demo-login > /dev/null
rm -f "$COOKIE_JAR"

echo "==> Running Playwright integration tests..."
cd "$FRONTEND_DIR"
npx playwright test e2e/integration.spec.ts --reporter=list

echo "==> Done!"
