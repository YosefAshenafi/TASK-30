#!/usr/bin/env bash
# Runs all Meridian test suites and prints a detailed per-test breakdown with coverage.
#
# Usage:
#   ./run_tests.sh          # unit + integration tests
#   ./run_tests.sh --e2e    # also run Playwright E2E (requires running services)

set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_E2E=false
[[ "${1:-}" == "--e2e" ]] && RUN_E2E=true

COV_THRESHOLD=90
DOCKER_AVAILABLE=false
JAVA_MAJOR=0
MAVEN_JAVA_MAJOR=0
MAVEN_TEST_FLAGS=""

# Docker image used to run backend Maven suites so the script does not depend
# on a host JDK/Maven. Matches the server Dockerfile's baseline JDK.
# Defaults to a locally-built image that includes postgresql-client so pg_dump /
# psql / pg_restore are available when integration tests invoke those binaries.
# Override via SERVER_TEST_IMAGE env var (e.g. in CI after pre-building).
SERVER_TEST_IMAGE="${SERVER_TEST_IMAGE:-}"

# ── Helpers ────────────────────────────────────────────────────────────────────

# Instruction coverage % from JaCoCo CSV — single class (simple name)
_jacoco_class_pct() {
  local csv="$1" cls="$2"
  [[ ! -f "$csv" ]] && echo "N/A" && return
  awk -F, -v c="$cls" '
    NR==1{next}
    $3==c{ t=$4+$5; if(t>0) printf "%d",int($5*100/t); else print "0"; found=1; exit }
    END  { if(!found) print "N/A" }
  ' "$csv"
}

# Instruction coverage % from JaCoCo CSV — aggregate over pkg::Class specs
_jacoco_pct() {
  local csv="$1"; shift
  [[ ! -f "$csv" ]] && { echo "N/A"; return; }
  awk -F, -v specs="$*" '
  BEGIN{ n=split(specs,sa," ")
    for(i=1;i<=n;i++){
      if(index(sa[i],"::")>0){ split(sa[i],p,"::"); pk[i]=p[1]; cl[i]=p[2] }
      else{ pk[i]=sa[i]; cl[i]="" }
    }
  }
  NR==1{next}
  { for(i=1;i<=n;i++){
      if($2==pk[i]&&(cl[i]==""||$3==cl[i])){ miss+=$4+0; cov+=$5+0; break }
    }
  }
  END{ t=miss+cov; if(t>0) printf "%d",int(cov*100/t); else print "0" }
  ' "$csv"
}

# Istanbul: worst pct across statements/branches/functions/lines (total)
_web_worst_pct() {
  local f="$1"
  docker run --rm \
    -v "$REPO:/work" -w /work \
    node:20-alpine node -e "
try{
  var s=require('${f#$REPO/}'),t=s.total;
  if(!t||!t.statements||!t.statements.total){console.log('N/A');process.exit(0);}
  var d=['statements','branches','functions','lines'].map(function(k){
    var x=t[k];return(x&&x.total>0)?x.pct:100;
  });
  console.log(Math.floor(Math.min.apply(null,d)));
}catch(e){console.log('N/A');}
" 2>/dev/null || echo "N/A"
}

# Istanbul: worst pct for a source file matching a partial path
_istanbul_src_pct() {
  local f="$1" partial="$2"
  docker run --rm \
    -v "$REPO:/work" -w /work \
    node:20-alpine node -e "
try{
  var s=require('${f#$REPO/}');
  var k=Object.keys(s).filter(function(p){return p.indexOf('$partial')>=0;})[0];
  if(!k){console.log('N/A');process.exit(0);}
  var e=s[k];
  var d=['statements','branches','functions','lines'].map(function(dim){
    var x=e[dim];return(x&&x.total>0)?x.pct:100;
  });
  console.log(Math.floor(Math.min.apply(null,d)));
}catch(e){console.log('N/A');}
" 2>/dev/null || echo "N/A"
}

# Parse surefire XML → "tests pass fail skip time_float"
_surefire_stats() {
  local xml="$1"
  [[ ! -f "$xml" ]] && echo "0 0 0 0 0" && return
  local line; line=$(grep -m1 "testsuite " "$xml" 2>/dev/null || echo "")
  if [[ -z "$line" ]]; then echo "0 0 0 0 0"; return; fi
  local t f e s tm
  t=$(echo "$line"  | grep -o 'tests="[0-9]*"'    | grep -o '[0-9]*' || echo 0)
  f=$(echo "$line"  | grep -o 'failures="[0-9]*"' | grep -o '[0-9]*' || echo 0)
  e=$(echo "$line"  | grep -o 'errors="[0-9]*"'   | grep -o '[0-9]*' || echo 0)
  s=$(echo "$line"  | grep -o 'skipped="[0-9]*"'  | grep -o '[0-9]*' || echo 0)
  tm=$(echo "$line" | grep -o 'time="[0-9.]*"'    | grep -o '[0-9.]*' || echo 0)
  t=${t:-0}; f=${f:-0}; e=${e:-0}; s=${s:-0}; tm=${tm:-0}
  local fail=$((f + e))
  local pass=$((t - fail - s))
  echo "$t $pass $fail $s $tm"
}

