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

第 6 节定义 Agent HTTP Client 状态已确定的目标 Distro 契约。它不属于上表的当前实现，也不
表示 `AI_AGENT_HTTP_CLIENT` 已经实现或对外声明。

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

## 6. AI Agent HTTP Client Distro 目标契约

本节定义 HTTP Runtime Endpoint publisher 状态的目标设计。只有服务端完成实现并声明对应
Agent/RAD 能力后，它才成为有效运行时契约。

目标流程为：

```text
Agent HTTP request
  -> route by clientId to responsible server
  -> mutate complete HTTP Client state
  -> Distro full-state CHANGE or DELETE
  -> peer Client state
  -> Naming and RAD runtime events
```

### 6.1 资源身份与路由

| 项目 | 目标语义要求 |
| --- | --- |
| Distro resource type | 常量 `AI_AGENT_HTTP_CLIENT`。 |
| `resourceKey` 和 `responsibleId` | 原始 opaque HTTP `clientId`。 |
| 责任归属 | Distro 使用稳定 client-id 分片选择唯一责任节点。 |
| 模块隔离 | module 不拼入分片 key；resource type 和专用 Client manager 将 Agent 状态与其他模块使用的同名 client id 隔离。 |
| Native owner | 只有责任节点拥有 native HTTP Client、`lastActiveTime` 和超时调度。 |
| 远端入口 | 非 owner 将变更或 heartbeat 路由到责任节点，不创建独立活性计时器。 |

首次 Endpoint 注册成功时，将 Client 绑定到一个鉴权主体和一个 `namespaceId`。状态只保存稳定
主体标识，不保存 credential 或 access token。后续 registration、deregistration 和 heartbeat
必须使用相同鉴权主体；后续包含 namespace 的请求必须使用已绑定 namespace，无 body 的 heartbeat
使用已保存的 namespace binding。不匹配时拒绝请求且不刷新活性。

`clientId` 只是路由和 publisher 归属标识，不是鉴权凭据。在其他 Distro resource type 下复用
相同文本时，不得续约、修改、verify、snapshot 或删除 Agent Client。

### 6.2 完整状态与操作

一个同步 Client datum 是完整替换状态，包含：

| 字段组 | 内容 |
| --- | --- |
| 身份 | `clientId`、绑定的 `namespaceId` 和鉴权主体标识。 |
| 收敛 | 领域 revision 和语义活性状态。 |
| 活性 | `lastActiveTime`、`heartbeatIntervalMillis`、`unhealthyTimeoutMillis` 和 `expireTimeoutMillis`。 |
| Publication | 全部 Agent Endpoint publication group 及其完整 Endpoint contribution。 |

Endpoint group 包含 AgentName、canonical protocol、runtime Version、canonical Version range
和 Endpoint payload。Distro datum 永远不携带局部 Endpoint patch，并排除 credential、请求
header 和原始鉴权材料。

`AI_AGENT_HTTP_CLIENT` 接受以下 operation：

| Operation | 语义 |
| --- | --- |
| `CHANGE` | 幂等创建或替换一个完整 Client state。本地 Endpoint 变化和语义活性转换产生该操作。 |
| `DELETE` | 删除完整 Client 及其全部 publisher contribution；状态不存在时幂等 no-op。 |
| `VERIFY` | 比较 client id、存在性和领域 revision；不匹配时调度定向修复。 |
| `SNAPSHOT` | 传输来源 snapshot 拥有的完整 Client state 集合。 |
| `QUERY` | 按 client id 返回一个完整 Client state，或返回强类型 not-found 结果。 |

创建使用 `CHANGE`；本目标不要求独立 `ADD` 语义。Apply 逻辑拒绝过期 revision，接受重复的
相同状态，并且不得把乱序完整替换中的字段合并。`VERIFY` 和 `QUERY` 使用完整 state 修复，
不得使用 Endpoint delta。

普通 heartbeat 只在责任节点刷新 `lastActiveTime`，不在每个间隔广播。Active/unhealthy 之间的
语义转换、Endpoint 变化或删除会推进领域 revision，并同步最新完整状态。

### 6.3 超时与故障转移

服务端返回并保存满足以下关系的超时值：

```text
heartbeatIntervalMillis < unhealthyTimeoutMillis < expireTimeoutMillis
```

Heartbeat 和成功的 Endpoint 写入会刷新 Client 活性。超过 `unhealthyTimeoutMillis` 后，该
Client 的 contribution 仍保留在 RAD/Naming 投影中，但变为 unhealthy；与其他健康 publisher
共享的 Endpoint 仍可能聚合为 `healthy=true`。Client 转换产生完整状态 `CHANGE`。过期前恢复
活性时 Client 回到 active，并在公开健康投影发生变化时产生 `CHANGE`。超过
`expireTimeoutMillis` 后，责任节点产生 `DELETE` 并移除全部 contribution。注销最后一个
Endpoint 时立即删除空 Client。

责任转移时，新 owner 只有在从本地 replica、`SNAPSHOT` 或 `QUERY` 安装完整 state，并校验其
identity 和 revision 后，才能启动超时调度。随后从接管时刻开始一个与该 Client
`expireTimeoutMillis` 等长的 failover grace window。Grace 期间不得只因为复制的
`lastActiveTime` 过旧就使 Client 过期。合法 heartbeat 会结束 grace 并恢复普通超时计算；到
grace deadline 仍没有 heartbeat 时，Client 过期。

新 owner 无法取得完整 state 时不得合成空 Client。Heartbeat 返回 `HTTP_CLIENT_NOT_FOUND`，
使 SDK 将全部期望 Endpoint group 标记为未注册，并使用同一 client id redo 完整 registration
batch。Registration 可以创建缺失 state；注销缺失 state 仍成功 no-op。

### 6.4 Apply 事件与可见性

Apply 本地变更、远端 `CHANGE`、修复后的 `QUERY` 或 `SNAPSHOT` state 时，必须物化相同的
Agent HTTP Client 和原始 Naming publisher contribution。发生语义变化的 apply 会产生 Naming
Client/service change 事件以重建 index 和 instance 聚合，同时产生 RAD runtime projection/watch
事件以刷新 Endpoint snapshot 和 `sourceRevision`。

Apply `DELETE` 会产生对应删除事件。相同语义 revision 的重复 state 不产生重复领域变更。
`VERIFY` 本身不修改领域状态或产生 discovery 事件。远端 apply 遵循内部 RPC 的鉴权和来源校验；
它会恢复已保存的鉴权主体 binding，但永远不把 `clientId` 当作权限凭据。

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
- Distro 是运行时数据的正式共享 AP 框架。Naming 当前使用它同步临时 Client state；
  `AI_AGENT_HTTP_CLIENT` 只在目标能力实现后加入该框架。Config Notify 是 Config 特定的
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
