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
| `ConfigHistoryConsoleApiOpenApiITCase` | `GET /v3/console/cs/history`<br>`GET /v3/console/cs/history/list`<br>`GET /v3/console/cs/history/previous`<br>`GET /v3/console/cs/history/configs` | Covered | Publishes versioned config changes and verifies history list/detail/previous/config snapshots; validates missing identifiers, pagination, and absent history/config behavior. |
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

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `A2aConsoleApiOpenApiITCase` | `GET,PUT,POST,DELETE /v3/console/ai/a2a`<br>`GET /v3/console/ai/a2a/list`<br>`GET /v3/console/ai/a2a/version/list` | Covered | Registers legacy and v1 AgentCards, verifies normalized fields and latest/version queries, updates a new version, lists by accurate/blur search, deletes resources, and validates missing names, bad search, invalid registration type, malformed JSON, incomplete endpoint definitions, and absent agents. |
| `AgentConsoleApiOpenApiITCase` | `GET,PUT,DELETE /v3/console/ai/agents`<br>`GET /v3/console/ai/agents/list`<br>`GET /v3/console/ai/agents/versions`<br>`GET /v3/console/ai/agents/version`<br>`GET /v3/console/ai/agents/runtime-endpoints`<br>`POST,PUT,DELETE /v3/console/ai/agents/draft`<br>`POST /v3/console/ai/agents/submit`<br>`POST /v3/console/ai/agents/publish`<br>`POST /v3/console/ai/agents/force-publish`<br>`POST /v3/console/ai/agents/redraft`<br>`POST /v3/console/ai/agents/online`<br>`POST /v3/console/ai/agents/offline`<br>`PUT /v3/console/ai/agents/labels` | Covered | Verifies the complete protocol-neutral Agent Console facade with form-encoded draft creation/update/deletion, metadata update, overview/list/version reads, submit, force publish, labels, online/offline, invalid publish/redraft transitions, and definition deletion. Confirms omitted namespace defaults to `public`, explicit Runtime namespace is retained, and `runtime-endpoints` wraps the unchanged Runtime snapshot with the server-composed Naming service reference. Validates required identity/protocol, Version, order, pagination, malformed JSON, and absent resources. |
| `McpConsoleApiOpenApiITCase` | `GET,PUT,POST,DELETE /v3/console/ai/mcp`<br>`GET /v3/console/ai/mcp/list`<br>`GET /v3/console/ai/mcp/importToolsFromMcp`<br>`POST /v3/console/ai/mcp/import/validate`<br>`POST /v3/console/ai/mcp/import/execute` | Covered | Creates, queries, updates, lists, and deletes MCP servers; verifies generated ID, latest/allVersions, tool spec, accurate/blur list, duplicate conflict, missing identity/spec/version, invalid ID, malformed JSON, absent server, unsupported tool import transport, and import request validation. Console currently accepts `resourceSpecification` but does not persist it because the controller does not parse resources; the IT records that observable behavior. |
| `PromptConsoleApiOpenApiITCase` | `DELETE /v3/console/ai/prompt`<br>`GET /v3/console/ai/prompt/list`<br>`GET /v3/console/ai/prompt/versions`<br>`GET /v3/console/ai/prompt/governance`<br>`GET /v3/console/ai/prompt/version`<br>`GET /v3/console/ai/prompt/version/download`<br>`POST,PUT,DELETE /v3/console/ai/prompt/draft`<br>`POST /v3/console/ai/prompt/submit`<br>`POST /v3/console/ai/prompt/publish`<br>`POST /v3/console/ai/prompt/force-publish`<br>`POST /v3/console/ai/prompt/redraft`<br>`POST /v3/console/ai/prompt/online`<br>`POST /v3/console/ai/prompt/offline`<br>`PUT /v3/console/ai/prompt/labels`<br>`PUT /v3/console/ai/prompt/description`<br>`PUT /v3/console/ai/prompt/biz-tags` | Covered | Verifies prompt draft/update/delete, submit, force publish, version detail, governance metadata, version list, list filters, Markdown download, labels, server-managed latest label preservation, publish-parameter compatibility, description/bizTags, online/offline latest maintenance, delete, and absent resource/version errors. Validates missing promptKey/template/version/labels/description, invalid search, publish/redraft state errors, and controlled non-500 failures. Runtime-only legacy prompt endpoints are intentionally not covered because they are not exposed by the console controller. |
| `SkillConsoleApiOpenApiITCase` | `GET,DELETE /v3/console/ai/skills`<br>`GET /v3/console/ai/skills/list`<br>`GET /v3/console/ai/skills/version`<br>`GET /v3/console/ai/skills/version/download`<br>`POST,PUT,DELETE /v3/console/ai/skills/draft`<br>`POST /v3/console/ai/skills/submit`<br>`POST /v3/console/ai/skills/publish`<br>`POST /v3/console/ai/skills/force-publish`<br>`POST /v3/console/ai/skills/redraft`<br>`POST /v3/console/ai/skills/online`<br>`POST /v3/console/ai/skills/offline`<br>`PUT /v3/console/ai/skills/labels`<br>`PUT /v3/console/ai/skills/biz-tags`<br>`PUT /v3/console/ai/skills/scope` | Covered | Verifies skill draft/update/fork/delete, submit, force publish, detail, version detail, list filters, ZIP download, labels, server-managed latest label preservation, publish-parameter compatibility, bizTags, PUBLIC/PRIVATE scope, version-level and skill-level online/offline latest maintenance, delete, and absent resource/version errors. Validates missing skillName/skillCard/targetVersion/version/labels/scope, name mismatch, invalid version/search/scope/page, and invalid lifecycle transitions. |
| `SkillUploadConsoleApiOpenApiITCase` | `POST /v3/console/ai/skills/upload`<br>`POST /v3/console/ai/skills/upload/precheck`<br>`POST /v3/console/ai/skills/upload/batch` | Covered | Verifies single and batch Skill ZIP upload, server-side ZIP precheck, owner, maximum published version (online or offline), target version, single-code reporting, archive entry paths, and distinct `NOT_A_SKILL`/`INVALID_SKILL` results for missing or invalid `SKILL.md`; validates overwrite behavior, next version generation, version normalization/fallback, partial batch results, empty/malformed ZIP, and upload error envelopes. Permission-denied owner reporting is covered by the service test because the default standalone IT environment does not switch authenticated users. |
| `AgentSpecConsoleApiOpenApiITCase` | `GET,DELETE /v3/console/ai/agentspecs`<br>`GET /v3/console/ai/agentspecs/list`<br>`GET /v3/console/ai/agentspecs/version`<br>`POST,PUT,DELETE /v3/console/ai/agentspecs/draft`<br>`POST /v3/console/ai/agentspecs/submit`<br>`POST /v3/console/ai/agentspecs/publish`<br>`POST /v3/console/ai/agentspecs/force-publish`<br>`POST /v3/console/ai/agentspecs/redraft`<br>`POST /v3/console/ai/agentspecs/online`<br>`POST /v3/console/ai/agentspecs/offline`<br>`PUT /v3/console/ai/agentspecs/labels`<br>`PUT /v3/console/ai/agentspecs/biz-tags`<br>`PUT /v3/console/ai/agentspecs/scope` | Covered | Verifies AgentSpec draft/update/auto-create/fork/delete, submit, force publish, detail, version detail, list filters, labels, server-managed latest label preservation, publish-parameter compatibility, bizTags, scope, version-level and resource-level online/offline latest maintenance, delete, and absent resource/version errors. Validates missing agentSpecName/agentSpecCard/targetVersion/version/labels/scope, invalid version/search/scope/page, and invalid lifecycle transitions. The shared `bizTag` filter is accepted but not applied by the AgentSpec service; the IT records that behavior. |
| `AgentSpecUploadConsoleApiOpenApiITCase` | `POST /v3/console/ai/agentspecs/upload` | Covered | Verifies single AgentSpec ZIP upload from `manifest.json` plus resources, overwrite of an editing draft, next draft version after publish, seed archives importing multiple AgentSpecs, empty file, malformed ZIP, and missing manifest errors. |
| `AiResourceImportConsoleApiOpenApiITCase` | `GET /v3/console/ai/import/sources`<br>`POST /v3/console/ai/import/search`<br>`POST /v3/console/ai/import/validate`<br>`POST /v3/console/ai/import/execute` | Covered | Verifies enabled managed importer plugins as the source list, resourceType filters, sanitized source info, unsupported resource type empty result, missing resourceType/sourceId/selectedItems, malformed JSON options/selectedItems, empty selected items, unknown source not-found, unsupported source/resourceType combinations, and controlled error bodies without performing external network import. |
| `PipelineConsoleApiOpenApiITCase` | `GET /v3/console/ai/pipelines`<br>`GET /v3/console/ai/pipelines/list`<br>`GET /v3/console/ai/pipelines/detail`<br>`GET /v3/console/ai/pipelines/{pipelineId}` | Partial | Verifies current and legacy list page contracts for resourceType/resourceName/namespaceId/version filters, required resourceType, page validation, required pipelineId, and absent pipeline not-found errors. Successful detail creation is not covered in the default standalone IT environment because pipeline rows require configured publish-pipeline plugins. |
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
