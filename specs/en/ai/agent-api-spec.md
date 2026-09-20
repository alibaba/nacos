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
| Client | HTTP and gRPC | Agent consumers and runtime publishers | Search, Discover, server-aware Watch, register, and deregister |
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
`Page<AgentSummary>`. `Result<T>`, gRPC wrappers,
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
| Valid runtime query with no instances | Success with `callInterface.endpointSets[0].endpoints=[]` |
| Discover filter matches no value | A typed empty result as defined by RAD, not `NOT_FOUND` |

HTTP status and `Result.code` use the common v3 exception mapping. gRPC
responses expose equivalent error categories. `HTTP_CLIENT_NOT_FOUND` is fixed
at `50404`; it must not alias ordinary `RESOURCE_NOT_FOUND`.

### External RAD migration admission

While the node's effective A2A authority is historical, native RAD Client Search, Discover,
Publish, complete Endpoint Register, and Watch admission/subsequent business reads MUST reject
with HTTP 409 / `AGENT_MIGRATION_IN_PROGRESS (50105)`. gRPC preserves the same detail code.
The binding checks admission after authentication and necessary input checks, before business
mutation or owner creation. Unprojected names and unrelated standard Agents are also fenced.
This is neither unsupported RAD nor a reason to fall back to the legacy protocol.

Reuse A2A effective-mode resolution: LEGACY, AUTO without a plan, AUTO/SYNCING and AUTO/QUIESCING
reject; fresh CANONICAL and an observed permanent CANONICAL marker allow access. A missing
marker alone does not prove readiness. Capability queries still advertise implementation support.
Whole-publication Deregister, local cancellation/shutdown, and existing legitimate owner heartbeats
retain their normal authentication/ownership checks. SDK partial deregistration that Registers a
remaining snapshot receives 50105 without altering confirmed intent or broadening removal to the
whole publication. Rejected Register/Watch requests cannot create owners or redo registration.
Pending HTTP Watch completion rechecks admission; a later gRPC Watch delivery terminates with
50105, and subsequent Discover is also fenced. Callers explicitly invoke or subscribe again after
cutover.

Apply this guard only to external RAD bindings, never to shared domain services. Old A2A wire,
Admin/Console, migration, indexing and internal projection retain their existing rules so migration
cannot fence itself. The historical QUIESCING mutation barrier remains unchanged.

## 2. Client API

### 2.1 Java SDK Contract

The user-facing interface is named `AgentDiscoveryService`; the RAD acronym is
not required in application code. During A2A compatibility:

```text
AiService.agent() -> AgentService extends AgentDiscoveryService, A2aService
```

| Capability | Method | Input | Result |
|---|---|---|---|
| Search | `searchAgents` | `AgentSearchRequest` without a caller-controlled namespace | `Page<AgentSummary>` |
| Discover | `discoverAgent` | `AgentReference` | `AgentDiscoveryResult` |
| Filtered Discover | `discoverAgent` | `AgentReference`, `AgentDiscoveryFilter` | `AgentDiscoveryResult` |
| Watch subscription | `subscribeAgent` | Reference, optional Filter, Listener | Current `AgentDiscoveryResult`, or `null` while the target is absent |
| Cancel Watch subscription | `unsubscribeAgent` | Same Reference, Filter, and Listener identity | `void` |
| Register | `registerAgentEndpoints` | `AgentEndpointRegistrationBatch` | `void` |
| Deregister | `deregisterAgentEndpoints` | `agentName, protocol, List<Endpoint>` | `void` |
| Code-first publish | `publishAgent` | `AgentPublishRequest` | `AgentVersionDetail` |

`subscribeAgent` is a transport-neutral SDK Watch. When the selected transport
and both peers advertise Watch, the SDK installs server-aware Wire Intent;
otherwise it preserves compatibility through bounded local Discover polling.
The initial successful Discover result is returned synchronously. If the target
is absent, the call returns `null` and retains a bounded pending intent. A
later Watch hint or fallback poll invokes the same authorized Discover. The
Listener receives only a new complete replacement result whose canonical
fingerprint differs from the cached result; it never receives a Wire hint.
`getAll`, `selectOneHealthy`, protocol choice, priority/weight selection, and
actual Agent calling are local SDK helpers, not additional remote operations.

