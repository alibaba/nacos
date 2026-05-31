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

# Naming 规范

本文定义 Nacos Naming 领域的顶层规范。它基于
[Nacos 设计规范](../design/nacos-design-spec.md)和
[资源模型规范](../design/resource-model-spec.md)，进一步定义服务发现能力。

## 1. 定位

Nacos Naming 是服务发现领域。它管理服务、集群、实例、服务元数据、实例元数据、健康状态、
订阅者、发布者和客户端服务视图。

Naming 是 Nacos 的一级领域。它不是通用流量治理引擎、服务网格控制面、配置存储或 AI Registry
模型。Naming 可以提供内部过滤、客户端 selector、权重、健康保护和元数据，但这些能力应限定在
服务发现语义内。

## 2. 资源身份

Naming 使用微服务资源层次：

```text
namespaceId -> groupName -> serviceName
```

Cluster 和 Instance 是从属资源：

```text
namespaceId -> groupName -> serviceName -> clusterName -> instance
```

具体身份规则由 [Naming 资源规范](naming-resource-spec.md)定义。

## 3. 服务类型

每个 Naming service 都属于以下一种服务类型：

| 服务类型 | 含义 | 主要状态路径 |
| --- | --- | --- |
| 临时服务 | 非持久化运行时服务。实例由存活的 client 持有，并会随心跳或连接过期而消失。 | 偏 AP 的临时 client state 和 Distro 同步。 |
| 持久服务 | 持久化服务。实例作为持久资源管理，并可以从服务端 snapshot 恢复。 | 偏 CP 的持久 client state 和元数据持久化。 |

服务类型是 service 级语义属性。Instance 的 `ephemeral` 输入必须与所属 service 类型匹配。实现中
可以为了兼容和路由在 instance 上保留 `ephemeral` 字段，但新行为不得把它当作可以在同一个
service 内混用类型的独立实例策略。

## 4. 规范层次

### 4.1 通用规范

| 责任 | 含义 | 详细规范 |
| --- | --- | --- |
| 资源模型 | 定义 service、cluster、instance、client、publisher 和 subscriber 身份。 | [Naming 资源规范](naming-resource-spec.md) |
| 发现与订阅 | 定义查询、订阅、推送、模糊订阅、本地缓存和 failover 视图。 | [Naming 发现与订阅规范](naming-discovery-subscription-spec.md) |
| 健康检查与保护 | 定义健康状态、主动健康检查、enabled 状态、权重、内部过滤和保护阈值。 | [Naming 健康检查与保护规范](naming-health-protection-spec.md) |
| 元数据与 selector | 定义 service、cluster、instance 元数据、运行时与运维态元数据优先级、保留 key、内部过滤、遗留 API selector 和客户端 selector 行为。 | [Naming 元数据与 Selector 规范](naming-metadata-selector-spec.md) |
| 运维 | 定义 client 诊断、subscriber 诊断、指标、开关、日志级别和清理边界。 | [Naming 运维规范](naming-ops-spec.md) |

### 4.2 按服务类型分化的规范

| 责任 | 含义 | 详细规范 |
| --- | --- | --- |
| 实例生命周期 | 定义通用生命周期，以及按服务类型区分的注册、心跳、注销、更新、批量注册和清理行为。 | [Naming 实例生命周期规范](naming-instance-lifecycle-spec.md) |
| 一致性与客户端状态 | 定义通用 client 身份，以及临时服务 AP 状态、持久服务 CP 状态、索引和 snapshot。 | [Naming 一致性与客户端状态规范](naming-consistency-client-spec.md) |
| 临时服务 Distro 一致性 | 定义临时 ownership、Distro 同步、verify、anti-entropy、清理和 AP 可见性。 | [Naming 临时服务 Distro 一致性规范](naming-ephemeral-distro-consistency-spec.md) |
| 持久服务 CP 一致性 | 定义持久实例 CP 写入、metadata group、snapshot、恢复和可见性。 | [Naming 持久服务 CP 一致性规范](naming-persistent-cp-consistency-spec.md) |

## 5. 设计原则

### 5.1 Service 是发现单元

Naming service 是可寻址的服务发现单元。Instance 必须在其 service scope 和 cluster scope 下
理解。脱离 `namespaceId`、`groupName` 和 `serviceName` 的 instance 不是完整的 Naming 资源。

### 5.2 运行面与管理面分离

运行时客户端注册或注销自己的实例、查询已知服务并订阅服务变化。服务创建、服务删除、服务元数据、
集群元数据、client 诊断、subscriber 诊断、指标、开关和日志级别操作属于管理能力，应通过
Admin API、Console API 或 Maintainer SDK 暴露。

