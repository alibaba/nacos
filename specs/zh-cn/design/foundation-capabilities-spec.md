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

# Nacos 基础能力规范

本文定义 Nacos 各领域共用的底层基础能力。基础能力层提供
[集群成员](foundation-cluster-membership-spec.md)、
[服务端生命周期与环境配置](foundation-server-lifecycle-env-spec.md)、
[远程连接生命周期](foundation-remote-connection-spec.md)、
[请求过滤与运行时上下文](foundation-request-context-spec.md)、
[内部 RPC 与集群请求](foundation-internal-rpc-spec.md)、
[AP 一致性](foundation-ap-consistency-spec.md)、
[CP 一致性](foundation-cp-consistency-spec.md)、
[持久化与 dump](foundation-persistence-dump-spec.md)、
[任务执行](foundation-task-execution-spec.md)和
[事件分发](foundation-event-dispatch-spec.md)，以及
[可观测钩子](foundation-observability-hooks-spec.md)等基础设施。它支撑领域语义，但不拥有 Config、Naming、
AI 或安全资源本身的含义。

## 1. 定位

基础能力位于领域规范之下：

```text
设计意图
  -> 资源模型
  -> 基础能力
  -> 领域功能
  -> HTTP / gRPC / SDK 接口
  -> 扩展与安全规则
```

基础能力可以定义状态传输、任务调度、本地事件、member 发现或持久化行为。领域规范负责决定某类
资源使用哪种基础能力，并定义资源生命周期、校验、鉴权和用户可见语义。

## 2. 能力清单

| 能力 | 主要模块 | 职责 | 领域契约 |
| --- | --- | --- | --- |
| [服务端生命周期与环境配置](foundation-server-lifecycle-env-spec.md) | `bootstrap`、`server`、`core.listener`、`sys.env` | 启动进程 context、选择部署模式、注入 environment、加载预置属性、刷新运行时服务端配置，并暴露 module/server state。 | 领域可以读取环境和生命周期状态，但不得通过启动机制定义资源语义。 |
| [集群成员](foundation-cluster-membership-spec.md) | `core.cluster`、寻址插件 | 发现 member、维护 member 状态、发布 member 变化通知，并支持基于 member 的路由或聚合。 | 领域可以依赖当前 member 视图，但必须容忍 member 变化和单机模式。 |
| [远程连接生命周期](foundation-remote-connection-spec.md) | `core.remote`、`common.remote` | 注册 gRPC 连接、维护连接元数据、处理连接关闭/踢除事件，并提供请求上下文。 | 运行时领域可以把状态绑定到连接生命周期，但传输层心跳细节隐藏在连接层之后。 |
| [请求过滤与运行时上下文](foundation-request-context-spec.md) | `core.context`、`core.auth`、`core.paramcheck`、`core.control`、`core.remote` | 填充请求上下文，执行 HTTP/gRPC handler 前置过滤，校验公共参数，并调用鉴权、Control、namespace guard。 | Filter 可以补充或拒绝请求，但领域 handler 拥有资源生命周期和操作语义。 |
| [内部 RPC 与集群请求](foundation-internal-rpc-spec.md) | `core.remote`、领域 request handler | 通过远程层发送服务端间请求、集群通知、校验请求和 ack。 | 内部 RPC 不得重新定义公开 HTTP/gRPC API 语义；领域 handler 拥有 payload 含义。 |
| [AP 一致性](foundation-ap-consistency-spec.md) | `core.distributed.distro`、Config notify path | 提供 Distro 运行时数据同步和 Config Notify 风格的 AP 最终传播能力。 | 领域必须定义 resource type、owner、操作语义、重试、verify、修复和收敛容忍度。 |
| [CP 一致性](foundation-cp-consistency-spec.md) | `core.distributed.raft`、`consistency.cp` | 提供 Raft/JRaft 支持的强顺序写、group、processor、snapshot 和恢复能力。 | 领域必须定义 group 归属、request 类型、snapshot 形态、读写可见性和不可用行为。 |
| [持久化与 dump](foundation-persistence-dump-spec.md) | `persistence`、领域存储模块 | 将持久数据写入内置或外部存储，加载本地 dump，并恢复服务缓存。 | 领域必须定义 schema、兼容性、数据归属，以及 dump 是缓存还是事实来源。 |
| [任务执行](foundation-task-execution-spec.md) | `common.task`、`common.executor`、领域 task engine | 执行立即、延迟、重试、合并、批量和定时任务，并控制资源使用。 | 任务必须具备幂等性，或通过版本、时间戳、状态、CAS 等机制显式保护。 |
| [事件分发](foundation-event-dispatch-spec.md) | `common.notify`、领域事件 publisher | 发布进程内事件，用于更新索引、触发异步任务和桥接 trace 事件。 | 除非领域通过持久化或集群协议传递，否则事件只是本进程事实。 |
| [可观测钩子](foundation-observability-hooks-spec.md) | `core.monitor`、`common.trace`、Trace/Control 插件 | 上报指标、trace、队列深度、连接状态和操作事件。 | 可观测能力不得改变资源语义，也不得成为必须依赖的控制路径。 |

