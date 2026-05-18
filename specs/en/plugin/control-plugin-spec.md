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

This is a configured single-service plugin. The configured control manager type
selects one `ControlManagerBuilder`. If no type is configured or the selected
plugin cannot be loaded, Nacos uses no-limit default managers. Common lifecycle
and state rules are defined by the [Nacos Plugin Spec](plugin-spec.md), and the
bundled implementation is defined by the
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

Control plugins implement `ControlManagerBuilder`.

| Method | Requirement |
|--------|-------------|
| `getName()` | Stable plugin name. |
| `buildConnectionControlManager()` | Build connection control manager. |
| `buildTpsControlManager()` | Build TPS control manager. |

External rule storage plugins implement `ExternalRuleStorageBuilder` and are
selected independently through control configuration.

The plugin is exposed to the core plugin manager as type `control`.

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

Custom control plugins may support non-JSON rule text by overriding the rule
parser methods. Custom TPS plugins may support sliding windows or other counter
algorithms by overriding the barrier creator.

## Selection And State

The selected manager implementation is named by:

```properties
nacos.plugin.control.manager.type=${controlPluginName}
```

The selected control plugin must also respect unified plugin state for
`control:{pluginName}` when integrated through the core plugin manager.

Point names are part of the public control contract. New `@TpsControl` points
must use stable names, document the protected operation, and preserve the name
when HTTP and gRPC endpoints represent the same semantic operation.

## Degradation

Control plugins affect request admission. If plugin construction fails, Nacos
must fall back to no-limit managers and log the failure unless the deployment
explicitly configures a fail-fast policy.

Runtime plugin exceptions must not corrupt request state. For monitoring-only
rules, failures should be logged and skipped. For intercepting rules, the
selected plugin owns whether a failure means pass, reject, or fail fast, and
that behavior must be documented by the implementation spec.
