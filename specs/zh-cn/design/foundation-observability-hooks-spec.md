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

# Nacos 可观测钩子规范

本文定义 Nacos 各领域共用的基础可观测模型。它是
[基础能力规范](foundation-capabilities-spec.md)中可观测部分的展开。

## 1. 定位

可观测钩子向运维人员、插件、诊断 API、日志和监控系统暴露 Nacos 运行时事实，用于理解服务端健康、
资源活动、请求延迟、队列压力和失败模式。

可观测不是控制路径，也不是领域事实来源。指标、trace event、审计日志、服务端状态和诊断视图不得
重新定义 Config、Naming、AI、安全或插件资源语义。

## 2. 信号类型

Nacos 当前暴露以下可观测信号族：

| 信号 | 主要实现 | 语义 |
| --- | --- | --- |
| 指标 | `NacosMeterRegistryCenter`、各模块 `MetricsMonitor`、Micrometer | counter、gauge、timer、summary、队列长度、连接数和异常数等数值观测。 |
| Trace event | `TraceEvent`、`NotifyCenter`、`NacosCombinedTraceSubscriber`、Trace 插件 | 由领域发出的操作事实，可选投递给插件订阅者。 |
| 审计或 trace 日志 | `ConfigTraceService`、`AiResourceTraceService`、模块操作日志 | 面向资源操作和诊断的结构化或行式记录。 |
| 健康与就绪 | `ModuleHealthCheckerHolder`、liveness/readiness 端点 | 面向负载均衡和编排系统的进程与模块就绪事实。 |
| 服务端状态 | `ModuleStateHolder`、server state API | 模块上报的管理状态摘要。 |
| 运行时诊断 | loader metrics、Config listener metrics、Naming metrics、日志级别 API | 面向 maintainer 的检查和调整接口面。 |
| 外部采集适配 | Spring Boot Actuator/Micrometer registry、Prometheus 模块 | 面向监控和服务发现系统的集成点。 |

## 3. 指标注册模型

`NacosMeterRegistryCenter` 是共享指标注册 facade。它创建命名 `CompositeMeterRegistry`，并在
Micrometer global registry 可用时挂载到对应 registry。

当前 registry 分组包括：

| Registry | 目标范围 |
| --- | --- |
| `CORE_STABLE_REGISTRY` | Core、remote、Raft、connection 和 server executor 指标。 |
| `CONFIG_STABLE_REGISTRY` | Config counter、timer、队列长度、subscriber 数和异常指标。 |
| `NAMING_STABLE_REGISTRY` | Naming service、instance、subscriber、publisher、health check、push 和队列指标。 |
| `TOPN_CONFIG_CHANGE_REGISTRY` | 动态 TopN Config 变更计数。 |
| `TOPN_SERVICE_CHANGE_REGISTRY` | 动态 TopN Naming service 变更计数。 |
| `CONTROL_DENIED_REGISTRY` | Control 插件拒绝指标。 |
| `LOCK_STABLE_REGISTRY` | Lock 模块指标。 |

规则：

- stable registry 应使用低基数 tag 和长期稳定 metric name；
- 动态 TopN registry 可以周期性清空并重建，不得视为稳定时间序列身份；
- metric tag 不得包含密钥、token 或完整配置内容；
- 高基数资源标签应使用 TopN 或有边界的诊断接口，而不是 stable metric tag；
- 指标可以描述队列长度、重试延迟、请求延迟、异常数、连接数和资源数，但不得作为权威数据源；
- 创建高吞吐 task 或 event 路径的模块，应暴露 queue、worker、retry、failure 或 latency 观测。

## 4. 领域指标

Core 指标覆盖 Raft read/apply 行为、gRPC 请求耗时、长连接、按模块连接数和 gRPC server executor
状态。`GrpcServerThreadPoolMonitor` 在 `nacos.metric.grpc.server.executor.enabled=true`
时周期性采样 SDK 和 cluster gRPC executor 状态。

