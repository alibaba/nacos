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

# Historical A2A Upgrade Migration Spec

| Item | Value |
| --- | --- |
| Status | Experimental upgrade contract |
| Activation | `nacos.ai.a2a.compatibility.mode=AUTO` |
| Source versions | Nacos 3.0 through 3.2 historical A2A storage |
| Removal target | Nacos 4.0 migration implementation |

This spec defines the one-time upgrade from historical A2A Config definitions
and exact-Version Naming services to the canonical Agent and RAD model. It
refines the [A2A Agent Spec](a2a-agent-spec.md), the
[Agent Management Spec](agent-management-spec.md), and the
[Agent Storage Spec](agent-storage-spec.md).

The migration implementation is temporary compatibility code. Canonical Agent,
Agent Version, AI Storage, RAD Runtime, and the pure AgentCard adapter remain
long-term capabilities and are not removed with this migration.

## 1. Compatibility Modes And Authority

`nacos.ai.a2a.compatibility.mode` remains case-insensitive and defaults to
`CANONICAL`:

| Mode | Definition authority | Runtime publication |
| --- | --- | --- |
| `LEGACY` | Historical A2A Config definitions. No migration state is created. | Historical exact-Version Naming service only. |
| `CANONICAL` | Canonical Agent Resource, Version, and AI Storage. No historical scan is started. | Canonical RAD Runtime only. |
| `AUTO` | Historical definitions remain authoritative until a permanent migration marker reaches `CANONICAL`. | Migration-state-dependent dual materialization defined in section 6. |

Before terminal completion, changing every member back to `LEGACY` stops the
migration and preserves historical authority. A non-terminal marker does not
override an explicit `LEGACY` or `CANONICAL` mode. A persisted terminal
`CANONICAL` marker has higher priority than local mode configuration: a capable
member that has observed it must permanently use canonical authority in that
process and must not resume a legacy-only write path even if the marker is
later deleted or local configuration changes.

`AUTO` does not perform request-level fallback, merged reads, or definition
dual writes. It selects one complete definition authority for every legacy A2A
request. Runtime dual materialization is a separate connection-state
compatibility behavior and never creates a second definition authority.

## 2. Configuration And Internal State

### 2.1 Configuration

| Property | Default | Rule |
| --- | ---: | --- |
| `nacos.ai.a2a.compatibility.mode` | `CANONICAL` | `AUTO` explicitly authorizes this migration. |
| `nacos.ai.a2a.migration.legacy-naming-shadow-enabled` | `false` | Whether exact-Version historical Naming services remain as a shadow after cutover. The value is frozen in the migration marker. |
| `nacos.ai.a2a.migration.reconciliation.interval-seconds` | `300` | Full reconciliation interval. |
| `nacos.ai.a2a.migration.reconciliation.page-size` | `100` | Bounded historical definition page size. The implementation applies a safe upper bound. |
| `nacos.ai.a2a.migration.quiescing-timeout-seconds` | `120` | Maximum time for one quiescing generation before returning to `SYNCING`. |

There is no separate migration enable switch. Members in one migration plan
must use `AUTO` and the same frozen shadow policy. A policy mismatch prevents
quiescing and cutover.

### 2.2 Internal Config Objects

The default Namespace and internal group `nacos_internal` contain at most
three bounded control objects:

| DataId | Meaning | Authority |
| --- | --- | --- |
| `nacos.ai.a2a.migration.v1` | State, generation, frozen shadow policy, and completion time. | Authoritative state machine marker. |
| `nacos.ai.a2a.reconciliation.lease.v1` | Renewable single-writer lease. | Temporary ownership only. |
| `nacos.ai.a2a.reconciliation.progress.v1` | Bounded cursor, counters, conflicts, and recent error summaries. | Diagnostic only. |

Marker and lease changes use Config compare-and-set. An uncertain write result
is resolved by rereading the same object. No per-Agent migration row or
`ai_resource_task` migration task is created. Existing Search tasks retain
their independent contract.

The marker schema is:

```json
{
  "schemaVersion": 1,
  "state": "SYNCING | QUIESCING | CANONICAL",
  "generation": "opaque-generation",
  "legacyNamingShadow": false,
  "startedAt": 0,
  "updatedAt": 0,
  "completedAt": null
}
```

`completedAt` is required in `CANONICAL`. Progress is bounded and must not grow
linearly with the number of Agents.

## 3. State Machine And Cluster Ability

```text
ABSENT -- AUTO creates plan --> SYNCING -- all gates --> QUIESCING
                                  ^                        |
                                  | timeout/member change | all-member ACK
                                  +------------------------+ + final zero diff
                                                           |
                                                           v
                                                       CANONICAL
                                                        terminal
```

| State | Legacy A2A definition reads | Legacy A2A definition mutations | Endpoint operations |
| --- | --- | --- | --- |
| `SYNCING` | Historical Config. | Historical write first; successful writes schedule non-blocking single-resource reconciliation. | Historical primary plus required canonical mirror. |
| `QUIESCING` | Historical Config. | Rejected with retryable detail error `AGENT_MIGRATION_IN_PROGRESS` (`50105`). | Continue dual materialization without interruption. |
| `CANONICAL` | Canonical Agent projection. | Canonical Agent compatibility facade. | Canonical primary plus the marker's optional legacy shadow. |

The following member metadata is required before `QUIESCING`:

- `supportA2aMigrationV1=true`;
- `a2aMigrationPolicyHash`, derived from the mode, marker schema, and frozen
  shadow policy; and
- `a2aMigrationAck=<generation>:READY` after the member has installed the
  definition write fence and verified local target readability.

String server versions are not a substitute for these abilities. Missing,
invalid, inconsistent, or empty member metadata keeps `AUTO` in `SYNCING`.
Members that do not understand a terminal marker must not serve A2A management
traffic in a completed cluster.

## 4. Historical Definition Reconciliation

### 4.1 Source Scan And Fingerprint

The lease owner pages Namespaces and scans historical Config `group=agent`
directly. It obtains the public AgentName from summary content and never derives
identity by decoding a historical data id. For every summary it validates all
listed `group=agent-version` objects.

A private source fingerprint covers summary content, enable state, latest,
Version set, and every Version Config MD5. The reconciler recomputes it after
reading. A concurrent source change invalidates that attempt and schedules a
retry. This fingerprint is migration-only and is not a RAD revision or public
cache token.

Invalid AgentName, invalid Version, missing Version content, invalid latest,
unknown registration type, malformed JSON, or an AgentCard that cannot be
normalized under the current adapter is a blocking conflict. The migration
does not trim, rename, invent Versions, or silently drop fields.

### 4.2 Canonical Projection

Each valid historical Agent is projected as follows:

- `type=agent`, preserving the original public AgentName;
- `owner=nacos`, `scope=PUBLIC`, and historical enabled state;
- source `legacy-a2a-migration-v1`;
- every historical Version is `online`;
- common `latest` exactly matches the historical pointer;
- one `protocol=a2a` CallInterface containing the complete AgentCard;
- registration type maps through the existing A2A adapter to declared
  Endpoints and source order; and
- no custom label is invented.

The implementation uses the same pure legacy-to-canonical converter as the
canonical compatibility facade. Conversion logic is not migration state and
remains reusable after the temporary migration package is removed.

### 4.3 Storage And Visibility Order

For every Version, the reconciler prepares canonical
`AgentVersionContent`, writes it through the current AI Storage provider, reads
the same key back, and validates bytes, size, SHA-256 digest, and decoding. It
then idempotently writes the Version row. The Resource row and derived Version
catalog are written last, after every Version is complete.

If a source-fence change or process failure leaves only Version-first rows, a later
reconciliation may resume them only when they form an exact canonical subset of the current
historical source. Extra rows, altered Storage descriptors, content differences, or metadata
differences remain blocking conflicts.

Storage consistency is handled as follows:

| Provider consistency | Reconciliation behavior |
| --- | --- |
| `STRONG` | Read-back validation may run immediately after save. |
| `EVENTUAL_WITH_NOTIFICATION` | Notification wakes a retry; read-back validation remains authoritative. |
| `EVENTUAL_WITHOUT_NOTIFICATION` | Bounded backoff polling is allowed, followed by a later full scan. |

Temporary storage invisibility blocks only that Agent and global cutover. It
does not break the historical read path. The internal batch persistence entry
may create or repair only `legacy-a2a-migration-v1` facts; it is not a general
Agent lifecycle bypass.

### 4.4 Idempotence, Conflict, And Delete

| Canonical target | Required behavior |
| --- | --- |
| Missing | Create a complete migration-owned Agent. |
| Same source and equivalent | Successful no-op. |
| Same source but historical source changed | Reconciler may repair after a new source validation. |
| Independent canonical Agent, strictly equivalent | Preserve its owner, scope, and source; count it as externally equivalent. |
| Independent canonical Agent, partially equivalent or different | Do not overwrite either side; record a blocking conflict. |
| Damaged target storage | Repair only migration-owned data; otherwise record a conflict. |

Strict equivalence includes AgentName, enabled state, complete online Version
set, latest, normalized A2A content, and declared Endpoints. Formatting,
timestamps, and internal database ids are excluded.

A historical delete may remove only migration-owned target facts. An
incomplete scan never performs orphan deletion. Source absence must be
confirmed by consecutive complete scans before cleanup, preventing temporary
source invisibility from deleting canonical state.

During `SYNCING`, a successful capable-node historical mutation commits the
historical write first and then submits a bounded, coalesced in-memory
reconciliation hint. Hint failure does not change the response. Periodic full
scanning repairs older-node writes and lost hints. Generic Agent mutations of
a migration-owned Agent return `AGENT_MIGRATION_IN_PROGRESS`; unrelated Agent
and AI resource operations remain available.

## 5. Search, Events, And Read Projections

Completed migration-owned Agents may appear atomically in canonical Agent,
ARD, Search, and RAD reads while global authority remains `SYNCING`; partially
written Agents must not appear. Legacy A2A reads remain wholly historical until
cutover.

After Resource-last visibility, the existing shared Search task and AI
Resource change notifier are scheduled. Runtime registration, shadow Naming
state, migration lease, and progress do not enter the Agent Search document or
definition change fingerprint.

When Search is enabled, current projection completion for every migrated Agent
is a one-time cutover gate. Ordinary Search retains its fast-partial behavior
while not ready. When Search is disabled, this migration gate is skipped.

## 6. Runtime Endpoint Dual Materialization

Runtime Endpoint state belongs to live Naming publishers and is not copied as
historical persistent data. A capable node routes one validated logical legacy
A2A publication to physical layouts according to state:

```text
SYNCING / QUIESCING: legacy primary -> required canonical mirror
CANONICAL:           canonical primary -> optional legacy shadow
```

The logical request performs validation and capacity accounting once. Each
physical layout uses a deterministic child publisher bound to the original AI
connection. Register, complete-batch replace, deregister, disconnect, client
expiration, and server shutdown clean both children idempotently.

In `SYNCING` and `QUIESCING`, a canonical mirror failure does not roll back a
successful historical primary write. The complete batch enters bounded
connection-local retry and blocks cutover until converged. In `CANONICAL`, a
legacy shadow failure does not roll back a successful canonical write or alter
RAD reads; it is retried within the connection and reported through bounded
logs and metrics.

The optional shadow covers only historical A2A single, batch, and deregister
operations carrying one exact Version. General RAD ranges are never expanded
into historical Version-specific services. Declared Endpoints, other
protocols, and definition lifecycle changes never create historical Naming
instances.

RAD Discover, Watch, health, binding selection, and source revision always use
the canonical Runtime service after cutover. The shadow is visible only to
direct historical Naming consumers and never produces duplicate RAD events.

