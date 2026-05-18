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

# Nacos Internal RPC And Cluster Request Spec

This document defines the foundation-level server-to-server RPC model used by
Nacos cluster nodes. It expands the internal RPC part of the
[Foundation Capabilities Spec](foundation-capabilities-spec.md) and depends on
the [Cluster Membership Spec](foundation-cluster-membership-spec.md),
[Remote Connection Lifecycle Spec](foundation-remote-connection-spec.md),
[Request Filtering And Runtime Context Spec](foundation-request-context-spec.md), and
[gRPC API Spec](../grpc-api/api-spec.md).

## 1. Scope

Internal RPC is the transport and handler model for requests sent between Nacos
server nodes. It is used by Core, Config, Naming, and other domains when a node
must notify, query, verify, or synchronize state with another member.

This spec covers:

- cluster-source gRPC connections and caller behavior;
- request handler registration and source restrictions;
- server identity, inner API auth, and request filters;
- payload registration requirements;
- sync, async, and fan-out cluster request rules;
- boundaries between shared transport behavior and domain-owned payload
  semantics.

This spec does not define the AP or CP consistency algorithms themselves. Distro
data ownership, Raft group behavior, Config dump ordering, and Naming
anti-entropy rules must be defined by domain or consistency specs that use this
internal RPC foundation. Shared Config dump and local cache boundaries are
defined by the [Persistence And Dump Spec](foundation-persistence-dump-spec.md).

## 2. Design Model

Nacos internal RPC uses the same fixed gRPC `Payload` envelope as SDK-facing
gRPC APIs. Business meaning is selected by `Payload.metadata.type`, and the body
contains a JSON-serialized `Request` or `Response` object.

The cluster gRPC server uses the source label `cluster`. Server-to-server
clients created by `ClusterRpcClientProxy` also carry the label:

```text
RemoteConstants.LABEL_SOURCE = RemoteConstants.LABEL_SOURCE_CLUSTER
```

Internal RPC is therefore not a separate protocol family. It is a restricted
source, auth, and handler discipline on top of the remote layer.

The model is:

```text
member view
  -> ClusterRpcClientProxy per-member gRPC client
  -> Payload(metadata.type = request simple class name)
  -> RequestHandlerRegistry
  -> Request filters
  -> domain RequestHandler
  -> Response payload
```

## 3. Caller Model

`ClusterRpcClientProxy` owns server-to-server clients.

Caller rules:

- one cluster RPC client is created for each remote member using a stable
  `Cluster-{member.address}` client key;
- each cluster client routes to exactly one member address through its
  `ServerListFactory`;
- `MembersChangeEvent` refreshes cluster clients and destroys clients for
  members that leave;
- requests must receive server identity headers before they are sent;
- callers may use synchronous request, asynchronous request with callback, or
  fan-out to `allMembersWithoutSelf()`;
- callers must tolerate member changes, missing clients, stopped clients,
  timeouts, and stale member state.

Domain callers should check target member existence, member state, capability,
and client running state when the domain behavior requires it. A successful RPC
only means that the remote handler accepted and processed the request according
to its own contract; it does not imply cluster-wide convergence unless the
domain protocol says so.

## 4. Handler Registration And Source Restrictions

Server-side handlers extend `RequestHandler<T extends Request, S extends
Response>`. `RequestHandlerRegistry` scans handler beans after the Spring
context is refreshed and registers each handler by the request class simple
name.

Handler rules:

- one payload type should have one semantic handler in a server runtime;
- a cluster-only handler must declare `@InvokeSource` with the `cluster` source;
- when a handler declares `@InvokeSource`, calls from other source labels are
  rejected before request parsing reaches the handler;
- if a handler does not declare `@InvokeSource`, source restriction is not
  enforced by the registry, so new internal handlers should declare it
  explicitly;
- handler source restriction is a transport-level protection and must not
  replace auth or permission checks.

The handler owns the request semantics. The remote foundation owns dispatch,
source checks, request context, filters, and response conversion.

## 5. Security And Request Filters

Internal RPC must use the same security model as other protected gRPC requests.

Rules:

- inner cluster APIs should annotate `handle(...)` with
  `@Secured(apiType = ApiType.INNER_API)`;
- domain-specific inner APIs should set `signType` or resource information when
  the auth plugin needs a typed resource;
- `ClusterRpcClientProxy` must inject the configured server identity headers;
- `RemoteRequestAuthFilter` checks server identity for inner APIs when inner API
  auth is enabled;
- auth being disabled for public APIs must not be treated as permission to skip
  required inner server identity checks;
- namespace validation, parameter extraction, TPS control, and other request
  filters should be declared on handlers when the domain requires them.

Known request filter concerns include:

| Filter concern | Typical annotation or path | Rule |
| --- | --- | --- |
| Server identity and auth | `@Secured`, `RemoteRequestAuthFilter` | Protect inner APIs and parse auth context. |
| Parameter validation | `@ExtractorManager.Extractor` | Reuse shared parameter extraction and validation. |
| Namespace validation | `@NamespaceValidation` | Validate namespace existence where resource identity contains namespace. |
| TPS control | `@TpsControl` | Register and enforce stable control points for cluster traffic. |

