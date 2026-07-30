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

# Nacos AP 一致性规范

本文定义 Nacos 使用的 AP 向一致性基础能力。它是
[基础能力规范](foundation-capabilities-spec.md)中 AP 一致性部分的展开。

## 1. 定位

AP 和 CP 是 CAP 理论中的一致性选择。在 Nacos 中，AP 路径优先保证可用性和分区容忍，用于可以
最终收敛的状态。它本身不提供全局强顺序写日志、线性一致读，或持久管理面归属。

当前 AP 风格实现包括：

| 实现 | 主要归属 | 用途 |
| --- | --- | --- |
| Distro | Naming 运行时状态 | 在服务端节点之间同步临时、客户端拥有的服务实例状态。 |
| Config Notify | Config 缓存与 listener 可见性 | 通知 peer 节点某个 Config 资源发生变化，使本地 dump 缓存和 listener 刷新。 |

第 6 节定义通用 HTTP connection-based Client 的目标契约。它复用 Naming 当前的 ClientData
Distro 资源类型和处理器；在对应 Client manager 完成并由上层 API 声明能力前，不表示该
Client 类型已经实现或可用。

历史上 `consistency` 模块中存在 `APProtocol` 接口，但当前活跃 AP 实现是 Distro 基础能力和
Config Notify 路径。新的规范应直接描述 AP 语义，而不应假设所有 AP 行为都必须实现 `APProtocol`。

## 2. AP 资源规则

AP 状态适用于具备以下特征的资源：

- 由存活客户端或本地观测拥有的运行时状态；
- 更新频率高，全局串行化代价过高；
- 可以重建、刷新或丢弃；
- 正确性允许最终收敛和重试；
- 失败处理可以通过 verify、snapshot、reload 或重新查询完成。

AP 状态不适合持久管理元数据、运维覆盖、schema 状态、长期权限，或要求单一全局提交顺序的资源。
这些资源应使用[CP 一致性规范](foundation-cp-consistency-spec.md)或
[持久化与 Dump 规范](foundation-persistence-dump-spec.md)。
AP 路径使用的 delayed task、execute task 和本地事件规则由
[任务执行规范](foundation-task-execution-spec.md)和
[事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)定义。

AP 使用方必须定义：

- 资源身份和 resource type；
- 生产状态的 owner 或责任规则；
- 数据操作集合和幂等预期；
- 收敛窗口和重试策略；
- verify 与修复行为；
- 启动加载和 snapshot 行为；
- 本地 apply 后发布的事件；
- 删除数据是否需要 tombstone、过期或替换语义。

## 3. Distro 模型

Distro 是 `core.distributed.distro` 下的共享 AP 同步框架。

Distro 模型如下：

```text
DistroKey(resourceKey, resourceType, targetServer)
  -> DistroData(type, content)
  -> DistroDelayTask / DistroExecuteTask
  -> DistroTransportAgent
  -> DistroDataRequest
  -> DistroDataProcessor
  -> local state and events
```

Distro 组件：

| 组件 | 职责 |
| --- | --- |
| `DistroKey` | 通过 resource key、resource type 和可选 target server 标识一个 AP datum。 |
| `DistroData` | 承载序列化后的 datum 内容和 `DataOperation`。 |
| `DistroDataStorage` | 产出单个数据、verify data 和完整 snapshot。 |
| `DistroDataProcessor` | apply 接收到的数据、verify data 和 snapshot data。 |
| `DistroTransportAgent` | 向 peer 节点发送 sync、verify、query 和 snapshot 请求。 |
| `DistroFailedTaskHandler` | 将失败的 sync 或 verify 操作转换为重试任务。 |
| `DistroTaskEngineHolder` | 管理 sync、verify、load 和 retry 的延迟任务与执行任务。 |

Distro 操作使用 `ADD`、`CHANGE`、`DELETE`、`VERIFY`、`SNAPSHOT`、`QUERY` 等
`DataOperation`。领域 processor 必须定义自身 resource type 支持哪些操作。

## 4. Distro 生命周期

非单机模式下，Distro 会启动 load 和 verify 任务。

生命周期规则：

- 启动加载会从 peer 节点获取 snapshot，并交给领域 processor apply；
- snapshot apply 成功后，对应 data storage 才能标记为已初始化；
- verify 任务不应在对应 data storage 初始化完成前运行；
- verify data 比较 revision、checksum 等紧凑状态，并在目标节点发现不一致时触发修复；
- change 和 delete 任务按 key 延迟执行，并在 task engine 支持时进行合并；
- sync 或 verify 失败必须由领域 failed-task handler 重试，或带诊断信息地显式丢弃。

单机模式不得伪造远端 AP 收敛。它应将 AP 运行时状态标记为本地初始化，并跳过远端同步。

## 5. Naming Distro 契约

Naming 使用 Distro 同步临时 client state。

