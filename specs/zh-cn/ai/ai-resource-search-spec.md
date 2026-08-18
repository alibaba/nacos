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

AI Resource Search 是 `ai` 模块内可复用、协议无关的逻辑能力。RAD、ARD、通用 AI Resource
Search 和资源专用 Search 都必须复用该能力，不得分别维护索引或搜索引擎。HTTP、Java SDK、
Console 和外部协议响应由各自的 API 或适配器规范定义。

基础关系检索运行时由 `nacos.ai.resource.search.enabled` 独立激活，默认值为 `true`。
`nacos.ai.ard.enabled` 只控制 ARD Web Context 和协议端点，不得关闭 RAD 或其他资源 API
依赖的检索运行时。`nacos.ai.ard.enabled=true` 且
`nacos.ai.resource.search.enabled=false` 是非法组合，服务端必须在启动配置校验时明确失败，
不能隐式创建一套 ARD 专属索引。检索实现不得依赖 ARD 请求、响应、identifier、federation、media type 或
artifact 语义。

## 2. 所有权边界

AI 模块负责：

- 标准 search document、chunk 和 facet 投影；
- 资源类型处理器注册、Query Planner 和多召回通道结果融合；
- 持久化索引任务与 reconciliation；
- 关键词召回和可选向量召回；
- 排序及确定性 tie-breaking；
- 可见性 query advice 和逐资源可见性校验；
- latest label 和当前 online version 校验；
- 类型化 predicate、不透明 cursor、numbered page 和完整结果集聚合；
- 每种资源投影代际的 readiness。

协议适配器或资源专用 API Facade 负责请求解析、协议校验、类型专用 filter 转换、响应 DTO
和错误映射。identifier、media type、artifact URL 和 federation 行为仅属于对应协议适配器。

## 3. 检索模型

内部 query model 包含 namespace、text、一个或多个标准 resource type、类型化 predicate、
时间边界、排序及 cursor 或 numbered page 信息，不得包含协议 DTO 或协议专属字段名。

标准 predicate 至少支持：

```text
field
operator = EXACT_ANY | EXACT_ALL | LITERAL_CONTAINS
values[]
caseSensitive
```

同一个 predicate 内由 operator 定义 ANY、ALL 或 literal contains；多个 predicate 使用 AND
组合。`metadata.<facetKey>` 是协议无关 facet 路径。实现必须把 `%`、`_` 和 escape char 当作
普通输入字符，不能让数据库 LIKE 通配符改变 literal contains 语义。类型专用 Facade 可以固定
resource type、允许的字段、大小写和字段权重，但不得改变公共可见性、enabled 和 currentness
规则。现有协议 filter 可保留兼容入口，但进入 Search Core 前必须转换为标准 predicate。

检索结果使用 application DTO，而不是持久化实体。结果可以暴露标准资源键、当前版本、
展示与检索元数据、时间戳和相关度分数。数据库行 ID、索引状态和任务状态保留在检索实现内部。

可见性、enabled 和当前版本校验必须在 total、offset 和 page limit 之前完成。Cursor 使用稳定
资源锚点，不能使用可变列表 offset。Numbered page 返回 `totalCount`、`pageNumber`、
`pagesAvailable` 和当前页 items；实现可以流式跳过 offset，但不得先截断候选再计算 total。

通用 Search 支持跨资源类型统一召回和排名；资源专用 Search 在 Query Planner 中固定一个
resource type，并可以使用类型专用 predicate 和权重。通用 Search 只指定一个 resource type
时，其候选资格、可见性和当前性结果必须与对应资源专用 Search 一致。跨类型 Search 不得通过
拼接多个已经分页的专用结果实现。

## 4. 聚合

聚合必须基于完整的合法匹配集，而不是单个结果页。服务按消费者需要返回标准 bucket value、
count、超过 bucket limit 的数量和匹配总数。

协议派生值由适配器映射。例如，适配器可以将标准 resource type bucket 转换成 ARD media
type，而不把 ARD media type 语义放入 AI 模块。

## 5. 索引与 Schema

关系检索索引使用协议无关对象：

- `ai_resource_search_document`；
- `ai_resource_search_chunk`；
- `ai_resource_task`。

