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

# Java SDK IT Scenario Matrix

This document records Java SDK integration-test scenario coverage. The goal is
SDK API scenario coverage, not line coverage, branch coverage, or a small demo
per service interface.

Run these scenarios with the dedicated Maven profile
`java-sdk-integration-test` after a standalone Nacos server is ready. The
generic `integration-test` profile is for HTTP API IT and must not be used to
execute SDK IT cases implicitly.

Destructive process-restart and multi-node variants are orchestrated by
[`../DEFAULT_AUTH_RELIABILITY_IT.md`](../DEFAULT_AUTH_RELIABILITY_IT.md) rather
than stopping the server shared by the required standalone suite.

## Status Legend

| Status | Meaning |
| --- | --- |
| Covered | The current IT verifies the expected behavior and its important result shape. |
| Partial | The current IT verifies representative behavior, but important public SDK scenarios remain. |
| Pending | No IT currently verifies this public SDK scenario. |
| Documented gap | The scenario is not practical in the standalone Java SDK IT yet; the reason must be recorded. |

An SDK API is not complete while important method parameters, defaulting rules,
return variants, lifecycle paths, listener behavior, or exception mappings are
left as `Partial` or `Pending` without a documented reason.

## Authentication Baseline

These cross-cutting rows do not change the public SDK-surface counts.

| Scenario | Required behavior | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| Explicit identity selection | Tests can choose anonymous, read-write, read-only, or authenticated-no-permission credentials without logging passwords or tokens. | Covered | `JavaSdkBaseITCase` maps shared `nacos.test.auth.*` properties and password environment variables into public SDK factory properties. |
| Auth-enabled functional matrix | A normal application identity executes the complete Config, Naming, AI, and Lock functional suite while administrative fixture setup uses a separate administrator identity. | Partial | Final C11 default and Jackson 3 runs each discover 175 tests: 146 pass and 29 skip with no failures or errors. DAUTH-F05 cases are restored; DAUTH-F04 remains a baseline finding. Environment-gated transport, migration, and restart cases have separate directed reports in C11_PROGRESS.md. |
| Negative identity and action matrix | Anonymous, invalid, authenticated-no-permission, read-only, and read-write callers produce controlled results without cache fallback or unauthorized side effects. | Partial | Config, Naming, HTTP/gRPC/AUTO, no-permission, and read-only checks remain active. `shouldRejectInvalidCredentialsInsteadOfDowngradingToAnonymousAi` is retained but disabled as `DAUTH-F04`. |
| Async identity and SDK lifecycle | Listener/Watch delivery retains the admitted identity across worker threads, unsubscribe/shutdown stops later delivery, and SDK instances release global subscribers. | Partial | The seven Agent identity-context scenarios formerly disabled as DAUTH-F05 are restored and pass. Cancellation and shutdown assertions remain active. The separately recorded DAUTH-F06 general notifier finding is not claimed fixed by this work. |
| Capacity, reconnect, and cluster fault injection | Capacity limits and real transport recovery remain authenticated and are not silently omitted from CI. | Partial | Final C11 standalone Config, Naming, Lock, Agent/MCP, Maintainer, and Config Jackson 3 restarts pass with the original clients. Pinned-node change and rolling-restart cases pass; final peer-restart and migration cluster results are tracked separately in C11_PROGRESS.md. None of these Agent scenarios remains disabled as DAUTH-F05. |
| Lock authorization denial | The experimental Lock server applies the documented `SignType.LOCK` guard and rejects insufficient identities. | Documented gap | The complete Lock lifecycle runs with the authenticated read-write identity, including a stable 5-second expiry/reacquire window. The current server handler lacks the authorization guard, so the suite does not assert a false denial contract. |

## ConfigService

