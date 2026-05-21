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

# Environment Plugin Spec

## Scope

The environment plugin type lets deployments transform selected server
configuration values before Nacos consumes them. Typical use cases include
decrypting database passwords or adapting deployment-specific properties.

This is an ordered override plugin. Multiple plugins may handle different keys
or the same key. The implementation currently sorts by `order()` ascending and
applies later values over earlier values, so larger order values have higher
override priority. Common lifecycle and state rules are defined by the
[Nacos Plugin Spec](plugin-spec.md). Server startup and `EnvUtil` integration
rules are defined by the
[Server Lifecycle And Environment Configuration Spec](../design/foundation-server-lifecycle-env-spec.md).

The plugin is for deployment-time configuration adaptation. It must run before
Nacos modules consume the final property values, and it must not be used as a
general runtime configuration mutation mechanism.

## Concepts

| Concept | Meaning |
|---------|---------|
| Declared property key | A server property that a plugin may read and transform. |
| Custom value | The transformed property value returned by the plugin. |
| Override order | Deterministic ordering when multiple plugins handle the same key. |

## SPI

Plugins implement `CustomEnvironmentPluginService`.

| Method | Requirement |
|--------|-------------|
| `pluginName()` | Stable plugin name. |
| `propertyKey()` | Set of property keys that the plugin may transform. |
| `order()` | Override order. Larger values have higher final priority. |
| `customValue(property)` | Return transformed values for the declared keys. |

The plugin is exposed to the core plugin manager as type `environment`.

## Execution Rules

The plugin manager passes only the declared keys to each plugin. Returned keys
outside the declared set are removed. Returned entries with null values are
removed before the final property map is used.

Environment plugins must be deterministic during bootstrap. They must not depend
on Nacos modules that are initialized after configuration binding.

Plugins must return values only for declared keys. A plugin may omit a declared
key when it has no transformation to apply. The plugin manager removes unknown
keys and null values before merging results.

When multiple plugins return the same key, the later applied value wins. Because
the current manager applies plugins in ascending `order()`, larger order values
have higher final priority.

## Configuration

The deployment switch documented for environment plugins is:

```properties
nacos.custom.environment.enabled=true
```

The current environment manager loads SPI services directly and uses the
deployment switch above. When this plugin type is routed through the core plugin
manager, runtime availability should converge on unified plugin state for
`environment:{pluginName}`.

Plugins should document:

- the exact property keys they transform;
- whether the original value is required;
- failure behavior when external secret systems or deployment APIs are
  unavailable;
- whether transformed values may be logged by downstream Nacos modules.

If a deployment uses environment plugins to prepare encrypted or secret
configuration values, the cryptographic boundary must still follow the
[Config Encryption Plugin Spec](config-encryption-plugin-spec.md).
