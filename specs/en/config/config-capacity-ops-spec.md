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

# Config Capacity And Ops Spec

This document defines Config capacity and operation semantics.

## 1. Capacity Scope

Config capacity protects cluster, namespace, and group resources from unbounded
growth.

| Scope | Meaning |
| --- | --- |
| Cluster | Total formal Config count in the cluster. |
| Namespace | Formal Config count under one namespace. |
| Group | Formal Config count under one group when namespace-specific capacity is not used. |

Capacity applies to formal Config records. Gray variants are release state of an
existing Config and are not counted as independent formal configs by the
capacity aspect.

## 2. Limits

Capacity contains:

| Field | Meaning |
| --- | --- |
| `quota` | Maximum number of Config records in the scope. `0` means use default. |
| `usage` | Current counted number of Config records in the scope. |
| `maxSize` | Maximum single Config content size in bytes. `0` means use default. |

Default values are server configuration:

| Configuration | Default |
| --- | --- |
| `defaultClusterQuota` | `100000` |
| `defaultGroupQuota` | `200` |
| `defaultTenantQuota` | `200` |
| `defaultMaxSize` | `100 * 1024` bytes |
| `correctUsageDelay` | `600` seconds |
| `initialExpansionPercent` | `100` |

## 3. Deprecated Aggregation Fields

Aggregation config is not part of the standard Config capability model. Existing
code, APIs, or database schemas may still contain `maxAggrCount`,
`maxAggrSize`, `defaultMaxAggrCount`, `defaultMaxAggrSize`, or related
aggregation paths for compatibility, but these are legacy redundant design
artifacts and are pending removal.

New specs, APIs, SDKs, and user-facing documents must not define formal behavior
based on aggregation config. Some database fields may remain temporarily to
avoid forcing users to adjust schemas frequently. The future standard database
schema should remove these fields when the compatibility window allows, following
the [Compatibility And Deprecation Spec](../design/compatibility-deprecation-spec.md).

## 4. Enforcement

Capacity management has two switches:

- `isManageCapacity` enables usage accounting around publish and delete.
- `isCapacityLimitCheck` enables quota and size rejection.

When limit checking is enabled, inserting a new formal config must:

1. check and increment cluster usage;
2. check content size;
3. check and increment namespace usage when namespace is non-blank, otherwise
   group usage;
4. roll back usage if the publish fails.

Updating an existing formal config checks content size without increasing usage.
Deleting an existing formal config decrements usage and rolls back if the delete
fails.

Usage correction runs periodically because concurrent delete and asynchronous
write flows can make counters temporarily inaccurate.

## 5. Capacity API

Capacity Admin API can query or update capacity for a namespace or group. At
least one of `namespaceId` or `groupName` must be provided. If the capacity
record does not exist, the server may initialize it and then return the effective
capacity with defaults applied.

Capacity API is a management API and must not be exposed through runtime Client
SDK surfaces.

## 6. Ops APIs

Config operation APIs are administrative repair or diagnostics surfaces:

| Operation | Rule |
| --- | --- |
| Local cache dump | Triggers a full local cache refresh from persistence according to the [Persistence And Dump Spec](../design/foundation-persistence-dump-spec.md). |
| Log level update | Changes Config module log level. |
| Derby query | Allows bounded `SELECT` statements only when embedded storage is active and `nacos.config.derby.ops.enabled=true`. |
| Derby import | Imports Derby data only when embedded storage is active and Derby ops is enabled. |
| Listener diagnostics | Queries listener state by IP or Config identity. |
| Metrics | Queries client cache and snapshot metrics locally or across [cluster members](../design/foundation-cluster-membership-spec.md), following the [Observability Hooks Spec](../design/foundation-observability-hooks-spec.md). |

Derby ops are maintainer-only behavior. They must require Admin permission and
must remain disabled by default.

## 7. Related Specs

- [HTTP API Spec](../http-api/api-spec.md)
- [Auth And Permission Spec](../auth/auth-permission-spec.md)
- [Persistence And Dump Spec](../design/foundation-persistence-dump-spec.md)
- [Observability Hooks Spec](../design/foundation-observability-hooks-spec.md)
- [Control Plugin Spec](../plugin/control-plugin-spec.md)
