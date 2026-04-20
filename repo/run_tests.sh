#!/usr/bin/env bash
# Runs all Meridian test suites and prints an overall summary report.
#
# Usage:
#   ./run_tests.sh          # unit + integration tests
#   ./run_tests.sh --e2e    # also run Playwright E2E (requires running services)

set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_E2E=false
[[ "${1:-}" == "--e2e" ]] && RUN_E2E=true

# Prefer Temurin 17 on macOS — matches CI and Spring Boot 3 baseline.
_java17=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
[[ -n "$_java17" ]] && export JAVA_HOME="$_java17"

COV_THRESHOLD=90

# ── Coverage helper ────────────────────────────────────────────────────────────
# Compute instruction-coverage % from a JaCoCo CSV for specific packages/classes.
# Args: <csv_path>  [pkg/name::ClassName | pkg/name ...]
#   pkg/name::ClassName  — single class
#   pkg/name             — whole package
_jacoco_pct() {
  local csv="$1"; shift
  [[ ! -f "$csv" ]] && { echo "N/A"; return; }
  awk -F, -v specs="$*" '
  BEGIN {
    n = split(specs, sa, " ")
    for (i=1; i<=n; i++) {
      if (index(sa[i], "::") > 0) {
        split(sa[i], p, "::")
        pkgs[i] = p[1]; clss[i] = p[2]
      } else {
        pkgs[i] = sa[i]; clss[i] = ""
      }
    }
  }
  NR == 1 { next }
  {
    for (i=1; i<=n; i++) {
      if ($2 == pkgs[i] && (clss[i] == "" || $3 == clss[i])) {
        missed += $4+0; covered += $5+0; break
      }
    }
  }
  END {
    total = missed + covered
    if (total > 0) printf "%d", int(covered * 100 / total)
    else print "0"
  }' "$csv"
}

# Istanbul: worst dimension among statements / branches / functions / lines.
_web_worst_pct() {
  local cov_file="$1"
  node -e "
try {
  var s = require('$cov_file');
  var t = s.total;
  if (!t || !t.statements || !t.statements.total) { console.log('N/A'); process.exit(0); }
  var dims = ['statements','branches','functions','lines'].map(function (k) {
    var x = t[k];
    return (x && x.total > 0) ? x.pct : 100;
  });
  console.log(Math.floor(Math.min.apply(null, dims)));
} catch (e) { console.log('N/A'); }
" 2>/dev/null || echo "N/A"
}

# ── Tracking (bash 3.2-compatible: parallel indexed arrays) ───────────────────
declare -a SUITE_ORDER=()
declare -a SUITE_STATUS=()
declare -a SUITE_TIME=()
declare -a SUITE_COVERAGE=()
PASS_COUNT=0; FAIL_COUNT=0; SKIP_COUNT=0
_COVERAGE=""   # test-runner functions set this before returning

