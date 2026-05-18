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

## 当前集成说明

核心插件管理器可以列出已加载的 AI pipeline 插件。当前代码说明，统一插件管理中的启停状态
尚未接入 pipeline 执行链路。在该集成完成前，pipeline 执行由 pipeline 配置控制。
