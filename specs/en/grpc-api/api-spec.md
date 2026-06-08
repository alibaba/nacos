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

# Nacos gRPC API Spec

This document defines the Nacos gRPC API model used by Java SDKs, Nacos server
nodes, and remote module handlers. Unlike the HTTP API, Nacos gRPC has a small
fixed proto surface and a Java payload type surface. Payload identity must align
with the [Resource Model Spec](../design/resource-model-spec.md), and public
client behavior must align with the [SDK Spec](../sdk/sdk-spec.md).
The server-side request filter and runtime context model is defined by the
[Request Filtering And Runtime Context Spec](../design/foundation-request-context-spec.md).

## 1. Design Model

Nacos gRPC is the primary remote protocol for SDK runtime traffic and server
cluster traffic. The proto file defines only transport-level methods:

| Service | Method | Shape | Purpose |
| --- | --- | --- | --- |
| `Request` | `request(Payload) returns (Payload)` | unary | Client or peer sends one request and receives one response. |
| `BiRequestStream` | `requestBiStream(stream Payload) returns (stream Payload)` | bidirectional stream | Connection setup, server push, and ack responses. |

Business operations are selected by `Payload.metadata.type`, not by separate
proto RPC methods. The value is the Java simple class name of a registered
`Request` or `Response` payload type.

### 1.1 Why Payload Uses JSON Objects

Nacos does not model every gRPC business request and response as a dedicated
protobuf message. Instead, the fixed protobuf `Payload` wraps JSON-serialized
Java semantic objects.

This is an intentional design choice:

- Nacos HTTP APIs and gRPC APIs can share the same semantic object definitions
  and validation model instead of maintaining two independent DTO families.
- During the original RPC selection, gRPC was compared with other RPC protocols
  and connector styles, including RSocket-like options. The remote layer was
  designed so a connector could be switched or made compatible without changing
  every business request object. gRPC later became the practical choice because
  of ecosystem activity, multi-language support, and operational maturity, but
  the connector abstraction remained.

The community accepts the trade-off. JSON inside protobuf introduces additional
serialization and deserialization CPU cost, and it cannot fully use protobuf's
safer schema model or native multi-language object generation. This cost is part
of the compatibility and abstraction decision.

## 2. Wire Contract

`Payload` contains:

| Field | Meaning |
| --- | --- |
| `metadata.type` | Java simple class name used by `PayloadRegistry`. |
| `metadata.clientIp` | Client IP set by the sender. Server-side request context uses connection metadata as the trusted source. |
| `metadata.headers` | Case-insensitive logical request headers copied to `Request.headers`. |
| `body.value` | JSON bytes of the Java request or response object. |

Payload classes must be registered through
`META-INF/services/com.alibaba.nacos.api.remote.Payload`; otherwise the receiver
cannot parse `metadata.type`.

Rules:

- Do not add one proto service method per business operation.
- Do not use Java package names in `metadata.type`; use the simple class name.
- Request and response classes must remain JSON-serializable.
- Request headers are transported through `metadata.headers`, not inside the JSON
  body.
- New request classes must define the module through `Request#getModule()`.

## 3. Ports And Connection Sources

Nacos starts two gRPC servers by default:

| Server | Default port | Source label | Caller |
| --- | --- | --- | --- |
| SDK gRPC server | `${server.port} + 1000` | `sdk` | SDK clients |
| Cluster gRPC server | `${server.port} + 1001` | `cluster` | Nacos server nodes |

The Java client may override the port offset with
`nacos.server.grpc.port.offset`. Server-side SDK and cluster servers use their
own default offsets.

Some handlers are annotated with `@InvokeSource`. When present, only the listed
source labels may invoke that payload type.

Server-to-server source rules, handler registration, server identity, and
cluster request retry boundaries are defined by the
[Internal RPC And Cluster Request Spec](../design/foundation-internal-rpc-spec.md).

## 4. Connection Lifecycle

