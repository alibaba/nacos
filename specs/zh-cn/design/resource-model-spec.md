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

# Nacos 资源模型规范

本文档定义 Nacos 共享资源模型，是 HTTP API、gRPC API、SDK、控制台流程、持久化
模型和用户文档的语义来源。

## 1. 资源信封

每个 Nacos 资源都应能用一组通用信封字段描述：

| 字段 | 要求 | 含义 |
| --- | --- | --- |
| `namespaceId` | 租户域资源必需 | 租户、团队和环境的隔离边界。 |
| `resourceType` | 多类资源共用存储或 API 面时必需 | 资源类型，例如 config、service、mcp、a2a、prompt、skill、agentspec。 |
| `resourceName` | 必需 | 在所属 scope 和 type 内稳定标识资源的名称。 |
| `group` | 可选，领域特定 | 配置和注册中心使用的二级分组。 |
| `version` | 可选，仅版本化资源 | 不可变或受生命周期管理的版本标识。 |
| `labels` | 可选，仅版本化资源 | `latest`、`stable` 或自定义标签等命名路由别名。 |
| `status` | 可选，领域特定 | 资源或版本生命周期状态。 |
| `metadata` | 可选 | 不改变资源身份的用户或系统元数据。 |
| `visibility` | 可选，支持可见性的资源 | 访问范围和 owner 信息。 |

历史 Nacos 数据模型是 namespace、group 和 resource name 三元组。该三元组仍然
适用于配置和注册中心资源。AI 资源在同一思路上扩展了 `resourceType`、`version`、
`labels` 和可见性治理。

## 2. Namespace

Namespace 是最主要的隔离边界，用于隔离租户、团队、环境或其他管理范围。

| 概念 | 标准名称 | 兼容名称 |
| --- | --- | --- |
| Namespace id | `namespaceId` 或 `namespace` | `tenant`, `tenantId` |
| 展示名称 | `namespaceShowName` | `tenantName` |
| 描述 | `namespaceDesc` | `tenantDesc` |

默认 namespace id 是 `public`。历史代码可能使用 `tenant` 或 `tenantId`；新的公开
API 和规范应使用 `namespaceId`，除非已有兼容契约要求继续使用其他名称。

跨 namespace 操作属于管理操作，必须通过 Admin API、Console API 或 Maintainer SDK
能力面暴露。

## 3. Group

Group 是领域特定的二级 scope。它是配置和注册中心资源身份的一部分，在支持省略的
接口中默认值为 `DEFAULT_GROUP`。

Group 不是所有 Nacos 资源的通用字段。AI 资源不应引入 group 字段，除非对应领域
规范明确给出语义。

## 4. 资源身份规则

| 资源 | 标准身份 | 说明 |
| --- | --- | --- |
| Namespace | `namespaceId` | 根隔离资源。 |
| Config | `namespaceId + group + dataId` | `tenant` 是 namespace 的历史存储/API 名称。 |
| Naming service | `namespaceId + group + serviceName` | 内部 grouped name 可以使用 `group@@serviceName`。 |
| Naming cluster | `namespaceId + group + serviceName + clusterName` | Cluster 从属于 service。 |
| Naming instance | `namespaceId + group + serviceName + clusterName + ip + port` | `instanceId` 可以生成或由用户提供，作为运行时标识。 |
| Naming client | `clientId` 或 connection id | 运行时视图，不是用户创建的领域资源。 |
| AI resource | `namespaceId + resourceType + name` | Prompt、Skill、AgentSpec 等治理资源的共享模型。 |
| AI resource version | `namespaceId + resourceType + name + version` | 版本状态独立于资源元数据管理。 |
| MCP Server | `namespaceId + name`，可附带 `id` | `id` 可表示 registry/import 身份；`name` 是面向用户的资源名。 |
| A2A AgentCard | `namespaceId + registrationType + name + version` | registration type 参与查询语义。 |
| Prompt | `namespaceId + promptKey + version` | 旧 Prompt 数据可以镜像为 config 数据。 |
| Skill | `namespaceId + name + version` | labels 映射路由名到版本。 |
| AgentSpec | `namespaceId + name + version` | AgentSpec 可以引用其他 AI 资源。 |
| Plugin | `pluginType + pluginName` | 插件状态是服务端控制面元数据。 |

资源身份字段不应被当作可变元数据。除非领域规范定义迁移操作，否则修改资源身份
应视为删除并创建，或 clone 操作。

## 5. Config 资源

Config 资源由 `namespaceId + group + dataId` 标识。

Config 负责：

- content 和 md5；
- config type；
- description、tags 和 app name 元数据；
- 发布、CAS 发布、删除和查询语义；
- listener 和 fuzzy-watch 语义；
- gray/beta 发布状态；
- history、rollback、dump 和 failover 数据。

`dataId` 是配置资源名。`appName`、`type`、`desc` 和 `configTags` 等元数据不改变
资源身份。

Prompt 存在旧兼容映射：固定 group 为 `nacos-ai-prompt`，dataId 为
`{promptKey}.json`。该映射不应让 Prompt 在新规范中被视为普通 Config 资源。

## 6. Naming 资源

