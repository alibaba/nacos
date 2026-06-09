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

# Maintainer SDK IT Coverage Registry

This registry records which public maintainer SDK interfaces are covered by
`test/maintainer-sdk-test` integration tests and the scenario groups each class
verifies.

The detailed scenario matrix lives in
[`MAINTAINER_SDK_IT_SCENARIOS.md`](MAINTAINER_SDK_IT_SCENARIOS.md). A `Partial`
status means the current IT has representative coverage but must not be treated
as complete maintainer SDK scenario coverage.

Maintainer SDK ITs run only with the dedicated Maven profile
`maintainer-sdk-integration-test`. The generic `integration-test` profile
belongs to HTTP API IT CI and should build this module without executing
maintainer SDK IT cases.

| Maintainer SDK interface | IT class | Status | Scenario coverage | Known gaps |
| --- | --- | --- | --- | --- |
| `CoreMaintainerService` | `CoreMaintainerServiceMaintainerSdkITCase` | Partial | Verifies factory creation through `NacosMaintainerFactory`, standalone server liveness/readiness, server-state result mapping, unavailable-server controlled exception mapping, ID generator list, cluster node list, current client map, cluster loader metrics, plugin list, plugin type filtering, plugin detail lookup, namespace create/get/list/update/check/delete lifecycle, duplicate namespace controlled error, invalid namespace parameter errors, default namespace lookup, default `nacos.host`/`nacos.port` profile wiring, and shutdown cleanup. | Auth-enabled behavior is intentionally deferred because standalone maintainer SDK IT currently runs with auth disabled. Mutating cluster/plugin/loader controls are intentionally excluded from shared standalone IT because they can alter runtime state. `getPluginAvailability` remains a server-route follow-up because the maintainer SDK points to `/v3/admin/core/plugin/availability`, while current standalone Nacos only exposes the availability query through the console plugin path. |
| `ConfigMaintainerService` / `ConfigHistoryMaintainerService` / `BetaConfigMaintainerService` / `ConfigOpsMaintainerService` | `ConfigMaintainerServiceMaintainerSdkITCase` | Covered | Verifies publish/get/list/search/update-metadata/update/delete lifecycle, namespace config list in the current namespace model, delete by storage ID, clone by storage ID, clone SKIP/OVERWRITE conflict policies, clone empty-selection failure data, missing config controlled exception, invalid publish parameters, config history list/detail/previous queries across updates, current gray-backed beta publish/query/stop and missing beta IP validation, config listener diagnostics by config and IP, local-cache dump command, config log-level command, default host/port wiring, and cleanup. | Import/export and delete-history are HTTP admin operations not currently exposed by the maintainer SDK. Removed pre-3.0 empty-tenant and `config_info_beta` / `config_info_tag` migration paths are outside the 3.3 maintainer SDK contract. Auth-enabled behavior remains intentionally deferred because standalone maintainer SDK IT currently runs with auth disabled. |
| `NamingMaintainerService` / `ServiceMaintainerService` / `InstanceMaintainerService` | `NamingMaintainerServiceMaintainerSdkITCase` | Covered | Verifies persistent service create/get/update/list/detail-list/remove lifecycle, missing service controlled exception, invalid service parameter validation, persistent instance register/list/detail/update/partial-update/batch-metadata-update/batch-metadata-delete/deregister lifecycle, invalid instance parameter validation, selector type and health-checker queries, cluster health-checker metadata update, manual persistent instance health status update, naming client list/detail/publisher/subscriber diagnostics, subscriber diagnostics, naming metrics/log operations, default host/port wiring, and cleanup. | Auth-enabled behavior is intentionally deferred because maintainer SDK IT currently runs against auth-disabled standalone Nacos. |
| `AiMaintainerService` / `McpMaintainerService` / `A2aMaintainerService` / `PromptMaintainerService` / `SkillMaintainerService` / `AgentSpecMaintainerService` / `PipelineMaintainerService` | `AiMaintainerServiceMaintainerSdkITCase` | Covered | Verifies AI maintainer factory delegate creation, empty-result list queries, pipeline Result-wrapper list query and missing-detail controlled exception, MCP local server create/get/list/search/update/delete lifecycle and invalid local/remote spec validation, A2A agent register/get/list/search/update/delete lifecycle, Prompt draft/update/force-publish/label/description/biz-tag/online-status/list/delete lifecycle, Skill draft/update/force-publish/label/biz-tag/scope/online-status/list/delete lifecycle, Skill ZIP upload with target version and commit message, Skill batch ZIP upload, AgentSpec draft/update/force-publish/label/biz-tag/scope/online-status/list/delete lifecycle, AgentSpec ZIP upload and version-meta query, Skill/AgentSpec submit direct-publish behavior when no review pipeline plugin is enabled, null factory property validation, default host/port wiring, and cleanup. | Real review-pipeline plugin approval remains intentionally excluded because standalone maintainer SDK IT does not enable review plugins. Skill/AgentSpec/Prompt download endpoints and AI import/adaptor paths are HTTP/admin or Java SDK surfaces that are not currently exposed by the maintainer SDK. Auth-enabled behavior remains intentionally deferred because standalone maintainer SDK IT currently runs with auth disabled. |

## Remaining Maintainer SDK Follow-up Surfaces

- `CoreMaintainerService` cluster/plugin/loader mutation controls are excluded
  from shared standalone IT unless a non-mutating or isolated environment is
  added. `getPluginAvailability` needs a server-route follow-up before it can be
  covered through the maintainer SDK admin path.
- Config import/export and delete-history remain HTTP admin operations that are
  not currently exposed by the maintainer SDK.
- AI review-pipeline plugin approval remains a plugin-enabled environment
  follow-up. Skill/AgentSpec/Prompt download endpoints and AI import/adaptor
  paths are not currently exposed by the maintainer SDK.
- Auth-enabled maintainer SDK behavior across core, config, naming, and AI is
  intentionally deferred because standalone maintainer SDK IT currently runs
  with auth disabled.
