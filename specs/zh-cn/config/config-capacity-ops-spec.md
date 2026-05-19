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

# Config 容量与运维规范

本文定义 Config 容量和运维语义。

## 1. 容量范围

Config 容量用于保护 cluster、namespace 和 group 资源，避免配置无限增长。

| 范围 | 含义 |
| --- | --- |
| Cluster | 集群内正式 Config 总数。 |
| Namespace | 单个 namespace 下的正式 Config 数量。 |
| Group | 不使用 namespace 维度容量时，单个 group 下的正式 Config 数量。 |

容量适用于正式 Config 记录。灰度版本是已有 Config 的发布状态，容量切面不把它们作为独立正式配置计数。

## 2. 限制项

容量包含：

| 字段 | 含义 |
| --- | --- |
| `quota` | 该范围内 Config 记录数量上限。`0` 表示使用默认值。 |
| `usage` | 当前统计的 Config 记录数量。 |
| `maxSize` | 单个 Config 内容大小上限，单位字节。`0` 表示使用默认值。 |

默认值由服务端配置提供：

| 配置项 | 默认值 |
| --- | --- |
| `defaultClusterQuota` | `100000` |
| `defaultGroupQuota` | `200` |
| `defaultTenantQuota` | `200` |
| `defaultMaxSize` | `100 * 1024` 字节 |
| `correctUsageDelay` | `600` 秒 |
| `initialExpansionPercent` | `100` |

## 3. 待移除的聚合配置字段

聚合配置不属于 Config 标准能力模型。现有代码、API 或数据库 schema 中可能仍包含
`maxAggrCount`、`maxAggrSize`、`defaultMaxAggrCount`、`defaultMaxAggrSize` 或相关聚合
路径用于兼容，但这些属于历史冗余设计，应该标记为待移除对象。

新的规范、API、SDK 和面向用户的文档不应基于聚合配置定义正式行为。为避免用户频繁调整表结构，
部分数据库字段可能暂时保留；后续标准数据库 schema 应在兼容窗口允许时，按照
[兼容与废弃策略规范](../design/compatibility-deprecation-spec.md)移除这些字段。

## 4. 执行规则

容量管理有两个开关：

- `isManageCapacity` 启用发布和删除周围的用量统计。
- `isCapacityLimitCheck` 启用配额和大小拒绝。

启用限制检查时，插入新的正式配置必须：

1. 检查并递增 cluster 用量；
2. 检查内容大小；
3. 当 namespace 非空时检查并递增 namespace 用量，否则检查并递增 group 用量；
4. 如果发布失败，回滚用量。

更新已有正式配置只检查内容大小，不增加用量。删除已有正式配置会递减用量，并在删除失败时回滚。

由于并发删除和异步写入流程可能使计数短暂不准确，系统会周期性修正用量。

## 5. 容量 API

Capacity Admin API 可以查询或更新 namespace 或 group 的容量。必须至少提供 `namespaceId` 或
`groupName` 之一。当容量记录不存在时，服务端可以先初始化，再返回应用默认值后的有效容量。

容量 API 是管理 API，不应通过运行时 Client SDK 暴露。

## 6. 运维 API

Config 运维 API 是管理修复或诊断接口：

| 操作 | 规则 |
| --- | --- |
| 本地缓存 dump | 按[持久化与 Dump 规范](../design/foundation-persistence-dump-spec.md)从持久化层全量刷新本地缓存。 |
| 日志级别更新 | 修改 Config 模块日志级别。 |
| Derby 查询 | 仅当嵌入式存储启用且 `nacos.config.derby.ops.enabled=true` 时，允许有边界的 `SELECT` 语句。 |
| Derby 导入 | 仅当嵌入式存储启用且 Derby ops 已启用时，导入 Derby 数据。 |
| 监听诊断 | 按 IP 或 Config 身份查询监听状态。 |
| 指标 | 在本节点或跨[集群成员](../design/foundation-cluster-membership-spec.md)查询客户端缓存和快照指标，并遵循[可观测钩子规范](../design/foundation-observability-hooks-spec.md)。 |

Derby ops 属于 maintainer-only 行为。它必须要求 Admin 权限，并默认保持关闭。

## 7. 相关规范

- [HTTP API 规范](../http-api/api-spec.md)
- [鉴权与权限规范](../auth/auth-permission-spec.md)
- [持久化与 Dump 规范](../design/foundation-persistence-dump-spec.md)
- [可观测钩子规范](../design/foundation-observability-hooks-spec.md)
- [Control 插件规范](../plugin/control-plugin-spec.md)
