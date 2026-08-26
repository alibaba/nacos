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

# Nacos 客户端本地缓存与 Redo 规范

本文定义客户端侧本地缓存、本地 failover、listener recovery、subscription recovery 和
redo 行为。本文展开[客户端运行时规范](client-runtime-spec.md)中的恢复部分。

## 1. 本地数据分类

客户端运行时使用多类本地数据：

| 数据类型 | 来源 | 目的 | 权威性 |
|----------|------|------|--------|
| Config failover file | 用户维护的本地文件 | 已知 Config item 的紧急覆盖。 | 最高本地读取优先级，但不会自动写回服务端。 |
| Config snapshot | 服务端查询响应 | 用于读取 fallback 的最后已知 Config content 和 encrypted data key。 | 仅恢复缓存。 |
| Config listener state | SDK listener 注册 | 跟踪已知 group key、listener MD5 和 fuzzy watch 状态。 | 仅运行时意图。 |
| Naming service-info cache | 服务端 push 或 query response | 订阅或查询服务的最后已知实例。 | 仅恢复缓存。 |
| Naming failover data | 用户或扩展提供的本地 failover source | failover switch 开启时覆盖 discovery view。 | 仅本地 discovery override。 |
| Redo data | SDK register、subscribe 或 endpoint 操作 | reconnect 后恢复运行时意图。 | 仅运行时意图。 |
| RAD 发现与 Watch 状态（目标） | Discover 结果或 Watch 注册 | 最后一个完整 Agent 发现快照和 Watch 意图。 | 仅恢复缓存和运行时意图。 |

除非领域规范显式说明，本地数据不得被视为服务端已提交状态。

## 2. Config 本地恢复

Config 读取优先级为：

1. 用户维护的本地 failover 文件；
2. 服务端查询；
3. 本地 snapshot。

failover 文件不会由客户端自动创建。它用于紧急场景：Nacos server 不可用或远端变更不安全时，
应用仍需要通过本地覆盖启动或继续运行。

snapshot 在服务端查询成功后写入，并在服务端确认 Config item 不存在时删除。Encrypted data key
snapshot 与 content snapshot 分开存储。Config filter，包括 encryption filter，会在选定本地或
远端 content 后执行。

Config listener 发送 listener check 前必须检查本地 failover 文件。当 failover 文件出现、变化
或消失时，必须更新 listener state，并可按 `CacheData` MD5 规则触发 listener callback。

## 3. Config Listener 与 Fuzzy Watch 恢复

Config gRPC client 注册 Config change notification、client metrics request 和 fuzzy watch
notification handler。连接建立时，客户端必须通知 listen context 和 fuzzy watch context，使已知
订阅重新同步。连接断开时，必须标记受影响的 `CacheData` entry 和 fuzzy watch context 与服务端
不一致。

Config listener recovery 不是写操作 redo，而是读/监听运行时意图的 resync。

## 4. Naming 本地缓存

Naming service-info cache 按 grouped service name 和 clusters 存储 `ServiceInfo`。服务端 push 或
query response 会更新内存 map，并在实例视图变化时写入磁盘缓存。

该缓存是恢复辅助：

- load-cache 选项开启时，可在启动时加载；
- 网络中断时，可提供临时 discovery view；
- 不得创建、更新或删除 Naming 服务端资源。

Push-empty protection 可以忽略空或无效 push，避免把已知可用视图意外替换为空视图。

## 5. Naming Failover 视图

Naming failover 是本地 discovery override。当 failover switch 开启且某服务存在有效 failover
data 时，SDK 可以返回 failover view，而不是正常的 server-driven view。

failover switch 或 failover data 变化导致可见实例集合变化时，应发布 instance-change event。
failover 关闭后，SDK 返回正常缓存的服务端视图；如果可见视图变化，也应通知 listener。

Naming failover 不得被用作服务端数据修复机制。

## 6. Redo 模型

Redo 用于连接丢失并重新建立后恢复运行时意图。Redo data 记录：

- 期望最终状态，例如 registered 或 unregistered；
- 数据是否已经在上一个 connection 上成功注册；
- 是否正在执行 unregister；
- 重放操作所需的领域 payload。

Redo operation 包括：

- 再次 register；
- 再次 unregister；
- 移除过期 redo data；
- 当当前运行时意图已经满足时不执行操作。

