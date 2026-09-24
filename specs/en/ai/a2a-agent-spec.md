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

# A2A Agent Binding And Compatibility Spec

| Item | Value |
| --- | --- |
| Status | Experimental binding and upgrade compatibility contract |
| Activation | `nacos.ai.a2a.compatibility.mode`, default `CANONICAL` |

This document defines A2A as a protocol binding of the canonical Nacos Agent
resource and specifies the compatibility facade for historical AgentCard APIs.
The canonical model is defined by the
[Agent Management Spec](agent-management-spec.md); remote discovery follows the
[RAD Protocol Spec](rad-protocol-spec.md).

## 1. Activation, Current Baseline, And Identity

The legacy A2A surfaces select one complete definition implementation through
`nacos.ai.a2a.compatibility.mode`:

| Mode | Compatibility implementation |
| --- | --- |
| `CANONICAL` | Canonical Agent metadata, Version storage, and RAD Runtime Endpoints. This remains the default static mode and does not scan historical data. |
| `LEGACY` | Historical AgentCard Config groups and exact-Version Naming Endpoints. The legacy implementation remains unchanged. |
| `AUTO` | Run the one-time historical upgrade state machine. Historical Config definitions remain authoritative through `SYNCING` and `QUIESCING`; only a permanent, zero-difference `CANONICAL` marker switches the complete definition facade. |

Mode tokens are case-insensitive. Each request is routed wholly to one branch;
there is no per-operation mixture, fallback, merged definition read, or
definition dual write. `AUTO` is specified by the
[Historical A2A Upgrade Migration Spec](a2a-upgrade-migration-spec.md). It
reconciles historical definitions in the background, uses an explicit member
ability and a short definition-write fence, and never switches by member
version alone. Runtime dual materialization during that migration is a
connection-state compatibility projection, not a second definition authority.

A persisted terminal migration marker has priority over local mode
configuration. Once a capable member observes it, that process permanently
routes A2A definition operations to `CANONICAL`; it must not resume legacy-only
writes if the marker is deleted or configuration changes. A non-terminal
marker does not override an explicitly selected static mode.

Sections 2 through 7 are normative for requests routed to `CANONICAL`. Requests
routed to `LEGACY` retain the complete historical Config definition and
Version-specific Naming Endpoint behavior. During `AUTO` synchronization,
definition reads and writes retain that complete historical behavior; after
the terminal marker, the complete facade uses the same branch as `CANONICAL`.
`QUIESCING` is the sole exception: definition mutations are temporarily
rejected with the retryable `AGENT_MIGRATION_IN_PROGRESS` detail error while
reads and Runtime operations continue.

A2A is not a top-level AI resource type. The canonical identity is:

```text
namespaceId -> agent -> agentName -> version -> protocol=a2a
```

The historical `namespaceId -> a2a -> agentName` identity is compatibility
only. All legacy requests are adapted to `type=agent`; no new `a2a` metadata or
version store may be created after the canonical write path is enabled.

## 2. A2A Call Interface

An A2A binding is one `AgentCallInterface` with:

| Agent field | A2A mapping |
| --- | --- |
| `protocol` | Canonical token `a2a`. |
| `protocolVersion` | Normalized A2A protocol version used for fast filtering. |
| `descriptorMediaType` | AgentCard JSON media type. |
| `nativeDescriptor` | Complete normalized AgentCard, without losing supported upstream fields. |
| `endpointSets[source=DECLARED].endpoints` | Derived from root URL and supported/additional interfaces. |
| `endpointSourceOrder` | Derived from the compatibility registration type. |

The current descriptor baseline supports A2A 1.0 fields and the existing 0.x
compatibility fields. Adapter normalization must not replace the stored native
descriptor with a synthetic generic Agent object.

An A2A call interface in the exact common-latest Version declares the ARD
representation `application/a2a-agent-card+json` only when it passes complete
AgentCard validation for that baseline. The artifact returns the stored native
descriptor directly and must not disguise a multi-protocol Nacos Agent wrapper
as an AgentCard. A2A support only on an older online Version affects RAD
`protocolsAny=a2a`; it does not create a current A2A ARD representation when
common latest has no valid AgentCard.

`registrationType=URL` maps to `[DECLARED,RUNTIME]` and
`registrationType=SERVICE` maps to `[RUNTIME,DECLARED]`. Registration type is a
legacy projection field, not part of Agent identity or the new APIs.

## 3. Legacy Definition Writes

Legacy AgentCard release and Admin update requests validate the same AgentName
and version syntax as canonical Agent APIs. A successful write creates or uses
the Agent metadata row, stores one A2A call interface, and takes the target
version directly online without introducing a separate legacy draft pipeline.

Rules:

- the first online version always becomes `latest`;
- for a new later version, `setAsLatest=true` moves `latest` and `false`
  preserves the current valid pointer;
