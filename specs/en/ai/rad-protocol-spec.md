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

# Remote Agent Discovery Protocol Spec

| Item | Value |
|---|---|
| Status | Experimental; normative for protocol version `0.1.0` |
| Protocol version | `0.1.0` |
| Scope | Remote Agent search, discovery, watch, and runtime endpoint publication |
| Goal | Return Agent calling descriptors and currently available addresses through a small, stable model |

This document defines the transport-independent core semantics of the Nacos
Remote Agent Discovery (RAD) protocol. HTTP, gRPC, and SDK bindings may use
native types, but their wire fields and observable behavior MUST be equivalent
to this specification.

## 1. Positioning And Scope

RAD answers two questions:

1. Which Agents may satisfy a requirement when the target is not known?
2. Which calling protocols and endpoints are available for a selected Agent
   version?

RAD returns metadata needed to call a remote Agent. It does not proxy the call
and does not define Agent message, task, or session protocols.

### 1.1 Operations

RAD 0.1.0 defines five operations:

| Operation | Input | Output | Semantics |
|---|---|---|---|
| `Search` | `AgentSearchRequest` | `AgentCatalogPage` | Search candidate Agents with pagination |
| `Discover` | `AgentDiscoveryRequest` | `AgentDiscoveryResult` | Return one complete calling snapshot for an Agent version |
| `Watch` | `AgentDiscoveryRequest` | `AgentDiscoveryResult` stream | Return the initial and subsequent complete replacement snapshots |
| `Register` | `AgentEndpointRegistrationBatch` | Success or error | Replace the current publisher's complete runtime endpoint batch |
| `Deregister` | `AgentEndpointDeregistrationBatch` | Success or error | Remove endpoint keys from the publisher's desired batch |

`Watch` reuses the `Discover` request and result. RAD does not add an event
envelope around watched snapshots.

### 1.2 Out Of Scope

RAD 0.1.0 does not define Agent management lifecycle, client connection and
reconnection, internal storage, historical compatibility, MCP, call proxying,
credentials, retries, or load balancing. Agent resource and version semantics
are defined by the [Agent Management Spec](./agent-management-spec.md).

## 2. Common Constraints

### 2.1 Namespace

Every operation executes in exactly one effective namespace.

- A top-level request carries `namespaceId` once.
- Nested Agent references, filters, and endpoints do not repeat it.
- A binding may obtain the value from client configuration or request context.
  It MUST normalize the default namespace to `public` before entering RAD core
  semantics.
- Cache, watch, authorization, and publisher-contribution keys MUST include the
  effective namespace.

`namespaceId` follows the Nacos namespace contract and contains 1 to 128
characters from `[A-Za-z0-9_-]`.

### 2.2 Agent, Protocol, And Label Identity

The public Agent identity is `(namespaceId, agentName)`.

`agentName` MUST:

- contain 1 to 64 printable ASCII characters;
- contain at least one non-space character;
- be compared case-sensitively and verbatim;
- not be trimmed, lowercased, slugged, or otherwise rewritten.

`protocol` contains 1 to 32 characters and matches
`[A-Za-z0-9][A-Za-z0-9-]{0,31}`. `label` contains 1 to 64 characters and
matches `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`. Both are case-sensitive.

`latest` is a reserved label that resolves to the Agent's current latest
version. It MUST NOT appear in `AgentCatalogVersion.labels`.

### 2.3 Agent Version

An Agent version uses `MAJOR.MINOR.PATCH[-PRERELEASE]` and has a maximum length
of 64 characters. Numeric core identifiers MUST NOT contain leading zeroes.
Prerelease identifiers are dot-separated `[0-9A-Za-z-]+` values. A prerelease
identifier containing only digits MUST NOT contain leading zeroes unless it is
exactly `0`.

RAD 0.1.0 does not accept build metadata. Version identity and comparison are
case-sensitive. Ordering follows SemVer precedence and MUST NOT first convert
the version to a fixed-width integer.

