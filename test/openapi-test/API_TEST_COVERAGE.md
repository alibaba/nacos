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
| Client OpenAPI | 14 | 13 | 1 | 0 | 92.86% | 96.43% |
| Admin API | 38 | 31 | 7 | 0 | 81.58% | 90.79% |
| Console API | 29 | 24 | 5 | 0 | 82.76% | 91.38% |
| Auth API | 4 | 0 | 2 | 2 | 0.00% | 25.00% |
| Total | 85 | 68 | 15 | 2 | 80.00% | 88.82% |

Partial rows are documented in the matching scenario document. The current
partial set is limited to operations whose remaining success paths mutate
shared runtime/storage state, require publish-pipeline plugin data, require a
data-plane publisher binding not yet present in standalone IT, require an
external MCP runtime or LLM provider, require authenticated multi-identity AI resource
visibility scenarios, or belong to the remaining default-auth user management operations outside
the covered login contract.

External protocol adaptors are tracked separately from the Nacos API coverage
totals because they run in independent web contexts. The ARD adaptor currently
has four covered scenario rows and no partial rows. Its standalone conformance
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
tests. Anonymous AI access is not exercised in standalone OpenAPI IT; explicit
credential presence, blank
credential rejection, and HTTP 403 `ACCESS_DENIED` error mapping are covered
by auth/core unit tests.

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
Console facade. The Client Endpoint scenario cross-validates an Admin-created
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

The Admin and Console MCP scenarios also remain the wire-contract regression
coverage for the compatibility router while management authority is `SYNCING`.
They exercise all twelve standard lifecycle routes at their public HTTP
boundary: name-only identity and exact-Version validation, nested legacy ID
rejection, case-insensitive status input, and the controlled pre-cutover
conflict envelope. If background reconciliation has already completed the
one-way cutover, the same scenario accepts only controlled absent-resource
responses and verifies one real draft create/delete pair, including the
resource status, owner, scope, labels, working pointers, and online count
returned in the lifecycle detail. The test does not
publish the `LIFECYCLE_MANAGED` marker into the shared standalone process.
Focused component tests instead cover the
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
draft-only and `autoSubmit` workflows, idempotent retry/resume, direct and
inherited Versions, validation/conflict handling, namespace isolation, and
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
| Auth API | [AUTH_API_TEST_SCENARIOS.md](AUTH_API_TEST_SCENARIOS.md) | `src/test/java/com/alibaba/nacos/test/adminapi/auth` |
| AI Registry Adaptor | [AI_REGISTRY_ADAPTOR_API_TEST_SCENARIOS.md](AI_REGISTRY_ADAPTOR_API_TEST_SCENARIOS.md) | `ai-registry-adaptor/src/test/java/com/alibaba/nacos/airegistry`, `src/test/java/com/alibaba/nacos/test/openapi/ard` |
