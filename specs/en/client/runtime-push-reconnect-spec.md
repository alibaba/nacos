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

# Nacos Runtime Push And Reconnect Spec

This document defines shared runtime push, push retry, disconnect, and
reconnect rules for Config, Naming, and AI runtime streams. It expands the
[Client Runtime Spec](client-runtime-spec.md) and complements the
[Client Connection And Failover Spec](client-connection-failover-spec.md).

## 1. Scope

This spec owns:

- server-to-client change notification over a registered connection;
- push acknowledgement and retry behavior;
- clearing server-side listener or subscription state when a connection closes;
- client-side resubscription or redo after reconnect;
- the boundary between push notification and authoritative domain reads.

It does not own:

- Config, Naming, or AI resource persistence;
- server-to-server consistency propagation;
- client local snapshot or failover file semantics;
- broad management diagnostics.

## 2. Push Is Notification, Not Authoritative State

Push messages notify a runtime client that the server-side view may have
changed. A push must not be treated as the only authoritative copy of a domain
resource.

Domain rules:

- Config push carries changed identity. The client must query the Config content
  after notification.
- Naming push carries the current discovery view for a subscribed service.
  It is still derived serving state and can be refreshed by re-query or
  resubscription.
- AI push behavior is versioned by each AI resource spec and must keep the same
  identity rules as the corresponding query API.

RAD Watch carries only an invalidation hint. The client must execute the
ordinary authorized Discover operation and compare the complete canonical
result fingerprint before replacing its local cache or invoking a listener.

## 3. Server-Side Connection State

Runtime listener or subscription state is scoped to a server-side connection id.
When the connection closes, the server must remove connection-scoped state:

- Config clears config listen context and fuzzy watch context for the connection.
- Naming removes connection-based client state, published ephemeral instances,
  subscribers, and indexes derived from that client.
- AI runtime endpoint and subscription state must follow the same connection
  ownership rule when it is scoped to a runtime connection.

Connection cleanup must publish local events required to update derived indexes
and push views.

## 4. Push Retry

Push retry is best-effort delivery within the current connection lifetime.

Config push retry:

- normal config change push uses `ConfigChangeNotifyRequest`;
- fuzzy watch push uses fuzzy watch notify requests;
- retry is bounded by configured max retry times;
- if normal config push retry exceeds the bound, the server may unregister the
  connection to force client-side recovery.

Naming push retry:

- service-change push is scheduled through merged delay tasks per service;
- service-subscribed push may target a single client;
- failed push may enqueue a delayed retry for the target client unless the
  failure explicitly says retry is not required;
- retry must not mutate Naming resource state.

Push retry should record metrics and trace facts, but observability must not be
part of the correctness path.

## 5. Client Reconnect Recovery

After reconnect, clients must restore runtime intent:

- Config marks listener and fuzzy watch state inconsistent on disconnect and
  resyncs known listeners after reconnect.
- Naming marks redo data not registered on disconnect and redoes ephemeral
  registrations and subscriptions after reconnect.
- AI runtime clients redo endpoint and subscription intent when the feature
  defines reconnectable runtime state.

Client recovery is defined in detail by the
[Client Local Cache And Redo Spec](client-local-cache-redo-spec.md). Connection
selection and liveness are defined by the
[Client Connection And Failover Spec](client-connection-failover-spec.md).

## 6. Agent And RAD Watch Contract

This section defines Nacos Agent/RAD server-aware Watch. It is advertised only
after the independent abilities in the
[Agent API Spec](../ai/agent-api-spec.md) are implemented and negotiated.

### 6.1 Identity, Projection, And Fingerprint

The canonical local Watch identity is:

```text
(namespaceId, canonicalAgentReference, canonicalFilter, listenerIdentity)
```

The canonical reference preserves exact Version, Label, explicit latest, and
the rollout-safe unspecified-version form. Filter construction materializes
defaults and sorts and deduplicates set-valued fields. Listener identity is the
same listener instance supplied to cancellation. The server projection key
excludes listener identity and authorization/visibility scope; authorization
decides whether a caller may install or refresh a Watch and is never encoded as
resource identity.

Client and server share canonical request normalization and complete-result
fingerprinting. Fingerprint format is
`sha256-canonical-json-v1:<64-lowercase-hex>`. It is an equality token only,
not a sequence, version, authorization proof, or replay cursor. Equal final
fingerprints permit A-B-A task coalescing without another listener callback.

