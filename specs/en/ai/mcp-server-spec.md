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

# MCP Server Spec

| Item | Value |
| --- | --- |
| Status | Experimental target migration contract |
| Canonical resource type | `mcp` |
| Migration route state | `SYNCING` or `CANONICAL` |
| Direct compatibility state | `SYNCING`, `CANONICAL_COMPAT`, or future `PROJECTION_RETIRED` |

This document defines the Nacos AI Registry contract for MCP Server resources,
including the migration from historical Config metadata to the standard AI
Resource lifecycle. Until that migration implementation is present, the
historical implementation remains the active code path; this target contract
must not be advertised as an implemented server ability.

The migration is intentionally MCP-specific. It does not introduce a new
abstraction shared by every AI resource, change Naming semantics, or move
historical MCP payloads merely to obtain a different physical key.

## 1. Scope And Fact Boundaries

MCP state is split by ownership and lifecycle:

```text
MCP metadata --------------------------> ai_resource
MCP Version governance ----------------> ai_resource_version
Server / Tools / Resources content ----> existing Config coordinates
Direct Endpoint fact ------------------> Version Server Config
Direct downgrade projection -----------> persistent Naming Service
ordinary Service Ref ------------------> externally owned Naming Service
Runtime Endpoint publication ----------> Naming Client runtime state
```

| Fact | Canonical owner after cutover |
| --- | --- |
| MCP identity, status, owner, scope, tags, labels, and working Version pointers | `ai_resource` |
| Version status, author, description, pipeline state, and storage pointer | `ai_resource_version` |
| Version Server, Tools, and Resources content | Existing Config objects selected by the Version storage descriptor |
| Direct Endpoint addresses | `endpointKind` and `directEndpoints` in the Version Server Config |
| Direct persistent Naming Service | MCP-owned downgrade projection; never a canonical read dependency |
| Ordinary referenced Service | Naming user that owns that Service |
| Runtime Endpoint | Naming Client publisher and its connection/liveness lifecycle |
| Historical `mcp-server-versions` object | Compatibility projection after cutover; never a canonical decision source |

MCP Registry-compatible discovery remains an optional adapter surface defined
by the [AI Registry Adaptor Spec](ai-registry-adaptor-spec.md). It is not a
second MCP resource store.

## 2. Identity And AI Resource Mapping

### 2.1 Resource Identity

The canonical identity is:

```text
namespaceId + type=mcp + name=mcpName
```

`mcpName` is case-sensitive and immutable as an identity field. The historical
UUID-shaped `mcpId` is retained in `AiResource.ext` only as an API and storage
compatibility alias; it does not participate in canonical identity,
authorization, labels, or runtime Service composition.

The schema-version-1 extension is:

```json
{
  "schemaVersion": 1,
  "mcpId": "4d7939c0-72ea-4ef4-b232-418d1e16b45c"
}
```

Its machine-readable contract is
[`mcp-resource-ext.schema.json`](../../schemas/ai/mcp/internal/v1/mcp-resource-ext.schema.json).

The Resource row maps MCP fields as follows:

| `AiResource` field | MCP mapping |
| --- | --- |
| `namespaceId`, `type`, `name` | Namespace, constant `mcp`, and `mcpName`. |
| `desc` | MCP description. |
| `status` | Historical `enabled=true` maps to `enable`; otherwise `disable`. |
| `owner` | Creating/importing operator; historical synchronization uses `nacos`. |
| `scope` | Visibility default for new resources; historical synchronization uses `PUBLIC`. |
| `bizTags` | Public MCP business tags, or an empty collection. |
| `ext` | `McpResourceExt` containing the compatibility `mcpId`. |
| `from` | Create, import, or `legacy-mcp` synchronization source. |
| `versionInfo` | Standard editing, reviewing, online-count, and label summary. |

### 2.2 Version Identity And Status

Each MCP Version has one `AiResourceVersion` row. Its exact identity is:

```text
namespaceId + type=mcp + mcpName + version
```

