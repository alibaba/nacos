<!--
  Copyright 1999-2026 Alibaba Group Holding Ltd.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# OpenAPI IT Coverage Registry

This registry records which Nacos HTTP APIs are covered by
`test/openapi-test` integration tests and where to find the scenario matrix for
each API surface. It is meant for maintainers and agents to quickly locate
coverage before adding or debugging an IT.

The historical A2A upgrade matrix is tracked separately in
[`A2A_MIGRATION_API_TEST_SCENARIOS.md`](A2A_MIGRATION_API_TEST_SCENARIOS.md).
Its rows strengthen the existing A2A, Agent, Search, Console, and RAD surface
coverage rather than creating new HTTP operations, so their status does not
change the API-surface totals below.

## Maintenance Rules

- Update the matching scenario document whenever an OpenAPI/AdminAPI/ConsoleAPI
  IT class is added, removed, or gains meaningful scenario coverage.
- Keep the class Javadoc `Scenario coverage` section or the scenario document
  as the source of truth for what a class verifies.
- Record API scenario coverage, not line or branch coverage. Each row should
  identify expected capability, boundary/validation, and exception/error
  handling coverage when those scenario groups are practical.
- If an exposed success path is intentionally not executed because it mutates
  risky runtime or storage state, record the reason in the scenario cell.
- When a change only corrects authorization metadata without changing the HTTP
  request or response contract, keep the functional scenario status unchanged,
  record the affected surface in its scenario document, and verify the exact
  `@Secured` tuple with a focused module test.

## Status Legend

| Status | Meaning |
| --- | --- |
| Covered | The current IT verifies the expected behavior and its important result shape. |
| Partial | The current IT verifies representative behavior, but important public API scenarios remain. |
| Pending | No IT currently verifies this public API scenario. |

## Coverage Calculation

Coverage is calculated from the API-surface rows in the scenario documents. One
row may cover multiple closely coupled HTTP operations in a workflow, such as a
create/query/update/delete API group. Strict coverage counts only `Covered`
rows. Effective coverage counts `Covered` rows as `1.0` and `Partial` rows as
`0.5`.

| API surface | Scenario rows | Covered | Partial | Pending | Strict coverage | Effective coverage |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Client OpenAPI | 15 | 13 | 2 | 0 | 86.67% | 93.33% |
| Admin API | 38 | 31 | 7 | 0 | 81.58% | 90.79% |
| Console API | 29 | 24 | 5 | 0 | 82.76% | 91.38% |
| Auth API | 4 | 4 | 0 | 0 | 100.00% | 100.00% |
| Total | 86 | 72 | 14 | 0 | 83.72% | 91.86% |

The covered Naming cluster metadata rows include Admin and Console validation
for built-in HTTP health-check request targets: relative paths and queries are
accepted, scheme/authority/fragment inputs are rejected with HTTP 400, and
invalid or request-framing headers are rejected with HTTP 400. Rejected
updates leave the previously persisted cluster metadata unchanged.
This strengthens existing rows and does not change the row totals.

`AuthScopeGuardITCase` and the method-level authorization inventory are
cross-cutting migration guards and are deliberately excluded from the
API-surface row totals above. When
`nacos.test.auth.enabled=true`, it proves that Client, Admin, and Console auth
scopes are still enabled, checks anonymous, invalid, authorized, read-only, and
no-permission identities on stable representative APIs, keeps the public
liveness probe available, and verifies that requests to the independent ARD
port cannot inherit a Nacos authorization header. The guard complements rather
than replaces the functional scenario rows. The live
[authorization operation registry](AUTHORIZATION_OPERATION_COVERAGE.md) maps all
393 production `@Secured` methods in 57 Controllers to direct tests or reviewed
parser/resource-equivalence groups and is checked against source by
`ModuleAuthorizationITCase`.

The unified default-auth functional workflow now runs the complete OpenAPI suite with
the production authorization cache enabled. Client routes use the restricted
`ClientReadWrite` identity, while Admin, Console, and Auth routes use the
administrator identity. Public endpoints and the independent ARD port remain
explicitly anonymous. Private AI fixtures are made visible through resource-
exact grants or are published by the client identity and observed as owner;
the functional assertions and row counts are unchanged.

Historical A2A and MCP reconciliation/cutover scenarios are intentionally excluded
from that functional workflow. They run in `.github/workflows/migration-it.yml`
through explicitly phase-gated test classes and are documented in
`A2A_MIGRATION_API_TEST_SCENARIOS.md` and
`MCP_MIGRATION_API_TEST_SCENARIOS.md`. Migration-only rows do not change the
stable API-surface coverage totals above.

