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
| `CoreMaintainerService` | `CoreMaintainerServiceMaintainerSdkITCase` | Partial | Verifies factory creation through `NacosMaintainerFactory`, standalone server liveness/readiness, server-state result mapping, ID generator list, cluster node list, current client map, cluster loader metrics, plugin list, namespace create/get/list/update/check/delete lifecycle, duplicate namespace controlled error, invalid namespace parameter errors, default namespace lookup, default `nacos.host`/`nacos.port` profile wiring, and shutdown cleanup. | Unavailable-server error mapping, auth-enabled behavior, mutating cluster/plugin/loader controls, and wider maintainer SDK surfaces remain pending for later batches. |
| `ConfigMaintainerService` / `ConfigHistoryMaintainerService` / `BetaConfigMaintainerService` / `ConfigOpsMaintainerService` | `ConfigMaintainerServiceMaintainerSdkITCase` | Partial | Verifies publish/get/list/search/update-metadata/update/delete lifecycle, namespace config list, delete by storage ID, missing config controlled exception, invalid publish parameters, config history list/detail/previous queries across updates, beta publish/query/stop and missing beta IP validation, config listener diagnostics by config and IP, local-cache dump command, config log-level command, default host/port wiring, and cleanup. | Clone/import/export and auth-enabled behavior remain pending for later batches. |
| `NamingMaintainerService` / `ServiceMaintainerService` / `InstanceMaintainerService` | `NamingMaintainerServiceMaintainerSdkITCase` | Covered | Verifies persistent service create/get/update/list/detail-list/remove lifecycle, missing service controlled exception, invalid service parameter validation, persistent instance register/list/detail/update/partial-update/batch-metadata-update/batch-metadata-delete/deregister lifecycle, invalid instance parameter validation, selector type and health-checker queries, cluster health-checker metadata update, manual persistent instance health status update, naming client list/detail/publisher/subscriber diagnostics, subscriber diagnostics, naming metrics/log operations, default host/port wiring, and cleanup. | Auth-enabled behavior is intentionally deferred because maintainer SDK IT currently runs against auth-disabled standalone Nacos. |
| `AiMaintainerService` / `McpMaintainerService` / `A2aMaintainerService` / `PromptMaintainerService` / `SkillMaintainerService` / `AgentSpecMaintainerService` / `PipelineMaintainerService` | `AiMaintainerServiceMaintainerSdkITCase` | Partial | Verifies AI maintainer factory delegate creation, empty-result list queries, pipeline Result-wrapper list query, MCP local server create/get/list/search/update/delete lifecycle and invalid local/remote spec validation, A2A agent register/get/list/search/update/delete lifecycle, Prompt draft/update/force-publish/label/description/biz-tag/online-status/list/delete lifecycle, Skill draft/update/force-publish/label/biz-tag/scope/online-status/list/delete lifecycle, AgentSpec draft/update/force-publish/label/biz-tag/scope/online-status/list/delete lifecycle, null factory property validation, default host/port wiring, and cleanup. | ZIP upload/download and batch upload workflows, real review-pipeline submit/publish approval, deeper pipeline detail assertions, AI import/adaptor paths, and auth-enabled behavior remain pending for later batches. |

## Remaining Maintainer SDK Follow-up Surfaces

- `CoreMaintainerService` cluster/plugin/loader mutation controls and
  unavailable-server error mapping.
- `ConfigMaintainerService` clone/import/export workflows and
  delete-history behavior.
- `AiMaintainerService` ZIP upload/download and batch-upload workflows,
  real review-pipeline submit/publish approval, deeper pipeline detail
  assertions, and AI import/adaptor paths.
- Auth-enabled maintainer SDK behavior across core, config, naming, and AI is
  intentionally deferred because standalone maintainer SDK IT currently runs
  with auth disabled.
