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

The destructive same-client restart scenario is orchestrated separately by
[`../DEFAULT_AUTH_RELIABILITY_IT.md`](../DEFAULT_AUTH_RELIABILITY_IT.md).

## Authentication Baseline

Authentication is cross-cutting and does not add another maintained SDK
surface to the registry below. `MaintainerSdkBaseITCase` selects the
administrator identity by default when `nacos.test.auth.enabled=true` and can
also construct read-write, read-only, no-permission, anonymous, and invalid
credential property sets. `AuthEnabledMaintainerSdkITCase` defines six
cross-cutting scenarios against the auth-on baseline:

- Core administration accepts the global administrator and rejects anonymous,
  invalid, ordinary read-write, and authenticated no-permission identities;
- Config and Naming administration follows the existing resource RBAC model:
  read-only identities can read, denied writes have no side effects, and an
  explicitly authorized non-administrator can perform the scoped operation;
- the invalid-explicit-credential check against an anonymous-enabled AI Skill
  list is retained but disabled as `DAUTH-F04`, because the rolled-back client
  currently downgrades a failed login to anonymous;
- a simulated stale token causes a controlled 403, refreshes within a bounded
  window, leaves the denied namespace creation unapplied, and allows exactly
  one explicit creation after recovery;
- shutting down a closeable Maintainer service stops its authentication refresh
  executor.
- a directed real-server restart preserves the pre-existing namespace, restores
  the original Core client, creates a new namespace exactly once, and retains
  controlled duplicate rejection.

The complete Core, Config, Naming, AI, Agent, and MCP suites run with the
administrator identity. This includes multipart Skill and AgentSpec ZIP
uploads with content assertions and the existing 400/404/409 error mappings.
On 2026-09-04, the default adapter and Jackson 3 each discovered 46 tests: 44
passed, none failed, and 2 skipped while all three server auth scopes and the
default auth cache were enabled. One skip is the environment-gated real-server
restart that passed in the reliability suite; the other is the exact
`DAUTH-F04` invalid-credential scenario.