# Format a float seconds value as "0.3s" / "12s" / "1m23s"
_fmt_time() {
  awk "BEGIN{
    t=$1; i=int(t)
    if(t<0) t=0
    if(i>=60) printf \"%dm%02ds\",int(i/60),i%60
    else if(i>=10) printf \"%ds\",i
    else printf \"%.1fs\",t
  }" 2>/dev/null || echo "${1}s"
}

_detect_docker() {
  command -v docker >/dev/null 2>&1 || return 1
  docker info >/dev/null 2>&1
}

_detect_java_major() {
  local line
  line=$(java -version 2>&1 | head -1 || true)
  [[ -z "$line" ]] && { echo 0; return; }
  local major
  major=$(echo "$line" | sed -E 's/.*version "([0-9]+).*/\1/' || true)
  [[ "$major" =~ ^[0-9]+$ ]] && echo "$major" || echo 0
}

_detect_maven_java_major() {
  local line
  line=$(cd "$REPO/server" && ./mvnw -v 2>/dev/null | grep -m1 "Java version:" || true)
  [[ -z "$line" ]] && { echo 0; return; }
  local major
  major=$(echo "$line" | sed -E 's/.*Java version: ([0-9]+).*/\1/' || true)
  [[ "$major" =~ ^[0-9]+$ ]] && echo "$major" || echo 0
}

# ── Detail table helpers ───────────────────────────────────────────────────────
_COL=82   # total inner width

_tbl_top()    { printf "  ┌"; printf "%${_COL}s" "" | tr ' ' '─'; printf "┐\n"; }
_tbl_mid()    { printf "  ├"; printf "%${_COL}s" "" | tr ' ' '─'; printf "┤\n"; }
_tbl_bot()    { printf "  └"; printf "%${_COL}s" "" | tr ' ' '─'; printf "┘\n"; }

_tbl_header() {
  _tbl_top
  printf "  │  %-42s %6s %6s %6s %6s %7s %7s  │\n" \
    "Test" "Total" "Pass" "Fail" "Skip" "Cov" "Time"
  _tbl_mid
}

_tbl_row() {
  # $1=name $2=total $3=pass $4=fail $5=skip $6=cov $7=time
  local icon=" "
  [[ "${4:-0}" -gt 0 ]] 2>/dev/null && icon="✗"
  [[ "${4:-0}" -eq 0 && "${3:-0}" -gt 0 ]] 2>/dev/null && icon="✓"
  local cov_str="${6:-N/A}"
  [[ "$cov_str" =~ ^[0-9]+$ ]] && cov_str="${6}%"
  printf "  │ %s %-42s %6s %6s %6s %6s %7s %7s  │\n" \
    "$icon" "$1" "${2:-0}" "${3:-0}" "${4:-0}" "${5:-0}" "$cov_str" "${7:--}"
}

_tbl_total() {
  # $1=total $2=pass $3=fail $4=skip $5=cov $6=time
  _tbl_mid
  local cov_str="${5:-N/A}"
  [[ "$cov_str" =~ ^[0-9]+$ ]] && cov_str="${5}%"
  printf "  │  %-42s %6s %6s %6s %6s %7s %7s  │\n" \
    "Suite Total" "${1:-0}" "${2:-0}" "${3:-0}" "${4:-0}" "$cov_str" "${6:--}"
  _tbl_bot
}

# ── Suite tracking ─────────────────────────────────────────────────────────────
declare -a SUITE_ORDER=()
declare -a SUITE_STATUS=()
declare -a SUITE_TIME=()
declare -a SUITE_COVERAGE=()
declare -a SUITE_T=(); declare -a SUITE_P=(); declare -a SUITE_F=(); declare -a SUITE_S=()
PASS_COUNT=0; FAIL_COUNT=0; SKIP_COUNT=0
GRAND_T=0; GRAND_P=0; GRAND_F=0; GRAND_S=0
_COVERAGE=""; _T=0; _P=0; _F=0; _S=0

