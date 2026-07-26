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

# Nacos Client Ability Negotiation Spec

This document defines client-side ability negotiation for Nacos runtime
connections. It expands the ability part of the
[Client Runtime Spec](client-runtime-spec.md) and complements the gRPC setup
rules in the [gRPC API Spec](../grpc-api/api-spec.md).

## 1. Ability Model

An ability is a named boolean feature flag scoped by an `AbilityMode`.

| Mode | Holder | Purpose |
|------|--------|---------|
| `SERVER` | Nacos server node | Describes server-side support visible to SDK clients or cluster clients. |
| `SDK_CLIENT` | Runtime SDK client | Describes features the SDK client can use or receive. |
| `CLUSTER_CLIENT` | Server-to-server client | Describes internal cluster client features. |

Ability names must be unique inside a mode. The ability key definition is the
compatibility registry for both sides of the connection.

## 2. Current SDK And Server Abilities

The current Java SDK declares support for:

| SDK ability | Meaning |
|-------------|---------|
| `SDK_CLIENT_FUZZY_WATCH` | Client can use fuzzy watch for Config or Naming. |
| `SDK_CLIENT_DISTRIBUTED_LOCK` | Client can use the distributed lock feature. |
| `SDK_MCP_REGISTRY` | Client can use MCP registry runtime features. |
| `SDK_AGENT_REGISTRY` | Client can use the legacy A2A Agent and AgentCard runtime features. |

The current server declares support for:

| Server ability | Meaning |
|----------------|---------|
| `SERVER_PERSISTENT_INSTANCE_BY_GRPC` | Persistent Naming instance register/deregister is supported by gRPC. |
| `SERVER_FUZZY_WATCH` | Config or Naming fuzzy watch is supported. |
| `SERVER_DISTRIBUTED_LOCK` | Distributed Lock is supported. |
| `SERVER_MCP_REGISTRY` | MCP registry operations are supported. |
| `SERVER_AGENT_REGISTRY` | Legacy A2A Agent and AgentCard registry operations are supported. |
| `SERVER_AGENT_CARD_V1` | A2A AgentCard 1.0 protocol fields are supported. |

Adding a new ability requires both a named key and a domain rule that explains
what behavior is gated by the ability.

### 2.1 Experimental Agent/RAD Target Abilities

The [Agent API Spec](../ai/agent-api-spec.md) approves the following target
abilities for the Nacos 3.3 line. They describe the target contract and are not
current runtime abilities until the corresponding constants and handlers are
implemented.

| Mode | Constant | Wire key | Meaning |
|---|---|---|---|
| `SERVER` | `SERVER_AGENT_DISCOVERY_V1` | `agentDiscoveryV1` | Server accepts RAD Search, Discover, and Watch payloads. |
| `SERVER` | `SERVER_AGENT_ENDPOINT_V1` | `agentEndpointV1` | Server accepts RAD runtime Endpoint publication payloads. |
| `SDK_CLIENT` | `SDK_AGENT_DISCOVERY_V1` | `agentDiscoveryV1` | SDK accepts RAD discovery push payloads. |

Ability names are scoped by mode, so the same `agentDiscoveryV1` wire key is
valid in both `SERVER` and `SDK_CLIENT` tables. Legacy
`SERVER_AGENT_REGISTRY`, `SERVER_AGENT_CARD_V1`, and `SDK_AGENT_REGISTRY`
continue to gate only the old A2A contract. They are not a fallback for any RAD
operation.

## 3. gRPC Negotiation Flow

The runtime client negotiates abilities during gRPC connection setup:

1. The client opens a channel to the selected server and sends
   `ServerCheckRequest`.
2. The server returns `ServerCheckResponse` with a connection id and a flag that
   indicates whether ability negotiation is supported.
3. The client opens the bidirectional stream and sends `ConnectionSetupRequest`
   with client version, labels, namespace/tenant, and the current client ability
   table for the connection mode.
4. If the server supports ability negotiation, the client waits for
   `SetupAckRequest`.
5. `SetupAckRequest` carries the server ability table. The client stores it on
   the current connection.
6. If the server declared ability negotiation support but no ability table is
   received before the configured timeout, the client must abandon that
   connection attempt.
7. If the server does not support ability negotiation, the client may complete
   setup for compatibility. Ability checks on that connection resolve to
   `UNKNOWN` unless the implementation defines an explicit legacy fallback.

Ability state is connection-scoped. Reconnect creates a new connection and must
refresh the ability table.

## 4. Ability Status Semantics

Client code observes ability status as:

| Status | Meaning | Required behavior |
|--------|---------|-------------------|
| `SUPPORTED` | The current connection explicitly supports the ability. | The gated feature may use the optimized or new path. |
| `NOT_SUPPORTED` | The current connection explicitly does not support the ability. | The feature must use a documented fallback or fail with a clear unsupported error. |
| `UNKNOWN` | No ability table is available or the key is absent. | The feature must not assume support. It may use a legacy fallback only when the domain spec permits it. |

Unknown is not success. New features should prefer fail-fast unsupported errors
over sending requests that the selected server may not understand.

## 5. Feature Gating Rules

Domain clients must check server abilities before using optional or versioned
features:

- Naming persistent instance registration should use gRPC only when
  `SERVER_PERSISTENT_INSTANCE_BY_GRPC` is supported; otherwise it may use the
  documented HTTP compatibility path.
- Config and Naming fuzzy watch must require `SERVER_FUZZY_WATCH`.
- Distributed Lock must require `SERVER_DISTRIBUTED_LOCK` because the feature is
  experimental and not universally available.
- AI MCP registry operations must require `SERVER_MCP_REGISTRY`.
- Legacy A2A Agent and AgentCard operations must require
  `SERVER_AGENT_REGISTRY`.
- A2A AgentCard 1.0 fields should require `SERVER_AGENT_CARD_V1` or use an
  explicitly documented compatibility conversion.
- RAD Search, Discover, and Watch requests must require
  `SERVER_AGENT_DISCOVERY_V1`.
- RAD runtime Endpoint registration and deregistration must require
  `SERVER_AGENT_ENDPOINT_V1`.
- The server may push either `SNAPSHOT` or `TERMINATED`
  `AgentDiscoveryNotifyRequest` only when the client advertises
  `SDK_AGENT_DISCOVERY_V1`. The ability covers acknowledgement and isolated
  termination of one `watchKey`; it never authorizes closing the shared
  Payload connection for a terminal Watch.

Feature code should not cache a positive ability result beyond the current
connection. It should query the runtime connection ability when the operation is
about to execute or when a cached value is known to belong to the current
connection.

After reconnect, the client must negotiate the ability again before restoring
Watch intent. A restored subscription obtains a new connection-scoped opaque
`watchKey` from `AgentSubscribeResponse`; the prior key must not be reused.

## 6. Compatibility Rules

Ability negotiation is a mixed-version compatibility mechanism. It should be
used before adding ad hoc version checks. Version strings may be logged or used
for diagnostics, but runtime behavior should prefer ability status whenever an
ability key exists.

Legacy fallback must be documented by the domain spec. A fallback can be
removed only according to the
[Compatibility And Deprecation Spec](../design/compatibility-deprecation-spec.md).

## 7. Pending Issues

- The public list of ability keys should be generated from source to avoid
  documentation drift.
