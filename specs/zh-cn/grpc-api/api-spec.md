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

# Nacos gRPC API 规范

本文档定义 Nacos gRPC API 模型，用于 Java SDK、Nacos Server 节点和各模块
远程处理器。与 HTTP API 不同，Nacos gRPC 只有很小的固定 proto 传输面，
业务 API 由 Java payload 类型表达。Payload 身份必须与
[资源模型规范](../design/resource-model-spec.md)对齐，公开客户端行为必须与
[SDK 规范](../sdk/sdk-spec.md)对齐。
服务端请求过滤和运行时上下文模型由
[请求过滤与运行时上下文规范](../design/foundation-request-context-spec.md)定义。

## 1. 设计模型

Nacos gRPC 是 SDK 运行时流量和服务端集群流量的主要远程协议。proto 文件
只定义传输级方法：

| Service | Method | 形态 | 用途 |
| --- | --- | --- | --- |
| `Request` | `request(Payload) returns (Payload)` | unary | 客户端或对端发送一次请求并接收一次响应。 |
| `BiRequestStream` | `requestBiStream(stream Payload) returns (stream Payload)` | 双向流 | 建连、服务端推送和 ack 响应。 |

业务操作由 `Payload.metadata.type` 选择，而不是由独立 proto RPC 方法选择。
该值是已注册 `Request` 或 `Response` payload 类型的 Java 简单类名。

### 1.1 为什么 Payload 使用 JSON 对象

Nacos 没有把每个 gRPC 业务请求和响应都建模成独立 protobuf message，而是
使用固定 protobuf `Payload` 包装 JSON 序列化后的 Java 语义对象。

这是一个有意的设计选择：

- Nacos HTTP API 和 gRPC API 可以复用同一套语义对象定义和校验模型，而不是
  维护两套独立 DTO。
- 最初进行 RPC 选型时，gRPC 曾和其他 RPC 协议及 connector 形态进行过对比，
  包括类似 RSocket 的方案。远程层设计上期望 connector 可以切换或兼容，而
  不需要改动所有业务请求对象。后来因为生态活跃度、多语言支持度和运维成熟度
  等原因，gRPC 成为实际选择，但 connector 抽象被保留了下来。

社区知道这个选择存在代价。protobuf 内再承载 JSON 会带来额外的序列化和反
序列化 CPU 开销，也无法完全利用 protobuf 更安全的 schema 模型和原生多语言
对象生成能力。这是兼容性和抽象能力取舍的一部分。

## 2. 传输契约

`Payload` 包含：

| 字段 | 含义 |
| --- | --- |
| `metadata.type` | `PayloadRegistry` 使用的 Java 简单类名。 |
| `metadata.clientIp` | 发送方设置的客户端 IP。服务端请求上下文以连接元数据作为可信来源。 |
| `metadata.headers` | 逻辑请求头，复制到 `Request.headers`。 |
| `body.value` | Java 请求或响应对象的 JSON 字节。 |

Payload 类必须通过
`META-INF/services/com.alibaba.nacos.api.remote.Payload` 注册，否则接收方无法
解析 `metadata.type`。

规则：

- 不为每个业务操作新增一个 proto service method。
- `metadata.type` 不使用 Java 包名，只使用简单类名。
- Request 和 Response 类必须保持 JSON 可序列化。
- 请求头通过 `metadata.headers` 传输，不放在 JSON body 中。
- 新增 Request 类必须通过 `Request#getModule()` 定义模块。

## 3. 端口和调用来源

Nacos 默认启动两个 gRPC Server：

| Server | 默认端口 | 来源标签 | 调用方 |
| --- | --- | --- | --- |
| SDK gRPC server | `${server.port} + 1000` | `sdk` | SDK 客户端 |
| Cluster gRPC server | `${server.port} + 1001` | `cluster` | Nacos Server 节点 |

Java 客户端可通过 `nacos.server.grpc.port.offset` 覆盖端口偏移。服务端 SDK
和 cluster gRPC Server 使用各自的默认偏移。

部分处理器带有 `@InvokeSource` 注解。存在该注解时，只有列出的来源标签可以
调用对应 payload 类型。

服务端间来源规则、handler 注册、服务端身份和集群请求重试边界由
[内部 RPC 与集群请求规范](../design/foundation-internal-rpc-spec.md)定义。

## 4. 连接生命周期

