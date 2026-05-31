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

# OIDC Auth Plugin Spec

## Scope

The OIDC auth plugin lets Nacos delegate authentication and authorization to an
OpenID Connect 1.0 / OAuth2 identity provider. It implements the
[Auth Plugin Spec](auth-plugin-spec.md) with auth service name `oidc`.

The server-side implementation lives in
`plugin-default-impl/nacos-oidc-auth-plugin`. It was introduced to support
standard identity providers for console SSO and token-based access. The Java
client also contains `OidcClientAuthServiceImpl`, which obtains bearer tokens
through the OAuth2 client credentials flow and injects them into SDK requests.

OIDC is not part of the default Nacos username/password auth plugin. It is an
alternative auth mode selected by `nacos.core.auth.system.type=oidc`.

## Server SPI

`OidcAuthPluginService` must:

| Method | Contract |
|--------|----------|
| `getAuthServiceName()` | Return `oidc`. |
| `identityNames()` | Accept `Authorization` and `accessToken`. |
| `enableAuth(action, type)` | Enable auth for all actions and sign types. |
| `validateIdentity(identityContext, resource)` | Extract a bearer token or `accessToken`, validate it, map claims to an OIDC user, and store that user in `IdentityContext`. |
| `validateAuthority(identityContext, permission)` | Grant global administrators directly; otherwise delegate the permission decision to the configured authorization provider. |
| `isLoginEnabled()` | Return `true`; console login is handled by the OIDC login controller. |
| `isAdminRequest()` | Return `false`; the IdP owns user bootstrap and user management. |

The plugin must not use Nacos local user, role, or permission management as the
source of truth. Console user, role, permission, and password management
surfaces should be hidden or disabled when OIDC is selected.

## Required Configuration

OIDC mode is selected with:

```properties
nacos.core.auth.system.type=oidc
nacos.core.auth.enabled=true
```

Server-to-server identity and the default Nacos token secret can still be
required by the runtime for internal communication and compatibility paths.

OIDC plugin configuration uses the `nacos.core.auth.plugin.oidc.` prefix:

| Configuration | Default | Purpose |
|---------------|---------|---------|
| `issuer-uri` | empty | IdP issuer URI. The plugin uses it for OIDC discovery. |
| `client-id` | empty | OAuth2 client id registered in the IdP. |
| `client-secret` | empty | OAuth2 client secret. Also used by the current implementation for signed state. |
| `scope` | `openid profile email` | Scopes requested during browser login. |
| `token-validation-method` | `jwt` | Declared validation mode. Current server code validates JWTs through JWKS. |
| `jwks-cache-ttl-seconds` | `3600` | JWKS cache TTL. |
| `username-claim` | `preferred_username` | Claim used as the Nacos display username. |
| `roles-claim` | `roles` | Primary claim used to extract roles. |
| `admin-role` | `nacos-admin` | Role that maps to global administrator. |
| `auto-create-user` | `true` | Reserved for user mapping compatibility; OIDC remains the identity source of truth. |
| `authorization-endpoint` | empty | External endpoint used for non-admin authorization decisions. |
| `authorization-timeout-ms` | `5000` | Timeout for external authorization requests. |
| `strict-nonce-validation` | `true` | Reject authorization-code login when the ID token lacks or mismatches nonce. |
| `strict-audience-validation` | `true` | Reject tokens whose audience or authorized party does not match `client-id`. |

`issuer-uri` and `client-id` are required for a valid server configuration.
Browser login also requires `client-secret`, authorization endpoint discovery,
and token endpoint discovery.

## Browser Login Flow

The current implementation exposes browser-oriented endpoints under
`/v1/auth/oidc`. These endpoints are implementation compatibility endpoints.
Any new Nacos auth HTTP API should follow the v3 API rules in
[HTTP API Spec](../http-api/api-spec.md).

