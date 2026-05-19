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

# Nacos 客户端连接与故障切换规范

本文定义 Nacos Client SDK 的客户端侧服务端发现、连接生命周期、故障切换、TLS 和请求
身份传递。本文展开[客户端运行时规范](client-runtime-spec.md)中的连接部分。服务端侧
连接生命周期由[远程连接生命周期规范](../design/foundation-remote-connection-spec.md)定义。

## 1. 地址解析

Client SDK 通过 `ServerListProvider` 解析 Nacos 服务端地址。当前 Java 实现支持：

- 来自 `serverAddr` 的固定地址；
- 来自 endpoint 或 address server 的动态地址；
- 面向扩展场景的 SPI provider。

固定地址列表在初始化后保持稳定。动态地址 provider 可以定期刷新，并在有效列表变化时发布
server-list-change 事件。

有效服务端地址列表在传输使用前必须规范化：

- 未携带端口的地址使用默认 Nacos 服务端端口；
- 固定地址中的 HTTP 或 HTTPS scheme 在 HTTP 调用中保留；
- gRPC 使用选中服务端端口加配置的 gRPC port offset；
- context path 和 namespace 属于客户端身份，不属于 gRPC host/port。

## 2. Server List 刷新

动态 server-list 刷新必须是本地且非权威的。它只改变客户端可连接的服务端，不改变 Config、
Naming、AI 或 Lock 资源状态。

当动态 provider 接收到变化后的列表时：

1. provider 原子替换本地列表；
2. 发布 `ServerListChangeEvent`；
3. 已有 RPC client 检查当前连接服务端是否仍在列表中；
4. 如果当前服务端不再有效，RPC client 开始重连。

如果使用固定列表，provider 不应发布刷新事件。

## 3. gRPC 连接生命周期

客户端 gRPC 连接遵循以下生命周期：

```text
WAIT_INIT -> INITIALIZED -> STARTING -> RUNNING
                                      -> UNHEALTHY -> reconnect -> RUNNING
                                      -> SHUTDOWN
```

运行时应在启动阶段尝试同步建立初始连接。如果启动阶段无法在配置的重试预算内建立运行中连接，
可以继续异步重连，但公开 SDK 调用必须按照领域契约暴露连接不可用状态。

触发 reconnect 的情况包括：

- request stream error 或 completed；
- health check 失败；
- 服务端显式 reset request；
- server list 刷新后当前服务端不在有效列表中；
- request failure 后 health check 也失败；
- client lifecycle restart。

服务端 reset request 可以携带推荐目标服务端。当推荐服务端仍在有效 server list 中时，客户端
可以优先尝试该服务端；如果失败，则回到正常轮转。

## 4. Health Check 与假死检测

当连接在配置的 keepalive 窗口内空闲时，客户端会周期性检查连接存活。health check 失败会将
RPC client 标记为 `UNHEALTHY` 并调度 reconnect。

gRPC 传输 keepalive 用于防止半开 TCP 连接。领域模块不应在 Naming、Config、AI 或 Lock 请求
之上再实现自己的 gRPC 心跳，而应响应连接事件和领域 push。

## 5. HTTP 传输

HTTP 仍是兼容和部分操作的客户端传输方式。领域客户端可以在以下场景使用 HTTP：

- 服务端不支持所需 gRPC 能力；
- 操作属于 legacy compatibility operation；
- 公开 SDK 方法有意映射到 Open API；
- 功能不需要长连接 push 或连接状态。

HTTP fallback 必须由领域客户端显式定义。gRPC 请求失败后，不应自动通过 HTTP 修改资源状态，
除非领域客户端已经定义该 fallback。

## 6. TLS

客户端 gRPC TLS 属于传输基础设施。运行时可以支持：

- TLS 关闭时使用 plaintext channel；
- 使用配置 provider、protocols 和 ciphers 的 TLS channel；
- 受控测试环境中的 trust-all 模式；
- 生产环境中的 trust collection certificate file；
- 使用 client certificate chain、private key 和 private key password 的双向 TLS。

当 TLS 开启时，选中的 Nacos 服务端必须在 gRPC 端口支持 TLS。TLS/client-server 不匹配是连接
失败，不是领域操作失败。

HTTP TLS 遵循选定 HTTP URL scheme 和 HTTP client 配置。领域规范不应重新定义 TLS 行为。

## 7. 请求身份传递

客户端鉴权插件通过运行时 security proxy 登录，并为每个 request resource 提供
`LoginIdentityContext`。运行时客户端在发送领域请求前，必须把身份参数写入 HTTP 或 gRPC
请求 header。

如果服务端返回 no-right response 表明运行时身份过期或无效，客户端可以标记 login context
待刷新，并按领域操作的 retry 规则处理。客户端不能把鉴权失败隐藏成本地缓存成功。

## 8. 失败可见性

连接故障切换修复的是传输路径。除非客户端收到并校验了领域 response，否则不能保证领域写入已经
生效。

Client SDK 应区分：

- 连接不可用；
- request timeout 且服务端结果未知；
- 服务端拒绝请求；
- read 使用了本地 failover 或本地 snapshot；
- redo 在 reconnect 后尚未恢复运行时意图。

## 9. 与本地恢复的关系

连接恢复会触发本地恢复行为，但每个领域拥有自己的恢复状态：

- Config listener 会 resync 已知 group key 和 fuzzy watch 状态。
- Naming 会 redo 临时实例注册和订阅。
- AI 会 redo 运行时 endpoint 注册和订阅。
- 本地缓存读取由[客户端本地缓存与 Redo 规范](client-local-cache-redo-spec.md)约束。

## 10. 待处理问题

- HTTP 和 gRPC 连接指标应遵循
  [可观测钩子规范](../design/foundation-observability-hooks-spec.md)中的共享字段和 label 指引。
- 多语言 SDK 应对齐 server list refresh event 语义和 reconnect status 命名。
