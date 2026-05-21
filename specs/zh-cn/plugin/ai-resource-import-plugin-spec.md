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

# AI 资源导入插件规范

## 范围

AI 资源导入插件用于让 Nacos 从运维人员配置的外部 registry 或市场导入 AI 资源。它面向
MCP Server、Skill 以及未来需要先做外部发现和转换再进入 Nacos AI Registry 治理流程的
AI 资源类型。

导入插件只拥有外部来源协议，以及从外部模型转换为 Nacos 导入 artifact 的逻辑。它不拥有
Nacos 资源身份、鉴权、可见性、存储、版本生命周期、发布流水线或 Trace 行为。这些规则仍由
[AI Registry 规范](../ai/ai-registry-spec.md)、资源类型规范以及 AI Registry 领域选择的
资源 Operator 负责。

该插件类型以 `ai-resource-import` 暴露给核心插件管理器。通用插件生命周期和状态规则由
[Nacos 插件化规范](plugin-spec.md)定义。

SPI 契约应定义在插件体系中，例如 `plugin/ai` 模块，和 AI storage、visibility 等插件类型保持
一致。Nacos 应允许用户通过插件机制扩展新的 importer 来源，例如企业内部 Skill 市场、私有 MCP
registry 或 Git 索引。资源 Operator 不属于用户扩展插件，第一阶段应由 `ai` 模块内置并通过
Nacos 当前领域服务写入资源。

## 概念

| 概念 | 含义 |
|------|------|
| Import source | 由运维定义、通过 `sourceId` 标识的导入来源配置。 |
| Importer | 导入来源选择的插件实现。 |
| Candidate | search 阶段返回的外部资源摘要，不包含可导入完整内容。 |
| Artifact | 可被资源 Operator 应用的 payload 和元数据。 |
| Resource operator | 校验并写入某一资源类型的 Nacos 领域服务。 |
| Dependency | 被导入 artifact 引用的其他资源，例如 Skill 依赖 MCP tools。 |

Import source 属于 Nacos 服务端配置或插件配置。终端用户选择 `sourceId`；导入请求不得提交任意
endpoint URL、IP 地址、凭证或 registry base path。

## 执行形态

`ai-resource-import` 是配置选择的单服务插件类型。

同一进程可以加载多个 importer 实现，例如 `mcp-registry`、`skills-well-known` 或企业内部
市场 importer。每次请求中，AI 导入来源管理器先把 `sourceId` 解析为一个已启用 source，再选择
该 source 指定的 importer。

Importer 在 search 阶段返回 candidate，在 validate 和 execute 阶段按选中项拉取 artifact。
随后 AI Registry 导入管理器根据 artifact 的 `resourceType` 路由到对应资源 Operator。

```text
sourceId -> ImportSource(pluginName, resourceTypes, endpoint, limits, authRef)
         -> AiResourceImportService
         -> AiResourceOperator(resourceType)
```

## Source 配置

一个导入来源应包含：

| 字段 | 要求 |
|------|------|
| `sourceId` | 稳定、面向用户展示的来源标识。 |
| `pluginName` | `ai-resource-import` 类型下的 importer 实现名。 |
| `resourceTypes` | 该来源支持的资源类型，例如 `mcp` 或 `skill`。 |
| `endpoint` | 运维配置的来源 endpoint 或 registry root。 |
| `enabled` | 该来源是否可以服务导入请求。 |
| `authRef` | 可选的服务端凭证引用；secret 不返回给用户。 |
| `connectTimeout` / `readTimeout` | 来源级网络超时。 |
| `maxPageCount` / `maxItemCount` | 分页保护限制。 |
| `maxArtifactSize` | 单个 artifact 最大大小。 |
| `properties` | importer 专属非 secret 配置。 |

来源管理器必须拒绝重复 `sourceId`，并拒绝 importer 插件未加载或已禁用的 source。

## SPI

导入实现由 builder 创建。

| Builder 方法 | 要求 |
|--------------|------|
| `importerType()` | 稳定 importer 实现名。 |
| `build(properties)` | 使用 importer 自有配置构造导入服务。 |

导入服务实现：

| Service 方法 | 要求 |
|--------------|------|
| `importerType()` | 运行时 importer 类型。 |
| `supportedResourceTypes()` | importer 可以产出的资源类型。 |
| `search(context)` | 从配置来源返回 candidate 分页，结果只包含必要元数据。 |
| `fetch(context, item)` | 从配置来源拉取一个被选择的 artifact。 |

`context` 包含 namespace、resource type、source 配置、query、cursor、limit 和 importer
选项。它不得包含用户传入的网络 endpoint。

`search` 应无副作用，并且不得返回 MCP tools、Skill 包内容、secret 或其他完整可导入 payload。
`fetch` 可以访问外部来源并返回字节或结构化 payload，但不得写入 Nacos
资源。

## 导入 Artifact

Artifact 应包含：

| 字段 | 含义 |
|------|------|
| `resourceType` | 目标 Nacos AI 资源类型。 |
| `externalId` | 来源内部的稳定 ID。 |
| `name` | 候选 Nacos 资源名，如已知。 |
| `version` | 候选版本，如已知。 |
| `description` | 资源描述。 |
| `payloadKind` | Payload 形态，例如 `MCP_DETAIL`、`SKILL_ZIP` 或 `JSON`。 |
| `payload` | 拉取到的字节或结构化数据。 |
| `dependencies` | 可选的被引用资源。 |
| `sourceMetadata` | 用于 Trace 和诊断的非 secret 来源元数据。 |

