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

默认 importer 实现应放在 `plugin-default-impl`，而不是 AI Registry 领域模块。`ai` 模块负责导入
API、插件路由、校验和资源 Operator；`plugin-default-impl` 负责默认外部来源适配器以及对应的
配置 definitions。

## 概念

| 概念 | 含义 |
|------|------|
| Managed importer | 通过 `pluginName` 标识的稳定 Builder 插件；一个实现表示一个外部来源。 |
| Import service | 从 Builder 的一份不可变配置快照创建的请求级协议适配器。 |
| Candidate | search 阶段返回的外部资源摘要，不包含可导入完整内容。 |
| Artifact | 可被资源 Operator 应用的 payload 和元数据。 |
| Resource operator | 校验并写入某一资源类型的 Nacos 领域服务。 |
| Dependency | 被导入 artifact 引用的其他资源，例如 Skill 依赖 MCP tools。 |

API 现有 `sourceId` 字段等于 managed `pluginName`。API 现有 `pluginName` 字段继续作为
importer/protocol metadata 返回，以兼容 Console。终端用户选择 `sourceId`；导入请求不得提交
任意 endpoint URL、IP 地址、凭证或 registry base path。

## 执行形态

`ai-resource-import` 是路由型统一管理插件。

同一进程可以加载多个 Builder 实现，例如 `mcp-official`、`mcp-registry-protocol`、
`skills-well-known` 或企业内部市场 importer。每次请求中，领域管理器直接把 `sourceId`
解析为一个已启用 Builder。

Importer 在 search 阶段返回 candidate，在 validate 和 execute 阶段按选中项拉取 artifact。
随后 AI Registry 导入管理器根据 artifact 的 `resourceType` 路由到对应资源 Operator。

```text
sourceId(managed pluginName)
  -> AiResourceImportServiceBuilder(当前配置快照)
  -> 请求级 AiResourceImportService
  -> AiResourceOperator(resourceType)
```

## 统一配置

模块总开关为：

```properties
nacos.plugin.ai-resource-import.enabled=true
```

旧 `nacos.ai.resource.import.enabled` 作为 alias。标准 key 只要存在就优先；默认值为
`true`，只有显式配置 `false` 才关闭 AI Resource Import。

每个实现使用标准插件 state key：

```properties
nacos.plugin.ai-resource-import.{pluginName}.enabled=true
```

每个配置项使用：

```properties
nacos.plugin.ai-resource-import.{pluginName}.{itemKey}=value
```

一份 `pluginName` 只表示一个来源。不支持通过配置把同一个 managed 实现复制为多个 endpoint
实例。需要另一个固定来源时，应提供具有不同 `pluginName` 的 Builder。

旧 `nacos.ai.resource.import.sources[N].*`、Source 模型和 Source Provider SPI 被移除。
由于旧模型允许一个 importer 创建多个 source 实例，因此不提供自动迁移。

## SPI

Builder 是稳定的 managed plugin，并实现 `PluginConfigSpec`。

| Builder 方法 | 要求 |
|--------------|------|
| `pluginName()` | 稳定 managed pluginName，也是 API `sourceId`。 |
| `importerType()` | 兼容 importer/protocol metadata，返回到 API `pluginName`。 |
| `displayName()` / `description()` | 从当前已接受配置快照返回展示 metadata。 |
| `supportedResourceTypes()` | 该来源可以产出的资源类型。 |
| `getConfigDefinitions()` | 该实现拥有的全部配置定义。 |
| `applyConfig(config)` | 原子替换不可变 effective configuration 快照。 |
| `build()` | 从一份快照创建一个请求级 Service，不再接收额外 Properties。 |

导入服务实现：

| Service 方法 | 要求 |
|--------------|------|
| `search(context)` | 从配置来源返回 candidate 分页，结果只包含必要元数据。 |
| `fetch(context, item)` | 从配置来源拉取一个被选择的 artifact。 |
| `close()` | 释放请求级资源；默认实现可以为空操作。 |

`context` 包含 namespace、resource type、query、cursor、limit 和 importer 选项，不再携带
source 配置或用户传入的 endpoint。

Builder 实例只发现一次，由统一 `PluginManager` 注册、恢复持久化 state、通过标准配置来源链
解析并 apply，之后才暴露给导入请求。search 每次创建一个 Service；validate 和 execute
各自创建一个 Service 并在请求内复用全部选中项，最终在 `finally` 中关闭。

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

对 MCP 而言，Operator 调用当前 `McpOperationService` 完整兼容 application contract 和相关校验
服务。生命周期 reconciliation 仍处于 `SYNCING` 时，该完整契约使用历史策略，并在写成功后立即
reconcile；原子切换后则使用标准生命周期策略、MCP Version Storage 和按标准名称调度的异步 Search
任务。切换前后 Import 插件和统一导入 API 保持不变，且不得再直接调用已经移除的 Config-backed
`McpServerOperationService`。