The server-side lifecycle details are defined by the
[Remote Connection Lifecycle Spec](../design/foundation-remote-connection-spec.md).
Client-side server selection, reconnect, TLS, and ability negotiation behavior
is defined by the [Client Runtime Specs](../client/README.md), especially the
[Client Connection And Failover Spec](../client/client-connection-failover-spec.md)
and [Client Ability Negotiation Spec](../client/client-ability-negotiation-spec.md).
Request context initialization, request filters, auth/control hooks, and
parameter extraction are defined by the
[Request Filtering And Runtime Context Spec](../design/foundation-request-context-spec.md).
This section summarizes the public gRPC flow.

1. The client calls unary `ServerCheckRequest` to verify that the selected
   server is usable and to obtain a connection id. The server replies with
   `ServerCheckResponse`.
2. The client opens `BiRequestStream.requestBiStream`.
3. The first stream payload should be `ConnectionSetupRequest`.
4. Server creates and registers a `Connection` using the connection id, remote
   address, client version, namespace, labels, and ability table.
5. If the client sends an ability table, the server replies with
   `SetupAckRequest` containing server abilities.
6. Unary business requests are accepted only after the connection is registered.
7. Server push requests are sent over the stream and clients answer with response
   payloads such as `NotifySubscriberResponse`, `ConfigChangeNotifyResponse`, or
   `PushAckRequest`-related responses.

If the server is starting, the connection is unregistered, the request type is
unknown, parsing fails, or a handler throws, the server returns `ErrorResponse`.

## 5. Response And Error Contract

All gRPC responses extend `com.alibaba.nacos.api.remote.response.Response`:

| Field | Meaning |
| --- | --- |
| `resultCode` | `200` for success, `500` or an error code for failure. |
| `errorCode` | Nacos error code when failed. |
| `message` | Error or diagnostic message. |
| `requestId` | Request correlation id when available. |

`Response#isSuccess()` is true only when `resultCode == 200`.

`ErrorResponse` is used for transport and handler errors. For `NacosException`
and `NacosRuntimeException`, `errorCode` is copied from the exception. For other
throwables, `errorCode` falls back to `500`.

## 6. Authorization

gRPC authorization is applied by `RemoteRequestAuthFilter`. Shared identity,
resource, and action semantics are defined by the
[Auth And Permission Spec](../auth/auth-permission-spec.md).
The filter execution contract is defined by the
[Request Filtering And Runtime Context Spec](../design/foundation-request-context-spec.md).

Handlers should annotate `handle(...)` with `@Secured` when the operation needs
identity, authority, or server identity validation. The filter:

- copies `@Secured.apiType()` into the request context;
- skips non-inner authorization when auth is disabled;
- always checks inner API server identity when inner auth is enabled;
- parses identity and resource from request headers and payload;
- validates identity and action permission.

Inner cluster APIs should use `ApiType.INNER_API` and restrict invocation with
`@InvokeSource(source = {RemoteConstants.LABEL_SOURCE_CLUSTER})`. Detailed
inner server-to-server rules are defined by the
[Internal RPC And Cluster Request Spec](../design/foundation-internal-rpc-spec.md).

## 7. Payload Inventory

### 7.1 Common And Core

| Request type | Response type | Direction | Auth/source | Contract |
| --- | --- | --- | --- | --- |
| `ConnectionSetupRequest` | `SetupAckRequest` | stream | connection bootstrap | Register a gRPC connection with client version, namespace, labels, and ability table. |
| `ServerCheckRequest` | `ServerCheckResponse` | unary | none | Verify the selected server and return connection id and ability negotiation support before stream setup. |
| `HealthCheckRequest` | `HealthCheckResponse` | unary | none | Keep-alive health check. |
| `ClientDetectionRequest` | `ClientDetectionResponse` | stream push | none | Server-side client detection. |
| `ConnectResetRequest` | `ConnectResetResponse` | stream push | none | Ask the client to reconnect to a target server. |
| `ServerReloadRequest` | `ServerReloadResponse` | unary | inner, cluster source | Reload server remote context on a peer. See the [Internal RPC And Cluster Request Spec](../design/foundation-internal-rpc-spec.md). |
| `ServerLoaderInfoRequest` | `ServerLoaderInfoResponse` | unary | inner, cluster source | Query server loader metrics from a peer. See the [Internal RPC And Cluster Request Spec](../design/foundation-internal-rpc-spec.md). |
| `MemberReportRequest` | `MemberReportResponse` | unary | inner, cluster source | Report member metadata and update server member state according to the [Cluster Membership Spec](../design/foundation-cluster-membership-spec.md). |
| `PluginAvailabilityRequest` | `PluginAvailabilityResponse` | unary | handler exists | Query plugin availability on a node. Current code has a handler, but the payload is not listed in the core payload SPI file, so it must be registered before becoming an active gRPC contract. |

