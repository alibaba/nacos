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

# Minimal functional smoke test for a running Nacos Server, intended for a freshly built Native
# executable. It covers readiness, authentication, Config and Naming over HTTP, the Web console
# assets, and that the gRPC port accepts connections. It does not replace the OpenAPI, Java SDK
# and Maintainer SDK integration suites, which are the acceptance gate (see bootstrap/README-AOT.md).
#
# Usage: native-smoke.sh [--bootstrap]
#   --bootstrap   initialize the administrator password first (only for an empty database)
#
# Environment:
#   NACOS_TEST_AUTH_ADMIN_PASSWORD   required; password of the 'nacos' administrator
#   NACOS_TEST_AUTH_ADMIN_USERNAME   default nacos
#   NACOS_TEST_SERVER_BASE_URL       default http://127.0.0.1:8848
#   NACOS_TEST_CONSOLE_BASE_URL      default http://127.0.0.1:8080
#   NACOS_TEST_GRPC_PORT             default 9848

set -euo pipefail

readonly SERVER_BASE_URL="${NACOS_TEST_SERVER_BASE_URL:-http://127.0.0.1:8848}"
readonly CONSOLE_BASE_URL="${NACOS_TEST_CONSOLE_BASE_URL:-http://127.0.0.1:8080}"
readonly GRPC_PORT="${NACOS_TEST_GRPC_PORT:-9848}"
readonly ADMIN_USERNAME="${NACOS_TEST_AUTH_ADMIN_USERNAME:-nacos}"
readonly API="${SERVER_BASE_URL}/nacos/v3"
SUFFIX="$(date +%s)-$$"
readonly SUFFIX
readonly DATA_ID="native-smoke-${SUFFIX}"
readonly SERVICE="native-smoke-${SUFFIX}"
TEMP_DIR="$(mktemp -d)"
readonly TEMP_DIR
readonly RESPONSE_FILE="${TEMP_DIR}/response.json"
trap 'rm -rf "${TEMP_DIR}"' EXIT

HTTP_STATUS=""
ACCESS_TOKEN=""
FAILURES=0

request() {
    HTTP_STATUS="$(curl --silent --show-error --max-time 30 --output "${RESPONSE_FILE}" \
        --write-out '%{http_code}' "$@")"
}

result_code() {
    sed -n 's/.*"code"[[:space:]]*:[[:space:]]*\(-\{0,1\}[0-9][0-9]*\).*/\1/p' \
        "${RESPONSE_FILE}" | head -n 1
}

check() {
    local name="$1"
    local expected_status="$2"
    local expected_code="${3:-}"
    if [[ "${HTTP_STATUS}" == "${expected_status}" ]] \
        && { [[ -z "${expected_code}" ]] || [[ "$(result_code)" == "${expected_code}" ]]; }; then
        echo "PASS  ${name}"
    else
        echo "FAIL  ${name}: HTTP ${HTTP_STATUS}, result code '$(result_code)'" >&2
        head -c 400 "${RESPONSE_FILE}" >&2 || true
        echo >&2
        FAILURES=$((FAILURES + 1))
    fi
}

authorized() {
    request --header "Authorization: Bearer ${ACCESS_TOKEN}" "$@"
}

if [[ "${1:-}" == "--bootstrap" ]]; then
    request --request POST --data-urlencode \
        "password=${NACOS_TEST_AUTH_ADMIN_PASSWORD:?NACOS_TEST_AUTH_ADMIN_PASSWORD is required}" \
        "${API}/auth/user/admin"
    check "administrator bootstrap" 200 0
fi
: "${NACOS_TEST_AUTH_ADMIN_PASSWORD:?NACOS_TEST_AUTH_ADMIN_PASSWORD is required}"

request "${CONSOLE_BASE_URL}/v3/console/health/liveness"
check "console liveness" 200
request "${CONSOLE_BASE_URL}/v3/console/health/readiness"
check "console readiness" 200

# The first login may race the first load of the user cache, which refreshes after a few seconds.
for _ in $(seq 1 40); do
    request --request POST --data-urlencode "username=${ADMIN_USERNAME}" \
        --data-urlencode "password=${NACOS_TEST_AUTH_ADMIN_PASSWORD}" "${API}/auth/user/login"
    if [[ "${HTTP_STATUS}" == "200" ]]; then
        ACCESS_TOKEN="$(sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
            "${RESPONSE_FILE}" | head -n 1)"
        [[ -n "${ACCESS_TOKEN}" ]] && break
    fi
    sleep 0.5
done
if [[ -z "${ACCESS_TOKEN}" ]]; then
    echo "FAIL  administrator login" >&2
    exit 1
fi
echo "PASS  administrator login"

authorized --request POST --data-urlencode "dataId=${DATA_ID}" \
    --data-urlencode "groupName=DEFAULT_GROUP" --data-urlencode "content=native=true" \
    --data-urlencode "type=properties" "${API}/admin/cs/config"
check "config publish" 200 0
authorized "${API}/admin/cs/config?dataId=${DATA_ID}&groupName=DEFAULT_GROUP"
check "config get" 200 0
grep -q 'native=true' "${RESPONSE_FILE}" || { echo "FAIL  config content" >&2; FAILURES=$((FAILURES + 1)); }
authorized "${API}/client/cs/config?dataId=${DATA_ID}&groupName=DEFAULT_GROUP"
check "config get (client API)" 200 0
authorized "${CONSOLE_BASE_URL}/v3/console/cs/config/list?pageNo=1&pageSize=10&namespaceId=public"
check "console config list" 200 0
authorized --request DELETE "${API}/admin/cs/config?dataId=${DATA_ID}&groupName=DEFAULT_GROUP"
check "config delete" 200 0

authorized --request POST --data-urlencode "serviceName=${SERVICE}" --data-urlencode "ip=127.0.0.1" \
    --data-urlencode "port=18080" "${API}/client/ns/instance"
check "instance register" 200 0
authorized "${API}/client/ns/instance/list?serviceName=${SERVICE}"
check "instance list" 200 0
grep -q '18080' "${RESPONSE_FILE}" || { echo "FAIL  instance content" >&2; FAILURES=$((FAILURES + 1)); }
authorized "${CONSOLE_BASE_URL}/v3/console/ns/service/list?pageNo=1&pageSize=10&namespaceId=public"
check "console service list" 200 0
authorized --request DELETE "${API}/client/ns/instance?serviceName=${SERVICE}&ip=127.0.0.1&port=18080"
check "instance deregister" 200 0

authorized "${CONSOLE_BASE_URL}/v3/console/core/namespace/list"
check "namespace list" 200 0
authorized "${CONSOLE_BASE_URL}/v3/console/server/state"
check "server state" 200 0
request "${CONSOLE_BASE_URL}/next/"
check "web console (next)" 200
request "${CONSOLE_BASE_URL}/legacy/index.html"
check "web console (legacy)" 200

if (exec 3<>"/dev/tcp/127.0.0.1/${GRPC_PORT}") 2>/dev/null; then
    echo "PASS  gRPC port ${GRPC_PORT} accepts connections"
else
    echo "FAIL  gRPC port ${GRPC_PORT} is not reachable" >&2
    FAILURES=$((FAILURES + 1))
fi

if [[ "${FAILURES}" -ne 0 ]]; then
    echo "${FAILURES} smoke check(s) failed" >&2
    exit 1
fi
echo "Native smoke test passed."
