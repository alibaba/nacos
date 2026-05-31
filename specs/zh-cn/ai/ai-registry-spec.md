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

# AI Registry 规范

AI Registry 是 Nacos 中负责 AI 资源注册、治理、发现和分发的领域。它是 Nacos
3.x 中与 Config、Naming 并列的一等能力。AI Registry 使用
[资源模型规范](../design/resource-model-spec.md)中定义的共享身份：
`namespaceId -> resourceType -> resourceName`。

## 1. 范围

AI Registry 负责：

- AI 资源元数据、版本、标签、状态、scope、owner 和业务标签；
- MCP Server、A2A Agent、Prompt、Skill、AgentSpec 等资源类型契约；
- 已支持 AI 资源的运行时查询和订阅行为；
- draft 创建、审核、发布、强制发布、上线/下线、删除、上传、导入和下载等管理流程；
- AI 发布流水线、存储插件、可见性、鉴权和 Trace 钩子的领域使用方式。

AI Registry 不负责：

- Config 资源语义，即使默认 AI 存储实现通过 Config 保存资源内容；
- Naming service 语义，即使 MCP 或 A2A endpoint 通过 Naming service 和 instance 表达；
- [AI Registry 适配器规范](ai-registry-adaptor-spec.md)暴露的社区 registry 协议定义；
- 插件扩展契约。流水线、存储、资源导入、可见性和 Trace 的扩展规则由对应插件规范定义。

## 2. 设计原则

- **以版本为中心**：标准模型基于 `AiResource` 元数据和
  `AiResourceVersion` 版本。新增资源类型应优先适配该模型，而不是引入自定义
  存储形态。
- **运行时与管理面分离**：Client API 和 SDK 应暴露运行时查询、endpoint 注册和
  订阅。Admin、Console 和 Maintainer SDK 负责宽范围列表、上传、发布治理和删除。
- **资源身份稳定**：`resourceType` 是第二层身份。AI 资源不应引入 Config 风格的
  `groupName` 身份，除非兼容路径必须保留。
- **插件组合**：可见性、存储、Trace 和发布流水线应通过插件组合，并在本领域规范中
  建立链接。外部资源导入也应使用相同插件模型，并把导入 artifact 路由回资源
  Operator。不应定义隐藏的 AI 专用扩展机制。
- **允许快速演进**：AI 协议和资源格式变化很快。当 MCP、A2A、Agent 包格式或
  模型工具生态变化时，规范可能需要不兼容或大幅调整。调整必须按照
  [兼容与废弃策略规范](../design/compatibility-deprecation-spec.md)说明迁移、兼容和废弃行为。

## 3. 标准 AI 资源模型

目标标准模型为：

```text
AiResource(namespaceId, type, name)
  -> AiResourceVersion(namespaceId, type, name, version)
```

`AiResource` 是元数据行，包含资源名、类型、描述、启用状态、namespace、owner、
可见性 scope、业务标签、来源、乐观锁 `metaVersion`、下载次数和 `versionInfo`
JSON。

`AiResourceVersion` 是版本行，包含作者、版本、版本状态、描述、存储 JSON、
发布流水线信息和下载次数。

字段和生命周期细则参见 [AI 资源模型规范](ai-resource-model-spec.md) 和
[AI 资源生命周期规范](ai-resource-lifecycle-spec.md)。

## 4. 资源类型清单

| Type | 标准身份 | 当前持久化形态 | 规范 |
| --- | --- | --- | --- |
| `mcp` | `namespaceId -> mcp -> mcpName` | 当前通过 Config 记录 MCP 元数据、版本、tool、resource 数据，通过 Naming service 表达 endpoint。 | [MCP Server 规范](mcp-server-spec.md) |
| `a2a` | `namespaceId -> a2a -> agentName` | 当前通过 Config 记录 AgentCard 元数据和版本数据，通过 Naming service 表达 endpoint。 | [A2A Agent 规范](a2a-agent-spec.md) |
| `prompt` | `namespaceId -> prompt -> promptKey` | 使用 `ai_resource`、`ai_resource_version` 和 AI 存储；旧 Prompt 数据可迁移。 | [Prompt 规范](prompt-spec.md) |
| `skill` | `namespaceId -> skill -> name` | 使用 `ai_resource`、`ai_resource_version`、AI 存储和轻量 discovery manifest。 | [Skill 规范](skill-spec.md) |
| `agentspec` | `namespaceId -> agentspec -> name` | 使用 `ai_resource`、`ai_resource_version` 和 AI 存储。 | [AgentSpec 规范](agentspec-spec.md) |

