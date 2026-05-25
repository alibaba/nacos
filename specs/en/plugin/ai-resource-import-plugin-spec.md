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
the AI Registry domain module. The `ai` module owns import APIs, source
resolution, validation, and resource operators; `plugin-default-impl` owns
default external source adapters and their preset source configuration.

## Concepts

| Concept | Meaning |
|---------|---------|
| Import source | Operator-defined source configuration identified by `sourceId`. |
| Importer | Plugin implementation selected by an import source. |
| Candidate | External resource summary returned during search, without full importable content. |
| Artifact | Fetched payload and metadata that can be applied by a resource operator. |
| Resource operator | Nacos domain service that validates and writes one resource type. |
| Dependency | Resource referenced by an imported artifact, such as a Skill requiring MCP tools. |

Import sources are part of Nacos server configuration or plugin configuration.
End users select a `sourceId`; they must not submit arbitrary endpoint URLs,
IP addresses, credentials, or registry base paths in import requests.

## Execution Mode

`ai-resource-import` is a configured single-service plugin type.

Multiple importer implementations may be loaded at the same time, for example
`mcp-registry`, `skills-well-known`, or an internal enterprise marketplace
importer. For each request, the AI import source manager resolves `sourceId` to
one enabled source, then selects the importer named by that source.

The importer returns candidates during search and fetches artifacts for selected
items during validate and execute. The AI Registry import manager then routes
each artifact to the resource operator for its `resourceType`.

```text
sourceId -> ImportSource(pluginName, resourceTypes, endpoint, limits, authRef)
         -> AiResourceImportService
         -> AiResourceOperator(resourceType)
```

## Source Configuration

An import source should include:

| Field | Requirement |
|-------|-------------|
| `sourceId` | Stable user-visible source id. |
| `pluginName` | Importer implementation name under `ai-resource-import`. |
| `resourceTypes` | Resource types supported by this source, such as `mcp` or `skill`. |
| `endpoint` | Operator-configured source endpoint or registry root. |
| `enabled` | Whether the source may serve import requests. |
| `authRef` | Optional reference to server-side credentials; secrets are not returned to users. |
| `connectTimeout` / `readTimeout` | Per-source network timeouts. |
| `maxPageCount` / `maxItemCount` | Pagination guards. |
| `maxArtifactSize` | Maximum fetched artifact size. |
| `properties` | Importer-specific non-secret options. |

The source manager must reject duplicate `sourceId` values and sources whose
importer plugin is not loaded or disabled.

For advanced deployments, explicit import sources may still be configured under
`nacos.ai.resource.import.sources[...]` and guarded by
`nacos.ai.resource.import.enabled=true`. Default importer presets are configured
under the plugin namespace and may be enabled independently through
operator-owned `nacos.plugin.ai.importer.*` properties.

## SPI

Import implementations are created by a builder.

| Builder method | Requirement |
|----------------|-------------|
| `importerType()` | Stable importer implementation name. |
| `build(properties)` | Build an import service with importer-owned properties. |

The import service implements:

| Service method | Requirement |
|----------------|-------------|
| `importerType()` | Runtime importer type. |
| `supportedResourceTypes()` | Resource types the importer can produce. |
| `search(context)` | Return a candidate page from the configured source with necessary metadata only. |
| `fetch(context, item)` | Fetch one selected artifact from the configured source. |

Source presets may be provided by an optional source provider SPI:

| Provider method | Requirement |
|-----------------|-------------|
| `loadSources(properties)` | Return enabled import sources derived from server configuration and trusted defaults. |

`context` contains namespace, resource type, source configuration, query,
cursor, limit, and importer options. It must not contain a user-provided
network endpoint.

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

For MCP, the initial operator may call the current `McpServerOperationService`
and related validation services, even though MCP is still backed by Config
records. When MCP later migrates to `ai_resource`, only the MCP operator should
change. Import plugins and unified import APIs must remain compatible.

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

The `mcp-registry` importer connects to an operator-configured MCP registry
endpoint. Search returns MCP Server summaries only, and fetch returns an
`MCP_DETAIL` artifact that can be written by the MCP resource operator.

The official MCP registry preset may be enabled with:

```properties
nacos.plugin.ai.importer.mcp.official.enabled=true
```

Unless overridden, this creates source id `mcp-official`, importer
`mcp-registry`, resource type `mcp`, and endpoint
`https://registry.modelcontextprotocol.io/v0/servers`. Operators may override
the preset source id, display name, endpoint, auth reference, timeouts, item
limits, and artifact size by using the same
`nacos.plugin.ai.importer.mcp.official.*` prefix.

All built-in source presets accept the following security opt-ins under their
own prefix. They default to `false` and should be enabled only by operators for
controlled private deployments:

| Property suffix | Meaning |
|-----------------|---------|
| `allow-http` / `allowHttp` | Allows a non-HTTPS source endpoint. |
| `allow-private-network` / `allowPrivateNetwork` | Allows localhost, loopback, link-local, multicast, or private-network source endpoints. |

The `skills-well-known` importer connects to an operator-configured Skill
marketplace or registry root. If the source endpoint is not already a
well-known path, the importer should first try `/.well-known/agent-skills` and
then fall back to `/.well-known/skills` for v0.1-compatible sources. If the
endpoint already ends with `/.well-known/agent-skills` or `/.well-known/skills`,
the importer should use that path directly.

