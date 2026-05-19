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

# Naming 一致性与客户端状态规范

本文定义 Naming 一致性路径、client 身份、索引和 snapshot。服务类型决定运行时实例状态由哪条
一致性路径负责。

## 1. 通用 Client State

Naming 使用 Client state 将 publisher 和 subscriber 连接到 service。Naming Client 是服务端对
某个运行时通信参与方相关数据的抽象，不等同于用户侧 SDK 对象或应用进程。

对于 gRPC 通信，一个存活连接对应一个 connection-based Client。该连接上的所有发布和订阅请求都会
更新同一个 Client 对象。这使 Naming 运行时数据从 HTTP 时代的逐请求无状态模型，转变为与连接状态
绑定的有状态模型。

对于 HTTP 和兼容客户端，Naming 可以创建 IP-port-based Client 来模拟等价的 publisher 状态。由于
不同 HTTP 请求可能落到不同 Nacos 节点，这类 Client 需要通过心跳和过期检查保活。

| Client 类型 | 典型来源 | 生命周期 |
| --- | --- | --- |
| Connection-based client | gRPC SDK 连接。 | 连接关闭时释放。 |
| Ephemeral IP-port client | HTTP 或兼容临时注册。 | 通过心跳和过期检查保活。 |
| Persistent IP-port client | 持久实例注册。 | 通过持久路径存储，可在进程重启后恢复。 |

Client id 是内部状态。公开运行时 API 应通过 service scope、cluster、IP、port 和 service type 识别
实例。

Client state 是 publisher 和 subscriber 索引的来源。索引是派生服务状态，可以根据 client state、
metadata snapshot 和事件重建。本地 Naming 事件行为遵循
[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)。

发布到推送的主链路为：

1. 注册、注销、订阅或取消订阅请求更新 connection-bound 或 IP-port-based Client；
2. Client 更新产生 Naming 事件；
3. service 到 publisher、service 到 subscriber 的索引根据事件更新；
4. 通过索引从 publisher Clients 聚合 service 数据；
5. 通过索引筛选订阅同一 service 的 subscriber Clients；
6. gRPC push 通过每个 subscriber 连接发送聚合后的 `ServiceInfo` 视图。

新的存储或推送行为必须保留这个边界：Client 持有运行时 publisher/subscriber 状态，索引是为了
service 维度聚合和推送而维护的派生加速结构。

## 2. 临时服务一致性

临时服务拥有偏 AP 的运行时实例状态。实例注册在临时 client 下，通过
[Naming 临时服务 Distro 一致性规范](naming-ephemeral-distro-consistency-spec.md)定义的 Distro
client data 路径在集群中同步，并在心跳或
[连接生命周期](../design/foundation-remote-connection-spec.md)表明 client 消失时移除。

临时状态应优先保证快速运行时可用和最终收敛，不应视为持久元数据。

批量注册和 gRPC 连接 redo 属于临时服务行为。它们从客户端进程恢复运行时意图，不创建持久的服务端
instance metadata。

## 3. 持久服务一致性

持久服务拥有偏 CP 的实例状态。注册、注销和更新操作会写入 persistent service group，并由
persistent client operation service apply。共享 CP 基础见
[CP 一致性规范](../design/foundation-cp-consistency-spec.md)，Naming 特有的持久行为由
[Naming 持久服务 CP 一致性规范](naming-persistent-cp-consistency-spec.md)定义。持久实例 snapshot
用于状态恢复。

持久 client 不支持 subscriber state。订阅属于临时 client 行为。

## 4. 元数据一致性

Service metadata、cluster metadata 和 instance metadata 通过 CP metadata group 写入。加载 metadata
snapshot 时必须按需重建 service singleton，使恢复后的 service identity 与 metadata 保持连接。

元数据一致性与临时服务实例状态分离。运维态 instance metadata 可以附加到运行时 instance 视图上，
并且优先于运行时注册元数据，但其写路径仍属于 metadata 路径。

## 5. 索引与 Service Storage

Naming 维护派生索引：

- service 到 publisher client ids；
- service 到 subscriber client ids；
- service 到 cluster names；
- service 到缓存 `ServiceInfo`；
- fuzzy watch pattern 到匹配 service keys。

索引是派生服务状态。新公开 API 不应将这些索引暴露为权威存储契约。

## 6. 客户端 Redo 与恢复

Java SDK 缓存已注册实例和订阅，用于 gRPC 重连后的 redo。Redo 按
[客户端本地缓存与 Redo 规范](../client/client-local-cache-redo-spec.md)恢复客户端进程的运行时意图，
不改变服务端资源身份模型或 service type。

客户端磁盘缓存和 failover 数据为 discovery read 提供本地恢复，不得作为服务端持久化使用。

## 7. 相关规范

- [Naming 实例生命周期规范](naming-instance-lifecycle-spec.md)
- [Naming 发现与订阅规范](naming-discovery-subscription-spec.md)
- [Naming 元数据与 Selector 规范](naming-metadata-selector-spec.md)
- [Naming 临时服务 Distro 一致性规范](naming-ephemeral-distro-consistency-spec.md)
- [Naming 持久服务 CP 一致性规范](naming-persistent-cp-consistency-spec.md)
- [AP 一致性规范](../design/foundation-ap-consistency-spec.md)
- [CP 一致性规范](../design/foundation-cp-consistency-spec.md)
- [内部 RPC 与集群请求规范](../design/foundation-internal-rpc-spec.md)
- [任务执行规范](../design/foundation-task-execution-spec.md)
- [事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)
