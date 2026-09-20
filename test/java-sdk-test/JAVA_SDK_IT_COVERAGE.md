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

# Java SDK IT Coverage Registry

This registry records which public Java SDK interfaces are covered by
`test/java-sdk-test` integration tests and the scenario groups each class
verifies.

The detailed scenario matrix lives in
[`JAVA_SDK_IT_SCENARIOS.md`](JAVA_SDK_IT_SCENARIOS.md). A `Partial` status means
the current IT has representative coverage but must not be treated as complete
SDK API scenario coverage.

The historical A2A upgrade Runtime dual-materialization and restart coverage,
plus the directed three-member rollout matrix, is tracked in
[`A2A_MIGRATION_SDK_IT_SCENARIOS.md`](A2A_MIGRATION_SDK_IT_SCENARIOS.md).
Historical Verified rows are not automatically final C11 evidence. Current
standalone/cluster executions and deployment limits are recorded in the C11
section below and its linked report.

Java SDK ITs run only with the dedicated Maven profile
`java-sdk-integration-test`. The generic `integration-test` profile belongs to
HTTP API IT CI and should build this module without executing SDK IT cases.
Destructive restart and cluster cases run through
[`../DEFAULT_AUTH_RELIABILITY_IT.md`](../DEFAULT_AUTH_RELIABILITY_IT.md).

## Agent model consolidation regression

Agent/RAD concrete models now use `model.agent`; client-only requests use
`model.agent.client` and remain namespace-free. Existing grpc/http/auto Search and Endpoint
scenarios use these concrete inputs. `shouldSearchDiscoverAndIsolateNamespaces` additionally
checks inherited catalog metadata, shared version entries, and absence of management-only fields.
`AgentPublishJavaSdkITCase` exercises the sibling Client draft request through both transports.
Model UTs verify flat JSON fixtures, abstract bases, separate definition/discovery fields, and
Client/Admin validation. Fault recovery and cluster coverage statuses are unchanged.

## Authentication Baseline

Authentication coverage is cross-cutting and does not add another public SDK
interface to the registry below. `JavaSdkBaseITCase` can select explicit
anonymous, read-write, read-only, and authenticated-no-permission identities
through the shared `nacos.test.auth.*` properties. With auth enabled, every
public Config, Naming, AI, and Lock functional IT uses the normal read-write
identity; administrator credentials are reserved for Maintainer SDK fixture
setup. `AuthEnabledJavaSdkITCase` verifies Config and Naming read/write action
boundaries plus AI identity propagation through explicit HTTP, explicit gRPC,
and AUTO for anonymous, invalid, authenticated-no-permission, read-only, and
read-write callers. The invalid-credential/anonymous-AI assertion is retained
but currently disabled as `DAUTH-F04`, because the rolled-back client behavior
downgrades a failed explicit login to anonymous access.

The following is the historical 2026-09-04 snapshot; the C09/C10/C11 entries below
record the restored Agent visibility/Watch cases and the expanded matrix.
The default and Jackson 3 adapters discovered the same 101-test matrix. On
2026-09-04, each auth-enabled standalone run completed with 81 passes, 0
failures or errors, and 20 skips. Eight skips are exact product findings
(`DAUTH-F04` once and `DAUTH-F05` seven times); the other twelve are
environment-gated migration/restart/cluster cases. The reliability runner
executed Config, Naming, Lock, Maintainer, Jackson 3, rolling-restart, and
peer-restart scenarios successfully; the Agent standalone restart and pinned-
cluster-change methods remain explicitly disabled as `DAUTH-F05`. Synthetic
Endpoint ports are allocated without reuse within the test JVM so multi-
publisher scenarios cannot accidentally create the same natural key. The
Lock functional lifecycle is authenticated, but authorization denial remains
a precise gap because the current server Lock handler does not yet apply its
documented `SignType.LOCK` guard.

## Client SDK

