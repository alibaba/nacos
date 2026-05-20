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

# Console 规范

本文定义 Nacos Console 领域。Console 是面向运维人员的管理体验层，包含 Web UI、Console API
后端，以及让 UI 能够在和 Nacos Server 合并部署或独立部署时工作的部署桥接能力。

Console 不拥有 Config、Naming、AI Registry、Core 运维、Auth 或插件领域数据。它负责把这些
领域能力适配成 UI 工作流，并必须保持各领域规范定义的语义。

## 1. 范围

Console 负责：

- Web UI 入口、静态资源、默认 UI 选择、console guide 和 announcement 展示；
- UI 使用的 `/v3/console/*` HTTP API；
- Console 请求过滤、参数校验、CORS、异常映射和 Console 鉴权配置；
- 将 UI 工作流映射到合并部署下本地域服务，或独立 Console 部署下远端 Server API 的
  handler/proxy 层；
- 独立 Console 部署配置和远端 Server member 解析；
- Config、Naming、AI 等 Console 模块的功能开关。

Console 不负责：

- Config 数据生命周期、历史、灰度发布、监听状态和 dump 规则，这些由
  [Config 规范](../config/config-spec.md)定义；
- Naming 服务、实例、元数据、健康检查、订阅和一致性语义，这些由
  [Naming 规范](../naming/naming-spec.md)定义；
- AI 资源模型和生命周期，这些由 [AI Registry 规范](../ai/ai-registry-spec.md)定义；
- namespace、集群 member、服务端状态、插件状态或 server loader 语义，这些由
  [Core 运维规范](../core/core-operations-spec.md)定义；
- 鉴权插件行为和 RBAC 语义，这些由[鉴权与权限规范](../auth/auth-permission-spec.md)定义；
- 插件扩展契约，这些由[插件规范](../plugin/plugin-spec.md)定义。

## 2. 部署模型

Nacos 3.x 将 Console 网络入口和 Server HTTP API 网络入口拆分。部署模型由
`nacos.deployment.type` 控制：

| 类型 | 含义 | 预期场景 |
| --- | --- | --- |
| `merged` | 默认模式。Core、Server Web 和 Console 在同一进程中运行。 | 本地体验、简单部署或兼容场景。 |
| `server` | Server 不带 Console 运行。 | 生产 Server 集群，尤其是 Console 独立部署时。 |
| `console` | Console 不带本地 Nacos Server 领域服务运行。 | 独立 UI/backend 部署，用于隔离管理面。 |

规则：

- `merged` 必须启动 core context、server web context 和 console context；
- `server` 必须启动服务端上下文，不得暴露 Console UI 或 Console API；
- `console` 只启动 console context，领域数据必须来自远端 Nacos Server 节点；
- 不支持的 deployment type 必须在 bootstrap 阶段快速失败；
- Console 部署必须被视为内部网络组件。Nacos 定位为 IDC/内部基础设施组件，不应直接暴露在公网。

## 3. 端口与 Context Path 模型

Nacos 3.x 为服务 API 和 Console 使用独立网络端口：

| 端口 | 用途 |
| --- | --- |
| 默认 `8848` | Nacos HTTP Open/Admin API 端口。 |
| 默认 `9848` | 客户端 gRPC 端口。 |
| 默认 `9849` | 服务端之间的 gRPC 端口。 |
| 默认 `7848` | JRaft 服务端端口。 |
| 默认 `8080` | Nacos Console UI 和 Console API 端口。 |

规则：

- Console 端口由 `nacos.console.port` 独立配置；
- Console context path 由 `nacos.console.contextPath` 配置；
- console-only 部署访问远端 Nacos Server 的 context path 由
  `nacos.console.remote.server.context-path` 配置，默认 `/nacos`；
- Server HTTP API context path 不属于 Controller 映射，其规则见
  [HTTP API 规范](../http-api/api-spec.md)；
- 对外暴露应保持最小化。典型部署中只应向预期的内部调用方暴露 Console 端口和客户端 gRPC
  端口，服务端之间的端口应保持私有。

## 4. Console API 受众

Console API 是 UI 后端 API，不是 Open API，也不应作为推荐自动化接口展示。自动化客户端应使用
Admin API 或 Maintainer SDK，除非某能力被明确设计为仅控制台可用。

规则：

- Console API 必须使用 `/v3/console/{module}/...` 受众前缀；
- 需要鉴权的 Console API 必须声明 `ApiType.CONSOLE_API`；
- Console API 可以使用面向 UI 的请求和响应模型，但 JSON 响应仍应遵循共享 `Result<T>`
  规则，除非[响应与错误规范](../http-api/response-error-spec.md)定义了例外；
- Console API 可以比 Open API 演进得更快，但文档化行为发生不兼容变更时仍需要迁移说明；
- Console API 行为不得重新定义 Config、Naming、AI Registry、Core 运维、Auth 或插件规范
  已拥有的领域语义。

当前 v3 Console API 范围由 [V3 API 范围](../http-api/v3-api-surface.md)描述。

## 5. UI 入口与静态资源

Console 负责浏览器入口和静态资源服务行为：

- `/` 跳转到默认 UI 版本；
- `nacos.console.ui.default` 选择 `next` 或 `legacy`，默认 `next`；
- `nacos.console.ui.enabled` 控制是否开启开源 Console UI；
- `announcement` 和 `console-guide` 内容在存在配置文件时作为展示内容读取；
- 静态资源路径和浏览器资源可以排除鉴权，但该排除范围不得包含领域修改 API。

