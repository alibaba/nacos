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
- bootstrap 内置 Skill 可以直接创建 online 元数据和版本行；
- submit 可以运行发布流水线，并发布或退回 draft；
- labels、online/offline、scope、bizTags 和 delete 操作按需通过 CAS 更新元数据。

导入的 Skill 遵循 upload 和 draft 规则，除非该操作是服务端拥有的显式 bootstrap 流程。
依赖处理，例如 Skill 引用 MCP tools，应通过统一导入流程 preview，默认不得递归导入依赖。

## 6. 运行时行为

运行时客户端可以按 latest、明确版本或 label 下载 Skill ZIP 内容。支持时，下载应增加
计数并发出 Trace 或下载事件。

运行时客户端不应获得 upload、publish、delete 或无限制列表等宽管理能力。

## 7. 待对齐问题

- upload 时强制执行完整的上游 name 校验规则。
- 判断哪些标准可选 frontmatter 字段应索引到 Nacos metadata，同时保持 `SKILL.md`
  作为包内容事实来源。
- 如果未来 Agent Skills 版本改变包结构、frontmatter 字段或 progressive disclosure
  建议，需要定义兼容行为。

## 8. 演进说明

Skill 包约定可能随 AI Agent framework 演进而变化。新的 Skill 包格式应定义解析、
校验、存储和迁移规则。除非明确废弃，已有 Skill 版本必须保持可获取。
