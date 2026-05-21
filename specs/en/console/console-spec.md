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

# Console Spec

This document defines the Nacos Console domain. Console is the management
experience layer for human operators. It contains the web UI, the Console API
backend, and the deployment bridge that lets the UI operate either in the same
process as Nacos Server or as an independent console process.

Console is not the owner of Config, Naming, AI Registry, Core Operations, Auth,
or Plugin domain data. It adapts those domain capabilities into UI workflows and
must preserve the semantics defined by each owning domain spec.

## 1. Scope

Console owns:

- the web UI entry, static assets, default UI selection, and console guide or
  announcement presentation;
- `/v3/console/*` HTTP APIs used by the UI;
- console request filtering, parameter checking, CORS, exception mapping, and
  console auth configuration;
- the handler/proxy layer that maps UI workflows to domain services in merged
  deployment or to remote server APIs in console-only deployment;
- independent console deployment configuration and remote server member
  resolution;
- UI feature gating for Config, Naming, AI, and other console modules.

Console does not own:

- Config data lifecycle, history, gray release, listener state, or dump rules,
  which are defined by the [Config Spec](../config/config-spec.md);
- Naming service, instance, metadata, health, subscription, or consistency
  semantics, which are defined by the [Naming Spec](../naming/naming-spec.md);
- AI resource models and lifecycle, which are defined by the
  [AI Registry Spec](../ai/ai-registry-spec.md);
- namespace, cluster member, server state, plugin state, or server loader
  semantics, which are defined by the
  [Core Operations Spec](../core/core-operations-spec.md);
- auth plugin behavior and RBAC semantics, which are defined by the
  [Auth And Permission Spec](../auth/auth-permission-spec.md);
- plugin extension contracts, which are defined by the
  [Plugin Spec](../plugin/plugin-spec.md).

## 2. Deployment Model

Nacos 3.x separates the console network surface from the server HTTP API
surface. The deployment model is controlled by `nacos.deployment.type`:

| Type | Meaning | Expected contexts |
| --- | --- | --- |
| `merged` | Default mode. Core, Server Web, and Console run in one process. | Local evaluation, simple deployments, or compatibility scenarios. |
| `server` | Server runs without Console. | Production server clusters, especially when Console is deployed separately. |
| `console` | Console runs without local Nacos Server domain services. | Independent UI/backend deployment for safer management-plane isolation. |

Rules:

- `merged` must start the core context, server web context, and console context;
- `server` must start server-side contexts and must not expose Console UI or
  Console API;
- `console` must start only the console context and must obtain domain data from
  remote Nacos Server nodes;
- an unsupported deployment type must fail fast during bootstrap;
- Console deployment must be treated as an internal network component. Nacos is
  designed as an IDC/internal infrastructure component and should not be exposed
  directly on the public Internet.

## 3. Port And Context Path Model

Nacos 3.x uses separate network ports for service APIs and Console:

| Port | Purpose |
| --- | --- |
| `8848` by default | Nacos HTTP Open/Admin API port. |
| `9848` by default | Client gRPC port. |
| `9849` by default | Server-to-server gRPC port. |
| `7848` by default | JRaft server port. |
| `8080` by default | Nacos Console UI and Console API port. |

Rules:

- Console port is configured independently by `nacos.console.port`;
- Console context path is configured by `nacos.console.contextPath`;
- remote Nacos Server context path for console-only deployment is configured by
  `nacos.console.remote.server.context-path` and defaults to `/nacos`;
- the server HTTP API context path remains outside controller mappings, as
  defined by the [HTTP API Spec](../http-api/api-spec.md);
- external exposure should be minimal. In typical deployments, only the Console
  port and client gRPC port should be exposed to intended internal callers, and
  server-to-server ports should remain private.

## 4. Console API Audience

Console APIs are UI backend APIs. They are not Open APIs and should not be
presented as the recommended automation surface. Automation clients should use
Admin APIs or Maintainer SDKs unless a capability is intentionally console-only.

Rules:

- Console APIs must use the `/v3/console/{module}/...` audience prefix;
- Console APIs must declare `ApiType.CONSOLE_API` when secured;
- Console APIs may use UI-oriented request and response models, but JSON
  responses should still follow the shared `Result<T>` rule unless the
  [Response And Error Spec](../http-api/response-error-spec.md) defines an
  exception;
- Console APIs may evolve faster than Open APIs, but documented behavior still
  requires migration guidance for incompatible changes;
- Console API behavior must not redefine domain semantics owned by Config,
  Naming, AI Registry, Core Operations, Auth, or Plugin specs.

The current v3 Console API surface is described by the
[V3 API Surface](../http-api/v3-api-surface.md).

## 5. UI Entry And Static Assets

Console owns the browser entry and static asset serving behavior:

- `/` redirects to the default UI version;
- `nacos.console.ui.default` selects `next` or `legacy`, defaulting to `next`;
- `nacos.console.ui.enabled` controls whether the open-source console UI is
  enabled;
