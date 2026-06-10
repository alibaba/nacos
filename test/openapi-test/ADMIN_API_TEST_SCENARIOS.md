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

# Admin API IT Scenario Index

This document records which admin API operations are covered by the
standalone-server IT classes under
`src/test/java/com/alibaba/nacos/test/adminapi`.

Source API surface: admin swagger at `https://nacos.io/swagger/admin/zh/api.json`.
The branch-level coverage target is API scenario coverage: expected capability,
boundary/validation behavior, and controlled exception/error handling.

## Status Legend

| Status | Meaning |
| --- | --- |
| Covered | The current IT verifies the expected behavior and its important result shape. |
| Partial | The current IT verifies representative behavior, but important public API scenarios remain. |
| Pending | No IT currently verifies this public API scenario. |

## Config

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `ConfigAdminApiOpenApiITCase` | `GET,POST,PUT,DELETE /v3/admin/cs/config` | Covered | Publishes, republishes, queries, updates metadata, and deletes config; covers blank/omitted namespace defaulting to current `public` storage, type normalization, required identity/content fields, absent config 404, duplicate/update semantics, and malformed metadata errors. Removed empty-tenant migration and dual-write behavior is outside the 3.3 API contract. |
| `ConfigListAdminApiOpenApiITCase` | `GET /v3/admin/cs/config/list` | Covered | Lists published configs through admin page model with fuzzy and accurate filters; covers type and tag filters, blank dataId group-scoped listing, public namespace defaulting, pagination validation, empty pages, and wrapped error bodies. |
| `ConfigBatchDeleteAdminApiOpenApiITCase` | `DELETE /v3/admin/cs/config/batch` | Covered | Deletes multiple configs by comma-separated ids and verifies absence; covers non-existing ids being ignored, required `ids`, and HTTP 400 v3 Result validation errors. |
| `ConfigBetaAdminApiOpenApiITCase` | `GET,DELETE /v3/admin/cs/config/beta` | Covered | Queries and deletes beta config created via the current publish API and gray model; covers public namespace defaulting, beta rule generated from `betaIps`, required fields, absent beta 404, and v3 error envelope. Removed `config_info_beta` old-table migration is not an expected scenario. |
| `ConfigGrayAdminApiOpenApiITCase` | `GET,POST,PUT,DELETE /v3/admin/cs/config/gray` | Covered | Publishes, queries, updates, and deletes gray config with current gray metadata; covers public namespace defaulting, tagv2 version acceptance, grayName/rule requirements, absent gray config, and parameter validation errors. Removed `config_info_tag` old-table migration is not an expected scenario. |
| `ConfigImportAdminApiOpenApiITCase` | `POST /v3/admin/cs/config/import` | Covered | Imports a metadata ZIP and verifies the imported config can be queried; covers public namespace defaulting, `ABORT` policy, missing file, malformed metadata ZIP, and business failures in v3 Result form. |
| `ConfigExportAdminApiOpenApiITCase` | `GET /v3/admin/cs/config/export` | Covered | Exports config by ids and namespace as downloadable ZIP containing config entries and metadata; covers public namespace defaulting, query serialization, invalid namespace, absent ids, and non-JSON download/error response variants. |
| `ConfigCloneAdminApiOpenApiITCase` | `POST /v3/admin/cs/config/clone` | Covered | Clones existing configs to target dataId/group/namespace and verifies queried target content; covers required namespace, empty clone list rejection, malformed clone payload, business failures, and v3 error bodies. |
| `ConfigHistoryAdminApiOpenApiITCase` | `GET /v3/admin/cs/history`<br>`GET /v3/admin/cs/history/list`<br>`GET /v3/admin/cs/history/previous`<br>`GET /v3/admin/cs/history/configs` | Covered | Publishes/republishes config and verifies history list, detail, previous, and configs history queries; covers large page size, required paging and identity fields, absent/mismatched history, and controlled errors. |
| `ConfigListenerAdminApiOpenApiITCase` | `GET /v3/admin/cs/config/listener`<br>`GET /v3/admin/cs/listener` | Covered | Queries config-scoped and IP-scoped listener state; covers public namespace defaulting, `aggregation=false`, required dataId/group/ip, and HTTP 400 validation envelopes. |
| `ConfigCapacityAdminApiOpenApiITCase` | `GET,POST /v3/admin/cs/capacity` | Covered | Updates and queries group/namespace capacity limits; covers identity requirements, at-least-one capacity field, and validation error envelopes. There is no public delete endpoint for capacity rows. |
| `ConfigMetricsAdminApiOpenApiITCase` | `GET /v3/admin/cs/metrics` | Covered | Queries config metrics and verifies JSON object shape; covers parameter-free request behavior and success response contract. |
| `ConfigOpsAdminApiOpenApiITCase` | `POST /v3/admin/cs/ops/localCache`<br>`PUT /v3/admin/cs/ops/log`<br>`GET /v3/admin/cs/ops/derby`<br>`POST /v3/admin/cs/ops/derby/import` | Partial | Triggers local-cache dump success and verifies ops validation; covers log update required params, Derby query required `sql`, Derby import disabled/non-embedded controlled failure, and intentionally avoids successful DB import because it mutates embedded storage. |

