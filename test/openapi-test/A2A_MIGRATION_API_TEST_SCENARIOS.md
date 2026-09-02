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

# Historical A2A Migration OpenAPI IT Scenarios

This document freezes the public HTTP and standalone-server test plan for the
[Historical A2A Upgrade Migration Spec](../../specs/en/ai/a2a-upgrade-migration-spec.md).
It is intentionally separate from the current API-surface coverage totals until
the corresponding migration implementation and executable tests land.

## Rules

- `Planned` means the scenario contract is frozen but no executable test claims
  coverage yet. A later implementation commit changes status only after the
  test has run successfully.
- `Verified` means the stable executable test and its assigned component tests
  have passed. `Partial` records the exact directed evidence already obtained
  and the later commit that still owns the remaining assertions.
- Historical fixtures use documented Config coordinates and public setup
  helpers. Assertions use A2A, Agent, Search, RAD, Watch, Console, or Naming
  results and restart durability, not direct database rows.
- Every asynchronous condition uses bounded polling. A fixed sleep may pace a
  retry but is never the success condition.
- Each test creates isolated Namespaces and names, cleans them when safe, and
  leaves deliberately malformed fixtures isolated from other suites.
- Runtime, reconnect, redo, and directed cluster portions are assigned to the
  Java SDK matrix rather than duplicated as HTTP-only internal assertions.

## Standalone Matrix

| ID | Executable workflow and assertions | Primary test owner | Commit | Status |
| --- | --- | --- | --- | --- |
| `M-ST-01` | Seed multiple historical Namespaces, URL/SERVICE Agents, and Versions; enable `AUTO`; poll Admin/Console/RAD until each complete Agent appears; compare identity, enabled state, exact online Version set, latest, AgentCard, declared Endpoints, and Search eligibility. | `A2aMigrationAdminApiOpenApiITCase` plus the U2 page-size-2 directed backfill scenario. | U2/U3 | Verified |
| `M-ST-02` | During `SYNCING`, perform historical create, update, set-latest, and delete through Admin/Console; assert the historical response immediately, then boundedly poll canonical Agent/Search and verify final equality without resurrecting deleted content. | `A2aMigrationAdminApiOpenApiITCase`. | U3 | Verified |
| `M-ST-03` | Isolate malformed summary JSON, missing Version content, invalid name/Version, invalid latest, and same-name different canonical content; assert migration progress remains blocking, legacy valid resources remain readable, and no conflicting target is overwritten. | `A2aMigrationAdminApiOpenApiITCase` with documented Config fixtures. | U2/U3 | Verified |
| `M-ST-04` | Stop and restart the standalone server after observable Storage-prepared, Version-complete, and Resource-complete boundaries; assert idempotent convergence and that no canonical API returns a partial Agent. | Directed real-instance restart plus deterministic target-store boundary tests; U6 retains crash-at-each-boundary automation. | U2/U3/U6 | Partial |
| `M-ST-05` | For the same resource before and after cutover, cross-check legacy Admin/Console A2A, canonical Admin/Console Agent, generic/Agent Search, ARD catalog/artifact, RAD Discover, and HTTP Watch fingerprint/Discover refresh. | `A2aMigrationAdminApiOpenApiITCase` plus Java SDK terminal Watch scenario. | U3/U5 | Verified |
| `M-ST-06` | Publish historical gRPC single and complete batches and prove the historical exact-Version and canonical RAD services are both visible during migration. | Java SDK `A2aUpgradeMigrationJavaSdkITCase`; OpenAPI verifies canonical Runtime snapshots. | U4 | Verified |
| `M-ST-07` | Complete migration with shadow disabled; assert canonical RAD/Watch remains correct and direct historical Naming lookup is not promised to retain the new post-cutover publication. | Java SDK directed scenario plus OpenAPI RAD/Watch assertions. | U5/U6 | Partial: canonical terminal behavior is verified; the full frozen-shadow comparison remains U6. |
| `M-ST-08` | Complete migration with shadow enabled; publish, replace, and deregister an exact Version after cutover; compare normalized historical Naming and canonical Runtime snapshots. | Java SDK `A2aUpgradeMigrationJavaSdkITCase`; OpenAPI verifies Admin/Console Runtime views. | U5/U6 | Planned |
| `M-ST-09` | Inject required-mirror failure, recover it, disconnect/reconnect/redo a real client, and restart the server; assert no lost retained batch, no duplicate logical capacity, and final historical/canonical equality. | Opt-in Java SDK restart scenario; deterministic failure injection remains in unit tests. | U4/U6 | Partial: restart/redo and deterministic mirror failure are verified; the combined U6 fault run remains. |
| `M-ST-10` | Hold one generation in `QUIESCING`; assert legacy Admin/Console definition mutations return detail code `50105`, while GET/list, RAD Discover, HTTP Watch, and Endpoint register/deregister continue and converge. | `A2aMigrationAdminApiOpenApiITCase` plus Java SDK client. | U5 | Verified |

## Cross-Surface Assertions

The following assertions apply to every applicable scenario:

- `LEGACY` and explicit `CANONICAL` retain their static pre-migration behavior;
- a non-terminal marker never switches a static mode, while a terminal marker
  cannot be downgraded by local configuration;
- migration-owned canonical facts are complete and read-only until cutover;
- shadow Naming data never changes Search or emits an extra Watch change;
- unrelated Agent, AgentSpec, Skill, Prompt, MCP, Config, and Naming resources
  remain queryable; and
- error bodies retain the standard `Result<T>` shape and do not expose complete
  AgentCards, Endpoint metadata, publisher ids, or migration internals.

## Completion Record

Each implementation commit updates only its assigned rows after running the
focused OpenAPI IT selection. U6 changes all remaining applicable rows to
`Verified` only after the complete standalone and directed cluster evidence is
recorded in the PR validation summary.

U3 added a stable three-method OpenAPI suite that runs after a dedicated
CANONICAL-to-AUTO server restart with a two-entry reconciliation page. It
verifies Admin and Console historical mutations, exact Version/latest
convergence, migration-owned generic Agent write rejection (`50105`),
URL/SERVICE namespace isolation, malformed/missing/invalid Config recovery,
canonical conflict preservation, orphan removal, and Agent/generic Search,
ARD, RAD, and Console reads. The focused suite passed with bounded polling.
A separate real-instance scenario retained one migrated Agent across a graceful
server restart and revalidated historical A2A, canonical Agent, Agent Search,
and RAD Discover before cleanup.

U5 added a controlled standalone quiescing/terminal method and two opt-in
three-member Java SDK methods. The standalone flow proves definition-mutation
fencing with detail code `50105` while reads, Runtime publication, gRPC Watch,
HTTP Watch, replacement, deregistration, terminal legacy-facade publication,
Search, and Discover remain available. The cluster flow uses fixed-node A/B/C
readers plus a load-balanced HTTP reader during a real 0/3-to-3/3 rolling
upgrade, accepts an already-completed `CANONICAL` transition when the
short-lived `QUIESCING` state was not sampled, and then requires terminal
`CANONICAL` on every member. U6 still owns shadow=true and the combined
persistence/failover fault matrix.
