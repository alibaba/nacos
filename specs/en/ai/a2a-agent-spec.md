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
| Status | Experimental target compatibility contract |
| Activation | Canonical Agent write-path cutover |

This document defines A2A as a protocol binding of the canonical Nacos Agent
resource and specifies the compatibility facade for historical AgentCard APIs.
The canonical model is defined by the
[Agent Management Spec](agent-management-spec.md); remote discovery follows the
[RAD Protocol Spec](rad-protocol-spec.md).

## 1. Activation, Current Baseline, And Identity

Before feature activation, the current Nacos runtime may continue to persist
`type=a2a` resources and use the legacy Config and Naming layouts. That
implementation remains conforming to the current A2A baseline; this target
spec does not claim that it has already migrated.

Sections 2 through 7 become normative for new requests only after the
canonical Agent write path is activated. Activation and mixed-version rollout
must be explicit: before cutover, the legacy model remains the fact source;
after cutover, new writes use the canonical Agent model and the legacy surface
is the compatibility facade defined here.

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
- releasing an already-online exact version through the Client SDK is a
  successful no-op;
- an existing exact version with different canonical content is a conflict;
  0.1.0 does not provide same-version force overwrite;
- deleting a missing Agent or version is a successful no-op only where the
  historical API already promises that behavior.

Direct-online, conflict rejection, deletion, and latest changes must emit audit
records without logging the complete descriptor or sensitive endpoint metadata.

## 4. Legacy Runtime Endpoint Writes

Legacy single and batch endpoint operations remain on the existing
Version-specific Naming layout during the first compatibility phase:

```text
publisher + namespaceId + agentName + exactVersion + protocol=a2a

group=agent-endpoints
serviceName=<legacyEncodedAgentName>::<exactVersion>
```

Single register delegates to the existing Naming single-instance operation and
replaces the publication with one endpoint. Batch register delegates to Naming
batch registration and replaces the complete submitted batch. Legacy
deregister removes the complete publication for the exact-Version service.

The compatibility handler does not redirect these writes to the new
Version-neutral RAD Runtime Service, does not write `runtimeVersion` or
`versionRange` metadata, and does not enter the new RAD Runtime write path. An
old A2A client cannot submit the complete cross-Version publisher batch
required by that Service; redirecting it would allow one Version registration
to overwrite another. Migration, dual read or write, cutover, rollback, and
old-service cleanup require a separate rolling-upgrade contract.

Endpoint publication may precede Agent or Version creation. It never creates an
Agent definition implicitly.

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

Runtime projection excludes `enabled=false` endpoints and retains
`healthy=false` endpoints because the legacy DTO has no health field. The
projection order is stable: priority first, then the endpoint natural key. New
RAD-only fields such as source revision, health, priority, weight, and general
metadata are not added to legacy DTOs.

Legacy list and version-list APIs read Agent metadata plus online A2A versions.
Legacy subscription events pass through the same projection as GET. A legacy
subscription may remain registered when the initial target is absent; this is a
compatibility behavior and is not the RAD Watch contract.

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

Historical data migration, mixed-version dual read/write, source switching,
rollback, and cleanup are rolling-upgrade concerns and are not defined by this
API compatibility spec.

## 7. Evolution

Changes in upstream AgentCard fields or A2A protocol versions are handled by the
A2A adapter and versioned Agent call interface. They must not redefine the
canonical Agent identity or the protocol-neutral RAD result.
