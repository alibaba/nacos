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

## 6. Ordering

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

## 7. Failure Rules

- A missing connection should cancel or skip push for that connection.
- A push timeout does not prove that the client failed to observe the change;
  it only means the server did not receive a successful ack in time.
- A client must be able to recover from missed push by re-query, resync, or redo.
- Server push must not hide authorization failures in the underlying query path.

## 8. Pending Issues

- AI runtime push and reconnect behavior should be refined when AI SDK
  subscription APIs stabilize.
- Push retry, timeout, and reconnect recovery observations should follow the
  shared field and label guidance in the
  [Observability Hooks Spec](../design/foundation-observability-hooks-spec.md).
