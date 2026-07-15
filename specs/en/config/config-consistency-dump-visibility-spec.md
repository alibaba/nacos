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

# Config Consistency, Dump, And Visibility Spec

This document defines Config write visibility, local dump ordering, cluster
change propagation, and runtime query visibility. It refines the
[Config Spec](config-spec.md) and the
[Config Persistence, Dump, And History Spec](config-persistence-history-spec.md).

## 1. Scope

This spec owns:

- how a Config write becomes visible to local reads and listeners;
- how external and embedded storage modes propagate change notifications;
- how local dump files and local cache are refreshed;
- how gray config visibility composes with formal config visibility.

It does not redefine:

- the Config resource identity model;
- Config content semantics;
- storage engine internals;
- client local failover files.

## 2. Source Of Truth

The authoritative state of a Config resource is the configured persistence
layer:

- external storage mode uses the configured external database;
- embedded storage mode uses the CP path for the Config model Raft group;
- local disk dump is serving cache, not authoritative storage.

Runtime reads may be served from local dump/cache after the dump path has loaded
the persisted record. A stale or missing local dump must be repaired from
persistence, not treated as a new authoritative state.

## 3. Write Path

A Config publish or delete operation must:

1. validate identity, parameters, capacity, and authorization before writing;
2. persist formal or gray state through the repository layer;
3. record history and trace facts according to Config operation rules;
4. publish a `ConfigDataChangeEvent` or equivalent cluster notification when
   the write should refresh serving cache and notify listeners.

CAS writes are successful only when the persistence layer confirms the expected
MD5. A failed CAS must not publish a change event.

Aggregation config is outside the standard Config capability model and must not
be introduced into new consistency rules. Its compatibility status is governed
by the [Compatibility And Deprecation Spec](../design/compatibility-deprecation-spec.md).

## 4. External Storage Visibility

In external storage mode, all nodes share the external database as durable
storage. After a successful write, the writing node publishes a local
`ConfigDataChangeEvent` and sends `ConfigChangeClusterSyncRequest` to other
cluster members.

Each node that receives the change event must create a dump task for the formal
or gray config key. The runtime query view on a node becomes updated after that
node completes the dump from persistence into local serving cache.

External storage propagation is AP-style notification over the cluster request
path. A node may lag until it receives notification, retries notification, or is
repaired by periodic full dump or change dump workers.

## 5. Embedded Storage Visibility

In embedded storage mode, durable ordering comes from the Config model CP group.
The server must wait until CP metadata indicates a leader is available before
startup dump can safely read data. The dump path marks the read context so
startup dump waits until data is available.

After a write is committed in the embedded storage path, local serving cache must
be refreshed through dump before runtime query and listener views are considered
updated on a node. Change notification must not bypass the CP commit result.

Only the leader should execute maintenance tasks that are leader-owned in
embedded mode, such as history cleanup. Local dump is still per-node serving
state.

## 6. Dump Ordering

Dump ordering is per Config identity:

- formal config uses `dataId`, `groupName`, and `namespaceId`;
- gray config also includes `grayName`;
- a later dump task for the same task key may replace or merge earlier pending
  work according to task manager semantics;
- the dump task must read the latest persisted state for that identity.

Dump completion updates local content cache and local disk dump. Listener and
fuzzy watch notifications must be emitted from local change visibility, not from
uncommitted write intent.

## 7. Gray Visibility

Gray config is subordinate to a formal Config identity. A gray publish or delete
must refresh the gray serving cache for the corresponding `grayName`. Runtime
query selection first evaluates gray rules and then falls back to formal config
according to the [Config Gray Release Spec](config-gray-release-spec.md).

Starting with the Nacos 3.3 line, consistency and dump paths do not translate
legacy beta/tag storage rows into `grayName`, and do not synchronize duplicate
default-namespace records between empty tenant and `public`. Dump tasks operate
only on the persisted Config identity for the current model.

## 8. Failure And Recovery

- If local disk cannot safely store dump content, the server must treat the
  condition as fatal because runtime queries depend on local serving cache.
- If cluster notification fails, it must be retried with bounded/backoff
  scheduling and repaired by periodic dump paths.
- If a node restarts, startup dump must rebuild local serving cache from
  persistence before the node is considered ready for Config query correctness.
- If a client misses push, it must recover through listener resync and query,
  as defined by the
  [Runtime Push And Reconnect Spec](../client/runtime-push-reconnect-spec.md).

## 9. Related Specs

- [Config Publish And Query Spec](config-publish-query-spec.md)
- [Config Listener And Watch Spec](config-listener-watch-spec.md)
- [Config Gray Release Spec](config-gray-release-spec.md)
- [Persistence And Dump Spec](../design/foundation-persistence-dump-spec.md)
- [CP Consistency Spec](../design/foundation-cp-consistency-spec.md)
- [AP Consistency Spec](../design/foundation-ap-consistency-spec.md)
- [Internal RPC And Cluster Request Spec](../design/foundation-internal-rpc-spec.md)