## 6. Payload Registration

A request handler bean is not enough to create a valid gRPC payload contract.
The request and response payload classes must also be registered so the remote
layer can parse `Payload.metadata.type`.

Payload rules:

- request and response classes must extend the Nacos remote `Request` and
  `Response` models;
- payload classes must remain JSON-compatible;
- payload classes must be registered in the appropriate payload registry or
  `META-INF/services/com.alibaba.nacos.api.remote.Payload` file before they are
  treated as active gRPC contracts;
- new request classes must return a stable module from `Request#getModule()`;
- registering a payload does not make it a public API unless an interface spec
  explicitly exposes it.

If code contains a handler but the payload type is not registered, the handler
must be treated as an inactive or incomplete gRPC contract.

## 7. Current Cluster Request Categories

| Category | Request types | Owner | Contract |
| --- | --- | --- | --- |
| Member report | `MemberReportRequest`, `MemberReportResponse` | Core cluster | Report member metadata to a peer, mark the peer view as `UP`, reset failure state, and return the receiver's self member. Detailed member rules are defined by the [Cluster Membership Spec](foundation-cluster-membership-spec.md). |
| Server remote context | `ServerReloadRequest`, `ServerReloadResponse`, `ServerLoaderInfoRequest`, `ServerLoaderInfoResponse` | Core remote | Reload remote protocol context or query connection and load metrics from another node. |
| Config change sync | `ConfigChangeClusterSyncRequest`, `ConfigChangeClusterSyncResponse` | Config | Notify peers of a Config change so they can refresh dump and listener-visible state. Config Notify semantics are defined by the [AP Consistency Spec](foundation-ap-consistency-spec.md), while Config resource semantics remain defined by Config specs. |
| Naming Distro transport | `DistroDataRequest`, `DistroDataResponse` | Naming and Distro | Carry Distro verify, snapshot, sync, delete, and query operations between nodes. Distro ownership and convergence rules are defined by the [AP Consistency Spec](foundation-ap-consistency-spec.md) and Naming specs. |
| Plugin availability | `PluginAvailabilityRequest`, `PluginAvailabilityResponse` | Core plugin | Query plugin availability on a node. Current code has a handler, but a payload that is not registered must not be treated as an active gRPC contract. |

Domain specs may add more categories, but they must preserve the caller,
handler, auth, source, and payload rules in this document.

## 8. Request Outcome And Retry Semantics

Cluster RPC callers must define their own retry or compensation behavior.

Rules:

- targeted calls should identify the member explicitly and handle missing or
  stopped target clients;
- fan-out calls should use the current member view and tolerate partial success;
- async calls must define callback behavior, timeout handling, and retry
  scheduling;
- timeout or transport failure does not prove that the remote domain operation
  did not happen;
- response success means handler-level success for one target node, not global
  convergence;
- background retry tasks must be idempotent or guarded by timestamp, version,
  operation type, or domain state, following the
  [Task Execution Spec](foundation-task-execution-spec.md).

For example, Config change sync may be retried through its async notify task
path, while Naming Distro verify failures may emit domain events and schedule
repair. Local event behavior is defined by the
[Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md).
Those retry rules belong to the Config and Naming consistency specs.

## 9. Boundary Rules

- Internal RPC is not a public HTTP, SDK, or open gRPC API unless an interface
  spec explicitly says so.
- Source labels, connection existence, or member membership alone are not
  authorization. Inner requests must use server identity and the auth filter.
- `ClusterRpcClientProxy` must remain transport-oriented. It must not own
  Config, Naming, AI, or plugin payload semantics.
- Domain payloads must not depend on unbounded request body size, blocking
  callbacks, or slow handler-side remote IO on critical remote threads.
- Compatibility fields in internal payloads should be documented at the domain
  level when they affect rolling upgrade behavior.
- New cluster-only handlers should be added to the gRPC API inventory and linked
  to the domain spec that owns their semantic contract.

## 10. Related Specs

- [Foundation Capabilities Spec](foundation-capabilities-spec.md)
- [Cluster Membership Spec](foundation-cluster-membership-spec.md)
- [Remote Connection Lifecycle Spec](foundation-remote-connection-spec.md)
- [Request Filtering And Runtime Context Spec](foundation-request-context-spec.md)
- [AP Consistency Spec](foundation-ap-consistency-spec.md)
- [CP Consistency Spec](foundation-cp-consistency-spec.md)
- [Persistence And Dump Spec](foundation-persistence-dump-spec.md)
- [Task Execution Spec](foundation-task-execution-spec.md)
- [Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md)
- [gRPC API Spec](../grpc-api/api-spec.md)
- [Auth And Permission Spec](../auth/auth-permission-spec.md)
- [Config Spec](../config/config-spec.md)
- [Naming Consistency And Client State Spec](../naming/naming-consistency-client-spec.md)
- [Control Plugin Spec](../plugin/control-plugin-spec.md)
