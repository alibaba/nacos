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

# OpenAPI IT Coverage Registry

This registry records which Nacos HTTP APIs are covered by `test/openapi-test`
integration tests and the main scenarios each test class proves. It is meant for
maintainers and agents to quickly locate coverage before adding or debugging an
IT.

## Maintenance Rules

- Update this file whenever an OpenAPI/AdminAPI/ConsoleAPI IT class is added,
  removed, or gains meaningful scenario coverage.
- Keep the class Javadoc `Scenario coverage` section as the detailed source for
  a class. This registry is the compact index.
- Record API scenario coverage, not line or branch coverage. Each row should
  identify capability, boundary/validation, and exception/error handling
  coverage when those scenario groups are practical.
- If an exposed success path is intentionally not executed because it mutates
  risky runtime or storage state, say so in the scenario cell.

## Client OpenAPI

| API | IT class | Scenario coverage |
| --- | --- | --- |
| `GET /nacos/v3/client/cs/config` | `ConfigOpenApiITCase` | Queries config published by admin API with content, md5, lastModified, contentType, and beta fields; verifies public namespace defaulting, wrong namespace not-found, required `dataId`/`groupName`, legacy `group` rejection, invalid namespace, and wrapped not-found/error bodies. |
| `POST /nacos/v3/client/ns/instance` | `InstanceRegisterOpenApiITCase` | Registers instances and verifies visibility through list; covers namespace/group/cluster/healthy/weight/enabled defaults, explicit group/cluster behavior, required service/ip/port validation, invalid weight/cluster, and duplicate or service-state errors. |
| `GET /nacos/v3/client/ns/instance/list` | `InstanceListOpenApiITCase` | Lists enabled registered instances with metadata and health fields; covers namespace/group/cluster defaults, healthy-only and enabled filtering, empty-result behavior, required `serviceName`, malformed/unknown parameters, and not-found style results. |
| `DELETE /nacos/v3/client/ns/instance` | `InstanceDeregisterOpenApiITCase` | Deregisters an existing instance and verifies absence from list; covers default and explicit group/cluster values, idempotent missing-instance behavior, required service/ip/port validation, and malformed port handling. |
| `GET /nacos/v3/client/ai/prompt` | `PromptClientOpenApiITCase` | Queries online prompts by latest, explicit version, and label; verifies namespace defaulting, version-over-label priority, md5 conditional HTTP 304, missing promptKey/version resolution, absent prompt, unknown version, and offline/not-online errors. |
| `GET /nacos/v3/client/ai/skills` | `SkillClientOpenApiITCase` | Downloads online skills as ZIP by latest, version, and label with resource entries; covers namespace defaulting, version-over-label priority, missing skillName, absent skill, unknown version/label, and controlled not-found JSON for download failures. |
| `GET /nacos/v3/client/ai/agentspecs` | `AgentSpecClientOpenApiITCase` | Queries online AgentSpecs by latest, version, and label with manifest/resource content; covers namespace defaulting, label/version resolution, missing agentSpecName, absent AgentSpec, unknown version, and controlled not-found errors. |
| `GET /nacos/v3/client/ai/agentspecs/search` | `AgentSpecSearchClientOpenApiITCase` | Searches enabled AgentSpecs with online versions and keyword filters; covers optional keyword, namespace defaulting, page defaults and validation, empty page success, and invalid pagination errors. |

## Admin API - Config

