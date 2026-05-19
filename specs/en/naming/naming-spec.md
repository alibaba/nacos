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

# Naming Spec

This document defines the top-level Nacos Naming domain. It refines the
[Nacos Design Spec](../design/nacos-design-spec.md) and the
[Resource Model Spec](../design/resource-model-spec.md) for service discovery.

## 1. Positioning

Nacos Naming is the service discovery domain. It manages services, clusters,
instances, service metadata, instance metadata, health status, subscribers,
publishers, and client-side service views.

Naming is a first-class Nacos domain. It is not a general traffic governance
engine, service mesh control plane, configuration store, or AI registry model.
Naming may provide internal filtering, client-side selectors, weights, health
protection, and metadata, but those features are scoped to service discovery.

## 2. Resource Identity

Naming uses the microservice resource hierarchy:

```text
namespaceId -> groupName -> serviceName
```

Cluster and instance are subordinate resources:

```text
namespaceId -> groupName -> serviceName -> clusterName -> instance
```

The concrete identity rules are defined by the
[Naming Resource Spec](naming-resource-spec.md).

## 3. Service Types

Every Naming service is one of the following service types:

| Service type | Meaning | Primary state path |
| --- | --- | --- |
| Ephemeral service | Non-persistent runtime service. Instances are owned by live clients and disappear with heartbeat or connection expiration. | AP-oriented ephemeral client state and Distro synchronization. |
| Persistent service | Durable service. Instances are managed as persistent resources and can recover from server-side snapshots. | CP-oriented persistent client state and metadata persistence. |

The service type is a service-level semantic attribute. Instance `ephemeral`
input must match the owning service type. Implementations may keep an
`ephemeral` field on instances for compatibility and routing, but new behavior
must not treat it as an independent per-instance policy that can mix types
inside one service.

## 4. Spec Layers

### 4.1 Common Specs

| Responsibility | Meaning | Detailed spec |
| --- | --- | --- |
| Resource model | Define service, cluster, instance, client, publisher, and subscriber identity. | [Naming Resource Spec](naming-resource-spec.md) |
| Discovery and subscription | Define query, subscribe, push, fuzzy watch, local cache, and failover views. | [Naming Discovery And Subscription Spec](naming-discovery-subscription-spec.md) |
| Health and protection | Define health state, active checks, enabled state, weight, internal filtering, and protection threshold. | [Naming Health And Protection Spec](naming-health-protection-spec.md) |
| Metadata and selector | Define service, cluster, instance metadata, runtime and operational metadata priority, reserved keys, internal filtering, legacy API selector, and client-side selector behavior. | [Naming Metadata And Selector Spec](naming-metadata-selector-spec.md) |
| Operations | Define client diagnostics, subscriber diagnostics, metrics, switches, log level, and cleanup boundaries. | [Naming Ops Spec](naming-ops-spec.md) |

### 4.2 Service-Type-Specific Specs

| Responsibility | Meaning | Detailed spec |
| --- | --- | --- |
| Instance lifecycle | Define common lifecycle plus service-type-specific registration, heartbeat, deregister, update, batch registration, and cleanup behavior. | [Naming Instance Lifecycle Spec](naming-instance-lifecycle-spec.md) |
| Consistency and client state | Define common client identity plus ephemeral-service AP state, persistent-service CP state, indexes, and snapshots. | [Naming Consistency And Client State Spec](naming-consistency-client-spec.md) |
| Ephemeral Distro consistency | Define ephemeral ownership, Distro sync, verify, anti-entropy, cleanup, and AP visibility. | [Naming Ephemeral Distro Consistency Spec](naming-ephemeral-distro-consistency-spec.md) |
| Persistent CP consistency | Define persistent instance CP writes, metadata groups, snapshots, recovery, and visibility. | [Naming Persistent CP Consistency Spec](naming-persistent-cp-consistency-spec.md) |

## 5. Design Principles

### 5.1 Service Is The Discovery Unit

A Naming service is the addressable discovery unit. Instances must be
interpreted under their service scope and cluster scope. An instance without
`namespaceId`, `groupName`, and `serviceName` context is not a complete Naming
resource.

### 5.2 Runtime And Management Separation

Runtime clients register or deregister their own instances, query known
services, and subscribe to service changes. Service creation, service deletion,
service metadata, cluster metadata, client diagnostics, subscriber diagnostics,
metrics, switches, and log-level operations are management capabilities and
belong to Admin API, Console API, or Maintainer SDK surfaces.