对 Skill 而言，Operator 应保持 Skill 包边界，并通过 Skill upload 或 draft 生命周期 API 写入。导入成功后，
如果 artifact 包含 `sourceMetadata.artifactUrl`，Skill Operator 应将该 URL 记录为导入后资源的来源
字段（`ai_resource.c_from`）；如果没有 `artifactUrl`，则回退使用 `sourceMetadata.source`。

Skill 冲突处理遵循 AI 资源 working-version 生命周期：

- 如果 Skill 不存在，导入会创建新草稿；
- 如果 Skill 已存在且没有 editing/reviewing 版本，导入会创建下一个草稿版本；
- 如果 Skill 已存在 editing 或 reviewing 版本，validate 返回 working-version 冲突；execute 默认跳过
  该项，只有 `overwriteExisting=true` 时才允许覆盖当前可编辑草稿，或按 Skill 服务生命周期创建新草稿。

## 内置 Importer

默认内置 importer 由 `plugin-default-impl` 下的 `nacos-default-ai-importer-plugin` 模块提供。

| Managed pluginName | API importer type | 资源 | Endpoint | 默认 state |
|--------------------|-------------------|------|----------|------------|
| `mcp-official` | `mcp-registry` | `mcp` | 固定官方 MCP Registry endpoint | enabled |
| `mcp-registry-protocol` | `mcp-registry` | `mcp` | 必须由运维配置 | disabled |
| `skills-sh` | `skills-sh` | `skill` | 固定 `https://skills.sh` | enabled |
| `skills-well-known` | `skills-well-known` | `skill` | 必须由运维配置 | disabled |

固定内置实现保持当前 Console 展示 metadata：

- `mcp-official`：display name 为 `Official MCP Registry`，description 为
  `Import MCP servers from the official MCP registry.`；
- `skills-sh`：display name 为 `skills.sh`，description 为
  `Import Skills from skills.sh.`。

公共 effective configuration 为：

| Item key | 生效范围 | 适用实现 | 含义 |
|----------|----------|----------|------|
| `endpoint` | `RESTART` | 可配置 endpoint 实现 | Registry 或 marketplace root。 |
| `allow-http` | `RESTART` | 可配置 endpoint 实现 | 允许非 HTTPS 目标。 |
| `allow-private-network` | `RESTART` | 可配置 endpoint 实现 | 允许本地或私网目标。 |
| `display-name` | `RUNTIME` | 全部内置实现 | API 和 Console 展示名称。 |
| `description` | `RUNTIME` | 全部内置实现 | API 和 Console 描述。 |
| `max-item-count` | `RUNTIME` | 全部内置实现 | 单请求结果或文件数上限，默认 `500`。 |
| `max-artifact-size` | `RUNTIME` | 全部内置实现 | 响应或 artifact 字节上限，默认 `10485760`。 |

固定 endpoint 实现不暴露 `endpoint`、`allow-http` 或 `allow-private-network`
definitions，也不接受旧 endpoint override。其来源身份和 endpoint 属于实现契约。

运维配置的 MCP Registry 来源示例：

```properties
nacos.plugin.ai-resource-import.mcp-registry-protocol.enabled=true
nacos.plugin.ai-resource-import.mcp-registry-protocol.endpoint=https://registry.example.com/v0/servers
```

运维配置的 Skill well-known 来源示例：

```properties
nacos.plugin.ai-resource-import.skills-well-known.enabled=true
nacos.plugin.ai-resource-import.skills-well-known.endpoint=https://skills.example.com
```

MCP Registry 实现在 search 阶段返回摘要，在 fetch 阶段返回 `MCP_DETAIL` artifact。

Skill well-known 实现连接运维配置的 Skill marketplace 或 registry root。endpoint 不是
well-known 路径时，它先尝试 `/.well-known/agent-skills`，再尝试
`/.well-known/skills`；endpoint 已是 well-known 路径时直接使用。

Importer 必须同时支持两类 Skill well-known discovery 版本：

- v0.1.0 或 legacy 来源，通过缺失 `$schema` 字段，或
  `https://schemas.agentskills.io/discovery/0.1.0/schema.json` schema URI 识别；
- v0.2.0 来源，通过
  `https://schemas.agentskills.io/discovery/0.2.0/schema.json` schema URI 识别。

v0.1.0 或 legacy 来源的 `index.json` 使用每个 Skill 的文件列表：

```json
{
  "skills": [
    {
      "name": "demo-skill",
      "description": "Demo skill",
      "files": [
        "SKILL.md",
        "docs/guide.md"
      ]
    }
  ]
}
```

