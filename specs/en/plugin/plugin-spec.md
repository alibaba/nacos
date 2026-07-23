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
| `EXCLUSIVE` | One implementation is selected for the process or request scope. Other loaded implementations remain inactive for that decision. | `auth`, `datasource-dialect`, `control` |
| `ROUTED` | Multiple implementations may be loaded, while a domain chooses one service by configuration, resource metadata, or request context. | `encryption`, `visibility`, `ai-storage`, `ai-resource-import` |
| `CHAIN` | Multiple matching plugins are invoked in a stable order. Each node may contribute a result, and the domain defines whether failure stops the chain. | `config-change`, `environment`, `ai-pipeline` |
| `BROADCAST` | Multiple subscribers observe the same event or trace point without owning the primary decision. | `trace`, event-style extensions |

For chained plugins, the domain SPI must define:

- How candidate plugins are selected for a resource or pointcut.
- Which field controls ordering, such as `getPreferOrder()` or `getOrder()`.
- Whether execution is serial or parallel.
- Whether a failed plugin stops the chain or only records a failed result.
- How partial results are persisted and exposed.

The core plugin manager records loaded and enabled plugins; it does not by
itself define the execution mode. Domain managers are responsible for applying
the mode consistently.

Execution mode and criticality are plugin-type capabilities rather than properties of a particular
built-in implementation. The shared `PluginType` must expose `executionMode` and `critical`.
The existing `exclusive` information remains derived from `executionMode == EXCLUSIVE` for API
compatibility. Whether an implementation is configurable is derived from
`PluginConfigSpec.isConfigurable()`; configurable and zero-config implementations may coexist under
the same plugin type.

## SPI Layers

Nacos plugins have two related SPI layers:

1. Domain SPI, such as `AuthPluginService` or `VisibilityService`, defines the
   behavior required by the owning domain.
2. Core plugin SPI, `PluginProvider`, exposes plugin instances to the core
   plugin manager for listing, status management, configuration, and
   observability.

Unified domain plugin SPIs extend `PluginConfigSpec`. Its compatibility defaults expose no
definitions, an empty current map, and a no-op apply callback, so an implementation compiled against
an older domain SPI and a new zero-config implementation both remain `configurable=false`. A plugin
that declares at least one `ConfigItemDefinition` is configurable and must implement the current-map
and apply callbacks. `environment` and `control` remain bootstrap exceptions until their unified
configuration lifecycle is designed; `ai-resource-import` remains outside unified management until
its redesign. A plugin category that supports enable or disable checks must use
`PluginStateCheckerHolder` rather than keeping an independent status source.

`PluginConfigDefinitionSpec` is the definition-only parent contract for a factory
that must expose metadata before creating an instance. Runtime plugin instances
that participate in unified configuration must implement the complete
`PluginConfigSpec`; a definition-only factory does not receive or own effective
configuration.

## Loading And Lifecycle

Plugin implementations are discovered with the Nacos SPI loader. Deployments may
provide plugins from the classpath or from the server plugin directory. The
plugin implementation must be loadable without changing Nacos server code.

After the Spring context is refreshed, the core `PluginManager` discovers lightweight
`PluginProvider` implementations. It invokes `getAllPlugins` immediately only for plugin types
whose domain policy enables loading. Active critical types always load regardless of the optional
loading predicate. For a deferred non-critical type, a later server configuration refresh that
enables loading must discover its implementations, restore persisted implementation state,
resolve effective configuration, and invoke `applyConfig` before those implementations are
exposed for execution. A loaded type is retained when its loading predicate later becomes false;
the owning domain entry switch continues to gate execution.

The loading predicate does not replace implementation state. Its default is `true` for binary
compatibility and a domain should override it only when it owns a type-wide module or capability
switch. A deferred type is enabled through that static or domain switch rather than by addressing
an implementation that has not yet been discovered through the plugin API.

`ApplicationReadyEvent` is only an idempotent fallback for non-standard embedded startup paths.
Domain managers may construct their services earlier through SPI, but a type that opts into
deferred loading must not independently instantiate its implementations while its loading
predicate is false. Final configuration and runtime participation must follow the unified result
before the server becomes available.

Plugin startup must be deterministic:

- A plugin type and name pair must map to one runtime plugin instance.
- Duplicate plugin names in the same type are invalid for stable operation.
- Plugin implementations must not change the meaning of shared Nacos resource
  identifiers, response envelopes, or error conventions.

## State And Configuration

Plugin state has two levels:

- Loaded: the implementation exists in the runtime.
- Enabled: the implementation may participate in request handling.

Core module switches and plugin state are separate layers. Module switches such as
`nacos.core.auth.enabled`, `nacos.core.auth.admin.enabled`, and
`nacos.core.auth.console.enabled` decide whether a core request path invokes the plugin system.
They are not implementation configuration and must not be modified by the plugin management API.
A plugin may remain loaded, enabled, and initialized while its owning core module is disabled.

