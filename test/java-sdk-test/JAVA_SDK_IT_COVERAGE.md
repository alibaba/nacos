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
| `AiService` / `A2aService` | `AiServiceJavaSdkITCase`, `McpHttpClientJavaSdkITCase` | Partial | Verifies factory creation, MCP gRPC/HTTP release/query/subscribe, omitted and explicit `createDraft=false` direct-online compatibility, managed `createDraft=true` draft creation or the controlled pre-cutover conflict, MCP latest-published and duplicate-version controlled errors, Tool/Resource content, direct MCP endpoint-spec release, versioned and default/latest HTTP MCP Endpoint register/query/idempotent-register/deregister for remote REF servers, missing MCP Endpoint controlled error, MCP stdio Endpoint controlled error, invalid HTTP arguments, A2A Agent Card release/query/subscribe/unsubscribe-stop, A2A latest-version behavior, A2A duplicate-version idempotency, A2A missing-card get behavior, single/batch/TLS A2A Endpoint registration with endpoint-detail assertion, current-value listener callbacks, missing-resource nullable MCP/A2A/Prompt subscribe shapes, gRPC Skill/AgentSpec unsupported error mapping, missing Skill download controlled exception, SDK validation for MCP/A2A/Prompt/Skill/AgentSpec required parameters, Endpoint validation, batch Endpoint version mismatch, and shutdown cleanup. Cross-contract IT additionally verifies Endpoint pre-registration before definition, exact/latest subscription convergence, cached resubscribe polling, and Agent plus MCP publication redo through a real standalone restart. | MCP unsubscribe-stop behavior and the remaining Prompt/Skill label-selection variants remain. No known first-version A2A AgentCard or Endpoint lifecycle gap remains. |
| Five-resource `AiService` transport matrix | `AiTransportResourceMatrixJavaSdkITCase` | Covered | Against one real standalone server, verifies Agent definition publication, Search, Discover, subscription, Runtime Endpoint publication and cleanup; MCP release/query/subscription; Prompt version query/subscription; Skill version ZIP download; and AgentSpec load/subscription under explicit `grpc`, explicit `http`, and `auto`. Agent and MCP protocol-neutral operations follow the selected transport. It also asserts the current controlled contract: Skill and AgentSpec polling/query paths that have no gRPC implementation return `SERVER_NOT_IMPLEMENTED`, while direct Skill download remains HTTP in all modes. | The matrix verifies current routing compatibility rather than adding gRPC implementations for Skill or AgentSpec. AUTO with deliberately unreachable gRPC is covered separately by `AgentDiscoveryServiceJavaSdkITCase`; shared Agent/MCP HTTP Client heartbeat and restart recovery are covered there as an opt-in directed test. |
| `AiService.publishAgent` | `AgentPublishJavaSdkITCase` | Covered | Verifies draft-only and auto-submit publication, draft-to-submit resume, equivalent retry convergence, content/metadata conflicts, advanced/offline state errors, direct and inherited Versions, default/custom namespace isolation, HTTP/gRPC parity, Endpoint independence and pre-registration, canonical RAD/Admin/Console projection, and legacy A2A query/subscription interoperability. | No known first-version public publish scenario gap. Caller immutability, ability negotiation, and submit-result ambiguity are covered by focused unit tests. |
| `AgentDiscoveryService` / `AiService` | `AgentDiscoveryServiceJavaSdkITCase` | Covered | Verifies default/custom namespace isolation and binding; immutable caller models; literal, typed, paged, and convergent Search; omitted/latest/exact/label and fully filtered Discover; complete Endpoint replacement, idempotence, partial/final deregistration, protocol isolation, two-publisher aggregation, pre-registration, Version ranges, and rollout-safe multi-Version pools. Subscription coverage includes subscribe-before-create `UNAVAILABLE` to complete `SNAPSHOT`, negotiated gRPC fingerprint Hint followed by authoritative Discover, generation-based HTTP Batch Long Poll, canonical intent sharing, multiple and throwing listeners, silent long-poll timeout, partial/final unsubscribe, resubscribe, shutdown suppression, complete-fingerprint de-duplication, local subscription capacity, authoritative server gRPC/HTTP Watch soft-watermark rejection/cleanup/reuse, latest-addition HTTP rejection with prior-batch retention, and publication capacity/redo cleanup. Transport coverage includes HTTP/gRPC parity, stable cross-transport canonical fingerprints, AUTO with available gRPC, AUTO immediate HTTP Watch when gRPC remains STARTING, explicit HTTP independence, explicit GRPC no operation fallback, bounded polling compatibility fallback, validation/error mapping, default and Jackson 3 adapters, and reusable `AUTO`/`INDEX`/`SCAN` Search projection assertions. Legacy A2A coverage verifies canonical Console/RAD projection, exact-Version child publishers, SERVICE compatibility without Beta historical Naming dual-write, pre-registration, duplicate protection, multi-Version aggregation, and later canonical publication. One opt-in directed IT stops and restarts a real standalone server while the same SDK process verifies connection failure, protocol-neutral and legacy gRPC publication redo, shared Agent/MCP HTTP `50404` replay, restored MCP REF Runtime Endpoint visibility, gRPC Watch ability renegotiation and resubscription with a new connection-scoped key, HTTP Batch Long Poll recovery, Search convergence, and later Version/Endpoint callbacks. A second opt-in directed IT stops and restarts one node of a three-node cluster and verifies that independent gRPC and HTTP Watches converge both while the node is unavailable and after it rejoins, while omitted and exact selectors preserve their documented multi-Version semantics. A third opt-in directed IT pins gRPC and HTTP subscribers to node A, performs definition and Runtime mutations through A and B independently, and cross-validates every A-A/A-B callback against complete Discover fingerprints on both nodes. A fourth opt-in directed IT stops and restarts node B in that two-node topology, accepts temporary CP unavailability while the cluster lacks a majority, and verifies that the original A-side gRPC and HTTP Watches consume later B-owned definition and Runtime changes after quorum returns without resubscription. | Management-metadata subscription, public local-selection helpers, legacy Naming serviceName dual-write, and all-HTTP dual identity headers remain deferred. Ability changes after a successful connection, individual frame loss/ACK ambiguity, non-50404 heartbeat failures, rollback, and redo races remain deterministic unit-test scenarios rather than unstable shared-server fault injection. |
| `LockService` | `LockServiceJavaSdkITCase` | Covered | Verifies factory creation, distributed lock acquire/compete/release/reacquire lifecycle, repeated release boundary, expiration-based reacquire, unsupported lock type and missing key error mapping, null lock-instance SDK boundary, direct `remoteTryLock`/`remoteReleaseLock`, and shutdown cleanup. | No known public `LockService` scenario gap in the standalone Java SDK IT. |

The Agent Discovery row also covers immediate current-snapshot Search availability through both
gRPC and HTTP without a readiness error, followed by polling to the complete converged catalog.

## Pending SDK Surfaces

The following SDK surfaces are documented by
`specs/*/testing/java-sdk-integration-test-spec.md` and should be added in
later batches:

- deprecated `NamingMaintainService`
- maintainer-client SDK interfaces are tracked separately in
  `test/maintainer-sdk-test`
