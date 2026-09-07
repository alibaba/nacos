<!--
  Copyright 1999-2026 Alibaba Group Holding Ltd.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Default-Auth Reliability Integration Tests

This suite complements the required auth-enabled standalone IT in
`.github/workflows/it-new.yml`. It owns destructive process replacement and
multi-node fault injection that must not stop the shared server used by the
ordinary OpenAPI, Java SDK, or Maintainer SDK suites.

The scheduled/manual workflow is
`.github/workflows/default-auth-reliability-it.yml`. Every scenario uses the
packaged defaults and first asserts that Client, Admin, and Console auth plus
the default-auth cache are enabled. The runner only changes credentials,
ports, AI test capacity, and other scenario fixtures; it does not turn auth
off to make functional checks pass.

## Running Locally

Build a release distribution first, then select one suite:

```bash
mvn -B '-Prelease-nacos,!dev' -Dmaven.test.skip=true clean install
bash test/scripts/default-auth-reliability-it.sh standalone
bash test/scripts/default-auth-reliability-it.sh cluster
# Or run both sequentially.
bash test/scripts/default-auth-reliability-it.sh all
```

The runner generates ephemeral administrator, read-write, read-only, and
no-permission passwords, a token secret, and server identity values unless
the corresponding `NACOS_TEST_AUTH_*` environment variables are supplied. On
GitHub Actions, every generated secret is masked before a server is started.

Set `NACOS_RELIABILITY_DISTRIBUTION` to test a specific `.tar.gz` or `.zip`,
`NACOS_RELIABILITY_REPORT_DIR` to relocate reports, or
`NACOS_RELIABILITY_JAVA_MEMORY` to change the per-node JVM memory options.

## Scenario Matrix

| Suite | Scenario | Required evidence |
| --- | --- | --- |
| Standalone | Config restart | Two original `ConfigService` instances reconnect; the original listener receives post-restart state and both clients can query/publish with normal credentials. |
| Standalone | Naming restart | Original publisher/subscriber instances reconnect; ephemeral registration Redo and subscription callbacks recover; a later instance update is visible. |
| Standalone | Lock restart | A lease longer than the restart window proves process replacement clears the connection-scoped lock; both original clients reconnect and mutex compete/release/reacquire behavior remains correct. |
| Standalone | Agent and MCP restart | Retained as an exact directed scenario, but currently disabled as `DAUTH-F05` because the authorized gRPC Watch cannot resume after process replacement. |
| Standalone | Maintainer restart | The original Maintainer client recovers, persistent namespace state remains readable, a new namespace is created exactly once, and a duplicate write remains rejected. |
| Standalone | Jackson 3 restart | The Config restart contract is repeated with the Jackson 3 adapter. |
| Cluster security | Default and scope checks | All three nodes start auth-on; an intentional mixed Client-auth state is detected; uniform explicit false affects only Client APIs and leaves Admin/Console protected; the runner restores all nodes to true. |
| Cluster security | Token expiry | A token issued with a 5-second TTL expires on every node; a newly issued token is accepted by every node; the normal TTL is restored. |
| Cluster security | Permission cache | Naming permission revoke and regrant converge on every node within a bounded window while the default cache remains enabled. |
| Cluster client | Pinned-node changes | Retained as an exact directed scenario, but currently disabled as `DAUTH-F05` because an authorized pinned client cannot read the initial Agent definition. |
| Cluster client | Rolling node restart | With node B stopped, existing gRPC/HTTP Watches converge to Version 2; after B rejoins they converge to Version 3 without rebuilding SDK instances. |
| Cluster client | Peer restart | Watches pinned to node A remain active while a node-B client observes failure; after B restarts, later definition and Runtime changes converge without resubscription. |

Every retry and convergence assertion is time bounded. The Java tests exchange
marker files with the external runner so that the server is only stopped after
the client baseline is complete, and only restarted after the same process has
observed transport unavailability.

Synthetic Endpoint ports are allocated as a non-repeating sequence within each
Java test JVM. This is required because an Agent Runtime Endpoint natural key
uses host, effective port, and transport rather than the URI path; independently
random ports can otherwise collide and create an unrelated conflict after
cluster state converges.

On 2026-09-04 after the out-of-scope product fixes were rolled back, five
standalone scenarios passed and the Agent/MCP restart scenario was explicitly
disabled as `DAUTH-F05`. The complete cluster security matrix passed; rolling
restart and peer restart passed, while pinned-node changes was explicitly
disabled under the same finding. Each disabled scenario has a `status.txt`
containing its finding, exact test method, and reason. A run with such entries
reports `passed-with-disabled`, never an unqualified pass.

## Process Safety And Reports

Each server is extracted under a unique `mktemp` directory. The runner records
the exact PID together with its normalized `-Dnacos.home` value, verifies that
command line before sending `TERM` or `KILL`, and never calls a broad Nacos
shutdown script. Traps clean all owned processes on success, test failure,
interrupt, or timeout.

Reports are written under `target/default-auth-reliability` by default:

- `summary.txt` is written after every executable scenario passes and reports
  `passed-with-disabled` plus each disabled finding when applicable;
- `<scenario>/maven.log` contains the Maven/Failsafe execution log;
- `<scenario>/failsafe-reports/` contains the isolated XML and text reports;
- `server-*-startup-command.log` records non-secret startup output.

The workflow uploads that directory even after a failure. Passwords, access
tokens, token secrets, and server identity values must never be added to Maven
arguments, report names, checked-in files, or diagnostic summaries.

## Intentional Boundaries

The suite proves public SDK and server behavior at process/node boundaries. It
does not inject individual frame loss or manufacture an unknown result for a
non-idempotent write; those races remain deterministic client unit tests.
Distributed Lock is experimental and has no complete `SignType.LOCK`
authorization guard, so this suite verifies authenticated functionality and
reconnect behavior but does not claim an authorization-denial contract that
the server does not implement.