MCP Version values are non-empty, case-sensitive, and at most 64 characters so
they fit the shared Version row without a table change. Strict SemVer is
recommended and is required for range matching, but historical non-SemVer
values within that limit remain valid exact identities. Migration must not
rewrite them; an over-limit historical value is invalid data that blocks
cutover until repaired.

Historical published Versions enter the canonical model as `online`. New
management operations use the standard `draft`, `reviewing`, `reviewed`,
`online`, and `offline` states from the
[AI Resource Lifecycle Spec](ai-resource-lifecycle-spec.md). The MCP Registry
content states such as `active` and `deprecated` remain Version-content facts;
they do not replace the AI Resource lifecycle state.

Runtime queries require an enabled Resource and an online Version. An omitted
Version resolves the server-managed `latest` label. Management reads may query
every lifecycle state.

### 2.3 Latest Selection

The first online Version becomes `latest`. Standard publish, force-publish, and
online operations move `latest` to the target. A legacy direct-online update
may preserve the current valid label when its historical `latest=false`
parameter is used.

Deleting or taking the current latest Version offline selects a replacement in
this order:

1. greatest valid SemVer by SemVer precedence;
2. if no SemVer remains, greatest `vN` value by numeric `N`; or
3. if neither form remains, greatest string by stable case-sensitive ordinal
   comparison.

If no online Version remains, `latest` is removed. A non-SemVer Version may be
selected by exact query but never participates in a Version range.

## 3. Version Content And Storage

### 3.1 Existing Physical Coordinates

The migration preserves the existing MCP Config groups and data ids:

| Content | Config group | Data id |
| --- | --- | --- |
| Historical Version manifest | `mcp-server-versions` | `<mcpId>-mcp-versions.json` |
| Version Server | `mcp-server` | `<mcpId>-<version>-mcp-server.json` |
| Version Tools | `mcp-tools` | `<mcpId>-<version>-mcp-tools.json` |
| Version Resources | `mcp-resources` | `<mcpId>-<version>-mcp-resources.json` |

The manifest becomes compatibility metadata. The three Version-content objects
remain at their current coordinates.

### 3.2 Storage Descriptor

`AiResourceVersion.storage` contains one schema-version-1 descriptor:

```json
{
  "provider": "nacos_config",
  "keyFormat": "mcp-config-v1",
  "serverKey": "public:mcp-server:<mcpId>-<version>-mcp-server.json",
  "toolKey": "public:mcp-tools:<mcpId>-<version>-mcp-tools.json",
  "resourceKey": "public:mcp-resources:<mcpId>-<version>-mcp-resources.json",
  "schemaVersion": 1
}
```

`serverKey` is required; `toolKey` and `resourceKey` are omitted when the
corresponding content is absent. The built-in provider parses only the first
two `:` separators and treats the remainder as the Config data id. It accepts
only the three MCP-owned groups above; this contract must not become arbitrary
`namespace:group:dataId` access to user Config.

All three keys select the same persisted provider. The initial migration
supports only `nacos_config`; an MCP multi-object contract for other providers
requires a later design. The schema is
[`mcp-version-storage.schema.json`](../../schemas/ai/mcp/internal/v1/mcp-version-storage.schema.json).

### 3.3 Byte Preservation And Write Order

Migration creates descriptors that point to existing Config objects; it does
not call storage `save` or copy content. Tools and Resources bytes remain
unchanged. A non-Direct historical Server Config also remains unchanged.

The only historical content mutation is Direct Endpoint materialization in
Section 4.2. It uses Config CAS to add an equivalent, self-contained snapshot
while preserving the existing `serviceRef` and every unrelated JSON field.

A new or updated draft writes in this order:

1. validate Server, optional Tools, optional Resources, and Endpoint kind;
2. save Tools and Resources;
3. save the Server object that references them, including a complete Direct
   snapshot when applicable;
4. create or update the Version row with its storage descriptor; and
5. CAS-update Resource `versionInfo`, then schedule Search and compatibility
   projections.

Draft retries overwrite deterministic keys. A failed attempt before the
Version row exists may leave retryable orphan content. Canonical APIs never
overwrite reviewing, reviewed, online, or offline content.

