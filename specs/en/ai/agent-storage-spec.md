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

# Agent Storage Spec

This document defines the internal persistence, runtime-publication, Naming
mapping, codec, digest, and revision contract for the
[Agent Management Spec](agent-management-spec.md). The
[RAD Protocol Spec](rad-protocol-spec.md) defines the external data-plane
messages; this document defines how Nacos produces their facts.

This is the normative target contract for the Agent model migration. A server
must not advertise Agent or RAD capability until the storage behavior required
by this contract is implemented. Existing A2A storage remains governed by the
[A2A Agent Spec](a2a-agent-spec.md) before that cutover.

## 1. Storage Responsibilities

Agent state is split by lifecycle and access pattern:

```text
Agent metadata -----------------------> ai_resource
Agent Version metadata ---------------> ai_resource_version
CallInterface + DECLARED Endpoint ----> AI Storage
                                          |
                                          +-- built-in nacos_config provider
RUNTIME publisher contributions ------> Naming Client runtime state
```

| Store | Owns | Does not own |
| --- | --- | --- |
| `ai_resource` | Agent identity, catalog, governance, version summary, and derived online catalog. | Version payload or runtime health. |
| `ai_resource_version` | Exact Version identity, lifecycle status, author, storage pointer, and pipeline state. | CallInterface payload or runtime endpoint. |
| AI Storage | Canonical `AgentVersionContent` bytes for one Version. | Resource identity, lifecycle, labels, or visibility. |
| Naming Client state | Live publisher contributions, health, enabled state, and version bindings. | Agent definition or Version lifecycle. |

The service must not persist a merged `AgentDiscoveryResult`. Summary,
management detail, catalog, discovery, and watch objects are read projections
over these facts.

## 2. AI Resource Persistence

### 2.1 Agent Resource Row

The canonical Agent identity is
`namespaceId + type=agent + name=agentName`. It maps to `ai_resource` as
follows:

| `ai_resource` field | Agent mapping |
| --- | --- |
| `namespace_id`, `type`, `name` | `namespaceId`, constant `agent`, original `agentName`. |
| `c_desc` | `description`. |
| `status` | `enable` or `disable`. |
| `owner`, `scope` | Same-named governance fields. |
| `biz_tags` | Public tags plus server-derived online-protocol tokens. |
| `ext` | Typed `AgentResourceExt`. |
| `c_from` | Creation, import, or synchronization source. |
| `version_info` | Shared editing, reviewing, online-count, and label summary. |
| `meta_version` | Metadata CAS version. |
| `gmt_create`, `gmt_modified` | Audit timestamps. |

`AgentResourceExt` has this fixed schema-version-1 shape:

| Field | Owner | Meaning |
| --- | --- | --- |
| `schemaVersion` | Server | Constant `1`. |
| `displayName`, `iconUrl`, `provider` | User, after validation | Catalog presentation. |
| `extensions` | User, after validation | Public Agent-level extensions. |
| `versionCatalog` | Server | Derived online Version catalog. |

`versionCatalog` contains `latestVersion` and `onlineVersions[]`; each entry
contains only `version`, `labels[]`, and `protocols[]`. Version status and
`version_info.labels` remain the facts. Publish, online, offline, delete, label,
or latest changes rebuild the catalog as one logical Resource update.

Protocol search tokens in `biz_tags` use the reserved prefix
`__nacos.agent.protocol:`. A user tag must not use `__nacos.agent.`. Read
projections remove internal tokens. Public tags and internal tokens share the
canonical persistence limit, and a write that would exceed that limit is
rejected atomically.

AgentName and Version identity are compared case-sensitively in DAO queries,
unique constraints, caches, labels, and authorization keys. Implementations
must not rely on a database's default case-insensitive collation.

### 2.2 Agent Version Row

Each Agent Version maps to one `ai_resource_version` row:

| `ai_resource_version` field | Agent Version mapping |
| --- | --- |
| `namespace_id`, `type`, `name`, `version` | Exact Version identity. |
| `status` | `draft`, `reviewing`, `reviewed`, `online`, or `offline`. |
| `author`, `c_desc` | Author and change description. |
| `storage` | Provider, opaque key, digest, media type, schema, and size. |
| `publish_pipeline_info` | Review execution and result. |
| `gmt_create`, `gmt_modified` | Audit timestamps. |

