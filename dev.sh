#!/usr/bin/env bash
# dev.sh — starts PostgreSQL + backend (Spring Boot) + frontend (Angular)

ROOT="$(cd "$(dirname "$0")" && pwd)"

cleanup() {
  echo ""
  echo "Stopping services..."
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null
  wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null
  echo "Stopping database..."
  docker compose -f "$ROOT/docker-compose.yml" stop db mailhog
  echo "Done."
}
trap cleanup EXIT INT TERM

echo "Starting database (PostgreSQL + Mailhog)..."
cd "$ROOT"
docker compose up -d db mailhog

echo "Waiting for PostgreSQL to be ready..."
until docker compose exec db pg_isready -U manga_tracker -d manga_tracker -q 2>/dev/null; do
  sleep 1
done
echo "PostgreSQL is ready."

echo "Starting backend..."
cd "$ROOT/backend"
./gradlew bootRun &
BACKEND_PID=$!

echo "Starting frontend..."
cd "$ROOT/frontend"
npm start &
FRONTEND_PID=$!

echo ""
echo "Backend PID : $BACKEND_PID"
echo "Frontend PID: $FRONTEND_PID"
echo ""
echo "Backend  -> http://localhost:8080"
echo "Frontend -> http://localhost:4200"
echo ""
echo "Press Ctrl+C to stop both."

wait "$BACKEND_PID" "$FRONTEND_PID"
