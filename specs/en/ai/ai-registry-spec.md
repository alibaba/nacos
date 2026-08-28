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

# AI Registry Spec

AI Registry is the Nacos domain for registering, governing, discovering, and
distributing AI resources. It is a first-class Nacos 3.x capability alongside
Config and Naming. It uses the shared resource identity
`namespaceId -> resourceType -> resourceName` from the
[Resource Model Spec](../design/resource-model-spec.md).

## 1. Scope

AI Registry owns:

- AI resource metadata, versions, labels, status, scope, owner, and business
  tags;
- resource type contracts for MCP Server, Agent, Prompt, Skill, and
  AgentSpec;
- runtime query and subscription behavior for supported AI resources;
- management workflows such as draft creation, review, publish, force publish,
  online/offline, delete, upload, import, and download;
- domain usage of publish pipelines, storage plugins, visibility, auth, and
  trace hooks.

AI Registry does not own:

- Config resource semantics, even when the default AI storage implementation
  stores resource content through Config;
- Naming service semantics, even when MCP or Agent endpoints are represented by
  Naming services and instances;
- community registry protocol definitions exposed by the
  [AI Registry Adaptor Spec](ai-registry-adaptor-spec.md);
- plugin extension contracts. Pipeline, storage, resource import, visibility,
  and trace extension rules are defined by their plugin specs.

## 2. Design Principles

- **Version first**: the standard model is based on `AiResource` metadata and
  `AiResourceVersion` immutable-or-governed versions. New resource types should
  fit this model before introducing custom storage shapes.
- **Runtime and management separation**: Client APIs and SDKs should expose
  runtime query, endpoint registration, and subscription. Admin, Console, and
  Maintainer SDK surfaces own broad listing, upload, publish governance, and
  deletion.
- **Resource identity stability**: `resourceType` is the second identity layer.
  AI resources should not introduce Config-style `groupName` identity unless a
  compatibility path requires it.
- **Plugin composition**: visibility, storage, trace, and publish pipeline
  behavior should be composed through plugins and linked from this domain spec.
  External resource import should use the same plugin model and route imported
  artifacts back through resource operators, not be redefined as a hidden
  AI-only extension mechanism.
- **Fast evolution tolerance**: AI protocols and resource formats change
  quickly. Specs may need incompatible or major revisions when MCP, A2A, agent
  packaging, or model-tool ecosystems change. Such revisions must state
  migration, compatibility, and deprecation behavior according to the
  [Compatibility And Deprecation Spec](../design/compatibility-deprecation-spec.md).

## 3. Standard AI Resource Model

The target standard model is:

```text
AiResource(namespaceId, type, name)
  -> AiResourceVersion(namespaceId, type, name, version)
```

`AiResource` is the metadata row. It contains the resource name, type,
description, enabled status, namespace, owner, visibility scope, business tags,
source, optimistic `metaVersion`, download count, and `versionInfo` JSON.

`AiResourceVersion` is the version row. It contains author, version, version
status, description, storage JSON, publish pipeline info, and download count.

Detailed field and lifecycle rules are defined by the
[AI Resource Model Spec](ai-resource-model-spec.md) and the
[AI Resource Lifecycle Spec](ai-resource-lifecycle-spec.md).

## 4. Resource Type Inventory

| Type | Standard identity | Current or approved target persistence shape | Spec |
| --- | --- | --- | --- |
| `mcp` | `namespaceId -> mcp -> mcpName` | Approved target: `ai_resource` and `ai_resource_version` host management lifecycle; descriptors point to unchanged Config content. The historical Manifest and existing Direct, Service Ref, frontend/backend, and Runtime Naming layouts remain the serving plane. | [MCP Server Spec](mcp-server-spec.md) |
| `agent` | `namespaceId -> agent -> agentName` | Approved target: `ai_resource`, `ai_resource_version`, AI storage, and Naming-backed runtime endpoint publications. Historical A2A storage remains a compatibility source until migration. | [Agent Management Spec](agent-management-spec.md) |
| `prompt` | `namespaceId -> prompt -> promptKey` | Uses `ai_resource`, `ai_resource_version`, and AI storage; legacy Prompt data may be migrated. | [Prompt Spec](prompt-spec.md) |
| `skill` | `namespaceId -> skill -> name` | Uses `ai_resource`, `ai_resource_version`, AI storage, and a lightweight manifest for discovery. | [Skill Spec](skill-spec.md) |
| `agentspec` | `namespaceId -> agentspec -> name` | Uses `ai_resource`, `ai_resource_version`, and AI storage. | [AgentSpec Spec](agentspec-spec.md) |