The physical Version field and every new Agent write support the same maximum
of 64 characters. The storage schema does not create a wider public identity
space than the Agent management contract.

Version list operations read only Resource and Version rows. Exact Version
detail performs one AI Storage read after resolving the Version row.

## 3. Agent Version Content In AI Storage

### 3.1 Content Object And Storage Pointer

One Version has exactly one complete storage object:

```text
AgentVersionContent
  kind = AgentVersionContent
  schemaVersion = 1
  callInterfaces[]
    protocol / protocolVersion
    descriptorMediaType / nativeDescriptor
    endpointSourceOrder[]
    declaredEndpoints[]
```

The server validates the object, creates the storage projection below, and
serializes it once with the common Nacos JSON serializer as UTF-8. The exact
emitted bytes are passed to AI Storage and are also used for `size` and
`contentDigest=sha256:<lowercase hex>`. Agent storage does not define semantic
JSON canonicalization: two JSON representations that decode to equivalent
values are not required to produce the same digest.

Before serialization, the server creates the storage projection:

1. it rejects unknown schema properties on the envelope, CallInterface, and
   Endpoint objects, then projects only schema-version-1 fields;
2. it canonicalizes every declared Endpoint URI, and validates and preserves
   its transport, through the common Endpoint canonicalizer;
3. it materializes effective Endpoint `priority=0` and `weight=1`;
4. it omits absent or empty Endpoint `metadata` and `declaredEndpoints`; and
5. it otherwise preserves all array order and descriptor JSON values.

`nativeDescriptor` JSON members and Endpoint `metadata` map entries remain
open content within their separately defined value constraints.

These projection rules normalize Agent-owned fields only. They do not reorder
members inside `nativeDescriptor` or otherwise rewrite protocol-owned JSON.
On read, integrity validation hashes the exact bytes returned by AI Storage
before decoding; it must not reserialize the decoded object for digest
comparison.

The Version row's `storage` JSON contains:

| Field | Value or meaning |
| --- | --- |
| `provider` | Storage provider; built-in value is `nacos_config`. |
| `key` | Provider-opaque key. |
| `keyFormat` | `agent-version-config-v1` for the built-in provider. |
| `agentNameCodec` | `rad-ascii-v1` for the built-in provider. |
| `contentDigest` | `sha256:<lowercase hex>`. |
| `mediaType` | `application/vnd.nacos.agent-version+json`. |
| `schemaVersion` | `1`. |
| `size` | Persisted content byte count. |

The Agent service composes one provider-neutral logical `StorageKey.key` and
passes it to every provider as an opaque value. A replacement provider keeps
the one-Version/one-object rule and owns the mapping from that logical key to
its physical key. The built-in provider uses the mapping in section 3.2.

### 3.2 Built-in Nacos Config Mapping

The `agent-version-config-v1` provider key carries this logical Config
coordinate. The Agent service composes the logical `StorageKey.key`; after it
is persisted in a descriptor, upper-layer consumers pass it through without
parsing it. The built-in provider parses it only to perform this mapping.

| Logical value | Logical `config_info` coordinate |
| --- | --- |
| `namespaceId` | `tenant_id=namespaceId`. |
| Content category | `group_id=agent-version`. |
| `agentName`, `version` | `data_id=agent__<encodedAgentId>__<version>.json`. |
| `AgentVersionContent` | UTF-8 JSON `content` with `type=json`. |

The built-in provider then applies the common `NacosAiConfigKeyCodec` to the
complete logical group and data id. A safe value within the Config limits is
stored unchanged. An overlong data id uses the codec's deterministic
`sha256.<digest>` physical fallback. The physical key is consequently not
always reversible, and no upper layer may derive Agent identity from it.
Valid Agent identity must not be rejected merely because this logical data id
is longer than the Config physical limit.

