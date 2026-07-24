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

# Control 插件规范

## 范围

Control 插件为 Nacos 服务端节点提供运行时流量和连接控制。它覆盖连接准入、TPS 检查、规则
解析、规则存储和可选指标采集。

这是配置选择的单服务插件。配置的 control type 会选择一个 `ControlManagerBuilder`。
稳定 adapter 会把选中的 builder 接入统一插件配置生命周期，并且只在 effective config
完成 apply 后创建 manager bundle。如果未配置类型，或选中的插件无法加载，Nacos 使用
无上限的默认 manager。通用生命周期和状态规则由 [Nacos 插件化规范](plugin-spec.md) 定义，
内置实现由
[默认 Control 插件实现规范](default-control-plugin-spec.md) 定义。

Control 是 Nacos 的反脆弱机制。它在某个控制点访问量超过规则时，对连接或请求进行监控或
拒绝，用于保护当前服务端节点。Control 插件不得改变资源语义；它只判断当前连接或请求能否
继续执行。

HTTP 和 gRPC TPS control 钩子通过
[请求过滤与运行时上下文规范](../design/foundation-request-context-spec.md)定义的共享请求过滤模型接入。

## 概念

| 概念 | 含义 |
|------|------|
| Control point | 可被度量和限制的命名运行时资源。 |
| Connection control | 针对长连接或长轮询连接的准入控制。 |
| TPS control | 针对命名 API 操作点的请求频率准入控制。 |
| Rule storage | 持久化本地或外部分发规则文本的存储。 |
| Rule parser | 将规则文本解析成运行时规则对象的解析器。 |
| Barrier | 某个 TPS point 的运行时计数与决策组件。 |

连接控制和 TPS 控制彼此独立。部署可以同时提供两个 manager，也可以只提供其中一个，
缺失的维度按无上限处理。

## SPI

Control 插件实现 `ControlManagerBuilder`。builder 继承 `PluginConfigDefinitionSpec`，
在 manager 构建前声明配置元数据，但不持有 effective config。

| 方法 | 要求 |
|------|------|
| `getName()` | 稳定插件名称。 |
| `buildConnectionControlManager()` | 构造连接控制 manager。 |
| `buildTpsControlManager()` | 构造 TPS 控制 manager。 |
| `buildConnectionControlManager(config)` | 使用 canonical effective plugin config 构造；兼容默认实现委托无参数方法。 |
| `buildTpsControlManager(config)` | 使用 canonical effective plugin config 构造；兼容默认实现委托无参数方法。 |

在 Control 定义受控的 manager 替换和 close 生命周期前，所有 builder definition 的
effect mode 必须为 `RESTART`。统一插件配置 API 会拒绝这些字段的 runtime 或 local-only
更新。

Control provider 会为每个 builder 创建一个稳定 `PluginConfigSpec` adapter。adapter 委托
builder 提供 definitions，持有不可变的 effective config 快照，并实现
`PluginStartupLifecycle`。Control registry 只执行一次 builder SPI 发现；provider、插件
管理器和 manager center 不得分别重复加载。

外部规则存储插件实现 `ExternalRuleStorageBuilder`，并通过 control 配置独立选择。

该插件以 `control` 类型暴露给核心插件管理器。

## 启动生命周期

Control 类型使用以下启动顺序：

1. 快照静态实现选择；
2. 只发现一次 builder，并注册稳定 adapter；
3. 恢复统一实现 state；
4. 为可配置 adapter 解析并 apply effective config；
5. 只为选中且 enabled 的 adapter 调用 `initialize()`；
6. 使用已接受的配置快照构建 Connection 和 TPS manager；
7. 在 Nacos 报告启动成功前，把两个最终结果作为一个 manager bundle 安装。

未选中的 adapter 仍可在插件 inventory 中展示，但不得构建 manager 或启动后台资源。零配置
旧 builder 仍会使用空配置快照执行启动 lifecycle。

`ControlManagerCenter` 对外暴露稳定的 Connection 和 TPS facade。调用方可以持有 facade
引用；安装启动 bundle 时，通过同一个 bundle 引用同时切换两个 facade 的 delegate。安装前
注册的 TPS point 必须重放给选中的 TPS manager。manager center 不得再次通过 SPI 加载
`ControlManagerBuilder`。安装前 facade 直接提供轻量 no-limit 行为，不得提前创建规则加载器、
指标上报任务或 TPS barrier。

