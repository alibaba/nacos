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

# Maintainer SDK IT Scenarios

Status legend: `Covered` means the important public contract is verified,
`Partial` means representative behavior is verified but important scenarios
remain, and `Pending` means no IT verifies that surface yet.

| Public maintainer SDK surface | Required scenarios | Current status | Current/missing coverage |
| --- | --- | --- | --- |
| `CoreMaintainerService` server state and health probes | Factory creation, standalone server liveness, readiness, server state shape, unavailable-server error mapping, and auth-disabled/admin-surface assumptions. | Partial | Covers factory creation through `NacosMaintainerFactory`, real HTTP liveness, and server-state result mapping against standalone server. Readiness is pending because `ConfigMaintainerService.readiness()` currently targets `/v3/admin/core/ops/readiness` while the server exposes `/v3/admin/core/state/readiness`; unavailable-server and auth-enabled mappings remain pending. |
| `CoreMaintainerService` namespace operations | Create, query, update, duplicate, delete, absent namespace, default/blank namespace boundaries, and cleanup idempotency. | Pending | No maintainer SDK IT yet. |
| `CoreMaintainerService` cluster/plugin/loader operations | Read-only cluster/plugin/loader queries, controlled operation boundaries, and dangerous mutation exclusions for shared standalone CI. | Pending | No maintainer SDK IT yet. |
| `ConfigMaintainerService` config lifecycle | Publish, query, list, metadata update, history query, delete, absent config, required parameter validation, and cleanup idempotency. | Pending | No maintainer SDK IT yet. |
| `BetaConfigMaintainerService` | Publish/query/delete beta config, required beta IP validation, and normal config compatibility. | Pending | No maintainer SDK IT yet. |
| `ConfigHistoryMaintainerService` | Config history list/detail/previous lookup across publish/update/delete lifecycle. | Pending | No maintainer SDK IT yet. |
| `ConfigOpsMaintainerService` | Config listener/client/search diagnostics with stable setup and empty-result behavior. | Pending | No maintainer SDK IT yet. |
| `NamingMaintainerService` and sub-services | Service/instance/cluster/client/health/ops admin workflows, defaulting, validation, idempotency, and cleanup. | Pending | No maintainer SDK IT yet. |
| `AiMaintainerService` and delegate services | MCP, A2A, Prompt, Skill, AgentSpec, and Pipeline admin workflows, version behavior, validation, upload/download boundaries, and cleanup. | Pending | No maintainer SDK IT yet. |

## Coverage Summary

Current in-scope maintained surfaces: 9.

- Strict coverage: 0 / 9 = 0.0%
- Effective coverage: (0 + 1 * 0.5) / 9 = 5.6%