Before quiescing, at least two consecutive full rounds compare every
recognizable active historical exact-Version service with the matching
canonical exact-Version projection. Equality covers URI, transport, legacy
protocol version, tenant, enabled, health, priority, weight, and public
metadata after normalization. Publisher ids, child ids, timestamps, and
implementation-only metadata are excluded. An unparseable historical service
or instance is a blocking conflict.

## 7. Safe Cutover

The lease owner may enter `QUIESCING` only when all of the following hold:

1. every known member reports the migration ability, `AUTO`, and the same
   policy hash;
2. at least two consecutive complete all-Namespace scans have zero definition
   difference;
3. no invalid source, conflict, missing Version, storage validation failure,
   or pending delete exists;
4. enabled Search projections are current;
5. every active historical Runtime snapshot is equivalent in canonical RAD and
   required mirror retry queues are empty; and
6. the lease and member view remained stable for the round.

The owner creates a new quiescing generation by CAS. Every member installs the
write fence, validates all local target Resource, Version, and Storage reads,
checks its local required Runtime mirror queue, and publishes a generation
ACK. The generation is externally opaque and internally binds the stable
member-view digest plus a fresh nonce, so an ACK from an older member view
cannot satisfy the current fence. After the current member set has ACKed, the
owner performs one final full definition, Storage, Search, and Runtime
zero-difference round.

Success writes the terminal `CANONICAL` marker with `completedAt`. Timeout,
member or policy change, missing ACK, or any final difference CASes the plan
back to `SYNCING` and removes the definition write fence. Reads, Discover,
Watch, Endpoint publication, and existing runtime traffic remain available
throughout quiescing.

The two-phase fence prevents a member from accepting a historical definition
write while another member has started canonical writes. During terminal
marker propagation, both read branches are safe because the final round proved
them equivalent and both Runtime layouts are materialized. Config notification
accelerates marker observation; every node also performs a low-frequency
three-second recheck by default. The temporary internal property
`nacos.ai.a2a.migration.quiescing-check-interval-seconds` may tune this period
for controlled verification or operations, but does not relax any member,
ACK, lease, Search, Runtime, or final-scan gate.

## 8. Failure, Rollback, And Cleanup

| Failure | Availability rule |
| --- | --- |
| Lease owner loss | Historical reads/writes and Runtime continue; another member resumes after lease expiry. |
| Incomplete Namespace/page scan | No delete and no zero-difference credit for that round. |
| Partial canonical write | Not complete; reconcile migration-owned facts idempotently. |
| Historical write succeeded but hint failed | Return historical success; periodic scan repairs it. |
| Required Runtime mirror failed | Historical Endpoint remains available; cutover is blocked. |
| Optional post-cutover shadow failed | Canonical RAD remains available; warn and retry. |
| Quiescing timeout or member change | Return to `SYNCING` and restore historical mutations. |
| Conflict or damaged data | Preserve both sides and block cutover until operator repair. |
| Restart | Reload marker; live clients redo Runtime publications. |

Before terminal cutover, operators may set every member to `LEGACY` and roll
the cluster back. Migration-owned target data may remain for a future
idempotent run. After terminal cutover, the marker is not automatically
removed or downgraded, historical Config is retained but frozen, and rollback
is supported only to a binary that understands canonical Agent/RAD and the
terminal marker. A legacy-only binary must not rejoin.

This contract performs no destructive source cleanup. A later cleanup requires
backup, dry run, per-Namespace counts, a stable compatibility window, no old
server/client/direct Config consumer, an explicit shadow decision, and a
tombstone preventing source resurrection.

Logs, metrics, marker, lease, and progress use bounded resource summaries or
hashes. They must not expose complete AgentCards, credentials, tokens, or
sensitive Endpoint metadata.

## 9. Upgrade Runbook

### 9.1 Historical 3.0-3.2 Cluster

1. Back up the database and historical A2A Config; inventory direct historical
   Naming service consumers.
2. Choose the post-cutover legacy Naming shadow policy.
3. Configure every new member with `mode=AUTO` and the same policy.
4. Roll the cluster. During mixed versions, historical definitions remain
   authoritative and capable nodes reconcile and dual-materialize Runtime.