`NacosAgentDiscoveryEvent` has event types `SNAPSHOT` and `UNAVAILABLE`. Its
existing `NacosAgentDiscoveryEvent(AgentDiscoveryResult)` constructor remains
the `SNAPSHOT` constructor. A `SNAPSHOT` exposes the complete result and no
error. `UNAVAILABLE` exposes a Nacos error code and message and no result.
Initial validation, authorization, and local or server Watch-capacity failures
are thrown synchronously and the rejected intent is removed from all local
Watch state. A later terminal authorization or capacity failure emits one
`UNAVAILABLE` event and removes the intent. A transient transport failure does
not delete intent; reconnect or fallback re-establishes it. `NOT_FOUND` emits
at most one unavailable transition for the current absent period, retains a
bounded pending intent, and emits a new `SNAPSHOT` when the target recovers.

One SDK instance keeps at most 300 distinct local Watch records
by default. `nacosAiAgentDiscoveryMaxSubscriptions` configures that Client
limit. Repeating the same canonical Reference, Filter, and Listener identity is
idempotent and consumes no new slot. A new subscription over the limit fails
synchronously with `CLIENT_OVER_THRESHOLD` and
`AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT`; it is not cached or scheduled.
Unsubscribe and shutdown release the slot. A server Watch binding MUST
independently enforce the same default of 300 active Wire Watches per owner
connection or HTTP Client. The current SDK installs one subscription per
public call. A batched Wire Watch operation MUST apply the same soft
pre-operation watermark:
when current usage is below the watermark it admits the whole normalized batch
even if the final count crosses it; at or above the watermark it rejects growth
atomically and never partially caches a batch.

The authoritative server default is configured in `application.properties` by
`nacos.ai.rad.capacity.watch.max-per-client=300`. It counts active gRPC
Wire Watches per connection and active HTTP batch items per HTTP Client. The
binding's independent hard item and request-byte bounds still apply. Server and
SDK limits are intentionally separate; a direct caller cannot rely on the SDK
limit as server admission.

An `AgentReference` with neither `version` nor `label` is the rollout-safe
default: it returns latest definition metadata and Runtime Endpoints compatible
with any current online Version. Explicit `label=latest` requests a strict
latest-only Runtime pool. Exact versions and custom labels remain exact after
resolution. Watch re-fetch and polling fallback preserve the same distinction
because they repeat the unchanged Discover request.

One registration batch is the complete desired state for the SDK publisher and
`(namespaceId, agentName, protocol)`. Register replaces the previous batch,
including each Endpoint's resolved `runtimeVersion` and `versionRange`; omitted Endpoints
are removed. Batch fields are optional per-field defaults, resolved before exact-range fallback
and validation. The SDK stores a deep copy of the complete normalized batch as redo intent.

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
`AgentPublishRequest` and `AgentDraftCreateRequest` are sibling subclasses of
`model.agent.base.AbstractAgentDraftRequest`, which holds Version content,
`basedOnVersion`, author, change description, and initial Agent metadata fields.
Only the Client request adds `autoSubmit`, whose default is `false`.
The caller does not supply a namespace. The proxy defensively copies the request.
The Client publication service uses the following rules:

- Creating a Version when the Agent currently has no Versions forces ordinary submit,
  even with autoSubmit=false. Creating an Admin/Console draft does not force submit.
- A current editable DRAFT is completely replaced, including protocols, author and change
  description. Its existence never counts as a new first Version; only this call's autoSubmit
  controls submit. Existing Agent presentation, owner and scope remain unchanged.
- Any existing REVIEWING, REVIEWED, ONLINE or OFFLINE Version is a no-op after request
  validation, identity/WRITE/visibility checks and the migration gate. Different valid content
  does not overwrite that Version or change latest; no-op does not load basedOnVersion content.
- Later missing Versions use autoSubmit normally. Direct content and basedOnVersion remain
  mutually exclusive; copied content replaces the entire definition.
- Creation, replacement and submit are single attempts. A failure is returned directly,
  including an uncertain write outcome; there is no equivalent-content recovery, publication
  redo, cross-server HTTP retry or cross-transport replay. This includes transparent retries
  inside the HTTP implementation: the Agent publication body is non-repeatable. A later explicit
  caller invocation uses then-current state. Failure never compensates by deleting a saved draft.

Only the ordinary Pipeline runs; automatic submit is not force-publish. It can result in
reviewing/reviewed/online. Endpoint registration never implicitly creates a definition.
Publication reuses the existing draft storage and state checks. It does not introduce a new
cross-store transaction or concurrent-draft CAS guarantee; those shared storage concerns
are outside this compatibility change. See Agent Storage for the existing failure boundaries.

