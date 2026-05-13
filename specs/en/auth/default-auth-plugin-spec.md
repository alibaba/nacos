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

## Required Configuration

| Configuration | Purpose |
|---------------|---------|
| `nacos.core.auth.enabled` | Enable the general auth system and Open API auth. |
| `nacos.core.auth.admin.enabled` | Enable Admin API auth. |
| `nacos.core.auth.console.enabled` | Enable Console API auth and default login behavior. |
| `nacos.core.auth.system.type` | Select the auth plugin, default `nacos`. |
| `nacos.core.auth.plugin.nacos.token.secret.key` | Secret key used to sign default tokens. Must be configured by deployments. |
| `nacos.core.auth.plugin.nacos.token.expire.seconds` | Token expiration time. |
| `nacos.core.auth.plugin.nacos.token.cache.enable` | Enable token parse and validation cache. |
| `nacos.core.auth.server.identity.key` | Server-to-server identity key. |
| `nacos.core.auth.server.identity.value` | Server-to-server identity value. |
| `nacos.core.auth.caching.enabled` | Enable user, role, and permission cache. |
| `nacos.core.auth.nacos.anonymous.ai.enabled` | Allow anonymous AI access when an endpoint explicitly opts in. |

Token secrets and server identity values must be deployment-specific. A default
or shared value is unsafe.

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
- `nacos.core.auth.nacos.anonymous.ai.enabled` is enabled.
- The default plugin accepts the request as the built-in anonymous identity.

When anonymous AI access is enabled, the implementation initializes the reserved
anonymous user and role with read permission on `public:*:ai/*`.

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

Management endpoints must be protected by console-scoped `@Secured` resources
such as `console/users`, `console/roles`, `console/permissions`, and
`console/user/password`.

Login is intentionally public. Administrator bootstrap is intentionally exposed
only for the no-admin initialization state and must be rejected after a global
administrator exists. These APIs are part of the
[V3 API Surface](../http-api/v3-api-surface.md) and must follow the
[HTTP Authorization Spec](../http-api/authorization-spec.md).

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

Range queries must combine the base visibility predicate with explicitly
authorized resources. The current default implementation exposes the structure
for explicit authorized resources; API and storage integrations must use it as
that integration is completed.

For AI list and search paths, visibility must be converted into repository query
conditions before count and page queries run. This keeps `totalCount` aligned
with the visible resource set and avoids full-load in-memory filtering.

## Compatibility

Legacy or compatibility endpoints may remain for existing clients, but new
documentation and new development should target the v3 auth API and the plugin
contracts defined here.

## Pending Issues

- The `ldap` plugin is currently coupled into the default auth implementation
  package by extending `NacosAuthPluginService`. Conceptually LDAP is a separate
  identity-provider-backed auth plugin, not part of the default Nacos
  username/password and token implementation. It should be split into a
  standalone auth plugin package and spec while preserving compatibility for
  existing `nacos.core.auth.system.type=ldap` deployments.
