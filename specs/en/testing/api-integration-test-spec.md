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

# API Integration Test Spec

This spec defines the required integration-test model for Nacos HTTP APIs. It
applies to Open API, Admin API, Console API, and Auth API changes that are
exposed through HTTP controllers or generated OpenAPI documents.

The goal is API scenario coverage. It is not line coverage or branch coverage.
An API IT must prove the externally visible contract of the deployed server.

## 1. Scope

The primary API IT location is `test/openapi-test`. Tests in this module start
from a standalone Nacos server and call APIs as external HTTP clients.

This spec covers:

- adding, changing, deleting, or deprecating HTTP API routes;
- changing request parameters, validation rules, default values, request body
  shapes, uploaded files, headers, or query serialization;
- changing response status, `Result<T>` body shape, download or streaming
  response shape, error code, message, or domain fields;
- changing API-visible business behavior, side effects, authorization,
  compatibility handling, or generated OpenAPI/Swagger definitions.

Unit tests and controller tests may still be necessary, but they do not replace
API ITs for user-visible HTTP behavior.

## 2. API Change Rule

Before implementing an API addition, modification, deletion, or deprecation, the
change owner must perform an IT impact analysis:

1. Identify the affected API surface and current IT class, if any.
2. Read the controller, form/request model, validators, response model, service
   path, exception handling, and matching domain specs.
3. Build a scenario matrix for expected capability, boundary/validation
   behavior, and exception/error handling.
4. Add, update, or remove `test/openapi-test` cases to match the new API
   contract in the same change set.
5. Update the coverage registry under `test/openapi-test`, such as
   `API_TEST_COVERAGE.md` and the surface-specific scenario document.

If the functional success path is not practical in the standalone IT
environment, the IT must still cover validation, boundary, response-contract,
and controlled error scenarios when possible. The skipped functional path and
reason must be documented in the scenario registry or class Javadoc.

## 3. Required Scenario Groups

Every API IT should cover the following scenario groups unless the group is not
observable for the API. Skipped groups must be explained.

### 3.1 Expected Capability

The test must prove that the API does what it is designed to do. Prefer
create-then-query, update-then-query, publish-then-read, delete-then-absent, or
list/filter assertions that verify the durable side effect or returned domain
state.

Assertions must check important response fields, not only HTTP success.

### 3.2 Boundary And Validation

The test must cover important request boundaries derived from code analysis,
including required fields, optional defaults, empty strings, enum values,
pagination, namespace/group/name normalization, malformed JSON, upload
boundaries, version selection, filters, and accepted-but-ignored parameters.

For large input spaces, cover contract-equivalence classes and record residual
risk in the scenario documentation.

### 3.3 Exception And Error Handling

The test must verify controlled behavior for likely failure branches:

- validation failures should return HTTP 400 rather than HTTP 500;
- not-found, conflict, disabled, unauthorized, or invalid-state errors should
  match the controller contract;
- JSON error bodies should preserve the expected `code`, `message`, and `data`
  shape when the API uses `Result<T>`;
- download or streaming APIs should still return controlled error responses for
  invalid inputs when the implementation exposes them.

## 4. Test Organization

API ITs should be organized by API surface and domain:

- Client OpenAPI: `com.alibaba.nacos.test.openapi.client.<domain>`
- Admin API: `com.alibaba.nacos.test.adminapi.<domain>`
- Console API: `com.alibaba.nacos.test.consoleapi.<domain>`
- Auth API: `com.alibaba.nacos.test.authapi.<domain>` when Auth API ITs are
  added

Prefer one API endpoint or closely coupled API workflow per test class. A class
may use helper APIs to create prerequisites or cleanup data, but its documented
scenario matrix should stay focused on the API it names.

Shared HTTP client creation, base URL construction, JSON assertions, retry
helpers, and cleanup should live in base classes when more than one IT class
needs them.

## 5. Test Data And Runtime Rules

API ITs must keep data isolated and repeatable:

