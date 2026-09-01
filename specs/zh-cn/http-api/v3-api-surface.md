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
| `/v3/client/ai/resources` | 1 | GET | 协议无关的跨资源 Search。 |
| `/v3/client/ai/prompt` | 2 | GET | 运行时 Prompt 查询和 Search。 |
| `/v3/client/ai/skills` | 2 | GET | 运行时 Skill zip 下载和 Search。 |
| `/v3/client/ai/agentspecs` | 2 | GET | 运行时 AgentSpec 获取和搜索。 |
| `/v3/client/ai/mcp` | 6 | GET, POST, PUT, DELETE | MCP Search、Serving Query、兼容 Release、Runtime Endpoint Publication 和 Heartbeat。 |
| `/v3/admin/core/*` | 25 | GET, POST, PUT, DELETE | Loader、集群、ops、命名空间、状态、插件。 |
| `/v3/admin/cs/*` | 25 | GET, POST, PUT, DELETE | 配置 CRUD、历史、监听者、容量、指标、ops。 |
| `/v3/admin/ns/*` | 29 | GET, POST, PUT, DELETE | 服务、实例、客户端、集群、健康状态、ops。 |
| `/v3/admin/ai/*` | 101 | GET, POST, PUT, DELETE | MCP、A2A、Agent、Prompt、Skill、AgentSpec、Pipeline。 |
| `/v3/console/core/*` | 7 | GET, POST, PUT, DELETE | 控制台集群和命名空间操作。 |
| `/v3/console/cs/*` | 17 | GET, POST, DELETE | 控制台配置和历史操作。 |
| `/v3/console/ns/*` | 11 | GET, POST, PUT, DELETE | 控制台服务和实例操作。 |
| `/v3/console/ai/*` | 79 | GET, POST, PUT, DELETE | 控制台 AI 管理、导入、生命周期、Pipeline。 |
| `/v3/console/copilot/*` | 6 | GET, POST | 配置和 SSE Copilot 操作。 |
| `/v3/auth/user` | 7 | GET, POST, PUT, DELETE | 默认鉴权插件中的用户登录和管理。 |
| `/v3/auth/role` | 4 | GET, POST, DELETE | 默认鉴权插件中的角色管理。 |
| `/v3/auth/permission` | 4 | GET, POST, DELETE | 默认鉴权插件中的权限管理。 |
| `/v3/auth/visibility` | 2 | POST, DELETE | 默认鉴权插件中的插件自有资源可见性授权管理。 |

## 4. Open API 已实现行为

已实现的 Open API 范围：

| 端点 | 行为 |
| --- | --- |
| `GET /v3/client/cs/config` | 查询单个配置。不提供 HTTP 长轮询。 |
| `POST /v3/client/ns/instance` | 注册实例，或在 `heartBeat=true` 时发送心跳。 |
| `DELETE /v3/client/ns/instance` | 注销实例。实例不存在时仍视为成功。 |
| `GET /v3/client/ns/instance/list` | 查询服务的启用实例列表。会过滤 disabled 实例。 |
| `GET /v3/client/ai/resources/search` | 通过统一 cursor Facade 搜索当前可见的 Agent、AgentSpec、Skill、Prompt 和 MCP 资源。 |
| `GET /v3/client/ai/prompt` | 按版本、标签或 latest 查询 Prompt。 |
| `GET /v3/client/ai/prompt/search` | 使用 numbered pagination 搜索当前可见 Prompt。 |
| `GET /v3/client/ai/skills` | 以 zip 响应下载在线 Skill 包。 |
| `GET /v3/client/ai/skills/search` | 使用 numbered pagination 搜索当前可见 Skill。 |
| `GET /v3/client/ai/agentspecs` | 按版本、标签或 latest 查询 AgentSpec。可能允许匿名访问。 |
| `GET /v3/client/ai/agentspecs/search` | 搜索运行时可用的已启用 AgentSpec。 |
| `GET /v3/client/ai/mcp/search` | 使用协议和能力过滤搜索当前可见 MCP Server。 |

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
- `ai`：MCP、A2A、Agent、Prompt、Skill、AgentSpec 和 Pipeline 管理。