| SDK interface | IT class | Status | Scenario coverage | Known gaps |
| --- | --- | --- | --- | --- |
| `ConfigService` | `ConfigServiceJavaSdkITCase` | Covered | Verifies factory creation, publish/query/getConfigWithResult/CAS/remove lifecycle, missing-result shape, missing/idempotent removal, standalone `addListener`, listener removal behavior, null listener rejection for add/sign/remove paths, client-side invalid parameter handling, valid `JSON` type metadata, unknown type compatibility, config filter request/response transformation, fuzzy-watch matched keys/add/delete/cancel behavior, missing config behavior, shutdown cleanup, Request/Result pattern API lifecycle (`GetConfigRequest`/`PublishConfigRequest`/`RemoveConfigRequest` with `PublishConfigResult`/`RemoveConfigResult`), CAS failure with detailed error code and message, and query result metadata assertion (content/md5/configType/encryptedDataKey). | `getConfig` timeout simulation is intentionally excluded from standalone Java SDK IT because it is not deterministic to force against the shared running server. |
| `NamingService` | `NamingServiceJavaSdkITCase` | Partial | Verifies factory creation, explicit/default group registration, string and `Instance` overloads, cluster string overloads, duplicate register idempotency, single persistent instance lifecycle, missing/repeated deregister idempotency, batch register, empty batch register no-op behavior, partial batch deregister, current null-list batch pre-remote failure behavior, query/select/list/deregister lifecycle, subscribe=true cached refresh, service-list pagination and deprecated selector overload boundaries, cluster and metadata behavior, explicit unhealthy selection, disabled/zero-weight filtering, subscribe callback delivery, subscribe state, cluster and public selector listener filtering, fuzzy-watch matched service keys/add/cancel behavior, null listener no-op, unsubscribe-stop behavior, validation for blank service, null instance, blank instance IP, invalid port/cluster, invalid heartbeat metadata, persistent batch member, empty batch deregister, mismatched group prefix, missing service empty result, no-healthy selection failure, and shutdown cleanup. | Fuzzy-watch delete-service events are not stable through public instance deregistration because the SDK has no public service delete API. |
| `AiService` / `A2aService` | `AiServiceJavaSdkITCase`, `McpHttpClientJavaSdkITCase` | Partial | Runs stable MCP functionality only after the fixture reaches `LIFECYCLE_MANAGED`. Verifies factory creation, MCP gRPC/HTTP release/query/subscribe, omitted and explicit `createDraft=false` direct-online compatibility, `createDraft=true` draft creation, MCP latest-published and duplicate-version controlled errors, Tool/Resource content, direct MCP endpoint-spec release, versioned and default/latest HTTP MCP Endpoint register/query/idempotent-register/deregister for remote REF servers, missing MCP Endpoint controlled error, MCP stdio Endpoint controlled error, invalid HTTP arguments, A2A Agent Card release/query/subscribe/unsubscribe-stop, A2A latest-version behavior, A2A duplicate-version idempotency, A2A missing-card get behavior, single/batch/TLS A2A Endpoint registration with endpoint-detail assertion, current-value listener callbacks, missing-resource nullable MCP/A2A/Prompt subscribe shapes, Skill/AgentSpec HTTP fallback and missing-resource nullable shapes, missing Skill download controlled exception, SDK validation for MCP/A2A/Prompt/Skill/AgentSpec required parameters, Endpoint validation, batch Endpoint version mismatch, and shutdown cleanup. Cross-contract IT additionally verifies Endpoint pre-registration before definition, exact/latest subscription convergence, cached resubscribe polling, and Agent plus MCP publication redo through a real standalone restart. | MCP migration behavior is owned by `McpUpgradeMigrationJavaSdkITCase`; MCP unsubscribe-stop behavior and the remaining Prompt/Skill label-selection variants remain. No known first-version A2A AgentCard or Endpoint lifecycle gap remains. |
| Historical `A2aService` Runtime migration | `A2aUpgradeMigrationJavaSdkITCase` | Covered | Against an `AUTO/SYNCING` release server, verifies old A2A single and complete-batch Endpoint publication into both historical exact-Version Naming and canonical RAD Runtime layouts, URI/path equality, independent multi-Version child publishers, one logical capacity charge across dual physical layouts, typed over-threshold rejection without cached retry state, slot reuse, connection-close cleanup, and fresh-publisher reuse. An opt-in controlled real restart keeps the SDK process alive and verifies redo of two exact Versions into both layouts plus isolated post-restart deregistration. Directed terminal methods retain one live publication through cutover, compare frozen shadow `true` and `false`, verify gRPC/HTTP Watch de-duplication, and restart a canonical-aware server locally configured as `LEGACY` to prove the marker remains authoritative. Directed three-member methods verify exact-Version replacement/deregistration through every Distro replica and a real 0/3-to-3/3 rolling cutover with fixed-node plus load-balanced reads. | A truly legacy-only binary cannot interpret the marker and remains a deployment/runbook exclusion rather than a destructive executable cluster case. |
| Five-resource `AiService` transport matrix | `AiTransportResourceMatrixJavaSdkITCase` | Covered | Against one real standalone server, verifies Agent definition publication, Search, Discover, subscription, Runtime Endpoint publication and cleanup; MCP release/query/subscription; Prompt version query/subscription; Skill version ZIP download; AgentSpec load/subscription; and an ordinary Naming register/query/subscribe/deregister control under explicit `grpc`, explicit `http`, and `auto`. Agent and MCP protocol-neutral operations follow the selected transport. The dedicated A2A migration workflow repeats the matrix in `AUTO/SYNCING` and after terminal cutover to prove migration Marker, shadow, retry, and capacity state remain isolated without mixing those restarts into the functional job. Skill and AgentSpec polling/query/download use HTTP in all requested modes; the same matrix adds opposite resource overrides and native HTTP with an unreachable alternate gRPC port. | The matrix verifies current routing compatibility rather than adding gRPC implementations for Skill or AgentSpec. AUTO with deliberately unreachable gRPC is covered separately by `AgentDiscoveryServiceJavaSdkITCase`; shared Agent/MCP HTTP Client heartbeat and restart recovery are covered there as an opt-in directed test. |
| `AiService.agent().publishAgent` | `AgentPublishJavaSdkITCase` | Partial | C05 replaces earlier equivalent-only behavior with first-Version submit, full draft replacement, non-draft no-op, direct/copy inputs and caller/governance isolation across HTTP/gRPC/AUTO. | C05 HTTP/gRPC/AUTO and two JSON adapters passed; auth and final matrix status are recorded in ADAPTATION_VALIDATION.md. |
| `AgentDiscoveryService` / `AiService.agent()` | `AgentDiscoveryServiceJavaSdkITCase` | Partial | Verifies default/custom namespace isolation and binding; immutable caller models; literal, typed, paged, and convergent Search; omitted/latest/exact/label and fully filtered Discover; complete Endpoint replacement, idempotence, single-key/multi-key partial removal and final/whole-multi-key deregistration under grpc/http/auto, mixed unknown keys, preserved Endpoint fields and Version bindings, protocol isolation, two-publisher aggregation, pre-registration, Version ranges, and rollout-safe multi-Version pools. Subscription coverage includes subscribe-before-create `UNAVAILABLE` to complete `SNAPSHOT`, negotiated gRPC fingerprint Hint followed by authoritative Discover, generation-based HTTP Batch Long Poll, canonical intent sharing, multiple and throwing listeners, silent long-poll timeout, partial/final unsubscribe, resubscribe, shutdown suppression, complete-fingerprint de-duplication, local subscription capacity, authoritative server gRPC/HTTP Watch soft-watermark rejection/cleanup/reuse, latest-addition HTTP rejection with prior-batch retention, and publication capacity/redo cleanup. Transport coverage includes HTTP/gRPC parity, stable cross-transport canonical fingerprints, AUTO with available gRPC, AUTO immediate HTTP Watch when gRPC remains STARTING, explicit HTTP independence, explicit GRPC no operation fallback, bounded polling compatibility fallback, validation/error mapping, default and Jackson 3 adapters, and reusable `AUTO`/`INDEX`/`SCAN` Search projection assertions. Legacy A2A coverage verifies canonical Console/RAD projection, exact-Version child publishers, SERVICE compatibility without Beta historical Naming dual-write, pre-registration, duplicate protection, multi-Version aggregation, and later canonical publication. One opt-in directed IT stops and restarts a real standalone server while the same SDK process verifies connection failure, protocol-neutral and legacy gRPC publication redo, shared Agent/MCP HTTP `50404` replay, restored MCP REF Runtime Endpoint visibility, gRPC Watch ability renegotiation and resubscription with a new connection-scoped key, HTTP Batch Long Poll recovery, Search convergence, and later Version/Endpoint callbacks. A second opt-in directed IT stops and restarts one node of a three-node cluster and verifies that independent gRPC and HTTP Watches converge both while the node is unavailable and after it rejoins, while omitted and exact selectors preserve their documented multi-Version semantics. A third opt-in directed IT pins gRPC and HTTP subscribers to node A, performs definition and Runtime mutations through A and B independently, and cross-validates every A-A/A-B callback against complete Discover fingerprints on both nodes. A fourth opt-in directed IT stops and restarts node B in that two-node topology, accepts temporary CP unavailability while the cluster lacks a majority, and verifies that the original A-side gRPC and HTTP Watches consume later B-owned definition and Runtime changes after quorum returns without resubscription. | Seven exact default-auth scenarios are disabled as `DAUTH-F05`: five ordinary Watch/version/capacity methods, standalone restart recovery, and pinned-node cluster convergence. Rolling restart and peer restart pass. Management-metadata subscription, public local-selection helpers, legacy Naming serviceName dual-write, and all-HTTP dual identity headers remain deferred. Ability changes after a successful connection, individual frame loss/ACK ambiguity, non-50404 heartbeat failures, rollback, and redo races remain deterministic unit-test scenarios rather than unstable shared-server fault injection. |
| `LockService` | `LockServiceJavaSdkITCase` | Covered | Verifies factory creation, distributed lock acquire/compete/release/reacquire lifecycle, repeated release boundary, expiration-based reacquire, unsupported lock type and missing key error mapping, null lock-instance SDK boundary, direct `remoteTryLock`/`remoteReleaseLock`, shutdown cleanup, and original-client reconnect after real standalone replacement. The directed case proves a long connection-scoped lease is reset rather than expiring naturally, then verifies mutex exclusion, release, and reacquire through both original clients. | Lock authorization denial remains excluded because the server does not yet implement the documented `SignType.LOCK` guard. |

