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

set -euo pipefail

readonly MODE="${1:-}"
readonly SERVER_BASE_URL="${NACOS_TEST_SERVER_BASE_URL:-http://127.0.0.1:8848}"
readonly AUTH_USER_URL="${SERVER_BASE_URL}/nacos/v3/auth/user"
readonly AUTH_ROLE_URL="${SERVER_BASE_URL}/nacos/v3/auth/role"
readonly AUTH_PERMISSION_URL="${SERVER_BASE_URL}/nacos/v3/auth/permission"
readonly CLIENT_PROBE_URL="${SERVER_BASE_URL}/nacos/v3/client/ns/instance/list?namespaceId=public&groupName=DEFAULT_GROUP&serviceName=nacos-it-auth-identity-probe"

readonly ADMIN_USERNAME="${NACOS_TEST_AUTH_ADMIN_USERNAME:-nacos}"
readonly CLIENT_USERNAME="${NACOS_TEST_AUTH_CLIENT_USERNAME:-nacos_it_client_rw}"
readonly READONLY_USERNAME="${NACOS_TEST_AUTH_READONLY_USERNAME:-nacos_it_client_readonly}"
readonly NO_PERMISSION_USERNAME="${NACOS_TEST_AUTH_NO_PERMISSION_USERNAME:-nacos_it_client_no_permission}"

readonly CLIENT_ROLE="NACOS_IT_CLIENT_RW"
readonly READONLY_ROLE="NACOS_IT_CLIENT_READONLY"
readonly NO_PERMISSION_ROLE="NACOS_IT_CLIENT_NO_PERMISSION"
readonly CLIENT_RESOURCES=(
    '*:*:config/*'
    '*:*:naming/*'
    '*:*:ai/*'
)

readonly TEMP_DIR="$(mktemp -d)"
readonly RESPONSE_FILE="${TEMP_DIR}/response.json"
trap 'rm -rf "${TEMP_DIR}"' EXIT

HTTP_STATUS=""
ACCESS_TOKEN=""

require_value() {
    local variable_name="$1"
    if [[ -z "${!variable_name:-}" ]]; then
        echo "Required environment variable is blank: ${variable_name}" >&2
        exit 2
    fi
}

request() {
    HTTP_STATUS="$(curl --silent --show-error --output "${RESPONSE_FILE}" \
        --write-out '%{http_code}' "$@")"
}

result_code() {
    sed -n 's/.*"code"[[:space:]]*:[[:space:]]*\(-\{0,1\}[0-9][0-9]*\).*/\1/p' \
        "${RESPONSE_FILE}" | head -n 1
}

assert_http_status() {
    local expected="$1"
    local scenario="$2"
    if [[ "${HTTP_STATUS}" != "${expected}" ]]; then
        echo "${scenario} failed: expected HTTP ${expected}, actual ${HTTP_STATUS}" >&2
        exit 1
    fi
}

assert_result_code() {
    local expected="$1"
    local scenario="$2"
    local actual
    actual="$(result_code)"
    if [[ "${actual}" != "${expected}" ]]; then
        echo "${scenario} failed: expected result code ${expected}, actual ${actual:-missing}" >&2
        exit 1
    fi
}

await_login() {
    local username="$1"
    local password="$2"
    local scenario="$3"
    local attempt
    for attempt in $(seq 1 80); do
        request --request POST --data-urlencode "username=${username}" \
            --data-urlencode "password=${password}" "${AUTH_USER_URL}/login"
        if [[ "${HTTP_STATUS}" == "200" ]]; then
            ACCESS_TOKEN="$(sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
                "${RESPONSE_FILE}" | head -n 1)"
            if [[ -n "${ACCESS_TOKEN}" ]]; then
                return
            fi
        fi
        sleep 0.25
    done
    echo "${scenario} failed to converge within 20 seconds" >&2
    exit 1
}

admin_request() {
    local method="$1"
    local url="$2"
    shift 2
    request --request "${method}" --header "Authorization: Bearer ${ADMIN_TOKEN}" \
        "$@" "${url}"
    assert_http_status 200 "${method} ${url}"
    assert_result_code 0 "${method} ${url}"
}

delete_identity_if_present() {
    local username="$1"
    local role="$2"
    local action="$3"
    local resource
    admin_request DELETE \
        "${AUTH_PERMISSION_URL}?role=${role}&resource=%2A&action=${action}"
    for resource in "${CLIENT_RESOURCES[@]}"; do
        admin_request DELETE "${AUTH_PERMISSION_URL}?role=${role}" \
            --data-urlencode "resource=${resource}" --data-urlencode "action=${action}"
    done
    admin_request DELETE "${AUTH_ROLE_URL}?role=${role}&username=${username}"
    admin_request DELETE "${AUTH_USER_URL}?username=${username}"
}

