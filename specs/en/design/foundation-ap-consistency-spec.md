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

# Nacos AP Consistency Spec

This document defines the AP-oriented consistency foundation used by Nacos. It
expands the AP consistency part of the
[Foundation Capabilities Spec](foundation-capabilities-spec.md).

## 1. Positioning

AP and CP are CAP-theory consistency choices. In Nacos, an AP path prioritizes
availability and partition tolerance for state that can converge eventually.
It does not provide a strongly ordered global write log, linearizable reads, or
durable management ownership by itself.

Current AP-style implementations are:

| Implementation | Primary owner | Purpose |
| --- | --- | --- |
| Distro | Naming runtime state | Synchronize ephemeral client-owned service instance state between server nodes. |
| Config Notify | Config cache and listener visibility | Notify peer nodes that a Config resource changed so local dump cache and listeners can refresh. |

Section 6 defines the target contract for a common HTTP connection-based
Client. It reuses Naming's existing ClientData Distro resource type and
processor. The Client type is not implemented or available until its manager is
complete and an upper-layer API advertises the corresponding capability.

The historical `APProtocol` interface exists in the consistency module, but the
current active AP implementations are the Distro foundation and the Config
Notify path. New specs should describe AP semantics directly instead of assuming
that all AP behavior must implement `APProtocol`.

## 2. AP Resource Rules

AP state is appropriate for resources with these characteristics:

- runtime state owned by live clients or local observations;
- high update frequency where global serialization is too expensive;
- state that can be reconstructed, refreshed, or discarded;
- correctness that tolerates eventual convergence and retry;
- failure handling that can use verify, snapshot, reload, or re-query.

AP state is not appropriate for durable management metadata, operator overrides,
schema state, long-lived permissions, or resources that require a single global
commit order. Those resources should use the
[CP Consistency Spec](foundation-cp-consistency-spec.md) or the
[Persistence And Dump Spec](foundation-persistence-dump-spec.md).
Delayed task, execute task, and local event rules used by AP paths are defined
by the [Task Execution Spec](foundation-task-execution-spec.md) and the
[Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md).

AP consumers must define:

- resource identity and resource type;
- owner or responsibility rule for producing state;
- data operation set and idempotency expectations;
- convergence window and retry policy;
- verify and repair behavior;
- startup loading and snapshot behavior;
- local events emitted after apply;
- whether deleted data needs tombstone, expiration, or replacement semantics.

## 3. Distro Model

Distro is the shared AP synchronization framework under
`core.distributed.distro`.

The Distro model is:

```text
DistroKey(resourceKey, resourceType, targetServer)
  -> DistroData(type, content)
  -> DistroDelayTask / DistroExecuteTask
  -> DistroTransportAgent
  -> DistroDataRequest
  -> DistroDataProcessor
  -> local state and events
```

Distro components:

| Component | Responsibility |
| --- | --- |
| `DistroKey` | Identifies one AP datum by resource key, resource type, and optional target server. |
| `DistroData` | Carries serialized datum content and a `DataOperation`. |
| `DistroDataStorage` | Produces individual data, verify data, and full snapshots. |
| `DistroDataProcessor` | Applies received data, verify data, and snapshot data. |
| `DistroTransportAgent` | Sends sync, verify, query, and snapshot requests to peer nodes. |
| `DistroFailedTaskHandler` | Converts failed sync or verify operations into retry tasks. |
| `DistroTaskEngineHolder` | Owns delayed tasks and execute tasks for sync, verify, load, and retry. |

Distro operations use `DataOperation` values such as `ADD`, `CHANGE`, `DELETE`,
`VERIFY`, `SNAPSHOT`, and `QUERY`. Domain processors must define which
operations are valid for their resource type.

## 4. Distro Lifecycle

Distro starts with load and verify tasks when Nacos is not in standalone mode.

Lifecycle rules:

- startup load obtains a snapshot from peer nodes and asks the domain processor
  to apply it;
- data storage is marked as initialized only after a snapshot is successfully
  applied;
- verify tasks should not run before the corresponding data storage finishes
  initialization;
- verify data compares compact state such as revision or checksum and triggers
  repair when the target detects mismatch;
- change and delete tasks are delayed and merged by key when the task engine
  supports that behavior;
- failed sync or verify operations must be retried by the domain failed-task
  handler or explicitly discarded with diagnostics.

Standalone mode must not pretend to have remote AP convergence. It should mark
AP runtime state as locally initialized and avoid remote sync.

## 5. Naming Distro Contract

Naming uses Distro for ephemeral client state.

