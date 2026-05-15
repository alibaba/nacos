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

# HTTP API Authorization Spec

This document defines how the Nacos auth model is applied to v3 HTTP APIs. The
shared auth domain model is defined by
[Auth And Permission Spec](../auth/auth-permission-spec.md), and plugin
contracts are defined by [Auth Plugin Spec](../auth/auth-plugin-spec.md) and
[Visibility Plugin Spec](../auth/visibility-plugin-spec.md).

## 1. Authorization Tuple

The effective authorization tuple for v3 HTTP APIs is:

```text
apiType + signType + resource + action + tags
```

Where:

- `apiType` separates `OPEN_API`, `ADMIN_API`, `CONSOLE_API`, and inner APIs.
- `signType` identifies the resource domain, such as `CONFIG`, `NAMING`, `AI`,
  or `CONSOLE`.
- `resource` identifies the protected resource path or logical resource name.
- `action` is usually `READ` or `WRITE`.
- `tags` adds special behavior such as `ONLY_IDENTITY` or `ALLOW_ANONYMOUS`.

## 2. Filter Split

Current code dispatches auth by `apiType`:

- `AuthAdminFilter` handles methods whose `@Secured.apiType()` is
  `ApiType.ADMIN_API`.
- `AuthFilter` handles secured APIs whose `apiType()` is not `ADMIN_API`,
  including `OPEN_API`, `CONSOLE_API`, and inner APIs.

## 3. Required Annotation

V3 HTTP APIs should declare `@Secured` unless the endpoint is explicitly:

- public;
- bootstrap-only;
- health-oriented;
- handled by a documented compatibility path.

Admin APIs should use `ApiType.ADMIN_API`. Console APIs should use
`ApiType.CONSOLE_API`. Open APIs should use `ApiType.OPEN_API`.

## 4. Public And Bootstrap Endpoints

Endpoints may omit `@Secured` only when they are intentionally public,
bootstrap-only, health-oriented, or compatibility-only. Public endpoints must be
documented as public and must not expose sensitive operational details.

Implemented public endpoints include:

- `GET /v3/admin/core/state`
- `GET /v3/admin/core/state/liveness`
- `GET /v3/admin/core/state/readiness`
- `GET /v3/console/server/state`
- `GET /v3/console/server/announcement`
- `GET /v3/console/server/guide`
- `GET /v3/console/health/liveness`
- `GET /v3/console/health/readiness`

The corresponding Admin API and Console API docs mark these endpoints as public
and requiring no identity information.

Bootstrap behavior:

- `/v3/auth/user/admin` can create the first admin user when no global admin
  exists and the auth system is `NACOS`.

## 5. Plugin-Provided Auth APIs

The `/v3/auth/*` API surface belongs to auth plugins. The
[default Nacos auth plugin](../auth/default-auth-plugin-spec.md) is shipped with
Nacos and must follow the [Nacos HTTP API rules](api-spec.md) for path shape,
response shape, validation, and error behavior.

Third-party auth plugins should follow the same rules when exposing HTTP APIs
through Nacos.

## 6. Implemented Exceptions

Implemented behavior that needs endpoint-level documentation:

- Some AI client endpoints allow anonymous access through `ALLOW_ANONYMOUS`.
