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

# V3 HTTP API Surface

This document describes the current v3 HTTP API coverage. It complements
[HTTP API Spec](api-spec.md), which defines the design rules. Endpoint
authorization follows the [HTTP Authorization Spec](authorization-spec.md), and
response shape follows the [Response And Error Spec](response-error-spec.md).

## 1. Scope

This document covers HTTP endpoints whose paths start with one of the following
v3 prefixes after the Nacos web context path:

| Prefix | API type | Primary users | Current auth scope |
| --- | --- | --- | --- |
| `/v3/client` | Open API | SDKs and custom clients | `ApiType.OPEN_API` |
| `/v3/admin` | Admin API | operators and maintainer tooling | `ApiType.ADMIN_API` |
| `/v3/console` | Console API | Nacos console UI backend calls | `ApiType.CONSOLE_API` |
| `/v3/auth` | Auth plugin API | plugin-provided auth and bootstrap APIs | [default auth plugin](../auth/default-auth-plugin-spec.md) |

This document does not cover:

- v1/v2 compatibility APIs, which are externalized to
  [nacos-api-legacy-adapter](https://github.com/nacos-group/nacos-api-legacy-adapter);
- gRPC request and response contracts;
- internal cluster APIs that are not exposed as v3 HTTP controllers;
- the AI Registry adaptor API, which has a separate compatibility surface.

## 2. Current Source Of Truth

The v3 HTTP behavior is currently defined by these source locations:

| Area | Code source |
| --- | --- |
| Admin core | `core/src/main/java/com/alibaba/nacos/core/controller/v3` |
| Admin config | `config/src/main/java/com/alibaba/nacos/config/server/controller/v3` |
| Admin naming | `naming/src/main/java/com/alibaba/nacos/naming/controllers/v3` |
| Admin AI | `ai/src/main/java/com/alibaba/nacos/ai/controller` |
| Console | `console/src/main/java/com/alibaba/nacos/console/controller/v3` |
| Auth v3 | `plugin-default-impl/nacos-default-auth-plugin/src/main/java/.../controller/v3` |
| Path constants | `Commons`, config `Constants`, naming `UtilsAndCommons`, AI `Constants`, `AuthConstants` |

The corresponding website source files are:

- `admin/admin-api.md`
- `admin/console-api.md`
- `user/open-api.md`

## 3. Current API Families

This section captures current implemented families. Counts are a script-assisted
inventory of Spring mappings in `src/main/java` and should be used as a review
guide, not as a final OpenAPI export.

| Family | Approx. mappings | Methods | Notes |
| --- | ---: | --- | --- |
| `/v3/client/cs/config` | 1 | GET | Query config for custom HTTP clients. |
| `/v3/client/ns/instance` | 3 | GET, POST, DELETE | Register, heartbeat, deregister, and list service instances. |
| `/v3/client/ai/prompt` | 1 | GET | Runtime prompt query. |
| `/v3/client/ai/skills` | 1 | GET | Runtime skill zip download. |
| `/v3/client/ai/agentspecs` | 2 | GET | Runtime AgentSpec get and search. |
| `/v3/admin/core/*` | 25 | GET, POST, PUT, DELETE | Loader, cluster, ops, namespace, state, plugin. |
| `/v3/admin/cs/*` | 25 | GET, POST, PUT, DELETE | Config CRUD, history, listener, capacity, metrics, ops. |
| `/v3/admin/ns/*` | 29 | GET, POST, PUT, DELETE | Service, instance, client, cluster, health, ops. |
| `/v3/admin/ai/*` | 71 | GET, POST, PUT, DELETE | MCP, A2A, Prompt, Skill, AgentSpec, Pipeline. |
| `/v3/console/core/*` | 7 | GET, POST, PUT, DELETE | Cluster and namespace console operations. |
| `/v3/console/cs/*` | 17 | GET, POST, DELETE | Config and history console operations. |
| `/v3/console/ns/*` | 11 | GET, POST, PUT, DELETE | Naming console service and instance operations. |
| `/v3/console/ai/*` | 67 | GET, POST, PUT, DELETE | Console AI management, imports, lifecycle, pipelines. |
| `/v3/console/copilot/*` | 6 | GET, POST | Config plus SSE copilot operations. |
| `/v3/auth/user` | 7 | GET, POST, PUT, DELETE | User login and management in default auth plugin. |
| `/v3/auth/role` | 4 | GET, POST, DELETE | Role management in default auth plugin. |
| `/v3/auth/permission` | 4 | GET, POST, DELETE | Permission management in default auth plugin. |

## 4. Open API Implemented Behavior

Implemented Open API surface:

| Endpoint | Behavior |
| --- | --- |
| `GET /v3/client/cs/config` | Query a single config. It does not provide HTTP long polling. |
| `POST /v3/client/ns/instance` | Register an instance, or send heartbeat when `heartBeat=true`. |
| `DELETE /v3/client/ns/instance` | Deregister an instance. Missing instance is still successful. |
| `GET /v3/client/ns/instance/list` | List enabled instances for a service. Disabled instances are filtered out. |
| `GET /v3/client/ai/prompt` | Query prompt by version, label, or latest. |
| `GET /v3/client/ai/skills` | Download online skill package as a zip response. |
| `GET /v3/client/ai/agentspecs` | Query AgentSpec by version, label, or latest. May allow anonymous access. |
| `GET /v3/client/ai/agentspecs/search` | Search enabled AgentSpecs for runtime use. |

## 5. Admin API Implemented Behavior

Admin APIs are operator-oriented and default to `ApiType.ADMIN_API`. The standard
Nacos 3.x Admin API uses the `/v3/admin/*` path. v1/v2 Admin APIs have been
removed from the current Nacos main distribution, and new integrations should
migrate to the v3 Admin API. If v1/v2 Admin APIs are still required during
migration, use the
[nacos-api-legacy-adapter](https://github.com/nacos-group/nacos-api-legacy-adapter)
approach and follow the
[Compatibility And Deprecation Spec](../design/compatibility-deprecation-spec.md).
`nacos.core.auth.admin.enabled` only controls whether Admin API authentication is
enabled; it is not a legacy Admin API compatibility switch.

Current modules:

- `core`: connection loader, cluster node data, Raft and ID ops, namespace,
  plugin, and server state.
- `cs`: config CRUD, metadata, batch operations, history, listener, capacity,
  metrics, and ops.
- `ns`: service, instance, cluster, health, client, and naming ops.
- `ai`: MCP, A2A, Prompt, Skill, AgentSpec, and Pipeline management.

Implemented behavior to document more explicitly:

- Naming service creation creates persistent service metadata.
- Open naming instance heartbeat uses the same `POST /v3/client/ns/instance`
  endpoint and returns `INSTANCE_NOT_FOUND` when re-registration is needed.
- Config query decrypts encrypted content before returning Admin API detail.
- Config publish encrypts content when no encrypted data key is supplied and the
  configured encryption handler applies.
- AI Prompt contains deprecated compatibility endpoints and newer lifecycle
  endpoints in the same controller.

## 6. Console API Implemented Behavior

Console APIs serve the Nacos web console and are not the same stability surface
as Open APIs. They default to `ApiType.CONSOLE_API` and often use console-specific
resource names, `ONLY_IDENTITY`, or UI-oriented response models.
Console deployment, UI, and handler boundaries are defined by the
[Console Spec](../console/console-spec.md).

Console API modules mirror Admin modules where the UI needs them:

- server state and health;
- core cluster, namespace, and plugin;
- config and history;
- naming service and instance;
- AI resources and copilot.

Console API docs should avoid presenting console-only endpoints as recommended
automation APIs. Automation users should prefer Admin APIs unless a feature is
intentionally console-only.

## 7. Auth API Implemented Behavior

The v3 auth API lives in the default auth plugin, not in core:

```text
/v3/auth/user
/v3/auth/role
/v3/auth/permission
```

Implemented behavior:

- user management supports create, delete, password update, login, list, and
  search.
- role management supports add, delete, list, and search.
- permission management supports add, delete, and list.
- first-admin bootstrap is implemented by `POST /v3/auth/user/admin`.

The default auth plugin is shipped with Nacos, so its v3 auth endpoints should
follow the Nacos HTTP API rules and the
[Auth Plugin Spec](../auth/auth-plugin-spec.md).

## 8. Documentation Gap Notes

This is not a bug list. It records places where the current documentation and
code appear to describe different surfaces.

- Admin AI Prompt lifecycle: code adds `/governance`, `/version`, `/draft`,
  `/submit`, `/publish`, `/force-publish`, `/online`, `/offline`, `/labels`,
  `/description`, and `/biz-tags`; docs mostly cover legacy `/detail`, `/label`,
  `/metadata`, plus list and versions.
- Console AI Prompt lifecycle: console code mirrors the Admin lifecycle under
  `/v3/console/ai/prompt`; docs mostly cover legacy `/detail`, `/label`, and
  `/metadata`.
- Pipeline list/detail: code exposes `/v3/*/ai/pipelines/list`, `/detail`, and
  `/{pipelineId}`; docs show `/v3/*/ai/pipelines` and `/{pipelineId}`.
- Force publish: code has `POST /force-publish` for Prompt, Skill, and
  AgentSpec; docs do not consistently describe the privileged operation.
- AgentSpec version meta: code has `GET /v3/admin/ai/agentspecs/version/meta`;
  it is not documented in the admin API doc.
- Auth v3: code exposes `/v3/auth/user`, `/role`, and `/permission`; the three
  website API files do not cover this API surface.
- Config Open API exception handling: `ConfigOpenApiController` lacks
  `@NacosApi` while most v3 controllers have it; Open API docs assume unified
  response.
- Config and Naming exception handlers: Config and Naming still have historical
  module-level `ControllerAdvice` classes that may return plain text error
  bodies. They should converge to `NacosApiExceptionHandler` for v3 APIs.

## 9. Deprecated Compatibility Notes

Some v3 AI APIs were released before this spec existed and were later replaced by
clearer lifecycle or REST-style APIs. These old endpoints should be treated as
deprecated compatibility APIs:

- AI Prompt legacy endpoints such as `/detail`, `/label`, and `/metadata`.
- Pipeline legacy REST-style endpoints that do not match the current `/list` and
  `/detail` shape.

Compatibility endpoints may remain available for a transition period, but the
user-facing documentation should describe the new APIs as the primary contract.
Deprecated endpoints should be documented only in compatibility sections with
migration guidance, following the
[Compatibility And Deprecation Spec](../design/compatibility-deprecation-spec.md).