- generate unique resource names for mutable resources;
- prefer public namespace defaults only when the API contract supports them;
- cleanup created resources in `finally` blocks or test tear-down helpers;
- make cleanup tolerant of resources already being absent;
- avoid mutating shared runtime state unless the API under test requires it and
  the test restores the previous state;
- use bounded retries only for asynchronous server effects.

The standalone test environment normally disables auth. Auth-enabled scenarios
must add token handling deliberately and isolate assumptions from auth-disabled
API contract tests.

## 6. API Deletion And Deprecation

When an API route is deleted, the matching IT coverage must be removed or
updated in the same change. If compatibility behavior intentionally remains,
add an IT for the deprecated or compatibility route and document its migration
expectation.

When a request or response field is removed, renamed, or changes semantics, the
IT must verify the new contract and, when relevant, the compatibility or
rejection behavior for the old contract.

## 7. Scenario Documentation

Each API IT must make its scenario set visible to maintainers. Use a compact
class Javadoc `Scenario coverage` section for small classes, or update a
Markdown scenario registry under `test/openapi-test` for larger surfaces.

The documentation must state what is verified, not merely list test method
names. It must also record intentionally uncovered branches, accepted-but-ignored
parameters, and standalone-environment limitations.

## 8. Validation

For API IT changes, run formatting and compilation for `test/openapi-test`. When
a standalone Nacos server is available, run the relevant Failsafe IT selection
or the full surface selection.

Minimum validation for documentation-only IT registry changes is license and
format checks for the affected module.

## 9. AI Resource Search And Agent Scenarios

When shared Search Core, Agent projection, or ARD Agent representation changes,
the OpenAPI IT scenario matrix covers at least:

- RAD and resource-specific Search still use the base index when ARD is disabled
  and `nacos.ai.resource.search.enabled=true`;
- Agent-name literal contains, tag ALL, protocol ANY, combined AND, case rules,
  literal `%`, `_`, and `\\`, first/middle/last/out-of-range pages, and correct
  totals;
- bounded convergence after create, metadata update, Version
  publish/online/offline/delete, and latest/label changes;
- Endpoint register/deregister/heartbeat changes Discover only and does not
  change the catalog Search document;
- successful current snapshots without mixing before `AUTO` or `INDEX`
  readiness, convergence to complete results, and the compatibility path for
  `SCAN`;
- generic Search restricted to Agent, AgentSpec, Skill, Prompt, or MCP matches
  the corresponding resource-specific Search eligibility, visibility, and
  currentness; and
- ARD pure-A2A, multi-protocol, and A2A-only-on-an-older-online-Version type
  filtering, primary representation, stable identifier,
  representation-specific artifact URL, offline/digest invalidation, and
  Runtime-state exclusion.

Tests of asynchronous indexing use only bounded polling of public API results;
they do not depend on a fixed sleep, internal database rows, or task execution
order.

## 10. Agent HTTP Watch Scenarios

When the Agent HTTP batch-long-poll Watch binding changes, OpenAPI IT covers at
least:

- one request carrying multiple Agent Watch items and returning only the
  changed caller item ids after definition, latest/label, runtime Endpoint,
  liveness, and visibility changes;
- timeout returning `changed=false`, followed by immediate reuse with the next
  generation and complete list;
- add/remove generation changes, a late prior response, duplicate item ids,
  mixed namespaces, empty/oversized batches, malformed fingerprints, timeout
  bounds, form-size bounds, and the configured soft Watch capacity;
- missing or invalid `X-Nacos-Client-Id` and `Request-Module`, request-level AI
  read denial, and no descriptor/Endpoint/per-item authorization data in a
  successful response;
- changed ids being re-readable only through ordinary authorized Discover,
  including invisible and missing resources returning the standard controlled
  result; and
- server restart and repeated long polls converging through bounded public API
  polling without depending on socket-cancel timing, one fixed server node, or
  internal waiter state.

## 11. MCP Migration And Lifecycle Scenarios

When MCP lifecycle hosting is implemented, the OpenAPI IT matrix must cover at
least:

- existing Admin and Console create/update/query/list/delete request and
  response shapes during `SYNCING` and after `LIFECYCLE_MANAGED`, including
  the compatibility-only same-Version overwrite and latest flag;
