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

# Prompt 规范

本文档定义 Prompt 资源在 AI Registry 中的领域契约。

## 1. 身份

Prompt 身份为：

```text
namespaceId -> prompt -> promptKey
```

`promptKey` 是资源名。旧 Config 存储可能使用固定 group `nacos-ai-prompt` 和
`{promptKey}.json` dataId，但该映射属于兼容存储。

## 2. 内容模型

Prompt 版本包含：

- prompt template 内容；
- 可选变量定义；
- 用于运行时条件获取的 md5；
- 作者、commit message、状态、存储指针等版本元数据。

Nacos 将 Prompt 内容视为 AI artifact。除 Prompt 模型要求的校验外，不应解析或执行
Prompt template。

## 3. 生命周期

Prompt 遵循共享的 [AI 资源生命周期规范](ai-resource-lifecycle-spec.md)：

- 从新内容或已有版本创建 draft；
- 更新或删除当前 draft；
- 提交到发布流水线，或在无匹配流水线时直接发布；
- 发布、强制发布、上线/下线、更新 labels、更新描述、更新业务标签和删除；
- 通过明确 version、label 或 `latest` 查询。

Prompt labels 不得指向 draft 或 reviewing 版本。

## 4. 运行时行为

运行时客户端可以通过 `promptKey`、可选 `version`、可选 `label` 和可选 md5 查询
Prompt。如果 md5 与当前版本内容 md5 一致，服务端可以返回 not-modified 错误。

订阅应报告 Prompt 变更，但不应向运行时客户端暴露宽范围管理列表能力。

## 5. 迁移

Prompt 存在从旧 Prompt 存储迁移到
`ai_resource + ai_resource_version + AI storage` 的迁移任务。迁移必须：

- 跳过已经迁移的 Prompt；
- 通过 marker 避免多节点并发迁移；
- 尽可能保留已有版本和 latest 行为；
- 将旧映射保持为兼容存储，而不是正式 Config 语义。

## 6. 演进说明

Prompt 格式、变量 schema、tool-call 约定和模型提供方要求可能快速变化。Prompt 规范
调整可以引入新的内容字段或校验规则，但必须为已有 Prompt 保留版本化迁移路径。