## Naming

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `ServiceAdminApiOpenApiITCase` | `GET,PUT,POST,DELETE /v3/admin/ns/service`<br>`GET /v3/admin/ns/service/list`<br>`GET /v3/admin/ns/service/selector/types`<br>`GET /v3/admin/ns/service/subscribers` | Covered | Creates, queries, updates, lists, and deletes persistent services; verifies selector types, subscriber empty page shape, public/default group defaults, required serviceName, pagination validation, and v3 errors. |
| `InstanceAdminApiOpenApiITCase` | `GET,PUT,POST,DELETE /v3/admin/ns/instance`<br>`GET /v3/admin/ns/instance/list`<br>`PUT /v3/admin/ns/instance/partial` | Covered | Registers, queries, lists, updates, partially updates, and deletes instances; covers defaults, healthy/enabled/ephemeral/weight behavior, required fields, invalid values, missing instance, and persistent-service conflicts. |
| `InstanceMetadataAdminApiOpenApiITCase` | `PUT,DELETE /v3/admin/ns/instance/metadata/batch` | Covered | Batch-updates and batch-deletes instance metadata and verifies applied/removed metadata; covers omitted instance selector meaning all instances, explicit selector isolation, required fields, malformed selector, and empty target behavior. |
| `ClusterAdminApiOpenApiITCase` | `PUT /v3/admin/ns/cluster` | Covered | Updates cluster health check config and verifies service cluster metadata; covers defaults, required service/cluster/checker/check port fields, missing service, and validation errors. |
| `HealthAdminApiOpenApiITCase` | `GET /v3/admin/ns/health/checkers`<br>`PUT /v3/admin/ns/health/instance` | Covered | Lists health checker types and manually updates instance health where eligible; covers defaults, required fields, missing checker/service branches, and controlled SERVER_ERROR/v3 errors. |
| `ClientAdminApiOpenApiITCase` | `GET /v3/admin/ns/client/list`<br>`GET /v3/admin/ns/client`<br>`GET /v3/admin/ns/client/publishers`<br>`GET /v3/admin/ns/client/subscribers`<br>`GET /v3/admin/ns/client/distro` | Covered | Verifies HTTP registered instance creates a visible client; covers detail, publish/subscriber lists, distro info, namespace/group isolation, required service fields, missing client 404, and empty list shapes. |
| `OperatorAdminApiOpenApiITCase` | `GET,PUT /v3/admin/ns/ops/switches`<br>`GET /v3/admin/ns/ops/metrics`<br>`PUT /v3/admin/ns/ops/log` | Covered | Queries and updates naming switches, queries metrics, and updates naming log level; covers metrics defaulting, required switch fields, invalid values, and controlled SERVER_ERROR/v3 errors. |

