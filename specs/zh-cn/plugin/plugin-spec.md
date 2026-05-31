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

# Nacos 插件化规范

## 目的

Nacos 使用插件机制和 SPI 扩展，将横切基础能力和可替换的领域能力从固定核心中拆出。
插件可以提供鉴权、资源可见性、数据源方言、加解密、链路追踪、流量控制、环境适配、
AI pipeline、AI 存储、AI 资源导入或 Java 客户端侧请求适配等能力。

插件机制的目标，是在保持 Nacos 核心模型稳定的同时，让不同部署环境可以选择符合自身
身份系统、数据库、观测体系或扩展场景的实现。

## 插件身份

每个插件由以下字段唯一标识：

- `pluginType`：扩展类别，例如 `auth` 或 `visibility`。
- `pluginName`：该类别下的实现名称，例如 `nacos`。
- `pluginId`：运行时标识，格式为 `{pluginType}:{pluginName}`。

`pluginId` 用于管理 API、集群状态同步、插件状态持久化和面向用户的诊断信息。

## 插件类型

当前插件类型注册表由 `PluginType` 定义。

| 类型 | 目的 | 契约 |
|------|------|------|
| `auth` | 认证与授权实现。 | [鉴权插件规范](../auth/auth-plugin-spec.md) |
| `visibility` | 资源可见性与查询可见性建议。 | [可见性插件规范](../auth/visibility-plugin-spec.md) |
| `datasource-dialect` | 数据库方言与持久化适配。 | [数据源方言插件规范](datasource-dialect-plugin-spec.md) |
| `config-change` | 配置变更扩展。 | [配置变更插件规范](config-change-plugin-spec.md) |
| `encryption` | 加解密扩展。 | [配置加密插件规范](config-encryption-plugin-spec.md) |
| `trace` | 链路追踪与观测扩展。 | [Trace 插件规范](trace-plugin-spec.md) |
| `environment` | 环境适配扩展。 | [环境插件规范](environment-plugin-spec.md) |
| `control` | 流量与控制扩展。 | [Control 插件规范](control-plugin-spec.md) |
| `ai-pipeline` | AI 注册中心 pipeline 扩展。 | [AI 发布 Pipeline 插件规范](ai-pipeline-plugin-spec.md) |
| `ai-storage` | AI 注册中心存储扩展。 | [AI 存储插件规范](ai-storage-plugin-spec.md) |
| `ai-resource-import` | AI 注册中心外部资源导入扩展。 | [AI 资源导入插件规范](ai-resource-import-plugin-spec.md) |

各插件类别的领域契约由对应规范定义。本文档定义所有插件类别共享的运行时契约。

[寻址扩展](addressing-plugin-spec.md)为了和公开插件文档保持连续性，也放在插件
规范中记录；但当前服务端代码通过 `MemberLookup` 处理寻址，并未将其注册到
`PluginType`。

## 运行位置

Nacos 有两类插件式扩展面：

| 运行位置 | 加载模型 | 状态归属 | 示例 |
|----------|----------|----------|------|
| 服务端插件 | 领域 SPI 加 `PluginProvider`，在支持时可由服务端插件 API 列出和管理。 | Nacos 服务端进程；对可管理插件，还包括服务端插件状态。 | `auth`、`visibility`、`datasource-dialect`、`control`、`trace`。 |
| Java 客户端扩展 | 在客户端进程内通过 Java SPI 或 SDK API 加载。 | 客户端 classpath、客户端配置和 SDK 实例生命周期。 | `ServerListProvider`、`ClientAuthService`、`IConfigFilter`、客户端侧配置加密。 |

客户端扩展不由 `/v3/admin/core/plugin/*` 管理，也不具备服务端
`PluginStateCheckerHolder` 决策，除非对应服务端插件同时参与请求处理。它们仍必须遵守
Nacos 资源身份、鉴权和 payload 语义，因为它们会影响 SDK 发出的请求。

## 执行形态

插件类别并不都以同一种形态执行。每个插件类型都必须明确自身执行形态。

| 形态 | 含义 | 示例 |
|------|------|------|
| 互斥选择 | 在进程或请求范围内选择一个实现，其他已加载实现不参与该次判断。 | `auth`、`datasource-dialect` |
| 配置选择的单服务 | 可以加载多个实现，但领域根据配置或请求上下文选择一个服务。 | `visibility`、`ai-resource-import` |
| 有序链式执行 | 多个匹配插件按稳定顺序执行。每个节点可以贡献结果，失败是否中断由领域定义。 | `ai-pipeline`、`config-change` |
| 订阅或广播 | 多个订阅者观察同一个事件或 trace 点，不拥有主决策权。 | `trace`、事件型扩展 |