| Maintainer SDK interface | IT class | Status | Scenario coverage | Known gaps |
| --- | --- | --- | --- | --- |
| `CoreMaintainerService` | `CoreMaintainerServiceMaintainerSdkITCase` | Partial | Verifies factory creation through `NacosMaintainerFactory`, standalone server liveness/readiness, server-state result mapping, unavailable-server controlled exception mapping, ID generator list, cluster node list, current client map, cluster loader metrics, plugin list, plugin type filtering, plugin detail lookup, namespace create/get/list/update/check/delete lifecycle, duplicate namespace controlled error, invalid namespace parameter errors, default namespace lookup, default `nacos.host`/`nacos.port` profile wiring, and shutdown cleanup. The complete suite runs as administrator under auth, and the focused matrix proves that anonymous, invalid, ordinary-client, and no-permission identities cannot enter Core namespace administration. | Mutating cluster/plugin/loader controls are intentionally excluded from shared standalone IT because they can alter runtime state. `getPluginAvailability` remains a server-route follow-up because the maintainer SDK points to `/v3/admin/core/plugin/availability`, while current standalone Nacos only exposes the availability query through the console plugin path. |
| `ConfigMaintainerService` / `ConfigHistoryMaintainerService` / `BetaConfigMaintainerService` / `ConfigOpsMaintainerService` | `ConfigMaintainerServiceMaintainerSdkITCase` | Covered | Verifies publish/get/list/search/update-metadata/update/delete lifecycle, namespace config list in the current namespace model, delete by storage ID scoped to namespace including default-method isolation and explicit namespace delete, clone by storage ID within one namespace and across explicit source/target namespaces, source-scoped clone ID resolution, clone SKIP/OVERWRITE conflict policies, clone empty-selection failure data, missing config controlled exception, invalid publish parameters, config history list/detail/previous queries across updates, current gray-backed beta publish/query/stop and missing beta IP validation, config listener diagnostics by config and IP, local-cache dump command, config log-level command, default host/port wiring, and cleanup. The complete suite runs as administrator under auth; the focused matrix additionally verifies read-write, read-only, no-permission, denied-write side effects, stale-token recovery, and auth-refresh shutdown behavior. | Import/export and delete-history are HTTP admin operations not currently exposed by the maintainer SDK. Removed pre-3.0 empty-tenant and `config_info_beta` / `config_info_tag` migration paths are outside the 3.3 maintainer SDK contract. |
| `NamingMaintainerService` / `ServiceMaintainerService` / `InstanceMaintainerService` | `NamingMaintainerServiceMaintainerSdkITCase` | Covered | Verifies persistent service create/get/update/list/detail-list/remove lifecycle, missing service controlled exception, invalid service parameter validation, persistent instance register/list/detail/update/partial-update/batch-metadata-update/batch-metadata-delete/deregister lifecycle, invalid instance parameter validation, selector type and health-checker queries, cluster health-checker metadata update, manual persistent instance health status update, naming client list/detail/publisher/subscriber diagnostics, subscriber diagnostics, naming metrics/log operations, default host/port wiring, and cleanup. The complete suite runs as administrator under auth; the focused matrix verifies scoped read-write/read-only access, no-permission rejection, and absence of denied-write side effects. | No generalized auth-enabled gap remains. |
| `AiMaintainerService` / `McpMaintainerService` compatibility API / `A2aMaintainerService` / `PromptMaintainerService` / `SkillMaintainerService` / `AgentSpecMaintainerService` / `PipelineMaintainerService` | `AiMaintainerServiceMaintainerSdkITCase` | Partial | Verifies AI maintainer factory delegate creation, including both `agent()` and the retained `a2a()` compatibility delegate; empty-result list queries; Pipeline Result-wrapper list query and missing-detail controlled 404 when the deprecated fallback API is gone; MCP compatibility create/get/list/search/update/delete behavior and invalid local/remote spec validation; A2A agent register/get/list/search/new-version update/delete lifecycle plus immutable same-Version conflict; Prompt draft/update/force-publish/label/description/biz-tag/online-status/list/delete lifecycle; Skill draft/update/force-publish/label/biz-tag/scope/online-status/list/delete lifecycle; Skill ZIP-only precheck followed by authenticated upload with target version and commit message; Skill batch ZIP upload with legacy success/failure fields and per-item result details; AgentSpec authenticated ZIP upload and version-meta query; AgentSpec lifecycle; Skill/AgentSpec submit direct-publish behavior when no review Pipeline plugin is enabled; null factory property validation; default host/port wiring; and cleanup. The complete suite runs as administrator under auth, verifies uploaded content after multipart transfer, and separately proves that an invalid explicit identity cannot enter an anonymous-enabled Skill list. | Real review-Pipeline plugin approval remains intentionally excluded because standalone Maintainer SDK IT does not enable review plugins. Skill/AgentSpec/Prompt download endpoints and AI import/adaptor paths are HTTP/admin or Java SDK surfaces that are not currently exposed by the Maintainer SDK. `AiMaintainerService` does not currently expose `shutdown`, so its private HTTP/auth-refresh resources cannot be closed through the public interface; changing that public contract requires a separate spec decision. The invalid-explicit-credential fail-closed assertion is retained but disabled as `DAUTH-F04`; restore it after the client authentication fix. |
| `McpMaintainerService` typed lifecycle API | `McpMaintainerServiceMaintainerSdkITCase` | Covered | Runs only after the functional fixture reaches stable `LIFECYCLE_MANAGED`, then verifies default-public and explicit-namespace lifecycle draft creation, exact and bounded Version reads, full draft replacement, no-Pipeline submit, force-publish, offline/online, Resource enable/disable and public/private scope, custom labels with preserved `latest`, draft deletion, controlled not-found and invalid-state errors, and cleanup. It also verifies STDIO optional Tools/Resources and a Direct Streamable endpoint remain readable through the deprecated compatibility detail API after lifecycle publication while typed lifecycle detail exposes server-derived `writable` and suppresses internal `mcpId`. `McpUpgradeMigrationJavaSdkITCase` owns the separately orchestrated historical reconciliation and cutover checks. Version summaries/details expose optional `publishPipelineInfo`; component tests cover approved/rejected payload mapping because standalone has no MCP review Pipeline plugin. Unit coverage fixes the deprecation boundary between superseded legacy methods and retained list/search/delete methods. | Successful reviewed-state `publish` and `redraft` require an MCP review Pipeline plugin and are intentionally excluded from the standalone environment. REF endpoint behavior remains covered by component and compatibility-path tests because this SDK change only adds typed lifecycle transport. |
| `AgentMaintainerService` | `AgentMaintainerServiceMaintainerSdkITCase` | Partial | Verifies that first-draft creation creates missing Agent metadata with optional catalog fields and server-derived enable/owner/default-scope values; Request/Command models contain no namespace; convenience overloads use `public`; explicit method arguments are the sole custom-namespace source; Agent get/update/delete with update preserving owner/scope; bounded overview/list/Version reads, including inherited metadata and actual concrete summaries without detail fields after Agent/RAD model consolidation; fuzzy name, one business-tag, scope, and owner filters; direct and copied later draft create/update/delete; force-publish and no-Pipeline submit success; offline/online; custom labels while preserving `latest`; empty Runtime Endpoint snapshots; and controlled not-found, validation, publish-state, and redraft-state errors. The complete suite runs as administrator under auth. | Successful reviewed-state `publish` and `redraft` require an Agent review Pipeline plugin and remain excluded because no review plugin is installed. Runtime Snapshot coverage is empty-state because publisher registration belongs to the later Client/RAD transport stage. Scope coverage adds PUBLIC creation, explicit/default namespace updateScope, lowercase input, private draft retry, and invalid-scope errors.  |