所有支持的主数据源都提供这些关系对象。`ai_resource_task` 可供 AI 资源域内的持久化异步
任务复用，但不是 Nacos 全局工作流引擎。每种任务类型拥有自己的版本化 JSON 输入和结果
Schema；逻辑 JSON 使用 text 或 CLOB 存储，不使用数据源专属的原生 JSON 类型。可选
PostgreSQL vector 实现负责独立的 pgvector Schema 和
`ai_resource_search_embedding_pg` 表。主数据源 Schema 不得创建 pgvector 扩展或
embedding 表。

标准资源写入始终是事实来源。Search document 和 chunk 属于可重建的派生状态。

逻辑 `IndexProjection` 由一个 document、零到多个 chunk 和 facet 集合组成：

- document 保存资源身份、展示信息、状态、当前版本、source digest 和稳定排序字段；
- facet 保存精确过滤属性。第一代实现可以将通用 key/value 或 array facet 保存在 document
  metadata 中，不要求立即增加物理表；
- chunk 只保存关键词或向量召回所需的文本内容。Facet 不生成独立 chunk，也不进入 embedding；
- 结构化 document/facet 和关键词索引是基础 Search 的必选组成，向量索引是可选召回通道；
- Agent 等资源只能填充自己拥有的 facet，不能要求 Skill、Prompt、MCP 或 AgentSpec 增加
  Agent 专属列。

所有召回通道由同一个 Query Planner/Fusion 负责候选生成、结构化过滤、去重、分数融合、
可见性、当前性和分页。向量 Provider 不能下推 facet 时，Planner 必须使用有界且可证明完整的
候选策略；禁止对固定 top-K 结果只做一次后过滤后声称 total 或分页完整。

### 5.1 资源类型处理器

每个声明可检索的资源类型必须注册协议无关的类型处理器，至少提供以下语义：

```text
resourceType()
project(namespaceId, resourceName) -> Optional<IndexProjection>
scan(namespaceId, cursor, batchSize) -> SourcePage
isCurrent(document) -> boolean
exists(namespaceId, resourceName) -> boolean
```

`project` 返回空表示资源不存在、不可发现或没有合法当前版本，索引服务删除该逻辑资源的派生
文档。`scan` 只用于 Backfill/Reconciliation，必须按稳定资源键有界扫描。`isCurrent` 必须执行
该资源的 enabled、可见性、当前版本和 source digest 校验。处理器属于 `ai` 模块，不能引用
ARD DTO、URL 或 media type。

本规范声明的可检索类型为 Agent、AgentSpec、Skill、Prompt 和 MCP。若将来某个 AI Resource
不参加通用 Search，必须在该资源规范中明确声明；不能仅因为尚未实现处理器而静默漏掉。

### 5.2 Agent 投影

每个 `(namespaceId, agentName)` 最多维护一个 enabled document。其 `resourceVersion` 是
common `latest` 指向的精确 online Version。Agent document 至少投影：

- display name、description、business tags、provider、icon 和 scope 等目录字段；
- 全部 online Version 的有序紧凑 version catalog；
- 全部 online Version 的 protocol 有序去重并集 `metadata.protocols`；
- common latest 精确 Version 可完整导出的表示 key 集合 `metadata.artifactKinds`；
- `metadata.projectionVersion` 和由稳定业务事实生成的 `sourceDigest`。

`protocols` 表示调用协议，`artifactKinds` 表示可完整返回的版本制品，两者不得混用。Agent name、
description、tags、能力和示例可以生成 chunk；scope、owner、status、protocols 和
artifactKinds 仅作为结构化字段。Runtime Endpoint、健康状态、Publisher、心跳和 Runtime
revision 永远不进入持久搜索索引。

Agent source digest 使用 canonical JSON 的 SHA-256，覆盖影响目录或检索投影的 Agent metadata、
完整 version catalog、common latest、latest Version `contentDigest`、artifactKinds 和
projection version；无语义的修改时间不单独触发 digest 变化。

标准资源标识字段和任务控制字段必须与标准资源存储保持一致，使用精确且大小写敏感的比较。
关键词匹配继续通过与 Locale 无关的查询规范化实现大小写不敏感，不得依赖数据源专属的表级
大小写不敏感排序规则。

## 6. 一致性

