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
| Client | HTTP and gRPC | Agent consumers and runtime publishers | Search, Discover, register, and deregister; a later SDK provides local subscription by polling Discover |
| Admin | HTTP | Maintainer SDK and management integrations | Agent CRUD, Version lifecycle, and runtime inspection |
| Console | HTTP | Nacos Console UI | UI-oriented facade over Admin semantics |

HTTP APIs follow the Nacos v3 conventions:

- Client paths start with `/v3/client/ai/agents`.
- Admin paths start with `/v3/admin/ai/agents`.
- Console paths start with `/v3/console/ai/agents`.
- Responses use `Result<T>`. New controllers use `@NacosApi`,
  `@Since(version = "3.3.0")`, the matching `ApiType`, `SignType.AI`, and
  `READ` or `WRITE` authorization.
- GET inputs use query parameters. Other HTTP input encodings are defined by
  the corresponding Client, Admin, or Console binding. `agentName` is compared
  verbatim and is not a path variable.
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
| Maintainer SDK and Admin API | A Maintainer SDK instance is not namespace-bound. Admin HTTP Forms retain `namespaceId` and normalize an omitted or blank value to `public`. Maintainer Request and Command payloads do not contain `namespaceId`: an explicit method argument is the sole custom-namespace source, while convenience overloads always use `public`. |

If an ordinary Client SDK accepts a model that already contains a nonempty
`namespaceId`, it must reject a value different from the SDK namespace and
must not mutate the caller's object.

### 1.2 Concurrency, Results, And Errors

Agent metadata updates reuse the current shared AI Resource update flow. The
initial Agent Admin contract does not expose an Agent-specific
`expectedMetaVersion`; conditional metadata updates will be defined together
with the common `ai_resource` and `ai_resource_version` CAS capability. Draft
content updates are allowed only when the target Version is the Resource's
current `editingVersion` and remains in `draft` status. Lists are paged; a
`RuntimeEndpointSnapshot` is a complete, non-paged snapshot.

| Condition | Required result |
|---|---|
| Missing or invalid field, invalid URI/range, or duplicate endpoint natural key | Standard parameter error |
| Invisible or absent Discover target | `RESOURCE_NOT_FOUND`; no visibility distinction |
| Endpoint pre-registration when no Agent definition exists | Accepted after structural, authorization, and per-batch quota validation |
| Converged runtime projection contains conflicting publisher payloads | `RESOURCE_CONFLICT` |
| Invalid Version lifecycle transition | `ILLEGAL_STATE` |
| HTTP registration cannot establish or retain its Client, or heartbeat cannot find the Client/publication | HTTP 404 and the distinct `HTTP_CLIENT_NOT_FOUND (50404)` application code |
| Unsupported negotiated transport capability | Local `FEATURE_NOT_SUPPORTED`; no remote request |
| Deregistration of a missing contribution | Success without change |
| Valid runtime query with no instances | Success with `items=[]` |
| Discover filter matches no value | A typed empty result as defined by RAD, not `NOT_FOUND` |

HTTP status and `Result.code` use the common v3 exception mapping. gRPC
responses expose equivalent error categories. `HTTP_CLIENT_NOT_FOUND` is fixed
at `50404`; it must not alias ordinary `RESOURCE_NOT_FOUND`.

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
| Polling subscription | `subscribeAgent` | Reference, optional Filter, Listener | Current `AgentDiscoveryResult`, or `null` while the target is absent |
| Cancel polling subscription | `unsubscribeAgent` | Same Reference, Filter, and Listener identity | `void` |
| Register | `registerAgentEndpoints` | `AgentEndpointRegistrationBatch` | `void` |
| Deregister | `deregisterAgentEndpoints` | `AgentEndpointDeregistrationBatch` | `void` |
| Code-first publish | `publishAgent` | `AgentPublishRequest` | `AgentVersionDetail` |