The provider-neutral `StorageKey.key` serializes the logical identity as
`<namespaceId>:agent-version:<logicalDataId>`. Every provider receives this
value, but only the built-in provider parses it into the Config coordinate
above. The key has exactly three colon-delimited segments because the
Namespace, encoded AgentName, and Version grammars exclude `:`. Existing
four- and five-part Skill, Prompt, and AgentSpec keys retain their original
interpretation.

A draft update overwrites the same key. Content becomes immutable when the
Version enters reviewing. `contentDigest` never participates in the data id;
it validates the exact persisted bytes and cache equality. Read, review, and
publish operations must verify the Storage pointer, byte count, and digest.

### 3.3 RAD ASCII AgentName Codec

Config data ids and Naming service names share
`RadAsciiAgentIdCodec` with codec id `rad-ascii-v1`:

1. input is the original 1-to-64-character printable-ASCII `agentName`;
2. if the entire input matches `[A-Za-z0-9-]+`, return it unchanged;
3. otherwise output `enc-<body>`;
4. in encoded form, preserve ASCII letters and digits and encode every other
   character, including `-`, as `-DDD`, where `DDD` is its three-digit decimal
   ASCII value;
5. preserve letter case and never trim or lowercase; and
6. decode only a segment already known to use this codec, rejecting truncated,
   non-decimal, out-of-range, or non-canonical escapes.

Examples:

```text
Nacos-Agent  -> Nacos-Agent
Nacos Agent  -> enc-Nacos-032Agent
name-ok.1:2  -> enc-name-045ok-0461-0582
```

The output contains only `[A-Za-z0-9-]`. Codec version 1 intentionally does not
reserve raw names beginning with `enc-`. Consequently, a raw safe name and the
encoded result of another name can theoretically produce the same physical
segment. Version 1 accepts this low-probability ambiguity and defines no
collision index, reservation, or atomic encoded-id mapping. Public identity
always comes from `ai_resource.name`; code must not infer it by decoding an
untyped physical key. A future collision-free codec requires a new codec id and
an explicit migration contract rather than changing `rad-ascii-v1` in place.

Version uses only letters, digits, `.` and `-` and is not processed by the
AgentName codec. The generic Config physical-key codec may hash the complete
logical data id solely to satisfy its physical length limit; this does not
truncate, hash, or rewrite either public identity field.

## 4. Runtime Publication Model

### 4.1 Public Endpoint And Version Binding

DECLARED and RUNTIME sources share the Endpoint value object. Within one Agent
protocol group, the public Endpoint natural key is:

```text
(namespaceId, agentName, protocol,
 normalizedHost(uri), effectivePort(uri), normalizedTransport)
```

URI path, query, priority, weight, and metadata are public Endpoint payload but
do not participate in that natural key. There is no public Endpoint id.

A runtime Version binding contains:

| Field | Meaning |
| --- | --- |
| `runtimeVersion` | Actual running implementation Version. |
| `versionRange` | Agent Versions served by the publication. |

An absent range is normalized to exact `[runtimeVersion]`. A range is one
Maven-style continuous interval whose boundaries and comparisons use the
case-sensitive Agent Version rules, not Maven `ComparableVersion`.

Canonical forms include exact `[1.0.6]`, bounded `[1.0.0,2.0.0)`, lower-bounded
`[1.0.0,)`, and upper-bounded `(,2.0.0]`. They contain no whitespace, have at
least one bound, and use exact form when equal bounds are included. Interval
unions and discrete sets are invalid. `runtimeVersion` must match its range.

### 4.2 Publication Commands

`AgentEndpointRegistrationBatch` contains:

```text
namespaceId / agentName / runtimeVersion / versionRange? / protocol
endpoints[1..1000]
```

All Endpoints in a batch share the Version binding and protocol. A one-item
array is the generic single-Endpoint form. The command itself is not persisted.
The server validates the complete batch before applying it atomically. A
duplicate natural key rejects the batch. Registration upserts only listed
contributions and does not remove omitted Endpoints; repeating identical
content succeeds without a semantic change.

`AgentEndpointDeregistrationBatch` contains only `namespaceId`, `agentName`,
`protocol`, and `endpoints[] {uri, transport}`. For the current publisher, each
natural Endpoint key removes all of that publisher's Version-binding groups.
The caller does not submit or cache endpoint ids, runtime Versions, ranges,
metadata, priority, or weight.