The post-rollback default-auth run on 2026-09-04 discovered 431 tests: 423
passed, 0 failed, and 8 were skipped. Three skips are explicitly linked to
existing product findings: the Skill authorization boundary (`DAUTH-F01`) and
two private shared-index projection scenarios (`DAUTH-F03`). The other five
are pre-existing environment-conditional scenarios. Exact disabled methods and
their restoration criteria are recorded in
`Codex/design/nacos-3.3-client-api-default-auth/UNEXPECTED_PRODUCT_FINDINGS.md`.

Partial rows are documented in the matching scenario document. The current
partial set is limited to operations whose remaining success paths mutate
shared runtime/storage state, require publish-pipeline plugin data, require a
data-plane publisher binding not yet present in standalone IT, require an
external MCP runtime or LLM provider, or require authenticated multi-identity
AI resource visibility scenarios. Default-auth User, Role, Permission, and
Visibility API workflows are now all `Covered` in the unified OpenAPI module.

External protocol adaptors are tracked separately from the Nacos API coverage
totals because they run in independent web contexts. The ARD adaptor currently
has one covered scenario row and three partial rows. Its standalone conformance
suite publishes canonical resources through the main-server Admin APIs and then
verifies the separate ARD port against the same shared index, including singular
Agent catalog entries, A2A/Nacos representation selection and filtering,
latest-Version eligibility, facets, and exact Version/digest artifacts.

Plugin management API IT covers detail metadata, request validation, not-found
responses, rejection of config updates for non-configurable plugins, and the
built-in `auth:nacos`, `auth:ldap`, and `auth:oidc` configuration contracts. It
verifies definitions, legacy aliases, effect modes, effective values, source
metadata, API-side secret masking, plugin-type execution/criticality metadata,
critical disable rejection, exclusive restart-only selection, and OIDC restart-only update
rejection. It also verifies local-only runtime mutation and empty-map source
clear through refreshed detail metadata.
Persisted runtime mutation remains partial to avoid carrying plugin state into
later SDK suites in the shared standalone process; persisted full-map
replacement, source fallback, effect mode checks, same-source sensitive value
preservation, and retained-source apply failure/retry are covered in core unit
tests. The migration guard exercises an anonymous ARD well-known request and
rejects Nacos credential forwarding to that external port. The live ARD
scenario also grants its anonymous identity exact read visibility to the
resources it publishes. Exhaustive explicit blank/invalid credential behavior
is now exercised directly for all conditional-anonymous operations, while
resource-level identity switching, exact adjacent-resource denial, owner versus
non-owner behavior, and cache-enabled revocation convergence are covered by the
focused authorization matrix. Auth/core unit tests retain lower-level error
mapping and cache branches.

Config scenario rows cover the current 3.3 Config model. Blank or omitted
namespace inputs are expected to use `public`, and beta/tag gray behavior is
verified through the current gray model. Config publish scenarios preserve dots
inside names while rejecting exact `.` and `..` identity segments, and gray
validation rejects directory control names. Batch delete and export-by-id scenarios
verify that storage IDs remain scoped by the requested namespace, and clone
scenarios verify that storage IDs are resolved only within the requested source
namespace before writing to the target namespace. Config, history, and capacity
responses also verify that storage IDs remain JSON strings. Removed pre-3.0 compatibility
migration paths, including empty-tenant storage migration and legacy
`config_info_beta` / `config_info_tag` old-table migration, are not counted as
missing OpenAPI IT coverage.

Skill upload precheck response-shape coverage is maintained by the admin and
console Skill upload scenario rows, including `maxPublishedVersion` and
`targetVersion`. Precheck requests carry only the archive and namespace. Those
rows verify that precheck predicts a version from archive sources and server
state, while upload can select a later available request `targetVersion` before
server-side version generation. They also cover the batch upload compatibility
fields `succeeded` and `failed`, plus per-item `success`, `errorCode`, and
`errorMessage` in `results`. Contract-only field changes do not alter the
scenario-row totals above.

Skill draft update and upload-overwrite rows on both Admin and Console surfaces
create content with a resource file, replace the draft without that file, and
verify that query/download results no longer expose it. Deletion through the
persisted storage provider and descriptor retention when cleanup fails are
covered by focused service tests because backing storage objects and injected
provider failures are not externally observable in the standalone API suite.

Skill, Prompt, and AgentSpec submit scenarios retry submit when the standalone
environment leaves the target in `reviewing`, proving the HTTP operation is
idempotent and does not require a new draft. Resubmitting a `reviewed` version
and recovering a legacy `reviewing` Skill with a terminal pipeline result are
covered by service tests because the standalone suite does not install a
deterministic publish pipeline that can create those states. Service tests also
verify that a terminal result marked `historical=true` remains an idempotent
`reviewing` submit because it belongs to a previous review cycle.

AI Agent, AgentSpec, Prompt, and Skill deletion success and post-delete absence
remain covered by the existing Admin and Console rows. Storage-provider failure,
multi-file partial failure, persisted-provider routing, and deletion of more
than one storage page are covered by focused service tests because the
standalone suite has no storage fault-injection provider and cannot safely seed
those failure states. Those tests verify that the HTTP service reports the
cleanup error and retains the resource/version descriptors for retry.