Search 阶段只能返回 `name`、`description` 和非 secret metadata。Fetch 阶段按
`{wellKnownBase}/{skillName}/{file}` 拉取被选择 Skill 的文件，校验文件路径安全性，组装为标准
Skill ZIP artifact，并交给 Skill Resource Operator 通过普通 Skill upload 或 draft 生命周期写入。

v0.2.0 来源的 `index.json` 使用 artifact 引用：

```json
{
  "$schema": "https://schemas.agentskills.io/discovery/0.2.0/schema.json",
  "skills": [
    {
      "name": "demo-skill",
      "type": "skill-md",
      "description": "Demo skill",
      "url": "/.well-known/agent-skills/demo-skill/SKILL.md",
      "digest": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    },
    {
      "name": "archive-skill",
      "type": "archive",
      "description": "Demo archive skill",
      "url": "/.well-known/agent-skills/archive-skill.tar.gz",
      "digest": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
  ]
}
```

Search 阶段不得下载 artifact 内容，只能暴露 `name`、`description`、`type`、`url`、`digest`、
schema version 和其他 Console 所需的非 secret metadata。Fetch 阶段必须以 index URL 为基准解析
`url`，在服务端下载被选择的 artifact，校验 `sha256` digest，并将 artifact 转换为标准 Nacos
Skill ZIP 边界。内置 importer 必须支持 `skill-md` 单文件 artifact，以及 ZIP、TAR、TAR.GZ、
TGZ 形式的 `archive` artifact。Archive 解包必须校验路径安全性，限制文件数量和解压后总大小，
并在交给 Skill Resource Operator 前拒绝不支持的 archive 格式。

`skills-sh` importer 使用内置固定的 `https://skills.sh` API root。它遵循 skills.sh CLI 的发现流程：
Search 阶段调用 `GET {endpoint}/api/search?q={query}&limit={limit}`，并且只返回候选摘要；
如果用户 query 为空，importer 应默认使用 `skill` 作为查询词；如果 trim 后的用户 query 只有 1
个字符，importer 应在本地拒绝请求，因为 skills.sh 要求 query 至少 2 个字符。Fetch 阶段根据被选择候选的
`source` 和 `skillId` 调用 `GET {endpoint}/api/download/{owner}/{repo}/{skillId}`，校验返回文件路径，
组装标准 Skill ZIP artifact，并交给 Skill Resource Operator 写入。

Search metadata 只能暴露 skills.sh 页面 URL、GitHub repository URL、repository source、skill id、
安装次数等非 secret 信息；Fetch source metadata 可以额外包含 download snapshot hash。Fetch 必须将
`sourceMetadata.artifactUrl` 设置为对应的 skills.sh 页面 URL，使导入后的 Skill 资源记录具体外部来源，
而不是 `local`。

旧 `nacos.plugin.ai.importer.*` 中 display、description、limits、state 和可配置 endpoint
等价 key 可以作为一个迁移周期的 alias；使用 alias 时应输出迁移 WARN。旧固定来源 endpoint
override、`auth-ref`、source/global timeout、`max-page-count`、`block-private-network`、
全局 defaults 和任意 `properties.*` 被移除，因为它们未生效或与 managed identity 冲突。

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

浏览器 search 后不应默认选中候选项。可以提供显式全选控件，并且用户全选后仍必须能够逐项反选。
如果提供导入全部有效项动作，该动作只能作用于用户显式选择并已完成校验的候选项，且应包含同一
source 下多次校验批次累积出的有效候选项。

浏览器不得接收完整 artifact。MCP 的 tools/specification、Skill zip 或其他可导入内容只允许在
服务端 Importer、Import Manager 和 Resource Operator 之间流转。

## 旧 MCP 导入兼容

现有 MCP 导入 API 可以在兼容窗口期内保留：

```text
POST /v3/console/ai/mcp/import/validate
POST /v3/console/ai/mcp/import/execute
```

validate 和 execute 端点应通过兼容 adapter 路由到统一导入管理器，不应继续作为独立导入实现扩展。

`GET /v3/console/ai/mcp/importToolsFromMcp` 不属于外部 registry 导入兼容范围。它是 Console
在构建 MCP Server schema 时，从用户自有 MCP runtime endpoint 拉取 tools 的辅助能力，不属于
AI 资源市场或 registry 导入流程。