规则：

- 只有当前节点负责的临时 client 会通过 Distro 同步；
- 持久 client 和元数据必须使用 CP 或持久化路径；
- client change 产生 Distro `CHANGE`，disconnect 产生 `DELETE`，verify 失败可以触发定向 `ADD`；
- Distro sync data 包含 client id、attributes、已发布服务、实例发布信息和批量实例数据；
- apply Distro data 必须更新服务端 Client state，并发布 Naming 事件，使派生索引和推送视图可以重建；
- verify 使用 client id 和 revision，并可以从源节点调度修复；
- snapshot 包含当前临时 client sync data 集合。

Naming Distro 传输通过
`DistroDataRequest` / `DistroDataResponse` 承载，并遵循
[内部 RPC 与集群请求规范](foundation-internal-rpc-spec.md)。

## 6. 通用 HTTP Connection-based Client 目标契约

本节定义由多个 HTTP 请求共同维护的临时 Naming Client。Agent Endpoint、普通 Naming
Instance 和未来其他运行时 Endpoint 在进入 Client 前分别完成领域适配；Client manager 和
Distro 只处理标准 `InstancePublishInfo` 或 `BatchInstancePublishInfo`。

目标流程为：

```text
HTTP request
  -> module-owned Distro Filter routes by internal client id
  -> mutate HttpConnectionBasedClient
  -> existing Naming ClientData CHANGE or DELETE
  -> peer Client state
  -> Naming indexes and runtime projections
```

### 6.1 资源身份与路由

| 项目 | 目标语义要求 |
| --- | --- |
| 外部身份 | 调用方提供的 opaque `externalClientId`。 |
| 内部 Client id | `HTTP_CLIENT@@<externalClientId>`。 |
| Distro resource type | 复用 `Nacos:Naming:v2:ClientData`。 |
| `resourceKey` 和 `responsibleId` | 完整内部 Client id。 |
| Client manager | `HttpConnectionBasedClientManager`，与 `ConnectionBasedClientManager` 同级并由 `ClientManagerDelegate` 路由。 |
| 责任归属 | Distro 根据稳定内部 Client id 选择唯一责任节点。 |
| 模块复用 | AI、Naming 或其他模块使用相同 external id 时共享同一个 Client、publisher/subscriber 容器和生命周期。 |
| 远端入口 | 每个模块使用自己的 HTTP Distro Filter 将有状态请求转发到责任节点；AI 不扩展 Naming 模块现有的 Distro Filter。 |

首次创建有状态 Client 时绑定一个鉴权主体和一个 `namespaceId`。状态只保存稳定主体标识，
不保存 credential 或 access token。后续有状态请求必须使用相同主体和 namespace；不匹配时
拒绝请求且不刷新任何活性时间。Client id 只是路由和状态归属标识，不是鉴权凭据。

### 6.2 ClientData 与操作

通用 HTTP Client 直接复用 Naming `ClientSyncData`、`DistroClientDataProcessor` 及其
`ADD/CHANGE/DELETE/VERIFY/SNAPSHOT/QUERY` 语义，不注册新的 Distro resource type 或
processor。同步数据包含标准 Client identity、attributes、全部 publication 和 revision；
HTTP Client attributes 额外保存 namespace、鉴权主体标识、Client 活性和 Publisher 活性。

Publisher 数据仍使用现有 Client 的完整 service publication：

- 单实例使用 `InstancePublishInfo`；
- 完整批次使用 `BatchInstancePublishInfo`；
- Agent 或其他领域 Adapter 不把领域 DTO 放入 Distro payload；
- Subscriber 与现有 connection-based Client 一致，只保留在实际承载订阅的一侧，不进入
  ClientData publication snapshot。

Publication 变化或语义健康状态变化推进 Client revision 并产生现有
`ClientChangedEvent`。普通 Client 续约和 Publisher heartbeat 不因时间戳本身变化而广播
ClientData；peer 通过已有 verify、snapshot 和 repair 收敛。

### 6.3 Client 与 Publisher 分层活性

HTTP Client 分别维护：

| 活性 | 刷新来源 | 影响 |
| --- | --- | --- |
| Client 活性 | 合法查询、订阅变更、publication 写入和显式 Publisher heartbeat。 | 决定 Client 及 subscriber state 是否仍存在。 |
| Publisher 活性 | publication 写入和显式 Publisher heartbeat。 | 决定该 Client 拥有的 publication 是否健康和保留。 |

查询只续约已存在的 Client，不创建空 Client，不修改 publisher payload、revision 或健康状态。
因此频繁查询可以维持 Client 或 subscriber state，但不能使已超时的 publication 恢复健康，
也不能阻止 publisher expiry。显式 Publisher heartbeat 同时续约 Client 和该 Client 的全部
publication。

Publisher timeout 满足：

