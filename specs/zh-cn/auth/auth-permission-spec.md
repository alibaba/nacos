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

# 鉴权与权限规范

## 范围

本文档定义 Nacos HTTP API、gRPC API、插件 API 和服务端内部调用共享的认证与权限模型。
传输协议相关细节由 [HTTP 鉴权规范](../http-api/authorization-spec.md) 和
[gRPC API 规范](../grpc-api/api-spec.md) 定义。插件契约由
[鉴权插件规范](auth-plugin-spec.md) 和 [可见性插件规范](visibility-plugin-spec.md) 定义。
传输过滤器执行和 `AuthContext` 写入由
[请求过滤与运行时上下文规范](../design/foundation-request-context-spec.md)定义。

Nacos 授权模型可以表示为：

```text
请求身份 -> 已认证主体 -> 角色 -> 权限 -> 资源/动作
```

内置实现采用 RBAC。自定义鉴权插件可以接入其他身份系统，但仍必须通过身份、资源和动作
语义来评估 Nacos 请求。

## 鉴权插件与可见性插件

Nacos 将请求级授权和数据级可见性分开处理。

| 层次 | 主要问题 | 典型 SPI | 范围 |
|------|----------|----------|------|
| [鉴权插件](auth-plugin-spec.md) | 该调用方是否可以针对解析出的资源/动作调用这个 API？ | `AuthPluginService` | 请求准入与权限判断。 |
| [可见性插件](visibility-plugin-spec.md) | 该调用方是否可以看见或修改这个具体资源，或范围查询应该返回哪些资源？ | `VisibilityService` | 资源实例可见性与查询计划。 |

这两层是正交关系：

- 鉴权插件可以在没有可见性插件的情况下存在。
- 可见性插件也可以独立存在，但每个领域必须定义没有请求身份时的行为。
- 可见性插件可以复用鉴权插件产出的身份，也可以把显式资源权限检查委托回当前选中的鉴权插件。
- 通过 `@Secured` 鉴权，只代表请求级准入通过，不代表所有匹配数据行都可见。

对于具备可见性语义的资源，推荐请求流程为：

```text
@Secured + AuthPlugin -> VisibilityService -> 业务操作
```

单资源读在可见性拒绝时可以返回 not found，以隐藏资源存在性。写操作在调用方能定位资源但
不能修改时应返回 access denied。列表和搜索 API 必须在产生分页数据和总数前应用可见性。

## RBAC 模型

默认权限模型包含：

| 概念 | 含义 |
|------|------|
| 用户 | 可以登录或调用 API 的认证主体。 |
| 角色 | 分配给用户的命名权限组。 |
| 权限 | 某个角色对某个 Nacos 资源允许执行的动作。 |
| 资源 | 由命名空间、分组或资源类型、资源名和领域类型共同标识的 Nacos 对象。 |
| 动作 | 操作类型，目前为读或写。 |

`ROLE_ADMIN` 是全局管理员角色。全局管理员会跳过普通资源权限检查。

## 核心概念

### `ApiType`

`ApiType` 描述 API 受众，以及请求适用的鉴权开关范围。

| 值 | 含义 |
|----|------|
| `OPEN_API` | 面向应用或 SDK 的客户端 API。 |
| `ADMIN_API` | 面向维护者、工具和网关的管理 API。 |
| `CONSOLE_API` | Nacos 控制台使用的 API。 |
| `INNER_API` | 服务端内部 API，通常由服务端身份保护。 |

### `SignType`

`SignType` 标识用于解析和授权资源的领域。

| 值 | 含义 |
|----|------|
| `CONFIG` | 配置资源。 |
| `NAMING` | 服务发现和注册资源。 |
| `AI` | MCP、Prompt、Agent、Tool 等 AI 注册中心资源。 |
| `CONSOLE` | 用户、角色、权限等控制台管理资源。 |
| `LOCK` | [锁资源](../lock/lock-spec.md)。 |
| `SPECIFIED` | 由受保护端点显式提供的资源字符串。 |