Config 指标覆盖查询、发布、长轮询、notify task、client notify task、dump task、fuzzy search、
配置数量、subscriber 数、读/写/通知/dump 延迟和 Config 相关异常计数。Config 还维护 TopN Config
变更计数。

Naming 指标覆盖 service 数、instance 数、subscriber 数、publisher 数、health check 计数、push
次数、失败 push、空 push、push 耗时、event queue size、pending push task count 和 TopN service
变更计数。

Persistence、Control、Lock 和其他模块可以定义额外指标。模块暴露自有指标时，仍必须遵循本规范的
tag、基数和事实来源规则。

## 5. Trace 与审计

Trace 和审计信号是操作事实。

规则：

- trace payload 应包含资源身份、操作类型、时间戳、结果、可用时的操作者或来源，以及最小诊断扩展字段；
- trace payload 不得包含完整 Config content、密钥、token 或凭据；
- trace event 是不可变观测，不得驱动主要领域决策；
- trace 插件订阅者执行慢 IO 时必须使用专用 executor 隔离；
- trace 插件失败不得回滚或破坏产生 trace 的领域操作；
- 当持久化或合规预期不同，领域应区分审计级日志和尽力而为的诊断 trace event。

Config 当前为 persistence、notify、dump 和 pull 操作写入行式 trace 日志。Naming 通过本地事件
基础设施和 Trace 插件桥接发出 `TraceEvent` 子类。AI 资源操作为 version、review、publish、
label、visibility 和生命周期操作写入 JSON 风格 trace 日志。

Trace 插件接口面由[Trace 插件规范](../plugin/trace-plugin-spec.md)定义。本地 trace event 分发还必须
遵循[事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)。

### 5.1 字段指引

Trace 和审计 payload 应保持一组小而稳定的基础字段：

| 字段类别 | 示例 | 规则 |
| --- | --- | --- |
| 信号身份 | `eventType`、`signalType`、`module`、`domain` | 说明发生了什么，不编码业务 payload。 |
| 资源身份 | `resourceType`、`namespaceId`、`groupName`、`resourceName`、`version` | 优先使用标准资源名称。 |
| 操作上下文 | `action`、`operation`、`phase`、`requestId`、`traceId` | 描述操作及其阶段。 |
| 操作者和来源 | `user`、`sourceIp`、`clientId`、`connectionId`、`member` | 仅在可获得且安全时包含。 |
| 结果 | `success`、`errorCode`、`exceptionClass`、`reason`、`latency` | 区分成功、失败和成本。 |
| 扩展 | `labels`、`metadata`、`ext` | 必须有边界且经过脱敏。 |

指标应使用低基数 label，例如 `module`、`operation`、`protocol`、`result`、`errorCode`、
`exceptionClass`、`registry`、`queue`、`task`、`connectionType` 或 `memberRole`。
稳定指标不得把原始 `dataId`、`serviceName`、`instanceIp`、`clientId`、Config 内容、AI artifact
正文、token 或凭据作为 label。高基数事实应使用 TopN registry、trace/audit 日志或诊断 API 表达。

领域拥有的字段示例：

- Config 可以包含 Config 身份、publish/query/listen/dump/notify 阶段和结果字段，但不得包含
  Config content。
- Naming 可以包含 service 身份、instance 操作原因、push 阶段和 health-check 阶段，但不得包含
  任意 instance metadata payload。
- AI 可以包含 AI resource 身份、版本、状态、review 结果、visibility 结果和 pipeline stage，
  但不得包含 artifact 正文或模型凭据。
- Core 和基础模块可以包含 member 身份、request type、raft group、task name、queue name、
  connection type 和生命周期阶段。

## 6. 健康、就绪与服务端状态

