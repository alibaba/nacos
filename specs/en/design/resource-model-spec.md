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
documentation. It refines the top-level domain structure from the
[Nacos Design Spec](nacos-design-spec.md).

## 1. Top-level Resource Hierarchy

Nacos top-level resource identity has three layers:

```text
NamespaceId -> Group/resourceType -> resourceName
```

The layers mean:

| Layer | Meaning | Scope |
| --- | --- | --- |
| `NamespaceId` | Isolation boundary for tenants, teams, environments, or management domains. | All tenant-scoped resources. |
| `Group/resourceType` | The second-level classifier. Microservice resources use conceptual `Group`; AI resources use `resourceType`. | Domain-specific. |
| `resourceName` | Stable name that identifies a concrete resource within the parent scope. | All named resources. |

`Group` and `resourceType` must not be treated as the same field:

- `Group` is a business grouping for microservice resources, mainly used by
  configuration and naming resources.
- `resourceType` is a type classifier for resources that share a governance
  model, mainly used by AI Registry resources.

Therefore, Nacos has two primary resource-model branches:

- **Microservice resource model**: `NamespaceId -> Group -> resourceName`.
- **AI resource model**: `NamespaceId -> resourceType -> resourceName`.

Version, labels, status, visibility, owner, and metadata are governance
attributes of a resource. They are not part of the top-level three-layer
identity unless a domain spec explicitly says so.

## 2. NamespaceId

NamespaceId is the primary isolation boundary. It separates tenants, teams,
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

## 3. Second Layer: Group Or resourceType

The second layer further classifies resources inside a namespace, but the
semantics are domain-specific.

### 3.1 Group

Group is the business grouping for microservice resources. It is part of
configuration and naming identity and defaults to `DEFAULT_GROUP` when omitted
by supported interfaces.

Group is suitable for business isolation inside the same resource family, such
as application, business line, environment-local grouping, or user-defined
grouping. Group does not express resource type, so a config and a service may
exist under the same Group.

When the Group layer is expressed as a concrete public field in new specs,
HTTP APIs, SDKs, or user-facing documents, the field name should be
`groupName`. The shorter `group` name is a conceptual term, internal model
field, or compatibility field.

### 3.2 resourceType

resourceType is a type classifier. It is suitable for shared governance models
that contain multiple resource types, such as AI Registry resource types:
`mcp`, `a2a`, `prompt`, `skill`, and `agentspec`.

resourceType is not a business grouping. AI resources should not introduce a
Group identity field unless a domain spec explicitly defines additional
semantics.

## 4. Third Layer: resourceName

resourceName is the stable name of a resource under
`NamespaceId + Group/resourceType`.

Different domains expose domain-specific names:

| Domain | Concrete resourceName |
| --- | --- |
| Config | `dataId` |
| Naming service | `serviceName` |
| MCP Server | `name` or `mcpName` |
| A2A AgentCard | `name` or `agentName` |
| Prompt | `promptKey` |
| Skill | `name` |
| AgentSpec | `name` |

resourceName is an identity field and should not be modified as ordinary
metadata. Updating a resourceName is a delete-and-create or clone operation
unless a domain spec defines a migration operation.

## 5. Microservice Resource Model

The microservice resource model uses:

```text
NamespaceId -> Group -> resourceName
```

It covers the traditional Nacos configuration and naming capabilities.

### 5.1 Config Resource

Config resource identity is:

```text
namespaceId -> groupName -> dataId
```

Config owns:

- content and md5;
- config type;
- description, tags, and app name metadata;
- publish, CAS publish, delete, and query semantics;
- listener and fuzzy-watch semantics;
- gray/beta publication state;
- history, rollback, dump, and failover data.

`dataId` is the resourceName for Config. Config metadata such as `appName`,
`type`, `desc`, and `configTags` does not change identity.

See the [Config Resource Spec](../config/config-resource-spec.md) for detailed
rules.

Prompt has a legacy compatibility mapping to config storage with fixed group
`nacos-ai-prompt` and dataId `{promptKey}.json`. This mapping is a compatibility
storage shape and must not make Prompt a normal Config resource in new specs.

### 5.2 Naming Service Resource

Naming service resource identity is:

```text
namespaceId -> groupName -> serviceName
```

Naming service owns:

- service metadata and internal filtering information;
- ephemeral-service or persistent-service semantics;
- clusters and health-check configuration;
- subscribers, publishers, and client connection views;
- service and instance change events.

Internal grouped names may use `group@@serviceName`, but public APIs and specs
should prefer separate `groupName` and `serviceName` fields. See the
[Naming Resource Spec](../naming/naming-resource-spec.md) for detailed rules.

### 5.3 Cluster And Instance

Cluster and Instance are subordinate resources of a service. They do not change
the top-level three-layer model.

```text
namespaceId -> groupName -> serviceName -> clusterName -> instance
```

Instance identity is usually determined by service scope, `clusterName`, `ip`,
and `port`; `instanceId` may be generated or provided as a runtime identifier.

Instance contains `ip`, `port`, `clusterName`, `weight`, `healthy`, `enabled`,
`ephemeral`, `metadata`, and optional `instanceId`. An instance must not be
interpreted without its service scope.

Ephemeral-service and persistent-service semantics affect lifecycle and
consistency behavior. They must be preserved across HTTP, gRPC, SDK, and
storage models.

## 6. AI Resource Model

The AI resource model uses:

```text
NamespaceId -> resourceType -> resourceName
```