### `ActionTypes`

| 值 | 存储值 | 语义 |
|----|--------|------|
| `READ` | `r` | 查询、列表、详情、订阅、监听或只读查看。 |
| `WRITE` | `w` | 创建、更新、删除、发布、注册、注销或状态变更。 |

实现可以存储 `rw` 这类组合动作，但端点注解必须使用与 API 行为匹配的明确动作。

### `@Secured`

`@Secured` 是端点级声明，用于把 Controller 或请求处理器绑定到鉴权模型。

| 字段 | 目的 |
|------|------|
| `action` | 需要的动作，通常为 `READ` 或 `WRITE`。 |
| `resource` | 显式资源名，主要用于 `SPECIFIED` 或控制台资源。 |
| `signType` | 用于资源解析和权限判断的领域。 |
| `parser` | 默认解析器不足时使用的自定义资源解析器。 |
| `tags` | 复制到 `Resource.properties` 的附加元数据。 |
| `apiType` | API 受众与鉴权范围。 |

每个非公开的 v3 HTTP API 和 gRPC 请求处理器都必须声明预期的鉴权元数据。公开端点必须由
所属规范明确记录。

## 授权流程

通用鉴权流程如下：

1. 定位请求的 `@Secured` 元数据。
2. 从 header、参数、token、证书或连接器元数据中构造 `IdentityContext`。
3. 从请求参数或 `@Secured` 显式资源中解析 Nacos `Resource`。
4. 询问选中的鉴权插件，该动作和领域是否启用鉴权。
5. 校验身份。
6. 针对 `Permission(resource, action)` 校验权限。

服务端内部请求在继续处理前，还可能要求配置的服务端身份 key 和 value 校验通过。

## 资源权限名

Nacos 权限基于[资源模型](../design/resource-model-spec.md)派生的资源进行评估：

```text
NamespaceId -> Group 或 resourceType -> resourceName
```

标准资源形式如下：

| 领域 | 权限资源形式 |
|------|--------------|
| 配置 | `{namespaceId}:{group}:config/{dataId}` |
| 注册发现 | `{namespaceId}:{group}:naming/{serviceName}` |
| AI | `{namespaceId}:{group}:ai/{resourceName}`，并携带 AI 资源元数据。 |
| 控制台 | `console/{managementResource}` |
| 显式资源 | `@Secured(resource = ...)` 提供的字符串。 |

默认鉴权实现支持在权限资源中使用 `*` 通配符。命名空间、分组、资源类型和资源名的含义以
资源模型规范为准。

## 鉴权开关范围

鉴权启用状态按 API 受众划分：

| 配置 | 范围 |
|------|------|
| `nacos.core.auth.enabled` | 启用 Open API 和通用鉴权系统。 |
| `nacos.core.auth.admin.enabled` | 启用 Admin API 鉴权。 |
| `nacos.core.auth.console.enabled` | 启用 Console API 和登录行为鉴权。 |

选中的鉴权插件由 `nacos.core.auth.system.type` 指定。

## 插件 API

`/v3/auth/*` 下的鉴权相关 HTTP API 属于插件提供的 API。[默认 Nacos 鉴权插件](default-auth-plugin-spec.md)
提供用户、角色、权限和登录端点。这些端点按路径不归属于 Open、Admin 或 Console API，
但仍必须遵守 Nacos v3 API 的响应、错误和鉴权约定。

## 公开端点

端点只有在所属规范和文档明确说明时，才可以被设计为无需鉴权。典型例子包括登录、由服务端
状态保护的一次性管理员初始化，以及为未认证探测而设计的健康检查或状态端点。

当某个端点因兼容性而公开，而不是当前设计要求公开时，新文档化 API 应作为主 API，旧端点
只作为兼容面保留。