单个逻辑资源的关系 document、chunks 和内嵌 facets 必须原子替换。关系索引与向量索引之间不要求分布式
事务；系统使用幂等的 `search_index` 持久化任务重新读取标准资源状态并收敛两类索引。任务
key 是 task type、namespace 以及由 resource type 和 resource name 组成的逻辑 subject
的 SHA-256。Key 包含 task type，因此其他 AI 资源工作流复用同一张表时不会与检索索引任务
冲突。

标准资源生命周期事务成功提交后，按 `(namespaceId, resourceType, resourceName)` 调度合并任务。
调度失败不得回滚已经成功的事实写入，由指标、告警和 reconciliation 修复。Agent 创建、目录
metadata 或治理字段变化、Version publish/online/offline/delete、common latest 或自定义 label
变化、legacy A2A facade 产生的 canonical 定义变化，以及 Agent 删除都必须调度。Endpoint
register、deregister、heartbeat、健康变化和 Runtime revision 不得调度目录索引任务。

同一任务行负责两个持久化阶段：

- `base_index` 收敛确定性关系分片及已配置的向量索引；
- `llm_enhancement` 替换可选的 AI 生成分片，再收敛完整资源版本的向量索引。

每个阶段使用 `pending`、`processing` 和 `completed` 状态。首次执行和可重试任务均使用
`pending`；通过 `retry_count`、`next_execute_at` 和 `last_error` 区分延迟重试与新
任务。成功行作为每个存活资源的有界完成检查点保留，不记录任务历史。资源生命周期变更递增
任务 revision，并从 `base_index` 重新开始。已领取的 revision 只有在仍持有任务行时才能
推进、重试或完成。Revision 表示已调度的任务内容，领取任务不得递增 revision。每次领取
成功都必须递增独立且单调的 `lease_token`；续租、状态迁移和 superseded work 释放必须
比较该 token，过期 worker 不得修改或释放后来 worker 的租约。生命周期任务无论连续合并
多少次更新，都必须保留尚未过期的租约；替代 revision 只有在当前 token 持有者释放租约或
租约过期后才能被领取。进程失败后，其他节点可在 lease 过期后接管。Enhancement 任务回退到
`base_index` 同样必须使用 revision 和 lease token 条件，旧 worker 不得覆盖更新的生命周期
revision 或后来一次领取。
基础阶段和 Enhancement 阶段都必须通过独立于轮询线程的执行器续租；领取的 Enhancement
任务数不得超过已配置的 worker 并发数。

检索索引任务输入保存在 `task_payload` 中，必须包含 `schemaVersion`、保存 resource type
和 resource name 的 `subject`，以及 `options.enhancementRequested`。调度新 revision
时整体替换 Payload，该 revision 执行期间 Payload 保持不可变。Enhancement 完成元数据
保存在版本化 `task_result` 中，当前结果包含完成时的 Enhancement fingerprint。用于轮询、
领取、重试、lease 接管、revision 防并发覆盖和 lease token fencing 的调度元数据继续使用
独立关系列。

无法解析或 Schema 版本不支持的任务 Payload 必须以带解码错误的完成检查点隔离，不能导致
同一批其他到期任务失败或饥饿。若其基础索引不一致，后续 reconciliation 可以使用当前
Payload 重新打开该任务。

调度截止点以 Unix Epoch 毫秒保存到 BIGINT 类型的 `next_execute_at` 和
`lease_expire_at`。轮询、领取、重试和 lease 续期必须使用同一个注入的应用时钟生成比较
时间及截止点，不得混用 JVM 经 JDBC 转换的 timestamp 和数据源
`CURRENT_TIMESTAMP`。Nacos 集群节点应保持系统时钟同步。

Enhancement 写入必须幂等。AI 生成的 chunk type 必须事务性替换，不能追加；向量索引按完整
资源版本替换。只有两类写入都成功后才能完成任务。Enhancement 配置 fingerprint 包含
provider endpoint、model、Prompt 版本、输出 Schema 版本及相关输出限制，但不得包含密钥。
Fingerprint 仅记录完成 Enhancement 时实际使用的配置，用于审计和问题诊断，不作为收敛
目标。配置变化不得重新调度已完成资源。资源生命周期任务在调度时持久化当时是否开启
Enhancement；只有明确请求 Enhancement 的任务才能从 `base_index` 推进到
`llm_enhancement`，执行 Enhancement 阶段时使用当时生效的配置。