5. Observe progress, conflicts, Storage, Search, member policy, and mirror
   diagnostics.
6. Let the system quiesce and cut over only after all gates pass.
7. Cross-check legacy A2A, canonical Agent, RAD, Watch, and, when enabled, the
   historical Naming Gateway.

### 9.2 Existing Early-3.3 `AUTO` Cluster

An earlier `AUTO` implementation may have switched by version alone. Before
rolling to this contract, pin every old member to `LEGACY`, restart it, and
verify that historical Config is the intended authority. Then follow section
9.1. New members do not guess an old member's in-memory route.

### 9.3 Existing Canonical Cluster

A cluster without import needs remains on the default `CANONICAL` and creates
no migration control objects. Importing retained historical Config requires a
dry run and `AUTO`; a same-name content difference is never auto-merged.

## 10. Temporary Implementation And Removal

Migration-only production code is isolated under an A2A migration package and
every class or migration-only method contains this declaration:

```java
/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 */
```

An unavoidable branch inside a long-term main flow is preceded by:

```java
// TODO(remove in 4.0): Temporary migration path for Nacos 3.0-3.2 A2A data.
// Keep canonical behavior independent from this branch.
```

Every integration point is recorded in the migration removal inventory with
its deletion action, retained canonical dependency, and tests to remove or
retain. A focused static test verifies declarations and inventory consistency.

The migration package, state machine, source scan, lease/progress, migration
write protection, dual Runtime transition, optional historical shadow, and
their configuration are targeted for removal in Nacos 4.0. Removing them must
not rewrite already canonical Resource, Version, Storage, or Runtime data and
must not remove a still-supported public A2A facade without a separate
deprecation decision.

## 11. Required Test Matrix

Each implementation commit completes and records its assigned scenarios before
the next commit starts. Asynchronous assertions use bounded polling and public
or stable internal facts, not fixed sleeps.

### 11.1 Unit Scenarios

| ID | Required behavior |
| --- | --- |
| `M-UT-01` | Mode, marker priority, and terminal process latch. |
| `M-UT-02` | Member ability/policy validation and conservative missing or invalid metadata. |
| `M-UT-03` | Marker/lease CAS, renewal, owner loss, uncertain result, and terminal irreversibility. |
| `M-UT-04` | Complete legacy summary/Version conversion, codec boundaries, and strict validation. |
| `M-UT-05` | All AI Storage consistency modes and byte/size/digest read-back. |
| `M-UT-06` | Version-first/Resource-last writes, concurrent source change, and idempotent recovery. |
| `M-UT-07` | Same-source repair, external equivalence, conflicts, and safe orphan cleanup. |
| `M-UT-08` | Write-after hint failure isolation, coalescing, and periodic repair. |
| `M-UT-09` | Syncing legacy-primary/canonical-mirror order and partial failure. |
| `M-UT-10` | Canonical-primary/optional-shadow order and partial failure. |
| `M-UT-11` | Register/replace/deregister/disconnect dual cleanup and single capacity accounting. |
| `M-UT-12` | Runtime semantic equivalence and malformed historical-instance blocking. |
| `M-UT-13` | Quiescing fence, ACK, timeout, and member-change rollback. |
| `M-UT-14` | Search/notifier/Watch authority excludes shadow duplicate events. |
| `M-UT-15` | Non-A2A Agent and other AI resources remain isolated. |
| `M-UT-16` | Temporary declarations and removal inventory are complete. |

### 11.2 Standalone And Cluster Scenarios

The detailed `M-ST-01..10` and `M-CL-01..10` executable scenario assignments
live in the OpenAPI and Java SDK IT registries. They cover complete migration,
concurrent historical mutations, malformed/conflicting data, crash recovery,
cross-surface reads, both Runtime layouts, shadow on/off, reconnect/redo,
quiescing availability, rolling mixed members, cross-node reconciliation,
leader/owner restart, ACK/marker propagation, load-balanced reads, rollback
boundaries, and all unrelated resource regressions.
