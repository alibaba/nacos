#!/usr/bin/env bash
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
umask 077
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
OLD_DIST='' NEW_DIST='' REPORT_ROOT='' WORK_ROOT=''
OLD_HOME='' NEW_HOME=''
declare -A SERVER_PIDS=()
fail() { echo "ERROR: $*" >&2; exit 1; }
usage() {
    echo "Usage: $0 --old-dist /abs/old.tar.gz --new-dist /abs/new.tar.gz --report-dir /abs/reports"
}
while (( $# )); do
    case "$1" in
        --old-dist|--new-dist|--report-dir)
            (( $# >= 2 )) || fail "Missing value for $1"
            case "$1" in
                --old-dist) OLD_DIST="$2" ;;
                --new-dist) NEW_DIST="$2" ;;
                --report-dir) REPORT_ROOT="$2" ;;
            esac
            shift 2 ;;
        --help|-h) usage; exit 0 ;;
        *) usage; fail "Unknown argument: $1" ;;
    esac
done
for tool in python3 curl openssl java mvn tar timeout; do
    command -v "${tool}" >/dev/null || fail "Required tool unavailable: ${tool}"
done
[[ "${OLD_DIST}" == /* && -f "${OLD_DIST}" ]] || fail "Old distribution must be an absolute file"
[[ "${NEW_DIST}" == /* && -f "${NEW_DIST}" ]] || fail "New distribution must be an absolute file"
[[ "${REPORT_ROOT}" == /* ]] || fail "Report directory must be absolute"
mkdir -p "${REPORT_ROOT}"
[[ -z "$(find "${REPORT_ROOT}" -mindepth 1 -maxdepth 1 -print -quit)" ]] \
    || fail "Report directory must be empty"
WORK_ROOT="$(mktemp -d /tmp/nacos-derby-upgrade.XXXXXX)"
export NACOS_DERBY_UPGRADE_STATE_DIR="${WORK_ROOT}/expected-state"
mkdir -p "${NACOS_DERBY_UPGRADE_STATE_DIR}"

owns_pid() {
    python3 - "$1" "$2" <<'PY'
import pathlib, sys
try:
    args = pathlib.Path('/proc', sys.argv[1], 'cmdline').read_bytes().split(b'\0')
    assert ('-Dnacos.home=' + sys.argv[2]).encode() in args
except (OSError, AssertionError):
    sys.exit(1)
PY
}
alive() {
    [[ -r "/proc/$1/stat" ]] && [[ "$(awk '{print $3}' "/proc/$1/stat")" != Z ]]
}
find_server_pid() {
    python3 - "$1" <<'PY'
import pathlib, sys
expected = ('-Dnacos.home=' + sys.argv[1]).encode()
for cmdline in pathlib.Path('/proc').glob('[0-9]*/cmdline'):
    try:
        if expected in cmdline.read_bytes().split(b'\0'):
            print(cmdline.parent.name)
            break
    except OSError:
        pass
PY
}
stop_server() {
    local home="$1" pid="${SERVER_PIDS[$1]:-}" attempt
    [[ -n "${pid}" ]] || return 0
    if ! alive "${pid}"; then unset 'SERVER_PIDS[$home]'; return 1; fi
    owns_pid "${pid}" "${home}" || return 1
    kill -TERM "${pid}"
    for attempt in $(seq 1 90); do
        if ! alive "${pid}"; then unset 'SERVER_PIDS[$home]'; return 0; fi
        sleep 1
    done
    return 1
}
redact_reports() {
    python3 - "${REPORT_ROOT}" <<'PY'
import html, os, pathlib, re, sys, urllib.parse
secrets = [v for k, v in os.environ.items() if k.startswith('NACOS_TEST_AUTH_')
           and any(s in k for s in ('PASSWORD', 'SECRET', 'IDENTITY')) and v]
for path in pathlib.Path(sys.argv[1]).rglob('*'):
    if not path.is_file():
        continue
    raw = path.read_bytes()
    for secret in secrets:
        forms = {secret, urllib.parse.quote(secret, safe=''), urllib.parse.quote_plus(secret)}
        forms.update(html.escape(value) for value in list(forms))
        for value in forms:
            raw = raw.replace(value.encode(), b'[REDACTED]')
    raw = re.sub(rb'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', b'[REDACTED_TOKEN]', raw)
    path.write_bytes(raw)
PY
}
cleanup() {
    local status=$? home pid
    trap - EXIT INT TERM
    # Also discover a process launched immediately before startup/recording failed.
    for home in "${OLD_HOME}" "${NEW_HOME}"; do
        [[ -n "${home}" ]] || continue
        pid="$(find_server_pid "${home}")"
        if [[ -n "${pid}" ]]; then SERVER_PIDS["${home}"]="${pid}"; fi
    done
    for home in "${!SERVER_PIDS[@]}"; do
        pid="${SERVER_PIDS[$home]}"
        if ! stop_server "${home}"; then
            status=1
            if owns_pid "${pid}" "${home}"; then kill -KILL "${pid}" 2>/dev/null || true; fi
        fi
    done
    for home in "${WORK_ROOT}"/old/* "${WORK_ROOT}"/new/*; do
        if [[ -d "${home}/logs" ]]; then
            mkdir -p "${REPORT_ROOT}/server-$(basename "$(dirname "${home}")")"
            cp -R "${home}/logs" "${REPORT_ROOT}/server-$(basename "$(dirname "${home}")")/" || true
        fi
    done
    if [[ -f "${NACOS_DERBY_UPGRADE_STATE_DIR}/expected.json" ]]; then
        cp "${NACOS_DERBY_UPGRADE_STATE_DIR}/expected.json" "${REPORT_ROOT}/expected.json" || status=1
    fi
    redact_reports || status=1
    printf 'result=%s\n' "$([[ ${status} == 0 ]] && echo passed || echo failed)" >> "${REPORT_ROOT}/summary.txt"
    rm -rf -- "${WORK_ROOT}"
    exit "${status}"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

export NACOS_TEST_AUTH_ADMIN_USERNAME=nacos
export NACOS_TEST_AUTH_ADMIN_PASSWORD="NacosUpgrade-$(openssl rand -hex 16)-A1!"
export NACOS_TEST_AUTH_CLIENT_USERNAME=nacos_derby_upgrade_user
export NACOS_TEST_AUTH_CLIENT_PASSWORD="NacosUpgrade-$(openssl rand -hex 16)-C1!"
export NACOS_TEST_AUTH_TOKEN_SECRET="$(openssl rand -base64 48 | tr -d '\n')"
export NACOS_TEST_AUTH_SERVER_IDENTITY_KEY="upgrade-$(openssl rand -hex 8)"
export NACOS_TEST_AUTH_SERVER_IDENTITY_VALUE="$(openssl rand -hex 32)"

# Inspect real artifact metadata, including nested Spring Boot dependencies.
extract_and_validate() {
    python3 - "$1" "$2" "$3" "${REPORT_ROOT}/summary.txt" <<'PY'
import hashlib, io, pathlib, sys, tarfile, zipfile
archive, target, phase, report = sys.argv[1:]
root = pathlib.Path(target)
root.mkdir()
with tarfile.open(archive, 'r:gz') as package:
    for member in package.getmembers():
        dest = (root / member.name).resolve()
        if root.resolve() not in (dest, *dest.parents) or not (member.isfile() or member.isdir()):
            raise SystemExit('Unsafe distribution archive entry')
    package.extractall(root)
homes = list(root.glob('*/conf/application.properties'))
if len(homes) != 1:
    raise SystemExit('Expected exactly one packaged Nacos home')
home = homes[0].parent.parent
found = []
def inspect(data, name):
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        for item in jar.namelist():
            if item.startswith('org/apache/derby/info/') and item.endswith('/info.properties'):
                props = dict(line.split('=', 1) for line in jar.read(item).decode().splitlines()
                             if '=' in line and not line.startswith('#'))
                artifact = props.get('derby.product.file', '').rsplit('.jar', 1)[0]
                maintenance = int(props['derby.version.maint'])
                version = '.'.join([props['derby.version.major'], props['derby.version.minor'],
                                    str(maintenance // 1000000), str(maintenance % 1000000)])
                found.append((artifact, version))
            if item.startswith('META-INF/maven/org.apache.derby/') and item.endswith('/pom.properties'):
                props = dict(line.split('=', 1) for line in jar.read(item).decode().splitlines()
                             if '=' in line and not line.startswith('#'))
                found.append((props.get('artifactId'), props.get('version')))
        # Derby publishes OSGi manifests even when Maven metadata is absent.
        if 'META-INF/MANIFEST.MF' in jar.namelist():
            text = jar.read('META-INF/MANIFEST.MF').decode().replace('\r\n ', '')
            props = dict(line.split(': ', 1) for line in text.splitlines() if ': ' in line)
            symbolic = props.get('Bundle-SymbolicName', '').split(';')[0]
            mapping = {'derby': 'derby', 'org.apache.derby': 'derby', 'org.apache.derby.shared': 'derbyshared',
                       'org.apache.derby.tools': 'derbytools', 'org.apache.derby.client': 'derbyclient',
                       'org.apache.derby.net': 'derbynet'}
            if symbolic in mapping:
                # Derby's OSGi version encodes fixpack/point in the third component.
                version = props.get('Bundle-Version', '').split(' ')[0]
                parts = version.split('.')
                if len(parts) == 4 and parts[2].isdigit() and int(parts[2]) >= 1000000:
                    maintenance = int(parts[2])
                    version = '.'.join(parts[:2] + [str(maintenance // 1000000),
                                                   str(maintenance % 1000000)])
                found.append((mapping[symbolic], version))
        for item in jar.namelist():
            if item.endswith('.jar'):
                inspect(jar.read(item), name + '!' + item)
for jar in home.rglob('*.jar'):
    inspect(jar.read_bytes(), str(jar))
expected = '10.14.2.0' if phase == 'old' else '10.16.1.1'
required = {'derby'} if phase == 'old' else {'derby', 'derbyshared', 'derbytools'}
if not required.issubset({a for a, v in found}) or any(v != expected for a, v in found):
    raise SystemExit('Unexpected Derby artifact metadata: ' + repr(found))
with open(report, 'a') as out:
    digest = hashlib.sha256()
    with open(archive, 'rb') as package:
        for block in iter(lambda: package.read(1024 * 1024), b''):
            digest.update(block)
    out.write(f'{phase}_sha256={digest.hexdigest()}\n')
    out.write(f'{phase}_derby={sorted(set(found))!r}\n')
print(home)
PY
}
OLD_HOME="$(extract_and_validate "${OLD_DIST}" "${WORK_ROOT}/old" old)"
NEW_HOME="$(extract_and_validate "${NEW_DIST}" "${WORK_ROOT}/new" new)"
java -version >> "${REPORT_ROOT}/summary.txt" 2>&1
printf 'old_commit=%s\nnew_source_id=%s\n' "${NACOS_DERBY_UPGRADE_OLD_COMMIT:-unspecified}" \
    "${NACOS_DERBY_UPGRADE_NEW_SOURCE_ID:-unspecified}" >> "${REPORT_ROOT}/summary.txt"

check_ports() {
    python3 - <<'PY'
import socket
sockets = []
try:
    for port in (8848, 8080, 9080, 9848, 9849, 7848):
        sock = socket.socket()
        sockets.append(sock)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(('0.0.0.0', port))
except OSError as error:
    raise SystemExit(f'Required port {port} unavailable: {error}')
finally:
    for sock in sockets:
        sock.close()
PY
}
configure() {
    python3 - "$1/conf/application.properties" <<'PY'
import os, pathlib, sys
file = pathlib.Path(sys.argv[1])
lines = file.read_text().splitlines()
values = {'nacos.server.main.port': '8848', 'nacos.console.port': '8080',
          'nacos.ai.registry.port': '9080',
          'nacos.core.auth.server.identity.key': os.environ['NACOS_TEST_AUTH_SERVER_IDENTITY_KEY'],
          'nacos.core.auth.server.identity.value': os.environ['NACOS_TEST_AUTH_SERVER_IDENTITY_VALUE'],
          'nacos.plugin.auth.nacos.token.secret.key': os.environ['NACOS_TEST_AUTH_TOKEN_SECRET']}
for key in ('nacos.core.auth.enabled', 'nacos.core.auth.admin.enabled', 'nacos.core.auth.console.enabled'):
    if [line.strip() for line in lines if line.startswith(key + '=')] != [key + '=true']:
        raise SystemExit('Package must retain auth-on default: ' + key)
lines = [line for line in lines if not any(line.startswith(k + '=') for k in values)]
file.write_text('\n'.join(lines + [k + '=' + v for k, v in values.items()]) + '\n')
PY
}
start_server() {
    local home="$1" label="$2" attempt pid=''
    check_ports
    CUSTOM_NACOS_MEMORY="${NACOS_DERBY_UPGRADE_JAVA_MEMORY:--Xms512m -Xmx512m -Xmn128m}" \
        bash "${home}/bin/startup.sh" -m standalone > "${REPORT_ROOT}/${label}-startup.log" 2>&1
    for attempt in $(seq 1 80); do
        pid="$(find_server_pid "${home}")"
        if [[ -n "${pid}" ]]; then SERVER_PIDS["${home}"]="${pid}"; break; fi
        sleep 0.25
    done
    [[ -n "${SERVER_PIDS[$home]:-}" ]] || fail "Server process missing: ${label}"
    for attempt in $(seq 1 240); do
        alive "${SERVER_PIDS[$home]}" || fail "Server exited: ${label}"
        if curl -fsS --max-time 2 http://127.0.0.1:8080/v3/console/health/liveness >/dev/null 2>&1 \
            && curl -fsS --max-time 2 http://127.0.0.1:8080/v3/console/health/readiness >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done
    fail "Server readiness timed out: ${label}"
}
run_phase() {
    local phase="$1" status=0 reports="${REPOSITORY_ROOT}/test/openapi-test/target/failsafe-reports"
    mkdir -p "${REPORT_ROOT}/${phase}"
    if [[ -d "${reports}" ]]; then
        find "${reports}" -maxdepth 1 -type f -name '*DerbyUpgradeOpenApiITCase*' -delete
    fi
    # A separate report directory prevents stale XML from making a skipped phase pass.
    (
        cd "${REPOSITORY_ROOT}"
        NACOS_DERBY_UPGRADE_PHASE="${phase}" timeout 900 mvn -B -pl test/openapi-test \
            -Pintegration-test -DskipTests=false -Dit.test=DerbyUpgradeOpenApiITCase \
            -Dnacos.test.auth.enabled=true \
            "-Dfailsafe.reportsDirectory=${REPORT_ROOT}/${phase}/failsafe-reports" verify
    ) > "${REPORT_ROOT}/${phase}/maven.log" 2>&1 || status=$?
    # Support project configurations that fix the Failsafe reports path.
    if [[ ! -d "${REPORT_ROOT}/${phase}/failsafe-reports" && -d "${reports}" ]]; then
        mkdir -p "${REPORT_ROOT}/${phase}/failsafe-reports"
        find "${reports}" -maxdepth 1 -type f -name '*DerbyUpgradeOpenApiITCase*' \
            -exec cp {} "${REPORT_ROOT}/${phase}/failsafe-reports/" \;
    fi
    [[ ${status} == 0 ]] || fail "Maven phase failed: ${phase} (${status}); see retained report"
    python3 - "${REPORT_ROOT}/${phase}/failsafe-reports" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
files = list(pathlib.Path(sys.argv[1]).glob('TEST-*DerbyUpgradeOpenApiITCase.xml'))
assert files, 'Missing Derby upgrade Failsafe XML'
executed = 0
for file in files:
    suite = ET.parse(file).getroot()
    assert int(suite.get('failures', 0)) == int(suite.get('errors', 0)) == 0
    executed += int(suite.get('tests', 0)) - int(suite.get('skipped', 0))
assert executed > 0, 'Derby upgrade phase executed no tests'
PY
}

configure "${OLD_HOME}"
configure "${NEW_HOME}"
start_server "${OLD_HOME}" old
NACOS_TEST_SERVER_BASE_URL=http://127.0.0.1:8848 timeout 180 \
    bash "${SCRIPT_DIR}/auth-it-identity.sh" bootstrap > "${REPORT_ROOT}/bootstrap.log" 2>&1
run_phase seed
stop_server "${OLD_HOME}" || fail "Old server did not shut down gracefully; refusing database copy"
[[ -d "${OLD_HOME}/data" ]] || fail "Old server produced no data directory"
cp -a "${OLD_HOME}/data" "${WORK_ROOT}/old-data-backup"
if [[ -d "${NEW_HOME}/data" ]]; then
    [[ -z "$(find "${NEW_HOME}/data" -mindepth 1 -print -quit)" ]] || fail "New data directory is not empty"
else
    mkdir "${NEW_HOME}/data"
fi
cp -a "${WORK_ROOT}/old-data-backup/." "${NEW_HOME}/data/"
start_server "${NEW_HOME}" new-upgrade
run_phase verify-upgrade
run_phase mutate
stop_server "${NEW_HOME}" || fail "New server did not shut down gracefully"
start_server "${NEW_HOME}" new-restart
run_phase verify-restart
stop_server "${NEW_HOME}" || fail "Restarted server did not shut down gracefully"
echo "Derby upgrade phases passed. Reports: ${REPORT_ROOT}"