The Agent Discovery row also covers immediate current-snapshot Search availability through both
gRPC and HTTP without a readiness error, followed by polling to the complete converged catalog.

## Pending SDK Surfaces

The following SDK surfaces are documented by
`specs/*/testing/java-sdk-integration-test-spec.md` and should be added in
later batches:

- deprecated `NamingMaintainService`
- maintainer-client SDK interfaces are tracked separately in
  `test/maintainer-sdk-test`

## AI Resource Interface Compatibility (3.3 phase 1)

This increment is separate from the historical surface denominator. The scope
is interface delegation and resource transport; A2A-to-RAD conversion is deferred.
See [AI_API_COMPATIBILITY.md](AI_API_COMPATIBILITY.md) for the executable old-API
fixture and exact released dependency resolution.

| Public SDK surface | Required scenarios | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| AiService resource accessors and legacy delegates | Stable delegates, old/new validation and default dispatch, shared state, cross-entry cancellation and shutdown | Covered | AiServiceJavaSdkITCase verifies all-five-resource validation parity, MCP cross-entry state and A2A query parity. Resource matrix adds Prompt/Skill/AgentSpec recovery from absence, unchanged-content suppression, cross-entry cancellation, resubscription and repeated shutdown under grpc/http/auto. |
| AgentService via agent() | Native Search/Discover/Watch/publication/publish and old A2A remain usable through the new owner | Partial | Existing AgentPublish and directed AgentDiscovery regressions verify publishing, namespace isolation, HTTP/gRPC result parity, independent publishers, pre-registration, replacement/deregistration, shutdown and legacy A2A interoperation. Existing DAUTH-F05 Watch/restart exclusions remain; migration-state/cluster harnesses were not executed in this phase. |
| Five resource transport overrides | Inheritance, opposite mixed modes, effective HTTP-only resources, strict configuration, connection-only read fallback and shared recovery | Partial | Resource matrix verifies three global modes, opposite overrides, native HTTP with unreachable gRPC, old A2A's original runtime error and continued native HTTP use, public factory errors and polling lifecycle. Auth matrix verifies three modes with real identities. UT covers immutable modes, all invalid explicit values, independent AUTO budgets, forced gRPC/A2A pins, business-error priority and owner replay. Real shared Agent/MCP restart recovery retains the existing DAUTH-F05 gap. |
| Released API bytecode and representative old SDK/server | Old third-party override/default resolution, old application with replacement SDK, both old/new SDK on current server, new SDK on a non-RAD server | Covered | Opt-in binary fixture compiles against nacos-api:3.2.4 only; isolated JVMs run the released nacos-client:3.2.4 dependency tree or the new SDK. Old-server evidence requires the separately supplied disposable 3.2.4 instance. Scope is old MCP/A2A operations, not every legacy version or native RAD. |

