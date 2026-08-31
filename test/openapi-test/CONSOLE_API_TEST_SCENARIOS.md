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

# Console API IT Scenario Index

This document records which console API operations are covered by the
standalone-server IT classes under
`src/test/java/com/alibaba/nacos/test/consoleapi`.

Source API surface: console swagger at `https://nacos.io/swagger/console/zh/api.json`.
The branch-level coverage target is API scenario coverage: expected capability,
boundary/validation behavior, and controlled exception/error handling.

## Status Legend

| Status | Meaning |
| --- | --- |
| Covered | The current IT verifies the expected behavior and its important result shape. |
| Partial | The current IT verifies representative behavior, but important public API scenarios remain. |
| Pending | No IT currently verifies this public API scenario. |

## Authorization Metadata Coverage

The standalone OpenAPI IT profile does not enable Console API authorization.
Functional scenarios therefore remain unchanged for authorization-only fixes.
A focused Console module test verifies the corrected `@Secured` metadata for
Cluster nodes, Config listener/beta, A2A version list, AI force-publish, and
Copilot configuration endpoints. AgentSpec parser tests additionally verify
plural path recognition, namespace-range list semantics, and draft target
resolution from `agentSpecCard.name`.

## AI Resource Deletion Failure Coverage

The Agent, AgentSpec, Prompt, and Skill rows cover successful deletion and
post-delete absence. Storage-provider failure, multi-file partial failure,
persisted-provider routing, and deletion beyond one storage page are covered by
focused service tests because the standalone profile has no storage
fault-injection provider. The service tests verify that cleanup errors reach the
API layer and resource/version descriptors remain available for retry.

## Core, Health, Plugin, And Server

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `HealthConsoleApiOpenApiITCase` | `GET /v3/console/health/liveness`<br>`GET /v3/console/health/readiness` | Covered | Verifies health endpoints return wrapped success bodies with `ok`; validates the console port/base path contract. These APIs have no request parameters, so boundary coverage is limited to response contract shape. |
| `NamespaceConsoleApiOpenApiITCase` | `GET,PUT,POST,DELETE /v3/console/core/namespace`<br>`GET /v3/console/core/namespace/exist`<br>`GET /v3/console/core/namespace/list` | Covered | Creates, queries, updates, lists, checks existence, and deletes a namespace; validates missing required fields, invalid or overlong namespace IDs/names, duplicate create, and absent namespace behavior. |
| `ClusterConsoleApiOpenApiITCase` | `GET /v3/console/core/cluster/nodes` | Covered | Verifies standalone cluster node list shape, node identity fields, and wrapped response contract. The endpoint has no request parameters in the swagger surface. |
| `ServerStateConsoleApiOpenApiITCase` | `GET /v3/console/server/state`<br>`GET /v3/console/server/announcement`<br>`GET /v3/console/server/guide` | Covered | Verifies server state exposes expected state keys and announcement/guide endpoints return controlled wrapped data in the default standalone environment. These APIs do not mutate state. |
| `PluginConsoleApiOpenApiITCase` | `GET /v3/console/plugin`<br>`GET /v3/console/plugin/list`<br>`GET /v3/console/plugin/availability`<br>`GET /v3/console/plugin/config`<br>`PUT /v3/console/plugin/config`<br>`PUT /v3/console/plugin/status` | Partial | Verifies plugin list/detail/availability response shapes, including `typeCritical`, `executionMode`, `exclusive`, and the `configValueMetas` map shape; verifies the built-in `auth:nacos`, `auth:ldap`, and `auth:oidc` definitions, legacy aliases, effect modes, effective values, source metadata, and secret metadata/masking; verifies a local-only runtime update and empty-map source clear through refreshed detail metadata; verifies that critical disable, exclusive runtime-switch, and OIDC restart-only updates are rejected; covers controlled validation/not-found errors and rejection of config updates for non-configurable plugins. Persisted runtime mutation is not exercised to avoid carrying plugin state into later SDK suites. Anonymous AI access is not exercised by standalone OpenAPI IT; explicit credential presence, blank credential rejection, and HTTP 403 `ACCESS_DENIED` error mapping are covered by auth/core unit tests. Persisted full-map replacement/removal, key normalization, runtime/restart checks, same-source sensitive value preservation, persistence failure isolation, and retained-source apply failure/retry are covered by core and plugin unit tests. |