Rules:

- only ephemeral clients owned by the current node are synchronized by Distro;
- persistent clients and metadata must use CP or persistence paths;
- a client change emits a Distro `CHANGE`, a disconnect emits `DELETE`, and a
  verify failure may trigger targeted `ADD`;
- Distro sync data contains the client id, attributes, published services,
  instance publish information, and batch instance data;
- applying Distro data must update server-side Client state and emit Naming
  events so derived indexes and push views can be rebuilt;
- verify uses client id and revision and may schedule repair from the source
  node;
- snapshot contains the current ephemeral client sync data set.

Naming Distro transport is carried by
`DistroDataRequest` / `DistroDataResponse` over the
[Internal RPC And Cluster Request Spec](foundation-internal-rpc-spec.md).

## 6. Target Common HTTP Connection-based Client Contract

This section defines an ephemeral Naming Client maintained across multiple
HTTP requests. Agent Endpoints, ordinary Naming Instances, and future runtime
Endpoints are adapted by their domains before entering the Client. The Client
manager and Distro handle only standard `InstancePublishInfo` or
`BatchInstancePublishInfo`.

The target flow is:

```text
HTTP request
  -> module-owned Distro Filter routes by internal client id
  -> mutate HttpConnectionBasedClient
  -> existing Naming ClientData CHANGE or DELETE
  -> peer Client state
  -> Naming indexes and runtime projections
```

### 6.1 Resource Identity And Routing

| Item | Required target semantics |
| --- | --- |
| External identity | Caller-supplied opaque `externalClientId`. |
| Internal Client id | `HTTP_CLIENT@@<externalClientId>`. |
| Distro resource type | Reuse `Nacos:Naming:v2:ClientData`. |
| `resourceKey` and `responsibleId` | The complete internal Client id. |
| Client manager | `HttpConnectionBasedClientManager`, a peer of `ConnectionBasedClientManager` routed by `ClientManagerDelegate`. |
| Responsibility | Distro selects one responsible node using the stable internal Client id. |
| Module reuse | AI, Naming, and other modules using the same external id share one Client, publisher/subscriber container, and lifecycle. |
| Remote entry | Each module owns an HTTP Distro Filter that forwards stateful requests to the responsible node; AI does not extend Naming's existing Distro Filter. |

The first stateful Client creation binds one authenticated subject and one
`namespaceId`. State stores a stable subject identity, never a credential or
access token. Later stateful requests must use the same subject and namespace.
A mismatch is rejected without refreshing either liveness clock. A Client id
is routing and state-ownership identity, not an authorization credential.

### 6.2 ClientData And Operations

The common HTTP Client directly reuses Naming `ClientSyncData`,
`DistroClientDataProcessor`, and their existing
`ADD/CHANGE/DELETE/VERIFY/SNAPSHOT/QUERY` semantics. It registers no new Distro
resource type or processor. Synchronized data contains the normal Client
identity, attributes, complete publications, and revision. HTTP Client
attributes additionally carry namespace, authenticated-subject identity,
Client liveness, and Publisher liveness.

Publisher data remains the existing complete service publication owned by a
Client:

- a single instance uses `InstancePublishInfo`;
- a complete batch uses `BatchInstancePublishInfo`;
- Agent and other domain adapters never put domain DTOs into Distro payload;
- subscribers, as with current connection-based Clients, stay only on the node
  carrying the subscription and are not part of the ClientData publication
  snapshot.

Publication changes and semantic health transitions advance Client revision
and emit the existing `ClientChangedEvent`. Ordinary Client renewal and
Publisher heartbeat do not broadcast ClientData merely because a timestamp
changed. Peers converge through the existing verify, snapshot, and repair
flow.

### 6.3 Layered Client And Publisher Liveness

An HTTP Client maintains two liveness scopes:

| Liveness | Refresh sources | Effect |
| --- | --- | --- |
| Client liveness | Valid query, subscription change, publication write, and explicit Publisher heartbeat. | Determines whether the Client and subscriber state remain. |
| Publisher liveness | Publication write and explicit Publisher heartbeat. | Determines whether publications owned by the Client remain healthy and retained. |

A query renews only an existing Client. It does not create an empty Client or
change publisher payload, revision, or health. Frequent queries may therefore
retain Client or subscriber state, but cannot make a timed-out publication
healthy or prevent Publisher expiration. An explicit Publisher heartbeat
renews both the Client and all publications owned by that Client.

Publisher timeouts satisfy:

```text
heartbeatIntervalMillis < unhealthyTimeoutMillis < expireTimeoutMillis
```