| Endpoint | Purpose |
|----------|---------|
| `/v1/auth/oidc/login` | Redirect the browser to the IdP authorization endpoint. |
| `/v1/auth/oidc/callback` | Receive authorization code, validate state and nonce, exchange code for tokens, and return to the console. |
| `/v1/auth/oidc/logout` | Clear console-side auth state and optionally redirect to the IdP logout endpoint. |
| `/v1/auth/oidc/config` | Tell the console that OIDC mode is enabled and that local user/role/permission management is disabled. |

The login flow must:

- Use OIDC discovery from `{issuer-uri}/.well-known/openid-configuration`.
- Generate a self-contained signed `state` value and a `nonce`.
- Exchange the authorization code at the IdP token endpoint.
- Validate the ID token signature and claims before accepting the user.
- Deliver short-lived console cookies only as a handoff mechanism for the
  frontend, then rely on normal request identity propagation.

## Token Validation

The current implementation validates JWT tokens with JWKS. Validation must:

- Accept only supported asymmetric JWS algorithms.
- Require `sub`, `iss`, `exp`, and `iat` claims.
- Reject expired tokens and tokens that are not yet valid.
- Verify issuer, with trailing slash normalization.
- Verify audience or `azp` against `client-id` when strict audience validation
  is enabled.
- Refresh JWKS and retry once when signature verification fails, to tolerate key
  rotation.

Username mapping uses the configured `username-claim`, then falls back to common
claims such as `preferred_username`, `email`, and finally `sub`. Role mapping
uses the configured `roles-claim`, and may also read common Keycloak-style
`realm_access.roles`, `resource_access.{client-id}.roles`, and `groups` claims.
The configured `admin-role` maps to the Nacos global administrator concept.

## Authorization

OIDC authentication identifies the caller. Authorization must still answer
whether that caller may perform the requested Nacos action on the parsed
resource.

The current implementation grants global administrators locally based on the
mapped role. For non-admin users it calls the configured external
`authorization-endpoint` with:

| Field | Meaning |
|-------|---------|
| `token` | User access token. |
| `resource` | Nacos resource URI derived from `Resource`. |
| `action` | Nacos action, such as read or write. |
| `resourceType`, `namespace`, `group`, `resourceName` | Structured Nacos resource identity. |

If `authorization-endpoint` is empty, the current implementation allows
non-admin access. Deployments that need authorization isolation must configure
an external authorization endpoint or provide a stricter OIDC authority
provider.

## Java Client Integration

`OidcClientAuthServiceImpl` is a Java Client SDK auth extension. It is separate
from browser console SSO.

| Client property | Purpose |
|-----------------|---------|
| `nacos.client.auth.oidc.issuer-uri` | OIDC issuer used for token endpoint discovery. |
| `nacos.client.auth.oidc.client-id` | OAuth2 client id. |
| `nacos.client.auth.oidc.client-secret` | OAuth2 client secret. |
| `nacos.client.auth.oidc.scope` | OAuth2 scopes, default `openid`. |
| `nacos.client.auth.oidc.token-endpoint` | Direct token endpoint override; skips discovery. |

When configured, the client uses the OAuth2 client credentials grant, refreshes
tokens before expiration, and injects both `Authorization: Bearer ...` and
`accessToken`. When not configured, it must return an empty identity context and
must not fail unrelated SDK calls.

## Pending Issues

- The configuration model declares `token-validation-method=introspection`, but
  the current server validation path is JWT/JWKS-based. Introspection must not
  be documented as supported until an implementation exists.
- OIDC browser endpoints currently use `/v1/auth/oidc`. Any future Nacos-native
  auth APIs should use `/v3/auth/oidc/*` and the standard response/error model.
- Documentation and code must stay aligned on default values for strict nonce
  and audience validation.

## Relationships

- General auth SPI rules: [Auth Plugin Spec](auth-plugin-spec.md).
- Java client auth extension rules:
  [Java SDK Implementation Spec](../sdk/sdk-java-impl-spec.md).
- Default username/password auth:
  [Default Auth Plugin Implementation Spec](default-auth-plugin-spec.md).