该辅助接口会让 Console 进程向请求指定的 MCP runtime 发起服务端网络连接。公网目标默认允许；私网或
本地目标默认拒绝，只有 `baseUrl` 解析得到的每一个此类地址都命中
`nacos.console.ai.mcp.import.allowed-private-addresses` 时才允许访问。运维可以通过
`nacos.console.ai.mcp.import.enabled=false` 关闭全部出站 tools 导入。请求 `baseUrl` 只能使用 HTTP
或 HTTPS；`endpoint` 参数必须是相对 URI，不得替换 `baseUrl` 的 scheme 或 authority。请求不得跟随
重定向。私网白名单中存在非法项时必须按拒绝处理，不得忽略非法项后继续访问。

兼容端点已废弃，仅保留至 Nacos 3.3.x，并计划在 Nacos 3.4.0 移除。端点默认关闭。
运维可以通过 `nacos.core.api.compatibility.enabled=true` 临时重新开启，客户端应迁移到
`/v3/{admin|console}/ai/import/*`。

旧的 `nacos.ai.resource.import.legacy-mcp-api-enabled` 参数不再识别。共享兼容开关还会重新开启其他
显式接入门禁的废弃 v3 API，具体范围由[兼容与废弃策略规范](../design/compatibility-deprecation-spec.md)定义。

对于旧的 `importType=url`，请求默认不得把用户传入 URL 作为网络目标。当 `data` 匹配已启用
source 时，可以按 `sourceId` 解释；否则应失败并提示迁移到
`nacos.plugin.ai-resource-import.{pluginName}.*` 受管插件配置并启用对应 `sourceId`。旧的直接
URL 导入只能由运维同时开启
`nacos.core.api.compatibility.enabled=true` 和
`nacos.ai.resource.import.allow-user-url=true` 后用于受控部署。

旧的 `importType=json` 和 `importType=file` 可以映射为内置本地 importer，因为它们不需要服务端
发起网络访问。

## 依赖处理

导入 artifact 可以引用其他 AI 资源。例如 Skill 可能需要 MCP tools 或 servers。

依赖处理是预留扩展点，不要求在统一导入初始实现中完整落地。在资源类型暴露明确、可版本化的依赖描述之前，
importer 可以保持 `dependencies` 为空，导入管理器也不应要求请求中必须提供 `dependencyPolicy`。
内置 importer 不得推断、安装或递归导入隐藏依赖。

当 Nacos 后续补充明确的 AI 资源依赖描述后，统一导入流程可以引入如下依赖策略：

| 策略 | 含义 |
|------|------|
| `IGNORE` | 保留依赖元数据，但不校验、不关联。 |
| `VALIDATE_ONLY` | 报告 Nacos 内是否已有匹配资源。 |
| `LINK_EXISTING` | 尽量关联已有匹配资源。 |
| `IMPORT_SELECTED` | 只导入用户显式选择的依赖。 |

依赖描述可用后的默认策略应为 `VALIDATE_ONLY`。自动递归导入不应作为默认行为，因为它会扩大供应链和鉴权边界。

## 安全要求

导入流程必须把外部来源视为不可信：

- 用户不能提交任意 URL、IP、registry root 或凭证；
- 运维配置的 HTTP source 默认应使用 HTTPS；
- 非 HTTPS source endpoint 必须被拒绝，除非运维在 source 配置中显式开启 `allow-http`；
- localhost、loopback、link-local、multicast 和私网 source endpoint 必须被拒绝，除非运维在
  source 配置中显式开启 `allow-private-network`；
- 内置 importer 的 HTTP 请求必须对每个派生出来的请求 URL 重新执行同一套 scheme 和网络策略校验，
  包括从 index 或 search response 中发现的 URL；
- 内置 importer 的 HTTP 请求必须在发送前解析目标 host，并在 DNS 结果为 loopback、link-local、
  multicast 或私网地址时默认拒绝，除非 source 显式开启 `allow-private-network`；
- redirect 必须禁用或按同一安全策略重新校验；
- DNS 解析后默认阻断 loopback、link-local、multicast 和私网目标；
- 内置请求必须强制固定的连接/读取超时，并执行已配置的 `max-item-count` 和
  `max-artifact-size` 限制；除非具体协议有更严格限制，每个 HTTP response 都必须由
  `max-artifact-size` 限制；
- 导入、查询或下载 Skill 包时不得执行包内脚本；
- importer 插件不得在 API 响应、Trace 事件或日志中泄露 secret。

Console MCP tools 导入辅助接口虽然不属于 importer plugin 操作，仍必须遵循旧 MCP 导入兼容章节中
单独定义的公网目标与私网例外策略。

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

统一 managed 模型是对 3.2.x 中短期存在的 Importer/Source 双 SPI 的 breaking replacement。
外部实现必须迁移为一个实现 `PluginConfigSpec` 的 `AiResourceImportServiceBuilder`；已移除的
Source 模型和 Source Provider SPI 不提供兼容 adapter。