- `announcement` and `console-guide` content are presentation data loaded from
  configured files when present;
- static asset paths and browser resources may be excluded from auth checks, but
  that exclusion must not include domain mutation APIs.

Console guide and announcement content are UI presentation data. They are not
canonical Core server state and must not be used as domain configuration.

## 6. Handler And Proxy Boundary

Console controllers must delegate through proxy and handler interfaces rather
than directly coupling UI controllers to a specific deployment mode.

The current layering is:

```text
Console Controller
  -> Console Proxy
  -> Console Handler interface
     -> Inner Handler  (merged deployment)
     -> Remote Handler (console deployment)
     -> Noop Handler   (disabled feature)
```

Rules:

- controller code owns HTTP shape, validation entry, UI request adaptation, and
  `@Secured` declarations;
- proxy code owns UI workflow composition and delegates to handler interfaces;
- inner handlers may call local domain services because the console shares the
  process with Nacos Server in `merged` mode;
- remote handlers must call remote Nacos Server through Maintainer SDK,
  Admin API, or carefully scoped remote HTTP forwarding;
- noop handlers should be used when a feature is disabled so the UI receives a
  clear unsupported response instead of accidentally loading a partial domain
  implementation;
- handler implementations must return the same domain meaning across deployment
  modes even if the transport path differs.

## 7. Independent Console Deployment

In `console` deployment, Console is a management-plane gateway to one or more
remote Nacos Server nodes.

Rules:

- a server or server cluster must be deployed first with Console disabled or not
  started in the server process;
- Console must discover remote server members through the standard member lookup
  mechanism, usually `cluster.conf` entries in `ip:port` form;
- Console must rebuild remote maintainer clients when the remote server member
  list changes;
- Console must not persist Config, Naming, AI, or Core domain data locally;
- remote requests must use the configured remote server context path;
- remote operations should prefer Maintainer SDK or Admin API contracts instead
  of relying on private server internals;
- file import/export and other large payload workflows must keep the same
  authorization and size limits as the corresponding UI workflow.

Remote member lookup is an operational view for the console process. It does
not change Nacos Server cluster membership by itself.

## 8. Security Boundary

Console has two security directions:

1. browser or operator traffic entering the Console API;
2. Console-originated remote traffic going from an independent Console process
   to Nacos Server.

Rules:

- browser/operator traffic must be controlled by console auth configuration,
  especially `nacos.core.auth.console.enabled`;
- mutating Console APIs must require write permission over the corresponding
  domain or console resource;
- read-only Console APIs must still declare read permission unless they are
  intentionally public health, static asset, bootstrap, or presentation
  endpoints;
- incoming browser requests must not be trusted as server identity requests;
- independent Console-to-Server calls must carry the configured server identity
  when server identity is enabled;
- `nacos.core.auth.server.identity.key` and
  `nacos.core.auth.server.identity.value` must match between independent Console
  and the target Nacos Server deployment;
- the auth plugin token secret used by Console login and token verification must
  be configured consistently with the selected auth plugin behavior.

Console auth is part of the shared auth model and must follow the
[Authorization Spec](../http-api/authorization-spec.md).

## 9. Feature Gating

Console feature availability must follow Nacos runtime capability and function
mode configuration:

- Config console handlers are loaded only when Config is enabled;
- Naming console handlers are loaded only when Naming is enabled;
- AI console handlers require AI function mode and AI extension enablement;
- microservice function mode enables Config and Naming console workflows;
- disabled features should be represented by noop handlers or hidden UI entries,
  not by partially loading incompatible domain services.

Feature gates are presentation and availability controls. They must not redefine
the domain model for Config, Naming, or AI Registry.

## 10. Error Handling And Observability

Console should keep errors readable for UI users while preserving the shared API
contract:

- v3 JSON Console APIs should use `Result<T>` and the shared API exception model
  where possible;
- health probes and static/presentation endpoints may use simpler response
  shapes when explicitly documented;
- error messages returned to browsers must be escaped or sanitized when they may
  contain user-controlled content;
- Console module state should expose low-cardinality operational state such as
  UI enabled/default version and console auth status;
- Console metrics and logs must not include secrets, tokens, full credentials,
  or large user payloads.

## 11. Pending Issues

- Clarify and document which v3 Console health, server state, announcement, and
  guide endpoints are intentionally public.
- Align legacy `ConsoleExceptionHandler` behavior with the shared v3
  `NacosApiExceptionHandler` and response/error rules.
- Define whether independent Console remote forwarding should be fully replaced
  by Maintainer SDK/Admin API calls for import/export and other large payload
  paths.
- Add validation that `console` deployment fails with a clear message when no
  remote server member can be resolved.
- Define a stricter production CORS recommendation because the current default
  is permissive for ease of deployment.
- Decide the long-term compatibility boundary for `legacy` UI static assets and
  legacy console paths.
