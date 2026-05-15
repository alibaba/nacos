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

# HTTP API 鉴权规范

本文档定义 Nacos 鉴权模型如何应用到 v3 HTTP API。共享鉴权领域模型由
[鉴权与权限规范](../auth/auth-permission-spec.md) 定义，插件契约由
[鉴权插件规范](../auth/auth-plugin-spec.md) 和
[可见性插件规范](../auth/visibility-plugin-spec.md) 定义。

## 1. 鉴权元组

V3 HTTP API 的有效鉴权元组为：

```text
apiType + signType + resource + action + tags
```

其中：

- `apiType` 区分 `OPEN_API`、`ADMIN_API`、`CONSOLE_API` 和内部 API。
- `signType` 标识资源领域，例如 `CONFIG`、`NAMING`、`AI` 或 `CONSOLE`。
- `resource` 标识受保护的资源路径或逻辑资源名。
- `action` 通常为 `READ` 或 `WRITE`。
- `tags` 增加特殊行为，例如 `ONLY_IDENTITY` 或 `ALLOW_ANONYMOUS`。

## 2. Filter 分流

当前代码按 `apiType` 分发鉴权：

- `AuthAdminFilter` 处理 `@Secured.apiType()` 为 `ApiType.ADMIN_API` 的方法。
- `AuthFilter` 处理 `apiType()` 不是 `ADMIN_API` 的受保护 API，包括
  `OPEN_API`、`CONSOLE_API` 和内部 API。

## 3. 必要注解

V3 HTTP API 应声明 `@Secured`，除非该端点被明确设计为：

- 公开端点；
- 初始化端点；
- 健康检查端点；
- 已记录的兼容路径。

Admin API 应使用 `ApiType.ADMIN_API`。Console API 应使用
`ApiType.CONSOLE_API`。Open API 应使用 `ApiType.OPEN_API`。

## 4. 公开端点和初始化端点

端点只有在被明确设计为公开端点、初始化端点、健康检查端点或兼容端点时，
才可以不声明 `@Secured`。公开端点必须在文档中标记为公开，并且不得暴露
敏感运维细节。

已实现的公开端点包括：

- `GET /v3/admin/core/state`
- `GET /v3/admin/core/state/liveness`
- `GET /v3/admin/core/state/readiness`
- `GET /v3/console/server/state`
- `GET /v3/console/server/announcement`
- `GET /v3/console/server/guide`
- `GET /v3/console/health/liveness`
- `GET /v3/console/health/readiness`

对应的 Admin API 和 Console API 文档已将这些端点标记为公开接口，无需身份信息。

初始化行为：

- `/v3/auth/user/admin` 可以在不存在全局管理员，且鉴权系统为 `NACOS` 时
  创建第一个管理员用户。

## 5. 插件提供的 Auth API

`/v3/auth/*` API 面属于鉴权插件。[Nacos 默认鉴权插件](../auth/default-auth-plugin-spec.md)
随 Nacos 一起发布，必须遵循 [Nacos HTTP API](api-spec.md) 对路径形态、响应形态、
参数校验和错误行为的规范。

第三方鉴权插件通过 Nacos 暴露 HTTP API 时，也建议遵循同一套规则。

## 6. 已实现例外

以下已实现行为需要在端点级文档中说明：

- 部分 AI 客户端端点通过 `ALLOW_ANONYMOUS` 允许匿名访问。
