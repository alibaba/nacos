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

# Config 持久化、Dump 与历史规范

本文定义 Config 持久化、本地 dump 缓存和历史语义。

## 1. 持久化记录

Config 领域通过 repository service 存储可靠状态。主要记录族包括：

| 记录族 | 目的 |
| --- | --- |
| `config_info` | 正式 Config 内容、md5、类型、元数据、来源信息、namespace、group 和 encrypted data key。 |
| `config_info_gray` | 灰度 Config 内容、md5、灰度名称、序列化灰度规则、namespace、group 和 encrypted data key。 |
| `his_config_info` | 正式和灰度发布或删除操作的历史变更记录。 |
| `tenant_capacity` / `group_capacity` | namespace、group 和 cluster 范围的容量限制和用量计数。 |

Repository 接口定义 Config 语义。具体 SQL 和数据库方言属于实现细节，由
[数据源方言插件规范](../plugin/datasource-dialect-plugin-spec.md)覆盖。

## 2. 本地 Dump 缓存

每个服务端节点维护本地服务状态：

- 以内部 group key 为键的 JVM 缓存，包含 md5、时间戳、encrypted data key、内容类型和灰度规则状态；
- 保存正式和灰度配置内容的本地 dump 文件。

启动阶段必须将持久化的正式和灰度配置 dump 到本地服务状态。运行时查询读取缓存和本地 dump 文件，
不做大范围数据库查询。

## 3. 变更 Dump 流程

写入成功后，Config 发布变更事件。Dump service 将事件转化为 dump 任务。任务从持久化层重新加载
受影响的正式或灰度记录，并更新本地缓存和本地 dump 文件。
本地事件行为由[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)定义，
dump task 行为由[任务执行规范](../design/foundation-task-execution-spec.md)定义。

Dump 必须忽略过期时间戳，并保留更新的本地状态。如果 md5 未变化但时间戳更新，只更新时间戳状态
而不重写内容。如果内容变化，则同时更新本地文件内容和 JVM md5 状态。

## 4. 嵌入式与外部存储

Config 支持嵌入式和外部存储模式：

- 嵌入式存储必须等到 CP 协议已有可读数据后，才完成启动 dump；
- 外部存储直接从配置的数据源初始化 dump；
- 历史清理和管理类存储操作必须只在存储模式策略选中的节点上执行。

详细 Config 一致性契约由
[Config 一致性、Dump 与可见性规范](config-consistency-dump-visibility-spec.md)定义。嵌入式存储
的 CP 基础见[CP 一致性规范](../design/foundation-cp-consistency-spec.md)，Config Notify 传播见
[AP 一致性规范](../design/foundation-ap-consistency-spec.md)。持久化、dump、任务和事件边界由
[持久化与 Dump 规范](../design/foundation-persistence-dump-spec.md)、
[任务执行规范](../design/foundation-task-execution-spec.md)和
[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)定义。

## 5. 历史

历史记录必须保留足够信息以便检查和恢复配置变化：

- `dataId`、`groupName` 和 `namespaceId`；
- 内容和 md5；
- 来源用户和来源 IP；
- 操作类型，例如发布或删除；
- 发布类型，例如正式或灰度；
- 适用时的 `grayName` 和扩展信息；
- 创建和修改时间。

历史列表和详情 API 属于管理 API。分页大小必须有边界。历史清理使用配置的保留窗口，默认 `30` 天。

## 6. 恢复与运维

Admin 本地缓存操作可以触发从持久化层到本地缓存的全量 dump。该操作是管理修复机制，不应作为正常
发布链路使用。

当本地磁盘已满或无法安全保存 dump 内容时，服务端必须将其视为致命条件，因为运行时查询正确性依赖
本地服务状态。

## 7. 相关规范

- [Config 一致性、Dump 与可见性规范](config-consistency-dump-visibility-spec.md)
- [持久化与 Dump 规范](../design/foundation-persistence-dump-spec.md)
- [CP 一致性规范](../design/foundation-cp-consistency-spec.md)
- [AP 一致性规范](../design/foundation-ap-consistency-spec.md)
- [内部 RPC 与集群请求规范](../design/foundation-internal-rpc-spec.md)
- [任务执行规范](../design/foundation-task-execution-spec.md)
- [事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)
- [数据源方言插件规范](../plugin/datasource-dialect-plugin-spec.md)
