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

# Naming 元数据与 Selector 规范

本文定义 Naming 元数据、元数据优先级和 selector 分类。

## 1. 元数据层次与来源

Naming 元数据有三类资源层次：

| 层级 | 范围 | 所属接口 |
| --- | --- | --- |
| Service metadata | 服务级发现元数据、保护阈值、遗留 selector 字段和 cluster map。 | Admin API、Console API、Maintainer SDK |
| Cluster metadata | 健康检查器、检查端口行为和 cluster 元数据。 | Admin API、Console API、Maintainer SDK |
| Instance metadata | 实例权重、enabled 状态和扩展元数据。 | 运行时注册和管理 API |

元数据不改变 service 身份。元数据变更应发布 service 或 instance information change 事件，使 storage
索引、推送和诊断可以刷新。本地事件投递由
[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)定义。

Naming 同时区分两类元数据来源：

| 来源 | 含义 | 持久化 | 优先级 |
| --- | --- | --- | --- |
| 运行时元数据 | 运行时 publisher 在实例注册或心跳时提交的元数据，主要描述由注册进程控制的部署时和运行时状态。 | 绑定到运行时 publisher 及其服务类型。 | 较低 |
| 运维态元数据 | 通过 Nacos 管理路径写入的元数据，例如 Admin API、Console API、Maintainer SDK 或元数据持久化路径，表示运维人员或开发人员的显式意图。 | 由 Nacos 存储，可在运行时 client 消失后按清理规则继续存在。 | 较高 |

当同一个元数据 key 同时存在于运行时元数据和运维态元数据时，对外服务的 Naming 视图必须以运维态
元数据为准。运维态元数据优先，因为它代表显式管理覆盖，并应由 Nacos 持久化或记忆化。

对于 service 级元数据，正式 service metadata 属于运维态元数据。对于 instance 级元数据，运行时
注册元数据是基础视图，运维态 instance metadata 会覆盖其同名 key。

## 2. 保留元数据 Key

大部分元数据是用户自定义 key-value 数据。Naming 为核心行为保留以下 instance metadata key：

| Key | 含义 |
| --- | --- |
| `preserved.register.source` | 实例注册来源。 |
| `preserved.heart.beat.interval` | 心跳间隔覆盖值。 |
| `preserved.heart.beat.timeout` | 心跳 unhealthy 超时覆盖值。 |
| `preserved.ip.delete.timeout` | 心跳删除超时覆盖值。 |
| `preserved.instance.id.generator` | instance id 生成器选择。 |

新的核心行为不得绑定到任意用户元数据 key。如果某个元数据 key 会改变 Naming 行为，必须被保留并
写入文档。

## 3. Selector 分类

Naming 当前存在三类 selector-like 概念：

| 分类 | 范围 | 规范状态 |
| --- | --- | --- |
| 内部实例过滤 | 服务端实现中用于形成发现视图的过滤规则，例如 cluster、enabled、health、保护阈值和内部过滤扩展点。 | 正式 Naming 行为。 |
| API 定义的 service selector | 旧 service API 和 SDK maintainer 方法接受的遗留 `selector` 输入。 | 仅兼容保留，待移除。 |
| 客户端 selector | SDK 侧 `NamingSelector`，用于本地 subscribe/unsubscribe 和 listener 匹配。 | 正式 SDK 扩展行为。 |

内部实例过滤属于服务端发现语义的一部分。它必须保留其他 Naming 规范定义的 service、cluster、
instance、health、enabled、服务类型和保护语义。

API 定义的 service selector 不应再用于定义新的服务端行为。新的 API 和规范应显式建模过滤条件，
或在行为只属于 SDK 本地时使用客户端 selector。

客户端 selector 是 SDK 扩展点。它过滤本地 listener 通知或 selection 结果，不得改变服务端的
service、instance、metadata 或一致性状态。

## 4. Cluster 健康检查元数据

Cluster 元数据控制主动健康检查行为：

- checker 类型和序列化后的 checker 字段；
- 使用实例端口还是固定检查端口；
- cluster 级扩展元数据。

健康检查元数据属于 cluster，不应复制进 instance identity 或 service identity。

## 5. 元数据持久化

Service metadata、cluster metadata 和 instance metadata 操作通过 CP metadata 路径写入。元数据
可能在运行时 client 消失后临时存活。过期元数据清理会在所属 service 或 instance 脱离达到配置
过期窗口后移除元数据。

运行时元数据跟随运行时 publisher 生命周期。运维态元数据跟随元数据持久化路径，并可以在恢复后
覆盖运行时元数据。

## 6. 待移除问题

- API 定义的 service selector 字段和请求参数属于遗留兼容行为。新 API 和 SDK 规范应将其废弃；
  等兼容要求允许后，应按照[兼容与废弃策略规范](../design/compatibility-deprecation-spec.md)
  从正式 Naming 行为中移除。

## 7. 相关规范

- [Naming 资源规范](naming-resource-spec.md)
- [Naming 健康检查与保护规范](naming-health-protection-spec.md)
- [Naming 一致性与客户端状态规范](naming-consistency-client-spec.md)
- [事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)
- [兼容与废弃策略规范](../design/compatibility-deprecation-spec.md)
