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
| Admin API | 35 | 30 | 5 | 0 | 85.71% | 92.86% |
| Console API | 27 | 25 | 2 | 0 | 92.59% | 96.30% |
| Total | 70 | 63 | 7 | 0 | 90.00% | 95.00% |

Partial rows are documented in the matching scenario document. The current
partial set is limited to operations whose remaining success paths mutate
shared runtime/storage state, require publish-pipeline plugin data, or require
an external LLM provider.

Config scenario rows cover the current 3.3 Config model. Blank or omitted
namespace inputs are expected to use `public`, and beta/tag gray behavior is
verified through the current gray model. Removed pre-3.0 compatibility
migration paths, including empty-tenant storage migration and legacy
`config_info_beta` / `config_info_tag` old-table migration, are not counted as
missing OpenAPI IT coverage.

## Coverage Documents

| API surface | Scenario document | Test package |
| --- | --- | --- |
| Client OpenAPI | [CLIENT_API_TEST_SCENARIOS.md](CLIENT_API_TEST_SCENARIOS.md) | `src/test/java/com/alibaba/nacos/test/openapi/client` |
| Admin API | [ADMIN_API_TEST_SCENARIOS.md](ADMIN_API_TEST_SCENARIOS.md) | `src/test/java/com/alibaba/nacos/test/adminapi` |
| Console API | [CONSOLE_API_TEST_SCENARIOS.md](CONSOLE_API_TEST_SCENARIOS.md) | `src/test/java/com/alibaba/nacos/test/consoleapi` |