## Manager

`ConnectionControlManager` 拥有连接规则，并为连接准入返回 `ConnectionCheckResponse`。
它可以加载 `ConnectionMetricsCollector` 实现来上报连接指标。

连接 manager 必须满足：

| 方法 | 要求 |
|------|------|
| `applyConnectionLimitRule(rule)` | 应用最新连接规则。 |
| `check(request)` | 返回连接准入的通过或拒绝结果。 |
| `buildConnectionControlRuleParser()` | 可以覆盖规则文本解析器。 |

`TpsControlManager` 拥有 TPS point、TPS 规则和 barrier，并为 TPS 准入返回
`TpsCheckResponse`。

TPS manager 必须满足：

| 方法 | 要求 |
|------|------|
| `registerTpsPoint(pointName)` | 在启动或路由扫描时注册控制点。 |
| `applyTpsRule(pointName, rule)` | 应用或移除某个 point 的规则。 |
| `check(request)` | 返回 TPS 请求的通过或拒绝结果。 |
| `buildTpsControlRuleParser()` | 可以覆盖规则文本解析器。 |
| `buildTpsBarrierCreator()` | 可以覆盖时间窗口和计数行为。 |

## 规则模型

`ConnectionControlRule` 包含：

| 字段 | 含义 |
|------|------|
| `countLimit` | 最大总连接数。小于 0 表示不限制。 |
| `monitorIpList` | 需要详细记录连接行为的 IP 列表。 |

`TpsControlRule` 包含：

| 字段 | 含义 |
|------|------|
| `pointName` | 控制点名称。 |
| `pointRule` | 控制点规则详情。 |

`RuleDetail` 包含：

| 字段 | 含义 |
|------|------|
| `ruleName` | 规则标识。自定义插件可以让一个 point 拥有多个规则名。 |
| `maxCount` | 周期内最大允许次数。小于 0 表示不限制。 |
| `period` | 计数周期，内置默认值为秒。 |
| `monitorType` | `monitor` 表示只观测，`intercept` 表示拒绝。 |

## 规则存储

规则可以来自本地磁盘存储，也可以来自外部规则存储插件。本地规则始终是安全基线。只有当
选中的 control 插件明确要求时，外部规则存储失败才应导致 fail closed。

规则重载通过 control 规则变更事件发布，并由当前活跃 manager 应用。本地事件分发遵循
[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)。
Control 指标和拒绝观测遵循
[可观测钩子规范](../design/foundation-observability-hooks-spec.md)。

外部规则存储通过以下配置选择：

```properties
nacos.plugin.control.rule.external.storage=${controlPluginName}
```

本地规则存储基准目录通过以下配置选择：

```properties
nacos.plugin.control.rule.local.basedir=${expectedDir}
```

自定义 control 插件可以通过覆盖规则解析器支持非 JSON 规则文本。自定义 TPS 插件可以通过
覆盖 barrier creator 支持滑动窗口等其他计数算法。

## 选择与状态

选中的 manager 实现由标准 key 指定：

```properties
nacos.plugin.control.type=${controlPluginName}
```

历史 key 保留为静态配置兼容 alias：

```properties
nacos.plugin.control.manager.type=${controlPluginName}
```

两者同时存在时标准 key 优先；读取历史 key 时输出迁移 WARN。选择按 `RESTART` 生效。启动时
选中的 adapter 为 enabled，其他已发现 adapter 为 disabled，插件 status API 拒绝运行时
切换选择。

Point name 属于公开 control 契约。新增 `@TpsControl` point 时，必须使用稳定名称，
记录被保护的操作，并在 HTTP 与 gRPC 端点表达同一个语义操作时复用该名称。

## 降级

Control 插件会影响请求准入。为保持兼容，Connection 和 TPS 的构建继续相互独立：某一维度
构建失败或返回 null 时，该维度回退到无上限 manager 并记录日志。两个最终结果仍作为一个
bundle 同时安装，调用方不能观察到只替换一个维度的启动中间态。选中的 builder 不存在时，
两个维度都保持无上限。

运行时插件异常不得破坏请求状态。对于只观测规则，失败应记录并跳过。对于拦截规则，失败后
通过、拒绝或 fail fast 由被选中的插件决定，并必须在实现规范中记录。
