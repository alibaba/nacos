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

## 6. 顺序

Push delivery order 只在某个节点的本地 event 和 task path 内成立。它不是跨集群全局 total order。

领域规范必须定义本地服务视图何时可见：

- Config 写入可见性、dump 顺序和本地缓存可见性由
  [Config 一致性、Dump 与可见性规范](../config/config-consistency-dump-visibility-spec.md)定义。
- Naming 临时服务收敛由
  [Naming 临时服务 Distro 一致性规范](../naming/naming-ephemeral-distro-consistency-spec.md)定义。
- Naming 持久服务和元数据可见性由
  [Naming 持久服务 CP 一致性规范](../naming/naming-persistent-cp-consistency-spec.md)定义。

## 7. 失败规则

- connection 不存在时，应取消或跳过对该 connection 的 push。
- push timeout 不证明客户端没有观察到变化，只表示服务端没有在超时时间内收到成功 ack。
- 客户端必须能够通过 re-query、resync 或 redo 从 missed push 中恢复。
- Server push 不得隐藏底层 query path 的鉴权失败。

## 8. 待处理问题

- AI runtime push 和 reconnect 行为应在 AI SDK subscription API 稳定后进一步细化。
- push retry、timeout 和 reconnect recovery 观测应遵循
  [可观测钩子规范](../design/foundation-observability-hooks-spec.md)中的共享字段和 label 指引。
