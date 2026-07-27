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

# AI Registry Adaptor Spec

This document defines the contract for the `ai-registry-adaptor` module. The
adaptor exposes Nacos AI Registry resources through selected community registry
protocols so existing MCP, Skill, and ARD clients can discover Nacos-managed
resources without speaking Nacos v3 APIs directly.

## 1. Scope

The AI Registry adaptor owns protocol compatibility surfaces. It translates
Nacos AI Registry resources into external registry response shapes, including:

- MCP Server data exposed through MCP Registry v0-compatible read APIs;
- Skill data exposed through skills CLI and well-known discovery-compatible
  endpoints;
- Skill, Prompt, and MCP data exposed through Agentic Resource Discovery (ARD)
  search, explore, catalog, and artifact endpoints;
- protocol-specific pagination, search, response, and file-fetch behavior
  required by those ecosystems.

The adaptor does not own canonical AI resource identity, lifecycle, storage,
visibility, or publish rules. Those rules remain defined by the
[AI Registry Spec](ai-registry-spec.md), [MCP Server Spec](mcp-server-spec.md),
[Skill Spec](skill-spec.md), and related plugin specs.

External protocol references include the
[MCP Registry](https://modelcontextprotocol.info/tools/registry/),
[skills.sh documentation](https://skills.sh/docs), and the
[Agent Skills Specification](https://agentskills.io/specification), and the
[ARD Specification](https://agenticresourcediscovery.org/spec/). Nacos uses
these references for compatibility, not as ownership boundaries for its canonical
resource model.

## 2. Startup And Enablement

The adaptor runs as an additional Spring Boot web context with its own HTTP
port. It is disabled by default and starts only when at least one compatible
registry surface is explicitly enabled:

| Property | Default | Effect |
| --- | --- | --- |
| `nacos.ai.mcp.registry.enabled` | `false` | Enables MCP Registry-compatible endpoints. |
| `nacos.ai.skill.registry.enabled` | `false` | Enables Skill registry-compatible endpoints. |
| `nacos.ai.ard.enabled` | `false` | Enables ARD endpoints and their required AI resource search runtime. |
| `nacos.ai.registry.port` | `9080` | HTTP port used by the adaptor context. |
| `nacos.ai.mcp.registry.port` | deprecated | Legacy fallback for the adaptor port. |

Users must opt in because the adaptor consumes an additional port and exposes
protocol shapes that are designed for community clients rather than Nacos
Admin, Console, or Client API consumers.

Disabling ARD must not require PostgreSQL pgvector. AI resource search document
and chunk metadata use the main datasource, while the pgvector extension and
`ai_resource_search_embedding_pg` table are initialized exclusively through
`pg-ai-vector-schema.sql` in the PostgreSQL datasource used for embeddings.
This datasource may be the Nacos main datasource or a dedicated datasource;
the main `pg-schema.sql` must remain usable without pgvector.

## 3. Security Boundary

Adaptor endpoints must be treated as public-protocol compatibility endpoints.
They are not v3 Nacos APIs and must not require the v3 `Result<T>` response
envelope. Some community registry protocols are designed around public
discovery, or may not carry Nacos authentication information.

For this reason:

- the adaptor must remain disabled unless the operator intentionally exposes it;
- operators should deploy it behind trusted network controls, gateway
  authentication, TLS, rate limits, or other external protections when the
  deployment contains non-public data;
- adaptor endpoints should only expose resources that are suitable for the
  target community protocol;
- ARD endpoints participate in Nacos Open API authentication inside the
  adaptor web context while retaining ARD-specific error responses.

When Nacos authentication is enabled, explicit credentials on an ARD request
are always validated. A valid identity is propagated to canonical visibility
checks, so callers can discover private resources only when permitted. Rejected
credentials return HTTP 401 with `UNAUTHENTICATED`. If anonymous AI access is
enabled, a request without credentials uses the reserved anonymous identity and
can discover only public resources; otherwise missing credentials also return
HTTP 401. The request-context and authentication filters must be registered in
the independent adaptor web context because filters in the main server web
context are not inherited across sibling contexts.

## 4. MCP Registry Compatibility

When `nacos.ai.mcp.registry.enabled=true`, the adaptor exposes MCP
Registry-compatible read endpoints:

| Method | Path | Behavior |
| --- | --- | --- |
| `GET` | `/v0/servers` | Lists MCP servers with cursor, limit, search, and optional Nacos `namespaceId`. |
| `GET` | `/v0/servers/{name}/versions` | Lists versions for a server. |
| `GET` | `/v0/servers/{serverName}/versions/{version}` | Returns a specific server version. The special version `latest` is resolved by the underlying MCP service when supported. |

The response model follows the MCP Registry-style server list and server
response objects. Nacos maps MCP metadata, version information, packages,
icons, website, repository, tools, and endpoints into the registry response
shape. Frontend endpoints are preferred over backend endpoints when both are
available. Endpoint data is converted into registry `remotes` according to the
MCP front protocol, such as streamable HTTP or SSE.

`namespaceId` is a Nacos extension. If it is omitted, the adaptor may search
across namespaces in deterministic namespace order. This makes Nacos usable as
an internal MCP subregistry while keeping the canonical MCP resource model in
the [MCP Server Spec](mcp-server-spec.md).

The adaptor currently exposes read and discovery behavior. MCP authoring,
publishing, governance, and deletion remain Nacos Admin, Console, or Maintainer
SDK responsibilities.

## 5. Skill Registry Compatibility

When `nacos.ai.skill.registry.enabled=true`, the adaptor exposes Skill
discovery endpoints compatible with skills CLI and well-known registry usage:

| Method | Path | Behavior |
| --- | --- | --- |
| `GET` | `/registry/{namespaceId}/.well-known/agent-skills/index.json` | Returns the namespace Skill index in Agent Skills discovery v0.2.0 shape. |
| `GET` | `/registry/{namespaceId}/.well-known/skills/index.json` | Returns the namespace Skill index in legacy v0.1-compatible shape. |
| `GET` | `/registry/{namespaceId}/api/search` | Searches exportable skills and returns CLI-compatible search results. |
| `GET` | `/registry/{namespaceId}/.well-known/agent-skills/{skillName}/SKILL.md` | Returns the exported `SKILL.md`. |
| `GET` | `/registry/{namespaceId}/.well-known/skills/{skillName}/SKILL.md` | Alias for the exported `SKILL.md`. |
| `GET` | `/registry/{namespaceId}/.well-known/agent-skills/{skillName}.zip` | Returns an exported Skill archive for v0.2.0 `archive` entries. |
| `GET` | `/registry/{namespaceId}/.well-known/skills/{skillName}.zip` | Archive alias for clients that already resolved the legacy base path. |
| `GET` | `/registry/{namespaceId}/.well-known/agent-skills/{skillName}/**` | Returns exported text resources. |
| `GET` | `/registry/{namespaceId}/.well-known/skills/{skillName}/**` | Alias for exported text resources. |

The `/.well-known/agent-skills/index.json` endpoint is the primary Skill
well-known discovery surface. It must return a top-level `$schema` value of
`https://schemas.agentskills.io/discovery/0.2.0/schema.json`. Each entry must
include `name`, `description`, `type`, `url`, and `digest`. Nacos should use
`type=skill-md` when the Skill contains only `SKILL.md`, with `url` pointing to
`{skillName}/SKILL.md`. If the Skill has exported supporting text resources,
Nacos should use `type=archive`, with `url` pointing to `{skillName}.zip`.
`digest` is the SHA-256 digest of the raw artifact bytes in the
`sha256:{hex}` format. Nacos may include non-standard extension fields such as
the resolved latest `version`; clients must ignore unknown fields according to
the discovery protocol.

The `/.well-known/skills/index.json` endpoint remains a legacy compatibility
surface. It omits `$schema` and returns each Skill with a `files` array so
v0.1-compatible clients can continue to fetch `SKILL.md` and text resources
from `/{skillName}/{file}` paths.

The adaptor exports only skills that are suitable for public-style discovery:

- the Skill is enabled;
- the Skill scope is public;
- at least one online version exists;
- name and description are present;
- the latest label resolves to an available version;
- exported resources are text resources. Binary resources are not exported by
  the current compatibility surface.

The canonical package and lifecycle rules are defined by the
[Skill Spec](skill-spec.md). The adaptor only converts eligible Nacos Skills
into the community discovery shape.

## 6. Agentic Resource Discovery Compatibility

Nacos targets the ARD draft at upstream commit
[`5fa2f5aef790b478319f6a3b43adf4661b0ed0e0`](https://github.com/ards-project/ard-spec/commit/5fa2f5aef790b478319f6a3b43adf4661b0ed0e0).
The commit, rather than the draft's mutable version label, is the compatibility
baseline for the vendored OpenAPI, JSON Schema, and conformance fixtures.

When `nacos.ai.ard.enabled=true`, the adaptor exposes these ARD discovery
endpoints from the adaptor web context:

| Method | Path | Behavior |
| --- | --- | --- |
| `POST` | `/v3/ai/ard/search` | Searches the latest online Skill, Prompt, and MCP resources. |
| `POST` | `/v3/ai/ard/explore` | Returns facets for discoverable resources. |
| `GET` | `/v3/ai/ard/agents` | Lists discoverable resources with filters and pagination. |
| `GET` | `/v3/ai/ard/ai-catalog.json` | Returns a namespace-scoped catalog. |
| `GET` | `/v3/ai/ard/artifacts` | Returns the versioned artifact referenced by a catalog entry. |
| `GET` | `/.well-known/ai-catalog.json` | Returns the host-level ARD catalog. |

The global ARD switch starts the adaptor context even when the MCP and Skill
compatibility switches remain disabled.

### 6.1 Protocol Contract

ARD request and response models belong to this adaptor because they are
external protocol contracts rather than canonical Nacos client API models.
They must follow the pinned upstream schemas:

- list responses use `items`; they must not rename the field to `results`;
- search result `score` is an integer from 0 through 100;
- search result `source` is an absolute URI identifying the serving registry;
- ARD errors use exactly the protocol body `{ "errorCode": "...",
  "message": "..." }` and must not be wrapped in Nacos `Result<T>`;
- missing or rejected credentials use HTTP 401 with error code
  `UNAUTHENTICATED`;
- a generated `trustManifest`, when present, includes the required `identity`;
  the adaptor omits the manifest when no valid identity is configured;
- catalog, list, and search DTOs remain separate so search-only fields do not
  leak into catalog entries.

ARD controllers use protocol-specific exception handling. Nacos v3 controller
annotations and the Nacos API exception envelope do not apply to these paths.
Validation, not-found, access-denied, and unexpected failures must be translated
to the HTTP statuses and error codes defined by the pinned ARD OpenAPI.

### 6.2 Artifact Ownership

Every artifact URL generated by an ARD response points to an adaptor-owned
`/v3/ai/ard/artifacts` endpoint on the public adaptor base URL. It must not
point to a main-server controller unless an explicit, separately configured
main-server base URL is part of the contract.

`nacos.ai.ard.catalog.base-url` is the complete public adaptor base URL. The
implementation must not append the main Nacos server context path to it.

For Skill resources, the artifact endpoint returns the complete Skill archive,
including `SKILL.md` and its packaged resources, using
`application/agent-skills+zip`. Prompt and MCP artifacts use their
protocol-defined representations. A deployment with the default Nacos server
on port 8848 and the adaptor on port 9080 must work without gateway path
co-location.

### 6.3 Discovery Boundary

The AI module owns the protocol-neutral capability defined by the
[AI Resource Search Spec](ai-resource-search-spec.md). ARD is its only consumer
in this version, so `nacos.ai.ard.enabled` activates the required search
runtime without creating a separate operator-facing search switch or another
public API.

Visibility and current-version validation occur before the requested result
limit is applied. List and aggregation scans use bounded database batches.
Keyword and vector recall use the bounds defined by the AI resource search
specification and fail explicitly when a channel exceeds its configured
bound. The cursor identifies a stable resource anchor rather than a mutable
list offset.

The adaptor validates and parses ARD requests, invokes the AI resource search
service, and maps canonical results and aggregations to ARD DTOs. It must not
directly query search repositories or reimplement recall, lifecycle,
visibility, pagination, or aggregation rules. ARD-specific facet names and
values are translated to and from canonical aggregation fields.

Search accepts all federation values defined by the pinned OpenAPI: `auto`,
`referrals`, and `none`. An omitted value defaults to `auto`. Until an upstream
registry is configured, all three modes execute local search; `referrals`
returns an empty referrals array rather than rejecting the request.

The `GET /agents` filter parser supports single-quoted equality expressions
joined by `AND` and timestamp comparisons such as
`createdAfter > '2026-01-01T00:00:00Z'`. Parsing is quote-aware. Unsupported
operators, malformed quoting, unknown fields, and legacy delimiter syntax fail
with the ARD invalid-argument response instead of being partially interpreted.

Catalog identifiers use deterministic, injective, schema-safe encoding for
every Nacos-derived URN segment. Namespaces and resource names containing
spaces, slashes, Unicode, or punctuation must remain distinct and validate
against the pinned catalog schema.

The namespace catalog contains the complete eligible resource set and pages
through the canonical list service instead of silently stopping at 100
entries. The host-level well-known catalog remains registry-only.

Explore facets aggregate the complete eligible result set after visibility,
current-version, and request filtering. They must not be computed from one
page or a fixed candidate prefix. Protocol constants such as publisher,
source, and trust identity are added by the adaptor after canonical
aggregation.

### 6.4 Index Consistency

Canonical resource writes remain authoritative. Relational replacement of one
resource's search document and chunks is atomic: deleting the previous index
rows, inserting the new document, and inserting all chunks occur in one
datasource transaction.

Relational and vector indexes do not require a distributed transaction.
Instead, the AI module records an idempotent, durable resource-level indexing
task keyed by namespace, resource type, and resource name. The consumer
re-reads canonical state, replaces or deletes the relational index, then
converges the selected vector index. A task is complete only after both
configured indexes have converged.

Failures retain retry state, including attempt count, next retry time, lease,
and the last error. Periodic reconciliation detects missed lifecycle events and
independently verifies relational and vector state. Logging and swallowing an
indexing exception is not a consistency mechanism, and startup backfill alone
is not sufficient.

`ai_resource_search_index_task` stores the coalesced task revision and retry
state. Completion and retry updates are revision-conditional so a concurrent
canonical change cannot be lost when an older lease finishes. While vector
replacement is in progress, the relational document remains `pending`; search
only reads `enabled` documents. The consumer enables the document after the vector
provider confirms replacement. Reconciliation also compares the embedding
model and vector document count with the relational chunks, and schedules
missing, partial, stale, wrong-model, and orphaned indexes.

### 6.5 Conformance

The adaptor module keeps the pinned upstream OpenAPI and schemas as test
fixtures with provenance and license information. Automated tests validate
serialized responses against those fixtures. End-to-end coverage also runs the
pinned official conformance runner, or an equivalent test matrix derived from
it, against the actual adaptor web context.

At minimum, integration coverage includes list `items`, integer search score,
URI `source`, trust identity behavior, protocol error bodies, catalog schema,
and Skill ZIP retrieval when the main server and adaptor use separate ports.

## 7. Compatibility Rules

- The adaptor must prefer external protocol compatibility over Nacos v3 response
  conventions on adaptor paths.
- Canonical Nacos APIs remain the source of truth for management semantics.
- ARD compatibility changes require updating the pinned upstream revision,
  vendored fixtures, this specification, and conformance coverage together.
- Community protocols evolve quickly. The adaptor may need breaking changes
  when MCP Registry, skills CLI, skills.sh, or well-known Skill discovery
  formats change.
- Compatibility behavior should be versioned or documented when an upstream
  protocol introduces incompatible field, pagination, authentication, or route
  changes.

## 8. Pending Issues

- Define a stable adaptor authentication model for operators who need to expose
  compatibility protocols without making data public.
- Track MCP Registry version changes and clarify whether future write APIs
  should be supported by the adaptor or remain intentionally out of scope.
- Track skills CLI and skills.sh protocol changes, including whether richer
  detail, audit, or authenticated API shapes should be supported.
- Define operational guidance for running the adaptor behind gateways and
  service meshes.