Phase 1 increment: 2 Covered / 2 Partial / 0 Pending; strict coverage
`2 / 4 = 50%`; effective coverage `(2 + 2 * 0.5) / 4 = 75%`.
These figures measure the four declared scenario groups, not code coverage or
all AI capabilities. Existing domain-level Partial rows and known findings are
not upgraded by this increment. Actual commands, adapter results, and skips
are recorded in `Codex/design/nacos-3.3-client-ai-api/VALIDATION.md`.

## Client namespace input correction (3.3 review)

| Public SDK surface | Required scenarios | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| Agent Search and Endpoint inputs | No namespace fields/accessors in public inputs; instance-bound search/register/deregister under HTTP, gRPC and AUTO; immutable inputs and existing validation | Covered | Public API contract tests reject namespace accessors and old wire-DTO overloads. The two-namespace lifecycle IT passes in grpc/http/auto with immutable Search/Endpoint inputs; default JSON and Jackson 3 both pass. Original wire serialization, HTTP/gRPC mapping, validation and authorization regressions pass. |

Separate review increment: strict 1/1 = 100%; effective 1/1 = 100%. This single input-contract group does not change the historical domain denominator or the existing migration/reliability gaps.

## Endpoint multi-key deregistration and transport matrix (3.3 review)

| Public SDK surface | Required scenarios | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| Multi-key deregistration | Three Endpoints minus two known keys plus one unknown key; retained Endpoint fields and Version bindings; immutable inputs; repeated no-op and whole multi-key removal | Covered | The existing publication UT verifies both HTTP/gRPC owners send one complete remainder and no whole deregistration. The lifecycle IT verifies the retained fields/bindings and final server state in all three configured modes. |
| Publication lifecycle transport matrix | Register, replace, partial removal, last removal, whole multi-key removal and protocol isolation under grpc/http/auto | Covered | The lifecycle IT is parameterized by AgentTransportMode and passes under grpc/http/auto with both default JSON and Jackson 3 (three invocations per adapter); both concrete owner transports are also asserted in UT. |

This increment excludes partial-deregistration fault injection, reconnect, server restart and cluster recovery at the user's request. Existing tests and Disabled markers remain unchanged outside the normal publication workflow. Broader Agent publication coverage remains Partial.

Separate normal-publication review increment: strict `2 / 2 = 100%`; effective `2 / 2 = 100%`. These two scenario groups do not change historical SDK denominators or establish recovery coverage. Actual adapter runs and validation are recorded in `Codex/design/nacos-3.3-client-ai-api/VALIDATION.md`.

### Agent 元数据模型合并（2026-09-14）

Agent 元数据合并：Search 的 AgentSummary/versionInfo/AgentVersionSummary 新路径及字段隔离纳入现有发现 IT，执行结果以本轮验证记录为准；不新增已覆盖行或提升覆盖状态。

本轮独立验证：默认 SDK 58 项通过、10 项既有跳过；Jackson 3 定向 9 项通过。详见 `Codex/design/nacos-3.3-client-ai-api/MODEL_VALIDATION.md` 的 2026-09-14 记录。

