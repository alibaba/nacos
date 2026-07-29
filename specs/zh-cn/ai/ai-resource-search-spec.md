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

同一任务行负责两个持久化阶段：

- `base_index` 收敛确定性关系分片及已配置的向量索引；
- `llm_enhancement` 替换可选的 AI 生成分片，再收敛完整资源版本的向量索引。

每个阶段使用 `pending`、`processing`、`retry` 和 `completed` 状态。成功行作为每个存活
资源的有界完成检查点保留，不记录任务历史。资源生命周期变更递增任务 revision，并从
`base_index` 重新开始。已领取的 revision 只有在仍持有任务行时才能推进、重试或完成。
进程失败后，其他节点可在 lease 过期后接管。

Enhancement 写入必须幂等。AI 生成的 chunk type 必须事务性替换，不能追加；向量索引按完整
资源版本替换。只有两类写入都成功后才能完成任务。Enhancement 配置 fingerprint 包含
provider endpoint、model、Prompt 版本、输出 Schema 版本及相关输出限制，但不得包含密钥。
Fingerprint 仅记录完成 Enhancement 时实际使用的配置，用于审计和问题诊断，不作为收敛
目标。配置变化不得重新调度已完成资源。资源生命周期任务在调度时持久化当时是否开启
Enhancement；只有明确请求 Enhancement 的任务才能从 `base_index` 推进到
`llm_enhancement`，执行 Enhancement 阶段时使用当时生效的配置。

Enhancement 关闭时，基础索引可以直接完成；开关已开启但配置不完整时，Enhancement 阶段
必须保留重试状态，不能当作关闭处理。

失败时保留重试状态。周期性 reconciliation 检测遗漏、部分、过期和孤儿基础索引。索引缺失
或不一致时按正常建索引流程重建，并根据修复任务调度时的 Enhancement 开关决定是否请求
Enhancement。索引已经一致时，不得仅因历史资源缺少 Enhancement 检查点而触发修复，因此
开启 Enhancement 不会导致历史数据全量刷新。同一资源已有活动任务时，reconciliation 必须
保留该任务持久化的 Enhancement 意图。检索只读取所配置索引已经收敛的 enabled document。

## 7. 资源边界

列表、聚合、reconciliation 和持久化任务轮询必须按有界数据库批次扫描关系状态。完成
可见性和当前版本校验后，列表分页在内存中只保留请求页及一条用于判断下一页的记录。

关键词和向量召回分别设置可配置的候选上限。任一通道超过上限时，检索必须明确失败，不得
返回静默截断的结果。运维可通过 `nacos.ai.resource.search.max-recall-candidates`
调整该上限。

## 8. 升级与初始化

如果部署环境曾在引入持久化重试之前创建 ARD 检索表，则在开启
`nacos.ai.ard.enabled` 前，必须使用当前数据库对应的 Schema 补齐以下三个协议无关关系表：

- `ai_resource_search_document`；
- `ai_resource_search_chunk`；
- `ai_resource_search_index_task`。

MySQL、PostgreSQL、Derby 和 Oracle 应分别使用匹配的当前主数据源 Schema。即使 document
和 chunk 表中已经存在数据，也必须创建 task 表；该表保存阶段、重试、租约、revision、
完成检查点和 Enhancement 请求/配置指纹，不能替代两个索引表。升级已有 task 表的部署必须
先增加 `task_stage`、`enhancement_requested` 和 `enhancement_fingerprint`。已有任务行
默认属于 `base_index` 阶段且 `enhancement_requested=false`；开启 Enhancement 不得修改
索引正常的历史检索数据。周期 reconciliation 只有在基础索引或已配置向量索引确实需要修复
时，才会对历史资源执行 Enhancement。对其他索引正常的历史资源执行 Enhancement，必须由
运维显式触发。

PostgreSQL 环境如果不开启默认向量插件，无需创建任何 pgvector 对象；如果开启，则必须另外
执行 `nacos-default-ai-vector-plugin` 自己维护的可选 Schema。

## 9. 兼容与测试

即使当前只有 ARD 消费者，内部检索命名、持久化模型、表、配置和 Vector SPI package 仍须
保持协议无关。

测试覆盖关键词和向量召回、排序、可见性、当前版本校验、cursor 分页、超过单页范围的全量
聚合、事务替换、两个持久化任务阶段、lease 恢复、过期 revision、Enhancement 幂等重试、
配置 fingerprint 记录但不全量重调度、生命周期 Enhancement 意图，以及仅对实际修复资源
执行 Enhancement 且不全量刷新历史数据的 reconciliation。各协议适配器分别测试自己的
请求语法和响应一致性。