Agent Admin definition creation is counted in the existing Agent Admin and
Version scenario rows. The unified `POST /v3/admin/ai/agents/draft` operation
creates missing Agent metadata together with the first draft; the removed root
`POST /v3/admin/ai/agents` operation is no longer a coverage surface. This
contract consolidation does not change the scenario-row totals. POST retry and
conflict scenarios prove that creation does not replace current draft content;
replacement remains the distinct `PUT /v3/admin/ai/agents/draft` operation.

Agent Console management is tracked as one additional covered Console scenario
row. It mirrors the Agent Admin relative paths and form contracts, while
`GET /v3/console/ai/agents/runtime-endpoints` adds only the Console-specific
Naming service reference wrapper. Agent lifecycle and persistence semantics
remain covered by the existing Admin rows rather than being redefined by the
Console facade. Agent Version summaries and exact details expose optional
`publishPipelineInfo`; Admin and Console IT assert that no value is invented
before a review Pipeline exists, while focused tests cover terminal rejection
projection and force-publish visibility. The Client Endpoint scenario
cross-validates an Admin-created
and published Agent through Console Overview, then verifies that Client
registration and deregistration produce matching populated and empty Runtime
snapshots through both Admin and Console.

Legacy A2A Admin and Console operations are now compatibility facades over the
same canonical Agent definition. The Admin A2A row covers both directions:
legacy create/update/promote/delete observed through canonical Agent reads, and
canonical draft/force-publish observed through legacy AgentCard reads. The
Console A2A row verifies legacy Console create to canonical Console read,
legacy Admin create to canonical Console read across ports, and canonical
Console create to legacy Console and Admin reads. These scenarios deliberately
do not read or write the removed parallel
Config definition layout; legacy SERVICE Runtime lookup remains a separate
exact-Version Naming concern.

Historical A2A upgrade coverage now includes a dedicated AUTO/SYNCING restart
of the standalone server. `A2aMigrationAdminApiOpenApiITCase` verifies
historical Admin create/delete and Console update/latest mutation, bounded
canonical Agent convergence, migration-owned Agent write protection with
detail code `50105`, namespace and URL/SERVICE isolation, malformed summary,
missing Version, invalid identity/Version/latest, independent canonical
conflict preservation, orphan cleanup, and cross-reads through Agent/generic
Search, Console, ARD, and RAD. Runtime dual materialization, standalone
quiescing/terminal behavior, and Watch across permanent cutover are now
covered by the migration Java SDK suite. The directed three-member suite
additionally verifies fixed-node and load-balanced reads, gRPC/HTTP Watch
de-duplication, cross-member Runtime replacement, terminal `CANONICAL`, and
cleanup during a real rolling binary upgrade. Internal persistence-boundary
crash injection, shadow=true, and separately directed Config-leader/Naming-
responsibility failures remain in the later migration commit identified by
the migration scenario registry.

The legacy MCP Console import validation and execute endpoints remain covered
by `McpConsoleApiOpenApiITCase` through Nacos 3.3.x. Their default HTTP 410 and
`API_DEPRECATED` responses use the shared
`nacos.core.api.compatibility.enabled` gate. They are planned for removal in
Nacos 3.4.0; the managed `/v3/console/ai/import/*` flow is covered separately by
`AiResourceImportConsoleApiOpenApiITCase`.

MCP lifecycle Version summaries and exact details now expose optional
`publishPipelineInfo`. Admin and Console standalone scenarios cover the
no-Pipeline shape; focused component tests cover approved/rejected payload
mapping because the standalone profile does not install an MCP review Pipeline
plugin.

`McpToolsImportConsoleApiOpenApiITCase` verifies that
`GET /v3/console/ai/mcp/importToolsFromMcp` rejects private or local targets by
default with an explicit private-allowlist message, without opening a network
connection. The standalone suite also covers its required endpoint parameter
and unsupported transport response. Public-target protocol success,
operator-approved private-target success, and optional token forwarding remain
an end-to-end gap because they require an external MCP runtime and controlled
server configuration. The operator switch, public-target policy, IP/CIDR
matching, DNS multi-address rejection, endpoint-origin enforcement, and invalid
configuration are verified by focused Console module tests.

MCP Admin detail coverage verifies the version-selection contract: when a
newer draft exists after a published version, an omitted `version` resolves the
latest published version, while the draft remains queryable by its explicit
version.

