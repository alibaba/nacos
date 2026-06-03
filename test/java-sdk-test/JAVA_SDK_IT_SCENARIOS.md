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
| `getConfig` | Existing config, missing config, invalid identity, default group behavior, and timeout path where practical. | Partial | Existing and missing config, blank dataId, invalid group, and default group are covered. Timeout remains. |
| `getConfigWithResult` | Existing config returns content and md5; missing config returns the documented result shape. | Covered | Existing content/md5 and missing-result empty shape are covered. |
| `publishConfig` overloads | Default type, explicit valid type, invalid type, empty or invalid content, group defaulting, and durable server state. | Partial | Default publish, explicit `TEXT`, invalid type, missing content, invalid group, and blank group are covered. Other valid types remain. |
| `publishConfigCas` overloads | Bad md5 rejection, correct md5 update, missing config CAS, empty CAS md5, explicit type, and unchanged state after failed CAS. | Covered | Bad md5, correct md5, missing config CAS, empty CAS md5 as normal publish, explicit type, and unchanged state after failed CAS are covered. |
| `removeConfig` | Existing config removal, missing config/idempotent behavior, invalid identity, and absence after removal. | Covered | Existing removal, missing/idempotent removal, invalid identity, and absence after removal are covered. |
| `addListener`, `getConfigAndSignListener`, `removeListener` | Initial value, later update callback, standalone `addListener`, removal stops callbacks, invalid listener input. | Partial | `getConfigAndSignListener`, standalone `addListener`, update callback, and remove-listener stop behavior are covered. Invalid listener input remains. |
| `addConfigFilter` | Filter registration effect or explicit standalone limitation. | Pending | No current scenario. Need decide whether a real filter can be observed through public SDK only. |
| Fuzzy watch APIs | Fixed group pattern, dataId+group pattern, matched key return, event callback, cancel behavior, invalid pattern/listener. | Pending | No current scenario. |

## NamingService

| Public SDK surface | Required scenarios | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| Factory, server status, shutdown | Create via `NamingFactory`, wait for `UP`, and close cleanly after each test. | Covered | `JavaSdkBaseITCase` creates and shuts down the client. |
| `registerInstance` overloads | Default group, explicit group, cluster, `Instance` metadata, string overloads, duplicate registration, invalid IP/port/cluster/service, and persistent/ephemeral behavior where exposed. | Partial | Explicit group, default group, string overload, cluster, metadata, blank service, null instance, invalid cluster, invalid heartbeat metadata, persistent batch member, and mismatched group prefix are covered. Duplicate registration and IP/port boundaries remain. |
| `batchRegisterInstance` / `batchDeregisterInstance` | Batch success, partial or invalid member validation, empty list, and cleanup after batch deregister. | Partial | Batch success, partial batch deregister, cleanup, and invalid persistent batch member are covered. Empty-list and null-list behavior remain. |
| `deregisterInstance` overloads | Existing instance removal, missing instance/idempotent behavior, default group, cluster overload, and invalid identity. | Partial | Existing removal through `Instance` overload, default string overload removal, and cluster removal are covered. Missing-instance/idempotent behavior remains. |
| `getAllInstances` overloads | Existing, missing service empty result, default group, explicit group, cluster filters, subscribe flag, and empty cluster list behavior. | Partial | Existing query, missing service, default group, explicit group, cluster filter, subscribe=false, and empty cluster list behavior are covered. subscribe=true/cache behavior remains. |
| `selectInstances` overloads | Healthy-only filtering, unhealthy/disabled boundaries, cluster filters, subscribe flag, and missing-service empty result. | Partial | Healthy selection, disabled filtering, zero-weight filtering, cluster filters, subscribe=false, and missing-service empty result are covered. Explicit unhealthy selection remains. |
| `selectOneHealthyInstance` overloads | Success, cluster selection, default group, subscribe flag, and controlled failure when no healthy instance exists. | Covered | Success, cluster/default-group/subscribe overloads, and missing/no-healthy `IllegalStateException` behavior are covered. |
| `subscribe` / `unsubscribe` overloads | Initial and update event, cluster/selector filtering, removal stops callbacks, invalid listener, and `getSubscribeServices` state. | Partial | Basic grouped subscribe event, `getSubscribeServices`, null listener no-op, unsubscribe-stop behavior, and cleanup are covered. Cluster/selector listener overloads remain. |
| Fuzzy watch APIs | Fixed group pattern, service+group pattern, matched service keys, event callback, cancel behavior, invalid pattern/listener. | Pending | No current scenario. |
| `getServicesOfServer` overloads | Pagination, default group, explicit group, selector overload, empty pages, and invalid page boundary. | Partial | Default and explicit group pages containing registered services are covered. Empty pages, invalid page boundary, and selector scenarios remain. |