### 2.4 Version Range

`versionRange` uses Maven/POM-style interval brackets, but every boundary is an
Agent version from Section 2.3 and comparison uses RAD SemVer rather than
Maven `ComparableVersion`.

| Form | Match rule |
|---|---|
| `[1.0.6]` | Match only `1.0.6` |
| `[1.0.0,1.0.6]` | `1.0.0 <= version <= 1.0.6` |
| `[1.0.0,2.0.0)` | `1.0.0 <= version < 2.0.0` |
| `[1.0.0,)` | `version >= 1.0.0` |
| `(,2.0.0)` | `version < 2.0.0` |

RAD 0.1.0 accepts one exact version or one continuous interval. It does not
accept a union of versions or intervals. An expression contains no spaces and
has at least one boundary. A missing lower boundary uses `(` and a missing
upper boundary uses `)`.

When both boundaries exist, the lower boundary MUST precede the upper boundary.
Equal boundaries are valid only when both ends are inclusive; the server then
canonicalizes `[version,version]` to `[version]`. Every other equal-boundary
form is invalid. An interval such as `[1.0.0,2.0.0)` promises only the stated
SemVer comparisons; prerelease versions are evaluated by SemVer precedence.
The server stores and compares the canonical form.

### 2.5 Protocol Version Negotiation

A binding declares support for RAD 0.1.0 through its documentation or Nacos
capability negotiation. RAD root messages do not carry a protocol-version or
schema-version field.

## 3. Public Model

### 3.1 Root Messages

The schema exposes exactly six root messages:

| Root message | Purpose |
|---|---|
| `AgentSearchRequest` | `Search` request |
| `AgentCatalogPage` | `Search` result |
| `AgentDiscoveryRequest` | `Discover` and `Watch` request |
| `AgentDiscoveryResult` | `Discover` and `Watch` complete snapshot |
| `AgentEndpointRegistrationBatch` | Complete desired batch for `Register` |
| `AgentEndpointDeregistrationBatch` | Publisher-client desired-state command for `Deregister` |

A language binding may reuse an exactly equivalent native type. For example,
Java may implement `AgentCatalogPage` as `Page<AgentCatalogEntry>` rather than
introducing another page class.

### 3.2 Common JSON Rules

- An absent optional value is omitted rather than represented as `null`.
- Ordinary objects reject unknown properties.
- Only `nativeDescriptor` and explicitly declared `metadata` maps are open
  content.
- An optional request array contains at least one item when present. An empty
  response collection is explicitly returned as `[]`.
- An empty filter object `{}` means no filtering.
- An empty metadata object `{}` is canonicalized to field omission. An empty
  `metadataSelector` is equivalent to no metadata filtering.

### 3.3 `AgentSearchRequest`

| Field | Required | Semantics |
|---|:---:|---|
| `namespaceId` | Yes | Effective namespace |
| `agentNameContains` | No | Case-sensitive literal substring match on `agentName` |
| `tagsAll[]` | No | Agent contains every supplied tag |
| `protocolsAny[]` | No | At least one online Version exposes any supplied calling protocol |
| `pageNo` | No | One-based page number; default `1` |
| `pageSize` | No | Page size; default `20`, maximum `100` |

Protocol filtering is a RAD result-semantic requirement, not a physical-index
contract. An implementation may evaluate the online Version catalog or use an
independent derived index. It must not encode protocol values as public Agent
tags.

Characters such as `%` and `_` that are special to a backing query language
MUST be treated as literals.

### 3.4 `AgentCatalogPage`, `AgentCatalogEntry`, And `AgentCatalogVersion`

`AgentCatalogPage` contains:

```text
totalCount / pageNumber / pagesAvailable / pageItems[]
```

Each `pageItems[]` entry is an `AgentCatalogEntry` relative to the request
namespace:

```text
agentName / displayName? / description? / iconUrl? / provider?
tags? / latestVersion
versions[] AgentCatalogVersion {
  version
  labels[]?
  protocols[]
}
```

