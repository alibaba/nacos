#!/usr/bin/env bash
#
# Copyright 1999-2026 Alibaba Group Holding Ltd.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Stages of the Native Image CI job (.github/workflows/native-image.yml). The workflow only calls
# this script, so what is built, tested and reported can change without editing the workflow.
# The stages also reproduce the job on any Linux host that has a GraalVM 25 JAVA_HOME with
# native-image, Maven, psql, curl, openssl and an EMPTY PostgreSQL database that the script
# initializes.
#
# Usage: native-ci.sh <prepare|build|start|smoke|integration|report|all>
#
# Environment (all optional):
#   NATIVE_CI_VARIANT       default | exact. 'exact' builds with --exact-reachability-metadata
#                           and runs with -XX:MissingRegistrationReportingMode=Warn so that every
#                           dynamic access without metadata is reported instead of failing.
#   NATIVE_CI_HEAP          Native builder heap, default 10g
#   NATIVE_CI_PARALLELISM   Native builder threads, default 4
#   NATIVE_CI_DB_HOST, NATIVE_CI_DB_PORT, NATIVE_CI_DB_NAME, NATIVE_CI_DB_USER,
#   NATIVE_CI_DB_PASSWORD   PostgreSQL connection, default 127.0.0.1:5432 nacos/nacos/nacos
#   NATIVE_CI_WORK          scratch directory, default ${RUNNER_TEMP:-/tmp}/native-ci
#   NATIVE_CI_OUT           receives logs and reports, default <repository>/ci-out
#   MVN                     Maven command, default mvn

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="${NATIVE_CI_WORK:-${RUNNER_TEMP:-/tmp}/native-ci}"
OUT="${NATIVE_CI_OUT:-${ROOT}/ci-out}"
STATE="${WORK}/state.env"
VARIANT="${NATIVE_CI_VARIANT:-default}"
DB_HOST="${NATIVE_CI_DB_HOST:-127.0.0.1}"
DB_PORT="${NATIVE_CI_DB_PORT:-5432}"
DB_NAME="${NATIVE_CI_DB_NAME:-nacos}"
DB_USER="${NATIVE_CI_DB_USER:-nacos}"
DB_PASSWORD="${NATIVE_CI_DB_PASSWORD:-nacos}"
MVN="${MVN:-mvn}"
METADATA_TOOL="${ROOT}/test/scripts/native-metadata.py"

cd "$ROOT"

log() {
    printf '\n==> %s\n' "$*"
}

# Persist a value for the later stages, which run in separate processes.
remember() {
    if [ "${3:-}" = "secret" ] && [ -n "${GITHUB_ACTIONS:-}" ]; then
        echo "::add-mask::$2"
    fi
    printf '%s=%q\n' "$1" "$2" >> "$STATE"
}

load_state() {
    if [ ! -f "$STATE" ]; then
        echo "Missing ${STATE}; run the prepare stage first" >&2
        exit 1
    fi
    set -a
    # shellcheck disable=SC1090
    . "$STATE"
    set +a
}