Deletion first loads every complete storage descriptor and attempts every
referenced content deletion. A content deletion failure preserves Resource and
Version rows for retry. A Direct Naming projection is derived state rather than
Version storage: its physical cleanup failure does not resurrect or roll back a
successful canonical business deletion, but it must create a durable,
owner-checked cleanup retry.

## 4. Endpoint Model

### 4.1 Public Shape And Internal Kinds

The public `McpEndpointSpec.type` remains `DIRECT` or `REF`. Internally, MCP
resolves one of three kinds:

| Internal kind | Meaning | Owner | Version deletion |
| --- | --- | --- | --- |
| `DIRECT` | Known addresses are part of this Version. | MCP Version | Delete only its owner/hash-matching projection. |
| `SERVICE_REF` | The Version references an ordinary existing Naming Service, including HTTP-to-MCP use cases. | Naming user | Never update or delete it. |
| `RUNTIME_REF` | MCP Client APIs publish ephemeral runtime instances. | Naming Client/connection | Explicit deregistration, expiry, or disconnect; not Version deletion. |

New Server content persists an explicit `endpointKind`. For historical content
that lacks it, the compatibility reader resolves in this order:

1. a referenced Service marked `__nacos.ai.mcp.service__=true` is `DIRECT`;
2. `mcp-endpoints / mcpName::version` without that mark is `RUNTIME_REF`; or
3. every other `REF` is `SERVICE_REF`.

No inference may delete or mutate a Service. A missing Service, missing Direct
mark, or conflicting fact blocks migration rather than being guessed.

### 4.2 Direct Endpoint Fact

Direct addresses are stored in the existing Version Server Config:

```json
{
  "endpointKind": "DIRECT",
  "directEndpoints": [
    {
      "address": "10.0.0.8",
      "port": 8080,
      "transportProtocol": "sse"
    }
  ],
  "remoteServerConfig": {
    "serviceRef": {
      "namespaceId": "public",
      "groupName": "mcp-endpoints",
      "serviceName": "demo::1.0.0",
      "transportProtocol": "sse"
    }
  }
}
```

`directEndpoints` is the complete Version snapshot. Entries are deduplicated by
`address + port + transportProtocol` and sorted by address ordinal, numeric
port, then transport ordinal. Canonical query and subscription projection read
this snapshot and do not query the persistent Naming Service.

For historical Direct content, synchronization must:

1. verify the `mcp-endpoints / mcpName::version` Service and its Direct mark;
2. read all persistent instances and combine each address/port with the
   `serviceRef.transportProtocol` value;
3. CAS-add `endpointKind` and the deterministic snapshot without changing the
   old `serviceRef` or other fields;
4. rebuild or repair the Direct compatibility projection metadata; and
5. reread and verify Config and Naming equivalence before the Version may pass
   final migration validation.

Old Jackson-based servers ignore the two unknown fields and can continue to
read `serviceRef`. This supports emergency whole-cluster downgrade reads of the
online compatibility view; mixed-version rolling downgrade and lossless
legacy writes followed by re-upgrade are not promised. An old write may
serialize the object without the new fields.

### 4.3 Direct Naming Compatibility Projection

The version-scoped persistent Naming Service remains during the first
canonical release. Its Service metadata is:

| Key | Value |
| --- | --- |
| `__nacos.ai.mcp.service__` | `true` |
| `__nacos.ai.mcp.id__` | Compatibility `mcpId` |
| `__nacos.ai.mcp.version__` | Exact MCP Version |
| `__nacos.ai.mcp.endpointSnapshotHash__` | `sha256:<64 lowercase hex>` |

The digest is SHA-256 over the UTF-8 bytes of the common Nacos JSON
serialization of the sorted, deduplicated `directEndpoints` storage
projection. The serializer must emit only `address`, `port`, and
`transportProtocol` in that order. Fixtures must freeze the emitted bytes.

Projection update or deletion requires the Direct mark, matching `mcpId`,
matching Version, and the expected snapshot digest. This prevents a delayed
retry from deleting a newly recreated Service with the same name.

