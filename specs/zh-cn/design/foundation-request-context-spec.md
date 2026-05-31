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

# Nacos 请求过滤与运行时上下文规范

本文定义 Nacos HTTP servlet filter、gRPC request filter、请求级运行时上下文、参数提取、
namespace 校验、鉴权和流量控制钩子的基础规则。

本文与 [HTTP API 规范](../http-api/api-spec.md)、[gRPC API 规范](../grpc-api/api-spec.md)、
[鉴权与权限规范](../auth/auth-permission-spec.md)、[Control 插件规范](../plugin/control-plugin-spec.md)
和[远程连接生命周期规范](foundation-remote-connection-spec.md)配合使用。

## 1. 定位

请求过滤是 Nacos HTTP 和 gRPC 请求进入 handler 之前的执行层。它可以补充运行时上下文、拒绝非法
请求、执行横切检查，或把请求元数据适配给后续 handler。

请求过滤不拥有领域资源语义。Config、Naming、AI、Core 和 Auth 领域仍然在各自规范中定义资源
身份、生命周期、鉴权含义和操作结果。

## 2. 运行时请求上下文

Nacos 使用 `RequestContextHolder` 和 `RequestContext` 作为进程内请求上下文模型。

上下文规则：

- `RequestContextHolder` 基于 `ThreadLocal`。当工作线程会被复用时，请求入口必须在处理结束后
  清理上下文。
- `RequestContext` 包含 request id、request timestamp、`BasicContext`、`EngineContext`、
  `AuthContext` 和具名扩展上下文。
- `BasicContext` 记录协议、请求目标、编码、app、user agent 和远端/source 地址信息。
- 当鉴权过滤器执行后，`AuthContext` 记录 API 类型、解析出的 identity、resource 和鉴权结果。
- 扩展上下文可以增加运行时元数据，但不得重新定义标准字段，也不得保存持久领域状态。
- 上下文仅属于运行时。它不是持久化数据，不是集群复制 payload，也不会自动传播到异步任务；如果
  组件需要跨线程使用，必须显式复制必要字段。

HTTP 请求由 `HttpRequestContextFilter` 初始化，它以最早的 servlet filter 顺序执行。该 filter
将协议设置为 HTTP，用 HTTP method 和 URI 作为请求目标，记录编码和客户端 header，并在 `finally`
中清理上下文。

gRPC unary 请求由 `GrpcRequestAcceptor` 在连接校验和 payload 解析之后初始化。它使用 `Request`
中的 request id，将协议设置为 gRPC，以请求类名作为请求目标，把客户端版本记录为 user agent，
解析 app 元数据，并从已注册连接中记录远端/source 地址。

## 3. HTTP 过滤模型

HTTP filter 是由 Nacos web 配置和领域模块注册的 servlet filter。

核心 HTTP filter 职责：

- `FormSizeFilter` 在正常 controller 处理之前拒绝过大的 form 请求。
- `HttpRequestContextFilter` 初始化并清理 `RequestContext`。
- `AuthFilter`、`AuthAdminFilter` 和控制台鉴权 filter 处理 `@Secured` API，并在执行鉴权时写入
  `AuthContext`。
- `NacosHttpTpsFilter` 对 HTTP v1/v2 Config 和 Naming 路径，通过 Control 插件 manager 检查
  `@TpsControl` 点位。
- `ParamCheckerFilter` 通过 `ExtractorManager` 提取结构化参数，并使用当前激活的 `ParamChecker`
  进行校验。
- 领域 filter 可以适配 legacy 请求参数、流量元数据或模块级兼容行为，但新 API 不得绕过公共
  response、鉴权或校验规则。

filter 顺序规则：

- 请求上下文初始化必须早于需要 request、auth、trace 或 control 元数据的 filter。
- 大小、鉴权、流量控制和参数校验 filter 可以在 controller 执行前拒绝请求。
- 拒绝 HTTP 请求的 filter 必须在目标 API 家族期望包裹响应时返回标准 Nacos result 格式。
- 当 filter 拥有拒绝逻辑时，filter 异常应转换为统一异常或 result 模型。未预期的基础设施失败
  可以抛出给全局异常处理。

## 4. gRPC 请求过滤模型

gRPC 业务请求由 `GrpcRequestAcceptor` 接收，解析为 `Request` 对象，匹配到 `RequestHandler`，
然后在 handler 的 `handle` 方法执行前经过已注册的 `AbstractRequestFilter`。

gRPC filter 规则：

