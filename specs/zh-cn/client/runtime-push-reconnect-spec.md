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

目标 RAD Watch 携带完整 Discovery Snapshot，而不是只携带变化身份。该 Snapshot 是替换本地
RAD Discovery Cache 的权威值；Registry 仍是资源权威，Discover Re-query 可以刷新 Snapshot。

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

## 6. Agent 与 RAD 目标 Watch 契约

本节定义 Agent/RAD 的目标 gRPC Watch 契约，不表示当前已经实现该能力。在
[Agent API 规范](../ai/agent-api-spec.md)中的 Agent/RAD 能力完成实现并经过协商前，
实现不得暴露或声明支持该行为。首版 Nacos HTTP Binding 支持 Discover，但不支持 Watch。

### 6.1 身份与初始结果

规范化 SDK Watch Key 为：

```text
(namespaceId, canonicalAgentReference, canonicalFilter, listenerIdentity)
```

规范化 Reference 保留调用方选择的是精确 Version、Label 还是 Latest。规范化 Filter
应用缺省值，并对集合字段排序和去重。Listener identity 是取消时传入的同一 Listener
实例。实现可以复用 Wire Subscription，但必须保留该公开身份并隔离 Callback。

Server 在创建 Watch 前执行 Discover。`NOT_FOUND` 不创建服务端或客户端 Watch State。
订阅成功的 `AgentSubscribeResponse` 返回 Connection 维度的不透明 `watchKey` 和当前
完整 `AgentDiscoveryResult`。SDK 将该 Key 映射到规范化本地 Watch Key，不解析其内容。

RAD 本身仍然只有六个根消息，不定义协议层 Event Envelope。Nacos gRPC Binding 使用
`AgentDiscoveryNotifyRequest(watchKey, eventType, result?, errorCode?)` 在共享 Payload
Connection 上复用 Watch 事件；该 Request 是 Binding 对象，不是新的 RAD 根消息。

### 6.2 完整替换与 Listener 投递

当 `eventType=SNAPSHOT` 时，`AgentDiscoveryNotifyRequest` 必须携带一个完整
`AgentDiscoveryResult` 且不携带错误。Client 收到后，按标识的 Watch 原子替换上一个
Snapshot，再通过 `AgentDiscoveryNotifyResponse` 发送 ACK。不得合并不同 Snapshot 中的
Calling Interface、EndpointSet 或 Endpoint。解析出的 Version、`contentDigest` 或
`sourceRevision` 变化表示可能存在新 Snapshot；这些 Token 只用于相等比较和去重，
不能用于排序。

过滤后为空是合法的完整结果，并会替换上一个 Snapshot。Naming Push-empty Protection
不适用于 RAD。Listener 异常必须与 Connection 处理和其他 Listener 隔离；它不会把已经
接受的 Snapshot 变成未确认事件。

### 6.3 目标消失

之前可发现的目标变为不存在、不可见、Disabled，或不再存在可发现的 Online 目标 Version
时，Server 发送 `eventType=TERMINATED`、不携带 Result 且
`errorCode=NOT_FOUND` 的 `AgentDiscoveryNotifyRequest`。该事件只关闭标识的
`watchKey`；共享 Payload Connection 和其他 Watch 保持有效。Client 投递终止状态，
只删除对应本地 Watch Key 和缓存 Snapshot，发送 ACK，并且后续 Reconnect 不再 Redo
该 Watch。Agent 仍存在但 Filter 当前不匹配任何 Interface 或 Endpoint 时，仍返回成功的
空 `SNAPSHOT`，不属于终止状态。

### 6.4 Missed Push 与 Reconnect

Connection 丢失、通知被拒绝或 Binding 特定的 Gap Detection 表明可能遗漏 Push 时，
Client 必须使用相同 Namespace、规范化 Reference 和规范化 Filter 重新执行 Discover，
然后原子替换缓存 Snapshot。不得通过应用本地推导出的 Delta 重建遗漏状态。

gRPC Disconnect 时，Server 移除 Connection 维度 Watch State，SDK 将对应本地 Watch
record 标记为未注册。Reconnect 后，SDK 使用新的 connection id 恢复相同的规范化本地
Watch Key，并丢弃各旧 Wire `watchKey`；每次成功重新订阅都返回新的不透明 `watchKey`
和初始完整结果，后者在后续 Push 前成为新的完整 Snapshot。恢复期间收到终止结果时，
按第 6.3 节处理，不继续保留在 Redo State 中。

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
