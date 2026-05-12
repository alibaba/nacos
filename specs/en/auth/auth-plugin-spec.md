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

# Auth Plugin Spec

## Scope

The auth plugin category lets Nacos replace the authentication and authorization
implementation without changing API controllers or resource parsers. The common
contract is:

```text
IdentityContext + Resource + Action -> allowed or rejected
```

The auth plugin does not own Nacos resource modeling. It consumes resources
created by Nacos controllers, protocol filters, and resource parsers.

## Server SPI

A server auth plugin implements `AuthPluginService`.

| Method | Requirement |
|--------|-------------|
| `getAuthServiceName()` | Return the stable plugin name selected by `nacos.core.auth.system.type`. |
| `identityNames()` | Declare identity fields that may be extracted from requests. |
| `enableAuth(action, type)` | Decide whether this plugin requires auth for the action and sign type. |
| `validateIdentity(identityContext, resource)` | Authenticate the caller and enrich identity metadata. |
| `validateAuthority(identityContext, permission)` | Authorize the caller for the target resource and action. |
| `isLoginEnabled()` | Declare whether plugin-provided login should be exposed. |
| `isAdminRequest()` | Declare whether the current request should be treated as administrator bootstrap flow. |

The plugin must throw or return Nacos auth exceptions for rejected identities or
permissions so that the protocol layer can map them to standard API errors.

## Client SPI

Client-side auth plugins provide request identity material. A client plugin must
inject only the credentials or tokens required by the selected server plugin and
must not alter the semantic request payload.

The Java client must support the built-in username/password and token flow. A
custom client auth plugin may provide access keys, signatures, certificates, or
external tokens, but it must keep compatibility with the server-side identity
names declared by the matching auth plugin.

## Selection And State

The selected auth implementation is named by:

```properties
nacos.core.auth.system.type=nacos
```

Auth plugins are also registered in the core plugin system with type `auth`.
Only the selected and enabled auth plugin may handle requests. If a plugin is
loaded but disabled by plugin state, it must not be used for auth decisions.

## Identity Context

`IdentityContext` is the transport-neutral caller description. It may contain:

- Built-in fields such as remote IP.
- Headers or parameters such as `Authorization`, `accessToken`, `username`, and
  `password`.
- Plugin-defined fields such as access key, signature, tenant claim, or
  external principal.
- Auth result metadata such as authenticated username, user id, or global admin
  marker.

Identity names are part of the plugin contract. Server and client plugin
implementations must agree on those names.

## Resource And Permission

Auth plugins receive Nacos `Resource` and `Permission` objects. The plugin may
map those objects to an external permission system, but it must preserve:

- Namespace isolation.
- Group or resource type semantics.
- Resource name semantics.
- `READ` and `WRITE` action semantics.
- Explicit resources declared with `SignType.SPECIFIED`.

## Plugin APIs

If an auth plugin exposes HTTP APIs, those APIs must:

- Use the `/v3/auth/{resource}` path family.
- Use `Result<T>` as the response envelope.
- Use standard Nacos error codes and exception handling.
- Add `@Secured` to protected management endpoints.
- Document any intentionally public endpoint, such as login or bootstrap.

The default Nacos auth plugin is the reference implementation for the current
`/v3/auth/user`, `/v3/auth/role`, and `/v3/auth/permission` surface.

## Relationship With Visibility

Auth answers who the caller is and whether the caller has permission for a
resource/action pair. Visibility answers which resources should be visible in a
single-resource operation or range query.

Visibility plugins may delegate explicit permission checks back to the selected
auth plugin. Auth plugins must therefore keep permission evaluation stable for
explicit resources as well as domain resources.

## Safety Requirements

The built-in Nacos auth plugin is designed for trusted internal networks and is
not a complete strong-auth solution for hostile public networks. Deployments
that require stronger authentication should provide or select an auth plugin
that matches their security requirements.