### Agent 地址模型统一：实施与验收（2026-09-15）

CallInterface → EndpointSet → Endpoint 统一已落地，验收要求见 [测试矩阵](../../Codex/design/nacos-3.3-client-ai-api/MODEL_ENDPOINT_TEST_PLAN.md)，本轮实际执行见 [验证记录](../../Codex/design/nacos-3.3-client-ai-api/MODEL_ENDPOINT_VALIDATION.md)。healthy 注册可写，服务端维护字段忽略；管理 Runtime 读取改为 `callInterface.endpointSets[].endpoints[]`，状态和绑定位于 Endpoint，观察时间位于 Set。旧 A2A wire 不变。以下原有覆盖状态不以编译通过或历史测试数量自动提升。

本轮补充非空 PUBLIC Agent Watch（GRPC/HTTP × 默认/Jackson 3），覆盖 healthy 变化、3 删 2、最后注销与回调/查询模型一致；原 DAUTH-F04/F05 及可选旧服务端/集群缺口保持，具体执行数见上述验证记录。

### 2026-09-15 请求整合回归

AgentDiscoveryService 现有覆盖行已迁移到共享 Search/RegistrationBatch 和三参数注销，并增加同名 Agent 双 namespace 隔离断言；未新增独立 API surface，覆盖率分母不变。

本轮实际执行状态见 [请求整合验证记录](../../Codex/design/nacos-3.3-client-ai-api/MODEL_REQUEST_VALIDATION.md)。
既有 Covered/Partial/Pending 表示场景覆盖归属，不表示本轮已重新执行；不能引用前轮结果代替本轮验收。

## Agent JSON 注解移除（2026-09-16）

Agent 去注解更新：Endpoint 生效默认值、完整查询 Endpoint 直接多项注销、onlineCnt()/latestVersion()；延续 GRPC/HTTP/AUTO × Jackson 2/3 × INDEX/SCAN、Watch、旧 A2A 与正常迁移矩阵。

[本轮测试矩阵](../../Codex/design/nacos-3.3-client-ai-api/MODEL_JSON_TEST_MATRIX.md)区分待执行项与实际结果。

The shared-listener fixture is PUBLIC for JSON regression coverage. The initial private
fixture reproduced DAUTH-F05 (`TERMINATED/-404` after successful synchronous Discover);
private authorized asynchronous Watch remains an explicit gap, not a passing claim.
Listener-sharing, failure-isolation and partial-unsubscribe assertions are retained.

## CONSOLE-ERR-01 impact（2026-09-16）

NacosApiException adds a raw-business-code constructor for Maintainer HTTP error propagation.
Client SDK interfaces, transports, callbacks and exception mapping are unchanged. Its constructor
is covered by API UT; the affected public SDK end-to-end checks are maintained in
[Maintainer SDK coverage](../maintainer-sdk-test/MAINTAINER_SDK_IT_COVERAGE.md), including both JSON adapters.
No Client SDK coverage status is upgraded by those results.

Agent/MCP visibility additions: `AgentPublishJavaSdkITCase` and `McpHttpClientJavaSdkITCase` verify HTTP/gRPC default-public creation, separate READ-only consumers, private/public transitions, and Agent publication retry preserving PRIVATE. No Client SDK signature changes are introduced. The additional `shouldInvalidateWatchAfterScopeBecomesPrivate` regression is disabled with `DAUTH-F05`: auth-enabled HTTP Watch failed to produce the initial snapshot before any scope mutation. Its UNAVAILABLE/error/payload assertions remain intact for restoration after the independent identity fix. Existing restart and environment-gated coverage remain unchanged.

## Config detail schema impact (#15853)

`ConfigDetailInfo` and `ConfigHistoryDetailInfo` are management response models.
Runtime `ConfigService` does not return them, so its client SDK IT scenarios are
unchanged. Maintainer SDK coverage and the non-null publish limitation are
recorded in `../maintainer-sdk-test/MAINTAINER_SDK_IT_COVERAGE.md` and
`../maintainer-sdk-test/MAINTAINER_SDK_IT_SCENARIOS.md`.

## Planned A2A/RAD adaptation evidence (2026-09-16)

