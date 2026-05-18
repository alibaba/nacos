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

# Nacos Design Spec

This document defines the top-level design intent for Nacos. The
[Resource Model Spec](./resource-model-spec.md) refines resource identity, while
the [HTTP API Spec](../http-api/api-spec.md),
[gRPC API Spec](../grpc-api/api-spec.md), and [SDK Spec](../sdk/sdk-spec.md)
refine external interface rules.

## 1. Positioning

Nacos is a dynamic service discovery, configuration management, service
management, and AI agent management platform for cloud-native and AI-native
applications.

Nacos is infrastructure for service-centered application architectures. A
service may be a microservice, RPC service, DNS-discoverable endpoint, gateway
backend, MCP Server, A2A Agent, Prompt, Skill, AgentSpec, or another runtime
resource that needs discovery, dynamic configuration, lifecycle management,
governance, and secure distribution.

## 2. Design Goals

Nacos should:

- make runtime resources easy to register, discover, configure, govern, and
  observe;
- let applications change configuration, routing, discovery, and AI resource
  selection without redeployment;
- provide a unified control plane for configuration, naming, service metadata,
  and AI registry resources;
- support multi-tenant and multi-environment isolation through namespaces and
  permission boundaries;
- remain production-grade for large-scale clusters and high-concurrency
  workloads;
- preserve an open ecosystem through standard protocols, SDKs, APIs, and plugin
  extension points.

## 3. Design Principles

### 3.1 Easy To Use

Nacos should provide simple APIs, SDKs, command-line tools, and console
workflows for common runtime and management tasks. A user should not need to
understand internal storage, consensus, or transport details to use Nacos
correctly.

### 3.2 Standards Oriented

Nacos should align with widely used standards and ecosystem protocols where
they exist, including cloud-native service discovery, HTTP APIs, gRPC transport,
MCP, A2A, and Kubernetes/Spring/Dubbo/Spring AI integration patterns.

When Nacos adds a protocol adaptation layer, the Nacos resource model remains
the internal source of semantic truth and protocol models are adapters over that
model.

### 3.3 Runtime Dynamic

Nacos resources are expected to change at runtime. Configuration content,
service instances, endpoints, AI resources, labels, visibility, and metadata may
change without requiring application redeployment.

Runtime-facing APIs and SDKs should support subscription, push, local cache, and
safe fallback where the domain requires them.

### 3.4 High Availability

Nacos should support standalone mode for local development and test scenarios,
and cluster mode for production scenarios. Cluster mode should provide
availability and horizontal scalability through the appropriate consistency and
replication mechanisms for each domain.

### 3.5 Extensible

Nacos should keep module boundaries clear and expose plugin extension points for
cross-cutting concerns such as authentication, visibility, datasource,
encryption, tracing, control, environment, and AI publish pipelines.

Extensions must not redefine core resource identity, API response semantics, or
authorization boundaries.

### 3.6 Secure And Governable

Nacos manages sensitive runtime and AI assets. Security is part of the design,
not an optional add-on:

- authentication and authorization should be enabled and explicit for protected
  APIs;
- AI resources should support ownership, visibility, version governance,
  review, distribution, and auditability;
- broad read and management APIs belong to admin or maintainer surfaces;
- runtime client surfaces should expose least-privilege capabilities.

## 4. Domain Responsibilities

Nacos domains should be organized around the unified resource hierarchy:

```text
NamespaceId -> Group/resourceType -> resourceName
```

`NamespaceId` is the isolation boundary. `Group/resourceType` is the
second-level classifier: microservice resources use `Group`, while AI Registry
resources use `resourceType`. `resourceName` is the concrete domain resource
name. Domain specs may further define governance attributes such as version,
label, status, and visibility, but they should not break this top-level
hierarchy.

### 4.1 Configuration Domain

The configuration domain manages dynamic configuration resources identified by
namespace, group, and dataId. It owns configuration content, type, md5, metadata,
listeners, fuzzy watch, gray/beta release, history, rollback, dump, and
failover-related behavior. The detailed rules are defined by the
[Config Spec](../config/config-spec.md).

### 4.2 Naming Domain

The naming domain manages service discovery resources identified by namespace,
group, and service name. It owns service metadata, instances, clusters, health
status, ephemeral-service and persistent-service semantics, subscribers, client
views, and service-change push. The detailed rules are defined by the
[Naming Spec](../naming/naming-spec.md).

### 4.3 AI Registry Domain

The [AI Registry domain](../ai/ai-registry-spec.md) manages AI resources such as
MCP Server, A2A AgentCard, Prompt, Skill, and AgentSpec. AI resources use
`NamespaceId -> resourceType -> resourceName` as their top-level identity. The
domain owns AI resource metadata, versions, labels, visibility, endpoints, tool
or skill descriptors, publish pipeline state, download/distribution, and
audit-oriented trace information.

AI Registry is not a separate product model inside Nacos. It is a first-class
Nacos domain that uses the same namespace, API, SDK, auth, plugin, and resource
governance principles.

### 4.4 Core And Operation Domain

The [Core Operations domain](../core/core-operations-spec.md) owns namespace management,
[cluster members](foundation-cluster-membership-spec.md), server state,
readiness/liveness,
[server lifecycle and environment](foundation-server-lifecycle-env-spec.md),
[connection management](foundation-remote-connection-spec.md),
[request filtering and runtime context](foundation-request-context-spec.md),
[internal RPC](foundation-internal-rpc-spec.md), log-level operations, plugin
state, and other server control-plane resources.