Liveness 表示进程是否运行。Readiness 表示 Nacos 是否应接收普通流量。模块就绪检查通过
`AbstractModuleHealthChecker` 注册，并由 `ModuleHealthCheckerHolder` 聚合。

服务端状态由 `ModuleStateHolder` 和各模块 `ModuleStateBuilder` 构建。它是管理状态视图，不是资源模型。

规则：

- 当部署或 API 规范将 liveness/readiness 标记为健康探针时，它们可以被有意设计为公开端点；
- readiness 失败应以粗粒度说明失败模块；
- module state 字段应适合运维人员查看，不得暴露密钥；
- server state 和 readiness 不得替代领域校验或鉴权检查。

## 7. 诊断 API 与日志

诊断 API 是管理或运维接口面。

示例包括：

- server loader metrics 和 connection reload 操作；
- 按 client IP 或 Config 身份查询 Config 客户端 cache/snapshot metrics；
- Naming metrics、switches、subscriber/client 诊断和日志级别更新；
- 模块日志级别更新；
- Prometheus 模块启用后的服务发现响应；
- memory、performance、Distro、queue、task worker 和响应延迟日志。

规则：

- 诊断 API 必须分类为 Admin API、Console API、internal API，或明确公开的健康探针；
- 大范围指标和诊断不得暴露到运行时 Client SDK；
- 诊断可以跨集群 member 聚合，但聚合是运维行为，必须容忍部分失败或超时；
- 日志级别更新是管理控制，必须要求写权限；
- Prometheus 或外部采集适配器必须记录启用方式、鉴权和 payload 范围。

## 8. 与其他基础能力的关系

可观测钩子通常附着在其他基础路径上：

- server state 和 readiness 上报依赖
  [服务端生命周期与环境配置规范](foundation-server-lifecycle-env-spec.md)；
- 请求指标、鉴权上下文和请求诊断使用
  [请求过滤与运行时上下文规范](foundation-request-context-spec.md)中的字段；
- task engine 按[任务执行规范](foundation-task-execution-spec.md)暴露队列和执行状态；
- event publisher 和 trace bridge 遵循[事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)；
- 内部集群诊断使用[集群成员规范](foundation-cluster-membership-spec.md)的 member 路由，以及
  [内部 RPC 与集群请求规范](foundation-internal-rpc-spec.md)的请求语义；
- connection metrics 和 loader 诊断依赖[远程连接生命周期规范](foundation-remote-connection-spec.md)；
- Control 插件指标和拒绝行为遵循[Control 插件规范](../plugin/control-plugin-spec.md)。

## 9. 边界规则

- 可观测不得改变资源归属、资源身份、持久化语义、一致性行为或鉴权决策。
- 指标和日志可能延迟、采样、重置、丢弃或不完整。
- 可观测成功不表示领域成功，除非所属领域 API 明确定义了该关系。
- 诊断 payload 必须避免密钥和完整黑盒 Config content。
- 高基数指标必须通过 TopN、采样或显式诊断 API 进行边界控制。
- 插件提供的可观测能力必须对核心数据变更 fail open，除非独立治理规范明确规定阻塞策略。

## 10. 相关规范

- [基础能力规范](foundation-capabilities-spec.md)
- [服务端生命周期与环境配置规范](foundation-server-lifecycle-env-spec.md)
- [请求过滤与运行时上下文规范](foundation-request-context-spec.md)
- [任务执行规范](foundation-task-execution-spec.md)
- [事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)
- [集群成员规范](foundation-cluster-membership-spec.md)
- [远程连接生命周期规范](foundation-remote-connection-spec.md)
- [内部 RPC 与集群请求规范](foundation-internal-rpc-spec.md)
- [Trace 插件规范](../plugin/trace-plugin-spec.md)
- [Control 插件规范](../plugin/control-plugin-spec.md)
- [Config 容量与运维规范](../config/config-capacity-ops-spec.md)
- [Naming 运维规范](../naming/naming-ops-spec.md)