Each managed plugin type may provide one internal `PluginTypePolicy`. The policy is owned by the
domain module rather than by the core plugin manager, and defines:

- whether the domain currently requires the plugin type;
- whether implementation loading is currently enabled for a non-critical type;
- the initial enabled state of each discovered implementation;
- the concrete implementation names required while a critical type is active;
- the selection property and activation reason used in diagnostics.

The core initializes every policy once before plugin discovery. Selection and provider properties
with `RESTART` semantics must be captured during that initialization; later server configuration
refreshes may re-evaluate dynamic module activation switches, but must not change the required
implementation until Nacos restarts.

`PluginType.isCritical()` remains the single static declaration that a type can be required for
correct server operation. A critical type is enforced only while its domain policy is active. The
core manager performs the generic validation; it must not contain type-specific property keys or
selection branches.

Before Nacos reports startup success, every active critical type must have all concrete
implementations required by its policy discovered and enabled. A missing implementation, a missing
selection for an active exclusive type, or a disabled required implementation is a startup error.
The error must identify the plugin type, required implementation, and relevant selection
configuration. Nacos must not silently select or re-enable an arbitrary fallback implementation.

Policies whose providers expose usable instances before the Spring context refreshes must support
pre-refresh validation so missing auth or datasource implementations fail before dependent business
beans are created. A policy whose implementations require Spring-managed resources to be built must
declare that pre-refresh validation is unsupported; the unified manager validates that type after
context refresh and still before Nacos reports startup success. Deferring this validation must not
weaken the required implementation or enabled-state checks.

The same validation runs before an accepted runtime state change, after restoring a state snapshot,
and after server configuration refresh changes whether a policy is active. A failed validation
keeps the proposed plugin state unapplied. `critical` in plugin detail describes whether that
specific enabled implementation is currently required, not merely whether its type can ever be
critical.

An accepted persisted state change follows validate-persist-apply order. The complete candidate
state is validated before storage is touched, the durable state projection must succeed before the
manager mutates its in-memory state, and a persistence failure leaves the previous in-memory state
unchanged and retryable. A `localOnly` state change intentionally skips persistence and applies only
to the current node.

The `plugin_state` consensus group may restore a snapshot while the Spring context is still being
created, before the unified manager has discovered implementations. In that phase, the manager
must validate the snapshot value format and stage or persist the complete state without treating
an empty registry as a missing critical implementation. Unified startup then discovers providers,
merges the staged state, and performs the same strict critical validation before Nacos reports
startup success. Snapshot restoration after manager initialization continues to validate the
candidate final state before applying it. The snapshot `states` map is the complete persisted
override map rather than a patch: restoration replaces the local persisted map as a whole, entries
absent from the snapshot remove stale local overrides, and loaded non-exclusive implementations
without an override return to their startup policy default. Entries for implementations that are
not loaded yet remain persisted and are applied if their plugin type is loaded later. Exclusive
implementation selection remains controlled by its restart-required selection property.

Built-in switches audited during the unified-state migration are classified as follows:

| Configuration | Ownership and migration behavior |
|---------------|----------------------------------|
| `nacos.core.auth.enabled`, `nacos.core.auth.admin.enabled`, `nacos.core.auth.console.enabled` | Core request-entry switches; excluded from plugin state. |
| `nacos.extension.ai.enabled` | AI module switch; excluded from plugin state. |
| `nacos.core.config.plugin.{name}.enabled` | Historical implementation switch; accepted only as an initial-state compatibility alias for `nacos.plugin.config-change.{name}.enabled`. |
| `nacos.plugin.visibility.enabled`, `nacos.plugin.ai-pipeline.enabled` | Existing domain-capability entry switches; they continue to gate whether the core path enters visibility or AI pipeline, remain dynamically readable, and are not converted into implementation states. |
| `nacos.plugin.visibility.type` | Historical visibility selector; accepted only to derive the initial state of the named implementation. Runtime routing uses enabled implementations and domain input. |
| `nacos.plugin.ai-pipeline.type` | Historical pipeline-chain membership input. Core uses it only to derive initial implementation states with `RESTART`; implementation configuration and ordering use each node's `PluginConfigSpec`. |
| `nacos.plugin.datasource.log.enabled` | Datasource behavior/logging configuration, not implementation state. |
| `nacos.ai.resource.import.enabled` | Historical AI import path; its removal or migration is deferred with the AI importer redesign. |

New family-wide switches must not duplicate per-implementation state. A core-module or
domain-capability entry switch may gate an entire capability, but it cannot select or enable a
particular implementation. Implementation participation is represented only by per-implementation
plugin state.

