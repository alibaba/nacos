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

# Naming Resource Spec

This document defines Naming resource identity, fields, validation, and
metadata.

## 1. Service Identity

A Naming service is identified by:

```text
namespaceId -> groupName -> serviceName
```

| Field | Meaning | Notes |
| --- | --- | --- |
| `namespaceId` | Namespace that owns the service. | Blank or omitted values are processed as the default namespace id when the interface supports it. |
| `groupName` | Business group inside a namespace. | New public specs and v3 interfaces use `groupName`; compatibility keys may still use `group@@serviceName`. |
| `serviceName` | Service resource name. | `serviceName` is the `resourceName` of Naming service. |

The identity is stable. Changing `namespaceId`, `groupName`, or `serviceName`
is a new service resource, not an in-place metadata update.

## 2. Service Types

Service type is a property of the service resource:

| Service type | `ephemeral` value | Resource meaning |
| --- | --- | --- |
| Ephemeral service | `true` | Non-persistent service whose instances are runtime publications owned by live clients. |
| Persistent service | `false` | Durable service whose instances are managed through the persistent path. |

A service must not contain both ephemeral and persistent instance semantics.
Registration input, storage routing, health checking, cleanup, and consistency
logic must be derived from the service type.

## 3. Service Fields

| Field | Meaning | Identity field |
| --- | --- | --- |
| `protectThreshold` | Health protection threshold used when healthy ratio is too low. | No |
| `selector` | Legacy API-defined service selector. New behavior must not depend on this field; see the [Naming Metadata And Selector Spec](naming-metadata-selector-spec.md). | No |
| `metadata` / `extendData` | Service metadata key-value map. | No |
| `ephemeral` | Service type. `true` means ephemeral service; `false` means persistent service. | Not part of equality, but it changes runtime semantics. |
| `clusters` | Cluster metadata map keyed by cluster name. | No |

Service equality in the v2 model uses `namespace`, `group`, and `name`.
However, service creation records whether the service is ephemeral or
persistent, and instance registration must match that service type.

## 4. Cluster Resource

A cluster is a subordinate resource under a service:

```text
namespaceId -> groupName -> serviceName -> clusterName
```

| Field | Meaning |
| --- | --- |
| `clusterName` | Cluster name. Defaults to `DEFAULT` when omitted by instance input. |
| `healthChecker` | Health checker definition, such as TCP, HTTP, MySQL, or NONE. |
| `healthyCheckType` | Serialized health checker type. |
| `healthyCheckPort` | Port used by active health checks when instance port is not used. |
| `useInstancePortForCheck` | Whether active checks use each instance port. |
| `metadata` / `extendData` | Cluster metadata key-value map. |

Cluster metadata does not create a top-level service. It is always scoped by the
owning service.

## 5. Instance Resource

An instance is subordinate to a service and cluster:

```text
namespaceId -> groupName -> serviceName -> clusterName -> ip:port
```

| Field | Meaning |
| --- | --- |
| `ip` | Instance host address. Required. |
| `port` | Instance port. Required, range `0..65535`. |
| `clusterName` | Instance cluster. Defaults to `DEFAULT`. |
| `weight` | Instance traffic weight. Defaults to `1.0`. |
| `healthy` | Runtime health status. Defaults to `true`, but may be changed by heartbeat or active health checks. |
| `enabled` | Whether the instance can be returned to runtime consumers. Defaults to `true`. |
| `ephemeral` | Compatibility and routing field that must match the owning service type. Defaults to `true` unless server-side default settings change the HTTP form default. |
| `metadata` | Instance metadata key-value map. |
| `instanceId` | Optional generated or user-provided runtime identifier. |

Public identity should be service scope plus `clusterName`, `ip`, and `port`.
`instanceId` is a runtime identifier and must not replace the canonical
identity fields in new APIs.

## 6. Client, Publisher, And Subscriber

Naming keeps runtime client views:

| Runtime object | Meaning |
| --- | --- |
| Client | Server-side runtime state object bound to one gRPC connection or one IP-port compatibility identity. |
| Publisher | A client that registers an instance for a service. |
| Subscriber | A client that subscribes to service changes. |

Client id is implementation-specific. gRPC Clients use connection ids.
HTTP/IP-port compatibility Clients use IP-port derived client ids. New public
APIs should not require users to construct internal client ids except in
maintainer diagnostics. See the
[Naming Consistency And Client State Spec](naming-consistency-client-spec.md)
for the Client state and index model.

## 7. Validation Rules

- `serviceName` and `groupName` must be non-blank for service operations.
- `ip` must be non-blank for instance operations.
- `port` must be in the range `0..65535`.
- `clusterName`, when provided, must match the Naming cluster-name character
  rule.
- Instance registration must match the owning service type. A persistent
  instance must not be registered into an ephemeral service, and an ephemeral
  instance must not be registered into a persistent service.
- Heartbeat timeout and delete timeout must be greater than or equal to the
  heartbeat interval.
- Batch registration is an ephemeral-service capability. The current Java SDK
  rejects persistent instances, and the server gRPC handler routes batch
  registration through the ephemeral operation service.

## 8. Internal Keys

Implementation code may use:

- grouped service name: `group@@serviceName`;
- service key: `namespace@@group@@serviceName`;
- service info key: `group@@serviceName@@clusters`;
- metadata id: generated from instance `ip`, `port`, and `clusterName`.

These keys are implementation and compatibility keys. New APIs and SDK
contracts should prefer explicit `namespaceId`, `groupName`, `serviceName`,
`clusterName`, `ip`, and `port` fields.

## 9. Related Specs

- [Resource Model Spec](../design/resource-model-spec.md)
- [Naming Instance Lifecycle Spec](naming-instance-lifecycle-spec.md)
- [Naming Metadata And Selector Spec](naming-metadata-selector-spec.md)
- [Naming Consistency And Client State Spec](naming-consistency-client-spec.md)