Enhancement 关闭时，仍处于 `base_index` 的任务可以直接完成。已经进入
`llm_enhancement` 的任务不得直接完成；它必须创建
`options.enhancementRequested=false` 的新 revision 并回到 `base_index`，以清除可能部分
写入的 Enhancement chunk、重新收敛基础向量索引，再作为基础索引检查点完成。之后重新开启
Enhancement 不得重新调度该已完成资源。开关已开启但配置不完整时，Enhancement 阶段必须回到
`pending` 并保留重试元数据，不能当作关闭处理。

失败时将当前阶段恢复为 `pending`，增加 `retry_count`，并按照指数退避设置
`next_execute_at`。周期性 reconciliation 检测遗漏、部分、过期和孤儿基础索引。索引
缺失或不一致时按正常建索引流程重建，并根据修复任务调度时的 Enhancement 开关决定是否
请求 Enhancement。索引已经一致时，不得仅因历史资源缺少 Enhancement 检查点而触发修复，
因此开启 Enhancement 不会导致历史数据全量刷新。同一资源已有活动任务时，
reconciliation 必须保留其 Payload 中持久化的 Enhancement 意图，以及 revision、阶段、
重试延迟和 lease。向量 reconciliation 除模型和 chunk 数量外，还必须比较关系 document
标识。检索只读取所配置索引已经收敛的 enabled document。每个类型处理器的 reconciliation
必须检测缺失、部分、过期和孤儿投影；Agent 还必须比较 projection version、source digest、
common latest、version catalog 和可选向量状态。无 online Version、disabled 或 deleted
资源的正确收敛结果是删除派生文档，而不是永久重试。

## 7. 资源边界

列表、聚合、reconciliation 和持久化任务轮询必须按有界数据库批次扫描关系状态。资源源扫描和
numbered list 使用稳定的 resource-key keyset；排序并列时使用不可变行键消除歧义。完成
predicate、可见性和当前版本校验后，列表分页在内存中只保留请求页及一条用于判断下一页的记录，
numbered page 只额外保留计数器，不能保留全部匹配项。
Reconciliation 不得在内存中保留全部标准资源名称。集群扫描 lease 必须记录 owner 和
过期时间，扫描期间通过 CAS 续租，并且只有相同 owner 仍持有 lease 时才能释放。

关键词和向量召回分别设置可配置的候选上限。任一通道超过上限时，检索必须明确失败，不得
返回静默截断的结果。运维可通过 `nacos.ai.resource.search.max-recall-candidates`
调整该上限。

## 8. Readiness 与读模式

需要从旧扫描路径切换到索引的资源类型，必须按 `(resourceType, projectionVersion)` 维护持久、
集群共享的 readiness。Backfill 扫描 lease 只表示当前扫描 owner，不能替代 readiness。

一个 projection generation 只有在以下条件全部满足时才能通过 CAS 标记为 `READY`：

1. 成功枚举全部有效 namespace，且没有用 `public` fallback 掩盖枚举失败；
2. 完成一轮该资源类型的有界 source scan，扫描差异均已成功调度；
3. 后续验证轮没有未修复的缺失、过期或孤儿文档；
4. 没有属于该轮的 pending、processing 或 retry 任务；
5. readiness record 写入当前 projection version。

`READY` 对同一 generation 是 sticky 的。普通生命周期任务短暂 pending 不把该 generation
退回未就绪；查询的 currentness 校验先排除陈旧文档，任务随后收敛。投影契约变化必须递增
projection version 并创建新的 readiness generation。

RAD Search 使用 `nacos.ai.rad.search.mode=AUTO|INDEX|SCAN` 选择读路径，默认 `AUTO`：

| 模式 | 未 READY | READY | 索引调用失败 |
|---|---|---|---|
| `AUTO` | 使用完整旧扫描路径 | 进程内 sticky 切换到索引 | 明确失败，不逐请求回退 |
| `INDEX` | 明确 service unavailable | 使用索引 | 明确失败 |
| `SCAN` | 使用旧扫描 | 始终使用旧扫描 | 不涉及索引 |

