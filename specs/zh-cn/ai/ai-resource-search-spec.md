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

# AI 资源检索规范

本文档定义 `ai` 模块负责的协议无关 AI 资源检索能力。该能力为 Nacos 标准 AI 资源建立
索引，并向协议适配器提供内部 application service，用于搜索、确定性列表、分页和聚合。

## 1. 范围与激活

AI Resource Search 是可复用的内部能力，不定义 Nacos HTTP API、Java SDK API、
Console API 或外部协议响应。

当前版本只有 ARD 适配器使用该能力，因此 `nacos.ai.ard.enabled` 同时激活 ARD 协议面
及其依赖的检索运行时。在出现其他消费者之前，不增加独立的运维检索开关。除激活关系外，
检索实现不得依赖 ARD 请求、响应、identifier、federation、media type 或 artifact 语义。

## 2. 所有权边界

AI 模块负责：

- 标准 search document 和 chunk；
- 持久化索引任务与 reconciliation；
- 关键词召回和可选向量召回；
- 排序及确定性 tie-breaking；
- 可见性 query advice 和逐资源可见性校验；
- latest label 和当前 online version 校验；
- 标准 filter、不透明 cursor 分页和完整结果集聚合。

协议适配器负责请求解析、协议校验、协议 filter 转换、identifier、media type、响应 DTO、
错误响应、artifact URL 和 federation 行为。

## 3. 检索模型

内部 query model 包含 namespace、text、标准 resource type、标准 filter、时间边界、
排序、page size 和不透明 cursor，不得包含协议 DTO 或协议专属字段名。

检索结果使用 application DTO，而不是持久化实体。结果可以暴露标准资源键、当前版本、
展示与检索元数据、时间戳和相关度分数。数据库行 ID、索引状态和任务状态保留在检索实现内部。

可见性和当前版本校验必须在 page limit 之前完成。Cursor 使用稳定资源锚点，不能使用可变
列表 offset。

## 4. 聚合

聚合必须基于完整的合法匹配集，而不是单个结果页。服务按消费者需要返回标准 bucket value、
count、超过 bucket limit 的数量和匹配总数。

协议派生值由适配器映射。例如，适配器可以将标准 resource type bucket 转换成 ARD media
type，而不把 ARD media type 语义放入 AI 模块。

## 5. 索引与 Schema

关系检索索引使用协议无关对象：

- `ai_resource_search_document`；
- `ai_resource_search_chunk`；
- `ai_resource_search_index_task`。

所有支持的主数据源都提供这些关系对象。可选 PostgreSQL vector 实现负责独立的 pgvector
Schema 和 `ai_resource_search_embedding_pg` 表。主数据源 Schema 不得创建 pgvector
扩展或 embedding 表。

标准资源写入始终是事实来源。Search document 和 chunk 属于可重建的派生状态。

## 6. 一致性

单个资源版本的关系 document 和 chunks 必须原子替换。关系索引与向量索引之间不要求分布式
事务；namespace、resource type 和 resource name 组成幂等持久化任务键。Consumer
重新读取标准资源状态，并收敛两类索引。

失败时保留重试状态。周期性 reconciliation 检测遗漏、部分、过期、模型错误和孤儿索引。
检索只读取所配置索引已经收敛的 enabled document。

## 7. 兼容与测试

即使当前只有 ARD 消费者，内部检索命名、持久化模型、表、配置和 Vector SPI package 仍须
保持协议无关。

测试覆盖关键词和向量召回、排序、可见性、当前版本校验、cursor 分页、超过单页范围的全量
聚合、事务替换、持久化重试和 reconciliation。各协议适配器分别测试自己的请求语法和响应
一致性。
