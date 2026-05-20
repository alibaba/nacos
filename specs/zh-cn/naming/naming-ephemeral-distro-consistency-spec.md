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

# Naming 临时服务 Distro 一致性规范

本文定义 Naming 领域中临时服务的 AP 一致性规则。本文细化共享的
[AP 一致性规范](../design/foundation-ap-consistency-spec.md)和
[Naming 一致性与客户端状态规范](naming-consistency-client-spec.md)。

## 1. 范围

本文负责：

- 临时 client 运行时状态的 ownership；
- Distro data type、change、delete、verify、snapshot 和 repair 语义；
- Distro apply 后的 client change 如何对 Naming 索引、service storage 和 push 可见；
- 服务端 Distro repair 与客户端 redo 的边界。

本文不定义持久服务、CP metadata group 或客户端 SDK 本地缓存行为。

## 2. Ownership

临时状态由负责该临时 client 的服务节点拥有：

| Client 类别 | Owner 规则 |
| --- | --- |
| Connection-based client | 持有存活 gRPC connection 的节点拥有 native client。 |
| Ephemeral IP-port client | 由 Distro responsibility 根据 client responsible id 选出的节点拥有 native client。 |
| Synced client | 从其他 owner 节点同步来的远端副本，是服务状态，不代表本地 ownership。 |

只有合法、native、responsible、ephemeral 的 client 才应作为 Distro 源数据同步。Persistent client
不得通过该路径同步。

## 3. Distro 数据模型

Naming ephemeral Distro data 使用 resource type `Nacos:Naming:v2:ClientData`。Resource key 为
client id。

同步 payload 是 client 级数据，包含 client identity、已发布 service identity、instance publish
information、可选的 batch instance data，以及 verify flow 使用的 client revision。Distro 不以
service 级记录作为权威来源；service view 由 client state 和索引派生。

## 4. 变更传播

源节点必须从 client event 发布 Distro 变更：

- client changed -> `CHANGE`；
- client disconnected -> `DELETE`；
- 目标节点 verify failed -> 对该目标立即发送 `ADD`。

节点收到 `ADD` 或 `CHANGE` 时，必须创建或更新 synced client，并 apply 同步来的实例数据。Apply
同步数据必须发布重建 publisher index、service storage 和 push view 所需的本地 Naming event。

节点收到 `DELETE` 时，必须 disconnect 并 release 对应 source client id 的 synced client。派生索引和
service storage 必须通过普通 client release 使用的同一本地事件路径更新。

## 5. Verify 与 Anti-Entropy

Distro verify 使用 source client revision 与目标节点状态进行比较。如果目标节点无法 verify 该
client，必须返回 verify failure，使源节点向该目标发送完整 client data。

Revision `0` 可以被视为兼容数据。对于当前协议数据，revision 匹配会刷新目标侧 liveness 观察；
revision 不匹配必须触发 repair。

Snapshot transfer 是针对整个 Naming ephemeral Distro data type 的 anti-entropy。Snapshot 包含源节点
负责的全部 ephemeral clients。加载 snapshot 时，必须对每个 client 使用与普通 `ADD` 或 `CHANGE`
相同的 sync-data apply 规则。

## 6. 过期与清理

Connection-based client 在远程 connection 断开，或 connection manager 移除过期连接时过期。释放
native client 必须产生 disconnect 和 release event，使 Distro 删除远端副本，并清理本地索引。

Ephemeral IP-port client 通过心跳和 cache-time cleanup 过期。Owner 节点必须移除过期 native client，
并发布 Distro deletion 使用的同类 disconnect event。

Remote synced client 是 Distro path 的清理对象，不得在接收节点续约 native ownership。

## 7. 可见性与 Push

Ephemeral Naming visibility 在集群中最终一致：

1. owner 节点更新 client state；
2. 本地 event 更新 publisher index 和 service storage；
3. owner 节点调度 Distro sync 到 peer 节点；
4. peer 节点 apply synced client data，并重建派生服务状态；
5. service change event 调度 subscriber push。

Subscriber push 携带派生的 `ServiceInfo` view，不会使临时状态变成持久状态。Push retry 和 reconnect
恢复由[运行时推送与重连规范](../client/runtime-push-reconnect-spec.md)定义。

## 8. 边界

- Ephemeral Distro 不把 instance state 持久化到数据库。
- Ephemeral Distro 不拥有 service metadata 或 instance metadata；这些状态存在时遵循 metadata CP
  规则。
- 某个节点上的注册成功，可能需要等到 Distro sync 或 anti-entropy repair 后，才对其他节点可见。
- 面向 ephemeral service state 的范围查询，在 AP 收敛完成前可能观察到节点本地服务状态。

## 9. 相关规范

- [Naming 资源规范](naming-resource-spec.md)
- [Naming 实例生命周期规范](naming-instance-lifecycle-spec.md)
- [Naming 发现与订阅规范](naming-discovery-subscription-spec.md)
- [Naming 一致性与客户端状态规范](naming-consistency-client-spec.md)
- [运行时推送与重连规范](../client/runtime-push-reconnect-spec.md)
- [AP 一致性规范](../design/foundation-ap-consistency-spec.md)
- [内部 RPC 与集群请求规范](../design/foundation-internal-rpc-spec.md)
