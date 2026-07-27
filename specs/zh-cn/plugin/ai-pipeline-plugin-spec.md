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

# AI 发布 Pipeline 插件规范

## 范围

AI 发布 Pipeline 插件用于在 AI 资源发布前提供审核或拦截逻辑。它面向 Skill、Prompt、
MCP、AgentSpec、Agent 以及未来的通用 AI 资源类型。

这是有序链式插件。匹配节点按 `PublishPipelineService.getPreferOrder()` 升序串行执行。
某个节点失败会停止后续 pipeline，并将本次执行标记为 rejected。通用生命周期和状态规则由
[Nacos 插件化规范](plugin-spec.md) 定义。

Pipeline 属于 AI 资源治理。它可以批准或拒绝一次发布操作，但不得改变被发布
[AI 资源](../ai/ai-resource-model-spec.md)的规范身份。领域生命周期如何响应 pipeline
结果由 [AI 资源生命周期规范](../ai/ai-resource-lifecycle-spec.md)定义。

## 概念

| 概念 | 含义 |
|------|------|
| Pipeline node | 一个审核或拦截单元。 |
| Pipeline execution | 一次发布操作对应的持久化执行记录。 |
| Supported resource type | 节点可以处理的 AI 资源类型。 |
| Approved | 所有选中节点都通过。 |
| Rejected | 某个选中节点失败并停止链路。 |

## SPI

Pipeline 实现直接实现 `PublishPipelineService`。该接口继承
`PluginConfigSpec`，实现类通过 Java SPI 注册，并且必须提供公开无参构造方法。
只有 Core plugin provider 被要求加载 `ai-pipeline` 类型时，Pipeline Manager 才加载并持有
轻量 service 实例。`nacos.plugin.ai-pipeline.enabled=false` 时，启动阶段延迟这次 SPI 加载；
后续服务配置刷新开启框架后，Core PluginManager 必须先加载 service、恢复实现 state、解析
effective config 并调用 `applyConfig`，节点才能参与执行。service 必须将运行时资源初始化
延迟到首次 `applyConfig`。

服务实现：

| Service 方法 | 要求 |
|--------------|------|
| `pipelineId()` | 运行时节点 ID。 |
| `execute(context)` | 执行审核或拦截逻辑。 |
| `getPreferOrder()` | 链式顺序，值越小越早执行。 |
| `pipelineResourceTypes()` | 该节点支持的 AI 资源类型。 |
| `getConfigDefinitions()` | 声明节点实现配置。 |
| `applyConfig(config)` | 应用以 item key 表示的 effective config。 |
| `getCurrentConfig()` | 返回 service 已接受的配置。 |

该插件以 `ai-pipeline` 类型暴露给核心插件管理器。
原 `PublishPipelineServiceBuilder` SPI 及其任意 `Properties` 构造链路不再属于本规范。

## 执行

Pipeline 执行器会：

1. 读取 pipeline 配置并检查 Pipeline 框架总开关。
2. 选择统一插件 state 已启用且支持目标资源类型的实现。
3. 创建 `IN_PROGRESS` 状态的 pipeline 执行记录。
4. 异步串行执行选中的节点。
5. 持久化每个节点结果。
6. 只有所有节点通过时，才将执行完成为 approved。

如果 pipeline 被关闭，或不存在匹配节点，发布流程不经过 pipeline 拦截并继续执行。
Pipeline 输出必须和[可见性](../auth/visibility-plugin-spec.md)过滤，以及发布内容使用的
[AI 存储](ai-storage-plugin-spec.md)保持兼容。

Pipeline 节点对于同一资源版本和输入元数据应返回确定性结果。调用外部系统的节点必须在实现
文档中定义超时与重试行为。

## 配置

Pipeline 框架配置和节点实现配置具有不同的 owner：

| 配置 | Owner | 统一配置定义 |
|------|-------|--------------|
| `nacos.plugin.ai-pipeline.enabled` | 动态 Pipeline 框架入口总开关 | 由 AI 领域模块配置持有，不进入节点 definitions，也不转换为实现级 state。 |
| `nacos.plugin.ai-pipeline.type` | 历史启动链组成 | 仅由 Core PluginManager 读取并生成重启时的实现初始 state，持久化或运行时统一 state 优先。 |
| `nacos.plugin.ai-pipeline.{pipelineId}.order` | Pipeline 链式顺序 | 由对应实现通过 `PluginConfigSpec` 声明为 `order` 配置项。 |
| `nacos.plugin.ai-pipeline.{pipelineId}.{itemKey}` | 对应节点实现 | 由实现通过 `PluginConfigSpec` 声明。 |

Pipeline 不再保留独立的实现配置 provider 或节点配置模型。AI 领域只读取家族级 `enabled`
入口开关；Core PluginManager 只为初始 state 迁移读取历史 `type`。包括 `order` 在内的实现
canonical key 和 alias 统一由插件配置 source chain 解析，并以 item-key map 通过
`applyConfig` 交付给实现。

统一实现级 state 是决定链成员的权威来源。历史 `type` 列表只作为 Core PluginManager 的
重启兼容输入。Core PluginManager 完成 state 初始化和 effective config apply 后，Pipeline
才进入可执行状态。

### Skill Scanner

内置 `ai-pipeline:skill-scanner` 节点声明以下实现配置。表中的 alias 是同一个
`nacos.plugin.ai-pipeline.skill-scanner.` 前缀下的历史相对 key。