Redo task 仅能在运行时连接已连接时执行。连接断开时，已注册 redo data 必须标记为未注册，使下一次
connected period 可以修复服务端挂载状态。

## 7. 领域 Redo 规则

Naming redo 覆盖：

- 临时实例注册；
- 批量临时实例注册；
- 服务订阅；
- fuzzy watch 一致性状态。

持久 Naming service 状态由服务端持有，除非领域明确把某操作视为运行时意图，否则不应由客户端
redo 恢复。

AI redo 覆盖运行时 endpoint 和 subscription intent，例如 MCP 或 Agent Endpoint 注册。AI resource
publish/delete 语义仍由 [AI Registry 规范](../ai/ai-registry-spec.md)约束。目标 Agent/RAD
规则在第 8 节定义。

Config listener 通过 listener resync 和 fuzzy watch resync 恢复。Client SDK 不会自动 redo Config
publish/delete 操作。

## 8. Agent 与 RAD 目标恢复契约

本节定义新 Agent/RAD SDK 的恢复契约。gRPC 路径在
[Agent API 规范](../ai/agent-api-spec.md)中的 Agent/RAD 能力完成协商后生效；HTTP
路径使用同一份本地期望状态，但不依赖 gRPC ability。

### 8.1 Endpoint 发布 Redo 身份

SDK 按 Publication 身份维护期望 Endpoint 发布状态，并保存重放所需的完整 Batch
Payload。Redo key 固定为：

```text
(namespaceId, agentName, protocol)
```

每个 key 只保存一份完整 `AgentEndpointRegistrationBatch`。Register 先复制并校验
全部 Endpoint，再以提交的完整 Batch 原子替换旧记录；它不合并 Endpoint upsert。
`runtimeVersion`、`versionRange` 和全部 Endpoint payload 都属于该记录内容，后一次
Register 可以完整更换它们。

Deregister 按 Endpoint 自然键从这份期望 Batch 中删除成员。仍有 Endpoint 时，SDK
通过 Register 发送完整剩余 Batch；没有 Endpoint 时发送整份 Publication 注销并清除
成功完成的期望记录。Redo Payload 必须保留 URI、Priority、Weight 和 Metadata 等完整
公开值。

### 8.2 HTTP 与 gRPC Publisher 恢复

HTTP Agent Publisher 为一个 SDK 实例生成一个 `X-Nacos-Client-Id`。在该 SDK 实例
生命周期内，这个 Id 在请求重试、Server 切换、故障转移、Heartbeat 和 Redo 时保持稳定；
进程重启后生成新 Id。

任一 Agent Endpoint 请求返回 `HTTP_CLIENT_NOT_FOUND` 时，SDK 将该 HTTP Client
拥有的全部 Endpoint redo record 标记为未注册，并 redo 每个完整期望 Publication 分组。
只重试失败 Endpoint 不充分，因为 Server 已声明整个 HTTP Client 状态不存在。

gRPC Endpoint 意图归属于当前 connection id。Reconnect 后，SDK 获取新的 connection id，
把旧 Connection 的全部 Endpoint redo record 标记为未注册，并在新 Connection 下重放完整
期望分组。HTTP 与 gRPC Publisher record 必须隔离；一种 Transport 不得注销另一种
Transport 拥有的 Contribution。

当 Agent Transport 为 `AUTO` 时，Publication 首次准备发送前选择并缓存
`ownerTransport`。同一 `(namespaceId, agentName, protocol)` 后续完整 Batch 替换、部分
注销、整份注销、HTTP Heartbeat 和 Redo 均使用该 owner。Client 可同时持有由 HTTP 和
gRPC 分别拥有的不同 Publication，但不得因为连接状态变化迁移一个已存在 Publication 的
owner，也不得让 HTTP maintenance 处理 gRPC-owned record。

SDK 默认对全部已保留完整 Publication Batch 中的 Runtime Endpoint 条目使用 100 的软水位，
可通过 `nacosAiAgentEndpointMaxPublications` 配置。一次原子 Register 前的条目数低于
水位时，SDK 整批保留已校验 Batch，即使完成后越过水位；已达到或超过水位时仍允许等量
替换或缩容，但拒绝新 Batch 或扩容 Batch，且不发生局部缓存修改。Server 独立执行权威的
每 Client 软水位。命中本地或远程 Publication 容量限制属于终止性写失败：SDK 从
Publication Manager 与 gRPC Redo Cache 移除被拒绝的身份，HTTP Maintenance 不再
Heartbeat 或重试它；其他瞬时 5xx Transport 失败继续使用既有 Rollback 和 Redo 语义。

