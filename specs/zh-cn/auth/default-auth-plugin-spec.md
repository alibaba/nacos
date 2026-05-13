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

# 默认鉴权插件实现规范

## 范围

默认鉴权实现包当前提供 `nacos` 和 `ldap` 两个鉴权插件。`nacos` 插件提供用户名/密码登录、
token 认证、RBAC 权限管理，以及 AI 资源使用的默认可见性集成。它实现
[鉴权插件规范](auth-plugin-spec.md)、共享的[鉴权与权限规范](auth-permission-spec.md)
和[可见性插件规范](visibility-plugin-spec.md)。

Java 客户端为默认插件暴露的用户名/密码和 token 流程提供
`NacosClientAuthServiceImpl`。RAM、OIDC 等其他内置客户端鉴权服务属于 Java Client SDK
鉴权扩展，由 [Java SDK 实现规范](../sdk/sdk-java-impl-spec.md)和
[鉴权插件规范](auth-plugin-spec.md)定义，不属于本文描述的服务端默认插件实现。

默认实现用于在可信内网环境中降低误用风险。它不是面向恶意公网环境的完整强鉴权方案。
如果需要暴露到公网，应使用外部安全边界，或选择更强的鉴权插件。

## 必要配置

| 配置 | 目的 |
|------|------|
| `nacos.core.auth.enabled` | 启用通用鉴权系统和 Open API 鉴权。 |
| `nacos.core.auth.admin.enabled` | 启用 Admin API 鉴权。 |
| `nacos.core.auth.console.enabled` | 启用 Console API 鉴权和默认登录行为。 |
| `nacos.core.auth.system.type` | 选择鉴权插件，默认 `nacos`。 |
| `nacos.core.auth.plugin.nacos.token.secret.key` | 默认 token 签名密钥，部署时必须配置。 |
| `nacos.core.auth.plugin.nacos.token.expire.seconds` | token 过期时间。 |
| `nacos.core.auth.plugin.nacos.token.cache.enable` | 启用 token 解析和校验缓存。 |
| `nacos.core.auth.server.identity.key` | 服务端之间调用的身份 key。 |
| `nacos.core.auth.server.identity.value` | 服务端之间调用的身份 value。 |
| `nacos.core.auth.caching.enabled` | 启用用户、角色和权限缓存。 |
| `nacos.core.auth.nacos.anonymous.ai.enabled` | 当端点明确选择匿名访问时，允许匿名 AI 访问。 |

token 密钥和服务端身份值必须由部署环境独立配置。使用默认值或共享值是不安全的。

`ldap` 插件变体额外使用 `nacos.core.auth.ldap.*` 配置族。LDAP 只改变身份认证方式，授权仍然
使用 Nacos 角色和权限。

## 身份

插件接受以下身份输入：

| 输入 | 用途 |
|------|------|
| `Authorization: Bearer ...` | token 认证。 |
| `accessToken` | 通过请求参数或 header 进行 token 认证。 |
| `username` 和 `password` | 登录或直接用户名/密码认证。 |
| 服务端身份 key/value | 服务端之间调用的身份。 |

认证成功后，插件会向 `IdentityContext` 补充已认证的 Nacos 用户和用户 ID。全局管理员状态由
用户角色模型推导。

匿名 AI 访问只有在以下条件同时满足时才允许：

- 端点标记该请求允许匿名访问。
- `nacos.core.auth.nacos.anonymous.ai.enabled` 已启用。
- 默认插件将请求接受为内置匿名身份。

当匿名 AI 访问启用时，实现会初始化保留的匿名用户和角色，并授予 `public:*:ai/*` 读权限。

## 默认 Java 客户端鉴权集成

默认插件对应的 Java 客户端侧集成为 `NacosClientAuthServiceImpl`。它通过客户端鉴权 SPI
加载，并在配置了 `username` 和 `password` 时调用默认 `/v3/auth/user/login` API。

| 客户端实现 | 身份材料 | 契约 |
|------------|----------|------|
| `NacosClientAuthServiceImpl` | `username`、`password` 和 `accessToken`。 | 通过默认鉴权 API 登录，附加返回的 `accessToken`，并在 token 过期前刷新。 |