### 4.3 Internal Publisher Contributions

The internal publication-group identity is:

```text
publisherIdentity
+ namespaceId + agentName + protocol
+ runtimeVersion + canonicalVersionRange
```

An Endpoint contribution identity appends the public Endpoint natural key.
This distinction is mandatory:

- one publisher may bind the same host, port, and transport through multiple
  runtime-Version/range groups;
- Version bindings do not create version-specific Naming services or duplicate
  the public Endpoint in a target discovery result;
- registering the same contribution identity is an upsert; and
- the new generic deregistration command removes every matching group for that
  publisher and natural Endpoint.

The compatibility adapter may use an internal group-delete operation. It
deletes only one exact publication group and is not part of the public RAD
command set. The old A2A exact-Version deregistration uses this operation so it
does not remove the same publisher's contributions for another Version.

Across all live contributions, the same public natural Endpoint key must have
one canonical public Endpoint payload, regardless of publisher identity or
whether their Version ranges overlap. A registration whose URI scheme, path,
query, priority, weight, or public metadata differs from the existing payload is
rejected as a contribution conflict. Contributions may add distinct Version
bindings only when their canonical Endpoint payload is identical.

### 4.4 Bindings Aggregation

Naming publisher contributions are aggregated into an Endpoint projection
with a canonical `bindings[]` array:

```json
[
  {"runtimeVersion":"1.0.6","versionRange":"[1.0.0,2.0.0)"}
]
```

The array is deduplicated and sorted in ascending Agent SemVer order by
`runtimeVersion`, then in ascending case-sensitive string order by
`versionRange`. This exact array is the Version-matching fact.

Each effective publisher record stores it under
`__nacos.agent.endpoint.bindings__`. When the array contains exactly one item,
the record also writes these diagnostic and initial-release compatibility
mirrors:

```text
__nacos.agent.endpoint.version__       = runtimeVersion
__nacos.agent.endpoint.versionRange__  = versionRange
```

When the array contains more than one item, both singular keys are removed.
Readers always use `bindings` when present and must not merge stale singular
keys into it.

`RuntimeEndpointSnapshot` aggregates publisher contributions without exposing
publisher identity. It contains exactly one item per public natural Endpoint
key, with the canonical Endpoint payload and all effective `bindings[]`. A
Version-filtered snapshot retains only matching bindings and omits an item when
none remain.

RAD discovery first filters bindings for the target Version, then aggregates
equal natural keys into one public Endpoint. Because every inconsistent payload
is rejected at write time, one target Version never produces two
different public payloads for the same natural key.

### 4.5 Pre-registration And Lifecycle

Runtime publication is independent from Agent definition creation. The server
accepts a structurally valid, authorized publication even when the Agent,
Version, or CallInterface does not exist. Registration success means runtime
intent was accepted; it does not imply current discoverability.

Registration validates AgentName, runtime Version, range, protocol, Endpoint,
authorization, capacity, and contribution conflicts. It does not validate
definition existence or Version lifecycle status.

Publisher identity is internal:

- gRPC contributions belong to a connection id;
- HTTP contributions belong to a validated client id and use one client-level
  heartbeat; and
- public management and RAD objects do not expose identity or publisher count.

Disconnect or client expiration removes only that publisher's contributions.
Other equal contributions remain. Aggregate `healthy` is true when at least one
matching live contribution is healthy and false only when all are unhealthy.
Heartbeat-only and publisher-count-only changes do not change the public
projection.

`enabled` is an independent Naming operational state and is not overwritten by
heartbeats. Agent Endpoint metadata must not set Naming heartbeat interval,
heartbeat timeout, or instance-delete timeout keys. Explicit deregistration,
publisher loss, or Naming cleanup ends runtime state. Agent disable, Version
offline, or definition deletion only removes it from applicable discovery
projections.

## 5. Runtime Mapping To Naming

### 5.1 Service And Cluster Identity

The logical Naming scope is:

```text
namespaceId
+ groupName=agent-endpoints
+ serviceName=radServiceName(encodedAgentId, protocol)
+ clusterName=normalizedTransport
```

Canonical protocol tokens match
`[A-Za-z0-9][A-Za-z0-9-]{0,31}`. The service-name algorithm is:

```text
rad-<encodedAgentId>-<protocol>
```

The result preserves case, contains only `[A-Za-z0-9-]`, starts with an
alphanumeric character, may end with an alphanumeric character or `-`, and
contains no Version. Its effective maximum is 297 characters under the field
limits, below the Naming limit of 512 characters.

Examples:

```text
Nacos-Agent / a2a -> rad-Nacos-Agent-a2a
Nacos Agent / a2a -> rad-enc-Nacos-032Agent-a2a
```

Version 1 favors a concise, readable physical name and adds no length framing
between `encodedAgentId` and protocol. Consequently, `(A, B-C)` and `(A-B, C)`
both compose to `rad-A-B-C`. Version 1 accepts this low-probability collision
and defines no collision index or extra disambiguation. Implementations must
not recover the two components from serviceName; readers recompose and compare
using the known AgentName and protocol. A future collision-free rule requires
a new composer id and an explicit migration contract.

The alphabet guarantees that `lb://<serviceName>` can be parsed as a Gateway
URI. It does not define a DNS name and does not lowercase the case-sensitive
Nacos service identity. An integration that normalizes service ids to lowercase
is outside this compatibility guarantee.

`clusterName` is normalized transport and matches
`[0-9A-Za-z-]{1,64}`. Transport is stored in both cluster identity and reserved
metadata and must agree on read.

### 5.2 Instance Field Mapping

| Agent runtime field | Naming field |
| --- | --- |
| `namespaceId` | Service namespace. |
| fixed group | `agent-endpoints`. |
| encoded Agent and protocol | Canonical service name from section 5.1. |
| normalized transport | `Instance.clusterName`. |
| normalized URI host and effective port | `Instance.ip`, `Instance.port`. |
| URI path | `__nacos.agent.endpoint.path__`. |
| normalized transport | `__nacos.agent.endpoint.transport__`. |
| URI scheme | `__nacos.agent.endpoint.protocol__`. |
| legacy A2A protocol version | Optional `__nacos.agent.endpoint.protocolVersion__`. |
| HTTPS state | `__nacos.agent.endpoint.supportTls__`. |
| raw URI query | `__nacos.agent.endpoint.query__`. |
| native tenant, when present | `__nacos.agent.endpoint.tenant__`. |
| canonical bindings | `__nacos.agent.endpoint.bindings__`. |
| single-binding diagnostic mirrors | `__nacos.agent.endpoint.version__`, `__nacos.agent.endpoint.versionRange__`. |
| priority | `__nacos.agent.endpoint.priority__`. |
| weight | `Instance.weight`. |
| public Endpoint metadata | Remaining `Instance.metadata`. |
| runtime state | `Instance.enabled`, `Instance.healthy`, `ephemeral=true`. |

User metadata must not override any `__nacos.agent.endpoint.*__` key. The server
constructs and validates the complete Naming metadata before accepting a
publication. Missing range input is canonicalized before writing `bindings`.

`__nacos.agent.endpoint.protocolVersion__` is a legacy-only compatibility fact.
Only the A2A compatibility adapter writes it. It is excluded from public RAD
Endpoint metadata and Runtime revision input. When projecting an old A2A
response, the adapter prefers this value and falls back to the target
CallInterface `protocolVersion` when it is absent. The aggregate writes this
singular key only while every represented A2A contribution reports the same
value; if values differ, it removes the key and each exact-Version projection
uses its target CallInterface fallback. A disagreement is not a public Endpoint
payload conflict.

The public natural key maps to service, cluster, IP, and port. Path and query
remain payload metadata. No Version appears in serviceName or clusterName, so
the service count does not grow with compatible Agent Versions.

### 5.3 Naming Fact Boundary

Naming Client publisher contributions, including their canonical bindings, are
the RUNTIME fact source. Ordinary Naming `ServiceInfo` may collapse equal IP and
port entries, apply selector or health-protection behavior, and cannot preserve
all Agent publication groups. It is not a RAD fact source.

