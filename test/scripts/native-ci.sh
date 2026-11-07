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

# Stages of the Native Image CI job, see bootstrap/README-AOT.md for scenarios.
# Usage: native-ci.sh <prepare|build|smoke|integration|scenario|report|all>
#
# Environment (all optional):
#   NATIVE_CI_SCENARIO      standard | features | cluster, default standard
#   NATIVE_CI_VARIANT       default | exact, default default
#   NATIVE_CI_DATABASE      postgresql | mysql, default postgresql
#   NATIVE_CI_SUITE         full | core, default full
#   NATIVE_CI_ARCHIVE       distribution archive, default the Native one in distribution/target
#   NATIVE_CI_TEST_OPTIONS  extra options; with -Dnacos.native.test.mode=jvm the stages
#                           smoke, integration and scenario run without a Native build
#   NATIVE_CI_HEAP          Native builder heap, default 10g
#   NATIVE_CI_PARALLELISM   Native builder threads, default 4
#   NATIVE_CI_WORK          scratch directory, default ${RUNNER_TEMP:-/tmp}/native-ci
#   NATIVE_CI_OUT           receives logs and reports, default <repository>/target/ci-out
#   MVN                     Maven command, default mvn

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="${NATIVE_CI_WORK:-${RUNNER_TEMP:-/tmp}/native-ci}"
OUT="${NATIVE_CI_OUT:-${ROOT}/target/ci-out}"
STATE="${WORK}/state.env"
VARIANT="${NATIVE_CI_VARIANT:-default}"
SCENARIO="${NATIVE_CI_SCENARIO:-standard}"
DATABASE="${NATIVE_CI_DATABASE:-postgresql}"
SUITE="${NATIVE_CI_SUITE:-full}"
case "$SCENARIO" in
    standard | features | cluster) ;;
    *)
        echo "Unknown scenario: ${SCENARIO}" >&2
        exit 2
        ;;
esac
case "$DATABASE" in
    postgresql | mysql) ;;
    *)
        echo "Unknown database: ${DATABASE}" >&2
        exit 2
        ;;
esac
case "$SUITE" in
    full | core) ;;
    *)
        echo "Unknown suite: ${SUITE}" >&2
        exit 2
        ;;
esac
MVN="${MVN:-mvn}"
ENVIRONMENT_DIR="${WORK}/environment"
# Where NativeEnvironmentMain puts the home of its Server and the database log.
NACOS_HOME="${ENVIRONMENT_DIR}/environment/nacos"
METADATA_TOOL="${ROOT}/test/scripts/native-metadata.py"

# The OpenAPI classes of the 'core' suite.
CORE_OPENAPI_CLASSES="ConfigAdminApiOpenApiITCase,ConfigConsoleApiOpenApiITCase,ConfigOpenApiITCase"
CORE_OPENAPI_CLASSES+=",ConfigHistoryAdminApiOpenApiITCase,ConfigListenerAdminApiOpenApiITCase"
CORE_OPENAPI_CLASSES+=",InstanceRegisterOpenApiITCase,InstanceAdminApiOpenApiITCase"
CORE_OPENAPI_CLASSES+=",ServiceAdminApiOpenApiITCase,NamespaceAdminApiOpenApiITCase"
CORE_OPENAPI_CLASSES+=",CoreStateAdminApiOpenApiITCase,UserLoginAuthApiITCase,DefaultAuthApiITCase"

# The Java SDK classes that run against a single, current Server. Left out are the scenarios that
# need an older Server (upgrade migration), a released SDK (binary compatibility) or the Jackson 3
# profile.
JAVA_SDK_CLASSES="ConfigServiceJavaSdkITCase,NamingServiceJavaSdkITCase,LockServiceJavaSdkITCase"
JAVA_SDK_CLASSES+=",AuthEnabledJavaSdkITCase,AiServiceJavaSdkITCase"
JAVA_SDK_CLASSES+=",AiTransportResourceMatrixJavaSdkITCase,McpHttpClientJavaSdkITCase"
JAVA_SDK_CLASSES+=",AgentPublishJavaSdkITCase,AgentRequestBuilderJavaSdkITCase"
JAVA_SDK_CLASSES+=",AgentModelValidationJavaSdkITCase,AgentDiscoveryServiceJavaSdkITCase"
JAVA_SDK_CLASSES+=",A2aRadRoutingJavaSdkITCase"

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

