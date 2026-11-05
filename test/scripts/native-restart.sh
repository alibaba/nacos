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
#

# Data that has to survive a restart of the Native Server. native-ci.sh runs this script twice, around
# the restart: 'seed' creates the data and 'verify' looks for it on the Server that was started again.
# It covers what no OpenAPI, Java SDK or Maintainer SDK suite reaches, because the Server of the job
# is never restarted and the suites create only a few kinds of data:
#
#   - a persistent service with a label selector and a persistent instance in a cluster with the MYSQL
#     health checker. The Raft groups of the naming service write them into their log and into their
#     snapshots (this job takes a snapshot every 30 seconds) and read both back when the Server starts.
#     The Server answers the service detail with the selector and the checker, which Jackson writes.
#   - a naming switch that is replicated through the Raft group. The suites set switches in memory only.
#   - a configuration, which the datasource keeps.
#
# Usage: native-restart.sh <seed|verify>
#
# Environment: the same as native-smoke.sh (the password of the administrator is required).

set -euo pipefail

readonly MODE="${1:-}"
readonly SERVER_BASE_URL="${NACOS_TEST_SERVER_BASE_URL:-http://127.0.0.1:8848}"
readonly ADMIN_USERNAME="${NACOS_TEST_AUTH_ADMIN_USERNAME:-nacos}"
readonly API="${SERVER_BASE_URL}/nacos/v3"
readonly NAMESPACE="public"
readonly GROUP="DEFAULT_GROUP"
readonly SERVICE="native-restart-service"
readonly CLUSTER="DB"
readonly INSTANCE_IP="127.0.0.1"
readonly INSTANCE_PORT="19306"
readonly DATA_ID="native-restart-config"
readonly SWITCH_ENTRY="defaultCacheMillis"
readonly SWITCH_VALUE="12345"
readonly SWITCH_DEFAULT="3000"
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

authorized() {
    request --header "Authorization: Bearer ${ACCESS_TOKEN}" "$@"
}

fail() {
    echo "FAIL  $1" >&2
    head -c 600 "${RESPONSE_FILE}" >&2 || true
    echo >&2
    FAILURES=$((FAILURES + 1))
}

# The HTTP status and the code of the Result wrapper of the last response.
check() {
    local name="$1"
    if [[ "${HTTP_STATUS}" == "200" ]] && [[ "$(result_code)" == "0" ]]; then
        echo "PASS  ${name}"
    else
        fail "${name}: HTTP ${HTTP_STATUS}, result code '$(result_code)'"
    fi
}

# The last response has to match an extended regular expression.
expect() {
    local name="$1"
    local pattern="$2"
    if grep -q -E "${pattern}" "${RESPONSE_FILE}"; then
        echo "PASS  ${name}"
    else
        fail "${name}: the response does not match ${pattern}"
    fi
}

login() {
    : "${NACOS_TEST_AUTH_ADMIN_PASSWORD:?NACOS_TEST_AUTH_ADMIN_PASSWORD is required}"
    # A Server that has just been started may answer the first logins before its user cache loaded.
    for _ in $(seq 1 60); do
        request --request POST --data-urlencode "username=${ADMIN_USERNAME}" \
            --data-urlencode "password=${NACOS_TEST_AUTH_ADMIN_PASSWORD}" "${API}/auth/user/login" \
            || true
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
}

service_query() {
    echo "serviceName=${SERVICE}&groupName=${GROUP}&namespaceId=${NAMESPACE}"
}

# Best effort: it removes what an earlier run left and what this run created.
cleanup() {
    authorized --request DELETE \
        "${API}/admin/ns/instance?$(service_query)&ip=${INSTANCE_IP}&port=${INSTANCE_PORT}&clusterName=${CLUSTER}&ephemeral=false" \
        || true
    authorized --request DELETE "${API}/admin/ns/service?$(service_query)" || true
    authorized --request DELETE "${API}/admin/cs/config?dataId=${DATA_ID}&groupName=${GROUP}" || true
    authorized --request PUT --data-urlencode "entry=${SWITCH_ENTRY}" \
        --data-urlencode "value=${SWITCH_DEFAULT}" --data-urlencode "debug=false" \
        "${API}/admin/ns/ops/switches" || true
}

