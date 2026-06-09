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
| `CoreMaintainerService` server state and health probes | Factory creation, standalone server liveness, readiness, server state shape, unavailable-server error mapping, and auth-disabled/admin-surface assumptions. | Covered | Covers factory creation through `NacosMaintainerFactory`, real HTTP liveness/readiness, server-state result mapping against standalone server, and unavailable-server controlled exception mapping. Auth-enabled mappings remain intentionally deferred because standalone maintainer SDK IT does not enable auth. |
| `CoreMaintainerService` namespace operations | Create, query, update, duplicate, delete, absent namespace, default/blank namespace boundaries, and cleanup idempotency. | Covered | Covers default namespace lookup, explicit namespace create/get/list/update/check/delete lifecycle, duplicate namespace controlled exception, invalid namespace ID/name controlled exceptions, absent-after-delete check behavior, and cleanup idempotency. |
| `CoreMaintainerService` cluster/plugin/loader operations | Read-only cluster/plugin/loader queries, controlled operation boundaries, and dangerous mutation exclusions for shared standalone CI. | Partial | Covers ID generator list, cluster node list, current client map, cluster loader metrics, plugin list, plugin type filtering, and plugin detail lookup. Mutating operations such as lookup-mode changes, log-level updates, connection reloads, and plugin status/config updates are intentionally excluded from shared standalone IT because they can alter runtime state. `getPluginAvailability` is a server-route follow-up because the maintainer SDK points to `/v3/admin/core/plugin/availability`, while current standalone Nacos only exposes the availability query through the console plugin path. |
| `ConfigMaintainerService` config lifecycle | Publish, query, list, metadata update, clone, history query, delete, absent config, required parameter validation, conflict policy behavior, and cleanup idempotency. | Covered | Covers publish/get/list/search/update-metadata/update/delete lifecycle, namespace config list against the current namespace model, delete by storage ID, clone by storage ID, clone SKIP/OVERWRITE conflict policies, clone empty-selection failure data, missing config controlled exception, invalid publish parameters, history list/detail/previous queries across updates, and cleanup. Import/export are HTTP admin operations not currently exposed by the maintainer SDK, and removed empty-tenant storage migration is outside the 3.3 SDK contract. |
| `BetaConfigMaintainerService` | Publish/query/delete current beta gray config and required beta IP validation. | Covered | Covers required beta IP validation, beta publish/query/stop lifecycle backed by the current gray model, beta content assertion, and missing-after-stop controlled exception. Removed `config_info_beta` old-table migration is not an expected SDK scenario. |
| `ConfigHistoryMaintainerService` | Config history list/detail/previous lookup across publish/update/delete lifecycle. | Covered | Covers history list/detail/previous lookup after publish and update. Delete-history is not currently exposed by the maintainer SDK. |
| `ConfigOpsMaintainerService` | Config listener/client/search diagnostics with stable setup and empty-result behavior. | Covered | Covers config listener diagnostics by dataId/group/namespace, IP listener diagnostics, local-cache dump command, and config log-level command. |
| `NamingMaintainerService` and sub-services | Service/instance/cluster/client/health/ops admin workflows, defaulting, validation, idempotency, and cleanup. | Covered | Covers persistent service create/get/update/list/detail-list/remove lifecycle, missing service controlled exception, invalid service parameter validation, persistent instance register/list/detail/update/partial-update/batch-metadata-update/batch-metadata-delete/deregister lifecycle, invalid instance parameter validation, selector type and health-checker queries, cluster health-checker metadata update, manual persistent instance health status update, naming client list/detail/publisher/subscriber diagnostics, subscriber diagnostics, naming metrics/log operations, and cleanup. Auth-enabled behavior is intentionally deferred because standalone maintainer SDK IT does not enable auth. |
| `AiMaintainerService` and delegate services | MCP, A2A, Prompt, Skill, AgentSpec, and Pipeline admin workflows, version behavior, validation, upload boundaries, controlled pipeline query failures, and cleanup. | Covered | Covers AI maintainer factory delegate creation, empty-result list queries, pipeline Result-wrapper list query and missing-detail controlled exception, MCP local server create/get/list/search/update/delete lifecycle and invalid local/remote spec validation, A2A agent register/get/list/search/update/delete lifecycle, Prompt draft/update/force-publish/label/description/biz-tag/online-status/list/delete lifecycle, Skill draft/update/force-publish/label/biz-tag/scope/online-status/list/delete lifecycle, Skill ZIP upload with target version and commit message, Skill batch ZIP upload, AgentSpec draft/update/force-publish/label/biz-tag/scope/online-status/list/delete lifecycle, AgentSpec ZIP upload and version-meta query, Skill/AgentSpec submit direct-publish behavior when no review pipeline plugin is enabled, null factory property validation, and cleanup. Real review-pipeline plugin approval is intentionally excluded from standalone maintainer SDK IT, and Skill/AgentSpec/Prompt download endpoints plus AI import/adaptor paths are not currently exposed by the maintainer SDK. Auth-enabled behavior remains deferred because standalone maintainer SDK IT does not enable auth. |

## Coverage Summary

Current in-scope maintained surfaces: 9.

- Strict coverage: 8 / 9 = 88.9%
- Effective coverage: (8 + 1 * 0.5) / 9 = 94.4%
