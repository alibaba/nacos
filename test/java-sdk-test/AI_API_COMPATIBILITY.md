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

# AI API compatibility fixtures (3.3 phase 1)

The opt-in `AiServiceBinaryCompatibilityJavaSdkITCase` compiles two small
fixtures against **only `com.alibaba.nacos:nacos-api:3.2.4`**, using Java 8
source/target. Their unchanged bytecode then runs against the replacement SDK.
They are deliberately outside `src/test/java` so the normal test compiler cannot
silently compile them against the current API.

| Combination | Assertion |
| --- | --- |
| Old third-party implementation + old caller bytecode + new API | Inherited overloads still dispatch to old overrides; new optional getters throw the documented `UnsupportedOperationException`; MCP `createDraft=false` keeps the old override and `true` retains 501 for unimplemented third-party services. |
| Old application bytecode + new SDK + current server | MCP release/query/subscription/cancellation and old A2A release/query/Endpoint registration keep their public result shapes. |
| Released 3.2.4 SDK + current server | The same old MCP/A2A wire operations remain accepted. |
| New SDK + isolated 3.2.4 server (optional) | Old MCP/A2A capabilities remain usable without RAD. Native Agent Discovery is outside this smoke and is not supported by that server. |

MCP IDs and A2A's registered Endpoint URL are asserted against actual server
results. The application runs in a separate JVM with a bounded timeout, and
prints its SDK code source. Each child is closed normally or terminated by its
own process handle on timeout. No Nacos/Spring server is started by JUnit.

## Run

Use JDK 17 to run the IT harness. First build/install the current repository's
release SDK artifacts, as in the ordinary standalone workflow:

```bash
mvn -B clean install '-Prelease-nacos,!dev' -DskipTests=true
```

A plain development-profile `client install` is not a replacement for the
published SDK: its flattened POM and unshaded gRPC classes do not form the same
external-client classpath. Do not overwrite the release artifact with that
form before running IT.

Start a current standalone server externally. Configure authentication using
the existing [`../DEFAULT_AUTH_RELIABILITY_IT.md`](../DEFAULT_AUTH_RELIABILITY_IT.md)
identity setup. The child JVM receives the same ordinary client identity via
its private environment; credentials are not added to its command line.

From the repository root:

```bash
bash test/java-sdk-test/run-ai-api-compatibility.sh \
  '-Dit.test=AiServiceBinaryCompatibilityJavaSdkITCase,AiTransportResourceMatrixJavaSdkITCase' \
  -Dnacos.port=8848 -Dnacos.console.port=8080 \
  -Dnacos.test.auth.enabled=true -Dnacos.test.auth.anonymous-ai.enabled=true
```

For Jackson 3 add
`-Pjava-sdk-integration-test,ai-api-compatibility,jackson3-sdk-test`. The new SDK
child receives the selected adapter property; the released SDK retains its own
dependencies and adapter. To include the old-server row, start a **disposable,
auth-disabled, isolated 3.2.4 server** externally and add
`-Dnacos.ai.compatibility.old-server-address=127.0.0.1:8858`. Its data directory
belongs to the harness and must be discarded after the run. This row skips
when the address is omitted; it must not be described as a pass then.

Run `clean` before the wrapper if needed, not as a wrapper argument: the wrapper
resolves historical dependencies into `target` before invoking the IT lifecycle.
The compatibility profile is opt-in, so the ordinary required SDK suite does
not download historical dependencies or start extra SDK JVMs.

## Exact dependency and evidence boundaries

`src/test/compatibility/pom.xml` has no repository parent and depends on
`com.alibaba.nacos:nacos-client:3.2.4`. Resolving it independently prevents the
current reactor's dependency management from replacing old transitive Nacos
modules, including `nacos-client-basic`. The profile separately copies the old
API jar used by `javac`.

The wrapper retains these generated artifacts:

- `target/compatibility-libs/legacy-dependency-tree.txt`: exact resolved versions.
- `target/compatibility-libs/legacy-classpath.txt`: actual released-SDK classpath.
- `target/compatibility-classes`: old-API bytecode reused by both SDK runs.
- `target/failsafe-reports/legacy-*.log`: child output and SDK code source.
- `target/failsafe-reports/TEST-*.xml`: pass/failure/skip evidence.

`AiTransportResourceMatrixJavaSdkITCase` additionally covers three global modes,
opposite per-resource overrides, the factory's invalid-mode exception contract,
native HTTP with an unreachable gRPC port, and Prompt/Skill/AgentSpec missing
resource recovery, unchanged-content suppression, cross-entry cancellation,
resubscription, ZIP contents, and repeated shutdown. Maintainer calls only
prepare/clean fixtures; all assertions use the ordinary Client identity.

This fixture does not enable A2A-to-RAD conversion. Old A2A stays on old gRPC in
every resource mode. Existing `DAUTH-F04`/`DAUTH-F05` exclusions and gated
standalone-restart, cluster, and migration-state suites remain explicitly
separate. A representative 3.2.4 smoke is not evidence for all historical
versions, all resource APIs, or SYNCING/QUIESCING/cutover states. Per-run evidence
is recorded in `Codex/design/nacos-3.3-client-ai-api/VALIDATION.md`.