| API | IT class | Scenario coverage |
| --- | --- | --- |
| `/nacos/v3/admin/cs/config` | `ConfigAdminApiOpenApiITCase` | Publishes, republishes, queries, updates metadata, and deletes config; covers public namespace defaulting, type normalization, required identity/content fields, absent config 404, duplicate/update semantics, and malformed metadata errors. |
| `GET /nacos/v3/admin/cs/config/list` | `ConfigListAdminApiOpenApiITCase` | Lists published configs through admin page model with fuzzy and accurate filters; covers type and tag filters, blank dataId group-scoped listing, public namespace defaulting, pagination validation, empty pages, and wrapped error bodies. |
| `DELETE /nacos/v3/admin/cs/config/batch` | `ConfigBatchDeleteAdminApiOpenApiITCase` | Deletes multiple configs by comma-separated ids and verifies absence; covers non-existing ids being ignored, required `ids`, and HTTP 400 v3 Result validation errors. |
| `/nacos/v3/admin/cs/config/beta` | `ConfigBetaAdminApiOpenApiITCase` | Queries and deletes beta config created via publish API; covers public namespace defaulting, beta rule generated from `betaIps`, required fields, absent beta 404, and v3 error envelope. |
| `/nacos/v3/admin/cs/config/gray` | `ConfigGrayAdminApiOpenApiITCase` | Publishes, queries, updates, and deletes gray config with gray metadata; covers public namespace defaulting, tagv2 version acceptance, grayName/rule requirements, absent gray config, and parameter validation errors. |
| `POST /nacos/v3/admin/cs/config/import` | `ConfigImportAdminApiOpenApiITCase` | Imports a metadata ZIP and verifies the imported config can be queried; covers public namespace defaulting, `ABORT` policy, missing file, malformed metadata ZIP, and business failures in v3 Result form. |
| `GET /nacos/v3/admin/cs/config/export` | `ConfigExportAdminApiOpenApiITCase` | Exports config by ids and namespace as downloadable ZIP containing config entries and metadata; covers public namespace defaulting, query serialization, invalid namespace, absent ids, and non-JSON download/error response variants. |
| `POST /nacos/v3/admin/cs/config/clone` | `ConfigCloneAdminApiOpenApiITCase` | Clones existing configs to target dataId/group/namespace and verifies queried target content; covers required namespace, empty clone list rejection, malformed clone payload, business failures, and v3 error bodies. |
| `/nacos/v3/admin/cs/history` | `ConfigHistoryAdminApiOpenApiITCase` | Publishes/republishes config and verifies history list, detail, previous, and configs history queries; covers large page size, required paging and identity fields, absent/mismatched history, and controlled errors. |
| `/nacos/v3/admin/cs/config/listener` and `/nacos/v3/admin/cs/listener` | `ConfigListenerAdminApiOpenApiITCase` | Queries config-scoped and IP-scoped listener state; covers public namespace defaulting, `aggregation=false`, required dataId/group/ip, and HTTP 400 validation envelopes. |
| `/nacos/v3/admin/cs/capacity` | `ConfigCapacityAdminApiOpenApiITCase` | Updates and queries group/namespace capacity limits; covers identity requirements, at-least-one capacity field, cleanup/deletion, and validation error envelopes. |
| `/nacos/v3/admin/cs/metrics` | `ConfigMetricsAdminApiOpenApiITCase` | Queries config metrics and verifies JSON object shape; covers parameter-free request behavior and success response contract. |
| `/nacos/v3/admin/cs/ops` | `ConfigOpsAdminApiOpenApiITCase` | Triggers local-cache dump success and verifies ops validation; covers log update required params, Derby query required `sql`, Derby import disabled/non-embedded controlled failure, and intentionally avoids successful DB import because it mutates embedded storage. |

## Admin API - Naming

| API | IT class | Scenario coverage |
| --- | --- | --- |
| `/nacos/v3/admin/ns/service` | `ServiceAdminApiOpenApiITCase` | Creates, queries, updates, lists, and deletes persistent services; verifies selector types, subscriber empty page shape, public/default group defaults, required serviceName, pagination validation, and v3 errors. |
| `/nacos/v3/admin/ns/instance` | `InstanceAdminApiOpenApiITCase` | Registers, queries, lists, updates, partially updates, and deletes instances; covers defaults, healthy/enabled/ephemeral/weight behavior, required fields, invalid values, missing instance, and persistent-service conflicts. |
| `/nacos/v3/admin/ns/instance/metadata/batch` | `InstanceMetadataAdminApiOpenApiITCase` | Batch-updates and batch-deletes instance metadata and verifies applied/removed metadata; covers omitted instance selector meaning all instances, explicit selector isolation, required fields, malformed selector, and empty target behavior. |
| `/nacos/v3/admin/ns/cluster` | `ClusterAdminApiOpenApiITCase` | Updates cluster health check config and verifies service cluster metadata; covers defaults, required service/cluster/checker/check port fields, missing service, and validation errors. |
| `/nacos/v3/admin/ns/health` | `HealthAdminApiOpenApiITCase` | Lists health checker types and manually updates instance health where eligible; covers defaults, required fields, missing checker/service branches, and controlled SERVER_ERROR/v3 errors. |
| `/nacos/v3/admin/ns/client` | `ClientAdminApiOpenApiITCase` | Verifies HTTP registered instance creates a visible client; covers detail, publish/subscriber lists, distro info, namespace/group isolation, required service fields, missing client 404, and empty list shapes. |
| `/nacos/v3/admin/ns/ops` | `OperatorAdminApiOpenApiITCase` | Queries and updates naming switches, queries metrics, and updates naming log level; covers metrics defaulting, required switch fields, invalid values, and controlled SERVER_ERROR/v3 errors. |

## Admin API - Core

