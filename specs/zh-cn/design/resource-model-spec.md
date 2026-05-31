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
模型和用户文档的语义来源。它细化 [Nacos 设计规范](nacos-design-spec.md)中的顶层领域
结构。

## 1. 顶层资源层次

Nacos 顶层资源身份由三层组成：

```text
NamespaceId -> Group/resourceType -> resourceName
```

三层含义如下：

| 层级 | 含义 | 适用范围 |
| --- | --- | --- |
| `NamespaceId` | 租户、团队、环境或管理域隔离边界。 | 所有租户域资源。 |
| `Group/resourceType` | 第二层分类。微服务应用资源使用概念层 `Group`；AI 资源使用 `resourceType`。 | 由领域决定。 |
| `resourceName` | 在上层 scope 内标识具体资源的稳定名称。 | 所有可命名资源。 |

`Group` 和 `resourceType` 不应被混为同一个字段：

- `Group` 是微服务应用资源的业务分组，主要用于配置和注册中心资源。
- `resourceType` 是资源类型分组，主要用于 AI Registry 等多类型资源共享同一套
  治理模型的场景。

因此，Nacos 的资源模型可以按领域分为两类主干：

- **微服务资源模型**：`NamespaceId -> Group -> resourceName`。
- **AI 资源模型**：`NamespaceId -> resourceType -> resourceName`。

版本、标签、状态、可见性、owner、元数据等字段属于资源的治理属性，除非领域规范
明确说明，否则不参与顶层三层身份。

## 2. NamespaceId

NamespaceId 是最主要的隔离边界，用于隔离租户、团队、环境或其他管理范围。

| 概念 | 标准名称 | 兼容名称 |
| --- | --- | --- |
| Namespace id | `namespaceId` 或 `namespace` | `tenant`, `tenantId` |
| 展示名称 | `namespaceShowName` | `tenantName` |
| 描述 | `namespaceDesc` | `tenantDesc` |

默认 namespace id 是 `public`。历史代码可能使用 `tenant` 或 `tenantId`；新的公开
API 和规范应使用 `namespaceId`，除非已有兼容契约要求继续使用其他名称。

跨 namespace 操作属于管理操作，必须通过 Admin API、Console API 或 Maintainer SDK
能力面暴露。

## 3. 第二层：Group 或 resourceType

第二层用于在 namespace 内继续划分资源，但不同领域使用不同语义。

### 3.1 Group

Group 是微服务应用资源的业务分组。它是配置和注册中心资源身份的一部分，在支持
省略的接口中默认值为 `DEFAULT_GROUP`。

Group 适合表达同一类微服务资源的业务隔离，例如应用、业务线、环境内分组或用户
自定义分组。Group 不表达资源类型，因此同一 Group 下可以存在配置和服务等不同
领域资源。

当 Group 层在新的规范、HTTP API、SDK 或面向用户文档中表达为具体公开字段时，
字段名应使用 `groupName`。较短的 `group` 名称只作为概念表达、内部模型字段或
兼容字段使用。

### 3.2 resourceType

resourceType 是资源类型分组。它适合表达一类共享治理模型中的不同资源类型，例如
AI Registry 中的 `mcp`、`a2a`、`prompt`、`skill`、`agentspec`。

resourceType 不表达业务分组。AI 资源不应再引入 Group 作为身份字段，除非对应领域
规范明确给出额外语义。

## 4. 第三层：resourceName

resourceName 是在 `NamespaceId + Group/resourceType` 下稳定标识资源的名称。

不同领域会使用更贴近业务的名称：

| 领域 | resourceName 的具体名称 |
| --- | --- |
| Config | `dataId` |
| Naming service | `serviceName` |
| MCP Server | `name` 或 `mcpName` |
| A2A AgentCard | `name` 或 `agentName` |
| Prompt | `promptKey` |
| Skill | `name` |
| AgentSpec | `name` |

resourceName 是身份字段，不应被当作普通元数据修改。除非领域规范定义迁移操作，
否则修改 resourceName 应视为删除并创建，或 clone 操作。

## 5. 微服务资源模型

微服务资源模型使用：