# The archive of the Native distribution that the build stage made.
find_archive() {
    local archive="${NATIVE_CI_ARCHIVE:-}"
    if [ -n "$archive" ]; then
        echo "$archive"
        return
    fi
    archive="$(find "$ROOT/distribution/target" -maxdepth 1 -name 'nacos-server-*-native-*.tar.gz' \
        | head -n 1)"
    if [ -z "$archive" ]; then
        echo "No Native distribution archive in distribution/target; run the build stage" >&2
        exit 1
    fi
    echo "$archive"
}

stage_prepare() {
    mkdir -p "$WORK" "$OUT"
    : > "$STATE"
    chmod 600 "$STATE"

    log "Native CI: scenario=${SCENARIO} database=${DATABASE} variant=${VARIANT} suite=${SUITE}"

    log "Check the bundled Native metadata"
    python3 -I "$METADATA_TOOL" selftest
    python3 -I "$METADATA_TOOL" check

    # The scenario decides the structure of the build and the tests that run on it. The AI registries
    # are checked when a Server starts, so every Server of the run repeats their switches. The
    # Kubernetes sync is a switch of the build only; its test turns it on.
    log "Select the scenario: ${SCENARIO}"
    local build_properties=("spring.sql.init.platform=${DATABASE}")
    local standalone=true suites=true smoke_tests="NativeServerSmokeITCase" scenario_tests=""
    local server_properties=""
    case "$SCENARIO" in
        standard) ;;
        features)
            server_properties="nacos.ai.mcp.registry.enabled=true"
            server_properties+=" nacos.ai.skill.registry.enabled=true nacos.ai.ard.enabled=true"
            # The ARD tests of the OpenAPI suite expect the catalog on the port of the adaptor, as
            # .github/workflows/it-new.yml configures it.
            server_properties+=" nacos.ai.ard.catalog.base-url=http://127.0.0.1:9080"
            build_properties+=(
                "nacos.ai.mcp.registry.enabled=true"
                "nacos.ai.skill.registry.enabled=true"
                "nacos.ai.ard.enabled=true"
                "nacos.k8s.sync.enabled=true"
            )
            scenario_tests="NativeAiRegistryITCase,NativeDnsITCase"
            scenario_tests+=",NativeKubernetesSyncITCase,NativeThirdPartyPluginITCase"
            ;;
        cluster)
            standalone=false
            suites=false
            smoke_tests=""
            scenario_tests="NativeClusterITCase"
            ;;
    esac
    printf '%s\n' "${build_properties[@]}" > "$WORK/build.properties"
    remember NATIVE_STANDALONE "$standalone"
    remember RUN_SUITES "$suites"
    remember SMOKE_TESTS "$smoke_tests"
    remember SCENARIO_TESTS "$scenario_tests"
    remember SERVER_PROPERTIES "$server_properties"

    log "Select the Native build variant: ${VARIANT}"
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

    log "Install the JVM artifacts that the test modules resolve"
    # The Java SDK suite needs the shaded client, which only release-nacos produces.
    "$MVN" -B -T 2 -Prelease-nacos install -Dmaven.test.skip=true -Drat.skip=true \
        -pl test/native-test,test/native-test-plugin,test/openapi-test,test/java-sdk-test,test/maintainer-sdk-test \
        -am

    # Jars that do not belong to the Server and go into the build, as the plugins of a deployment do.
    # Their metadata is part of the jar. The directory is outside target, which the build cleans.
    local plugin_dir="${WORK}/native-plugins" plugin_options=()
    rm -rf "$plugin_dir"
    case "$SCENARIO" in
        features)
            log "A third-party plugin for the build: the sample plugin of native-test-plugin"
            mkdir -p "$plugin_dir"
            cp "$ROOT"/test/native-test-plugin/target/native-test-plugin-*[0-9].jar "$plugin_dir/"
            plugin_options=("-Dnacos.native.plugins=${plugin_dir}")
            ;;
    esac

    log "Build the Native distribution"
    "$MVN" -B '-Pnative,release-native,!dev' -pl distribution -am clean package \
        -Dmaven.test.skip=true -Drat.skip=true \
        -Dnacos.native.config="${WORK}/build.properties" \
        -Dnacos.native.standalone="${NATIVE_STANDALONE}" \
        -Dnacos.native.build.heap="${NATIVE_CI_HEAP:-10g}" \
        -Dnacos.native.build.parallelism="${NATIVE_CI_PARALLELISM:-4}" \
        -Dnacos.native.options="${NATIVE_BUILD_OPTIONS}" \
        "${plugin_options[@]}"

    log "Check that the bundled Native metadata repeats nothing that the build provides"
    python3 -I "$METADATA_TOOL" check --classpath "$ROOT/bootstrap/target/native/classpath.args"

    ls -la "$ROOT"/distribution/target/nacos-server-*-native-*.tar.gz \
        "$ROOT/bootstrap/target/native/nacos-server"
    mkdir -p "$OUT/build"
    cp "$ROOT"/bootstrap/target/native/{options.args,classpath.args,aot-manifest.properties,build.properties} \
        "$OUT/build/"

    log "Resolve the classpath of the test environment"
    "$MVN" -B -q -pl test/native-test dependency:build-classpath \
        -Dmdep.outputFile="${WORK}/native-test.classpath"
}