- name-only, name-plus-ID, and historical ID-only management inputs, including
  protocol authentication followed by exact canonical re-authorization for
  ID-only input and controlled missing, duplicate, or conflicting Resource aliases;
- new Version list/detail and draft, submit, reviewed/publish, force-publish,
  redraft, online/offline, custom-label, and invalid-state paths, with equivalent
  Admin and Console semantics;
- an enabled Resource exposing only online Versions through the unchanged
  historical serving projection, and draft/reviewing/reviewed/offline Versions
  only through new management reads;
- legacy fixtures remaining wholly visible during `SYNCING`, idempotent
  asynchronous reconciliation, all-member management gating, zero-difference
  automatic cutover, and restart persistence;
- unchanged Manifest/Server/Tools/Resources Config coordinates and bytes, with
  no Naming Service, instance, frontend/backend, or Runtime metadata mutation
  caused by reconciliation;
- Manifest-last publish, offline removal from the serving view while content
  and Direct Service remain available, and old Config/Naming consumers never
  observing incomplete Version content;
- Version and full Resource deletion, Manifest-first stop-serving behavior,
  Resource/Version row preservation after Direct or content cleanup failure,
  retry by deprecated ID after Manifest removal, and no accidental delete of an
  ordinary referenced Service or client Runtime state;
- missing content, invalid Manifest, conflicting row, and partial storage
  deletion returning controlled behavior while preventing managed cutover; and
- generic and MCP-specific Search using canonical `mcpName`, durable
  asynchronous convergence and historical ID-keyed cleanup, plus unified Import
  and Registry-adaptor compatibility across the management transition.

Migration tests assert only public behavior and durable restart outcomes. They
may seed documented legacy fixtures through test setup, but must not use direct
database-row assertions as the success contract. Every asynchronous condition
uses bounded polling rather than a fixed sleep.

## 12. Historical A2A Upgrade Migration Scenarios

When the historical A2A upgrade state machine, reconciliation, or Runtime
dual-materialization behavior changes, OpenAPI IT freezes and records these
standalone scenarios from the
[Historical A2A Upgrade Migration Spec](../ai/a2a-upgrade-migration-spec.md):

| ID | Public scenario |
| --- | --- |
| `M-ST-01` | Multiple Namespaces, Agents, Versions, and URL/SERVICE definitions migrate completely and retain identity, latest, descriptor, declared Endpoints, and enabled state. |
| `M-ST-02` | Historical create, update, set-latest, and delete during `SYNCING` converge without changing the already returned historical result. |
| `M-ST-03` | Malformed JSON, missing Version, invalid name/Version, and a conflicting independent canonical Agent block cutover while historical reads remain available. |
| `M-ST-04` | Restart after Storage, Version-row, and Resource-row boundaries recovers idempotently and never exposes a partial Agent. |
| `M-ST-05` | Historical A2A, Admin, Console, ARD/Search, RAD Discover, and Watch agree before and after cutover. |
| `M-ST-06` | Historical gRPC single/batch Endpoint publication is visible in both historical and canonical Runtime layouts during migration. |
| `M-ST-07` | With shadow disabled, canonical RAD remains available after cutover and the old Gateway is no longer promised visibility. |
| `M-ST-08` | With shadow enabled, canonical exact-Version RAD and the old Gateway expose equivalent normalized Runtime snapshots after cutover. |
| `M-ST-09` | Mirror failure/retry, client disconnect/reconnect/redo, and server restart converge without duplicate logical capacity or lost retained publication. |
| `M-ST-10` | Quiescing returns the retryable migration error for definition mutations while queries, Discover, Watch, and Endpoint operations continue. |

`test/openapi-test/A2A_MIGRATION_API_TEST_SCENARIOS.md` assigns the executable
HTTP portions and records scenarios that require the Java SDK or a directed
cluster fixture. Tests may seed documented historical Config through setup, but
success is asserted through public APIs, durable restart behavior, and bounded
polling rather than direct row inspection or fixed sleep.
