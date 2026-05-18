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

# Nacos Core Capabilities Spec

This document defines the top-level capability boundaries of Nacos. The
[Nacos Design Spec](nacos-design-spec.md) defines overall intent, the
[Resource Model Spec](resource-model-spec.md) defines shared resource identity,
the [Foundation Capabilities Spec](foundation-capabilities-spec.md) defines
shared infrastructure, and domain specs define detailed behavior for each
capability.

## 1. Capability Layers

Nacos capabilities are organized from product intent to concrete interfaces:

```text
Design intent
  -> Resource model
  -> Foundation capabilities
  -> Domain capabilities
  -> HTTP / gRPC / SDK interfaces
  -> Extension and security rules
```

Domain capability specs own the meaning of resources and behavior. Interface
specs define how those semantics are exposed. Plugin specs define extension
points and must not redefine domain ownership. Foundation capabilities provide
[server lifecycle and environment](foundation-server-lifecycle-env-spec.md),
[cluster membership](foundation-cluster-membership-spec.md),
[remote connection](foundation-remote-connection-spec.md),
[request filtering and runtime context](foundation-request-context-spec.md),
[internal RPC](foundation-internal-rpc-spec.md),
[AP consistency](foundation-ap-consistency-spec.md),
[CP consistency](foundation-cp-consistency-spec.md),
[persistence and dump](foundation-persistence-dump-spec.md),
[task](foundation-task-execution-spec.md), and
[event](foundation-event-dispatch-spec.md), and
[observability](foundation-observability-hooks-spec.md) infrastructure; they support domains
but do not own domain resource semantics.

## 2. Core Domains

| Domain | Primary responsibility | Resource identity | Detailed spec |
| --- | --- | --- | --- |
| Configuration | Dynamic configuration storage, release, query, subscription, gray delivery, history, capacity, and audit. | `namespaceId -> groupName -> dataId` | [Config Spec](../config/config-spec.md) |
| Naming | Service discovery, service metadata, instances, health, subscription, and runtime push. | `namespaceId -> groupName -> serviceName` | [Naming Spec](../naming/naming-spec.md) |
| AI Registry | MCP, A2A, Prompt, Skill, AgentSpec, versions, labels, visibility, and publish governance. | `namespaceId -> resourceType -> resourceName` | [AI Registry Spec](../ai/ai-registry-spec.md) |
| Core Operations | Namespace, [cluster member](foundation-cluster-membership-spec.md), server state, readiness, liveness, plugin state, and operation controls. | Domain-specific administrative resources. | [Core Operations Spec](../core/core-operations-spec.md) |
| Console | Web UI, Console API backend, deployment bridge, and UI workflow adaptation for domain-owned resources. | UI workflows over domain-owned resources. | [Console Spec](../console/console-spec.md) |
| Distributed Lock | Experimental short critical-section mutual exclusion over CP state. | `lockType -> key` | [Distributed Lock Spec](../lock/lock-spec.md) |
| Security And Visibility | Authentication, authorization, permissions, API classification, resource visibility, and identity propagation. | Structured Nacos resource identity. | [Auth And Permission Spec](../auth/auth-permission-spec.md), [Visibility Plugin Spec](../auth/visibility-plugin-spec.md) |
| Extension | Server and client extension points for auth, visibility, datasource, encryption, trace, control, addressing, AI pipeline, and related concerns. | Plugin-type identity plus domain-owned resource identity. | [Plugin Spec](../plugin/plugin-spec.md) |

## 3. Cross-domain Rules

- A domain owns its resource semantics, lifecycle, validation, and observable
  state.
- `core`, `common`, `persistence`, `consistency`, `auth`, and `plugin` modules
  provide shared infrastructure as defined by the
  [Foundation Capabilities Spec](foundation-capabilities-spec.md), including
  [server lifecycle and environment](foundation-server-lifecycle-env-spec.md),
  [cluster membership](foundation-cluster-membership-spec.md),
  [remote connection lifecycle](foundation-remote-connection-spec.md),
  [request filtering and runtime context](foundation-request-context-spec.md),
  [internal RPC](foundation-internal-rpc-spec.md),
  [AP consistency](foundation-ap-consistency-spec.md), and
  [CP consistency](foundation-cp-consistency-spec.md), and
  [persistence and dump](foundation-persistence-dump-spec.md),
  [task execution](foundation-task-execution-spec.md), and
  [event dispatch](foundation-event-dispatch-spec.md), and
  [observability hooks](foundation-observability-hooks-spec.md); they do not own Config,
  Naming, or AI resource semantics unless a domain spec explicitly delegates a
  behavior.
- Runtime client surfaces should expose least-privilege operations for known
  resources. Broad list, export, clone, migration, capacity, and operation APIs
  belong to Admin API, Console API, or Maintainer SDK.
- All domain APIs must preserve the shared resource model and the interface
  rules from the [HTTP API Spec](../http-api/api-spec.md),
  [gRPC API Spec](../grpc-api/api-spec.md), and
  [SDK Spec](../sdk/sdk-spec.md).
- Cross-cutting behavior such as authorization, visibility, encryption,
  datasource dialect, trace, and control must be implemented through the
  relevant specs instead of duplicating rules inside each domain.

## 4. Capability Boundary Checklist

Every new capability should identify:

- owning domain and module;
- resource identity and whether the second layer is `groupName` or
  `resourceType`;
- runtime-facing, management-facing, or operation-facing audience;
- HTTP, gRPC, Client SDK, Maintainer SDK, Console, or plugin surface;
- persistence, cache, event, consistency, and recovery expectations;
- authorization, visibility, audit, trace, and control requirements;
- compatibility impact for existing APIs, SDKs, storage, and plugins.
