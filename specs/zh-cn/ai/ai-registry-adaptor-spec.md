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

# AI Registry 适配器规范

本文档定义 `ai-registry-adaptor` 模块的契约。该适配器将 Nacos AI Registry
资源以部分社区注册协议暴露出去，使已有 MCP、Skill 和 ARD 客户端可以发现 Nacos 管理的资源，
而不需要直接使用 Nacos v3 API。

## 1. 范围

AI Registry 适配器负责协议兼容面。它将 Nacos AI Registry 资源转换为外部 registry
响应形态，包括：

- 通过 MCP Registry v0 兼容只读 API 暴露 MCP Server 数据；
- 通过 skills CLI 与 well-known discovery 兼容端点暴露 Skill 数据；
- 通过 Agentic Resource Discovery（ARD）的搜索、探索、目录和 artifact 端点暴露
  Agent、Skill、Prompt 和 MCP 数据；
- 这些生态需要的协议特定分页、搜索、响应和文件读取行为。

适配器不负责标准 AI resource identity、生命周期、存储、可见性或发布规则。这些规则仍由
[AI Registry 规范](ai-registry-spec.md)、[MCP Server 规范](mcp-server-spec.md)、
[Skill 规范](skill-spec.md)及相关插件规范定义。

外部协议参考包括 [MCP Registry](https://modelcontextprotocol.info/tools/registry/)、
[skills.sh documentation](https://skills.sh/docs) 和
[Agent Skills Specification](https://agentskills.io/specification)，以及
[ARD Specification](https://agenticresourcediscovery.org/spec/)。Nacos 使用这些参考实现兼容，
不把它们作为自身标准资源模型的归属边界。

## 2. 启动与开关

适配器作为额外的 Spring Boot Web Context 运行，并使用独立 HTTP 端口。默认不开启；
只有至少一个兼容 registry surface 被显式开启时才启动：

| 配置项 | 默认值 | 作用 |
| --- | --- | --- |
| `nacos.ai.mcp.registry.enabled` | `false` | 开启 MCP Registry 兼容端点。 |
| `nacos.ai.skill.registry.enabled` | `false` | 开启 Skill registry 兼容端点。 |
| `nacos.ai.ard.enabled` | `false` | 只开启 ARD 端点；不拥有基础 AI 资源检索运行时。 |
| `nacos.ai.resource.search.enabled` | `true` | 在主 AI 模块中开启 RAD、ARD 和资源 API 共用的基础关系检索。 |
| `nacos.ai.registry.port` | `9080` | 适配器 Context 使用的 HTTP 端口。 |
| `nacos.ai.mcp.registry.port` | deprecated | 适配器端口的历史兼容配置。 |

用户必须主动开启该能力，因为适配器会额外占用端口，并暴露面向社区客户端的协议形态，
而不是面向 Nacos Admin、Console 或 Client API 消费者的标准接口。

`nacos.ai.ard.enabled=true` 且 `nacos.ai.resource.search.enabled=false` 时，服务端必须在启动配置
校验时明确失败，不能创建 ARD 专属索引。关闭 ARD 不影响 RAD 或资源专用
Search 的索引、任务和 Reconciliation。

关闭 ARD 时不能要求安装 PostgreSQL pgvector。AI 资源检索 document 和 chunk 元数据使用
主数据源；pgvector 扩展及 `ai_resource_search_embedding_pg` 表只能通过
`pg-ai-vector-schema.sql` 初始化到用于存储 embedding 的 PostgreSQL 数据源中。
该数据源既可以是 Nacos PostgreSQL 主数据源，也可以是独立数据源；主
`pg-schema.sql` 必须在未安装 pgvector 时仍可正常执行。

## 3. 安全边界

适配器端点必须被视为公共协议兼容端点。它们不是 v3 Nacos API，也不要求使用 v3
`Result<T>` 响应包裹。部分社区 registry 协议本身以公开发现为主要场景，或者不会携带
Nacos 鉴权信息。

因此：

- 除非运维人员明确希望暴露这些协议，否则适配器必须保持关闭；
- 当部署中包含非公开数据时，运维人员应通过可信网络、网关鉴权、TLS、限流或其他外部
  保护措施暴露适配器；
- 适配器端点只应暴露适合目标社区协议的数据；
- ARD 端点在适配器 Web Context 内参与 Nacos Open API 鉴权，同时保留 ARD 专属错误响应。

开启 Nacos 鉴权后，ARD 请求显式携带的凭据必须始终被校验。合法身份会传递到标准可见性
检查，因此调用方只能发现其有权读取的私有资源；被拒绝的凭据返回 HTTP 401 和
`UNAUTHENTICATED`。开启 AI 匿名访问时，未携带凭据的请求使用系统保留的匿名身份，且只能
发现公开资源；否则缺失凭据同样返回 HTTP 401。请求上下文过滤器和鉴权过滤器必须注册在
独立的适配器 Web Context 中，因为主服务 Web Context 的过滤器不会被同级 Context 继承。

## 4. MCP Registry 兼容

当 `nacos.ai.mcp.registry.enabled=true` 时，适配器暴露 MCP Registry 兼容只读端点：

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| `GET` | `/v0/servers` | 按 cursor、limit、search 和可选 Nacos `namespaceId` 列出 MCP servers。 |
| `GET` | `/v0/servers/{name}/versions` | 列出指定 server 的版本。 |
| `GET` | `/v0/servers/{serverName}/versions/{version}` | 返回指定 server 版本。特殊版本 `latest` 在底层 MCP service 支持时进行解析。 |

响应模型遵循 MCP Registry 风格的 server list 和 server response 对象。Nacos 将 MCP
metadata、version、packages、icons、website、repository、tools 和 endpoints 映射到
registry 响应形态。当 frontend endpoint 和 backend endpoint 同时存在时，优先使用
frontend endpoint。Endpoint 数据会根据 MCP front protocol 转换为 registry `remotes`，
例如 streamable HTTP 或 SSE。

`namespaceId` 是 Nacos 扩展字段。未传入时，适配器可以按确定性的 namespace 顺序跨
namespace 搜索。这使 Nacos 可以作为内部 MCP subregistry 使用，同时标准 MCP 资源模型仍由
[MCP Server 规范](mcp-server-spec.md)定义。

适配器当前只暴露读取与发现行为。MCP 创建、发布、治理和删除仍属于 Nacos Admin、Console
或 Maintainer SDK 的职责。

## 5. Skill Registry 兼容

当 `nacos.ai.skill.registry.enabled=true` 时，适配器暴露兼容 skills CLI 与 well-known
registry 用法的 Skill discovery 端点：

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| `GET` | `/registry/{namespaceId}/.well-known/agent-skills/index.json` | 以 Agent Skills discovery v0.2.0 形态返回 namespace 下的 Skill index。 |
| `GET` | `/registry/{namespaceId}/.well-known/skills/index.json` | 以 legacy v0.1 兼容形态返回 namespace 下的 Skill index。 |
| `GET` | `/registry/{namespaceId}/api/search` | 搜索可导出的 Skills，返回 CLI 兼容搜索结果。 |
| `GET` | `/registry/{namespaceId}/.well-known/agent-skills/{skillName}/SKILL.md` | 返回导出的 `SKILL.md`。 |
| `GET` | `/registry/{namespaceId}/.well-known/skills/{skillName}/SKILL.md` | 导出 `SKILL.md` 的别名。 |
| `GET` | `/registry/{namespaceId}/.well-known/agent-skills/{skillName}.zip` | 为 v0.2.0 `archive` 条目返回导出的 Skill archive。 |
| `GET` | `/registry/{namespaceId}/.well-known/skills/{skillName}.zip` | 为已经解析到 legacy base path 的客户端提供 archive 别名。 |
| `GET` | `/registry/{namespaceId}/.well-known/agent-skills/{skillName}/**` | 返回导出的文本资源。 |
| `GET` | `/registry/{namespaceId}/.well-known/skills/{skillName}/**` | 导出文本资源的别名。 |

`/.well-known/agent-skills/index.json` 是主要的 Skill well-known discovery surface。它必须返回
顶层 `$schema` 字段，取值为
`https://schemas.agentskills.io/discovery/0.2.0/schema.json`。每个条目必须包含
`name`、`description`、`type`、`url` 和 `digest`。当 Skill 只包含 `SKILL.md`
时，Nacos 应使用 `type=skill-md`，并将 `url` 指向 `{skillName}/SKILL.md`；
当 Skill 包含可导出的文本资源时，Nacos 应使用 `type=archive`，并将 `url` 指向
`{skillName}.zip`。`digest` 是 artifact 原始字节的 SHA-256 摘要，格式为
`sha256:{hex}`。Nacos 可以包含已解析 latest `version` 等非标准扩展字段；按照 discovery
协议，客户端必须忽略未知字段。

`/.well-known/skills/index.json` 保留为 legacy 兼容面。它不返回 `$schema`，并继续以
`files` 数组描述每个 Skill，使 v0.1 兼容客户端可以继续从 `/{skillName}/{file}` 路径获取
`SKILL.md` 和文本资源。

适配器只导出适合公开发现语义的 Skills：

- Skill 已启用；
- Skill scope 为 public；
- 至少存在一个 online 版本；
- name 和 description 存在；
- latest label 可以解析到可用版本；
- 导出资源为文本资源。当前兼容面不导出二进制资源。

标准包与生命周期规则由 [Skill 规范](skill-spec.md)定义。适配器只负责将符合条件的
Nacos Skills 转换为社区 discovery 形态。

## 6. Agentic Resource Discovery 兼容

Nacos 以 ARD 上游 commit
[`5fa2f5aef790b478319f6a3b43adf4661b0ed0e0`](https://github.com/ards-project/ard-spec/commit/5fa2f5aef790b478319f6a3b43adf4661b0ed0e0)
对应的草案为兼容基线。由于草案的版本标签仍可能变化，仓库内固定的 OpenAPI、JSON Schema
和一致性测试 fixture 均以该 commit 为准，而不是只依赖版本标签。

当 `nacos.ai.ard.enabled=true` 时，适配器通过自身 Web Context 暴露以下 ARD discovery
端点：

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| `POST` | `/v3/ai/ard/search` | 搜索最新 online 的 Agent、Skill、Prompt 和 MCP 资源。 |
| `POST` | `/v3/ai/ard/explore` | 返回可发现资源的 facets。 |
| `GET` | `/v3/ai/ard/agents` | 按过滤条件和分页参数列出可发现资源。 |
| `GET` | `/v3/ai/ard/ai-catalog.json` | 返回 namespace 维度的 catalog。 |
| `GET` | `/v3/ai/ard/artifacts` | 返回 catalog 条目引用的版本化 artifact。 |
| `GET` | `/.well-known/ai-catalog.json` | 返回 host 维度的 ARD catalog。 |

即使 MCP 和 Skill 兼容开关均未开启，ARD 总开关也会启动适配器 Context。

### 6.1 协议契约

ARD 请求和响应模型属于该适配器，因为它们是外部协议契约，而不是 Nacos Client API
标准模型。模型必须遵循固定版本的上游 Schema：

- 列表响应使用 `items`，不得将其重命名为 `results`；
- 搜索结果的 `score` 为 0 到 100 的整数；
- 搜索结果的 `source` 为标识当前 registry 的绝对 URI；
- ARD 错误严格使用 `{ "errorCode": "...", "message": "..." }`，不得包裹为
  Nacos `Result<T>`；
- 凭据缺失或被拒绝时使用 HTTP 401 和 `UNAUTHENTICATED` error code；
- 生成的 `trustManifest` 如果存在，必须包含必填的 `identity`；未配置合法 identity
  时，适配器不返回该 manifest；
- catalog、list 和 search 使用独立 DTO，避免 search 专属字段出现在 catalog 条目中。

ARD Controller 使用协议专属异常处理。Nacos v3 Controller 注解和 Nacos API 异常包裹
不适用于这些路径。参数校验、资源不存在、访问拒绝和未预期异常必须按照固定版本 ARD
OpenAPI 转换为对应 HTTP 状态码和 error code。

### 6.2 Artifact 归属

ARD 响应生成的所有 artifact URL 均指向公开适配器 base URL 下、由适配器自身提供的
`/v3/ai/ard/artifacts` 端点。除非契约中引入独立且显式配置的主服务 base URL，否则不得
指向主服务 Controller。

`nacos.ai.ard.catalog.base-url` 表示完整的公开适配器 base URL，实现不得再向其追加
Nacos 主服务 context path。

对于 Skill 资源，artifact 端点以 `application/agent-skills+zip` 返回完整 Skill
压缩包，其中包含 `SKILL.md` 及其打包资源。Prompt 和 MCP artifact 使用各自协议规定的
表示形式。Agent 支持：

```text
application/a2a-agent-card+json
application/vnd.nacos.ai-agent+json
```

前者只在 common latest 精确 Version 中存在可完整导出的合法 A2A Agent Card 时可用，响应只
包含该原生 Agent Card。后者返回版本化、协议无关的 Nacos Agent 定义，不包含 Runtime Endpoint、
健康、Publisher、心跳、owner、scope 或审批状态。Artifact URL 必须包含精确 Version、该
Version 的 `contentDigest` 和 representation key；Version offline、digest 不匹配或表示不可用时
返回 ARD not found。Nacos 表示必须通过
[`NacosAgentArtifact`](../../schemas/ai/agent/0.2.0/agent-artifact.schema.json#/$defs/NacosAgentArtifact)
校验。默认 Nacos 主服务运行在 8848、适配器运行在 9080 时，无需通过网关合并路径
也必须可以正常获取 artifact。

共享 Agent Search document 仍然每个逻辑 Agent 只有一个。其 `artifactKinds` 至少使用稳定 key
`a2a-agent-card` 和 `nacos-agent`。ARD media type filter 先映射为
`resourceType=agent + artifactKinds`，不能只按 `protocols` 判断。一个 ARD 请求对同一逻辑 Agent
最多返回一个 Entry：无 type filter 时，纯 A2A latest 优先 A2A 表示，多协议或自定义协议 latest
优先 Nacos 表示；有 type filter 时从请求允许且实际存在的表示中选择同一确定性 primary 顺序。
表示切换不改变逻辑 resource identifier，representation-specific Artifact URL 负责区分内容。
过滤和表示选择必须在 total 与 page token 计算前完成。

### 6.3 Discovery 边界

AI 模块负责 [AI 资源检索规范](ai-resource-search-spec.md)定义的协议无关能力。RAD、ARD、
通用 AI Resource Search 和资源专用 Search 共用该运行时；`nacos.ai.ard.enabled` 只控制
适配器协议面，不激活或关闭基础 Search Core。

可见性与当前版本校验必须在截取请求结果数量之前执行。列表和聚合按有界数据库批次扫描；
关键词和向量召回使用 AI 资源检索规范定义的边界，任一通道超过配置上限时必须明确失败。
Cursor 标识稳定的资源锚点，不使用易受结果变化影响的列表 offset。

适配器只负责校验和解析 ARD 请求、调用 AI 资源检索服务，并将标准结果和聚合转换为
ARD DTO。适配器不得直接访问 search repository，也不得重复实现召回、生命周期、可见性、
分页或聚合规则。ARD 特有的 facet 名称和值需要与标准聚合字段相互转换。

Search 接受固定版本 OpenAPI 定义的全部 federation 值：`auto`、`referrals` 和 `none`。
未传值时默认为 `auto`。在尚未配置上游 Registry 时，三种模式都执行本地检索；
`referrals` 返回空 referrals 数组，而不是拒绝请求。

`GET /agents` filter 支持以 `AND` 连接、值使用单引号包裹的等值表达式，以及使用 ISO
date 或 instant 的时间比较，例如 `createdAfter > '2026-01-01'` 和
`createdAfter > '2026-01-01T00:00:00Z'`。解析必须感知引号边界。Search 和 explore
filter 接受语法合法的点分字段路径，包括固定 OpenAPI 中的
`trustManifest.attestations.type` 示例；路径对应的值不存在时不匹配任何条目。不支持的
扩展字段路径分段可以使用任意非空、非空白且不含点号的属性名。不支持的操作符、错误的
引号、非法字段路径语法和旧分隔符语法必须返回 ARD invalid-argument，不能只解析其中一部分。

Catalog identifier 对每个来自 Nacos 的 URN segment 使用确定性、无碰撞且符合 Schema
字符集的编码。包含空格、斜杠、Unicode 或标点的 namespace 与资源名必须保持可区分，
并通过固定版本 catalog Schema 校验。

Namespace catalog 必须包含完整的合法资源集，通过标准 list service 连续分页，不能在
100 条时静默停止。Host 级 well-known catalog 仍只包含 registry 条目。

Explore facet 必须在可见性、当前版本和请求过滤后，对完整的有效结果集进行聚合，不能基于
单页或固定长度的候选前缀计算。Publisher、source 和 trust identity 等协议常量由适配器
在标准聚合之后补充。

### 6.4 索引一致性

标准资源写入是事实来源。单个资源对应的 search document、chunks 和内嵌 facets 必须原子
替换：删除旧索引行、插入新 document、插入全部 chunks 必须在同一个数据源事务中完成。

关系索引与向量索引之间不要求分布式事务。AI 模块改为记录资源级、幂等、持久化的索引任务，
任务以 namespace、resource type 和 resource name 为键。Consumer 重新读取标准资源状态，
替换或删除关系索引，再使所选向量索引收敛。只有已配置的两类索引均完成收敛，任务才算完成。

失败任务回到 `pending`，并保留 retry count、Unix Epoch 毫秒形式的 next execution 与
lease 截止点，以及 last error。周期性 reconciliation 用于发现遗漏的生命周期事件，并
分别校验关系索引与向量索引状态。只记录日志并吞掉索引异常不构成一致性机制，仅依靠启动
backfill 也不充分。

`ai_resource_task` 保存合并后的 `search_index` 任务 revision、版本化 Payload 和 Result，
以及重试状态。任务完成和重试更新都必须带 revision 条件，避免旧租约结束时误删并发产生的
新变更。向量替换期间，关系 document 保持 `pending`，检索只读取 `enabled` document；
Vector Provider 完成替换后，Consumer 才启用关系 document。Reconciliation 还需要比较
embedding model、向量文档数量与关系 chunks，并调度缺失、部分写入、过期、模型不一致及
孤儿索引。

### 6.5 一致性测试

适配器模块保存固定版本的上游 OpenAPI 和 Schema 测试 fixture，并记录来源和许可证信息。
自动化测试必须使用这些 fixture 校验序列化响应。端到端测试还需要针对真实适配器 Web
Context 运行固定版本的官方 conformance runner，或运行从该 runner 等价转换出的测试矩阵。

集成测试至少覆盖列表 `items`、整数搜索 score、URI `source`、trust identity 行为、
协议错误体、catalog schema，以及主服务和适配器使用不同端口时的 Skill ZIP 获取。Agent
还必须覆盖纯 A2A、多协议、只有旧 online Version 支持 A2A、两种 media type filter、稳定逻辑
identifier、representation-specific URL、offline/digest 失效，以及 Artifact 不包含 Runtime 状态。

## 7. 兼容规则

- 在适配器路径上，外部协议兼容性优先于 Nacos v3 响应约定。
- 标准 Nacos API 仍是管理语义的事实来源。
- ARD 兼容性变更必须同时更新固定的上游 revision、仓库内 fixture、本规范和一致性测试。
- 社区协议变化很快。当 MCP Registry、skills CLI、skills.sh 或 well-known Skill
  discovery 格式变化时，适配器可能需要不兼容调整。
- 当上游协议引入不兼容字段、分页、鉴权或路由变化时，兼容行为应明确版本化或文档化。

## 8. 待处理问题

- 为需要暴露兼容协议但又不希望数据公开的运维场景定义稳定的适配器鉴权模型。
- 跟踪 MCP Registry 版本变化，并明确未来是否支持写 API，还是继续将写操作保持在适配器
  范围之外。
- 跟踪 skills CLI 与 skills.sh 协议变化，包括是否支持更丰富的详情、审计或鉴权 API 形态。
- 定义通过网关和服务网格运行适配器的运维指引。
