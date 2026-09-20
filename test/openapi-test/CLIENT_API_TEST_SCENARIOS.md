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

# Client API IT Scenario Index

This document records which client OpenAPI operations are covered by the
standalone-server IT classes under
`src/test/java/com/alibaba/nacos/test/openapi/client`.

Source API surface: Nacos client OpenAPI swagger and production controllers
for `/v3/client/**`. The branch-level coverage target is API scenario coverage:
expected capability, boundary/validation behavior, and controlled
exception/error handling.

## Status Legend

| Status | Meaning |
| --- | --- |
| Covered | The current IT verifies the expected behavior and its important result shape. |
| Partial | The current IT verifies representative behavior, but important public API scenarios remain. |
| Pending | No IT currently verifies this public API scenario. |

## Authorization Metadata Coverage

The unified default-auth functional workflow executes every Client API functional
scenario as the restricted `ClientReadWrite` identity. Management-created
private AI fixtures receive resource-exact read visibility grants; fixtures
whose owner semantics are part of the scenario are published by the client
identity. `AuthScopeGuardITCase` separately verifies anonymous, invalid,
read-only, and authenticated-no-permission behavior on stable representative
Client APIs. Focused Auth and AI module tests continue to verify that the
AgentSpec detail endpoint keeps its `OPEN_API`/`AI` metadata and resolves the
authorization resource from the client `name` parameter.

## Config

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `ConfigOpenApiITCase` | `GET /v3/client/cs/config` | Covered | Queries config published by admin API with content, md5, lastModified, contentType, and current gray-backed beta fields; verifies public namespace defaulting, wrong namespace not-found, required `dataId`/`groupName`, legacy `group` rejection, invalid namespace, directory-control identity segment rejection with HTTP 400, and controlled not-found/error bodies. Removed pre-3.0 namespace or beta/tag storage migration is outside the 3.3 client API contract. |

## Naming

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `InstanceRegisterOpenApiITCase` | `POST /v3/client/ns/instance` | Covered | Registers instances and verifies visibility through list; covers namespace/group/cluster/healthy/weight/enabled defaults, explicit group/cluster behavior, required service/ip/port validation, invalid weight/cluster, and duplicate or service-state errors. |
| `InstanceListOpenApiITCase` | `GET /v3/client/ns/instance/list` | Covered | Lists enabled registered instances with metadata and health fields; covers namespace/group/cluster defaults, healthy-only and enabled filtering, empty-result behavior, required `serviceName`, malformed or unknown parameters, and not-found style results. |
| `InstanceDeregisterOpenApiITCase` | `DELETE /v3/client/ns/instance` | Covered | Deregisters an existing instance and verifies absence from list; covers default and explicit group/cluster values, idempotent missing-instance behavior, required service/ip/port validation, and malformed port handling. |

