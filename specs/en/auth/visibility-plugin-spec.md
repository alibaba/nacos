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

The plugin is domain-neutral. The current Nacos integration applies it to AI
registry resources, where users may create resources that are private to an
owner, public to readers, or visible through explicit authorization.

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

The default domain integration converts `QueryAdvisor` to repository `QueryCondition`
before count and page queries run. Let `F` be the caller-supplied business filters already
present on the incoming `QueryCondition` (for example an explicit `scope` or `owner` filter
from the request), `B` be the resolved `BaseVisibilityPredicate`, and `G` be
`name IN AuthorizedResources`. The converter must produce:

```text
final query = F AND (B OR G)
```

`B` is resolved on its own, independently of `G`, into one of: always satisfied, never
satisfied, or a set of OR branches. The base predicate resolves as follows:

| Predicate | `B` resolution |
|-----------|----------------|
| `ALL` | Always satisfied; adds no visibility condition. |
| `PUBLIC` | Satisfied when `scope=PUBLIC`; never satisfied when the caller's business filter conflicts with a public scope. |
| `OWNER` | Satisfied when `owner=identity`; never satisfied when identity is absent, or when the caller's business filter conflicts with the identity as owner. |
| `PUBLIC_AND_OWNER` | Satisfied when `scope=PUBLIC OR owner=identity`; anonymous callers degrade to the `PUBLIC` resolution. Never satisfied only when the caller's business filter fixes both scope and owner to values that conflict with both branches. |

Only after `B` is resolved is it unioned with `G`: an always-satisfied `B` makes `G`
irrelevant (`B OR G` is still always satisfied), a never-satisfied `B` collapses `B OR G`
down to `G` alone, and OR-branch resolutions add `G` as one more OR branch alongside them.
This union must happen before any simplification into a concrete `QueryCondition` shape (a
hard field, an OR group, or `alwaysEmpty`): resolving `B` into the condition first, before
`G` is known, can silently turn a union into an intersection, or mark the whole query
`alwaysEmpty` even though `F AND G` could still match.

Caller-supplied business filters such as owner and scope must be present in the base
`QueryCondition` before `QueryAdvisor` is applied: they are used both to compute `F` and to
prune branches of `B` that are already satisfied or already impossible, before the converter
decides whether to emit an OR group or fall back to `alwaysEmpty`. Resource-type
implementations must not reset those fields after conversion and overwrite plugin visibility
constraints.

If `AuthorizedResources` is populated, `G` is added as an OR branch alongside `B` (or in
place of `B` when `B` alone is never satisfied), per the union above -- it is never dropped
merely because `B` could not independently be satisfied. The default visibility
implementation populates this list from plugin-owned explicit grants stored by the selected
auth plugin. Stored write grants imply read visibility, while read grants only affect
read/list queries.

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

If visibility is disabled, the owning domain must define whether it behaves as
fully visible or whether it rejects visibility-sensitive operations. The default
visibility implementation treats disabled auth as allowing visibility.
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

When a plugin-owned grant-management API needs to verify resource existence or
owner metadata, the domain may expose a lightweight lookup bridge such as
`VisibilityResourceLocator` so the auth/visibility plugin can resolve
`namespaceId`, `resourceType`, `resourceName`, `owner`, and `scope` without
taking a direct compile-time dependency on domain persistence classes.

The default built-in grant-management API is:

```text
POST /v3/auth/visibility
DELETE /v3/auth/visibility
```

These endpoints are plugin-owned auth APIs and must use `ApiType.ADMIN_API`.
The default implementation does not expose a management-side grant-list
endpoint.

## API Requirements

Any API that returns visibility-aware resources must:

- Validate single-resource read/write operations with `validateVisibility`.
- Apply `adviseQuery` to list or search operations before returning data.
- Preserve owner and scope metadata when resources are created or updated.
- If the domain exposes explicit grant-management APIs, validate resource
  existence and management authority before mutating grants.
- Avoid exposing private resource names through counts, errors, or partial list
  responses.
- Return not found for denied single-resource reads when the API needs to hide
  resource existence.
- Return access denied for denied writes.