The HTTP Open API exists for custom clients that cannot use gRPC. It provides
register, heartbeat, deregister, and list operations for a specified service.
It must not become a broad service-management or push-subscription API.

### 5.3 Ephemeral And Persistent Services Are Different Semantics

Ephemeral services are non-persistent runtime services whose instances are bound
to client liveness and use the AP-oriented ephemeral path. Persistent services
are durable services whose instances use the CP-oriented persistent path. APIs,
SDKs, and storage code must preserve this distinction instead of treating
`ephemeral` as only a display flag.

### 5.4 Discovery View Is Filtered State

Naming discovery results are not a raw storage dump. Query and subscribe results
may be filtered by cluster, enabled state, health state, internal server-side
filtering rules, and protection threshold. SDK selection adds client-side
selectors and weighted selection on top of the server-provided `ServiceInfo`
view.

### 5.5 Push Updates The Client View

gRPC subscription push carries updated `ServiceInfo` state for a subscribed
service. Clients store the pushed state in local memory and disk cache, compare
instance diffs, notify listeners, and may re-query on reconnect, cache miss, or
polling fallback. HTTP Open API does not provide long polling or push
subscription.

### 5.6 Extensible Cross-cutting Behavior

Naming integrates extension mechanisms without moving Naming ownership out of
the Naming domain:

| Concern | Rule |
| --- | --- |
| Authorization | Naming APIs and gRPC handlers use Naming resources and must follow the [Auth And Permission Spec](../auth/auth-permission-spec.md). |
| Visibility | Range queries over services, instances, subscribers, or clients should apply [visibility](../auth/visibility-plugin-spec.md) rules when a visibility plugin is enabled. |
| Control | High-frequency register, deregister, query, subscribe, push, and list flows should expose stable control points for the [Control Plugin Spec](../plugin/control-plugin-spec.md). |
| Trace and metrics | Naming lifecycle events should follow the [Trace Plugin Spec](../plugin/trace-plugin-spec.md); shared metrics and diagnostics follow the [Observability Hooks Spec](../design/foundation-observability-hooks-spec.md). |
| Health check extension | Health checker types are loaded through the health checker registry and must keep the service/cluster/instance resource model intact. |
| Addressing | Client server discovery should follow the [Addressing Plugin Spec](../plugin/addressing-plugin-spec.md). |

## 6. Interface Surfaces

| Surface | Scope |
| --- | --- |
| HTTP Open API | `/v3/client/ns/instance` register, heartbeat, deregister, and list for custom runtime clients. |
| HTTP Admin API | `/v3/admin/ns/*` service, instance, cluster, health, client, and operation management. |
| gRPC API | Runtime register, batch register, persistent register, query, subscribe, fuzzy watch, and server push. See the [gRPC API Spec](../grpc-api/api-spec.md). |
| Client SDK | Runtime application access through `NamingService`, including register, deregister, query, subscribe, fuzzy watch, local cache, and failover. See the [SDK Spec](../sdk/sdk-spec.md) and [Client Runtime Specs](../client/README.md). |
| Maintainer SDK | Management integration through naming maintainer services. |
| Console API | UI-oriented management workflows. Console APIs may shape presentation data, but must not redefine Naming semantics. |

## 7. Boundaries

- Naming does not own configuration content or Config listener semantics.
- Naming does not own AI resource identity. AI resources may refer to Naming
  services or endpoints, but that reference must not make the AI resource a
  normal Naming service.
- Naming metadata is key-value discovery metadata. Only reserved metadata keys
  explicitly defined by Naming can change core behavior.
- Naming health checks determine discovery availability; they are not a general
  application observability or SLA system.
- Internal filtering and SDK selectors are discovery-side filtering and
  selection tools. Legacy API-defined service selectors are compatibility
  fields and must not become the basis for new traffic policy semantics.

## 8. Related Specs

- [Naming Ephemeral Distro Consistency Spec](naming-ephemeral-distro-consistency-spec.md)
- [Naming Persistent CP Consistency Spec](naming-persistent-cp-consistency-spec.md)
- [Runtime Push And Reconnect Spec](../client/runtime-push-reconnect-spec.md)
- [AP Consistency Spec](../design/foundation-ap-consistency-spec.md)
- [CP Consistency Spec](../design/foundation-cp-consistency-spec.md)
- [Internal RPC And Cluster Request Spec](../design/foundation-internal-rpc-spec.md)