Console guide 和 announcement 内容属于 UI 展示数据，不是标准 Core 服务端状态，也不得作为领域配置使用。

## 6. Handler 与 Proxy 边界

Console Controller 必须通过 proxy 和 handler interface 进行委托，不应把 UI Controller 直接耦合到
某一种部署模式。

当前层次为：

```text
Console Controller
  -> Console Proxy
  -> Console Handler interface
     -> Inner Handler  (merged 部署)
     -> Remote Handler (console 部署)
     -> Noop Handler   (功能禁用)
```

规则：

- controller 代码负责 HTTP 形态、校验入口、UI 请求适配和 `@Secured` 声明；
- proxy 代码负责 UI 工作流编排，并委托给 handler interface；
- inner handler 可以调用本地域服务，因为 `merged` 模式下 Console 和 Nacos Server 共享进程；
- remote handler 必须通过 Maintainer SDK、Admin API 或严格限定的远程 HTTP 转发调用远端
  Nacos Server；
- 功能禁用时应使用 noop handler，让 UI 获得清晰的 unsupported 响应，而不是加载一部分不完整
  的领域实现；
- 即使传输路径不同，handler 实现也必须在不同部署模式下返回相同领域语义。

## 7. 独立 Console 部署

在 `console` 部署模式下，Console 是访问一个或多个远端 Nacos Server 节点的管理面网关。

规则：

- 必须先部署不带 Console 的 Server 或 Server 集群；
- Console 必须通过标准 member lookup 机制发现远端 Server member，通常使用 `cluster.conf`
  中的 `ip:port` 记录；
- 远端 Server member 列表变化时，Console 必须重建远端 maintainer client；
- Console 不得在本地持久化 Config、Naming、AI 或 Core 领域数据；
- 远端请求必须使用已配置的 remote server context path；
- 远端操作应优先使用 Maintainer SDK 或 Admin API 契约，而不是依赖私有服务端内部实现；
- 文件导入导出等大 payload 工作流必须保持和对应 UI 工作流一致的鉴权和大小限制。

远端 member lookup 是 Console 进程自己的运维视图，它本身不会改变 Nacos Server 集群成员关系。

## 8. 安全边界

Console 存在两个安全方向：

1. 浏览器或运维人员访问 Console API；
2. 独立 Console 进程访问 Nacos Server。

规则：

- 浏览器和运维人员流量必须由 Console 鉴权配置控制，尤其是
  `nacos.core.auth.console.enabled`；
- 修改类 Console API 必须要求对应领域资源或 Console 资源的写权限；
- 只读 Console API 也必须声明读权限，除非它们被明确设计为公开健康检查、静态资源、初始化或
  展示端点；
- 进入 Console 的浏览器请求不得被当作 server identity 请求信任；
- 独立 Console 到 Server 的调用在启用 server identity 时必须携带配置的 server identity；
- `nacos.core.auth.server.identity.key` 和 `nacos.core.auth.server.identity.value` 必须在独立
  Console 和目标 Nacos Server 部署之间保持一致；
- Console 登录和 token 校验所需的 auth plugin token secret 必须与所选鉴权插件行为保持一致。

Console 鉴权属于共享鉴权模型，必须遵循[鉴权规范](../http-api/authorization-spec.md)。

## 9. 功能开关

Console 功能可用性必须遵循 Nacos runtime capability 和 function mode 配置：

- Config console handler 只在 Config 启用时加载；
- Naming console handler 只在 Naming 启用时加载；
- AI console handler 需要 AI function mode 和 AI extension 启用；
- microservice function mode 会开启 Config 和 Naming Console 工作流；
- 禁用功能应通过 noop handler 或隐藏 UI 入口表达，不应加载不兼容的半套领域服务。

功能开关是展示和可用性控制，不得重新定义 Config、Naming 或 AI Registry 的领域模型。

## 10. 错误处理与可观测性

Console 应让 UI 用户能读懂错误，同时保持共享 API 契约：

- v3 JSON Console API 应尽可能使用 `Result<T>` 和共享 API 异常模型；
- 健康检查、静态资源和展示端点可以在明确记录时使用更简单的响应形态；
- 返回给浏览器的错误信息如果可能包含用户可控内容，必须进行转义或清理；
- Console module state 应暴露低基数运维状态，例如 UI 是否开启、默认 UI 版本和 Console
  auth 状态；
- Console 指标和日志不得包含密钥、token、完整凭据或大体积用户载荷。

## 11. 待处理问题

- 明确并文档化哪些 v3 Console health、server state、announcement 和 guide 端点是有意公开的。
- 将遗留 `ConsoleExceptionHandler` 行为与共享 v3 `NacosApiExceptionHandler` 和响应错误规则对齐。
- 判断独立 Console 的远程转发是否应在导入导出等大 payload 路径上完全替换为 Maintainer SDK
  或 Admin API 调用。
- 在 `console` 部署无法解析远端 Server member 时，提供清晰失败信息。
- 当前默认 CORS 策略偏向易部署，需要定义更严格的生产配置建议。
- 判断 `legacy` UI 静态资源和旧 Console 路径的长期兼容边界。
