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

# A2A Agent Spec

This document defines the AI Registry contract for A2A AgentCard resources.

## 1. Identity

Canonical A2A Agent identity is:

```text
namespaceId -> a2a -> agentName
```

`agentName` is the public resource name. Current storage may encode the agent
name before writing Config data. Encoding is an implementation detail and must
not change public identity.

## 2. Domain Model

An A2A Agent resource contains AgentCard metadata and versioned AgentCard
details. It may include:

- agent name, description, provider, capabilities, skills, authentication, and
  protocol information;
- registration type;
- version list and latest published version;
- service-style endpoints represented by agent interfaces.

The current AgentCard model follows the Google A2A 1.0.0 protocol fields. Nacos
keeps several 0.x-compatible fields, such as root-level `url`,
`protocolVersion`, `preferredTransport`, `additionalInterfaces`, and
`supportsAuthenticatedExtendedCard`, for compatibility with existing clients.
These legacy fields are compatibility inputs only and may be removed after the
1.0.0 model becomes the only supported contract.

When no version is specified, runtime query resolves the latest version.

## 3. Endpoint Model

A2A endpoints may be registered by runtime clients and represented through a
Naming service under the A2A endpoint group.

Endpoint resolution rules:

- endpoints are attached to a specific agent version;
- endpoint metadata should include transport, protocol binding, protocol
  version, path, query, TLS support, and tenant data when available;
- when multiple compatible endpoints exist, the current implementation chooses
  one randomly; this is not a stable policy contract and should be refined by a
  future endpoint-selection spec.

Naming is endpoint infrastructure. The A2A Agent remains an AI Registry
resource.

## 4. API And SDK Behavior

- Admin APIs may register, query, update, delete, list, and operate A2A
  AgentCard versions.
- Client APIs and SDKs may query AgentCards, release AgentCards where supported,
  register/deregister endpoints, batch replace endpoints owned by the current
  client, and subscribe to AgentCard changes.
- gRPC payloads include AgentCard query/release and endpoint registration
  requests as defined by the [gRPC API Spec](../grpc-api/api-spec.md).

## 5. Current Compatibility Storage

Current A2A implementation stores AgentCard metadata and versions through
Config-shaped records and uses Naming services for endpoints. This is
compatibility storage. The canonical model should be
`ai_resource + ai_resource_version`.

## 6. Pending Migration Issues

- Migrate AgentCard metadata and version rows to the standard AI resource
  model.
- Define migration from encoded Config data ids to resourceName identity.
- Define endpoint ownership, connection cleanup, and deterministic endpoint
  selection.
- Align latest-version behavior with the shared label model.
- Introduce a protocol-neutral abstraction above A2A, such as `RemoteAgent` or
  `AgentService`. Nacos is a unified registry and should not bind its AI
  registry model to one specific agent protocol unless that protocol has become
  a clear community standard. A2A should remain one supported protocol binding
  under the higher-level remote-agent resource model.

## 7. Evolution Note

A2A protocol versions, AgentCard fields, security schemes, and endpoint
representation may evolve rapidly. The current baseline is A2A 1.0.0, while
0.x-compatible fields are retained only to avoid breaking existing clients. This
spec may need incompatible revisions when the upstream A2A model changes or when
Nacos introduces a protocol-neutral remote-agent abstraction. Such changes must
define migration and compatibility behavior.
