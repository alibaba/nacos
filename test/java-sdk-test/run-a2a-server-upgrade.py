#!/usr/bin/env python3
# Copyright 1999-2026 Alibaba Group Holding Ltd.
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#     http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Run server-first A2A upgrade against a disposable 3.2.3 container and local 3.3 distribution.

The test JVM stays alive. Only this harness's container/process is stopped. Derby
files are copied after clean shutdown, and no historical definition is republished.
Requires installed release SDK artifacts, Docker, Maven, Java 17 and Python 3.
"""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.parse
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--old-image', default='nacos-registry.cn-hangzhou.cr.aliyuncs.com/nacos/nacos-server:v3.2.3')
    parser.add_argument('--distribution', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--main-port', type=int, default=18848)
    parser.add_argument('--console-port', type=int, default=18080)
    parser.add_argument('--registry-port', type=int, default=19080)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    dist = args.distribution.resolve()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    control = out / 'control'
    control.mkdir()
    ports = [args.main_port, args.main_port + 1000, args.main_port + 1001,
             args.console_port, args.registry_port]
    if len(set(ports)) != len(ports):
        raise ValueError('All server ports must be distinct')
    for port in ports:
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', port))
    jar = dist / 'target/nacos-server.jar'
    if not jar.is_file():
        raise ValueError('Expected an unpacked server distribution with target/nacos-server.jar')
    name = 'nacos-sdk-upgrade-' + uuid.uuid4().hex[:10]
    server = test = None
    created = False
    result = {'container': name, 'oldImage': args.old_image, 'stages': []}

    def get(path):
        with urllib.request.urlopen('http://127.0.0.1:' + str(args.main_port) + path, timeout=3) as response:
            return json.load(response)

    def wait(description, check, timeout=180):
        deadline = time.monotonic() + timeout
        last = None
        while time.monotonic() < deadline:
            if test is not None and test.poll() is not None:
                raise RuntimeError('SDK test exited before ' + description)
            try:
                if check():
                    print(description, flush=True)
                    return
            except Exception as error:
                last = str(error)
            time.sleep(0.5)
        raise RuntimeError(description + ' timed out: ' + str(last))

    def stop(process):
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)

    with tempfile.TemporaryDirectory(prefix='nacos-sdk-data-upgrade-') as temporary:
        home = Path(temporary)
        try:
            with (out / 'docker.log').open('w') as dockerlog:
                container_env = os.environ.copy()
                container_env.update(NACOS_AUTH_TOKEN=base64.b64encode(secrets.token_bytes(48)).decode(),
                                     NACOS_AUTH_IDENTITY_KEY='upgrade-it',
                                     NACOS_AUTH_IDENTITY_VALUE=secrets.token_hex(24))
                subprocess.run(['docker', 'create', '--name', name,
                    '-p', f'127.0.0.1:{args.main_port}:8848',
                    '-p', f'127.0.0.1:{args.main_port + 1000}:9848',
                    '-p', f'127.0.0.1:{args.console_port}:8080',
                    '-e', 'MODE=standalone', '-e', 'PREFER_HOST_MODE=ip',
                    '-e', 'NACOS_AUTH_ENABLE=false', '-e', 'JVM_XMS=256m',
                    '-e', 'JVM_XMX=512m', '-e', 'JVM_XMN=128m',
                    '-e', 'NACOS_AUTH_TOKEN', '-e', 'NACOS_AUTH_IDENTITY_KEY',
                    '-e', 'NACOS_AUTH_IDENTITY_VALUE', args.old_image],
                    env=container_env, check=True, stdout=dockerlog, stderr=subprocess.STDOUT)
                created = True
                subprocess.run(['docker', 'start', name], check=True, stdout=dockerlog)
                wait('3.2.3 server ready', lambda: get('/nacos/v3/admin/core/state')['data']['version'] == '3.2.3')
                result['oldState'] = get('/nacos/v3/admin/core/state')
                result['sourceCommit'] = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
                result['serverJarSha256'] = hashlib.sha256(jar.read_bytes()).hexdigest()
                command = ['mvn', '-B', '-pl', 'test/java-sdk-test', '-Pjava-sdk-integration-test',
                    '-DskipTests=false', '-Dit.test=A2aServerUpgradeJavaSdkITCase',
                    '-Dnacos.test.auth.enabled=false', '-Dnacos.port=' + str(args.main_port),
                    '-Dnacos.console.port=' + str(args.console_port),
                    '-Dnacos.ai.server-upgrade.enabled=true',
                    '-Dnacos.ai.server-upgrade.control.dir=' + str(control), 'verify']
                (out / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
                reports = root / 'test/java-sdk-test/target/failsafe-reports'
                if reports.exists():
                    shutil.rmtree(reports)
                with (out / 'sdk.log').open('w') as testlog, (out / 'server-330.log').open('w') as serverlog:
                    test = subprocess.Popen(command, cwd=root, stdout=testlog, stderr=subprocess.STDOUT)
                    wait('live clients verified old-server writes and changed callbacks',
                         lambda: (control / 'old-ready').exists(), 240)
                    result['stages'].append('old-server-read-write-listen')
                    subprocess.run(['docker', 'stop', '--time', '30', name], check=True, stdout=dockerlog)
                    subprocess.run(['docker', 'cp', name + ':/home/nacos/data', str(home / 'data')],
                                   check=True, stdout=dockerlog, stderr=subprocess.STDOUT)
                    manifest = {str(file.relative_to(home)): hashlib.sha256(file.read_bytes()).hexdigest()
                                for file in (home / 'data').rglob('*') if file.is_file()}
                    if not any('derby-data' in file for file in manifest):
                        raise RuntimeError('No persisted Derby database was copied')
                    (out / 'copied-data-sha256.json').write_text(json.dumps(manifest, indent=2) + '\n')
                    shutil.copytree(dist / 'conf', home / 'conf')
                    shutil.copytree(dist / 'plugins', home / 'plugins')
                    (home / 'logs').mkdir()
                    props = home / 'conf/application.properties'
                    properties = props.read_text()
                    values = {'nacos.server.main.port': args.main_port,
                        'nacos.console.port': args.console_port, 'nacos.ai.registry.port': args.registry_port,
                        'nacos.inetutils.ip-address': '127.0.0.1', 'nacos.core.auth.enabled': 'false',
                        'nacos.core.auth.admin.enabled': 'false', 'nacos.core.auth.console.enabled': 'false',
                        'nacos.plugin.auth.nacos.token.secret.key': base64.b64encode(secrets.token_bytes(48)).decode(),
                        'nacos.core.auth.server.identity.key': 'upgrade-it',
                        'nacos.core.auth.server.identity.value': secrets.token_hex(24),
                        'nacos.ai.a2a.compatibility.mode': 'AUTO',
                        'nacos.ai.a2a.migration.legacy-naming-shadow-enabled': 'true',
                        'nacos.ai.a2a.migration.reconciliation.interval-seconds': 1,
                        'nacos.ai.a2a.migration.quiescing-check-interval-seconds': 1,
                        'nacos.ai.mcp.resource.reconciliation.enabled': 'false',
                        'nacos.ai.resource.search.index.reconcile.interval-seconds': 1}
                    for key, value in values.items():
                        properties = '\n'.join(line for line in properties.splitlines()
                                               if not line.startswith(key + '=')) + '\n' + key + '=' + str(value) + '\n'
                    props.write_text(properties)
                    server_command = ['java', '-Xms256m', '-Xmx512m', '-Dnacos.standalone=true',
                        '-Dnacos.deployment.type=merged', '-Dnacos.home=' + str(home),
                        '-Dloader.path=' + str(home / 'plugins'),
                        '--add-opens=java.base/java.lang=ALL-UNNAMED',
                        '--add-opens=java.base/java.lang.reflect=ALL-UNNAMED',
                        '--add-opens=java.base/java.util=ALL-UNNAMED', '-jar', str(jar),
                        '--spring.config.additional-location=file:' + str(home / 'conf') + '/',
                        '--logging.config=' + str(home / 'conf/nacos-logback.xml')]
                    server = subprocess.Popen(server_command, stdout=serverlog, stderr=subprocess.STDOUT)
                    wait('3.3.0-RC server ready with copied data',
                         lambda: get('/nacos/v3/admin/core/state')['data']['version'] == '3.3.0-RC')
                    result['newState'] = get('/nacos/v3/admin/core/state')
                    query = urllib.parse.urlencode({'dataId': 'nacos.ai.a2a.migration.v1',
                        'groupName': 'nacos_internal', 'namespaceId': '_nacos_internal_'})

                    def canonical():
                        state = get('/nacos/v3/admin/cs/config?' + query)
                        content = state['data']
                        if isinstance(content, dict):
                            content = content['content']
                        marker = json.loads(content)
                        if marker['state'] == 'CANONICAL':
                            result['migrationMarker'] = marker
                            return True
                        return False

                    wait('historical data migration reached permanent CANONICAL', canonical)
                    result['stages'].append('persisted-data-migrated')
                    (control / 'upgraded-ready').write_text('ready\n')
                    code = test.wait(timeout=420)
                    result['sdkExitCode'] = code
                    if code != 0 or not (control / 'client-verified').exists():
                        raise RuntimeError('Upgrade SDK assertions failed; inspect sdk.log')
                    result['stages'].append('same-client-legacy-recreated-client-rad-native-all-verified')
                    print('Upgrade read/write/listener assertions passed for grpc/http/auto', flush=True)
        finally:
            stop(test)
            stop(server)
            if created:
                with (out / 'server-323.log').open('w') as log:
                    subprocess.run(['docker', 'logs', name], stdout=log, stderr=subprocess.STDOUT)
                subprocess.run(['docker', 'rm', '-f', name], check=True, stdout=subprocess.DEVNULL)
            reports = root / 'test/java-sdk-test/target/failsafe-reports'
            if test is not None and reports.exists():
                shutil.copytree(reports, out / 'failsafe-reports')
            if (home / 'logs').exists():
                shutil.copytree(home / 'logs', out / 'server-internal-logs')
            result['ownedProcessesStopped'] = True
            (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')


if __name__ == '__main__':
    main()