Artifact 是导入边界对象，不是持久化资源模型。资源 Operator 负责把它转换为当前存储和生命周期模型。

## Resource Operator

Resource Operator 位于 AI Registry 领域内，不属于导入插件。它们通过资源类型当前的服务层校验并
写入 artifact。

对 MCP 而言，初始 Operator 可以调用当前 `McpServerOperationService` 和相关校验服务，即使 MCP
当前仍由 Config 记录承载。未来 MCP 迁移到 `ai_resource` 后，只应修改 MCP Operator。导入插件和
统一导入 API 应保持兼容。

对 Skill 而言，Operator 应保持 Skill 包边界，并通过 Skill upload 或 draft 生命周期 API 写入。

## API 流程

Nacos 应暴露统一的 Admin 和 Console 导入 API：

| 方法 | 路径 | 目的 |
|------|------|------|
| `GET` | `/v3/admin/ai/import/sources` | 查询可用导入来源。 |
| `POST` | `/v3/admin/ai/import/search` | 根据 source 查询候选摘要。 |
| `POST` | `/v3/admin/ai/import/validate` | 校验被选择的候选并返回冲突、依赖和 warning。 |
| `POST` | `/v3/admin/ai/import/execute` | 导入被选择的候选。 |
| `GET` | `/v3/console/ai/import/sources` | Console 来源列表。 |
| `POST` | `/v3/console/ai/import/search` | Console search 流程。 |
| `POST` | `/v3/console/ai/import/validate` | Console validate 流程。 |
| `POST` | `/v3/console/ai/import/execute` | Console execute 流程。 |

所有统一 API 必须使用标准 v3 `Result<T>` 响应、错误和鉴权约定。

统一导入 API 必须遵循 Nacos v3 表单绑定约定。Controller 方法应暴露 `*Form` 参数，而不是直接以
request model 作为 `@RequestBody` 契约。标量字段可以通过 query 参数或
`application/x-www-form-urlencoded` 表单字段提交。`selectedItems`、`options` 等复杂导入字段应
作为 JSON 字符串表单字段提交，并由 Form 对象转换为内部 request model。

推荐的浏览器流程为：

```text
list sources(resourceType)
  -> select sourceId
  -> search candidates by sourceId and query
  -> user selects candidates
  -> validate selected candidates
  -> show conflicts, dependency warnings, and overwrite options
  -> execute selected candidates
```

浏览器不得接收完整 artifact。MCP 的 tools/specification、Skill zip 或其他可导入内容只允许在
服务端 Importer、Import Manager 和 Resource Operator 之间流转。

## 旧 MCP 导入兼容

现有 MCP 导入 API 可以在兼容窗口期内保留：

```text
POST /v3/console/ai/mcp/import/validate
POST /v3/console/ai/mcp/import/execute
GET  /v3/console/ai/mcp/importToolsFromMcp
```

validate 和 execute 端点应通过兼容 adapter 路由到统一导入管理器，不应继续作为独立导入实现扩展。

对于旧的 `importType=url`，请求默认不得把用户传入 URL 作为网络目标。当 `data` 匹配已启用
source 时，可以按 `sourceId` 解释；否则应失败并提示迁移到
`nacos.ai.resource.import.sources` 配置。旧的直接 URL 导入只能由运维显式配置后用于受控部署。

旧的 `importType=json` 和 `importType=file` 可以映射为内置本地 importer，因为它们不需要服务端
发起网络访问。

## 依赖处理

导入 artifact 可以引用其他 AI 资源。例如 Skill 可能需要 MCP tools 或 servers。统一导入流程应支持：

| 策略 | 含义 |
|------|------|
| `IGNORE` | 保留依赖元数据，但不校验、不关联。 |
| `VALIDATE_ONLY` | 报告 Nacos 内是否已有匹配资源。 |
| `LINK_EXISTING` | 尽量关联已有匹配资源。 |
| `IMPORT_SELECTED` | 只导入用户显式选择的依赖。 |

默认应为 `VALIDATE_ONLY`。自动递归导入不应作为默认行为，因为它会扩大供应链和鉴权边界。

## 安全要求

导入流程必须把外部来源视为不可信：

- 用户不能提交任意 URL、IP、registry root 或凭证；
- 运维配置的 HTTP source 默认应使用 HTTPS；
- redirect 必须禁用或按同一安全策略重新校验；
- DNS 解析后默认阻断 loopback、link-local、multicast 和私网目标；
- 来源请求必须强制连接超时、读超时、响应大小、页数和 artifact 大小限制；
- 导入、查询或下载 Skill 包时不得执行包内脚本；
- importer 插件不得在 API 响应、Trace 事件或日志中泄露 secret。

需要从私网导入的部署必须通过运维配置显式开启。

## Trace 与审计

Search、validate 和 execute 操作应发出 Trace 或审计事件，包含：

- source id；
- importer 类型；
- 资源类型；
- candidate 数量和选中数量；
- 单项成功、跳过或失败状态；
- 非 secret 来源元数据；
- 可用时的操作者身份和客户端地址。

Trace 行为必须遵循 [Trace 插件规范](trace-plugin-spec.md)。

## 演进说明

该插件类型是转换边界。单个资源的存储实现演进时，它应保持稳定。特别是 MCP 从 Config-backed
记录迁移到标准 AI 资源模型时，应通过替换 MCP Resource Operator 保持导入兼容，而不是修改每个
外部 importer。
