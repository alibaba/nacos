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

Java SDK ITs run only with the dedicated Maven profile
`java-sdk-integration-test`. The generic `integration-test` profile belongs to
HTTP API IT CI and should build this module without executing SDK IT cases.

## Client SDK

| SDK interface | IT class | Status | Scenario coverage | Known gaps |
| --- | --- | --- | --- | --- |
| `ConfigService` | `ConfigServiceJavaSdkITCase` | Covered | Verifies factory creation, publish/query/getConfigWithResult/CAS/remove lifecycle, missing-result shape, missing/idempotent removal, standalone `addListener`, listener removal behavior, null listener rejection for add/sign/remove paths, client-side invalid parameter handling, valid `JSON` type metadata, unknown type compatibility, config filter request/response transformation, fuzzy-watch matched keys/add/delete/cancel behavior, missing config behavior, and shutdown cleanup. | `getConfig` timeout simulation is intentionally excluded from standalone Java SDK IT because it is not deterministic to force against the shared running server. |
| `NamingService` | `NamingServiceJavaSdkITCase` | Partial | Verifies factory creation, explicit/default group registration, string and `Instance` overloads, cluster string overloads, duplicate register idempotency, single persistent instance lifecycle, missing/repeated deregister idempotency, batch register, empty batch register no-op behavior, partial batch deregister, current null-list batch pre-remote failure behavior, query/select/list/deregister lifecycle, subscribe=true cached refresh, service-list pagination and deprecated selector overload boundaries, cluster and metadata behavior, explicit unhealthy selection, disabled/zero-weight filtering, subscribe callback delivery, subscribe state, cluster and public selector listener filtering, fuzzy-watch matched service keys/add/cancel behavior, null listener no-op, unsubscribe-stop behavior, validation for blank service, null instance, blank instance IP, invalid port/cluster, invalid heartbeat metadata, persistent batch member, empty batch deregister, mismatched group prefix, missing service empty result, no-healthy selection failure, and shutdown cleanup. | Fuzzy-watch delete-service events are not stable through public instance deregistration because the SDK has no public service delete API. |
| `AiService` / `A2aService` | `AiServiceJavaSdkITCase` | Partial | Verifies factory creation, MCP release/query/subscribe, MCP latest and duplicate-version controlled error behavior, direct MCP endpoint-spec release, versioned and default/latest MCP endpoint register/query/deregister for remote servers, missing MCP endpoint controlled error, MCP stdio endpoint registration controlled error, A2A agent card release/query/subscribe/unsubscribe-stop, A2A latest-version behavior, A2A duplicate-version idempotency, A2A missing-card get behavior, single/batch/TLS A2A endpoint registration with endpoint-detail assertion, current-value listener callbacks, missing-resource nullable MCP/A2A/Prompt subscribe shapes, gRPC Skill/AgentSpec unsupported error mapping, missing skill download controlled exception, SDK validation for MCP/A2A/Prompt/Skill/AgentSpec required parameters, endpoint validation, batch endpoint version mismatch, and shutdown cleanup. Cross-contract IT additionally verifies Endpoint pre-registration before definition, exact/latest subscription convergence, cached resubscribe polling, and independent multi-Version Endpoint redo through a real standalone restart. | MCP unsubscribe-stop behavior and functional Prompt/Skill/AgentSpec resource scenarios remain. No known first-version A2A AgentCard or Endpoint lifecycle gap remains. |
| `AiService.publishAgent` | `AgentPublishJavaSdkITCase` | Covered | Verifies draft-only and auto-submit publication, draft-to-submit resume, equivalent retry convergence, content/metadata conflicts, advanced/offline state errors, direct and inherited Versions, default/custom namespace isolation, HTTP/gRPC parity, Endpoint independence and pre-registration, canonical RAD/Admin/Console projection, and legacy A2A query/subscription interoperability. | No known first-version public publish scenario gap. Caller immutability, ability negotiation, and submit-result ambiguity are covered by focused unit tests. |
| `AgentDiscoveryService` / `AiService` | `AgentDiscoveryServiceJavaSdkITCase` | Covered | Verifies default/custom namespace isolation and binding, caller immutability, default/individual/combined/empty/paged Search, omitted/latest/exact/label and filtered Discover, complete Endpoint publication replacement, idempotence, canonical natural-key partial and final deregistration, protocol isolation, two-publisher aggregation, pre-registration, subscribe-before-create and subscribe-existing polling, complete-fingerprint change/de-duplication, unsubscribe suppression, Version evolution under Endpoint-first and definition-first ordering, catalog/latest/label/exact subscription consistency, offline/online latest recalculation, inclusive Version-range replacement, active HTTP shutdown cleanup, HTTP/gRPC parity, local validation boundaries, not-found/error mapping, and both default and Jackson 3 JSON adapters. The rollout-specific matrix uses independent Version 1 and Version 2 publishers to prove that an omitted selector returns latest metadata plus every online-Version-compatible Endpoint and binding, explicit `label=latest` is latest-only, the old pool survives the new-definition/Endpoint-registration gap, exact Version remains isolated, and offlining Version 1 changes only the default pool without duplicating an unchanged latest callback. Compatibility coverage releases through the legacy A2A SDK, verifies the canonical Console and RAD projections, registers exact-Version legacy Endpoints for Versions 1 and 2 into the canonical Runtime Service, verifies Console Runtime Snapshot plus old SERVICE projection, directly verifies that Beta does not dual-write the historical Naming service, proves pre-registration does not create a definition, compares omitted multi-Version aggregation with explicit-latest isolation, rejects duplicate definition overwrite, then publishes Version 2 through the canonical Maintainer SDK and verifies legacy query plus latest polling convergence. An opt-in directed IT stops and restarts a real standalone server while the same SDK process verifies connection failure, reconnect, protocol-neutral gRPC publication redo, HTTP `50404` publication replay, polling recovery, independent legacy Version 1 and pre-registered Version 2 child-publisher redo on the same canonical Runtime Service, coexistence with generic parent-connection publications, and a later Version/Endpoint transition. | Server Watch/Push, management-metadata subscription, public local-selection helpers, legacy Naming serviceName dual-write, and all-HTTP dual identity headers are explicitly deferred by the scoped Beta contract. Ability changes, transport-result ambiguity, non-50404 heartbeat failures, listener failure, rollback, and redo races remain deterministic client unit-test scenarios rather than unstable shared-server fault injection. |
| `LockService` | `LockServiceJavaSdkITCase` | Covered | Verifies factory creation, distributed lock acquire/compete/release/reacquire lifecycle, repeated release boundary, expiration-based reacquire, unsupported lock type and missing key error mapping, null lock-instance SDK boundary, direct `remoteTryLock`/`remoteReleaseLock`, and shutdown cleanup. | No known public `LockService` scenario gap in the standalone Java SDK IT. |

## Pending SDK Surfaces

The following SDK surfaces are documented by
`specs/*/testing/java-sdk-integration-test-spec.md` and should be added in
later batches:

- deprecated `NamingMaintainService`
- maintainer-client SDK interfaces are tracked separately in
  `test/maintainer-sdk-test`