- a standard Agent publish or online operation always moves `latest`;
- deleting or taking the current latest offline selects the greatest remaining
  online Agent version, or removes `latest` when none remains;
- releasing an already-online exact version that already contains an A2A call
  interface through the Client SDK is a successful no-op: content is neither
  compared nor replaced, and `latest` is not moved;
- different canonical content is a conflict for Admin updates of an existing
  exact version and for Client releases that hit an exact version without an
  A2A call interface; 0.1.0 does not provide same-version force overwrite;
- deleting a missing Agent or version is a successful no-op only where the
  historical API already promises that behavior.

Direct-online, conflict rejection, deletion, and latest changes must emit audit
records without logging the complete descriptor or sensitive endpoint metadata.

## 4. Legacy Runtime Endpoint Writes

The `CANONICAL` branch adapts legacy single, batch, and deregistration requests
to the canonical RAD Runtime Naming layout:

```text
group=agent-endpoints
serviceName=rad-<encodedAgentId>-a2a
runtimeVersion=<exactVersion>
versionRange=[<exactVersion>]
```

Legacy SDK redo and replacement identity is
`(connection, namespaceId, agentName, exactVersion)`, while the canonical
Runtime Service stores one complete batch per Naming publisher. The adapter
therefore creates a deterministic internal child publisher for each legacy
exact Version and binds it to the original AI gRPC connection. Single register
replaces that child publication with one Endpoint; batch register replaces the
same child publication with the submitted complete batch; deregister removes
the complete exact-Version child publication. Different Version child
publishers write the same canonical Service without overwriting each other.
Disconnecting the original connection releases all of its children and keeps
using Naming ClientData Distro, indexes, events, and cleanup. The adapter never
reads and merges an old publication.

Every converted Naming Instance uses canonical singular `runtimeVersion` and
`versionRange` metadata. A2A `protocolVersion` and `tenant` are exposed as public Endpoint metadata
`__nacos.agent.endpoint.protocolVersion__` and `__nacos.agent.endpoint.tenant__`, including in Runtime revision input.
These are the existing Nacos-owned Naming keys, reused without alias conversion.
Only these two reserved keys are accepted as compatibility metadata in external
Endpoint input; other internal control keys remain forbidden. Legacy Endpoint URI, transport, health,
and weight pass through the canonical Runtime mapping and validation.

The `LEGACY` branch preserves the existing handler and
`<legacyEncodedAgentName>::<exactVersion>` Naming Service implementation
unchanged. The explicit `CANONICAL` branch writes only the canonical Service.

`AUTO` adds a temporary migration router above those two unchanged physical
implementations. In `SYNCING` and `QUIESCING`, it writes the historical Service
as primary and the canonical Service as a required mirror. After terminal
cutover, canonical RAD is primary and the frozen migration policy may retain an
optional historical exact-Version Naming shadow. One logical publication is
validated and counted once; both physical child publishers remain bound to the
original connection and are cleaned idempotently. Mirror or shadow failure
does not roll back a successful primary write, but enters bounded
connection-local retry. The exact ordering, cutover gate, supported shadow
scope, and rollback boundary follow the
[Historical A2A Upgrade Migration Spec](a2a-upgrade-migration-spec.md).

Endpoint publication may precede Agent or Version creation. It never creates an
Agent definition implicitly.

The legacy Java SDK stores Endpoint redo independently for each
`(agentName, exactVersion)` and keeps a defensive snapshot of the submitted
payload. Reconnect caching must not lose one Version's publication intent
because another Version shares the Agent name. Internal child publishers are a
server implementation detail and never enter public payloads, redo keys,
authorization resources, or management queries.

## 5. Legacy Query Projection

The compatibility query first selects an online version containing a valid
`protocol=a2a` call interface. An explicit version is case-sensitive; otherwise
the Agent `latest` pointer is used. Client runtime reads also require the Agent
to be enabled and visible.

Projection rules:

| Query mode | Result |
| --- | --- |
| `URL` | Return the stored native AgentCard and its declared interfaces. |
| `SERVICE` with matching Runtime Endpoints | Project the deterministic Runtime Endpoint set into AgentCard interfaces and root URL. |
| `SERVICE` with no matching Runtime Endpoint | Fall back to the stored declared AgentCard. |

`CANONICAL` queries read `rad-<encodedAgentId>-a2a` and filter bindings by the
target exact Version. `LEGACY` queries continue reading the historical
Version-specific Service. Runtime projection excludes `enabled=false` endpoints and retains
`healthy=false` endpoints because the legacy DTO has no health field. The
projection order is stable: priority first, then the endpoint natural key. New
RAD-only fields such as source revision, health, priority, weight, and general
metadata are not added to legacy DTOs.