Projection compatibility is independent of read-route state:

| Compatibility state | Direct fact | Persistent Naming behavior |
| --- | --- | --- |
| `SYNCING` | Being materialized; Naming may still be the legacy fact. | Never delete. |
| `CANONICAL_COMPAT` | Server Config is canonical. | Maintain an online-view downgrade projection; do not bulk-delete at cutover. |
| `PROJECTION_RETIRED` | Server Config remains canonical. | Future explicitly gated retirement may stop creation and clean projections. |

The initial implementation reaches only `CANONICAL_COMPAT`. Draft creation
does not create a projection. Publish, force-publish, online, and legacy
direct-online writes ensure it exists. Offline removes the Version from the
legacy manifest projection but retains the persistent Direct Service for later
online. Business deletion removes the manifest entry and schedules projection
cleanup immediately.

`PROJECTION_RETIRED` requires a later spec and implementation that declares the
end of legacy downgrade support, gates every server at the cleanup version,
retires the old manifest projection, verifies every Direct snapshot, and
operates only on complete owner/hash metadata. Cutover to `CANONICAL` alone is
never permission to remove surviving Direct Services.

### 4.4 Ordinary Service Ref

A `SERVICE_REF` stores the user-provided `namespaceId`, `groupName`,
`serviceName`, and `transportProtocol`. MCP reads the Service but never creates,
overwrites, or deletes its Service or instances. MCP Client Endpoint
register/deregister rejects this kind. Ordinary service registration continues
through Naming APIs.

### 4.5 Runtime Ref And Naming Layout

The target Runtime layout is:

```text
group       = mcp-endpoints
serviceName = mcpName
cluster     = DEFAULT
instance    = ephemeral
```

Version, protocol, and transport are not part of Service or Cluster identity.
Runtime instance metadata uses exactly these reserved keys:

```text
__nacos.mcp.endpoint.supportedTransports__
__nacos.mcp.endpoint.version__
__nacos.mcp.endpoint.versionRange__
```

`supportedTransports__` is one canonical comma-delimited value: `sse`,
`streamable-http`, or `sse,streamable-http`. Values are lower-case,
deduplicated, contain no whitespace or empty token, and follow that fixed
order. An absent value means transport-unrestricted compatibility.

Version binding is singular; no serialized `versionBindings__` array exists:

| `version__` | `versionRange__` | Meaning |
| --- | --- | --- |
| absent | absent | Compatible with all current and future Versions. |
| any exact string | absent | Exact match, including a historical non-SemVer Version. |
| SemVer | canonical range | Runtime is at that Version and supports the range; the range must contain it. |
| absent | present | Invalid. |
| non-SemVer | present | Invalid. |

Range syntax and comparison reuse the Agent/RAD canonical range parser. The
legacy `_mcp_server_version` metadata is read as an exact binding only when the
new Version key is absent. If both are absent, the instance is all-Version.

For target Version `V` and transport `T`, runtime query retains an instance
only when it is enabled, its transport is unrestricted or includes `T`, and
its exact/range binding accepts `V`. Health is preserved in the result; the
server does not load-balance.

Historical Version content is not rewritten merely to change its old
`mcpName::version` service reference. During compatibility, query reads the new
Versionless Service and that historical referenced Service, prefers new
contributions, and deduplicates by IP and port. Old ephemeral instances expire
on disconnect; SDK redo after reconnect publishes to the new layout.

MCP does not expose direct subscription to the underlying Naming Service.
`subscribeMcpServer` continues polling the complete MCP query projection.

## 5. Canonical Lifecycle And Compatibility Facades

### 5.1 Standard Management Lifecycle

MCP uses the common draft, submit, review, publish, force-publish, redraft,
online, offline, label, and delete rules. Published content is immutable through
canonical APIs; a change creates a new Version or redrafts a reviewed Version.

The approved Admin prefix is `/v3/admin/ai/mcp`. Console mirrors the same
relative operations under `/v3/console/ai/mcp`. Exact routes are listed in the
[V3 HTTP API Surface](../http-api/v3-api-surface.md).

