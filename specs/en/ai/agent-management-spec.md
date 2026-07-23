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

# Agent Management Spec

This document defines the protocol-neutral Agent management model for Nacos AI
Registry. It refines the [AI Resource Model Spec](ai-resource-model-spec.md),
the [AI Resource Lifecycle Spec](ai-resource-lifecycle-spec.md), and the
[Naming specs](../naming/README.md).

This is the normative target contract for the Agent model migration. A server
or SDK must not advertise this contract until the corresponding behavior is
implemented. Before that capability is advertised, the current
[A2A Agent Spec](a2a-agent-spec.md) remains the runtime contract.

## 1. Scope And Boundaries

The model separates three facts with different lifecycles:

```text
Agent
  1 --- N AgentVersion
             1 --- N AgentCallInterface
                         1 --- N DECLARED Endpoint

Runtime publisher --- N RUNTIME Endpoint
RUNTIME Endpoint --- versionRange match ---> AgentVersion + AgentCallInterface
```

| Fact | Responsibility | Fact source |
| --- | --- | --- |
| `Agent` | Stable identity, catalog metadata, ownership, visibility, and version governance. | `ai_resource` |
| `AgentVersion` | One versioned calling definition, mutable only while it is a draft. | `ai_resource_version` and AI Storage |
| `AgentCallInterface` | One protocol binding and its declared addresses. | `AgentVersionContent` |
| `RuntimeEndpoint` | A live publisher's callable address and compatible version range. | Naming runtime state |

A2A is the first protocol adapter. The common Agent model must not contain a
union of A2A-specific capability, skill, security, task, or message fields.
Those fields remain in the protocol-native descriptor.

This spec does not define MCP resources or actual remote invocation. Nacos
returns calling metadata; it does not proxy Agent messages, tasks, sessions,
streams, retries, or credentials. Search, Discover, Watch, and runtime
publication wire objects are defined by the
[RAD Protocol Spec](rad-protocol-spec.md). Runtime publication ownership,
physical storage, Naming mapping, codecs, and revision algorithms are defined
by the [Agent Storage Spec](agent-storage-spec.md).

## 2. Identity And Validation

### 2.1 Agent Identity

The canonical Agent identity is:

```text
namespaceId + resourceType=agent + agentName
```

`agentName` is the public `resourceName` and has these rules:

- it contains 1 to 64 printable ASCII characters in the inclusive range
  `U+0020..U+007E`;
- it contains at least one non-space character;
- it is stored exactly as submitted and is case-sensitive;
- the server must not trim, lowercase, slug, or otherwise rewrite it; and
- it is immutable after creation.

`displayName` is an optional Unicode presentation field. A presentation layer
must use `agentName` when `displayName` is absent or blank. `displayName` never
participates in identity, authorization, storage keys, or endpoint matching.

Exact lookup compares the original `agentName`. A name filter performs literal
substring matching; persistence implementations must escape wildcard
characters such as `%` and `_` instead of interpreting them as patterns.

### 2.2 Version Identity

An Agent Version identity is:

```text
namespaceId + resourceType=agent + agentName + version
```

`version` uses `MAJOR.MINOR.PATCH[-PRERELEASE]` and is at most 64 characters:

- `MAJOR`, `MINOR`, and `PATCH` are `0` or a positive integer without a
  leading zero;
- `PRERELEASE` contains one or more dot-separated `[0-9A-Za-z-]+`
  identifiers, and a numeric-only identifier has no leading zero;
- build metadata introduced by `+` is not accepted;
- the original value is stored and compared case-sensitively; and
- all Agent write paths, including compatibility facades, apply these rules.

Version precedence compares major, minor, and patch numerically. A release is
higher than its prerelease. Prerelease identifiers are compared from left to
right: numeric identifiers use numeric order and are lower than non-numeric
identifiers; non-numeric identifiers use case-sensitive ASCII order; a longer
otherwise-equal sequence is higher.

Version labels match `[A-Za-z0-9][A-Za-z0-9._-]{0,63}` and are
case-sensitive. `latest` is reserved for the server-managed pointer; it cannot
be created, replaced, or removed through a custom-label write.

### 2.3 Endpoint Identity

DECLARED and RUNTIME sources use the same `Endpoint` value object. Within one
Agent protocol group, its natural identity is:

```text
(namespaceId, agentName, protocol,
 normalizedHost(uri), effectivePort(uri), normalizedTransport)
```

There is no public `endpointId`. URI path, query, metadata, priority, and
weight do not participate in identity and may be updated by the same publisher.

## 3. Agent Resource

The Agent resource contains the following fields:

| Field | Required | Meaning |
| --- | :---: | --- |
| `namespaceId` | Yes | Nacos namespace isolation boundary; 1 to 128 `[A-Za-z0-9_-]` characters. |
| `agentName` | Yes | Stable public identity. |
| `displayName` | No | Unicode presentation name. |
| `description` | No | Catalog description. |
| `iconUrl` | No | Catalog icon URI. |
| `provider` | No | Provider `name` and `url`; this is not the management owner. |
| `tags[]` | No | Public catalog and exact-match search tags. |
| `extensions` | No | Namespaced `Map<String, JsonValue>` for public Agent-level extensions. |
| `status` | Yes | `enable` or `disable`. |
| `owner` | Yes | Management owner. |
| `scope` | Yes | Shared visibility scope; `PUBLIC` or `PRIVATE` in this version. |
| `versionInfo` | Read-only | Shared editing, reviewing, online-count, and label summary. |
| `versionCatalog` | Read-only | Compact catalog of online versions and protocols. |
| `metaVersion` | Read-only | Metadata CAS version. |
| `createTime`, `updateTime` | Read-only | Audit timestamps. |

The following invariants apply:

- Agent metadata does not embed protocol descriptors, endpoints, health state,
  or complete version history.
- `tags` is the only public generic classification list in this version.
- `extensions` does not affect identity, authorization, version selection,
  endpoint selection, or default search. It must not contain credentials or
  server-internal state.
- Updating catalog or extension fields advances `metaVersion` but does not
  create an Agent Version.
- A protocol adapter may initialize missing catalog fields from a native
  descriptor only when the Agent is first created. Later descriptor updates
  do not overwrite independently governed Agent metadata.

`versionCatalog` contains `latestVersion` and `onlineVersions[]`. Each online
entry contains only `version`, `labels[]`, and `protocols[]`. It is derived by
the server and is not a client-writable fact.

## 4. Agent Version And Lifecycle

### 4.1 Version Metadata And Content

An Agent Version exposes this metadata:

| Field | Required | Meaning |
| --- | :---: | --- |
| `namespaceId`, `agentName`, `version` | Yes | Exact version identity. |
| `status` | Yes | Shared AI Resource version status. |
| `callInterfaces[]` | Yes | Ordered protocol bindings; at least one. |
| `author` | No | Version author. |
| `changeDescription` | No | Version change description. |
| `contentDigest` | Read-only | SHA-256 digest of the persisted Version content bytes. |
| `createTime`, `updateTime` | Read-only | Audit timestamps. |

The complete storage payload is one `AgentVersionContent` object:

```text
AgentVersionContent
  kind = AgentVersionContent
  schemaVersion = 1
  callInterfaces[]
```

The server serializes the validated object once as UTF-8 JSON. The same bytes
are persisted, counted as `size`, and hashed as
`sha256:<lowercase hex>`. Reads validate the digest against the bytes returned
by AI Storage without reserializing the object. Storage normalization and
validation rules are defined by the [Agent Storage Spec](agent-storage-spec.md).

### 4.2 Lifecycle Rules

Agent Versions use the shared lifecycle:

| Status | Content mutable | Available to ordinary RAD discovery |
| --- | :---: | :---: |
| `draft` | Yes | No |
| `reviewing` | No | No |
| `reviewed` | No | No |
| `online` | No | Yes |
| `offline` | No | No |

`ai_resource_version.status` is the lifecycle fact source.
`publishPipelineInfo` records review execution and outcome only.

One Agent may have at most one editing version and one reviewing version.
Content becomes frozen when a draft enters reviewing. Reviewed, online, and
offline content must not be updated in place. This version of the contract does
not provide a forced same-version content replacement operation.

`latest` is a server-managed label and must always point to an online version.
The following Agent-specific rules refine the common AI lifecycle rule:

- every successful standard publish or online transition makes its target
  Version `latest`;
