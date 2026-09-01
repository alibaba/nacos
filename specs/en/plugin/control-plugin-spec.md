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

# Control Plugin Spec

## Scope

The control plugin type provides runtime traffic and connection control for
Nacos server nodes. It covers connection admission, TPS checks, rule parsing,
rule storage, and optional metrics collection.

This is a configured single-service plugin. The configured control type selects
one `ControlManagerBuilder`. A stable adapter exposes the selected builder to
the unified plugin configuration lifecycle and creates its manager bundle only
after effective configuration is applied. If no type is configured or the
selected plugin cannot be loaded, Nacos uses no-limit default managers. Common
lifecycle and state rules are defined by the
[Nacos Plugin Spec](plugin-spec.md), and the bundled implementation is defined by the
[Default Control Plugin Implementation Spec](default-control-plugin-spec.md).

Control is an anti-fragility mechanism. It protects a Nacos node by rejecting or
monitoring requests when access to a control point exceeds configured rules.
Control plugins must not change resource semantics; they only decide whether the
current connection or request may continue.

HTTP and gRPC TPS control hooks are connected through the shared request
filtering model defined by the
[Request Filtering And Runtime Context Spec](../design/foundation-request-context-spec.md).

## Concepts

| Concept | Meaning |
|---------|---------|
| Control point | A named runtime resource that can be measured and limited. |
| Connection control | Admission control for long connections or long polling connections. |
| TPS control | Admission control for request frequency at a named API operation point. |
| Rule storage | Storage that persists rule text for local or external distribution. |
| Rule parser | Parser that converts stored rule text into runtime rule objects. |
| Barrier | Runtime TPS counter and decision component for a point. |

Connection and TPS control are independent. A deployment may provide both
managers, only one manager, or no manager. A missing manager is treated as
no-limit for that dimension.

## SPI

Control plugins implement `ControlManagerBuilder`. The builder extends
`PluginConfigDefinitionSpec`: it declares configuration metadata before manager
construction but does not own effective configuration.

| Method | Requirement |
|--------|-------------|
| `getName()` | Stable plugin name. |
| `buildConnectionControlManager()` | Build connection control manager. |
| `buildTpsControlManager()` | Build TPS control manager. |
| `buildConnectionControlManager(config)` | Build with canonical effective plugin config; the compatibility default delegates to the no-argument method. |
| `buildTpsControlManager(config)` | Build with canonical effective plugin config; the compatibility default delegates to the no-argument method. |

Every builder definition has `RESTART` effect mode until Control defines a
controlled manager replacement and close lifecycle. Runtime or local-only
updates to those fields are rejected by the unified plugin configuration API.

The Control provider wraps each builder in one stable `PluginConfigSpec`
adapter. The adapter delegates definitions to the builder, owns an immutable
effective configuration snapshot, and implements `PluginStartupLifecycle`.
Builder SPI discovery happens once in the Control registry; the provider,
plugin manager, and manager center must not perform independent loads.

External rule storage plugins implement `ExternalRuleStorageBuilder` and are
selected independently through control configuration.

The plugin is exposed to the core plugin manager as type `control`.

## Startup Lifecycle

The Control type uses this startup order:

1. capture the static implementation selection;
2. discover builders once and register stable adapters;
3. restore unified implementation state;
4. resolve and apply effective configuration to configurable adapters;
5. invoke `initialize()` only for the selected, enabled adapter;
6. build connection and TPS managers from the accepted configuration snapshot;
7. install both results as one manager bundle before Nacos is marked as started.

An unselected adapter remains visible in plugin inventory but must not build
managers or start background resources. A zero-config legacy builder still
receives the startup lifecycle with an empty configuration snapshot.

`ControlManagerCenter` exposes stable connection and TPS facades. Callers may
retain those facade references; installing the startup bundle changes the
delegates behind both facades through one bundle reference. TPS points
registered before installation are replayed to the selected TPS manager.
Before installation, the facades provide lightweight no-limit behavior without
creating rule loaders, metrics reporters, or TPS barriers. The manager center
must not reload `ControlManagerBuilder` through SPI.

## Managers

