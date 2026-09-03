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
Rows remain `Planned` until their executable test has run successfully. U4
standalone, U5 cluster-cutover, and U6 frozen-shadow/fault rows are now
`Verified`; the optional crash-at-each-persistence-boundary hardening remains
listed separately in the OpenAPI matrix.

## Standalone Client Responsibilities

The `A2aUpgradeMigrationJavaSdkITCase` uses public `A2aService`,
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

## Standalone Runtime Migration Matrix

| ID | Observable assertion | Commit | Status |
| --- | --- | --- | --- |
| `M-SDK-01` | In `AUTO/SYNCING`, an old A2A single Endpoint publication appears in both the historical exact-Version Naming service and canonical RAD Discover. | U4 | Verified |
| `M-SDK-02` | A complete batch replacement updates both layouts atomically from the client's point of view; URI/path projection is equal in both reads. | U4 | Verified |
| `M-SDK-03` | Exact Versions `1.0.0` and `2.0.0` use independent child publishers and remain independently discoverable and deregistrable. | U4 | Verified |
| `M-SDK-04` | With a server soft watermark of three logical publications, dual physical materialization is charged once; the fourth publication is rejected with `OVER_THRESHOLD`, leaves no retry/redo state, and succeeds after one slot is released. | U4 | Verified |
| `M-SDK-05` | Closing the publishing SDK connection removes both physical layouts; a fresh publisher can reuse the same logical identity. | U4 | Verified |
| `M-SDK-06` | After a real standalone server stop and restart with the same data directory, the same live SDK process redoes two exact-Version publications into both layouts; later deregistration of one Version does not affect the other. | U4 | Verified |
| `M-SDK-07` | Publish while `SYNCING`, cut over with frozen shadow `false`, then replace and deregister through the same connection; canonical RAD remains authoritative and the obsolete historical child is removed, including retry after an injected cleanup failure. | U6 | Verified |
| `M-SDK-08` | Publish while `SYNCING`, cut over with frozen shadow `true`, then replace, deregister, disconnect, and redo; historical Naming and canonical RAD retain equal exact-Version snapshots without duplicate Watch callbacks. | U6 | Verified |
| `M-SDK-09` | After a terminal marker, restart a canonical-aware server locally configured as `LEGACY`; old A2A definition and exact-Version Endpoint calls still use canonical authority plus the frozen shadow policy, and the marker remains terminal. | U6 | Verified |

The default standalone run completed `M-SDK-01..05` with two executed tests
and one opt-in restart test skipped. The separately controlled real-restart
run completed `M-SDK-06` with the server unavailable for about one minute.
Both runs used the release distribution and a publication soft watermark of
three. During this run, rapid historical Version publication exposed a
Version-first/Resource-last reconciliation race: an exact unowned canonical
Version subset could be left before the source fence changed. U4 fixed the
resume rule to accept only an exact canonical subset with no extra or altered
row; conflicting content or metadata still blocks migration.

## Three-Member Directed Matrix

The opt-in cluster methods in `A2aUpgradeMigrationJavaSdkITCase` use three
fixed server addresses plus one load-balanced client endpoint. They never
assume that consecutive load-balanced requests reach the same member.

| ID | Topology, mutation, and observable assertion | Commit | Status |
| --- | --- | --- | --- |
| `M-CL-01` | Exercise 0/3, 1/3, 2/3, and 3/3 migration-capable members; legacy SDK writes and reads stay authoritative until every ability, policy, data, Search, and Runtime gate passes. | U5 | Verified |
| `M-CL-02` | Send a historical definition write to A, reconcile under the cluster lease, and poll another member's canonical RAD read until the complete Agent appears; the old response remains successful throughout. | U5 | Verified |
| `M-CL-03` | Restart the lease owner, a non-owner, the Config mutation/notification ingress, and the Endpoint publication owner in separate rounds; verify lease transfer, idempotent progress, available legacy reads, and eventual convergence. External-MySQL Config has no Config Raft leader; embedded Config leader failover remains owned by the Config consistency suite. | U5/U6 | Verified |
| `M-CL-04` | Add/remove a member during `QUIESCING`, suppress or delay an ACK, and delay marker observation; assert timeout returns to `SYNCING` or all nodes reach one terminal authority, never split writes. | U5 | Verified |
| `M-CL-05` | Mutate historical Config through A, reconcile under the cluster lease, and compare legacy plus canonical reads from A/B/C after bounded convergence. | U5 | Verified |
| `M-CL-06` | Register an exact-Version Endpoint through A, which owns the historical A2A Naming publication; compare that owner and the B/C Distro replicas with canonical Runtime from all members, then replace and deregister. | U5 | Verified |
| `M-CL-07` | During terminal marker propagation, alternate definition, Discover, and Watch refresh requests through the load balancer; every successful response is one of two already-proved equivalent projections. | U5 | Verified |
| `M-CL-08` | Run complete rolling upgrades with frozen shadow `false` and `true`; after cutover, cross-check canonical RAD and the documented direct historical Naming behavior. | U6 | Verified |
| `M-CL-09` | Before terminal cutover, return all members to `LEGACY` and prove old authority remains writable, then resume `AUTO`; after terminal cutover, restart a canonical-aware member locally configured as `LEGACY` and prove the permanent marker still forces canonical authority. A truly legacy-only binary is rejected by the deployment/runbook gate because it cannot interpret the marker and cannot be safely admitted by another server process. | U6 | Verified |
| `M-CL-10` | In both shadow policies, execute ordinary Agent, Skill, Prompt, AgentSpec, MCP, and Naming publish/query/subscribe flows and verify no migration marker, capacity, or duplicate event leaks into them. | U6 | Verified |

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

