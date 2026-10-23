#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-functional}"
ROWS="${P3_PERFORMANCE_ROWS:-500000}"
HEAP="${P3_PERFORMANCE_HEAP:-2g}"

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || true)}"
export TESTCONTAINERS_RYUK_DISABLED="${TESTCONTAINERS_RYUK_DISABLED:-true}"

if [[ -z "${JAVA_HOME}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  echo "JAVA_HOME for JDK 21 is required" >&2
  exit 1
fi

cd "${ROOT}/data-service"
COMMIT="$(git -C "${ROOT}" rev-parse --short HEAD)"
BRANCH="$(git -C "${ROOT}" rev-parse --abbrev-ref HEAD)"
STARTED="$(date '+%Y-%m-%d %H:%M:%S %Z')"
STARTED_EPOCH="$(date +%s)"

print_environment() {
  echo "P3_ACCEPTANCE mode=${MODE}"
  echo "P3_ACCEPTANCE commit=${COMMIT} branch=${BRANCH}"
  echo "P3_ACCEPTANCE java=$("${JAVA_HOME}/bin/java" -version 2>&1 | head -1)"
  echo "P3_ACCEPTANCE os=$(uname -srm)"
  echo "P3_ACCEPTANCE postgres=pgvector/pgvector:pg16"
  echo "P3_ACCEPTANCE testcontainers_ryuk_disabled=${TESTCONTAINERS_RYUK_DISABLED}"
  echo "P3_ACCEPTANCE started=${STARTED}"
  if command -v docker >/dev/null 2>&1; then
    echo "P3_ACCEPTANCE docker=$(docker version --format '{{.Server.Version}}' 2>/dev/null || echo unavailable)"
  else
    echo "P3_ACCEPTANCE docker=missing"
  fi
}

run_functional() {
  echo "P3_ACCEPTANCE command=./mvnw -q -Dtest=P3FunctionalAcceptanceTest -Dp3.acceptance=true test"
  ./mvnw -q \
    -Dtest=P3FunctionalAcceptanceTest \
    -Dp3.acceptance=true \
    -Dp3.commit="${COMMIT}" \
    test
}

run_performance() {
  mkdir -p target/p3-acceptance
  rm -f target/p3-acceptance/performance-summary.json
  echo "P3_ACCEPTANCE rows=${ROWS} heap=${HEAP}"
  echo "P3_ACCEPTANCE command=./mvnw -q -Dtest=P3PerformanceAcceptanceTest -Dp3.performance=true -Dp3.performance.rows=${ROWS} test"
  ./mvnw -q \
    -Dtest=P3PerformanceAcceptanceTest \
    -Dp3.performance=true \
    -Dp3.performance.rows="${ROWS}" \
    -Dp3.commit="${COMMIT}" \
    -DargLine="-Xmx${HEAP}" \
    test
  echo "P3_ACCEPTANCE performance_summary=target/p3-acceptance/performance-summary.json"
  cat target/p3-acceptance/performance-summary.json
}

cleanup() {
  rm -rf target/p3-acceptance
  echo "P3_ACCEPTANCE cleaned target/p3-acceptance; Testcontainers databases are removed with each test JVM"
}

print_environment
case "${MODE}" in
  functional)
    run_functional
    ;;
  performance)
    run_performance
    ;;
  all)
    run_functional
    run_performance
    ;;
  cleanup)
    cleanup
    ;;
  *)
    echo "usage: $0 [functional|performance|all|cleanup]" >&2
    exit 2
    ;;
esac
echo "P3_ACCEPTANCE finished=$(date '+%Y-%m-%d %H:%M:%S %Z') status=passed"
echo "P3_ACCEPTANCE elapsed_s=$(( $(date +%s) - STARTED_EPOCH ))"
