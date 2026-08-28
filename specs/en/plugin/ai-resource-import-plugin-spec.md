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

# AI Resource Import Plugin Spec

## Scope

The AI resource import plugin type lets Nacos import AI resources from
operator-configured external registries or marketplaces. It is intended for MCP
Server, Skill, and future AI resource types that need external discovery and
conversion before they enter the Nacos AI Registry governance flow.

An import plugin owns only the external source protocol and conversion from that
source into a Nacos import artifact. It does not own Nacos resource identity,
authorization, visibility, storage, version lifecycle, publish pipeline, or
trace behavior. Those rules remain owned by the
[AI Registry Spec](../ai/ai-registry-spec.md), resource-type specs, and the
resource operator selected by the AI Registry domain.

The plugin type is exposed to the core plugin manager as `ai-resource-import`.
Common plugin lifecycle and state rules are defined by the
[Nacos Plugin Spec](plugin-spec.md).

The SPI contract should live in the plugin system, for example in the
`plugin/ai` module, consistent with AI storage, visibility, and other plugin
types. Nacos should allow users to extend importer sources through the plugin
mechanism, such as enterprise Skill marketplaces, private MCP registries, or
Git indexes. Resource operators are not user extension plugins; in the first
stage they should be built into the `ai` module and write resources through the
current Nacos domain services.

Default importer implementations should live in `plugin-default-impl`, not in
the AI Registry domain module. The `ai` module owns import APIs, plugin routing,
validation, and resource operators; `plugin-default-impl` owns default external
source adapters and their configuration definitions.

## Concepts

| Concept | Meaning |
|---------|---------|
| Managed importer | Stable Builder plugin identified by `pluginName`; one implementation represents one external source. |
| Import service | Request-scoped protocol adapter built from one immutable Builder configuration snapshot. |
| Candidate | External resource summary returned during search, without full importable content. |
| Artifact | Fetched payload and metadata that can be applied by a resource operator. |
| Resource operator | Nacos domain service that validates and writes one resource type. |
| Dependency | Resource referenced by an imported artifact, such as a Skill requiring MCP tools. |

The existing API field `sourceId` is the managed `pluginName`. The existing API
field `pluginName` remains importer/protocol metadata for compatibility with
the Console. End users select a `sourceId`; they must not submit arbitrary
endpoint URLs, IP addresses, credentials, or registry base paths in requests.

## Execution Mode

`ai-resource-import` is a routed managed plugin type.

Multiple Builder implementations may be loaded at the same time, for example
`mcp-official`, `mcp-registry-protocol`, `skills-well-known`, or an internal
enterprise marketplace importer. For each request, the domain manager resolves
`sourceId` directly to one enabled Builder.

The importer returns candidates during search and fetches artifacts for selected
items during validate and execute. The AI Registry import manager then routes
each artifact to the resource operator for its `resourceType`.

```text
sourceId(managed pluginName)
  -> AiResourceImportServiceBuilder(current configuration snapshot)
  -> request-scoped AiResourceImportService
  -> AiResourceOperator(resourceType)
```

## Managed Configuration

The module switch is:

```properties
nacos.plugin.ai-resource-import.enabled=true
```

The legacy `nacos.ai.resource.import.enabled` key is an alias. The standard key
wins whenever it is present. The default is `true`; only an explicit `false`
disables AI Resource Import.

Each implementation uses the standard plugin state key:

```properties
nacos.plugin.ai-resource-import.{pluginName}.enabled=true
```

Each configurable item uses:

```properties
nacos.plugin.ai-resource-import.{pluginName}.{itemKey}=value
```

One `pluginName` represents exactly one source. Nacos does not support cloning
the same managed implementation into multiple endpoint instances through
configuration. A deployment that needs another fixed source should provide
another Builder with a distinct `pluginName`.

The old `nacos.ai.resource.import.sources[N].*` model, old Source model, and old
Source Provider SPI are removed. There is no automatic migration because one
indexed importer could previously create multiple source instances.

## SPI

The Builder is the stable managed plugin and implements `PluginConfigSpec`.

| Builder method | Requirement |
|----------------|-------------|
| `pluginName()` | Stable managed plugin name and API `sourceId`. |
| `importerType()` | Compatibility importer/protocol metadata returned in the API `pluginName` field. |
| `displayName()` / `description()` | Current display metadata from the accepted configuration snapshot. |
| `supportedResourceTypes()` | Resource types produced by this source. |
| `getConfigDefinitions()` | All configurable items owned by this implementation. |
| `applyConfig(config)` | Atomically replace the immutable effective configuration snapshot. |
| `build()` | Build one request-scoped service from one snapshot; it accepts no extra properties. |

The import service implements:

| Service method | Requirement |
|----------------|-------------|
| `search(context)` | Return a candidate page from the configured source with necessary metadata only. |
| `fetch(context, item)` | Fetch one selected artifact from the configured source. |
| `close()` | Release request-scoped resources; the default implementation may be a no-op. |

`context` contains namespace, resource type, query, cursor, limit, and importer
options. It does not carry source configuration or a user-provided endpoint.

A Builder instance is discovered once, registered with the unified
`PluginManager`, restored to its persisted state, resolved through the standard
configuration source chain, and applied before it is exposed for import
requests. Search creates one service for the request. Validate and execute each
create one service and reuse it for all selected items, then close it in a
`finally` block.

`search` should be side-effect free and must not return MCP tools, Skill package
content, secrets, or any other full importable payload. `fetch` may call the
external source and return bytes or structured payload, but it must not write
Nacos resources.

## Import Artifact

An artifact should include:

| Field | Meaning |
|-------|---------|
| `resourceType` | Target Nacos AI resource type. |
| `externalId` | Source-specific stable id. |
| `name` | Candidate Nacos resource name, if known. |
| `version` | Candidate version, if known. |
| `description` | Resource description. |
| `payloadKind` | Payload shape, such as `MCP_DETAIL`, `SKILL_ZIP`, or `JSON`. |
| `payload` | Fetched payload bytes or structured data. |
| `dependencies` | Optional referenced resources. |
| `sourceMetadata` | Non-secret source metadata for trace and diagnostics. |

The artifact is an import boundary object, not a persisted resource model. The
resource operator converts it into the current storage and lifecycle model.

## Resource Operators

Resource operators live in the AI Registry domain, not in the import plugin.
They validate and write artifacts through the resource type's current service
layer.

For MCP, the operator calls the current `McpOperationService` compatibility
application contract and related validation services. While lifecycle
reconciliation is `SYNCING`, that complete contract uses the historical
strategy and immediately reconciles successful writes. After the atomic
cutover it uses the canonical lifecycle strategy, MCP Version Storage, and
canonical name-keyed asynchronous Search tasks. Import plugins and unified
import APIs remain unchanged across the cutover and must not call the removed
Config-backed `McpServerOperationService` directly.

For Skill, the operator should preserve the Skill package boundary and write
through the Skill upload or draft lifecycle APIs. After a successful import, if
the artifact contains `sourceMetadata.artifactUrl`, the Skill operator should
record that URL as the imported resource source (`ai_resource.c_from`). If
`artifactUrl` is absent, it should fall back to `sourceMetadata.source`.

Skill conflict handling follows the AI resource working-version lifecycle:

- If the Skill does not exist, import creates a new draft.
- If the Skill exists and has no editing or reviewing version, import creates
  the next draft version.
- If the Skill has an editing or reviewing version, validation returns a
  working-version conflict. Execute must skip the item unless
  `overwriteExisting=true`; with overwrite enabled, the current editable draft
  may be replaced or a new draft may be created according to the Skill service
  lifecycle.

## Built-in Importers

The default built-in importers are delivered by the
`nacos-default-ai-importer-plugin` module in `plugin-default-impl`.

| Managed pluginName | API importer type | Resource | Endpoint | Default state |
|--------------------|-------------------|----------|----------|---------------|
| `mcp-official` | `mcp-registry` | `mcp` | Fixed official MCP Registry endpoint | enabled |
| `mcp-registry-protocol` | `mcp-registry` | `mcp` | Required operator configuration | disabled |
| `skills-sh` | `skills-sh` | `skill` | Fixed `https://skills.sh` | enabled |
| `skills-well-known` | `skills-well-known` | `skill` | Required operator configuration | disabled |

The fixed built-ins keep their current Console-facing metadata:

- `mcp-official`: display name `Official MCP Registry`, description
  `Import MCP servers from the official MCP registry.`;
- `skills-sh`: display name `skills.sh`, description
  `Import Skills from skills.sh.`.

The common effective configuration is:

| Item key | Scope | Applies to | Meaning |
|----------|-------|------------|---------|
| `endpoint` | `RESTART` | configurable endpoint implementations | Registry or marketplace root. |
| `allow-http` | `RESTART` | configurable endpoint implementations | Allow non-HTTPS targets. |
| `allow-private-network` | `RESTART` | configurable endpoint implementations | Allow local or private targets. |
| `display-name` | `RUNTIME` | all built-ins | API and Console display name. |
| `description` | `RUNTIME` | all built-ins | API and Console description. |
| `max-item-count` | `RUNTIME` | all built-ins | Maximum request result/file count, default `500`. |
| `max-artifact-size` | `RUNTIME` | all built-ins | Maximum response/artifact bytes, default `10485760`. |

