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

# Agent API Spec

| Item | Value |
|---|---|
| Status | Experimental target contract; not an implemented-surface inventory |
| Target line | Nacos 3.3 |
| Scope | Client HTTP/gRPC, Admin HTTP/Maintainer SDK, and Console HTTP bindings for Agent and RAD |

This document binds the [Agent Management Spec](agent-management-spec.md) and
the [RAD Protocol Spec](rad-protocol-spec.md) to Nacos APIs. It is normative
for an implementation that advertises the new Agent/RAD abilities. Existing
A2A APIs remain governed by the [A2A Agent Spec](a2a-agent-spec.md) until the
new binding is implemented and negotiated.

## 1. API Families And Common Rules

| Surface | Transport | Primary caller | Responsibility |
|---|---|---|---|
| Client | HTTP and gRPC | Agent consumers and runtime publishers | Search, Discover, Watch, register, and deregister |
| Admin | HTTP | Maintainer SDK and management integrations | Agent CRUD, Version lifecycle, and runtime inspection |
| Console | HTTP | Nacos Console UI | UI-oriented facade over Admin semantics |

HTTP APIs follow the Nacos v3 conventions:

- Client paths start with `/v3/client/ai/agents`.
- Admin paths start with `/v3/admin/ai/agents`.
- Console paths start with `/v3/console/ai/agents`.
- Responses use `Result<T>`. New controllers use `@NacosApi`,
  `@Since(version = "3.3.0")`, the matching `ApiType`, `SignType.AI`, and
  `READ` or `WRITE` authorization.
- GET inputs are query parameters. Write inputs are JSON bodies. `agentName`
  is compared verbatim and is not a path variable.
- gRPC continues to use the common Nacos `Payload` stream and
  `metadata.type`; it does not add a protobuf service method.

The six RAD root messages are reused without creating another domain model.
Java may represent `AgentCatalogPage` as an equivalent
`Page<AgentCatalogEntry>`. `Result<T>`, gRPC wrappers,
`ClientLivenessInfo`, and Console-only views are binding objects and are not
part of the RAD Schema.

### 1.1 Namespace Rules

| Caller | Rule |
|---|---|
| Ordinary Client SDK | The SDK instance is bound to one namespace. Public methods do not accept a namespace argument. The proxy copies the request and injects the bound value before transport. |
| Client HTTP caller | `namespaceId` may be supplied explicitly. When omitted, the binding inserts the normalized default namespace `public` before invoking RAD. |
| Maintainer SDK and Admin API | A Maintainer SDK instance is not namespace-bound. Every request explicitly supplies `namespaceId`; no default-namespace overload is provided. |

If an ordinary Client SDK accepts a model that already contains a nonempty
`namespaceId`, it must reject a value different from the SDK namespace and
must not mutate the caller's object.

### 1.2 Concurrency, Results, And Errors

Agent metadata updates use `expectedMetaVersion`. Draft content updates use
`expectedContentDigest`. Lists are paged; a `RuntimeEndpointSnapshot` is a
complete, non-paged snapshot.

| Condition | Required result |
|---|---|
| Missing or invalid field, invalid URI/range, or duplicate endpoint natural key | Standard parameter error |
| Invisible or absent Discover target | `RESOURCE_NOT_FOUND`; no visibility distinction |
| Endpoint pre-registration when no Agent definition exists | Accepted after structural, authorization, quota, and conflict validation |
| Metadata CAS, content CAS, or publisher-payload conflict | `RESOURCE_CONFLICT` |
| Invalid Version lifecycle transition | `ILLEGAL_STATE` |
| HTTP heartbeat for unknown client | HTTP 404 and the distinct `HTTP_CLIENT_NOT_FOUND` application code |
| Unsupported negotiated transport capability | Local `FEATURE_NOT_SUPPORTED`; no remote request |
| Deregistration of a missing contribution | Success without change |
| Valid runtime query with no instances | Success with `items=[]` |
| Discover filter matches no value | A typed empty result as defined by RAD, not `NOT_FOUND` |