Rules:

- `versions` lists every online version in descending SemVer order. Versions do
  not repeat, and `protocols` contains at least one unique value.
- There is no product-level hard limit on the number of online versions in an
  entry. The list MUST NOT be silently truncated. A binding's global response
  size limit still applies and produces its standard oversized-response error.
- A non-reserved label points to at most one version. `latest` MUST NOT appear
  in `labels`, and `latestVersion` MUST match one listed `version`.
- The entry does not repeat `namespaceId` and does not return protocol
  descriptors, endpoints, health, or management fields.
- Search does not promise a currently healthy endpoint. Discover determines
  current callability.

### 3.5 `AgentReference`

| Field | Required | Semantics |
|---|:---:|---|
| `agentName` | Yes | Agent name in the effective namespace |
| `version` | No | Select one online exact version |
| `label` | No | Resolve a label to one online version at request time |

`version` and `label` are mutually exclusive. Definition metadata always
resolves to one exact online version. When both are absent, that definition is
the current latest version, while Runtime Endpoint discovery uses every online
version as its compatibility target set. Explicit `label=latest` is different:
both definition metadata and Runtime Endpoints are restricted to the current
latest version. An exact version or any other label also uses one resolved
version for both parts.

### 3.6 `AgentDiscoveryFilter`

Every filter field is optional:

| Field | Semantics |
|---|---|
| `protocols[]` | Allowed calling protocols |
| `protocolVersion` | Exact match against candidate interfaces |
| `transports[]` | Allowed transports |
| `endpointSources[]` | Allowed `RUNTIME` or `DECLARED` sources |
| `metadataSelector` | Endpoint metadata contains every exact key/value pair |

Values within one array are ORed; distinct fields are ANDed. A filter only
prunes one discovery result. It does not select another Agent version and does
not perform load balancing.

### 3.7 `Endpoint`

All operations reuse one `Endpoint` model:

| Field | Required | Semantics |
|---|:---:|---|
| `uri` | Yes | Complete absolute calling URI, at most 2048 characters |
| `transport` | Yes | Canonical transport; 1 to 64 `[0-9A-Za-z+-]` characters, for example A2A `HTTP+JSON` |
| `priority` | No | Lower is preferred; integer `0..2147483647`, default `0` |
| `weight` | No | Weight within a priority; number `0..10000`, default `1` |
| `metadata` | No | At most 32 flat string key/value entries |
| `healthy` | Conditionally | Present only and always in a `RUNTIME` discovery result |

Context rules:

- Register MUST NOT submit `healthy`.
- A `DECLARED` endpoint MUST NOT contain `healthy`.
- A `RUNTIME` discovery endpoint MUST contain `healthy`.
- Deregister submits only `uri` and `transport`, the endpoint natural-key
  fields represented by the public object. It is a publisher-client convenience
  command; a Nacos binding applies it to local desired state before sending a
  complete replacement batch.

Runtime endpoints do not use `endpointId`.

A discovery result uses `AgentDiscoveryEndpoint`, which extends these Endpoint
fields with `bindings[] { runtimeVersion, versionRange }`. The field is absent
for `DECLARED` endpoints and is non-empty for every `RUNTIME` endpoint. It is
the sorted, de-duplicated union of enabled publisher bindings that made the
endpoint eligible for the current discovery target set. It exposes rollout
provenance without publisher identity or liveness timestamps.

### 3.8 Endpoint Natural Key And Normalization

The runtime endpoint natural key is:

```text
(namespaceId, agentName, protocol,
 normalizedHost(uri), effectivePort(uri), normalizedTransport)
```

Path, query, metadata, priority, and weight do not participate in identity.
Two endpoints in the same group cannot coexist only by using different paths.

Normalization rules:

- A URI contains a scheme and host and has an explicit or inferable effective
  port. `http` and `ws` infer port `80`; `https` and `wss` infer port `443`.
  Every other scheme requires an explicit port.