## Core

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `NamespaceAdminApiOpenApiITCase` | `GET,PUT,POST,DELETE /v3/admin/core/namespace`<br>`GET /v3/admin/core/namespace/exist`<br>`GET /v3/admin/core/namespace/list` | Covered | Creates, queries, lists, checks, updates, and deletes namespace metadata; covers id trimming/length, namespace name validation, duplicate/missing fields, post-delete checks, and HTTP 400 errors. |
| `CoreClusterAdminApiOpenApiITCase` | `GET /v3/admin/core/cluster/self`<br>`GET /v3/admin/core/cluster/nodes`<br>`PUT /v3/admin/core/cluster/node`<br>`POST /v3/admin/core/cluster/lookup` | Partial | Queries self node and node list with address/state filters; covers case-insensitive legal state, illegal state validation, empty node update body, lookup required fields, and intentionally avoids topology mutation success paths. |
| `ServerLoaderAdminApiOpenApiITCase` | `GET /v3/admin/core/loader/current`<br>`GET /v3/admin/core/loader/cluster`<br>`POST /v3/admin/core/loader/reloadCurrent`<br>`POST /v3/admin/core/loader/reloadClient`<br>`POST /v3/admin/core/loader/smartReloadCluster` | Partial | Queries current connections and cluster loader metrics; covers required count/connectionId, numeric loaderFactor validation for smart reload, and intentionally avoids successful rebalance operations. |
| `PluginAdminApiOpenApiITCase` | `GET /v3/admin/core/plugin`<br>`GET /v3/admin/core/plugin/list`<br>`GET /v3/admin/core/plugin/detail`<br>`PUT /v3/admin/core/plugin/status`<br>`GET,PUT /v3/admin/core/plugin/config` | Partial | Lists plugins, filters by pluginType, and queries detail; covers unknown type empty list, missing plugin 404, status/config required params, and intentionally avoids mutating plugin runtime state. |
| `CoreOpsAdminApiOpenApiITCase` | `GET /v3/admin/core/ops/ids`<br>`POST /v3/admin/core/ops/raft`<br>`PUT /v3/admin/core/ops/log` | Covered | Queries id-generator diagnostics and updates runtime log level; covers raft command/value requirements, log body requirements, and JSON body validation errors. |
| `CoreStateAdminApiOpenApiITCase` | `GET /v3/admin/core/state`<br>`GET /v3/admin/core/state/liveness`<br>`GET /v3/admin/core/state/readiness` | Covered | Queries server state, liveness, and readiness; covers parameter-free behavior, unexpected query tolerance, and documents that readiness failure is not forced because it mutates shared server state. |