run_suite() {
  local name="$1"; shift
  local idx=${#SUITE_ORDER[@]}
  SUITE_ORDER+=("$name")
  _COVERAGE=""; _T=0; _P=0; _F=0; _S=0
  echo
  echo "━━━  $name  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  local t0; t0=$(date +%s)
  local ok=0; "$@" || ok=$?
  local t1; t1=$(date +%s)
  SUITE_TIME[$idx]=$((t1 - t0))
  local cov="${_COVERAGE:-N/A}"
  SUITE_COVERAGE[$idx]="$cov"
  SUITE_T[$idx]=$_T; SUITE_P[$idx]=$_P; SUITE_F[$idx]=$_F; SUITE_S[$idx]=$_S
  GRAND_T=$((GRAND_T + _T)); GRAND_P=$((GRAND_P + _P))
  GRAND_F=$((GRAND_F + _F)); GRAND_S=$((GRAND_S + _S))
  if [[ $ok -ne 0 ]]; then
    SUITE_STATUS[$idx]="FAIL"; ((FAIL_COUNT++))
  elif [[ "$cov" =~ ^[0-9]+$ ]] && [[ "$cov" -lt $COV_THRESHOLD ]]; then
    SUITE_STATUS[$idx]="FAIL"; ((FAIL_COUNT++))
    echo "  ✗ Coverage ${cov}% is below the ${COV_THRESHOLD}% threshold"
  else
    SUITE_STATUS[$idx]="PASS"; ((PASS_COUNT++))
  fi
}

skip_suite() {
  local name="$1"; shift
  local idx=${#SUITE_ORDER[@]}
  SUITE_ORDER+=("$name")
  SUITE_STATUS[$idx]="SKIP"; SUITE_TIME[$idx]=0; SUITE_COVERAGE[$idx]="N/A"
  SUITE_T[$idx]=0; SUITE_P[$idx]=0; SUITE_F[$idx]=0; SUITE_S[$idx]=0
  ((SKIP_COUNT++))
  echo
  echo "━━━  $name  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "  ⊘  Skipped: $*"
}

# ── Environment detection ──────────────────────────────────────────────────────
if _detect_docker; then
  DOCKER_AVAILABLE=true
else
  DOCKER_AVAILABLE=false
fi

# Build the Maven test image (which includes postgresql-client) if not already
# cached and no override was supplied. This ensures pg_dump / psql / pg_restore
# are present for any integration test path that spawns those processes.
_MAVEN_TEST_IMG_TAG="meridian-maven-test:local"
if $DOCKER_AVAILABLE && [[ -z "${SERVER_TEST_IMAGE}" ]]; then
  if docker image inspect "$_MAVEN_TEST_IMG_TAG" >/dev/null 2>&1; then
    SERVER_TEST_IMAGE="$_MAVEN_TEST_IMG_TAG"
  else
    echo "  Building Maven test image (includes postgresql-client)..."
    if docker build -q -f "$REPO/server/Dockerfile.test" \
        -t "$_MAVEN_TEST_IMG_TAG" "$REPO/server" 2>/dev/null; then
      SERVER_TEST_IMAGE="$_MAVEN_TEST_IMG_TAG"
    else
      SERVER_TEST_IMAGE="maven:3.9-eclipse-temurin-17"
      echo "  Warning: test image build failed — falling back to base Maven image"
      echo "           pg_dump/psql will be unavailable in the test container"
    fi
  fi
fi
SERVER_TEST_IMAGE="${SERVER_TEST_IMAGE:-maven:3.9-eclipse-temurin-17}"

JAVA_MAJOR=$(_detect_java_major)
MAVEN_JAVA_MAJOR=$(_detect_maven_java_major)
MAVEN_TEST_FLAGS="-Djacoco.skip=true -Djdk.attach.allowAttachSelf=true"
if [[ "$JAVA_MAJOR" -gt 0 ]]; then
  echo "  ! Host Java $JAVA_MAJOR detected — enabling compatibility mode (disable JaCoCo instrumentation for host-run suites)."
fi
if [[ "$MAVEN_JAVA_MAJOR" -gt 0 && "$MAVEN_JAVA_MAJOR" -ne "$JAVA_MAJOR" ]]; then
  echo "  ! Maven runtime Java is $MAVEN_JAVA_MAJOR (differs from shell java)."
fi

# ── 1. Server Unit Tests ──────────────────────────────────────────────────────
# Unit tests live authoritatively in server/src/test/java/**; the copies under
# unit_tests/server/ are kept only as an archival mirror. No copy step is needed.
run_server_unit_tests() {
  local test_list="PasswordPolicyTest,LockoutPolicyTest,JwtServiceTest,IdempotencyServiceTest,SyncResolverTest,TemplateRendererTest,AesAttributeConverterTest,AuthServiceAuditTest,BackupRunnerTest,RateLimitFilterTest,RecoveryDrillRunnerTest,AuthServiceTest,DeviceFingerprintServiceTest,JwtAuthenticationFilterTest,RegistrationSlaSchedulerTest,AnalyticsServiceTest,ApprovalServiceTest,BackupSchedulerTest,ClassificationPolicyTest,MaskingPolicyTest,NotificationServiceTest,RecycleBinRetentionSchedulerTest,ReportRunnerTest,ReportSchedulerTest,AnomalyDetectorTest,AuditEventPublisherTest,AdminUserServiceTest,RequestIdFilterTest,IdempotencyInterceptorTest,AuthPrincipalTest,PageResponseTest,AllowedIpRangeControllerUnitTest,SessionControllerUnitTest"
  local result=0
  docker run --rm \
      -v "$REPO/server:/workspace" \
      -v "$HOME/.m2:/root/.m2" \
      -w /workspace \
      -e JAVA_TOOL_OPTIONS="-XX:+EnableDynamicAgentLoading" \
      "$SERVER_TEST_IMAGE" \
      mvn test -Dtest="$test_list" \
          -Djacoco.destFile=target/jacoco-unit.exec \
          -Djacoco.reportDir=target/site/jacoco-unit \
          -Djacoco.skip=true \
          --no-transfer-progress 2>&1 || result=$?

  local surefire="$REPO/server/target/surefire-reports"
  local jacoco="$REPO/server/target/site/jacoco-unit/jacoco.csv"
  local st=0 sp=0 sf=0 ss=0

  _tbl_header
  IFS=',' read -ra _cls <<< "$test_list"
  for cls in "${_cls[@]}"; do
    local xml; xml=$(find "$surefire" -name "TEST-*${cls}.xml" 2>/dev/null | head -1 || true)
    local row; row=$(_surefire_stats "$xml")
    local t p f s tm; read -r t p f s tm <<< "$row"
    local src_cls="${cls%Test}"
    local cov; cov=$(_jacoco_class_pct "$jacoco" "$src_cls"); cov="${cov:-N/A}"
    _tbl_row "$cls" "$t" "$p" "$f" "$s" "$cov" "$(_fmt_time "$tm")"
    st=$((st+t)); sp=$((sp+p)); sf=$((sf+f)); ss=$((ss+s))
  done

  _COVERAGE=$(_jacoco_pct "$jacoco" \
    "com.meridian.auth::PasswordPolicy" \
    "com.meridian.auth::LockoutPolicy" \
    "com.meridian.auth::JwtService" \
    "com.meridian.auth::AuthService" \
    "com.meridian.auth::DeviceFingerprintService" \
    "com.meridian.common.idempotency::IdempotencyService" \
    "com.meridian.sessions::SyncResolver" \
    "com.meridian.governance::ClassificationPolicy" \
    "com.meridian.governance::MaskingPolicy" \
    "com.meridian.reports.runner::ReportRunner" \
    "com.meridian.security.anomaly::AnomalyDetector" \
    "com.meridian.security.audit::AuditEventPublisher" \
    "com.meridian.notifications::NotificationService")
  _tbl_total "$st" "$sp" "$sf" "$ss" "${_COVERAGE:-N/A}" "-"

  _T=$st; _P=$sp; _F=$sf; _S=$ss
  return $result
}
if $DOCKER_AVAILABLE; then
  run_suite "Server Unit Tests" run_server_unit_tests
else
  skip_suite "Server Unit Tests" "Docker unavailable (required to run backend tests in the Maven container)"
fi

# ── 2. API Integration Tests ─────────────────────────────────────────────────
# API tests live authoritatively in server/src/test/java/com/meridian/*ApiTest.java.
# Copies under api_tests/src/test/java/ are kept only as an archival mirror.
run_api_integration_tests() {
  local test_list="AuthApiTest,SyncApiTest,OrgScopeApiTest,ReportApiTest,AuthAuditTest,ClassificationApiTest,OrgIsolationContentApiTest,SensitiveDataApiTest,HealthApiTest,AdminUserApiTest,ApprovalApiTest,BackupApiTest,AllowedIpRangeApiTest,TemplateApiTest,NotificationApiTest,RecycleBinApiTest,AnomalyApiTest,SessionLifecycleApiTest,AttemptDraftApiTest,CourseAuthoringApiTest,NoMockAuthCoverageApiTest,TrueNoMockHttpApiTest,TrainingSessionRepositoryTest"
  local result=0
  # Testcontainers needs access to the host Docker socket to boot Postgres.
  docker run --rm \
      -v "$REPO/server:/workspace" \
      -v "$HOME/.m2:/root/.m2" \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -w /workspace \
      -e JAVA_TOOL_OPTIONS="-XX:+EnableDynamicAgentLoading" \
      -e TESTCONTAINERS_HOST_OVERRIDE="host.docker.internal" \
      --add-host=host.docker.internal:host-gateway \
      "$SERVER_TEST_IMAGE" \
      mvn test -Dtest="$test_list" \
          -Djacoco.destFile=target/jacoco-it.exec \
          -Djacoco.reportDir=target/site/jacoco-it \
          -Djacoco.skip=true \
          --no-transfer-progress 2>&1 || result=$?

  local surefire="$REPO/server/target/surefire-reports"
  local jacoco="$REPO/server/target/site/jacoco-it/jacoco.csv"
  local st=0 sp=0 sf=0 ss=0

  _tbl_header
  IFS=',' read -ra _cls <<< "$test_list"
  for cls in "${_cls[@]}"; do
    local xml; xml=$(find "$surefire" -name "TEST-*${cls}.xml" 2>/dev/null | head -1 || true)
    local row; row=$(_surefire_stats "$xml")
    local t p f s tm; read -r t p f s tm <<< "$row"
    local src_cls="${cls%Test}"
    local cov; cov=$(_jacoco_class_pct "$jacoco" "$src_cls"); cov="${cov:-N/A}"
    _tbl_row "$cls" "$t" "$p" "$f" "$s" "$cov" "$(_fmt_time "$tm")"
    st=$((st+t)); sp=$((sp+p)); sf=$((sf+f)); ss=$((ss+s))
  done

  _COVERAGE=$(_jacoco_pct "$jacoco" \
    "com.meridian.auth::AuthController" \
    "com.meridian.auth::AuthService" \
    "com.meridian.sessions::SessionSyncController" \
    "com.meridian.sessions::SessionController" \
    "com.meridian.sessions::SyncResolver" \
    "com.meridian.reports::ReportController" \
    "com.meridian.security.audit::AuditController" \
    "com.meridian.users::AdminUserController" \
    "com.meridian.notifications::NotificationController" \
    "com.meridian.backups::BackupController" \
    "com.meridian.approvals::ApprovalController")

  _tbl_total "$st" "$sp" "$sf" "$ss" "${_COVERAGE:-N/A}" "-"
  _T=$st; _P=$sp; _F=$sf; _S=$ss
  return $result
}
if $DOCKER_AVAILABLE; then
  run_suite "API Integration Tests" run_api_integration_tests
else
  skip_suite "API Integration Tests" "Docker unavailable (required by Testcontainers)"
fi

# ── 3. Web Unit Tests ────────────────────────────────────────────────────────
run_web_unit_tests() {
  local web="$REPO/web"
  local guards_dest="$web/src/app/core/guards"
  local stores_dest="$web/src/app/core/stores"
  local http_dest="$web/src/app/core/http"
  local copied=()

  mkdir -p "$guards_dest" "$stores_dest" "$http_dest"

  sed "s|../../web/src/app/core/guards/|./|g; \
       s|../../web/src/app/core/stores/|../stores/|g" \
      "$REPO/unit_tests/web/auth.guard.spec.ts" > "$guards_dest/auth.guard.spec.ts"
  copied+=("$guards_dest/auth.guard.spec.ts")

  sed "s|../../web/src/app/core/guards/|./|g; \
       s|../../web/src/app/core/stores/|../stores/|g" \
      "$REPO/unit_tests/web/role.guard.spec.ts" > "$guards_dest/role.guard.spec.ts"
  copied+=("$guards_dest/role.guard.spec.ts")

  sed "s|../../web/src/app/core/guards/|./|g; \
       s|../../web/src/app/core/stores/|../stores/|g" \
      "$REPO/unit_tests/web/org-scope.guard.spec.ts" > "$guards_dest/org-scope.guard.spec.ts"
  copied+=("$guards_dest/org-scope.guard.spec.ts")

  sed "s|../../web/src/app/core/stores/|./|g; \
       s|../../web/src/app/core/models/|../models/|g" \
      "$REPO/unit_tests/web/auth.store.spec.ts" > "$stores_dest/auth.store.spec.ts"
  copied+=("$stores_dest/auth.store.spec.ts")

  sed "s|../../web/src/app/core/http/|./|g; \
       s|../../web/src/app/core/stores/|../stores/|g" \
      "$REPO/unit_tests/web/auth.interceptor.spec.ts" > "$http_dest/auth.interceptor.spec.ts"
  copied+=("$http_dest/auth.interceptor.spec.ts")

  sed "s|../../web/src/app/core/http/|./|g; \
       s|../../web/src/app/core/stores/|../stores/|g" \
      "$REPO/unit_tests/web/error.interceptor.spec.ts" > "$http_dest/error.interceptor.spec.ts"
  copied+=("$http_dest/error.interceptor.spec.ts")

  sed "s|../../web/src/app/core/http/|./|g" \
      "$REPO/unit_tests/web/idempotency.interceptor.spec.ts" > "$http_dest/idempotency.interceptor.spec.ts"
  copied+=("$http_dest/idempotency.interceptor.spec.ts")

  sed "s|../../web/src/app/core/http/|./|g; \
       s|../../web/src/app/core/stores/|../stores/|g" \
      "$REPO/unit_tests/web/offline.interceptor.spec.ts" > "$http_dest/offline.interceptor.spec.ts"
  copied+=("$http_dest/offline.interceptor.spec.ts")

  sed "s|../../web/src/app/core/http/|./|g" \
      "$REPO/unit_tests/web/api.service.spec.ts" > "$http_dest/api.service.spec.ts"
  copied+=("$http_dest/api.service.spec.ts")

  sed "s|../../web/src/app/core/http/|./|g" \
      "$REPO/unit_tests/web/background-sync.service.spec.ts" > "$http_dest/background-sync.service.spec.ts"
  copied+=("$http_dest/background-sync.service.spec.ts")

  cp "$REPO/unit_tests/web/outbox.service.spec.ts" "$http_dest/outbox.service.spec.ts"
  copied+=("$http_dest/outbox.service.spec.ts")

  local app_dest="$web/src/app"
  sed "s|../../web/src/app/|./|g" \
      "$REPO/unit_tests/web/pending-route.spec.ts" > "$app_dest/pending-route.spec.ts"
  copied+=("$app_dest/pending-route.spec.ts")

  local sessions_dest="$web/src/app/sessions"
  sed "s|../../web/src/app/sessions/|./|g; \
       s|../../web/src/app/core/db/|../core/db/|g" \
      "$REPO/unit_tests/web/session-sync-keys.spec.ts" > "$sessions_dest/session-sync-keys.spec.ts"
  copied+=("$sessions_dest/session-sync-keys.spec.ts")

  # assessment-draft.service.spec.ts (sessions module) — rewrite path imports
  if [[ -f "$REPO/unit_tests/web/assessment-draft.service.spec.ts" ]]; then
    sed "s|../../web/src/app/sessions/|./|g; \
         s|../../web/src/app/core/http/|../core/http/|g; \
         s|../../web/src/app/core/db/|../core/db/|g" \
        "$REPO/unit_tests/web/assessment-draft.service.spec.ts" > "$sessions_dest/assessment-draft.service.spec.ts"
    copied+=("$sessions_dest/assessment-draft.service.spec.ts")
  fi

  # dexie.spec.ts (core/db module) — real IndexedDB is provided by Chrome headless
  local db_dest="$web/src/app/core/db"
  mkdir -p "$db_dest"
  if [[ -f "$REPO/unit_tests/web/dexie.spec.ts" ]]; then
    sed "s|../../web/src/app/core/db/|./|g" \
        "$REPO/unit_tests/web/dexie.spec.ts" > "$db_dest/dexie.spec.ts"
    copied+=("$db_dest/dexie.spec.ts")
  fi

  # Auto-discover component specs. Any *.component.spec.ts in unit_tests/web/
  # is copied to web/src/app/__test_specs__/ with a uniform path rewrite.
  # Specs must import via "../../web/src/app/…" — rewritten to "../…" here.
  local specs_dest="$web/src/app/__test_specs__"
  mkdir -p "$specs_dest"
  shopt -s nullglob
  for spec_file in "$REPO/unit_tests/web/"*.component.spec.ts; do
    local name
    name=$(basename "$spec_file")
    sed "s|../../web/src/app/|../|g" "$spec_file" > "$specs_dest/$name"
    copied+=("$specs_dest/$name")
  done
  shopt -u nullglob

  # Capture output to parse test counts.
  # The `web-test` compose service (target=test in web/Dockerfile) has Node,
  # Chromium, and all deps baked in — no host-side `npm ci` is needed.
  local tmpout; tmpout=$(mktemp)
  local result=0
  (cd "$REPO" && docker compose --profile test run --rm \
    -v "$web/src:/app/src" -w /app \
    web-test \
    npx ng test --watch=false --browsers=ChromeHeadlessCI --no-progress) \
    2>&1 | tee "$tmpout" || result=$?

  for f in "${copied[@]}"; do rm -f "$f"; done

  # Parse counts from "TOTAL: N SUCCESS [M FAILED]"
  local total_line; total_line=$(grep "^TOTAL:" "$tmpout" 2>/dev/null | tail -1 || echo "")
  local wt=0 wp=0 wf=0
  if [[ -n "$total_line" ]]; then
    wt=$(echo "$total_line" | grep -o '[0-9]* SUCCESS' | grep -o '[0-9]*' || echo 0)
    local failed_n; failed_n=$(echo "$total_line" | grep -o '[0-9]* FAILED' | grep -o '[0-9]*' || echo 0)
    wf=${failed_n:-0}; wp=$((wt - wf)); wt=$((wp + wf))
  fi
  rm -f "$tmpout"

  local cov_file="$web/coverage/meridian-web/coverage-summary.json"

  # Per-spec-file coverage table (source files, from Istanbul)
  # Maps: spec file label → source file partial path for Istanbul lookup
  declare -a SPEC_LABELS=(
    "auth.guard.spec" "role.guard.spec" "org-scope.guard.spec" "auth.store.spec"
    "auth.interceptor.spec" "error.interceptor.spec" "idempotency.interceptor.spec"
    "offline.interceptor.spec" "api.service.spec" "background-sync.service.spec"
    "outbox.service.spec" "pending-route.spec" "session-sync-keys.spec"
    "assessment-draft.service.spec" "dexie.spec"
  )
  declare -a SRC_PATHS=(
    "guards/auth.guard" "guards/role.guard" "guards/org-scope.guard" "stores/auth.store"
    "http/auth.interceptor" "http/error.interceptor" "http/idempotency.interceptor"
    "http/offline.interceptor" "http/api.service" "http/background-sync.service"
    "http/outbox.service" "app.routes" "sessions/session-sync-keys"
    "sessions/assessment-draft.service" "core/db/dexie"
  )

  # Auto-register component specs discovered above. Uses Istanbul's "component.ts"
  # key — derived from the spec filename: "login.component.spec.ts" → "login.component".
  shopt -s nullglob
  for spec_file in "$REPO/unit_tests/web/"*.component.spec.ts; do
    local spec_name
    spec_name=$(basename "$spec_file" .ts)            # login.component.spec
    local src_name="${spec_name%.spec}"                # login.component
    SPEC_LABELS+=("$spec_name")
    SRC_PATHS+=("$src_name")
  done
  shopt -u nullglob

  # Compute suite coverage as average of per-tested-file coverages (not global total,
  # which includes all app files not exercised by these 5 spec files).
  _tbl_header
  local cov_sum=0 cov_n=0
  local i=0
  for label in "${SPEC_LABELS[@]}"; do
    local src="${SRC_PATHS[$i]}"
    local cov; cov=$(_istanbul_src_pct "$cov_file" "$src"); cov="${cov:-N/A}"
    _tbl_row "$label" "-" "-" "-" "-" "$cov" "-"
    [[ "$cov" =~ ^[0-9]+$ ]] && { cov_sum=$((cov_sum + cov)); ((cov_n++)); }
    ((i++))
  done
  if [[ $cov_n -gt 0 ]]; then
    _COVERAGE=$((cov_sum / cov_n))
  else
    _COVERAGE="N/A"
  fi

  _tbl_total "$wt" "$wp" "$wf" "0" "${_COVERAGE:-N/A}" "-"
  _T=$wt; _P=$wp; _F=$wf; _S=0
  return $result
}
if $DOCKER_AVAILABLE; then
  run_suite "Web Unit Tests" run_web_unit_tests
else
  skip_suite "Web Unit Tests" "Docker unavailable (required to run Node/Chrome test container)"
fi

# ── 4. E2E Tests ─────────────────────────────────────────────────────────────
run_e2e_tests() {
  local e2e_dir="$REPO/e2e_tests"
  local tmpout; tmpout=$(mktemp)
  local result=0
  # Pre-install e2e deps in a throwaway layer so the runner invocation itself
  # is a clean `npx playwright test` (no install at run time in the test step).
  docker run --rm \
    -v "$e2e_dir:/app" -w /app \
    mcr.microsoft.com/playwright:v1.44.0-jammy \
    sh -c 'test -d node_modules || npm ci --no-audit --no-fund' >/dev/null 2>&1 || true

  docker run --rm \
    -e API_URL="${API_URL:-https://host.docker.internal:8443}" \
    -v "$e2e_dir:/app" -w /app \
    mcr.microsoft.com/playwright:v1.44.0-jammy \
    npx playwright test \
    2>&1 | tee "$tmpout" || result=$?

  # Count ✓ / ✗ lines from Playwright output
  local ep=0 ef=0
  ep=$(grep -c "^ *[✓✔] " "$tmpout" 2>/dev/null || echo 0)
  ef=$(grep -c "^ *[✗✘×] \|FAILED\b" "$tmpout" 2>/dev/null || echo 0)
  local et=$((ep + ef))

  # Parse per-test lines for the detail table
  _tbl_header
  local line_count=0
  while IFS= read -r line; do
    if [[ "$line" =~ ^[[:space:]]*([✓✔✗✘×])[[:space:]](.+)$ ]]; then
      local icon="${BASH_REMATCH[1]}" name="${BASH_REMATCH[2]}"
      local fail=0; [[ "$icon" == "✗" || "$icon" == "✘" || "$icon" == "×" ]] && fail=1
      local pass=$((1 - fail))
      _tbl_row "$name" "1" "$pass" "$fail" "0" "N/A" "-"
      ((line_count++))
    fi
  done < "$tmpout"
  [[ $line_count -eq 0 ]] && echo "  (no individual test output captured)"

  rm -f "$tmpout"

  local cov_file="$e2e_dir/coverage/coverage-summary.json"
  if [[ -f "$cov_file" ]]; then
    _COVERAGE=$(docker run --rm \
      -v "$REPO:/work" -w /work \
      node:20-alpine node -e "
try{
  var s=require('${cov_file#$REPO/}'),t=s.total;
  if(t&&t.statements&&t.statements.total>0) console.log(Math.round(t.statements.pct));
  else console.log('N/A');
}catch(e){console.log('N/A');}
" 2>/dev/null || echo "N/A")
  else
    _COVERAGE="N/A"
  fi

  _tbl_total "$et" "$ep" "$ef" "0" "${_COVERAGE:-N/A}" "-"
  _T=$et; _P=$ep; _F=$ef; _S=0
  return $result
}

if $RUN_E2E; then
  if $DOCKER_AVAILABLE; then
    run_suite "E2E Tests (Playwright)" run_e2e_tests
  else
    skip_suite "E2E Tests (Playwright)" "Docker unavailable (required for Playwright container)"
  fi
else
  skip_suite "E2E Tests (Playwright)" "Pass --e2e to include (requires running services)"
fi

# ── Summary ────────────────────────────────────────────────────────────────────
echo
echo "╔══════════════════════════════════════════════════════════════════════════════════╗"
echo "║                              TEST SUITE RESULTS                                  ║"
echo "╠══════════════════════════════════════════════════════════════════════════════════╣"
printf "║  %-6s  %-30s  %6s %6s %6s %6s  %7s  %7s  ║\n" \
  "Result" "Suite" "Total" "Pass" "Fail" "Skip" "Cov" "Time"
echo "╠══════════════════════════════════════════════════════════════════════════════════╣"

for i in "${!SUITE_ORDER[@]}"; do
  local_suite="${SUITE_ORDER[$i]}"
  local_status="${SUITE_STATUS[$i]}"
  local_secs="${SUITE_TIME[$i]}"
  local_cov="${SUITE_COVERAGE[$i]}"
  local_t="${SUITE_T[$i]:-0}"
  local_p="${SUITE_P[$i]:-0}"
  local_f="${SUITE_F[$i]:-0}"
  local_s="${SUITE_S[$i]:-0}"

  case "$local_status" in
    PASS) icon="✓ PASS" ;;
    FAIL) icon="✗ FAIL" ;;
    SKIP) icon="- SKIP" ;;
    *)    icon="? ????" ;;
  esac

  if   [[ $local_secs -ge 60 ]]; then time_str="$(( local_secs/60 ))m$(( local_secs%60 ))s"
  elif [[ $local_secs -eq 0  ]]; then time_str="    -"
  else time_str="${local_secs}s"
  fi

  [[ "$local_cov" =~ ^[0-9]+$ ]] && cov_str="${local_cov}%" || cov_str="$local_cov"

  local_t_disp="$local_t"; [[ "$local_status" == "SKIP" ]] && local_t_disp="-"
  local_p_disp="$local_p"; [[ "$local_status" == "SKIP" ]] && local_p_disp="-"
  local_f_disp="$local_f"; [[ "$local_status" == "SKIP" ]] && local_f_disp="-"
  local_s_disp="$local_s"; [[ "$local_status" == "SKIP" ]] && local_s_disp="-"

  printf "║  %-6s  %-30s  %6s %6s %6s %6s  %7s  %7s  ║\n" \
    "$icon" "$local_suite" "$local_t_disp" "$local_p_disp" "$local_f_disp" "$local_s_disp" "$cov_str" "$time_str"