HTTP status and `Result.code` use the common v3 exception mapping. gRPC
responses expose equivalent error categories. The numeric value for a new
application error is allocated by the implementation change; it must not alias
ordinary `RESOURCE_NOT_FOUND`.

## 2. Client API

### 2.1 Java SDK Contract

The user-facing interface is named `AgentDiscoveryService`; the RAD acronym is
not required in application code. During A2A compatibility:

```text
AiService extends AgentDiscoveryService, A2aService
```

| Capability | Method | Input | Result |
|---|---|---|---|
| Search | `searchAgents` | `AgentSearchRequest` without a caller-controlled namespace | `Page<AgentCatalogEntry>` |
| Discover | `discoverAgent` | `AgentReference` | `AgentDiscoveryResult` |
| Filtered Discover | `discoverAgent` | `AgentReference`, `AgentDiscoveryFilter` | `AgentDiscoveryResult` |
| Watch | `subscribeAgent` | Reference, optional Filter, Listener | Current `AgentDiscoveryResult` |
| Cancel Watch | `unsubscribeAgent` | Same Reference, Filter, and Listener identity | `void` |
| Register | `registerAgentEndpoints` | `AgentEndpointRegistrationBatch` | `void` |
| Deregister | `deregisterAgentEndpoints` | `AgentEndpointDeregistrationBatch` | `void` |

`subscribeAgent` returns the current complete result and later delivers
complete replacement results. `getAll`, `selectOneHealthy`, protocol choice,
priority/weight selection, and actual Agent calling are local SDK helpers, not
additional remote operations.

Register is a natural-key upsert and does not replace omitted endpoints. The
SDK stores registration batches as redo intent. The initial implementation may
omit a new generic Agent-definition publish method, but existing
`A2aService.releaseAgentCard` remains functional through the compatibility
adapter. A later Client SDK revision will provide an optional code-first Agent
publish operation: `autoSubmit=false` creates a draft, and `autoSubmit=true`
runs the ordinary submit Pipeline. It is not force-publish and endpoint
registration never creates a definition implicitly.

### 2.2 Transport Matrix

| Capability | HTTP | gRPC |
|---|:---:|:---:|
| Search | Yes | Yes |
| Discover | Yes | Yes |
| Watch and push | No | Yes |
| Register and Deregister | Yes | Yes |
| Publisher heartbeat | Yes | Uses the gRPC connection lifecycle |

An HTTP-only SDK fails Watch locally; it must not simulate Watch by polling.
After a write timeout, an SDK may change transport only when it knows the
server did not process the request. An unknown gRPC write result must not be
blindly repeated through HTTP.

### 2.3 Client HTTP Paths

| Method | Path | Input | Result |
|---|---|---|---|
| GET | `/v3/client/ai/agents/search` | RAD search query | `Result<Page<AgentCatalogEntry>>` |
| GET | `/v3/client/ai/agents` | RAD reference and optional filter query | `Result<AgentDiscoveryResult>` |
| POST | `/v3/client/ai/agents/endpoints` | `AgentEndpointRegistrationBatch` | `Result<ClientLivenessInfo>` |
| DELETE | `/v3/client/ai/agents/endpoints` | JSON `AgentEndpointDeregistrationBatch` | `Result<Void>` |
| PUT | `/v3/client/ai/agents/endpoints/heartbeat` | No body | `Result<ClientLivenessInfo>` |

Search query names equal RAD field names. Repeated `tagsAll` values use AND;
repeated `protocolsAny` values use OR. `agentNameContains` is a literal,
case-sensitive substring.

Discover maps `agentName`, `version`, and `label` directly. Repeated filter
parameters are `protocol`, `transport`, and `endpointSource`.
`protocolVersion` is singular. `metadataSelector` is one URL-encoded JSON
object rather than dynamic `metadata.<key>` parameter names.