run_suite() {
  local name="$1"; shift
  local idx=${#SUITE_ORDER[@]}
  SUITE_ORDER+=("$name")
  _COVERAGE=""
  echo
  echo "━━━  $name  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  local start; start=$(date +%s)
  local ok=0
  "$@" || ok=$?
  local end; end=$(date +%s)
  SUITE_TIME[$idx]=$((end - start))
  local cov="${_COVERAGE:-N/A}"
  SUITE_COVERAGE[$idx]="$cov"

  if [[ $ok -ne 0 ]]; then
    SUITE_STATUS[$idx]="FAIL"
    ((FAIL_COUNT++))
  elif [[ "$cov" != "N/A" && "$cov" -lt $COV_THRESHOLD ]]; then
    SUITE_STATUS[$idx]="FAIL"
    ((FAIL_COUNT++))
    echo "  ✗ Coverage ${cov}% is below the ${COV_THRESHOLD}% threshold"
  else
    SUITE_STATUS[$idx]="PASS"
    ((PASS_COUNT++))
  fi
}

skip_suite() {
  local name="$1"; shift
  local idx=${#SUITE_ORDER[@]}
  SUITE_ORDER+=("$name")
  SUITE_STATUS[$idx]="SKIP"
  SUITE_TIME[$idx]=0
  SUITE_COVERAGE[$idx]="N/A"
  ((SKIP_COUNT++))
  echo
  echo "━━━  $name  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "  ⊘  Skipped: $*"
}

# ── 1. Server Unit Tests ──────────────────────────────────────────────────────
run_server_unit_tests() {
  local dest="$REPO/server/src/test/java/com/meridian/auth"
  local copied=()

  mkdir -p "$dest"
  for f in "$REPO/unit_tests/server/"*.java; do
    cp "$f" "$dest/"
    copied+=("$dest/$(basename "$f")")
  done

  local tests="PasswordPolicyTest,LockoutPolicyTest,JwtServiceTest,IdempotencyServiceTest,SyncResolverTest,TemplateRendererTest"
  local result=0
  (cd "$REPO/server" && ./mvnw test -Dtest="$tests" \
      -Djacoco.destFile=target/jacoco-unit.exec \
      -Djacoco.reportDir=target/site/jacoco-unit \
      --no-transfer-progress 2>&1) || result=$?

  for f in "${copied[@]}"; do rm -f "$f"; done

  _COVERAGE=$(_jacoco_pct "$REPO/server/target/site/jacoco-unit/jacoco.csv" \
    "com.meridian.auth::PasswordPolicy" \
    "com.meridian.auth::LockoutPolicy" \
    "com.meridian.auth::JwtService" \
    "com.meridian.common.idempotency::IdempotencyService" \
    "com.meridian.sessions::SyncResolver")

  return $result
}
run_suite "Server Unit Tests" run_server_unit_tests

# ── 2. API Integration Tests ─────────────────────────────────────────────────
run_api_integration_tests() {
  local api_src="$REPO/api_tests/src/test/java/com/meridian"
  local dest="$REPO/server/src/test/java/com/meridian"
  local copied=()

  for f in "$api_src"/*.java; do
    cp "$f" "$dest/"
    copied+=("$dest/$(basename "$f")")
  done

  local tests="AuthApiTest,SyncApiTest,OrgScopeApiTest,ReportApiTest,AuthAuditTest,ClassificationApiTest"
  local result=0
  (cd "$REPO/server" && ./mvnw test -Dtest="$tests" \
      -Djacoco.destFile=target/jacoco-it.exec \
      -Djacoco.reportDir=target/site/jacoco-it \
      --no-transfer-progress 2>&1) || result=$?

  for f in "${copied[@]}"; do rm -f "$f"; done

  _COVERAGE=$(_jacoco_pct "$REPO/server/target/site/jacoco-it/jacoco.csv" \
    "com.meridian.auth::AuthController" \
    "com.meridian.auth::AuthService" \
    "com.meridian.sessions::SessionSyncController" \
    "com.meridian.sessions::SyncResolver")

  return $result
}
run_suite "API Integration Tests" run_api_integration_tests

# ── 3. Web Unit Tests ────────────────────────────────────────────────────────
run_web_unit_tests() {
  local web="$REPO/web"
  local guards_dest="$web/src/app/core/guards"
  local stores_dest="$web/src/app/core/stores"
  local http_dest="$web/src/app/core/http"
  local copied=()

  mkdir -p "$guards_dest" "$stores_dest" "$http_dest"

  # Copy with import-path fixup for each spec's destination directory
  sed "s|../../web/src/app/core/guards/|./|g; \
       s|../../web/src/app/core/stores/|../stores/|g" \
      "$REPO/unit_tests/web/auth.guard.spec.ts" > "$guards_dest/auth.guard.spec.ts"
  copied+=("$guards_dest/auth.guard.spec.ts")

  sed "s|../../web/src/app/core/stores/|./|g; \
       s|../../web/src/app/core/models/|../models/|g" \
      "$REPO/unit_tests/web/auth.store.spec.ts" > "$stores_dest/auth.store.spec.ts"
  copied+=("$stores_dest/auth.store.spec.ts")

  cp "$REPO/unit_tests/web/outbox.service.spec.ts" "$http_dest/outbox.service.spec.ts"
  copied+=("$http_dest/outbox.service.spec.ts")

  # pending-route.spec.ts imports from ../../web/src/app/app.routes
  local app_dest="$web/src/app"
  sed "s|../../web/src/app/|./|g" \
      "$REPO/unit_tests/web/pending-route.spec.ts" > "$app_dest/pending-route.spec.ts"
  copied+=("$app_dest/pending-route.spec.ts")

  # session-sync-keys.spec.ts imports from ../../web/src/app/...
  local sessions_dest="$web/src/app/sessions"
  sed "s|../../web/src/app/sessions/|./|g; \
       s|../../web/src/app/core/db/|../core/db/|g" \
      "$REPO/unit_tests/web/session-sync-keys.spec.ts" > "$sessions_dest/session-sync-keys.spec.ts"
  copied+=("$sessions_dest/session-sync-keys.spec.ts")

  if [[ ! -x "$web/node_modules/.bin/ng" ]]; then
    echo "  Installing web dependencies (npm ci)…"
    if ! (cd "$web" && npm ci --legacy-peer-deps 2>&1); then
      echo "  ✗ npm ci failed"
      return 1
    fi
  fi

  local result=0
  (cd "$web" && ./node_modules/.bin/ng test --watch=false --browsers=ChromeHeadlessCI \
      --no-progress 2>&1) || result=$?

  for f in "${copied[@]}"; do rm -f "$f"; done

  local cov_file="$web/coverage/meridian-web/coverage-summary.json"
  if [[ -f "$cov_file" ]]; then
    _COVERAGE=$(_web_worst_pct "$cov_file")
  else
    _COVERAGE="N/A"
  fi

  return $result
}
run_suite "Web Unit Tests" run_web_unit_tests

# ── 4. E2E Tests ─────────────────────────────────────────────────────────────
run_e2e_tests() {
  local e2e_dir="$REPO/e2e_tests"
  if [[ ! -f "$e2e_dir/node_modules/.bin/playwright" ]]; then
    echo "  Installing Playwright dependencies…"
    (cd "$e2e_dir" && npm ci 2>&1)
    (cd "$e2e_dir" && npx playwright install chromium 2>&1)
  fi
  local result=0
  (cd "$e2e_dir" && npx playwright test 2>&1) || result=$?

  # Parse V8 function coverage written by global-teardown
  local cov_file="$e2e_dir/coverage/coverage-summary.json"
  if [[ -f "$cov_file" ]]; then
    _COVERAGE=$(node -e "
try {
  var s = require('$cov_file');
  var t = s.total;
  if (t && t.statements && t.statements.total > 0)
    console.log(Math.round(t.statements.pct));
  else
    console.log('N/A');
} catch(e) { console.log('N/A'); }
" 2>/dev/null || echo "N/A")
  else
    _COVERAGE="N/A"
  fi

  return $result
}

if $RUN_E2E; then
  run_suite "E2E Tests (Playwright)" run_e2e_tests
else
  skip_suite "E2E Tests (Playwright)" "Pass --e2e to include (requires running services on :4200)"
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo
echo "╔═════════════════════════════════════════════════════════════════╗"
echo "║                       TEST SUITE RESULTS                        ║"
echo "╠═════════════════════════════════════════════════════════════════╣"
printf "║  %-6s  %-36s  %8s  %5s  ║\n" "Result" "Suite" "Coverage" "Time"
echo "╠═════════════════════════════════════════════════════════════════╣"
for i in "${!SUITE_ORDER[@]}"; do
  suite="${SUITE_ORDER[$i]}"
  status="${SUITE_STATUS[$i]}"
  secs="${SUITE_TIME[$i]}"
  cov="${SUITE_COVERAGE[$i]}"

  case "$status" in
    PASS) icon="✓ PASS" ;;
    FAIL) icon="✗ FAIL" ;;
    SKIP) icon="- SKIP" ;;
  esac

  if [[ $secs -ge 60 ]]; then
    time_str="$(( secs / 60 ))m$(( secs % 60 ))s"
  elif [[ $secs -eq 0 ]]; then
    time_str="    -"
  else
    time_str="${secs}s"
  fi

  if [[ "$cov" == "N/A" ]]; then
    cov_str="N/A"
  else
    cov_str="${cov}%"
  fi

  printf "║  %-6s  %-36s  %8s  %5s  ║\n" "$icon" "$suite" "$cov_str" "$time_str"
done

# Overall: mean of numeric suite coverages (skipped / N/A excluded).
OVERALL_SUM=0
OVERALL_N=0
for i in "${!SUITE_ORDER[@]}"; do
  c="${SUITE_COVERAGE[$i]}"
  st="${SUITE_STATUS[$i]}"
  [[ "$st" == "SKIP" ]] && continue
  [[ "$c" == "N/A" ]] && continue
  [[ "$c" =~ ^[0-9]+$ ]] || continue
  OVERALL_SUM=$((OVERALL_SUM + c))
  OVERALL_N=$((OVERALL_N + 1))
done
OVERALL_PCT=0
[[ $OVERALL_N -gt 0 ]] && OVERALL_PCT=$((OVERALL_SUM / OVERALL_N))

if [[ $OVERALL_N -gt 0 ]]; then
  echo "╠═════════════════════════════════════════════════════════════════╣"
  printf "║  %-6s  %-36s  %8s  %5s  ║\n" "—" "Overall (mean of suite scores)" "${OVERALL_PCT}%" "-"
fi

echo "╠═════════════════════════════════════════════════════════════════╣"
printf "║  %-63s║\n" "  Passed: $PASS_COUNT    Failed: $FAIL_COUNT    Skipped: $SKIP_COUNT"
echo "╚═════════════════════════════════════════════════════════════════╝"
echo

if [[ $FAIL_COUNT -gt 0 ]]; then
  echo "Some test suites FAILED. See output above for details."
  exit 1
else
  echo "All executed test suites passed."
  exit 0
fi