MCP 和 A2A 即使当前持久化尚未完全适配 `ai_resource`，也仍属于 AI Registry 资源。
它们的标准规范应以标准身份为准，并把当前兼容存储单独记录。

## 5. 接口面

AI Registry 通过多个接口面暴露：

| 接口面 | 受众 | 规则 |
| --- | --- | --- |
| `/v3/client/ai/...` | 运行时客户端和 Agent framework。 | 查询已知资源、下载运行时产物、订阅，以及注册客户端拥有的 endpoint。 |
| `/v3/admin/ai/...` | 管理工具和 Maintainer SDK。 | 创建、更新、列表、发布、删除、上传和版本运维。 |
| `/v3/console/ai/...` | Nacos 控制台。 | 围绕相同领域语义进行 UI 编排。 |
| gRPC AI requests | Java Client SDK 运行时流量。 | 查询和发布 MCP/A2A/Prompt 资源，并在支持时注册 endpoint。 |
| Java SDK | 运行时应用集成。 | 参见 [Java SDK 实现规范](../sdk/sdk-java-impl-spec.md)。 |
| Java Maintainer SDK | 类型化管理集成。 | 应与 Admin API 语义和资源类型规范保持一致。 |
| AI Registry 适配器 | 外部社区 registry 客户端。 | 独立端口上的可选兼容端点，参见 [AI Registry 适配器规范](ai-registry-adaptor-spec.md)。 |

## 6. 横切规则

- AI Registry API 必须遵循 [HTTP API 规范](../http-api/api-spec.md)中的 v3 响应、
  错误、鉴权和 API 类型规则。
- gRPC payload 必须遵循 [gRPC API 规范](../grpc-api/api-spec.md)。
- 运行时查询和订阅应优先通过版本或 label 路由，而不是宽范围资源列表。
- 可见性必须使用 [可见性插件规范](../auth/visibility-plugin-spec.md)。
- 发布流水线扩展行为必须使用
  [AI 发布流水线插件规范](../plugin/ai-pipeline-plugin-spec.md)。
- 资源存储扩展行为必须使用 [AI 存储插件规范](../plugin/ai-storage-plugin-spec.md)。
- 外部 AI 资源导入行为必须使用
  [AI 资源导入插件规范](../plugin/ai-resource-import-plugin-spec.md)。导入插件负责把
  运维配置的外部来源转换为导入 artifact；资源 Operator 负责把 artifact 应用到当前存储和
  生命周期模型。
- Trace 和审计事件应使用 [Trace 插件规范](../plugin/trace-plugin-spec.md)和共享
  可观测规则。

## 7. 待迁移问题

- MCP Server 应将持久元数据和版本模型从 Config 形态记录迁移到标准的
  `ai_resource` 和 `ai_resource_version` 模型，同时保留现有数据兼容。
- A2A Agent 应将 AgentCard 元数据和版本数据从 Config 形态记录迁移到标准 AI
  资源模型。
- Prompt 已有从旧 Config 形态 Prompt 数据迁移到标准 AI 资源模型的路径。旧映射
  必须作为兼容存储，而不是正式 Config 资源语义。
- A2A 当前在多个兼容 endpoint 中随机选择 endpoint。未来如有需要，应定义可插拔或
  确定性的 endpoint 选择规范。
- 随着 MCP、A2A 和 Agent 包生态演进，AI 资源 schema 和协议 payload 可能需要
  大幅调整。