### 7.2 Config

| Request type | Response type | Action | Main fields | Contract |
| --- | --- | --- | --- | --- |
| `ConfigQueryRequest` | `ConfigQueryResponse` | read | `dataId`, `group`, `tenant`, `tag` | Query config content, md5, type, encrypted key, beta/tag metadata. |
| `ConfigPublishRequest` | `ConfigPublishResponse` | write | `dataId`, `group`, `tenant`, `content`, `casMd5`, `additionMap` | Publish or CAS-publish config. |
| `ConfigRemoveRequest` | `ConfigRemoveResponse` | write | `dataId`, `group`, `tenant`, `tag` | Remove config. |
| `ConfigBatchListenRequest` | `ConfigChangeBatchListenResponse` | read | `listen`, `ConfigListenContext[]` | Add or remove config listeners and return changed configs. |
| `ConfigChangeNotifyRequest` | `ConfigChangeNotifyResponse` | server push | `dataId`, `group`, `tenant` | Notify client that a config changed. |
| `ConfigFuzzyWatchRequest` | `ConfigFuzzyWatchResponse` | read | `groupKeyPattern`, `receivedGroupKeys`, `watchType`, `isInitializing` | Add or cancel fuzzy watch for config group keys. |
| `ConfigFuzzyWatchChangeNotifyRequest` | `ConfigFuzzyWatchChangeNotifyResponse` | server push | `groupKey`, `changeType` | Notify client of fuzzy-watch resource changes. |
| `ConfigFuzzyWatchSyncRequest` | `ConfigFuzzyWatchSyncResponse` | server push | `syncType`, `groupKeyPattern`, `contexts`, `totalBatch`, `currentBatch` | Sync fuzzy-watch initial or diff state. |
| `ClientConfigMetricRequest` | `ClientConfigMetricResponse` | read | `metricsKeys` | Query client config metrics. |
| `ConfigChangeClusterSyncRequest` | `ConfigChangeClusterSyncResponse` | inner | `dataId`, `group`, `tenant`, `lastModified`, `grayName`, legacy `isBeta`/`tag` | Sync config change events between server nodes through the [internal RPC model](../design/foundation-internal-rpc-spec.md); Config Notify semantics are defined by the [AP Consistency Spec](../design/foundation-ap-consistency-spec.md). Starting with the Nacos 3.3 line, server-side handling must not use legacy `isBeta` or `tag` fields to migrate beta/tag changes into `grayName`. |

### 7.3 Naming

| Request type | Response type | Action | Main fields | Contract |
| --- | --- | --- | --- | --- |
| `InstanceRequest` | `InstanceResponse` | write | `namespace`, `groupName`, `serviceName`, `type`, `instance` | Register or deregister an ephemeral instance. |
| `PersistentInstanceRequest` | `InstanceResponse` | write | `namespace`, `groupName`, `serviceName`, `type`, `instance` | Register or deregister a persistent instance. |
| `BatchInstanceRequest` | `BatchInstanceResponse` | write | `namespace`, `groupName`, `serviceName`, `type`, `instances` | Batch register or deregister instances. |
| `ServiceQueryRequest` | `QueryServiceResponse` | read | `namespace`, `groupName`, `serviceName`, `cluster`, `healthyOnly`, `udpPort` | Query service instances. |
| `ServiceListRequest` | `ServiceListResponse` | read | `namespace`, `groupName`, `pageNo`, `pageSize`, `selector` | List service names. |
| `SubscribeServiceRequest` | `SubscribeServiceResponse` | read | `namespace`, `groupName`, `serviceName`, `clusters`, `subscribe` | Subscribe or unsubscribe a service. |
| `NotifySubscriberRequest` | `NotifySubscriberResponse` | server push | `namespace`, `groupName`, `serviceName`, `serviceInfo` | Push service info changes to subscribers. |
| `NamingFuzzyWatchRequest` | `NamingFuzzyWatchResponse` | read | `namespace`, `groupKeyPattern`, `receivedGroupKeys`, `watchType`, `isInitializing` | Add or cancel fuzzy watch for service keys. |
| `NamingFuzzyWatchChangeNotifyRequest` | `NamingFuzzyWatchChangeNotifyResponse` | server push | `serviceKey`, `changedType` | Notify client of fuzzy-watch service changes. |
| `NamingFuzzyWatchSyncRequest` | `NamingFuzzyWatchSyncResponse` | server push | `groupKeyPattern`, `contexts`, `totalBatch`, `currentBatch` | Sync fuzzy-watch initial or diff state. |
| `DistroDataRequest` | `DistroDataResponse` | inner | `distroData`, `dataOperation` | Distro AP protocol data transport between server nodes through the [internal RPC model](../design/foundation-internal-rpc-spec.md); Distro semantics are defined by the [AP Consistency Spec](../design/foundation-ap-consistency-spec.md). |