# The system properties that make a Server repeat the structural choices of the build, which it
# checks when it starts. One option per line.
server_property_options() {
    local item
    for item in ${SERVER_PROPERTIES}; do
        printf '%s\n' "-Dnacos.native.test.property.${item}"
    done
}

# Runs tests of native-test. Each of them starts the database and the Servers that it needs, from the
# archive that the build made.
run_native_tests() {
    local tests="$1" archive
    local -a property_options extra_options
    archive="$(find_archive)"
    mapfile -t property_options < <(server_property_options)
    read -r -a extra_options <<< "${NATIVE_CI_TEST_OPTIONS:-}"
    "$MVN" -B -pl test/native-test verify -Pnative-integration-test -DskipTests=false \
        -Drat.skip=true "-Dit.test=${tests}" \
        -Dnacos.native.test.database="$DATABASE" \
        -Dnacos.native.test.distribution="$archive" \
        "-Dnacos.native.test.opts=${NACOS_NATIVE_OPTS}" \
        "-Dnacos.native.test.work=${WORK}/native-test" \
        "${property_options[@]}" "${extra_options[@]}"
}

stage_smoke() {
    load_state
    if [ -z "$SMOKE_TESTS" ]; then
        log "The ${SCENARIO} scenario has no smoke test"
        return 0
    fi
    log "Smoke test: a ${DATABASE} database and a Native Server of its own, started by Testcontainers"
    run_native_tests "$SMOKE_TESTS"
}

stage_scenario() {
    load_state
    if [ -z "$SCENARIO_TESTS" ]; then
        log "The ${SCENARIO} scenario has no tests of its own"
        return 0
    fi
    log "Tests of the ${SCENARIO} scenario: ${SCENARIO_TESTS}"
    run_native_tests "$SCENARIO_TESTS"
}

# Keeps a secret out of the log of a GitHub Actions job.
mask() {
    if [ -n "${GITHUB_ACTIONS:-}" ]; then
        echo "::add-mask::$1"
    fi
}

run_suite() {
    local name="$1"
    shift
    log "Integration suite: ${name}"
    "$MVN" -B "$@" -DskipTests=false -Dmaven.test.failure.ignore=true -Drat.skip=true \
        -Dnacos.test.auth.enabled=true
}