## AI Registry

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `PromptClientOpenApiITCase` | `GET /v3/client/ai/prompt` | Partial | Queries an exactly granted online prompt as the restricted Client identity by latest, explicit version, and label; verifies namespace defaulting, version-over-label priority, md5 conditional HTTP 304, missing promptKey/version resolution, absent prompt, unknown version, and offline/not-online errors. Direct cross-identity unreadable-as-not-found and grant-revocation convergence remain covered by focused service tests until the Stage 3 authorization matrix is merged. |
| `SkillClientOpenApiITCase` | `GET /v3/client/ai/skills` | Covered | Downloads online skills as ZIP by latest, version, and label with resource entries; covers namespace defaulting, version-over-label priority, missing skillName, absent skill, unknown version/label, and controlled not-found JSON for download failures. |
| `AgentSpecClientOpenApiITCase` | `GET /v3/client/ai/agentspecs` | Covered | Queries online AgentSpecs by latest, version, and label with manifest/resource content; covers namespace defaulting, label/version resolution, missing name, absent AgentSpec, unknown version, and controlled not-found errors. |
| `AgentSpecSearchClientOpenApiITCase` | `GET /v3/client/ai/agentspecs/search` | Covered | Searches shared-index AgentSpec projections with online versions, literal keyword filtering, and `tagsAll`; covers eventual index convergence, optional keyword, namespace defaulting, page defaults and validation, empty page success, and invalid pagination errors. |
| `AiCapabilitiesClientOpenApiITCase` | `GET /v3/client/ai/capabilities` | Covered | Five HTTP features; four real identity roles; denied anonymous/invalid/expired/replaced credentials; resource-free parsing and continued business authorization; no Client creation or Endpoint lease renewal; secured 405 method errors. C06 also executes Client/Admin/Console auth-switch isolation and records directed dual-JSON/old-server component probes separately. |
| `AiResourceSearchClientOpenApiITCase` | `GET /v3/client/ai/resources/search`<br>`GET /v3/client/ai/skills/search`<br>`GET /v3/client/ai/prompt/search`<br>`GET /v3/client/ai/mcp/search` | Partial | Validation, empty-result, numbered-page, basic projection, and resource-specific scenarios remain active. The compound private-resource cross-type/facade/filter/cursor scenario is retained but disabled as `DAUTH-F03` because the rolled-back product implementation removes private projections from the shared index. Restore it after the canonical background projection fix and visibility non-leakage tests pass. |
| `AgentDiscoveryClientOpenApiITCase` | `GET /v3/client/ai/agents/search`<br>`GET /v3/client/ai/agents` | Covered | Publishes Agents through management helpers with exact Client visibility, and publishes the special-name search set directly as the Client owner, then verifies RAD Search and Discover projections. Search covers the `AUTO/INDEX/SCAN`-compatible eventual contract: `AUTO` and `INDEX` return a successful current snapshot without readiness 503 while convergence polling establishes the complete catalog; it also covers case-sensitive literal name filtering including `%`, `_`, and `\`, `tagsAll`/`protocolsAny` composition, stable ASCII numbered pagination including an out-of-range page, complete multi-Version catalogs, latest/offline convergence, and the invariant that Runtime Endpoint writes do not change Search. In the two-Version rollout workflow, independent HTTP publishers keep Version 1 and Version 2 Endpoints concurrently: an omitted selector returns latest metadata plus all online-Version-compatible Endpoints and binding provenance, while explicit `label=latest` returns only Version 2 Endpoints; exact Version 1 remains isolated, and taking Version 1 offline removes its Endpoint only from the default pool. Also covers default namespace, typed empty protocol results, empty search, pagination validation, mutually exclusive version/label, missing identity, and absent Agent errors. |
| `AgentWatchClientOpenApiITCase` | `POST /v3/client/ai/agents/watch` | Covered | Verifies request-scoped HTTP Batch Long Poll with immediate opaque invalidation for changed fingerprints, bounded unchanged timeout, multi-intent batches, custom namespaces, Runtime Endpoint wake-up, and Discover materialization of the complete current snapshot. It covers required stateful headers, generation and timeout ranges, empty/malformed/oversized lists, duplicate ids, mixed namespaces, malformed fingerprints, per-client soft growth, whole-generation replacement, per-request item/byte hard limits, controlled node waiter-capacity rejection, capacity reuse after client cancellation, and same-client generation cleanup. A two-node run verifies that self-describing generations remain correct when consecutive requests reach different servers: either node can observe the mutation, the peer generation quickly reports the opaque id, both Discover results converge to the same fingerprint, and the next current-fingerprint generation times out unchanged. Responses are checked not to expose Agent identity, fingerprints, descriptors, Endpoints, or per-item authorization/error details. |
| `AgentPublishClientOpenApiITCase` | `POST /v3/client/ai/agents` | Partial | C05: first-Version submit, Admin draft replacement, later autoSubmit, non-draft/no-op including offline, direct/copy inputs, raw Form validation and cross-surface projection. C05 auth-off and final auth-on stage tests passed; final routing/migration environments remain assigned to C10/C11. |
| `AgentEndpointClientOpenApiITCase` | `POST,DELETE /v3/client/ai/agents/endpoints`<br>`PUT /v3/client/ai/agents/endpoints/heartbeat` | Covered | Verifies Form-based complete HTTP Publisher replacement, visibility through Discover, idempotent registration/deregistration, empty Runtime Endpoint projection after deregistration, liveness intervals, heartbeat, and `HTTP_CLIENT_NOT_FOUND (50404)` before registration and after deregistration. Cross-validates the same workflow from Admin creation and Overview through Console Overview, then checks the populated and post-deregistration empty Runtime snapshots on both management surfaces, including lossless `HTTP+JSON` transport, endpoint payload, Version binding, enablement, health, state, and Console Naming reference. Confirms that a query with the same Client id does not create a Publisher, and covers required headers, Client-id syntax, complete-batch validation, malformed `endpoints` JSON Form-field handling, the configured Server soft watermark (reduced to 3 in `it-new.yml`), whole-batch admission from below to above the watermark, equal-size replacement above it, atomic rejection of further growth with `AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT`, and capacity reuse after deregistration. |
| `McpPublishClientOpenApiITCase`, `McpEndpointClientOpenApiITCase` | `GET,POST /v3/client/ai/mcp`<br>`POST,DELETE /v3/client/ai/mcp/endpoints`<br>`PUT /v3/client/ai/mcp/endpoints/heartbeat` | Covered | Runs against stable `LIFECYCLE_MANAGED` state and verifies latest/exact MCP query; omitted and explicit-false direct-online release plus `createDraft=true`; Tool, Resource, and auto-REF Form JSON fields; duplicate, malformed, and missing errors; Runtime Endpoint register/query/idempotent-register/deregister; stable Agent/MCP shared HTTP Client identity; heartbeat renewal while either module still owns a publication; `HTTP_CLIENT_NOT_FOUND` before creation and after the final publication is removed; and required header, identity, namespace, address, port, missing-target, and non-REF error envelopes. `McpMigrationAdminApiOpenApiITCase` owns the pre-cutover client draft gate. Shared-client expiration and replay are exercised through the Java SDK directed-restart scenario because they require a long-lived client process across server replacement. AiResourceVisibilityOpenApiITCase verifies default-public Client release, non-owner READ, private scope and grant/revoke, without bypassing WRITE or request authorization. |


### Agent 元数据模型合并（2026-09-14）

Agent Search HTTP 响应统一为 AgentSummary 的 versionInfo.labels/onlineVersions；验证旧顶层 latestVersion/versions 和管理字段不再出现。

### Agent 地址模型统一：实施与验收（2026-09-15）

CallInterface → EndpointSet → Endpoint 统一已落地，验收要求见 [测试矩阵](../../Codex/design/nacos-3.3-client-ai-api/MODEL_ENDPOINT_TEST_PLAN.md)，本轮实际执行见 [验证记录](../../Codex/design/nacos-3.3-client-ai-api/MODEL_ENDPOINT_VALIDATION.md)。healthy 注册可写，服务端维护字段忽略；管理 Runtime 读取改为 `callInterface.endpointSets[].endpoints[]`，状态和绑定位于 Endpoint，观察时间位于 Set。旧 A2A wire 不变。以下原有覆盖状态不以编译通过或历史测试数量自动提升。

### 统一地址模型新增场景（2026-09-15）

| 场景 | 用例 | 断言 |
| --- | --- | --- |
| EP-03/07：HTTP 注册上报健康值及维护字段隔离 | `AgentEndpointClientOpenApiITCase.testReportedHealthAndIgnoredManagementFieldsAcrossReadSurfaces` | false 注册及 ACTIVE heartbeat 后仍 false；替换为 true 可发现；bindings 逐字段继承 Batch；enabled/state 暂保持服务端维护；Admin 三层非空读取及 RAD 字段隔离 |
| EP-12：公开 Agent 实际索引与目录 | `AiResourceSearchClientOpenApiITCase.testPublicAgentIndexTracksUnifiedVersionCatalog` | 旧 A2A 创建 PUBLIC Agent，新 Agent 发布第二版；tag/协议/namespace、onlineVersions/labels、offline 与 delete 收敛；默认 AUTO/显式 INDEX 均走共享索引 |

原私有 Search DAUTH-F03 Disabled 保持；公开 fixture 通过旧 A2A 的既有 PUBLIC 语义准备，不关闭鉴权，不写内部存储。

### 2026-09-15 请求整合回归

Agent HTTP Search/Register 的 namespace 参数保持；服务端业务模型与 namespace 分离，直接 HTTP 的默认值、自定义 namespace、非法参数、授权隔离及完整替换/全量 DELETE 仍由现有 Agent IT 验证。局部注销三个参数仅为 Java SDK API，不能向 HTTP DELETE 发送 Endpoint 列表。

本轮实际执行状态见 [请求整合验证记录](../../Codex/design/nacos-3.3-client-ai-api/MODEL_REQUEST_VALIDATION.md)。
既有 Covered/Partial/Pending 表示场景覆盖归属，不表示本轮已重新执行；不能引用前轮结果代替本轮验收。

## Agent JSON 注解移除（2026-09-16）

JSON-01/03/04：Endpoint 缺省 0/1/true/true，false/0 和最大 priority 往返，伪造 enabled/state/bindings 不覆盖服务端状态；Search 可选管理字段允许 null，仍不返回非空管理事实。

[本轮测试矩阵](../../Codex/design/nacos-3.3-client-ai-api/MODEL_JSON_TEST_MATRIX.md)区分待执行项与实际结果。

## Planned Agent compatibility contract (2026-09-16; not implemented)

The [SDK scenario matrix](../java-sdk-test/JAVA_SDK_IT_SCENARIOS.md)
defines H01–H08 and C01–C22. These are separate future-target rows, not new claims about
deployed endpoints or changes to the historical coverage totals above.

| API surface / IT class | Covered API operations | Current status | Current/missing coverage |
| --- | --- | --- | --- |
| AiCapabilitiesClientOpenApiITCase (proposed) | GET /v3/client/ai/capabilities (proposed) | Pending | Declare radV1/mcp/skill/prompt/agentSpec HTTP capabilities; follow standard Client auth with ONLY_IDENTITY and the global switch (O6 agreed). When identity validation applies, valid users without grants succeed and invalid/missing credentials fail. No ALLOW_ANONYMOUS, resource reads or lease renewal. |
| Existing Agent Client discovery tests, target revision | GET /v3/client/ai/agents and /search | Pending | Public A2A metadata, Card projection prerequisites, field/visibility/index regressions; retain the current baseline separately. |
| AgentPublishClientOpenApiITCase, target revision | POST /v3/client/ai/agents | Pending | Auto-submit only a newly created first Version; existing drafts follow autoSubmit and fully replace definitions; non-draft no-op; failures propagate without retry/recreation or recovery into success. Admin/Console draft creation remains unchanged. |
| AgentEndpointClientOpenApiITCase, target revision | POST/DELETE /v3/client/ai/agents/endpoints and PUT /endpoints/heartbeat | Pending | O1/O2 are agreed: field-level defaults, writable enabled, state removal and one binding per Endpoint; A2A reference removal preserves declared ranges. Verify full replacement, metadata, ownership and auth against the target schema. |
| AgentWatchClientOpenApiITCase, target revision | POST /v3/client/ai/agents/watch | Pending | Metadata-only changes, identity through async work, cancellation, no unchanged callbacks; DAUTH-F05 stays explicit. |

Future-target HTTP coverage is reported by operation: 8 operations, all Pending,
strict/effective 0/8=0%. Existing aggregate coverage uses API-surface rows and is unchanged;
do not mix these two denominators. Use an auth-enabled real server for identity claims,
the normal Client port, and independent integrated/standalone Console regression fixtures.

O1 HTTP acceptance follows matrix §7.1 (E17/E18/E24). Send raw forms whose Endpoint
fields require server-side Batch inheritance; do not pre-expand every request in helpers.
Cover range defaulting after inheritance, open/closed boundaries, invalid-batch atomicity,
enabled=false retained in management but excluded from discovery, re-enable, heartbeats,
Naming overrides and multiple publishers. Assert state is absent from target response JSON
and both Console deployments return enabled/healthy for frontend-derived labels. Historical
ignored enabled/bindings/state tests remain baseline evidence only; this target is Pending.

Capability matrix §10.1 adds five-resource declarations and eight detailed acceptance
cases without adding an HTTP operation. Verify real zero-permission identity and an empty
registry; use parser UT/controlled responses for per-key false/missing/invalid values.
Do not infer gRPC reachability or business authority, or require the new capability call
before every existing resource operation on older servers. All cases remain Pending.

O3 HTTP acceptance follows matrix §6.1. Direct Client requests must verify sole existing
drafts obey false/true, failed first submissions are not automatically resumed, and full
definition replacement removes omitted protocols while preserving governance fields.
Errors propagate even if a write has already taken effect; subsequent explicit calls
are separate operations, not internal retries. Cover server conflicts and deletions
without automatic recreation; use UTs/directed external fixtures for precise failure
timing. Admin and both Console deployments retain explicit submission. Targets are Pending.

O4 HTTP acceptance follows matrix §5.1 (eight detailed targets). Definition writes
must reject single-source orders and accept both full permutations; query filters
may still select only Runtime or Declared. Assert requested source selection under
the opposite default preference, empty Sets, Discover/Watch parity and retained
identity/visibility rules. Regress Admin/Maintainer and both Console deployments,
with frontend selectors/detail hints, storage schemas and Artifact fixtures updated
together. These targets remain Pending and do not add HTTP operations.

O6 is agreed: capabilities uses ordinary Client auth, including auth-off and plugin
enablement branches; no endpoint-specific forced authentication or plugin failure rules.
Update C08/C09 assertions accordingly and do not count bypassed validation as authenticated
coverage. O5 is agreed: new RAD instances may be blocked by explicit migration-unready errors.
Migration projections, same-name historical writes and native Runtime publication need
separate assertions; the current definition guard is not a blanket guarantee for RAD.
Verify 50105 detail preservation, rejection before business mutation, no misleading empty
results for migration unavailability. The agreed uniform gate also rejects unrelated
standard resources; normal existence/domain checks resume on fresh/terminal CANONICAL.
Capabilities and existing-intent cleanup remain available. Preserve auth ordering and compare HTTP/gRPC
semantics. Refer to routing design §6 and matrix M04/M05/M09/M11.

O5 must also cover clients upgraded before the server, capability-cache refresh and
mixed old/new HTTP backends during rollout. A successful capability response from one
new node does not establish readiness of every backend or historical resource. Capability
responses remain current even when an SDK instance retains legacy A2A mode. That retention,
reconnection/redo/polling and re-instantiation fixture belongs to the Java SDK matrix; no
automatic handover is planned for legacy instances. Newly instantiated clients first using
RAD during migration receive explicit errors for all business requests while that node
retains historical authority; capabilities still reports RAD support. After cutover, explicit
subsequent requests succeed. Server-first upgrade ordering is not a compatibility prerequisite.

## Staged adaptation validation (2026-09-17)

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
| Failure/concurrency | 复用原有草稿写入和状态校验；Client 发布失败直接传播，不补读恢复成功、不自动重放 | 通用存储/CAS/并发原子性加固已撤出；此前 12 项事务测试证据失效。保留权限、状态、失败传播、显式删除重建验证；本轮结果见范围收敛记录。 |

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

### A2A/RAD C09 visibility and Watch regression

The HTTP Watch binding keeps its existing request and response contract. Re-run
`AgentWatchClientOpenApiITCase` for opaque changed ids, timeouts, complete generation
replacement, namespace isolation, runtime-change wakeup, malformed input, capacity and
cancellation. PRIVATE Agent reads by a granted non-owner are additionally compared through
native HTTP and gRPC Java SDK subscriptions, including complete replacement and no duplicate
callback on unchanged content. Public-to-private scope changes must invalidate an ungranted
reader without returning its cached result. Cross-node generations remain a directed fixture
and are not covered merely by the standalone slice.

`AgentWatchClientOpenApiITCase#testVisibilityLossInvalidatesOnlyOpaqueIdWithoutChangingSharedFingerprint`
covers a two-item batch after PUBLIC-to-PRIVATE change: only the ungranted reader's affected
opaque id is invalidated, its subsequent Discover is 404, the granted reader sees the unchanged
fingerprint and continues waiting. Per-owner eligibility never changes shared content.