After `unhealthyTimeoutMillis`, publications remain but become unhealthy.
Publisher recovery returns them to active and emits `CHANGE` only when the
public health projection changes. After `expireTimeoutMillis`, all
publications owned by that Client are removed, while a Client with subscriber
state remains. Client expiration releases all of its publisher and subscriber
state.

Only the responsible node schedules native Client and Publisher timeouts.
ClientData replicas converge through the existing snapshot, verify, and repair
flow. A replica's latest successful verify time participates in local timeout
calculation if that replica later becomes responsible, so the Client does not
need a second ownership flag or a separately synchronized failover state.
Ordinary heartbeat timestamps are not broadcast every interval.

### 6.4 Apply Events And Visibility

Local publication changes, remote `ADD/CHANGE`, repair, and snapshot apply use
the existing Naming Client/service events to rebuild publisher indexes,
service storage, and push views. `DELETE` uses the normal Client release path
to clean derived state. `VERIFY` itself emits no discovery change.

Upper Agent/RAD projections consume only Naming `ServiceStorage` output. The
HTTP Client manager does not maintain a second Agent Endpoint projection and
does not depend directly on Agent definitions or AI Resource state.

## 7. Config Notify Contract

Config Notify is an AP-style change propagation path. It is not a durable
storage protocol and does not carry authoritative config content.

The model is:

```text
Config write or delete
  -> ConfigDataChangeEvent
     -> local DumpService refresh
     -> AsyncNotifyService fan-out to peers
        -> ConfigChangeClusterSyncRequest
        -> peer DumpService refresh
        -> LocalDataChangeEvent
        -> client listener push
```

Rules:

- the durable source of truth remains the Config persistence layer;
- the notify request carries config identity, `lastModified`, gray name, and
  compatibility fields, not the full authoritative content;
- the receiving node must refresh local dump/cache from persistence according
  to Config rules;
- local `LocalDataChangeEvent` triggers listener and watch notifications after
  local cache changes;
- unhealthy target members should be delayed instead of blocking the write
  path;
- callback failure or timeout must schedule retry with bounded or controlled
  backoff;
- peer removal may turn a pending notify task into a no-op.

For Config, AP notification success means peer nodes were told to refresh their
serving state. It does not replace persistence success, and it does not make the
push payload authoritative content.

## 8. Failure Semantics

AP consumers must handle partial success.

Rules:

- a local write can succeed before every peer observes the change;
- retry may duplicate operations, so apply logic must be idempotent or guarded
  by revision, timestamp, operation type, or current state;
- timeout does not prove the remote operation did not happen;
- stale remote state must be repaired through verify, snapshot, re-query, or
  domain-specific reload;
- AP recovery must be observable through logs, metrics, trace, or diagnostics;
- AP failure must not silently turn runtime state into durable metadata.

## 9. Boundary Rules

- AP consistency is eventual convergence, not strong consistency.
- Local `NotifyCenter` events are not AP consistency by themselves; they become
  part of AP behavior only when a domain defines remote propagation and repair.
- Distro is the formal shared AP framework for runtime data. Naming uses it for
  ephemeral Client state, including common HTTP connection-based Clients.
  Config Notify is a Config-specific AP notification path for cache/listener
  visibility.
- AP paths must not be used for permissions, namespace metadata, persistent
  service metadata, plugin state, or database schema state.
- AP payloads are internal cluster contracts unless an interface spec exposes
  them explicitly.
- AP transport must follow internal RPC auth, source, payload, and retry rules.

## 10. Related Specs

- [Foundation Capabilities Spec](foundation-capabilities-spec.md)
- [Internal RPC And Cluster Request Spec](foundation-internal-rpc-spec.md)
- [Remote Connection Lifecycle Spec](foundation-remote-connection-spec.md)
- [Cluster Membership Spec](foundation-cluster-membership-spec.md)
- [CP Consistency Spec](foundation-cp-consistency-spec.md)
- [Persistence And Dump Spec](foundation-persistence-dump-spec.md)
- [Task Execution Spec](foundation-task-execution-spec.md)
- [Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md)
- [Config Spec](../config/config-spec.md)
- [Config Listener And Watch Spec](../config/config-listener-watch-spec.md)
- [Naming Consistency And Client State Spec](../naming/naming-consistency-client-spec.md)
- [Agent Storage Spec](../ai/agent-storage-spec.md)
- [RAD Protocol Spec](../ai/rad-protocol-spec.md)
- [gRPC API Spec](../grpc-api/api-spec.md)