stage_prepare() {
    mkdir -p "$WORK" "$OUT"
    : > "$STATE"
    chmod 600 "$STATE"

    log "Check the bundled Native metadata"
    python3 -I "$METADATA_TOOL" selftest
    python3 -I "$METADATA_TOOL" check

    log "Initialize the database"
    # Use the tracked source: distribution/conf/*.sql are copies made by a Maven build.
    local schema="${ROOT}/plugin-default-impl/nacos-default-datasource-plugin/nacos-datasource-plugin-postgresql/src/main/resources/META-INF/pg-schema.sql"
    if [ ! -f "$schema" ]; then
        echo "Missing ${schema}" >&2
        exit 1
    fi
    command -v psql >/dev/null \
        || sudo apt-get install -y --no-install-recommends postgresql-client
    export PGPASSWORD="$DB_PASSWORD"
    for _ in $(seq 1 60); do
        if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -qc 'select 1' \
            >/dev/null 2>&1; then
            break
        fi
        sleep 2
    done
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -q \
        -f "$schema"

    log "Generate disposable credentials"
    remember NACOS_TEST_AUTH_ADMIN_USERNAME nacos
    remember NACOS_TEST_AUTH_ADMIN_PASSWORD "NacosIt-$(openssl rand -hex 16)-A1!" secret
    remember NACOS_TEST_AUTH_CLIENT_USERNAME nacos_it_client_rw
    remember NACOS_TEST_AUTH_CLIENT_PASSWORD "NacosIt-$(openssl rand -hex 16)-C1!" secret
    remember NACOS_TEST_AUTH_READONLY_USERNAME nacos_it_client_readonly
    remember NACOS_TEST_AUTH_READONLY_PASSWORD "NacosIt-$(openssl rand -hex 16)-R1!" secret
    remember NACOS_TEST_AUTH_NO_PERMISSION_USERNAME nacos_it_client_no_permission
    remember NACOS_TEST_AUTH_NO_PERMISSION_PASSWORD "NacosIt-$(openssl rand -hex 16)-N1!" secret
    remember NATIVE_TOKEN_SECRET "$(openssl rand -base64 48 | tr -d '\n')" secret
    remember NATIVE_SERVER_IDENTITY_KEY "nacos-it-$(openssl rand -hex 8)" secret
    remember NATIVE_SERVER_IDENTITY_VALUE "$(openssl rand -hex 32)" secret

    log "Select the Native build variant: ${VARIANT}"
    printf 'spring.sql.init.platform=postgresql\n' > "$WORK/build.properties"
    case "$VARIANT" in
        default)
            remember NATIVE_BUILD_OPTIONS ""
            remember NACOS_NATIVE_OPTS "-Xmx1g"
            ;;
        exact)
            remember NATIVE_BUILD_OPTIONS "$ROOT/test/scripts/native-ci-exact.args"
            remember NACOS_NATIVE_OPTS "-Xmx1g -XX:MissingRegistrationReportingMode=Warn"
            ;;
        *)
            echo "Unknown variant: ${VARIANT}" >&2
            exit 2
            ;;
    esac
}

stage_build() {
    load_state

    log "Install the JVM artifacts that the integration test modules resolve"
    # The Java SDK suite needs the shaded client, which only release-nacos produces.
    "$MVN" -B -T 2 -Prelease-nacos install -Dmaven.test.skip=true -Drat.skip=true \
        -pl test/openapi-test,test/java-sdk-test,test/maintainer-sdk-test -am

    log "Build the Native distribution"
    "$MVN" -B '-Pnative,release-native,!dev' -pl distribution -am clean package \
        -Dmaven.test.skip=true -Drat.skip=true \
        -Dnacos.native.config="${WORK}/build.properties" \
        -Dnacos.native.standalone=true \
        -Dnacos.native.build.heap="${NATIVE_CI_HEAP:-10g}" \
        -Dnacos.native.build.parallelism="${NATIVE_CI_PARALLELISM:-4}" \
        -Dnacos.native.options="${NATIVE_BUILD_OPTIONS}"

    ls -la "$ROOT"/distribution/target/nacos-server-*-native-*.tar.gz \
        "$ROOT/bootstrap/target/native/nacos-server"
    mkdir -p "$OUT/build"
    cp "$ROOT"/bootstrap/target/native/{options.args,classpath.args,aot-manifest.properties,build.properties} \
        "$OUT/build/"
}

