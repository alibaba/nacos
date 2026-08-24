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

The standalone OpenAPI IT profile does not enable Client API authorization.
Functional scenarios therefore remain unchanged for authorization-only fixes.
Focused Auth and AI module tests verify that the AgentSpec detail endpoint
keeps its `OPEN_API`/`AI` metadata and resolves the authorization resource from
the client `name` parameter.

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
| `PromptClientOpenApiITCase` | `GET /v3/client/ai/prompt` | Partial | Queries online prompts by latest, explicit version, and label; verifies namespace defaulting, version-over-label priority, md5 conditional HTTP 304, missing promptKey/version resolution, absent prompt, unknown version, and offline/not-online errors. The auth-disabled standalone profile cannot switch identities, so unreadable prompts returning not found remains covered by focused service tests rather than end-to-end IT. |
| `SkillClientOpenApiITCase` | `GET /v3/client/ai/skills` | Covered | Downloads online skills as ZIP by latest, version, and label with resource entries; covers namespace defaulting, version-over-label priority, missing skillName, absent skill, unknown version/label, and controlled not-found JSON for download failures. |
| `AgentSpecClientOpenApiITCase` | `GET /v3/client/ai/agentspecs` | Covered | Queries online AgentSpecs by latest, version, and label with manifest/resource content; covers namespace defaulting, label/version resolution, missing name, absent AgentSpec, unknown version, and controlled not-found errors. |
| `AgentSpecSearchClientOpenApiITCase` | `GET /v3/client/ai/agentspecs/search` | Covered | Searches shared-index AgentSpec projections with online versions, literal keyword filtering, and `tagsAll`; covers eventual index convergence, optional keyword, namespace defaulting, page defaults and validation, empty page success, and invalid pagination errors. |
| `AiResourceSearchClientOpenApiITCase` | `GET /v3/client/ai/resources/search`<br>`GET /v3/client/ai/skills/search`<br>`GET /v3/client/ai/prompt/search`<br>`GET /v3/client/ai/mcp/search` | Covered | Publishes Agent, AgentSpec, Skill, Prompt, and MCP resources through their Admin lifecycle APIs, verifies Search stays successful with the current snapshot during asynchronous projection, waits for durable convergence, and verifies cross-type keyword recall. For every declared searchable type, a generic single-type query is cross-checked against its resource-specific Search facade, including the existing Agent and AgentSpec facades. Also covers default namespace, deterministic blank-query listing, `tagsAll`, `capabilitiesAny`, MCP `protocolsAny`, opaque multi-page cursor traversal without duplicates, no-match success, unsupported resource type, malformed cursor, bounded limit, oversized query, and invalid numbered pagination. |
| `AgentDiscoveryClientOpenApiITCase` | `GET /v3/client/ai/agents/search`<br>`GET /v3/client/ai/agents` | Covered | Publishes Agents through the Admin helper path, then verifies RAD Search and Discover projections. Search covers the `AUTO/INDEX/SCAN`-compatible eventual contract: `AUTO` and `INDEX` return a successful current snapshot without readiness 503 while convergence polling establishes the complete catalog; it also covers case-sensitive literal name filtering including `%`, `_`, and `\`, `tagsAll`/`protocolsAny` composition, stable ASCII numbered pagination including an out-of-range page, complete multi-Version catalogs, latest/offline convergence, and the invariant that Runtime Endpoint writes do not change Search. In the two-Version rollout workflow, independent HTTP publishers keep Version 1 and Version 2 Endpoints concurrently: an omitted selector returns latest metadata plus all online-Version-compatible Endpoints and binding provenance, while explicit `label=latest` returns only Version 2 Endpoints; exact Version 1 remains isolated, and taking Version 1 offline removes its Endpoint only from the default pool. Also covers default namespace, typed empty protocol results, empty search, pagination validation, mutually exclusive version/label, missing identity, and absent Agent errors. |
| `AgentPublishClientOpenApiITCase` | `POST /v3/client/ai/agents` | Covered | Verifies draft-only and auto-submit publication, resume, equivalent retries, conflicting content or initial metadata, advanced/offline Version errors, direct and `basedOnVersion` content, default/custom namespace isolation, malformed Form JSON and boolean fields, no Endpoint side effect, and Admin/Console/RAD/legacy A2A cross-checks. |
| `AgentEndpointClientOpenApiITCase` | `POST,DELETE /v3/client/ai/agents/endpoints`<br>`PUT /v3/client/ai/agents/endpoints/heartbeat` | Covered | Verifies Form-based complete HTTP Publisher replacement, visibility through Discover, idempotent registration/deregistration, empty Runtime Endpoint projection after deregistration, liveness intervals, heartbeat, and `HTTP_CLIENT_NOT_FOUND (50404)` before registration and after deregistration. Cross-validates the same workflow from Admin creation and Overview through Console Overview, then checks the populated and post-deregistration empty Runtime snapshots on both management surfaces, including lossless `HTTP+JSON` transport, endpoint payload, Version binding, enablement, health, state, and Console Naming reference. Confirms that a query with the same Client id does not create a Publisher, and covers required headers, Client-id syntax, complete-batch validation, malformed `endpoints` JSON Form-field handling, the configured Server soft watermark (reduced to 3 in `it-new.yml`), whole-batch admission from below to above the watermark, equal-size replacement above it, atomic rejection of further growth with `AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT`, and capacity reuse after deregistration. |
