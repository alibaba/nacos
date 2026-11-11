#!/bin/bash
# Copyright 1999-2024 Alibaba Group Holding Ltd.
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

# Native-only launcher. The ordinary JVM launcher remains startup.sh.
BASE_DIR=$(cd "$(dirname "$0")/.." && pwd) || exit 1
NACOS_SERVER="${BASE_DIR}/target/nacos-server"
MANIFEST="${BASE_DIR}/target/aot-manifest.properties"

if [ ! -x "$NACOS_SERVER" ] || [ ! -f "$MANIFEST" ]; then
    echo "Missing Native executable or AOT manifest in ${BASE_DIR}/target" >&2
    exit 1
fi

manifest_value() {
    awk -v key="$1" 'index($0, key "=") == 1 { print substr($0, length(key) + 2); exit }' "$MANIFEST"
}

MODE="cluster"
[ "$(manifest_value standalone)" = "true" ] && MODE="standalone"
DEPLOYMENT=$(manifest_value deployment | tr '[:upper:]' '[:lower:]')
FUNCTION_MODE=$(manifest_value functionMode)
EMBEDDED_STORAGE=$(manifest_value embeddedStorage)

while getopts ":m:d:f:" opt; do
    case "$opt" in
        m) MODE=$OPTARG ;;
        d) DEPLOYMENT=$OPTARG ;;
        f) FUNCTION_MODE=$OPTARG ;;
        *) echo "Usage: $0 [-m standalone|cluster] [-d merged|server|console] [-f function-mode]" >&2; exit 1 ;;
    esac
done
case "$MODE" in
    standalone) STANDALONE=true ;;
    cluster) STANDALONE=false ;;
    *) echo "Invalid mode: $MODE" >&2; exit 1 ;;
esac
case "$DEPLOYMENT" in
    merged|server|console) ;;
    *) echo "Invalid deployment: $DEPLOYMENT" >&2; exit 1 ;;
esac

mkdir -p "${BASE_DIR}/logs" || exit 1
LOGFILE="${BASE_DIR}/logs/startup.log"
# Native VM options are whitespace-separated flags such as -Xmx1g.
read -r -a NATIVE_OPTIONS <<< "${NACOS_NATIVE_OPTS:-}"
COMMAND=("$NACOS_SERVER" "${NATIVE_OPTIONS[@]}"
    "-Dnacos.home=${BASE_DIR}"
    "-Dnacos.standalone=${STANDALONE}"
    "-Dnacos.deployment.type=${DEPLOYMENT}"
    "-DembeddedStorage=${EMBEDDED_STORAGE}"
    "-Dspring.config.additional-location=file:${BASE_DIR}/conf/"
    "-Dlogging.config=${BASE_DIR}/conf/nacos-logback.xml")
case "$FUNCTION_MODE" in
    ""|All) ;;
    config|naming|microservice|ai) COMMAND+=("-Dnacos.functionMode=${FUNCTION_MODE}") ;;
    *) echo "Invalid function mode: $FUNCTION_MODE" >&2; exit 1 ;;
esac
nohup "${COMMAND[@]}" nacos.nacos > "$LOGFILE" 2>&1 &
echo "Native Nacos is starting with ${MODE}/${DEPLOYMENT}; check ${LOGFILE}"
