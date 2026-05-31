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

Plugin-owned properties use the prefix:

```properties
nacos.core.config.plugin.{pluginName}.*
```

The legacy enablement key documented for plugin packages is:

```properties
nacos.core.config.plugin.{pluginName}.enabled=true
```

Direct lookup through `ConfigChangePluginManager.findPluginServiceImpl` respects
the unified plugin state for `config-change:{pluginName}`. Pointcut execution
also uses the legacy `enabled` property and should converge on the unified state
model when the execution path is updated.

Plugin custom properties are read using the lowercase service type:

```text
nacos.core.config.plugin.{serviceType}.{propertyKey}
```

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