The Endpoint path deliberately uses only POST and DELETE. POST already upserts
complete endpoint values, so a general PUT would introduce ambiguous partial
update semantics. GET is unnecessary because consumers use Discover and
maintainers use `RuntimeEndpointSnapshot`. DELETE with a JSON body is the only
0.1 binding and requires clients and gateways that preserve that body.

### 2.4 HTTP Publisher Identity And Liveness

Endpoint write and heartbeat requests require:

```text
X-Nacos-Client-Id: http-<ipToken>-<processToken>-<clientSequence>-<createTimestamp>
Request-Module: AI
```

The server treats the client id as an opaque 1-to-256-character value matching
`[A-Za-z0-9._:-]+`. The official generator uses only
`[A-Za-z0-9-]`, includes at least 96 bits of random process entropy, uses
`clientSequence` to distinguish SDK instances in one process, and may include
a diagnostic PID token. The id is stable across retry, server switch, and
redo; a process restart creates a new id. It is routing identity, not a
credential.

`ClientLivenessInfo` contains only:

```text
heartbeatIntervalMillis < unhealthyTimeoutMillis < expireTimeoutMillis
```

The latest successful registration or heartbeat response controls scheduling.
One heartbeat keeps the whole client alive, independent of endpoint count.
Endpoint writes also refresh liveness. A client with no remaining endpoints is
removed and stops heartbeats.

| State | Runtime behavior |
|---|---|
| `ACTIVE` | Contributions use their current Naming health. |
| `UNHEALTHY` | After `unhealthyTimeoutMillis`, contributions remain discoverable with `healthy=false`. |
| `EXPIRED` | After `expireTimeoutMillis`, all contributions owned by the client are removed. |

The server routes HTTP publisher state by `clientId` through Distro type
`AI_AGENT_HTTP_CLIENT`. Only the responsible node owns the native client,
`lastActiveTime`, and timeout task. Peers receive complete client state needed
to rebuild the Naming/RAD projection. A new owner starts its failover grace
period only after receiving a complete snapshot; otherwise it returns
`HTTP_CLIENT_NOT_FOUND`, and the client redoes every expected endpoint group.
The first accepted write binds the client id to authenticated identity and
namespace. Later mismatches are rejected. The same string in another module
does not share liveness or cleanup state.

### 2.5 gRPC Payloads And Abilities

| Request | Response | Semantics |
|---|---|---|
| `AgentSearchRequest` | `AgentSearchResponse` | Search and return a page of catalog entries |
| `AgentDiscoveryRequest` | `AgentDiscoveryResponse` | One Discover |
| `AgentSubscribeRequest` | `AgentSubscribeResponse` | Subscribe or unsubscribe; a successful subscription returns an opaque `watchKey` and the current complete result |
| `AgentDiscoveryNotifyRequest` | `AgentDiscoveryNotifyResponse` | Push a `SNAPSHOT` or `TERMINATED` event for one `watchKey` and receive an acknowledgement |
| `AgentEndpointRegisterRequest` | `AgentEndpointOperationResponse` | Register one RAD batch |
| `AgentEndpointDeregisterRequest` | `AgentEndpointOperationResponse` | Deregister one RAD batch |

All requests report module `ai`. gRPC endpoint contributions belong to
`RequestMeta.connectionId`; no client id or heartbeat payload is added.
Disconnect removes that connection's contributions. Reconnect obtains a new
connection id and redoes endpoints and subscriptions.

`AgentSubscribeResponse.watchKey` is the binding-defined opaque identity for
the accepted wire subscription. The SDK maps it to the canonical local Watch
identity and does not parse it. `AgentDiscoveryNotifyRequest` contains
`watchKey` and `eventType`:

- `SNAPSHOT` requires one complete `AgentDiscoveryResult` and has no error;
- `TERMINATED` contains no result and, in this version, requires
  `errorCode=NOT_FOUND`;
- either event is acknowledged with `AgentDiscoveryNotifyResponse`;
- `TERMINATED` closes only the identified Watch on the shared Payload
  connection. It does not close that connection or any other Watch.