stage_integration() {
    load_state
    if [ "$RUN_SUITES" != "true" ]; then
        log "The ${SCENARIO} scenario runs no suites"
        return 0
    fi
    local archive status=0
    local -a property_options extra_options
    archive="$(find_archive)"
    mapfile -t property_options < <(server_property_options)
    read -r -a extra_options <<< "${NATIVE_CI_TEST_OPTIONS:-}"
    mkdir -p "$OUT/logs" "$OUT/reports"
    rm -rf "$ENVIRONMENT_DIR"
    mkdir -p "$ENVIRONMENT_DIR"

    log "Start the test environment: a ${DATABASE} database and the Native Server"
    local classpath
    classpath="$ROOT/test/native-test/target/classes:$(cat "${WORK}/native-test.classpath")"
    java -cp "$classpath" \
        "-Dnacos.native.test.distribution=${archive}" \
        "-Dnacos.native.test.opts=${NACOS_NATIVE_OPTS}" \
        "${property_options[@]}" "${extra_options[@]}" \
        com.alibaba.nacos.test.nativeimage.NativeEnvironmentMain "$DATABASE" "$ENVIRONMENT_DIR" \
        > "$OUT/logs/environment.log" 2>&1 &
    local launcher=$!
    # shellcheck disable=SC2064
    trap "kill ${launcher} 2>/dev/null || true" EXIT
    local waited=0
    until [ -f "$ENVIRONMENT_DIR/environment.properties" ]; do
        if ! kill -0 "$launcher" 2>/dev/null; then
            echo "The test environment ended while it started" >&2
            tail -n 100 "$OUT/logs/environment.log" >&2 || true
            return 1
        fi
        if [ "$waited" -ge 900 ]; then
            echo "The test environment is not ready after 15 minutes" >&2
            tail -n 100 "$OUT/logs/environment.log" >&2 || true
            return 1
        fi
        sleep 2
        waited=$((waited + 2))
    done
    echo "The test environment is ready after ${waited} s"

    # The environment hands the credentials to the suites as system properties. The suites read
    # these before they look at the environment variables, but a few read only the variables.
    local suite_properties=() key value name
    while IFS='=' read -r key value; do
        [ -n "$key" ] || continue
        suite_properties+=("-D${key}=${value}")
        name="$(printf '%s' "$key" | tr '[:lower:].-' '[:upper:]__')"
        export "${name}=${value}"
        case "$key" in
            *password* | *secret*) mask "$value" ;;
        esac
    done < "$ENVIRONMENT_DIR/environment.properties"

    local openapi_selection=()
    if [ "$SUITE" = "core" ]; then
        openapi_selection=("-Dit.test=${CORE_OPENAPI_CLASSES}")
    fi
    run_suite openapi -pl test/openapi-test verify -Pintegration-test \
        "${openapi_selection[@]}" "${suite_properties[@]}" || status=1
    run_suite java-sdk -pl test/java-sdk-test verify -Pjava-sdk-integration-test \
        "-Dit.test=${JAVA_SDK_CLASSES}" "${suite_properties[@]}" || status=1
    run_suite maintainer-sdk -pl test/maintainer-sdk-test verify \
        -Pmaintainer-sdk-integration-test "${suite_properties[@]}" || status=1

    log "Resource usage of the Native Server"
    local pid
    pid="$(pgrep -f -- "-Dnacos.home=${NACOS_HOME}" | head -n 1 || true)"
    if [ -n "$pid" ]; then
        {
            ps -o pid=,rss=,etime=,args= -p "$pid" | cut -c1-160
            echo "RSS: $(( $(awk '/VmRSS/ {print $2}' "/proc/${pid}/status") / 1024 )) MB"
        } | tee "$OUT/reports/resource-usage.txt" || true
    fi

    # Ending the environment restarts the Server, which holds the data of the suites by now, and
    # checks that the data of NativeRestartCheck survives. It never fails this stage: the report
    # stage judges target/ci-out/reports/restart-check.txt.
    log "End the test environment: the restart check"
    touch "$ENVIRONMENT_DIR/finish"
    wait "$launcher" || true
    trap - EXIT
    cp "$ENVIRONMENT_DIR/restart-check.txt" "$OUT/reports/restart-check.txt" 2>/dev/null || true
    return "$status"
}