For wire compatibility, the complete projected Runtime Endpoint set is exposed
through both `supportedInterfaces` and the historical `additionalInterfaces`
field. The root URL and preferred transport select one member of that same set;
the selected member is not removed from `additionalInterfaces`.

Legacy list and version-list APIs read Agent metadata plus online A2A versions.
Legacy subscription events pass through the same projection as GET. A legacy
subscription may remain registered when the initial target is absent; this is a
compatibility behavior and is not the RAD Watch contract.
Exact-Version and latest subscriptions use distinct identities. A Version's
current latest flag cannot choose the sole event target. Moving latest to an
already cached exact Version still notifies latest subscribers. Resubscription
after cancellation restarts polling, and SDK shutdown stops all legacy
AgentCard polling tasks.

## 6. Compatibility Surfaces

| Surface | State and window |
| --- | --- |
| Java `A2aService` and legacy A2A gRPC payloads | Compatibility-only; no removal version is set. |
| Admin `/v3/admin/ai/a2a` and `A2aMaintainerService` | Supported through the 4.0.x compatibility window. |
| Console `/v3/console/ai/a2a` | Supported through the 3.4.x compatibility window. |

The legacy Console Agent pages are an A2A-only compatibility UI and use
`/v3/console/ai/a2a`. Protocol-neutral Agent lifecycle, multi-protocol editing,
and generic Agent metadata management belong to the next Console and its
canonical Agent APIs. A generic Agent without an online A2A binding is not
required to appear in the legacy Console list.

Legacy paths, payload type names, DTOs, ability keys, authorization identity,
and response wrappers remain stable during their windows. New Agent/RAD APIs
must not expose `registrationType`, `setAsLatest`, or AgentCard-specific list
wrappers.

Historical 3.0-3.2 data reconciliation, mixed-member operation, safe cutover,
optional historical Naming shadow, rollback boundary, and deferred cleanup are
defined by the
[Historical A2A Upgrade Migration Spec](a2a-upgrade-migration-spec.md).
Migration-only implementation code and its configuration are targeted for
removal in Nacos 4.0. Canonical Agent/RAD facts and a still-supported public A2A
facade do not depend on that temporary code after completion.

## 7. Evolution

Changes in upstream AgentCard fields or A2A protocol versions are handled by the
A2A adapter and versioned Agent call interface. They must not redefine the
canonical Agent identity or the protocol-neutral RAD result. The AgentCard
media type and pinned upstream schema baseline used by ARD are versioned with
the [AI Registry Adaptor Spec](ai-registry-adaptor-spec.md); a change updates
the adaptor fixtures, validator, specification, and conformance tests together.

### Java SDK RAD Card adaptation

When the SDK uses RAD, a missing or blank legacy Version explicitly selects
`label=latest` and `protocols=[a2a]`; it does not use the unqualified RAD runtime
pool. Discover retains both Endpoint Sets in definition preference order,
including empty Sets. That order determines the returned registrationType;
an explicit query type only selects the current projection. URL returns the
complete normalized native Card. SERVICE projects enabled Runtime Endpoints,
retains unhealthy ones, and falls back to the stored Card only for an empty
runtime set. Priority and natural key determine stable order; the stored
preferred transport selects the root interface when present. Both interface
arrays retain every projected address. A missing/invalid A2A descriptor or an
identity mismatch fails as not found. No supplementary Admin or legacy-wire queries are made.

RAD latest queries return latestVersion=true; exact-Version queries return
null even if the selected Version is latest, without an additional lookup.
Legacy wire responses keep their existing latest flag semantics.
Client release converts the complete Card into one A2A CallInterface and passes
setAsLatest as autoSubmit to Client publish, with no pre-read, retry or forced
publication. Client publication state rules remain authoritative.

Public `__nacos.agent.endpoint.protocolVersion__` must be nonempty printable ASCII of at most 64
characters; `__nacos.agent.endpoint.tenant__` is a string of at most 256 characters and may be empty.
Only a missing protocol version falls back to the target CallInterface;
tenant is never synthesized. Native RAD callers may provide these keys.

Historical empty protocol versions mean absent; empty tenant values are preserved.

The historical LEGACY wire projector retains its original empty-string fields; the absent-value normalization above applies to canonical RAD conversion.

### Java SDK RAD Endpoint intent adaptation

This section applies only when the SDK's A2A facade selects RAD. Its legacy-wire
mode and old SDKs retain the exact-Version isolation described above.
Each single or collection register replaces the complete intent of one exact
Version. Empty, mixed-Version, null-member and duplicate-key inputs are rejected
before changing cached or remote state. Caller objects are defensively copied.
For one SDK publication `(namespaceId, agentName, a2a)`, matching natural keys
with identical remaining payload merge into one Endpoint and one binding.
Different transports, hosts or effective ports remain separate Endpoints;
conflicting path, query or metadata cannot be silently selected from one Version.