## 3. 服务端生命周期与环境配置

服务端生命周期与环境配置定义 Nacos 进程如何启动、加载运行时配置、暴露 application context、选择
部署类型，并上报 module/server state。

生命周期规则：

- bootstrap 必须在 context 专属 bean 和 module state builder 依赖部署类型之前选择部署类型；
- `EnvUtil` 是服务端环境属性、Nacos home、端口、context path、单机模式、function mode、
  member list 和 processor sizing 的共享门面；
- 启动阶段必须在服务流量前准备工作目录、property source、system property 和自定义环境钩子；
- 运行时服务端配置刷新是本地进程机制，必须为动态配置 subscriber 发布 `ServerConfigChangeEvent`；
- module state 和 server state 是运维视图，不得包含密钥或重新定义领域资源。

详细规则由[服务端生命周期与环境配置规范](foundation-server-lifecycle-env-spec.md)定义。

## 4. 集群成员

集群成员定义可以参与集群路由、聚合和内部协议的 Nacos 服务端 member 集合。

成员规则：

- 单机模式只有一个有效 member，不得暴露虚构的多节点成员关系；
- member 身份必须能被集群模块解析，并足够稳定以用于路由、日志和诊断；
- member lookup 可以是静态、动态或插件提供，但所有 lookup 模式都必须发布等价的 member 变化语义；
- 领域必须处理 member 新增、删除、健康变化和临时不可达状态；
- 跨 member 的大范围诊断聚合属于运维行为，不是领域资源模型。

详细成员规则由[集群成员规范](foundation-cluster-membership-spec.md)定义。服务端 member lookup
扩展由[寻址插件规范](../plugin/addressing-plugin-spec.md)定义。

## 5. 远程连接生命周期

远程层负责 gRPC 建连、连接元数据、请求上下文、推送、ack、连接踢除和断连事件。

连接规则：

- 普通 unary 请求和推送流程依赖连接之前，连接必须完成注册；
- connection id、远端地址、客户端版本、labels、namespace、ability table 等连接元数据属于远程层；
- 领域可以把运行时状态绑定到 connection id，但必须在连接关闭、重连或踢除事件发生时释放或 redo；
- 传输层心跳和假死检测属于远程层；领域应消费连接生命周期事件，而不是重复实现传输层心跳逻辑；
- push queue、阻塞推送和过载连接踢除是保护机制，不得重新定义领域数据归属。

详细连接规则由[远程连接生命周期规范](foundation-remote-connection-spec.md)定义。公开请求包裹由
[gRPC API 规范](../grpc-api/api-spec.md)定义。领域规范可以继续定义自身运行时状态如何响应连接
生命周期事件。

服务端间请求规则、cluster 来源限制、handler 注册、服务端身份和 payload 注册由
[内部 RPC 与集群请求规范](foundation-internal-rpc-spec.md)定义。

## 6. 请求过滤与运行时上下文

请求过滤与运行时上下文定义 HTTP 和 gRPC 请求的 handler 前置层。

过滤规则：

- HTTP 和 gRPC 入口应在可用时把协议、请求目标、身份相关信息、app、user agent 和远端/source
  地址元数据写入 `RequestContext`；
- 请求上下文仅属于运行时，并且在可复用工作线程完成请求处理后必须清理；
- 鉴权、Control、参数检查和 namespace 校验是横切 guard，不是领域资源实现；
- Filter 可以在 handler 执行前拒绝请求，但应使用当前传输协议的标准 response 和错误模型；
- 公共结构校验属于共享 filter 和 extractor，领域特有校验仍属于领域 form、request、service 或
  handler。

HTTP/gRPC filter、`RequestContext`、extractor、namespace 校验、鉴权和 Control 钩子规则由
[请求过滤与运行时上下文规范](foundation-request-context-spec.md)定义。

## 7. 一致性协议

### 7.1 协议选择

领域必须根据资源语义选择一致性行为：

| 资源特征 | 推荐基础能力 |
| --- | --- |
| 运行时、高频、客户端拥有、可丢弃、允许最终收敛的状态。 | Distro 风格 AP 一致性。 |
| 持久、管理面拥有、可通过 snapshot 恢复、需要强顺序的状态。 | Raft/JRaft 风格 CP 一致性。 |
| 数据库存储的持久数据，并通过本地服务缓存加速读取。 | [持久化加 dump](foundation-persistence-dump-spec.md)，以及领域定义的缓存失效规则。 |

协议选择是语义决策。领域不得仅因为实现路径方便就使用 Distro 或 Raft。

### 7.2 AP 一致性

AP 一致性为运行时状态或缓存/listener 可见性提供最终收敛。当前 AP 风格实现包括 Distro 和
Config Notify。共享资源选择和实现规则由[AP 一致性规范](foundation-ap-consistency-spec.md)定义。

### 7.3 CP 一致性

CP 一致性提供强顺序持久状态。当前内置 CP 行为通过 `JRaftProtocol` 使用 Raft/JRaft 实现。
共享 group、processor、读写、snapshot 和恢复规则由[CP 一致性规范](foundation-cp-consistency-spec.md)
定义。

## 8. 持久化、Dump 与本地缓存