## AiService And A2aService

| Public SDK surface | Required scenarios | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| Factory and shutdown | Create via `AiFactory` and close cleanly after each test. | Covered | `JavaSdkBaseITCase` creates and shuts down the client. |
| MCP release/query | New MCP, new version, duplicate version idempotency, latest-version lookup, explicit-version lookup, tool/resource/endpoint variants, invalid specification, and missing MCP behavior. | Partial | New MCP with tool/resource specs, new version, duplicate version idempotency, latest-version lookup, explicit-version query, invalid specification, and missing MCP get behavior are covered. Direct endpoint-spec release variant remains. |
| MCP endpoint register/deregister | Register all-version or versioned endpoint, verify returned detail/endpoint state, deregister own endpoint, invalid address/port/version, and missing MCP. | Partial | Versioned endpoint register/deregister, returned detail state, and invalid address/port are covered. All-version endpoint and missing MCP endpoint behavior remain. |
| MCP subscribe/unsubscribe | Current-value callback, versioned/latest subscription, not-found nullable result, invalid listener, and unsubscribe stops callbacks. | Partial | Versioned current-value callback, missing nullable subscribe result, unsubscribe cleanup, and invalid listener are covered. Unsubscribe-stop callback behavior remains. |
| A2A agent card release/query | New card, new version, duplicate version idempotency, `setAsLatest`, URL vs service registration type, default latest query, explicit version query, invalid card, and missing card behavior. | Partial | New card, new versions, default latest query, explicit version query, `setAsLatest`, URL and service registration type query, invalid card, and missing nullable subscribe behavior are covered. Duplicate-version idempotency remains. |
| A2A endpoint register/deregister | Single endpoint, batch endpoint overwrite, transport/path/TLS boundaries, own-client deregister behavior, invalid endpoint, and missing agent. | Partial | Single endpoint register/deregister, service registration type query, invalid endpoint, empty batch, and mismatched endpoint versions are covered. Batch overwrite and missing agent remain. |
| A2A subscribe/unsubscribe | Current-value callback, latest/versioned subscription, not-found nullable result, invalid listener, and unsubscribe stops callbacks. | Partial | Versioned current-value callback, missing nullable subscribe result, unsubscribe cleanup, and invalid listener are covered. Unsubscribe-stop callback behavior remains. |
| Prompt APIs | Get by latest/version/label, subscribe/unsubscribe, missing prompt behavior, invalid key/label/listener, and label/version selection. | Partial | Missing prompt nullable subscribe, invalid key/label/listener, and unsubscribe cleanup are covered. Functional prompt resource scenarios and label/version selection remain. |
| Skill APIs | Download by latest/version/label, subscribe/unsubscribe, missing skill behavior, invalid name/listener, and ZIP byte contract. | Partial | Missing skill nullable subscribe, missing download controlled exception, invalid name/listener, and unsubscribe cleanup are covered. Functional ZIP contract and version/label download remain. |
| AgentSpec APIs | Load, subscribe/unsubscribe, missing AgentSpec behavior, invalid name/listener, and assembled resource contract. | Partial | Missing load and subscribe nullable shapes, invalid name/listener, and unsubscribe cleanup are covered. Functional assembled resource contract remains. |

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

1. Config and Naming watcher expansion: config filter, invalid listener, selector
   subscription, duplicate/missing deregister behavior, invalid IP/port, and
   explicit unhealthy selection.
2. AI functional expansion: direct MCP endpoint-spec release, all-version MCP
   endpoints, A2A batch endpoint overwrite, duplicate A2A version behavior, and
   functional Prompt/Skill/AgentSpec resource creation or explicit standalone
   limitations.
3. Fuzzy watch expansion for Config and Naming once the standalone server path
   and event timing are proven stable.