| API | IT class | Scenario coverage |
| --- | --- | --- |
| `/nacos/v3/admin/core/namespace` | `NamespaceAdminApiOpenApiITCase` | Creates, queries, lists, checks, updates, and deletes namespace metadata; covers id trimming/length, namespace name validation, duplicate/missing fields, post-delete checks, and HTTP 400 errors. |
| `/nacos/v3/admin/core/cluster` | `CoreClusterAdminApiOpenApiITCase` | Queries self node and node list with address/state filters; covers case-insensitive legal state, illegal state validation, empty node update body, lookup required fields, and intentionally avoids topology mutation success paths. |
| `/nacos/v3/admin/core/loader` | `ServerLoaderAdminApiOpenApiITCase` | Queries current connections and cluster loader metrics; covers required count/connectionId, numeric loaderFactor validation for smart reload, and intentionally avoids successful rebalance operations. |
| `/nacos/v3/admin/core/plugin` | `PluginAdminApiOpenApiITCase` | Lists plugins, filters by pluginType, and queries detail; covers unknown type empty list, missing plugin 404, status/config required params, and intentionally avoids mutating plugin runtime state. |
| `/nacos/v3/admin/core/ops` | `CoreOpsAdminApiOpenApiITCase` | Queries id-generator diagnostics and updates runtime log level; covers raft command/value requirements, log body requirements, and JSON body validation errors. |
| `/nacos/v3/admin/core/state` | `CoreStateAdminApiOpenApiITCase` | Queries server state, liveness, and readiness; covers parameter-free behavior, unexpected query tolerance, and documents that readiness failure is not forced because it mutates shared server state. |

## Admin API - AI

| API | IT class | Scenario coverage |
| --- | --- | --- |
| `/nacos/v3/admin/ai/a2a` | `A2aAdminApiOpenApiITCase` | Registers legacy and v1 agent cards, normalizes interfaces, queries by version/latest, updates latest, lists, enumerates versions, and deletes; covers defaults, invalid search/type/card JSON, missing identity, absent agent, and tolerant delete. |
| `/nacos/v3/admin/ai/mcp` | `McpAdminApiOpenApiITCase` | Creates MCP server with stdio spec/tools/resources, queries by id/name/latest, updates version, lists accurate/blur, and deletes; covers identity alternatives, invalid search/custom id/JSON, duplicate conflict, not-found, and empty pages. |
| `/nacos/v3/admin/ai/pipelines` | `PipelineAdminApiOpenApiITCase` | Lists current and legacy pipeline page contracts and queries pipeline detail; covers required resourceType, pagination validation, unknown pipeline 404, and unavailable external resource behavior. |
| `/nacos/v3/admin/ai/import` | `AiResourceImportAdminApiOpenApiITCase` | Lists sanitized import sources and runs search/validate/execute flows with fake source data; covers required fields, JSON option parsing, overwrite/skipInvalid flags, unsupported resource/source types, token mismatch, and import result errors. |
| `/nacos/v3/admin/ai/prompt` | `PromptAdminApiOpenApiITCase` | Exercises prompt draft create/update/delete, submit, force-publish, governance, versions, list, metadata, labels, bizTags, online/offline, download, legacy compatibility, and delete; covers defaults, search filters, version format, missing params, absent resources, and controlled workflow errors. |
| `/nacos/v3/admin/ai/skills` | `SkillAdminApiOpenApiITCase` | Exercises skill draft/create/update/delete/fork, submit, force-publish, metadata, labels, bizTags, scope, online/offline, download, list, and delete; covers defaults, search/scope/bizTag filters, version and SKILL.md mismatch validation, absent resources, and controlled workflow errors. |
| `/nacos/v3/admin/ai/skills/upload` | `SkillUploadAdminApiOpenApiITCase` | Uploads single and batch skill ZIPs, validates generated drafts/resources, overwrite behavior, next version generation, and partial batch handling; covers empty/malformed ZIP, invalid targetVersion, missing SKILL.md, and upload error envelopes. |
| `/nacos/v3/admin/ai/agentspecs` | `AgentSpecAdminApiOpenApiITCase` | Exercises AgentSpec draft/create/update/delete/fork, force-publish, metadata, version/meta, labels, bizTags, scope, online/offline, list, and delete; covers defaults, search/scope filters, version validation, absent resources, and controlled workflow errors. |
| `/nacos/v3/admin/ai/agentspecs/upload` | `AgentSpecUploadAdminApiOpenApiITCase` | Uploads single and batch AgentSpec ZIPs, validates manifest/resources, overwrite behavior, next version generation, and partial batch handling; covers empty/malformed ZIP, missing manifest, invalid targetVersion, and upload error envelopes. |
