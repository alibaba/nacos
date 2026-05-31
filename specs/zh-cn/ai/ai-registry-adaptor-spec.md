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
资源以部分社区注册协议暴露出去，使已有 MCP 和 Skill 客户端可以发现 Nacos 管理的资源，
而不需要直接使用 Nacos v3 API。

## 1. 范围

AI Registry 适配器负责协议兼容面。它将 Nacos AI Registry 资源转换为外部 registry
响应形态，包括：

- 通过 MCP Registry v0 兼容只读 API 暴露 MCP Server 数据；
- 通过 skills CLI 与 well-known discovery 兼容端点暴露 Skill 数据；
- 这些生态需要的协议特定分页、搜索、响应和文件读取行为。

适配器不负责标准 AI resource identity、生命周期、存储、可见性或发布规则。这些规则仍由
[AI Registry 规范](ai-registry-spec.md)、[MCP Server 规范](mcp-server-spec.md)、
[Skill 规范](skill-spec.md)及相关插件规范定义。

外部协议参考包括 [MCP Registry](https://modelcontextprotocol.info/tools/registry/)、
[skills.sh documentation](https://skills.sh/docs) 和
[Agent Skills Specification](https://agentskills.io/specification)。Nacos 使用这些参考
实现兼容，不把它们作为自身标准资源模型的归属边界。

## 2. 启动与开关

适配器作为额外的 Spring Boot Web Context 运行，并使用独立 HTTP 端口。默认不开启；
只有至少一个兼容 registry surface 被显式开启时才启动：

| 配置项 | 默认值 | 作用 |
| --- | --- | --- |
| `nacos.ai.mcp.registry.enabled` | `false` | 开启 MCP Registry 兼容端点。 |
| `nacos.ai.skill.registry.enabled` | `false` | 开启 Skill registry 兼容端点。 |
| `nacos.ai.registry.port` | `9080` | 适配器 Context 使用的 HTTP 端口。 |
| `nacos.ai.mcp.registry.port` | deprecated | 适配器端口的历史兼容配置。 |

用户必须主动开启该能力，因为适配器会额外占用端口，并暴露面向社区客户端的协议形态，
而不是面向 Nacos Admin、Console 或 Client API 消费者的标准接口。

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

## 6. 兼容规则

- 在适配器路径上，外部协议兼容性优先于 Nacos v3 响应约定。
- 标准 Nacos API 仍是管理语义的事实来源。
- 社区协议变化很快。当 MCP Registry、skills CLI、skills.sh 或 well-known Skill
  discovery 格式变化时，适配器可能需要不兼容调整。
- 当上游协议引入不兼容字段、分页、鉴权或路由变化时，兼容行为应明确版本化或文档化。

## 7. 待处理问题

- 为需要暴露兼容协议但又不希望数据公开的运维场景定义稳定的适配器鉴权模型。
- 跟踪 MCP Registry 版本变化，并明确未来是否支持写 API，还是继续将写操作保持在适配器
  范围之外。
- 跟踪 skills CLI 与 skills.sh 协议变化，包括是否支持更丰富的详情、审计或鉴权 API 形态。
- 定义通过网关和服务网格运行适配器的运维指引。