`subscribeAgent` is a local SDK convenience rather than a server Watch or Push
operation. The SDK periodically executes Discover with the same Reference and
Filter. If the target is initially absent, it returns `null` but retains the
polling task. A later `NOT_FOUND` poll neither terminates the subscription nor
delivers an empty snapshot. When the target appears, or the resolved Version,
`contentDigest`, or any `sourceRevision` changes, the Listener receives a new
complete replacement result. `getAll`, `selectOneHealthy`, protocol choice,
priority/weight selection, and actual Agent calling are local SDK helpers, not
additional remote operations.

One SDK instance keeps at most 300 distinct local polling-subscription records
by default. `nacosAiAgentDiscoveryMaxSubscriptions` configures that Client
limit. Repeating the same canonical Reference, Filter, and Listener identity is
idempotent and consumes no new slot. A new subscription over the limit fails
synchronously with `CLIENT_OVER_THRESHOLD` and
`AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT`; it is not cached or scheduled.
Unsubscribe and shutdown release the slot. The later server Watch binding MUST
independently enforce the same default of 300 active Wire Watches per owner
connection. The current SDK installs one subscription per public call. A later
batched Wire Watch operation MUST apply the same soft pre-operation watermark:
when current usage is below the watermark it admits the whole normalized batch
even if the final count crosses it; at or above the watermark it rejects growth
atomically and never partially caches a batch.

An `AgentReference` with neither `version` nor `label` is the rollout-safe
default: it returns latest definition metadata and Runtime Endpoints compatible
with any current online Version. Explicit `label=latest` requests a strict
latest-only Runtime pool. Exact versions and custom labels remain exact after
resolution. Polling subscriptions preserve the same distinction because they
repeat the unchanged Discover request.

One registration batch is the complete desired state for the SDK publisher and
`(namespaceId, agentName, protocol)`. Register replaces the previous batch,
including its single `runtimeVersion` and `versionRange`; omitted Endpoints are
removed. The SDK stores that complete batch as redo intent.

One SDK instance has a soft watermark of 100 Endpoint publication entries
across all retained complete intents by default;
`nacosAiAgentEndpointMaxPublications` configures the local watermark. When the
pre-operation entry count is below the watermark, the SDK admits and caches a
whole validated batch even if the resulting count crosses it. At or above the
watermark, equal-size or shrinking replacement remains allowed, while a new
identity or growing replacement is rejected atomically. The server remains
authoritative and independently applies its configured per-Client watermark.
A local or server publication-capacity rejection is terminal for that attempted
identity: the public API throws the capacity exception and the SDK removes the
rejected publication from every heartbeat and reconnect redo cache instead of
retrying it indefinitely.

`deregisterAgentEndpoints` remains a convenience method over natural keys. The
SDK removes those keys from its expected batch and sends the complete remaining
batch through Register. When no Endpoint remains, it sends a whole-publication
deregistration. Existing `A2aService.releaseAgentCard` remains functional
through the compatibility adapter.

`publishAgent` is an optional, namespace-bound definition-publication step.
`AgentPublishRequest` reuses the Version content, `basedOnVersion`, author,
change description, and initial Agent metadata fields from
`AgentDraftCreateRequest`, and adds `autoSubmit`, whose default is `false`.
The caller does not supply a namespace. The proxy copies the request, uses the
SDK namespace, and never mutates the caller's object. `autoSubmit=false` only
creates or returns an equivalent draft. `autoSubmit=true` runs the ordinary
submit Pipeline after draft creation and returns the final observable
`reviewing`, `reviewed`, or `online` Version. It is not force-publish and
endpoint registration never creates a definition implicitly.

Equivalent retries for the same namespace, Agent, and exact Version converge.
A draft retry is idempotent. When an earlier `autoSubmit=true` request already
advanced equivalent content to `reviewing`, `reviewed`, or `online`, the retry
returns the existing Version. The same request may resume an existing draft by
changing only `autoSubmit` to `true`. Different content, author, change
description, or explicitly supplied initial metadata is a conflict.
`autoSubmit=false` against an advanced Version, and either mode against an
`offline` Version, returns illegal state or conflict. A submit failure does not
compensate by deleting the created draft.

### 2.2 Transport Matrix