stage_report() {
    load_state
    mkdir -p "$OUT/logs" "$OUT/reports"
    # The logs of every Server that the run started, one directory each, and of its database.
    local logs_dir database_log name
    while IFS= read -r logs_dir; do
        name="${logs_dir#"${WORK}"/}"
        name="${name%/nacos/logs}"
        mkdir -p "$OUT/logs/${name//\//_}"
        cp "$logs_dir"/*.log "$OUT/logs/${name//\//_}/" 2>/dev/null || true
    done < <(find "$WORK" -type d -path '*/nacos/logs' 2>/dev/null | sort)
    while IFS= read -r database_log; do
        name="${database_log#"${WORK}"/}"
        cp "$database_log" "$OUT/logs/${name//\//_}" 2>/dev/null || true
    done < <(find "$WORK" -name database.log 2>/dev/null | sort)
    cp "$ROOT"/test/*/target/failsafe-reports/TEST-*.xml "$OUT/reports/" 2>/dev/null || true

    log "Resource usage of the Native Server"
    cat "$OUT/reports/resource-usage.txt" 2>/dev/null || echo "No measurement"

    log "Integration test results"
    local status=0
    local baselines=("$ROOT/test/scripts/native-ci-baseline.txt")
    if [ "$SCENARIO" != "features" ]; then
        baselines+=("$ROOT/test/scripts/native-ci-baseline-ard-off.txt")
    fi
    python3 -I - "$ROOT" "${baselines[@]}" <<'PY' || status=1
import fnmatch
import glob
import sys
import xml.etree.ElementTree as ET

root, baseline_files = sys.argv[1], sys.argv[2:]
baseline = [line.strip() for baseline_file in baseline_files for line in open(baseline_file)
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

    log "Restart check"
    local restart_report="$OUT/reports/restart-check.txt"
    if [ -f "$restart_report" ]; then
        cat "$restart_report"
        # The check is done when its last step passed; a report that stops earlier is a failure too.
        if ! grep -q '^PASS  data after the restart' "$restart_report" \
            || grep -q '^FAIL' "$restart_report"; then
            status=1
        fi
    elif [ "$RUN_SUITES" = "true" ]; then
        # The environment writes the report before it ends, so a missing one means that it ended
        # in the middle of the check.
        echo "The restart check did not run" >&2
        status=1
    else
        echo "The ${SCENARIO} scenario runs no suites, so it has no restart check"
    fi

    log "Missing registrations reported by the Native executable"
    # Every Server of the run, and the first run of the Servers that were restarted: a restart
    # truncates startup.log.
    local scanned="$OUT/logs/startup-all-runs.log" candidate
    : > "$scanned"
    while IFS= read -r candidate; do
        cat "$candidate" >> "$scanned"
    done < <(find "$WORK" \( -name startup.log -o -name startup-before-restart.log \) \
        -path '*/nacos/logs/*' 2>/dev/null | sort)
    if [ ! -s "$scanned" ]; then
        echo "No Native Server log; the servers did not start" >&2
        return 1
    fi
    mkdir -p "$OUT/missing-registrations"
    python3 -I "$METADATA_TOOL" collect-missing "$scanned" --output "$OUT/missing-registrations" \
        2> "$OUT/missing-registrations/unparsed.txt" || true
    cat "$OUT/missing-registrations/unparsed.txt" >&2
    if [ "$VARIANT" = "exact" ]; then
        # A report that the tool cannot turn into metadata is a gap that nobody can name yet.
        if grep -q '^UNPARSED ' "$OUT/missing-registrations/unparsed.txt"; then
            echo "Reports above could not be turned into metadata; extend native-metadata.py" >&2
            status=1
        fi
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

    log "Tail of the Native Server logs"
    tail -n 200 "$scanned" || true
    return "$status"
}

case "${1:-}" in
    prepare) stage_prepare ;;
    build) stage_build ;;
    smoke) stage_smoke ;;
    integration) stage_integration ;;
    scenario) stage_scenario ;;
    report) stage_report ;;
    all)
        stage_prepare
        stage_build
        stage_smoke
        stage_integration || true
        stage_scenario || true
        stage_report
        ;;
    *)
        echo "Usage: $0 <prepare|build|smoke|integration|scenario|report|all>" >&2
        exit 2
        ;;
esac