create_identity() {
    local username="$1"
    local password="$2"
    local role="$3"
    admin_request POST "${AUTH_USER_URL}" --data-urlencode "username=${username}" \
        --data-urlencode "password=${password}"
    admin_request POST "${AUTH_ROLE_URL}" --data-urlencode "role=${role}" \
        --data-urlencode "username=${username}"
}

grant_client_permissions() {
    local role="$1"
    local action="$2"
    local resource
    for resource in "${CLIENT_RESOURCES[@]}"; do
        admin_request POST "${AUTH_PERMISSION_URL}" --data-urlencode "role=${role}" \
            --data-urlencode "resource=${resource}" --data-urlencode "action=${action}"
    done
}

probe_client() {
    local token="$1"
    request --request GET --header "Authorization: Bearer ${token}" "${CLIENT_PROBE_URL}"
}

assert_denied() {
    local token="$1"
    local scenario="$2"
    probe_client "${token}"
    assert_http_status 403 "${scenario}"
    assert_result_code 10001 "${scenario}"
}

await_authorized() {
    local token="$1"
    local scenario="$2"
    local attempt
    for attempt in $(seq 1 80); do
        probe_client "${token}"
        if [[ "${HTTP_STATUS}" == "200" && "$(result_code)" == "0" ]]; then
            return
        fi
        sleep 0.25
    done
    echo "${scenario} failed to converge within 20 seconds" >&2
    exit 1
}

bootstrap_admin() {
    require_value NACOS_TEST_AUTH_ADMIN_PASSWORD
    request --request POST --data-urlencode \
        "password=${NACOS_TEST_AUTH_ADMIN_PASSWORD}" "${AUTH_USER_URL}/admin"
    assert_http_status 200 "administrator bootstrap"
    assert_result_code 0 "administrator bootstrap"

    request --request POST --data-urlencode \
        "password=${NACOS_TEST_AUTH_ADMIN_PASSWORD}" "${AUTH_USER_URL}/admin"
    assert_http_status 200 "repeated administrator bootstrap"
    assert_result_code 409 "repeated administrator bootstrap"
    echo "Administrator bootstrap contract verified."
}

prepare_identities() {
    require_value NACOS_TEST_AUTH_ADMIN_PASSWORD
    require_value NACOS_TEST_AUTH_CLIENT_PASSWORD
    require_value NACOS_TEST_AUTH_READONLY_PASSWORD
    require_value NACOS_TEST_AUTH_NO_PERMISSION_PASSWORD

    await_login "${ADMIN_USERNAME}" "${NACOS_TEST_AUTH_ADMIN_PASSWORD}" \
        "administrator login"
    readonly ADMIN_TOKEN="${ACCESS_TOKEN}"

    delete_identity_if_present "${CLIENT_USERNAME}" "${CLIENT_ROLE}" rw
    delete_identity_if_present "${READONLY_USERNAME}" "${READONLY_ROLE}" r
    delete_identity_if_present "${NO_PERMISSION_USERNAME}" "${NO_PERMISSION_ROLE}" r

    create_identity "${CLIENT_USERNAME}" "${NACOS_TEST_AUTH_CLIENT_PASSWORD}" \
        "${CLIENT_ROLE}"
    create_identity "${READONLY_USERNAME}" "${NACOS_TEST_AUTH_READONLY_PASSWORD}" \
        "${READONLY_ROLE}"
    create_identity "${NO_PERMISSION_USERNAME}" \
        "${NACOS_TEST_AUTH_NO_PERMISSION_PASSWORD}" "${NO_PERMISSION_ROLE}"

    await_login "${CLIENT_USERNAME}" "${NACOS_TEST_AUTH_CLIENT_PASSWORD}" \
        "client read-write login"
    local client_token="${ACCESS_TOKEN}"
    await_login "${READONLY_USERNAME}" "${NACOS_TEST_AUTH_READONLY_PASSWORD}" \
        "client read-only login"
    local readonly_token="${ACCESS_TOKEN}"
    await_login "${NO_PERMISSION_USERNAME}" \
        "${NACOS_TEST_AUTH_NO_PERMISSION_PASSWORD}" "client no-permission login"
    local no_permission_token="${ACCESS_TOKEN}"

    assert_denied "${client_token}" "client before permission grant"
    grant_client_permissions "${CLIENT_ROLE}" rw
    await_authorized "${client_token}" "client read-write permission"

    grant_client_permissions "${READONLY_ROLE}" r
    await_authorized "${readonly_token}" "client read-only permission"
    assert_denied "${no_permission_token}" "client without permission"
    echo "Shared auth test identities prepared and verified."
}

case "${MODE}" in
    bootstrap)
        bootstrap_admin
        ;;
    prepare)
        prepare_identities
        ;;
    *)
        echo "Usage: $0 {bootstrap|prepare}" >&2
        exit 2
        ;;
esac