### 8.3 本地 Watch Manager 与 Wire Intent

Canonical Local Watch Key 包含：

```text
(namespaceId, canonicalAgentReference, canonicalFilter, listenerIdentity)
```

Reference 规范化保留 Exact Version、Label、显式 Latest 和未指定 Version 的语义。
Filter Collection 和 Map 参与 Canonical Value Equality。Listener Identity 是取消时使用的
同一实例。一个 Record 拥有 Request 防御性副本、Listener、最后完整 Result、Canonical
Fingerprint、Availability State、所选 Wire Transport、Wire Generation/Key、Dirty/Refresh
State 和有界 Retry State。Transport Adapter 不得拥有另一份 Listener 或 Result Cache。

本地 Watch Manager 默认最多保留 300 个不同的 Canonical Subscription Key，可通过
`nacosAiAgentDiscoveryMaxSubscriptions` 配置。超限订阅必须在首次 Discover、缓存插入和
Wire 调度前失败；重复 Subscribe 保持幂等，Unsubscribe 或 Shutdown 释放容量。被本地或
Server 容量拒绝的注册完整回滚且不进入 Retry。任何 Batch Mutation 都使用操作前软水位，
在水位以下整批保留规范化结果，或在已达到水位时无局部插入地拒绝增长。

Wire Intent 从本地 Intent 派生，不能取代它：

- gRPC State 保存当前 Connection 范围的 `watchKey`；Disconnect 清除该 Key，并把 Record
  标记为在新 Connection 下重新订阅；
- HTTP State 把 Request 与 Fingerprint 加入下一轮完整 List Batch Generation，不保存持久
  Server Key；
- 轮询回退调度有界周期 Discover，不改变 Canonical Identity；
- 已接受 Hint 只标记 Refresh Dirty。一个串行化的 Current-fact Discover 计算完整
  Fingerprint、原子更新 Cache，并在 Transport I/O 外投递 Listener Event；
- Unsubscribe 在 Best-effort Wire Cleanup 前先删除 Listener 和 Local Intent，因此迟到的
  gRPC Hint 与 HTTP Response 不能调用它。

[运行时推送与重连规范](runtime-push-reconnect-spec.md)定义对应 Transport Recovery 和
面向最终 Projection 的 Push 纪律。

### 8.4 旧 A2A 兼容恢复

namespace-bound `A2aService` 的旧 Version-specific Endpoint Publication 使用
`(agentName, exactVersion)` 作为本地 redo 身份；不同精确 Version 不得相互覆盖。Redo record
保存 Endpoint 集合及其 URI、transport、metadata 等字段的防御性快照，调用方后续修改原始
`AgentEndpoint` 或 Collection 不得改变重连意图。

旧 AgentCard 订阅的 exact Version 和 latest 是不同本地身份。服务端返回的 Version 当前是否为
latest 不能替代调用方订阅身份；一次变化必须通知所有受影响的 exact/latest key。当 latest 指向已
缓存的精确 Version 时仍需产生 latest 变化。取消后使用已有 Cache 重新订阅必须重新启动轮询任务。
SDK shutdown 必须停止旧 AgentCard Cache Holder 的全部轮询。

## 9. Shutdown

SDK shutdown 必须清理内存 redo state、停止后台 retry task、关闭 transport client，并停止本地
cache/failover refresh task。除非用户显式调用缓存清理操作，shutdown 不应删除用户维护的 failover
文件或服务端派生 snapshot。

Agent Shutdown 还要取消 HTTP Batch Long Poll、Best-effort Unsubscribe 当前 gRPC Wire Key、
拒绝迟到 Generation、停止轮询回退，并在禁止新 Callback 后关闭 Listener Executor。

## 10. 待处理问题

- Naming redo 当前仍使用独立实现，较新的 AI redo 使用通用 redo 抽象。后续实现应收敛到共享 redo
  模型。
- Config listener recovery、Naming redo、AI redo 和
  [运行时推送与重连规范](runtime-push-reconnect-spec.md)定义的 runtime push recovery 应共享可观测字段。
- 多语言 SDK 应说明自己支持哪些本地缓存和 redo 行为，以及哪些行为有意与 Java 不同。