需要更明确文档化的已实现行为：

- Naming service 创建会创建持久化服务元数据。
- Open naming instance 心跳复用 `POST /v3/client/ns/instance` 端点，并在
  需要重新注册时返回 `INSTANCE_NOT_FOUND`。
- Config 查询在返回 Admin API 详情前会解密加密内容。
- Config 发布在未提供 encrypted data key 且适用加密处理器时会加密内容。
- AI Prompt 在同一个 Controller 中同时包含已废弃兼容端点和新的生命周期端点。
- Agent 管理在 `/v3/admin/ai/agents` 下提供定义 CRUD、受限 Agent 与 Version
  读取、Draft 与 Version 生命周期、自定义 Label 以及只读 Runtime Endpoint
  Snapshot；省略或传入空白 `namespaceId` 时统一规范化为 `public`。
- Plugin detail 在已有 `config` 字段中返回当前 effective plugin config，并可以
  追加来源、overridden 等值元数据，不改变已有字段。
- Plugin config 更新保持完整 override map 替换语义。运行时更新必须拒绝
  restart-effective 字段的变化，包括通过省略 key 移除 override。敏感字段脱敏输入只
  保留同一目标 source 的原始值；目标 source 不存在该值时忽略此项，不创建 override。
  source 更新成功但插件 apply 失败时返回明确的服务端错误，且不自动回滚。

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

`GET /v3/console/ai/mcp/importToolsFromMcp` 是由 Console 进程发起出站连接的控制台专用
辅助接口。公网目标默认允许；私网或本地目标必须命中运维通过
`nacos.console.ai.mcp.import.allowed-private-addresses` 配置的 IP/CIDR 白名单。运维可以通过
`nacos.console.ai.mcp.import.enabled` 关闭该能力。

## 7. Auth API 已实现行为

V3 Auth API 位于默认鉴权插件中，而不是 core 模块中：

```text
/v3/auth/user
/v3/auth/role
/v3/auth/permission
/v3/auth/visibility
```

已实现行为：

- 用户管理支持创建、删除、密码更新、登录、列表和搜索。
- 角色管理支持添加、删除、列表和搜索。
- 权限管理支持添加、删除和列表。
- 可见性授权管理支持对显式资源可见性访问执行 grant 和 revoke。
- 第一个管理员初始化由 `POST /v3/auth/user/admin` 实现。

默认鉴权插件随 Nacos 一起发布，因此它的 v3 auth 端点应遵循 Nacos HTTP API 规范和
[鉴权插件规范](../auth/auth-plugin-spec.md)。

## 8. 已批准的 Agent/RAD API 面

下列路径是 [Agent API 规范](../ai/agent-api-spec.md)确定的实验性 API 面。Admin
管理路径已经计入第 3 节的已实现 API 清单和 Controller 统计；Client 传输 Binding 与
Console Facade 在对应 Controller、鉴权、传输 Binding 和测试完成前仍属于目标 API 面。

Client 目标路径：

| Method | Path | 契约 |
| --- | --- | --- |
| GET | `/v3/client/ai/agents/search` | 搜索 Agent 目录。 |
| GET | `/v3/client/ai/agents` | 发现一个 Agent，可附带 Discovery Filter。 |
| POST | `/v3/client/ai/agents/endpoints` | 完整替换当前 Publisher 的 Runtime Endpoint Batch。 |
| DELETE | `/v3/client/ai/agents/endpoints` | 使用 JSON body 标识并移除当前 Publisher 的整份 Runtime Endpoint Publication。 |
| PUT | `/v3/client/ai/agents/endpoints/heartbeat` | 刷新一个 HTTP Publisher Client 的活性。 |

Admin 路径使用已实现的 `/v3/admin/ai/agents` 前缀；Console 目标路径使用
`/v3/console/ai/agents` 前缀，Console 是相同相对管理契约的 UI Facade。

