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

# V3 HTTP API 范围

本文档说明当前 v3 HTTP API 覆盖范围。它补充
[HTTP API 规范](api-spec.md)，后者定义通用设计规则。端点鉴权遵循
[HTTP 鉴权规范](authorization-spec.md)，响应形态遵循[响应与错误规范](response-error-spec.md)。

## 1. 范围

本文档覆盖在 Nacos Web context path 之后，以以下 v3 前缀开头的 HTTP
端点：

| 前缀 | API 类型 | 主要用户 | 当前鉴权范围 |
| --- | --- | --- | --- |
| `/v3/client` | Open API | SDK 和自定义客户端 | `ApiType.OPEN_API` |
| `/v3/admin` | Admin API | 运维人员和维护工具 | `ApiType.ADMIN_API` |
| `/v3/console` | Console API | Nacos 控制台 UI 后端调用 | `ApiType.CONSOLE_API` |
| `/v3/auth` | Auth plugin API | 插件提供的鉴权和初始化 API | [默认鉴权插件](../auth/default-auth-plugin-spec.md) |

本文档不覆盖：

- v1/v2 兼容 API，它们已经外置到
  [nacos-api-legacy-adapter](https://github.com/nacos-group/nacos-api-legacy-adapter)；
- gRPC 请求和响应契约；
- 未作为 v3 HTTP Controller 暴露的内部集群 API；
- AI Registry adaptor API，它有独立的兼容性边界。

## 2. 当前事实来源

V3 HTTP 行为当前由以下代码位置定义：

| 领域 | 代码来源 |
| --- | --- |
| Admin core | `core/src/main/java/com/alibaba/nacos/core/controller/v3` |
| Admin config | `config/src/main/java/com/alibaba/nacos/config/server/controller/v3` |
| Admin naming | `naming/src/main/java/com/alibaba/nacos/naming/controllers/v3` |
| Admin AI | `ai/src/main/java/com/alibaba/nacos/ai/controller` |
| Console | `console/src/main/java/com/alibaba/nacos/console/controller/v3` |
| Auth v3 | `plugin-default-impl/nacos-default-auth-plugin/src/main/java/.../controller/v3` |
| 路径常量 | `Commons`、config `Constants`、naming `UtilsAndCommons`、AI `Constants`、`AuthConstants` |

对应的网站源文件：

- `admin/admin-api.md`
- `admin/console-api.md`
- `user/open-api.md`

## 3. 当前 API 家族

本节记录当前已经实现的 API 家族。数量来自对 `src/main/java` 中 Spring
映射的脚本辅助盘点，应作为核对参考，而不是最终 OpenAPI 导出。

| 家族 | 近似映射数 | Method | 说明 |
| --- | ---: | --- | --- |
| `/v3/client/cs/config` | 1 | GET | 供自定义 HTTP 客户端查询配置。 |
| `/v3/client/ns/instance` | 3 | GET, POST, DELETE | 注册、心跳、注销和查询服务实例。 |
| `/v3/client/ai/prompt` | 1 | GET | 运行时 Prompt 查询。 |
| `/v3/client/ai/skills` | 1 | GET | 运行时 Skill zip 下载。 |
| `/v3/client/ai/agentspecs` | 2 | GET | 运行时 AgentSpec 获取和搜索。 |
| `/v3/admin/core/*` | 25 | GET, POST, PUT, DELETE | Loader、集群、ops、命名空间、状态、插件。 |
| `/v3/admin/cs/*` | 25 | GET, POST, PUT, DELETE | 配置 CRUD、历史、监听者、容量、指标、ops。 |
| `/v3/admin/ns/*` | 29 | GET, POST, PUT, DELETE | 服务、实例、客户端、集群、健康状态、ops。 |
| `/v3/admin/ai/*` | 71 | GET, POST, PUT, DELETE | MCP、A2A、Prompt、Skill、AgentSpec、Pipeline。 |
| `/v3/console/core/*` | 7 | GET, POST, PUT, DELETE | 控制台集群和命名空间操作。 |
| `/v3/console/cs/*` | 17 | GET, POST, DELETE | 控制台配置和历史操作。 |
| `/v3/console/ns/*` | 11 | GET, POST, PUT, DELETE | 控制台服务和实例操作。 |
| `/v3/console/ai/*` | 67 | GET, POST, PUT, DELETE | 控制台 AI 管理、导入、生命周期、Pipeline。 |
| `/v3/console/copilot/*` | 6 | GET, POST | 配置和 SSE Copilot 操作。 |
| `/v3/auth/user` | 7 | GET, POST, PUT, DELETE | 默认鉴权插件中的用户登录和管理。 |
| `/v3/auth/role` | 4 | GET, POST, DELETE | 默认鉴权插件中的角色管理。 |
| `/v3/auth/permission` | 4 | GET, POST, DELETE | 默认鉴权插件中的权限管理。 |

## 4. Open API 已实现行为

已实现的 Open API 范围：

| 端点 | 行为 |
| --- | --- |
| `GET /v3/client/cs/config` | 查询单个配置。不提供 HTTP 长轮询。 |
| `POST /v3/client/ns/instance` | 注册实例，或在 `heartBeat=true` 时发送心跳。 |
| `DELETE /v3/client/ns/instance` | 注销实例。实例不存在时仍视为成功。 |
| `GET /v3/client/ns/instance/list` | 查询服务的启用实例列表。会过滤 disabled 实例。 |
| `GET /v3/client/ai/prompt` | 按版本、标签或 latest 查询 Prompt。 |
| `GET /v3/client/ai/skills` | 以 zip 响应下载在线 Skill 包。 |
| `GET /v3/client/ai/agentspecs` | 按版本、标签或 latest 查询 AgentSpec。可能允许匿名访问。 |
| `GET /v3/client/ai/agentspecs/search` | 搜索运行时可用的已启用 AgentSpec。 |

## 5. Admin API 已实现行为

Admin API 面向运维人员，默认使用 `ApiType.ADMIN_API`。Nacos 3.x 标准
Admin API 使用 `/v3/admin/*` 路径。v1/v2 Admin API 已从当前 Nacos 主
发行包中移除，新接入应迁移到 v3 Admin API；如果迁移期仍需使用 v1/v2
Admin API，应参考
[nacos-api-legacy-adapter](https://github.com/nacos-group/nacos-api-legacy-adapter)
方案和[兼容与废弃策略规范](../design/compatibility-deprecation-spec.md)。
`nacos.core.auth.admin.enabled` 仅表示是否启用 Admin API 鉴权，不是旧
Admin API 兼容开关。

当前模块：

- `core`：连接 loader、集群节点数据、Raft 和 ID 运维、命名空间、插件和
  服务端状态。
- `cs`：配置 CRUD、元数据、批量操作、历史、监听者、容量、指标和 ops。
- `ns`：服务、实例、集群、健康状态、客户端和注册中心 ops。
- `ai`：MCP、A2A、Prompt、Skill、AgentSpec 和 Pipeline 管理。

需要更明确文档化的已实现行为：

- Naming service 创建会创建持久化服务元数据。
- Open naming instance 心跳复用 `POST /v3/client/ns/instance` 端点，并在
  需要重新注册时返回 `INSTANCE_NOT_FOUND`。
- Config 查询在返回 Admin API 详情前会解密加密内容。
- Config 发布在未提供 encrypted data key 且适用加密处理器时会加密内容。
- AI Prompt 在同一个 Controller 中同时包含已废弃兼容端点和新的生命周期端点。

## 6. Console API 已实现行为

Console API 服务于 Nacos Web 控制台，它不是 Open API 的同一稳定性边界。
它默认使用 `ApiType.CONSOLE_API`，并经常使用控制台专用资源名、
`ONLY_IDENTITY` 或面向 UI 的响应模型。
Console 部署、UI 和 handler 边界由 [Console 规范](../console/console-spec.md)定义。

Console API 模块在 UI 需要时会镜像 Admin 模块：

- 服务端状态和健康检查；
- core 集群、命名空间和插件；
- 配置和历史；
- 注册中心服务和实例；
- AI 资源和 Copilot。

Console API 文档应避免把控制台专用端点呈现为推荐的自动化 API。自动化用户
应优先使用 Admin API，除非某个功能被明确设计为仅控制台可用。

## 7. Auth API 已实现行为

V3 Auth API 位于默认鉴权插件中，而不是 core 模块中：

```text
/v3/auth/user
/v3/auth/role
/v3/auth/permission
```

已实现行为：

- 用户管理支持创建、删除、密码更新、登录、列表和搜索。
- 角色管理支持添加、删除、列表和搜索。
- 权限管理支持添加、删除和列表。
- 第一个管理员初始化由 `POST /v3/auth/user/admin` 实现。

默认鉴权插件随 Nacos 一起发布，因此它的 v3 auth 端点应遵循 Nacos HTTP API 规范和
[鉴权插件规范](../auth/auth-plugin-spec.md)。

## 8. 文档 Gap 记录

这不是 bug 列表，而是记录当前文档和代码可能描述了不同 API 面的地方。

- Admin AI Prompt 生命周期：代码增加 `/governance`、`/version`、`/draft`、
  `/submit`、`/publish`、`/force-publish`、`/online`、`/offline`、`/labels`、
  `/description` 和 `/biz-tags`；文档主要覆盖旧的 `/detail`、`/label`、
  `/metadata`，以及 list 和 versions。
- Console AI Prompt 生命周期：控制台代码在 `/v3/console/ai/prompt` 下镜像
  Admin 生命周期；文档主要覆盖旧的 `/detail`、`/label` 和 `/metadata`。
- Pipeline list/detail：代码暴露 `/v3/*/ai/pipelines/list`、`/detail` 和
  `/{pipelineId}`；文档展示 `/v3/*/ai/pipelines` 和 `/{pipelineId}`。
- Force publish：代码中 Prompt、Skill 和 AgentSpec 都有 `POST /force-publish`；
  文档没有一致描述这个高权限操作。
- AgentSpec version meta：代码中有
  `GET /v3/admin/ai/agentspecs/version/meta`；admin API 文档未记录。
- Auth v3：代码暴露 `/v3/auth/user`、`/role` 和 `/permission`；三份网站
  API 文档未覆盖这个 API 面。
- Config Open API 异常处理：`ConfigOpenApiController` 没有 `@NacosApi`，
  而大多数 v3 Controller 都有；Open API 文档假设统一响应。
- Config 和 Naming ExceptionHandler：Config 和 Naming 仍有历史模块级
  `ControllerAdvice`，可能返回纯文本错误体。它们应在 v3 API 上收敛到
  `NacosApiExceptionHandler`。

## 9. 废弃兼容说明

部分 v3 AI API 在本规范建立之前已经发布，后续又被更清晰的生命周期 API 或
REST 风格 API 替代。这些旧端点应视为废弃兼容 API：

- AI Prompt legacy 端点，例如 `/detail`、`/label` 和 `/metadata`。
- Pipeline 中不符合当前 `/list` 和 `/detail` 形态的 legacy REST 风格端点。

兼容端点可以在过渡期内继续保留，但面向用户的文档应以新 API 作为主要契约。
废弃端点只应出现在兼容章节中，并按照
[兼容与废弃策略规范](../design/compatibility-deprecation-spec.md)提供迁移说明。