stage_start() {
    load_state

    log "Unpack and configure the Native distribution"
    local archive
    archive="$(find "$ROOT/distribution/target" -maxdepth 1 -name 'nacos-server-*-native-*.tar.gz' \
        | head -n 1)"
    if [ -z "$archive" ]; then
        echo "No Native distribution archive in distribution/target; run the build stage" >&2
        exit 1
    fi
    rm -rf "${WORK:?}/home"
    mkdir -p "${WORK}/home"
    tar -xzf "$archive" -C "${WORK}/home"
    remember NACOS_HOME "${WORK}/home/nacos"
    local home="${WORK}/home/nacos"
    cat >> "${home}/conf/application.properties" <<EOF

spring.sql.init.platform=postgresql
nacos.plugin.datasource.db.num=1
nacos.plugin.datasource.db.url.0=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?currentSchema=public
nacos.plugin.datasource.db.user=${DB_USER}
nacos.plugin.datasource.db.password=${DB_PASSWORD}
nacos.plugin.auth.nacos.token.secret.key=${NATIVE_TOKEN_SECRET}
nacos.core.auth.server.identity.key=${NATIVE_SERVER_IDENTITY_KEY}
nacos.core.auth.server.identity.value=${NATIVE_SERVER_IDENTITY_VALUE}
EOF

    log "Executable"
    ls -la "${home}/target"
    ldd "${home}/target/nacos-server" || true

    log "Start the Native Server (NACOS_NATIVE_OPTS=${NACOS_NATIVE_OPTS})"
    bash "${home}/bin/startup-native.sh"
    local started
    started="$(date +%s%N)"
    if ! timeout 180s bash -c \
        'until curl --silent --fail http://127.0.0.1:8080/v3/console/health/liveness >/dev/null; do sleep 0.2; done'; then
        echo "The Native Server did not become live" >&2
        tail -n 300 "${home}/logs/startup.log" || true
        exit 1
    fi
    echo "Native Server live after $(( ($(date +%s%N) - started) / 1000000 )) ms"
    # Like it-new.yml: MCP lifecycle APIs are unavailable until the background cutover is persisted.
    if ! timeout 120s bash -c \
        "until grep -R -q 'Persisted permanent MCP LIFECYCLE_MANAGED marker' '${home}/logs' 2>/dev/null; do sleep 1; done"; then
        echo "WARNING: the MCP LIFECYCLE_MANAGED marker was not logged within 120 s" >&2
    fi
}

stage_smoke() {
    load_state
    log "Bootstrap the administrator and the test identities"
    bash "$ROOT/test/scripts/auth-it-identity.sh" bootstrap
    bash "$ROOT/test/scripts/auth-it-identity.sh" prepare
    log "Smoke test"
    bash "$ROOT/test/scripts/native-smoke.sh"
}

run_suite() {
    local name="$1"
    shift
    log "Integration suite: ${name}"
    "$MVN" -B "$@" -DskipTests=false -Dmaven.test.failure.ignore=true -Drat.skip=true \
        -Dnacos.test.auth.enabled=true \
        -Dnacos.test.auth.admin.username="$NACOS_TEST_AUTH_ADMIN_USERNAME" \
        -Dnacos.test.auth.client.username="$NACOS_TEST_AUTH_CLIENT_USERNAME" \
        -Dnacos.test.auth.readonly.username="$NACOS_TEST_AUTH_READONLY_USERNAME" \
        -Dnacos.test.auth.no-permission.username="$NACOS_TEST_AUTH_NO_PERMISSION_USERNAME"
}

stage_integration() {
    load_state
    local status=0
    run_suite openapi -pl test/openapi-test verify -Pintegration-test || status=1
    run_suite java-sdk -pl test/java-sdk-test verify -Pjava-sdk-integration-test \
        '-Dit.test=ConfigServiceJavaSdkITCase,NamingServiceJavaSdkITCase,LockServiceJavaSdkITCase,AuthEnabledJavaSdkITCase' \
        || status=1
    run_suite maintainer-sdk -pl test/maintainer-sdk-test verify \
        -Pmaintainer-sdk-integration-test || status=1
    return "$status"
}