| Capability | HTTP | gRPC |
|---|:---:|:---:|
| Search | Yes | Yes |
| Discover | Yes | Yes |
| Server Watch and push | No | No |
| Local SDK polling subscription | Reuses Discover | Reuses Discover |
| Register and Deregister | Yes | Yes |
| Code-first definition publish | Yes | Yes |
| Publisher heartbeat | Yes | Uses the gRPC connection lifecycle |

Polling subscriptions use the SDK-selected Discover transport and add no HTTP
path, gRPC payload, ability key, or Publisher renewal. Ordinary Discover and
subscription polls renew only an HTTP Client, never its Publisher. After a
write timeout, an SDK may change transport only when it knows the server did
not process the request. An unknown gRPC write result must not be blindly
repeated through HTTP.

#### 2.2.1 Java SDK Agent Transport Modes

The Java SDK configures protocol-neutral Agent/RAD operations with
`nacosAiTransportMode`. Its public values are `grpc`, `http`, and `auto`, and
the unset default remains `grpc`. Values are case-insensitive, but surrounding
whitespace and unknown values are rejected while creating `AiService`. This
property controls only the protocol-neutral Agent operations in this section;
it does not change the existing transport contracts of MCP, legacy A2A,
Prompt, Skill, or AgentSpec.

- `grpc`: synchronously attempts the initial gRPC connection while creating the
  SDK and keeps reconnecting asynchronously after failure, without HTTP fallback;
- `http`: does not start gRPC initially for protocol-neutral Agent operations.
  Another AI feature that only supports gRPC may start the shared gRPC client
  lazily under its existing contract;
- `auto`: also attempts gRPC synchronously during SDK creation. An operation
  prefers gRPC only when the connection is `RUNNING` and the complete
  `SERVER_RAD_V1` ability is negotiated; otherwise that invocation uses HTTP
  immediately and never waits for a background probe.

In `auto`, the client suspends the initial reconnect loop and settles Agent
routing on HTTP only when gRPC has never connected, remains `STARTING`, reaches
the configured gRPC retry count in failed asynchronous initial reconnects, and
at least one Agent HTTP operation has succeeded. `UNHEALTHY` means that a
connection existed previously and is not eligible for this startup fallback.
If another feature of the same `AiService` explicitly requires gRPC, the client
resumes and keeps retrying that connection, while Agent routing may remain on
its settled HTTP choice.

Search and Discover are reads. In `auto`, a connection-class failure after
selecting gRPC may be reread through HTTP. Definite business failures such as
authorization, validation, conflict, not-found, and capacity errors do not
trigger fallback. A connection-class failure is limited to a disconnected or
unregistered RPC connection, a connection that is no longer `RUNNING` after
the failed invocation, or an underlying gRPC `UNAVAILABLE` status.
Generic `SERVER_ERROR`, `BAD_GATEWAY`, unsupported ability/handler errors, and
other server responses are not transport evidence and must remain visible to
the caller. Definition publication never crosses transports after it is handed
to one transport. Endpoint Publication selects an owner transport on its first
send and keeps that owner for replacement, deregistration, heartbeat, and redo
throughout the Publication lifetime.

### 2.3 Client HTTP Paths

| Method | Path | Input | Result |
|---|---|---|---|
| GET | `/v3/client/ai/agents/search` | RAD search query | `Result<Page<AgentCatalogEntry>>` |
| GET | `/v3/client/ai/agents` | RAD reference and optional filter query | `Result<AgentDiscoveryResult>` |
| POST | `/v3/client/ai/agents` | Form: `AgentPublishRequest`; complex fields are JSON strings | `Result<AgentVersionDetail>` |
| POST | `/v3/client/ai/agents/endpoints` | Form: complete `AgentEndpointRegistrationBatch`, with `endpoints` as a JSON string | `Result<ClientLivenessInfo>` |
| DELETE | `/v3/client/ai/agents/endpoints` | Form: `namespaceId + agentName + protocol` publication identity | `Result<Void>` |
| PUT | `/v3/client/ai/agents/endpoints/heartbeat` | No body | `Result<ClientLivenessInfo>` |