## AI Registry

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `A2aAdminApiOpenApiITCase` | `GET,PUT,POST,DELETE /v3/admin/ai/a2a`<br>`GET /v3/admin/ai/a2a/list`<br>`GET /v3/admin/ai/a2a/version/list` | Covered | Registers legacy and v1 agent cards, normalizes interfaces, queries by version/latest, updates latest, lists, enumerates versions, and deletes; covers defaults, invalid search/type/card JSON, missing identity, absent agent, and tolerant delete. |
| `McpAdminApiOpenApiITCase` | `GET,PUT,POST,DELETE /v3/admin/ai/mcp`<br>`GET /v3/admin/ai/mcp/list` | Covered | Creates MCP server with stdio spec/tools/resources, queries by id/name/latest, updates version, lists accurate/blur, and deletes; covers identity alternatives, invalid search/custom id/JSON, duplicate conflict, not-found, and empty pages. |
| `PipelineAdminApiOpenApiITCase` | `GET /v3/admin/ai/pipelines`<br>`GET /v3/admin/ai/pipelines/list`<br>`GET /v3/admin/ai/pipelines/detail`<br>`GET /v3/admin/ai/pipelines/{pipelineId}` | Partial | Lists current and legacy pipeline page contracts and queries pipeline detail; covers required resourceType, pagination validation, unknown pipeline 404, and unavailable external resource behavior. |
| `AiResourceImportAdminApiOpenApiITCase` | `GET /v3/admin/ai/import/sources`<br>`POST /v3/admin/ai/import/search`<br>`POST /v3/admin/ai/import/validate`<br>`POST /v3/admin/ai/import/execute` | Covered | Lists sanitized import sources and runs search/validate/execute flows with fake source data; covers required fields, JSON option parsing, overwrite/skipInvalid flags, unsupported resource/source types, token mismatch, and import result errors. |
| `PromptAdminApiOpenApiITCase` | `DELETE /v3/admin/ai/prompt`<br>`GET /v3/admin/ai/prompt/list`<br>`GET /v3/admin/ai/prompt/versions`<br>`GET /v3/admin/ai/prompt/governance`<br>`GET /v3/admin/ai/prompt/version`<br>`GET /v3/admin/ai/prompt/version/download`<br>`POST,PUT,DELETE /v3/admin/ai/prompt/draft`<br>`POST /v3/admin/ai/prompt/submit`<br>`POST /v3/admin/ai/prompt/publish`<br>`POST /v3/admin/ai/prompt/force-publish`<br>`POST /v3/admin/ai/prompt/redraft`<br>`POST /v3/admin/ai/prompt/online`<br>`POST /v3/admin/ai/prompt/offline`<br>`PUT /v3/admin/ai/prompt/labels`<br>`PUT /v3/admin/ai/prompt/description`<br>`PUT /v3/admin/ai/prompt/biz-tags` | Covered | Exercises prompt draft create/update/delete, submit, force-publish, governance, versions, list, metadata, labels, server-managed latest label preservation, publish-parameter compatibility, bizTags, online/offline latest maintenance, download, legacy compatibility, and delete; covers defaults, search filters, version format, missing params, absent resources, and controlled workflow errors. |
| `SkillAdminApiOpenApiITCase` | `GET,DELETE /v3/admin/ai/skills`<br>`GET /v3/admin/ai/skills/list`<br>`GET /v3/admin/ai/skills/version`<br>`GET /v3/admin/ai/skills/version/download`<br>`POST,PUT,DELETE /v3/admin/ai/skills/draft`<br>`POST /v3/admin/ai/skills/submit`<br>`POST /v3/admin/ai/skills/publish`<br>`POST /v3/admin/ai/skills/force-publish`<br>`POST /v3/admin/ai/skills/redraft`<br>`POST /v3/admin/ai/skills/online`<br>`POST /v3/admin/ai/skills/offline`<br>`PUT /v3/admin/ai/skills/labels`<br>`PUT /v3/admin/ai/skills/biz-tags`<br>`PUT /v3/admin/ai/skills/scope` | Covered | Exercises skill draft/create/update/delete/fork, submit, force-publish, metadata, labels, server-managed latest label preservation, publish-parameter compatibility, bizTags, scope, online/offline latest maintenance, download, list, and delete; covers defaults, search/scope/bizTag filters, version and `SKILL.md` mismatch validation, absent resources, and controlled workflow errors. |
| `SkillUploadAdminApiOpenApiITCase` | `POST /v3/admin/ai/skills/upload`<br>`POST /v3/admin/ai/skills/upload/batch` | Covered | Uploads single and batch skill ZIPs, validates generated drafts/resources, overwrite behavior, next version generation, and partial batch handling; covers empty/malformed ZIP, invalid targetVersion, missing `SKILL.md`, and upload error envelopes. |
| `AgentSpecAdminApiOpenApiITCase` | `GET,DELETE /v3/admin/ai/agentspecs`<br>`GET /v3/admin/ai/agentspecs/list`<br>`GET /v3/admin/ai/agentspecs/version`<br>`GET /v3/admin/ai/agentspecs/version/meta`<br>`POST,PUT,DELETE /v3/admin/ai/agentspecs/draft`<br>`POST /v3/admin/ai/agentspecs/submit`<br>`POST /v3/admin/ai/agentspecs/publish`<br>`POST /v3/admin/ai/agentspecs/force-publish`<br>`POST /v3/admin/ai/agentspecs/redraft`<br>`POST /v3/admin/ai/agentspecs/online`<br>`POST /v3/admin/ai/agentspecs/offline`<br>`PUT /v3/admin/ai/agentspecs/labels`<br>`PUT /v3/admin/ai/agentspecs/biz-tags`<br>`PUT /v3/admin/ai/agentspecs/scope` | Covered | Exercises AgentSpec draft/create/update/delete/fork, force-publish, metadata, version/meta, labels, server-managed latest label preservation, publish-parameter compatibility, bizTags, scope, online/offline latest maintenance, list, and delete; covers defaults, search/scope filters, version validation, absent resources, and controlled workflow errors. |
| `AgentSpecUploadAdminApiOpenApiITCase` | `POST /v3/admin/ai/agentspecs/upload` | Covered | Uploads single and batch AgentSpec ZIPs, validates manifest/resources, overwrite behavior, next version generation, and partial batch handling; covers empty/malformed ZIP, missing manifest, invalid targetVersion, and upload error envelopes. |
