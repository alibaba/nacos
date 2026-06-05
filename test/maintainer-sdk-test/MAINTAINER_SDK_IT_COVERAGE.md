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

## Pending Maintainer SDK Surfaces

- `CoreMaintainerService` namespace, cluster, plugin, and loader operations
- `ConfigMaintainerService`
- `BetaConfigMaintainerService`
- `ConfigHistoryMaintainerService`
- `ConfigOpsMaintainerService`
- `NamingMaintainerService`, `ServiceMaintainerService`,
  `InstanceMaintainerService`, and `NamingClientMaintainerService`
- `AiMaintainerService`, `McpMaintainerService`, `A2aMaintainerService`,
  `PromptMaintainerService`, `SkillMaintainerService`,
  `AgentSpecMaintainerService`, and `PipelineMaintainerService`
