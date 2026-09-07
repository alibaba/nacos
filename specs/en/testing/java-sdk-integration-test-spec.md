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

# Java SDK Integration Test Spec

This spec defines the integration-test model for Nacos Java SDK public
contracts. It complements the [API Integration Test Spec](api-integration-test-spec.md):
HTTP API ITs verify deployed HTTP contracts, while Java SDK ITs verify the
typed Java SDK behavior seen by applications.

The goal is SDK scenario coverage. It is not line coverage or branch coverage.

## 1. Scope

Public Client SDK IT lives in `test/java-sdk-test`. Maintainer SDK IT lives in
`test/maintainer-sdk-test`. Both modules assume a standalone Nacos server is
already running and create real external clients, but they keep separate Maven
profiles, reports, and failure boundaries.

This spec applies when changing:

- public interfaces such as `ConfigService`, `NamingService`, `AiService`,
  `A2aService`, `LockService`, and their maintainer-client equivalents;
- public factories such as `NacosFactory`, `ConfigFactory`, `NamingFactory`,
  `AiFactory`, and `NacosLockFactory`;
- public request, response, or domain models returned by SDK methods;
- listener, subscription, local cache, redo, factory initialization, shutdown,
  or exception mapping behavior;
- SDK configuration keys and defaulting behavior.

Unit tests remain necessary for isolated implementation branches, but they do
not replace Java SDK ITs for externally visible SDK behavior.

## 2. SDK Change Rule

Before implementing a Java SDK contract addition, modification, deletion, or
deprecation, the change owner must perform an SDK IT impact analysis:

1. Identify the affected SDK interface, factory, model, or listener path.
2. Read the public API, implementation, validators, transport mapping, response
   assembly, exception mapping, lifecycle code, and matching SDK/client specs.
3. Build a scenario matrix for factory/lifecycle behavior, expected capability,
   boundary/validation behavior, listener or subscription behavior, and
   exception/error handling.
4. Add, update, or remove `test/java-sdk-test` cases in the same change set.
5. Update `test/java-sdk-test/JAVA_SDK_IT_COVERAGE.md`.

If the full success path is not practical in standalone IT, the test must still
cover SDK parameter validation, local boundary behavior, controlled exceptions,
and any low-risk observable server interaction. The skipped path and reason
must be documented.

## 3. Required Scenario Groups

Every Java SDK IT should cover these groups when observable.

### 3.1 Factory And Lifecycle

Verify that the SDK can be created through the public factory with realistic
properties, honors server address and namespace defaults, and releases
resources through the public shutdown method.

### 3.2 Expected Capability

Verify that SDK methods perform the promised remote or local behavior. Prefer
publish-then-query, register-then-query, subscribe-then-callback,
lock-then-unlock, release-then-load, and delete-then-absent flows.

Assertions must check typed SDK return values, model fields, callbacks, and
remote side effects instead of only checking that no exception was thrown.

### 3.3 Boundary And Validation

Cover required parameters, optional defaults, invalid enum or type values,
namespace and group defaults, timeout behavior, malformed model objects,
listener identity requirements, duplicate or idempotent calls, and missing
resource behavior.

### 3.4 Exception And Error Handling

Verify that SDK-visible failures produce controlled `NacosException` or
documented return values. Tests should catch regressions where invalid input,
not-found resources, remote failures, or invalid lifecycle use become
unexpected runtime exceptions.

### 3.5 Listener And Subscription Behavior

For listener APIs, verify initial query behavior when applicable, callback
delivery for an observable change, unsubscribe/remove behavior, and cleanup.
Use bounded waits and clear assertion messages.

### 3.6 Authentication And Authorization

With the Nacos 3.3 default-auth baseline, verify successful remote behavior with
an audience-appropriate identity, missing and invalid credentials, an
authenticated identity without authority, read/write boundaries where the SDK
exposes both actions, and exact-resource boundaries where they are observable.
Authentication or authority failure must remain a controlled SDK exception or
documented result and must not be mistaken for timeout, not-found, empty data,
or local-cache success.

Listener, subscription, Watch, retry, reconnect, token refresh, redo, and
shutdown paths must preserve the same identity boundary. Tests must not create
a replacement client merely to hide re-authentication or reconnect defects.

## 4. Test Organization

Java SDK ITs should live under:

- `com.alibaba.nacos.test.sdk.config`
- `com.alibaba.nacos.test.sdk.naming`
- `com.alibaba.nacos.test.sdk.ai`
- `com.alibaba.nacos.test.sdk.lock`

Maintainer SDK ITs use the corresponding domain packages under
`test/maintainer-sdk-test/src/test/java/com/alibaba/nacos/test/maintainer`.

