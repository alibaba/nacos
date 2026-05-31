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
protocols so existing MCP and Skill clients can discover Nacos-managed
resources without speaking Nacos v3 APIs directly.

## 1. Scope

The AI Registry adaptor owns protocol compatibility surfaces. It translates
Nacos AI Registry resources into external registry response shapes, including:

- MCP Server data exposed through MCP Registry v0-compatible read APIs;
- Skill data exposed through skills CLI and well-known discovery-compatible
  endpoints;
- protocol-specific pagination, search, response, and file-fetch behavior
  required by those ecosystems.

The adaptor does not own canonical AI resource identity, lifecycle, storage,
visibility, or publish rules. Those rules remain defined by the
[AI Registry Spec](ai-registry-spec.md), [MCP Server Spec](mcp-server-spec.md),
[Skill Spec](skill-spec.md), and related plugin specs.

External protocol references include the
[MCP Registry](https://modelcontextprotocol.info/tools/registry/),
[skills.sh documentation](https://skills.sh/docs), and the
[Agent Skills Specification](https://agentskills.io/specification). Nacos uses
these references for compatibility, not as ownership boundaries for its
canonical resource model.

## 2. Startup And Enablement

The adaptor runs as an additional Spring Boot web context with its own HTTP
port. It is disabled by default and starts only when at least one compatible
registry surface is explicitly enabled:

| Property | Default | Effect |
| --- | --- | --- |
| `nacos.ai.mcp.registry.enabled` | `false` | Enables MCP Registry-compatible endpoints. |
| `nacos.ai.skill.registry.enabled` | `false` | Enables Skill registry-compatible endpoints. |
| `nacos.ai.registry.port` | `9080` | HTTP port used by the adaptor context. |
| `nacos.ai.mcp.registry.port` | deprecated | Legacy fallback for the adaptor port. |

Users must opt in because the adaptor consumes an additional port and exposes
protocol shapes that are designed for community clients rather than Nacos
Admin, Console, or Client API consumers.

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
- future adaptor-level authentication must be compatible with the external
  protocol and must not silently change canonical Nacos v3 auth semantics.

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

## 6. Compatibility Rules

- The adaptor must prefer external protocol compatibility over Nacos v3 response
  conventions on adaptor paths.
- Canonical Nacos APIs remain the source of truth for management semantics.
- Community protocols evolve quickly. The adaptor may need breaking changes
  when MCP Registry, skills CLI, skills.sh, or well-known Skill discovery
  formats change.
- Compatibility behavior should be versioned or documented when an upstream
  protocol introduces incompatible field, pagination, authentication, or route
  changes.

## 7. Pending Issues

- Define a stable adaptor authentication model for operators who need to expose
  compatibility protocols without making data public.
- Track MCP Registry version changes and clarify whether future write APIs
  should be supported by the adaptor or remain intentionally out of scope.
- Track skills CLI and skills.sh protocol changes, including whether richer
  detail, audit, or authenticated API shapes should be supported.
- Define operational guidance for running the adaptor behind gateways and
  service meshes.
