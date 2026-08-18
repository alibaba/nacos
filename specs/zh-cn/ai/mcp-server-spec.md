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

# MCP Server 规范

本文档定义 MCP Server 资源在 AI Registry 中的领域契约。

## 1. 身份

MCP Server 标准身份为：

```text
namespaceId -> mcp -> mcpName
```

`mcpName` 是公开资源名。当前代码还会使用内部 MCP server id 进行 Config 存储。
公开规范应优先使用 `mcpName` 和标准 AI 资源身份。

## 2. 领域模型

MCP Server 描述一个 MCP 兼容服务端，可以包含：

- server 元数据和协议信息；
- version details 和 latest published version；
- tool specification；
- resource specification；
- backend 或 frontend endpoint 引用；
- endpoint 协议、地址、端口、路径、headers 和 export path 元数据。

MCP Server 版本应可独立查询。未指定版本时，运行时查询解析最新发布版本。

### 2.1 Tool 出参 Schema 兼容性

MCP Tool 的 `outputSchema` 是 JSON Schema。Nacos 必须保留合法的 JSON Schema 类型声明，
不能把 `type` 关键字限制为单个字符串。可空的响应字段可以使用一个基础类型与 `null` 组成的
联合类型，例如 `{"type": ["string", "null"]}`。

控制台加载和保存 Tool 时必须保留该表示。导入 OpenAPI 3 响应 Schema 时，具有明确类型且
配置了 `nullable: true` 的字段必须转换为等价的 JSON Schema 联合类型。该表示属于新增元数据，
不会改变现有消费者的 Tool 调用或响应透传契约。

MCP Registry 兼容发现能力不属于标准 MCP resource 契约本身。它由可选的
[AI Registry 适配器规范](ai-registry-adaptor-spec.md)暴露，将 Nacos MCP resources
映射为 MCP Registry 响应形态以兼容社区客户端。

## 3. Endpoint 模型

MCP endpoint 可用两种模式表达：

| 模式 | 含义 |
| --- | --- |
| `REF` | MCP 资源引用已有 Naming service。 |
| Direct endpoint | Nacos 在 MCP endpoint group 下创建或更新 Naming service，并注册 endpoint instance。 |

Endpoint service 和 instance 是传输目标，不会让 MCP Server 变成 Naming service 资源。
Naming 只作为 endpoint 基础设施使用。

Direct endpoint service 应标记为 MCP 拥有的 service metadata；当前实现需要持久
endpoint 可见性时，应使用非临时服务/实例行为。

## 4. API 和 SDK 行为

- Admin API 可以列表、查询、创建/发布、更新、删除和导入 MCP 资源。
- Client API 和 SDK 可以查询 MCP 详情、在支持时发布 MCP server 版本、注册/注销
  客户端拥有的 endpoint，并订阅 MCP 变更。
- gRPC payload 包含查询、发布和 endpoint 注册请求，详见
  [gRPC API 规范](../grpc-api/api-spec.md)。

MCP 参与通用 AI Resource Search，并提供固定 `resourceType=mcp` 的资源专用 Search
Facade。两者复用 [AI 资源检索规范](ai-resource-search-spec.md)的同一索引和 Query Planner，
不能继续把 Config-backed 名称列表作为独立的全文搜索引擎。MCP handler 可以投影 server name、
description、tools、resources 和公开 tags；endpoint credential、运行时 instance 和敏感 auth
metadata 不进入检索 chunk。迁移到标准 AI Resource 前，handler 可以读取兼容存储，但必须生成相同
的标准 projection。通用 Search 只指定 MCP 时与专用 Search 候选资格一致。

## 5. 当前兼容存储

当前 MCP 实现通过 Config 形态记录保存 MCP 元数据，并通过 Naming service 表达
endpoint：

- MCP version info；
- 某版本 MCP server detail；
- MCP tool specification；
- MCP resource specification；
- MCP endpoint service 和 instance。

这是兼容存储，不是目标标准模型。新的 MCP 语义应按 AI Registry 语义定义，然后在迁移
完成前映射到当前存储。

## 6. 外部导入

从外部 registry 或市场导入 MCP 应使用
[AI 资源导入插件规范](../plugin/ai-resource-import-plugin-spec.md)。导入插件负责把运维配置的
外部 MCP registry 数据转换为 MCP 导入 artifact，不得直接写入 MCP 存储。

MCP Resource Operator 负责通过 MCP 领域操作服务应用导入 artifact。当前实现可以使用
Config-backed 的 `McpServerOperationService`。未来 MCP 元数据和版本迁移到 `ai_resource`
后，应替换 MCP Resource Operator 到新的存储模型，同时保持导入插件和统一导入 API 兼容。

旧 MCP 导入 API 可以作为兼容路由保留，但应委托给统一 AI 资源导入流程。默认情况下，不得把用户传入的
registry URL 或 MCP endpoint 地址作为服务端直接访问的网络目标。

## 7. 待迁移问题

- 将 MCP 元数据和版本行迁移到 `ai_resource` 和 `ai_resource_version`。
- 定义现有 Config-backed MCP 记录在混合版本集群中的发现、迁移和服务方式。
- 让 MCP label 和 latest-version 行为与共享 AI 资源 label 模型对齐。
- 定义运行时客户端注册 direct endpoint 的所有权和清理规则。

## 8. 演进说明

MCP 仍在快速演进。Tool schema、resources、transport mode、auth metadata 和 registry
互操作都可能变化。因此 MCP Server 规范的调整可能大于普通 Nacos 领域变更，但必须
包含兼容和迁移说明。
