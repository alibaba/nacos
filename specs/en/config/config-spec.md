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

# Config Spec

This document defines the top-level Nacos Config domain. It refines the
[Core Capabilities Spec](../design/core-capabilities-spec.md) and the
[Resource Model Spec](../design/resource-model-spec.md) for dynamic
configuration.

## 1. Positioning

Nacos Config is the dynamic configuration domain. It stores configuration
content as durable resources and provides lifecycle capabilities including
publish, query, subscription-based distribution, gray release, delete, history,
import, export, clone, capacity, and operational diagnostics.

Config is a first-class Nacos domain. It is not a generic document store, object
storage system, secret management system, service discovery model, or AI
resource model.

## 2. Resource Identity

Config uses the microservice resource hierarchy:

```text
namespaceId -> groupName -> dataId
```

The concrete identity rules are defined by the
[Config Resource Spec](config-resource-spec.md).

## 3. Responsibilities

| Responsibility | Meaning | Detailed spec |
| --- | --- | --- |
| Resource model | Define Config identity, metadata, content, md5, type, tags, and validation. | [Config Resource Spec](config-resource-spec.md) |
| Publish and query | Define create, update, CAS, delete, query, list, import, export, clone, and query-chain behavior. | [Config Publish And Query Spec](config-publish-query-spec.md) |
| Listener and watch | Define exact config listening, change push, fuzzy watch, and client synchronization semantics. | [Config Listener And Watch Spec](config-listener-watch-spec.md) |
| Gray release | Define formal config, gray config, beta, tag, rule matching, and gray query precedence. | [Config Gray Release Spec](config-gray-release-spec.md) |
| Persistence and history | Define persistent storage, local dump cache, md5 state, history, recovery, and cleanup expectations. | [Config Persistence, Dump, And History Spec](config-persistence-history-spec.md) |
| Consistency and visibility | Define write visibility, dump ordering, cluster propagation, and runtime query visibility. | [Config Consistency, Dump, And Visibility Spec](config-consistency-dump-visibility-spec.md) |
| Capacity and operations | Define quota, size limit, usage accounting, metrics, listener diagnostics, local cache operation, and Derby ops boundaries. | [Config Capacity And Ops Spec](config-capacity-ops-spec.md) |

## 4. Design Principles

### 4.1 Config Content Is A Black Box

Config treats `content` as an opaque payload. Nacos owns the resource lifecycle:
publish, query, subscription-based distribution, gray release, delete, history,
and related management operations. It must not parse, merge, partially update,
or define behavior around individual business items inside a configuration file.

The `type` field describes content type for presentation and response handling;
it does not make Nacos the owner of a business schema inside the content. If a
deployment requires item-aware processing, validation, transformation, or
side-effect behavior, that requirement should be implemented through extensions
or downstream systems. The community should not define or develop core Config
features that require Nacos to understand specific internal configuration items.

### 4.2 Durable Source, Runtime Cache

Config content must be durably persisted. Runtime reads are served through the
Config cache and local dump files so high-frequency client queries and change
checks do not depend directly on broad database scans.

The persistence layer remains the durable source of truth. The local dump cache
is a serving and recovery layer and must be refreshed from persistence during
startup and on change events. Local change event semantics are defined by the
[Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md);
background dump and refresh execution is defined by the
[Task Execution Spec](../design/foundation-task-execution-spec.md).

### 4.3 Content Version By md5

Config uses `md5` as the content version indicator for client change detection
and CAS publish. A listener compares the client-held md5 with server-side state;
a CAS publish compares the supplied md5 with the stored md5 before updating.

### 4.4 Change Push Is A Hint

Config change push notifies clients that a resource may have changed. The push
payload must not be treated as authoritative configuration content. Clients
must query the Config resource after receiving a change notification.

### 4.5 Runtime And Management Separation

Runtime clients should query known configs and listen to known or pattern-based
configs. Broad list, search, import, export, clone, listener diagnostics,
history, capacity, metrics, and local cache operations are management
capabilities and belong to Admin API, Console API, or Maintainer SDK surfaces.
Runtime client connection, listener recovery, snapshot, and failover behavior is
defined by the [Client Runtime Specs](../client/README.md).

### 4.6 Extensible Cross-cutting Behavior

Config integrates extension mechanisms without moving Config ownership out of
the Config domain:

| Concern | Rule |
| --- | --- |
| Encryption | Config owns content identity and persistence; encryption algorithms are provided by the [Config Encryption Plugin Spec](../plugin/config-encryption-plugin-spec.md). |
| Config change notification | Config owns local change events defined by the [Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md); external callbacks are provided by the [Config Change Plugin Spec](../plugin/config-change-plugin-spec.md). |
| Datasource dialect | Config owns repository semantics; SQL dialect behavior is provided by the [Datasource Dialect Plugin Spec](../plugin/datasource-dialect-plugin-spec.md). |
| Authorization | Config APIs and gRPC handlers use `SignType.CONFIG` and must follow the [Auth And Permission Spec](../auth/auth-permission-spec.md). |
| Control | High-frequency publish, query, listen, push, and fuzzy watch flows should expose stable control points for the [Control Plugin Spec](../plugin/control-plugin-spec.md). |

## 5. Interface Surfaces

| Surface | Scope |
| --- | --- |
| HTTP Open API | `/v3/client/cs/config` query for custom HTTP clients. This surface does not provide HTTP long polling or broad management operations. |
| HTTP Admin API | `/v3/admin/cs/*` configuration CRUD, list/search, history, listener diagnostics, capacity, metrics, and operations. |
| gRPC API | Runtime query, publish compatibility, remove compatibility, exact listen, fuzzy watch, and server push messages. See the [gRPC API Spec](../grpc-api/api-spec.md). |
| Client SDK | Runtime application access through `ConfigService`, including query, listen, local snapshot, filters, and compatibility write methods. See the [SDK Spec](../sdk/sdk-spec.md). |
| Maintainer SDK | Management integration through `ConfigMaintainerService` and related services. |
| Console API | UI-oriented management workflows. Console APIs may shape presentation data, but must not redefine Config semantics. |

## 6. Boundaries

- Config does not own service discovery, service instance lifecycle, or health
  checks. Those belong to Naming.
- Config does not own AI resource identity. A storage compatibility mapping
  used by an AI resource must not make that AI resource a normal Config
  resource in new specs.
- Config encryption protects configuration content through plugin-defined
  algorithms, but Config is not a full secret lifecycle or KMS domain.
- Config metadata such as `appName`, `desc`, `configTags`, `type`, `use`,
  `effect`, and `schema` does not change resource identity.
- Gray release state is subordinate to a Config resource. It must not create a
  second top-level Config identity.

## 7. Foundation Alignment

Shared datasource, embedded/external storage, repository, dump, and cache
boundaries are defined by the
[Persistence And Dump Spec](../design/foundation-persistence-dump-spec.md).
Config-specific write visibility, dump recovery, and cluster change propagation
are defined by the
[Config Consistency, Dump, And Visibility Spec](config-consistency-dump-visibility-spec.md).
Shared task execution and local event boundaries are defined by the
[Task Execution Spec](../design/foundation-task-execution-spec.md) and
[Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md).
Shared observability boundaries are defined by the
[Observability Hooks Spec](../design/foundation-observability-hooks-spec.md).
Config trace and audit fields should follow the shared field guidance in that
spec and must not include full Config content.