### 6.2 Server Projection And Push Execution

Definition, Version/Label, visibility, runtime Endpoint, liveness, and
connection cleanup events mark affected projections dirty. The reusable push
pipeline follows Naming's latest-snapshot task discipline: producers add or
merge a delay task by projection; tasks not yet executing may coalesce; once
execution starts it completes; a concurrent later change creates or merges
into a subsequent task. Agent supplies projection matching and fingerprint
construction, while shared runtime code supplies task merge, target fan-out,
connection lookup, asynchronous push, retry, and metrics.

The server does not keep a prior business snapshot or per-Watch sequence. A
gRPC Notify contains only `watchKey`, event type, optional observed
fingerprint, and terminal error code. The client acknowledges after validating
the key and marking the local intent dirty. ACK does not wait for Discover or
listener execution. Retry is bounded and connection-scoped; exhausting retry
may force reconnect so resubscription restores intent.

### 6.3 Current-Fact Refresh And Listener Delivery

Every accepted Hint, reconnect refresh requirement, HTTP changed item, or
polling-fallback tick executes ordinary authorized Discover. The client
canonicalizes the complete result and compares its fingerprint with cache.
Only a different result atomically replaces cache and emits a complete
`SNAPSHOT`; it never merges result fragments. An empty filtered shape is a
valid snapshot. Listener work is isolated from connection and long-poll I/O;
slow, throwing, or rejected listener execution cannot change Hint ACK state or
block another Watch.

`NOT_FOUND` enters a bounded local pending state and emits at most one
`UNAVAILABLE` transition for the absent period; recovery emits a new snapshot.
Terminal authorization or capacity errors emit one unavailable event and
remove the rejected intent. Transient transport or Discover failures retain
intent and use bounded backoff without exposing stale data as a new snapshot.

### 6.4 gRPC Reconnect

gRPC Watch state belongs to one connection. Disconnect removes server state
and invalidates every old `watchKey`. The SDK retains canonical local intent,
re-subscribes the complete active set under the new connection, and performs
Discover when `refreshRequired` is true. There is no periodic full-data Sync;
resubscription plus current-fact Discover is the reconciliation mechanism.
Late notifications for old keys are rejected and cannot reach listeners.

### 6.5 HTTP Batch Long Poll

HTTP uses one request-scoped batch long poll for the complete current Watch
set, not one request per Agent. The request contains local generation, timeout,
and each item with client Watch id, discovery request, and last materialized
fingerprint. A response contains timeout or changed client ids only. It carries
no business data and no per-item authorization details.

Each long-poll request may reach a different cluster node. Correctness does not
depend on node-local generation continuity: the receiving node compares the
submitted fingerprints with its current serving projection, and a returned id
is re-fetched through Discover. Local list changes advance generation and
supersede the prior request. A late old-generation response is ignored. Server
awareness of socket cancellation is an optimization; timeout and the next
complete-list request clean up request-scoped wait state.

The first HTTP binding performs one-namespace request-level AI read
authorization and deliberately does not implement per-item multi-resource
fine-grained authorization. Discover remains the mandatory content and
visibility authorization boundary.

## 7. Ordering

Push delivery order is scoped to the local event and task paths of a node. It is
not a global total order across the cluster.

Domain specs must define when a local serving view is visible:

- Config write visibility, dump ordering, and local cache visibility are defined
  by the
  [Config Consistency, Dump, And Visibility Spec](../config/config-consistency-dump-visibility-spec.md).
- Naming ephemeral service convergence is defined by the
  [Naming Ephemeral Distro Consistency Spec](../naming/naming-ephemeral-distro-consistency-spec.md).
- Naming persistent service and metadata visibility are defined by the
  [Naming Persistent CP Consistency Spec](../naming/naming-persistent-cp-consistency-spec.md).

## 8. Failure Rules

- A missing connection should cancel or skip push for that connection.
- A push timeout does not prove that the client failed to observe the change;
  it only means the server did not receive a successful ack in time.
- A client must be able to recover from missed push by re-query, resync, or redo.
- Server push must not hide authorization failures in the underlying query path.

## 9. Pending Issues

- Push retry, timeout, and reconnect recovery observations should follow the
  shared field and label guidance in the
  [Observability Hooks Spec](../design/foundation-observability-hooks-spec.md).
