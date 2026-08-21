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

This document defines the AI Registry contract for MCP Server resources.

## 1. Identity

Canonical MCP Server identity is:

```text
namespaceId -> mcp -> mcpName
```

`mcpName` is the public resource name. Current code also uses an internal MCP
server id for Config-backed storage. The public spec should prefer `mcpName`
and the standard AI resource identity.

## 2. Domain Model

An MCP Server describes an MCP-compatible server and may include:

- server metadata and protocol information;
- version details and latest published version;
- tool specification;
- resource specification;
- backend or frontend endpoint references;
- endpoint protocol, address, port, path, headers, and export path metadata.

MCP Server versions should be independently queryable. When a version is not
specified, runtime query resolves the latest published version.

### 2.1 Tool Output Schema Compatibility

An MCP tool `outputSchema` is a JSON Schema. Nacos must preserve valid JSON
Schema type declarations instead of narrowing the `type` keyword to a single
string. A nullable response property may use a union containing one base type
and `null`, for example `{"type": ["string", "null"]}`.

Console output-schema editing must preserve this representation when loading
and saving a tool. When importing an OpenAPI 3 response schema, an explicitly
typed property with `nullable: true` must be converted to the equivalent JSON
Schema union. This representation is additive metadata and does not change the
tool invocation or response forwarding contract for existing consumers.

MCP Registry-compatible discovery is not part of the canonical MCP resource
contract. It is exposed by the optional
[AI Registry Adaptor Spec](ai-registry-adaptor-spec.md), which maps Nacos MCP
resources into MCP Registry response shapes for community clients.

## 3. Endpoint Model

MCP endpoints can be represented in two modes:

| Mode | Meaning |
| --- | --- |
| `REF` | The MCP resource references an existing Naming service. |
| Direct endpoint | Nacos creates or updates a Naming service under the MCP endpoint group and registers endpoint instances. |

Endpoint services and instances are transport targets. They do not make the MCP
Server a Naming service resource. Naming is used as endpoint infrastructure.

Direct endpoint services should be marked as MCP-owned service metadata and
should use non-ephemeral service/instance behavior when current implementation
requires persistent endpoint visibility.

## 4. API And SDK Behavior

- Admin APIs may list, query, create/release, update, delete, and import MCP
  resources.
- Client APIs and SDKs may query MCP details, release MCP server versions where
  supported, register/deregister client-owned endpoints, and subscribe to MCP
  changes.
- gRPC payloads include query, release, and endpoint registration requests as
  defined by the [gRPC API Spec](../grpc-api/api-spec.md).

MCP participates in generic AI Resource Search and provides a
resource-specific Search facade with `resourceType=mcp` fixed. Both reuse the
same index and Query Planner from the
[AI Resource Search Spec](ai-resource-search-spec.md); a Config-backed name
list does not remain a separate full-text search engine. The MCP handler may
project server name, description, tools, resources, and public tags. Endpoint
credentials, runtime instances, and sensitive authentication metadata do not
enter search chunks. Before migration to canonical AI Resource storage, the
handler may read compatibility storage but produces the same canonical
projection. Generic Search restricted to MCP has the same candidate
eligibility as resource-specific Search.
The Client facade is `GET /v3/client/ai/mcp/search`; it accepts `query`,
repeated `tagsAll`, `protocolsAny`, `capabilitiesAny`, `pageNo`, and `pageSize`,
and returns `Page<McpServerBasicInfo>`.

## 5. Current Compatibility Storage

Current MCP implementation stores MCP metadata through Config-shaped records
and uses Naming services for endpoints:

- MCP version info;
- MCP server detail for a version;
- MCP tool specification;
- MCP resource specification;
- MCP endpoint service and instances.

This is compatibility storage, not the target canonical model. New MCP
semantics should be specified as AI Registry semantics, then mapped to current
storage until migration is complete.

## 6. External Import

MCP import from external registries or marketplaces should use the
[AI Resource Import Plugin Spec](../plugin/ai-resource-import-plugin-spec.md).
Import plugins convert operator-configured external MCP registry data into MCP
import artifacts. They must not write MCP storage directly.

The MCP resource operator applies imported artifacts through the MCP domain
operation service. In the current implementation this may use the Config-backed
`McpServerOperationService`. After MCP metadata and versions migrate to
`ai_resource`, the MCP resource operator should change to the new storage model
while import plugins and unified import APIs remain compatible.

Legacy MCP import APIs may remain as compatibility routes, but they should
delegate to the unified AI resource import flow. User-provided registry URLs or
MCP endpoint addresses must not be used as direct server-side network targets by
default.

## 7. Pending Migration Issues

- Migrate MCP metadata and version rows to `ai_resource` and
  `ai_resource_version`.
- Define how existing Config-backed MCP records are discovered, migrated, and
  served during mixed-version clusters.
- Align MCP labels and latest-version behavior with the shared AI resource
  label model.
- Define endpoint ownership and cleanup rules for direct endpoints registered
  by runtime clients.

## 8. Evolution Note

MCP is evolving quickly. Tool schema, resources, transport modes, auth
metadata, and registry interoperability may change. MCP Server spec changes may
therefore be larger than normal Nacos domain changes, but they must include
compatibility and migration guidance.