Fixed endpoint implementations do not expose `endpoint`, `allow-http`, or
`allow-private-network` definitions and do not accept old endpoint overrides.
Their source identity and endpoint are part of the implementation contract.

For example, an operator-configured MCP Registry source uses:

```properties
nacos.plugin.ai-resource-import.mcp-registry-protocol.enabled=true
nacos.plugin.ai-resource-import.mcp-registry-protocol.endpoint=https://registry.example.com/v0/servers
```

An operator-configured Skill well-known source uses:

```properties
nacos.plugin.ai-resource-import.skills-well-known.enabled=true
nacos.plugin.ai-resource-import.skills-well-known.endpoint=https://skills.example.com
```

The MCP Registry implementation returns summaries during search and an
`MCP_DETAIL` artifact during fetch.

The Skill well-known implementation supports discovery schema v0.1.0 and
v0.2.0. It tries `/.well-known/agent-skills/index.json` and then
`/.well-known/skills/index.json` when the configured endpoint is a registry
root. Search does not download artifact content. Fetch validates paths and
digests and converts `skill-md`, ZIP, TAR, TAR.GZ, or TGZ distributions into a
standard Skill ZIP artifact.

The skills.sh implementation searches
`GET /api/search?q={query}&limit={limit}` and downloads
`GET /api/download/{owner}/{repo}/{skillId}`. A blank query uses `skill`; a
one-character query is rejected. Returned paths and aggregate size are
validated before a Skill ZIP artifact is created.

Legacy `nacos.plugin.ai.importer.*` display, description, limits, state, and
configurable endpoint keys may be consumed as aliases for one migration
window. The server should emit a migration warning when an alias is used.
Legacy fixed-source endpoint overrides, `auth-ref`, source/global timeouts,
`max-page-count`, `block-private-network`, global defaults, and arbitrary
`properties.*` are removed because they were ineffective or conflict with the
managed identity model.

## API Flow

Nacos should expose unified Admin and Console import APIs:

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/v3/admin/ai/import/sources` | List enabled import sources. |
| `POST` | `/v3/admin/ai/import/search` | Search candidate summaries from a source. |
| `POST` | `/v3/admin/ai/import/validate` | Validate selected candidates and return conflicts, dependencies, and warnings. |
| `POST` | `/v3/admin/ai/import/execute` | Import selected candidates. |
| `GET` | `/v3/console/ai/import/sources` | Console source list. |
| `POST` | `/v3/console/ai/import/search` | Console search flow. |
| `POST` | `/v3/console/ai/import/validate` | Console validate flow. |
| `POST` | `/v3/console/ai/import/execute` | Console execute flow. |

All unified APIs must use standard v3 `Result<T>` response, error, and
authorization conventions.

Unified import APIs must follow the Nacos v3 form binding convention. Controller
methods should expose `*Form` parameters instead of direct request-model
`@RequestBody` contracts. Scalar fields may be submitted as query parameters or
`application/x-www-form-urlencoded` form fields. Complex import fields, such as
`selectedItems` and `options`, should be submitted as JSON string form fields
and converted by the form object into the internal request model.

The recommended browser flow is:

```text
list sources(resourceType)
  -> select sourceId
  -> search candidates by sourceId and query
  -> user selects candidates
  -> validate selected candidates
  -> show conflicts, dependency warnings, and overwrite options
  -> execute selected candidates
