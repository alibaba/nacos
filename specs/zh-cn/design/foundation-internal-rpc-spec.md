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

# Nacos 内部 RPC 与集群请求规范

本文定义 Nacos 服务端节点之间使用的基础层 RPC 模型。它是
[基础能力规范](foundation-capabilities-spec.md)中内部 RPC 部分的展开，并依赖
[集群成员规范](foundation-cluster-membership-spec.md)、
[远程连接生命周期规范](foundation-remote-connection-spec.md)、
[请求过滤与运行时上下文规范](foundation-request-context-spec.md)和
[gRPC API 规范](../grpc-api/api-spec.md)。

## 1. 范围

内部 RPC 是 Nacos Server 节点之间发送请求时使用的传输和 handler 模型。Core、Config、Naming
以及其他领域在需要向其他 member 发送通知、查询、校验或同步状态时使用它。

本文覆盖：

- cluster-source gRPC 连接和调用方行为；
- request handler 注册和调用来源限制；
- 服务端身份、inner API 鉴权和请求 filter；
- payload 注册要求；
- 同步、异步和 fan-out 集群请求规则；
- 共享传输行为与领域 payload 语义之间的边界。

本文不定义 AP 或 CP 一致性算法本身。Distro data ownership、Raft group 行为、Config dump
顺序和 Naming anti-entropy 规则，应由使用该内部 RPC 基础能力的领域规范或一致性规范定义。共享
Config dump 和本地 cache 边界由[持久化与 Dump 规范](foundation-persistence-dump-spec.md)定义。

## 2. 设计模型

Nacos 内部 RPC 使用和 SDK gRPC API 相同的固定 gRPC `Payload` 包裹。业务含义由
`Payload.metadata.type` 选择，body 中承载 JSON 序列化后的 `Request` 或 `Response` 对象。

Cluster gRPC Server 使用 `cluster` 来源标签。`ClusterRpcClientProxy` 创建的服务端间 client
也携带该标签：

```text
RemoteConstants.LABEL_SOURCE = RemoteConstants.LABEL_SOURCE_CLUSTER
```

因此内部 RPC 不是独立的协议族，而是在远程层之上施加来源、鉴权和 handler 约束的一类调用纪律。

模型如下：

```text
member 视图
  -> ClusterRpcClientProxy 按 member 创建 gRPC client
  -> Payload(metadata.type = request 简单类名)
  -> RequestHandlerRegistry
  -> request filters
  -> 领域 RequestHandler
  -> Response payload
```

## 3. 调用方模型

`ClusterRpcClientProxy` 负责服务端间 client。

调用方规则：

- 每个远端 member 对应一个 cluster RPC client，client key 使用稳定的 `Cluster-{member.address}`；
- 每个 cluster client 通过自己的 `ServerListFactory` 固定路由到一个 member address；
- `MembersChangeEvent` 会刷新 cluster client，并销毁已离开 member 对应的 client；
- 请求发送前必须注入服务端身份 header；
- 调用方可以使用同步请求、带 callback 的异步请求，或向 `allMembersWithoutSelf()` 做 fan-out；
- 调用方必须容忍 member 变化、client 缺失、client 停止、超时和过期 member 状态。

领域调用方应根据自身行为需要检查目标 member 是否存在、member 状态、能力和 client 运行状态。
一次 RPC 成功只表示远端 handler 按自身契约接受并处理了该请求；除非领域协议明确说明，否则不表示
全局集群已收敛。

## 4. Handler 注册和来源限制

服务端 handler 继承 `RequestHandler<T extends Request, S extends Response>`。
`RequestHandlerRegistry` 在 Spring context 刷新后扫描 handler bean，并按 request class
简单类名注册 handler。

Handler 规则：

- 一个 payload 类型在同一 server runtime 中应只有一个语义 handler；
- cluster-only handler 必须使用 `@InvokeSource` 声明 `cluster` 来源；
- 当 handler 声明了 `@InvokeSource`，其他来源标签的调用会在进入 handler 之前被拒绝；
- 如果 handler 未声明 `@InvokeSource`，registry 不会强制来源限制，因此新的内部 handler 应显式声明；
- handler 来源限制是传输层保护，不能替代鉴权或权限检查。

Handler 拥有请求语义。远程基础层拥有 dispatch、来源检查、请求上下文、filter 和响应转换。

## 5. 安全与请求 Filter

内部 RPC 必须使用和其他受保护 gRPC 请求一致的安全模型。

规则：

- 内部集群 API 应在 `handle(...)` 上添加 `@Secured(apiType = ApiType.INNER_API)`；
- 当鉴权插件需要类型化资源时，领域内部 API 应设置 `signType` 或 resource 信息；
- `ClusterRpcClientProxy` 必须注入配置的服务端身份 header；
- 当 inner API auth 开启时，`RemoteRequestAuthFilter` 会校验服务端身份；
- 公开 API 鉴权关闭，不代表可以跳过必要的 inner server identity 校验；
- 领域需要 namespace 校验、参数提取、TPS control 等能力时，应在 handler 上声明对应 filter。

已知请求 filter 关注点包括：

