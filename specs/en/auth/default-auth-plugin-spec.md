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

# Default Auth Plugin Implementation Spec

## Scope

The default auth implementation package currently provides the `nacos` and
`ldap` auth plugins. The `nacos` plugin provides username/password login, token
authentication, RBAC permission management, and the default visibility
integration used by AI resources. It implements the
[Auth Plugin Spec](auth-plugin-spec.md), the shared
[Auth And Permission Spec](auth-permission-spec.md), and the
[Visibility Plugin Spec](visibility-plugin-spec.md).

The Java client provides `NacosClientAuthServiceImpl` for the username/password
and token flow exposed by the default plugin. Other built-in client auth
services, such as RAM and OIDC, are Java Client SDK auth extensions and are
specified by the [Java SDK Implementation Spec](../sdk/sdk-java-impl-spec.md)
and the [Auth Plugin Spec](auth-plugin-spec.md), not by this server-side default
plugin implementation.

The default implementation is intended to reduce accidental misuse in trusted
internal networks. It is not a full strong-auth solution for hostile public
networks. Public exposure requires an external security boundary or a stronger
auth plugin.

## Module Configuration

| Configuration | Purpose |
|---------------|---------|
| `nacos.core.auth.enabled` | Enable the general auth system and Open API auth. |
| `nacos.core.auth.admin.enabled` | Enable Admin API auth. |
| `nacos.core.auth.console.enabled` | Enable Console API auth and default login behavior. |
| `nacos.core.auth.system.type` | Select the auth plugin, default `nacos`. |
| `nacos.core.auth.server.identity.key` | Server-to-server identity key. |
| `nacos.core.auth.server.identity.value` | Server-to-server identity value. |

These settings control the auth module, API scopes, plugin selection, and
server identity. They are not configuration items owned by `auth:nacos`.
Server identity values must be deployment-specific.

## Managed Plugin Configuration

The `nacos` implementation directly implements `PluginConfigSpec` and is
registered as configurable plugin `auth:nacos`. Its canonical configuration
prefix is `nacos.plugin.auth.nacos.`.

| Item key | Canonical static key | Legacy static alias | Type | Effect | Default | Sensitive |
|----------|----------------------|---------------------|------|--------|---------|-----------|
| `token.secret.key` | `nacos.plugin.auth.nacos.token.secret.key` | `nacos.core.auth.plugin.nacos.token.secret.key` | String | `RESTART` | Empty | Yes |
| `token.expire.seconds` | `nacos.plugin.auth.nacos.token.expire.seconds` | `nacos.core.auth.plugin.nacos.token.expire.seconds` | Number | `RUNTIME` | `18000` | No |
| `token.cache.enable` | `nacos.plugin.auth.nacos.token.cache.enable` | `nacos.core.auth.plugin.nacos.token.cache.enable` | Boolean | `RUNTIME` | `false` | No |
| `caching.enabled` | `nacos.plugin.auth.nacos.caching.enabled` | `nacos.core.auth.caching.enabled` | Boolean | `RUNTIME` | `true` | No |
| `anonymous.ai.enabled` | `nacos.plugin.auth.nacos.anonymous.ai.enabled` | `nacos.core.auth.nacos.anonymous.ai.enabled` | Boolean | `RUNTIME` | `false` | No |

`token.expire.seconds` must be greater than zero. When any Nacos API auth scope
needs token support, `token.secret.key` must be a valid Base64 value that
decodes to at least 32 bytes. A token secret must be deployment-specific; a
default or shared value is unsafe. The secret is returned in masked form by
plugin management APIs and cannot be changed through a runtime update.

The canonical key wins when it and a legacy alias are both present. Legacy
aliases remain readable for compatibility and produce migration diagnostics
without logging configuration values. Runtime and local-only updates use the
item keys in the table and follow the common full-source-map semantics from the
[Nacos Plugin Spec](../plugin/plugin-spec.md).

The plugin owns an immutable effective configuration snapshot. Applying a new
snapshot updates token expiration, token-cache selection, authorization cache
behavior, and anonymous access without making those consumers read Spring
environment properties directly. The JWT parser is created from the accepted
restart-only secret. Enabling token caching selects a cache wrapper around the
same base manager. Disabling token caching switches back to the base manager
and clears the token cache. Changing token expiration also clears the wrapper
cache so the next token request uses the accepted runtime lifetime; tokens
already returned to clients remain valid until their signed expiration.