持久化将 durable data 写入内置或外部存储。Dump 和本地 cache 提供服务加速或 failover，但它们不
自动成为事实来源。

持久化规则：

- 领域规范拥有逻辑 schema、兼容性和迁移预期；
- datasource dialect 插件可以适配 SQL 和数据库行为，但不得改变逻辑数据含义；
- dump 文件和本地 cache 必须说明自己是可恢复缓存、failover 数据还是内置存储事实来源；
- 持久写成功后，领域应定义本地缓存和 listener 视图何时可见；
- 为兼容保留的冗余 schema 字段应记录为兼容字段或待移除项。

Datasource、repository、嵌入式存储、dump、缓存更新和维护规则由
[持久化与 Dump 规范](foundation-persistence-dump-spec.md)定义。

## 9. 任务执行

Nacos 使用 task engine 和 executor 执行延迟同步、重试、dump、健康检查、推送、指标采集等异步工作。

任务规则：

- 当期望合并或按 key 替换行为时，任务应具备明确 key；
- 可重试任务必须幂等，或通过版本、时间戳、状态、CAS 等机制保护；
- 延迟任务必须定义后续任务是替换、合并还是与已有任务共存；
- 任务队列必须通过上限、control、超时、背压或丢弃策略保护；
- 除非 API 明确等待任务完成，否则用户可见成功应绑定到领域语义，而不是后台任务完成；
- task engine 应暴露队列长度、重试次数、失败次数和执行延迟等指标或诊断信息。

Delayed task、execute task、processor、queue、retry、merge 和领域 executor 规则由
[任务执行规范](foundation-task-execution-spec.md)定义。

## 10. 事件分发与消息总线

`NotifyCenter` 和领域事件 publisher 提供进程内事件总线，用于更新派生索引、调度异步任务、通知
push 组件和桥接 trace 事件。

事件规则：

- 事件是本地状态转换或观测结果的不可变事实；
- 本地事件发布不等同于跨节点复制保证；
- subscriber 不应在关键写路径或连接路径中执行慢速远端 IO；
- 需要隔离的 subscriber 应使用专用 executor；
- 派生索引必须能从权威状态和事件重建；
- trace 插件可以观察事件，但不得拥有主业务决策。

当领域需要跨节点事件可见性时，必须显式通过持久化、AP 一致性、CP 一致性或
[内部集群请求](foundation-internal-rpc-spec.md)传递状态。

`NotifyCenter`、publisher、subscriber、slow event、自定义 publisher 和本地事件语义由
[事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)定义。

## 11. 可观测钩子

可观测钩子为 metrics、trace、审计日志、健康、服务端状态、队列状态、worker 状态和诊断 API 暴露
运行时事实。

可观测规则：

- 指标和日志可能延迟、采样、重置、丢弃或不完整；
- stable metrics 必须使用有边界的名称和低基数 tag；
- 高基数观测应使用 TopN、采样或显式诊断 API；
- trace 和审计 payload 不得包含密钥或完整黑盒 Config content；
- liveness/readiness 和 server state 是运维视图，不得替代领域校验或鉴权；
- 插件提供的可观测能力必须对核心数据变更 fail open，除非治理规范明确规定阻塞策略。

Metric registry、trace、审计、健康、服务端状态、诊断和外部采集规则由
[可观测钩子规范](foundation-observability-hooks-spec.md)定义。

## 12. 基础能力边界规则

- 基础能力不拥有 Config `dataId`、Naming `serviceName`、AI resource name 或 auth permission 含义。
- 领域规范必须选择并约束基础能力行为，而不是继承所有实现细节。
- 除非接口规范显式暴露，内部请求、事件、任务和协议消息都不是公开 API 契约。
- 当兼容行为影响用户可见数据或 API 行为时，应记录在领域规范中。
- 安全、可见性、Trace 和 Control 是横切关注点，即使接入基础能力路径，也必须遵循各自规范。

## 13. 相关规范

- [Nacos 核心功能规范](core-capabilities-spec.md)
- [资源模型规范](resource-model-spec.md)
- [服务端生命周期与环境配置规范](foundation-server-lifecycle-env-spec.md)
- [集群成员规范](foundation-cluster-membership-spec.md)
- [远程连接生命周期规范](foundation-remote-connection-spec.md)
- [请求过滤与运行时上下文规范](foundation-request-context-spec.md)
- [内部 RPC 与集群请求规范](foundation-internal-rpc-spec.md)
- [AP 一致性规范](foundation-ap-consistency-spec.md)
- [CP 一致性规范](foundation-cp-consistency-spec.md)
- [持久化与 Dump 规范](foundation-persistence-dump-spec.md)
- [任务执行规范](foundation-task-execution-spec.md)
- [事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)
- [可观测钩子规范](foundation-observability-hooks-spec.md)
- [gRPC API 规范](../grpc-api/api-spec.md)
- [插件规范](../plugin/plugin-spec.md)
- [寻址插件规范](../plugin/addressing-plugin-spec.md)
- [Trace 插件规范](../plugin/trace-plugin-spec.md)
- [Control 插件规范](../plugin/control-plugin-spec.md)