Design-only target, not newly executed coverage:
[scenario registry](JAVA_SDK_IT_SCENARIOS.md#a2a-to-rad-adaptation-target-2026-09-16-design-only),
[SDK scenario matrix](JAVA_SDK_IT_SCENARIOS.md).
All 18 A2A + 10 native Agent signatures remain Pending for the new contract
(strict/effective target coverage 0/28=0%). Historical implemented totals above are unchanged.
The next implementation must replace old assertions for forced A2A gRPC,
exact latestVersion probing, equivalent-only draft publication and polling-first A2A;
retain real legacy-server/legacy-wire assertions separately.
Record all four RAD transport outcomes, real non-RAD servers, both facade/resource entries,
identity-only capability errors, and unresolved/disabled tests. AUTO configuration alone
does not prove AUTO-to-gRPC and AUTO-to-HTTP.

O1 is now agreed in matrix §7.1: field-level binding defaults, range-boundary
validation, writable enabled and removal of state with Console-derived labels.
O2 is also agreed: one registration binding, continuous compatibility range, and
deregistration that preserves the range until the final reference is removed (matrix §7.2).
All targets remain Pending; historical ignored-field assertions do not validate the new contract.

Five-resource capability consumption is planned in matrix §10.1, including partial
declarations and old-server compatibility. Adding HTTP mcp/skill/prompt/agentSpec keys
does not claim new gRPC implementations or increase the SDK signature denominator.

O3 is now agreed in matrix §6.1: only a newly created first Version forces submit;
an existing draft follows autoSubmit, fully replaces its definition and removes omitted
protocols. Creation/update/submit failures propagate without automatic retry, recreation
or recoverEquivalent success conversion. Cover a caller's later explicit invocation
separately, including false after a failed first submission. All ten detailed targets
remain Pending; neither historical equivalent-retry tests nor this design raise coverage.

O4 is agreed in matrix §5.1: definition source order becomes preference-only and
must include both sources. Explicit Discover/Watch source selection remains valid,
including querying Runtime under a declared-first definition. Add default/explicit
selection, invalid-definition, empty-Set and A2A projection regressions. All eight
detailed targets remain Pending; implemented signature totals are unchanged.

O6 is agreed: consume capabilities under standard Client auth/global-switch behavior,
without endpoint-specific authentication policy. O5 policy is agreed, but historical migration
evidence covers the old wire and cannot prove explicit rejection of unready new RAD requests.
Record distinct query, definition mutation and Runtime publication outcomes, including
same-name resources not yet projected. Target signature coverage remains 0/28.

SDK-first upgrades are required. The agreed O5 retention policy needs a continuous-process
rolling-upgrade fixture: abilities refresh across old/new nodes, but all old methods, redo
and polling of a legacy-selected instance retain their wire without endpoint/listener
handover. Re-instantiation reevaluates capabilities; failed/unknown initial probes must not
latch a mode. Initial RAD selection during migration now accepts explicit 50105 rejection
uniformly on nodes retaining historical authority, including unrelated standard resources.
Capabilities and existing-intent cleanup remain available. Add error-preservation and no-business-write
assertions for unprojected names, queries/Watch and Runtime operations, followed by success
on an explicit call after cutover. Current old A2A is always legacy; the initial capability
router and complete migration protection are not implemented. All targets remain Pending.
Do not substitute a server-first deployment sequence for this missing evidence.

## Staged adaptation validation (2026-09-17)

The [SDK scenario matrix](JAVA_SDK_IT_SCENARIOS.md)
requires tests with each implementation commit and a full rerun after all changes. The
[SDK scenario matrix](JAVA_SDK_IT_SCENARIOS.md) now contains
148 main groups and 73 detailed targets; section 16 assigns every main group to its first
responsible stage. These counts are test-design inventory, not executed coverage.

| Stage | SDK evidence required | Status |
| --- | --- | --- |
| C01–C06 | Migration rejection, native Endpoint/source/publication contracts, capability consumers; existing public behavior tested at each stage | Pending |
| C07–C09 | Adapter UT plus already exposed native/legacy regression; no public A2A-to-RAD coverage claim yet | Pending |
| C10 | All 18 old and 10 native signatures through actual public factories/interfaces, four N/three L environments and applicable F/R entries | Pending |
| C11 | Full final matrix at final source SHA, both JSON adapters, legacy artifacts, other 28 AI signatures and directed fixtures | Pending |

Keep target coverage 0/28 until executed. DAUTH-F05 cannot be hidden by auth-off or Disabled
cases; MODEL-D01 remains a separately recorded baseline issue. Source-mixing rejection and
public metadata precedence now have explicit E25-a/b and E23-a assertions.

### C01 migration admission implementation (2026-09-17)

New `AgentMigrationClientOpenApiITCase` uses an external historical-authority server with
`-Dnacos.agent.migration.gate=blocked`. It asserts Search/Discover/Publish/Register/Watch
50105 for both independent managed and unprojected names, no HTTP owner or publication
side effects, heartbeat 50404 for missing owners, and whole cleanup remaining available.
`A2aMigrationAdminApiOpenApiITCase` now observes internal convergence through management
while asserting native RAD rejection before cutover. `A2aUpgradeMigrationJavaSdkITCase`
retains old-wire mutation/layout assertions, checks native rejection, and installs RAD Watch
after terminal cutover. Runtime mirror inspection before cutover is management evidence,
not successful Client discovery coverage. `AiTransportResourceMatrixJavaSdkITCase` accepts an
explicit blocked fixture to verify other AI resources while native Agent publication is fenced.

Execution evidence is recorded in the SDK scenario and coverage records.
The overall C01–C11 target matrix remains pending until its required environments are executed.

### C02 per-Endpoint binding scenarios (stage validation passed)

| Contract | Required external evidence |
| --- | --- |
| Batch defaults and Endpoint overrides | HTTP and SDK: inherit independently; override runtime only with inclusive/exclusive default range; absent range resolves after override |
| Input cardinality / atomic failure | Empty, null-item or multiple bindings, invalid version/range and a later invalid Endpoint reject the entire replacement; original publication remains |
| Per-Endpoint versions | One batch with distinct effective versions; version-specific Discover and management snapshots preserve each binding |
| SDK lifecycle | Caller mutation does not affect cached bindings; remove two of three Endpoints and retain the third binding; final removal cleans publication |
| Query aggregation | Two publishers sharing one natural key produce multiple output bindings; multi-binding output cannot be registered as one input |

UT additionally covers deep-copy/redo and error mapping. Default and Jackson 3 SDK adapters are both required.
Stage evidence: 9 HTTP scenarios and 7 SDK cases per adapter (default/Jackson 3) passed; see ADAPTATION_VALIDATION.md for fixture corrections and environment limits. The final full matrix remains pending.

### C03 Endpoint enabled and derived state (stage validation passed)

| Surface | Scenarios | Stage evidence |
| --- | --- | --- |
| Runtime registration | healthy/enabled default true; disabled publication retained in management, excluded per publisher from discovery, restored by replacement; no state property | Passed: HTTP/gRPC/AUTO with default and Jackson 3 adapters |

Final adaptation matrix remains pending; see the staged validation report.

### C04 Source preferences (stage validation passed)

| Surface | Scenarios | Stage evidence |
| --- | --- | --- |
| Definition and discovery sources | Both definition orders; reject single/null/empty/duplicate/unknown orders before writes; explicit source selection, reversed filter order, empty Runtime without fallback; Watch selection and cancellation; Admin/Console/Maintainer and Artifact regression | Passed: 33 HTTP + 4 independent Console; 13 SDK and 4 Maintainer per JSON adapter. Auth-off stage; see ADAPTATION_VALIDATION.md for final auth-on/Disabled exclusions. |

Final A2A projection routing (G14-d) remains assigned to C07/C10. MODEL-D01 protocol-union behavior remains outside this change.

### C05 Client publication (stage validation passed)

| Surface | Scenarios | Stage evidence |
| --- | --- | --- |
| Agent publish | First Version forces ordinary submit; Admin-created draft remains a draft with false; complete replacement including protocol removal and basedOnVersion; later versions respect flag; non-draft no-op preserves digest/latest/governance, offline included; caller object unchanged | Passed on final C05 artifact with auth enabled: 17 HTTP/Console/Artifact, 6 independent Console, 14 SDK and 4 Maintainer per JSON adapter (59 total, zero skips). Earlier auth-off runs are archived separately. |
| Failure/concurrency | No server/client publication replay; Resource/Version transactional CAS; reject update after submit/delete and stale first-version detection; storage failure and unknown commit preserve verified active content | Passed: domain/transport UT and 12 real Derby transaction cases through both repository implementations; 1,214 related UT in total. Real READ-only/no-permission no-op denial, scope/owner preservation and explicit delete/recreate passed. Asynchronous Watch remains C09. |

### A2A/RAD C06 capability preparation

The HTTP capability parser/cache and connection-scoped negotiation components are tested
independently before the legacy facade is enabled in C10. They add no public SDK signature
and do not change the coverage denominator. C06 reruns the public legacy Card
release/query/subscription and endpoint replacement scenarios plus five-resource
HTTP/gRPC/AUTO and HTTP-only regressions in both JSON adapters. Directed component
probes against the official 3.2.4 server and an HTTP-only gateway are recorded separately
in the SDK scenario and coverage records; they do not count
as public A2A-to-RAD SDK coverage.

### C07 A2A/RAD metadata interoperation

`AgentDiscoveryServiceJavaSdkITCase.shouldInteroperateA2aMetadataWithRadAndChangeRevision`
uses two public SDK instances. Legacy Card publication and Endpoint registration are
observed with native RAD discovery in HTTP/GRPC/AUTO modes. Tenant-only and protocol-version-only
changes must alter Runtime revision; tenant also changes the complete discovery fingerprint.
After legacy cleanup, native RAD metadata is projected back into a complete old Card, including
an unhealthy endpoint, stored URL preference and both complete interface arrays.
Run with default and Jackson 3 SDK adapters. This proves current public interoperation,
not the C10 A2A-to-RAD facade routing, which remains pending.

C07 execution: 74 external cases passed (11 SDK cases per JSON adapter, 34 HTTP cases,
18 independent-Console cases), with authentication enabled and no skips. See
ADAPTATION_VALIDATION.md for artifact hashes and component-test evidence. The legacy
SDK facade still uses the old wire; public A2A-to-RAD routing is validated in C10.

### C08 staged Endpoint-intent coverage

The A2A-to-RAD merger is an internal component until C10. C08 UTs cover per-Version
replacement, continuous retained ranges, conflicts, source ownership, rollback,
unknown writes, serialized reconnect replay and shutdown. Native public SDK IT
regression covers bindings, multi-item removal, namespace/publisher isolation,
capacity and HTTP cleanup on HTTP/gRPC/AUTO and both JSON adapters. These runs
do not claim public legacy-to-RAD routing coverage before C10. Final execution:
15 SDK cases per JSON adapter and 7 HTTP cases passed with real identities,
no skips; 716 default-adapter UTs and 81 Jackson 3 UTs passed. One existing MCP
Disabled UT is recorded separately. See ADAPTATION_VALIDATION.md.

### A2A/RAD C09 incremental coverage

`AgentDiscoveryServiceJavaSdkITCase#shouldWatchExistingAgentOnlyWhenCompleteFingerprintChanges`
now covers HTTP/GRPC PRIVATE Agent visibility for a granted non-owner, initial return,
complete replacement, unchanged fingerprint suppression and cancellation. DAUTH-F05
Agent methods are restored; real restart and pinned-cluster fixture results remain separately
required. `AgentPublishJavaSdkITCase#shouldInvalidateWatchAfterScopeBecomesPrivate` checks
visibility loss without requiring an initial callback after a synchronous snapshot return.
See the SDK scenario and coverage records for executed stage evidence;
public legacy A2A-to-RAD routing is still reserved for C10.

### C10 public routing activation — stage validated

`A2aRadRoutingJavaSdkITCase` enumerates all 18 legacy overloads through both public
entrypoints and the 10 native signatures, with separate real RAD, HTTP-only and
released-old-server environments. Optional external fixtures must be enabled and
executed before their environment is covered. C10 completion and per-binding evidence
are tracked in the adaptation validation report; compilation alone is not coverage.

C10 execution: 56 public SDK cases per JSON adapter and one real reconnect case
(three persistent instances) passed, with no skips. The full scenario and transport
observability matrix remains C11 work; see the SDK scenario and coverage records for scope.


### C11 final artifact regression (2026-09-18)

Final product commit `9f5ab4d9f` passes the auth-enabled full SDK suite with each
JSON adapter: 175 executions, 146 passes, 29 conditional/baseline skips and no
failures. Separate fixtures pass 8 released-SDK/binary cases, 16 observed
HTTP/gRPC/AUTO transport cases, 16 network-boundary cases, 12 no-Watch fallback
cases and 6 lost-publication-response cases. The same-address old/new/old case
covers grpc/http/auto instances, pinned legacy mode, new-instance RAD selection,
same-instance multi-Version redo, partial deregistration and no RAD downgrade.

Standalone migration runs pass 19 cases with shadow enabled and 6 with shadow
disabled. They use isolated released 3.2.4 SDK processes; current SDK calls are
asserted separately. Original-instance Config, Naming, Lock, Agent/MCP,
Maintainer and Config Jackson 3 restart cases all pass. Cluster results, final
artifact hashes, baseline skips and component-only boundaries are recorded in
[SDK scenario matrix](JAVA_SDK_IT_SCENARIOS.md).
The [SDK scenario matrix](JAVA_SDK_IT_SCENARIOS.md)
retains all 148 main groups and 73 subcases without changing SDK/API denominators.

### A2A metadata reserved-key regression

A2A conversion and RAD metadata use the same `__nacos.agent.endpoint.protocolVersion__` / `__nacos.agent.endpoint.tenant__` keys as legacy Naming Instances. AgentDiscoveryService and A2aRadRouting SDK ITs verify HTTP/gRPC/AUTO Discover/Watch and legacy projection under default/Jackson 3 profiles; migration comparator UTs verify identical old/new meaning and changed tenant/protocol detection. Invalid control metadata remains rejected.

### A2A/RAD review 范围收敛

Client 发布契约仍为首版普通提交、已有 DRAFT 完整覆盖、非 DRAFT no-op；Admin/Console 保持原有流程。
本轮移除独立存储对象和 Agent 专用 CAS 加固，不新增跨存储或并发原子性保证；历史事务测试不计入当前覆盖。
公开 API 场景集合未增删，覆盖率分母不变；本轮执行证据及限制见
[既有存储边界](../../specs/zh-cn/ai/agent-storage-spec.md)。

### Discover 公共描述与标签（本轮范围）

仅扩展已有 Discover/Watch 响应，不增加 API surface，覆盖率分母不变。
`AgentDiscoveryServiceJavaSdkITCase#shouldWatchCurrentMetadataForAnExactOldVersion`
覆盖 HTTP/gRPC/auto：旧版本订阅首次返回当前 description/tags、分别修改描述与标签的回调、
标签换序不通知、空过滤仍返回元数据、清空字段及取消订阅；同时断言 contentDigest、
nativeDescriptor 和 Endpoint sourceRevision 不变。默认与 Jackson 3 adapter 均执行。
公共响应与指纹契约见 [RAD 协议规范](../../specs/zh-cn/ai/rad-protocol-spec.md)。

集群专项复用 `shouldConvergePinnedNodeDefinitionAndRuntimeChanges`：从节点 B 更新
description/tags，要求固定在节点 A 的 gRPC/HTTP Watch 收到相同完整快照，
并与 A/B 两节点 Discover 指纹一致；版本与 contentDigest 保持不变。
该场景在 Derby+Raft 和 MySQL 三节点夹具中分别执行。