```text
NamespaceId -> Group -> resourceName
```

它覆盖 Nacos 传统配置中心和注册中心能力。

### 5.1 Config 资源

Config 资源身份为：

```text
namespaceId -> groupName -> dataId
```

Config 负责：

- content 和 md5；
- config type；
- description、tags 和 app name 元数据；
- 发布、CAS 发布、删除和查询语义；
- listener 和 fuzzy-watch 语义；
- gray/beta 发布状态；
- history、rollback、dump 和 failover 数据。

`dataId` 是 Config 的 resourceName。`appName`、`type`、`desc` 和 `configTags` 等
元数据不改变资源身份。

详细规则参见 [Config 资源规范](../config/config-resource-spec.md)。

Prompt 存在旧兼容映射：固定 group 为 `nacos-ai-prompt`，dataId 为
`{promptKey}.json`。该映射是兼容存储形态，不应让 Prompt 在新规范中被视为普通
Config 资源。

### 5.2 Naming service 资源

Naming service 资源身份为：

```text
namespaceId -> groupName -> serviceName
```

Naming service 负责：

- 服务元数据和内部过滤信息；
- 临时服务或持久服务语义；
- cluster 和健康检查配置；
- subscriber、publisher 和 client connection 视图；
- service 和 instance 变更事件。

内部 grouped name 可以使用 `group@@serviceName` 表达，但公开 API 和规范应优先
使用独立的 `groupName` 与 `serviceName` 字段。详细规则参见
[Naming 资源规范](../naming/naming-resource-spec.md)。

### 5.3 Cluster 和 Instance

Cluster 和 Instance 是 service 的下级资源，不改变顶层三层模型。

```text
namespaceId -> groupName -> serviceName -> clusterName -> instance
```

Instance 身份通常由 service scope、`clusterName`、`ip` 和 `port` 共同确定；
`instanceId` 可以生成或由用户提供，作为运行时标识。

Instance 包含 `ip`、`port`、`clusterName`、`weight`、`healthy`、`enabled`、
`ephemeral`、`metadata` 和可选 `instanceId`。脱离 service scope 的 instance 不应
被单独解释。

临时服务和持久服务语义会影响生命周期和一致性行为。HTTP、gRPC、SDK 和存储模型都必须保留该语义。

## 6. AI 资源模型

AI 资源模型使用：

```text
NamespaceId -> resourceType -> resourceName
```

它覆盖 [MCP Server](../ai/mcp-server-spec.md)、
[A2A AgentCard](../ai/a2a-agent-spec.md)、[Prompt](../ai/prompt-spec.md)、
[Skill](../ai/skill-spec.md)、[AgentSpec](../ai/agentspec-spec.md) 等 AI Registry
资源。共享 AI 模型由 [AI Registry 规范](../ai/ai-registry-spec.md)和
[AI 资源模型规范](../ai/ai-resource-model-spec.md)定义。

AI 资源共享一套治理属性：

| 属性 | 含义 |
| --- | --- |
| `version` | 资源版本，形成 `NamespaceId + resourceType + resourceName + version`。 |
| `labels` | 标签到版本的映射，例如 `latest`、`stable`。 |
| `status` | 资源或版本状态。 |
| `visibility` | 资源可见性，例如 `PUBLIC` 或 `PRIVATE`。 |
| `owner` | 资源所有者 identity。 |
| `bizTags` / `metadata` / `ext` | 不参与身份的业务或扩展元数据。 |
| `pipeline` | 发布审核或自动化处理状态。 |

AI 资源元数据身份为 `namespaceId + resourceType + resourceName`。AI 资源版本身份为
`namespaceId + resourceType + resourceName + version`。

已发布 AI 版本应视为不可变，除非领域规范显式定义安全修改方式。变更应创建新的
draft 版本，在需要时通过审核，然后发布或调整 label。

### 6.1 MCP Server

MCP Server 的标准资源身份为：

```text
namespaceId -> mcp -> mcpName
```

MCP Server 资源描述具备 MCP 能力的服务。它可以来自新构建的 MCP Server、导入的
外部 MCP Server，也可以来自通过适配声明转换而来的存量 HTTP/RPC 服务。

