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
| `declaredEndpoints` | Derived from root URL and supported/additional interfaces. |
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
`versionRange` metadata. Legacy `protocolVersion` and `tenant` remain reserved
metadata solely for A2A reverse projection; they are excluded from public RAD
Endpoint metadata and Runtime revision. Legacy Endpoint URI, transport, health,
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
