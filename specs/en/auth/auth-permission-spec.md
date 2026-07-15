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

# Auth And Permission Spec

## Scope

This document defines the Nacos authentication and permission model shared by
HTTP APIs, gRPC APIs, plugin APIs, and server-internal calls. Transport-specific
details are defined by the [HTTP Authorization Spec](../http-api/authorization-spec.md)
and the [gRPC API Spec](../grpc-api/api-spec.md). Plugin contracts are defined
by the [Auth Plugin Spec](auth-plugin-spec.md) and
[Visibility Plugin Spec](visibility-plugin-spec.md).
Transport filter execution and `AuthContext` population are defined by the
[Request Filtering And Runtime Context Spec](../design/foundation-request-context-spec.md).

Nacos authorization is modeled as:

```text
request identity -> authenticated subject -> roles -> permissions -> resource/action
```

The built-in implementation uses RBAC. Custom auth plugins may connect to other
identity systems, but they must still evaluate Nacos requests through identity,
resource, and action semantics.

## Auth Plugin And Visibility Plugin

Nacos separates request-level authorization from data-level visibility.

| Layer | Main question | Typical SPI | Scope |
|-------|---------------|-------------|-------|
| [Auth plugin](auth-plugin-spec.md) | Can this caller invoke this API for the parsed resource/action? | `AuthPluginService` | Request admission and permission decision. |
| [Visibility plugin](visibility-plugin-spec.md) | Can this caller see or modify this concrete resource, or which resources should a range query return? | `VisibilityService` | Resource instance visibility and query planning. |

The two layers are orthogonal:

- An auth plugin can exist without a visibility plugin.
- A visibility plugin can exist independently, but each domain must define what
  happens when no request identity is available.
- A visibility plugin may reuse the identity produced by the auth plugin and may
  delegate explicit resource permission checks back to the selected auth plugin.
- Passing `@Secured` auth does not automatically make every matching data row
  visible.

For visibility-aware resources, the recommended request flow is:

```text
@Secured + AuthPlugin -> VisibilityService -> business operation
```

Single-resource reads may return not found when visibility is denied in order to
hide resource existence. Writes should return access denied when the caller can
address the resource but cannot modify it. List and search APIs must apply
visibility before pagination and total count are produced.

## RBAC Model

The default permission model contains:

| Concept | Meaning |
|---------|---------|
| User | Authenticated subject that can log in or call APIs. |
| Role | Named permission group assigned to users. |
| Permission | A role's allowed action on a Nacos resource. |
| Resource | A Nacos object identified by namespace, group or resource type, name, and domain type. |
| Action | Operation type, currently read or write. |

`ROLE_ADMIN` is the global administrator role. A global administrator bypasses
normal resource permission checks.

## Core Concepts

### `ApiType`

`ApiType` describes the API audience and the auth scope switch that applies to
the request.

| Value | Meaning |
|-------|---------|
| `OPEN_API` | Client-facing API for application or SDK access. |
| `ADMIN_API` | Administrative API for maintainers, tools, and gateways. |
| `CONSOLE_API` | API used by the Nacos web console. |
| `INNER_API` | Server-internal API, usually protected by server identity. |

### `SignType`

`SignType` identifies the domain used to parse and authorize resources.

| Value | Meaning |
|-------|---------|
| `CONFIG` | Configuration resources. |
| `NAMING` | Naming and service discovery resources. |
| `AI` | AI registry resources such as MCP, prompts, agents, and tools. |
| `CONSOLE` | Console management resources such as users, roles, and permissions. |
| `LOCK` | [Lock resources](../lock/lock-spec.md). |
| `SPECIFIED` | Explicit resource string supplied by the secured endpoint. |

### `ActionTypes`

| Value | Stored value | Semantics |
|-------|--------------|-----------|
| `READ` | `r` | Query, list, detail, subscribe, watch, or read-only inspection. |
| `WRITE` | `w` | Create, update, delete, publish, register, deregister, or state change. |

An implementation may store combined actions such as `rw`, but endpoint
annotations must use the explicit action that matches the API behavior.

### `@Secured`

`@Secured` is the endpoint-level declaration that binds a controller or request
handler to the auth model.

| Field | Purpose |
|-------|---------|
| `action` | Required action, usually `READ` or `WRITE`. |
| `resource` | Explicit resource name, mainly for `SPECIFIED` or console resources. |
| `signType` | Domain used for resource parsing and permission evaluation. |
| `parser` | Custom resource parser when the default parser is not enough. |
| `tags` | Additional metadata copied into `Resource.properties`. |
| `apiType` | API audience and auth scope. |

Every non-public v3 HTTP API and gRPC request handler must declare the intended
auth metadata. Public endpoints must be explicitly documented by their owning
spec.

## Authorization Flow

The common auth flow is:

1. Locate the request's `@Secured` metadata.
2. Build `IdentityContext` from headers, parameters, tokens, certificates, or
   connector metadata.
3. Parse the Nacos `Resource` from request parameters or from the explicit
   resource declared by `@Secured`.
4. Ask the selected auth plugin whether auth is enabled for the action and
   domain.
5. Validate identity.
6. Validate authority for the `Permission(resource, action)`.

Server-internal requests may also require the configured server identity key and
value before normal request handling continues.

## Resource Permission Names

Nacos permissions are evaluated against resources derived from the
[resource model](../design/resource-model-spec.md):

```text
NamespaceId -> Group or resourceType -> resourceName
```

Standard resource forms are:

| Domain | Permission resource form |
|--------|--------------------------|
| Config | `{namespaceId}:{group}:config/{dataId}` |
| Naming | `{namespaceId}:{group}:naming/{serviceName}` |
| AI | `{namespaceId}:{group}:ai/{resourceName}` plus AI resource metadata. |
| Console | `console/{managementResource}` |
| Explicit | The string supplied by `@Secured(resource = ...)`. |

The default auth implementation supports `*` wildcards in permission resources.
The resource model spec remains authoritative for the meaning of namespace,
group, resource type, and resource name.

## Auth Scope Switches

Auth enablement is scoped by API audience:

| Configuration | Scope |
|---------------|-------|
| `nacos.core.auth.enabled` | Enables auth for Open APIs and the general auth system. |
| `nacos.core.auth.admin.enabled` | Enables auth for Admin APIs. |
| `nacos.core.auth.console.enabled` | Enables auth for Console APIs and login behavior. |

The selected auth plugin is named by `nacos.core.auth.system.type`.

## Plugin APIs

Auth-related HTTP APIs under `/v3/auth/*` are plugin-provided APIs. The
[default Nacos auth plugin](default-auth-plugin-spec.md) provides user, role,
permission, and login endpoints. These endpoints are not Open, Admin, or Console
APIs by path, but they must still follow the Nacos v3 API response, error, and
authorization conventions.

## Public Endpoints

An endpoint may be intentionally unauthenticated only when the owning spec and
documentation say so. Typical examples are login, one-time administrator
initialization guarded by server-side state, and health or status endpoints that
are designed for unauthenticated probes.

When an endpoint is public for compatibility rather than by current design, the
new documented API should be the primary API and the old endpoint should remain
only as a compatibility surface.