- A URI MUST NOT contain user-info or a fragment.
- Scheme and DNS host are lowercased; a DNS host uses an ASCII A-label.
- IPv4 and IPv6 use stable text forms.
- The output URI includes the effective port explicitly.
- Transport uses the Registry-accepted canonical value and is not
  automatically case-folded.
- Priority and weight are materialized as `0` and `1` before comparison.
- Metadata keys are sorted before comparison; map order does not affect
  equality.

### 3.9 `EndpointSet`

Declared and runtime sources share one object:

```text
EndpointSet {
  source = DECLARED | RUNTIME
  sourceRevision
  endpoints[] AgentDiscoveryEndpoint
}
```

`source` determines the `healthy` constraint. `AgentDiscoveryResult` does not
return `endpointSourceOrder`. The Registry emits `endpointSets[]` in the source
order declared by the selected Agent version and preserves the relative order
of remaining sources after filtering. A declared but currently empty source is
returned with `endpoints=[]` and a stable `sourceRevision`.

### 3.10 `AgentDiscoveryCallInterface` And `AgentDiscoveryResult`

```text
AgentDiscoveryResult
├── namespaceId / agentName / version / contentDigest
└── callInterfaces[] AgentDiscoveryCallInterface
    ├── protocol / protocolVersion?
    ├── descriptorMediaType / nativeDescriptor
    └── endpointSets[]
        ├── source / sourceRevision
        └── endpoints[]
```

`AgentDiscoveryCallInterface` is a data-plane projection. It is intentionally
different from the management-plane `AgentCallInterface` defined by the
[Agent Management Spec](./agent-management-spec.md): the discovery view omits
management and source-order fields and contains resolved endpoint sets.

Rules:

- `version` is the online exact version supplying definition metadata. For an
  omitted selector it is still the current latest version even though Runtime
  Endpoints can serve multiple online versions.
- One version has at most 16 calling interfaces. Protocols do not repeat.
- Calling interfaces retain their order in the Agent version definition.
- `nativeDescriptor` is any non-null JSON value.
- `descriptorMediaType` describes `nativeDescriptor`.
- `endpointSets[]` is authoritative for this discovery snapshot. Addresses
  inside `nativeDescriptor` MUST NOT override it.
- The result does not return display fields, owner, scope, extensions, or
  publisher identity.

### 3.11 Digest And Revision

`contentDigest` identifies the complete immutable version content:

- It is `sha256:` followed by 64 lowercase hexadecimal characters.
- It covers ordered calling interfaces, `nativeDescriptor`, internal source
  order, and declared endpoints.
- It excludes status, latest, labels, management metadata, and runtime
  endpoints.
- A consumer compares the complete value and does not calculate it.

Each discovery projection has one `sourceRevision`, scoped by namespace,
Agent, definition version, protocol, source, and selector semantics:

- It is an opaque equality token. It cannot be ordered or compared across
  scopes.
- It changes when endpoint membership, URI, transport, priority, weight,
  public metadata, health, or returned Runtime binding provenance changes.
- Heartbeat time, publisher count, or an internal storage revision does not by
  itself require a change.
- An empty endpoint set still has a stable revision.
- A `DECLARED` set uses the Version `contentDigest`. A Nacos `RUNTIME` set uses
  `murmur3-x64-128-v1:<32 lowercase hex>` generated by the deterministic
  projection contract in the [Agent Storage Spec](agent-storage-spec.md).
  Consumers still treat both forms as opaque and do not calculate them.

### 3.12 Endpoint Batches

`AgentEndpointRegistrationBatch` contains:

```text
namespaceId / agentName / runtimeVersion / protocol
versionRange?            # default: exact range [runtimeVersion]
endpoints[]              # 1..1000
```

`runtimeVersion` is the deployed implementation version. `versionRange`
describes Agent versions that the deployment can serve. When absent, the
server canonicalizes it to `[runtimeVersion]`. `runtimeVersion` MUST be
contained in the effective range.