stage_report() {
    load_state
    NACOS_HOME="${NACOS_HOME:-}"
    mkdir -p "$OUT/logs" "$OUT/reports"
    if [ -n "$NACOS_HOME" ]; then
        cp "${NACOS_HOME}"/logs/*.log "$OUT/logs/" 2>/dev/null || true
    fi
    cp "$ROOT"/test/*/target/failsafe-reports/TEST-*.xml "$OUT/reports/" 2>/dev/null || true

    log "Resource usage of the Native Server"
    local pid
    pid="$(pgrep -f 'target/nacos-server' | head -n 1 || true)"
    if [ -n "$pid" ]; then
        ps -o pid=,rss=,etime=,args= -p "$pid" | cut -c1-160 || true
        echo "RSS: $(( $(awk '/VmRSS/ {print $2}' "/proc/${pid}/status") / 1024 )) MB"
    fi

    log "Integration test results"
    local status=0
    python3 -I - "$ROOT" "$ROOT/test/scripts/native-ci-baseline.txt" <<'PY' || status=1
import fnmatch
import glob
import sys
import xml.etree.ElementTree as ET

root, baseline_file = sys.argv[1], sys.argv[2]
baseline = [line.strip() for line in open(baseline_file)
            if line.strip() and not line.startswith("#")]
executed, failed = 0, []
for report in sorted(glob.glob(root + "/test/*/target/failsafe-reports/TEST-*.xml")):
    for case in ET.parse(report).getroot().iter("testcase"):
        if case.find("skipped") is not None:
            continue
        executed += 1
        name = case.get("classname").split(".")[-1] + "." + case.get("name")
        for kind in ("failure", "error"):
            item = case.find(kind)
            if item is not None:
                failed.append((name, (item.get("message") or "").replace("\n", " ")[:200]))
known = [f for f in failed if any(fnmatch.fnmatch(f[0], p) for p in baseline)]
new = [f for f in failed if f not in known]
print("%d tests executed, %d failed (%d are known failures of the JVM server in this setup)"
      % (executed, len(failed), len(known)))
by_class = {}
for name, message in new:
    by_class.setdefault(name.split(".")[0], []).append((name, message))
for test_class, items in sorted(by_class.items()):
    print("NEW FAILURES in %s: %d" % (test_class, len(items)))
    for name, message in items[:3]:
        print("    %s: %s" % (name, message))
sys.exit(1 if executed == 0 or new else 0)
PY

    log "Missing registrations reported by the Native executable"
    local startup="${NACOS_HOME}/logs/startup.log"
    if [ ! -f "$startup" ]; then
        echo "No Native Server log; the server did not start" >&2
        return 1
    fi
    python3 -I "$METADATA_TOOL" collect-missing "$startup" --output "$OUT/missing-registrations" \
        || true
    if [ "$VARIANT" = "exact" ]; then
        python3 -I "$METADATA_TOOL" refresh \
            --classpath "$ROOT/bootstrap/target/native/classpath.args" \
            --output "$OUT/missing-registrations/curated-delta.json" \
            "$OUT/missing-registrations" 2> "$OUT/missing-registrations/dropped.txt" || true
        if [ -f "$OUT/missing-registrations/curated-delta.json" ]; then
            echo "Entries to add to the bundled metadata:"
            cat "$OUT/missing-registrations/curated-delta.json"
            if grep -q '"type"\|"glob"\|"bundle"' "$OUT/missing-registrations/curated-delta.json"; then
                status=1
            fi
        fi
    fi

    log "Tail of the Native Server log"
    tail -n 200 "$startup" || true
    return "$status"
}

case "${1:-}" in
    prepare) stage_prepare ;;
    build) stage_build ;;
    start) stage_start ;;
    smoke) stage_smoke ;;
    integration) stage_integration ;;
    report) stage_report ;;
    all)
        stage_prepare
        stage_build
        stage_start
        stage_smoke
        stage_integration || true
        stage_report
        ;;
    *)
        echo "Usage: $0 <prepare|build|start|smoke|integration|report|all>" >&2
        exit 2
        ;;
esac
