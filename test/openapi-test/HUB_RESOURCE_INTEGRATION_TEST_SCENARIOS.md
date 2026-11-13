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

# Hub resource API scenarios (2026-10-29)

| Surface | Required scenarios | Automated coverage |
| --- | --- | --- |
| MCP/Agent/Prompt/AgentSpec management | share, retry, adjust CAS, revoke, denied source READ/WRITE, governance denial, malformed identity, reserved namespace | HubTypedManagementOpenApiITCase, HubPromptSharingOpenApiITCase, HubResource* unit tests |
| Ordinary shared reads | pinned version, opaque ID, duplicate display names, original ordinary namespace dispatch, disabled Hub, revoked/private/deleted/offline source | typed adapter tests and existing Client IT boundary cases |
| MCP live services | existing source service ref and backend/frontend endpoints, paths and headers; no extra instance table or ordinary response fields; no Hub Naming writes | McpEndpointOperationServiceTest, HubMcpReadAdapterTest, Hub content component tests |
| Agent runtime-endpoints | source Runtime Snapshot with exact shared version, source updates, denied/revoked share, no synthetic Hub Naming service | runtime/Hub unit coverage; deployed run pending |
| Four resource clone exclusion | Hub import enabled; no direct source copy/clone methods or UI entry | management contract and component tests |
| Hub read admission regression | Skill-style conditional bindings admit verified no-source-READ viewers for pinned Hub content; ordinary source reads and writes still deny; native Agent/AgentSpec DTOs | HubSharedReadBindingTest, ConsoleHubSharedReadBindingTest, HubTypedManagementOpenApiITCase; exported permission suite adds paired Hub/source Client reads |
| Disabled typed management | MCP/Agent/Prompt/AgentSpec cancellation and source/governance state return 501/API_FUNCTION_DISABLED before storage access | HubDisabledApiOpenApiITCase; management/publication unit tests; dedicated disabled-instance HTTP suite |
| Empty Agent tags | metadata [] projects to nullable RAD tags without rejecting the catalog or mutating source | HubAgentReadAdapterTest; Agent lifecycle and duplicate-name HTTP search |
| Skill-review parity | preserve version storage on failed rollback; source-query failures fail directory with sanitized 5xx/cause; ordinary compatibility writes reject reserved Namespace before delegation | HubResourceDraftTransferWriterTest, HubResourcePublicationServiceTest, HubReservedNamespaceWriteBoundaryTest; fault injection is unit/component coverage, not a deployed database-outage test |
| Skill clone | label Clone, existing copy authorization and target conflict behavior | existing Skill transfer tests |

Deployed success/permission/revocation verification and remote Console execution remain pending. Compilation and unit/component results do not certify a live deployed run.


All four resources retain Hub import. MCP Direct import creates an independent target Naming service from one unambiguous persistent fixed address; ordinary REF retains original coordinates and STDIO creates no Naming state. All imports remain private drafts; frontend REF/DIRECT/BACKEND, paths, headers and Tools/Resources are preserved. HubMcpAutomaticTransferOpenApiITCase covers Direct creation, draft serving exclusion, explicit publish, offline retention, independent source/target state, target deletion and REF preservation. Unit tests cover ownership classification, unhealthy/disabled fixed endpoints, dynamic-instance exclusion, invalid/missing/ambiguous addresses, target service conflicts, write failure and rollback ownership, shared-revision revalidation. Deployed execution remains pending; compilation is not integration acceptance.

2026-10-30 shared-detail parity: ordinary MCP detail/model/query are restored (no added instance table/field). MCP source and Hub reuse the detail body; Agent Hub reads the pinned source runtime snapshot and original Naming link. Prompt retains native template debugging and variable input. Prompt/MCP/Agent/AgentSpec preserve pinned-version review results and actual labels; unrelated source versions remain excluded. Regression tests cover runtime refresh, Prompt debug payload, label filtering and review fidelity. Live deployed API acceptance is pending.

Pinned source navigation parity: non-Skill HubResourceDiscoveryInfo adds sourceReadable and permission-gated sourceNamespaceId/sourceName, following Skill. Cover independent source auth READ + visibility, anonymous/denied/plugin failure hiding coordinates without blocking Hub reads, large opaque entry identity preservation, and exact-version source navigation for each type. HTTP/SDK method signatures are unchanged; API JSON model/permission unit tests and frontend mapping tests cover the added contract. Deployed multi-identity acceptance remains pending.


## Native regression alignment, 2026-11-12

| Scenario | Expected | Native evidence |
| --- | --- | --- |
| Skill transfer boundary and removed alias | `/clone` receives parameter/auth validation; removed `/copy` is not used as an active API | HubSkillApiBaseITCase, inherited by Admin and Console |
| Console Skill/Prompt lifecycle | Existing Console port and relative `/v3/console/...` route, no duplicated host or `/nacos` prefix | HubSkillConsoleApiOpenApiITCase; HubPromptSharingOpenApiITCase |
| MCP Direct/REF source preparation | Top-level concrete version and matching serverSpecification.versionDetail; unshare carries Hub ID and CAS | HubMcpAutomaticTransferOpenApiITCase |
| Numeric Hub bounds and missing entries | Keep strict 400/20002 numeric validation and 404/20004 missing-entry assertions | HubSkillApiBaseITCase, inherited by Admin and Console |
| Removed dedicated Skill read routes | No controller handler registered for `/hub/skills/{list,detail,content,download}`; no assertion on the framework's unmapped-URL HTTP status | HubSkillReadRouteRegistrationTest in console, Admin and Console cases; unit coverage, not deployed HTTP IT |

Nonnumeric pagination is outside this PR's regression scope. Reproduction records are retained:
stock develop 7f71d44f880535dcf0ba14829df4a98329fc4e06 and 4a8c0cd3f both return
500/30000 for ordinary Skill list `pageNo=abc` and 500 for unmapped Console GET/POST
paths. The previous strict failures and actual request/response evidence remain in the
test report. Removing these checks from the Hub selection does not fix the develop
defects or reclassify HTTP 500 as passing; public logic remains unchanged.
