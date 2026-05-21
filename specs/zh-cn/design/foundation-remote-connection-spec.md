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

# Nacos 远程连接生命周期规范

本文定义 Nacos 服务端远程连接的基础层生命周期，是
[基础能力规范](foundation-capabilities-spec.md)中远程连接部分的展开。

远程连接层负责传输连接状态、请求上下文、推送与 ack 管道，以及连接保护。它不负责领域请求语义。
详细请求上下文和 filter 规则由[请求过滤与运行时上下文规范](foundation-request-context-spec.md)定义。

## 1. 范围

远程连接生命周期能力负责：

- SDK 和 cluster 来源的 gRPC transport 启动；
- connection id 创建和 transport 地址捕获；
- 连接 setup、注册、拒绝和注销；
- 连接元数据和能力表传递；
- 请求元数据和请求上下文创建；
- 服务端到客户端 push 以及 ack 匹配；
- active time 刷新、过期连接检测和运行时踢除；
- 面向领域运行时状态的连接 listener 回调。

它不负责：

- Config、Naming、AI 或 auth payload 语义；
- 公开 HTTP API 行为；
- 持久领域状态；
- SDK 重试、failover 或 server-list 选择行为，这部分由
  [客户端连接与故障切换规范](../client/client-connection-failover-spec.md)定义；
- AP/CP 一致性 membership。

公开 gRPC 包裹和 JSON payload 策略由 [gRPC API 规范](../grpc-api/api-spec.md)定义。本文定义承载
这些请求的服务端连接生命周期。

## 2. Connection 模型

`Connection` 是服务端对 live remote connection 的抽象。`GrpcConnection` 是默认 gRPC 实现。

| 模型 | 职责 |
| --- | --- |
| `Connection` | 暴露连接元数据、labels、能力表、trace 标记、同步请求、异步请求、no-ack push、close 和连通性检查。 |
| `ConnectionMeta` | 存储 connection id、source、client ip、remote endpoint、local port、version、app、namespace、labels、TLS 标记、创建时间、最后活跃时间和 push queue block 时间戳。 |
| `ConnectionManager` | 拥有内存连接注册表、按 client ip 统计、连接 control 检查、listener 回调、active time 刷新和运行时踢除。 |
| `ClientConnectionEventListener` | 允许领域在连接注册或注销时挂载或释放运行时状态。 |

连接身份规则：

- `connectionId` 在 gRPC transport ready 时由时间戳、remote ip 和 remote port 生成；
- 普通 unary 请求被接受前，连接必须完成 `ConnectionSetupRequest` 处理；
- labels 必须包含 source 信息，尤其是 SDK 或 cluster source；
- cluster-source 连接是内部连接，跳过 SDK 连接数限制检查；
- 能力表在 setup 时协商，并通过 `RequestMeta` 传递给 request handler；
- 已接受的 unary 请求和 push ack 必须刷新 `lastActiveTime`。

## 3. gRPC Server 表面

Nacos 暴露两类 gRPC server source：

| 来源 | Server | 端口偏移 | 目的 |
| --- | --- | --- | --- |
| SDK | `GrpcSdkServer` | SDK gRPC port offset | 客户端到服务端请求和服务端 push。 |
| Cluster | `GrpcClusterServer` | Cluster gRPC port offset | 服务端间请求。 |

两类 server 都使用 `BaseGrpcServer`，并暴露：

- unary `Request/request`，用于普通 request/response 调用；
- bidirectional `BiRequestStream/requestBiStream`，用于 setup、server push 和 push ack；
- `AddressTransportFilter`，用于捕获 remote/local 地址，并在 transport terminated 时注销连接；
- `GrpcConnectionInterceptor`，用于把 connection id 和地址属性放入 gRPC context；
- source-specific interceptor、transport filter、keepalive 配置和 inbound-message-size 配置。

服务端 keepalive 配置是传输层保护。领域模块必须消费连接生命周期事件，而不是自行实现 gRPC 传输层心跳。

## 4. Setup 与注册

连接 setup 通过 bidirectional stream 完成：

1. transport 层在 gRPC transport ready 时创建连接属性。
2. 客户端在 bidirectional stream 上发送 `ConnectionSetupRequest`。
3. 服务端根据 payload metadata、setup labels、client version、namespace、source、app name、
   local port 和 TLS 状态构建 `ConnectionMeta`。
4. 服务端通过 `ConnectionGeneratorService` 创建 `Connection`。
5. setup 中存在能力表时，将能力表存入 connection。
6. 服务端仍处于 starting 状态时拒绝 SDK 连接。
7. `ConnectionManager.register` 校验连接状态、应用连接 control 规则、记录 trace、保存连接、递增
   per-ip 计数，并通知 listener。
8. 如果使用了能力协商，服务端发送带当前服务端能力表的 `SetupAckRequest`。

注册失败必须关闭连接，且不得留下绑定到被拒绝 connection id 的领域运行时状态。

