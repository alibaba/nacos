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
| `ConfigService` | `ConfigServiceJavaSdkITCase` | Partial | Verifies factory creation, publish/query/getConfigWithResult/CAS/remove lifecycle, missing-result shape, missing/idempotent removal, standalone `addListener`, listener removal behavior, client-side invalid parameter handling, unknown type compatibility, missing config behavior, and shutdown cleanup. | Config filter, fuzzy watch, invalid listener input, timeout behavior, and additional valid config types remain. |
| `NamingService` | `NamingServiceJavaSdkITCase` | Partial | Verifies factory creation, explicit/default group registration, string and `Instance` overloads, batch register and partial batch deregister, query/select/list/deregister lifecycle, cluster and metadata behavior, disabled/zero-weight filtering, subscribe callback delivery, subscribe state, null listener no-op, unsubscribe-stop behavior, validation for blank service, null instance, invalid cluster, invalid heartbeat metadata, persistent batch member, mismatched group prefix, missing service empty result, no-healthy selection failure, and shutdown cleanup. | Duplicate and missing deregister behavior, IP/port boundaries, explicit unhealthy selection, selector/fuzzy watch, empty batch-list behavior, and remaining pagination boundaries remain. |
| `AiService` / `A2aService` | `AiServiceJavaSdkITCase` | Partial | Verifies factory creation, MCP release/query/subscribe, MCP latest and duplicate-version controlled error behavior, MCP stdio endpoint registration controlled error, A2A agent card release/query/subscribe, A2A latest-version and endpoint registration call paths, current-value listener callbacks, missing-resource nullable MCP/A2A/Prompt subscribe shapes, gRPC Skill/AgentSpec unsupported error mapping, missing skill download controlled exception, SDK validation for MCP/A2A/Prompt/Skill/AgentSpec required parameters, endpoint validation, batch endpoint version mismatch, and shutdown cleanup. | Direct MCP endpoint-spec release, functional MCP endpoint registration for remote servers, all-version MCP endpoint behavior, A2A duplicate-version idempotency, A2A batch endpoint overwrite, A2A endpoint-detail assertion, functional Prompt/Skill/AgentSpec resource scenarios, and unsubscribe-stop callback behavior remain. |
| `LockService` | `LockServiceJavaSdkITCase` | Covered | Verifies factory creation, distributed lock acquire/compete/release/reacquire lifecycle, repeated release boundary, expiration-based reacquire, unsupported lock type and missing key error mapping, null lock-instance SDK boundary, direct `remoteTryLock`/`remoteReleaseLock`, and shutdown cleanup. | No known public `LockService` scenario gap in the standalone Java SDK IT. |

## Pending SDK Surfaces

The following SDK surfaces are documented by
`specs/*/testing/java-sdk-integration-test-spec.md` and should be added in
later batches:

- deprecated `NamingMaintainService`
- maintainer-client SDK interfaces
