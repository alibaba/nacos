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

# Nacos Resource Model Spec

This document defines the shared resource model for Nacos. It is the semantic
source for HTTP APIs, gRPC APIs, SDKs, console workflows, persistence models, and
documentation.

## 1. Resource Envelope

Every Nacos resource should be described by a common envelope:

| Field | Requirement | Meaning |
| --- | --- | --- |
| `namespaceId` | Required for tenant-scoped resources | Isolation boundary for tenants, teams, and environments. |
| `resourceType` | Required when multiple resource kinds share a store or API surface | Domain type such as config, service, mcp, a2a, prompt, skill, or agentspec. |
| `resourceName` | Required | Stable name that identifies the resource within its scope and type. |
| `group` | Optional, domain-specific | Secondary grouping used by configuration and naming resources. |
| `version` | Optional, versioned resources only | Immutable or lifecycle-managed version identifier. |
| `labels` | Optional, versioned resources only | Named route aliases such as `latest`, `stable`, or custom labels. |
| `status` | Optional, domain-specific | Resource or version lifecycle state. |
| `metadata` | Optional | User or system metadata that does not change identity. |
| `visibility` | Optional, visibility-aware resources | Access scope and owner information. |

The historical Nacos data model is a tuple of namespace, group, and resource
name. This tuple remains valid for configuration and naming resources. AI
resources extend the same idea with `resourceType`, `version`, `labels`, and
visibility governance.

## 2. Namespace

Namespace is the primary isolation boundary. It separates tenants, teams,
environments, or other administrative scopes.

| Concept | Canonical name | Compatibility names |
| --- | --- | --- |
| Namespace id | `namespaceId` or `namespace` | `tenant`, `tenantId` |
| Display name | `namespaceShowName` | `tenantName` |
| Description | `namespaceDesc` | `tenantDesc` |

The default namespace id is `public`. Historical code may use `tenant` or
`tenantId`; new public APIs and specs should use `namespaceId` unless an
existing compatibility contract requires another name.

Cross-namespace operations are administrative operations and must use Admin API,
Console API, or Maintainer SDK surfaces.

## 3. Group

Group is a domain-specific secondary scope. It is required for configuration and
naming identity and defaults to `DEFAULT_GROUP` when omitted by supported
interfaces.

Group is not a universal Nacos resource field. AI resources should not invent a
group field unless a domain spec explicitly defines its semantics.

## 4. Resource Identity Rules

| Resource | Canonical identity | Notes |
| --- | --- | --- |
| Namespace | `namespaceId` | Root isolation resource. |
| Config | `namespaceId + group + dataId` | `tenant` is the historical storage/API name for namespace. |
| Naming service | `namespaceId + group + serviceName` | Internal grouped names may use `group@@serviceName`. |
| Naming cluster | `namespaceId + group + serviceName + clusterName` | Cluster is subordinate to a service. |
| Naming instance | `namespaceId + group + serviceName + clusterName + ip + port` | `instanceId` may be generated or provided as a runtime identifier. |
| Naming client | `clientId` or connection id | Runtime view, not a user-created domain resource. |
| AI resource | `namespaceId + resourceType + name` | Shared model for Prompt, Skill, AgentSpec, and similar governed resources. |
| AI resource version | `namespaceId + resourceType + name + version` | Version state is managed separately from resource metadata. |
| MCP Server | `namespaceId + name` plus optional `id` | `id` may represent registry/import identity; `name` remains the user-facing resource name. |
| A2A AgentCard | `namespaceId + registrationType + name + version` | Registration type participates in lookup semantics. |
| Prompt | `namespaceId + promptKey + version` | Legacy Prompt data may be mirrored as config data. |
| Skill | `namespaceId + name + version` | Labels map route names to versions. |
| AgentSpec | `namespaceId + name + version` | AgentSpec may reference other AI resources. |
| Plugin | `pluginType + pluginName` | Plugin state is server-control-plane metadata. |

Resource identity fields must not be treated as mutable metadata. Updating a
resource identity is a delete-and-create or clone operation unless a domain spec
defines a migration operation.

## 5. Config Resource

A config resource is identified by `namespaceId + group + dataId`.

Config owns:

- content and md5;
- config type;
- description, tags, and app name metadata;
- publish, CAS publish, delete, and query semantics;
- listener and fuzzy-watch semantics;
- gray/beta publication state;
- history, rollback, dump, and failover data.

`dataId` is the resource name for configuration. Config metadata such as
`appName`, `type`, `desc`, and `configTags` does not change identity.

Prompt has a legacy compatibility mapping to config storage with fixed group
`nacos-ai-prompt` and dataId `{promptKey}.json`; this mapping must not make
Prompt a normal config resource in new specs.