Search and complete registration use root-package `AgentSearchRequest` and
`AgentEndpointRegistrationBatch`, containing business fields without namespace accessors.
Partial deregistration uses
`deregisterAgentEndpoints(String agentName, String protocol, List<Endpoint> endpoints)`;
there is no deregistration Java Request/Batch. The SDK defensively copies caller content
and supplies its instance namespace through HTTP parameters or the RPC envelope to query
and registration services. Publication keys and redo data retain namespace separately.
Partial deregistration registers the complete nonempty remainder or deregisters the whole
publication when empty, without mutating caller objects or collections. HTTP fields,
authorization, replacement and error semantics remain unchanged. Search/Register RPC
namespace is on the envelope rather than nested in the business request.
No 3.3 BETA Java compatibility wrappers are retained; historical A2A contracts are unchanged.


### Java model binding

Agent management and RAD concrete Java models share `com.alibaba.nacos.api.ai.model.agent`.
The former `model.rad` package is removed; legacy `model.a2a` remains unchanged.
Shared field-only classes live in `model.agent.base` as public abstract classes with protected
constructors. Public SDK parameters, return values, DTO members and collection elements use
concrete types. No polymorphic discriminator or additional JSON nesting is introduced.

Java models use `com.alibaba.nacos.api.ai.model.agent` as the root. Shared RAD models,
Search and RegistrationBatch stay in that package. `agent.admin` contains
`AgentDraftCreateRequest`, `AgentDraftUpdateRequest`, `AgentUpdateRequest`,
`AgentLabelsUpdateRequest` and `AgentVersionRequest`; `agent.client` contains `AgentPublishRequest`.
`agent.base` contains only `AbstractAgentMetadata` and `AbstractAgentDraftRequest`, both
abstract with protected constructors. Metadata shares metadata fields and extensions;
Draft shares version-definition fields and draft validation. Client publication and Admin
draft creation are sibling concrete subclasses; public APIs use concrete types.
Shared validation lives in `com.alibaba.nacos.api.ai.utils.AgentValidationUtils`, outside model.
Forms perform HTTP string parsing. Admin models remain shared by the Maintainer SDK,
Console and server; namespace comes from the Form or an explicit method argument.
JSON conversion uses `JsonUtils`/`NacosTypeReference`.

Management and discovery bind to the same concrete AgentCallInterface, EndpointSet and Endpoint.
Definition, raw runtime and discovery remain explicit projections with context-specific field constraints;
no additional CallInterface base or Endpoint subclass is exposed.
AgentSummary is the unified resource type; detail projections may include extensions and lists
omit them. AgentVersionDetail still extends AgentVersionSummary and Version lists do not load
protocol content. Management and Search use versionInfo.labels/onlineVersions with
AgentVersionSummary entries. Search omits management fields and non-online label targets;
management entries keep explicit empty labels arrays while Search permits omission.
This step changes Search/Admin/Console version-metadata JSON, preserving discovery results,
RPC envelope types, endpoints, version-storage bytes and canonical fingerprints.
Agent/MCP shared `ClientLivenessInfo` lives in `api.ai.model`.