HTTP Open API 面向无法使用 gRPC 的自定义客户端，提供指定服务的注册、心跳、注销和列表查询。
它不应扩展为大范围服务管理或推送订阅 API。

### 5.3 临时服务与持久服务是不同语义

临时服务是非持久化运行时服务，其实例绑定 client 存活状态，并使用偏 AP 的临时路径。持久服务是
持久化服务，其实例使用偏 CP 的持久路径。API、SDK 和存储代码必须保留此区别，不能把
`ephemeral` 仅作为展示字段处理。

### 5.4 发现视图是过滤后的状态

Naming 发现结果不是原始存储 dump。查询和订阅结果可能经过 cluster、enabled 状态、健康状态、
服务端内部过滤规则和保护阈值过滤。SDK selection 会在服务端提供的 `ServiceInfo` 视图之上继续做
客户端 selector 和权重选择。

### 5.5 推送更新客户端视图

gRPC 订阅推送携带已订阅服务的最新 `ServiceInfo` 状态。客户端将推送状态保存到内存和磁盘缓存，
比较实例 diff，通知 listener，并在重连、缓存缺失或轮询兜底时重新查询。HTTP Open API 不提供
长轮询或推送订阅。

### 5.6 横切能力通过扩展接入

Naming 可以集成扩展机制，但 Naming 领域归属不转移：

| 关注点 | 规则 |
| --- | --- |
| 鉴权 | Naming API 和 gRPC handler 使用 Naming 资源，并遵循[鉴权与权限规范](../auth/auth-permission-spec.md)。 |
| 可见性 | 对 service、instance、subscriber 或 client 的范围查询，在启用可见性插件时应应用[可见性](../auth/visibility-plugin-spec.md)规则。 |
| Control | 高频注册、注销、查询、订阅、推送和列表流程应暴露稳定的 Control 点，遵循 [Control 插件规范](../plugin/control-plugin-spec.md)。 |
| Trace 与指标 | Naming 生命周期事件应遵循 [Trace 插件规范](../plugin/trace-plugin-spec.md)；共享指标和诊断遵循[可观测钩子规范](../design/foundation-observability-hooks-spec.md)。 |
| 健康检查扩展 | 健康检查类型通过 health checker registry 加载，并必须保持 service/cluster/instance 资源模型不变。 |
| 寻址 | 客户端服务端寻址应遵循[寻址插件规范](../plugin/addressing-plugin-spec.md)。 |

## 6. 接口面

| 接口面 | 范围 |
| --- | --- |
| HTTP Open API | `/v3/client/ns/instance` 面向自定义运行时客户端提供注册、心跳、注销和列表查询。 |
| HTTP Admin API | `/v3/admin/ns/*` 提供 service、instance、cluster、health、client 和运维管理。 |
| gRPC API | 提供运行时注册、批量注册、持久注册、查询、订阅、模糊订阅和服务端推送。参见 [gRPC API 规范](../grpc-api/api-spec.md)。 |
| Client SDK | 通过 `NamingService` 面向运行时应用提供注册、注销、查询、订阅、模糊订阅、本地缓存和 failover。参见 [SDK 规范](../sdk/sdk-spec.md)和[客户端运行时规范](../client/README.md)。 |
| Maintainer SDK | 通过 naming maintainer service 提供管理类接入。 |
| Console API | 面向 UI 的管理流程。Console API 可以调整展示形态，但不能重新定义 Naming 语义。 |

## 7. 边界

- Naming 不拥有配置内容或 Config listener 语义。
- Naming 不拥有 AI 资源身份。AI 资源可以引用 Naming service 或 endpoint，但引用关系不应让 AI
  资源变成普通 Naming service。
- Naming metadata 是 key-value 服务发现元数据。只有 Naming 明确定义的保留元数据 key 才能改变
  核心行为。
- Naming 健康检查用于判断发现可用性，不是通用应用观测或 SLA 系统。
- 内部过滤和 SDK selector 是发现侧过滤与选择工具。遗留 API 定义的 service selector 是兼容字段，
  不应成为新流量策略语义的基础。

## 8. 相关规范

- [Naming 临时服务 Distro 一致性规范](naming-ephemeral-distro-consistency-spec.md)
- [Naming 持久服务 CP 一致性规范](naming-persistent-cp-consistency-spec.md)
- [运行时推送与重连规范](../client/runtime-push-reconnect-spec.md)
- [AP 一致性规范](../design/foundation-ap-consistency-spec.md)
- [CP 一致性规范](../design/foundation-cp-consistency-spec.md)
- [内部 RPC 与集群请求规范](../design/foundation-internal-rpc-spec.md)
