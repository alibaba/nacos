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
MCP、AgentSpec 以及未来的通用 AI 资源类型。

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

Pipeline 实现由 `PublishPipelineServiceBuilder` 创建。

| Builder 方法 | 要求 |
|--------------|------|
| `pipelineId()` | 稳定 pipeline 节点 ID。 |
| `build(properties)` | 构造已配置的 `PublishPipelineService`。 |

服务实现：

| Service 方法 | 要求 |
|--------------|------|
| `pipelineId()` | 运行时节点 ID。 |
| `execute(context)` | 执行审核或拦截逻辑。 |
| `getPreferOrder()` | 链式顺序，值越小越早执行。 |
| `pipelineResourceTypes()` | 该节点支持的 AI 资源类型。 |

该插件以 `ai-pipeline` 类型暴露给核心插件管理器。

## 执行

Pipeline 执行器会：

1. 读取 pipeline 配置。
2. 选择已配置且支持目标资源类型的节点。
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
| `nacos.plugin.ai-pipeline.enabled` | Pipeline 框架总开关 | 不进入节点 definitions。 |
| `nacos.plugin.ai-pipeline.type` | Pipeline 节点选择 | 不进入节点 definitions。 |
| `nacos.plugin.ai-pipeline.{pipelineId}.order` | Pipeline 链式顺序 | 不进入节点 definitions。 |
| `nacos.plugin.ai-pipeline.{pipelineId}.{itemKey}` | 对应节点实现 | 由实现通过 `PluginConfigSpec` 声明。 |

### Skill Scanner

内置 `ai-pipeline:skill-scanner` 节点声明以下实现配置。表中的 alias 是同一个
`nacos.plugin.ai-pipeline.skill-scanner.` 前缀下的历史相对 key。

| Item key | Alias | 类型 | 默认值 | 敏感 | 生效模式 | 含义 |
|----------|-------|------|--------|------|----------|------|
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

当前 Skill Scanner service 在启动时解析命令并构造不可变扫描选项，因此上述字段均为
`RESTART`。启动初始化可以应用这些字段；运行时 API 不得接受新增、修改或移除这些字段。
配置命令和默认命令均无法解析为可执行文件时，节点仍保持加载和可查询，但执行扫描时必须
拒绝发布并返回安装提示。

## 统一状态集成

核心插件管理器按 `pipelineId` 列出已加载的 AI pipeline 插件。
`PublishPipelineManager` 在资源类型匹配和排序前，按
`ai-pipeline:{pipelineId}` 的统一状态过滤配置中的候选节点。被禁用的节点仍保持注册，但不
参与发布流程。
