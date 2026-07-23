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

# AI Vector 插件规范

本文档定义 AI Vector 插件契约。该插件为 Nacos AI discovery 提供可选的向量索引与召回
能力。它扩展 [Nacos 插件化规范](plugin-spec.md)，不改变标准 AI 资源的 identity、
生命周期、可见性或鉴权语义。

## 1. 范围与启用

AI 模块在 `plugin/ai` 中定义 Vector SPI，具体实现位于标准 AI 领域模块之外。默认
PostgreSQL 实现由 `nacos-default-ai-vector-plugin` 提供。

向量索引是可选能力。ARD 关闭或者没有可用 Vector Provider 时，不能阻止 Nacos 启动、
标准 AI 资源写入或关键词 discovery。通过 `nacos.ai.ard.vector.provider` 选择 Provider；
Provider 专属配置由各实现负责。

## 2. Provider 生命周期

每个实现通过 builder 提供稳定的 Provider type，并创建 `AiResourceVectorIndex` 实例。
Router 至多选择一个 Provider，通过统一插件管理模型上报插件状态；未配置或没有可用
Provider 时使用 no-op 实现。

`available()` 表示当前实例是否可以执行向量操作，不代表标准资源或关系索引是否可用。
实现必须在 `close()` 中释放连接池、客户端和执行器。

## 3. 索引契约

SPI 支持按资源版本替换、添加文档、按资源删除、按资源版本删除和近邻搜索，并遵守以下规则：

- 替换和删除操作必须幂等；
- 在单个 Provider 内替换资源版本时，对外可见的只能是完整旧版本或完整新版本，不能出现
  部分文档集合；
- 文档 identity 包含 namespace、resource type、resource name、version、model 和
  chunk identity；
- 搜索限定在 namespace 内，并可进一步限定 resource type；
- 返回 hit 必须标识标准资源与 chunk，并包含 Provider similarity score；
- 协议专属 DTO、URL、trust manifest、可见性判断和最终排序不属于 Vector SPI。

协议无关的 AI discovery 服务负责合并向量与关键词召回，并执行生命周期、可见性、
最终排序和分页。

## 4. Schema 归属

每个实现负责自身可选数据库对象和迁移脚本。默认 PostgreSQL 实现负责
`pg-ard-vector-schema.sql`，其中包括 pgvector 扩展和
`ai_resource_ard_embedding_pg` 表。

Nacos PostgreSQL 主数据源 Schema 不得创建 pgvector 扩展或 embedding 表。因此，全新
部署可以在未安装 pgvector 的 PostgreSQL 上运行；当向量 discovery 未开启时，没有扩展
创建权限的数据库用户也可以启动 Nacos。

运维人员需要在所选实现的数据源中显式初始化对应 Schema。实现只有在确认所需扩展、表、
维度和索引兼容后，才可以报告为 available。

## 5. 一致性与失败处理

关系 ARD 索引与所选向量索引之间不使用分布式事务。AI 模块中的持久化幂等 indexing
consumer 根据标准资源状态驱动两类索引。向量处理失败时任务保持可重试，且不能回滚已经
提交的标准资源写入。

Consumer 对瞬时失败执行有界退避重试。周期性 reconciliation 用于发现缺失、部分写入、
过期或模型不匹配的向量数据。切换 embedding model 或 Vector Provider 时，必须重建受影响
文档。实现需要暴露 reconciliation 所需的健康状态和已索引 identity 信息，但不能向协议
适配器泄漏 Provider 专属类型。

## 6. 安全与运维

- 连接凭据和 Provider secret 属于敏感配置，不得通过插件详情 API 返回或写入日志。
- Embedding 内容来源于标准资源，必须遵守与标准资源一致的 namespace 和数据处理边界。
- 实现必须限制 batch size、query limit、连接使用量和重试并发度。
- 插件不可用和索引延迟必须与标准资源写入健康状态分别观测。

## 7. 兼容性与测试

SPI 变更必须保持插件模块 Java 8 兼容，并遵循 Nacos 插件兼容规则。新增可选方法时，需要
提供向后兼容的默认实现，或作为协同兼容性变更处理。

SPI 契约测试覆盖 Provider 选择、no-op fallback、幂等 replace/delete、限定范围的搜索和
生命周期清理。默认 PostgreSQL 实现还需要测试 Schema 隔离、关闭向量能力时不依赖
pgvector、Provider 内部事务替换，以及模拟向量失败后的 reconciliation。

