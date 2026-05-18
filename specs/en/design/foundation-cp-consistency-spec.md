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

# Nacos CP Consistency Spec

This document defines the CP-oriented consistency foundation used by Nacos. It
expands the CP consistency part of the
[Foundation Capabilities Spec](foundation-capabilities-spec.md).

## 1. Positioning

AP and CP are CAP-theory consistency choices. In Nacos, a CP path prioritizes
strongly ordered committed state under partition tolerance. When quorum, leader,
or a protocol group is unavailable, a CP operation may fail or become
temporarily unavailable instead of accepting divergent writes.

The current built-in CP implementation is JRaft through `JRaftProtocol`.
Nacos may load a `CPProtocol` implementation through SPI, but the supported
in-repository CP semantics are defined by Raft/JRaft.

## 2. CP Resource Rules

CP state is appropriate for resources with these characteristics:

- durable state owned by management or server-side control paths;
- state that must survive client disappearance and server restart;
- operator or developer intent that must override runtime registration;
- writes that require a single commit order inside a logical group;
- state that can be recovered from snapshots and committed logs.

CP state is not appropriate for high-frequency disposable runtime state when AP
eventual convergence is enough. Those resources should use the
[AP Consistency Spec](foundation-ap-consistency-spec.md).

CP consumers must define:

- Raft group name and ownership;
- write request shape and operation values;
- read request shape and read visibility;
- deterministic `onApply` behavior;
- snapshot save/load shape and compatibility;
- leader or readiness requirements for user-visible operations;
- error behavior when the group has no leader, no processor, or no quorum.

## 3. Protocol Model

The shared consistency interface is `ConsistencyProtocol`. CP specializes it as
`CPProtocol`.

The CP model is:

```text
ProtocolManager
  -> CPProtocol(JRaftProtocol)
  -> RequestProcessor4CP per group
  -> JRaftServer multi-raft group
  -> NacosStateMachine
  -> processor.onApply / processor.onRequest
  -> snapshot operations
```

`ProtocolManager` injects CP members as `ip:raftPort` addresses. Member change
events are propagated to the CP protocol asynchronously.

## 4. Request Processor Contract

Each CP domain integrates by registering a `RequestProcessor4CP`.

Processor rules:

- `group()` must return a stable unique group name;
- one group should have one authoritative processor in the server runtime;
- `onApply(WriteRequest)` must be deterministic and must not depend on slow
  remote IO;
- `onRequest(ReadRequest)` must return a response consistent with the group's
  read rule;
- `loadSnapshotOperate()` must declare snapshot operations when the group needs
  snapshot recovery;
- processors must treat unknown operations as explicit failures;
- processors must publish domain events only after committed apply has updated
  local state, following the
  [Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md).

The processor owns domain semantics. The CP foundation owns group routing,
leader forwarding, log commit, read-index handling, metadata, and snapshot
integration.

## 5. JRaft Runtime Rules

JRaft is used as a multi-group CP runtime.

Rules:

- each `RequestProcessor4CP` creates one Raft group;
- writes are submitted to the group leader or forwarded to the current leader;
- committed logs are applied through `NacosStateMachine`;
- follower replay applies committed writes but ignores follower-local read
  entries without a closure;
- read paths first try Raft read-index and may fall back to leader read;
- `protocolMetaData()` tracks leader, term, group members, and errors;
- `isReady()` indicates that the protocol has started and, in strict mode,
  that groups have leaders;
- member removal is handled through Raft peer-change commands; joining nodes
  register themselves when starting.

Implementation timeouts are operational defaults, not public API guarantees.
Domain specs must not expose JRaft timeout values as user-visible correctness
contracts unless a domain API explicitly defines them.

## 6. Current CP Consumers

| Group | Owner | Purpose |
| --- | --- | --- |
| `nacos_config` | Persistence and Config embedded storage | Replicate embedded storage operations and make Config dump wait for readable committed state. |
| `naming_persistent_service_v2` | Naming | Replicate persistent instance register, update, and deregister operations. |
| `naming_service_metadata` | Naming | Replicate service metadata and cluster metadata. |
| `naming_instance_metadata` | Naming | Replicate operational instance metadata. |
| `naming_persistent_service` | Naming operations | Replicate legacy naming switch/domain state. |
| `plugin_state` | Core plugin | Replicate plugin state changes. |

New CP consumers must add their group to the relevant domain spec and define the
resource semantics that the group commits.

## 7. Read And Write Semantics

Write rules:

- a write request must include group, operation, and serialized data;
- success means the write was accepted and applied according to the group's
  processor contract;
- failure must be surfaced to callers instead of being silently retried as a
  user-visible success;
- domain apply logic must be idempotent or guarded when duplicate submissions
  are possible at the caller layer;
- writes must not publish events before committed apply.

Read rules:

- a read request must include the target group and serialized query data;
- the group processor defines whether a read is supported and what state it
  reads;
- read-index failure may fall back to leader read;
- if a domain reads from local cache outside CP, it must document stale-read
  tolerance in its domain spec.

## 8. Snapshot And Recovery

Snapshot rules:

- a group with recoverable state should provide `SnapshotOperation` entries;
- snapshot format and compatibility are owned by the domain processor;
- snapshot save/load must preserve enough data to rebuild serving state after
  restart;
- groups without snapshot operations rely on log replay or separate persistence;
- domain specs must define how snapshot recovery interacts with local cache and
  derived indexes.

For embedded Config storage, startup waits for CP metadata indicating readable
data before dump recovery continues. That wait is a Config persistence rule
built on the CP foundation and the
[Persistence And Dump Spec](foundation-persistence-dump-spec.md).

## 9. Boundary Rules

- CP consistency is strong ordered commit within a group, not a guarantee that
  every interface reads through CP.
- CP groups own committed domain state, not transport retries, SDK redo, or AP
  runtime state.
- JRaft groups must not be used to serialize high-frequency ephemeral state
  unless the domain spec explicitly requires CP semantics.
- A CP processor must not redefine public API fields, resource identity, or
  authorization rules.
- CP metadata is operational protocol state. It may be exposed through
  authorized ops APIs, but it is not a domain resource identity.
- Replacing the CP implementation must preserve the `CPProtocol`,
  `RequestProcessor4CP`, group, snapshot, metadata, and error semantics defined
  here.

## 10. Related Specs

- [Foundation Capabilities Spec](foundation-capabilities-spec.md)
- [AP Consistency Spec](foundation-ap-consistency-spec.md)
- [Persistence And Dump Spec](foundation-persistence-dump-spec.md)
- [Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md)
- [Cluster Membership Spec](foundation-cluster-membership-spec.md)
- [Config Spec](../config/config-spec.md)
- [Config Persistence, Dump, And History Spec](../config/config-persistence-history-spec.md)
- [Naming Consistency And Client State Spec](../naming/naming-consistency-client-spec.md)
- [Auth And Permission Spec](../auth/auth-permission-spec.md)
