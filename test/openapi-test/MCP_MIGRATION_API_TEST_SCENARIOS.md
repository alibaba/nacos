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

# Historical MCP Migration OpenAPI IT Scenarios

This document owns the temporary historical MCP management migration matrix.
The stable API classes test only `LIFECYCLE_MANAGED` functionality; they do not
accept both pre-cutover and post-cutover outcomes.

The scenarios run only in `.github/workflows/migration-it.yml`. That workflow
uses an auth-disabled isolated server because authentication is independently
covered by the default-auth functional workflow.

## Phase Matrix

| Phase | Server fixture | Public assertions | Test owner | Status |
| --- | --- | --- | --- | --- |
| `syncing` | Start a clean standalone server with MCP reconciliation disabled and no permanent marker. | Historical Admin create/query remains authoritative; every standard lifecycle operation returns the controlled 409 cutover gate; Client `createDraft=true` returns the same controlled gate. | `McpMigrationAdminApiOpenApiITCase#testHistoricalAuthorityAndLifecycleGateDuringSyncing` | Covered |
| `managed` | Restart the same server with reconciliation enabled, a one-second test interval, and wait with a bounded deadline for the permanent marker. | The historical resource appears as an online lifecycle Version without changing its serving projection; a new Client lifecycle draft succeeds; final deletion removes the resource. | `McpMigrationAdminApiOpenApiITCase#testHistoricalResourceIsManagedAfterCutover` | Covered |

## Isolation Rules

- Neither phase is enabled during normal Failsafe discovery; the workflow must
  pass `nacos.mcp.migration.phase` explicitly.
- The two phases share one durable standalone data directory, so the managed
  assertion proves reconciliation rather than creating a new canonical fixture.
- Success is asserted through Admin and Client APIs. Logs are used only as a
  bounded readiness signal and are uploaded for diagnosis.
- The normal OpenAPI, Java SDK, and Maintainer SDK jobs start testing only after
  stable managed readiness and contain no dual-outcome cutover assertions.
- Lease races, partial storage failures, mixed-member capability gates, and
  reconciliation retry internals remain deterministic component-test scenarios.

This workflow and document can be removed together when the supported rolling
upgrade window and historical MCP reconciliation implementation are retired.
