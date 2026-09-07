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

# Historical MCP Migration Java SDK IT Scenarios

`McpUpgradeMigrationJavaSdkITCase` is a directed, temporary migration class.
It is not enabled by the normal Java SDK suite and is orchestrated only by
`.github/workflows/migration-it.yml`.

| Phase | Java Client and Maintainer SDK assertions | Status |
| --- | --- | --- |
| `syncing` | `AiService` keeps direct-online historical release/query semantics and maps `createDraft=true` to the controlled cutover conflict. The historical resource is intentionally retained across the server restart. | Covered |
| `managed` | `AiService` still reads the serving resource, `McpMaintainerService` reads the reconciled online Version, `AiService` creates a managed draft Version, and the Maintainer SDK removes the fixture. | Covered |

Both phases use the default JSON adapter. Stable MCP Java Client and Maintainer
SDK functionality runs with both default and Jackson 3 adapters in the normal
functional workflow; the temporary server-side reconciliation transition is
not duplicated per adapter.

The class, workflow steps, and this document share the same retirement boundary:
remove them when historical MCP management reconciliation is no longer supported.