| Filter 关注点 | 典型注解或路径 | 规则 |
| --- | --- | --- |
| 服务端身份与鉴权 | `@Secured`, `RemoteRequestAuthFilter` | 保护 inner API，并解析鉴权上下文。 |
| 参数校验 | `@ExtractorManager.Extractor` | 复用共享参数提取和校验。 |
| Namespace 校验 | `@NamespaceValidation` | 当资源身份包含 namespace 时校验 namespace 存在性。 |
| TPS Control | `@TpsControl` | 为集群流量注册并执行稳定 control 点。 |

## 6. Payload 注册

只有 request handler bean 不足以形成有效的 gRPC payload 契约。请求和响应 payload 类还必须注册，
远程层才能解析 `Payload.metadata.type`。

Payload 规则：

- request 和 response 类必须继承 Nacos remote `Request` 与 `Response` 模型；
- payload 类必须保持 JSON 兼容；
- payload 类必须先在对应 payload registry 或
  `META-INF/services/com.alibaba.nacos.api.remote.Payload` 文件中注册，才能被视为已生效的
  gRPC 契约；
- 新 request 类必须通过 `Request#getModule()` 返回稳定 module；
- 注册 payload 不表示它是公开 API，除非接口规范显式暴露。

如果代码中存在 handler，但 payload 类型未注册，应将该 handler 视为未激活或未完成的 gRPC 契约。

## 7. 当前集群请求分类

| 分类 | Request 类型 | 归属 | 契约 |
| --- | --- | --- | --- |
| Member report | `MemberReportRequest`, `MemberReportResponse` | Core cluster | 向对端上报 member 元数据，将本地 peer 视图标记为 `UP`，重置失败状态，并返回接收方 self member。详细成员规则由[集群成员规范](foundation-cluster-membership-spec.md)定义。 |
| Server remote context | `ServerReloadRequest`, `ServerReloadResponse`, `ServerLoaderInfoRequest`, `ServerLoaderInfoResponse` | Core remote | 在其他节点重新加载 remote protocol context，或查询对端 connection 和 load 指标。 |
| Config change sync | `ConfigChangeClusterSyncRequest`, `ConfigChangeClusterSyncResponse` | Config | 通知 peer 某个 Config 发生变化，使其刷新 dump 和 listener 可见状态。Config Notify 语义由[AP 一致性规范](foundation-ap-consistency-spec.md)定义，Config 资源语义仍由 Config 规范定义。 |
| Naming Distro transport | `DistroDataRequest`, `DistroDataResponse` | Naming 与 Distro | 在节点间承载 Distro verify、snapshot、sync、delete 和 query 操作。Distro ownership 和收敛规则由[AP 一致性规范](foundation-ap-consistency-spec.md)以及 Naming 规范定义。 |
| Plugin availability | `PluginAvailabilityRequest`, `PluginAvailabilityResponse` | Core plugin | 查询节点上的插件可用性。当前代码已有 handler，但未注册的 payload 不应被视为已生效的 gRPC 契约。 |

领域规范可以增加更多分类，但必须保持本文定义的调用方、handler、鉴权、来源和 payload 规则。

## 8. 请求结果与重试语义

Cluster RPC 调用方必须定义自己的重试或补偿行为。

规则：

- 定向调用应明确目标 member，并处理目标 client 缺失或停止；
- fan-out 调用应使用当前 member 视图，并容忍部分成功；
- 异步调用必须定义 callback 行为、超时处理和重试调度；
- 超时或传输失败不能证明远端领域操作一定没有发生；
- response success 表示单个目标节点上的 handler 级成功，不表示全局收敛；
- 后台重试任务必须按[任务执行规范](foundation-task-execution-spec.md)保持幂等，或由时间戳、版本、
  操作类型或领域状态保护。

例如 Config change sync 可以通过 async notify task 路径重试；Naming Distro verify 失败可以产生
领域事件并调度修复。本地事件行为由
[事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)定义。这些重试规则属于 Config
和 Naming 一致性规范。

## 9. 边界规则

- 除非接口规范显式声明，内部 RPC 不是公开 HTTP、SDK 或 open gRPC API。
- 来源标签、连接存在性或 member 关系本身不是鉴权。Inner request 必须使用服务端身份和 auth filter。
- `ClusterRpcClientProxy` 必须保持传输职责，不应拥有 Config、Naming、AI 或插件 payload 语义。
- 领域 payload 不应依赖无限制 request body、阻塞 callback，或在关键 remote 线程上执行慢速远端 IO。
- 当内部 payload 的兼容字段影响滚动升级行为时，应在领域层记录。
- 新增 cluster-only handler 时，应更新 gRPC API 清单，并链接到拥有语义契约的领域规范。

## 10. 相关规范

- [基础能力规范](foundation-capabilities-spec.md)
- [集群成员规范](foundation-cluster-membership-spec.md)
- [远程连接生命周期规范](foundation-remote-connection-spec.md)
- [请求过滤与运行时上下文规范](foundation-request-context-spec.md)
- [AP 一致性规范](foundation-ap-consistency-spec.md)
- [CP 一致性规范](foundation-cp-consistency-spec.md)
- [持久化与 Dump 规范](foundation-persistence-dump-spec.md)
- [任务执行规范](foundation-task-execution-spec.md)
- [事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)
- [gRPC API 规范](../grpc-api/api-spec.md)
- [鉴权与权限规范](../auth/auth-permission-spec.md)
- [Config 规范](../config/config-spec.md)
- [Naming 一致性与客户端状态规范](../naming/naming-consistency-client-spec.md)
- [Control 插件规范](../plugin/control-plugin-spec.md)
