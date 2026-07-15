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

# Nacos 客户端运行时规范

本文定义公共 Client SDK interface 之下的共享运行时规则。公开 SDK 能力边界由
[SDK 规范](../sdk/sdk-spec.md)定义。本文定义客户端进程如何连接 Nacos、发现
服务端地址、携带鉴权身份、协商能力、缓存运行时数据，并在重连后恢复运行时意图。

Java 是当前基准实现。其他语言 SDK 在语言运行时支持时，应对齐这些运行时语义。

## 1. 范围

客户端运行时负责：

- SDK 初始化、namespace 绑定、属性解析和生命周期关闭；
- server list 解析和刷新；
- 客户端 HTTP 与 gRPC 传输选择；
- 连接生命周期、故障切换、TLS 和身份传递；
- connection 维度的能力协商；
- 本地 snapshot、本地 failover 数据、监听状态、订阅状态和 redo 状态；
- 客户端指标与诊断钩子。

客户端运行时不负责：

- Config、Naming、AI 或 Lock 的资源语义；
- 服务端 AP/CP 一致性、持久化或 dump 顺序；
- Admin API 和 Maintainer SDK 的管理契约；
- 插件语义，除调用客户端侧插件扩展点之外。

领域规范定义资源含义。客户端运行时定义 SDK 在应用正常运行过程中如何保持这些
资源视图可用。

## 2. 运行时层次

客户端运行时分层如下：

```text
Public SDK interface
  -> Service implementation and client proxy
     -> Server list, authentication, and transport runtime
        -> Connection, ability, cache, listener, and redo runtime
           -> Domain request or local recovery view
```

Service implementation 可以使用 gRPC、HTTP、本地文件或组合方式。即使传输方式
变化，公开 SDK 行为也必须保持稳定。

## 3. 设计规则

### 3.1 运行时客户端不是管理面

客户端运行时面向应用执行优化。它应提供已知资源快速访问、订阅、本地恢复和连接
修复。不应静默引入大范围 namespace、集群或领域管理能力。管理行为属于 Admin API
或 Maintainer SDK。

### 3.2 运行时数据默认是派生数据

本地缓存、failover 文件、监听状态、订阅状态和 redo entry 都来自客户端意图或服务端
响应。它们不是服务端权威状态。

唯一例外是用户显式维护的本地 failover 文件。failover 文件可以临时覆盖远端读取视图，
但它本身不会自动写回服务端。

### 3.3 连接状态是恢复信号

gRPC 连接事件会触发 listener resync、fuzzy watch resync、Naming subscription
redo、临时实例 redo、AI endpoint redo 和其他运行时修复。除非领域规范定义更强的
契约，否则领域客户端必须把 reconnect 视为新的服务端挂载点。

### 3.4 传输安全是运行时基础设施

客户端鉴权插件、请求身份 header、TLS 和双向 TLS 属于运行时基础设施。领域请求对象
不应重复实现传输安全逻辑。领域规范可以定义权限检查使用的资源身份，但客户端运行时
负责如何通过选定传输携带登录身份。

## 4. 运行时组件

| 组件 | 职责 | 详细规范 |
|------|------|----------|
| Server list 与连接 | 解析服务端地址、刷新动态地址列表、创建 HTTP/gRPC client、故障重连并应用 TLS。 | [客户端连接与故障切换规范](client-connection-failover-spec.md) |
| 能力协商 | 交换客户端与服务端能力表，并按当前连接能力状态控制可选能力。 | [客户端能力协商规范](client-ability-negotiation-spec.md) |
| 缓存与 redo | 维护本地 snapshot、failover 视图、监听状态、订阅状态和重连 redo 数据。 | [客户端本地缓存与 Redo 规范](client-local-cache-redo-spec.md) |
| Push 与重连恢复 | 定义服务端 push 语义、push retry、disconnect cleanup 和重连后的客户端恢复。 | [运行时推送与重连规范](runtime-push-reconnect-spec.md) |

## 5. 领域对齐

Config 运行时行为包括已知配置读取、listener 注册、fuzzy watch、本地 failover 文件、
encrypted-data-key snapshot 和服务端查询 snapshot。Config 内容语义仍由
[Config 规范](../config/config-spec.md)定义。

Naming 运行时行为包括服务订阅、push 处理、本地 service-info cache、failover 视图、
临时实例 redo 和 subscriber redo。Naming 资源语义仍由
[Naming 规范](../naming/naming-spec.md)定义。

AI 运行时行为包括 endpoint 注册、资源查询、订阅、能力检查，以及面向快速变化 AI 协议
的兼容处理。AI 资源语义仍由
[AI Registry 规范](../ai/ai-registry-spec.md)定义。

分布式锁运行时行为是可选且实验性的。客户端发送 lock 操作前必须检查服务端 lock 能力。
Lock 语义由[分布式锁规范](../lock/lock-spec.md)定义。

## 6. 插件对齐

客户端运行时可以调用客户端侧插件：

- 寻址插件可以参与 server-list 解析；
- 鉴权插件可以登录并提供请求身份上下文；
- 配置加解密插件可以转换 Config payload 和 encrypted data key。

插件行为必须遵循[插件规范](../plugin/README.md)。客户端运行时必须按照对应插件契约
处理插件失败，而不是把插件失败隐藏成领域成功。

## 7. 待处理问题

- 多语言 SDK 尚未完全对齐 server list 刷新、failover、redo、能力协商和 TLS 行为。
- 客户端运行时指标和 trace 字段应遵循
  [可观测钩子规范](../design/foundation-observability-hooks-spec.md)中的共享字段和 label 指引。
- 如果客户端鉴权、TLS 和加解密行为继续扩展，可能需要在当前插件和连接契约之下拆分子规范。
