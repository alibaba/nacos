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

# AI 资源模型规范

本文档定义 AI Registry 资源的标准元数据和版本模型，并细化
[AI Registry 规范](ai-registry-spec.md)。

## 1. 身份

AI 资源元数据身份为：

```text
namespaceId + resourceType + resourceName
```

AI 资源版本身份为：

```text
namespaceId + resourceType + resourceName + version
```

公开资源名可以使用类型专属别名，例如 `mcpName`、`agentName`、`promptKey` 或
`name`，但底层身份仍然是 `resourceName`。

## 2. 元数据行

`AiResource` 是标准元数据行。

| 字段 | 含义 |
| --- | --- |
| `namespaceId` | Namespace 隔离边界。 |
| `type` | 资源类型，例如 `prompt`、`skill`、`agentspec`。 |
| `name` | 稳定资源名。 |
| `desc` | 资源描述。 |
| `status` | 资源元数据状态，目前为 `enable` 或 `disable`。 |
| `owner` | 创建者或所属 identity。 |
| `scope` | 可见性 scope，例如 `PUBLIC` 或 `PRIVATE`。 |
| `bizTags` | 用于筛选或 UI 分组的业务标签。 |
| `ext` | 由资源类型拥有的扩展 JSON。 |
| `from` | bootstrap、import 或 sync 来源标记。 |
| `versionInfo` | 版本治理摘要 JSON。 |
| `metaVersion` | 元数据 CAS 更新使用的乐观锁版本。 |
| `downloadCount` | 支持时记录聚合下载或使用次数。 |

`name`、`type`、`namespaceId` 是身份字段，不应作为普通元数据修改。

## 3. 版本行

`AiResourceVersion` 是标准版本行。

| 字段 | 含义 |
| --- | --- |
| `namespaceId`, `type`, `name` | 父元数据身份。 |
| `version` | 父资源下唯一的版本字符串。 |
| `author` | 创建或导入该版本的操作者。 |
| `desc` | 版本描述或 commit message。 |
| `status` | 版本生命周期状态。 |
| `storage` | 通过 AI 存储插件管理的内容存储指针 JSON。 |
| `publishPipelineInfo` | 与流水线执行关联的发布审核状态 JSON。 |
| `downloadCount` | 支持时记录该版本的下载或使用次数。 |

已发布内容默认应视为不可变。如果某个类型必须允许内容修改，它的类型规范必须定义
明确的安全规则。

## 4. VersionInfo JSON

`AiResource.versionInfo` 保存资源级版本摘要：

| 字段 | 含义 |
| --- | --- |
| `editingVersion` | 当前 draft 版本。 |
| `reviewingVersion` | 当前审核中版本。 |
| `onlineCnt` | online 版本数量。 |
| `labels` | label 到 version 的映射，包括 `latest`。 |

同一资源最多应有一个 `editingVersion` 和一个 `reviewingVersion`。除非类型规范明确
定义覆盖或多 draft 行为，否则已有 working version 时应拒绝创建新 draft。

Labels 不得指向 draft 或 reviewing 版本。运行时客户端可以通过明确 version、label
或类型默认 latest 查询。

## 5. 存储

标准模型把元数据存入持久化表，把 payload 内容通过 AI 存储抽象保存。

默认存储实现基于 Nacos Config，但 Config 在这里只是存储后端。通过
`nacos_config` 保存的 AI 内容不应被视为用户拥有的 Config 资源。

存储扩展行为由 [AI 存储插件规范](../plugin/ai-storage-plugin-spec.md)定义。数据库
方言行为由 [数据源方言插件规范](../plugin/datasource-dialect-plugin-spec.md)定义。

## 6. 可见性

AI 资源通过共享的可见性插件模型实现可见性。

规则：

- 创建操作应通过配置的可见性服务解析默认 scope；
- 读操作中，如果资源存在但调用者不可见，应返回 not found；
- 写操作在修改元数据、版本或 scope 之前必须检查写权限；
- 查询操作应尽量使用 visibility query advice，而不是先读取大结果集再过滤。

扩展契约由 [可见性插件规范](../auth/visibility-plugin-spec.md)定义。

## 7. 演进说明

AI Registry 以版本为中心，是因为 AI 资产变化通常比应用配置或服务发现数据更快。
新增资源类型应适配元数据/版本分离模型。如果上游 AI 标准变化导致现有模型不安全
或容易误解，本规范可以演进，但必须明确迁移和兼容规则。