```

The browser must not select searched candidates by default. It may provide an
explicit select-all control, and users must still be able to deselect individual
candidates after selecting all. Import-all-valid actions, if present, must only
operate on candidates that the user explicitly selected and validated, including
candidates accumulated across multiple validation batches in the same source.

The browser must not receive full artifacts. MCP tools/specification, Skill zip
content, and other importable payloads may flow only among the server-side
Importer, Import Manager, and Resource Operator.

## Legacy MCP Import Compatibility

Existing MCP import APIs may remain during a compatibility window:

```text
POST /v3/console/ai/mcp/import/validate
POST /v3/console/ai/mcp/import/execute
```

The validate and execute endpoints should be routed through a compatibility
adapter into the unified import manager. They must not continue to grow as an
independent implementation.

`GET /v3/console/ai/mcp/importToolsFromMcp` is not part of external registry
import compatibility. It is a Console helper for building an MCP Server schema
from a user-owned MCP runtime endpoint and remains outside the AI resource
marketplace or registry import flow.

This helper causes the Console process to open a server-side network connection
to a request-selected MCP runtime. Public targets are allowed by default, while
private or local targets are rejected unless every such address resolved from
`baseUrl` matches
`nacos.console.ai.mcp.import.allowed-private-addresses`. Operators may disable
all outbound tool import with `nacos.console.ai.mcp.import.enabled=false`. The
request `baseUrl` must use HTTP or HTTPS. The `endpoint` parameter must remain a
relative URI and cannot replace the scheme or authority from `baseUrl`.
Redirects are not followed. Invalid private allowlist entries fail closed
instead of being ignored.

The compatibility endpoints are deprecated, remain available only through
Nacos 3.3.x, and are planned for removal in Nacos 3.4.0. They are disabled by
default. Operators may reopen them temporarily with
`nacos.core.api.compatibility.enabled=true` while clients migrate to
`/v3/{admin|console}/ai/import/*`.

The former `nacos.ai.resource.import.legacy-mcp-api-enabled` property is no
longer recognized. The shared compatibility switch also reopens other
explicitly gated deprecated v3 APIs, as defined by the
[Compatibility And Deprecation Spec](../design/compatibility-deprecation-spec.md).

For legacy `importType=url`, the request must not use a user-provided URL as a
network target by default. It may be interpreted as a `sourceId` when it matches
an enabled source. Otherwise the request should fail with a migration message.
Legacy direct URL import may only be enabled by explicit operator configuration
for controlled deployments by setting
`nacos.ai.resource.import.allow-user-url=true` together with
`nacos.core.api.compatibility.enabled=true`.

Legacy `importType=json` and `importType=file` may be mapped to built-in local
importers because they do not require server-side network access.

## Dependency Handling

Imported artifacts may reference other AI resources. A Skill may require MCP
tools or servers, for example.

Dependency handling is a reserved extension point and is not required for the
initial unified import implementation. Until resource types expose concrete,
versioned dependency descriptors, importers may leave `dependencies` empty and
the import manager should not require a `dependencyPolicy` request parameter.
Built-in importers must not infer, install, or recursively import hidden
dependencies.

When Nacos adds explicit AI resource dependency descriptors, the unified import
flow may introduce these dependency policies:

| Policy | Meaning |
|--------|---------|
| `IGNORE` | Keep dependency metadata but do not validate or link it. |
| `VALIDATE_ONLY` | Report whether matching resources exist in Nacos. |
| `LINK_EXISTING` | Link to existing matching resources when possible. |
| `IMPORT_SELECTED` | Import only dependencies explicitly selected by the user. |

The default should be `VALIDATE_ONLY` after dependency descriptors are
available. Automatic recursive import must not be the default because it expands
the supply-chain and authorization boundary.

## Security Requirements

The import flow must treat external sources as untrusted:

- users cannot submit arbitrary URLs, IPs, registry roots, or credentials;
- operator-configured HTTP sources should use HTTPS by default;
- non-HTTPS source endpoints must be rejected unless an operator-owned source
  configuration explicitly enables `allow-http`;
- localhost, loopback, link-local, multicast, and private-network source
  endpoint targets must be rejected unless an operator-owned source
  configuration explicitly enables `allow-private-network`;
- built-in importer HTTP requests must re-apply the same scheme and network
  policy to every derived request URL, including URLs discovered from indexes
  or search responses;
- built-in importer HTTP requests must resolve request hosts before sending and
  reject loopback, link-local, multicast, and private-network DNS results unless
  the source explicitly enables `allow-private-network`;
- redirects must be disabled or revalidated against the same safety policy;
- loopback, link-local, multicast, and private network targets should be blocked
  by default after DNS resolution;
- built-in requests must enforce fixed connection/read timeouts and the
  configured `max-item-count` and `max-artifact-size` limits. Each HTTP response
  must be capped by `max-artifact-size` unless a stricter protocol limit applies;
- fetched Skill packages must not execute scripts during import, query, or
  download;
- importer plugins must not leak secrets in API responses, trace events, or
  logs.

The Console MCP tool-import helper follows the separate public-target policy
and private exceptions described in the legacy MCP compatibility section even
though the helper is not an importer-plugin operation.

Deployments that intentionally import from private networks must opt in through
operator-owned configuration.

## Trace And Audit

Search, validate, and execute operations should emit trace or audit events that include:

- source id;
- importer type;
- resource type;
- candidate count and selected count;
- per-item success, skipped, or failed status;
- non-secret source metadata;
- operator identity and client address when available.

Trace behavior must follow the [Trace Plugin Spec](trace-plugin-spec.md).

## Evolution Notes

This plugin type is a conversion boundary. It should remain stable while the
storage implementation of individual resources evolves. In particular, MCP
import must continue to work across the migration from Config-backed records to
the standard AI resource model by changing the MCP resource operator rather
than each external importer.

The unified managed model is a breaking replacement for the short-lived
Importer/Source dual SPI introduced in the 3.2.x line. External implementations
must migrate to one `AiResourceImportServiceBuilder` that implements
`PluginConfigSpec`; the removed Source model and Source Provider SPI have no
compatibility adapter.
