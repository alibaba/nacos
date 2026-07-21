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

本节定义新 Agent/RAD SDK 的目标恢复契约，不表示当前已经实现该能力。只有
[Agent API 规范](../ai/agent-api-spec.md)中的 Agent/RAD 能力完成实现并经过协商后，
本节才成为生效契约。

### 8.1 Endpoint 发布 Redo 身份

SDK 按规范化 Publication 分组维护期望 Endpoint 发布状态，并保存重放所需的完整 Batch
Payload。物化后的注册 redo key 包含：

```text
(namespaceId, agentName, protocol, runtimeVersion,
 canonicalVersionRange, sortedCanonicalEndpointNaturalKeys)
```

构造 Key 前，缺失的 `versionRange` 规范化为 `[runtimeVersion]`。每个 Endpoint Key
按 [RAD 协议规范](../ai/rad-protocol-spec.md)进行规范化，集合去重后按稳定 ASCII 顺序
排序。因此 URI 输入顺序、Map 顺序和省略的缺省值不会产生不同 redo 身份。

SDK 为每个
`(namespaceId, agentName, protocol, runtimeVersion, canonicalVersionRange)`
分组维护一个规范化 Endpoint Map。Register 把提交的 Endpoint Upsert 合并到该 Map，
并原子替换物化后的 redo record。Deregister 按 RAD 跨 Binding 注销语义，从相同
namespace、Agent 和 protocol 的全部本地分组中移除每个已提交自然键。Redo Payload
保留完整 URI、Priority、Weight 和 Metadata；不能因为这些非身份值不进入 redo key
就丢弃它们。

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

### 8.3 Watch 恢复身份

规范化本地 Watch Key 包含：

```text
(namespaceId, canonicalAgentReference, canonicalFilter, listenerIdentity)
```

Reference 规范化保持精确 Version、Label 和 Latest 之间的区别。Filter 规范化应用缺省值，
并对集合字段排序和去重。Listener identity 是取消 Watch 时使用的同一 Listener 实例。

SDK 将 `AgentSubscribeResponse` 返回的 Connection 维度不透明 `watchKey` 与该规范化本地
身份一同保存。收到的 `SNAPSHOT` 和 `TERMINATED` 通知只使用 Wire Key 定位记录；SDK
不解析该 Key，也不把它作为 Redo 身份。

gRPC Disconnect 时，SDK 将每条 Watch record 标记为未注册。新 Connection 建立后，
它恢复相同的规范化本地 Watch Key，丢弃各旧 Wire Key，并保存随初始完整结果返回的新
`watchKey`。携带 `errorCode=NOT_FOUND` 的 `TERMINATED` 通知只删除所标识的 Watch
record 及其缓存快照，因此后续 Reconnect 不再重试。完整替换、ACK、Missed Push 和终止
行为由[运行时推送与重连规范](runtime-push-reconnect-spec.md)定义。

## 9. Shutdown

SDK shutdown 必须清理内存 redo state、停止后台 retry task、关闭 transport client，并停止本地
cache/failover refresh task。除非用户显式调用缓存清理操作，shutdown 不应删除用户维护的 failover
文件或服务端派生 snapshot。

## 10. 待处理问题

- Naming redo 当前仍使用独立实现，较新的 AI redo 使用通用 redo 抽象。后续实现应收敛到共享 redo
  模型。
- Config listener recovery、Naming redo、AI redo 和
  [运行时推送与重连规范](runtime-push-reconnect-spec.md)定义的 runtime push recovery 应共享可观测字段。
- 多语言 SDK 应说明自己支持哪些本地缓存和 redo 行为，以及哪些行为有意与 Java 不同。
