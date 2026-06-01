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

# Naming Ephemeral Distro Consistency Spec

This document defines Naming domain rules for ephemeral service AP
consistency. It refines the shared
[AP Consistency Spec](../design/foundation-ap-consistency-spec.md) and the
[Naming Consistency And Client State Spec](naming-consistency-client-spec.md).

## 1. Scope

This spec owns:

- ownership of ephemeral client runtime state;
- Distro data type, change, delete, verify, snapshot, and repair semantics;
- how Distro-applied client changes become visible through Naming indexes,
  service storage, and push;
- recovery boundaries between server-side Distro repair and client-side redo.

It does not define persistent services, CP metadata groups, or client SDK local
cache behavior.

## 2. Ownership

Ephemeral state is owned by the server node responsible for the ephemeral
client:

| Client category | Owner rule |
| --- | --- |
| Connection-based client | The node holding the live gRPC connection owns the native client. |
| Ephemeral IP-port client | The node selected by Distro responsibility for the client responsible id owns the native client. |
| Synced client | A remote copy received from another owner node. It is serving state, not local ownership. |

Only valid, native, responsible, ephemeral clients should be synchronized as
Distro source data. Persistent clients must not be synchronized through this
path.

## 3. Distro Data Model

Naming ephemeral Distro data uses the resource type
`Nacos:Naming:v2:ClientData`. The resource key is the client id.

The synchronized payload is client-level data. It includes the client identity,
published service identities, instance publish information, optional batch
instance data, and the client revision used by verify flow. Distro does not
use service-level rows as the source of truth; service views are derived from
client state and indexes.

## 4. Change Propagation

The source node must publish Distro changes from client events:

- client changed -> `CHANGE`;
- client disconnected -> `DELETE`;
- verify failed on a target node -> immediate `ADD` to that target.

When a node receives `ADD` or `CHANGE`, it must create or update a synced
client and apply the synchronized instances. Applying synchronized data must
publish the same local Naming events required to rebuild publisher indexes,
service storage, and push views.

When a node receives `DELETE`, it must disconnect and release the synced client
for that source client id. Derived indexes and service storage must be updated
through the same local event path used by normal client release.

## 5. Verify And Anti-Entropy

Distro verify compares the source client revision with the target node state.
If the target node cannot verify the client, it must return verify failure so
the source can send full client data to that target.

Revision `0` may be treated as compatibility data. For current protocol data,
a matching revision refreshes the target-side liveness observation; a mismatch
requires repair.

Snapshot transfer is anti-entropy for the whole Naming ephemeral Distro data
type. A snapshot contains all responsible ephemeral clients from the source
node. Loading a snapshot must process each client with the same sync-data
application rules as normal `ADD` or `CHANGE`.

## 6. Expiration And Cleanup

Connection-based clients expire when the remote connection is disconnected or
the connection manager removes an expired connection. Releasing the native
client must emit disconnect and release events so Distro can delete remote
copies and local indexes can be cleaned.

Ephemeral IP-port clients expire through heartbeat and cache-time based cleanup.
The owner node must remove expired native clients and publish the same
disconnect events used by Distro deletion.

Remote synced clients are cleanup targets owned by the Distro path. They must
not renew native ownership on the receiving node.

## 7. Visibility And Push

Ephemeral Naming visibility is eventually consistent across nodes:

1. the owner node updates client state;
2. local events update publisher indexes and service storage;
3. the owner node schedules Distro sync to peer nodes;
4. peer nodes apply synced client data and rebuild derived serving state;
5. service change events schedule subscriber push.

Subscriber push carries the derived `ServiceInfo` view. It does not make
ephemeral state durable. Push retry and reconnect recovery are defined by the
[Runtime Push And Reconnect Spec](../client/runtime-push-reconnect-spec.md).

## 8. Boundaries

- Ephemeral Distro does not persist instance state to the database.
- Ephemeral Distro does not own service metadata or instance metadata; those
  follow metadata CP rules when present.
- A successful registration on one node may become visible on other nodes only
  after Distro sync or anti-entropy repair.
- Range queries over ephemeral service state may observe per-node serving state
  until AP convergence completes.

## 9. Related Specs

- [Naming Resource Spec](naming-resource-spec.md)
- [Naming Instance Lifecycle Spec](naming-instance-lifecycle-spec.md)
- [Naming Discovery And Subscription Spec](naming-discovery-subscription-spec.md)
- [Naming Consistency And Client State Spec](naming-consistency-client-spec.md)
- [Runtime Push And Reconnect Spec](../client/runtime-push-reconnect-spec.md)
- [AP Consistency Spec](../design/foundation-ap-consistency-spec.md)
- [Internal RPC And Cluster Request Spec](../design/foundation-internal-rpc-spec.md)