The stable Admin and Console MCP scenarios exercise all fourteen standard
lifecycle routes at their public HTTP boundary after `LIFECYCLE_MANAGED`:
name-only identity and exact-Version validation, nested legacy ID rejection,
case-insensitive lifecycle and scope input, controlled absent-resource responses,
resource enable/disable and scope updates, and one real draft create/delete/recreate
cycle including resource status, owner, scope, writable, labels, working pointers,
online count, and the retained zero-Version management detail. They do not accept a `SYNCING` conflict as
an alternative result. `McpMigrationAdminApiOpenApiITCase`, enabled only by the
dedicated migration workflow, owns the compatibility-router 409 gate,
historical fixture reconciliation, and post-cutover projection. Focused
component tests cover the
zero-difference, all-member capability, and Search-projection gates; permanent
marker retry/observation; per-request authority pinning; lifecycle
create/read/update/delete and state-transition success paths; storage-first
draft deletion/retry; and canonical re-authorization of deprecated ID-only
requests. Embedded and standalone Console use this local lifecycle facade;
remote Console forwards the same typed contract through the Maintainer SDK
transport and does not fall back to the legacy Config-writing path.

RAD Agent Client coverage is split into three rows. Search/Discover validates
the online catalog and discovery projection. The Search scenario is reusable
against `AUTO`, `INDEX`, and `SCAN`: `AUTO` and `INDEX` return the current
snapshot immediately without a readiness 503, then the test verifies literal and case-sensitive filters, stable
numbered pagination, complete online-Version catalog changes, and Runtime
Endpoint non-indexing. Definition publication validates
Client first-Version ordinary submit, full draft replacement controlled by `autoSubmit`,
non-draft no-op, direct and inherited Versions, validation/conflict handling, namespace isolation, and
cross-surface canonical projections. Endpoint publication validates the
Form-based independent HTTP Publisher lifecycle, required headers, idempotency,
and the `HTTP_CLIENT_NOT_FOUND (50404)` recovery signal. The Endpoint row also
cross-validates the published definition and Runtime state through Admin,
Client, and Console reads. Querying with a Client id is explicitly covered as
not creating an empty Client or Publisher. CI configures a small authoritative
Server Publication soft watermark, allowing the same row to prove whole-batch
crossing, atomic growth rejection, and slot reuse without creating 100 test
entries. The Client-only renewal and Publisher-renewal separation is covered by
the corresponding lifecycle unit tests.

Config Client OpenAPI validation coverage includes exact current-directory and
parent-directory identity segments for dataId, group, and namespace, and
asserts that each rejection remains an HTTP 400 parameter error.

MCP Client coverage adds direct-online-compatible and managed-draft release,
latest/exact serving reads, and stateful REF Runtime Endpoint publication. The
Endpoint scenario proves that Agent and MCP share one external HTTP Client
identity: removing either module's publication leaves heartbeat renewal active
while the other remains, and removing the final publication yields typed
`HTTP_CLIENT_NOT_FOUND`. Long-lived expiration and replay are exercised by the
Java SDK directed-restart scenario instead of mutating the shared OpenAPI IT
server lifecycle.

Protocol-neutral AI Resource Search coverage publishes one current online
resource for each declared type (Agent, AgentSpec, Skill, Prompt, and MCP),
waits for the durable projection rather than assuming synchronous indexing,
and validates both cross-type global recall and every generic-single-type to
resource-specific eligibility boundary. Its cursor scenario drains all pages
and rejects duplicates, while structured-filter scenarios cover exact-all
tags, exact-any capabilities, and MCP protocol filtering. Validation coverage
includes unknown types, malformed cursors, query/limit bounds, numbered-page
bounds, default namespace, and successful empty results.

## Coverage Documents

| API surface | Scenario document | Test package |
| --- | --- | --- |
| Client OpenAPI | [CLIENT_API_TEST_SCENARIOS.md](CLIENT_API_TEST_SCENARIOS.md) | `src/test/java/com/alibaba/nacos/test/openapi/client` |
| Admin API | [ADMIN_API_TEST_SCENARIOS.md](ADMIN_API_TEST_SCENARIOS.md) | `src/test/java/com/alibaba/nacos/test/adminapi` |
| Console API | [CONSOLE_API_TEST_SCENARIOS.md](CONSOLE_API_TEST_SCENARIOS.md) | `src/test/java/com/alibaba/nacos/test/consoleapi` |
| Auth API | [AUTH_API_TEST_SCENARIOS.md](AUTH_API_TEST_SCENARIOS.md) | `src/test/java/com/alibaba/nacos/test/adminapi/auth`, `src/test/java/com/alibaba/nacos/test/openapi/auth` |
| AI Registry Adaptor | [AI_REGISTRY_ADAPTOR_API_TEST_SCENARIOS.md](AI_REGISTRY_ADAPTOR_API_TEST_SCENARIOS.md) | `ai-registry-adaptor/src/test/java/com/alibaba/nacos/airegistry`, `src/test/java/com/alibaba/nacos/test/openapi/ard` |

## Agent model consolidation

Agent model consolidation strengthens the existing Agent Admin row with raw-JSON summary/detail boundary assertions in AgentAdminApiOpenApiITCase. The initial package-only consolidation preserved wire contracts. The resource/version consolidation below changes Search and management response shapes; Discover, Endpoint publication, definition publish and Watch retain their existing wire structures and regression suites. No HTTP surface is added or reclassified, so strict/effective coverage totals are unchanged.