| 相对路径 | Method | 契约 |
| --- | --- | --- |
| *（Base path）* | GET, PUT, DELETE | 读取、更新 Agent metadata 或删除 Agent 定义。 |
| `/list` | GET | 列举 Agent Summary。 |
| `/versions` | GET | 列举 Version Summary。 |
| `/version` | GET | 读取一个精确 Version 定义。 |
| `/runtime-endpoints` | GET | 读取一个完整、不分页的 Runtime Endpoint Snapshot。 |
| `/draft` | POST, PUT, DELETE | 创建新 Draft（Agent 缺失时同时首建 metadata）、更新当前 Draft 内容或删除 Draft。 |
| `/submit` | POST | 提交 Draft。 |
| `/publish` | POST | 发布 Reviewed Version。 |
| `/force-publish` | POST | 经审计地绕过 Pipeline。 |
| `/redraft` | POST | 将 Reviewed Version 退回 Draft。 |
| `/online` | POST | 将 Offline Version 上线。 |
| `/offline` | POST | 将 Online Version 下线。 |
| `/labels` | PUT | 更新自定义 Version Label。 |

目标 API 不增加 Client HTTP Watch 或 Endpoint List GET。Watch 和 Push 使用协商后的
gRPC Binding；Runtime 查看使用 Admin 或 Console 的 `/runtime-endpoints` 路径。

## 9. 已批准的 MCP 生命周期 API 面

下列路径是按 [MCP Server 规范](../ai/mcp-server-spec.md)实现的实验性管理 API 面。只有 MCP
管理权威完成单向切换并达到 `LIFECYCLE_MANAGED` 后才可用；切换前，有效请求返回
`RESOURCE_CONFLICT`，且不会修改历史 MCP 状态。

Admin 使用 `/v3/admin/ai/mcp`；Console 使用 `/v3/console/ai/mcp`，作为相同相对生命周期
契约的 UI Facade：

| 相对路径 | Method | 契约 |
| --- | --- | --- |
| `/versions` | GET | 受限列举 MCP Version metadata 和生命周期状态。 |
| `/version` | GET | 读取一个精确 Version 的内容和 metadata。 |
| `/draft` | POST, PUT, DELETE | 创建 Draft、只更新当前 Draft 或删除 Draft。 |
| `/submit` | POST | 通过普通发布 Pipeline 提交 Draft。 |
| `/publish` | POST | 发布 Reviewed Version。 |
| `/force-publish` | POST | 经审计地执行管理端 Pipeline bypass。 |
| `/redraft` | POST | 将 Reviewed Version 退回 Draft。 |
| `/online` | POST | 将 Offline Version 上线并设为 latest。 |
| `/offline` | POST | 将 Online Version 下线，并在需要时修复 latest。 |
| `/labels` | PUT | 更新自定义 Label，忽略客户端提供的 `latest`。 |

所有路径都使用 Form/Query 参数。通用身份字段是可选的 `namespaceId`（默认 `public`）、
必填 `mcpName`，以及除 `/versions` 和 `/labels` 外必填的精确 `version`。`/versions`
还接受可选 `status` 以及受限的 `pageNo`、`pageSize`。

`POST` 和 `PUT /draft` 还接受必填 JSON `serverSpecification`，以及可选 JSON
`toolSpecification`、`resourceSpecification`、`endpointSpecification`。外层
`mcpName` 和 `version` 是 Canonical Identity；`serverSpecification` 中重复出现的名称或
Version 必须一致，并拒绝 `serverSpecification.id`。`/labels` 接受 JSON String Map；空输入
表示清空自定义 Label，同时保留服务端管理的 Label。

Version 列表返回 `Page<McpServerVersionSummary>`。精确读取和 Draft 写入返回
`McpServerVersionDetail`，其中包含生命周期 Metadata 和 Server/Tools/Resources 内容，但不包含
内部 MCP ID。Detail 还会投影生命周期管理客户端所需的 Resource Status、Owner、Scope、Labels、
Editing/Reviewing 指针和 Online Version 数量。生命周期命令返回转换后的 Summary，删除 Draft 返回空 Success Result，替换 Label
返回最终生效的 Label Map。

现有 MCP create/update/delete 路径和参数形态作为兼容专用的 direct-online Facade 保留，
不能复制为新的生命周期 Form。尤其是同 Version 内容覆盖只允许通过历史更新路由执行。