Agent runtime reads aggregate raw publisher contributions from the Naming
Client/index path, then apply binding and enabled filters. They must not forward
a standard Naming Java SDK subscription result as a RAD watch snapshot.

Operational Naming metadata for `enabled` and `weight` has its normal
precedence over runtime publication values. The Agent projection still retains
unhealthy instances and exposes their raw aggregate health; it does not apply
Naming health-protection fallback.

## 6. Runtime Discovery Projection

A RUNTIME Endpoint is eligible for one target discovery result only when:

1. the Agent exists, is visible, and is enabled;
2. the target Version is online;
3. the target Version has the same protocol CallInterface and permits the
   `RUNTIME` source;
4. at least one effective binding contains the target Version; and
5. the Naming Endpoint has `enabled=true`.

An eligible Endpoint with `healthy=false` remains in RAD output. SDK
`selectOneHealthy` filters it; get-all and watch retain it. A disabled Endpoint
is absent.

The projection uses the target Version's CallInterface for protocol version,
descriptor, and endpoint-source order. Runtime contributions never override
those definition fields; the legacy-only Naming protocol-version metadata is
ignored by RAD.

## 7. Runtime Source Revision

For each
`(namespaceId, agentName, targetVersion, protocol, source=RUNTIME)`, the server
generates an opaque `sourceRevision` after it:

1. aggregates live publisher contributions;
2. selects bindings that contain the target Version;
3. canonicalizes each Endpoint URI, validates and preserves transport,
   materializes effective `priority=0` and `weight=1`, and requires `healthy`;
4. validates one canonical payload per natural key;
5. removes `enabled=false` and retains both health states;
6. sorts Endpoints by natural key and metadata keys by UTF-16 code-unit ordinal
   order; and
7. computes MurmurHash3 x64 128 over the revision bytes defined below.

Within one projection, natural-key order compares `normalizedHost` by UTF-16
code-unit ordinal order, then `effectivePort` numerically, then transport by
UTF-16 code-unit ordinal order. Implementations must not use locale-sensitive
collation. URI path and query do not participate in ordering because they are
not natural-key fields.

The external token is:

```text
murmur3-x64-128-v1:<32 lowercase hex>
```

Revision input contains URI, transport, effective priority and weight, public
Endpoint metadata, and `healthy`. It excludes runtimeVersion, versionRange,
publisher identity and count, heartbeat time, last-updated time, and Naming
internal revisions. Runtime Version and range do not enter the hash because the
target projection has already filtered them. Range or enabled changes alter
membership; health changes alter returned content. Both therefore advance the
revision when the target projection changes.

Absent and empty public Endpoint metadata both use a metadata entry count of
zero.

The empty set has a stable revision. An additional or removed redundant
publisher does not change it. The token is only cache equality and watch
deduplication; it is not identity, authorization, CAS, or tamper protection.

All nodes use seed `0` and the following fixed big-endian binary layout:

| Element | Encoding |
| --- | --- |
| Endpoint count | Unsigned four-byte integer. |
| `uri`, `transport` | Unsigned four-byte UTF-8 byte length followed by the bytes. |
| `priority` | Signed four-byte integer. |
| `weight` | Eight-byte IEEE-754 binary64 bits; negative zero is normalized to positive zero. |
| metadata | Unsigned four-byte entry count, followed by each ordered key and value using the string encoding above. |
| `healthy` | One byte: `0` for false and `1` for true. |

The empty set is exactly `uint32be(0)`. The Murmur result emits `h1` followed
by `h2`, each as an unsigned eight-byte big-endian value, and then lowercase
hexadecimal. These rules are also machine-readable in internal storage schema
version 1.

Implementations mark semantic projections dirty, coalesce bursts, and cache
the result. They must not hash every heartbeat or every discovery read.

Persistent AgentVersion content continues to use SHA-256. A DECLARED endpoint
set uses the Version `contentDigest` as its opaque source revision.

## 8. Read, Write, Cache, And Consistency Paths