| Public SDK surface | Required scenarios | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| Factory, server status, shutdown | Create via `ConfigFactory`, wait for `UP`, and close cleanly after each test. | Covered | `JavaSdkBaseITCase` creates and shuts down the client. |
| `getConfig` | Existing config, missing config, invalid identity, default group behavior, and timeout path where practical. | Covered | Existing and missing config, blank dataId, invalid group, and default group are covered. Timeout behavior is intentionally excluded because it is not deterministic to force against the shared standalone server. |
| `getConfigWithResult` | Existing config returns content and md5; missing config returns the documented result shape. | Covered | Existing content/md5 and missing-result empty shape are covered. |
| `publishConfig` overloads | Default type, explicit valid type, unknown type compatibility, empty or invalid content, group defaulting, and durable server state. | Covered | Default publish, explicit `TEXT`, explicit `JSON`, unknown type compatibility, missing content, invalid group, blank group, and durable server state are covered. |
| `publishConfigCas` overloads | Bad md5 rejection, correct md5 update, missing config CAS create, empty CAS md5, explicit type, and unchanged state after failed CAS. | Covered | Bad md5 rejection, correct md5 update, missing config CAS create, empty CAS md5 as normal publish, explicit type, and unchanged state after failed CAS are covered. |
| `removeConfig` | Existing config removal, missing config/idempotent behavior, invalid identity, and absence after removal. | Covered | Existing removal, missing/idempotent removal, invalid identity, and absence after removal are covered. |
| `addListener`, `getConfigAndSignListener`, `removeListener` | Initial value, later update callback, standalone `addListener`, removal stops callbacks, invalid listener input. | Covered | `getConfigAndSignListener`, standalone `addListener`, update callback, remove-listener stop behavior, and null listener rejection for add/sign/remove paths are covered. |
| `addConfigFilter` | Filter registration effect or explicit standalone limitation. | Covered | Public SDK filter registration is covered by a transforming filter that mutates publish request content and query response content. |
| Fuzzy watch APIs | Fixed group pattern, dataId+group pattern, matched key return, event callback, cancel behavior, invalid pattern/listener. | Covered | DataId+group pattern matching, matched group-key return, add/delete event callbacks, and cancel-stop behavior are covered. Null-listener behavior is not asserted because the Config SDK path does not currently expose a stable controlled exception contract for it. |

## NamingService

| Public SDK surface | Required scenarios | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| Factory, server status, shutdown | Create via `NamingFactory`, wait for `UP`, and close cleanly after each test. | Covered | `JavaSdkBaseITCase` creates and shuts down the client. |
| `registerInstance` overloads | Default group, explicit group, cluster, `Instance` metadata, string overloads, duplicate registration, invalid IP/port/cluster/service, and persistent/ephemeral behavior where exposed. | Covered | Explicit group, default group, string overload, cluster string overload, metadata, duplicate registration, blank service, null instance, blank instance IP, invalid port/cluster, invalid heartbeat metadata, persistent batch member, single persistent instance lifecycle, and mismatched group prefix are covered. Hostname-style instance addresses remain accepted, so malformed-address format validation is not treated as a Java SDK IT requirement. |
| `batchRegisterInstance` / `batchDeregisterInstance` | Batch success, partial or invalid member validation, empty list, and cleanup after batch deregister. | Covered | Batch success, empty batch register no-op behavior, partial batch deregister, cleanup, invalid persistent batch member, empty batch deregister validation, and current null-list pre-remote failure behavior are covered. |
| `deregisterInstance` overloads | Existing instance removal, missing instance/idempotent behavior, default group, cluster overload, and invalid identity. | Covered | Existing removal through `Instance` overload, default string overload removal, cluster string overload removal, missing-instance no-op behavior, and repeated removal idempotency are covered. |
| `getAllInstances` overloads | Existing, missing service empty result, default group, explicit group, cluster filters, subscribe flag, and empty cluster list behavior. | Covered | Existing query, missing service, default group, explicit group, cluster filter, subscribe=false, subscribe=true cached refresh through server push, and empty cluster list behavior are covered. |
| `selectInstances` overloads | Healthy-only filtering, unhealthy/disabled boundaries, cluster filters, subscribe flag, and missing-service empty result. | Covered | Healthy selection, explicit unhealthy selection, disabled filtering, zero-weight filtering, cluster filters, subscribe flag variants, and missing-service empty result are covered. |
| `selectOneHealthyInstance` overloads | Success, cluster selection, default group, subscribe flag, and controlled failure when no healthy instance exists. | Covered | Success, cluster/default-group/subscribe overloads, and missing/no-healthy `IllegalStateException` behavior are covered. |
| `subscribe` / `unsubscribe` overloads | Initial and update event, cluster/selector filtering, removal stops callbacks, invalid listener, and `getSubscribeServices` state. | Covered | Basic grouped subscribe event, `getSubscribeServices`, cluster filtering, public selector filtering, null listener no-op, unsubscribe-stop behavior, and cleanup are covered. |
| Fuzzy watch APIs | Fixed group pattern, service+group pattern, matched service keys, event callback, cancel behavior, invalid pattern/listener. | Partial | Service+group pattern matching, matched service-key return, add event callback, and cancel-stop behavior are covered. Delete-service event callback remains because public instance deregistration does not reliably produce a `DELETE_SERVICE` fuzzy-watch event and the Java SDK has no public service delete API. Null watcher is already a no-op in the Naming SDK path and invalid pattern is covered by SDK-side pattern generation. |
| `getServicesOfServer` overloads | Pagination, default group, explicit group, selector overload, empty pages, and invalid page boundary. | Covered | Default and explicit group pages containing registered services, deprecated selector overloads, empty second page, and current `pageNo=0` first-page normalization are covered. |

