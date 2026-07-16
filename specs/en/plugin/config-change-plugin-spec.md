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

# Config Change Plugin Spec

## Scope

The config change plugin type lets Nacos run extension logic before or after
configuration mutation operations. Typical uses include audit records, format
validation, whitelist validation, and webhook notification.

This is an ordered chain plugin. Multiple plugins may match the same pointcut
and are executed by `ConfigChangePluginService.getOrder()` in ascending order.
Common lifecycle and state rules are defined by the
[Nacos Plugin Spec](plugin-spec.md).

The design follows an AOP-style model: configuration mutations are pointcuts,
and plugins are woven before or after those pointcuts. The plugin is for config
change governance; it must not redefine config identity or persistence
semantics.

## Concepts

| Concept | Meaning |
|---------|---------|
| Pointcut | A classified config mutation operation and source. |
| Execute type | Whether the plugin runs before or after the pointcut. |
| Before plugin | May validate, reject, or rewrite mutation arguments. |
| After plugin | May observe committed mutations and run best-effort side effects. |
| Plugin properties | Per-plugin configuration passed through `ConfigChangeRequest`. |

## SPI

Plugins implement `ConfigChangePluginService`.

| Method | Requirement |
|--------|-------------|
| `getServiceType()` | Stable plugin name used by plugin management and config. |
| `getOrder()` | Chain order. Lower values execute earlier. |
| `executeType()` | `EXECUTE_BEFORE_TYPE` or `EXECUTE_AFTER_TYPE`. |
| `pointcutMethodNames()` | Pointcuts handled by this plugin. |
| `execute(request, response)` | Plugin logic. |

The plugin is exposed to the core plugin manager as type `config-change`.

## Pointcuts

The current pointcuts are:

| Pointcut | Meaning |
|----------|---------|
| `PUBLISH_BY_HTTP` | Create or update config through [HTTP APIs](../http-api/api-spec.md). |
| `PUBLISH_BY_RPC` | Create or update config through [gRPC APIs](../grpc-api/api-spec.md). |
| `REMOVE_BY_HTTP` | Remove one config through HTTP. |
| `REMOVE_BY_RPC` | Remove one config through gRPC. |
| `IMPORT_BY_HTTP` | Import config files through HTTP or console. |
| `REMOVE_BATCH_HTTP` | Batch remove configs through HTTP. |

Pointcut names are part of the plugin contract. New config mutation paths must
either reuse the matching semantic pointcut or add a new documented pointcut
before third-party plugins are expected to depend on it.

## Request And Response

`ConfigChangeRequest` contains:

| Field | Meaning |
|-------|---------|
| `requestType` | The current pointcut. |
| `requestArgs` | Operation arguments, such as namespace, group, dataId, content, or source-specific values. |

`ConfigChangeResponse` contains:

| Field | Meaning |
|-------|---------|
| `responseType` | The pointcut response type. |
| `success` | When false in a before plugin, the mutation is intercepted. |
| `retVal` | Reserved return value. |
| `msg` | Failure message returned to the caller when interception happens. |
| `args` | Replacement arguments for before plugins. |

Nacos also passes `ConfigChangeConstants.ORIGINAL_ARGS` and
`ConfigChangeConstants.PLUGIN_PROPERTIES` through request arguments.

## Execution Rules

Before plugins may inspect or rewrite the mutation arguments through
`ConfigChangeResponse.args`. If a before plugin sets `success=false`, the
configuration mutation must be intercepted and the failure message returned.

After plugins run only after the owning mutation has executed. They are suitable
for audit, notification, or best-effort side effects. After plugin failure must
not corrupt the committed config state.

Execution order is evaluated after filtering disabled plugins. Before plugins
run synchronously before the mutation. After plugins are scheduled through the
config executor and must be treated as asynchronous. That scheduling follows
the [Task Execution Spec](../design/foundation-task-execution-spec.md).

Before plugins must preserve argument order and type when replacing arguments.
After plugins must not assume that their side effects can roll back the already
committed config mutation.

## Configuration

### Unified Plugin Configuration

A config change plugin that owns configurable properties should also implement
`PluginConfigSpec`. Its canonical full keys use the standard prefix:

```properties
nacos.plugin.config-change.{pluginName}.{itemKey}
```

The implementation declares item keys, legacy aliases, sensitivity, and effect
mode through `ConfigItemDefinition`. The common plugin configuration resolver
loads and applies the effective configuration. For compatibility with the
config change SPI request contract, `ConfigChangeConstants.PLUGIN_PROPERTIES`
contains the implementation's current effective item-key map when the service
implements `PluginConfigSpec`.

Plugin enablement is unified plugin state for
`config-change:{pluginName}` and is not a `ConfigItemDefinition`. Pointcut
candidate lookup is the only runtime enablement gate.

### Legacy Compatibility

Plugins that do not implement `PluginConfigSpec` remain supported through the
deprecated legacy configuration adapter. Their properties continue to use:

```properties
nacos.core.config.plugin.{pluginName}.{propertyKey}
```

The adapter refreshes these static properties on server configuration changes,
strips the plugin prefix, and passes the resulting `Properties` through
`ConfigChangeConstants.PLUGIN_PROPERTIES`. It logs a migration warning the
first time it supplies properties to each legacy plugin. Such a plugin remains
`configurable=false` in the unified plugin API.

The historical enablement property is:

```properties
nacos.core.config.plugin.{pluginName}.enabled=true
```

It is used only to initialize unified plugin state when no persisted state
exists. Missing legacy enablement preserves the historical default of `false`.
Persisted plugin state takes precedence, and subsequent runtime enablement is
managed only through unified plugin state. The legacy `enabled` entry may still
be present in the compatibility `Properties` map, but it is no longer a second
execution gate.

## Reference Implementations

The Nacos server repository defines the SPI and config aspect. Reference
implementations may live in external plugin repositories. Official examples have
included:

| Example | Expected behavior |
|---------|-------------------|
| `webhook` | Send a notification after config changes. |
| `whitelist` | Validate imported config names or suffixes before import. |
| `fileformatcheck` | Validate imported file type or content before import. |

These examples are not part of the built-in server runtime unless their plugin
JARs are added to the server classpath and enabled.
