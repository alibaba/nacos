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

## Coverage Documents

| API surface | Scenario document | Test package |
| --- | --- | --- |
| Client OpenAPI | [CLIENT_API_TEST_SCENARIOS.md](CLIENT_API_TEST_SCENARIOS.md) | `src/test/java/com/alibaba/nacos/test/openapi/client` |
| Admin API | [ADMIN_API_TEST_SCENARIOS.md](ADMIN_API_TEST_SCENARIOS.md) | `src/test/java/com/alibaba/nacos/test/adminapi` |
| Console API | [CONSOLE_API_TEST_SCENARIOS.md](CONSOLE_API_TEST_SCENARIOS.md) | `src/test/java/com/alibaba/nacos/test/consoleapi` |
