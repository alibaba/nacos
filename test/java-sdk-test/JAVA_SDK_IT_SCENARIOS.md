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
| MCP release/query | New MCP, new version, duplicate version controlled error, latest-version lookup, explicit-version lookup, tool/resource/endpoint variants, invalid specification, and missing MCP behavior. | Covered | New MCP with tool/resource specs, direct endpoint-spec release, new version, duplicate version controlled error, latest-version lookup, explicit-version query, invalid specification, and missing MCP get behavior are covered. |
| MCP endpoint register/deregister | Register all-version or versioned endpoint for supported remote servers, verify returned detail/endpoint state, deregister own endpoint, invalid address/port/version, and stdio unsupported behavior. | Covered | Versioned and default/latest endpoint register/query/deregister for a remote MCP server, missing MCP endpoint controlled error, stdio endpoint registration controlled error, and invalid address/port are covered. |
| MCP subscribe/unsubscribe | Current-value callback, versioned/latest subscription, not-found nullable result, invalid listener, and unsubscribe stops callbacks. | Partial | Versioned and latest current-value callbacks, missing nullable subscribe result, unsubscribe cleanup, and invalid listener are covered. Unsubscribe-stop callback behavior remains because releasing a new version does not trigger the existing latest-version listener path deterministically. |
| A2A agent card release/query | New card, new version, duplicate version idempotency, `setAsLatest`, URL vs service registration type, default latest query, explicit version query, invalid card, missing card behavior, and canonical Agent interoperability. | Covered | New card, new versions, duplicate-version idempotency, default latest query, explicit version query, `setAsLatest`, URL and service registration type query, invalid card, missing card get behavior, and missing nullable subscribe behavior are covered. The cross-contract Agent discovery suite additionally verifies legacy A2A release through canonical Console and RAD reads, online duplicate no-overwrite, and canonical Maintainer publication through legacy A2A query. |
| A2A endpoint register/deregister | Single endpoint, batch endpoint overwrite, transport/path/TLS boundaries, own-client deregister behavior, invalid endpoint, pre-registration before definition, and multi-Version reconnect redo. | Covered | Single endpoint register/deregister, batch overwrite, TLS/path/query detail, invalid/empty/mismatched input, and own-client removal are covered. Cross-contract IT proves exact-Version legacy SERVICE visibility and intentional isolation from the new Runtime Registry. Stable IT pre-registers Version 1 and Version 2 Endpoints before their definitions, while directed IT restarts the real server and verifies both exact-Version redo records recover independently. |
| A2A subscribe/unsubscribe | Current-value callback, latest/versioned subscription, not-found nullable result, invalid listener, canonical publication convergence, and unsubscribe stops callbacks. | Covered | Latest and versioned current-value callbacks, missing nullable subscribe result, unsubscribe cleanup, unsubscribe-stop callback behavior, invalid listener, and a legacy latest subscription observing a new canonical Maintainer-published Version are covered. |
| Prompt APIs | Get by latest/version/label, subscribe/unsubscribe, missing prompt behavior, invalid key/label/listener, and label/version selection. | Partial | Missing prompt nullable subscribe, invalid key/label/listener, and unsubscribe cleanup are covered. Functional prompt resource scenarios and label/version selection remain because the public Java SDK does not expose a create/publish Prompt API for standalone setup. |
| Skill APIs | Download by latest/version/label, subscribe/unsubscribe, missing skill behavior, gRPC transport unsupported query behavior, invalid name/listener, and ZIP byte contract. | Partial | gRPC subscribe/query unsupported error, missing download controlled exception, invalid name/listener, and unsubscribe cleanup are covered. Functional ZIP contract and version/label download remain because the public Java SDK does not expose a stable upload/create Skill API for standalone setup. |
| AgentSpec APIs | Load, subscribe/unsubscribe, missing AgentSpec behavior, gRPC transport unsupported query behavior, invalid name/listener, and assembled resource contract. | Partial | gRPC load/subscribe unsupported error, invalid name/listener, and unsubscribe cleanup are covered. Functional assembled resource contract remains because the public Java SDK does not expose a create/upload AgentSpec API and the client query path depends on AI resource metadata. |

## AgentDiscoveryService

The detailed operation, boundary, failure, and compound matrix is maintained in
[`AGENT_DISCOVERY_SDK_IT_SCENARIOS.md`](AGENT_DISCOVERY_SDK_IT_SCENARIOS.md).