该集成不得修改请求 payload，只提供当前服务端鉴权插件消费的身份材料。
[RAM](ram-auth-plugin-spec.md)、[OIDC](oidc-auth-plugin-spec.md) 等其他客户端鉴权实现作为
Java Client SDK 扩展在 [Java SDK 实现规范](../sdk/sdk-java-impl-spec.md)中描述。

## RBAC 存储模型

默认插件存储：

| 对象 | 含义 |
|------|------|
| `User` | 用户名和密码身份。 |
| `RoleInfo` | 分配给用户名的角色。 |
| `PermissionInfo` | 分配给角色的资源和动作。 |

`ROLE_ADMIN` 是全局管理员角色。拥有该角色的用户可以访问所有资源和控制台管理操作。

## 权限资源格式

默认资源权限使用：

```text
{namespaceId}:{group}:{signType}/{resourceName}
```

示例：

| 资源 | 示例 |
|------|------|
| 配置数据 | `public:DEFAULT_GROUP:config/example.properties` |
| 注册发现服务 | `public:DEFAULT_GROUP:naming/com.example.Service` |
| 控制台用户 | `console/users` |
| 控制台角色 | `console/roles` |
| 控制台权限 | `console/permissions` |
| 可见性权限 | `@@visibility/public/mcp/example-mcp` |

规则：

- 权限资源中可以使用 `*` 作为通配符。
- group 为空时，权限检查会在 group 段使用 `*`。
- resource name 为空时，资源名段会变成 `*`。
- 存储资源以 `:` 开头时，会补充默认命名空间 `public`。
- `SPECIFIED` 资源直接使用显式资源字符串。
- 存储动作可以是 `r`、`w` 或 `rw`。

非管理员角色不得管理控制台用户、角色或权限。

## 默认鉴权 API

默认插件拥有以下 v3 API 族：

| 路径 | 目的 |
|------|------|
| `/v3/auth/user` | 用户管理和密码更新。 |
| `/v3/auth/user/login` | 登录和 token 签发。 |
| `/v3/auth/user/admin` | 当不存在全局管理员时进行管理员初始化。 |
| `/v3/auth/role` | 角色管理。 |
| `/v3/auth/permission` | 权限管理。 |

管理端点必须使用控制台域的 `@Secured` 资源保护，例如 `console/users`、
`console/roles`、`console/permissions` 和 `console/user/password`。

登录端点是有意公开的。管理员初始化端点只在无管理员初始化状态下有意暴露；一旦全局管理员
已经存在，必须拒绝该端点。这些 API 属于 [V3 API 范围](../http-api/v3-api-surface.md)，
并必须遵守 [HTTP 鉴权规范](../http-api/authorization-spec.md)。

## 默认可见性实现

默认可见性实现名称同样为 `nacos`，当前用于 AI 资源。

默认行为：

- 除非领域提供其他 scope，新资源默认 `PRIVATE`。
- 全局管理员可以读写所有具备可见性语义的资源。
- 资源 owner 可以读写该资源。
- `PUBLIC` 资源可以被非 owner 读取。
- 显式可见性权限可以通过鉴权插件授予访问。
- 匿名 AI 读访问只能通过匿名 AI 显式选择路径开启。
- 读拒绝可以返回 not found，以隐藏资源存在性。
- 写拒绝返回 access denied。

显式可见性权限资源使用：

```text
@@visibility/{namespaceId}/{resourceType}/{resourceName}
```

范围查询必须组合基础可见性谓词和显式授权资源。当前默认实现已经暴露显式授权资源结构；
API 和存储集成在补齐后必须使用该结构。

对于 AI 列表和搜索路径，可见性必须在 count 和分页查询前转换为仓储层查询条件。这可以让
`totalCount` 与可见资源集合保持一致，并避免全量加载后在内存中过滤。

## 兼容性

旧端点或兼容端点可以为已有客户端保留，但新的文档和新的开发应以 v3 鉴权 API 以及本文档
定义的插件契约为准。

## 待处理问题

- `ldap` 插件当前通过继承 `NacosAuthPluginService` 耦合在默认鉴权实现包中。从概念上看，
  LDAP 是由外部身份提供方支撑的独立鉴权插件，不属于默认 Nacos 用户名/密码和 token
  实现。后续应将它拆分为独立鉴权插件包和规范，同时保持已有
  `nacos.core.auth.system.type=ldap` 部署兼容。
