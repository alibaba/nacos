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

# Nacos 集群成员规范

本文定义 Nacos 服务端模块使用的基础层集群成员模型，是
[基础能力规范](foundation-capabilities-spec.md)中集群成员部分的展开。

集群成员是 Nacos Server member 的基础设施视图。它不是 Config、Naming、AI 或安全领域的资源模型，
也不应重新定义领域归属。

## 1. 范围

集群成员能力负责：

- 服务端 member 身份和 member 状态；
- member lookup 和 lookup 模式切换；
- 本机 member ready 状态；
- member 元数据和能力刷新；
- 基于上报任务的 member 健康观测；
- member-change 事件发布；
- 供集群 RPC、一致性协议和运维 API 使用的 member 视图。

它不负责：

- 暴露 member 视图之外的领域分片语义；
- Config、Naming、AI 或 auth 资源生命周期；
- 公开 HTTP 或 gRPC API 契约；
- 除 cluster member list 兼容落盘之外的数据源持久化语义。

## 2. Member 模型

Nacos Server member 由 `Member` 表示。`Member` 继承公开的 `NacosMember` 响应模型，
并增加服务端运行时字段。

| 字段 | 含义 |
| --- | --- |
| `address` | `ip:port` 形式的稳定 member 地址。端口是 Nacos 主服务端口。 |
| `ip` / `port` | 用于路由和诊断的解析后 member 端点。 |
| `state` | 生命周期状态，例如 `STARTING`、`UP`、`SUSPICIOUS`、`DOWN` 或 `ISOLATION`。 |
| `extendInfo` | 服务端元数据，例如版本、raft port、site、weight、升级兼容和最后刷新时间。 |
| `abilities` | 由 member 上报的服务端能力表，包括 remote、config 和 naming 能力。 |
| `failAccessCnt` | 本地临时失败计数，用于把 member 从 suspicious 推进到 down。 |
| `grpcReportEnabled` | 混合版本 member 上报的兼容标记，不是新的语义能力。 |

Member 身份规则：

- `address` 是 membership map 的 key，必须能解析为互联网地址加端口；
- 如果 lookup 结果没有包含本机 member，必须把本机 member 加回有效 member 集合；
- `allMembers()` 返回包含 self 的当前 member 视图；
- `allMembersWithoutSelf()` 返回不包含 self 的当前 member 视图；
- `memberAddressInfos` 是当前健康地址视图，只包含有效 `UP` 视图中的 member。

## 3. Lookup 模式

`MemberLookup` 是通用 lookup 接口。所有 lookup 实现都必须通过
`afterLookup(Collection<Member>)` 喂给同一套 membership 模型，最终委托到
`NacosMemberManager.memberChange`。

支持的 lookup 模式：

| 模式 | 实现 | 选择与行为 |
| --- | --- | --- |
| 单机 | `StandaloneMemberLookup` | Nacos 运行在单机模式时使用。有效 member 集合只包含本机 member。 |
| 文件配置 | `FileConfigMemberLookup` | 当 `nacos.core.member.lookup.type=file`、`cluster.conf` 存在，或配置了 member list 属性时选择。它读取 `cluster.conf`，并监听配置目录变更。 |
| 地址服务器 | `AddressServerMemberLookup` | 当 `nacos.core.member.lookup.type=address-server`，或没有文件/member-list 来源时选择。它在启动时同步拉取 member list，并周期刷新。 |

Lookup 选择规则：

- 单机模式始终使用 standalone lookup；
- 集群模式下，显式 `nacos.core.member.lookup.type` 优先；
- 文件/member-list 配置优先于 address-server 兜底；
- 切换 lookup 模式必须销毁旧 lookup，并把同一个 `ServerMemberManager` 注入新 lookup；
- lookup 实现可以暴露诊断 `info()`，但这些数据不是领域资源契约。

服务端 member lookup 扩展由[寻址插件规范](../plugin/addressing-plugin-spec.md)描述。本文定义的是
所有寻址实现都必须喂给的 membership 模型。