The `ldap` plugin variant additionally uses the `nacos.core.auth.ldap.*`
configuration family. LDAP changes identity authentication only; authorization
continues to use Nacos roles and permissions.

## Identity

The plugin accepts these identity inputs:

| Input | Usage |
|-------|-------|
| `Authorization: Bearer ...` | Token authentication. |
| `accessToken` | Token authentication through request parameter or header. |
| `username` and `password` | Login or direct username/password authentication. |
| Server identity key/value | Server-to-server request identity. |

After successful authentication, the plugin enriches `IdentityContext` with the
authenticated Nacos user and user id. Global administrator status is derived from
the user role model.

Anonymous AI access is allowed only when all of these are true:

- The endpoint marks the request as allowing anonymous access.
- `anonymous.ai.enabled` is enabled in `auth:nacos` configuration.
- The default plugin accepts the request as the built-in anonymous identity.

Enabling anonymous access immediately enables only identity acceptance. A
background reconciler then ensures the reserved anonymous user and role exist.
On first initialization it adds read permission on `public:*:ai/*` and writes
the anonymous role binding last as the durable completion marker. Concurrent
nodes use read-after-conflict verification so duplicate creation is treated as
success only when the expected persisted state is observable.

An existing anonymous role binding is treated as already initialized. The
reconciler does not restore the broad default permission in that case, so
administrator-customized anonymous permission scope is preserved. Disabling
anonymous access stops identity acceptance but does not delete the reserved
user, role, or permissions. Reconciliation state is only a local database-work
optimization and is not an authorization condition: normal RBAC authority
checks still deny the anonymous identity when no matching role or permission is
present.

## Default Java Client Auth Integration

The Java client-side integration for this default plugin is
`NacosClientAuthServiceImpl`. It is loaded through the client auth SPI and uses
the default `/v3/auth/user/login` API when `username` and `password` are
configured.

| Client implementation | Identity material | Contract |
|-----------------------|-------------------|----------|
| `NacosClientAuthServiceImpl` | `username`, `password`, and `accessToken`. | Log in through the default auth API, attach the returned `accessToken`, and refresh the token before expiration. |

This integration must not mutate request payloads. It only provides identity
material consumed by the selected server-side auth plugin. Additional client
auth implementations, including [RAM](ram-auth-plugin-spec.md) and
[OIDC](oidc-auth-plugin-spec.md), are documented as Java Client SDK extensions
in the [Java SDK Implementation Spec](../sdk/sdk-java-impl-spec.md).

## RBAC Storage Model

The default plugin stores:

| Object | Meaning |
|--------|---------|
| `User` | Username and password identity. |
| `RoleInfo` | Role assigned to a username. |
| `PermissionInfo` | Resource and action assigned to a role. |

`ROLE_ADMIN` is the global administrator role. Users with this role may access
all resources and console management operations.

## Permission Resource Format

Default resource permissions use:

```text
{namespaceId}:{group}:{signType}/{resourceName}
```

Examples:

| Resource | Example |
|----------|---------|
| Config data | `public:DEFAULT_GROUP:config/example.properties` |
| Naming service | `public:DEFAULT_GROUP:naming/com.example.Service` |
| Console users | `console/users` |
| Console roles | `console/roles` |
| Console permissions | `console/permissions` |
| Visibility permission | `@@visibility/public/mcp/example-mcp` |

Rules:

- `*` may be used as a wildcard in permission resources.
- If group is empty, the permission check uses `*` for the group segment.
- If resource name is empty, the resource name segment becomes `*`.
- A stored resource that starts with `:` is interpreted with the default
  namespace `public`.
- `SPECIFIED` resources use the explicit resource string directly.
- Stored actions may include `r`, `w`, or `rw`.

Non-admin roles must not manage console users, roles, or permissions.

## Default Auth APIs

The default plugin owns these v3 API families:

| Path | Purpose |
|------|---------|
| `/v3/auth/user` | User management and password update. |
| `/v3/auth/user/login` | Login and token issuance. |
| `/v3/auth/user/admin` | Administrator bootstrap when no global admin exists. |
| `/v3/auth/role` | Role management. |
| `/v3/auth/permission` | Permission management. |
| `/v3/auth/visibility` | Explicit visibility grant management. |

Management endpoints must be protected by console-scoped `@Secured` resources
such as `console/users`, `console/roles`, `console/permissions`, and
`console/user/password`.

