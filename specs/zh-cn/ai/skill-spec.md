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

# Skill 规范

本文档定义 Skill 资源在 AI Registry 中的领域契约。

## 1. 身份

Skill 身份为：

```text
namespaceId -> skill -> name
```

Skill name 在上传时从 `SKILL.md` 元数据解析，是稳定资源名。

## 2. 包模型

Skill 是可复用的 AI Agent 能力包，包含：

- `SKILL.md` 作为主描述和指令文件；
- 描述文件引用的可选资源文件；
- description、bizTags、owner、scope、labels、version 和 download count 等元数据。

Skill upload 接收 ZIP 包。Batch upload 是 best effort，必须报告每个 Skill 的成功和
失败。

## 3. Agent Skills 标准兼容

Nacos Skill 包应与
[Agent Skills Specification](https://agentskills.io/specification) 对齐。上游标准将
Skill 定义为“一个目录，至少包含一个 `SKILL.md` 文件”。Nacos 采用这一包约定作为
外部内容契约，并在其上增加注册中心元数据、版本、可见性和存储语义。

符合标准的 Skill 包遵循以下规则：

- `SKILL.md` 必须存在，内容由 YAML frontmatter 和 Markdown 指令正文组成。
- `name` 与 `description` 是必填 frontmatter 字段。Nacos 将 `name` 映射为
  AI resource name，将 `description` 映射为可搜索元数据。
- `license`、`compatibility`、`metadata` 与 `allowed-tools` 是标准定义的可选字段。
  Nacos 必须在 `SKILL.md` 中保留这些字段；后续可以选择索引其中一部分字段，但描述文件
  仍是包内容的事实来源。
- 标准包根目录可以包含可选的 `scripts/`、`references/` 与 `assets/` 目录。
  Nacos 将这些文件作为 Skill resource 存储和分发。
- Skill name 应遵循上游命名规则：小写字母、数字和连字符，不能以连字符开头或结尾，
  不能包含连续连字符，长度不超过 64 个字符。

上游标准中的 progressive disclosure 模型也是 Nacos 契约的一部分：metadata 用于发现，
客户端激活 Skill 时加载 `SKILL.md`，只有需要时才加载引用资源。Nacos 可以索引 metadata
用于发现，但必须保持包文件边界，使客户端可以执行渐进式加载。

社区 registry 兼容能力，包括 skills CLI 与 well-known discovery 端点，由
[AI Registry 适配器规范](ai-registry-adaptor-spec.md)定义。适配器是可选兼容面，
不会替代标准 Skill resource 生命周期。

从外部市场或 registry 导入 Skill 由
[AI 资源导入插件规范](../plugin/ai-resource-import-plugin-spec.md)定义。导入插件必须产出标准
Skill 包 artifact，Skill Resource Operator 必须通过普通 Skill upload 或 draft 生命周期应用这些
artifact。导入插件不得绕过包校验、可见性、存储或发布治理。

Nacos 注册中心路径不得在 upload、query 或 download 过程中执行包内脚本。脚本执行、静态
分析或安全扫描属于发布流水线插件，或属于显式激活 Skill 的客户端行为。AI 流水线插件契约
由 [AI 流水线插件规范](../plugin/ai-pipeline-plugin-spec.md)定义。

## 4. 存储与索引

Skill 元数据和版本使用 `ai_resource` 与 `ai_resource_version`。Skill 文件内容通过
AI 存储保存。默认存储为 `nacos_config`，但它只是实现后端。

Skill 还维护一个轻量 manifest 以支持客户端发现。Manifest 是从 Skill 元数据派生的
索引，不应成为生命周期状态的事实来源。

存储扩展规则由 [AI 存储插件规范](../plugin/ai-storage-plugin-spec.md)定义。

## 5. 生命周期

Skill 遵循共享的 [AI 资源生命周期规范](ai-resource-lifecycle-spec.md)：

- upload 根据请求选项创建或覆盖 draft；
- upload 可以接收可选 commit message，创建或覆盖 draft 版本时必须保存为该版本描述；
- bootstrap 内置 Skill 可以直接创建 online 元数据和版本行；
- submit 可以运行发布流水线，并发布或退回 draft；
- labels、online/offline、scope、bizTags 和 delete 操作按需通过 CAS 更新元数据。

导入的 Skill 遵循 upload 和 draft 规则，除非该操作是服务端拥有的显式 bootstrap 流程。
依赖处理，例如 Skill 引用 MCP tools，应通过统一导入流程 preview，默认不得递归导入依赖。

## 6. 运行时行为

运行时客户端可以按 latest、明确版本或 label 下载 Skill ZIP 内容。支持时，下载应增加
计数并发出 Trace 或下载事件。

运行时客户端不应获得 upload、publish、delete 或无限制列表等宽管理能力。

运行时客户端可以通过 `name`、可选 `version`、可选 `label` 和可选 md5 查询
Skill。如果 md5 与当前命中版本的内容 md5 一致，服务端可以返回 not-modified 错误，
响应不携带 ZIP 主体。客户端不传 md5 时，服务端必须按当前内容返回 ZIP 与对应 md5。
该契约用于支持轮询监听，订阅应基于 md5 变更报告 Skill 内容变化，但不应向运行时
客户端暴露宽范围管理列表能力。

Skill 内容 md5 是版本级字段，必须在 upload 或发布写入版本内容时一次性计算并随
`ai_resource_version` 持久化，运行时查询不得重新计算。计算输入是发布版本的全部包
字节内容（`SKILL.md` 与所有引用资源），计算口径必须与下载返回的 ZIP 字节内容
保持一致，避免出现“客户端 md5 命中但服务端会返回不同字节”的偏差。

对升级前已存在但缺少 md5 的历史版本，服务端首次响应监听类查询时必须按上述口径
回填 md5，并在同一次响应中返回该 md5；只要 md5 缺失或回填失败，服务端必须返回
带 ZIP 的 200 响应，不得返回 not-modified。

### 6.1 客户端轮询监听契约

Nacos 不为 Skill 提供推送通道，客户端 SDK 通过周期性条件查询 `GET /v3/client/ai/skills`
实现监听语义。监听契约由以下要素组成，服务端与所有实现该 SDK 契约的客户端必须遵守：

- **响应头**：200 响应必须携带 `Content-Type: application/zip`、`Content-Disposition:
  attachment;filename=<name>.zip`、`ETag: "<md5>"`、`X-Nacos-Skill-Md5: <md5>` 与
  `X-Nacos-Skill-Resolved-Version: <version>`。`X-Nacos-Skill-Resolved-Version` 反映
  `label`/`latest` 等路由参数解析后的真实版本。
- **304 响应**：当客户端传入 md5 与服务端命中版本的 md5 一致时，服务端返回
  `304 Not Modified`，body 必须为空，必须携带 `ETag` 与 `X-Nacos-Skill-Md5`，按 RFC 7232
  不得携带 `Content-Type`，且不得携带 `X-Nacos-Skill-Resolved-Version`（304 不应再次声明
  实体元信息）。
- **404 响应**：当 skill 名合法但资源缺失时返回 `404` 与业务错误码 `20004`，客户端
  必须将其翻译为本地缓存淘汰并发布"内容缺失"事件，不得视为暂时性错误重试。
- **轮询调度**：SDK 必须采用单线程 `schedule + 任务尾端自调度` 模式，使下一次查询的
  起点为上一次任务的结束时刻而非开始时刻，避免服务端慢响应导致请求堆积。SDK 不应
  使用 `scheduleAtFixedRate`。
- **频率默认值**：默认轮询间隔为 `10000` 毫秒（`AiConstants.DEFAULT_AI_CACHE_UPDATE_INTERVAL`）。
  首次查询发生在订阅后第一个 interval 之后，订阅本身已同步预热缓存，因此不应再立即
  发起一次轮询。
- **频率可调项**：客户端通过 `Properties` 传入 `nacosAiSkillCacheUpdateInterval`
  （`AiConstants.AI_SKILL_CACHE_UPDATE_INTERVAL`）覆盖默认值，单位毫秒。该配置仅作用于
  Skill，与 Prompt、MCP Server、AgentCard 等其他资源的轮询配置相互独立。
- **取消语义**：`unsubscribeSkill` 必须取消对应任务并移除 md5 缓存项，且不得继续向服务
  端发起轮询请求。

## 7. 待对齐问题

- upload 时强制执行完整的上游 name 校验规则。
- 判断哪些标准可选 frontmatter 字段应索引到 Nacos metadata，同时保持 `SKILL.md`
  作为包内容事实来源。
- 如果未来 Agent Skills 版本改变包结构、frontmatter 字段或 progressive disclosure
  建议，需要定义兼容行为。

## 8. 演进说明

Skill 包约定可能随 AI Agent framework 演进而变化。新的 Skill 包格式应定义解析、
校验、存储和迁移规则。除非明确废弃，已有 Skill 版本必须保持可获取。