## AiService And A2aService

| Public SDK surface | Required scenarios | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| Factory and shutdown | Create via `AiFactory` and close cleanly after each test. | Covered | `JavaSdkBaseITCase` creates and shuts down the client. |
| Five-resource transport compatibility | Exercise Agent, MCP, Prompt, Skill, and AgentSpec through explicit `grpc`, explicit `http`, and `auto`, including one-shot reads/writes, subscriptions, and resource-specific overrides and HTTP degradation. | Covered | `AiTransportResourceMatrixJavaSdkITCase` uses a real standalone server and isolated Maintainer SDK fixtures to verify every mode. Agent and MCP protocol-neutral operations follow the selected transport; Prompt direct reads/polling share its resource router; Skill ZIP and subscription plus AgentSpec load/subscription use HTTP for every requested mode. |
| MCP release/query | New MCP, new version, duplicate version controlled error, latest-published-version lookup, explicit-version lookup, direct-online compatibility, managed draft creation, tool/resource/endpoint variants, invalid specification, and missing MCP behavior. | Covered | `AiServiceJavaSdkITCase` and `McpHttpClientJavaSdkITCase` verify stable-state gRPC and HTTP default/explicit `createDraft=false` direct-online behavior, managed `createDraft=true` draft-only behavior, Tool/Resource/direct and auto-REF forms, duplicate errors, latest and exact query, invalid arguments, and absence before draft publication. Strict gRPC ability gating is also covered by focused client tests. McpHttpClientJavaSdkITCase also verifies default-public release and cross-user private/public access across HTTP and gRPC. |
| Historical MCP management migration | Preserve Java Client compatibility in `SYNCING`, reject lifecycle draft creation before cutover, reconcile historical content, and expose the same resource through Client and Maintainer SDKs after cutover. | Covered | `McpUpgradeMigrationJavaSdkITCase` is disabled during normal discovery and runs only from `.github/workflows/migration-it.yml`. Its `syncing` phase persists a historical direct-online resource and verifies the controlled draft gate; its `managed` phase verifies the reconciled online Version, unchanged serving query, successful managed draft, and cleanup. |
| MCP endpoint register/deregister/recovery | Register all-version or versioned endpoint for supported remote servers, verify returned detail/endpoint state, deregister own endpoint, invalid address/port/version, stdio unsupported behavior, and recover desired state after transport loss. | Covered | `AiServiceJavaSdkITCase` and `McpHttpClientJavaSdkITCase` cover gRPC and HTTP Versioned and default/latest Endpoint register/query/deregister, idempotent HTTP registration, missing MCP and stdio controlled errors, and invalid address/port. `AgentDiscoveryServiceJavaSdkITCase` uses one opt-in real restart to verify the shared Agent/MCP HTTP Client heartbeat marks and replays both modules' desired publications after `50404`; sticky owner and failure races remain focused client tests. |
| MCP subscribe/unsubscribe | Current-value callback, versioned/latest subscription, not-found nullable result, invalid listener, and unsubscribe stops callbacks. | Partial | Versioned and latest current-value callbacks, missing nullable subscribe result, unsubscribe cleanup, and invalid listener are covered. Unsubscribe-stop callback behavior remains because releasing a new version does not trigger the existing latest-version listener path deterministically. |
| A2A agent card release/query | New card, new version, duplicate version idempotency, `setAsLatest`, URL vs service registration type, default latest query, explicit version query, invalid card, missing card behavior, and canonical Agent interoperability. | Covered | New card, new versions, duplicate-version idempotency, default latest query, explicit version query, `setAsLatest`, URL and service registration type query, invalid card, missing card get behavior, and missing nullable subscribe behavior are covered. The cross-contract Agent discovery suite additionally verifies legacy A2A release through canonical Console and RAD reads, online duplicate no-overwrite, and canonical Maintainer publication through legacy A2A query. |
| A2A endpoint register/deregister | Single endpoint, batch endpoint overwrite, transport/path/TLS boundaries, own-client deregister behavior, invalid endpoint, canonical Runtime projection, pre-registration before definition, Beta no-dual-write behavior, and multi-Version reconnect redo. | Covered | Single endpoint register/deregister, batch overwrite, TLS/path/query detail, invalid/empty/mismatched input, and own-client removal are covered. Cross-contract IT proves exact-Version legacy SERVICE, Console Runtime Snapshot, and RAD visibility from one canonical Runtime publication, while a direct Naming SDK read verifies that the historical Version-specific service remains empty in Beta. Stable IT pre-registers Version 1 and Version 2 Endpoints before their definitions and compares omitted versus explicit-latest selection, while directed IT restarts the real server and verifies both exact-Version child-publisher redo records recover independently without overwriting generic parent-connection publications. |
| A2A subscribe/unsubscribe | Current-value callback, latest/versioned subscription, not-found nullable result, invalid listener, canonical publication convergence, and unsubscribe stops callbacks. | Covered | Latest and versioned current-value callbacks, missing nullable subscribe result, unsubscribe cleanup, unsubscribe-stop callback behavior, invalid listener, and a legacy latest subscription observing a new canonical Maintainer-published Version are covered. |
| Prompt APIs | Get by latest/version/label, subscribe/unsubscribe, missing prompt behavior, invalid key/label/listener, and label/version selection. | Partial | A Maintainer SDK fixture now verifies version query and current-value subscription through explicit gRPC, explicit HTTP, and AUTO clients. Missing nullable subscribe, invalid key/label/listener, and unsubscribe cleanup are also covered. Latest and custom-label selection remain. |
| Skill APIs | Download by latest/version/label, subscribe/unsubscribe, missing skill behavior, HTTP degradation for requested gRPC/AUTO, invalid name/listener, and ZIP byte contract. | Partial | A Maintainer SDK fixture verifies versioned ZIP download and HTTP subscription for all three requested modes, including cross-entry cancellation. Missing download, invalid name/listener, and unsubscribe cleanup are covered. Latest and custom-label downloads remain. |
| AgentSpec APIs | Load, subscribe/unsubscribe, missing AgentSpec behavior, HTTP degradation for requested gRPC/AUTO, invalid name/listener, and assembled resource contract. | Partial | A Maintainer SDK fixture verifies HTTP load/subscription for all three requested modes and cross-entry query/cancellation. Invalid name/listener and unsubscribe cleanup are covered. Multi-resource assembly remains. |

