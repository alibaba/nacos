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

# 默认 Control 插件实现规范

## 范围

默认 Control 实现是 `plugin-default-impl/nacos-default-control-plugin` 中的 `nacos`
插件。它为单个 Nacos 服务端节点提供简单的本地连接数限制和 TPS 限制，是
[Control 插件规范](control-plugin-spec.md)的内置实现。

该实现是节点本地的，不自行提供集群级配额协调。集群级规则分发需要外部规则存储插件，
或由运维系统在该实现之外完成同步。

## 启用方式

通过以下配置启用：

```properties
nacos.plugin.control.manager.type=nacos
```

如果该配置不存在，control manager center 使用无上限 manager。如果 `nacos` builder
创建任一 manager 失败，该 manager 会回退到无上限实现并记录日志。

## 本地规则文件

默认实现从以下位置读取本地 JSON 规则文件：

```text
${nacos.home}/data/connection/limitRule
${nacos.home}/data/tps/{pointName}
```

基准目录可以通过以下配置修改：

```properties
nacos.plugin.control.rule.local.basedir=${expectedDir}
```

修改后，规则文件从以下位置读取：

```text
${expectedDir}/data/connection/limitRule
${expectedDir}/data/tps/{pointName}
```

连接规则示例：

```json
{"countLimit":100}
```

TPS 规则示例：

```json
{"pointName":"ConfigQuery","pointRule":{"maxCount":100,"monitorType":"intercept"}}
```

## 连接行为

`NacosConnectionControlManager` 会汇总所有已加载 `ConnectionMetricsCollector` 的计数。
如果 `countLimit` 小于 0，连接被允许。如果当前总连接数大于或等于 `countLimit`，
连接检查会以 `DENY_BY_TOTAL_OVER` 拒绝。

`monitorIpList` 属于规则模型，但具体 IP 维度行为取决于指标采集器和 remote 模块集成。

## TPS 行为

`NacosTpsControlManager` 注册 TPS point，并为每个 point 创建一个 barrier。注册 point
时如果存在规则文本，会应用该规则；后续也可以通过规则变更事件应用更新后的规则。

默认 barrier 会定期把通过与拒绝计数输出到 TPS 日志。如果某个 point 没有注册 barrier，
或 TPS 应用过程失败，检查会跳过并允许请求继续。
TPS 和拒绝观测属于运维指标，必须遵循
[可观测钩子规范](../design/foundation-observability-hooks-spec.md)。

## 内置 Point Name

当前代码通过 `@TpsControl` 注册以下 point name：

- Config: `ConfigQuery`, `ConfigPublish`, `ConfigRemove`, `ConfigListen`,
  `ConfigFuzzyWatch`, `ClusterConfigChangeNotify`。
- Naming gRPC: `RemoteNamingInstanceRegisterDeregister`,
  `RemoteNamingInstanceBatchRegister`, `RemoteNamingServiceQuery`,
  `RemoteNamingServiceListQuery`, `RemoteNamingServiceSubscribeUnSubscribe`。
- Naming HTTP: `NamingInstanceRegister`, `NamingInstanceDeregister`,
  `NamingInstanceUpdate`, `NamingInstanceMetadataUpdate`,
  `NamingServiceSubscribe`, `NamingInstanceQuery`, `NamingServiceRegister`,
  `NamingServiceDeregister`, `NamingServiceQuery`, `NamingServiceListQuery`,
  `NamingServiceUpdate`。
- Core: `HealthCheck`。

Point name 一旦文档化就必须保持稳定，因为规则文件和外部规则存储都会把它作为 key。

## 规则重载

规则可以通过以下方式重载：

- 调用 `ControlManagerCenter.reloadTpsControlRule(pointName, external)`；
- 调用 `ControlManagerCenter.reloadConnectionControlRule(external)`；
- 发布 `TpsControlRuleChangeEvent` 或 `ConnectionLimitRuleChangeEvent`。

当配置了外部规则存储插件时，`external` 标记决定是否从外部存储读取规则。
规则变更事件是进程内本地事件，并遵循
[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)。