Exclusive plugin types covered by unified startup selection use the following standard static key:

```text
nacos.plugin.{pluginType}.type={pluginName}
```

Selection is startup configuration and consistently takes effect with `RESTART`. Historical
selection keys are aliases:

| Type | Standard key | Historical alias | Default |
|------|--------------|------------------|---------|
| `auth` | `nacos.plugin.auth.type` | `nacos.core.auth.system.type` | `nacos` |
| `datasource-dialect` | `nacos.plugin.datasource-dialect.type` | `spring.sql.init.platform` | `derby` |

The standard key takes precedence when both forms are present, and reading an alias must emit a
migration warning. Exclusive selection currently affects startup resources such as Spring beans
and datasources, so the plugin status API must not report a switch as dynamically effective.
Changing selection requires updating the static key and restarting the server. Runtime selection
may only be opened after the owning domain provides a controlled reinitialization lifecycle.
`control` remains a bootstrap exception: its current selector is
`nacos.plugin.control.manager.type`. The management API reports the selected builder but rejects
runtime state changes until the control manager has a controlled rebuild lifecycle and its selector
is migrated to the standard form.

Non-exclusive implementations may provide an initial enabled state with:

```text
nacos.plugin.{pluginType}.{pluginName}.enabled=true|false
```

Runtime changes are managed by the plugin API and unified plugin state. Persisted state takes
precedence over the static initial value. Keys without an implementation name, such as
`nacos.plugin.{pluginType}.enabled`, are not implementation state. When an existing key actually
gates a core module or domain capability, the owning domain continues to read it and persisted
implementation state must not bypass it. All enabled chain and broadcast implementations
participate, while routed types may select only from enabled candidates.

`critical=true` means that an active plugin type must retain its policy-required usable
implementations; it does not make every built-in implementation permanently non-disableable. The
current critical types are `auth`, `datasource-dialect`, and `ai-storage`. The owning domain policy
decides when the type is active and which concrete implementations are required, while the core
manager rejects startup when those implementations are missing or disabled. The management API
must also reject updates that would leave an active critical type without its required usable
implementations. Module switches remain owned by the core domain rather than by plugin state.

The existing response field `critical` continues to mean that the concrete implementation cannot
currently be disabled by itself, so it may change as peer implementation states change. List and
detail responses add `typeCritical` and `executionMode`; the existing `exclusive` field remains and
is derived from the execution mode.

Plugins for which `PluginConfigSpec.isConfigurable()` returns `true` expose config definitions,
current config, and config application behavior. Its default implementation returns `true` only
when `getConfigDefinitions()` is non-null and non-empty. Cluster-wide status or config changes must
be synchronized through the plugin state operation path unless the request is explicitly local only.

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
accepted as migration-compatible API input. Alias use is logged as a migration
hint. After normalization, aliases must
not be written into runtime persistence files or local-only memory maps. If an
input contains multiple aliases for the same item, the first alias declared in
the definition takes effect and the server logs the ignored aliases.
`enabled` is reserved for the unified implementation state and must not be declared as a regular
item key in `ConfigItemDefinition`.

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

The runtime persisted source resolver owns its persistence lifecycle. It loads
the complete source before plugin config initialization, persists a normalized
complete map before replacing one plugin source, exports the in-memory terminal
map for consensus snapshots, and replaces the complete persisted source before
applying a restored snapshot. Plugin orchestration does not directly read or
write `plugin-configs.json`. Persisted plugin enabled state remains owned by the
state-management path.

Every internal source resolver must expose its canonical item-key map through
`getConfig(PluginInfo)`. Reading is independent from update capability:
`DEFAULT` reads definition defaults, `STATIC` reads normalized and alias keys
from the environment, and the two runtime sources read their internal maps.
`isUpdatable` is checked only when replacing a source map. An update replaces
the complete map; an empty map clears all overrides for that plugin and source.
The source contract does not require separate remove or restore operations.

The core source registry owns the enabled resolver set and their fixed order.
The four built-in sources are always registered in the order shown above;
internal storage implementations may replace a resolver at the same source
layer but must not insert a new priority above `LOCAL_ONLY` or merge `DEFAULT`
into `STATIC`. Source implementation selection is a startup concern and is not
changed by plugin config update APIs.

### Runtime State Enforcement

Plugin types whose implementations are selected for each runtime operation must check unified
plugin state before invoking an extension. Types currently using this gate include `auth`,
`datasource-dialect`, `encryption`, `trace`, `visibility`, `config-change`, `ai-pipeline`, and
`ai-storage`. A disabled plugin remains loaded and visible to management APIs but does not
participate in domain execution. The gate is not implementation selection: exclusive types still
use startup `type` selection, routed types still use domain routing, and chain or broadcast types
invoke every enabled implementation.

