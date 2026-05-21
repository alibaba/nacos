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

# Nacos SDK Spec

This document defines the shared SDK design rules for Nacos. Language-specific
SDKs may use idiomatic names, async primitives, and packaging, but their public
capability boundaries should follow this document. The Java baseline is defined
by the [Java SDK Implementation Spec](./sdk-java-impl-spec.md).

## 1. SDK Families

Nacos SDKs are divided into two families:

- **Client SDK**: used by microservice applications, agent frameworks, and
  other runtime workloads that consume Nacos capabilities during normal service
  execution.
- **Maintainer SDK**: used by operation tools, consoles, gateways, management
  platforms, and other applications that need administrative access without
  manually integrating Nacos Admin HTTP APIs.

The two families can share model objects, authentication primitives, retry
rules, and connection infrastructure, but they must not blur their user
audience or permission boundary.

## 2. Client SDK Scope

The Client SDK is designed for application runtime access. It should expose only
the capabilities that a runtime application normally needs:

- read known configuration items and subscribe to their changes;
- register and deregister the current application instance;
- query and subscribe to known services used by the application;
- register, resolve, and subscribe to runtime AI resources, such as MCP
  endpoints, A2A agent endpoints, Prompt, Skill, and AgentSpec resources;
- use optional runtime primitives such as
  [distributed lock](../lock/lock-spec.md) when the language SDK supports them;
- manage its own lifecycle, local cache, listeners, and connections according to
  the [Client Runtime Specs](../client/README.md).

The Client SDK should avoid broad management capabilities, including:

- cluster control, server state mutation, log level changes, or traffic reloads;
- listing all namespaces, all configurations, all services, or all clients;
- querying history, audit-oriented metadata, dump data, or subscriber lists;
- batch deletion, cross-namespace management, and other high-impact operations;
- introducing new write APIs whose main user is an operator instead of a runtime
  application.

Some historical Client SDK interfaces may already contain write or broad query
methods, such as configuration publish/delete or service list operations. These
APIs are compatibility surfaces. New SDK designs should not expand this surface;
management-oriented use cases should be implemented through the Maintainer SDK
or Admin APIs.

## 3. Maintainer SDK Scope

The Maintainer SDK is designed for administrative integration. It may expose
capabilities that are intentionally absent from the Client SDK:

- namespace, cluster, server state, readiness/liveness, and log-level
  maintenance;
- broad configuration listing, searching, publishing, deletion, history, beta,
  dump, and metadata operations;
- service, instance, cluster metadata, subscriber, client, and health-check
  maintenance;
- AI resource management for MCP, A2A, Prompt, Skill, AgentSpec, and Pipeline
  resources;
- paginated and filterable access to large management datasets.

The Maintainer SDK should be treated as a typed facade over the Nacos Admin API
surface. When a capability is only useful for management, UI, gateway, or
operation tools, it belongs here instead of the Client SDK.

## 4. Security Rules

SDK capability design must follow least privilege:

- Client SDK credentials should be scoped to runtime resources and should not
  require broad read or write permissions.
- Maintainer SDK credentials are higher privilege and must be clearly separated
  from Client SDK credentials in documentation and examples.
- Broad read APIs must support explicit filters and pagination. They must not
  silently perform unbounded full-cluster reads.
- Cross-namespace operations belong to the Maintainer SDK and should require an
  explicit namespace parameter.
- SDK documentation should make data-leakage risks visible when an API can list
  or export a large amount of configuration, service, client, or metadata.

## 5. Transport and API Alignment

The SDK contract is a semantic contract, not a transport contract:

- Client SDKs may use [gRPC](../grpc-api/api-spec.md),
  [HTTP Open APIs](../http-api/api-spec.md), local cache files, or a mix of
  transports, as long as the public SDK behavior remains stable.
- Client SDK connection, server list, ability negotiation, local cache, and redo
  behavior is defined by the [Client Runtime Spec](../client/client-runtime-spec.md).
- Maintainer SDKs should align with Nacos Admin API semantics and result models,
  even if the implementation later changes transport details.
- SDK model objects should align with the HTTP and gRPC semantic objects so the
  same business meaning is not redefined differently per transport.
- SDK errors should map Nacos error codes and validation failures into
  language-idiomatic exceptions or result types without hiding server-side
  semantics.

## 6. Multi-language Alignment

Java is currently the baseline implementation for defining shared SDK
semantics. Other language SDKs should align with the same capability families:

- initialization, namespace binding, authentication, and lifecycle shutdown;
- Client SDK configuration, naming, AI, and optional lock runtime capabilities;
- Maintainer SDK core, configuration, naming, and AI management capabilities;
- consistent data identity rules, such as namespace, group, dataId, service
  name, cluster, version, and label;
- consistent listener, subscription, retry, timeout, and local cache behavior
  where the language runtime supports them, following the
  [Client Local Cache And Redo Spec](../client/client-local-cache-redo-spec.md).

Language SDKs may expose futures, promises, streams, coroutines, callbacks, or
context cancellation according to local conventions. These differences should be
documented in language implementation specs without changing the shared SDK
scope.
