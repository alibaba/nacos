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

# Naming 实例生命周期规范

本文定义 Naming 领域中的 service 和 instance 生命周期行为。

## 1. Service 生命周期

Admin service 创建会创建 service 元数据和 service singleton。创建的 service 可以是临时或持久，
后续实例注册必须与该 service 类型匹配。创建 service 时必须选择一种服务类型：

| 服务类型 | 生命周期规则 |
| --- | --- |
| 临时服务 | 运行时发布信息由存活 client 持有，并通过心跳或[连接生命周期](../design/foundation-remote-connection-spec.md)清理。 |
| 持久服务 | 实例是持久资源，只能通过显式注销、删除或持久状态恢复规则清理。 |

持久实例注册到临时服务，或临时实例注册到持久服务，都必须被拒绝。

仅当 service 没有已注册实例时才允许删除 service。删除 service 会删除 service 元数据，并让清理
流程移除运行时 singleton 和派生缓存状态。

运行时实例注册可以隐式创建 service singleton。这是为了服务发现便利性而允许的行为，但管理元数据
仍与运行时实例发布分离。

## 2. 实例注册

实例注册必须：

1. 校验实例字段和心跳元数据；
2. 在 cluster name 省略时填充默认值；
3. 从已有 service singleton 解析所属服务类型，或在允许隐式创建时用请求类型创建 singleton；
4. 确保输入 instance 的 `ephemeral` 值与 service 类型匹配；
5. 派生或使用正确的运行时 client id；
6. 通过临时服务或持久服务操作路径注册实例；
7. 通过 Naming 事件更新 service 索引和 service storage；
8. 发布 trace 事件用于审计和诊断。

Naming 事件遵循[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)。

临时服务注册通过 gRPC 和 HTTP Open/Admin API 支持。持久服务注册通过 persistent request 路径支持；当
服务端未声明支持 gRPC 持久实例能力时，客户端可以回退到 HTTP 兼容路径。

## 3. 心跳

心跳适用于 HTTP 和兼容临时 IP-port client。HTTP Open API 心跳复用
`POST /v3/client/ns/instance`，并设置 `heartBeat=true`。gRPC 临时服务通过
[连接生命周期](../design/foundation-remote-connection-spec.md)事件保活；传输层心跳在 Naming 之外定义。

如果心跳找到 client 和 service instance，会更新 last-updated time 并调度 beat processing。如果
心跳找不到 instance 且没有 beat payload，服务端返回 `INSTANCE_NOT_FOUND`，调用方应重新注册。

心跳间隔、心跳超时和 IP 删除超时可由保留实例元数据 key 控制。这些值必须满足
[Naming 资源规范](naming-resource-spec.md)中的校验规则。

## 4. 注销

注销会从所属 client 中移除实例，并发布 service change 事件。为了运行时调用方的幂等性，注销
不存在的实例或不存在的兼容 client 应视为成功 no-op。

当最后一个 publisher 消失时，service 索引会发出 delete-service 变更事件。空 service 清理可在
配置的过期窗口之后移除 service singleton。

## 5. 更新与局部更新

Admin 实例更新会修改 `enabled`、`weight` 和扩展元数据等运维态 instance metadata。全量更新会
校验实例权重并替换已存储运维态 metadata。局部更新只修改请求中显式出现的字段。

对外服务的发现视图中，运维态 instance metadata 优先于运行时注册元数据，规则见
[Naming 元数据与 Selector 规范](naming-metadata-selector-spec.md)。

实例更新不改变 service 身份。修改 `ip`、`port` 或 `clusterName` 等价于操作另一个 instance 身份。

## 6. 批量操作

批量注册是临时服务 gRPC 能力。批量输入必须包含合法临时实例，且所属 service 类型必须为临时服务。
服务端将批量发布信息存储在所属 client 下，并发出 service change 事件。

Java SDK 批量注销通过保留批量注册记录中的剩余实例，并发送新的批量注册实现。新 API 应将其描述为
批量状态替换行为，而不是独立持久的逐实例删除。

## 7. 清理

Naming 清理包括：

- HTTP 和兼容临时实例心跳过期；
- 基于连接的 client 断连释放；
- service 在配置过期时间内没有 publisher 后的空 service 清理；
- service 或 instance 元数据脱离所属资源后的过期元数据清理。

清理属于 Naming 生命周期的一部分。它必须发布 subscriber、索引、元数据清理和 trace 所需的同类
资源事件。

## 8. 相关规范

- [Naming 资源规范](naming-resource-spec.md)
- [Naming 健康检查与保护规范](naming-health-protection-spec.md)
- [Naming 一致性与客户端状态规范](naming-consistency-client-spec.md)
- [事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)
- [Trace 插件规范](../plugin/trace-plugin-spec.md)