Naming service 由 `namespaceId + group + serviceName` 标识。

Naming 负责：

- 服务元数据和 selector 信息；
- 临时或持久化服务语义；
- cluster 和健康检查配置；
- instance，包含 `ip`、`port`、`clusterName`、`weight`、`healthy`、`enabled`、
  `ephemeral`、`metadata` 和可选 `instanceId`；
- subscriber、publisher 和 client connection 视图；
- service 和 instance 变更事件。

Instance 从属于 service。脱离 service scope 的 instance 不应被单独解释。

临时和持久化语义会影响生命周期和一致性行为。HTTP、gRPC、SDK 和存储模型都必须
保留该语义。

## 7. AI 资源

AI 资源使用共享治理模型：

- 资源元数据行：`namespaceId + type + name`；
- 版本行：`namespaceId + type + name + version`；
- 标签：名称到版本的映射，包括 `latest`；
- 元数据状态：enable 或 disable；
- 版本状态：draft、reviewing、reviewed、online 或 offline；
- 可选 owner 和可见性范围：`PUBLIC` 或 `PRIVATE`；
- 可选发布流水线状态：in progress、approved 或 rejected；
- 可选业务标签、扩展元数据、来源和下载次数。

已发布 AI 版本应视为不可变，除非领域规范显式定义安全修改方式。变更应创建新的
draft 版本，在需要时通过审核，然后发布或调整 label。

### 7.1 MCP Server

MCP Server 资源描述具备 MCP 能力的服务。它可以来自新构建的 MCP Server、导入的
外部 MCP Server，也可以来自通过适配声明转换而来的存量 HTTP/RPC 服务。

MCP Server 身份基于 `namespaceId + name`，并可带有 registry `id`。MCP 特有元数据
包括 protocol、front protocol、repository、packages、icons、website URL、本地或
远程 server config、endpoint spec、tool spec、status 和自动发现的 capabilities。

支持的协议值包括 stdio、SSE 风格 MCP、streamable HTTP、HTTP 和 Dubbo 兼容形态，
具体由 AI 领域定义。

### 7.2 A2A AgentCard

A2A AgentCard 资源描述 Agent 的能力、skills、supported interfaces、provider 信息、
security schemes、signatures 和 endpoint 元数据。

AgentCard 查询按 namespace、registration type、agent name 和 version 确定范围。
Endpoint registration 从属于对应 AgentCard，并且在运行时可能由客户端拥有。

### 7.3 Prompt

Prompt 资源在 namespace 内由 prompt key 和 version 标识。Prompt 包含 template
内容、variables、md5 和版本元数据。

运行时 Prompt 查询应按照对应 API 或 SDK 契约，通过显式 version、label、`latest`
的顺序进行解析。

### 7.4 Skill

Skill 资源表示 AI Agent 的可复用能力。Skill 包含元数据、指令内容、可选资源、
版本、标签、可见性和发布流水线元数据。

Skill version 会经历 draft、review、publish、offline 等状态。除非管理 API 显式
请求其他状态，否则运行时客户端只应获得 online version。

### 7.5 AgentSpec

AgentSpec 资源通过引用 Prompt、Skill、MCP Server、A2A Agent 或其他必要资源来组装
Agent 配置。AgentSpec 身份遵循 `namespaceId + name + version`，运行时路由应使用
labels。

AgentSpec 应通过稳定身份和 version 或 label 引用其他资源，不应引用存储实现细节。

## 8. 可见性和 Owner

支持可见性的资源必须暴露：

- `namespaceId`；
- 稳定资源名；
- 资源类型；
- scope，目前为 `PUBLIC` 或 `PRIVATE`；
- owner identity。

可见性影响发现、详情查看、下载和写入操作。它补充授权逻辑，但不能替代权限校验。

## 9. 状态和生命周期

状态值是领域特定的，但必须显式定义并记录：

- Config 资源使用发布、gray/beta、history 和 listener 状态；
- Naming 资源使用 service、instance、health、enabled 和 ephemeral 状态；
- AI 资源使用 metadata status、version status、labels、pipeline state 和 visibility
  state；
- Core 资源使用 server、member、readiness、liveness、plugin 和 connection 状态。

运行时 API 应只返回运行时消费者需要的状态。管理 API 在授权后可以返回 draft、
review、offline、internal 或 operational 状态。

## 10. API 表达规则

所有 API 家族必须保持相同的资源身份：

- HTTP path 和参数名应使用本规范中的标准资源术语。
- gRPC request 对象即使使用 JSON payload，也应携带同样的身份字段。
- Client SDK 应暴露运行时安全的资源操作。
- Maintainer SDK 应暴露大范围管理资源操作。
- Console API 可以为 UI 调整数据形态，但不能重新定义资源身份。

如果历史 API 使用兼容名称，实现应在内部映射到标准资源术语，并记录该别名。

## 11. 新资源检查项

每个新的资源类型都必须定义：

- 所属领域和模块；
- 标准身份字段；
- namespace 和 group 行为；
- version、label、status 和 visibility 行为；
- runtime API、management API 和 SDK 暴露方式；
- 授权和审计要求；
- 持久化和缓存预期；
- 兼容别名，如存在。