## U6 Frozen Execution Plan

Before U6 production changes, the following order is fixed:

1. component tests reproduce and then prevent a stale historical child when a
   connection crosses from dual materialization to terminal `shadow=false`;
2. low-cardinality metric tests cover migration state, reconciliation,
   cutover/rollback, primary/secondary writes, retry, pending gauges, and write
   latency without resource identities or payloads in labels;
3. standalone tests run independent frozen-shadow `false` and `true` plans,
   including post-cutover replace/deregister and both Watch transports;
4. three-member tests run both plans, pre-terminal withdrawal to `LEGACY`,
   resumed `AUTO`, canonical-aware post-terminal rollback, Config
   mutation/notification ingress and publication-owner restart, and fixed-node
   plus load-balanced reads; and
5. the existing five-resource transport matrix and ordinary Naming lifecycle
   run once in `AUTO/SYNCING` and once after terminal cutover.

The migration workflow executes the terminal flow twice with isolated internal
fixtures: frozen shadow `true` and frozen shadow `false`. It also restarts each
terminal plan with local `LEGACY` configuration and runs `M-SDK-09`. Directly
rewriting the internal marker between the two plans is strictly a CI fixture
reset; it is not a supported operator downgrade and does not exercise the
product transition API.

The OpenAPI-to-SDK cutover hand-off is deterministic rather than timer-dependent. After
observing `QUIESCING`, the OpenAPI phase restores its malformed historical-source blocker.
The Java SDK phase creates both HTTP and gRPC subscriptions, proves that definition writes
are fenced and Runtime replacement remains available, and only then removes the blocker.
The next scheduled gate can therefore complete the same generation regardless of Maven
compilation or process-start latency.

No test starts a truly legacy-only node after terminal cutover: that node has
no marker logic, so executing the scenario would deliberately create the split
authority the deployment gate exists to prevent. The stable assertion is that
every canonical-aware binary obeys the terminal marker regardless of its local
mode, while the runbook explicitly blocks older binaries.

## Completion Record

U4 recorded successful standalone dual-publication, capacity rejection,
disconnect cleanup, reconnect, and redo evidence in `M-SDK-01..06`.

U5 used a real three-member MySQL cluster and two binaries. It observed the
0/3, 1/3, 2/3, and 3/3 rolling-upgrade states, proved that incomplete member
ability holds `SYNCING`, then upgraded the final member while an external Java
SDK process retained fixed-node A/B/C readers and an A/B/C load-balanced
reader. The same run proved gRPC and HTTP Watch delivery, complete Runtime
replacement, one business callback per listener, cross-member definition and
Runtime equality, terminal `CANONICAL`, and deregistration. A separate
three-member method proved independent exact-Version historical/canonical
layouts, replacement, and cleanup from every member. Directed component and
real-instance evidence also covered policy mismatch, timeout rollback, member
removal rollback, generation replacement, generation ACK, and final
validation.

The external rolling-upgrade handshake permits five minutes because cold node
startup and binary replacement are fixture operations, not a product
convergence SLA. Since all members can observe and acknowledge one generation
before a polling client samples it, the intermediate assertion accepts either
`QUIESCING` or the already reached `CANONICAL`; the final assertion remains
strictly `CANONICAL`.

For historical A2A Runtime, the SDK connection that registers through node A
also owns the corresponding Naming client publication; B and C are Distro
replicas. Treating B as an independently selected Naming responsibility would
misstate this legacy route.

U6 completed independent standalone and three-member external-MySQL plans for
frozen shadow `false` and `true`. The plans covered pre-terminal withdrawal to
`LEGACY`, resumed `AUTO`, fixed-node and load-balanced reads, gRPC and HTTP
Watch, post-terminal local-`LEGACY` restart, Endpoint-owner restart while the
same SDK process stayed alive, and a peer restart followed by definition and
Runtime mutations. Stable publisher identity plus replica promotion prevented
duplicate exact-Version children after reconnect. The terminal transport
matrix also passed for Agent, Skill, Prompt, AgentSpec, MCP, and ordinary
Naming in `GRPC`, `HTTP`, and `AUTO` modes.

The external-MySQL topology has no Config Raft leader. Its directed failure
round therefore restarts the node receiving Config mutations and notifications;
embedded Config leader failover remains covered by the Config consistency
suite rather than being misrepresented as an A2A migration responsibility.

Final U6 validation ran 2820 `ai` unit tests with zero failures or errors and
two pre-existing skips. The migration metrics class and all six modified
migration/runtime classes have no missed executable U6 line; the focused child
publisher class reached 109/109 lines. `AiGrpcClientTest`, OpenAPI/Java SDK test
compilation, Spotless, Checkstyle, SpotBugs, RAT, and all 27 workflow shell
blocks also passed.