服务端连接生命周期细节由
[远程连接生命周期规范](../design/foundation-remote-connection-spec.md)定义。客户端侧 server
selection、reconnect、TLS 和能力协商行为由[客户端运行时规范](../client/README.md)定义，
尤其是[客户端连接与故障切换规范](../client/client-connection-failover-spec.md)和
[客户端能力协商规范](../client/client-ability-negotiation-spec.md)。本节仅总结公开 gRPC 流程。
请求上下文初始化、request filter、鉴权/Control 钩子和参数提取由
[请求过滤与运行时上下文规范](../design/foundation-request-context-spec.md)定义。

1. 客户端通过 unary `ServerCheckRequest` 校验选中服务端可用性，并获取 connection id。
   服务端返回 `ServerCheckResponse`。
2. 客户端打开 `BiRequestStream.requestBiStream`。
3. 流上的第一个 payload 应为 `ConnectionSetupRequest`。
4. 服务端根据 connection id、远端地址、客户端版本、命名空间、labels 和
   ability table 创建并注册 `Connection`。
5. 如果客户端发送 ability table，服务端返回包含服务端能力的
   `SetupAckRequest`。
6. unary 业务请求只有在连接已注册后才会被接受。
7. 服务端推送请求通过双向流发送，客户端以 `NotifySubscriberResponse`、
   `ConfigChangeNotifyResponse` 或与 `PushAckRequest` 相关的响应返回 ack。

当服务端仍在启动、连接未注册、请求类型未知、解析失败或处理器抛出异常时，
服务端返回 `ErrorResponse`。

## 5. 响应和错误契约

所有 gRPC 响应都继承 `com.alibaba.nacos.api.remote.response.Response`：

| 字段 | 含义 |
| --- | --- |
| `resultCode` | 成功为 `200`，失败为 `500` 或具体错误码。 |
| `errorCode` | 失败时的 Nacos 错误码。 |
| `message` | 错误或诊断信息。 |
| `requestId` | 可用时的请求关联 id。 |

只有 `resultCode == 200` 时，`Response#isSuccess()` 才为 true。

`ErrorResponse` 用于传输层和处理器错误。对于 `NacosException` 和
`NacosRuntimeException`，`errorCode` 来自异常；其他异常回退为 `500`。

## 6. 鉴权

gRPC 鉴权由 `RemoteRequestAuthFilter` 执行。共享身份、资源和动作语义由
[鉴权与权限规范](../auth/auth-permission-spec.md)定义。
filter 执行契约由[请求过滤与运行时上下文规范](../design/foundation-request-context-spec.md)定义。

需要身份、权限或服务端身份校验的处理器，应在 `handle(...)` 上添加
`@Secured`。该 filter 会：

- 将 `@Secured.apiType()` 写入请求上下文；
- 当非 inner API 且鉴权关闭时跳过鉴权；
- 当 inner auth 开启时始终校验 inner API 服务端身份；
- 从请求头和 payload 中解析身份和资源；
- 校验身份和 action 权限。

集群内部 API 应使用 `ApiType.INNER_API`，并通过
`@InvokeSource(source = {RemoteConstants.LABEL_SOURCE_CLUSTER})` 限制调用来源。服务端间
inner 请求的详细规则由
[内部 RPC 与集群请求规范](../design/foundation-internal-rpc-spec.md)定义。

## 7. Payload 清单

### 7.1 通用和 Core

| Request type | Response type | 方向 | 鉴权/来源 | 契约 |
| --- | --- | --- | --- | --- |
| `ConnectionSetupRequest` | `SetupAckRequest` | stream | 建连 | 使用客户端版本、命名空间、labels 和 ability table 注册 gRPC 连接。 |
| `ServerCheckRequest` | `ServerCheckResponse` | unary | 无 | stream setup 前校验选中服务端，并返回 connection id 和能力协商支持情况。 |
| `HealthCheckRequest` | `HealthCheckResponse` | unary | 无 | keep-alive 健康检查。 |
| `ClientDetectionRequest` | `ClientDetectionResponse` | stream push | 无 | 服务端发起客户端探测。 |
| `ConnectResetRequest` | `ConnectResetResponse` | stream push | 无 | 要求客户端重连到目标服务端。 |
| `ServerReloadRequest` | `ServerReloadResponse` | unary | inner，cluster 来源 | 在对端重新加载远程上下文。参见[内部 RPC 与集群请求规范](../design/foundation-internal-rpc-spec.md)。 |
| `ServerLoaderInfoRequest` | `ServerLoaderInfoResponse` | unary | inner，cluster 来源 | 查询对端 server loader 指标。参见[内部 RPC 与集群请求规范](../design/foundation-internal-rpc-spec.md)。 |
| `MemberReportRequest` | `MemberReportResponse` | unary | inner，cluster 来源 | 按[集群成员规范](../design/foundation-cluster-membership-spec.md)上报成员信息并更新成员状态。 |
| `PluginAvailabilityRequest` | `PluginAvailabilityResponse` | unary | 已有 handler | 查询节点上的插件可用性。当前代码已有 handler，但 payload 未列入 core payload SPI 文件；注册前不应视为已生效的 gRPC 契约。 |