| Read | Facts read | AI Storage read |
| --- | --- | :---: |
| Management Agent list or RAD Search | `ai_resource` page. | No |
| Agent overview | Resource plus bounded Version-row page. | No |
| Exact Version detail | One Version row. | One |
| Runtime Endpoint snapshot | Raw Naming publisher contributions for one protocol; optional binding filter. | No |
| RAD Discover | Resource, online Version, cached content, and eligible runtime projection. | Once on digest miss |

| Change | Write target | Consistency rule |
| --- | --- | --- |
| Agent catalog, governance, extensions | `ai_resource`. | `metaVersion` CAS. |
| Create or update draft | AI Storage fixed key plus Version row. | Pointer, bytes, size, and digest agree. |
| Publish, online, offline, delete, label/latest | Version row plus Resource summaries. | Rebuild derived catalog and protocol tokens. |
| Runtime register, heartbeat, deregister | Naming Client runtime state. | Does not write AI Resource or Storage. |

Cache validators follow facts:

| Fact | Validator |
| --- | --- |
| Agent metadata | `metaVersion`. |
| Agent Version content | `contentDigest`. |
| Target runtime projection | `sourceRevision`. |

An AI Storage provider guarantees atomic bytes for one StorageKey and the read
consistency it declares. Agent Registry owns orchestration across Resource,
Version, Storage pointer, digest, and derived catalog. It performs validation,
idempotent retry, and failure compensation. Publish must reread content and
validate the digest.

A successful Storage write followed by a failed metadata write produces an
observable incomplete operation that is retried or cleaned as orphan content.
Digest mismatch must never return unverified content. `versionCatalog`,
protocol tokens, and Resource version summaries are rebuildable derived data;
their consistency is not delegated to Storage providers.

## 9. Capacity And Security

| Runtime or physical field | Limit |
| --- | ---: |
| `runtimeVersion` | 64 characters. |
| Canonical `versionRange` | 256 characters; one continuous interval. |
| Registration batch | 1 to 1000 Endpoints. |
| Runtime Endpoints per Agent and protocol | 1000, subject to a lower cluster quota. |
| Final Endpoint metadata | 32 public items; key 64 and value 256 characters. |
| Final Naming metadata | Sum of Java `String.length()` for keys and values is 1024. |
| Agent Version physical Config data id | 255 characters, enforced by `NacosAiConfigKeyCodec`; an overlong logical id uses its SHA-256 fallback. |
| Agent Version content | 1 MiB. |

The server validates the complete generated metadata, including reserved keys,
before writing Naming. It rejects an overflow and never truncates or silently
drops fields.

AI Storage content, Endpoint metadata, and publisher state must not contain
plaintext credentials. Logs and audit events must not expose complete native
descriptors, security schemes, publisher identities to ordinary users, or
sensitive Endpoint metadata.

## 10. A2A Runtime Compatibility Boundary

The A2A adapter is the first consumer of this storage contract:

| Legacy A2A fact | New storage projection |
| --- | --- |
| AgentCard definition | A2A `AgentCallInterface.nativeDescriptor` in Version content. |
| Root and additional interfaces | Adapter-derived DECLARED Endpoints. |
| Runtime AgentEndpoint Version | `runtimeVersion=version`, `versionRange=[version]`. |
| Runtime calling protocol | Canonical Agent protocol token `a2a`. |
| Legacy endpoint transport and URI parts | Common Endpoint and reserved Naming metadata. |

Old single and batch registrations keep an exact-Version replacement scope:

```text
(publisherIdentity, namespaceId, agentName, protocol=a2a,
 runtimeVersion=version, versionRange=[version])
```

The compatibility adapter replaces that internal group. Old deregistration
deletes only that exact group, even when the same publisher and physical
Endpoint have bindings for other Versions. New RAD deregistration instead
deletes all bindings for the supplied natural Endpoint under the current
publisher.

After cutover, compatibility writes use the new AI Resource, AI Storage, and
Naming layouts. Historical Config rows, historical Naming services, mixed
cluster dual-read or dual-write, cutover, rollback, and malformed historical
identity handling belong to a separate rolling-upgrade and migration contract.