## 6. Naming Resource

A naming service is identified by `namespaceId + group + serviceName`.

Naming owns:

- service metadata and selector information;
- ephemeral or persistent service semantics;
- clusters and health-check configuration;
- instances with `ip`, `port`, `clusterName`, `weight`, `healthy`, `enabled`,
  `ephemeral`, `metadata`, and optional `instanceId`;
- subscribers, publishers, and client connection views;
- service and instance change events.

An instance is subordinate to a service. An instance must not be interpreted
without its service scope.

Ephemeral and persistent semantics affect lifecycle and consistency behavior.
They must be preserved across HTTP, gRPC, SDK, and storage models.

## 7. AI Resource

AI resources use a shared governance model:

- resource metadata row: `namespaceId + type + name`;
- version row: `namespaceId + type + name + version`;
- labels: name-to-version mappings, including `latest`;
- meta status: enable or disable;
- version status: draft, reviewing, reviewed, online, or offline;
- optional owner and visibility scope: `PUBLIC` or `PRIVATE`;
- optional publish pipeline state: in progress, approved, or rejected;
- optional business tags, extension metadata, source, and download count.

Published AI versions should be treated as immutable unless a domain spec
explicitly defines a safe mutation. Changes should create a new draft version,
pass review if required, and then publish or relabel.

### 7.1 MCP Server

MCP Server resources describe MCP-capable services. They may be created from
new MCP servers, imported external MCP servers, or existing HTTP/RPC services
adapted into MCP services.

MCP Server identity is based on `namespaceId + name`, with optional registry
`id`. MCP-specific metadata includes protocol, front protocol, repository,
packages, icons, website URL, local or remote server config, endpoint spec, tool
spec, status, and discovered capabilities.

Supported protocol values include stdio, SSE-style MCP, streamable HTTP, HTTP,
and Dubbo-compatible forms as defined by the AI domain.

### 7.2 A2A AgentCard

A2A AgentCard resources describe agent capabilities, skills, supported
interfaces, provider information, security schemes, signatures, and endpoint
metadata.

AgentCard lookup is scoped by namespace, registration type, agent name, and
version. Endpoint registration is subordinate to the owning agent card and may
be client-owned at runtime.

### 7.3 Prompt

Prompt resources are identified by prompt key and version within a namespace.
A Prompt contains template content, variables, md5, and version metadata.

Runtime Prompt lookup should resolve by explicit version, then label, then
`latest` according to the relevant API or SDK contract.

### 7.4 Skill

Skill resources represent reusable AI Agent capabilities. A Skill contains
metadata, instruction content, optional resources, versions, labels, visibility,
and publish pipeline metadata.

A Skill version moves through draft/review/publish/offline states. Only online
versions should be returned to runtime clients unless a management API
explicitly requests other states.

### 7.5 AgentSpec

AgentSpec resources assemble agent configuration by referencing prompts, skills,
MCP servers, A2A agents, or other required resources. AgentSpec identity follows
`namespaceId + name + version` and should use labels for runtime routing.

AgentSpec should reference other resources by stable identity and version or
label, not by storage implementation details.

## 8. Visibility And Ownership

Resources that support visibility must expose:

- `namespaceId`;
- stable resource name;
- resource type;
- scope, currently `PUBLIC` or `PRIVATE`;
- owner identity.

Visibility affects discovery, detail viewing, download, and write operations.
It complements authorization and must not replace permission checks.

## 9. Status And Lifecycle

Status values are domain-specific but must be explicit and documented.

- Config resources use publication, gray/beta, history, and listener state.
- Naming resources use service, instance, health, enabled, and ephemeral state.
- AI resources use metadata status, version status, labels, pipeline state, and
  visibility state.
- Core resources use server, member, readiness, liveness, plugin, and connection
  state.

Runtime APIs should return only states intended for runtime consumers.
Management APIs may return draft, review, offline, internal, or operational
states when authorized.

## 10. API Representation Rules

All API families must preserve the same resource identity:

- HTTP path and parameter names should use the canonical resource terms from
  this spec.
- gRPC request objects should carry the same identity fields even when the
  transport payload is JSON encoded.
- Client SDKs should expose runtime-safe resource operations.
- Maintainer SDKs should expose broad management resource operations.
- Console APIs may shape data for UI, but must not redefine resource identity.

If a historical API uses a compatibility name, the implementation should map it
to the canonical resource term internally and document the alias.

## 11. New Resource Checklist

Every new resource type must define:

- owning domain and module;
- canonical identity fields;
- namespace and group behavior;
- version, label, status, and visibility behavior;
- runtime API, management API, and SDK exposure;
- authorization and audit requirements;
- persistence and cache expectations;
- compatibility aliases, if any.