### 7.2 Config

| Request type | Response type | 动作 | 主要字段 | 契约 |
| --- | --- | --- | --- | --- |
| `ConfigQueryRequest` | `ConfigQueryResponse` | read | `dataId`, `group`, `tenant`, `tag` | 查询配置内容、md5、类型、加密 key、beta/tag 元数据。 |
| `ConfigPublishRequest` | `ConfigPublishResponse` | write | `dataId`, `group`, `tenant`, `content`, `casMd5`, `additionMap` | 发布配置或 CAS 发布配置。 |
| `ConfigRemoveRequest` | `ConfigRemoveResponse` | write | `dataId`, `group`, `tenant`, `tag` | 删除配置。 |
| `ConfigBatchListenRequest` | `ConfigChangeBatchListenResponse` | read | `listen`, `ConfigListenContext[]` | 添加或移除配置监听，并返回发生变化的配置。 |
| `ConfigChangeNotifyRequest` | `ConfigChangeNotifyResponse` | server push | `dataId`, `group`, `tenant` | 通知客户端配置发生变化。 |
| `ConfigFuzzyWatchRequest` | `ConfigFuzzyWatchResponse` | read | `groupKeyPattern`, `receivedGroupKeys`, `watchType`, `isInitializing` | 添加或取消配置 group key 模糊订阅。 |
| `ConfigFuzzyWatchChangeNotifyRequest` | `ConfigFuzzyWatchChangeNotifyResponse` | server push | `groupKey`, `changeType` | 通知客户端模糊订阅资源变化。 |
| `ConfigFuzzyWatchSyncRequest` | `ConfigFuzzyWatchSyncResponse` | server push | `syncType`, `groupKeyPattern`, `contexts`, `totalBatch`, `currentBatch` | 同步模糊订阅初始化或 diff 状态。 |
| `ClientConfigMetricRequest` | `ClientConfigMetricResponse` | read | `metricsKeys` | 查询客户端配置指标。 |
| `ConfigChangeClusterSyncRequest` | `ConfigChangeClusterSyncResponse` | inner | `dataId`, `group`, `tenant`, `lastModified`, `grayName`，legacy `isBeta`/`tag` | 通过[内部 RPC 模型](../design/foundation-internal-rpc-spec.md)在服务端节点之间同步配置变更事件；Config Notify 语义由[AP 一致性规范](../design/foundation-ap-consistency-spec.md)定义。从 Nacos 3.3 版本线开始，服务端处理不得再使用 legacy `isBeta` 或 `tag` 字段把 beta/tag 变更迁移为 `grayName`。 |

### 7.3 Naming

| Request type | Response type | 动作 | 主要字段 | 契约 |
| --- | --- | --- | --- | --- |
| `InstanceRequest` | `InstanceResponse` | write | `namespace`, `groupName`, `serviceName`, `type`, `instance` | 注册或注销临时实例。 |
| `PersistentInstanceRequest` | `InstanceResponse` | write | `namespace`, `groupName`, `serviceName`, `type`, `instance` | 注册或注销持久实例。 |
| `BatchInstanceRequest` | `BatchInstanceResponse` | write | `namespace`, `groupName`, `serviceName`, `type`, `instances` | 批量注册或注销实例。 |
| `ServiceQueryRequest` | `QueryServiceResponse` | read | `namespace`, `groupName`, `serviceName`, `cluster`, `healthyOnly`, `udpPort` | 查询服务实例。 |
| `ServiceListRequest` | `ServiceListResponse` | read | `namespace`, `groupName`, `pageNo`, `pageSize`, `selector` | 列举服务名。 |
| `SubscribeServiceRequest` | `SubscribeServiceResponse` | read | `namespace`, `groupName`, `serviceName`, `clusters`, `subscribe` | 订阅或取消订阅服务。 |
| `NotifySubscriberRequest` | `NotifySubscriberResponse` | server push | `namespace`, `groupName`, `serviceName`, `serviceInfo` | 向订阅者推送服务信息变化。 |
| `NamingFuzzyWatchRequest` | `NamingFuzzyWatchResponse` | read | `namespace`, `groupKeyPattern`, `receivedGroupKeys`, `watchType`, `isInitializing` | 添加或取消服务 key 模糊订阅。 |
| `NamingFuzzyWatchChangeNotifyRequest` | `NamingFuzzyWatchChangeNotifyResponse` | server push | `serviceKey`, `changedType` | 通知客户端模糊订阅服务变化。 |
| `NamingFuzzyWatchSyncRequest` | `NamingFuzzyWatchSyncResponse` | server push | `groupKeyPattern`, `contexts`, `totalBatch`, `currentBatch` | 同步模糊订阅初始化或 diff 状态。 |
| `DistroDataRequest` | `DistroDataResponse` | inner | `distroData`, `dataOperation` | 通过[内部 RPC 模型](../design/foundation-internal-rpc-spec.md)进行服务端节点之间的 Distro AP 协议数据传输；Distro 语义由[AP 一致性规范](../design/foundation-ap-consistency-spec.md)定义。 |

