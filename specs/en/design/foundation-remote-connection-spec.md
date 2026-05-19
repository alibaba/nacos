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

# Nacos Remote Connection Lifecycle Spec

This document defines the foundation-level lifecycle of server-side remote
connections in Nacos. It refines the remote connection part of the
[Foundation Capabilities Spec](foundation-capabilities-spec.md).

The remote connection layer owns transport connection state, request context,
push and acknowledgement plumbing, and connection protection. It does not own
domain request semantics.
The detailed request context and filter rules are defined by the
[Request Filtering And Runtime Context Spec](foundation-request-context-spec.md).

## 1. Scope

The remote connection lifecycle capability owns:

- gRPC transport startup for SDK and cluster sources;
- connection id creation and transport address capture;
- connection setup, registration, rejection, and unregister;
- connection metadata and ability table propagation;
- request metadata and request context creation;
- server-to-client push and acknowledgement matching;
- active-time refresh, stale connection detection, and runtime ejection;
- connection listener callbacks for domain runtime state.

It does not own:

- Config, Naming, AI, or auth payload semantics;
- public HTTP API behavior;
- durable domain state;
- SDK retry, failover, or server-list selection behavior, which is defined by
  the [Client Connection And Failover Spec](../client/client-connection-failover-spec.md);
- AP/CP consistency membership.

The public gRPC envelope and JSON payload policy are defined by the
[gRPC API Spec](../grpc-api/api-spec.md). This document defines the server-side
connection lifecycle that carries those requests.

## 2. Connection Model

`Connection` is the server-side abstraction for a live remote connection.
`GrpcConnection` is the default gRPC implementation.

| Model | Responsibility |
| --- | --- |
| `Connection` | Exposes connection metadata, labels, ability table, trace flag, synchronous request, async request, no-ack push, close, and connectivity check. |
| `ConnectionMeta` | Stores connection id, source, client ip, remote endpoint, local port, version, app, namespace, labels, TLS flag, create time, last active time, and push-queue block timestamps. |
| `ConnectionManager` | Owns the in-memory connection registry, per-client-ip counters, connection control checks, listener callbacks, active-time refresh, and runtime ejection. |
| `ClientConnectionEventListener` | Lets domains attach or release runtime state when a connection is registered or unregistered. |

Connection identity rules:

- `connectionId` is generated at gRPC transport readiness from timestamp,
  remote ip, and remote port;
- a connection must finish `ConnectionSetupRequest` processing before ordinary
  unary requests are accepted;
- labels must include source information, especially SDK or cluster source;
- cluster-source connections are internal and bypass SDK connection-limit checks;
- the ability table is negotiated during setup and is passed to request handlers
  through `RequestMeta`;
- `lastActiveTime` must be refreshed by accepted unary requests and push
  acknowledgements.

## 3. gRPC Server Surfaces

Nacos exposes two gRPC server sources:

| Source | Server | Port offset | Purpose |
| --- | --- | --- | --- |
| SDK | `GrpcSdkServer` | SDK gRPC port offset | Client-to-server requests and server push. |
| Cluster | `GrpcClusterServer` | Cluster gRPC port offset | Server-to-server requests. |

Both servers use `BaseGrpcServer` and expose:

- unary `Request/request` for ordinary request/response calls;
- bidirectional `BiRequestStream/requestBiStream` for setup, server push, and
  push acknowledgement;
- `AddressTransportFilter` to capture remote/local addresses and unregister on
  transport termination;
- `GrpcConnectionInterceptor` to put connection id and address attributes into
  gRPC context;
- source-specific interceptors, transport filters, keepalive settings, and
  inbound-message-size settings.

The server-side keepalive settings are transport protections. Domain modules
must consume connection lifecycle events rather than implement their own gRPC
transport heartbeat.

## 4. Setup And Registration

Connection setup runs through the bidirectional stream:

1. The transport layer creates connection attributes when the gRPC transport is
   ready.
2. The client sends `ConnectionSetupRequest` on the bidirectional stream.
3. The server builds `ConnectionMeta` from payload metadata, setup labels,
   client version, namespace, source, app name, local port, and TLS state.
4. The server creates a `Connection` through `ConnectionGeneratorService`.
5. The ability table from setup is stored on the connection when present.
6. SDK connections are rejected while the server is still starting.
7. `ConnectionManager.register` verifies connectivity, applies connection
   control rules, records tracing, stores the connection, increments per-ip
   count, and notifies listeners.
8. If ability negotiation was used, the server sends `SetupAckRequest` with the
   current server ability table.

Registration failure closes the connection. It must not leave domain runtime
state attached to the rejected connection id.