An A2A AgentCard is a protocol binding inside an `agent` version. The historical
`a2a` resource identity and APIs are compatibility facades described by the
[A2A Agent Spec](a2a-agent-spec.md); they must not create a second canonical
Agent identity.

## 5. Interface Surfaces

AI Registry is exposed through multiple surfaces:

| Surface | Audience | Rules |
| --- | --- | --- |
| `/v3/client/ai/...` | Runtime clients and agent frameworks. | Query known resources, download runtime artifacts, subscribe, and register client-owned endpoints. |
| `/v3/admin/ai/...` | Management tools and Maintainer SDK. | Create, update, list, publish, delete, upload, import, and operate versions. |
| `/v3/console/ai/...` | Nacos console UI. | UI orchestration over the same domain semantics. |
| gRPC AI requests | Java Client SDK runtime traffic. | Query AI resources, perform RAD discovery and subscription, and publish client-owned endpoints where supported. |
| Java SDK | Runtime application integration. | See the [Java SDK Implementation Spec](../sdk/sdk-java-impl-spec.md). |
| Java Maintainer SDK | Typed management integration. | Should align with Admin API semantics and the resource type specs. |
| AI Registry adaptor | External community registry clients. | Optional compatibility endpoints on a separate port; see the [AI Registry Adaptor Spec](ai-registry-adaptor-spec.md). |

## 6. Cross-cutting Rules

- AI Registry APIs must use the v3 response, error, auth, and API type rules
  from the [HTTP API Spec](../http-api/api-spec.md).
- gRPC payloads must follow the [gRPC API Spec](../grpc-api/api-spec.md).
- Runtime query and subscription should prefer version or label routing over
  broad resource listing.
- Visibility must use the [Visibility Plugin Spec](../auth/visibility-plugin-spec.md).
- Publish pipeline extension behavior must use the
  [AI Publish Pipeline Plugin Spec](../plugin/ai-pipeline-plugin-spec.md).
- Resource storage extension behavior must use the
  [AI Storage Plugin Spec](../plugin/ai-storage-plugin-spec.md).
- External AI resource import behavior must use the
  [AI Resource Import Plugin Spec](../plugin/ai-resource-import-plugin-spec.md).
  Import plugins convert operator-configured external sources into import
  artifacts; resource operators apply those artifacts to the current storage and
  lifecycle model.
- Trace and audit events should use the [Trace Plugin Spec](../plugin/trace-plugin-spec.md)
  and the shared observability rules.

## 7. Migration And Evolution Items

- MCP migration follows the asynchronous, one-way management transition
  `SYNCING -> LIFECYCLE_MANAGED` in the MCP Server Spec. It creates
  Resource/Version pointers without changing Config bytes or Naming, waits for
  zero-difference reconciliation and every-member management capability, and
  continues maintaining the historical Manifest and current endpoint serving
  layout after cutover. Production behavior remains pending until that contract
  is implemented and verified.
- Historical A2A AgentCard and Naming endpoint data must migrate to the Agent
  model through the rolling-upgrade plan; the legacy APIs remain projections,
  not an independent resource store.
- Prompt has a migration path from legacy Config-shaped Prompt data to the
  standard AI resource model. Legacy mappings must remain compatibility
  storage, not formal Config resource semantics.
- RAD returns a deterministic endpoint set. Health filtering, priority/weight
  selection, and load balancing are client-side policies and do not change the
  Registry snapshot.
- AI resource schemas and protocol-specific payloads may require major revision
  as upstream MCP, A2A, and agent package ecosystems evolve.