`ConnectionControlManager` owns connection rules and returns
`ConnectionCheckResponse` for connection admission. It may load
`ConnectionMetricsCollector` implementations to report connection metrics.

Required connection manager behavior:

| Method | Requirement |
|--------|-------------|
| `applyConnectionLimitRule(rule)` | Apply the latest connection rule. |
| `check(request)` | Return pass or reject for a connection admission request. |
| `buildConnectionControlRuleParser()` | Optionally override the rule text parser. |

`TpsControlManager` owns TPS points, TPS rules, and barriers. It returns
`TpsCheckResponse` for TPS admission.

Required TPS manager behavior:

| Method | Requirement |
|--------|-------------|
| `registerTpsPoint(pointName)` | Register a control point during startup or route scan. |
| `applyTpsRule(pointName, rule)` | Apply or remove the rule for a point. |
| `check(request)` | Return pass or reject for a TPS request. |
| `buildTpsControlRuleParser()` | Optionally override the rule text parser. |
| `buildTpsBarrierCreator()` | Optionally override time-window and counter behavior. |

## Rule Model

`ConnectionControlRule` contains:

| Field | Meaning |
|-------|---------|
| `countLimit` | Maximum total connection count. A value below 0 means no limit. |
| `monitorIpList` | IP addresses whose connection behavior should be logged in detail. |

`TpsControlRule` contains:

| Field | Meaning |
|-------|---------|
| `pointName` | Control point name. |
| `pointRule` | Rule detail for the control point. |

`RuleDetail` contains:

| Field | Meaning |
|-------|---------|
| `ruleName` | Rule identifier. A point may have multiple rule names in custom plugins. |
| `maxCount` | Maximum allowed count in the period. A value below 0 means no limit. |
| `period` | Counting period. The built-in default is seconds. |
| `monitorType` | `monitor` for observation only, or `intercept` for rejection. |

## Rule Storage

Rules may come from local disk storage or an external rule storage plugin.
Local rules are always available as the safe baseline. External rule storage
must fail closed only when the selected control plugin explicitly requires it.

Rule reloads are published through control rule change events and applied by the
active managers. Local event dispatch follows the
[Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md).
Control metrics and denied observations follow the
[Observability Hooks Spec](../design/foundation-observability-hooks-spec.md).

External rule storage is selected by:

```properties
nacos.plugin.control.rule.external.storage=${controlPluginName}
```

The local rule storage base directory is selected by:

```properties
nacos.plugin.control.rule.local.basedir=${expectedDir}
```

When local TPS rules are stored on disk, `pointName` is one direct child file
name below the TPS rule directory. Directory-control names (`.` and `..`), path
separators, absolute paths, and any normalized path outside that directory must
be rejected before reading, writing, or deleting a rule file.

Custom control plugins may support non-JSON rule text by overriding the rule
parser methods. Custom TPS plugins may support sliding windows or other counter
algorithms by overriding the barrier creator.

## Selection And State

The selected manager implementation is named by the standard key:

```properties
nacos.plugin.control.type=${controlPluginName}
```

The historical key remains a static compatibility alias:

```properties
nacos.plugin.control.manager.type=${controlPluginName}
```

The standard key wins when both are present, and use of the historical key
emits a migration warning. Selection has `RESTART` semantics. The selected
adapter is enabled at startup, other discovered adapters are disabled, and the
plugin status API rejects runtime selection changes.

Point names are part of the public control contract. New `@TpsControl` points
must use stable names, document the protected operation, and preserve the name
when HTTP and gRPC endpoints represent the same semantic operation.

## Degradation

Control plugins affect request admission. Connection and TPS construction
remain independent for compatibility: when construction of one dimension fails
or returns null, that dimension falls back to its no-limit manager and logs the
failure. The two final results are installed together as one bundle, so callers
cannot observe a partially replaced startup state. If the selected builder is
missing, both dimensions remain no-limit.

Runtime plugin exceptions must not corrupt request state. For monitoring-only
rules, failures should be logged and skipped. For intercepting rules, the
selected plugin owns whether a failure means pass, reject, or fail fast, and
that behavior must be documented by the implementation spec.