## AgentDiscoveryService

The detailed operation, boundary, failure, and compound matrix is maintained in
[`AGENT_DISCOVERY_SDK_IT_SCENARIOS.md`](AGENT_DISCOVERY_SDK_IT_SCENARIOS.md).

| Public SDK surface | Required scenarios | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| Factory, namespace, and lifecycle | Default/custom namespace binding, namespace-free inputs, caller isolation, inactive/active/repeated shutdown. | Covered | Default and custom service creation, namespace-free Search/Endpoint inputs with implicit instance binding across grpc/http/auto, caller-owned request and Batch isolation, active HTTP publication cleanup, and repeated shutdown are covered in standalone IT; deterministic resource cleanup is also covered by unit tests. |
| Agent transport mode | Explicit GRPC/HTTP and AUTO, synchronous initial gRPC startup, never-connected STARTING fallback, operation routing, and publication ownership. | Partial | Stable IT verifies AUTO on an available negotiated gRPC connection, AUTO Search/subscription/Publication over HTTP when a deliberately unreachable gRPC port remains STARTING, explicit HTTP independence from gRPC startup, and explicit GRPC failure without HTTP fallback. Probe thresholds, business-error classification, read-only fallback, sticky mixed Publication ownership, and reconnect suspension are deterministic UT scenarios. The affected `DAUTH-F05` methods were restored in C09; final directed lifecycle evidence is tracked in the C11 adaptation validation report. |
| Search | Default, literal name, tags-all, protocols-any, combined filters, pagination, empty result, validation, and transport parity. | Covered | Individual/default/combined/empty/paged searches, local null/page/duplicate/protocol boundaries, namespace isolation, and HTTP/gRPC parity are covered. |
| Discover | Latest/exact/label resolution, unfiltered and combined filters, declared/runtime source shape, not found, validation, and transport parity. | Covered | Latest/exact/label and combined-filter results, full unfiltered interface shape, declared/runtime source projection, not-found mapping, ambiguous/null reference validation, and HTTP/gRPC parity are covered. |
| Definition and Version evolution | Endpoint-first and definition-first ordering, latest/exact/label consistency, catalog ordering, offline/online latest recalculation, and publication ranges. | Partial | Standalone IT covers Versions 1 through 3, Endpoint-first and definition-first transitions, latest/exact/label polling behavior, catalog ordering, latest recalculation through offline/online, and replacement between two inclusive Version ranges. The affected `DAUTH-F05` methods were restored in C09; final directed lifecycle evidence is tracked in the C11 adaptation validation report. |
| Local polling subscription | Existing and missing initial target, full replacement callbacks, fingerprint de-duplication, unsubscribe, and listener isolation/failure. | Partial | Standalone IT covers subscribe-before-create, subscribe-existing, Runtime source-revision replacement, unchanged de-duplication, and post-unsubscribe suppression. Listener identity, failure, scheduling, shutdown races, digest/version revisions, and poll failures use deterministic unit tests. The affected `DAUTH-F05` methods were restored in C09; final directed lifecycle evidence is tracked in the C11 adaptation validation report. |
| Complete Endpoint publication | Pre-registration, register/replace/idempotence, partial/final/unknown/repeated deregistration, multiple protocols/publishers, HTTP heartbeat identity, gRPC redo, validation, and shutdown. | Partial | Stable standalone IT covers pre-registration, complete replacement convergence, canonical single-key and multi-key partial removal under grpc/http/auto, mixed unknown keys, preserved Endpoint fields and Version bindings, immutable inputs, final/whole-multi-key/repeated removal, protocol isolation, two-publisher aggregation, HTTP publication observed through gRPC, active HTTP shutdown, and public local boundaries. An opt-in directed IT stops and restarts the real server and verifies gRPC reconnect redo plus HTTP `50404` replay through the same SDK process. Generic heartbeat failures, retry classification, rollback, and redo races use deterministic unit tests. Combined partial-deregistration failure/recovery remains deferred and is not claimed by the multi-key transport increment. The affected `DAUTH-F05` methods were restored in C09; final directed lifecycle evidence is tracked in the C11 adaptation validation report. |