See [the model consolidation contract](./client-ai-api-evolution-spec.md#6-agent--rad-java-model-consolidation-proposal)
for the complete abstract-base and concrete-request inventory.

### 2.2 Transport Matrix

| Capability | HTTP | gRPC |
|---|:---:|:---:|
| Search | Yes | Yes |
| Discover | Yes | Yes |
| Server Watch hint | Batch long poll | Connection push |
| Watch business-data refresh | Reuses Discover | Reuses Discover |
| Compatibility fallback | Local Discover polling | Local Discover polling |
| Register and Deregister | Yes | Yes |
| Code-first definition publish | Yes | Yes |
| Publisher heartbeat | Yes | Uses the gRPC connection lifecycle |

Watch hints never carry business data. The SDK always reuses the selected
Discover transport to materialize a changed snapshot. Ordinary Discover,
Watch re-fetch, and fallback polls renew only an HTTP Client, never its
Publisher. After a write timeout, an SDK may change transport only when it
knows the server did not process the request. An unknown gRPC write result must
not be blindly repeated through HTTP.

#### 2.2.1 Java SDK AI Transport Modes

The Java SDK configures protocol-neutral Agent/RAD and MCP operations with
`nacosAiTransportMode`. Its public values are `grpc`, `http`, and `auto`, and
the unset default remains `grpc`. Values are case-insensitive, but surrounding
whitespace and unknown values are rejected while creating `AiService`. This
property controls the protocol-neutral Agent and MCP operations; it does not
change the existing transport contracts of legacy A2A, Prompt, Skill, or
AgentSpec.

- `grpc`: synchronously attempts the initial gRPC connection while creating the
  SDK and keeps reconnecting asynchronously after failure, without HTTP fallback;
- `http`: does not start gRPC initially for protocol-neutral Agent operations.
  Another AI feature that only supports gRPC may start the shared gRPC client
  lazily under its existing contract;
- `auto`: also attempts gRPC synchronously during SDK creation. An operation
  prefers gRPC only when the connection is `RUNNING` and the complete
  `SERVER_RAD_V1` ability is negotiated; otherwise that invocation uses HTTP
  immediately and never waits for a background probe.

In `auto`, the client suspends the initial reconnect loop and settles
protocol-neutral AI routing on HTTP only when gRPC has never connected, remains `STARTING`, reaches
the configured gRPC retry count in failed asynchronous initial reconnects, and
at least one Agent or MCP HTTP operation has succeeded. `UNHEALTHY` means that a
connection existed previously and is not eligible for this startup fallback.
If another feature of the same `AiService` explicitly requires gRPC, the client
resumes and keeps retrying that connection, while protocol-neutral routing may
remain on its settled HTTP choice.

Agent Search and Discover and MCP query are reads. In `auto`, a connection-class
failure after selecting gRPC may be reread through HTTP. Definite business failures such as
authorization, validation, conflict, not-found, and capacity errors do not
trigger fallback. A connection-class failure is limited to a disconnected or
unregistered RPC connection, a connection that is no longer `RUNNING` after
the failed invocation, or an underlying gRPC `UNAVAILABLE` status.
Generic `SERVER_ERROR`, `BAD_GATEWAY`, unsupported ability/handler errors, and
other server responses are not transport evidence and must remain visible to
the caller. Definition publication never crosses transports after it is handed
to one transport. Agent or MCP Endpoint Publication selects an owner transport on its first
send and keeps that owner for replacement, deregistration, heartbeat, and redo
throughout the Publication lifetime.

Local Watch Intent is transport-neutral and is the sole listener/cache source
of truth. A Wire Watch has at most one active owner transport and generation.
Explicit `grpc` waits for gRPC reconnect and never creates an HTTP long poll;
explicit `http` uses only the HTTP batch long poll. In `auto`, gRPC Watch is
chosen only when the connection is `RUNNING` and both Watch abilities are
negotiated; otherwise HTTP Watch is used when its endpoint succeeds. A
connection-class failure may migrate only the Wire owner, never duplicate the
local listener record. Installing the new generation before retiring or
ignoring the old generation is safe because late and duplicate hints only
cause current-fact Discover and fingerprint comparison. When neither server
Watch binding is available, the SDK uses bounded local polling with the normal
Discover routing rules.

### 2.3 Client HTTP Paths

| Method | Path | Input | Result |
|---|---|---|---|
| GET | `/v3/client/ai/agents/search` | RAD search query | `Result<Page<AgentSummary>>` |
| GET | `/v3/client/ai/agents` | RAD reference and optional filter query | `Result<AgentDiscoveryResult>` |
| POST | `/v3/client/ai/agents/watch` | Form: `generation + timeoutMillis + watches`, where `watches` is a JSON array string | `Result<AgentWatchBatchResponse>` |
| POST | `/v3/client/ai/agents` | Form: `AgentPublishRequest`; complex fields are JSON strings | `Result<AgentVersionDetail>` |
| POST | `/v3/client/ai/agents/endpoints` | Form: `namespaceId` plus complete `AgentEndpointRegistrationBatch`, with `endpoints` as a JSON string | `Result<ClientLivenessInfo>` |
| DELETE | `/v3/client/ai/agents/endpoints` | Form: `namespaceId + agentName + protocol` publication identity | `Result<Void>` |
| PUT | `/v3/client/ai/agents/endpoints/heartbeat` | No body | `Result<ClientLivenessInfo>` |

Search query names equal RAD field names. Repeated `tagsAll` values use AND;
repeated `protocolsAny` values use OR. `agentNameContains` is a literal,
case-sensitive substring.

The Watch path is one request-scoped batch long poll, not one HTTP request per
Agent and not three subscribe/listen/cancel APIs. It requires
`X-Nacos-Client-Id` and `Request-Module: AI`. One request contains a monotonically
increasing local `generation`, a timeout from 1000 through 60000 milliseconds,
and the caller's complete current normalized Watch set for one effective
namespace. Each item contains a client-generated `clientWatchId`, its complete
`AgentDiscoveryRequest`, and the last materialized fingerprint. The server
returns the same generation, `changed=false` on timeout, or `changed=true` plus
only changed client Watch ids. It returns no descriptor, Endpoint, fingerprint,
or per-item authorization result. The client ignores ids removed after the
request began, fetches changed current items through Discover, and immediately
starts the next long poll. Adding or removing local intent interrupts or
supersedes the prior client request; server-side disconnect detection is an
optimization, not a correctness requirement.

One batch is limited by the configured Watch soft watermark and an independent
hard binding bound of 1000 items. Request size also follows the shared HTTP
form limit. Duplicate client Watch ids, mixed effective namespaces, malformed
fingerprints, and an empty Watch list are invalid. The first binding performs
request-level AI read authorization only; mandatory Discover re-fetch remains
the fine-grained visibility and content authorization boundary.

The HTTP binding additionally enforces per-node active-request and active-byte
limits plus a per-request byte limit through
`nacos.ai.rad.capacity.watch.http.max-active-requests-per-node`,
`nacos.ai.rad.capacity.watch.http.max-active-bytes-per-node`, and
`nacos.ai.rad.capacity.watch.http.max-request-bytes`. The 1000-item and
128-character client Watch id bounds also place a fixed upper bound on the
changed-id response. Capacity rejection is atomic and returns no partial set.

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
logical `AgentEndpointDeregistrationBatch` RAD command; the Java SDK exposes its three business parameters directly.

Definition publication uses a dedicated Form rather than a JSON body.
`provider`, `tags`, `extensions`, and `callInterfaces` are JSON strings; the
remaining values are ordinary Form fields. The Form's single `toRequest()`
call performs deserialization and validation, so the Controller does not call
`validate()` separately. Persistent Agent/Version publication does not require
the `X-Nacos-Client-Id` or `Request-Module` headers used by Endpoint publishers.

### 2.4 HTTP Publisher Identity And Liveness

Agent and MCP Endpoint write and Publisher heartbeat requests require:

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
`HTTP_CLIENT@@<externalClientId>`. Agent Search and Discover and MCP query may carry the same
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

The official SDK uses one stable external Client id and one heartbeat
coordinator for Agent and MCP HTTP Endpoint publications owned by the same
`AiService`. Each module keeps its own desired Publication state and sticky
owner transport. If a heartbeat reports that the HTTP Client is missing, the
coordinator marks every Agent and MCP HTTP Publication dirty before it redoes
either module, then recreates all expected Publications under the same Client
id. One module must not recreate the Client and thereby hide lost Publications
owned by the other module.

### 2.5 gRPC Payloads And Abilities

| Request | Response | Semantics |
|---|---|---|
| `AgentSearchRpcRequest` | `AgentSearchResponse` | Search and return a page of catalog entries |
| `AgentDiscoveryRpcRequest` | `AgentDiscoveryResponse` | One Discover |
| `AgentSubscribeRpcRequest` | `AgentSubscribeRpcResponse` | Install one authorized connection-owned Watch intent |
| `AgentUnsubscribeRpcRequest` | `AgentUnsubscribeRpcResponse` | Remove one connection-owned Watch intent |
| `AgentDiscoveryNotifyRequest` | `AgentDiscoveryNotifyResponse` | Push one invalidation, revalidation, or terminal hint; never a discovery result |
| `AgentPublishRpcRequest` | `AgentPublishRpcResponse` | Create an Agent draft in code and optionally run ordinary submit according to `autoSubmit` |
| `AgentEndpointRegisterRpcRequest` | `AgentEndpointOperationResponse` | Replace one complete RAD batch for the connection, Agent, and protocol |
| `AgentEndpointDeregisterRpcRequest` | `AgentEndpointOperationResponse` | Remove the connection's whole publication for one Agent and protocol |

All requests report module `ai`. gRPC endpoint contributions and Watches belong to
`RequestMeta.connectionId`; no client id or heartbeat payload is added.
Disconnect removes that connection's contributions. Reconnect obtains a new
connection id and redoes endpoints and the complete current Watch intent.

The `RpcRequest` suffix distinguishes Nacos Payload wrappers from the
transport-neutral RAD root messages. Search carries namespace on the envelope and a namespace-free business request.
Discover continues carrying its complete RAD request. Register carries namespace on the envelope and one
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

Subscribe carries a stable client-generated `clientWatchId`, the complete
`AgentDiscoveryRequest`, and an optional last materialized fingerprint. The
server returns a connection-scoped opaque `watchKey`, its optional observed
fingerprint, and `refreshRequired`. Unsubscribe accepts only that `watchKey`.
Notify carries `watchKey`, event type `INVALIDATE`, `REVALIDATE`, or
`TERMINATED`, an optional observed fingerprint only for invalidation, and a
required error code for termination. The client acknowledges `watchKey` and
whether the hint was accepted after marking the matching local intent dirty;
ACK does not mean Discover or listener execution completed. Unknown or stale
keys are rejected without mutating another connection's state.

The server push queue is latest-projection-oriented. Dirty tasks for the same
Watch may coalesce before execution. Once one Notify execution starts, it runs
to completion; a later dirty mark creates or merges into a subsequent task.
The server keeps no prior business snapshot and no per-Watch sequence. The
client handles loss, duplication, stale observed fingerprints, and A-B-A
coalescing by executing current-fact Discover and comparing the canonical
complete-result fingerprint. gRPC has no periodic full-data synchronization;
reconnect resubscription is the low-frequency state reconciliation.

The target ability keys are:

| Constant | Wire key | Meaning |
|---|---|---|
| `SERVER_RAD_V1` | `radV1` | Server accepts the complete Nacos 3.3 RAD v1 contract |
| `SERVER_RAD_WATCH_V1` | `radWatchV1` | Server accepts the Nacos RAD Watch hint binding |
| `SDK_RAD_WATCH_V1` | `radWatchV1` | SDK accepts Nacos RAD Watch hint push requests |

This ability is a compatibility and release unit rather than a per-handler
inventory. It covers Agent definition publication, Search and Discover, and
Runtime Endpoint publication because Nacos 3.3 implements, advertises, and
tests them as one RAD v1 capability set. Watch/Push is independently deployable
and therefore uses one separate server ability and one separate SDK push
ability. A gRPC Watch is selected only when both are negotiated; base
`SERVER_RAD_V1` alone never authorizes Watch payloads. HTTP Watch availability
is discovered by its HTTP result and does not use gRPC ability negotiation.

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
| gRPC reconnect | Redo complete endpoint batches and active Watch intents under the new connection id; perform Discover when the Subscribe response requires refresh |
| Lost, duplicate, or stale Watch hint | Mark current local intent dirty once and use current-fact Discover plus fingerprint comparison |
| Terminal Watch authorization or capacity error | Emit one unavailable event, remove local Wire Intent, and do not retry indefinitely |
| Transient Watch transport failure | Retain local intent and re-establish it through reconnect, AUTO routing, or polling fallback |
| Cross-transport deregistration | Forbidden; one publisher identity cannot remove another transport's contribution |

The SDK records expected state before the first write and serializes desired
batch changes per Agent and protocol. Shutdown performs a best-effort
whole-publication deregistration; expiry remains the cleanup fallback.
Parameter, authorization, and capacity errors do not enter infinite redo.
Unsubscribe and shutdown remove Wire Intent before releasing the local slot;
late notifications are acknowledged as stale and cannot invoke a removed
listener.

## 3. Admin API And Maintainer SDK

Admin reads do not run an implicit data-plane Discover and do not inject
runtime endpoints into a Version descriptor.

### 3.1 Agent And Read Views

| Method | Path | Action | Result |
|---|---|---|---|
| GET | `/v3/admin/ai/agents` | Read Agent and first bounded Version-summary page | `Result<AgentOverview>` |
| PUT | `/v3/admin/ai/agents` | Update writable Agent fields through the shared AI Resource update flow | `Result<AgentSummary>` |
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

The five typed requests in `model.agent.admin` are shared by the Maintainer SDK, Console
and server. HTTP Forms parse strings and bind namespace separately; they do not replace
SDK inputs. Complex values use `JsonUtils`/`NacosTypeReference`, while namespace comes
from the Form or explicit SDK argument.

Form size uses the shared Nacos HTTP form-size policy. The serialized
AgentVersion content is still independently limited by the Agent Management
contract.

Runtime query input is `namespaceId + agentName + protocol + version?`.
`protocol` is required. Omitting `version` returns one item per natural
Endpoint key for the protocol with all bindings; supplying it retains only
matching bindings. The query does
not apply `endpointSourceOrder`, does not require a definition to exist, and
returns `callInterface.endpointSets[0].endpoints=[]` with a retained RUNTIME Set when no instance exists.

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
the shared metadata CAS, and use `PUT /agents/scope`. This Form operation accepts
`namespaceId` (omitted or empty means `public`), required `agentName`, and required
`scope` (`PUBLIC` or `PRIVATE`, case-insensitive). It returns `Result<String>` with
`data="ok"`, requires Agent WRITE and resource write visibility, and retains the
A2A migration mutation guard. It changes only scope and its update timestamp,
audits the change, and schedules existing search and Watch invalidation. It does
not change owner, content, Version states, labels, or Runtime Endpoints. Repeating
the same scope succeeds. Invalid input, missing resources, and denied writes keep
the existing 400, 404, and 403 error contracts.

The built-in initial Agent scope is `PUBLIC`. Creation and publication requests
do not accept scope; publication, new Versions, equivalent retries, and Runtime
registration preserve the stored value. `AgentMaintainerService.updateScope`
exposes explicit-namespace and default-namespace overloads and returns `boolean`.
Console forwards the same relative `/scope` operation and uses the existing detail
page scope control; creation forms do not gain a visibility selector.
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
| PUT | `/v3/admin/ai/agents/labels` | Update custom labels; `latest` stays server-managed | `Result<AgentSummary>` |

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

The Console reads `publishPipelineInfo` from the selected Version. It must not
offer force publish for a fresh draft or an in-progress/approved review. The
force-publish action is shown only to a global administrator after the current,
non-historical review result is `REJECTED` and the Version is `reviewing` or
`reviewed`.

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
3. HTTP publisher Distro state, liveness, batch Watch long poll, idempotency,
   and redo;
4. Java SDK namespace binding, canonical cache, server-aware Watch, bounded
   polling fallback, reconnect, and endpoint redo;
5. Admin/Maintainer and Console contracts;
6. old A2A facade conversion; and
7. OpenAPI, Java SDK, and Maintainer SDK integration-test scenario matrices and
   coverage registries, including dual-transport Watch failure and recovery.

Legacy Console A2A APIs are supported through the Nacos 3.4 line. Legacy Admin
and Maintainer A2A APIs remain through the Nacos 4.0 compatibility boundary.
During this compatibility window, legacy A2A Endpoint APIs keep their existing
version-qualified Naming layout and replacement scopes. They are not rewritten
onto the new version-neutral Agent Naming service, because an old client cannot
construct the complete cross-Version publisher batch required by that service.
Historical data migration and mixed-version rolling-upgrade behavior are a
separate specification and must not be inferred from this API-only contract.

## Endpoint Consolidation Acceptance

Consolidation affects Client registration/publication, Admin/Maintainer, Console, and internal legacy A2A conversion. Runtime registration/complete replacement accept healthy and enabled, both defaulting to true and rejecting explicit null; resolve one input binding per Endpoint using Batch defaults. EndpointSet revisions and observations remain server-maintained. HTTP, gRPC, and both SDK JSON adapters must agree while preserving namespace, authorization, error, query, and subscription behavior.

The shared models and schemas follow the agreed endpoint contract. See the [endpoint test plan](../../../Codex/design/nacos-3.3-client-ai-api/MODEL_ENDPOINT_TEST_PLAN.md) for field policies, fixtures, 16 acceptance groups, and known gaps. The acceptance ledger distinguishes planned scenarios from executed tests.

### Agent JSON inclusion contract

The Agent forms/models delegate optional null inclusion to the serializer. Bindings must accept shared Endpoint defaults and deregister using uri/transport only; other fields do not change the removal key. See RAD/management Schema 0.5.0 and the [JSON regression matrix](../../../Codex/design/nacos-3.3-client-ai-api/MODEL_JSON_TEST_MATRIX.md). HTTP, gRPC, both SDK JSON adapters and merged/independent Console are regression targets.
