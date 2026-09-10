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
| Auth-enabled functional matrix | A normal application identity executes the complete Config, Naming, AI, and Lock functional suite while administrative fixture setup uses a separate administrator identity. | Partial | Default and Jackson 3 each discover 101 tests: 81 pass and 20 skip with no failures or errors. Eight skips are exact product findings (`DAUTH-F04` once and `DAUTH-F05` seven times); twelve are environment-gated migration/restart/cluster cases. |
| Negative identity and action matrix | Anonymous, invalid, authenticated-no-permission, read-only, and read-write callers produce controlled results without cache fallback or unauthorized side effects. | Partial | Config, Naming, HTTP/gRPC/AUTO, no-permission, and read-only checks remain active. `shouldRejectInvalidCredentialsInsteadOfDowngradingToAnonymousAi` is retained but disabled as `DAUTH-F04`. |
| Async identity and SDK lifecycle | Listener/Watch delivery retains the admitted identity across worker threads, unsubscribe/shutdown stops later delivery, and SDK instances release global subscribers. | Partial | Seven exact Agent identity-context scenarios are disabled as `DAUTH-F05`. The possible `NacosAiService.shutdown()` notifier leak is recorded as `DAUTH-F06`; it did not cause a stable failure in either full adapter run and was not fixed in this change. |
| Capacity, reconnect, and cluster fault injection | Capacity limits and real transport recovery remain authenticated and are not silently omitted from CI. | Partial | Config, Naming, Lock, Maintainer, Jackson 3, Agent rolling-restart, and Agent peer-restart reliability cases pass. Agent standalone restart and pinned-node convergence are explicitly disabled as `DAUTH-F05`; the runner writes a `status.txt` for each instead of reporting a false pass. |
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
| MCP release/query | New MCP, new version, duplicate version controlled error, latest-published-version lookup, explicit-version lookup, direct-online compatibility, managed draft creation, tool/resource/endpoint variants, invalid specification, and missing MCP behavior. | Covered | `AiServiceJavaSdkITCase` and `McpHttpClientJavaSdkITCase` verify stable-state gRPC and HTTP default/explicit `createDraft=false` direct-online behavior, managed `createDraft=true` draft-only behavior, Tool/Resource/direct and auto-REF forms, duplicate errors, latest and exact query, invalid arguments, and absence before draft publication. Strict gRPC ability gating is also covered by focused client tests. |
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
| Agent transport mode | Explicit GRPC/HTTP and AUTO, synchronous initial gRPC startup, never-connected STARTING fallback, operation routing, and publication ownership. | Partial | Stable IT verifies AUTO on an available negotiated gRPC connection, AUTO Search/subscription/Publication over HTTP when a deliberately unreachable gRPC port remains STARTING, explicit HTTP independence from gRPC startup, and explicit GRPC failure without HTTP fallback. Probe thresholds, business-error classification, read-only fallback, sticky mixed Publication ownership, and reconnect suspension are deterministic UT scenarios. The exact affected methods are retained with `DAUTH-F05` and must be restored after the visibility identity fix. |
| Search | Default, literal name, tags-all, protocols-any, combined filters, pagination, empty result, validation, and transport parity. | Covered | Individual/default/combined/empty/paged searches, local null/page/duplicate/protocol boundaries, namespace isolation, and HTTP/gRPC parity are covered. |
| Discover | Latest/exact/label resolution, unfiltered and combined filters, declared/runtime source shape, not found, validation, and transport parity. | Covered | Latest/exact/label and combined-filter results, full unfiltered interface shape, declared/runtime source projection, not-found mapping, ambiguous/null reference validation, and HTTP/gRPC parity are covered. |
| Definition and Version evolution | Endpoint-first and definition-first ordering, latest/exact/label consistency, catalog ordering, offline/online latest recalculation, and publication ranges. | Partial | Standalone IT covers Versions 1 through 3, Endpoint-first and definition-first transitions, latest/exact/label polling behavior, catalog ordering, latest recalculation through offline/online, and replacement between two inclusive Version ranges. The exact affected methods are retained with `DAUTH-F05` and must be restored after the visibility identity fix. |
| Local polling subscription | Existing and missing initial target, full replacement callbacks, fingerprint de-duplication, unsubscribe, and listener isolation/failure. | Partial | Standalone IT covers subscribe-before-create, subscribe-existing, Runtime source-revision replacement, unchanged de-duplication, and post-unsubscribe suppression. Listener identity, failure, scheduling, shutdown races, digest/version revisions, and poll failures use deterministic unit tests. The exact affected methods are retained with `DAUTH-F05` and must be restored after the visibility identity fix. |
| Complete Endpoint publication | Pre-registration, register/replace/idempotence, partial/final/unknown/repeated deregistration, multiple protocols/publishers, HTTP heartbeat identity, gRPC redo, validation, and shutdown. | Partial | Stable standalone IT covers pre-registration, complete replacement convergence, canonical single-key and multi-key partial removal under grpc/http/auto, mixed unknown keys, preserved Endpoint fields and Version bindings, immutable inputs, final/whole-multi-key/repeated removal, protocol isolation, two-publisher aggregation, HTTP publication observed through gRPC, active HTTP shutdown, and public local boundaries. An opt-in directed IT stops and restarts the real server and verifies gRPC reconnect redo plus HTTP `50404` replay through the same SDK process. Generic heartbeat failures, retry classification, rollback, and redo races use deterministic unit tests. Combined partial-deregistration failure/recovery remains deferred and is not claimed by the multi-key transport increment. The exact affected methods are retained with `DAUTH-F05` and must be restored after the visibility identity fix. |

## Agent Code Publication

The complete implemented scenario matrix is maintained in
[`AGENT_PUBLISH_SDK_IT_SCENARIOS.md`](AGENT_PUBLISH_SDK_IT_SCENARIOS.md).

| Public SDK surface | Required scenarios | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `AiService.agent().publishAgent` | Draft-only and auto-submit publication, resume, equivalent retry, conflicts, direct and inherited Version evolution, namespace/caller isolation, HTTP/gRPC parity, Endpoint independence, and cross-surface A2A visibility. | Covered | `AgentPublishJavaSdkITCase` verifies draft/resume/online convergence, exact retry and conflict behavior, invalid state mapping, direct and inherited Versions, default/custom namespaces, HTTP/gRPC parity, Endpoint independence and pre-registration, and canonical Admin/Console/RAD plus legacy A2A projections. Caller isolation, ability negotiation, and submit-result ambiguity are covered by focused unit tests. |

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