seed() {
    login
    cleanup

    authorized --request POST --data-urlencode "serviceName=${SERVICE}" \
        --data-urlencode "groupName=${GROUP}" --data-urlencode "namespaceId=${NAMESPACE}" \
        --data-urlencode "ephemeral=false" --data-urlencode "protectThreshold=0.5" \
        --data-urlencode 'metadata={"owner":"team-x"}' \
        --data-urlencode 'selector={"type":"label","expression":"CONSUMER.label.zone = PROVIDER.label.zone"}' \
        "${API}/admin/ns/service"
    check "persistent service with a label selector"

    authorized --request POST --data-urlencode "serviceName=${SERVICE}" \
        --data-urlencode "groupName=${GROUP}" --data-urlencode "namespaceId=${NAMESPACE}" \
        --data-urlencode "ip=${INSTANCE_IP}" --data-urlencode "port=${INSTANCE_PORT}" \
        --data-urlencode "clusterName=${CLUSTER}" --data-urlencode "weight=2.5" \
        --data-urlencode "ephemeral=false" --data-urlencode 'metadata={"zone":"z1"}' \
        "${API}/admin/ns/instance"
    check "persistent instance"
    # The cluster exists once the instance is visible.
    for _ in $(seq 1 40); do
        authorized "${API}/admin/ns/instance/list?$(service_query)&clusterName=${CLUSTER}"
        grep -q "${INSTANCE_PORT}" "${RESPONSE_FILE}" && break
        sleep 0.5
    done

    authorized --request PUT --data-urlencode "serviceName=${SERVICE}" \
        --data-urlencode "groupName=${GROUP}" --data-urlencode "namespaceId=${NAMESPACE}" \
        --data-urlencode "clusterName=${CLUSTER}" --data-urlencode "checkPort=${INSTANCE_PORT}" \
        --data-urlencode "useInstancePort4Check=true" \
        --data-urlencode 'healthChecker={"type":"MYSQL","user":"probe","pwd":"secret","cmd":"select 1"}' \
        --data-urlencode 'metadata={"owner":"team-x"}' "${API}/admin/ns/cluster"
    check "cluster with the MYSQL health checker"

    # debug=false replicates the switch through the Raft group instead of keeping it in memory.
    authorized --request PUT --data-urlencode "entry=${SWITCH_ENTRY}" \
        --data-urlencode "value=${SWITCH_VALUE}" --data-urlencode "debug=false" \
        "${API}/admin/ns/ops/switches"
    check "naming switch through the Raft group"

    authorized --request POST --data-urlencode "dataId=${DATA_ID}" \
        --data-urlencode "groupName=${GROUP}" --data-urlencode "content=restart=true" \
        --data-urlencode "type=properties" "${API}/admin/cs/config"
    check "config publish"

    # What the Server answers now is what it has to answer after the restart.
    authorized "${API}/admin/ns/service?$(service_query)"
    check "service detail before the restart"
    expect "label selector before the restart" \
        '"labels"[[:space:]]*:[[:space:]]*\[[[:space:]]*"zone"[[:space:]]*\]'
    expect "MYSQL health checker before the restart" \
        '"type"[[:space:]]*:[[:space:]]*"MYSQL"'
}

verify() {
    login

    authorized "${API}/admin/ns/service?$(service_query)"
    check "service detail after the restart"
    expect "label selector after the restart" \
        '"labels"[[:space:]]*:[[:space:]]*\[[[:space:]]*"zone"[[:space:]]*\]'
    expect "MYSQL health checker after the restart" \
        '"type"[[:space:]]*:[[:space:]]*"MYSQL"'

    authorized "${API}/admin/ns/instance/list?$(service_query)&clusterName=${CLUSTER}"
    check "instance list after the restart"
    expect "persistent instance after the restart" "${INSTANCE_PORT}"
    expect "instance metadata after the restart" '"zone"[[:space:]]*:[[:space:]]*"z1"'

    authorized "${API}/admin/ns/ops/switches"
    check "switches after the restart"
    expect "replicated switch after the restart" \
        "\"${SWITCH_ENTRY}\"[[:space:]]*:[[:space:]]*${SWITCH_VALUE}"

    authorized "${API}/admin/cs/config?dataId=${DATA_ID}&groupName=${GROUP}"
    check "config get after the restart"
    expect "config content after the restart" 'restart=true'

    cleanup
}

case "${MODE}" in
    seed) seed ;;
    verify) verify ;;
    *)
        echo "Usage: $0 <seed|verify>" >&2
        exit 2
        ;;
esac

if [[ "${FAILURES}" -ne 0 ]]; then
    echo "${FAILURES} check(s) of the restart test failed" >&2
    exit 1
fi
echo "Native restart test (${MODE}) passed."
