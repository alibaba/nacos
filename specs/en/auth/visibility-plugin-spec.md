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

# Visibility Plugin Spec

## Scope

The visibility plugin category controls whether a resource is visible to a
caller. It is separate from auth:

- Auth decides identity and permission for a target resource/action.
- Visibility decides whether the target resource, or a resource in a range
  query, should be visible to that identity.

Visibility is especially important for AI registry resources, where users may
create resources that are private to an owner, public to readers, or visible
through explicit authorization.

Visibility complements the [Auth And Permission Spec](auth-permission-spec.md)
and follows the common lifecycle rules in the
[Nacos Plugin Spec](../plugin/plugin-spec.md). It can cooperate with, but does
not replace, an [auth plugin](auth-plugin-spec.md).

Visibility must be applied at data-query time. List and search APIs must not
page over the raw candidate set and then filter the page in memory, because that
produces incorrect `totalCount`, empty pages, and unpredictable latency.

## Resource Model

A visibility-aware resource must follow the
[Nacos resource model](../design/resource-model-spec.md) and provide:

| Field | Meaning |
|-------|---------|
| `namespaceId` | Namespace that owns the resource. |
| `resourceType` | Resource category inside the namespace. |
| `resourceName` | Stable resource name inside the type. |
| `scope` | Visibility scope, currently `PUBLIC` or `PRIVATE`. |
| `owner` | Identity that owns the resource. |

This follows the Nacos resource hierarchy:

```text
NamespaceId -> resourceType -> resourceName
```

## Visibility SPI

A visibility plugin implements `VisibilityService`.

| Method | Requirement |
|--------|-------------|
| `getVisibilityServiceName()` | Return the stable plugin name. |
| `init(properties)` | Deprecated legacy initialization callback for implementations that do not use unified plugin configuration. |
| `resolveDefaultScopeForCreate(identity, apiType, resourceType)` | Decide the default scope when a resource is created without an explicit scope. |
| `validateVisibility(identity, action, apiType, resource)` | Validate visibility for one resource. |
| `adviseQuery(identity, action, apiType, queryContext)` | Return query predicates and explicit resources for range queries. |

The plugin is discovered by SPI and registered with plugin type `visibility`.
The visibility service name is selected at startup by:

```properties
nacos.plugin.visibility.type=nacos
```

The selection is restart-effective. It determines the implementation requested
by the AI domain and the default enabled state in unified plugin management; it
is not an implementation-owned `ConfigItemDefinition`.

## Actions

Visibility uses the same read/write vocabulary as auth:

| Action | Meaning |
|--------|---------|
| `r` | Read or list visible resources. |
| `w` | Create, update, delete, or change visibility-sensitive resource state. |

Write visibility must be stricter than read visibility. Public read access does
not imply public write access.

## Query Advisory

Range queries must not load all resources and filter only in memory when the
storage layer can apply visibility predicates. `QueryAdvisor` carries:

| Field | Purpose |
|-------|---------|
| `BaseVisibilityPredicate` | Base predicate such as all resources, public only, owner only, or public plus owner. |
| `AuthorizedResources` | Explicitly authorized resource names that should be included. |

The API or storage adapter that lists resources must combine both parts without
leaking private resources.

The default AI integration converts `QueryAdvisor` to repository `QueryCondition`
before count and page queries run. The base predicate maps as follows:

| Predicate | Query behavior |
|-----------|----------------|
| `ALL` | Add no visibility condition. |
| `PUBLIC` | Restrict to `scope=PUBLIC`, or empty result if the caller requested a conflicting scope. |
| `OWNER` | Restrict to `owner=identity`, or empty result if identity is absent or conflicts. |
| `PUBLIC_AND_OWNER` | Restrict to `scope=PUBLIC OR owner=identity`; anonymous callers degrade to public-only. |

If `AuthorizedResources` is populated, it is added as an OR branch with the base
predicate. The default implementation currently leaves this list empty and keeps
the field as the extension point for explicit resource grants.

## Plugin State And Configuration

Runtime availability requires both the family-wide switch and unified plugin
state for `visibility:{serviceName}`. The family-wide switch is:

```properties
nacos.plugin.visibility.enabled=true
```

This switch is the outer runtime gate. When it is `false`, no visibility
implementation may execute, regardless of its unified plugin state. The core
plugin manager does not convert this switch into implementation state. Startup also defers
visibility implementation discovery while this switch is false. A server
configuration refresh that changes it to true triggers one-time discovery, persisted state
restoration, and unified configuration application before visibility services become available.
After discovery, changing the switch back to false keeps instances registered while the outer gate
prevents their execution.

Initial implementation state comes from the compatibility selector
`nacos.plugin.visibility.type`, then the standard implementation key
`nacos.plugin.visibility.{serviceName}.enabled`; persisted state takes
precedence over both, but cannot override the family-wide gate.
Implementation-level runtime changes use the plugin management API.

`VisibilityService` extends `PluginConfigSpec`. The built-in `visibility:nacos` implementation has
no private configuration, declares no definitions, and is exposed as `configurable=false`.
An external implementation may own properties under:

```properties
nacos.plugin.visibility.{serviceName}.{itemKey}
```

Legacy implementations compiled against the older SPI, and implementations that declare no
definitions, receive their service-local properties once through
`VisibilityService.init(Properties)`.
Use of non-empty legacy properties emits a migration warning without logging
configuration values. When an implementation reports `isConfigurable()=true`, the visibility
manager must not invoke the legacy callback; the core plugin
manager's unified `applyConfig` lifecycle is its only configuration application
path. Such implementations declare their own definitions and receive unified
source, metadata, masking, and update semantics.

If the selected plugin is disabled or unavailable, the current AI domain skips
visibility filtering and single-resource visibility validation; creation falls
back to `PRIVATE` scope. This preserves the historical disabled behavior and
must not be confused with auth being enabled or disabled. The built-in plugin
also treats disabled auth as allowing visibility.

## Relationship With Auth

A visibility plugin may delegate explicit permission checks to the selected auth
plugin. Explicit visibility permission resources use a domain-owned resource
string and `SignType.SPECIFIED`; the default implementation uses:

```text
@@visibility/{namespaceId}/{resourceType}/{resourceName}
```

This preserves the separation of concerns: visibility decides candidate
resources, while auth remains the source of permission decisions. The
[default auth plugin implementation](default-auth-plugin-spec.md) provides the
current built-in visibility implementation.

## API Requirements

Any API that returns visibility-aware resources must:

- Validate single-resource read/write operations with `validateVisibility`.
- Apply `adviseQuery` to list or search operations before returning data.
- Preserve owner and scope metadata when resources are created or updated.
- Avoid exposing private resource names through counts, errors, or partial list
  responses.
- Return not found for denied single-resource reads when the API needs to hide
  resource existence.
- Return access denied for denied writes.
