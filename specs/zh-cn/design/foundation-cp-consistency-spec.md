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

# Nacos CP 一致性规范

本文定义 Nacos 使用的 CP 向一致性基础能力。它是
[基础能力规范](foundation-capabilities-spec.md)中 CP 一致性部分的展开。

## 1. 定位

AP 和 CP 是 CAP 理论中的一致性选择。在 Nacos 中，CP 路径在分区容忍前提下优先保证强顺序提交
状态。当 quorum、leader 或协议 group 不可用时，CP 操作可以失败或暂时不可用，而不是接受分叉写入。

当前内置 CP 实现是通过 `JRaftProtocol` 接入的 JRaft。Nacos 可以通过 SPI 加载 `CPProtocol`
实现，但仓库内支持的 CP 语义由 Raft/JRaft 定义。

## 2. CP 资源规则

CP 状态适用于具备以下特征的资源：

- 由管理面或服务端控制路径拥有的持久状态；
- 需要在客户端消失和服务端重启后继续存在；
- 代表运维人员或开发人员意图，并需要覆盖运行时注册；
- 在一个逻辑 group 内要求单一提交顺序；
- 可以从 snapshot 和已提交日志恢复。

如果高频可丢弃运行时状态只需要 AP 最终收敛，不应使用 CP。这类资源应使用
[AP 一致性规范](foundation-ap-consistency-spec.md)。

CP 使用方必须定义：

- Raft group name 和归属；
- write request 形态和 operation 值；
- read request 形态和读取可见性；
- 确定性的 `onApply` 行为；
- snapshot 保存/加载形态和兼容性；
- 用户可见操作对 leader 或 readiness 的要求；
- group 无 leader、无 processor 或无 quorum 时的错误行为。

## 3. 协议模型

共享一致性接口是 `ConsistencyProtocol`。CP 通过 `CPProtocol` 对其进行特化。

CP 模型如下：

```text
ProtocolManager
  -> CPProtocol(JRaftProtocol)
  -> RequestProcessor4CP per group
  -> JRaftServer multi-raft group
  -> NacosStateMachine
  -> processor.onApply / processor.onRequest
  -> snapshot operations
```

`ProtocolManager` 以 `ip:raftPort` 形式注入 CP member。Member 变化事件会异步传播给 CP 协议。

## 4. Request Processor 契约

每个 CP 领域通过注册 `RequestProcessor4CP` 接入。

Processor 规则：

- `group()` 必须返回稳定且唯一的 group name；
- 一个 group 在同一个 server runtime 中应只有一个权威 processor；
- `onApply(WriteRequest)` 必须是确定性的，不得依赖慢速远端 IO；
- `onRequest(ReadRequest)` 必须按照该 group 的读规则返回响应；
- group 需要 snapshot 恢复时，`loadSnapshotOperate()` 必须声明 snapshot operation；
- processor 必须将未知 operation 作为显式失败处理；
- processor 只能在 committed apply 更新本地状态之后，按
  [事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)发布领域事件。

Processor 拥有领域语义。CP 基础层拥有 group 路由、leader 转发、日志提交、read-index 处理、
metadata 和 snapshot 集成。

## 5. JRaft 运行时规则

JRaft 是当前使用的多 group CP 运行时。

规则：

- 每个 `RequestProcessor4CP` 创建一个 Raft group；
- 写请求提交到 group leader，或被转发到当前 leader；
- 已提交日志通过 `NacosStateMachine` apply；
- follower replay 会 apply 已提交写入，但会忽略没有 closure 的 follower-local read entry；
- 读路径优先尝试 Raft read-index，失败时可以回退到 leader read；
- `protocolMetaData()` 记录 leader、term、group members 和错误；
- `isReady()` 表示协议已启动；strict mode 下还要求 group 已存在 leader；
- member 移除通过 Raft peer-change command 处理；新增节点启动时会将自身注册到集群。

实现中的超时是运维默认值，不是公开 API 保证。除非领域 API 明确声明，领域规范不得把 JRaft
超时值暴露为用户可见正确性契约。

## 6. 当前 CP 使用方

| Group | 归属 | 用途 |
| --- | --- | --- |
| `nacos_config` | Persistence 与 Config 内置存储 | 复制内置存储操作，并使 Config dump 等待可读的 committed state。 |
| `naming_persistent_service_v2` | Naming | 复制持久实例注册、更新和注销操作。 |
| `naming_service_metadata` | Naming | 复制 service metadata 和 cluster metadata。 |
| `naming_instance_metadata` | Naming | 复制运维态 instance metadata。 |
| `naming_persistent_service` | Naming 运维 | 复制遗留 naming switch/domain 状态。 |
| `plugin_state` | Core plugin | 复制插件状态变化。 |

新的 CP 使用方必须把 group 加入相关领域规范，并定义该 group 提交的资源语义。

## 7. 读写语义

写规则：

- write request 必须包含 group、operation 和序列化数据；
- 成功表示写入已按该 group processor 契约被接受并 apply；
- 失败必须向调用方暴露，不能静默重试后仍作为用户可见成功返回；
- 当调用层可能重复提交时，领域 apply 逻辑必须幂等或受保护；
- 写入不得在 committed apply 前发布事件。

读规则：

- read request 必须包含目标 group 和序列化查询数据；
- group processor 定义是否支持读取以及读取哪类状态；
- read-index 失败时可以回退到 leader read；
- 如果领域绕过 CP 从本地 cache 读取，必须在领域规范中记录 stale-read 容忍度。

## 8. Snapshot 与恢复

Snapshot 规则：

- 具有可恢复状态的 group 应提供 `SnapshotOperation`；
- snapshot 格式和兼容性由领域 processor 拥有；
- snapshot save/load 必须保留重启后重建服务状态所需的数据；
- 没有 snapshot operation 的 group 依赖日志 replay 或独立持久化；
- 领域规范必须定义 snapshot 恢复如何与本地 cache 和派生索引交互。

对于 Config 内置存储，启动阶段会等待 CP metadata 表明数据可读后继续 dump 恢复。这是建立在
CP 基础能力和[持久化与 Dump 规范](foundation-persistence-dump-spec.md)上的 Config 持久化规则。

## 9. 边界规则

- CP 一致性是在 group 内的强顺序提交，不保证每个接口都通过 CP 读取。
- CP group 拥有 committed 领域状态，不拥有传输重试、SDK redo 或 AP 运行时状态。
- 除非领域规范明确要求 CP 语义，不应使用 JRaft group 串行化高频临时状态。
- CP processor 不得重新定义公开 API 字段、资源身份或鉴权规则。
- CP metadata 是运维协议状态。它可以通过授权的 ops API 暴露，但不是领域资源身份。
- 替换 CP 实现时，必须保持本文定义的 `CPProtocol`、`RequestProcessor4CP`、group、snapshot、
  metadata 和错误语义。

## 10. 相关规范

- [基础能力规范](foundation-capabilities-spec.md)
- [AP 一致性规范](foundation-ap-consistency-spec.md)
- [持久化与 Dump 规范](foundation-persistence-dump-spec.md)
- [事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)
- [集群成员规范](foundation-cluster-membership-spec.md)
- [Config 规范](../config/config-spec.md)
- [Config 持久化、Dump 与历史规范](../config/config-persistence-history-spec.md)
- [Naming 一致性与客户端状态规范](../naming/naming-consistency-client-spec.md)
- [鉴权与权限规范](../auth/auth-permission-spec.md)