## Config

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `ConfigConsoleApiOpenApiITCase` | `GET,POST,DELETE /v3/console/cs/config` | Covered | Publishes, queries, updates, and deletes config; verifies content, md5, type, description, config tags, namespace/group defaults into current `public` storage, missing required fields, invalid type, and absent config behavior. Removed empty-tenant migration and dual-write behavior is outside the 3.3 console API contract. |
| `ConfigListConsoleApiOpenApiITCase` | `GET /v3/console/cs/config/list`<br>`GET /v3/console/cs/config/searchDetail` | Covered | Verifies list/search pagination shape, accurate and blur search behavior, dataId/group/content filters, empty pages, page validation, and required search parameters. |
| `ConfigListenerConsoleApiOpenApiITCase` | `GET /v3/console/cs/config/listener`<br>`GET /v3/console/cs/config/listener/ip` | Covered | Verifies listener status response shape for config and IP scoped queries, missing dataId/group validation, query type fields, and controlled empty listener state. |
| `ConfigHistoryConsoleApiOpenApiITCase` | `GET /v3/console/cs/history`<br>`GET /v3/console/cs/history/list`<br>`GET /v3/console/cs/history/previous`<br>`GET /v3/console/cs/history/configs` | Covered | Publishes versioned config changes and verifies history list/detail/previous/config snapshots, including storage IDs represented as JSON strings; validates missing identifiers, pagination, and absent history/config behavior. |
| `ConfigBetaConsoleApiOpenApiITCase` | `GET,DELETE /v3/console/cs/config/beta` | Covered | Publishes current gray-backed beta config through console headers, queries and deletes beta content, and verifies missing/absent beta responses stay wrapped and non-500. Removed `config_info_beta` old-table migration is not an expected scenario. |
| `ConfigBatchDeleteConsoleApiOpenApiITCase` | `DELETE /v3/console/cs/config/batchDelete` | Covered | Creates multiple configs, deletes them through the batch API, verifies absence, skips ids outside the requested namespace, and validates missing/empty IDs and malformed batch input. |
| `ConfigExportConsoleApiOpenApiITCase` | `GET /v3/console/cs/config/export2` | Covered | Exports existing config data, verifies file response and exported content, skips ids outside the requested namespace, and validates empty export, missing namespace/group filters, and controlled bad request cases. |
| `ConfigImportConsoleApiOpenApiITCase` | `POST /v3/console/cs/config/import` | Covered | Imports zipped config payloads, verifies persisted imported data, overwrite behavior, malformed archive handling, and import result structure for success and failure cases. |
| `ConfigCloneConsoleApiOpenApiITCase` | `POST /v3/console/cs/config/clone` | Covered | Clones config from source `namespaceId` to `targetNamespaceId`, verifies target content and metadata, covers source-scoped ID resolution, IDs outside the source namespace returning controlled `DATA_EMPTY`, and validates missing target namespace, empty selection, malformed clone payload, and absent source config behavior. |

## Naming

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `ServiceConsoleApiOpenApiITCase` | `GET,PUT,POST,DELETE /v3/console/ns/service`<br>`GET /v3/console/ns/service/list`<br>`GET /v3/console/ns/service/selector/types`<br>`GET /v3/console/ns/service/subscribers` | Covered | Creates, queries, updates, lists, and deletes services; verifies selector type list, empty subscriber page shape, namespace/group defaults, duplicate create, invalid service/group/page fields, and absent service errors. |
| `ServiceClusterConsoleApiOpenApiITCase` | `PUT /v3/console/ns/service/cluster` | Covered | Creates service cluster metadata, verifies cluster-specific service detail/list behavior, updates health checker/protect threshold style fields, and validates missing service/cluster fields plus absent service behavior. |
| `InstanceConsoleApiOpenApiITCase` | `PUT,DELETE /v3/console/ns/instance`<br>`GET /v3/console/ns/instance/list` | Covered | Registers setup service/instance, updates instance metadata/weight/enabled fields, lists instance state, deletes the instance, and validates missing IP/port/service, invalid port/weight, absent service, and controlled not-found behavior. |

## AI Registry And Copilot