MCP Server 可带有 registry `id`，但 `mcpName` 仍是面向用户的 resourceName。
MCP 特有元数据包括 protocol、front protocol、repository、packages、icons、
website URL、本地或远程 server config、endpoint spec、tool spec、status 和自动
发现的 capabilities。

### 6.2 A2A AgentCard

A2A AgentCard 的标准资源身份为：

```text
namespaceId -> a2a -> agentName
```

AgentCard 资源描述 Agent 的能力、skills、supported interfaces、provider 信息、
security schemes、signatures 和 endpoint 元数据。

`registrationType` 参与 AgentCard 查询和兼容语义，但它不是顶层第二层字段。需要
在具体 A2A 领域规范中定义它与 resourceName、version 和 endpoint 的关系。

### 6.3 Prompt

Prompt 的标准资源身份为：

```text
namespaceId -> prompt -> promptKey
```

Prompt version 形成：

```text
namespaceId -> prompt -> promptKey -> version
```

Prompt 包含 template 内容、variables、md5 和版本元数据。运行时查询应按照对应
API 或 SDK 契约，通过显式 version、label、`latest` 的顺序进行解析。

### 6.4 Skill

Skill 的标准资源身份为：

```text
namespaceId -> skill -> skillName
```

Skill 表示 AI Agent 的可复用能力，包含元数据、指令内容、可选资源、版本、标签、
可见性和发布流水线元数据。

Skill version 会经历 draft、reviewing、reviewed、online、offline 等状态。除非
管理 API 显式请求其他状态，否则运行时客户端只应获得 online version。

### 6.5 AgentSpec

AgentSpec 的标准资源身份为：

```text
namespaceId -> agentspec -> agentSpecName
```

AgentSpec 通过引用 Prompt、Skill、MCP Server、A2A Agent 或其他必要资源来组装
Agent 配置。AgentSpec 应通过稳定身份和 version 或 label 引用其他资源，不应引用
存储实现细节。

## 7. 可见性和 Owner

支持可见性的资源必须暴露：

- `namespaceId`；
- `resourceType`；
- 稳定 resourceName；
- scope，目前为 `PUBLIC` 或 `PRIVATE`；
- owner identity。

可见性影响发现、详情查看、下载和写入操作。它补充授权逻辑，但不能替代权限校验。
权限语义由[鉴权与权限规范](../auth/auth-permission-spec.md)定义。

## 8. 状态和生命周期

状态值是领域特定的，但必须显式定义并记录：

- Config 资源使用发布、gray/beta、history 和 listener 状态；
- Naming 资源使用 service type、instance、health、enabled 和 lifecycle 状态；
- AI 资源使用 metadata status、version status、labels、pipeline state 和 visibility
  state；
- Core 资源使用 server、[member](foundation-cluster-membership-spec.md)、readiness、liveness、
  plugin 和 [connection](foundation-remote-connection-spec.md) 状态。

运行时 API 应只返回运行时消费者需要的状态。管理 API 在授权后可以返回 draft、
review、offline、internal 或 operational 状态。

## 9. API 表达规则

所有 API 家族必须保持相同的资源身份：

- [HTTP](../http-api/api-spec.md) path 和参数名应使用本规范中的标准资源术语。
- [gRPC](../grpc-api/api-spec.md) request 对象即使使用 JSON payload，也应携带同样的
  身份字段。
- [Client SDK](../sdk/sdk-spec.md) 应暴露运行时安全的资源操作。
- Maintainer SDK 应暴露大范围管理资源操作。
- Console API 可以为 UI 调整数据形态，但不能重新定义资源身份。

如果历史 API 使用兼容名称，实现应在内部映射到标准资源术语，并记录该别名。

## 10. 新资源检查项

每个新的资源类型都必须定义：

- 所属领域和模块；
- 标准身份字段；
- 使用 `Group` 还是 `resourceType` 作为第二层；
- resourceName 的具体业务名称；
- version、label、status 和 visibility 行为；
- runtime API、management API 和 SDK 暴露方式；
- 授权和审计要求；
- 持久化和缓存预期；
- 兼容别名，如存在。
