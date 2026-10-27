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

Skill upload 接收 ZIP 包。Batch upload 是 best effort，返回兼容对象：保留原有的
`succeeded` 和 `failed` 字段，并在 `results` 中为每个 Skill 或候选目录返回一条结果。
每条结果包含 `name`、`success`、`errorCode`、`errorMessage` 和可选的 `owner`。成功项使用
`success=true`、错误码 `SUCCESS` 和错误信息 `success`；失败项使用 `success=false` 并返回
具体失败信息。Batch upload 对等失败复用 precheck 业务码
`NOT_A_SKILL`、`INVALID_SKILL` 和 `NO_PERMISSION`，无法分类的失败使用
`UPLOAD_FAILED`。当上传已有 Skill 因调用方缺少写权限而失败时，结果必须在可获取时包含
当前 owner。

上传预检必须接收与上传接口相同的 ZIP 包，并由服务端统一解析单个或批量 Skill。每个
有效 Skill 返回一条结果；候选目录缺少 `SKILL.md` 时返回 `NOT_A_SKILL`，描述文件无效时
返回 `INVALID_SKILL`。精简结果包含 `namespaceId`、`entryPath`、`skillName`、`reason`、
`owner`、`maxPublishedVersion`、`parsedVersion`、`targetVersion`、`exists`、
`editingVersion`、`reviewingVersion` 和单值 `precheckCode`。`entryPath` 是 Skill 或无效
目录在 ZIP 中的相对路径；`skillName` 在解析失败时可以为空；`reason` 用于说明解析失败
原因。客户端只需根据 `precheckCode` 选择下一步：

`maxPublishedVersion` 是已经发布过的最大版本，包含 ONLINE 和 OFFLINE 版本；从未发布
过版本时为空，不包含 DRAFT、REVIEWING 和 REVIEWED 版本。`targetVersion` 是本次上传
成功后的草稿版本。

- `READY`：可以按 `targetVersion` 创建草稿；
- `VERSION_ADJUSTED`：可以创建草稿，但解析版本经过了规范化、替换或递增，实际版本为
  `targetVersion`；
- `DRAFT_EXISTS`：已有编辑中草稿，只能覆盖后继续上传；
- `REVIEWING_EXISTS`：已有审核中版本，阻断上传；
- `NO_PERMISSION`：调用方无权修改已有 Skill；
- `NOT_A_SKILL`：ZIP 中的候选目录缺少 `SKILL.md`；
- `INVALID_SKILL`：候选目录存在 `SKILL.md`，但 Skill 描述文件无效。

同时命中多个条件时，预检必须按以下优先级返回一个编码：`NOT_A_SKILL`、
`INVALID_SKILL`、`NO_PERMISSION`、`REVIEWING_EXISTS`、`DRAFT_EXISTS`、
`VERSION_ADJUSTED`、`READY`。客户端必须将未知编码按阻断处理。

预检请求只包含 ZIP 包和可选 namespace，不接受 `targetVersion`。预检结果中的
`targetVersion` 是服务端根据 ZIP 内容和当前服务端状态推算的版本。预检版本来源优先级为
`SKILL.md` frontmatter 的 `version`、`SKILL.md` frontmatter 的 `metadata.version`、
同目录 `_meta.json` 的 `version`、服务端默认版本。

单 Skill 上传请求额外支持可选的 `targetVersion`。上传版本来源优先级为 `SKILL.md`
frontmatter 的 `version`、`SKILL.md` frontmatter 的 `metadata.version`、同目录
`_meta.json` 的 `version`、请求参数 `targetVersion`、服务端默认版本。服务端必须按此顺序
检查显式版本候选，并使用第一个合法且可用的版本。高优先级候选非法或已被占用时，如果存在
低优先级可用候选，不得直接进入服务端版本生成逻辑。当前编辑中版本可用于覆盖；替换该编辑中
版本的候选必须更大且未被占用。只有所有显式候选均不可用时，服务端才生成版本。因此，上传
请求携带 `targetVersion` 时，实际版本可以不同于此前预检推算的版本。