Login is intentionally public. Administrator bootstrap is intentionally exposed
only for the no-admin initialization state and must be rejected after a global
administrator exists. These APIs are part of the
[V3 API Surface](../http-api/v3-api-surface.md) and must follow the
[HTTP Authorization Spec](../http-api/authorization-spec.md).

The visibility grant API is plugin-owned, not part of any domain controller
family. It uses `ApiType.ADMIN_API` with identity-only request authentication
and enforces resource management authority in the grant service. When auth is
enabled, only the resource owner or a global administrator may grant, revoke,
or list explicit visibility access for that resource.

## Default Visibility Implementation

The default visibility implementation is also named `nacos` and is currently
used by AI resources.

Default behavior:

- New resources default to `PRIVATE` unless the domain supplies another scope.
- Global administrators can read and write all visibility-aware resources.
- A resource owner can read and write the resource.
- `PUBLIC` resources can be read by non-owners.
- Explicit visibility permission can grant access through the auth plugin.
- Anonymous AI read access is allowed only through the anonymous AI opt-in path.
- Denied reads may be reported as not found to hide resource existence.
- Denied writes are reported as access denied.

Explicit visibility permission resources use:

```text
@@visibility/{namespaceId}/{resourceType}/{resourceName}
```

The exact canonical resource string must be stored in the default RBAC
`permissions.resource` column. The column must support at least 512 characters
so namespaced resources can be persisted without truncation. Resource matching
is exact and case-sensitive; the default MySQL schema therefore uses
`utf8mb4_bin` for this column and `ROW_FORMAT=DYNAMIC` for the `permissions`
table to keep the existing `(role, resource, action)` indexes valid with
`utf8mb4`.

Existing MySQL deployments should review the MySQL version, InnoDB page size,
row format, and current `permissions` table definition before applying
`META-INF/mysql-upgrade-visibility-permission-resource.sql`. Existing Oracle
deployments should apply
`META-INF/oracle-upgrade-visibility-permission-resource.sql` to expand
`permissions.resource` to `VARCHAR2(512 CHAR)`.

Explicit visibility grants for currently supported resources are managed through:

```text
/v3/auth/visibility
```

Grant behavior:

- Read grants store action `r`.
- Write or read-write grant requests store action `rw`, and `rw` implies read
  visibility when list/search queries are advised.
- Grant data reuses the default RBAC persistence by storing plugin-owned
  internal roles and permissions in the auth backend.
- The default implementation creates at most one reserved internal visibility
  role for each grantee user. The role name is deterministic, unique to the
  grantee, and bounded by the existing role-name column. Resource and action
  data must be stored only in permission rows attached to that role, not encoded
  into the role name.
- List/search authorization must derive explicit resources from the actual
  permission rows attached to the caller's reserved visibility role. A role
  binding without a matching permission row must not grant visibility. Resource
  grant-list lookup must use exact permission resource matching and then resolve
  the matched visibility roles back to users. The storage schema should index
  both `permissions(resource, action, role)` and `roles(role, username)` for
  this reverse lookup path.
- Resource existence and owner metadata are resolved through a domain-provided
  visibility resource locator instead of a direct compile-time dependency from
  the auth plugin to domain persistence types.

Range queries must combine the base visibility predicate with explicitly
authorized resources. The default visibility implementation populates
explicit authorized resources from the grant service so list/search paths can
include private resources that were granted to the caller.

For AI list and search paths, visibility must be converted into repository query
conditions before count and page queries run. This keeps `totalCount` aligned
with the visible resource set and avoids full-load in-memory filtering.

## Compatibility

Legacy or compatibility endpoints may remain for existing clients, but new
documentation and new development should target the v3 auth API and the plugin
contracts defined here.

Legacy static configuration aliases in the managed-plugin table remain
supported. New distribution templates use canonical keys and identify the old
keys in comments. Startup scripts migrate a valid legacy token secret to the
canonical key when the canonical key is absent or empty; when both are set, the
canonical value wins. Secret values must never be printed during migration.

## Pending Issues

- The `ldap` plugin is currently coupled into the default auth implementation
  package through shared authentication behavior and token infrastructure.
  Conceptually LDAP is a separate identity-provider-backed auth plugin, not
  part of the default Nacos username/password implementation. Its complete
  `PluginConfigSpec` ownership and shared token configuration boundary must be
  resolved in the LDAP integration phase while preserving compatibility for
  existing `nacos.core.auth.system.type=ldap` deployments.
