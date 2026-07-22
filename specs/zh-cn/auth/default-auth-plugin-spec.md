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

## 鉴权框架配置

| 配置 | 目的 |
|------|------|
| `nacos.core.auth.enabled` | 启用通用鉴权系统和 Open API 鉴权。 |
| `nacos.core.auth.admin.enabled` | 启用 Admin API 鉴权。 |
| `nacos.core.auth.console.enabled` | 启用 Console API 鉴权和默认登录行为。 |
| `nacos.plugin.auth.type` | 启动时选择鉴权插件，默认 `nacos`；`nacos.core.auth.system.type` 是历史 alias。 |
| `nacos.core.auth.server.identity.key` | 服务端之间调用的身份 key。 |
| `nacos.core.auth.server.identity.value` | 服务端之间调用的身份 value。 |

这些配置负责鉴权模块、API 范围、启动期插件选择和服务端身份，不属于 `auth:nacos` 插件
自身的配置项。插件选择需要重启生效，服务端身份值必须由部署环境独立配置。

## 统一管理的插件配置

`nacos` 实现直接实现 `PluginConfigSpec`，并以可配置插件 `auth:nacos` 注册。其 canonical
配置前缀为 `nacos.plugin.auth.nacos.`。

| item key | canonical 静态 key | 历史静态 alias | 类型 | 生效模式 | 默认值 | 敏感 |
|----------|--------------------|----------------|------|----------|--------|------|
| `token.secret.key` | `nacos.plugin.auth.nacos.token.secret.key` | `nacos.core.auth.plugin.nacos.token.secret.key` | String | `RESTART` | 空 | 是 |
| `token.expire.seconds` | `nacos.plugin.auth.nacos.token.expire.seconds` | `nacos.core.auth.plugin.nacos.token.expire.seconds` | Number | `RUNTIME` | `18000` | 否 |
| `token.cache.enable` | `nacos.plugin.auth.nacos.token.cache.enable` | `nacos.core.auth.plugin.nacos.token.cache.enable` | Boolean | `RUNTIME` | `false` | 否 |
| `caching.enabled` | `nacos.plugin.auth.nacos.caching.enabled` | `nacos.core.auth.caching.enabled` | Boolean | `RUNTIME` | `true` | 否 |
| `anonymous.ai.enabled` | `nacos.plugin.auth.nacos.anonymous.ai.enabled` | `nacos.core.auth.nacos.anonymous.ai.enabled` | Boolean | `RUNTIME` | `false` | 否 |

`token.expire.seconds` 必须大于零。当任一 Nacos API 鉴权范围需要 token 能力时，
`token.secret.key` 必须是有效的 Base64 内容，且解码后不少于 32 字节。token 密钥必须由
部署环境独立设置，使用默认值或共享值是不安全的。插件管理 API 必须返回脱敏后的密钥，
并禁止通过运行时更新修改该字段。

canonical key 与历史 alias 同时存在时，canonical key 优先。历史 alias 为兼容已有部署
继续可读，并在使用时输出不包含配置值的迁移提示。运行时和 localOnly 更新使用表中的
item key，并遵循 [Nacos 插件化规范](../plugin/plugin-spec.md)定义的 source 完整 Map 语义。

插件持有不可变的 effective 配置快照。应用新快照时更新 token 过期时间、token 缓存选择、
鉴权信息缓存和匿名访问，消费者不再直接读取 Spring 环境。JWT parser 由已接受的
restart-only 密钥构建。开启 token 缓存时，在同一个基础 manager 外选择缓存包装；关闭时
切回基础 manager，并清空 token 缓存。token 过期时间变化时也会清空包装缓存，使下一次
取 token 使用新的运行时有效期；已经返回给客户端的 token 仍按签名中的原过期时间有效。

`ldap` 实现同样实现 `PluginConfigSpec`，并以可配置插件 `auth:ldap` 注册。其 canonical
配置前缀为 `nacos.plugin.auth.ldap.`。

