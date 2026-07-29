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
| Client OpenAPI | 8 | 8 | 0 | 0 | 100.00% | 100.00% |
| Admin API | 38 | 31 | 7 | 0 | 81.58% | 90.79% |
| Console API | 28 | 25 | 3 | 0 | 88.89% | 94.44% |
| Auth API | 4 | 0 | 1 | 3 | 0.00% | 12.50% |
| Total | 78 | 64 | 11 | 3 | 86.30% | 93.15% |

Partial rows are documented in the matching scenario document. The current
partial set is limited to operations whose remaining success paths mutate
shared runtime/storage state, require publish-pipeline plugin data, require a
data-plane publisher binding not yet present in standalone IT, or require an
external LLM provider.

External protocol adaptors are tracked separately from the Nacos API coverage
totals because they run in independent web contexts. The ARD adaptor currently
has three covered scenario rows and one partial row; the remaining gap is a
live standalone-adaptor conformance suite.

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
verified through the current gray model. Batch delete and export-by-id scenarios
verify that storage IDs remain scoped by the requested namespace, and clone
scenarios verify that storage IDs are resolved only within the requested source
namespace before writing to the target namespace. Removed pre-3.0 compatibility
migration paths, including empty-tenant storage migration and legacy
`config_info_beta` / `config_info_tag` old-table migration, are not counted as
missing OpenAPI IT coverage.

Skill upload precheck response-shape coverage is maintained by the admin and
console Skill upload scenario rows, including `maxPublishedVersion` and
`targetVersion`. Those rows also cover the batch upload per-item `success`,
`errorCode`, and `errorMessage` contract. Contract-only field changes do not
alter the scenario-row totals above.

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
Console facade.

## Coverage Documents

| API surface | Scenario document | Test package |
| --- | --- | --- |
| Client OpenAPI | [CLIENT_API_TEST_SCENARIOS.md](CLIENT_API_TEST_SCENARIOS.md) | `src/test/java/com/alibaba/nacos/test/openapi/client` |
| Admin API | [ADMIN_API_TEST_SCENARIOS.md](ADMIN_API_TEST_SCENARIOS.md) | `src/test/java/com/alibaba/nacos/test/adminapi` |
| Console API | [CONSOLE_API_TEST_SCENARIOS.md](CONSOLE_API_TEST_SCENARIOS.md) | `src/test/java/com/alibaba/nacos/test/consoleapi` |
| Auth API | [AUTH_API_TEST_SCENARIOS.md](AUTH_API_TEST_SCENARIOS.md) | `src/test/java/com/alibaba/nacos/test/adminapi/auth` |
| AI Registry Adaptor | [AI_REGISTRY_ADAPTOR_API_TEST_SCENARIOS.md](AI_REGISTRY_ADAPTOR_API_TEST_SCENARIOS.md) | `ai-registry-adaptor/src/test/java/com/alibaba/nacos/airegistry` |