新的 Lifecycle Form 使用 `namespaceId + mcpName` 定位 Resource，再增加精确
`version` 定位 Version，不增加 `mcpId`。已经接受 `mcpId` 的历史 Admin、Console
和 Maintainer HTTP 输入继续作为已废弃兼容字段；服务端通过 `AiResource.ext` 解析，
校验同时提供的名称，再进入相同的 Name-Based 鉴权和 Lifecycle Service。现有响应 ID
字段保持 Wire-Compatible。

MCP Client HTTP Binding 使用 `/v3/client/ai/mcp`：

| Method | Path | 契约 |
| --- | --- | --- |
| GET | `/v3/client/ai/mcp/search` | 现有 Current MCP Search Facade。 |
| GET | `/v3/client/ai/mcp` | 按 `namespaceId + mcpName (+ version)` 查询 Latest Published 或一个精确 Serving Version。 |
| POST | `/v3/client/ai/mcp` | Form Release；`createDraft` 缺失或为 false 时 Direct-online，为 true 时只创建生命周期 Draft。 |
| POST | `/v3/client/ai/mcp/endpoints` | 使用 IP 字面地址及 `1..65535` 端口注册当前 HTTP Client 的 Runtime Endpoint。 |
| DELETE | `/v3/client/ai/mcp/endpoints` | 使用相同的已校验身份注销当前 HTTP Client 匹配的 Runtime Endpoint。 |
| PUT | `/v3/client/ai/mcp/endpoints/heartbeat` | 刷新共享 AI HTTP Client 及其全部 Publisher。 |

所有写使用 Form/Query Binding。`serverSpecification`、`toolSpecification`、
`resourceSpecification` 和 `endpointSpecification` 都是 JSON String 字段，不使用 JSON Body。
有状态 Endpoint Path 要求稳定的 `X-Nacos-Client-Id` 与 `Request-Module: AI` Header。Query
可以携带 Client Id，但只续约已经存在的 Client。不增加新的顶层 `mcpId` Input。

Embedded 或 Standalone Console 直接委托与 Admin 相同的生命周期 Application Service。
Console-only Remote 部署需要下一 MCP 治理阶段计划的 Typed Maintainer Lifecycle Transport；在该
Transport 落地前，新 Console 生命周期路径在 Remote 模式下返回 `API_FUNCTION_DISABLED`，不会回退到
Legacy 或基于 ID 的写路径。

## 10. 文档 Gap 记录

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
- Auth v3：代码暴露 `/v3/auth/user`、`/role`、`/permission` 和
  `/v3/auth/visibility`；三份网站 API 文档未覆盖这个 API 面。
- Config Open API 异常处理：`ConfigOpenApiController` 没有 `@NacosApi`，
  而大多数 v3 Controller 都有；Open API 文档假设统一响应。
- Config 和 Naming ExceptionHandler：Config 和 Naming 仍有历史模块级
  `ControllerAdvice`，可能返回纯文本错误体。它们应在 v3 API 上收敛到
  `NacosApiExceptionHandler`。

## 11. 废弃兼容说明

部分 v3 AI API 在本规范建立之前已经发布，后续又被更清晰的生命周期 API 或
REST 风格 API 替代。这些旧端点应视为废弃兼容 API：

- AI Prompt legacy 端点，例如 `/detail`、`/label` 和 `/metadata`。
- Pipeline 中不符合当前 `/list` 和 `/detail` 形态的 legacy REST 风格端点。

兼容端点可以在过渡期内继续保留，但面向用户的文档应以新 API 作为主要契约。
废弃端点只应出现在兼容章节中，并按照
[兼容与废弃策略规范](../design/compatibility-deprecation-spec.md)提供迁移说明。

旧 Pipeline base-path list、path-variable detail 端点，以及
`POST /v3/console/ai/mcp/import/{validate|execute}` 默认关闭，返回 HTTP `410 Gone` 和
`API_DEPRECATED`。运维人员可以通过 `nacos.core.api.compatibility.enabled=true` 临时重新开启全部
显式接入门禁的 v3 兼容 API。旧的 `nacos.ai.resource.import.legacy-mcp-api-enabled` 参数不再读取。
