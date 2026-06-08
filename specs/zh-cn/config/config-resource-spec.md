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

# Config 资源规范

本文定义 Config 资源身份、字段、校验和元数据。

## 1. 身份

Config 资源由以下字段唯一标识：

```text
namespaceId -> groupName -> dataId
```

| 字段 | 含义 | 说明 |
| --- | --- | --- |
| `namespaceId` | 配置所属 namespace。 | 请求中的空值或缺省值会被处理为默认 namespace id，当前为 `public`。存储代码中仍可能称为 `tenant` 或 `tenantId`，但当前模型不要求为空 tenant 与 `public` 保留重复默认 namespace 记录。 |
| `groupName` | namespace 内的业务分组。 | 新公开规范和 HTTP v3 表单使用 `groupName`；底层 Config 模型和兼容 API 仍可能将该值称为 `group`。 |
| `dataId` | 配置资源名。 | `dataId` 是 Config 的 `resourceName`。 |

身份字段是稳定的。修改 `namespaceId`、`groupName` 或 `dataId` 表示新资源、克隆、导入或删除后重建，
不是普通元数据更新。

## 2. 内容与版本字段

| 字段 | 含义 |
| --- | --- |
| `content` | 黑盒配置正文。以文本内容存储，并使用配置的持久化编码。Config 不应操作该正文内部的业务配置项。 |
| `md5` | 内容摘要，用于监听变更检测和 CAS 发布。 |
| `encryptedDataKey` | 加密配置使用的受保护密钥材料。普通配置为空。 |
| `type` | 配置内容类型。合法值为 `properties`、`xml`、`json`、`text`、`html`、`yaml`、`toml`、`unset`；发布时非法输入会归一化为 `text`。 |

加密配置通过[配置加密插件规范](../plugin/config-encryption-plugin-spec.md)定义的
`cipher-{algorithm}-` dataId 约定识别。Config 领域负责存储处理后的内容和
`encryptedDataKey`；算法选择和加解密操作属于加密插件。

`type` 和 `schema` 类元数据不改变 `content` 的黑盒属性。它们可以辅助展示、响应处理或扩展
行为，但 Config 核心语义以完整资源为粒度定义。

## 3. 元数据字段

| 字段 | 含义 | 是否身份字段 |
| --- | --- | --- |
| `appName` | 应用名或客户端应用元数据。 | 否 |
| `desc` | 人类可读描述。 | 否 |
| `configTags` | 逗号分隔的管理标签。 | 否 |
| `use` | 使用场景描述。 | 否 |
| `effect` | 影响范围描述。 | 否 |
| `schema` | 可选 schema 文本。 | 否 |
| `srcUser` / `srcIp` | 写入操作的审计来源。 | 否 |
| `createTime` / `modifyTime` | 创建和修改时间。 | 否 |

元数据更新不得创建新的 Config 资源身份。元数据更新应发布普通 Config 变更事件，使依赖元数据的
监听方可以刷新视图。本地事件投递由
[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)定义。

## 4. 校验规则

单资源操作必须包含 Config 身份字段：

- `dataId` 不能为空；
- `groupName` 不能为空；
- 仅当接口支持默认 namespace 处理时，`namespaceId` 可以省略。

Config 服务端会校验 `dataId`、`groupName`、`namespaceId`、tag 和部分元数据字段。公开 Config
名称应只包含字母、数字、`_`、`-`、`.` 和 `:`，除非未来领域规范明确扩展字符集。

当前字段限制包括：

| 字段 | 限制 |
| --- | --- |
| `namespaceId` | 提供时最长 128 字符。 |
| tag | 最长 16 字符。 |
| `configTags` | 最多 5 个 tag，每个 tag 最长 64 字符。 |
| `desc` | 最长 128 字符。 |
| `use` | 最长 32 字符。 |
| `effect` | 最长 32 字符。 |
| `type` | 最长 32 字符。 |
| `schema` | 最长 32768 字符。 |
| `content` | 不得超过配置的 `maxContent`；容量检查可能施加更小的 max-size 策略。 |

## 5. 内部 Group Key

实现代码可以根据 `dataId`、`groupName` 的值和 `namespaceId` 派生内部 group key，用于缓存、监听、
dump 和模糊订阅状态。该派生 key 是实现细节，新 API 或 SDK 契约中应继续使用规范化公开字段。

## 6. 相关规范

- [资源模型规范](../design/resource-model-spec.md)
- [Config 发布与查询规范](config-publish-query-spec.md)
- [Config 灰度发布规范](config-gray-release-spec.md)
- [Config 容量与运维规范](config-capacity-ops-spec.md)
- [事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)