Search query names equal RAD field names. Repeated `tagsAll` values use AND;
repeated `protocolsAny` values use OR. `agentNameContains` is a literal,
case-sensitive substring.

Agent Search is a resource-specific facade over shared Search Core with
`resourceType=agent` fixed. It maintains no second index and performs no
secondary business filtering after index pagination. `agentNameContains`,
`tagsAll`, and `protocolsAny` are converted to typed predicates from the
[AI Resource Search Spec](ai-resource-search-spec.md) before totals and page
truncation. When generic AI Resource Search queries only Agent, its candidate
eligibility, visibility, and currentness match this API; the response DTO,
ordering, and numbered-page contract continue to follow RAD.

With `nacos.ai.rad.search.mode=AUTO` or `INDEX`, the HTTP and gRPC bindings use
the shared index even when the Agent projection is not READY. They return the
current snapshot, which may be incomplete, and the server emits rate-limited
diagnostics without logging query content. `SCAN` explicitly selects the
legacy compatibility path. A binding does not expose the selected physical
path and does not downgrade or mix results within one request after an
index-call failure.

Discover maps `agentName`, `version`, and `label` directly. Repeated filter
parameters are `protocol`, `transport`, and `endpointSource`.
`protocolVersion` is singular. `metadataSelector` is one URL-encoded JSON
object rather than dynamic `metadata.<key>` parameter names.

The Endpoint path deliberately uses only POST and DELETE. POST replaces the
current publisher's complete batch for one Agent and protocol, so a general
PUT would duplicate the same replacement operation. GET is unnecessary because
consumers use Discover and maintainers use `RuntimeEndpointSnapshot`.

Endpoint HTTP writes use dedicated Forms. They do not bind public RAD request
objects directly and do not use `@RequestBody`. POST uses
`application/x-www-form-urlencoded`: `namespaceId`, `agentName`,
`runtimeVersion`, `versionRange`, and `protocol` are ordinary fields, while
`endpoints` is a JSON array string. DELETE uses ordinary `namespaceId`,
`agentName`, and `protocol` Form parameters. The Form normalizes an omitted or
blank namespace to `public`.

DELETE removes the current HTTP publisher's whole publication for the supplied
Agent and protocol. It does not accept endpoint keys. The official SDK
implements partial deregistration by updating its local expected batch and
POSTing the complete remainder; it uses DELETE only when that remainder is
empty. A direct HTTP caller likewise owns its complete desired batch. The
three-field DELETE Form is a binding object, not a replacement for the
application-facing `AgentEndpointDeregistrationBatch` RAD model.

Definition publication uses a dedicated Form rather than a JSON body.
`provider`, `tags`, `extensions`, and `callInterfaces` are JSON strings; the
remaining values are ordinary Form fields. The Form's single `toRequest()`
call performs deserialization and validation, so the Controller does not call
`validate()` separately. Persistent Agent/Version publication does not require
the `X-Nacos-Client-Id` or `Request-Module` headers used by Endpoint publishers.

### 2.4 HTTP Publisher Identity And Liveness

Endpoint write and Publisher heartbeat requests require:

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

The server wraps the external value as the Naming internal Client id
`HTTP_CLIENT@@<externalClientId>`. Search and Discover may carry the same
header. When the Client already exists, a query renews only Client liveness. It
does not create an empty Client or change any Publisher liveness, health, or
revision. An AI-module Distro Filter routes stateful requests by that internal
id; it does not extend the Naming HTTP API Distro Filter.

`ClientLivenessInfo` contains only:

```text
heartbeatIntervalMillis < unhealthyTimeoutMillis < expireTimeoutMillis
```

The initial Naming HTTP Client uses fixed effective values of 5000, 15000, and
30000 milliseconds; a caller cannot override them. Returning the values keeps
the SDK from hard-coding server policy. If Naming later makes them configurable,
the response carries the effective server values without changing the protocol
fields.

An HTTP Client tracks Client liveness and Publisher liveness separately. A
valid query renews Client liveness only. Endpoint writes and Publisher
heartbeat renew both the Client and every Publisher owned by that Client. One
Publisher heartbeat is independent of endpoint count. A Client with no
remaining Endpoint and no subscriber state is removed and stops heartbeats.