批量场景中，`NOT_A_SKILL` 和 `INVALID_SKILL` 项不计入 Skill 数量，也不计入阻断 Skill
数量。只有没有有效 Skill，或所有有效 Skill 都被阻断时，客户端才应禁止上传；只要至少
一个有效 Skill 可以上传，就可以调用 batch upload。上传接口必须重新执行权限、版本和
工作版本校验，不能信任预检结果作为写入授权。

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
- 上传解析必须忽略平台生成的 ZIP 元数据文件，例如 macOS 的 `.DS_Store`、
  `._*` AppleDouble 文件和 `__MACOSX/` 目录；这些文件不得作为 Skill resource
  存储或分发。该过滤不得影响普通资源文件，也不得把嵌套 Skill 目录特殊隐藏。
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

每个版本必须在 `ai_resource_version` 的存储描述中持久化存储 provider。读取和删除必须
按该版本已持久化的 provider 路由。有效 AI Resource 存储 provider 配置只控制新写入，不得重定向已有
版本。缺少 `provider` 的历史存储描述归属于 `nacos_config`。

更新或覆盖 draft 时，必须替换完整的 Skill 包内容。写入替换文件后，旧存储描述符中已引用、
但替换包中未包含的文件必须在持久化新描述符前，通过该版本已持久化的 provider 删除。清理
失败时更新必须失败，并保留旧描述符以便重试清理。

Skill 还维护一个轻量 manifest 以支持客户端发现。Manifest 是从 Skill 元数据派生的
索引，不应成为生命周期状态的事实来源。

Skill 参与通用 AI Resource Search，并提供固定 `resourceType=skill` 的资源专用 Search
Facade。两者复用 [AI 资源检索规范](ai-resource-search-spec.md)的 document/chunk/facet、
当前性、可见性和分页，不得把 manifest 或既有管理列表当作第二套 Search 索引。Skill handler
投影 latest online Version 的名称、description、tags 和可检索 manifest 内容；包内脚本、
credential 和未声明的二进制内容不进入检索 chunk。通用 Search 只指定 Skill 时与专用 Search
候选资格一致。
Client Facade 为 `GET /v3/client/ai/skills/search`；它接受 `query`、可重复的
`tagsAll`、`pageNo` 和 `pageSize`，并返回既有 `Page<SkillBasicInfo>` 结构。

存储扩展规则由 [AI 存储插件规范](../plugin/ai-storage-plugin-spec.md)定义。

### 4.1 管理列表 frontmatter

Admin 和 Console 的 Skill 列表及元数据详情响应提供可空的
`frontMatter: Map<String, String>` 和 `frontMatterTruncated: Boolean`
（响应序列化器可以省略 null 字段）。值沿用现有 Skill frontmatter 解析器的字符串表示，
包括展开后的 `metadata.*` 键。本次不增加 frontmatter 搜索，也不修改包解析规则。

展示版本优先使用服务端维护的 `latest`，其次是 `editingVersion`，最后是
`reviewingVersion`。编辑草稿不能覆盖已上线版本的 frontmatter；没有展示版本时返回 null。

新增或更新内容时，在版本存储描述符中保存完整的解析后 `frontMatter`。上传、覆盖上传、
创建/更新/派生草稿及新增内置 Skill 均从实际保存的 SKILL.md 内容提取这些字段；
版本级 frontmatter 不截断。

`ai_resource.ext` 仅保存受限的展示快照 `frontMatter`、`frontMatterVersion` 和
`frontMatterTruncated`，并保留无关扩展键。快照只包含自定义字段，保留字段 `name`、
`description` 和 `version` 不进入快照。标记版本与展示版本匹配时，响应分别从
`SkillSummary.name`、`SkillSummary.description` 和已解析的展示版本生成这三个字段，
再合并缓存的自定义字段，缓存内容不得覆盖生成值。

