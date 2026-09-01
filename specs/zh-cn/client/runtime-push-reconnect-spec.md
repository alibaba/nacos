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

# Nacos 运行时推送与重连规范

本文定义 Config、Naming 和 AI 运行时流共享的 push、push retry、disconnect 和 reconnect
规则。本文展开[客户端运行时规范](client-runtime-spec.md)，并补充
[客户端连接与故障切换规范](client-connection-failover-spec.md)。

## 1. 范围

运行时推送与重连负责：

- 已注册 connection 上的服务端到客户端变更通知；
- push ack 与 retry 行为；
- connection 关闭时清理服务端 listener 或 subscription 状态；
- reconnect 后客户端侧 resubscription 或 redo；
- push notification 与领域权威读取之间的边界。

它不负责：

- Config、Naming 或 AI 资源持久化；
- 服务端间一致性传播；
- 客户端本地 snapshot 或 failover 文件语义；
- 大范围管理诊断。

## 2. Push 是通知，不是权威状态

Push message 通知运行时客户端：服务端视图可能已经变化。Push 不应被视为领域资源的唯一权威副本。

领域规则：

- Config push 携带变化身份。客户端收到通知后必须重新查询 Config content。
- Naming push 携带订阅服务的当前 discovery view。该视图仍是派生服务状态，可以通过 re-query 或
  resubscription 刷新。
- AI push 行为由各 AI resource spec 版本化定义，并必须保持与对应 query API 相同的身份规则。

RAD Watch 只携带失效 Hint。Client 必须执行普通的鉴权 Discover，并比较完整结果的
Canonical Fingerprint，之后才能替换本地 Cache 或调用 Listener。

## 3. 服务端 Connection 状态

运行时 listener 或 subscription state 绑定到服务端 connection id。连接关闭时，服务端必须移除
connection 维度状态：

- Config 清理该 connection 的 config listen context 和 fuzzy watch context。
- Naming 移除 connection-based client state、由该 client 发布的临时实例、subscriber，以及从该
  client 派生的索引。
- 当 AI runtime endpoint 和 subscription state 绑定运行时 connection 时，必须遵循同样的连接
  归属规则。

Connection cleanup 必须发布更新派生索引和 push 视图所需的本地事件。

## 4. Push Retry

Push retry 是当前 connection 生命周期内的 best-effort delivery。

Config push retry：

- 普通配置变更 push 使用 `ConfigChangeNotifyRequest`；
- fuzzy watch push 使用 fuzzy watch notify request；
- retry 受配置的最大重试次数约束；
- 普通配置 push retry 超过上限时，服务端可以 unregister connection，强制触发客户端恢复。

Naming push retry：

- service-change push 通过按 service 合并的 delay task 调度；
- service-subscribed push 可以只面向单个 client；
- push 失败时，除非失败明确表示不需要重试，否则可以为目标 client 加入延迟重试；
- retry 不得修改 Naming 资源状态。

Push retry 应记录指标和 trace 事实，但可观测不能成为正确性路径的一部分。

## 5. 客户端重连恢复

Reconnect 后，客户端必须恢复运行时意图：

- Config 在 disconnect 时将 listener 和 fuzzy watch state 标记为不一致，并在 reconnect 后重新同步
  已知 listener。
- Naming 在 disconnect 时将 redo data 标记为未注册，并在 reconnect 后 redo 临时实例注册和订阅。
- AI runtime client 在功能定义了可重连运行时状态时，redo endpoint 和 subscription intent。

客户端恢复细节由[客户端本地缓存与 Redo 规范](client-local-cache-redo-spec.md)定义。连接选择和
存活由[客户端连接与故障切换规范](client-connection-failover-spec.md)定义。

## 6. Agent 与 RAD Watch 契约

本节定义 Nacos Agent/RAD Server-aware Watch。只有
[Agent API 规范](../ai/agent-api-spec.md)中的独立能力已实现并完成协商后才能声明支持。

### 6.1 身份、Projection 与 Fingerprint

规范化本地 Watch Identity 为：

```text
(namespaceId, canonicalAgentReference, canonicalFilter, listenerIdentity)
```

Canonical Reference 保留 Exact Version、Label、显式 Latest 和面向发布安全的未指定
Version 形式。Filter 物化默认值，并对 Set-valued Field 排序去重。Listener Identity 是
取消时传入的同一实例。Server Projection Key 不包含 Listener Identity 和鉴权/可见性
Scope；鉴权决定调用方能否安装或刷新 Watch，绝不编码为资源身份。

Client 与 Server 共用 Request 规范化和完整结果 Fingerprint 逻辑。Fingerprint 格式为
`sha256-canonical-json-v1:<64-lowercase-hex>`，它只表示相等，不是 Sequence、Version、
鉴权证明或 Replay Cursor。最终 Fingerprint 相同时允许合并 A-B-A Task，且无需再次回调。

### 6.2 Server Projection 与 Push 执行