| State | Runtime behavior |
|---|---|
| `ACTIVE` | The Publisher is active and contributions use their current Naming health. |
| `UNHEALTHY` | After Publisher `unhealthyTimeoutMillis`, contributions remain discoverable with `healthy=false`; query cannot recover them. |
| `EXPIRED` | After Publisher `expireTimeoutMillis`, all contributions owned by the Client are removed, while a Client with subscriber state may remain. |

The HTTP Client reuses Naming
`Nacos:Naming:v2:ClientData`, `DistroClientDataProcessor`, Client snapshot,
verify, and repair. It adds no Agent-specific Distro type.
`HttpConnectionBasedClientManager` is a peer of
`ConnectionBasedClientManager` and `ClientManagerDelegate` routes it by the
internal id. Only the responsible node schedules native Client and Publisher
timeouts. Peers receive the standard Client state required to rebuild
Naming/RAD projections. Replica verify time provides the local timeout lower
bound after responsibility transfer; the Client does not maintain another
ownership flag. This normal Distro failover does not define a mixed-version
compatibility path.

The first stateful write binds the Client id to authenticated identity and
namespace. Later mismatches are rejected. Another module using the same
external Client id shares the same HTTP Client lifecycle. Old nodes have no
corresponding Agent Client HTTP API capability; this spec defines no execution
path for an upgrading cluster in which that API is not yet available.

### 2.5 gRPC Payloads And Abilities

| Request | Response | Semantics |
|---|---|---|
| `AgentSearchRpcRequest` | `AgentSearchResponse` | Search and return a page of catalog entries |
| `AgentDiscoveryRpcRequest` | `AgentDiscoveryResponse` | One Discover |
| `AgentPublishRpcRequest` | `AgentPublishRpcResponse` | Create an Agent draft in code and optionally run ordinary submit according to `autoSubmit` |
| `AgentEndpointRegisterRpcRequest` | `AgentEndpointOperationResponse` | Replace one complete RAD batch for the connection, Agent, and protocol |
| `AgentEndpointDeregisterRpcRequest` | `AgentEndpointOperationResponse` | Remove the connection's whole publication for one Agent and protocol |

All requests report module `ai`. gRPC endpoint contributions belong to
`RequestMeta.connectionId`; no client id or heartbeat payload is added.
Disconnect removes that connection's contributions. Reconnect obtains a new
connection id and redoes endpoints. Local polling subscriptions are not
connection-scoped server state.

The `RpcRequest` suffix distinguishes Nacos Payload wrappers from the
transport-neutral RAD root messages. Search and Discover wrappers carry their
corresponding RAD request. Register carries one
`AgentEndpointRegistrationBatch`. Deregister directly carries
`namespaceId + agentName + protocol`; it does not introduce a separate
identity object or accept partial Endpoint keys.

The endpoint handlers are Naming adapters. Register validates and converts the
submitted complete Endpoint batch to Naming Instances, then invokes Naming
batch registration. Deregister invokes Naming whole-publication deregistration.
They do not read or merge the previous publisher payload, add an Agent service
lock, or scan other publishers during a write. The admission step counts
Runtime Endpoint entries across only the current Client's complete Agent
publication batches. It evaluates the pre-operation entry count together with
the existing and requested target-batch sizes, and serializes that soft-watermark
check with the Naming replacement for the same Client.

Runtime Snapshot and Discover read the complete internal Naming
`ServiceStorage` projection. They construct one binding from each Instance's
singular runtime Version and Version-range metadata, retain ranges matching the
requested Version, and aggregate the resulting `bindings[]` and health by
public Endpoint natural key.

This version defines no `AgentSubscribeRequest`,
`AgentDiscoveryNotifyRequest`, `watchKey`, Push acknowledgement, or
connection-scoped Watch redo state. Poll scheduling, complete-result caching,
and change deduplication are local Java SDK behavior and do not extend the six
RAD root messages.

The target ability keys are:

| Constant | Wire key | Meaning |
|---|---|---|
| `SERVER_RAD_V1` | `radV1` | Server accepts the complete Nacos 3.3 RAD v1 contract |

This ability is a compatibility and release unit rather than a per-handler
inventory. It covers Agent definition publication, Search and Discover, and
Runtime Endpoint publication because Nacos 3.3 implements, advertises, and
tests them as one RAD v1 capability set. A future contract that can be deployed
or enabled independently, such as server Watch/Push, must use a separate
ability key.

Legacy `SERVER_AGENT_REGISTRY`, `SERVER_AGENT_CARD_V1`, and
`SDK_AGENT_REGISTRY` gate only the old A2A contract. Absence of a new ability
does not authorize sending a RAD payload through a legacy fallback.

### 2.6 Idempotency And Redo

| Event | Required behavior |
|---|---|
| Repeat identical Register | Success without semantic change |
| Register changed content, runtime Version, or range | Replace that publisher's complete service batch |
| Duplicate natural key in one batch | Reject the complete batch |
| Partial SDK Deregister | Remove keys from local expected state and Register the complete remainder |
| Last SDK Deregister or direct remote Deregister | Remove the publisher's whole service publication |
| Repeat whole-publication Deregister | Success without change |
| Repeat Publisher heartbeat | Refresh Client and Publisher liveness without changing Publisher payload or revision |
| Repeat query carrying an existing Client id | Refresh Client liveness only; do not create a Client or renew Publisher |
| HTTP timeout | Retry with the same client id and identical payload using backoff |
| `HTTP_CLIENT_NOT_FOUND` | Mark local endpoint intent unregistered and redo each complete service batch |
| Local or server publication capacity rejection | Throw the capacity exception and remove that identity from publication, heartbeat, and reconnect redo caches |
| gRPC reconnect | Redo complete endpoint batches under the new connection id; local polling subscriptions require no server redo |
| Cross-transport deregistration | Forbidden; one publisher identity cannot remove another transport's contribution |

The SDK records expected state before the first write and serializes desired
batch changes per Agent and protocol. Shutdown performs a best-effort
whole-publication deregistration; expiry remains the cleanup fallback.
Parameter, authorization, and capacity errors do not enter infinite redo.

## 3. Admin API And Maintainer SDK

Admin reads do not run an implicit data-plane Discover and do not inject
runtime endpoints into a Version descriptor.

### 3.1 Agent And Read Views

| Method | Path | Action | Result |
|---|---|---|---|
| GET | `/v3/admin/ai/agents` | Read Agent and first bounded Version-summary page | `Result<AgentOverview>` |
| PUT | `/v3/admin/ai/agents` | Update writable Agent fields through the shared AI Resource update flow | `Result<Agent>` |
| DELETE | `/v3/admin/ai/agents` | Delete Agent definition and Version content | `Result<Void>` |
| GET | `/v3/admin/ai/agents/list` | Filter and page Agent summaries | `Result<Page<AgentSummary>>` |
| GET | `/v3/admin/ai/agents/versions` | Page Version summaries | `Result<Page<AgentVersionSummary>>` |
| GET | `/v3/admin/ai/agents/version` | Read one exact Version definition | `Result<AgentVersionDetail>` |
| GET | `/v3/admin/ai/agents/runtime-endpoints` | Read one protocol's complete runtime snapshot, optionally filtered by Version | `Result<RuntimeEndpointSnapshot>` |

The initial Admin list reuses the shared AI Resource query contract.
`agentName` is a fuzzy name filter, and the optional `bizTag` is one fuzzy
business-tag filter. Multi-tag AND matching and Agent-specific collation rules
are not introduced by this binding. `scope` and `owner` are business filters
intersected with Visibility Plugin constraints before stable pagination. The
initial binding does not provide an `ai_resource.status` list filter.

Admin write inputs use `application/x-www-form-urlencoded`. Scalar identity
and resource-status fields are ordinary form parameters. HTTP Forms
contain `namespaceId`; the Request and Command objects produced from those
Forms do not. The following complex fields are JSON strings:

- Agent update: `provider`, `tags`, and `extensions`;
- draft create: `provider`, `tags`, `extensions`, and `callInterfaces`;
- draft update: `callInterfaces`; and
- label update: `labels`.

Form size uses the shared Nacos HTTP form-size policy. The serialized
AgentVersion content is still independently limited by the Agent Management
contract.

Runtime query input is `namespaceId + agentName + protocol + version?`.
`protocol` is required. Omitting `version` returns one item per natural
Endpoint key for the protocol with all bindings; supplying it retains only
matching bindings. The query does
not apply `endpointSourceOrder`, does not require a definition to exist, and
returns an empty item array when no instance exists.

There is no separate `createAgent` operation. `POST /draft` is the single
creation entry:

- when the Agent does not exist, it creates the Agent metadata, first Version
  row, and Storage content as one logical operation. The request must contain
  direct `callInterfaces` and must not contain `basedOnVersion`. Presentation
  metadata (`displayName`, `description`, `iconUrl`, `provider`, `tags`, and
  `extensions`) is optional. The server initializes `status=enable`, owner to
  the current caller identity, and scope through the shared default-visibility
  rule;
- when the Agent exists, it creates a subsequent draft from either direct
  `callInterfaces` or one exact `basedOnVersion`. First-create presentation
  metadata is rejected instead of being silently ignored.

The first-create Agent, Version row, and Storage writes have one logical atomic
outcome and compensate partial failures. Agent update may change presentation,
tags, extensions, and enabled state, but not identity, owner, scope, Version
content, labels, or the derived catalog. The server initializes owner on first
creation and the initial release exposes no owner-transfer operation. Scope
changes are a dedicated public/private visibility operation, are not part of
the shared metadata CAS, and are not exposed by the initial Agent API binding.
Definition deletion immediately prevents ordinary discovery; it does not
delete independently owned runtime publications.

### 3.2 Version Lifecycle Paths

| Method | Path | Transition or action | Result |
|---|---|---|---|
| POST | `/v3/admin/ai/agents/draft` | Create the Agent and first direct-content draft when absent, or create a subsequent direct/copy-based draft | `Result<AgentVersionDetail>` |
| PUT | `/v3/admin/ai/agents/draft` | Replace the current exact draft content; never create a missing Agent or Version or update Agent metadata | `Result<AgentVersionDetail>` |
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

Agent metadata update and draft-content update are separate operations.
`PUT /agents` changes only presentation, catalog, and resource-status fields in
`ai_resource`, preserves the existing owner and scope, and advances
`metaVersion`. `PUT /agents/draft` changes only the current exact draft's
CallInterface content, change description, and `contentDigest`.

### 3.3 Maintainer SDK

`AiMaintainerService.agent()` returns `AgentMaintainerService`.
`AiMaintainerService.a2a()` remains during its compatibility window. The Agent
maintainer interface maps one-to-one to Admin HTTP and uses Request/Command
objects for compound writes. It is not namespace-bound. Each operation has an
explicit-namespace form and a convenience form whose omitted namespace is
normalized to `public`. Request and Command objects do not contain
`namespaceId`; the method argument is the only custom-namespace source. It
does not add a Maintainer gRPC transport.

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
4. Java SDK namespace binding, cache, Discover polling subscriptions,
   reconnect, and endpoint redo;
5. Admin/Maintainer and Console contracts;
6. old A2A facade conversion; and
7. OpenAPI, Java SDK, and Maintainer SDK integration-test scenario matrices and
   coverage registries.

Legacy Console A2A APIs are supported through the Nacos 3.4 line. Legacy Admin
and Maintainer A2A APIs remain through the Nacos 4.0 compatibility boundary.
During this compatibility window, legacy A2A Endpoint APIs keep their existing
version-qualified Naming layout and replacement scopes. They are not rewritten
onto the new version-neutral Agent Naming service, because an old client cannot
construct the complete cross-Version publisher batch required by that service.
Historical data migration and mixed-version rolling-upgrade behavior are a
separate specification and must not be inferred from this API-only contract.