It covers AI Registry resources such as
[MCP Server](../ai/mcp-server-spec.md), [A2A AgentCard](../ai/a2a-agent-spec.md),
[Prompt](../ai/prompt-spec.md), [Skill](../ai/skill-spec.md), and
[AgentSpec](../ai/agentspec-spec.md). The shared AI model is defined by the
[AI Registry Spec](../ai/ai-registry-spec.md) and the
[AI Resource Model Spec](../ai/ai-resource-model-spec.md).

AI resources share governance attributes:

| Attribute | Meaning |
| --- | --- |
| `version` | Resource version, forming `NamespaceId + resourceType + resourceName + version`. |
| `labels` | Label-to-version mappings such as `latest` or `stable`. |
| `status` | Resource or version lifecycle state. |
| `visibility` | Visibility scope, such as `PUBLIC` or `PRIVATE`. |
| `owner` | Owner identity. |
| `bizTags` / `metadata` / `ext` | Business or extension metadata that does not participate in identity. |
| `pipeline` | Publish review or automation state. |

AI resource metadata identity is `namespaceId + resourceType + resourceName`.
AI resource version identity is
`namespaceId + resourceType + resourceName + version`.

Published AI versions should be treated as immutable unless a domain spec
explicitly defines a safe mutation. Changes should create a new draft version,
pass review if required, and then publish or relabel.

### 6.1 MCP Server

MCP Server canonical resource identity is:

```text
namespaceId -> mcp -> mcpName
```

MCP Server resources describe MCP-capable services. They may be created from new
MCP servers, imported external MCP servers, or existing HTTP/RPC services
adapted into MCP services.

MCP Server may carry a registry `id`, but `mcpName` remains the user-facing
resourceName. MCP-specific metadata includes protocol, front protocol,
repository, packages, icons, website URL, local or remote server config,
endpoint spec, tool spec, status, and discovered capabilities.

### 6.2 A2A AgentCard

A2A AgentCard canonical resource identity is:

```text
namespaceId -> a2a -> agentName
```

AgentCard resources describe agent capabilities, skills, supported interfaces,
provider information, security schemes, signatures, and endpoint metadata.

`registrationType` participates in AgentCard lookup and compatibility
semantics, but it is not the top-level second-layer field. Its relation with
resourceName, version, and endpoint should be defined by a specific A2A domain
spec.

### 6.3 Prompt

Prompt canonical resource identity is:

```text
namespaceId -> prompt -> promptKey
```

Prompt version identity is:

```text
namespaceId -> prompt -> promptKey -> version
```

A Prompt contains template content, variables, md5, and version metadata.
Runtime Prompt lookup should resolve by explicit version, then label, then
`latest` according to the relevant API or SDK contract.

### 6.4 Skill

Skill canonical resource identity is:

```text
namespaceId -> skill -> skillName
```

Skill represents reusable AI Agent capability. A Skill contains metadata,
instruction content, optional resources, versions, labels, visibility, and
publish pipeline metadata.

A Skill version moves through draft, reviewing, reviewed, online, and offline
states. Only online versions should be returned to runtime clients unless a
management API explicitly requests other states.

### 6.5 AgentSpec

AgentSpec canonical resource identity is:

```text
namespaceId -> agentspec -> agentSpecName
```

AgentSpec assembles agent configuration by referencing prompts, skills, MCP
servers, A2A agents, or other required resources. AgentSpec should reference
other resources by stable identity and version or label, not by storage
implementation details.

## 7. Visibility And Ownership

Resources that support visibility must expose:

- `namespaceId`;
- `resourceType`;
- stable resourceName;
- scope, currently `PUBLIC` or `PRIVATE`;
- owner identity.

Visibility affects discovery, detail viewing, download, and write operations.
It complements authorization and must not replace permission checks. Permission
semantics are defined by the
[Auth And Permission Spec](../auth/auth-permission-spec.md).

## 8. Status And Lifecycle

Status values are domain-specific but must be explicit and documented.

- Config resources use publication, gray/beta, history, and listener state.
- Naming resources use service type, instance, health, enabled, and lifecycle
  state.
- AI resources use metadata status, version status, labels, pipeline state, and
  visibility state.
- Core resources use server, [member](foundation-cluster-membership-spec.md),
  readiness, liveness, plugin, and
  [connection](foundation-remote-connection-spec.md) state.

Runtime APIs should return only states intended for runtime consumers.
Management APIs may return draft, review, offline, internal, or operational
states when authorized.

## 9. API Representation Rules

All API families must preserve the same resource identity:

- [HTTP](../http-api/api-spec.md) path and parameter names should use the
  canonical resource terms from this spec.
- [gRPC](../grpc-api/api-spec.md) request objects should carry the same identity
  fields even when the transport payload is JSON encoded.
- [Client SDKs](../sdk/sdk-spec.md) should expose runtime-safe resource
  operations.
- Maintainer SDKs should expose broad management resource operations.
- Console APIs may shape data for UI, but must not redefine resource identity.

If a historical API uses a compatibility name, the implementation should map it
to the canonical resource term internally and document the alias.

## 10. New Resource Checklist

Every new resource type must define:

- owning domain and module;
- canonical identity fields;
- whether the second layer is `Group` or `resourceType`;
- concrete business name for resourceName;
- version, label, status, and visibility behavior;
- runtime API, management API, and SDK exposure;
- authorization and audit requirements;
- persistence and cache expectations;
- compatibility aliases, if any.