### 7.4 AI

AI payload 语义由 [AI Registry 规范](../ai/ai-registry-spec.md)和各资源类型规范定义。

| Request type | Response type | 动作 | 主要字段 | 契约 |
| --- | --- | --- | --- | --- |
| `QueryMcpServerRequest` | `QueryMcpServerResponse` | read | `namespace`, `mcpName`, `version` | 查询 MCP Server 详情。 |
| `ReleaseMcpServerRequest` | `ReleaseMcpServerResponse` | write | `serverSpecification`, `toolSpecification`, `resourceSpecification`, `endpointSpecification` | 发布 MCP Server 或新版本。 |
| `McpServerEndpointRequest` | `McpServerEndpointResponse` | write | `mcpName`, `address`, `port`, `version`, `type` | 注册或注销 MCP endpoint。 |
| `QueryAgentCardRequest` | `QueryAgentCardResponse` | read | `namespace`, `agentName`, `version`, `registrationType` | 查询 A2A AgentCard 详情。 |
| `ReleaseAgentCardRequest` | `ReleaseAgentCardResponse` | write | `agentCard`, `registrationType`, `setAsLatest` | 发布 AgentCard 或新版本。 |
| `AgentEndpointRequest` | `AgentEndpointResponse` | write | `agentName`, `endpoint`, `type` | 注册或注销一个 Agent endpoint。 |
| `BatchAgentEndpointRequest` | `AgentEndpointResponse` | write | `agentName`, `endpoints` | 替换当前客户端为某个 Agent 注册的 endpoints。 |
| `QueryPromptRequest` | `QueryPromptResponse` | read | `namespace`, `promptKey`, `version`, `label`, `md5` | 按版本、标签、latest 或 md5 查询 Prompt。 |

Skill ZIP 下载和 AgentSpec 组装属于 Java SDK interface 能力，但当前 Java 客户端
实现使用 HTTP/config 组合，不对应专用 gRPC payload。

### 7.5 Lock

Lock 领域语义由[分布式锁规范](../lock/lock-spec.md)定义。当前 gRPC 入口仍为实验性能力，
可能随该领域一起变化。

| Request type | Response type | 动作 | 主要字段 | 契约 |
| --- | --- | --- | --- | --- |
| `LockOperationRequest` | `LockOperationResponse` | handler 未声明 | `lockInstance`, `lockOperationEnum` | 尝试获取或释放 Nacos 分布式锁。 |

## 8. 新增或变更 gRPC API 的规则

1. 新增具体 `Request` 和 `Response` 类型；只有语义契约相同的操作才复用类型。
2. 在正确的 `META-INF/services/com.alibaba.nacos.api.remote.Payload` 文件中注册
   请求和响应 payload。
3. 新增 `RequestHandler<Request, Response>` bean，并记录 action、module 和 source。
4. 新增 handler 类必须添加 `@Since`，声明该 gRPC API 起始支持的 Nacos 版本号。
5. 面向 SDK 或受保护的 inner 操作应添加 `@Secured`。
6. cluster-only payload 应添加 `@InvokeSource`。
7. 请求字段保持显式且 JSON 兼容。
8. 当操作暴露为公开 SDK interface 时，同步更新本规范和
   [SDK interface 规范](../sdk/sdk-spec.md)。
9. 对于服务端间 payload，还应同步更新
   [内部 RPC 与集群请求规范](../design/foundation-internal-rpc-spec.md)，或拥有该集群请求语义的
   领域规范。
