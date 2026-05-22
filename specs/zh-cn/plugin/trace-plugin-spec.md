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

# Trace 插件规范

## 范围

Trace 插件用于将 Nacos 领域操作事件发布给订阅者。它面向 Nacos 操作追踪、审计和诊断，不是
应用服务之间的分布式链路追踪。

这是订阅或广播插件。多个订阅者可以观察同一个事件。Trace 插件不得拥有主业务决策权。
通用插件生命周期和状态规则由 [Nacos 插件化规范](plugin-spec.md) 定义。
Trace 事件分发运行在 Nacos 本地事件基础设施之上，并必须遵循
[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)。
共享 trace、审计、指标和诊断边界由
[可观测钩子规范](../design/foundation-observability-hooks-spec.md)定义。

与通用分布式链路追踪不同，Nacos Trace 事件描述的是 Nacos 资源操作，例如实例注册、
服务删除、服务推送和健康状态变化。它不是应用服务之间调用链的 span。

## 概念

| 概念 | 含义 |
|------|------|
| `TraceEvent` | 基础不可变事件，包含 type、event time、namespace、group 和 name。 |
| 领域 Trace 事件 | 增加领域专有字段的子类。 |
| Combined subscriber | 核心桥接器，将领域事件分发给感兴趣的插件订阅者。 |
| Subscriber executor | 可选 executor，用于隔离插件 IO 或慢回调。 |

## SPI

插件实现 `NacosTraceSubscriber`。

| 方法 | 要求 |
|------|------|
| `getName()` | 稳定订阅者名称，重复名称后加载者会替换先加载者。 |
| `subscribeTypes()` | 该订阅者希望接收的 Trace 事件类。 |
| `onEvent(event)` | 订阅者回调。 |
| `executor()` | 可选的异步回调执行器。 |

该插件以 `trace` 类型暴露给核心插件管理器。

## 事件规则

Trace 事件携带事件类型、事件时间、命名空间、分组和资源名等
[Nacos 资源信息](../design/resource-model-spec.md)。领域事件可以增加额外字段。
通用字段名、脱敏规则和 metric label 边界由
[可观测钩子规范](../design/foundation-observability-hooks-spec.md)定义。本文档不要求每个领域
发出完全相同的业务字段。

订阅者必须把事件视为不可变事实。除非所属领域明确记录该副作用，否则订阅者不得在 Trace
回调中修改 Nacos 资源。

当前 Naming Trace 事件类型包括：

| 事件类 | Event type | 含义 |
|--------|------------|------|
| `RegisterInstanceTraceEvent` | `REGISTER_INSTANCE_TRACE_EVENT` | 实例注册。 |
| `BatchRegisterInstanceTraceEvent` | `BATCH_REGISTER_INSTANCE_TRACE_EVENT` | 批量实例注册。 |
| `DeregisterInstanceTraceEvent` | `DEREGISTER_INSTANCE_TRACE_EVENT` | 实例注销。 |
| `RegisterServiceTraceEvent` | `REGISTER_SERVICE_TRACE_EVENT` | 空服务创建。 |
| `DeregisterServiceTraceEvent` | `DEREGISTER_SERVICE_TRACE_EVENT` | 空服务删除。 |
| `UpdateInstanceTraceEvent` | `UPDATE_INSTANCE_TRACE_EVENT` | 实例元数据或状态更新。 |
| `UpdateServiceTraceEvent` | `UPDATE_SERVICE_TRACE_EVENT` | 服务元数据更新。 |
| `SubscribeServiceTraceEvent` | `SUBSCRIBE_SERVICE_TRACE_EVENT` | 服务订阅。 |
| `UnsubscribeServiceTraceEvent` | `UNSUBSCRIBE_SERVICE_TRACE_EVENT` | 服务取消订阅。 |
| `PushServiceTraceEvent` | `PUSH_SERVICE_TRACE_EVENT` | 向订阅者推送服务。 |
| `HealthStateChangeTraceEvent` | `HEALTH_STATE_CHANGE_TRACE_EVENT` | 实例健康状态变化。 |

`DeregisterInstanceTraceEvent` 携带注销原因。当前原因包括 `REQUEST`、
`NATIVE_DISCONNECTED`、`SYNCED_DISCONNECTED` 和 `HEARTBEAT_EXPIRE`。

当前 AI 资源 Trace 事件类型包括：

| 事件类 | Event type | 含义 |
|--------|------------|------|
| `AiResourceTraceEvent` | `AI_RESOURCE_TRACE_EVENT` | AI 资源生命周期操作，例如创建草稿、评审、发布、上线/下线、删除、标签更新、scope 更新，或兼容审计日志的默认文件输出。 |

`AiResourceTraceEvent` 携带操作者、资源类型、资源标识、可选版本、操作、状态、
客户端 IP 和可选扩展文本。

## 执行

`NacosCombinedTraceSubscriber` 注册领域事件 publisher，并只把匹配的事件类分发给插件
订阅者。如果 `executor()` 返回 `null`，回调会在事件分发路径中执行。写远端系统、文件、
数据库或其他慢 sink 的插件应返回专用 executor。

Trace publisher 允许在队列压力下丢弃 trace event 进行降级，具体边界遵循本地事件降级规则。

Trace 订阅者通过 SPI 加载。同一类型内重复名称不适合生产稳定使用，插件包应使用唯一名称。

## 降级

Trace 插件属于观测扩展。插件失败不得破坏 Nacos 核心数据变更或请求处理。执行阻塞 IO 的
插件应返回专用 executor。当 Trace 队列过载时，可以丢弃事件以保护服务端稳定性。

当前核心桥接器会捕获订阅者回调异常。插件实现仍必须记录足够信息，帮助运维人员诊断自己的
sink 故障。

## 实现说明

Nacos 服务端仓库定义 Trace SPI 和事件模型。参考订阅者实现可以位于外部插件仓库，并应遵守
本文档。

为了兼容已有 AI 资源审计日志，Nacos 在 `plugin-default-impl` 中提供默认的
`AiResourceTraceEvent` 文件日志订阅者，并随默认插件包发布。它是标准 Trace 订阅者，
并将既有 JSON 行格式写入 `ai-resource-trace.log`。