Prefer one public SDK interface, or one tightly coupled API family, per test
class. Shared client construction, cleanup, bounded waits, random resource
names, and shutdown handling should live in a base class.

## 5. Runtime Rules

Java SDK ITs must:

- use JUnit 5 and Failsafe;
- avoid `@SpringBootTest`, `SpringExtension`, and starting Nacos inside tests;
- read `nacos.host` and `nacos.port`, defaulting to `127.0.0.1:8848`;
- create real SDK clients through public factories;
- generate isolated resource names;
- cleanup created config, naming, AI, or lock resources;
- shut down every SDK instance even when assertions fail;
- use bounded retries for asynchronous server effects.

The standard Nacos 3.3 standalone SDK IT baseline uses the packaged defaults
with Client, Admin, and Console auth enabled. The workflow configures only the
deployment-specific token secret, server identity, test identities, and
functional fixtures; it does not force auth scopes or the authorization cache.

Public Client SDK functional tests use a non-admin identity with the minimum
read/write permissions required by the scenario. Maintainer SDK functional
tests use a global administrator or an explicitly scoped management identity
when that distinction is under test. Both modules add focused missing,
invalid, no-authority, and read-only cases instead of multiplying every
business workflow by every identity. Default and Jackson 3 adapters must use
the same auth expectations.

## 6. Scenario Documentation

Each SDK IT class must include a compact `Scenario coverage` Javadoc section,
or update the appropriate `JAVA_SDK_IT_COVERAGE.md` or
`MAINTAINER_SDK_IT_COVERAGE.md` when the matrix is large. The documentation
must say what is verified and why any branch is intentionally not covered.

## 7. Validation

For Java SDK IT changes, run:

- `mvn -pl test/java-sdk-test spotless:check`
- `mvn -pl test/java-sdk-test -DskipTests test-compile`

For Maintainer SDK IT changes, run:

- `mvn -pl test/maintainer-sdk-test spotless:check`
- `mvn -pl test/maintainer-sdk-test -DskipTests test-compile`

When a standalone Nacos server is available, run the relevant Failsafe
selection or
`mvn -pl test/java-sdk-test -Pjava-sdk-integration-test -DskipTests=false
verify`.

Java SDK ITs intentionally use the dedicated `java-sdk-integration-test` Maven
profile. The generic `integration-test` profile is reserved for HTTP API IT
workflows and must not accidentally run SDK tests that depend on SDK gRPC
connection readiness or optional server abilities.

Maintainer SDK ITs use the separate `maintainer-sdk-integration-test` profile.
The Client and Maintainer modules may share one running server and one CI job,
but one profile must not implicitly execute the other module.

## 8. AI Resource Search And Agent Scenarios

When public AI SDK Search or Agent behavior changes, Java SDK IT covers at
least:

- a real SDK client performing Agent single-condition, combined-predicate,
  numbered-page, and default-namespace queries;
- equivalent Agent catalog results over HTTP and gRPC for the same facts and
  transport selection;
- bounded convergence after Agent publish/online/offline/latest transitions,
  while Endpoint operations change Discover only;
- matching eligibility between generic single-type Search and
  resource-specific Agent, AgentSpec, Skill, Prompt, and MCP Search;
- the same Search contract for supported client transport
  `AUTO/HTTP/GRPC`, with a controlled exception when ability negotiation rejects
  a transport; and
- SDK shutdown, reconnect, and redo neither duplicate catalog-index writes nor
  expose Runtime Endpoints in Search results.

Protocol conformance for ARD artifacts remains covered by OpenAPI/adaptor IT.
Java SDK IT validates only observable catalog and Discover behavior through
public SDK contracts.

## 9. Agent Watch And Push Scenarios

When Agent Watch, listener events, or transport routing changes, Java SDK IT
uses real external clients and a standalone server to cover at least:

- `GRPC` and `HTTP` separately: initial existing and initially missing targets,
  definition/metadata/latest/label changes, runtime register/replace/
  deregister/health/expiry, filtered empty results, duplicate and A-B-A
  coalescing, unsubscribe/resubscribe, multiple listeners, and shutdown;
- listener delivery as complete replacement `SNAPSHOT`, fingerprint-equal
  suppression, one unavailable transition for absence, recovery snapshot,
  listener executor selection, slow/throwing listeners, and callback isolation;
- validation, authorization, conflict, local/server capacity, oversized Watch,
  Discover transient failure, push/long-poll timeout, executor rejection, and
  rejected-state cleanup without infinite retry;