For one publisher and `(namespaceId, agentName, protocol)`, this array is the
complete desired Endpoint batch. Register replaces the previous batch in full;
an omitted Endpoint is removed. One publisher therefore has one effective
`runtimeVersion` and `versionRange` for this scope at a time. Changing either
value is a complete replacement, not an internal group update.

`AgentEndpointDeregistrationBatch` contains:

```text
namespaceId / agentName / protocol
endpoints[] { uri, transport }
```

`AgentEndpointDeregistrationBatch` is retained as an application-facing
convenience object. The publisher client removes the supplied natural keys
from its locally cached registration batch and registers the complete
remaining batch. When no Endpoint remains, it deregisters the whole publisher
publication for `(namespaceId, agentName, protocol)`. A Nacos server does not
perform a partial read-merge-write for this object.

## 4. Search

Search MUST:

1. return only visible, enabled Agents with at least one online version and a
   valid latest version;
2. apply `agentNameContains`, `tagsAll`, and `protocolsAny`;
3. sort by the original `agentName` in case-sensitive ascending ASCII order;
4. provide stable pagination for the same request and data snapshot;
5. return `totalCount`, `pageNumber`, `pagesAvailable`, and `pageItems`;
6. avoid loading or returning complete descriptors and endpoints.

`pageNo` defaults to `1`; `pageSize` defaults to `20` and is at most `100`.

Search reuses the shared Search Core defined by the
[AI Resource Search Spec](ai-resource-search-spec.md) and fixes the request to
`resourceType=agent`:

- `agentNameContains` maps to case-sensitive `LITERAL_CONTAINS`, where `%`,
  `_`, and the escape character are all literals;
- `tagsAll` maps to case-sensitive `EXACT_ALL`;
- `protocolsAny` maps to case-sensitive `EXACT_ANY`;
- different filter categories combine with AND, and filtering, visibility,
  and currentness checks all occur before totals and page truncation; and
- the complete online-Version catalog comes from the current Search document,
  while Runtime Endpoints, health, Publishers, and heartbeats enter neither the
  Search index nor its response.

`nacos.ai.rad.search.mode` selects `AUTO`, `INDEX`, or `SCAN`. `AUTO` and
`INDEX` use the shared index before and after Agent projection readiness. When
the generation is not READY, they return the current snapshot, whose total and
pages may be incomplete, and emit rate-limited diagnostics without logging
query content. `SCAN` explicitly selects the legacy compatibility path. An
index-call failure does not cause per-request fallback, and one request never
mixes the two paths. All three modes preserve this section's filtering,
ordering, visibility, and version-catalog semantics.

## 5. Discover

`AgentDiscoveryRequest` contains:

```text
namespaceId
reference: AgentReference
filter?: AgentDiscoveryFilter
```

The Registry performs Discover in this order:

1. Find `agentName` verbatim in the effective namespace.
2. Resolve one definition version using `version`, `label`, or latest.
3. Verify visibility, Agent enabled state, and definition-version online state.
4. Load calling interfaces in definition-version order. These interfaces are
   authoritative; protocols removed from latest metadata are not resurrected
   by an older Runtime publication.
5. Build the Runtime compatibility target set. When both `version` and `label`
   are absent it contains every current online version. Otherwise it contains
   only the exact resolved version, including explicit `label=latest`.
6. Retain runtime bindings whose `versionRange` contains at least one target
   version, and return the matching binding union on each endpoint.
7. Exclude `enabled=false` runtime instances and retain both
   `healthy=true` and `healthy=false` instances.
8. Aggregate matching contributions with the same public endpoint natural key.
9. Apply the optional filter.
10. Return a complete snapshot ordered by calling interface, source, priority,
   and stable natural key.

The fixed shapes for an empty filtered result are:

| Unmatched level | Result shape |
|---|---|
| `protocols` or `protocolVersion` | `callInterfaces=[]` |
| `endpointSources` | Keep the interface and return `endpointSets=[]` |
| `transports` or `metadataSelector` | Keep the endpoint set and return `endpoints=[]` |