- legacy A2A publication with `setAsLatest=false` is the only exception in the
  initial release and preserves the current valid `latest`;
- publishing the first online Version establishes `latest` even through that
  compatibility path;
- deleting or offlining the current `latest` selects the SemVer-greatest
  remaining online Version;
- removing the last online Version removes `latest`; and
- deleting or offlining an online Version other than the current `latest` does
  not trigger recalculation.

Whenever online status or labels change, the server must rebuild
`versionCatalog` as one logical update.
When at least one online version exists, exactly one valid `latestVersion` must
exist and must occur in `onlineVersions`.

Agent metadata, Agent Version definitions, and Runtime Endpoints do not own one
another's lifecycle. Deleting or disabling an Agent definition changes its
read projection but does not delete still-live runtime publisher state.

## 5. Call Interfaces And Declared Endpoints

### 5.1 AgentCallInterface

Each Agent Version contains an ordered, non-empty `callInterfaces[]` list.
Each item contains:

| Field | Required | Meaning |
| --- | :---: | --- |
| `protocol` | Yes | Canonical protocol token, unique within the Version. |
| `protocolVersion` | No | Fast protocol negotiation value; not interface identity. |
| `descriptorMediaType` | Yes | Media type of `nativeDescriptor`. |
| `nativeDescriptor` | Yes | Complete protocol-native descriptor. |
| `endpointSourceOrder[]` | Yes | Non-empty ordered set of `RUNTIME` and `DECLARED`. |
| `declaredEndpoints[]` | No | Static endpoint projection derived by the adapter. |

The canonical protocol token matches
`[A-Za-z0-9][A-Za-z0-9-]{0,31}` and is compared case-sensitively. The same
token is used by CallInterface uniqueness, endpoint publication, RAD filters,
and Naming service composition.

The order of `callInterfaces[]` is the default protocol preference. Reordering
the list changes `contentDigest`. The first interface with a usable endpoint is
the SDK's default selection candidate.

`endpointSourceOrder` contains no duplicate and has one of the following
meanings:

- `[RUNTIME, DECLARED]` prefers live addresses and keeps declared addresses as
  fallback;
- `[DECLARED, RUNTIME]` prefers declared addresses;
- `[RUNTIME]` or `[DECLARED]` allows only that source in ordinary discovery.

Source order belongs to one CallInterface, not to the whole Version. It does
not prevent runtime publication. Management queries may inspect Runtime
Endpoints even when a CallInterface omits `RUNTIME`.

### 5.2 Endpoint Value Object

| Field | Required | Meaning |
| --- | :---: | --- |
| `uri` | Yes | Complete callable URI. |
| `transport` | Yes | Canonical transport token. |
| `priority` | No | Lower values have higher priority. |
| `weight` | No | Load weight among endpoints with equal priority. |
| `metadata` | No | Flat zone, environment, data-center, and extension labels. |

The URI has a non-empty scheme and host. Its port is explicit or can be derived
as a valid `1..65535` default for the scheme. DNS hosts use a case-insensitive
canonical form; IP literals use stable IPv4 or IPv6 representation.

An adapter derives and validates `declaredEndpoints` from
`nativeDescriptor`. Clients must not edit the two representations
independently. When the same natural endpoint occurs more than once, the first
descriptor occurrence determines list position while the native descriptor
remains byte-for-byte represented by the canonical content.

## 6. Management Read Models

Management APIs use bounded views rather than one unbounded aggregate:

| View | Contains | Excludes |
| --- | --- | --- |
| `AgentSummary` | Presentation, governance, and version-catalog summary. | Descriptor, Endpoint, full history, extensions. |
| `AgentOverview` | Full Agent and a bounded page of Version summaries. | Version payload and Runtime Endpoint. |
| `AgentVersionSummary` | Version, status, author, change description, digest, and timestamps. | CallInterface payload. |
| `AgentVersionDetail` | Exact Version metadata and complete CallInterfaces. | Runtime Endpoint. |
| `RuntimeEndpointSnapshot` | Raw runtime snapshot for one Agent and protocol, optionally filtered by Version. | Descriptor, publisher identity, final discoverability decision. |

`RuntimeEndpointSnapshot` is not paged. It contains:

```text
namespaceId / agentName / protocol / version?
items[] {
  endpoint, bindings[] { runtimeVersion, versionRange },
  state, enabled, healthy, lastUpdatedTime
}
state = AVAILABLE | DISABLED | UNHEALTHY
```

State evaluation is ordered: `enabled=false` is `DISABLED`; otherwise
`healthy=false` is `UNHEALTHY`; all other items are `AVAILABLE`.
`lastUpdatedTime` changes only when public Endpoint content, enabled state, or
aggregate health changes. A heartbeat alone does not change it.

`protocol` is required. Without `version`, the snapshot contains one effective
item per natural Endpoint key for that protocol and all of its Version
bindings. With `version`, it retains only bindings matching the supplied
Version and omits an item when no binding remains. Missing instances produce
an empty `items[]`.
The snapshot does not apply `endpointSourceOrder` and does not claim that an
item is discoverable. A console combines Version detail and snapshots only as
separate read facts.

RAD catalog, discovery, and watch objects are data-plane views and are defined
only by the [RAD Protocol Spec](rad-protocol-spec.md). In particular,
`AgentDiscoveryResult` combines one online Version definition with permitted
DECLARED and RUNTIME Endpoint sets; it is never stored as a fact.

## 7. Capacity And Security

The target management model enforces these limits before writing an Agent or
Version fact:

| Field | Limit |
| --- | ---: |
| `displayName`, `provider.name` | 128 Unicode code points. |
| `description` | 2048 characters. |
| Icon, provider, or declared Endpoint URI | 2048 characters. |
| Public tags | 32 items, 64 characters each. |
| Agent `extensions` | 32 items; key 128 characters; serialized UTF-8 JSON total 16 KiB. |
| `protocol`, `protocolVersion` | 32 and 64 characters. |
| CallInterfaces per Version | 16. |
| Declared Endpoints per CallInterface | 64. |
| Endpoint metadata | 32 items; key 64 and value 256 characters. |
| `AgentVersionContent` | 1 MiB. |

`biz_tags` stores only public tags supplied by the user and does not contain
server-derived indexes. Its serialized JSON must not exceed 1024 characters.

Descriptors, extensions, and Endpoint metadata must not contain plaintext
credentials. Audit records must not log complete native descriptors, security
schemes, or sensitive Endpoint metadata. Runtime publication and physical
storage limits are defined by the
[Agent Storage Spec](agent-storage-spec.md).

## 8. A2A Compatibility Boundary

After migration, old A2A APIs are compatibility facades over the Agent model;
they do not create a second AgentCard fact source.

| A2A value | Agent model projection |
| --- | --- |
| AgentCard name and version | `agentName` and Agent Version identity. |
| Complete AgentCard | A2A CallInterface `nativeDescriptor`. |
| A2A protocol version | CallInterface `protocolVersion` and native descriptor. |
| Root URL and supported/additional interfaces | Adapter-derived declared Endpoints. |
| `registrationType=URL` | Declared-first source order. |
| `registrationType=SERVICE` | Runtime-first source order. |
| Runtime A2A endpoint version | `runtimeVersion` and exact `[version]` range. |

The first implementation supports only the A2A protocol, so old A2A latest
and common Agent latest use the same label. The adapter reconstructs old query
DTOs from the native descriptor and the applicable Endpoint projection. It
uses declared addresses for URL-style reads and runtime addresses for
service-style reads, with declared addresses as the compatibility fallback
when no runtime address exists.

New writes through old APIs apply the identity, Version, immutability, and
capacity rules in this spec. They may use an audited internal direct-online
transition to preserve code-first A2A publication, but they must not overwrite
different content in an already published Version.

Runtime A2A publication and deregistration projection are defined by the
[Agent Storage Spec](agent-storage-spec.md).

Historical Config rows, historical Naming layouts, mixed-version cluster
dual-read or dual-write, source cutover, rollback, and malformed historical
identities belong to a separate rolling-upgrade and data-migration contract.
They are not relaxed by this target model.

Agent and AgentSpec resources may reference each other through a general
resource relation, but neither owns the other's lifecycle. This version does
not add Agent-specific `sourceRef`, `defaultInterfaceId`, `interfaceId`,
`descriptorDigest`, or random Endpoint identifiers.