自定义字段快照最多包含 64 项；键最多 128 个 UTF-8 字节；值最多 1024 个字符，
超长值保留前 1021 个字符并追加 `...`；序列化后的 Map 最多 16 KiB。超长键直接省略。
`alias`、`license`、`compatibility`、`allowed-tools` 和展开后的 `metadata.*` 优先于
其他自定义字段；达到项目数或字节预算后可以省略剩余低优先级字段。发生任何键省略或值截断时
`frontMatterTruncated` 为 true，投影完整时为 false；frontmatter 不可用时为 null 或省略。

发布、强制发布、退回编辑、删除草稿和版本上下线通过版本元数据更新快照，不读取包文件。
编辑其他版本时复用未变化的展示快照。现有列表 `pageSize` 行为保持不变。

快照使用元数据 CAS 写入；冲突后必须重新读取资源行、选择展示版本并读取相应元数据。
快照刷新采用尽力而为语义：重试耗尽或刷新失败时记录日志，不得使已经完成的生命周期操作失败。
列表和元数据详情从同一资源行比较 `frontMatterVersion` 与展示版本，
不匹配时返回 null，不为 frontmatter 额外查询版本表或存储。

没有这些元数据的历史版本保持可读，frontmatter 可以返回 null；不进行迁移、bootstrap 修复或
列表查询时回填。更新历史内容只为被更新版本生成元数据，单纯发布或切换未经更新的历史版本
不会重新解析其文件。
历史 `ai_resource.ext` 内容损坏时按元数据不可用处理：列表和元数据详情请求继续成功，
frontmatter 返回 null，不向调用方传播 JSON 反序列化异常。

## 5. 生命周期

Skill 遵循共享的 [AI 资源生命周期规范](ai-resource-lifecycle-spec.md)：

- upload 根据请求选项创建或覆盖 draft；
- upload 可以接收可选 commit message，创建或覆盖 draft 版本时必须保存为该版本描述；
- bootstrap 内置 Skill 可以直接创建 online 元数据和版本行；
- 提交 draft 或 reviewed 版本可以运行发布流水线，并发布或保留为 reviewed；提交
  reviewing 版本应按幂等调用返回；
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

### 6.2 共享命名空间发现与下载

> 注意：`__nacos_ai_hub__` 是服务端保留的共享命名空间 ID。只有显式传入该 ID
> 才访问共享 Skill；省略 namespace 仍只访问 `public`。它不允许普通资源写入。

共享发现复用 `GET /v3/client/ai/skills/search`，参数仍为 namespaceId、query、tagsAll、
pageNo、pageSize；不新增 source 参数和独立的 Client Hub 路由。共享结果返回现有
`Page<SkillBasicInfo>`，附加字段由
[`Skill discovery schema`](../../schemas/ai/skill/0.1.0/skill-discovery.schema.json)定义。

- 普通结果 `discoverySource=NAMESPACE`，保留原 namespaceId/name；共享结果 `discoverySource=HUB`，
  namespaceId 为保留 ID，name 为不透明的十进制 hubEntryId 字符串。
- `displayName` 是展示名，允许不同共享条目同名；不得按展示名去重。`sourceDisplayName`
  仅显示来源命名空间名称，不公开未授权的源 namespace ID、数据库身份、ext 或 storage。
- `version` 是固定共享版本；`downloadCount` 是共享条目自身累计下载量。
- 共享目录只计算当前有效、调用者可读的条目，先按 Hub downloadCount 降序、hubEntryId
  升序排定，再分页。空或负下载量按零处理。pageSize 最大为 100；深分页保留前
  offset+pageSize 个合格候选，内存成本随页深度增长。

共享下载复用 `GET /v3/client/ai/skills?namespaceId=__nacos_ai_hub__&name=<hubEntryId>`。
不按展示名猜测条目，不回退源 latest；version 若提供必须等于当前共享版本，省略 version
时 label 只接受空或 latest。共享身份须经过普通鉴权链识别，再由共享读取守卫检查 PUBLIC、
在线固定版本、共享开关和撤销状态；不要求读取者具备源命名空间 READ。