## Agent Code Publication

The complete implemented scenario matrix is maintained in
[`AGENT_PUBLISH_SDK_IT_SCENARIOS.md`](AGENT_PUBLISH_SDK_IT_SCENARIOS.md).

| Public SDK surface | Required scenarios | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `AiService.agent().publishAgent` | First-Version submit, complete draft replacement, non-draft no-op, direct/copy inputs, namespace/caller/governance isolation, Endpoint independence and three transports. | Pending | Updated AgentPublishJavaSdkITCase; see C05 stage and final matrix evidence. |

## LockService

| Public SDK surface | Required scenarios | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| Factory and shutdown | Create via `NacosLockFactory` and close cleanly after each test. | Covered | `JavaSdkBaseITCase` creates and shuts down the client. |
| `lock` / `unLock` | Acquire, competing client rejection, release, reacquire, repeated release, invalid type, null or invalid fields, and expiration behavior. | Covered | Acquire/compete/release/reacquire/repeated release, unsupported type, missing key, null instance, and expiration are covered. |
| `remoteTryLock` / `remoteReleaseLock` | Direct remote acquire/release path, repeated release, invalid input, consistency with public `lock`/`unLock`, and reconnect behavior. | Covered | Direct remote acquire/release, repeated acquire/release, and consistency with public lock behavior are covered. A directed real-restart case uses a lease longer than the restart window to verify connection-scoped state reset, recovery of both original clients, mutex exclusion, release, and reacquire. |

## Later SDK Surfaces

| Public SDK surface | Required scenarios | Current status | Notes |
| --- | --- | --- | --- |
| Deprecated `NamingMaintainService` | Create/query/update/delete service and update instance if the deprecated client can still be created in the standalone IT. | Pending | Listed separately because the API is deprecated after 3.3.0. |
| Maintainer client SDK interfaces | Maintainer API behavior, authorization assumptions, validation, and controlled errors. | Covered | Tracked separately in `test/maintainer-sdk-test`; its default and Jackson 3 auth-enabled suites each discover 46 tests, including one environment-gated real-restart case. This row is not counted as a Java Client SDK surface. |