- `AbstractRequestFilter` 在初始化阶段注册到 `RequestFilters`。
- filter 在 `RequestHandler.handleRequest` 中串行执行。
- filter 返回 `null` 表示继续。返回非成功 response 表示中止链路并返回给调用方。
- filter 异常会由 request handler 记录日志，但异常本身不会中止 handler 链路。
- 拒绝请求的 filter 应创建 handler 声明的 response 类型，并设置合适的错误码和错误信息。
- `RemoteRequestAuthFilter` 执行 `@Secured`、服务端身份、identity 有效性和权限校验，并写入
  `AuthContext`。
- `RemoteParamCheckFilter` 使用 `ExtractorManager` 和当前激活的 `ParamChecker` 校验请求参数。
- `TpsControlRequestFilter` 通过 Control 插件 manager 检查 `@TpsControl` 点位，并在被限制时返回
  `OVER_THRESHOLD`。
- `NamespaceValidationRequestFilter` 在 handler 通过 `@NamespaceValidation` 显式开启时校验
  namespace 是否存在。

gRPC acceptor 会在进入 handler filter 链之前拒绝启动中服务、未知请求类型、非法连接、非法
payload 和非 `Request` payload。

## 5. 参数提取与校验

`ExtractorManager.Extractor` 是 controller method 或 request handler 映射到 HTTP/RPC 参数
提取器的公共注解。

参数提取规则：

- extractor 生成供共享 validator 使用的 `ParamInfo`，不应修改领域状态或执行持久写入。
- 注解可以声明在方法或所属类上。方法注解优先。
- HTTP extractor 读取 servlet request。RPC extractor 读取 `Request` 对象。
- extractor 通过 Nacos SPI 加载，并且对同一请求输入应保持确定性。
- 参数校验由服务端参数校验配置和当前激活的 `ParamChecker` 控制。
- 领域级校验仍属于 form、request object、service 或领域 handler。参数 filter 只执行公共结构
  规则。

## 6. Namespace 校验

Namespace 校验是显式 opt-in 的横切保护。

Namespace 校验规则：

- Namespace 校验必须同时受全局 namespace validation 开关和 handler 级 `@NamespaceValidation`
  注解控制。
- 空 namespace 值按照领域默认值处理，filter 不把它当作缺失 namespace 进行校验。
- 非空 namespace id 在请求继续之前必须已存在于 namespace operation service。
- 校验失败必须使用当前传输协议的标准错误码和 response 模型。
- Namespace 校验不得创建 namespace、推断 tenant 归属，也不得覆盖领域鉴权规则。

## 7. 横切边界

- 鉴权 filter 执行身份和权限判断，但鉴权 resource 语义仍由
  [鉴权与权限规范](../auth/auth-permission-spec.md)定义。
- Control filter 执行流量治理，但 control point 定义和插件行为仍由
  [Control 插件规范](../plugin/control-plugin-spec.md)定义。
- 请求上下文可以为 metrics 和 trace 提供字段，但可观测行为仍由
  [可观测钩子规范](foundation-observability-hooks-spec.md)定义。
- 远程连接元数据来自[远程连接生命周期规范](foundation-remote-connection-spec.md)。
- 除非 API 契约显式要求某个 filter，领域 handler 不应假设 filter 已执行领域特有校验。
- 新 API 应优先复用共享 filter 和注解，而不是在 controller 中重复实现等价的鉴权、参数、
  namespace 或 control 逻辑。

## 8. 待处理问题

- 部分模块级 legacy filter 和 controller 仍混合了兼容适配、校验或业务行为。新的 v3 API 应避免
  将这些行为纳入正式 API 契约，并逐步把公共检查迁移到共享 filter 或领域 service。
- gRPC 连接心跳和假死检测目前隐藏在 Naming 等领域之下。详细传输心跳语义后续应在远程连接或
  gRPC 客户端规范中展开，而不是在领域规范中重复定义。

## 9. 相关规范

- [基础能力规范](foundation-capabilities-spec.md)
- [服务端生命周期与环境配置规范](foundation-server-lifecycle-env-spec.md)
- [远程连接生命周期规范](foundation-remote-connection-spec.md)
- [内部 RPC 与集群请求规范](foundation-internal-rpc-spec.md)
- [HTTP API 规范](../http-api/api-spec.md)
- [gRPC API 规范](../grpc-api/api-spec.md)
- [响应与错误规范](../http-api/response-error-spec.md)
- [鉴权与权限规范](../auth/auth-permission-spec.md)
- [Control 插件规范](../plugin/control-plugin-spec.md)
- [可观测钩子规范](foundation-observability-hooks-spec.md)