| item key | canonical 静态 key | 历史静态 alias | 类型 | 生效模式 | 默认值 | 敏感 |
|----------|--------------------|----------------|------|----------|--------|------|
| `url` | `nacos.plugin.auth.ldap.url` | `nacos.core.auth.ldap.url` | String | `RESTART` | `ldap://localhost:389` | 否 |
| `base-dn` | `nacos.plugin.auth.ldap.base-dn` | `nacos.core.auth.ldap.basedc` | String | `RESTART` | `dc=example,dc=org` | 否 |
| `timeout` | `nacos.plugin.auth.ldap.timeout` | `nacos.core.auth.ldap.timeout` | Number | `RESTART` | `3000` | 否 |
| `user-dn` | `nacos.plugin.auth.ldap.user-dn` | `nacos.core.auth.ldap.userDn` | String | `RESTART` | `cn=admin,dc=example,dc=org` | 否 |
| `password` | `nacos.plugin.auth.ldap.password` | `nacos.core.auth.ldap.password` | String | `RESTART` | `password` | 是 |
| `filter-prefix` | `nacos.plugin.auth.ldap.filter-prefix` | `nacos.core.auth.ldap.filter.prefix` | String | `RESTART` | `uid` | 否 |
| `case-sensitive` | `nacos.plugin.auth.ldap.case-sensitive` | `nacos.core.auth.ldap.case.sensitive` | Boolean | `RESTART` | `true` | 否 |
| `ignore-partial-result-exception` | `nacos.plugin.auth.ldap.ignore-partial-result-exception` | `nacos.core.auth.ldap.ignore.partial.result.exception` | Boolean | `RESTART` | `false` | 否 |

`timeout` 单位为毫秒且必须大于零。插件管理 API 必须对绑定密码脱敏。第一阶段 LDAP 自有
字段全部为 `RESTART`，因此运行时或 local-only 更新只要新增、修改或移除这些字段都必须
拒绝。

canonical key 与历史 alias 同时存在时 canonical key 优先。历史模板中的
`nacos.core.auth.ldap.userdn` 没有生产读取点，且原本想表达的 user DN pattern 语义不明确，
因此不作为兼容 alias。

LDAP 插件持有不可变的 effective 配置快照。Spring LDAP context 和 template 从已接受快照
延迟构建，LDAP 消费者不再通过第二套 `@Value` 属性读取配置。LDAP 只改变身份认证方式；
token 签名和有效期、Nacos 用户与角色存储及授权仍使用 `auth:nacos` 配置的基础设施，相关
共享字段不复制到 `auth:ldap` definitions。

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
- `auth:nacos` 的 `anonymous.ai.enabled` 已启用。
- 默认插件将请求接受为内置匿名身份。

只有当请求没有显式提供任何默认鉴权凭据 key 时，才允许降级为匿名身份。提供
`Authorization`、`accessToken`、`username` 或 `password` 都视为显式凭据存在，
即使对应值为空白也一样。如果这些凭据为空白或无效，插件必须返回认证失败，而不能降级为
匿名身份。在 HTTP 过滤器层，身份或权限校验失败会被转换为 HTTP 403 的
`ACCESS_DENIED` 响应；插件级失败码和消息可以保留在响应详情中。

开启匿名访问后，只会立即开启匿名身份接受。后台协调任务随后保证保留的匿名用户和角色
存在；首次初始化时增加 `public:*:ai/*` 读权限，并最后写入匿名角色绑定，将该绑定作为
持久化完成标记。多节点并发创建发生冲突时，只有重新读取到预期持久化状态才视为成功。

如果匿名角色绑定已经存在，则视为已经初始化，协调任务不会重新补回宽泛的默认权限，
从而保留管理员自定义的匿名权限范围。关闭匿名访问只会停止匿名身份接受，不删除保留的
用户、角色或权限。协调任务的本地状态只用于减少数据库操作，不参与鉴权判断；当找不到
匹配的角色或权限时，普通 RBAC 权限校验仍然必须拒绝匿名身份。

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

统一管理表中的历史静态 alias 继续兼容。新的发行版模板使用 canonical key，并在注释中
标明历史 key。canonical token 密钥缺失或为空时，启动脚本会把合法的历史密钥迁移到
canonical key；二者同时存在时 canonical 值优先。迁移过程不得输出密钥内容。

## 待处理问题

- `ldap` 插件已经通过 `PluginConfigSpec` 接管 LDAP 连接和查找配置，但仍消费由
  `auth:nacos` 配置的 token、用户、角色和授权基础设施。后续应把这些共享能力迁移到显式的
  auth 模块服务，使身份提供方插件不再依赖默认插件的配置所有权。