| Public SDK surface | Required scenarios | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| Factory, namespace, and lifecycle | Default/custom namespace binding, caller isolation, invalid mismatch, inactive/active/repeated shutdown. | Covered | Default and custom service creation, omitted/explicit/mismatched namespace behavior, caller-owned request and Batch isolation, active HTTP publication cleanup, and repeated shutdown are covered in standalone IT; deterministic resource cleanup is also covered by unit tests. |
| Search | Default, literal name, tags-all, protocols-any, combined filters, pagination, empty result, validation, and transport parity. | Covered | Individual/default/combined/empty/paged searches, local null/page/duplicate/protocol boundaries, namespace isolation, and HTTP/gRPC parity are covered. |
| Discover | Latest/exact/label resolution, unfiltered and combined filters, declared/runtime source shape, not found, validation, and transport parity. | Covered | Latest/exact/label and combined-filter results, full unfiltered interface shape, declared/runtime source projection, not-found mapping, ambiguous/null reference validation, and HTTP/gRPC parity are covered. |
| Definition and Version evolution | Endpoint-first and definition-first ordering, latest/exact/label consistency, catalog ordering, offline/online latest recalculation, and publication ranges. | Covered | Standalone IT covers Versions 1 through 3, Endpoint-first and definition-first transitions, latest/exact/label polling behavior, catalog ordering, latest recalculation through offline/online, and replacement between two inclusive Version ranges. |
| Local polling subscription | Existing and missing initial target, full replacement callbacks, fingerprint de-duplication, unsubscribe, and listener isolation/failure. | Covered | Standalone IT covers subscribe-before-create, subscribe-existing, Runtime source-revision replacement, unchanged de-duplication, and post-unsubscribe suppression. Listener identity, failure, scheduling, shutdown races, digest/version revisions, and poll failures use deterministic unit tests. |
| Complete Endpoint publication | Pre-registration, register/replace/idempotence, partial/final/unknown/repeated deregistration, multiple protocols/publishers, HTTP heartbeat identity, gRPC redo, validation, and shutdown. | Covered | Stable standalone IT covers pre-registration, complete replacement convergence, canonical natural-key partial removal, final/unknown/repeated removal, protocol isolation, two-publisher aggregation, HTTP publication observed through gRPC, active HTTP shutdown, and public local boundaries. An opt-in directed IT stops and restarts the real server and verifies gRPC reconnect redo plus HTTP `50404` replay through the same SDK process. Other heartbeat failures, retry classification, rollback, and redo races use deterministic unit tests. |

## Agent Code Publication

The complete implemented scenario matrix is maintained in
[`AGENT_PUBLISH_SDK_IT_SCENARIOS.md`](AGENT_PUBLISH_SDK_IT_SCENARIOS.md).

| Public SDK surface | Required scenarios | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `AiService.publishAgent` | Draft-only and auto-submit publication, resume, equivalent retry, conflicts, direct and inherited Version evolution, namespace/caller isolation, HTTP/gRPC parity, Endpoint independence, and cross-surface A2A visibility. | Covered | `AgentPublishJavaSdkITCase` verifies draft/resume/online convergence, exact retry and conflict behavior, invalid state mapping, direct and inherited Versions, default/custom namespaces, HTTP/gRPC parity, Endpoint independence and pre-registration, and canonical Admin/Console/RAD plus legacy A2A projections. Caller isolation, ability negotiation, and submit-result ambiguity are covered by focused unit tests. |

## LockService

| Public SDK surface | Required scenarios | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| Factory and shutdown | Create via `NacosLockFactory` and close cleanly after each test. | Covered | `JavaSdkBaseITCase` creates and shuts down the client. |
| `lock` / `unLock` | Acquire, competing client rejection, release, reacquire, repeated release, invalid type, null or invalid fields, and expiration behavior. | Covered | Acquire/compete/release/reacquire/repeated release, unsupported type, missing key, null instance, and expiration are covered. |
| `remoteTryLock` / `remoteReleaseLock` | Direct remote acquire/release path, repeated release, invalid input, and consistency with public `lock`/`unLock`. | Covered | Direct remote acquire/release, repeated acquire/release, and consistency with public lock behavior are covered. |

## Later SDK Surfaces

| Public SDK surface | Required scenarios | Current status | Notes |
| --- | --- | --- | --- |
| Deprecated `NamingMaintainService` | Create/query/update/delete service and update instance if the deprecated client can still be created in the standalone IT. | Pending | Listed separately because the API is deprecated after 3.3.0. |
| Maintainer client SDK interfaces | Maintainer API behavior, authorization assumptions, validation, and controlled errors. | Pending | Needs a separate batch because it uses a different artifact and service model. |

## Recommended Next Test Batches

1. Confirm the intended contracts for Naming fuzzy-watch delete events and A2A
   missing-agent endpoint registration, then add stable IT or file follow-up
   issues as needed.
2. Add functional Prompt/Skill/AgentSpec Java SDK IT after the public SDK
   exposes stable create/upload setup APIs, or after the standalone framework
   provides an approved setup helper for AI resource metadata.