### 5.2 Historical Direct-Online Mapping

Existing Admin, Console, Maintainer SDK, Java Client SDK, and gRPC shapes remain
wire-compatible and are adapted as follows:

| Historical operation | Canonical behavior |
| --- | --- |
| Create/release MCP | Create Resource and Version, take it online immediately, and set `latest`. |
| Update with a new Version | Create an online Version; move `latest` only when the historical parameter requests it. |
| Update an existing exact Version | Compatibility-only same-Version overwrite; preserve status and apply the historical latest parameter. |
| Delete exact Version | Delete its storage and owner/hash-matching Direct projection. |
| Delete MCP | Apply common Resource deletion to every Version and compatibility projection. |
| Runtime query | Return only enabled + online; omitted Version resolves `latest`. |
| Historical `allVersions` | Project online Versions only. |
| Subscribe | Continue full-result polling; do not subscribe to Naming directly. |

Same-Version overwrite is an audited exception available only through the
historical update facade. Canonical lifecycle services must not reuse that
relaxation. Draft, reviewing, reviewed, and offline Versions are visible through
new management Version APIs rather than being disguised as published entries
in old DTOs.

Client HTTP parity with gRPC, transport selection, and heartbeat reuse are
deferred until the management migration is complete. The existing Java SDK
public interfaces should remain unchanged where they can express the old
contract.

## 6. Historical Synchronization And Automatic Cutover

### 6.1 Route State, Marker, And Lease

There is no operator-controlled `nacos.ai.mcp.storage.mode`. The cluster has one
durable, one-way route state:

| State | Read and write route |
| --- | --- |
| `SYNCING` | Historical MCP facts remain authoritative while canonical rows are reconciled. |
| `CANONICAL` | Every MCP surface uses Resource/Version facts; no legacy read fallback. |

The completion marker is an internal Config object:

```text
group  = nacos_internal
dataId = nacos.ai.mcp.resource.migration.v1
content = {"schemaVersion":1,"state":"CANONICAL","completedAt":<epochMillis>}
```

It is permanent and is never deleted when a task finishes. A separate renewable
lease uses group `nacos_internal` and data id
`nacos.ai.mcp.resource.migration.lock.v1`; lease expiry permits another node to
continue reconciliation. Losing the lease aborts the current writer but never
removes MCP content.

### 6.2 Asynchronous Reconciliation

After the root `ApplicationReadyEvent`, a background task periodically:

1. stops immediately when the completion marker already exists;
2. acquires and renews the cluster lease;
3. pages through every Namespace and the authoritative
   `mcp-server-versions` Config group rather than trusting only an in-memory
   index;
4. validates each manifest and every referenced Server, Tools, and Resources
   object;
5. materializes and verifies historical Direct snapshots first;
6. idempotently upserts all Version rows as `online`, using descriptors that
   point to existing content;
7. upserts the Resource row last with legacy enabled/latest facts and
   `from=legacy-mcp`;
8. removes reconciled rows whose `legacy-mcp` source was deleted, without
   deleting independently created canonical resources;
9. performs a zero-difference validation round; and
10. publishes the completion marker only after the data conditions and the
    all-member capability gate both pass.

No historical data is also a valid zero-difference result for a new cluster.
Invalid manifests, missing content, conflicts, Direct mismatch, pending delete,
or lease loss keep the cluster in `SYNCING` and are retried. They are never
silently skipped.

### 6.3 Writes During `SYNCING`

All query, list, subscribe, and Search responses remain wholly legacy while
`SYNCING`; partial canonical rows are never exposed. A successful legacy write
on a capable node invokes the same per-resource reconciler. The periodic full
scan remains the repair path for writes made by an older node.

The Resource row is created last, so a failed Version-first attempt is not
visible through the legacy route. Reconciliation is idempotent and must compare
an existing row for equivalence rather than overwrite a different MCP identity
or content pointer.