```text
heartbeatIntervalMillis < unhealthyTimeoutMillis < expireTimeoutMillis
```

超过 `unhealthyTimeoutMillis` 时 publication 保留但转为 unhealthy；恢复 Publisher 活性时
恢复为 active，并仅在公开健康投影变化时产生 `CHANGE`。超过 `expireTimeoutMillis` 时删除
该 Client 的全部 publication，但如果 Client 仍有 subscriber state 则保留 Client。Client
自身过期时释放其全部 publisher 和 subscriber state。

只有责任节点执行 native Client 和 Publisher 超时调度。ClientData replica 通过现有 snapshot、
verify 和 repair 流程收敛。Replica 最近一次成功 verify 的时间参与其后成为责任节点时的本地
超时计算，因此 Client 不需要维护第二个 ownership 标记或单独同步 failover 状态。普通 heartbeat
不在每个间隔广播。

### 6.4 Apply 事件与可见性

本地 publication 变更、远端 `ADD/CHANGE`、repair 或 snapshot apply 必须沿用 Naming
Client/service 事件，重建 publisher index、service storage 和 push view。`DELETE` 使用现有
Client release 路径清理派生状态。`VERIFY` 本身不产生 discovery 变化。

上层 Agent/RAD 投影只消费 Naming `ServiceStorage` 结果；HTTP Client manager 不维护第二份
Agent Endpoint projection，也不直接依赖 Agent 定义或 AI Resource 状态。

## 7. Config Notify 契约

Config Notify 是 AP 风格的变更传播路径。它不是持久存储协议，也不承载权威配置内容。

模型如下：

```text
Config write or delete
  -> ConfigDataChangeEvent
     -> local DumpService refresh
     -> AsyncNotifyService fan-out to peers
        -> ConfigChangeClusterSyncRequest
        -> peer DumpService refresh
        -> LocalDataChangeEvent
        -> client listener push
```

规则：

- 权威数据源仍是 Config 持久化层；
- notify request 只携带配置身份、`lastModified`、gray name 和兼容字段，不携带完整权威内容；
- 接收节点必须按照 Config 规则从持久化层刷新本地 dump/cache；
- 本地缓存变化后，通过 `LocalDataChangeEvent` 触发 listener 和 watch 通知；
- 不健康目标 member 应延迟重试，而不是阻塞写路径；
- callback 失败或超时必须按受控 backoff 调度重试；
- peer 已移除时，待处理 notify task 可以成为 no-op。

对于 Config，AP notify 成功表示 peer 节点已被通知刷新服务状态。它不替代持久化成功，也不使推送
payload 成为权威内容。

## 8. 失败语义

AP 使用方必须处理部分成功。

规则：

- 本地写成功时，可能尚未被所有 peer 观测；
- retry 可能导致重复操作，因此 apply 逻辑必须幂等，或由 revision、timestamp、operation type、
  当前状态保护；
- 超时不能证明远端操作一定没有发生；
- 远端旧状态必须通过 verify、snapshot、重新查询或领域特定 reload 修复；
- AP 恢复过程必须可以通过日志、指标、trace 或诊断观察；
- AP 失败不得静默地把运行时状态转化为持久元数据。

## 9. 边界规则

- AP 一致性是最终收敛，不是强一致。
- 本地 `NotifyCenter` 事件本身不是 AP 一致性；只有领域定义了远端传播和修复行为时，它才成为
  AP 行为的一部分。
- Distro 是运行时数据的正式共享 AP 框架。Naming 使用它同步包括通用 HTTP
  connection-based Client 在内的临时 Client state。Config Notify 是 Config 特定的
  cache/listener 可见性 AP 通知路径。
- AP 路径不得用于权限、namespace 元数据、持久服务元数据、插件状态或数据库 schema 状态。
- 除非接口规范显式暴露，AP payload 是内部集群契约。
- AP 传输必须遵循内部 RPC 的鉴权、来源、payload 和重试规则。

## 10. 相关规范

- [基础能力规范](foundation-capabilities-spec.md)
- [内部 RPC 与集群请求规范](foundation-internal-rpc-spec.md)
- [远程连接生命周期规范](foundation-remote-connection-spec.md)
- [集群成员规范](foundation-cluster-membership-spec.md)
- [CP 一致性规范](foundation-cp-consistency-spec.md)
- [持久化与 Dump 规范](foundation-persistence-dump-spec.md)
- [任务执行规范](foundation-task-execution-spec.md)
- [事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)
- [Config 规范](../config/config-spec.md)
- [Config 监听与订阅规范](../config/config-listener-watch-spec.md)
- [Naming 一致性与客户端状态规范](../naming/naming-consistency-client-spec.md)
- [Agent 存储规范](../ai/agent-storage-spec.md)
- [RAD 协议规范](../ai/rad-protocol-spec.md)
- [gRPC API 规范](../grpc-api/api-spec.md)