The SDK atomically replaces the cached result for `SNAPSHOT`. For
`TERMINATED`, it delivers the terminal status and removes only that Watch and
its redo state before acknowledging. After reconnect, the SDK discards the old
connection-scoped `watchKey`, subscribes again using its canonical local Watch
identity, and stores the new response `watchKey` and current result. These
request and response types are Nacos gRPC binding objects; they do not add to
the six RAD root messages.

The target ability keys are:

| Constant | Wire key | Meaning |
|---|---|---|
| `SERVER_AGENT_DISCOVERY_V1` | `agentDiscoveryV1` | Server accepts RAD Search, Discover, and Watch payloads |
| `SERVER_AGENT_ENDPOINT_V1` | `agentEndpointV1` | Server accepts RAD endpoint publication payloads |
| `SDK_AGENT_DISCOVERY_V1` | `agentDiscoveryV1` | SDK accepts RAD discovery push |

Legacy `SERVER_AGENT_REGISTRY`, `SERVER_AGENT_CARD_V1`, and
`SDK_AGENT_REGISTRY` gate only the old A2A contract. Absence of a new ability
does not authorize sending a RAD payload through a legacy fallback.

### 2.6 Idempotency And Redo

| Event | Required behavior |
|---|---|
| Repeat identical Register | Success without semantic change |
| Register changed non-identity fields | Upsert that publisher contribution |
| Duplicate natural key in one batch | Reject the complete batch |
| Repeat Deregister | Success without change |
| Repeat heartbeat | Refresh only client liveness |
| HTTP timeout | Retry with the same client id and identical payload using backoff |
| `HTTP_CLIENT_NOT_FOUND` | Mark all local endpoint intent unregistered and redo by complete group |
| gRPC reconnect | Redo endpoints and subscriptions under the new connection id |
| Cross-transport deregistration | Forbidden; one publisher identity cannot remove another transport's contribution |

The SDK records expected state before the first write. Shutdown performs a
best-effort deregistration; expiry remains the cleanup fallback. Parameter,
authorization, and publisher-conflict errors do not enter infinite redo.

## 3. Admin API And Maintainer SDK

Admin reads do not run an implicit data-plane Discover and do not inject
runtime endpoints into a Version descriptor.

### 3.1 Agent And Read Views

| Method | Path | Action | Result |
|---|---|---|---|
| POST | `/v3/admin/ai/agents` | Create Agent and initial draft atomically | `Result<AgentOverview>` |
| GET | `/v3/admin/ai/agents` | Read Agent and first bounded Version-summary page | `Result<AgentOverview>` |
| PUT | `/v3/admin/ai/agents` | Update writable Agent fields using metadata CAS | `Result<Agent>` |
| DELETE | `/v3/admin/ai/agents` | Delete Agent definition and Version content | `Result<Void>` |
| GET | `/v3/admin/ai/agents/list` | Filter and page Agent summaries | `Result<Page<AgentSummary>>` |
| GET | `/v3/admin/ai/agents/versions` | Page Version summaries | `Result<Page<AgentVersionSummary>>` |
| GET | `/v3/admin/ai/agents/version` | Read one exact Version definition | `Result<AgentVersionDetail>` |
| GET | `/v3/admin/ai/agents/runtime-endpoints` | Read one protocol's complete runtime snapshot, optionally filtered by Version | `Result<RuntimeEndpointSnapshot>` |

Runtime query input is `namespaceId + agentName + protocol + version?`.
`protocol` is required. Omitting `version` returns one item per natural
Endpoint key for the protocol with all bindings; supplying it retains only
matching bindings. The query does
not apply `endpointSourceOrder`, does not require a definition to exist, and
returns an empty item array when no instance exists.

Create contains writable Agent fields and a required `initialDraft`. Agent,
Version row, and Storage writes have one logical atomic outcome and compensate
partial failures. Update may change presentation, tags, extensions, enabled
state, owner, and scope, but not identity, Version content, labels, or the
derived catalog. Definition deletion immediately prevents ordinary discovery;
it does not delete independently owned runtime publications.