These capabilities are administrative by nature and should be exposed through
Admin API, Console API, or Maintainer SDK surfaces rather than runtime Client
SDK surfaces.

### 4.5 Security And Visibility Domain

The security domain owns authentication, authorization, identity propagation,
API classification, action classification, and visibility enforcement. It
should apply consistently across HTTP APIs, gRPC calls, SDKs, console actions,
and plugin-provided APIs.

## 5. Interface Architecture

Nacos exposes the same resource semantics through multiple interface families:

- HTTP APIs for Open, Admin, Console, Auth, and plugin APIs;
- gRPC APIs for high-frequency runtime communication, push, subscription, and
  client-server control messages;
- Client SDKs for runtime applications and agent frameworks;
- Maintainer SDKs for management, UI, gateway, and operation integrations;
- console and CLI tools for human and automation workflows.

Interfaces may use different transport models, but they must preserve the same
resource identity, validation, authorization, lifecycle, and error semantics.

## 6. Module Architecture

Nacos modules should follow these ownership rules:

- `api`, `client`, and `client-basic` define public client models, SDK
  interfaces, and transport contracts.
- `config`, `naming`, and `ai` own domain behavior for their resources.
- `core` owns cluster, namespace, server, plugin, and operation fundamentals.
- `common`, `consistency`, and `persistence` provide shared foundation
  capabilities such as events, tasks, AP/CP protocols, and storage. Core member
  and connection boundaries are further defined by the
  [Cluster Membership Spec](foundation-cluster-membership-spec.md) and
  [Remote Connection Lifecycle Spec](foundation-remote-connection-spec.md).
  Server lifecycle and request context boundaries are defined by the
  [Server Lifecycle And Environment Configuration Spec](foundation-server-lifecycle-env-spec.md)
  and [Request Filtering And Runtime Context Spec](foundation-request-context-spec.md).
  Server-to-server request boundaries are defined by the
  [Internal RPC And Cluster Request Spec](foundation-internal-rpc-spec.md).
  AP/CP consistency boundaries are defined by the
  [AP Consistency Spec](foundation-ap-consistency-spec.md) and
  [CP Consistency Spec](foundation-cp-consistency-spec.md). Persistence and dump
  boundaries are defined by the
  [Persistence And Dump Spec](foundation-persistence-dump-spec.md). Task and
  event boundaries are defined by the
  [Task Execution Spec](foundation-task-execution-spec.md) and
  [Event Dispatch And NotifyCenter Spec](foundation-event-dispatch-spec.md).
  Observability boundaries are defined by the
  [Observability Hooks Spec](foundation-observability-hooks-spec.md).
  Other foundation boundaries are defined by the
  [Foundation Capabilities Spec](foundation-capabilities-spec.md).
- `auth` and plugin modules own extensible security and policy behavior.
- `maintainer-client` exposes typed Java management entry points over Admin API
  semantics.
- `console` exposes UI-oriented backend APIs and should not redefine domain
  semantics independently.

Shared models should live where their API compatibility requirement is clear.
Server-only implementation details should not leak into public SDK contracts.

## 7. Consistency And Storage

Each domain should choose consistency and storage behavior according to resource
semantics:

- configuration resources require durable storage, version/history awareness,
  and reliable change notification;
- naming resources require fast runtime updates, health-driven availability,
  and clear ephemeral/persistent semantics;
- AI resources require durable metadata, immutable published versions where
  applicable, label-based routing, visibility, and review/audit metadata;
- server and cluster resources require explicit administrative control and
  operational safety.

The implementation may use database persistence, local cache, Distro, Raft, or
other mechanisms, but public semantics must be expressed in domain specs rather
than storage implementation details. The common foundation expectations for
[server lifecycle](foundation-server-lifecycle-env-spec.md),
[membership](foundation-cluster-membership-spec.md),
[connection lifecycle](foundation-remote-connection-spec.md),
[request filtering](foundation-request-context-spec.md),
[internal RPC](foundation-internal-rpc-spec.md),
[AP consistency](foundation-ap-consistency-spec.md),
[CP consistency](foundation-cp-consistency-spec.md),
[persistence and dump](foundation-persistence-dump-spec.md),
[task execution](foundation-task-execution-spec.md), and
[event dispatch](foundation-event-dispatch-spec.md), and
[observability hooks](foundation-observability-hooks-spec.md) are defined by the
[Foundation Capabilities Spec](foundation-capabilities-spec.md) and its sub
specs.

## 8. New Feature Design Rules

Every new Nacos feature should define:

- the domain that owns the feature;
- the resource type and resource identity;
- whether the feature is runtime-facing, management-facing, or both;
- the API audience: Open, Admin, Console, Auth, plugin, gRPC, Client SDK, or
  Maintainer SDK;
- namespace, group, version, label, status, and visibility behavior if relevant;
- authentication, authorization, and audit requirements;
- compatibility and deprecation impact for existing APIs and SDKs;
- tests or validation rules that keep the spec enforceable.

If a feature cannot answer these questions, it is not ready to become a stable
Nacos contract.
