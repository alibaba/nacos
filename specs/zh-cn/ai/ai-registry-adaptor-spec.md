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
  Skill、Prompt 和 MCP 数据；
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
| `nacos.ai.ard.enabled` | `false` | 开启 ARD 端点及其本地索引能力。 |
| `nacos.ai.registry.port` | `9080` | 适配器 Context 使用的 HTTP 端口。 |
| `nacos.ai.mcp.registry.port` | deprecated | 适配器端口的历史兼容配置。 |

用户必须主动开启该能力，因为适配器会额外占用端口，并暴露面向社区客户端的协议形态，
而不是面向 Nacos Admin、Console 或 Client API 消费者的标准接口。

关闭 ARD 时不能要求安装 PostgreSQL pgvector。ARD entry 和 chunk 元数据使用主数据源；
pgvector 扩展及 `ai_resource_ard_embedding_pg` 表只能通过
`pg-ard-vector-schema.sql` 初始化到用于存储 embedding 的 PostgreSQL 数据源中。
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
- 未来如果引入适配器级鉴权，必须兼容外部协议，且不能静默改变 Nacos v3 的标准鉴权语义。

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
| `POST` | `/v3/ai/ard/search` | 搜索最新 online 的 Skill、Prompt 和 MCP 资源。 |
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

对于 Skill 资源，artifact 端点以 `application/agent-skills+zip` 返回完整 Skill
压缩包，其中包含 `SKILL.md` 及其打包资源。Prompt 和 MCP artifact 使用各自协议规定的
表示形式。默认 Nacos 主服务运行在 8848、适配器运行在 9080 时，无需通过网关合并路径
也必须可以正常获取 artifact。

### 6.3 Discovery 边界

AI 模块负责协议无关的 discovery 应用服务。该服务负责：

- 关键词与向量召回；
- 排序与确定性的同分处理；
- 可见性 QueryAdvice 和逐资源可见性校验；
- latest label 与当前 online version 解析；
- 标准过滤与不透明 cursor 分页。

可见性与当前版本校验必须在截取请求结果数量之前执行。内部召回可以分批限制候选数量，
但必须持续取数，直到填满当前页或符合条件的结果耗尽。Cursor 标识稳定的资源锚点，
不使用易受结果变化影响的列表 offset。

适配器只负责校验和解析 ARD 请求、调用标准 discovery 服务，并将标准搜索结果转换为
ARD DTO。适配器不得直接访问 ARD index repository，也不得重复实现生命周期和可见性规则。
ARD 特有的 facet 名称和值可以在映射标准可见结果集时聚合。

### 6.4 索引一致性

标准资源写入是事实来源。单个资源对应的 ARD entry 和 chunks 必须原子替换：删除旧索引行、
插入新 entry、插入全部 chunks 必须在同一个数据源事务中完成。

关系索引与向量索引之间不要求分布式事务。AI 模块改为记录资源级、幂等、持久化的索引任务，
任务以 namespace、resource type 和 resource name 为键。Consumer 重新读取标准资源状态，
替换或删除关系索引，再使所选向量索引收敛。只有已配置的两类索引均完成收敛，任务才算完成。

失败任务保留 attempt count、next retry time、lease 和 last error 等重试状态。周期性
reconciliation 用于发现遗漏的生命周期事件，并分别校验关系索引与向量索引状态。只记录
日志并吞掉索引异常不构成一致性机制，仅依靠启动 backfill 也不充分。

`ai_resource_ard_index_task` 保存合并后的任务 revision 与重试状态。任务完成和重试更新
都必须带 revision 条件，避免旧租约结束时误删并发产生的新变更。向量替换期间，关系 entry
保持 `pending`，discovery 只读取 `enabled` entry；Vector Provider 完成替换后，Consumer
才启用关系 entry。Reconciliation 还需要比较 embedding model、向量文档数量与关系 chunks，
并调度缺失、部分写入、过期、模型不一致及孤儿索引。

### 6.5 一致性测试

适配器模块保存固定版本的上游 OpenAPI 和 Schema 测试 fixture，并记录来源和许可证信息。
自动化测试必须使用这些 fixture 校验序列化响应。端到端测试还需要针对真实适配器 Web
Context 运行固定版本的官方 conformance runner，或运行从该 runner 等价转换出的测试矩阵。

集成测试至少覆盖列表 `items`、整数搜索 score、URI `source`、trust identity 行为、
协议错误体、catalog schema，以及主服务和适配器使用不同端口时的 Skill ZIP 获取。

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