## 4. Member 变更应用

`ServerMemberManager.memberChange` 应用完整的 member-list 视图。该方法是同步的，用于保证有效列表
更新、兼容文件同步和事件发布保持顺序。

变更规则：

- 空 lookup 结果不得替换当前 member 视图；
- 本机 member 必须保留在有效视图中；
- 当同一地址仍然存在于新列表时，应保留已有 member 的元数据和能力；
- `memberJoin` 和 `memberLeave` 是便捷操作，最终都解析为完整 member-list 变更；
- topology 变化会通过 `MemberUtil.syncToFile` 写入 `cluster.conf` 以保持兼容；
- 只有有效 topology 变化或 member 基础元数据变化时，才发布 `MembersChangeEvent`。

`ServerMemberManager.update(Member)` 用于更新已存在 member 的元数据/状态。它不得新增未知 member。
如果更新改变基础元数据，则发布以该 member 为 trigger 的 `MembersChangeEvent`。

## 5. Member Ready 与健康观测

本机 ready 由 `ServerMemberManager.setSelfReady` 设置。它会将本机 member 标记为 `UP`，把本机
地址发布到环境中，并在集群模式下启动 member 上报任务。

Member 健康观测基于尽力而为的上报：

- `MemberInfoReportTask` 周期性地把本机 member 元数据上报给一个 peer；
- `UnhealthyMemberInfoReportTask` 独立重试上报给非 `UP` 的 member；
- peer 支持远程上报时使用 gRPC，HTTP 仅作为混合版本兼容保留；
- 成功上报会刷新 peer 状态和上报的元数据；
- 失败上报会把 peer 标记为 `SUSPICIOUS`，递增失败计数，并在达到配置阈值或出现 connection refused
  时标记为 `DOWN`；
- 状态变化必须发布 `MembersChangeEvent`。

Member 健康是本地对服务端 peer 的观测。它不能替代 AP/CP 协议 membership 决策、请求级失败判断或
领域资源健康。

## 6. Member Change 事件

`MembersChangeEvent` 是携带有效 member 视图和可选 trigger member 的进程内事件。
`MemberChangeListener` 订阅该事件，并默认忽略过期事件。通用本地事件语义由
[事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)定义。

已知使用方包括：

- `ProtocolManager`，把 member 变化传播给 AP 和 CP 协议；
- `ClusterRpcClientProxy`，创建或销毁服务端间 gRPC client；
- Naming 运行时组件，用于维护 server 和 service 索引；
- 基于 member 做聚合的运维和诊断服务。

事件规则：

- 事件是本进程通知，不是跨节点复制保证；
- subscriber 需要最新状态时应重新读取 member 视图；
- subscriber 必须容忍重复、延迟或合并后的观测；
- member-change 事件不得作为公开 API 契约。

## 7. 使用方规则

集群成员使用方必须遵循：

- 容忍单机模式和单 member 有效视图；
- 容忍 member 新增、删除、状态变化和临时不可达；
- 远端 fan-out 优先使用 `allMembersWithoutSelf()`；
- 当行为只适用于 ready 或具备能力的 member 时，需要按状态或能力过滤；
- 除非通过 `MembersChangeEvent` 刷新，否则不要长期缓存 member list；
- 除非领域规范明确声明，否则不要把 member 顺序视为领域分片规则；
- 不要把领域归属写入 `Member.extendInfo`。

## 8. 相关规范

- [基础能力规范](foundation-capabilities-spec.md)
- [远程连接生命周期规范](foundation-remote-connection-spec.md)
- [内部 RPC 与集群请求规范](foundation-internal-rpc-spec.md)
- [AP 一致性规范](foundation-ap-consistency-spec.md)
- [CP 一致性规范](foundation-cp-consistency-spec.md)
- [事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)
- [寻址插件规范](../plugin/addressing-plugin-spec.md)
- [gRPC API 规范](../grpc-api/api-spec.md)
- [Control 插件规范](../plugin/control-plugin-spec.md)
