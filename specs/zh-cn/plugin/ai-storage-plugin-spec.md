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

# AI 存储插件规范

## 范围

AI 存储插件抽象 AI 资源的二进制或文本内容存储。元数据仍由 AI 资源模型和持久化服务拥有；
存储插件只负责按 key 读、写和删除内容。通用生命周期和状态规则由
[Nacos 插件化规范](plugin-spec.md) 定义。

这是路由型存储插件。可以注册多个存储提供者。每个 `StorageKey.provider` 选择一个
`AiResourceStorage`。

存储与 [AI 资源元数据](../ai/ai-resource-model-spec.md)有意分离。AI 领域拥有资源身份、
版本、标签、可见性和生命周期。存储插件只拥有不透明 storage key 对应的内容字节。

## 概念

| 概念 | 含义 |
|------|------|
| Storage provider | 由 `StorageKey.provider` 选择的命名后端。 |
| Opaque key | provider 专属 key，上层不应解析。 |
| Content | 与 AI 资源版本关联的二进制或文本载荷。 |
| Metadata | AI 持久化层存储的 AI 资源记录。 |

## SPI

存储实现由 `AiResourceStorageBuilder` 创建。

| Builder 方法 | 要求 |
|--------------|------|
| `type()` | 稳定存储提供者类型。 |
| `build()` | 构造 `AiResourceStorage`。 |

存储服务实现：

| Service 方法 | 要求 |
|--------------|------|
| `type()` | 运行时存储提供者类型。 |
| `save(storageKey, content)` | 为该 key 存储内容。 |
| `get(storageKey)` | 读取该 key 的内容，不存在时返回 null。 |
| `delete(storageKey)` | 删除该 key 的内容。 |

该插件以 `ai-storage` 类型暴露给核心插件管理器。

## 路由

上层必须构造 provider 非空且 key 不透明的 `StorageKey`。`AiResourceStorageRouter` 按
provider 路由。除非自身 provider 契约定义了编码方式，存储插件不得从不透明 key 中解析
Nacos 资源身份。

默认 provider 为 `nacos_config`，它通过 Nacos 配置存储保存 AI 资源内容。

## 要求

存储插件必须精确保留字节内容，不得改变资源元数据、版本状态、
[可见性](../auth/visibility-plugin-spec.md)或鉴权。存储 provider 缺失时必须显式失败。
发布前审核仍由 [AI Pipeline](ai-pipeline-plugin-spec.md) 负责。

实现必须记录：

- 支持的最大内容大小；
- `save` 和 `delete` 后的一致性预期；
- 读取是强一致还是最终一致；
- 备份与迁移行为；
- storage key 是否可以出现在 API 响应或日志中。

## 当前集成说明

核心插件管理器可以列出已加载的 AI 存储插件。当前代码说明，统一插件管理中的启停状态尚未
接入 `AiResourceStorageRouter`。在该集成完成前，路由由已注册的存储 provider 控制。
