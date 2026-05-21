#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

PASSED=0
FAILED=0

echo "=== Meridian Test Suite ==="

echo "[1/5] Building images..."
docker compose build --quiet 2>&1 | tail -5

echo "[2/5] Starting DB service..."
docker compose up -d db
echo "Waiting for PostgreSQL to be ready..."
until docker compose exec -T db pg_isready -U postgres -d meridian -q; do
  sleep 2
done
echo "DB ready."

echo "[3/5] Running backend unit + API tests (JUnit 5 + Testcontainers)..."
docker compose run --rm \
  -e DATABASE_URL=postgresql://postgres:postgres@db:5432/meridian \
  backend mvn test -q 2>&1
BACKEND_EXIT=$?
if [ $BACKEND_EXIT -eq 0 ]; then
  echo "Backend tests: PASSED"
  PASSED=$((PASSED + 1))
else
  echo "Backend tests: FAILED"
  FAILED=$((FAILED + 1))
fi

echo "[4/5] Running frontend unit tests (Karma/Jasmine)..."
docker compose run --rm frontend npm test -- --watch=false --browsers=ChromeHeadless 2>&1
FRONTEND_EXIT=$?
if [ $FRONTEND_EXIT -eq 0 ]; then
  echo "Frontend tests: PASSED"
  PASSED=$((PASSED + 1))
else
  echo "Frontend tests: FAILED"
  FAILED=$((FAILED + 1))
fi

echo "[5/5] Running E2E tests (Playwright)..."
docker compose up -d frontend backend
echo "Waiting for frontend to be ready..."
until docker compose exec -T frontend curl -sf http://localhost:80/ > /dev/null 2>&1; do
  sleep 2
done
docker compose run --rm \
  -e BASE_URL=http://frontend:80 \
  playwright npx playwright test --reporter=line 2>&1
E2E_EXIT=$?
if [ $E2E_EXIT -eq 0 ]; then
  echo "E2E tests: PASSED"
  PASSED=$((PASSED + 1))
else
  echo "E2E tests: FAILED"
  FAILED=$((FAILED + 1))
fi

docker compose down -v --remove-orphans 2>/dev/null || true

echo ""
echo "=== Test Summary ==="
echo "passed=${PASSED} failed=${FAILED}"
[ "$FAILED" -eq 0 ]