## Recommended Next Test Batches

1. Confirm the intended contracts for Naming fuzzy-watch delete events and MCP
   latest-listener unsubscribe behavior, then add stable IT or file follow-up
   issues as needed.
2. Add the remaining Prompt/Skill latest and custom-label selection plus
   AgentSpec multi-resource assembly scenarios using the existing authenticated
   Maintainer fixture.
3. Decide whether the deprecated `NamingMaintainService` still warrants new IT
   before its removal window.

## AI Resource Interface Compatibility (3.3 phase 1)

The following table preserves the historical phase-1 snapshot, separate from the
original surface denominator. Its scope was interface delegation and resource
transport; the later C10/C11 entries below supersede its A2A-to-RAD and Watch gaps.
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

## Agent model consolidation

Agent model consolidation uses concrete namespace-free ClientRequest types; Search verifies inherited catalog metadata and shared version entries under grpc/http/auto. Draft publication retains idempotence, source validation and namespace binding. Abstract-base and fixed-JSON contracts are covered in API UTs.

### Agent 元数据模型合并（2026-09-14）

Agent 模型合并验证沿用 AgentDiscoveryServiceJavaSdkITCase：新目录路径、跨 transport 返回一致、管理字段隔离，既有发现/订阅场景不变。

### Agent 地址模型统一：实施与验收（2026-09-15）

CallInterface → EndpointSet → Endpoint 统一已落地，验收要求见 [测试矩阵](../../Codex/design/nacos-3.3-client-ai-api/MODEL_ENDPOINT_TEST_PLAN.md)，本轮实际执行见 [验证记录](../../Codex/design/nacos-3.3-client-ai-api/MODEL_ENDPOINT_VALIDATION.md)。healthy 注册可写，服务端维护字段忽略；管理 Runtime 读取改为 `callInterface.endpointSets[].endpoints[]`，状态和绑定位于 Endpoint，观察时间位于 Set。旧 A2A wire 不变。以下原有覆盖状态不以编译通过或历史测试数量自动提升。

### 2026-09-15 请求整合回归

Agent Search/Register 使用 agent 根包共享模型，局部注销使用三参数；publish 使用 agent.client.AgentPublishRequest。新增同名 Agent 双 namespace 搜索、注册及 3 删 2 隔离场景，GRPC/HTTP/AUTO 和两种 JSON adapter 共用。

本轮实际执行状态见 [请求整合验证记录](../../Codex/design/nacos-3.3-client-ai-api/MODEL_REQUEST_VALIDATION.md)。
既有 Covered/Partial/Pending 表示场景覆盖归属，不表示本轮已重新执行；不能引用前轮结果代替本轮验收。

Scope Watch regression: `AgentPublishJavaSdkITCase#shouldInvalidateWatchAfterScopeBecomesPrivate` is Partial and explicitly disabled under `DAUTH-F05` after reproducing missing initial Watch delivery with auth enabled. Direct HTTP/gRPC default-public discovery and private-preserving publish retry remain executable.

## A2A to RAD adaptation target (2026-09-16; design only)

This target supersedes the early phase-one-only routing assumptions for future implementation.
Current production code and historical coverage rows above are unchanged.
The [SDK scenario matrix](JAVA_SDK_IT_SCENARIOS.md)
enumerates API01–API28, every overload/entry, and scenario groups G/P/E/W/N/C/T/M.
Run RAD HTTP, gRPC, AUTO-to-gRPC, AUTO-to-HTTP separately, plus real non-RAD
servers under all three modes. Native local unsubscribe/shutdown remain cleanup exceptions.

| Public SDK surface | Required scenarios | Current status | Current/missing coverage |
| --- | --- | --- | --- |
| A2aService, all 18 overloads, facade and agent() | RAD query projection, exact latestVersion=null, Client release state table, cached multi-Version Endpoint intents, Watch-first subscription, deregistration and cleanup | Pending | Existing AiService/Agent tests are a baseline, not evidence for the new routing/semantics. O1/O2 are agreed: field defaults, writable enabled, state removal, single-binding continuous ranges retained across partial reference removal; implementation remains Pending. |
| AgentDiscoveryService, all 9 signatures | Search/Discover/filter/Watch, registration and partial deregistration in each actual wire environment; no-RAD controlled errors | Pending | Reuse AgentDiscoveryServiceJavaSdkITCase; retain DAUTH-F05 skips as gaps, not passes. |
| AgentService.publishAgent | Auto-submit only a newly created first Version; existing drafts follow autoSubmit and use full definition replacement; non-draft no-op; failures propagate without automatic retry/recreation | Pending | Replace the previous equivalent-retry and failure-recovery assertions; verify O3 matrix §6.1, governance isolation and unchanged Admin/Console behavior. |