### Agent 元数据模型合并（2026-09-14）

本轮 Agent 元数据合并更新 Search/Admin/Console 响应形状断言，复用既有 API surface 行，不增加覆盖率；执行结果见下方。

本轮独立验证：28 项通过、2 项既有条件跳过，包含受影响的 A2A 管理互通场景。详情见 `Codex/design/nacos-3.3-client-ai-api/MODEL_VALIDATION.md` 的 2026-09-14 记录。

### Agent 地址模型统一：实施与验收（2026-09-15）

CallInterface → EndpointSet → Endpoint 统一已落地，验收要求见 [测试矩阵](../../Codex/design/nacos-3.3-client-ai-api/MODEL_ENDPOINT_TEST_PLAN.md)，本轮实际执行见 [验证记录](../../Codex/design/nacos-3.3-client-ai-api/MODEL_ENDPOINT_VALIDATION.md)。healthy 注册可写，服务端维护字段忽略；管理 Runtime 读取改为 `callInterface.endpointSets[].endpoints[]`，状态和绑定位于 Endpoint，观察时间位于 Set。旧 A2A wire 不变。以下原有覆盖状态不以编译通过或历史测试数量自动提升。

本轮新增公开 INDEX/Artifact、直接 HTTP 健康输入/维护字段忽略，以及两种 Console 部署的非空统一模型成功流程。普通批次 56 通过、2 个原私有资源跳过；独立 Console 11 通过、3 个原错误映射断言失败。保留失败和 Disabled，不据此修改原统计口径。

### 2026-09-15 请求整合回归

Agent Client/Admin/Console 覆盖行的 HTTP 契约保持不变，本轮 Request 合并不增加新 HTTP surface；既有默认值、namespace 隔离、发布/运行地址、索引、A2A 迁移与 Artifact 场景全部纳入回归。

本轮实际执行状态见 [请求整合验证记录](../../Codex/design/nacos-3.3-client-ai-api/MODEL_REQUEST_VALIDATION.md)。
既有 Covered/Partial/Pending 表示场景覆盖归属，不表示本轮已重新执行；不能引用前轮结果代替本轮验收。

## Agent JSON 注解移除（2026-09-16）

Agent JSON 契约更新到 Schema 0.3.0：Client Endpoint 默认值/false/0/忽略管理输入、管理/Console 可选 null、版本摘要及导出；测试分别落在 AgentEndpointClientOpenApiITCase、AgentDiscoveryClientOpenApiITCase、AgentConsoleApiOpenApiITCase 及既有 Agent 管理套件。

[本轮测试矩阵](../../Codex/design/nacos-3.3-client-ai-api/MODEL_JSON_TEST_MATRIX.md)区分待执行项与实际结果。

## CONSOLE-ERR-01 错误透传回归（2026-09-16）

复用 AgentConsoleApiOpenApiITCase 和 A2aConsoleApiOpenApiITCase 原14项，在合并和独立
Console 各执行一遍，保留原400/404及23000/20004/50100断言、错误详情和成功副作用验证。
共享 Maintainer 代理的修改另回归 Config/Naming 代表流程；不新增覆盖行、不提升覆盖比例。
独立部署使用 nacos.deployment.type=console，nacos.console.port 指向该独立进程；Client
写入仍使用服务端端口。CI 自动运行独立部署仍是后续任务。

结果见 [Console 错误透传验证](../../Codex/design/nacos-3.3-client-ai-api/CONSOLE_ERROR_VALIDATION.md)。

实测：合并22项通过；独立21项通过、1项既有 Naming cluster 失败。Agent/A2A 两种部署各14项
全部通过，三项原错误码问题已消除。旧构件对照复现三项原失败及相同 Naming 失败，后者登记为
CONSOLE-NAMING-01；不放宽断言，不将其计为通过。详见上述验证记录。

Agent/MCP visibility coverage: the existing Agent Admin, Agent Console and MCP rows include independent scope updates and default PUBLIC creation. `AiResourceVisibilityOpenApiITCase` strengthens those rows with auth-enabled non-owner READ/WRITE and explicit grant/revoke tests; it does not add a new counted API surface. Default creation tests do not grant explicit visibility to their readers. Auth-disabled runs cannot validate isolation. Existing unrelated coverage gaps remain unchanged.

## Config detail schema (#15853)

Admin and Console config history ITs cover current detail, history detail, and
previous-version `schema`, including absent values (omitted or JSON null),
explicit empty current values, different schema versions, and preservation of
historical `extInfo`.
See the Config detail schema matrices in `ADMIN_API_TEST_SCENARIOS.md` and
`CONSOLE_API_TEST_SCENARIOS.md`. Corrupt/legacy extensions are covered by unit
tests because the public HTTP publish workflow cannot generate them.

