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

# Nacos 客户端能力协商规范

本文定义 Nacos 运行时连接的客户端侧能力协商。本文展开
[客户端运行时规范](client-runtime-spec.md)中的能力部分，并补充
[gRPC API 规范](../grpc-api/api-spec.md)中的 setup 规则。

## 1. 能力模型

Ability 是按 `AbilityMode` 划分作用域的具名 boolean feature flag。

| Mode | 持有方 | 目的 |
|------|--------|------|
| `SERVER` | Nacos server node | 描述 SDK client 或 cluster client 可见的服务端支持能力。 |
| `SDK_CLIENT` | Runtime SDK client | 描述 SDK client 可使用或可接收的特性。 |
| `CLUSTER_CLIENT` | Server-to-server client | 描述内部集群 client 特性。 |

Ability name 在同一 mode 内必须唯一。Ability key 定义是连接两侧的兼容注册表。

## 2. 当前 SDK 与服务端能力

当前 Java SDK 声明支持：

| SDK ability | 含义 |
|-------------|------|
| `SDK_CLIENT_FUZZY_WATCH` | 客户端可以使用 Config 或 Naming fuzzy watch。 |
| `SDK_CLIENT_DISTRIBUTED_LOCK` | 客户端可以使用分布式锁功能。 |
| `SDK_MCP_REGISTRY` | 客户端可以使用 MCP registry 运行时功能。 |
| `SDK_AGENT_REGISTRY` | 客户端可以使用 Agent 和 AgentCard 运行时功能。 |

当前服务端声明支持：

| Server ability | 含义 |
|----------------|------|
| `SERVER_PERSISTENT_INSTANCE_BY_GRPC` | 支持通过 gRPC 注册或注销 Naming 持久实例。 |
| `SERVER_FUZZY_WATCH` | 支持 Config 或 Naming fuzzy watch。 |
| `SERVER_DISTRIBUTED_LOCK` | 支持分布式锁。 |
| `SERVER_MCP_REGISTRY` | 支持 MCP registry 操作。 |
| `SERVER_AGENT_REGISTRY` | 支持 Agent 和 AgentCard registry 操作。 |
| `SERVER_AGENT_CARD_V1` | 支持 A2A AgentCard 1.0 协议字段。 |

新增 ability 需要同时提供具名 key 和领域规则，说明该 ability 控制的行为。

## 3. gRPC 协商流程

运行时客户端在 gRPC connection setup 阶段协商能力：

1. 客户端向选中的服务端打开 channel 并发送 `ServerCheckRequest`。
2. 服务端返回 `ServerCheckResponse`，包含 connection id 和是否支持能力协商的标记。
3. 客户端打开 bidirectional stream，并发送 `ConnectionSetupRequest`，携带 client version、
   labels、namespace/tenant 和当前 client 在该 connection mode 下的能力表。
4. 如果服务端支持能力协商，客户端等待 `SetupAckRequest`。
5. `SetupAckRequest` 携带服务端能力表。客户端将其存入当前 connection。
6. 如果服务端声明支持能力协商，但客户端在配置 timeout 内没有收到能力表，本次连接尝试必须放弃。
7. 如果服务端不支持能力协商，客户端可以为了兼容完成 setup。该 connection 上的能力检查解析为
   `UNKNOWN`，除非实现定义了显式 legacy fallback。

能力状态是 connection 维度的。Reconnect 会创建新的 connection，并必须刷新能力表。

## 4. 能力状态语义

客户端代码观察到的能力状态包括：

| 状态 | 含义 | 必须遵循的行为 |
|------|------|----------------|
| `SUPPORTED` | 当前 connection 显式支持该能力。 | 被该能力控制的功能可以使用优化路径或新路径。 |
| `NOT_SUPPORTED` | 当前 connection 显式不支持该能力。 | 功能必须使用有文档说明的 fallback，或返回明确的 unsupported error。 |
| `UNKNOWN` | 不存在能力表或 key 缺失。 | 功能不能假定支持。只有领域规范允许时，才可以使用 legacy fallback。 |

Unknown 不是成功。新功能应优先返回 fail-fast unsupported error，而不是发送选中服务端可能无法理解的
请求。

## 5. 功能控制规则

领域客户端使用可选或版本化能力前必须检查服务端能力：

- Naming 持久实例注册仅在 `SERVER_PERSISTENT_INSTANCE_BY_GRPC` 支持时使用 gRPC；
  否则可以使用有文档说明的 HTTP 兼容路径。
- Config 和 Naming fuzzy watch 必须要求 `SERVER_FUZZY_WATCH`。
- 分布式锁必须要求 `SERVER_DISTRIBUTED_LOCK`，因为该功能实验性且不保证所有服务端可用。
- AI MCP registry 操作必须要求 `SERVER_MCP_REGISTRY`。
- AI Agent 和 AgentCard 操作必须要求 `SERVER_AGENT_REGISTRY`。
- A2A AgentCard 1.0 字段应要求 `SERVER_AGENT_CARD_V1`，或使用显式文档化的兼容转换。

功能代码不应把 positive ability result 缓存在当前 connection 生命周期之外。执行操作前应查询
运行时 connection ability，或确认缓存值属于当前 connection。

## 6. 兼容规则

能力协商是混合版本兼容机制。新增运行时行为前应优先使用能力协商，而不是增加临时版本判断。版本号
可以用于日志和诊断，但只要存在 ability key，运行时行为应优先使用 ability status。

Legacy fallback 必须由领域规范说明。Fallback 的移除应遵循
[兼容与废弃策略规范](../design/compatibility-deprecation-spec.md)。

## 7. 待处理问题

- 公开 ability key 列表应由源码生成，避免文档漂移。