Future-target signature coverage: Covered=0, Partial=0, Pending=28; strict 0/28=0%,
effective (0+0×0.5)/28=0%. This separate denominator is not added to implemented-surface totals.
Additional 28 AI regression signatures and all five accessors/factory/shutdown are listed in the matrix.

O1 acceptance is detailed in matrix §7.1 (E17/E18/E24): resolve each binding field
from Endpoint then Batch, default the final range only afterwards, reject invalid
open-boundary combinations atomically, preserve defensive copies, accept enabled,
and remove state from results. Verify management/discovery/Watch and Console projections,
including Naming operational overrides and independent publishers. These targets remain
Pending across all four RAD transport environments; historical ignored-field assertions
must be revised during implementation, not counted as current target coverage.

Capability consumption also follows matrix §10.1: parse radV1/mcp/skill/prompt/agentSpec
independently, distinguish HTTP declarations from gRPC reachability, retain Skill/AgentSpec
HTTP paths and existing resource behavior on servers without the new endpoint. No mandatory
preflight is added to the four existing resource services. These new scenarios remain Pending.

O3 is agreed in matrix §6.1, with ten detailed acceptance cases. Verify sole existing
drafts (including Admin-created drafts and failed first submissions) follow only the
current flag. Publication failures must not trigger automatic writes or post-failure
recovery into success; a later explicit application invocation uses the actual state.
Verify complete multi-protocol definition replacement, caller isolation and retained
governance properties across all four RAD transports. Fault timing and write-attempt
counts use focused UTs and a directed external harness; no target is marked executed.

O4 is agreed in matrix §5.1 (eight detailed targets). Definitions must carry both
sources in the preferred order, while Discover/Watch may explicitly select either
source. Verify default order, empty Sets, single-source query filters, A2A type
overrides and watch isolation across all four RAD transports. Reject single-source
definitions without rejecting single-source queries; retain source filters in the
public API. Console, storage and Artifact regressions remain part of the matrix.
These are Pending targets, not changes to historical execution evidence.

O6 uses the standard Client auth flow; auth-off capability success is not proof of
validated identity. O5 now accepts explicit migration-unready rejection for new RAD instances;
existing old-wire migration tests do not validate that target. Separate reads, publication and Endpoint writes
for unprojected historical names, migration-owned projections and independent standard
Agents. SDK-first upgrades must be supported; no upgrade-order prerequisite is accepted.
Keep the SDK process alive across old-to-new-to-old reconnections: actual abilities refresh,
but an instance that initially selects legacy A2A retains all old F/R methods, exact-Version
redo/deregistration and polling. Assert no RAD Batch/publisher/Watch conversion, including
migration completion on a live connection. Initial failed/unknown probes must not latch
legacy mode; concurrent calls share one decision. Re-instantiation may select RAD without
changing the original instance or automatically transferring its registrations/listeners.
Also start new instances first reaching RAD during migration: unsafe/unready related requests
must expose migration detail 50105 without legacy fallback, transparent publication retry or
partial business writes, across all four RAD transports and applicable F/R entry points.
Cover query/Watch, unprojected name conflicts and Runtime writes; complete server protection
beyond current definition guards. After cutover, an explicit subsequent call can succeed.
The agreed node-level gate rejects all RAD business requests, even unrelated standard Agents
and absent names, while historical authority remains. Fresh/terminal CANONICAL restores normal
resource outcomes; capabilities and existing-intent cleanup remain available.
Existing applications using old servers retain old A2A; do not require a native-RAD handover
mechanism. Legacy public compatibility remains in scope (matrix M04/M05/M09/M11).

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

## C09 恢复记录（2026-09-17）

