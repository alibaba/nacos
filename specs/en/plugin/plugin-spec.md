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

# Nacos Plugin Spec

## Purpose

Nacos uses plugins and SPI extensions to keep cross-cutting infrastructure and
replaceable domain capabilities outside the fixed core. A plugin may provide
authentication, resource visibility, data source dialects, encryption, tracing,
flow control, environment adaptation, AI pipeline behavior, AI storage behavior,
AI resource import behavior, or Java client-side request adaptation.

The plugin mechanism must let Nacos keep a stable core model while allowing
deployments to choose an implementation that matches their identity system,
database, observability stack, or extension scenario.

## Plugin Identity

Every plugin is identified by:

- `pluginType`: the extension category, such as `auth` or `visibility`.
- `pluginName`: the implementation name inside that category, such as `nacos`.
- `pluginId`: the runtime identifier in the form `{pluginType}:{pluginName}`.

The `pluginId` is the value used by the admin plugin API, cluster state
synchronization, persisted plugin state, and user-facing diagnostics.

## Plugin Types

The current plugin type registry is defined by `PluginType`.

| Type | Purpose | Contract |
|------|---------|----------|
| `auth` | Authentication and authorization implementation. | [Auth Plugin Spec](../auth/auth-plugin-spec.md) |
| `visibility` | Resource visibility and query visibility advisory. | [Visibility Plugin Spec](../auth/visibility-plugin-spec.md) |
| `datasource-dialect` | Database dialect and persistence adaptation. | [Data Source Dialect Plugin Spec](datasource-dialect-plugin-spec.md) |
| `config-change` | Configuration change extension. | [Config Change Plugin Spec](config-change-plugin-spec.md) |
| `encryption` | Encryption and decryption extension. | [Config Encryption Plugin Spec](config-encryption-plugin-spec.md) |
| `trace` | Trace and observability extension. | [Trace Plugin Spec](trace-plugin-spec.md) |
| `environment` | Environment adaptation extension. | [Environment Plugin Spec](environment-plugin-spec.md) |
| `control` | Traffic and control extension. | [Control Plugin Spec](control-plugin-spec.md) |
| `ai-pipeline` | AI registry pipeline extension. | [AI Publish Pipeline Plugin Spec](ai-pipeline-plugin-spec.md) |
| `ai-storage` | AI registry storage extension. | [AI Storage Plugin Spec](ai-storage-plugin-spec.md) |
| `ai-resource-import` | AI registry external import extension. | [AI Resource Import Plugin Spec](ai-resource-import-plugin-spec.md) |

Domain-specific plugin contracts are defined by their own specs. This document
defines the common runtime contract shared by all plugin categories.

[Addressing extension](addressing-plugin-spec.md) is documented with plugin
specs for continuity with the public plugin documentation, but current server
code handles it through `MemberLookup` and does not register it in `PluginType`.

## Runtime Location

Nacos has two plugin-like extension surfaces:

| Runtime | Loading model | State owner | Examples |
|---------|---------------|-------------|----------|
| Server plugin | Domain SPI plus `PluginProvider`, listed and managed by server plugin APIs where supported. | Nacos server process and, for managed plugins, server plugin state. | `auth`, `visibility`, `datasource-dialect`, `control`, `trace`. |
| Java client extension | Java SPI or SDK API loaded inside the client process. | Client classpath, client properties, and SDK instance lifecycle. | `ServerListProvider`, `ClientAuthService`, `IConfigFilter`, client-side config encryption. |

Client extensions are not managed by `/v3/admin/core/plugin/*` and do not have a
server-side `PluginStateCheckerHolder` decision unless their corresponding
server plugin also participates in request handling. They must still follow
Nacos resource identity, authorization, and payload semantics because they shape
requests sent by the SDK.

## Execution Modes

Plugin categories do not all execute in the same shape. A plugin type must
define its execution mode explicitly.

| Mode | Meaning | Examples |
|------|---------|----------|
| Exclusive selection | One implementation is selected for the process or request scope. Other loaded implementations remain inactive for that decision. | `auth`, `datasource-dialect` |
| Configured single service | Multiple implementations may be loaded, while a domain chooses one service by configuration or request context. | `visibility`, `ai-resource-import` |
| Ordered chain | Multiple matching plugins are invoked in a stable order. Each node may contribute a result, and the domain defines whether failure stops the chain. | `ai-pipeline`, `config-change` |
| Subscriber or broadcast | Multiple subscribers observe the same event or trace point without owning the primary decision. | `trace`, event-style extensions |

For chained plugins, the domain SPI must define:

- How candidate plugins are selected for a resource or pointcut.
- Which field controls ordering, such as `getPreferOrder()` or `getOrder()`.
- Whether execution is serial or parallel.
- Whether a failed plugin stops the chain or only records a failed result.
- How partial results are persisted and exposed.

The core plugin manager records loaded and enabled plugins; it does not by
itself define the execution mode. Domain managers are responsible for applying
the mode consistently.

## SPI Layers

Nacos plugins have two related SPI layers:

1. Domain SPI, such as `AuthPluginService` or `VisibilityService`, defines the
   behavior required by the owning domain.
2. Core plugin SPI, `PluginProvider`, exposes plugin instances to the core
   plugin manager for listing, status management, configuration, and
   observability.

A plugin that needs dynamic configuration implements `PluginConfigSpec`. A
plugin category that supports enable or disable checks must use
`PluginStateCheckerHolder` rather than keeping an independent status source.

## Loading And Lifecycle