A runtime endpoint with `healthy=false` remains in the result. Selecting only
healthy instances, applying priority and weight, and defining fallback when no
healthy instance exists are consumer concerns.

## 6. Watch

Watch uses the same request and result as Discover.

- The Registry first evaluates Discover. If it returns `NOT_FOUND`, no watch is
  created.
- A successful Watch first emits the current complete
  `AgentDiscoveryResult`.
- Every later notification is another complete replacement result, without an
  event envelope.
- A changed resolved version, `contentDigest`, or any `sourceRevision` produces
  a new snapshot.
- Matching runtime registration, update, deregistration, or liveness changes
  produce a snapshot when the public projection changes.
- Internal changes that do not change the public projection SHOULD NOT produce
  duplicate notifications.
- If a previously discoverable target becomes `NOT_FOUND`, the binding sends a
  terminal `NOT_FOUND` status and closes the watch.
- A consumer atomically replaces its previous snapshot with each new result.
- Subscriber identity, acknowledgement, reconnect, replay, and backpressure
  are binding concerns.

A binding may use its own transport envelope for snapshot and terminal
delivery. Such an envelope is not a RAD public model and does not extend the
six root messages in Section 3.1.

The equivalent cancellation key includes `namespaceId`, canonical
`AgentReference`, filter, and subscriber identity.

## 7. Register And Deregister

### 7.1 Validation And Pre-registration

Register verifies:

- request structure, endpoint constraints, authorization, and capacity;
- valid `runtimeVersion` and `versionRange`, with the runtime version contained
  in the range;
- no duplicate natural key in one batch;
- the request does not submit or overwrite `protocolVersion`.

Register validation is limited to the submitted complete batch. It does not
scan other publishers or reserve a natural Endpoint key before writing.

Register does not require the Agent, the runtime version, a range boundary, a
version within the range, or a corresponding calling interface to exist. It
therefore supports endpoint pre-registration.

Pre-registration creates only a runtime publication. It does not implicitly
create an Agent, version, or calling interface and does not enter ordinary
Discover early. Discover still requires a visible and enabled Agent, an online
target version, and a calling interface that allows the `RUNTIME` source. It
uses the target version's descriptor and `protocolVersion`.

Agent and version definitions do not own publication lifecycle. Creating,
publishing, taking offline, or deleting a definition changes only the
discovery projection. Register, Deregister, and publisher liveness manage the
publication itself.

### 7.2 Batch, Idempotency, And Atomicity

One registration batch is the complete desired publication for one publisher
and:

```text
(namespaceId, agentName, protocol)
```

`runtimeVersion` and `versionRange` are shared content of that batch, not an
additional publication identity or a server-managed binding group.

Rules:

- The binding validates all endpoints before atomically applying one complete
  batch for the current publisher and publication identity.
- Register replaces the previous batch; omitted endpoints are removed.
- Repeating identical content for the same publisher succeeds without a
  change.
- A changed Endpoint field, runtime Version, or range is expressed by replacing
  the complete batch.
- A duplicate natural key within one batch rejects the whole batch.
- The publisher client serializes changes to its local desired batch. Partial
  Deregister and Version replacement calculate the new batch locally and use
  Register for the replacement.
- When the desired batch becomes empty, the binding removes the current
  publisher's whole publication for the service.
- Deregistering a missing local contribution succeeds without a remote change.

A single-endpoint operation uses an `endpoints[]` of length one; RAD does not
define separate single-item commands.

The Nacos server path is a data-structure adapter over Naming. It transforms
the complete Endpoint batch into Naming Instances and invokes Naming batch
registration or whole-publication deregistration. It does not read the prior
publisher payload, incrementally merge it, add an Agent service lock, or scan
other publishers during a write. The admission path counts Runtime Endpoint
entries across only the current publisher Client's complete Agent publication
batches. It evaluates the pre-operation entry count together with the existing
and requested target-batch sizes. That soft-watermark check and the Naming
batch replacement are serialized for the same Client.