对于链式插件，领域 SPI 必须定义：

- 如何根据资源或 pointcut 选择候选插件。
- 哪个字段控制顺序，例如 `getPreferOrder()` 或 `getOrder()`。
- 执行方式是串行还是并行。
- 某个插件失败时，是中断链路还是只记录失败结果。
- 如何持久化和暴露部分执行结果。

核心插件管理器记录插件的加载状态和启用状态，本身不定义执行形态。领域管理器负责稳定地
应用对应执行形态。

## SPI 层次

Nacos 插件包含两个相关的 SPI 层次：

1. 领域 SPI，例如 `AuthPluginService` 或 `VisibilityService`，定义所属领域需要的行为。
2. 核心插件 SPI，即 `PluginProvider`，将插件实例暴露给核心插件管理器，用于列表查询、
   状态管理、配置管理和运行时观测。

需要动态配置的插件应实现 `PluginConfigSpec`。支持启停状态判断的插件类别，应通过
`PluginStateCheckerHolder` 获取状态，而不是维护一套独立状态来源。

## 加载与生命周期

插件实现通过 Nacos SPI 加载。部署时可以从 classpath 或服务端插件目录提供插件。
插件实现必须能在不修改 Nacos 服务端代码的情况下被加载。

核心 `PluginManager` 会在服务端应用就绪后发现 `PluginProvider` 实现。领域管理器也可以
通过 SPI 加载自身领域服务，但是否可参与请求处理，仍必须遵守核心插件管理器维护的启停
状态。

插件启动必须具备确定性：

- 一个插件类型和插件名称组合只能对应一个运行时插件实例。
- 同一插件类型下重复的插件名称不适合稳定运行。
- 插件实现不得改变 Nacos 共享资源标识、响应封装或错误约定的含义。

## 状态与配置

插件状态分为两个层次：

- 已加载：实现存在于运行时。
- 已启用：实现可以参与请求处理。

大多数插件类型在加载后默认启用。互斥插件类型会选择一个默认实现：

| 类型 | 默认选择规则 |
|------|--------------|
| `auth` | 由 `nacos.core.auth.system.type` 指定，默认 `nacos`。 |
| `datasource-dialect` | 由 SQL platform 配置指定，默认 `derby`。 |

当服务端依赖某些插件维持基本运行能力时，这些插件不能被禁用。当前关键插件集合包括内置
数据源方言，以及服务端需要的默认 AI 存储插件。

实现 `PluginConfigSpec` 的插件应暴露配置定义、当前配置和配置应用行为。除非请求明确
声明为仅本机生效，否则集群级状态或配置变更必须通过插件状态操作链路进行同步。

## 管理 API

核心插件管理 API 如下：

| 方法 | 路径 | 目的 |
|------|------|------|
| `GET` | `/v3/admin/core/plugin/list` | 查询已加载插件，可按类型过滤。 |
| `GET` | `/v3/admin/core/plugin/detail` | 查询单个插件详情。 |
| `PUT` | `/v3/admin/core/plugin/status` | 启用或禁用插件。 |
| `PUT` | `/v3/admin/core/plugin/config` | 更新插件配置。 |

这些端点属于 Admin API，并要求符合 [HTTP 鉴权规范](../http-api/authorization-spec.md)
中的控制台域鉴权。插件管理 API 必须使用标准 v3
[响应与错误模型](../http-api/response-error-spec.md)。

## 设计要求

插件实现必须遵守以下规则：

- 使用已有 Nacos [资源标识](../design/resource-model-spec.md)和领域模型，不为同一
  资源发明不兼容的新模型。
- 插件提供的 HTTP API 必须保持 v3 [HTTP API](../http-api/api-spec.md) 响应、错误和
  鉴权约定。
- 仅通过 `PluginConfigSpec` 暴露插件自身拥有的配置。
- 除调用方明确要求本机操作用于诊断或应急处理外，集群级状态变更必须保持同步。
- 安全敏感的默认值和部署要求必须在插件实现规范中说明。

插件机制是扩展边界，不是绕过 Nacos 资源、API 或安全规则的通道。