200 返回 ZIP，带 `X-Nacos-Hub-Entry-Id` 和 `X-Nacos-Skill-Resolved-Version`。
仅在已验证源版本的持久化 contentMd5 存在且匹配时返回无正文 304；缺少 digest 总是返回
200，并按普通 Skill 口径计算、尽力回填内容摘要以支持后续订阅。304 不携带 Content-Type 或 resolved-version，也不增加下载量。

每次成功的共享下载只增加 Hub 元数据和对应 Hub 引用版本行的下载量，不读取或增加源
Skill 的计数。首次共享从零开始；同一 Hub 条目的重新共享、版本调整保留累计计数，版本
计数按该引用版本单独统计。普通源下载只增加源计数。列表、详情和内容预览不计为下载。
沿用现有异步计数管理器定期刷新（默认 10 秒），因此热度更新最终可见。

管理列表沿用普通 Skill 的 `orderBy`：省略或为空时按 Hub 条目自身 `gmt_modified` 倒序，
`download_count` 时按 Hub 自身下载量倒序；两种排序均以 hubEntryId 升序打破同值，
且必须在有效性筛选之后、分页之前完成。缺失更新时间排在最后，不使用源 Skill 更新时间或下载量。
Client `/search` 保持上述热度排序，不因管理页面默认排序改变而变化。

Admin 和 Console 的管理读取也复用现有 Skill 接口：

`GET /v3/{admin|console}/ai/skills/list`、`GET /v3/{admin|console}/ai/skills`、
`GET /v3/{admin|console}/ai/skills/version` 和
`GET /v3/{admin|console}/ai/skills/version/download`。传入保留 namespace，
`skillName` 使用不透明 Hub ID；版本内容和下载选择固定共享版本。列表返回现有
`Page<SkillSummary>`，详情返回现有 `SkillMeta`，包含相同来源字段和唯一的固定版本摘要；
内容返回 `Skill`，两类下载均返回 ZIP 字节。跳转源资源所需的字段只在源 READ 授权通过后
公开。共享读取经过身份鉴权与 Hub 读取守卫；普通 namespace 的权限及写入限制保持不变。
不保留独立 Hub list/detail/content/download HTTP 路由或公开 Maintainer SDK 读取方法。
分享、调整、撤销、治理状态、来源分享状态、导入和直接复制继续使用专用管理接口。

客户端是否合并共享发现由 `nacosAiAccessHub`（默认 false）控制，与服务端
`nacos.ai.hub.enabled`（既有默认 true）及状态 `ai_hub_enabled` 分离。合并规则见
[SDK 规范](../sdk/sdk-spec.md)。默认或显式 false 时只查询业务 namespace，显式 true 时合并共享；SDK 配置
不改变 HTTP 省略 namespace 时使用 public 的规则。控制台入口按资源类型组织；目前完整共享生命周期仅实现
Skill，类型专属 Admin/Console 分享、撤销、导入和治理接口继续保留。

## 7. 待对齐问题

- upload 时强制执行完整的上游 name 校验规则。
- 判断哪些标准可选 frontmatter 字段应索引到 Nacos metadata，同时保持 `SKILL.md`
  作为包内容事实来源。
- 如果未来 Agent Skills 版本改变包结构、frontmatter 字段或 progressive disclosure
  建议，需要定义兼容行为。

## 8. 演进说明

Skill 包约定可能随 AI Agent framework 演进而变化。新的 Skill 包格式应定义解析、
校验、存储和迁移规则。除非明确废弃，已有 Skill 版本必须保持可获取。


### Console 源资源共享管理区

五类资源详情页统一在资源信息及生命周期操作下方、内容标签页/内容区域上方展示共享管理卡片，集中放置共享、调整版本、取消共享和跨命名空间复制。复制与共享 WRITE/PUBLIC 资格分开，提交时仍校验源 READ 和目标 WRITE。Skill 复用既有管理 /copy 接口，源须启用且指定版本 online；其余四类资源保留既有已发布版本复制规则。无可用版本时仍展示复制按钮，禁用并说明原因。Hub 关闭不展示卡片。Prompt 不显示 PUBLIC/PRIVATE 共享条件或新增 scope 操作。
