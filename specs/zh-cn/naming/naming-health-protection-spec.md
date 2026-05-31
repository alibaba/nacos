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

# Naming 健康检查与保护规范

本文定义 Naming 健康状态、健康检查、enabled 状态、权重和保护阈值行为。

## 1. 健康状态

`healthy` 描述实例当前是否被 Naming 健康逻辑认为可用。它可以由以下流程更新：

- 临时服务实例的心跳检查；
- 持久服务实例的主动健康检查；
- cluster health checker 为 `NONE` 的持久服务实例手动健康更新；
- 负责节点之间的健康状态同步。

`enabled` 描述实例是否允许接收发现流量。disabled 实例不应返回给运行时 Open API 消费者，并会被
Java SDK selection 过滤。

## 2. 临时服务健康

临时服务的实例是非持久化运行时状态。其健康状态由运行时 publisher 的存活状态驱动，而不是由
服务端主动健康检查驱动。

### 2.1 HTTP 与兼容心跳

HTTP 客户端、1.x 客户端以及其他不使用 gRPC 的客户端，通过上报心跳来维持临时实例存活。
Beat check task 会检查 last heartbeat time。如果实例超过 heartbeat timeout 未恢复，可能被标记为
unhealthy；如果超过 delete timeout 且过期删除启用，可能被移除。Beat check 任务调度必须遵循
[任务执行规范](../design/foundation-task-execution-spec.md)。

心跳时间可由保留元数据 key 自定义：

| Key | 含义 |
| --- | --- |
| `preserved.heart.beat.interval` | 期望心跳间隔。 |
| `preserved.heart.beat.timeout` | 实例被视为 unhealthy 前的超时时间。 |
| `preserved.ip.delete.timeout` | 实例可被删除前的超时时间。 |

### 2.2 gRPC 连接存活

gRPC 客户端通过[远程连接生命周期](../design/foundation-remote-connection-spec.md)维持临时实例存活。
Naming 通过连接关闭和释放事件移除或 redo 运行时 publisher/subscriber 状态。本地事件投递遵循
[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)。

为防止连接假死，gRPC 传输层内部封装了心跳与存活检测。对 Naming 模块来说，这部分能力隐藏在
gRPC 连接层之后。Naming 应依赖
[远程连接生命周期规范](../design/foundation-remote-connection-spec.md)定义的连接生命周期事件，而不应
重复实现传输层心跳逻辑。

## 3. 持久服务主动健康检查

持久服务的实例由服务端健康检查 processor 主动检查。Cluster 元数据选择 checker 类型和端口行为。

### 3.1 主动检查类型

内置 checker 类型包括 TCP、HTTP、MySQL 和 NONE，额外 checker 类型可通过 health checker registry
注册。

主动健康检查只能在负责节点执行检查且 service health check switch 允许时改变健康状态。

### 3.2 手动健康更新

仅当 cluster health checker 为 `NONE` 时，才允许手动更新持久服务实例健康状态。如果配置了主动健康
检查，健康状态归 checker 所有，手动健康更新必须被拒绝。

## 4. 权重

`weight` 是实例级值，用于客户端权重选择。运行时选择应忽略 weight 小于等于 0 的实例。服务端查询
负责存储和返回 weight，但不保证所有消费者都使用权重负载均衡。

## 5. 保护阈值

Service `protectThreshold` 用于防止发现结果收缩到过少健康实例。在 cluster、enabled、服务端内部
过滤规则和 health 过滤后，如果健康比例小于等于阈值，服务端会将结果标记为达到保护阈值，并返回
更宽的过滤实例集合，同时在该保护视图中把 unhealthy 实例表现为 healthy。

保护阈值是发现可用性保护机制，不表示底层实例真实健康。

## 6. 运行时连接健康

gRPC 连接心跳、假死检测和客户端连接存活行为由
[客户端连接与故障切换规范](../client/client-connection-failover-spec.md)定义。基础层服务端连接
生命周期边界由[远程连接生命周期规范](../design/foundation-remote-connection-spec.md)定义。

## 7. 相关规范

- [Naming 资源规范](naming-resource-spec.md)
- [Naming 发现与订阅规范](naming-discovery-subscription-spec.md)
- [Naming 元数据与 Selector 规范](naming-metadata-selector-spec.md)
- [任务执行规范](../design/foundation-task-execution-spec.md)
- [事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)