## Remaining Maintainer SDK Follow-up Surfaces

- `CoreMaintainerService` cluster/plugin/loader mutation controls are excluded
  from shared standalone IT unless a non-mutating or isolated environment is
  added. `getPluginAvailability` needs a server-route follow-up before it can be
  covered through the maintainer SDK admin path.
- Config import/export and delete-history remain HTTP admin operations that are
  not currently exposed by the maintainer SDK.
- AI review-Pipeline plugin approval, including Agent reviewed-state
  `publish`/`redraft` success, remains a plugin-enabled environment follow-up.
  Skill/AgentSpec/Prompt download endpoints and AI import/adaptor paths are not
  currently exposed by the Maintainer SDK.
- `AiMaintainerService` has no public lifecycle/shutdown method. The auth-on IT
  validates its complete functional and multipart behavior, but a public API
  and spec decision is required before callers can explicitly stop its private
  HTTP and auth-refresh resources.

### Agent 元数据模型合并（2026-09-14）

Agent 元数据合并的响应形状和完整标签验证补入现有 AgentMaintainerService 场景，覆盖登记保持不变。

本轮独立验证：默认与 Jackson 3 各 4 项通过。详情见 `Codex/design/nacos-3.3-client-ai-api/MODEL_VALIDATION.md` 的 2026-09-14 记录。

### Agent 地址模型统一：实施与验收（2026-09-15）

CallInterface → EndpointSet → Endpoint 统一已落地，验收要求见 [测试矩阵](../../Codex/design/nacos-3.3-client-ai-api/MODEL_ENDPOINT_TEST_PLAN.md)，本轮实际执行见 [验证记录](../../Codex/design/nacos-3.3-client-ai-api/MODEL_ENDPOINT_VALIDATION.md)。healthy 注册可写，服务端维护字段忽略；管理 Runtime 读取改为 `callInterface.endpointSets[].endpoints[]`，状态和绑定位于 Endpoint，观察时间位于 Set。旧 A2A wire 不变。以下原有覆盖状态不以编译通过或历史测试数量自动提升。


### 2026-09-15 请求整合回归

AgentMaintainerService 覆盖行保持；本轮只调整五个具体请求的包名/类名及共享字段继承，现有真实生命周期与两种 JSON adapter 验收继续作为门禁。

本轮实际执行状态见 [请求整合验证记录](../../Codex/design/nacos-3.3-client-ai-api/MODEL_REQUEST_VALIDATION.md)。
既有 Covered/Partial/Pending 表示场景覆盖归属，不表示本轮已重新执行；不能引用前轮结果代替本轮验收。

## Remote API errors（2026-09-16）

AgentMaintainerServiceMaintainerSdkITCase strengthens existing missing-resource, invalid-publish,
invalid-redraft, and deleted-version scenarios: NacosApiException retains HTTP status separately
from the exact business code and summary. The complete Maintainer SDK suite is exercised with
both default and Jackson 3 adapters because ClientHttpProxy is shared by all domains, including
multipart uploads, authentication refresh, and legacy fallback. Existing scenario row counts
and known exclusions remain unchanged. Nonstandard remote responses keep the generic exception
fallback and are covered by proxy unit tests.

See [validation evidence](../../Codex/design/nacos-3.3-client-ai-api/CONSOLE_ERROR_VALIDATION.md).

Verified on 2026-09-16: both adapters discovered 46 cases, with 44 passed, zero failures/errors,
and two existing skips each (DAUTH-F04 and the opt-in real restart scenario). No recovery test was
performed. This validates the strengthened business-error assertions without upgrading unrelated coverage gaps.

Skill frontmatter (#15345): `AiMaintainerServiceMaintainerSdkITCase` verifies
that metadata detail and list deserialization retain parsed frontmatter after
content update, publication and version offline/online. Historical responses may
omit frontmatter or return null; API model round-trip unit tests cover that boundary.