- gRPC disconnect/reconnect, server restart, new connection wire keys, lost or
  duplicate Hint tolerance, late old-key notification, Subscribe/ACK failure,
  ability absence, and bounded polling fallback;
- HTTP complete-list generation changes, one long poll for many Agents, late
  old-generation response, repeated timeout, server switch or load-balancer
  node change, and restart recovery;
- `AUTO` initial gRPC success, never-connected gRPC settling on HTTP, gRPC Watch
  ability absence, connection-class migration, and no fallback on business
  errors; and
- non-Agent Prompt, Skill, MCP, AgentSpec, and legacy A2A operations remaining
  behaviorally isolated in every Agent transport mode.

Every asynchronous assertion uses an explicit bounded deadline and observable
SDK/API state. Fixed sleeps may pace retries but never constitute the success
condition.

## 10. MCP Compatibility And Runtime Endpoint Scenarios

When MCP Storage routing or lifecycle hosting changes, Java SDK IT must cover at
least:

- a real `AiService` releasing a new MCP Resource/Version, preserving the
  historical ID response, querying an explicit Version and latest, and
  observing the same enabled and published serving content;
- historical exact-Version conflict/overwrite behavior remaining isolated from
  standard lifecycle writes;
- `subscribeMcpServer` initial delivery, changed full-result callback,
  unsubscribe, resubscribe, and shutdown cleanup without a direct Naming
  subscription;
- current version-scoped Runtime endpoint registration and deregistration,
  Service/cluster/metadata compatibility, disconnect, reconnect, and redo
  restoring the same defensive publication snapshot without duplicating
  instances or losing another MCP publication;
- the Java Client continuing to use `mcpName` and not populating the dormant
  top-level gRPC `mcpId`, while active model, event, and response ID fields
  retain their current values;
- lifecycle reconciliation and management cutover causing no new Runtime
  publication, Naming layout, ability-negotiation, or public `AiService`
  interface behavior; and
- equivalent behavior through the default JSON adapter and Jackson 3 adapter
  with current request fixtures and response models.

Versionless Runtime Services, explicit transport lists, MCP Version ranges,
Client HTTP parity, and heartbeat renewal remain outside this matrix until
their separate designs are approved.

Historical reconciliation and cutover behavior runs in explicitly phase-gated
SDK classes under the dedicated migration workflow. Stable Client and
Maintainer SDK functional classes start from one terminal server state and must
not turn a pre-cutover conflict into an alternative successful test outcome.
Adapter parity remains part of the stable functional suite; it need not
duplicate a server-side migration transition unless adapter behavior itself is
under change.

## 11. Historical A2A Upgrade And Cluster Scenarios

When historical A2A migration changes, Java SDK IT complements the OpenAPI
`M-ST-01..10` matrix with real `A2aService`, `AiService`, Naming, gRPC/HTTP RAD,
Watch, reconnect, and redo clients. In particular, `M-ST-06`, `M-ST-09`, and
`M-ST-10` require observable client behavior rather than only internal
publisher assertions.

Directed three-member tests cover this cluster matrix:

| ID | Required cluster behavior |
| --- | --- |
| `M-CL-01` | With 0/3, 1/3, 2/3, and 3/3 capable members, historical authority remains until all abilities and gates pass. |
| `M-CL-02` | A historical write on member A is reconciled by lease owner B and canonical content is readable on C. |
| `M-CL-03` | Restarting lease owner, non-owner, Config leader, or Naming responsibility member preserves progress and availability. |
| `M-CL-04` | Member join/leave, lost ACK, and delayed marker observation during quiescing return safely to syncing or converge without split authority. |
| `M-CL-05` | Historical Config mutation on A, reconciliation on B, and historical/canonical reads on A/B/C converge. |
| `M-CL-06` | Endpoint publication on A with Naming responsibility on B converges in both historical and canonical Services. |
| `M-CL-07` | Load-balanced A/B/C reads during terminal marker propagation see equivalent definitions and Runtime snapshots. |
| `M-CL-08` | Complete rolling upgrades with shadow disabled and enabled satisfy their documented Gateway behavior. |
| `M-CL-09` | Pre-cutover rollback to historical authority succeeds; post-cutover rollback accepts only a canonical-aware binary. |
| `M-CL-10` | Ordinary Agent, Skill, Prompt, AgentSpec, MCP, and Naming registration/subscription remain isolated. |

Every test uses explicit bounded deadlines and public or stable wire behavior.
The suite does not assume load-balancer stickiness, one Config leader, one
Naming responsibility member, or fixed task execution order.

Historical A2A restart and rolling-cutover clients follow the same dedicated
migration-workflow boundary and do not run after the ordinary SDK functional
suite in the same job.
