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

# Naming Metadata And Selector Spec

This document defines Naming metadata, metadata priority, and selector
categories.

## 1. Metadata Levels And Sources

Naming metadata exists at three resource levels:

| Level | Scope | Owner |
| --- | --- | --- |
| Service metadata | Service-level discovery metadata, protect threshold, legacy selector field, and cluster map. | Admin API, Console API, Maintainer SDK |
| Cluster metadata | Health checker, check port behavior, and cluster metadata. | Admin API, Console API, Maintainer SDK |
| Instance metadata | Instance weight, enabled state, and extended metadata. | Runtime registration and management APIs |

Metadata does not change service identity. Metadata changes should publish
service or instance information change events so storage indexes, push, and
diagnostics can refresh. Local event delivery is defined by the
[Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md).

Naming also distinguishes two metadata sources:

| Source | Meaning | Persistence | Priority |
| --- | --- | --- | --- |
| Runtime metadata | Metadata submitted by the runtime publisher during instance registration or heartbeat. It mainly describes deployment-time and runtime state controlled by the registering process. | Bound to the runtime publisher and its service type. | Lower |
| Operational metadata | Metadata written through Nacos management paths, such as Admin API, Console API, Maintainer SDK, or metadata persistence. It represents operator or developer intent. | Stored by Nacos and can survive runtime client disappearance until cleanup rules apply. | Higher |

When the same metadata key exists in both runtime metadata and operational
metadata, the operational value must win in the served Naming view. Operational
metadata has higher priority because it is written as an explicit management
override and must be persistent or memoized by Nacos.

For service-level metadata, the formal service metadata is operational
metadata. For instance-level metadata, the runtime registration metadata is the
base view and operational instance metadata overlays it.

## 2. Reserved Metadata Keys

Most metadata is user-defined key-value data. Naming reserves the following
instance metadata keys for core behavior:

| Key | Meaning |
| --- | --- |
| `preserved.register.source` | Registry source of the instance. |
| `preserved.heart.beat.interval` | Heartbeat interval override. |
| `preserved.heart.beat.timeout` | Heartbeat unhealthy timeout override. |
| `preserved.ip.delete.timeout` | Heartbeat deletion timeout override. |
| `preserved.instance.id.generator` | Instance id generator selection. |

New core behavior must not be bound to arbitrary user metadata keys. If a
metadata key changes Naming behavior, it must be reserved and documented.

## 3. Selector Categories

Naming currently has three selector-like concepts:

| Category | Scope | Spec status |
| --- | --- | --- |
| Internal instance filtering | Server-side implementation filters that shape discovery views, such as cluster, enabled, health, protection threshold, and internal filtering hooks. | Formal Naming behavior. |
| API-defined service selector | Legacy service `selector` input accepted by older service APIs and SDK maintainer methods. | Compatibility only; pending removal. |
| Client-side selector | SDK-side `NamingSelector` used by local subscribe/unsubscribe and listener matching. | Formal SDK extension behavior. |

Internal instance filtering is part of server discovery semantics. It must
preserve the service, cluster, instance, health, enabled, service type, and
protection semantics defined by other Naming specs.

API-defined service selector must not be used to define new server behavior.
New APIs and specs should model filtering explicitly or use client-side
selectors where the behavior is local to the SDK.

Client-side selector is an SDK extension point. It filters local listener
notification or selection results and must not mutate server-side service,
instance, metadata, or consistency state.

## 4. Cluster Health Checker Metadata

Cluster metadata controls active health check behavior:

- checker type and serialized checker fields;
- whether to use instance port or a fixed check port;
- cluster-level extended metadata.

Health checker metadata belongs to the cluster. It must not be copied into
instance identity or service identity.

## 5. Metadata Persistence

Service metadata, cluster metadata, and instance metadata operations are written
through the CP metadata path. Metadata may outlive runtime clients temporarily.
Expired metadata cleanup removes metadata after its owning service or instance
becomes detached for the configured expiration window.

Runtime metadata follows the lifecycle of the runtime publisher. Operational
metadata follows the metadata persistence path and may overlay runtime metadata
after recovery.

## 6. Pending Removal

- API-defined service selector fields and request parameters are legacy
  compatibility behavior. They should be deprecated in new API and SDK specs,
  and removed from the formal Naming behavior after compatibility requirements
  allow it, following the
  [Compatibility And Deprecation Spec](../design/compatibility-deprecation-spec.md).

## 7. Related Specs

- [Naming Resource Spec](naming-resource-spec.md)
- [Naming Health And Protection Spec](naming-health-protection-spec.md)
- [Naming Consistency And Client State Spec](naming-consistency-client-spec.md)
- [Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md)
- [Compatibility And Deprecation Spec](../design/compatibility-deprecation-spec.md)
