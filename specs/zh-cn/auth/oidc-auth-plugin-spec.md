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

# OIDC 鉴权插件规范

## 范围

OIDC 鉴权插件让 Nacos 将认证和授权委托给 OpenID Connect 1.0 / OAuth2 身份提供方。它以
`oidc` 作为 auth service name，实现[鉴权插件规范](auth-plugin-spec.md)。

服务端实现位于 `plugin-default-impl/nacos-oidc-auth-plugin`。该插件用于支持标准身份提供方
的控制台 SSO 和 token 访问。Java 客户端同时包含 `OidcClientAuthServiceImpl`，用于通过
OAuth2 client credentials flow 获取 bearer token，并注入到 SDK 请求中。

OIDC 不属于默认 Nacos 用户名/密码鉴权插件。它是通过
`nacos.plugin.auth.type=oidc` 选择的另一种鉴权模式。
`nacos.core.auth.system.type=oidc` 继续作为历史启动 alias。

## 服务端 SPI

`OidcAuthPluginService` 必须满足：

| 方法 | 契约 |
|------|------|
| `getAuthServiceName()` | 返回 `oidc`。 |
| `identityNames()` | 接受 `Authorization` 和 `accessToken`。 |
| `enableAuth(action, type)` | 对所有 action 和 sign type 启用鉴权。 |
| `validateIdentity(identityContext, resource)` | 提取 bearer token 或 `accessToken`，完成 token 校验，将 claims 映射为 OIDC user，并写入 `IdentityContext`。 |
| `validateAuthority(identityContext, permission)` | 全局管理员直接放行；其他用户将权限决策委托给配置的 authorization provider。 |
| `isLoginEnabled()` | 返回 `true`；控制台登录由 OIDC login controller 处理。 |
| `isAdminRequest()` | 返回 `false`；用户初始化和用户管理由 IdP 负责。 |

插件不得以 Nacos 本地用户、角色、权限管理作为事实来源。选择 OIDC 时，控制台中的用户、
角色、权限和密码管理面应隐藏或禁用。

## 必要配置

OIDC 模式通过以下配置选择：

```properties
nacos.plugin.auth.type=oidc
nacos.core.auth.enabled=true
```

服务端之间身份配置和默认 Nacos token secret 仍可能被运行时用于内部通信和兼容路径。

OIDC 插件配置使用标准 full key 前缀 `nacos.plugin.auth.oidc.` 下的 item key。
对应的 `nacos.core.auth.plugin.oidc.{item-key}` 保留为废弃 alias；两者同时存在时，
标准 key 优先。

| item key | 类型 | 默认值 | 敏感 | 生效方式 | 目的 |
|----------|------|--------|------|----------|------|
| `issuer-uri` | string | 空 | 否 | 重启 | 用于 OIDC discovery 的 IdP issuer URI。 |
| `client-id` | string | 空 | 否 | 重启 | 在 IdP 中注册的 OAuth2 client id。 |
| `client-secret` | string | 空 | 是 | 重启 | OAuth2 client secret，同时用于签名 state。 |
| `scope` | string | `openid profile email` | 否 | 重启 | 浏览器登录时请求的 scope。 |
| `token-validation-method` | string | `jwt` | 否 | 重启 | 预留的校验模式选择项；当前服务端仅支持 JWT/JWKS。 |
| `jwks-cache-ttl-seconds` | number | `3600` | 否 | 重启 | 正数，单位为秒的 JWKS 缓存 TTL。 |
| `username-claim` | string | `preferred_username` | 否 | 重启 | 作为 Nacos 展示用户名的 claim。 |
| `roles-claim` | string | `roles` | 否 | 重启 | 提取角色时优先使用的 claim。 |
| `admin-role` | string | `nacos-admin` | 否 | 重启 | 映射为全局管理员的角色。 |
| `auto-create-user` | boolean | `true` | 否 | 重启 | 预留兼容配置，当前不会改变运行时行为。 |
| `authorization-endpoint` | string | 空 | 否 | 重启 | 用于非管理员授权决策的外部端点。 |
| `authorization-timeout-ms` | number | `5000` | 否 | 重启 | 外部授权请求的正数毫秒超时。 |
| `strict-nonce-validation` | boolean | `true` | 否 | 重启 | 当 ID token 缺少或不匹配 nonce 时拒绝 authorization-code 登录。 |
| `strict-audience-validation` | boolean | `true` | 否 | 重启 | 当 token audience 或 authorized party 与 `client-id` 不匹配时拒绝 token。 |

`issuer-uri` 和 `client-id` 是有效服务端配置的必要条件。浏览器登录还需要 `client-secret`、
authorization endpoint discovery 和 token endpoint discovery。

## 统一插件配置生命周期

`OidcAuthPluginService` 实现 `PluginConfigSpec`，并通过插件 detail API 暴露全部 14 项定义。
API 必须对 `client-secret` 脱敏，任何查询响应都不得返回有效明文。

当前生命周期中全部配置均为重启生效。会改变 OIDC 字段的 runtime-persisted 或 local-only API
更新必须被拒绝。启动时，统一插件管理器解析标准 key、历史 alias 和默认值，再将完整 item-key
Map 一次性 apply 给插件。