The Skill well-known preset may be enabled with:

```properties
nacos.plugin.ai.importer.skills.well-known.enabled=true
nacos.plugin.ai.importer.skills.well-known.url=https://developers.cloudflare.com
```

This creates source id `skills-well-known`, importer `skills-well-known`, and
resource type `skill`. Operators may override the preset source id, display
name, auth reference, timeouts, item limits, and artifact size by using the same
`nacos.plugin.ai.importer.skills.well-known.*` prefix. The `url` property is
required when this preset is enabled.

The importer must support both Skill well-known discovery versions:

- v0.1.0 or legacy sources, identified by an absent `$schema` field or the
  `https://schemas.agentskills.io/discovery/0.1.0/schema.json` schema URI;
- v0.2.0 sources, identified by the
  `https://schemas.agentskills.io/discovery/0.2.0/schema.json` schema URI.

For v0.1.0 or legacy sources, `index.json` uses a file list for each Skill:

```json
{
  "skills": [
    {
      "name": "demo-skill",
      "description": "Demo skill",
      "files": [
        "SKILL.md",
        "docs/guide.md"
      ]
    }
  ]
}
```

Search may return only `name`, `description`, and non-secret metadata. Fetch
downloads the selected Skill files from `{wellKnownBase}/{skillName}/{file}`,
validates path safety, assembles a standard Skill ZIP artifact, and passes it to
the Skill resource operator so it is applied through the normal Skill upload or
draft lifecycle.

For v0.2.0 sources, `index.json` uses artifact references:

```json
{
  "$schema": "https://schemas.agentskills.io/discovery/0.2.0/schema.json",
  "skills": [
    {
      "name": "demo-skill",
      "type": "skill-md",
      "description": "Demo skill",
      "url": "/.well-known/agent-skills/demo-skill/SKILL.md",
      "digest": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    },
    {
      "name": "archive-skill",
      "type": "archive",
      "description": "Demo archive skill",
      "url": "/.well-known/agent-skills/archive-skill.tar.gz",
      "digest": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
  ]
}
```

Search must not download artifact content. It may expose only `name`,
`description`, `type`, `url`, `digest`, schema version, and other non-secret
metadata needed by the console. Fetch must resolve `url` against the index URL,
download the selected artifact server-side, verify the `sha256` digest, and
convert the artifact into the standard Nacos Skill ZIP boundary. The built-in
importer must support `skill-md` single-file artifacts and `archive` artifacts
packaged as ZIP, TAR, TAR.GZ, or TGZ. Archive extraction must validate path
safety, limit file count and decompressed size, and reject unsupported archive
formats before the artifact is handed to the Skill resource operator.

The `skills-sh` importer connects to the operator-configured skills.sh API root.
It follows the skills.sh CLI discovery flow: search uses
`GET {endpoint}/api/search?q={query}&limit={limit}` and returns candidate
summaries only. If the user query is blank, the importer should use `skill` as
the default query. If the trimmed user query is one character, the importer
should reject the request locally because skills.sh requires at least two query
characters. Fetch resolves the selected candidate's `source` and `skillId` to
`GET {endpoint}/api/download/{owner}/{repo}/{skillId}`, validates the returned
file paths, assembles a standard Skill ZIP artifact, and passes that artifact
to the Skill resource operator.

The skills.sh preset may be enabled with:

```properties
nacos.plugin.ai.importer.skills.skills-sh.enabled=true
```

Unless overridden, this creates source id `skills-sh`, importer `skills-sh`,
resource type `skill`, and endpoint `https://skills.sh`. Operators may override
the preset source id, display name, endpoint, auth reference, timeouts, item
limits, and artifact size by using the same
`nacos.plugin.ai.importer.skills.skills-sh.*` prefix.

Search metadata may expose only non-secret values such as the skills.sh page
URL, the GitHub repository URL, repository source, skill id, and install count.
Fetch source metadata may additionally include the download snapshot hash.
Fetch must set `sourceMetadata.artifactUrl` to the corresponding skills.sh page
URL so imported Skill resources record a concrete external source instead of
`local`.

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

The compatibility endpoints are disabled by default. Operators may reopen them
temporarily with `nacos.ai.resource.import.legacy-mcp-api-enabled=true` while
clients migrate to `/v3/{admin|console}/ai/import/*`.

For legacy `importType=url`, the request must not use a user-provided URL as a
network target by default. It may be interpreted as a `sourceId` when it matches
an enabled source. Otherwise the request should fail with a migration message.
Legacy direct URL import may only be enabled by explicit operator configuration
for controlled deployments by setting
`nacos.ai.resource.import.allow-user-url=true` together with the legacy API
switch.

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
- source requests must enforce connect timeout, read timeout, response size,
  page count, and artifact size limits. Built-in importers should cap each HTTP
  response by the source `max-artifact-size` unless a stricter per-protocol
  limit applies;
- fetched Skill packages must not execute scripts during import, query, or
  download;
- importer plugins must not leak secrets in API responses, trace events, or
  logs.

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