### 7.3 Naming Publication And Multiple Publishers

The publisher transport supplies an opaque identity and liveness semantics.
The identity does not enter discovery results.

Each publisher contributes at most one complete batch, with one singular
`runtimeVersion` and `versionRange` pair, to one
`(namespaceId, agentName, protocol)` Naming service. Different publishers may
contribute different pairs. The Nacos read path loads the complete internal
Service projection from Naming `ServiceStorage`, retains contributions whose
range matches the target Version, and aggregates query-time bindings by public
Endpoint natural key. Agent code does not directly walk the Naming client
index.

Contributions visible after AP convergence that project to the same public
Endpoint MUST agree on canonical URI, transport, priority, weight, and
metadata. Registration does not perform a cross-publisher write-time scan.
When a converged `ServiceStorage` projection contains conflicting payloads, the
affected read or Watch reports `CONFLICT` rather than selecting an
arbitrary value. Removing either conflicting publication restores the
projection after normal Naming convergence. Health is aggregated across
matching active contributions:

- at least one healthy contribution produces `healthy=true`;
- all contributions unhealthy produces `healthy=false`;
- removing one publisher removes only its contributions;
- a publisher-count change that leaves the public endpoint unchanged does not
  change `sourceRevision`.

## 8. Ordering And Capacity

Calling interfaces use version-definition order. Endpoint sets use declared
source order. Endpoints sort by ascending priority and then stable natural key.
Health does not change order, and weight does not participate in Registry
sorting.

One version has at most 16 calling interfaces. One declared endpoint set has at
most 64 endpoints. One runtime endpoint set and one endpoint batch each have at
most 1000 endpoints. The online-version list has no separate product limit and
follows the response-size rule in Section 3.4.

One publisher Client has a soft watermark of 100 Runtime Endpoint publication
entries by default. The watermark is configured by
`nacos.ai.rad.capacity.publication.max-publications-per-client` in the server
`application.properties`. Admission evaluates the Client's current entry count
before one complete registration batch. When that count is below the
watermark, the whole validated batch is accepted atomically even if its final
count crosses the watermark. Once the Client is at or above the watermark,
equal-size or shrinking replacement of an existing
`(namespaceId, agentName, protocol)` batch remains allowed, while a new batch
or growing replacement is rejected atomically with `RESOURCE_EXHAUSTED`; no
partial Naming publication is created. Removing Endpoint entries or
disconnecting the owner reduces the observed count.

## 9. Binding Profiles

A binding advertises the profiles and optional capabilities it supports:

| Profile or capability | Required operations |
|---|---|
| Consumer profile | `Search`, `Discover` |
| Publisher profile | `Register`, `Deregister` |
| Watch capability | `Watch` |

A conforming binding implements at least one profile. Watch is optional at the
RAD core level. The initial Nacos HTTP and gRPC bindings implement only the
Consumer and Publisher profiles and expose no server-side Watch or Push
operation. A later Java SDK may provide a local subscription convenience by
periodically executing Discover. Such polling does not advertise RAD Watch
support for either transport and adds no Watch wire message.

## 10. Error Semantics

A binding maps these abstract categories to its concrete response model:

| Category | Typical case |
|---|---|
| `INVALID_ARGUMENT` | Invalid field, mutually exclusive fields, duplicate natural key, invalid range, or runtime version outside its range |
| `NOT_FOUND` | Discover target is absent, invisible, disabled, or not online; watched target later disappears |
| `PERMISSION_DENIED` | Caller cannot operate in the target namespace |
| `RESOURCE_EXHAUSTED` | Endpoint or publication capacity is full, or a complete response exceeds a binding limit |
| `CONFLICT` | Converged runtime contributions contain incompatible payloads for one natural Endpoint key |
| `UNSUPPORTED_CAPABILITY` | Binding does not support the requested operation |
| `UNAVAILABLE` | Registry cannot currently read Naming state or apply a write |

