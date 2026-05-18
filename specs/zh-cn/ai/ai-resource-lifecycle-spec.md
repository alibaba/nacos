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

# AI 资源生命周期规范

本文档定义版本化 AI Registry 资源的通用生命周期规则。具体类型规范可以进一步细化
这些规则。

## 1. 状态模型

元数据状态：

| 状态 | 含义 |
| --- | --- |
| `enable` | 资源在可见且存在可查询版本时可用。 |
| `disable` | 资源在元数据层被禁用；具体查询行为由类型规范定义。 |

版本状态：

| 状态 | 含义 |
| --- | --- |
| `draft` | 正在编辑的版本。 |
| `reviewing` | 已提交到发布流水线审核。 |
| `reviewed` | 流水线已通过，等待显式发布。 |
| `online` | 已发布且可查询。 |
| `offline` | 已存在但从普通运行时路由中移除。 |

## 2. 标准流程

标准生命周期为：

```text
create/upload draft
  -> update draft
  -> submit
  -> reviewing
  -> reviewed
  -> publish
  -> online
  -> offline/online toggle or delete
```

如果没有启用发布流水线，或没有匹配该资源类型的流水线节点，`submit` 可以根据类型
实现直接发布。

`force-publish` 会绕过流水线校验，必须保持为管理操作。

## 3. Draft 规则

- 除非类型规范定义覆盖或多 draft 行为，一个资源最多应有一个 working draft。
- 创建 draft 可以创建新的元数据行，也可以从 online 版本 fork。
- 更新 draft 只能修改当前 draft 版本。
- 删除 draft 会清理元数据中的 `editingVersion` 指针，并删除 draft 版本行和存储内容。
- 上传操作可以是类型专属行为，但除 bootstrap/import 外，通常应生成 draft 版本。

## 4. 审核和发布规则

- Submit 会解析明确版本或当前 `editingVersion`。
- 当不存在 draft 目标时，Submit 必须失败。
- 审核中版本必须在元数据中记录为 `reviewingVersion`。
- 流水线执行状态可以写入 `publishPipelineInfo` 和 `pipeline_execution`。
- 流水线拒绝会把版本退回 `draft`，并恢复 editing 指针。
- 流水线通过会把版本改为 `reviewed`。
- Publish 会把版本改为 `online`，清理 working 指针，按需增加 `onlineCnt`，并可更新
  `latest` label。

流水线扩展行为由 [AI 发布流水线插件规范](../plugin/ai-pipeline-plugin-spec.md)定义。
本领域规范只定义 AI 资源生命周期如何响应流水线结果。

## 5. Labels

- `latest` 是保留的默认 label，表示最近发布版本。
- Labels 映射到版本字符串，且不得指向 `draft` 或 `reviewing` 版本。
- 修改 labels 不应直接修改版本内容或版本状态。
- 运行时通过 label 查询时，应在请求时解析 label。

## 6. 删除规则

- 删除版本应删除版本行和该版本的类型自有存储内容。
- 删除资源应删除元数据、所有版本行和所有类型自有存储内容。
- 只有公开 API 契约明确说明缺失资源视为成功时，删除操作才应具备该幂等语义。
- 删除 online 版本时，类型实现支持的情况下应更新 `onlineCnt` 或 labels。

## 7. Trace 与计数

AI 资源操作应对 create draft、update draft、submit、review approved/rejected、publish、
force publish、online/offline、delete、label update、description update、scope update 和
download 发出 Trace/审计事件。

Trace 插件行为由 [Trace 插件规范](../plugin/trace-plugin-spec.md)定义。计数只用于诊断，
不得定义鉴权或生命周期状态。

## 8. 演进说明

随着 AI 发布流程成熟，生命周期状态可能扩展，例如支持审批链、分阶段发布、策略评估、
签名或 artifact 扫描。新增状态必须定义与现有 `draft`、`reviewing`、`reviewed`、
`online`、`offline` 行为的兼容关系。