## Planned AI capability and A2A/RAD adaptation (2026-09-16 historical design)

This section preserves the pre-implementation decisions. Its Pending/0-of-8 statements
are historical; current implementation and execution evidence follow in the C01–C11 entries.

The capability route is proposed, not deployed. Its identity-only design and the
seven affected Agent Client HTTP operations are listed in
[Client scenarios](CLIENT_API_TEST_SCENARIOS.md#planned-agent-compatibility-contract-2026-09-16-not-implemented)
and the [SDK scenario matrix](../java-sdk-test/JAVA_SDK_IT_SCENARIOS.md).
All 8 target operations remain Pending (strict/effective 0/8=0%, operation denominator);
the current API-surface coverage totals above are unchanged.
Implementation must add/update actual IT and promote only verified targets.
Admin and both Console deployment modes must prove first-version creation stays DRAFT
until explicit submission. Identity-only success must use a valid zero-resource-permission
identity; an administrator-only happy path is insufficient.

O1 is now agreed in matrix §7.1. Direct HTTP tests must exercise server-side
field inheritance, open/closed range boundaries and atomic rejection, writable
enabled, management/discovery differences, and state-free responses in both Console
deployments. O2 is agreed: one input binding per Endpoint; native complete replacement
remains explicit while the new A2A adapter preserves ranges on partial reference removal
(matrix §7.2). All target operations stay Pending.

The proposed capabilities operation now covers radV1/mcp/skill/prompt/agentSpec,
all scoped to the responding Client HTTP binding. Matrix §10.1 adds eight subcases
for response/identity, per-key UNKNOWN, old-server compatibility and no side effects.
The operation denominator remains eight and all target statuses remain Pending.

O3 adds ten detailed publication acceptance cases in matrix §6.1 without adding a
new HTTP operation. Verify first-Version creation versus existing-draft flags, complete
definition replacement, direct failure propagation and no automatic retry/recreation
or post-failure success recovery. A later explicit call uses current persisted state.
Keep Admin/Console draft semantics and real old-wire behavior separate. C05 stage results
are recorded below; future A2A routing and final matrix targets remain Pending.

O4 records eight detailed cases in matrix §5.1: mandatory dual-source definition
orders, preserved explicit single-source query filters, Discover/Watch consistency
and Console/Schema/storage/Artifact impact. Cover direct Client and Admin/Console
definition validation as well as runtime reads under either preference. All targets
remain Pending; no new HTTP operation or executed-coverage claim is introduced.

O6 now follows standard Client auth with ONLY_IDENTITY and the Client switch (C08/C09);
retain true/false/plugin branch evidence without adding capability-specific enforcement.
O5 direction is agreed: explicitly reject migration-unready requests from new RAD instances.
Separately validate unprojected historical names, lagging projections, definition guards
and Runtime writes, preserving 50105 and rejecting before business mutation. Current guards
and old-wire migration evidence do not prove complete coverage of that target. Preserve
the agreed uniform gate: unrelated standard resources are also rejected under historical
authority, with normal domain results restored on fresh/terminal CANONICAL. Capabilities and
existing-intent cleanup remain available. All future-target statuses remain Pending.

The user rejected an SDK upgrade-order restriction. O5 now explicitly includes SDK-first
rollout, capability refresh and mixed-version backend observations. Legacy-instance retention
is now agreed: no automatic promotion on reconnect or migration completion, while capability
responses still reflect the current node. SDK retention/re-instantiation and explicit
migration-error tests are Pending. Complete server rejection coverage and cross-transport
error propagation still need implementation. This design update does not claim completed
production changes or test execution.

## Staged adaptation validation (2026-09-17 design snapshot)

The Pending rows and 0/8 target below preserve the C00 planning snapshot. They do not
describe current implementation status; stage results follow, and final C11 execution
is recorded in C11_PROGRESS.md and the C11 section of this registry.

The [SDK scenario matrix](../java-sdk-test/JAVA_SDK_IT_SCENARIOS.md)
requires tests with each implementation commit and a full rerun after all changes. The
[SDK scenario matrix](../java-sdk-test/JAVA_SDK_IT_SCENARIOS.md) now contains
148 main groups and 73 detailed targets; section 16 assigns every main group to its first
responsible stage. These counts are test-design inventory, not executed coverage.

| Stage | HTTP evidence required | Status |
| --- | --- | --- |
| C01 | Uniform migration gate, 50105 preservation, capability/cleanup exceptions and no interception of old wire/internal migration | Pending |
| C02–C05 | Endpoint binding/default/null/state/source/publication contracts with real deployed Client/Admin/Console behavior | Pending |
| C06 | Five-key capability endpoint with actual identities, standard auth switch and no resource/liveness side effects | Pending |
| C07–C10 | Metadata interoperability, Watch identity, error/lifecycle regressions as each path becomes available | Pending |
| C11 | Full final matrix, Client/Admin/both Console deployments, default and applicable Jackson 3 contracts, directed fixtures | Pending |

Keep target operation coverage 0/8 until executed; the final check is not a substitute for
each stage's changed HTTP operation tests and registry updates. Raw JSON null assertions
must exercise the actual binding, not only JSON Schema or Java primitive setters.

### C01 migration admission implementation (2026-09-17)

New `AgentMigrationClientOpenApiITCase` uses an external historical-authority server with
`-Dnacos.agent.migration.gate=blocked`. It asserts Search/Discover/Publish/Register/Watch
50105 for both independent managed and unprojected names, no HTTP owner or publication
side effects, heartbeat 50404 for missing owners, and whole cleanup remaining available.
`A2aMigrationAdminApiOpenApiITCase` now observes internal convergence through management
while asserting native RAD rejection before cutover. `A2aUpgradeMigrationJavaSdkITCase`
retains old-wire mutation/layout assertions, checks native rejection, and installs RAD Watch
after terminal cutover. Runtime mirror inspection before cutover is management evidence,
not successful Client discovery coverage. `AiTransportResourceMatrixJavaSdkITCase` accepts an
explicit blocked fixture to verify other AI resources while native Agent publication is fenced.

Execution evidence is recorded in the SDK scenario and coverage records.
The overall C01–C11 target matrix remains pending until its required environments are executed.

### C02 per-Endpoint binding scenarios (stage validation passed)

| Contract | Required external evidence |
| --- | --- |
| Batch defaults and Endpoint overrides | HTTP and SDK: inherit independently; override runtime only with inclusive/exclusive default range; absent range resolves after override |
| Input cardinality / atomic failure | Empty, null-item or multiple bindings, invalid version/range and a later invalid Endpoint reject the entire replacement; original publication remains |
| Per-Endpoint versions | One batch with distinct effective versions; version-specific Discover and management snapshots preserve each binding |
| SDK lifecycle | Caller mutation does not affect cached bindings; remove two of three Endpoints and retain the third binding; final removal cleans publication |
| Query aggregation | Two publishers sharing one natural key produce multiple output bindings; multi-binding output cannot be registered as one input |

UT additionally covers deep-copy/redo and error mapping. Default and Jackson 3 SDK adapters are both required.
Stage evidence: 9 HTTP scenarios and 7 SDK cases per adapter (default/Jackson 3) passed; see ADAPTATION_VALIDATION.md for fixture corrections and environment limits. The final full matrix remains pending.

### C03 Endpoint enabled and derived state (stage validation passed)

| Surface | Scenarios | Stage evidence |
| --- | --- | --- |
| Client/Admin/Console runtime views | disabled contribution retained; heartbeat preserves enablement; explicit null for priority/weight/healthy/enabled returns 400 without replacement; no state property | Passed: merged and independent Console deployments |

Final adaptation matrix remains pending; see the staged validation report.

### C04 Source preferences (stage validation passed)

| Surface | Scenarios | Stage evidence |
| --- | --- | --- |
| Definition and discovery sources | Both definition orders; reject single/null/empty/duplicate/unknown orders before writes; explicit source selection, reversed filter order, empty Runtime without fallback; Watch selection and cancellation; Admin/Console/Maintainer and Artifact regression | Passed: 33 HTTP + 4 independent Console; 13 SDK and 4 Maintainer per JSON adapter. Auth-off stage; see ADAPTATION_VALIDATION.md for final auth-on/Disabled exclusions. |

Final A2A projection routing (G14-d) remains assigned to C07/C10. MODEL-D01 protocol-union behavior remains outside this change.

### C05 Client publication (stage validation passed)

| Surface | Scenarios | Stage evidence |
| --- | --- | --- |
| Agent publish | First Version forces ordinary submit; Admin-created draft remains a draft with false; complete replacement including protocol removal and basedOnVersion; later versions respect flag; non-draft no-op preserves digest/latest/governance, offline included; caller object unchanged | Passed on final C05 artifact with auth enabled: 17 HTTP/Console/Artifact, 6 independent Console, 14 SDK and 4 Maintainer per JSON adapter (59 total, zero skips). Earlier auth-off runs are archived separately. |
| Failure/concurrency | No server/client publication replay; Resource/Version transactional CAS; reject update after submit/delete and stale first-version detection; storage failure and unknown commit preserve verified active content | Passed: domain/transport UT and 12 real Derby transaction cases through both repository implementations; 1,214 related UT in total. Real READ-only/no-permission no-op denial, scope/owner preservation and explicit delete/recreate passed. Asynchronous Watch remains C09. |

### C07 public A2A Endpoint metadata

`AgentEndpointClientOpenApiITCase.testA2aPublicMetadataAndInvalidReplacementAreAtomic`
covers Client POST Endpoint metadata `__nacos.agent.endpoint.protocolVersion__` / `__nacos.agent.endpoint.tenant__`, Discover round trip,
empty tenant, malformed/null/oversize protocol values and forbidden internal control keys.
Rejected complete replacements must preserve the previously accepted payload. Effective public
values participate in revisions; Endpoint and Naming use the same reserved compatibility keys
(server mapper/migration UT). No new endpoint or authorization exception is introduced.

C07 execution: 74 external cases passed (11 SDK cases per JSON adapter, 34 HTTP cases,
18 independent-Console cases), with authentication enabled and no skips. See
ADAPTATION_VALIDATION.md for artifact hashes and component-test evidence. The legacy
SDK facade still uses the old wire; public A2A-to-RAD routing is validated in C10.

### A2A/RAD C09 shared visibility impact

No HTTP operation or request/response shape is added. The default visibility plugin now checks
its explicit authenticated identity and API scope during asynchronous Agent Watch eligibility.
`AgentWatchClientOpenApiITCase` retains timeout/change, namespace, generation, cancellation,
capacity and input-boundary coverage. The private granted-reader HTTP/GRPC cross-binding
regression is also exercised through public Java SDK IT. Directed cluster cases require
separate fixture evidence; no skip is considered passing coverage.

`AgentWatchClientOpenApiITCase#testVisibilityLossInvalidatesOnlyOpaqueIdWithoutChangingSharedFingerprint`
covers a two-item batch after PUBLIC-to-PRIVATE change: only the ungranted reader's affected
opaque id is invalidated, its subsequent Discover is 404, the granted reader sees the unchanged
fingerprint and continues waiting. Per-owner eligibility never changes shared content.

### A2A/RAD final regression evidence (C11)

The final artifact reruns the complete OpenAPI suite and the AI Console suite
against a separately deployed Console. `AgentConsoleApiOpenApiITCase` now checks
that unified Runtime endpoints expose enabled/healthy without the removed state.
`A2aMigrationAdminApiOpenApiITCase` sends an explicit ordinary Client identity to
ARD; admin credentials are used only for fixture/control operations. Historical
reconciliation, malformed-source recovery, QUIESCING and terminal cross-surface
assertions remain distinct from the normal canonical suite.

This changes assertions and execution evidence, not the documented API-surface
row denominator. See the SDK scenario and coverage records and
the applicable API/SDK scenario tables for actual result scopes and baseline exclusions; gated
migration cases in the ordinary suite are not treated as executed coverage.


C11 final product `9f5ab4d9f`: the complete auth-enabled OpenAPI suite runs 470
cases, with 459 passes, 11 conditional/baseline skips and no failures. The
separately deployed Console AI suite passes all 31 cases. Nine direct HTTP
auth-scope assertions verify Client/Admin/Console isolation; the independent
Console is restarted for each configuration because it has no main-server
file watcher. All auth switches are restored after testing. The eight target
Client operations now have executed evidence in the full and directed suites;
review-plugin/TLS component boundaries and unrelated baseline findings remain
explicit in the scenario records, rather than being counted as external successes.

### A2A/RAD review contract regression

Capability declares GET only. MVC supplies secured HEAD without a response body and OPTIONS with Allow but no capability data; POST/PUT/DELETE/PATCH return framework 405. The external IT asserts those boundaries and rejects anonymous HEAD. The resourceless identity parser belongs to the AI module. Endpoint metadata reuses `__nacos.agent.endpoint.protocolVersion__` and `__nacos.agent.endpoint.tenant__`; only these two reserved keys are writable, with all other control keys still rejected. Existing capability and Endpoint ITs cover the revised contract, including round trips and invalid input preserving the prior publication.

### A2A/RAD review 范围收敛

Client 发布契约仍为首版普通提交、已有 DRAFT 完整覆盖、非 DRAFT no-op；Admin/Console 保持原有流程。
本轮移除独立存储对象和 Agent 专用 CAS 加固，不新增跨存储或并发原子性保证；历史事务测试不计入当前覆盖。
公开 API 场景集合未增删，覆盖率分母不变；本轮执行证据及限制见
[既有存储边界](../../specs/zh-cn/ai/agent-storage-spec.md)。

### Discover 公共描述与标签（本轮范围）

仅扩展已有 Discover/Watch 响应，不增加 API surface，覆盖率分母不变。
`AgentDiscoveryClientOpenApiITCase#testDiscoverCurrentMetadataForOldVersionAndEmptyProjection`
覆盖当前 description/tags、精确旧版本与 latest、过滤后空调用接口、清空字段；断言
contentDigest、nativeDescriptor 与 Endpoint sourceRevision 不变，未透出展示及管理字段。
HTTP/gRPC/auto Watch 元数据变更及无变化通知边界由对应 Java SDK IT 验证。
公共响应与指纹契约见 [RAD 协议规范](../../specs/zh-cn/ai/rad-protocol-spec.md)。