| Item key | Alias | 类型 | 默认值 | 敏感 | 生效模式 | 含义 |
|----------|-------|------|--------|------|----------|------|
| `order` | 无 | NUMBER | `100` | 否 | RUNTIME | Pipeline 链式执行顺序，数值越小越先执行。 |
| `command` | `executable`、`path` | STRING | `skill-scanner` | 否 | RESTART | CLI 命令或可执行文件路径；命令名从服务端进程的 `PATH` 和用户本地 bin 目录解析。 |
| `use-llm` | `useLlm` | BOOLEAN | `false` | 否 | RESTART | 是否在扫描时启用 LLM 语义分析。 |
| `llm-api-key` | `llmApiKey` | STRING | 空 | 是 | RESTART | 作为 `SKILL_SCANNER_LLM_API_KEY` 传给扫描子进程。 |
| `llm-model` | `llmModel` | STRING | 空 | 否 | RESTART | 作为 `SKILL_SCANNER_LLM_MODEL` 传给扫描子进程。 |
| `llm-provider` | `llmProvider` | STRING | 空 | 否 | RESTART | 传给 CLI 的 LLM provider；当前实现不限定枚举值。 |
| `enable-meta` | `enableMeta` | BOOLEAN | `false` | 否 | RESTART | 是否启用 skill-scanner meta 检查。 |

canonical full key 使用
`nacos.plugin.ai-pipeline.skill-scanner.{itemKey}`。实现必须继续接受表中的 alias，
但查询和运行时持久化只返回或保存 canonical item key。`llm-api-key` 必须在插件详情 API
返回前脱敏，并且不得写入日志。

当前 Skill Scanner service 在首次配置 apply 时解析命令并构造不可变扫描选项，因此扫描器
字段均为 `RESTART`；`order` 不依赖扫描器资源，可以运行时修改。配置命令和默认命令均无法
解析为可执行文件时，节点仍保持加载和可查询，但执行扫描时必须拒绝发布并返回安装提示。

### SkillSpector

内置 `ai-pipeline:skill-spector` 节点声明以下实现配置。表中的 alias 是同一个
`nacos.plugin.ai-pipeline.skill-spector.` 前缀下的历史相对 key。

| Item key | Alias | 类型 | 默认值 | 敏感 | 生效模式 | 含义 |
|----------|-------|------|--------|------|----------|------|
| `order` | 无 | NUMBER | `90` | 否 | RUNTIME | Pipeline 链式执行顺序，数值越小越先执行。 |
| `command` | `executable`、`path` | STRING | `skill-spector` | 否 | RESTART | CLI 命令或可执行文件路径；命令名从服务端进程的 `PATH`、`~/ai-infra/ai-pipeline/bin` 和 `~/.local/bin` 解析。 |
| `use-llm` | `useLlm` | BOOLEAN | `false` | 否 | RESTART | 是否启用 SkillSpector LLM 分析；关闭时仍执行静态扫描。 |
| `provider` | 无 | STRING | 空 | 否 | RESTART | 传给 SkillSpector 子进程的 LLM provider。 |
| `model` | 无 | STRING | 空 | 否 | RESTART | 传给 SkillSpector 子进程的 LLM model。 |
| `api-key` | `apiKey` | STRING | 空 | 是 | RESTART | 按最终 provider 映射到对应子进程环境变量的凭据。 |
| `base-url` | `baseUrl` | STRING | 空 | 否 | RESTART | 作为 `OPENAI_BASE_URL` 传给子进程的 OpenAI-compatible endpoint。 |
| `log-level` | `logLevel` | STRING | `WARNING` | 否 | RESTART | SkillSpector 子进程日志级别；当前实现不限定枚举值。 |
| `risk-score-threshold` | `riskScoreThreshold` | NUMBER | `50` | 否 | RESTART | risk score 大于最终阈值时拒绝发布；整数值 clamp 到 `0..100`，未配置或非整数时使用默认值。 |
| `max-findings` | `maxFindings` | NUMBER | `20` | 否 | RESTART | review message 最多展示的问题数；整数值大于 `100` 时截为 `100`，未配置、非整数、零或负数时使用默认值。 |

canonical full key 使用
`nacos.plugin.ai-pipeline.skill-spector.{itemKey}`。同时配置 canonical key 和 alias 时，
canonical key 优先；查询和运行时持久化只返回或保存 canonical item key。`api-key` 必须在
插件详情 API 返回前脱敏，并且不得写入日志。
NUMBER 类型显式配置为非数字时，由通用插件配置类型检查在 apply 前拒绝。

当前 SkillSpector service 在首次配置 apply 时解析命令并构造不可变扫描选项，因此扫描器
字段均为 `RESTART`；`order` 不依赖扫描器资源，可以运行时修改。子进程中已经存在的环境
变量优先于插件配置传入的值。配置命令和默认命令均无法解析为可执行文件时，节点仍保持加载
和可查询，但执行扫描时必须拒绝发布并返回安装提示。

## 统一状态集成

核心插件管理器按 `pipelineId` 列出已加载的 AI pipeline 插件。
`PublishPipelineManager` 在资源类型匹配和排序前，按
`ai-pipeline:{pipelineId}` 的统一状态过滤配置中的候选节点。被禁用的节点仍保持注册，但不
参与发布流程。