Definition、Version/Label、Visibility、Runtime Endpoint、Liveness 和 Connection Cleanup
事件把受影响 Projection 标记为 Dirty。可复用 Push Pipeline 遵循 Naming 面向最新 Snapshot
的任务纪律：Producer 按 Projection 新增或合并 Delay Task；未开始执行的 Task 可以合并；
一旦开始就执行完成；并发的后续变化创建或合并到下一 Task。Agent 提供 Projection Matching
和 Fingerprint 构建，共享 Runtime Code 提供 Task Merge、Target Fan-out、Connection Lookup、
异步 Push、Retry 和 Metrics。

Server 不保存此前业务 Snapshot 或每 Watch Sequence。gRPC Notify 只包含 `watchKey`、
Event Type、可选的已观测 Fingerprint 和终止错误码。Client 校验 Key 并将本地 Intent 标记
Dirty 后立即 ACK；ACK 不等待 Discover 或 Listener。Retry 有界且属于 Connection；耗尽时
可以强制重连，由重新订阅恢复 Intent。

### 6.3 Current-fact 刷新与 Listener 投递

每个已接受 Hint、Reconnect Refresh Requirement、HTTP Changed Item 或轮询回退 Tick 都
执行普通鉴权 Discover。Client 对完整结果进行 Canonicalize 并比较 Fingerprint；只有不同
结果才原子替换 Cache 并投递完整 `SNAPSHOT`，不得合并结果片段。过滤后的空 Shape 是合法
Snapshot。Listener 工作与 Connection/Long-poll I/O 隔离；慢、抛异常或 Executor Reject
都不能改变 Hint ACK 状态或阻塞其他 Watch。

`NOT_FOUND` 进入有界本地 Pending State，同一缺失周期最多投递一次 `UNAVAILABLE`；恢复
后投递新 Snapshot。终止性鉴权或容量错误投递一次 Unavailable Event 并删除被拒绝 Intent。
瞬时传输或 Discover 失败保留 Intent 并有界退避，不得把陈旧数据作为新 Snapshot 投递。

### 6.4 gRPC 重连

gRPC Watch State 属于单个 Connection。Disconnect 删除 Server State，并使旧 `watchKey`
全部失效。SDK 保留 Canonical Local Intent，在新 Connection 下重新订阅完整 Active Set，
并在 `refreshRequired=true` 时执行 Discover。不进行周期性 Full-data Sync；重新订阅加
Current-fact Discover 就是对账机制。旧 Key 的迟到通知被拒绝，不能到达 Listener。

### 6.5 HTTP Batch Long Poll

HTTP 对当前完整 Watch Set 使用一个 Request-scoped Batch Long Poll，而不是每 Agent 一个
请求。请求包含 Local Generation、Timeout，以及每个 Item 的 Client Watch ID、Discovery
Request 和最后物化 Fingerprint。Response 只包含 Timeout 或变化的 Client ID，不携带业务
数据或逐 Item 鉴权详情。

每轮 Long Poll 可以到达不同 Cluster Node。正确性不依赖节点本地 Generation 连续：接收
节点将提交的 Fingerprint 与当前 Serving Projection 比较，返回的 ID 再通过 Discover 获取。
本地 List 变化时推进 Generation 并取代上一请求；迟到的旧 Generation Response 被忽略。
Server 感知 Socket Cancel 只是优化；Timeout 和下一轮完整 List 请求清理 Request-scoped
Wait State。

首版 HTTP Binding 只执行单 Namespace 的请求级 AI Read 鉴权，明确不实现逐 Item 的多资源
精细化鉴权。Discover 仍是强制的内容与可见性鉴权边界。

## 7. 顺序

Push delivery order 只在某个节点的本地 event 和 task path 内成立。它不是跨集群全局 total order。

领域规范必须定义本地服务视图何时可见：

- Config 写入可见性、dump 顺序和本地缓存可见性由
  [Config 一致性、Dump 与可见性规范](../config/config-consistency-dump-visibility-spec.md)定义。
- Naming 临时服务收敛由
  [Naming 临时服务 Distro 一致性规范](../naming/naming-ephemeral-distro-consistency-spec.md)定义。
- Naming 持久服务和元数据可见性由
  [Naming 持久服务 CP 一致性规范](../naming/naming-persistent-cp-consistency-spec.md)定义。

## 8. 失败规则

- connection 不存在时，应取消或跳过对该 connection 的 push。
- push timeout 不证明客户端没有观察到变化，只表示服务端没有在超时时间内收到成功 ack。
- 客户端必须能够通过 re-query、resync 或 redo 从 missed push 中恢复。
- Server push 不得隐藏底层 query path 的鉴权失败。

## 9. 待处理问题

- push retry、timeout 和 reconnect recovery 观测应遵循
  [可观测钩子规范](../design/foundation-observability-hooks-spec.md)中的共享字段和 label 指引。