Plugin implementations are discovered with the Nacos SPI loader. Deployments may
provide plugins from the classpath or from the server plugin directory. The
plugin implementation must be loadable without changing Nacos server code.

The core `PluginManager` discovers `PluginProvider` implementations after the
server application is ready. Domain managers may also load their domain services
through SPI, but availability decisions must still respect the plugin state held
by the core plugin manager.

Plugin startup must be deterministic:

- A plugin type and name pair must map to one runtime plugin instance.
- Duplicate plugin names in the same type are invalid for stable operation.
- Plugin implementations must not change the meaning of shared Nacos resource
  identifiers, response envelopes, or error conventions.

## State And Configuration

Plugin state has two levels:

- Loaded: the implementation exists in the runtime.
- Enabled: the implementation may participate in request handling.

Most plugin types are enabled by default after loading. Exclusive plugin types
select one default implementation:

| Type | Default selection rule |
|------|------------------------|
| `auth` | The implementation named by `nacos.core.auth.system.type`, default `nacos`. |
| `datasource-dialect` | The configured SQL platform, default `derby`. |

Critical plugins cannot be disabled while the server depends on them. The
current critical set includes built-in data source dialects and the default AI
storage plugin required by the server.

Plugins with `PluginConfigSpec` expose config definitions, current config, and
config application behavior. Cluster-wide status or config changes must be
synchronized through the plugin state operation path unless the request is
explicitly local only.

### Configuration Definition

Plugin config items are described by `ConfigItemDefinition`. The `key` field is
the canonical item key inside the plugin implementation and does not include the
`nacos.plugin.{pluginType}.{pluginName}.` prefix. Static configuration should
prefer this normalized full key:

```text
nacos.plugin.{pluginType}.{pluginName}.{itemKey}
```

Config definitions may declare the following metadata:

| Field | Meaning |
|-------|---------|
| `aliases` | Historical static config keys for compatibility and migration hints. |
| `sensitive` | Whether the value is sensitive. Query APIs must mask it before returning. |
| `effectMode` | Effect mode. `RUNTIME` can take effect at runtime, and `RESTART` requires restart. |

`aliases` are used when reading compatible static configuration and may also be
accepted as migration-compatible API input. After normalization, aliases must
not be written into runtime persistence files or local-only memory maps. If an
input contains multiple aliases for the same item, the first alias declared in
the definition takes effect and the server logs the ignored aliases.

### Config Sources And Value Metadata

Effective plugin config values are computed by a unified resolution flow. Source
priority is:

```text
LOCAL_ONLY > RUNTIME_PERSISTED > STATIC > DEFAULT
```

| Source | Meaning |
|--------|---------|
| `DEFAULT` | Value from `ConfigItemDefinition.defaultValue`. |
| `STATIC` | Value from static configuration, such as `application.properties`, environment variables, JVM parameters, or Spring parameters. |
| `RUNTIME_PERSISTED` | Cluster-wide runtime override. It may currently be persisted as the final content in `plugin-configs.json`. |
| `LOCAL_ONLY` | Current-node override for diagnosis or emergency handling, not synchronized to the cluster. |

Plugin detail responses may add a `configValueMetas` map keyed by canonical item
key. Each `PluginConfigValueMeta` describes the current source and overridden
state of one config item. `overridden` ignores `DEFAULT` and should be `true`
only when the same key has multiple non-default sources.

Runtime persisted config and local-only config store only values by
`pluginId + itemKey`. They do not store normalized full keys, alias keys,
source, or version information.

### Config Update Compatibility

Plugin detail APIs must remain additively compatible: existing `config` and
`configDefinitions` fields remain available. `config` may represent the current
effective config, and the added `configValueMetas` map carries source and
overridden metadata by canonical item key.

`PUT /v3/admin/core/plugin/config` and the matching Console API keep the current
full override map update semantics. `localOnly=true` updates only the current
node local-only override; otherwise the request updates the cluster-wide runtime
persisted override. Key normalization and `effectMode` checks are server-side
logic and are not exposed as new API parameters. Fields marked
`effectMode=RESTART` must not be applied immediately by runtime updates.
Canonical item keys, normalized full keys, and compatible alias keys are
normalized to item keys before validation and storage. An undefined key or an
alias that ambiguously matches multiple config items must produce a parameter
validation error.

## Admin API

The core plugin admin API is:

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/v3/admin/core/plugin/list` | List loaded plugins, optionally filtered by type. |
| `GET` | `/v3/admin/core/plugin/detail` | Read one plugin detail with effective config and optional value metadata. |
| `PUT` | `/v3/admin/core/plugin/status` | Enable or disable a plugin. |
| `PUT` | `/v3/admin/core/plugin/config` | Update plugin configuration. |

These endpoints are Admin APIs and require console-scoped authorization as
defined by the [HTTP Authorization Spec](../http-api/authorization-spec.md).
Plugin management must use the standard v3
[response and error model](../http-api/response-error-spec.md).

## Design Requirements

Plugin implementations must follow these rules:

- Use existing Nacos [resource identifiers](../design/resource-model-spec.md)
  and domain models instead of inventing an incompatible model for the same
  resource.
- Preserve v3 [HTTP API](../http-api/api-spec.md) response, error, and
  authorization conventions for any plugin-provided HTTP APIs.
- Expose only plugin-owned configuration through `PluginConfigSpec`.
- Keep cluster-wide state changes synchronized unless the caller explicitly
  requests a local-only operation for diagnosis or emergency handling.
- Document security-sensitive defaults and deployment requirements in the
  plugin implementation spec.

The plugin mechanism is an extension boundary, not a license to bypass Nacos
resource, API, or security rules.