DAUTH-F05 的显式身份校验已在本轮 A2A/RAD C09 修复；所有七项 Agent Discovery
方法及 Scope Watch 方法已移除该编号的 Disabled，可靠性脚本也已恢复对应入口。
此外，HTTP Watch 在权限或 scope 变化而共享 fingerprint 不变时返回受影响的 opaque ID，
由后续 Discover 执行资源授权。当前用例分别断言 HTTP 404 和 gRPC RESOURCE_NOT_FOUND(-404)。
普通测试和重启/集群 fixture 的执行证据分开登记；恢复入口不等于可靠性验证通过。
以上替代本文历史段落中“仍 Disabled”的当前状态描述，历史失败记录保持。
最终阶段结果见 [SDK scenario matrix](JAVA_SDK_IT_SCENARIOS.md)。

## C10 公共入口矩阵（阶段验证完成）

`A2aRadRoutingJavaSdkITCase` 通过公开工厂逐项调用 18 个 A2A 重载（扁平/agent()）
及 10 个原生签名。新服 grpc/http/auto 为普通参数化用例；外部 HTTP-only 网关用
`nacos.ai.adaptation.http-only-address`，真实 3.2.4 用
`nacos.ai.compatibility.old-server-address`。缺少 fixture 的 skip 不能计为通过。
断言包括首版默认发布、草稿再提交、精确 latestVersion 降级、单条/批量替换、
同址版本范围保留、两类订阅回调、取消、完整清理后换来源、原生 API 的旧服异常。
C10 阶段已完成；C11 最终矩阵的运行及制品证据见 ADAPTATION_VALIDATION.md。

### C11 migration and restart fixture update

| Public SDK surface | Required scenarios | Current status | Current/missing coverage |
| --- | --- | --- | --- |
| Released A2A SDK + current RAD SDK | LEGACY/SYNCING/QUIESCING, terminal authority, redo and cluster cutover | Partial | Migration tests call public A2A methods in a separate 3.2.4 SDK process with its independent dependencies. Current SDK A2A/native methods assert the RAD gate. Standalone shadow=true/false fixtures pass 19/6 executions; external-MySQL three-node Runtime/cutover cases pass. Extra embedded cutover remains a recorded Search persistence failure; details in C11_PROGRESS.md. |
| A2A and native publication restart | Both sources restore without owner collision | Covered | Different AiService instances publish to the same Agent; same-instance source conflict is tested separately by C10. The final real Agent/MCP restart passes with original instances; the old/new/old roundtrip also verifies same-instance version redo and selective deregistration. |


The first HTTP callback after a real server restart uses the existing 120-second
recovery budget. A failed Discover may retain up to 60 seconds of retry backoff
after HTTP Watch falls back to polling; the ordinary 25-second callback budget
is insufficient for that transition. Version, endpoint, callback and subsequent
change assertions remain unchanged; normal gRPC hint latency keeps its own budget.

### C11-F2 HTTP 发布响应丢失

`A2aRadRoutingJavaSdkITCase#lostPublishResponseIsNotReplayedAndExplicitRetryUsesStoredState`
通过 `nacos.ai.adaptation.lost-publish-address` 启用。使用外部 HTTP 代理在服务端成功
处理发布后丢弃响应，覆盖 AiService 旧入口、agent() 旧入口、原生 publishAgent，分别
执行默认/Jackson 3 profile。首调用必须抛异常；服务端已保存 ONLINE；调用方显式重试
保持 contentDigest 不变。每 profile 代理应观察到 6 次 POST 和 3 次响应丢弃，避免只
通过 mock 验证上层一次调用而漏掉 JDK 自动重放。未部署丢响应代理的普通套件会跳过，
不能用普通套件通过来代替本项定向证据。

C11 final cluster results: embedded definition/Runtime changes, rolling restart,
and pinned-peer restart pass; external-MySQL Runtime migration and terminal cutover
pass with all-node native admission, old/new reads, both Watch bindings, duplicate
suppression, replacement and deregistration. The additional embedded cutover
failure is retained as an unresolved Search persistence finding, not counted as
passing migration coverage. See C11_PROGRESS.md for all 66 report groups and skips.

### A2A metadata reserved-key regression

A2A conversion and RAD metadata use the same `__nacos.agent.endpoint.protocolVersion__` / `__nacos.agent.endpoint.tenant__` keys as legacy Naming Instances. AgentDiscoveryService and A2aRadRouting SDK ITs verify HTTP/gRPC/AUTO Discover/Watch and legacy projection under default/Jackson 3 profiles; migration comparator UTs verify identical old/new meaning and changed tenant/protocol detection. Invalid control metadata remains rejected.
