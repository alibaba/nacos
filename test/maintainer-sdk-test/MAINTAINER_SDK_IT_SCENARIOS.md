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
| `CoreMaintainerService` server state and health probes | Factory creation, standalone server liveness, readiness, server state shape, unavailable-server error mapping, and auth-disabled/admin-surface assumptions. | Covered | Covers factory creation through `NacosMaintainerFactory`, real HTTP liveness/readiness, and server-state result mapping against standalone server. Unavailable-server and auth-enabled mappings are intentionally left for the later auth/error-mapping batch. |
| `CoreMaintainerService` namespace operations | Create, query, update, duplicate, delete, absent namespace, default/blank namespace boundaries, and cleanup idempotency. | Covered | Covers default namespace lookup, explicit namespace create/get/list/update/check/delete lifecycle, duplicate namespace controlled exception, invalid namespace ID/name controlled exceptions, absent-after-delete check behavior, and cleanup idempotency. |
| `CoreMaintainerService` cluster/plugin/loader operations | Read-only cluster/plugin/loader queries, controlled operation boundaries, and dangerous mutation exclusions for shared standalone CI. | Partial | Covers ID generator list, cluster node list, current client map, cluster loader metrics, and plugin list. Mutating operations such as lookup-mode changes, log-level updates, connection reloads, and plugin status/config updates remain pending or intentionally excluded until their standalone CI safety is reviewed. |
| `ConfigMaintainerService` config lifecycle | Publish, query, list, metadata update, history query, delete, absent config, required parameter validation, and cleanup idempotency. | Partial | Covers publish/get/list/search/update-metadata/update/delete lifecycle, namespace config list, delete by storage ID, missing config controlled exception, invalid publish parameters, history list/detail/previous queries across updates, and cleanup. Clone/import/export remain pending because they need separate file/body setup and conflict-policy coverage. |
| `BetaConfigMaintainerService` | Publish/query/delete beta config, required beta IP validation, and normal config compatibility. | Covered | Covers required beta IP validation, beta publish/query/stop lifecycle, beta content assertion, and missing-after-stop controlled exception. |
| `ConfigHistoryMaintainerService` | Config history list/detail/previous lookup across publish/update/delete lifecycle. | Partial | Covers history list/detail/previous lookup after publish and update. Delete-history behavior remains pending to avoid coupling this batch to delete trace timing. |
| `ConfigOpsMaintainerService` | Config listener/client/search diagnostics with stable setup and empty-result behavior. | Covered | Covers config listener diagnostics by dataId/group/namespace, IP listener diagnostics, local-cache dump command, and config log-level command. |
| `NamingMaintainerService` and sub-services | Service/instance/cluster/client/health/ops admin workflows, defaulting, validation, idempotency, and cleanup. | Partial | Covers persistent service create/get/update/list/remove lifecycle, missing service controlled exception, invalid service parameter validation, persistent instance register/list/detail/update/partial-update/batch-metadata-update/batch-metadata-delete/deregister lifecycle, invalid instance parameter validation, subscriber diagnostics, naming metrics/log operations, and cleanup. Cluster health, health-checker queries, selector types, naming client diagnostics, and auth-enabled behavior remain pending. |
| `AiMaintainerService` and delegate services | MCP, A2A, Prompt, Skill, AgentSpec, and Pipeline admin workflows, version behavior, validation, upload/download boundaries, and cleanup. | Pending | No maintainer SDK IT yet. |

## Coverage Summary

Current in-scope maintained surfaces: 9.

- Strict coverage: 4 / 9 = 44.4%
- Effective coverage: (4 + 3 * 0.5) / 9 = 61.1%