### 3.2 Version Lifecycle Paths

| Method | Path | Transition or action | Result |
|---|---|---|---|
| POST | `/v3/admin/ai/agents/draft` | Create a new draft, optionally copying one exact Version | `Result<AgentVersionDetail>` |
| PUT | `/v3/admin/ai/agents/draft` | Update one draft using content-digest CAS | `Result<AgentVersionDetail>` |
| DELETE | `/v3/admin/ai/agents/draft` | Delete one draft | `Result<Void>` |
| POST | `/v3/admin/ai/agents/submit` | `draft -> reviewing`, or the shared no-Pipeline transition | `Result<AgentVersionSummary>` |
| POST | `/v3/admin/ai/agents/publish` | `reviewed -> online` | `Result<AgentVersionSummary>` |
| POST | `/v3/admin/ai/agents/force-publish` | Audited Pipeline bypass to `online` | `Result<AgentVersionSummary>` |
| POST | `/v3/admin/ai/agents/redraft` | `reviewed -> draft` | `Result<AgentVersionSummary>` |
| POST | `/v3/admin/ai/agents/online` | `offline -> online` | `Result<AgentVersionSummary>` |
| POST | `/v3/admin/ai/agents/offline` | `online -> offline` | `Result<AgentVersionSummary>` |
| PUT | `/v3/admin/ai/agents/labels` | Update custom labels; `latest` stays server-managed | `Result<Agent>` |

Every action identifies `namespaceId + agentName + exact version`; an omitted
version never means latest for a write. `force-publish` uses ordinary Agent
WRITE permission rather than a new permission point, but every success and
failure records caller, resource identity, prior and target state, result,
request id, and time. Audit records omit descriptor and sensitive metadata.
The initial release does not expose a same-Version forced content replacement.

### 3.3 Maintainer SDK

`AiMaintainerService.agent()` returns `AgentMaintainerService`.
`AiMaintainerService.a2a()` remains during its compatibility window. The Agent
maintainer interface maps one-to-one to Admin HTTP and uses Request/Command
objects for compound writes. It is not namespace-bound, requires
`namespaceId` on every call, and does not add a Maintainer gRPC transport.

## 4. Console API

Console uses `/v3/console/ai/agents` and mirrors every Admin relative path,
request, result, lifecycle rule, and authorization intent. It is a UI facade,
not a second Agent application service.

The only Console-specific response is `ConsoleRuntimeEndpointView`, which
wraps `RuntimeEndpointSnapshot` and adds:

```text
namingServiceRef { namespaceId, groupName, serviceName }
```

The backend computes this reference; the browser does not implement the Agent
name codec or Naming service composer. A Version page first reads
`AgentVersionDetail`, creates protocol tabs from `callInterfaces[]`, and lazily
loads one runtime snapshot per selected protocol with the current Version
filter. It may query a CallInterface that omits `RUNTIME`; in that case it
shows any registered state separately and explains that it is not currently
discoverable through that Version. Runtime editing is not part of the initial
Agent Console API; the UI links to the Naming instance page for enable or
disable operations.

Console does not expose RAD Search, Discover, Watch, endpoint publication, or
remote Agent calling.

## 5. Implementation And Compatibility Requirements

An implementation must complete these together before advertising an Agent or
RAD ability:

1. API models, validation, error mapping, authorization, and audit;
2. gRPC payload registration and ability negotiation;
3. HTTP publisher Distro state, liveness, idempotency, and redo;
4. Java SDK namespace binding, cache, Watch, reconnect, and endpoint redo;
5. Admin/Maintainer and Console contracts;
6. old A2A facade conversion; and
7. OpenAPI, Java SDK, and Maintainer SDK integration-test scenario matrices and
   coverage registries.

Legacy Console A2A APIs are supported through the Nacos 3.4 line. Legacy Admin
and Maintainer A2A APIs remain through the Nacos 4.0 compatibility boundary.
Historical data migration and mixed-version rolling-upgrade behavior are a
separate specification and must not be inferred from this API-only contract.