MCP Version summaries and exact details expose optional `publishPipelineInfo`
so the Console can distinguish approved and rejected reviews. The standalone
profile has no MCP review Pipeline plugin, so the existing MCP row covers the
no-Pipeline response while focused component tests cover both terminal
Pipeline payloads and force-publish visibility.

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `A2aConsoleApiOpenApiITCase` | `GET,PUT,POST,DELETE /v3/console/ai/a2a`<br>`GET /v3/console/ai/a2a/list`<br>`GET /v3/console/ai/a2a/version/list` | Covered | Registers legacy and v1 AgentCards, verifies normalized fields and latest/version queries, updates a new version, lists by accurate/blur search, deletes resources, and validates missing names, bad search, invalid registration type, malformed JSON, incomplete endpoint definitions, and absent agents. Cross-contract scenarios verify legacy Console create through canonical Console Overview/Version reads, legacy Admin create through canonical Console reads across the 8848/8080 boundary, and canonical Console draft/force-publish through both legacy Console and Admin reads. |
| `AgentConsoleApiOpenApiITCase`<br>`AgentEndpointClientOpenApiITCase` | `GET,PUT,DELETE /v3/console/ai/agents`<br>`GET /v3/console/ai/agents/list`<br>`GET /v3/console/ai/agents/versions`<br>`GET /v3/console/ai/agents/version`<br>`GET /v3/console/ai/agents/runtime-endpoints`<br>`POST,PUT,DELETE /v3/console/ai/agents/draft`<br>`POST /v3/console/ai/agents/submit`<br>`POST /v3/console/ai/agents/publish`<br>`POST /v3/console/ai/agents/force-publish`<br>`POST /v3/console/ai/agents/redraft`<br>`POST /v3/console/ai/agents/online`<br>`POST /v3/console/ai/agents/offline`<br>`PUT /v3/console/ai/agents/labels` | Covered | Verifies the complete protocol-neutral Agent Console facade with form-encoded draft creation/update/deletion, metadata update, overview/list/version reads, submit, force publish, labels, online/offline, invalid publish/redraft transitions, and definition deletion. Confirms omitted namespace defaults to `public`, explicit Runtime namespace is retained, and `runtime-endpoints` wraps the unchanged Runtime snapshot with the server-composed Naming service reference. The Client Endpoint workflow cross-validates an Admin-published Agent through Console Overview and verifies that a real Client registration and deregistration appear in the Console Runtime snapshot and retain the expected Naming reference. Validates required identity/protocol, Version, order, pagination, malformed JSON, and absent resources. |
| `McpConsoleApiOpenApiITCase` | `GET,PUT,POST,DELETE /v3/console/ai/mcp`<br>`GET /v3/console/ai/mcp/list`<br>`GET /v3/console/ai/mcp/versions`<br>`GET /v3/console/ai/mcp/version`<br>`POST,PUT,DELETE /v3/console/ai/mcp/draft`<br>`POST /v3/console/ai/mcp/submit`<br>`POST /v3/console/ai/mcp/publish`<br>`POST /v3/console/ai/mcp/force-publish`<br>`POST /v3/console/ai/mcp/redraft`<br>`POST /v3/console/ai/mcp/online`<br>`POST /v3/console/ai/mcp/offline`<br>`PUT /v3/console/ai/mcp/labels`<br>`POST /v3/console/ai/mcp/import/validate` (deprecated)<br>`POST /v3/console/ai/mcp/import/execute` (deprecated) | Partial | Creates, queries, updates, lists, and deletes MCP servers; verifies generated ID, latest/allVersions, tool spec, accurate/blur list, duplicate conflict, missing identity/spec/version, invalid ID, malformed JSON, and absent server. For the standard lifecycle surface, verifies name-only identity, required exact version, rejection of nested `serverSpecification.id`, case-insensitive status validation, and the resource status/owner/scope/labels/working-pointer/online-count metadata returned for a newly created draft. Each route returns the controlled HTTP 409 gate while authority is `SYNCING`; if background reconciliation has already completed the one-way cutover, absent-target operations return controlled not-found and draft create/delete runs successfully. Full managed state transitions are covered by focused component tests because this scenario does not itself publish the permanent marker into the shared standalone process. Embedded and standalone Console use the local lifecycle handler, while remote Console forwards the same typed lifecycle contract through the Maintainer SDK transport. The two legacy import endpoints return HTTP 410 and `API_DEPRECATED` by default, may be reopened together with other gated v3 compatibility APIs through `nacos.core.api.compatibility.enabled=true`, and are planned for removal in 3.4.0; clients must migrate to `/v3/console/ai/import/*`. Console persists the optional `resourceSpecification` through the same lifecycle draft storage path. |
| `McpToolsImportConsoleApiOpenApiITCase` | `GET /v3/console/ai/mcp/importToolsFromMcp` | Partial | Verifies that private or local targets are rejected by default with an explicit private-allowlist message before network access, the endpoint parameter is required, and unsupported transport returns a wrapped failure. Public-target protocol success, operator-approved private-target success, and optional `authToken` header forwarding require an external MCP runtime plus controlled server configuration and remain an end-to-end gap. Focused Console tests cover the operator switch, public-target policy, exact IP, IPv4/IPv6 CIDR and non-byte-aligned prefix matching, rejection when any DNS result is an unapproved private address, invalid configuration, non-HTTP URLs, unresolvable hosts, and endpoint-origin override. |
| `PromptConsoleApiOpenApiITCase` | `DELETE /v3/console/ai/prompt`<br>`GET /v3/console/ai/prompt/list`<br>`GET /v3/console/ai/prompt/versions`<br>`GET /v3/console/ai/prompt/governance`<br>`GET /v3/console/ai/prompt/version`<br>`GET /v3/console/ai/prompt/version/download`<br>`POST,PUT,DELETE /v3/console/ai/prompt/draft`<br>`POST /v3/console/ai/prompt/submit`<br>`POST /v3/console/ai/prompt/publish`<br>`POST /v3/console/ai/prompt/force-publish`<br>`POST /v3/console/ai/prompt/redraft`<br>`POST /v3/console/ai/prompt/online`<br>`POST /v3/console/ai/prompt/offline`<br>`PUT /v3/console/ai/prompt/labels`<br>`PUT /v3/console/ai/prompt/description`<br>`PUT /v3/console/ai/prompt/biz-tags` | Partial | Verifies prompt draft/update/delete, submit, reviewing-state repeat-submit idempotency, force publish, version detail, governance metadata, version list, list filters, Markdown download, labels, server-managed latest label preservation, publish-parameter compatibility, description/bizTags, online/offline latest maintenance, delete, and absent resource/version errors. Validates missing promptKey/template/version/labels/description, invalid search, publish/redraft state errors, and controlled non-500 failures. Runtime-only legacy prompt endpoints are intentionally not covered because they are not exposed by the console controller. The auth-disabled standalone profile cannot switch identities, so owner/scope/grant list filtering and unreadable-as-not-found behavior remain covered by focused service tests rather than end-to-end IT; auth-enabled caller-identity propagation through an independent Console remains an end-to-end gap. |
| `SkillConsoleApiOpenApiITCase` | `GET,DELETE /v3/console/ai/skills`<br>`GET /v3/console/ai/skills/list`<br>`GET /v3/console/ai/skills/version`<br>`GET /v3/console/ai/skills/version/download`<br>`POST,PUT,DELETE /v3/console/ai/skills/draft`<br>`POST /v3/console/ai/skills/submit`<br>`POST /v3/console/ai/skills/publish`<br>`POST /v3/console/ai/skills/force-publish`<br>`POST /v3/console/ai/skills/redraft`<br>`POST /v3/console/ai/skills/online`<br>`POST /v3/console/ai/skills/offline`<br>`PUT /v3/console/ai/skills/labels`<br>`PUT /v3/console/ai/skills/biz-tags`<br>`PUT /v3/console/ai/skills/scope` | Covered | Verifies skill draft/update/fork/delete, submit, reviewing-state repeat-submit idempotency, force publish, detail, version detail, list filters, ZIP download, labels, server-managed latest label preservation, publish-parameter compatibility, bizTags, PUBLIC/PRIVATE scope, version-level and skill-level online/offline latest maintenance, delete, and absent resource/version errors. Validates missing skillName/skillCard/targetVersion/version/labels/scope, name mismatch, invalid version/search/scope/page, and invalid lifecycle transitions. |
| `SkillUploadConsoleApiOpenApiITCase` | `POST /v3/console/ai/skills/upload`<br>`POST /v3/console/ai/skills/upload/precheck`<br>`POST /v3/console/ai/skills/upload/batch` | Covered | Verifies single and batch Skill ZIP upload, ZIP-and-namespace-only server-side precheck, owner, maximum published version (online or offline), predicted target version, single-code reporting, archive entry paths, distinct `NOT_A_SKILL`/`INVALID_SKILL` results, legacy batch `succeeded`/`failed` fields, and per-item `success`, `errorCode`, and `errorMessage` in `results`; validates overwrite behavior, next version generation, version normalization/fallback, upload-time first-available version-source selection when a higher-priority candidate is occupied, partial batch results, empty/malformed ZIP, and upload error envelopes. Permission-denied owner and error-code reporting is covered by the service test because the default standalone IT environment does not switch authenticated users. |
| `AgentSpecConsoleApiOpenApiITCase` | `GET,DELETE /v3/console/ai/agentspecs`<br>`GET /v3/console/ai/agentspecs/list`<br>`GET /v3/console/ai/agentspecs/version`<br>`POST,PUT,DELETE /v3/console/ai/agentspecs/draft`<br>`POST /v3/console/ai/agentspecs/submit`<br>`POST /v3/console/ai/agentspecs/publish`<br>`POST /v3/console/ai/agentspecs/force-publish`<br>`POST /v3/console/ai/agentspecs/redraft`<br>`POST /v3/console/ai/agentspecs/online`<br>`POST /v3/console/ai/agentspecs/offline`<br>`PUT /v3/console/ai/agentspecs/labels`<br>`PUT /v3/console/ai/agentspecs/biz-tags`<br>`PUT /v3/console/ai/agentspecs/scope` | Covered | Verifies AgentSpec draft/update/auto-create/fork/delete, submit, reviewing-state repeat-submit idempotency, force publish, detail, version detail, list filters, labels, server-managed latest label preservation, publish-parameter compatibility, bizTags, scope, version-level and resource-level online/offline latest maintenance, delete, and absent resource/version errors. Validates missing agentSpecName/agentSpecCard/targetVersion/version/labels/scope, invalid version/search/scope/page, and invalid lifecycle transitions. The shared `bizTag` filter is accepted but not applied by the AgentSpec service; the IT records that behavior. |
| `AgentSpecUploadConsoleApiOpenApiITCase` | `POST /v3/console/ai/agentspecs/upload` | Covered | Verifies single AgentSpec ZIP upload from `manifest.json` plus resources, overwrite of an editing draft, next draft version after publish, seed archives importing multiple AgentSpecs, empty file, malformed ZIP, and missing manifest errors. |
| `AiResourceImportConsoleApiOpenApiITCase` | `GET /v3/console/ai/import/sources`<br>`POST /v3/console/ai/import/search`<br>`POST /v3/console/ai/import/validate`<br>`POST /v3/console/ai/import/execute` | Covered | Verifies enabled managed importer plugins as the source list, resourceType filters, sanitized source info, unsupported resource type empty result, missing resourceType/sourceId/selectedItems, malformed JSON options/selectedItems, empty selected items, unknown source not-found, unsupported source/resourceType combinations, and controlled error bodies without performing external network import. |
| `PipelineConsoleApiOpenApiITCase` | `GET /v3/console/ai/pipelines`<br>`GET /v3/console/ai/pipelines/list`<br>`GET /v3/console/ai/pipelines/detail`<br>`GET /v3/console/ai/pipelines/{pipelineId}` | Partial | Verifies the current list/detail contracts for resourceType/resourceName/namespaceId/version filters, required resourceType, page validation, required pipelineId, and absent pipeline not-found errors. The deprecated base-path list and path-variable detail endpoints return HTTP 410 and `API_DEPRECATED` by default and may be temporarily reopened with `nacos.core.api.compatibility.enabled=true`. Successful detail creation is not covered in the default standalone IT environment because pipeline rows require configured publish-pipeline plugins. |
| `CopilotConsoleApiOpenApiITCase` | `GET,POST /v3/console/copilot/config`<br>`POST /v3/console/copilot/skill/optimize`<br>`POST /v3/console/copilot/skill/generate`<br>`POST /v3/console/copilot/prompt/optimize`<br>`POST /v3/console/copilot/prompt/debug` | Partial | Verifies config save/read for API key, model, studio URL, and studio project; records that non-editable config fields are accepted but ignored by the save path. Verifies malformed JSON config error and SSE validation error events for empty bodies and missing required skill/background/prompt/userInput fields without invoking an external LLM provider. |

## Validation Snapshot

The console API IT set was validated with:

- `mvn -pl test/openapi-test spotless:apply`
- `mvn -pl test/openapi-test spotless:check`
- `mvn -pl test/openapi-test -DskipTests test-compile`
- `mvn -pl test/openapi-test -Pintegration-test -DskipTests=false -Dit.test='*ConsoleApiOpenApiITCase' verify`
- `mvn -pl test/openapi-test -Pintegration-test -DskipTests=false -Dit.test=AgentConsoleApiOpenApiITCase verify`

The full console IT verification ran 75 tests with no failures.
The Agent Console API verification ran 2 tests with no failures.
