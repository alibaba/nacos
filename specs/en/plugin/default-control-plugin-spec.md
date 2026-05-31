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

# Default Control Plugin Implementation Spec

## Scope

The default control implementation is the `nacos` control plugin in
`plugin-default-impl/nacos-default-control-plugin`. It provides simple local
connection count limiting and TPS limiting for a Nacos server node. It is the
bundled implementation of the [Control Plugin Spec](control-plugin-spec.md).

The implementation is node-local. It does not provide cluster-wide quota
coordination by itself. Cluster-wide rule distribution requires an external rule
storage plugin or operational synchronization outside this implementation.

## Enablement

Enable the implementation with:

```properties
nacos.plugin.control.manager.type=nacos
```

If this property is absent, the control manager center uses no-limit managers.
If the `nacos` builder fails to create either manager, that manager falls back to
the no-limit implementation and logs the failure.

## Local Rule Files

The implementation reads local JSON rule files by default from:

```text
${nacos.home}/data/connection/limitRule
${nacos.home}/data/tps/{pointName}
```

The base directory can be changed with:

```properties
nacos.plugin.control.rule.local.basedir=${expectedDir}
```

When changed, rule files are read from:

```text
${expectedDir}/data/connection/limitRule
${expectedDir}/data/tps/{pointName}
```

Connection rule example:

```json
{"countLimit":100}
```

TPS rule example:

```json
{"pointName":"ConfigQuery","pointRule":{"maxCount":100,"monitorType":"intercept"}}
```

## Connection Behavior

`NacosConnectionControlManager` sums all loaded `ConnectionMetricsCollector`
counts. If `countLimit` is below 0, the connection is allowed. If total current
connections are greater than or equal to `countLimit`, the connection check is
rejected with `DENY_BY_TOTAL_OVER`.

`monitorIpList` belongs to the rule model, but detailed per-IP behavior depends
on the metric collectors and surrounding remote module integration.

## TPS Behavior

`NacosTpsControlManager` registers TPS points and creates one barrier for each
point. It applies a rule during point registration when rule text exists, and it
can apply updated rules through reload events.

The default barrier reports pass and denied counts periodically to the TPS log.
If a point has no registered barrier or if applying TPS fails, the check is
skipped and the request is allowed.
TPS and denied observations are operational metrics and must follow the
[Observability Hooks Spec](../design/foundation-observability-hooks-spec.md).

## Built-In Point Names

The current code registers these point names through `@TpsControl`:

- Config: `ConfigQuery`, `ConfigPublish`, `ConfigRemove`, `ConfigListen`,
  `ConfigFuzzyWatch`, `ClusterConfigChangeNotify`.
- Naming gRPC: `RemoteNamingInstanceRegisterDeregister`,
  `RemoteNamingInstanceBatchRegister`, `RemoteNamingServiceQuery`,
  `RemoteNamingServiceListQuery`, `RemoteNamingServiceSubscribeUnSubscribe`.
- Naming HTTP: `NamingInstanceRegister`, `NamingInstanceDeregister`,
  `NamingInstanceUpdate`, `NamingInstanceMetadataUpdate`,
  `NamingServiceSubscribe`, `NamingInstanceQuery`, `NamingServiceRegister`,
  `NamingServiceDeregister`, `NamingServiceQuery`, `NamingServiceListQuery`,
  `NamingServiceUpdate`.
- Core: `HealthCheck`.

Point names must remain stable once documented, because rule files and external
rule storage use them as keys.

## Rule Reload

Rules can be reloaded by:

- calling `ControlManagerCenter.reloadTpsControlRule(pointName, external)`;
- calling `ControlManagerCenter.reloadConnectionControlRule(external)`;
- publishing `TpsControlRuleChangeEvent` or `ConnectionLimitRuleChangeEvent`.

The `external` flag selects whether the reload should read external storage
when an external rule storage plugin is configured.
The rule change events are local process events and follow the
[Event Dispatch And NotifyCenter Spec](../design/foundation-event-dispatch-spec.md).