Execution mode and criticality are type capabilities and must come from the shared `PluginType`
definition. Core and Console API adapters must not maintain separate hard-coded exclusive type or
critical implementation lists.

Bootstrap or build-time types cannot satisfy this contract with a late runtime
check. `control` caches managers built before unified persisted state is loaded,
and `environment` transforms Spring properties before the core plugin manager
is ready. Their status capability and restart/bootstrap semantics must be
defined before management APIs can report a state update as effective.
`ai-resource-import` is not currently exposed through `PluginProvider` and is
outside unified state management.

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
`effectMode=RESTART` must not be applied immediately by runtime updates. The
server compares the previous and submitted full map for the target source, so
adding, changing, or removing a `RESTART` item is rejected. Omitting a key from
the submitted map therefore removes its override only when that item is
runtime-effective.
Canonical item keys, normalized full keys, and compatible alias keys are
normalized to item keys before validation and storage. An undefined key or an
alias that ambiguously matches multiple config items must produce a parameter
validation error.

For an item declared `sensitive=true`, a submitted value containing the
standard `******` marker is treated as a masked display value. If the target
source already contains that item, the server preserves the original value
from that same source. If the target source does not contain the item, the
input is ignored and no override is created. This rule also covers values such
as `a******z` and `ab******yz`; it must not copy an effective value from another
source such as `STATIC` into a runtime override. The server logs a warning with
only `pluginId`, item key, and target source, and must not log the value.

### Initialization And Runtime Apply

Startup and runtime updates use the same source resolver and effective config
calculation:

1. The runtime persisted source resolver loads all `plugin-configs.json`
   entries before any plugin config is applied.
2. Every loaded configurable plugin is then resolved and applied, including
   plugins without a persisted override. Startup may apply both `RUNTIME` and
   `RESTART` fields because the plugin is being initialized.
3. A runtime request replaces one complete `RUNTIME_PERSISTED` or `LOCAL_ONLY`
   source map. The server resolves all sources again and invokes the plugin for
   each accepted request, including a same-map request used as a manual retry.

The `STATIC` resolver keeps an accepted per-plugin snapshot instead of reading
live environment values independently for every detail query. Startup captures
all defined static fields and may apply both effect modes. After startup,
`ServerConfigChangeEvent` refreshes the snapshot and runs the same
resolve-validate-apply flow for every configurable plugin whose effective
runtime config changed.

During a static refresh, only fields declared `effectMode=RUNTIME` are accepted
into the running snapshot. An added, changed, or removed `RESTART` field keeps
its startup snapshot value until server restart and produces a warning that
contains the plugin ID and item keys but no config values. Detail queries keep
returning the accepted effective snapshot, so they do not report an unapplied
restart-required environment value as effective. A static change hidden by a
higher-priority source updates source metadata but does not require another
plugin apply when the effective config equals the last successfully applied
snapshot. If apply fails after a static snapshot is accepted, the plugin keeps
its previous applied config and a later refresh retries while the resolved
effective config still differs from that successful snapshot.

Updates for the same plugin are serialized. A runtime persisted update first
persists the normalized complete source map, replaces the resolver source,
resolves and validates the effective config, and then applies it to the plugin.
If persistence fails, the resolver source and plugin are not changed and no
rollback is attempted. If apply fails after the source update, the accepted
source map remains persisted and resolved; the server does not issue an
automatic rollback or compensation update. The API returns an explicit server
error that the config was updated but apply failed, and the server logs the
plugin ID and source without config values. Repeating the same complete map is
a supported manual apply retry. A `LOCAL_ONLY` update follows the same
replace-resolve-apply behavior without persistence or synchronization; its new
local source map also remains when apply fails.

### Console Configuration Workflow

The Console plugin detail view uses the detail API as the authoritative source
for effective values, definitions, and value metadata. It must:

- render `RUNTIME` items as editable controls and render `RESTART` items as
  read-only, with guidance to update the Nacos configuration file and restart;
- show the effective source and override state without revealing unmasked
  sensitive values;
- expose cluster-wide runtime persisted updates and current-node local-only
  updates as explicit, separate modes;
- preserve the full-map update contract by reconstructing the target source
  only from values whose effective metadata identifies that source, then
  applying the user's edits and explicit override removals; effective
  `STATIC` or `DEFAULT` values must not be copied into a runtime source merely
  because the form was submitted.

An effective `LOCAL_ONLY` value can hide an existing runtime persisted value,
and the current detail model intentionally does not expose that lower-priority
value. The Console must therefore block cluster configuration submission while
the current node has any local-only overrides. It may clear the complete
local-only source by submitting an empty map with `localOnly=true`; after the
detail is refreshed, cluster editing can proceed without accidentally deleting
or replacing a hidden persisted value.

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