## 5. Unary Request Processing

Unary requests are processed by `GrpcRequestAcceptor`.

Request processing rules:

- requests are rejected while the server is starting, except server check;
- `ServerCheckRequest` is handled directly and returns the connection id;
- request handlers are resolved by request class simple name;
- the connection id in gRPC context must already be registered;
- malformed, unknown, or non-request payloads return error responses;
- `RequestMeta` must include client ip, connection id, client version, labels,
  and ability table from the registered connection;
- `RequestContext` must be populated with protocol, request target, app, remote
  endpoint, and source ip;
- request filters run before the domain handler;
- accepted unary requests refresh connection active time before domain handling.

Request handlers own payload semantics. The remote layer only resolves routing,
metadata, context, filtering, and response conversion.
Request filter execution is further constrained by the
[Request Filtering And Runtime Context Spec](foundation-request-context-spec.md).

## 6. Push And Acknowledgement

Server-to-client push uses the registered `Connection`.

Push rules:

- `sendRequestNoAck` converts a Nacos `Request` into a gRPC payload and writes
  it on the bidirectional stream;
- stream writes must be serialized because `StreamObserver.onNext` is not
  thread-safe;
- the gRPC write queue readiness must be checked before push;
- when the write queue is not ready, the connection records push-queue block
  timestamps and the push fails with connection-busy semantics;
- request/async push assigns a push request id and registers an ack future in
  `RpcAckCallbackSynchronizer`;
- bidirectional-stream `Response` payloads are treated as push acknowledgements
  and clear or complete the matching future;
- disconnect cleanup must clear acknowledgement state for the connection.

Push and acknowledgement plumbing must not define domain subscription semantics.
Config, Naming, and AI specs define what should be pushed and when.

## 7. Unregister And Listener Callbacks

A connection is unregistered when the transport terminates, active detection
fails, over-limit ejection closes it, or other server logic explicitly removes
it.

Unregister rules:

- the connection must be removed from the registry before listener callbacks;
- per-client-ip counters must be decremented and removed when they reach zero;
- the underlying transport must be closed;
- `ClientConnectionEventListener.clientDisConnected` must be invoked for cleanup;
- listener failures must be logged but must not stop other listeners.

Domain runtime managers may attach state to `connectionId` through
`clientConnected`, but must release or rebuild that state through
`clientDisConnected`. Durable state must not depend on connection lifetime.

## 8. Active Detection And Runtime Ejection

`ConnectionManager` starts a periodic runtime ejection task. The default
`NacosRuntimeConnectionEjector` performs stale detection and overload ejection.

Detection rules:

- connections whose active time exceeds `RuntimeConnectionEjector.KEEP_ALIVE_TIME`
  are candidates for active detection;
- connections whose push queue has remained blocked for the server-side block
  window are also candidates;
- active detection sends `ClientDetectionRequest` and waits for a successful
  response;
- a successful response refreshes active time;
- connections that do not respond successfully are unregistered.

Overload ejection rules:

- runtime load ejection applies only when a target load count is set;
- SDK connections may receive `ConnectResetRequest` with an optional redirect
  address;
- cluster connections must not be ejected by SDK load balancing logic;
- runtime ejector implementations may be loaded by SPI, but they must preserve
  `ConnectionManager` registry and listener semantics.

Connection control rules and TPS checks are cross-cutting protection points
defined by the Control plugin. They may reject or slow a connection path, but
must not change domain data ownership.

## 9. Domain Consumer Rules

Domains consuming remote connection lifecycle must follow these rules:

- bind only runtime state to `connectionId`;
- cleanup connection-bound state on disconnect;
- make disconnect handling idempotent;
- treat reconnect as a new connection unless the domain spec defines a
  resumable identity;
- use `RequestMeta` labels, source, ability table, and client version for
  compatibility decisions instead of re-parsing transport internals;
- never use connection existence as durable authorization, ownership, or
  persistence evidence;
- keep slow remote IO outside listener callbacks when it can block connection
  registration or cleanup;
- make push failure and connection-busy behavior visible through domain retry or
  resync semantics.

## 10. Related Specs

- [Foundation Capabilities Spec](foundation-capabilities-spec.md)
- [Cluster Membership Spec](foundation-cluster-membership-spec.md)
- [Internal RPC And Cluster Request Spec](foundation-internal-rpc-spec.md)
- [Request Filtering And Runtime Context Spec](foundation-request-context-spec.md)
- [gRPC API Spec](../grpc-api/api-spec.md)
- [Control Plugin Spec](../plugin/control-plugin-spec.md)
- [Trace Plugin Spec](../plugin/trace-plugin-spec.md)