apply 配置只能构造并原子发布不可变的内存运行时对象图，不得执行 discovery、JWKS、token 或
authorization 网络 I/O。Provider discovery 保持延迟执行，由登录和 JWKS 路径共享，仅缓存成功
结果，失败后允许后续请求重试。

`issuer-uri` 和 `client-id` 只在 OIDC 被选择时必填，但通用 `ConfigItemDefinition.required` 保持
false，因为统一管理器也会初始化未被选择的已发现鉴权插件。OIDC 请求和登录路径仍必须识别并
报告无效的 active 配置，不能将其静默视为可用。

## 浏览器登录流程

当前实现将浏览器端点暴露在 `/v1/auth/oidc` 下。这些端点属于实现兼容端点。新增 Nacos
鉴权 HTTP API 应遵守 [HTTP API 规范](../http-api/api-spec.md)中的 v3 API 规则。

| 端点 | 目的 |
|------|------|
| `/v1/auth/oidc/login` | 将浏览器重定向到 IdP authorization endpoint。 |
| `/v1/auth/oidc/callback` | 接收 authorization code，校验 state 和 nonce，交换 token，并返回控制台。 |
| `/v1/auth/oidc/logout` | 清理控制台侧鉴权状态，并可选重定向到 IdP logout endpoint。 |
| `/v1/auth/oidc/config` | 告诉控制台 OIDC 模式已启用，且本地用户/角色/权限管理已禁用。 |

登录流程必须：

- 使用 `{issuer-uri}/.well-known/openid-configuration` 进行 OIDC discovery。
- 生成自包含签名的 `state` 和 `nonce`。
- 在 IdP token endpoint 交换 authorization code。
- 接受用户前校验 ID token 签名和 claims。
- 只把短期 console cookie 作为前端交接机制，随后依赖正常请求身份传播。

## Token 校验

当前实现通过 JWKS 校验 JWT token。校验必须：

- 只接受已支持的非对称 JWS 算法。
- 要求 `sub`、`iss`、`exp` 和 `iat` claims。
- 拒绝已过期或尚未生效的 token。
- 校验 issuer，并兼容尾部斜杠差异。
- 启用 strict audience validation 时，校验 audience 或 `azp` 与 `client-id` 匹配。
- 当签名校验失败时刷新 JWKS 并重试一次，以兼容 key rotation。

用户名映射优先使用配置的 `username-claim`，随后回退到 `preferred_username`、`email`，
最后使用 `sub`。角色映射优先使用配置的 `roles-claim`，也可以读取常见 Keycloak 风格的
`realm_access.roles`、`resource_access.{client-id}.roles` 和 `groups` claims。配置的
`admin-role` 会映射为 Nacos 全局管理员。

## 授权

OIDC authentication 负责识别调用方。Authorization 仍然必须回答该调用方是否可以对解析后的
Nacos 资源执行目标动作。

当前实现会根据映射角色在本地放行全局管理员。对于非管理员用户，它会调用配置的外部
`authorization-endpoint`，请求包含：

| 字段 | 含义 |
|------|------|
| `token` | 用户 access token。 |
| `resource` | 从 `Resource` 推导出的 Nacos resource URI。 |
| `action` | Nacos action，例如 read 或 write。 |
| `resourceType`, `namespace`, `group`, `resourceName` | 结构化的 Nacos 资源身份。 |

如果 `authorization-endpoint` 为空，当前实现会允许非管理员访问。需要授权隔离的部署必须
配置外部 authorization endpoint，或提供更严格的 OIDC authority provider。

## Java 客户端集成

`OidcClientAuthServiceImpl` 是 Java Client SDK 鉴权扩展。它与浏览器控制台 SSO 是两个不同
流程。

| 客户端配置 | 目的 |
|------------|------|
| `nacos.client.auth.oidc.issuer-uri` | 用于 token endpoint discovery 的 OIDC issuer。 |
| `nacos.client.auth.oidc.client-id` | OAuth2 client id。 |
| `nacos.client.auth.oidc.client-secret` | OAuth2 client secret。 |
| `nacos.client.auth.oidc.scope` | OAuth2 scopes，默认 `openid`。 |
| `nacos.client.auth.oidc.token-endpoint` | 直接指定 token endpoint；设置后跳过 discovery。 |

配置完成后，客户端使用 OAuth2 client credentials grant，并在 token 过期前刷新，同时注入
`Authorization: Bearer ...` 和 `accessToken`。未配置时，它必须返回空 identity context，
不得让无关 SDK 调用失败。

## 待处理问题

- 配置模型声明了 `token-validation-method=introspection`，但当前服务端校验路径基于
  JWT/JWKS。在实现补齐前，不应将 introspection 文档化为已支持能力。
- OIDC 浏览器端点当前使用 `/v1/auth/oidc`。未来新增 Nacos 原生 auth API 时，应使用
  `/v3/auth/oidc/*` 并遵守标准响应和错误模型。
- 通用配置模型暂时无法表达“插件被选中时必填”。在该能力完成设计前，OIDC 保留 active mode
  校验。

## 关联规范

- 通用鉴权 SPI 规则：[鉴权插件规范](auth-plugin-spec.md)。
- Java 客户端鉴权扩展规则：[Java SDK 实现规范](../sdk/sdk-java-impl-spec.md)。
- 默认用户名/密码鉴权：[默认鉴权插件实现规范](default-auth-plugin-spec.md)。