Rolling upgrade stays `SYNCING` until every known member reports the minimum
MCP canonical capability version and at least one later full scan produces no
create, update, delete, pending, or failed result. Missing or invalid member
versions fail the gate. Once a marker is published, a server below that minimum
must not join and serve MCP traffic.

### 6.4 Completion Conditions

Cutover requires all of the following:

- one equivalent MCP Resource for every legacy manifest;
- one equivalent Version row and correct descriptor for every historical
  Version;
- matching `mcpId`, enabled state, latest label, online count, and Version set;
- complete Direct snapshots, equivalent retained projections, and complete
  owner/hash metadata;
- no invalid, missing, conflicting, pending, or deleted-but-unreconciled fact;
- a final zero-difference reconciliation round; and
- every cluster member supporting canonical reads/writes and `SYNCING` write
  hooks.

After completion, canonical row absence is an integrity error rather than a
reason to revive data from the old manifest. The old manifest may be rebuilt
from online canonical facts as a downgrade projection, but projection failure
does not change the durable route state. Restart does not roll back
`CANONICAL`; automatic rollback is unsupported.

At minimum, migration diagnostics expose state, total, scanned, created,
updated, deleted, pending, failed, and last-success time, with
namespace/mcpId/version context for invalid facts.

## 7. Tool Schema, Search, Import, And Adaptor Rules

An MCP tool `outputSchema` is JSON Schema. Nacos preserves valid type unions,
including a nullable property such as `{"type":["string","null"]}`. Console
load/save and OpenAPI import must not narrow that union to one string type.

MCP participates in generic AI Resource Search and the MCP-specific Search
facade through one shared index and Query Planner. Canonical Search identity is
`mcpName`, not `mcpId`. Search may project public Server description, Tools,
Resources, tags, protocols, and capabilities; credentials, runtime instances,
and sensitive auth metadata never enter search chunks. While `SYNCING`, the
handler may read compatibility storage but must produce the same projection.

External import uses the
[AI Resource Import Plugin Spec](../plugin/ai-resource-import-plugin-spec.md).
Plugins produce artifacts and never write MCP storage directly. The MCP
resource operator applies artifacts through the current MCP facade, which
routes them to the canonical lifecycle after cutover.

The Console-only `GET /v3/console/ai/mcp/importToolsFromMcp` helper follows its
existing outbound-network policy: operators may disable it, private/local
targets require the operator allowlist, an endpoint cannot override the
`baseUrl` origin, and redirects are disabled.

## 8. Required Verification

Implementation PRs must cover at least:

- unchanged Config group/data-id coordinates and unchanged Tools/Resources
  bytes;
- the sole Direct Server Config extension and old-model unknown-field reads;
- draft through publish lifecycle, latest selection, old overwrite isolation,
  and storage deletion retry;
- `SYNCING` legacy visibility, idempotent asynchronous reconciliation, mixed
  member gating, zero-difference cutover, restart, and no fallback;
- multi-instance Direct materialization, projection retention at cutover,
  owner/hash cleanup isolation, and cleanup retry;
- ordinary Service Ref non-ownership;
- Versionless runtime publication, transport and Version binding validation,
  old/new Service merge, disconnect, reconnect, and redo;
- both default JSON and Jackson 3 client adapters; and
- equivalent Admin, Console, Maintainer SDK, Java SDK, Search, Import, and
  Registry-adaptor projections.

Asynchronous assertions use bounded polling of public behavior. They must not
depend on fixed sleeps or an internal task order.

## 9. Evolution And Deferred Work

The first migration does not define a multi-object MCP format for non-Config AI
Storage providers, multiple disjoint runtime ranges, forced deletion of every
connection-owned runtime publication, or Direct projection retirement.

Client HTTP API parity and reuse of Agent HTTP publisher heartbeat/renewal are
recorded follow-up work. They require a separate design after the canonical
management migration; this spec intentionally does not freeze their paths,
payloads, transport negotiation, or heartbeat intervals.

Upstream MCP tool, resource, transport, auth, and registry formats may evolve.
Such changes must preserve the canonical identity and ownership boundaries or
publish an explicit schema and migration revision.