The binding's runtimeVersion is the greatest active reference by RAD's exact
case-sensitive SemVer order. Its closed versionRange expands to include all
observed registrations while that Endpoint remains referenced, including
intermediate Versions. Removing a Version or replacing its address list removes
references but never shrinks that retained range. Removing the last reference
deletes the Endpoint and forgets its range. Re-registering later starts a fresh
exact range. Legacy deregistration removes the entire target Version's intent,
even when the overload carries only one address. Other Versions remain intact;
only an empty publication sends whole-publication Deregister.

Native RAD and adapted A2A Runtime writes to the same live SDK publication are
mutually exclusive. A conflicting source fails before remote I/O; definition
publication and reads are unaffected. Confirmed final cleanup releases the
source. A rejected initial write does not acquire it; unknown results retain
the intended source and sticky transport until recovery or confirmed cleanup.
Ordinary definitive failures restore the prior references and ranges. Remote
publication-capacity eviction retains the existing discard contract. Native
partial removal remains a natural-key operation and does not use legacy
whole-Version removal or retained-range behavior.

All writes and gRPC reconnect replays pass through the same publication monitor.
A stale redo snapshot cannot overwrite a newer registration or removal. Rejected
or completed replay reconciles the existing redo record without another remote
write; HTTP recovery keeps the existing shared AI liveness coordinator. No
physical child-publisher identity or second heartbeat/redo framework is added.

### Java SDK RAD Card Watch Adaptation

In RAD mode, legacy Card subscriptions use the same canonical Watch manager, transport,
capacity accounting and listener dispatcher as native discovery. Blank Version selects
explicit `latest`; exact Version retains the degraded `latestVersion=null` projection.
The adapter returns the current Card and queues the legacy initial callback through the
serialized listener dispatcher, including the caller's executor. Each listener compares
the complete projected Card structurally, independently of JSON member order. Changes
only to RAD fields that do not affect the Card do not trigger a legacy callback.

A missing Agent or missing valid A2A descriptor returns no invented Card and retains the
existing pending observation. Unavailability clears Card comparison state without sending
stale content; recovery can notify even when the recovered Card equals the old one. The
legacy listener has no unavailable-event shape; native terminal authorization/capacity
errors still end observation and require explicit resubscription. No authentication error
is converted into polling fallback. Where the selected binding lacks Watch, existing RAD
Discover polling is used. This never changes to legacy A2A polling in RAD mode.

Duplicate listener identity shares one bridge. Unsubscribe removes it before wire cleanup;
late callbacks cannot resurrect it. Shutdown removes only the adapter's listeners before
its owning service closes the shared manager. All remote activation and user callbacks run
outside the adapter monitor. Initial snapshot and refresh notifications share monotonic
ordering, so a late-enqueued older event cannot overwrite a newer delivered state.

C09 prepares this component; C10 enables all legacy facade operations together.

### Client instance routing activation

The 3.3 Java SDK routes every inherited A2A overload through the same Agent facade.
The first reliable selection is fixed for that AiService lifetime. Initial preferred
gRPC negotiation can establish this selection; HTTP-only initialization does not start
gRPC just to choose A2A. An undecided call can use authenticated HTTP capabilities,
and only missing HTTP evidence can require legacy gRPC negotiation. Authentication
and transport failures do not select legacy mode. Reconnect invalidates HTTP evidence
without changing an existing A2A selection. Native Agent calls use current evidence.

A positive HTTP RAD declaration remains independent of gRPC. With missing HTTP
evidence, a successful old gRPC negotiation may reject a native operation only for
a sole, matching configured main address; this is not cached as an HTTP capability
or promoted to all cluster members. Different targets or multiple configured targets
remain unknown. Explicit gRPC native operations report an unavailable connection
separately from unsupported RAD. Unsubscribe uses local listener state without a
capability request. Full publication cleanup continues through its existing owner.

All legacy query/publication/Endpoint/Watch operations switch together. The existing
Endpoint manager owns both API sources and rejects mixing them for one live
publication. Card queries and events share the same conversion; no migration or
business error silently re-enters old A2A after RAD selection.

Only positive initial RAD negotiation fixes the mode during construction. A negative
or missing gRPC RAD bit waits until the first A2A operation checks independent HTTP
evidence. Once that operation selects legacy, later reconnects do not upgrade it.
An instance with no A2A operation yet has no legacy intent to migrate.

For an undecided instance, positive live gRPC RAD evidence also selects the RAD API
family when HTTP evidence is missing or negative. It does not establish HTTP support
or reachability: each RAD-backed legacy business operation still checks its configured
transport. A negative HTTP declaration therefore produces unsupported on HTTP rather
than silently sending legacy gRPC. Cancellation and full cleanup retain their local
and existing-owner paths.