## 5. Unary 请求处理

Unary 请求由 `GrpcRequestAcceptor` 处理。

请求处理规则：

- 服务端 starting 时拒绝请求，server check 除外；
- `ServerCheckRequest` 直接处理并返回 connection id；
- request handler 按请求类 simple name 解析；
- gRPC context 中的 connection id 必须已经注册；
- malformed、unknown 或 non-request payload 返回错误响应；
- `RequestMeta` 必须包含来自注册连接的 client ip、connection id、client version、labels 和能力表；
- `RequestContext` 必须填充 protocol、request target、app、remote endpoint 和 source ip；
- request filter 在领域 handler 之前执行；
- 已接受的 unary 请求会在进入领域处理前刷新连接 active time。

Request handler 拥有 payload 语义。远程层只负责路由解析、元数据、上下文、filter 和响应转换。
Request filter 执行还必须遵循[请求过滤与运行时上下文规范](foundation-request-context-spec.md)。

## 6. Push 与 Ack

服务端到客户端 push 使用已注册的 `Connection`。

Push 规则：

- `sendRequestNoAck` 将 Nacos `Request` 转换为 gRPC payload，并写入 bidirectional stream；
- 由于 `StreamObserver.onNext` 不是线程安全的，stream 写入必须串行化；
- push 前必须检查 gRPC write queue readiness；
- 当 write queue not ready 时，连接记录 push queue block 时间戳，并以 connection-busy 语义失败；
- request/async push 分配 push request id，并在 `RpcAckCallbackSynchronizer` 中注册 ack future；
- bidirectional-stream 上收到的 `Response` payload 视为 push ack，用于清理或完成匹配的 future；
- disconnect cleanup 必须清理该 connection 的 ack 状态。

Push 和 ack 管道不定义领域订阅语义。Config、Naming 和 AI 规范定义应该推送什么以及何时推送。

## 7. 注销与 Listener 回调

当 transport terminated、active detection 失败、over-limit ejection 关闭连接，或其他服务端逻辑显式
移除连接时，连接会被注销。

注销规则：

- listener 回调前必须先从注册表中移除连接；
- per-client-ip 计数必须递减，并在归零时移除；
- 底层 transport 必须关闭；
- 必须调用 `ClientConnectionEventListener.clientDisConnected` 执行清理；
- listener 失败应记录日志，但不得阻止其他 listener。

领域运行时 manager 可以通过 `clientConnected` 把状态挂载到 `connectionId`，但必须通过
`clientDisConnected` 释放或重建这些状态。持久状态不得依赖连接生命周期。

## 8. Active Detection 与运行时踢除

`ConnectionManager` 启动周期性运行时踢除任务。默认 `NacosRuntimeConnectionEjector` 执行过期检测和
过载踢除。

检测规则：

- active time 超过 `RuntimeConnectionEjector.KEEP_ALIVE_TIME` 的连接会成为 active detection 候选；
- push queue 在服务端 block 窗口内持续 block 的连接也会成为候选；
- active detection 发送 `ClientDetectionRequest`，并等待成功响应；
- 成功响应会刷新 active time；
- 未成功响应的连接会被注销。

过载踢除规则：

- 只有设置了目标 load count 时才执行运行时 load ejection；
- SDK 连接可以收到带可选 redirect address 的 `ConnectResetRequest`；
- cluster 连接不得被 SDK load balancing 逻辑踢除；
- runtime ejector 实现可以通过 SPI 加载，但必须保持 `ConnectionManager` 注册表和 listener 语义。

连接 control 规则和 TPS 检查是 Control 插件定义的横切保护点。它们可以拒绝或延迟连接路径，但不得改变
领域数据归属。

## 9. 领域使用方规则

使用远程连接生命周期的领域必须遵循：

- 只把运行时状态绑定到 `connectionId`；
- disconnect 时清理 connection-bound 状态；
- disconnect 处理必须幂等；
- 除非领域规范定义了可恢复身份，否则 reconnect 应视为新连接；
- 使用 `RequestMeta` 中的 labels、source、能力表和 client version 做兼容决策，而不是重新解析
  transport 内部细节；
- 不得把连接存在性当作持久鉴权、归属或持久化证据；
- listener 回调中涉及慢速远端 IO 时，应避免阻塞连接注册或清理路径；
- push failure 和 connection-busy 行为必须通过领域 retry 或 resync 语义体现。

## 10. 相关规范

- [基础能力规范](foundation-capabilities-spec.md)
- [集群成员规范](foundation-cluster-membership-spec.md)
- [内部 RPC 与集群请求规范](foundation-internal-rpc-spec.md)
- [请求过滤与运行时上下文规范](foundation-request-context-spec.md)
- [gRPC API 规范](../grpc-api/api-spec.md)
- [Control 插件规范](../plugin/control-plugin-spec.md)
- [Trace 插件规范](../plugin/trace-plugin-spec.md)
