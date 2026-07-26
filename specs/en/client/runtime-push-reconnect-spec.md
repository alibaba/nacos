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

The target RAD Watch carries a complete discovery snapshot rather than only a
change identity. That snapshot is authoritative for replacement of the local
RAD discovery cache, while the Registry remains the resource authority and a
Discover re-query can refresh the snapshot.

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

## 6. Agent And RAD Target Watch Contract

This section defines the target gRPC Watch contract for Agent/RAD. It does not
describe a currently implemented capability. An implementation must not expose
or advertise this behavior until the Agent/RAD abilities in the
[Agent API Spec](../ai/agent-api-spec.md) are implemented and negotiated. The
initial Nacos HTTP binding supports Discover but not Watch.

### 6.1 Identity And Initial Result

The canonical SDK Watch key is:

```text
(namespaceId, canonicalAgentReference, canonicalFilter, listenerIdentity)
```

The canonical reference preserves whether the caller selected an exact
version, a label, or latest. Canonical Filter construction applies defaults and
sorts and deduplicates set-valued fields. Listener identity is the same
listener instance supplied to cancellation. An implementation may multiplex
wire subscriptions, but it must preserve this public identity and callback
isolation.

The server evaluates Discover before creating a Watch. `NOT_FOUND` creates no
server or client Watch state. A successful `AgentSubscribeResponse` returns a
connection-scoped opaque `watchKey` and the current complete
`AgentDiscoveryResult`. The SDK maps that key to its canonical local Watch
key and does not parse it.

RAD itself still has exactly six root messages and defines no protocol-level
event envelope. The Nacos gRPC binding uses
`AgentDiscoveryNotifyRequest(watchKey, eventType, result?, errorCode?)` to
multiplex Watch events on the shared Payload connection. This request is a
binding object, not another RAD root message.

### 6.2 Complete Replacement And Listener Delivery

For `eventType=SNAPSHOT`, `AgentDiscoveryNotifyRequest` requires one complete
`AgentDiscoveryResult` and has no error. The client atomically replaces the
previous snapshot for the identified Watch with each accepted result and then
acknowledges it with `AgentDiscoveryNotifyResponse`. It must not merge calling
interfaces, Endpoint sets, or Endpoints from different snapshots. A changed resolved version,
`contentDigest`, or `sourceRevision` identifies a potentially new snapshot;
these tokens support equality and deduplication, not ordering.

An empty filtered shape is a valid complete result and replaces the previous
snapshot. Naming push-empty protection does not apply to RAD. Listener
exceptions are isolated from connection processing and from other listeners;
they do not turn an already accepted snapshot into an unacknowledged event.

### 6.3 Terminal Disappearance

If a previously discoverable target becomes absent, invisible, disabled, or
has no discoverable online target version, the server sends
`AgentDiscoveryNotifyRequest` with `eventType=TERMINATED`, no result, and
`errorCode=NOT_FOUND`. This event closes only the identified `watchKey`; the
shared Payload connection and every other Watch remain active. The client
delivers the terminal status, removes only that local Watch key and cached
snapshot, acknowledges the terminal event, and does not redo that Watch on
later reconnects. An existing Agent whose Filter currently matches no
interface or Endpoint remains a successful empty `SNAPSHOT` and is not
terminal.

### 6.4 Missed Push And Reconnect

When connection loss, a rejected notification, or binding-specific gap
detection means a push may have been missed, the client must re-run Discover
with the same namespace, canonical reference, and canonical Filter, then
atomically replace its cached snapshot. It must not reconstruct the missing
state by applying locally inferred deltas.

On gRPC disconnect, the server removes connection-scoped Watch state. The SDK
marks the corresponding local Watch records unregistered. After reconnect it
uses the new connection id to restore the same canonical local Watch keys. It
discards each old wire `watchKey`; every successful resubscription supplies a
new opaque `watchKey` and initial complete result, which becomes the new
snapshot before subsequent pushes. A terminal result during recovery follows
Section 6.3 instead of remaining in redo state.

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
