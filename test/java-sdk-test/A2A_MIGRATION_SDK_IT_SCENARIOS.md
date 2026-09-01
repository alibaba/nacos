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

# Historical A2A Migration Java SDK IT Scenarios

This document freezes the real-client, Runtime, redo, Watch, and cluster test
plan for the
[Historical A2A Upgrade Migration Spec](../../specs/en/ai/a2a-upgrade-migration-spec.md).
Rows remain `Planned` until their executable test has run successfully.

## Standalone Client Responsibilities

The planned `A2aUpgradeMigrationJavaSdkITCase` uses public `A2aService`,
`AiService`, `NamingService`, and both RAD Watch transports against an external
standalone server. It complements the OpenAPI `M-ST-01..10` matrix by verifying:

- legacy AgentCard reads/subscriptions and canonical Discover/Watch converge on
  the same definition before and after the terminal marker;
- gRPC single/batch exact-Version publications create the required canonical
  mirror during `SYNCING` and honor the frozen historical shadow after cutover;
- logical publication capacity is charged once although two physical child
  publishers exist;
- replace, deregister, disconnect, reconnect, redo, client shutdown, and server
  restart clean or restore both layouts without losing another Version; and
- a retryable quiescing definition error reaches the public SDK as a controlled
  `NacosException`, while Runtime and read operations continue.

The opt-in restart scenario stops only the server process owned by its fixture,
uses explicit bounded deadlines, and restores it in `finally`.

## Three-Member Directed Matrix

The planned `A2aMigrationClusterJavaSdkITCase` uses three server addresses and
a load-balanced client endpoint. It never assumes that consecutive requests
reach the same member.

| ID | Topology, mutation, and observable assertion | Commit | Status |
| --- | --- | --- | --- |
| `M-CL-01` | Exercise 0/3, 1/3, 2/3, and 3/3 migration-capable members; legacy SDK writes and reads stay authoritative until every ability, policy, data, Search, and Runtime gate passes. | U5 | Planned |
| `M-CL-02` | Send a historical definition write to A, elect B as lease owner, and poll C's canonical Admin/RAD read until the complete Agent appears; the old response remains successful throughout. | U5 | Planned |
| `M-CL-03` | Restart the lease owner, a non-owner, the Config leader, and the Naming responsibility member in separate rounds; verify lease transfer, idempotent progress, available legacy reads, and eventual convergence. | U5/U6 | Planned |
| `M-CL-04` | Add/remove a member during `QUIESCING`, suppress or delay an ACK, and delay marker observation; assert timeout returns to `SYNCING` or all nodes reach one terminal authority, never split writes. | U5 | Planned |
| `M-CL-05` | Mutate historical Config through A, reconcile on B, and compare legacy plus canonical reads from A/B/C after bounded convergence. | U5 | Planned |
| `M-CL-06` | Register an exact-Version Endpoint through A while Naming responsibility is B; compare historical and canonical Service results from all members, then replace and deregister. | U5 | Planned |
| `M-CL-07` | During terminal marker propagation, alternate definition, Discover, and Watch refresh requests through the load balancer; every successful response is one of two already-proved equivalent projections. | U5 | Planned |
| `M-CL-08` | Run complete rolling upgrades with frozen shadow `false` and `true`; after cutover, cross-check canonical RAD and the documented direct historical Naming behavior. | U6 | Planned |
| `M-CL-09` | Before terminal cutover, return all members to `LEGACY` and prove old authority remains writable; after terminal cutover, reject a legacy-only member and allow rollback only to a canonical-aware binary. | U6 | Planned |
| `M-CL-10` | In both shadow policies, execute ordinary Agent, Skill, Prompt, AgentSpec, MCP, and Naming publish/query/subscribe flows and verify no migration marker, capacity, or duplicate event leaks into them. | U6 | Planned |

## Transport And Failure Matrix

Every applicable standalone and cluster scenario is run with:

| Dimension | Values |
| --- | --- |
| Definition client | Legacy gRPC SDK, Admin HTTP, Console HTTP |
| Canonical read | Agent Admin/Console, RAD gRPC, RAD HTTP, ARD HTTP |
| Watch | Explicit gRPC and explicit HTTP; AUTO is a routing regression only |
| Runtime | Register, complete replace, deregister, disconnect, reconnect/redo, expiry, server restart |
| State | `LEGACY`, explicit `CANONICAL`, `AUTO/SYNCING`, `AUTO/QUIESCING`, terminal `CANONICAL` |
| Data | URL/SERVICE, multiple Namespaces, multiple Versions, missing/malformed/conflicting fixture |
| Shadow | Frozen `false` and `true` |

Business errors never trigger a cross-transport retry. Late or duplicate Watch
hints are resolved by authoritative Discover and fingerprint equality. Cleanup
is idempotent and does not depend on one child publisher executing first.

## Completion Record

U4 records successful standalone dual-publication, failure, reconnect, and redo
evidence. U5 records quiescing and `M-CL-01..07`. U6 records both shadow plans,
rollback boundaries, unrelated-resource regression, and the final complete
suite before changing the relevant rows to `Verified`.