一次请求不得合并部分索引和部分旧扫描结果。切换不得改变 RAD 名称、Tag、Protocol、大小写、
排序、total、分页、可见性或 version catalog 契约。其他消费者若需要兼容读模式，必须在其协议
或 API 规范中定义，但仍复用同一 readiness。

## 9. 升级与初始化

如果部署环境曾在引入持久化重试之前创建 ARD 检索表，则在开启
`nacos.ai.resource.search.enabled` 前，必须使用当前数据库对应的 Schema 补齐以下三个协议无关关系表：

- `ai_resource_search_document`；
- `ai_resource_search_chunk`；
- `ai_resource_task`。

MySQL、PostgreSQL、Derby 和 Oracle 应分别使用匹配的当前主数据源 Schema。即使 document
和 chunk 表中已经存在数据，也必须创建 task 表；该表保存任务类型、版本化 Payload 和
Result、阶段、重试、租约、revision 和完成检查点，不能替代两个索引表。

已经创建 `ai_resource_search_index_task` 的部署必须在开启检索前迁移到
`ai_resource_task`。原 `resource_type`、`resource_name` 和
`enhancement_requested` 写入版本 1 的 `task_payload`；`enhancement_fingerprint`
写入版本 1 的 `task_result`；`attempt_count` 改为 `retry_count`；
`next_retry_time` 转换为 Unix Epoch 毫秒的 `next_execute_at`；原 `retry` 状态改为
`pending`。如果中间版本的 `ai_resource_task` 仍使用 timestamp 类型的
`next_execute_time` 和 `lease_until`，则必须转换为 Epoch 毫秒类型的
`next_execute_at` 和 `lease_expire_at`。已有 `ai_resource_task` 表还必须增加非空且
默认值为 `0` 的 BIGINT `lease_token` 列。迁移行的
`task_type` 为 `search_index`，并按包含 task type 的新规则重新生成 task key。在线升级时
必须保留已有任务意图。对于允许丢弃任务状态的未发布开发环境，也可以删除旧 task 表并使用
当前 Schema 重建，之后由 reconciliation 修复不一致的基础索引。

开启 Enhancement 不得修改索引正常的历史检索数据。周期 reconciliation 只有在基础索引
或已配置向量索引确实需要修复时，才会对历史资源执行 Enhancement。对其他索引正常的历史
资源执行 Enhancement，必须由运维显式触发。

PostgreSQL 环境如果不开启默认向量插件，无需创建任何 pgvector 对象；如果开启，则必须另外
执行 `nacos-default-ai-vector-plugin` 自己维护的可选 Schema。

新增资源类型必须先完成对应 projection generation 的 Backfill 和 readiness，再让 `AUTO` 或
`INDEX` 使用该索引。索引是可重建派生状态，不改变标准资源或 Runtime Endpoint 的事实源，也
不要求把 Runtime 状态迁移到关系检索表。

## 10. 兼容与测试

内部检索命名、持久化模型、表、配置和 Vector SPI package 必须保持协议无关。现有 ARD
Skill、Prompt 和 MCP 的请求、cursor、排序与 artifact 行为在共享内核扩展时保持兼容。

测试覆盖关键词和向量召回、结构化 facet、类型化 predicate、排序、可见性、当前版本校验、
cursor 和 numbered page、超过单页范围的全量聚合、通用单类型与资源专用 Search 一致性、
事务替换、两个持久化任务阶段、lease 恢复、过期 revision、Enhancement 幂等重试、
版本化任务 Payload 和 Result、task type 隔离、时区无关的 Epoch 调度、确定性时钟下的
lease 与重试边界、连续生命周期合并保留活动租约、基于 lease token 防止旧 worker 释放
新租约、配置 fingerprint 记录但不全量重调度、生命周期 Enhancement 意图，
仅对实际修复资源执行 Enhancement 且不全量刷新历史数据的 reconciliation、Agent lifecycle
调度与 Runtime Endpoint 非调度、readiness CAS/重启/新 generation，以及 `AUTO/INDEX/SCAN`
交叉行为。各协议适配器和资源 API 分别测试自己的请求语法、响应一致性和单类型交叉结果。