An invisible resource and a nonexistent resource both appear as `NOT_FOUND`
to prevent visibility side channels. An empty filter result is not an error and
uses the shapes in Section 5.

## 11. Security

The Registry performs namespace and permission checks before every operation.
Search, Discover, and Watch also apply resource visibility. Register does not
skip authorization when the Agent definition is absent. Publisher identity is
not a credential.

Descriptors, URIs, and metadata are untrusted input and MUST NOT store
plaintext credentials. Discovery results MUST NOT expose connection ownership,
publisher identity, heartbeat data, or internal routing information. Endpoint
metadata MUST NOT use Nacos-reserved internal keys.

## 12. Schema And Evolution

The normative companion is the
[RAD 0.1.0 JSON Schema](../../schemas/ai/rad/0.1.0/rad-protocol.schema.json),
using JSON Schema Draft 2020-12. Ordinary objects use strict property sets;
only metadata maps and `nativeDescriptor` are open content. Schema defaults are
annotations; implementations materialize effective values.

Adding a field, changing `required`, widening a union, or changing an enum
requires a new RAD protocol version. Domain validation additionally verifies
SemVer, version/label exclusivity, reserved labels, endpoint natural keys,
source/health conditions, range boundaries, ordering, runtime-version
containment, batch atomicity, and capacity. JSON Schema validates only the
coarse syntax of a version-range string and does not replace domain validation.

## 13. Examples

### 13.1 Discover Request

```json
{
  "namespaceId": "public",
  "reference": {"agentName": "Order Agent", "label": "latest"},
  "filter": {
    "protocols": ["a2a"],
    "transports": ["JSONRPC"],
    "endpointSources": ["RUNTIME", "DECLARED"],
    "metadataSelector": {"zone": "cn-hangzhou-h"}
  }
}
```

### 13.2 Discover Result

```json
{
  "namespaceId": "public",
  "agentName": "Order Agent",
  "version": "1.0.6",
  "contentDigest": "sha256:1111111111111111111111111111111111111111111111111111111111111111",
  "callInterfaces": [{
    "protocol": "a2a",
    "protocolVersion": "1.0",
    "descriptorMediaType": "application/json",
    "nativeDescriptor": {"name": "Order Agent", "version": "1.0.6"},
    "endpointSets": [{
      "source": "RUNTIME",
      "sourceRevision": "murmur3-x64-128-v1:0123456789abcdef0123456789abcdef",
      "endpoints": [{
        "uri": "https://10.0.0.8:8443/a2a",
        "transport": "JSONRPC",
        "priority": 0,
        "weight": 1,
        "metadata": {"zone": "cn-hangzhou-h"},
        "healthy": true,
        "bindings": [{
          "runtimeVersion": "1.0.6",
          "versionRange": "[1.0.0,2.0.0)"
        }]
      }]
    }, {
      "source": "DECLARED",
      "sourceRevision": "sha256:1111111111111111111111111111111111111111111111111111111111111111",
      "endpoints": []
    }]
  }]
}
```

### 13.3 Register Request

```json
{
  "namespaceId": "public",
  "agentName": "Order Agent",
  "runtimeVersion": "1.0.6",
  "versionRange": "[1.0.0,2.0.0)",
  "protocol": "a2a",
  "endpoints": [{
    "uri": "https://10.0.0.8:8443/a2a",
    "transport": "JSONRPC",
    "metadata": {"zone": "cn-hangzhou-h"}
  }]
}
```

### 13.4 Deregister Request

```json
{
  "namespaceId": "public",
  "agentName": "Order Agent",
  "protocol": "a2a",
  "endpoints": [{
    "uri": "https://10.0.0.8:8443/a2a",
    "transport": "JSONRPC"
  }]
}
```

This is the application-facing SDK command. The SDK removes the natural key
from its cached batch and sends the complete remaining Register request. If
the remaining batch is empty, the Nacos binding sends a whole-publication
deregistration for `namespaceId`, `agentName`, and `protocol`.
