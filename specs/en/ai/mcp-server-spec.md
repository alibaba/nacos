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

This document defines the Nacos AI Registry contract for MCP Server resources.
The first migration hosts MCP management identity and Version governance in the
common AI Resource lifecycle while preserving the existing MCP serving and
discovery plane.

## 1. Scope And Contract Status

The first migration has two management-route states:

| State | Management route | Client and gateway serving route |
| --- | --- | --- |
| `SYNCING` | Historical MCP management remains authoritative while Resource and Version rows are reconciled. | Existing Manifest, Config, and Naming behavior is unchanged. |
| `LIFECYCLE_MANAGED` | The complete compatible management operation set uses the common AI Resource lifecycle for both reads and writes. | Existing Manifest, Config, and Naming behavior remains unchanged. |

`LIFECYCLE_MANAGED` is not a data-plane cutover. It does not make the
historical Manifest, Config objects, Direct Services, ordinary Service
references, or client-owned Runtime Services disposable projections.

Management route selection is resolved once per request against the complete
operation contract. A node must never route reads to lifecycle rows while
routing writes to the historical implementation, or expose any other
mixed-authority combination.

The following changes are explicitly outside this migration:

- adding an internal `McpEndpointKind` or
  `DIRECT/SERVICE_REF/RUNTIME_REF` persistence model;
- materializing Direct endpoint addresses into Version Server Config;
- replacing the current version-scoped Runtime Service with a versionless
  Service;
- adding `supportedTransports`, `versionRange`, or range-based MCP Runtime
  binding;
- retiring Direct persistent Naming Services or the historical Manifest; and
- changing frontend/backend, subscription, reconnect, redo, or heartbeat
  behavior.

Those items require a separate compatibility design and consumer migration
window.

## 2. Fact Ownership

The first migration uses the following ownership boundaries:

| Fact | Owner | Contract |
| --- | --- | --- |
| MCP management identity | `ai_resource` | `namespaceId + type=mcp + mcpName`. |
| Enabled state, owner, scope, labels, and working Version pointers | `ai_resource` | Common AI Resource metadata and lifecycle facts. |
| Version state, author, Pipeline state, and content pointer | `ai_resource_version` | Common AI Resource Version facts. |
| Server, Tools, and Resources payload | Existing MCP Config objects | Coordinates and bytes are preserved. |
| Published Version set and historical latest view | `mcp-server-versions` Manifest | Compatibility serving index that remains maintained. |
| Direct endpoint addresses | Existing persistent Naming Service and instances | Current Direct endpoint fact; not a downgrade projection. |
| Ordinary REF backend | User-owned Naming Service selected by `serviceRef` | MCP reads but does not own the referenced Service. |
| Frontend/backend mapping | Existing Server Config and endpoint query logic | `frontEndpointConfigList` behavior is unchanged. |
| Client Runtime endpoint | Existing client-owned Naming state | Service name, cluster, metadata, redo, and liveness are unchanged. |
| Search identity and index maintenance | `mcpName` and the shared asynchronous index service | Search is eventually consistent and is never an identity source. |

AI Resource hosts the MCP management lifecycle. It does not replace the current
MCP serving or discovery data plane.

## 3. Identity And AI Resource Mapping

### 3.1 Canonical Identity

The canonical Nacos management identity is:

```text
namespaceId + type=mcp + name=mcpName
```

`mcpName` is case-sensitive and immutable as an identity field. The MCP wire
protocol does not define a public MCP Server UUID. The official MCP Registry
uses a registry-scoped name and Version as public coordinates. Nacos therefore
uses its own Namespace to scope `mcpName`; it must not treat a runtime
`serverInfo.name` value as a globally unique or security-sensitive identity.

This conclusion is based on the current upstream contracts:

- the [MCP protocol schema](https://github.com/modelcontextprotocol/modelcontextprotocol/blob/main/schema/2026-07-28/schema.ts)
  exposes implementation name and Version but no MCP Server UUID;
- the [MCP Tools specification](https://github.com/modelcontextprotocol/modelcontextprotocol/blob/main/docs/specification/2026-07-28/server/tools.mdx)
  states that a self-reported server name is not guaranteed unique across
  Servers; and
- the [official Registry API](https://github.com/modelcontextprotocol/registry/blob/main/docs/reference/api/official-registry-api.md)
  and [current API types](https://github.com/modelcontextprotocol/registry/blob/main/pkg/api/v0/types.go)
  expose name and Version coordinates. Registry
  [migration 009](https://github.com/modelcontextprotocol/registry/blob/main/internal/database/migrations/009_separate_official_metadata.sql)
  removed earlier UUID columns in favor of the natural server-name and Version
  key.

The historical UUID-shaped `mcpId` remains an internal physical-storage alias
and a deprecated compatibility field. It does not participate in canonical
identity, authorization, visibility, labels, Search document identity, or
Runtime Naming identity.

The schema-version-1 Resource extension is:

```json
{
  "schemaVersion": 1,
  "mcpId": "4d7939c0-72ea-4ef4-b232-418d1e16b45c"
}
```

Its machine-readable contract is
[`mcp-resource-ext.schema.json`](../../schemas/ai/mcp/internal/v1/mcp-resource-ext.schema.json).

### 3.2 Resource Mapping

The Resource row maps MCP fields as follows:

| `AiResource` field | MCP mapping |
| --- | --- |
| `namespaceId`, `type`, `name` | Namespace, constant `mcp`, and `mcpName`. |
| `desc` | MCP description. |
| `status` | Historical `enabled=true` maps to `enable`; otherwise `disable`. |
| `owner` | Creating or importing operator; historical reconciliation uses `nacos`. |
| `scope` | Visibility default for new resources; historical reconciliation uses `PUBLIC`. |
| `bizTags` | Public MCP business tags, or an empty collection. |
| `ext` | `McpResourceExt` containing the internal `mcpId` alias. |
| `from` | Local creation, import source, or `legacy-mcp` reconciliation source. |
| `versionInfo` | Standard editing, reviewing, online-count, and label summary. |

Within one Namespace, exactly one effective `type=mcp` Resource may exist for
one `mcpName`. Because the current physical uniqueness includes `from`,
reconciliation must detect multiple same-name source rows and block completion
instead of choosing one silently.

### 3.3 Version Mapping

Each MCP Version has one `AiResourceVersion` row with exact identity:

```text
namespaceId + type=mcp + mcpName + version
```

Historical published Versions enter the lifecycle as `online`. New
management APIs use the common `draft`, `reviewing`, `reviewed`,
`online`, and `offline` states. Version strings remain unchanged.
Non-SemVer historical values remain valid exact identities within the shared
Version field limit. This migration does not introduce MCP Version ranges.

Runtime query still exposes only an enabled Resource and an online Version.
When Version is omitted, the query resolves the server-managed `latest`
label. Management reads may inspect every lifecycle state.

Latest selection follows the common lifecycle with this MCP compatibility
refinement:

- standard publish, force-publish, and online operations move `latest` to
  the target Version;
- a historical direct-online update may preserve the current valid pointer
  when its existing latest parameter requests that behavior; and
- deletion or offline of the current latest selects the greatest remaining
  online SemVer, then greatest numeric `vN`, then greatest stable
  case-sensitive string. If no online Version remains, `latest` is removed.

## 4. Physical Content And Storage Boundary

### 4.1 Preserved Coordinates

The migration preserves these Config groups and data ids:

| Content | Config group | Data id |
| --- | --- | --- |
| Published-Version Manifest | `mcp-server-versions` | `<mcpId>-mcp-versions.json` |
| Version Server | `mcp-server` | `<mcpId>-<version>-mcp-server.json` |
| Version Tools | `mcp-tools` | `<mcpId>-<version>-mcp-tools.json` |
| Version Resources | `mcp-resources` | `<mcpId>-<version>-mcp-resources.json` |

Historical reconciliation creates pointers only. It must not copy, move,
rewrite, or extend the Server, Tools, or Resources payload bytes. It must not
change any Naming Service or instance.

The Manifest remains a compatibility serving index for clients and gateways
that read Config and Naming directly. It is not the canonical management
identity or lifecycle store.

### 4.2 Version Storage Descriptor

`AiResourceVersion.storage` contains a schema-version-1 descriptor:

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

`serverKey` is required. `toolKey` and `resourceKey` are omitted when the
corresponding content is absent. The built-in provider splits only the first
two `:` separators and treats the remainder as the Config data id. It accepts
only the three MCP-owned groups above and must not become arbitrary
`namespace:group:dataId` access to user Config.

All keys use the provider persisted in the Version row. The initial migration
supports `nacos_config`. A multi-object MCP format for another AI Storage
provider requires a separate design. The machine-readable contract is
[`mcp-version-storage.schema.json`](../../schemas/ai/mcp/internal/v1/mcp-version-storage.schema.json).

### 4.3 Required Layering

The migration may reuse historical model conversion, JSON handling, Manifest
selection, endpoint, and Naming logic. It must move physical Config access
behind the following boundary:

```text
MCP lifecycle/application service
        -> MCP Version Storage / MCP Serving Manifest Storage
        -> AI Resource Storage router or Config implementation
```

Normative rules:

- MCP Version Storage loads, saves, and deletes Server, optional Tools, and
  optional Resources through the descriptor persisted in the Version row.
- MCP Serving Manifest Storage encapsulates reads, publication, and deletion
  of `mcp-server-versions`. The Manifest is a serving compatibility index,
  not an identity resolver.
- MCP lifecycle and operation services must not directly call Config CRUD for
  the four MCP Config groups.
- A service must not accept `mcpId`, compose a data id, and bypass the
  persisted Version descriptor.
- Direct, REF, and client Runtime Naming state does not enter the generic
  `AiResourceStorage` SPI. MCP-specific ownership cleanup participates in the
  common lifecycle deletion flow.

Preserving Config and Naming means preserving physical compatibility, not
preserving a service-to-Config layering violation.

## 5. Endpoint And Serving Compatibility

The public endpoint model and current resolution algorithm remain unchanged:

1. `frontEndpointConfigList` determines which frontend endpoint shape is
   returned to the caller.
2. A Direct fixed address remains represented by the current version-scoped
   persistent Naming Service and instances. Server Config retains its current
   `serviceRef`.
3. A REF continues reading the ordinary Naming Service selected by
   `serviceRef`; Nacos MCP does not own that Service or its instances.
4. A `BACKEND` frontend entry continues using the resolved backend endpoint
   directly.
5. In gateway proxy scenarios, the gateway is the frontend while
   `remoteServerConfig.serviceRef` still selects the real backend.
6. Client API endpoint registration continues using the current
   version-scoped Runtime Service, cluster, and instance metadata.
7. `subscribeMcpServer` continues polling the complete MCP query projection
   rather than subscribing directly to the underlying Naming Service.

A Direct persistent Service is current MCP data and an external-consumer
contract, not a downgrade projection. Offline removes a Version from the
serving Manifest but retains its content and Direct Service so it can be
brought online again.

Known gateway integrations, including Higress and Istio-based gateways, may
read this serving plane without calling an MCP-specific query API. Their
compatible flow is:

1. list `mcp-server-versions` Config entries;
2. read and watch `<mcpId>-mcp-versions.json`;
3. compose exact Server and Tools data ids from the published Version;
4. read `remoteServerConfig.serviceRef`;
5. query or subscribe to the referenced Naming Service; and
6. build the gateway frontend route while retaining the referenced backend.

Consequently, lifecycle hosting must not require those consumers to negotiate a
new Nacos ability or release merely to preserve existing discovery.

## 6. Lifecycle And Compatibility Facades

### 6.1 Standard Management Lifecycle

MCP uses the common draft, submit, review, publish, force-publish, redraft,
online, offline, label, and delete rules. Published content is immutable through
standard lifecycle APIs; changing it creates a new Version or follows the
allowed redraft transition.

The Admin prefix is `/v3/admin/ai/mcp`. Console mirrors the same relative
operations under `/v3/console/ai/mcp`. Exact routes are listed in the
[V3 HTTP API Surface](../http-api/v3-api-surface.md).

These standard routes are enabled only after management authority reaches
`LIFECYCLE_MANAGED`. Embedded and standalone Console use the same application
service directly. A Console-only remote deployment must use the typed
Maintainer Version-management transport and must not fall back to a legacy
write. The
transport maps typed request objects onto the same form/query Admin routes; it
does not introduce a second JSON-body HTTP contract.

The two bundled Console frontends intentionally have different compatibility
roles during this release window. The legacy `console-ui` remains on the
historical direct-online create and update routes. `console-ui-next` creates or
replaces drafts only through the standard lifecycle routes and exposes the
valid submit, publish, force-publish, redraft, online, offline, draft-delete,
label, and Visibility actions for the selected exact Version. Before
`LIFECYCLE_MANAGED`, the next UI may retain historical reads for diagnosis but
must disable lifecycle mutations and must not fall back to a historical write.

`McpMaintainerService` exposes Version-management methods with
explicit-namespace and default-namespace overloads. New draft creation and
replacement reuse the established `createMcpServer` and `updateMcpServer`
names through `McpServerDraftRequest` overloads. Exact reads use
`listMcpServerVersions` and `getMcpServerVersion`; exact Version transitions
use `McpServerVersionCommand`, and label replacement uses
`McpServerLabelsUpdateRequest`. Public method and model names describe the user
operation and must not expose the internal Lifecycle hosting mechanism. These
models do not carry
top-level `namespaceId` or `mcpId` selectors; the namespace is a separate
method argument and the canonical resource identity is `mcpName`. Historical
`id` or `namespaceId` fields inside the reused `McpServerBasicInfo` payload are
compatibility content only: the server ignores them for identity resolution
and applies the internal coordinates resolved from the lifecycle target.

The legacy Maintainer detail and direct-online create/update methods are
deprecated since 3.3.0 and planned for removal in 4.0.0. Their Javadoc must
identify the exact typed Version read or draft-submit-publish replacement.
Cross-resource list/search and published-Version or full-Resource delete remain
outside this deprecation until equivalent lifecycle operations are defined.

Submit builds a `ResourceFilesPipelineContext` with resource type `MCP` and
the preserved Server, optional Tools, and optional Resources payloads. If no
enabled Pipeline node supports MCP, submit follows the common direct-publish
path. Otherwise the Version enters `reviewing`; an approved or rejected
callback moves it to `reviewed`, and only an explicit approved publish updates
the online lifecycle state and compatibility Manifest. Force-publish remains
the audited Pipeline bypass.

### 6.2 Historical Direct-Online Facades

Existing Admin, Console, Maintainer SDK, Java Client SDK, and gRPC wire shapes
remain compatible and map to the lifecycle application service:

| Historical operation | Managed behavior |
| --- | --- |
| Create or release MCP | Create Resource and Version, take the Version online immediately, set latest according to the historical contract, and return the historical response shape. |
| Update with a new Version | Create an online Version and apply the historical latest parameter. |
| Update an existing exact Version | Compatibility-only same-Version overwrite through MCP Storage; preserve lifecycle state and historical latest behavior. |
| Query | Return the same serving projection and response shape as before migration. |
| Delete exact Version | Stop Manifest exposure, clean MCP-owned Direct state and Version content through the managed deletion flow, then remove the Version row. |
| Delete MCP | Stop Manifest exposure, run common Resource-with-Versions deletion with MCP storage cleanup, then remove metadata rows. |

The same-Version overwrite is an audited compatibility exception. Standard
lifecycle APIs must never reuse it.

A compatibility overwrite with `isPublish=false` must retain the current
Version lifecycle status, Manifest presentation, latest pointer, and existing
release metadata. The overwritten Version content becomes the published
presentation only after a later explicit publish.

A compatibility direct-online create may temporarily use the Resource
`editingVersion` pointer as its in-flight retry marker. It clears that pointer
only after the Manifest is reread and verified. A completed or intentionally
offline Resource has no such marker and remains a duplicate create conflict.

### 6.3 Draft And Publish Ordering

A draft write uses this order:

1. resolve or generate the internal `mcpId`;
2. save Server and optional Tools/Resources through MCP Version Storage;
3. create or update the `draft` Version row with the same descriptor; and
4. update the Resource working pointer.

A draft is not added to the historical Manifest.

Deleting an exact draft uses MCP Storage cleanup first, removes the Version row
after cleanup succeeds, and clears the matching Resource working pointer last.
The retained pointer is the retry anchor if storage or row deletion is
interrupted. A retry after the row has already been removed clears that pointer
without requiring the deleted content descriptor.

Publish or online uses this order:

1. load and validate Version content through MCP Version Storage;
2. validate the existing Direct or REF endpoint facts without rewriting them;
3. transition the Version and update server-managed labels;
4. rebuild the compatibility Manifest from the complete online Version set;
5. publish the Manifest last through MCP Serving Manifest Storage; and
6. reread and verify the serving view before returning success.

The online lifecycle row is the durable desired state. If Manifest publication
or verification fails, the operation reports failure while preserving that row;
an idempotent retry or managed reconciler rebuilds the missing serving
projection. Search indexing is scheduled only after the business mutation and
never determines publish success.

### 6.4 Offline And Delete

Offline first converges the Version to the durable `offline` lifecycle state,
then rebuilds and verifies the Manifest serving view without that Version. It
does not implicitly disable the Resource, and retains Server/Tools/Resources
content and the Direct persistent Service. If Manifest convergence fails, the
operation reports failure while the retained offline row gives retry and
reconciliation an unambiguous target.

Version deletion:

1. loads and retains the Version storage descriptor;
2. converges the Version to `offline`, repairs labels, and removes and verifies
   its Manifest exposure;
3. invokes the MCP-specific cleanup hook for Direct state owned by that
   Version;
4. deletes Server/Tools/Resources through MCP Version Storage;
5. deletes the Version row only after all physical cleanup succeeds; and
6. schedules asynchronous Search maintenance.

Full Resource deletion:

1. resolves and authorizes the canonical Resource by name or a deprecated
   compatible ID and loads every Version descriptor;
2. disables the Resource and converges its Versions to `offline` so lifecycle
   rows durably express the non-serving target;
3. deletes and verifies the serving Manifest so gateways stop discovering it;
4. calls the common Resource-with-Versions deletion flow with the MCP storage
   deleter;
5. for every Version, the deleter validates the descriptor, cleans MCP-owned
   Direct state, and deletes Resources, Tools, and Server content through
   Storage; and
6. removes Resource and Version rows only after every callback succeeds.

Any Manifest, endpoint, or content cleanup failure reports failure and
preserves the disabled/offline Resource and Version rows plus storage
descriptors required for retry. Those lifecycle states are also the durable
recovery intent, so no separate MCP operation journal or Manifest tombstone is
required. An ID-only retry still resolves through `AiResource.ext`. An ordinary
REF Service and client-owned Runtime instances retain their existing ownership
and are not deleted with the MCP Version.

## 7. Deprecated `mcpId` Compatibility

### 7.1 Supported Uses

`mcpId` remains necessary to:

- compose the existing Config data ids;
- let Version and Manifest Storage locate historical Config;
- preserve existing Admin, Console, Maintainer, Client model, event, and
  response shapes; and
- preserve direct Config/Naming consumers.

It must not become the identity of a new API, Search document, authorization
rule, visibility rule, label, or lifecycle operation.

### 7.2 Management Resolution

New lifecycle APIs accept `namespaceId + mcpName (+ version)` and do not add
an `mcpId` parameter. Existing Admin, Console, and Maintainer HTTP paths that
already accept ID-only input remain compatible:

- name-only performs an exact `AiResource` lookup by Namespace,
  `type=mcp`, and name;
- name plus ID performs the exact name lookup and verifies
  `ext.mcpId` matches;
- ID-only pages the current Namespace's `type=mcp` Resource rows, parses
  `ext.mcpId`, and requires exactly one match; and
- missing, malformed, duplicate, or conflicting aliases return a controlled
  parameter or integrity error.

The protocol filter authenticates the request first using the existing wire
contract. For ID-only input, the lifecycle locator then resolves the canonical
Resource and, before any content read or mutation, repeats identity and
authority validation against that exact canonical name. The path subsequently
applies the same Visibility and lifecycle operation as name-based input. This
order avoids unauthenticated alias enumeration while preventing an empty wire
name from bypassing canonical authorization. ID lookup must not query the
Search index, Manifest, Config, or the historical MCP in-memory index. No new
table, column, or JSON index is introduced for this low-frequency deprecated path.
The historical index may continue serving wholly historical management paths
while `SYNCING`; after `LIFECYCLE_MANAGED`, no management correctness path
depends on it.

Existing create or release responses and existing DTOs continue returning
their ID fields. Existing legacy-only custom UUID input is not expanded.
Removal of `mcpId` requires a later migration of physical Config coordinates
and direct consumers; deprecation does not authorize removal in this phase.

### 7.3 gRPC Field Distinction

Three wire fields have different compatibility status:

1. the top-level `AbstractMcpRequest.mcpId`, flattened into current MCP
   requests, remains ignored and deprecated; handlers do not add ID lookup and
   retain their current name requirements;
2. nested `McpServerBasicInfo.id` remains an active compatibility input or
   model field where current requests use it, and name/ID inputs must agree;
   and
3. `ReleaseMcpServerResponse.mcpId` remains an active compatibility output.

Field numbers and wire shapes remain unchanged. A separate SDK-proto change may
add a deprecation option to the dormant top-level field, but the lifecycle
migration does not depend on that release.

## 8. Historical Reconciliation And Managed Cutover

### 8.1 Marker And Lease

There is no operator-selected storage mode. The one-way management completion
marker is an internal Config object:

```text
group  = nacos_internal
dataId = nacos.ai.mcp.resource.migration.v1
content = {"schemaVersion":1,"state":"LIFECYCLE_MANAGED","completedAt":<epochMillis>}
```

The permanent marker means management rows are completely hosted. It does not
authorize deletion or mutation of serving Config or Naming data. A renewable
cluster lease uses `nacos.ai.mcp.resource.reconciliation.lease.v1`. While the
system is still synchronizing, the task may persist non-authoritative
diagnostics at `nacos.ai.mcp.resource.reconciliation.progress.v1` with
`state=SYNCING`. Neither object is the completion marker. Losing the lease
stops the current writer without deleting MCP content.

### 8.2 Reconciliation

After the root `ApplicationReadyEvent`, a background task:

1. acquires and renews the cluster lease;
2. pages every Namespace and scans `mcp-server-versions` through Manifest
   Storage rather than trusting only the in-memory MCP index;
3. validates Server, optional Tools, and optional Resources through Version
   Storage;
4. idempotently upserts each historical Version as `online` with a descriptor
   pointing to existing content;
5. upserts the Resource last with name, internal ID, enabled state, latest,
   online count, and `from=legacy-mcp`;
6. schedules shared asynchronous Search reconciliation by canonical
   `mcpName`;
7. detects missing content, conflicting identity, duplicate source rows,
   invalid Versions, and pending deletion;
8. routes removed `legacy-mcp` rows through the common lifecycle
   delete/recovery flow without deleting independently created resources;
9. completes a zero-difference validation round; and
10. writes the completion marker only after every known cluster member supports
    managed writes and write-after-reconcile hooks.

The Version/Resource upsert phase creates pointers only. It never saves or
rewrites historical payloads and never mutates Naming. Until the canonical
name Search projector and the common lifecycle delete/recovery handlers are
available on every member, a `SYNCING` reconciler records Search backfill,
extra Version, and orphaned `legacy-mcp` work as blocking diagnostics. It must
not enqueue an ID-keyed Search task or directly delete Resource/Version rows,
payload Config, or Naming state. Such a partial synchronization can never
write the completion marker. Before the name-keyed projector is introduced,
the progress record keeps `searchBackfillPending=true` and
`managedCutoverReady=false` even when lifecycle rows have zero difference.

### 8.3 Writes During `SYNCING`

Historical management responses remain wholly historical while `SYNCING`;
partial Resource rows are not exposed as the management authority. A capable
node performs the current physical compatibility write through MCP Storage and
then invokes the same per-Resource reconciler. Periodic scanning repairs writes
from an older node. New lifecycle write APIs do not become available before
managed cutover. A mixed-version cluster remains `SYNCING`.

Lifecycle reconciliation is a secondary convergence step in this state. Its
failure is diagnosed and repaired by periodic scanning, but does not reinterpret
or roll back an already successful authoritative historical write.

The compatibility facade routes the complete read/write operation contract to
the historical implementation in this state. The permanent marker may switch
that complete contract to the lifecycle implementation only after every
managed operation and its recovery path are available; it never switches
individual methods independently.

Cutover requires:

- exactly one equivalent Resource for every historical Manifest;
- an equivalent Version row and correct descriptor for every historical
  Version;
- matching name, internal ID, enabled state, latest, online count, and Version
  set;
- no duplicate source rows, missing content, identity conflict, or pending
  delete;
- one final zero-difference round;
- every cluster member supporting MCP Storage, lifecycle facades,
  write-after-reconcile, and canonical-name Search tasks; and
- no managed MCP service path bypassing Storage for Config CRUD.

External gateways do not participate in this ability gate because their
serving contract does not change.

The marker is permanent and is not rolled back automatically. After it exists,
a Nacos member that lacks the managed-write capability must not serve MCP
management traffic, because an unhooked historical write could diverge the
lifecycle rows. This restriction does not create a new negotiation requirement
for external Config/Naming consumers.

## 9. Search, Import, And Adaptor Rules

MCP participates in generic AI Resource Search and the MCP-specific Search
facade through one shared index and Query Planner. Canonical Search
`resourceName` is `mcpName`, never `mcpId`.

The MCP Search projector follows the same complete compatibility operation
router as management traffic. While `SYNCING`, it projects the complete
historical view through MCP Storage by canonical name so partially reconciled
Resource rows cannot hide MCP Servers. After `LIFECYCLE_MANAGED`, that same
router loads the visible Resource, online Version, and content through the
persisted storage descriptor. The projector input and Search identity never use
`mcpId`; the `SYNCING` strategy may still resolve the internal compatibility
alias needed to read the unchanged Manifest and Config coordinates. It may
project public description, Tools, Resources, tags, protocols, and capabilities.
Credentials, runtime instances, and sensitive authentication metadata never
enter Search chunks.

Every successful create, update, publish, online, offline, delete,
enable/disable, label, or import mutation schedules a durable asynchronous
maintenance task by `namespaceId + type=mcp + mcpName`. Tasks may merge
successive updates and retry failures. Business requests do not wait for index
completion. Eventual Search state is never used for identity resolution,
authorization, visibility, or write correctness.

Historical backfill rebuilds name-keyed documents. Projection-version
reconciliation and orphan sweep remove historical ID-keyed documents and
tasks; the system must not retain two canonical Search identities.

External import uses the
[AI Resource Import Plugin Spec](../plugin/ai-resource-import-plugin-spec.md).
Plugins produce artifacts and never write MCP storage directly. The MCP
resource operator applies artifacts through the lifecycle application service
and MCP Storage while preserving the existing Manifest, Config, and Naming
serving outputs.

The Console-only `GET /v3/console/ai/mcp/importToolsFromMcp` helper keeps its
existing outbound-network policy: operators may disable it, private or local
targets require the operator allowlist, an endpoint cannot override the
`baseUrl` origin, and redirects are disabled.

The optional AI Registry adaptor retains its external response shape. This
management migration does not require adaptor consumers to negotiate a new
version.

## 10. API And SDK Boundaries

The first migration changes management implementation and later adds standard
management lifecycle operations:

- Admin and Console historical methods retain their request, response, error,
  and direct-online compatibility semantics while entering the same lifecycle
  service.
- Maintainer SDK binary signatures and historical overloads remain compatible;
  typed name/Version lifecycle methods may be added with the standard Admin
  semantics.
- Import converges on the lifecycle service and MCP Storage.
- The legacy Console UI retains its direct-online compatibility flow; the next
  Console UI uses lifecycle-only mutations after the corresponding APIs are
  available and remains read-only while authority is still `SYNCING`.

The first migration does not change:

- the Java Client `AiService` MCP public interface;
- Query, Release, or Endpoint gRPC wire layout and field numbers;
- Client endpoint registration/deregistration, subscription, reconnect, redo,
  or heartbeat;
- current Runtime Service names, clusters, or metadata;
- MCP Client HTTP APIs; or
- AI Registry adaptor response shapes.

Client HTTP parity with gRPC and reuse of Agent HTTP publisher
heartbeat/renewal remain separate follow-up work.

## 11. Tool Schema Compatibility

An MCP tool `outputSchema` is JSON Schema. Nacos preserves valid type unions,
including a nullable property such as `{"type":["string","null"]}`. Console
load/save and OpenAPI import must not narrow that union to one string type.

## 12. Required Verification

Implementation PRs must cover at least:

- exact Resource and Version mapping, including historical non-SemVer Version
  strings;
- name-only, name-plus-ID, and legacy ID-only resolution from Resource rows,
  protocol authentication followed by exact canonical re-authorization for
  ID-only input, and conflict handling;
- unchanged Manifest/Server/Tools/Resources coordinates and bytes;
- no Naming mutation during reconciliation and unchanged Direct, REF,
  frontend/backend, Runtime, subscription, reconnect, and redo behavior;
- all Server/Tools/Resources and Manifest Config access passing through MCP
  Storage rather than direct service Config CRUD;
- draft through publish lifecycle, historical same-Version overwrite isolation,
  latest selection, and Manifest-last publication recovery;
- offline retention of content and Direct Service;
- Version and full Resource deletion, common row-preservation on physical
  cleanup failure, retry by deprecated ID after Manifest removal, and no delete
  of ordinary REF or client Runtime state;
- idempotent asynchronous reconciliation, lease takeover, mixed-member gating,
  zero-difference completion, restart, and `LIFECYCLE_MANAGED` persistence;
- canonical name-keyed asynchronous Search, failure retry, backfill, and
  historical ID-keyed orphan cleanup;
- equivalent Admin, Console, Maintainer, Client, Import, Search, and adaptor
  compatibility projections; and
- both default JSON and Jackson 3 client adapters where existing MCP Java
  Client behavior is covered.

Asynchronous assertions use bounded polling of public behavior. They must not
depend on fixed sleeps, internal task order, or eventually consistent Search
for identity correctness.

## 13. Deferred Evolution

The following require later independent designs:

- MCP Client HTTP query, release, endpoint, subscription, and
  heartbeat/renewal parity;
- Endpoint-kind persistence and Direct endpoint materialization;
- retirement or version negotiation for the historical Manifest or Direct
  Services;
- versionless Runtime publication, multi-transport metadata, and SemVer range
  binding;
- non-Config multi-object MCP storage; and
- removal of the deprecated physical `mcpId` alias.

Upstream MCP tool, resource, transport, auth, and Registry formats may evolve.
Such changes must preserve the Nacos identity and ownership boundaries or
publish an explicit schema and migration revision.