### 7.4 AI

AI payload semantics are defined by the
[AI Registry Spec](../ai/ai-registry-spec.md) and each resource type spec.

| Request type | Response type | Action | Main fields | Contract |
| --- | --- | --- | --- | --- |
| `QueryMcpServerRequest` | `QueryMcpServerResponse` | read | `namespace`, `mcpName`, `version` | Query MCP server detail. |
| `ReleaseMcpServerRequest` | `ReleaseMcpServerResponse` | write | `serverSpecification`, `toolSpecification`, `resourceSpecification`, `endpointSpecification` | Release MCP server or a new version. |
| `McpServerEndpointRequest` | `McpServerEndpointResponse` | write | `mcpName`, `address`, `port`, `version`, `type` | Register or deregister an MCP endpoint. |
| `QueryAgentCardRequest` | `QueryAgentCardResponse` | read | `namespace`, `agentName`, `version`, `registrationType` | Query A2A AgentCard detail. |
| `ReleaseAgentCardRequest` | `ReleaseAgentCardResponse` | write | `agentCard`, `registrationType`, `setAsLatest` | Release an AgentCard or a new version. |
| `AgentEndpointRequest` | `AgentEndpointResponse` | write | `agentName`, `endpoint`, `type` | Register or deregister one Agent endpoint. |
| `BatchAgentEndpointRequest` | `AgentEndpointResponse` | write | `agentName`, `endpoints` | Replace this client's endpoints for an Agent. |
| `QueryPromptRequest` | `QueryPromptResponse` | read | `namespace`, `promptKey`, `version`, `label`, `md5` | Query Prompt by version, label, latest, or md5. |

Skill ZIP download and AgentSpec assembly are Java SDK interface capabilities,
but current Java client implementation uses HTTP/config composition rather than a
dedicated gRPC payload.

### 7.5 Lock

Lock domain semantics are defined by the
[Distributed Lock Spec](../lock/lock-spec.md). The current gRPC surface is
experimental and may change with that domain.

| Request type | Response type | Action | Main fields | Contract |
| --- | --- | --- | --- | --- |
| `LockOperationRequest` | `LockOperationResponse` | none in handler | `lockInstance`, `lockOperationEnum` | Try lock or release a Nacos distributed lock. |

## 8. Rules For Adding Or Changing gRPC APIs

1. Add a concrete `Request` and `Response` type, or reuse an existing type only
   when the operation is the same semantic contract.
2. Register both payload classes in the correct
   `META-INF/services/com.alibaba.nacos.api.remote.Payload` file.
3. Add a `RequestHandler<Request, Response>` bean and document its action,
   module, and source.
4. Add `@Since` to the new handler class to declare the first Nacos version that
   supports the gRPC API.
5. Add `@Secured` for SDK-facing or inner protected operations.
6. Add `@InvokeSource` for cluster-only payloads.
7. Keep request fields explicit and JSON-compatible.
8. Update this spec and the [SDK interface spec](../sdk/sdk-spec.md) when the
   operation is exposed through a public SDK interface.
9. For server-to-server payloads, also update the
   [Internal RPC And Cluster Request Spec](../design/foundation-internal-rpc-spec.md)
   or the domain spec that owns the cluster request semantics.