done

# Grand totals
OVERALL_SUM=0; OVERALL_N=0
for i in "${!SUITE_ORDER[@]}"; do
  c="${SUITE_COVERAGE[$i]}"; [[ "${SUITE_STATUS[$i]}" == "SKIP" ]] && continue
  [[ "$c" =~ ^[0-9]+$ ]] && { OVERALL_SUM=$((OVERALL_SUM+c)); ((OVERALL_N++)); }
done
if [[ $OVERALL_N -gt 0 ]]; then
  OVERALL_PCT=$((OVERALL_SUM / OVERALL_N))
  OVERALL_COV="${OVERALL_PCT}%"
else
  OVERALL_COV="N/A"
fi

echo "╠══════════════════════════════════════════════════════════════════════════════════╣"
printf "║  %-6s  %-30s  %6d %6d %6d %6d  %7s  %7s  ║\n" \
  "━━━━━━" "TOTAL" "$GRAND_T" "$GRAND_P" "$GRAND_F" "$GRAND_S" "$OVERALL_COV" "-"
echo "╠══════════════════════════════════════════════════════════════════════════════════╣"
printf "║  %-80s  ║\n" \
  "  Suites — Passed: $PASS_COUNT   Failed: $FAIL_COUNT   Skipped: $SKIP_COUNT"
echo "╚══════════════════════════════════════════════════════════════════════════════════╝"
echo

if [[ $FAIL_COUNT -gt 0 ]]; then
  echo "Some test suites FAILED. See per-suite output above for details."
  exit 1
else
  echo "All executed test suites passed."
  exit 0
fi
