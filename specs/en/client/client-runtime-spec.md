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

# Nacos Client Runtime Spec

This document defines the shared runtime rules below the public Client SDK
interfaces. The public SDK capability boundary is defined by the
[SDK Spec](../sdk/sdk-spec.md). This spec defines how a client process connects
to Nacos, discovers server addresses, authenticates requests, negotiates
abilities, caches runtime data, and recovers runtime intent after reconnect.

Java is the current reference implementation. Other language SDKs should align
with these runtime semantics when the language runtime supports the same
behavior.

## 1. Scope

Client runtime owns:

- SDK initialization, namespace binding, property parsing, and lifecycle
  shutdown;
- server list resolution and refresh;
- client-side HTTP and gRPC transport selection;
- connection lifecycle, failover, TLS, and identity propagation;
- connection-scoped ability negotiation;
- local snapshots, local failover data, listener state, subscription state, and
  redo state;
- client-side metrics and diagnostic hooks.

Client runtime does not own:

- Config, Naming, AI, or Lock resource semantics;
- server-side AP/CP consistency, persistence, or dump ordering;
- Admin API and Maintainer SDK management contracts;
- plugin semantics, except for invoking client-side plugin extension points.

Domain specs define the meaning of a resource. Client runtime defines how the
SDK keeps its view of that resource usable during normal application execution.

## 2. Runtime Layers

Client runtime is layered as follows:

```text
Public SDK interface
  -> Service implementation and client proxy
     -> Server list, authentication, and transport runtime
        -> Connection, ability, cache, listener, and redo runtime
           -> Domain request or local recovery view
```

The service implementation may use gRPC, HTTP, local files, or a mix of them.
The public SDK behavior must remain stable even when the transport changes.

## 3. Design Rules

### 3.1 Runtime Client Is Not a Management Plane

Client runtime is optimized for application execution. It should provide fast
known-resource access, subscription, local recovery, and connection repair. It
must not silently introduce broad namespace, cluster, or domain management
capabilities. Management behavior belongs to Admin APIs or Maintainer SDKs.

### 3.2 Runtime Data Is Derived Unless Explicitly Declared

Local cache, failover files, listener status, subscription status, and redo
entries are derived from client intent or server responses. They are not
authoritative server-side state.

The only exception is an explicitly user-maintained local failover file. A
failover file can temporarily override the remote read view, but it still does
not write back to the server by itself.

### 3.3 Connection State Is a Recovery Signal

gRPC connection events are the trigger for listener resync, fuzzy watch resync,
Naming subscription redo, ephemeral instance redo, AI endpoint redo, and other
runtime repairs. Domain clients must treat reconnect as a new server-side
attachment point unless their domain spec defines a stronger contract.

### 3.4 Transport Security Is Runtime Infrastructure

Client-side authentication plugins, request identity headers, TLS, and mutual
TLS are runtime infrastructure. Domain request objects should not duplicate
transport security logic. Domain specs may define what resource identity is used
for permission checks, but client runtime owns how the request carries login
identity to the selected transport.

## 4. Runtime Components

| Component | Responsibility | Detail spec |
|-----------|----------------|-------------|
| Server list and connection | Resolve server addresses, refresh dynamic address lists, create HTTP/gRPC clients, reconnect on failure, and apply TLS. | [Client Connection And Failover Spec](client-connection-failover-spec.md) |
| Ability negotiation | Exchange client and server ability tables and gate optional features by the current connection ability state. | [Client Ability Negotiation Spec](client-ability-negotiation-spec.md) |
| Cache and redo | Maintain local snapshots, failover views, listener state, subscription state, and reconnect redo data. | [Client Local Cache And Redo Spec](client-local-cache-redo-spec.md) |
| Push and reconnect recovery | Define server push semantics, push retry, disconnect cleanup, and client recovery after reconnect. | [Runtime Push And Reconnect Spec](runtime-push-reconnect-spec.md) |

## 5. Domain Alignment

Config runtime behavior includes known-config reads, listener registration,
fuzzy watch, local failover files, encrypted-data-key snapshots, and server
query snapshots. Config content semantics remain defined by the
[Config Spec](../config/config-spec.md).

Naming runtime behavior includes service subscription, push processing, local
service-info cache, failover views, ephemeral instance redo, and subscriber
redo. Naming resource semantics remain defined by the
[Naming Spec](../naming/naming-spec.md).

AI runtime behavior includes endpoint registration, resource query,
subscription, capability checks, and compatibility handling for fast-moving AI
protocols. AI resource semantics remain defined by the
[AI Registry Spec](../ai/ai-registry-spec.md).

Distributed Lock runtime behavior is optional and experimental. A client must
check the server lock ability before sending lock operations. Lock semantics are
defined by the [Distributed Lock Spec](../lock/lock-spec.md).

## 6. Plugin Alignment

Client runtime may invoke client-side plugins:

- addressing plugins may contribute server-list resolution;
- auth plugins may login and provide request identity context;
- config encryption plugins may transform Config payloads and encrypted data
  keys.

Plugin behavior must follow the [Plugin Specs](../plugin/README.md). Client
runtime must treat plugin failure according to the corresponding plugin
contract instead of hiding the failure as a domain success.

## 7. Pending Issues

- Multi-language SDKs have not yet fully aligned on server list refresh,
  failover, redo, ability negotiation, and TLS behavior.
- Client runtime metrics and trace fields should follow the shared field and
  label guidance in the
  [Observability Hooks Spec](../design/foundation-observability-hooks-spec.md).
- Client-side auth, TLS, and encryption behavior may need separate child specs
  if they grow beyond the current plugin and connection contracts.