### A2A/RAD review contract regression

Capability declares GET only. MVC supplies secured HEAD without a response body and OPTIONS with Allow but no capability data; POST/PUT/DELETE/PATCH return framework 405. The external IT asserts those boundaries and rejects anonymous HEAD. The resourceless identity parser belongs to the AI module. Endpoint metadata reuses `__nacos.agent.endpoint.protocolVersion__` and `__nacos.agent.endpoint.tenant__`; only these two reserved keys are writable, with all other control keys still rejected. Existing capability and Endpoint ITs cover the revised contract, including round trips and invalid input preserving the prior publication.

### Discover 公共描述与标签（本轮范围）

仅扩展已有 Discover/Watch 响应，不增加 API surface，覆盖率分母不变。
`AgentDiscoveryClientOpenApiITCase#testDiscoverCurrentMetadataForOldVersionAndEmptyProjection`
覆盖当前 description/tags、精确旧版本与 latest、过滤后空调用接口、清空字段；断言
contentDigest、nativeDescriptor 与 Endpoint sourceRevision 不变，未透出展示及管理字段。
HTTP/gRPC/auto Watch 元数据变更及无变化通知边界由对应 Java SDK IT 验证。
公共响应与指纹契约见 [RAD 协议规范](../../specs/zh-cn/ai/rad-protocol-spec.md)。
