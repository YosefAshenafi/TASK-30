#!/usr/bin/env bash
#
# Meridian full test suite. Everything runs in Docker — no local JDK/Node/browser required.
#
#   1. Backend unit + API tests   — JUnit 5 + MockMvc + Testcontainers (real PostgreSQL)
#   2. Frontend unit tests        — Karma/Jasmine on headless Chromium
#   3. End-to-end tests           — Playwright against the running frontend + backend
#
# Each suite runs independently; a failure in one does not abort the others, so the final
# summary always reports every suite. The script exits non-zero if any suite failed.
#
# NOTE: the runtime backend/frontend images are slim (JRE-only / Nginx) and intentionally do
# not contain Maven, Node, or a browser. Tests therefore run from dedicated build stages
# (`backend-test`, `frontend-test`) selected via the compose `test` profile.

set -uo pipefail
cd "$(dirname "$0")"

PASSED=0
FAILED=0
declare -a FAILED_SUITES=()

run_suite() {
  local name="$1"; shift
  echo ""
  echo "----------------------------------------------------------------------"
  echo ">>> ${name}"
  echo "----------------------------------------------------------------------"
  if "$@"; then
    echo "${name}: PASSED"
    PASSED=$((PASSED + 1))
  else
    echo "${name}: FAILED"
    FAILED=$((FAILED + 1))
    FAILED_SUITES+=("${name}")
  fi
}

cleanup() {
  echo ""
  echo "Tearing down containers and volumes..."
  docker compose --profile test --profile e2e down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "=== Meridian Test Suite ==="

echo ""
echo "[1/4] Building images (runtime + test stages)..."
docker compose --profile test --profile e2e build

echo ""
echo "[2/4] Backend unit + API tests (JUnit 5 + Testcontainers)..."
run_suite "Backend tests" docker compose run --rm backend-test

echo ""
echo "[3/4] Frontend unit tests (Karma/Jasmine, headless Chromium)..."
run_suite "Frontend tests" docker compose run --rm frontend-test

echo ""
echo "[4/4] End-to-end tests (Playwright)..."
echo "Starting db, backend and frontend, then waiting for health checks..."
docker compose up -d --wait db backend frontend
run_suite "E2E tests" docker compose run --rm playwright

echo ""
echo "=== Test Summary ==="
echo "passed=${PASSED} failed=${FAILED}"
if [ "${FAILED}" -ne 0 ]; then
  echo "Failed suites: ${FAILED_SUITES[*]}"
fi

[ "${FAILED}" -eq 0 ]
