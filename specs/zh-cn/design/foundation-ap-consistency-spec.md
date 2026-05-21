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

## 6. Config Notify 契约

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

## 7. 失败语义

AP 使用方必须处理部分成功。

规则：

- 本地写成功时，可能尚未被所有 peer 观测；
- retry 可能导致重复操作，因此 apply 逻辑必须幂等，或由 revision、timestamp、operation type、
  当前状态保护；
- 超时不能证明远端操作一定没有发生；
- 远端旧状态必须通过 verify、snapshot、重新查询或领域特定 reload 修复；
- AP 恢复过程必须可以通过日志、指标、trace 或诊断观察；
- AP 失败不得静默地把运行时状态转化为持久元数据。

## 8. 边界规则

- AP 一致性是最终收敛，不是强一致。
- 本地 `NotifyCenter` 事件本身不是 AP 一致性；只有领域定义了远端传播和修复行为时，它才成为
  AP 行为的一部分。
- Distro 是运行时数据的正式共享 AP 框架。Config Notify 是 Config 特定的缓存/listener 可见性
  AP 通知路径。
- AP 路径不得用于权限、namespace 元数据、持久服务元数据、插件状态或数据库 schema 状态。
- 除非接口规范显式暴露，AP payload 是内部集群契约。
- AP 传输必须遵循内部 RPC 的鉴权、来源、payload 和重试规则。

## 9. 相关规范

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
- [gRPC API 规范](../grpc-api/api-spec.md)
