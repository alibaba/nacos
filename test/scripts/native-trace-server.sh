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

# Run the AOT-prepared Nacos Server on a GraalVM JVM under the Native Image tracing agent, with
# exactly the classpath and structural configuration of a finished '-Pnative' Maven build, then
# curate the result with test/scripts/native-metadata.py (see bootstrap/README-AOT.md).
#
# Usage: native-trace-server.sh <output|merge|classlog> <trace-dir> <nacos-home> [native-dir]
#   output      start a fresh trace; trace-dir must not contain an earlier trace
#   merge       add to the trace already stored in trace-dir (for example after a restart)
#   classlog    no agent: write the order in which the classes are loaded and initialized to the
#               file given as trace-dir, for test/scripts/native-condition-order.py
#   trace-dir   absolute path outside the source tree
#   nacos-home  runtime home with conf/application.properties for a disposable database
#   native-dir  bootstrap/target/native of the finished build (default: that path in this repo)
#
# JAVA_HOME must point to a GraalVM installation (any JDK 17+ for classlog). The locale is fixed
# to English so that the trace does not depend on the machine. NACOS_TRACE_OPTS replaces the
# default JVM options (-Xmx1536m -Duser.language=en -Duser.country=US).

set -euo pipefail

MODE="${1:-}"
TRACE_DIR="${2:-}"
NACOS_HOME="${3:-}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NATIVE_DIR="${4:-${ROOT}/bootstrap/target/native}"
MANIFEST="${NATIVE_DIR}/aot-manifest.properties"

case "$MODE" in
    output|merge|classlog) ;;
    *) echo "Usage: $0 <output|merge|classlog> <trace-dir> <nacos-home> [native-dir]" >&2; exit 2 ;;
esac
case "$TRACE_DIR" in
    /*) ;;
    *) echo "trace-dir must be an absolute path (the agent resolves it against the Nacos home)" >&2
       exit 2 ;;
esac
if [ -z "$NACOS_HOME" ] || [ ! -f "${NACOS_HOME}/conf/application.properties" ]; then
    echo "nacos-home must contain conf/application.properties" >&2
    exit 2
fi
if [ "$MODE" = "classlog" ]; then
    if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
        echo "JAVA_HOME must point to a JDK" >&2
        exit 2
    fi
elif [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/native-image" ]; then
    echo "JAVA_HOME must point to a GraalVM installation that contains native-image" >&2
    exit 2
fi
if [ ! -f "${NATIVE_DIR}/classpath.args" ] || [ ! -f "$MANIFEST" ]; then
    echo "Missing ${NATIVE_DIR}/classpath.args; run the '-Pnative' Maven build first" >&2
    exit 2
fi
if [ "$MODE" = "output" ] && [ -e "${TRACE_DIR}/reachability-metadata.json" ]; then
    echo "${TRACE_DIR} already holds a trace; use merge, or choose an empty directory" >&2
    exit 2
fi

manifest_value() {
    awk -v key="$1" 'index($0, key "=") == 1 { print substr($0, length(key) + 2); exit }' "$MANIFEST"
}

# classpath.args holds "-cp" and the quoted, escaped classpath on its second line.
CLASSPATH_VALUE="$(sed -n '2p' "${NATIVE_DIR}/classpath.args" | sed -e 's/^"//' -e 's/"$//' \
    -e 's/\\"/"/g' -e 's/\\\\/\\/g')"
DEPLOYMENT="$(manifest_value deployment | tr '[:upper:]' '[:lower:]')"
OPTIONS=(
    -Dspring.aot.enabled=true
    "-Dnacos.standalone=$(manifest_value standalone)"
    "-Dnacos.deployment.type=${DEPLOYMENT}"
    "-DembeddedStorage=$(manifest_value embeddedStorage)"
    "-Dnacos.home=${NACOS_HOME}"
    "-Dspring.config.additional-location=file:${NACOS_HOME}/conf/"
    "-Dlogging.config=${NACOS_HOME}/conf/nacos-logback.xml"
    # The AOT application logs with the packaged default configuration and does not read the
    # logging.config above, so the log files would go to ${user.home}/nacos/logs without this.
    "-Dnacos.logs.path=${NACOS_HOME}/logs"
)
FUNCTION_MODE="$(manifest_value functionMode)"
case "$FUNCTION_MODE" in
    ""|All) ;;
    *) OPTIONS+=("-Dnacos.functionMode=${FUNCTION_MODE}") ;;
esac

mkdir -p "${NACOS_HOME}/logs"
if [ "$MODE" = "classlog" ]; then
    # One log for the whole run: the JVM would otherwise rotate it at 20 MB.
    mkdir -p "$(dirname "$TRACE_DIR")"
    OBSERVER="-Xlog:class+load=info,class+init=info:file=${TRACE_DIR}:uptime:filecount=0"
else
    mkdir -p "$TRACE_DIR"
    OBSERVER="-agentlib:native-image-agent=config-${MODE}-dir=${TRACE_DIR}"
    OBSERVER="${OBSERVER},config-write-period-secs=120"
fi
read -r -a JVM_OPTIONS <<< "${NACOS_TRACE_OPTS:--Xmx1536m -Duser.language=en -Duser.country=US}"
cd "$NACOS_HOME"
exec "${JAVA_HOME}/bin/java" "$OBSERVER" \
    "${JVM_OPTIONS[@]}" "${OPTIONS[@]}" \
    -cp "$CLASSPATH_VALUE" com.alibaba.nacos.bootstrap.NacosBootstrap
