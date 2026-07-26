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

## Config

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `ConfigOpenApiITCase` | `GET /v3/client/cs/config` | Covered | Queries config published by admin API with content, md5, lastModified, contentType, and current gray-backed beta fields; verifies public namespace defaulting, wrong namespace not-found, required `dataId`/`groupName`, legacy `group` rejection, invalid namespace, and wrapped not-found/error bodies. Removed pre-3.0 namespace or beta/tag storage migration is outside the 3.3 client API contract. |

## Naming

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `InstanceRegisterOpenApiITCase` | `POST /v3/client/ns/instance` | Covered | Registers instances and verifies visibility through list; covers namespace/group/cluster/healthy/weight/enabled defaults, explicit group/cluster behavior, required service/ip/port validation, invalid weight/cluster, and duplicate or service-state errors. |
| `InstanceListOpenApiITCase` | `GET /v3/client/ns/instance/list` | Covered | Lists enabled registered instances with metadata and health fields; covers namespace/group/cluster defaults, healthy-only and enabled filtering, empty-result behavior, required `serviceName`, malformed or unknown parameters, and not-found style results. |
| `InstanceDeregisterOpenApiITCase` | `DELETE /v3/client/ns/instance` | Covered | Deregisters an existing instance and verifies absence from list; covers default and explicit group/cluster values, idempotent missing-instance behavior, required service/ip/port validation, and malformed port handling. |

## AI Registry

| API surface / IT class | Covered API operations | Current status | Current / missing coverage |
| --- | --- | --- | --- |
| `PromptClientOpenApiITCase` | `GET /v3/client/ai/prompt` | Covered | Queries online prompts by latest, explicit version, and label; verifies namespace defaulting, version-over-label priority, md5 conditional HTTP 304, missing promptKey/version resolution, absent prompt, unknown version, and offline/not-online errors. |
| `SkillClientOpenApiITCase` | `GET /v3/client/ai/skills` | Covered | Downloads online skills as ZIP by latest, version, and label with resource entries; covers namespace defaulting, version-over-label priority, missing skillName, absent skill, unknown version/label, and controlled not-found JSON for download failures. |
| `AgentSpecClientOpenApiITCase` | `GET /v3/client/ai/agentspecs` | Covered | Queries online AgentSpecs by latest, version, and label with manifest/resource content; covers namespace defaulting, label/version resolution, missing agentSpecName, absent AgentSpec, unknown version, and controlled not-found errors. |
| `AgentSpecSearchClientOpenApiITCase` | `GET /v3/client/ai/agentspecs/search` | Covered | Searches enabled AgentSpecs with online versions and keyword filters; covers optional keyword, namespace defaulting, page defaults and validation, empty page success, and invalid pagination errors. |
