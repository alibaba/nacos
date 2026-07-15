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

# Nacos HTTP API 规范

本文档定义 Nacos HTTP API 的通用设计模型，是 API 设计规则的入口。当前
API 范围、鉴权规则、响应规则等细节维护在关联文档中。

## 1. 设计动机

Nacos 使用 gRPC 作为高频运行时流量的主要客户端通信协议。HTTP API 仍然
存在，是因为它服务于不同的需求：

- 为无法使用官方 SDK 或 gRPC 的客户端提供语言无关的访问方式；
- 为管理员和运维工具提供运维访问能力；
- 为 Nacos 控制台提供 UI 工作流访问能力；
- 为现有 Nacos 用户提供兼容和迁移路径；
- 便于通过常见 HTTP 基础设施进行检查、脚本化和集成。

因此，HTTP API 的设计首先区分调用者受众，再区分资源。客户端 API、运维
API 和控制台 API 可能操作相似的领域对象，但它们的兼容性承诺、权限模型
和响应预期并不相同。

## 2. 设计原则

### 2.1 受众优先

每个 HTTP API 必须先声明它的调用者受众：

| 受众 | 路径前缀 | 预期调用方 |
| --- | --- | --- |
| Open API | `/v3/client` | SDK 和自定义运行时客户端 |
| Admin API | `/v3/admin` | 运维人员和维护工具 |
| Console API | `/v3/console` | [Nacos 控制台 UI](../console/console-spec.md) |
| Auth API | `/v3/auth` | 插件提供的鉴权 API 和初始化流程 |

不能仅因为某个端点可以通过 HTTP 访问，就把它记录为 Open API。Open API
比 Admin API 或 Console API 承担更强的兼容性预期。

### 2.2 稳定的资源路径形态

HTTP 路径在 Nacos Server context path 之后应遵循以下形态：

```text
/v3/{audience}/{module}/{resource}[/{subResource}]
```

当前模块名包括：

| 模块 | 含义 |
| --- | --- |
| `core` | 集群、命名空间、服务端状态、插件和运维操作 |
| `cs` | 配置中心 |
| `ns` | 注册中心 |
| `ai` | MCP、A2A、Prompt、Skill、AgentSpec 和 Pipeline |
| `auth` | 用户、角色和权限 |
| `copilot` | 控制台 Copilot 功能 |

部署 context path 通常为 `/nacos`，它不属于 Controller 映射。面向用户的
示例可以包含它，但代码级路径定义不应包含它。

### 2.3 HTTP Method 语义

V3 HTTP API 按操作语义使用 HTTP Method：

| Method | 含义 |
| --- | --- |
| `GET` | 查询或下载数据。 |
| `POST` | 创建、发布、注册、上传、提交或触发任务。 |
| `PUT` | 更新已有状态，或设置幂等的可变状态。 |
| `DELETE` | 删除、注销，或删除绑定和草稿。 |

任何例外都应先记录到端点级规范中，再被视为有意设计的行为。

### 2.4 一致的响应契约

JSON HTTP API 应返回 `com.alibaba.nacos.api.model.v2.Result<T>`，除非存在
明确的响应形态理由。下载、流式 API 和健康检查是常见例外。

详细响应和错误规则见 [响应与错误规范](response-error-spec.md)。

### 2.5 显式鉴权

HTTP API 应通过 `@Secured` 声明鉴权，除非端点被明确设计为公开端点、初始化
端点或健康检查端点。鉴权声明必须反映 API 受众、资源领域和操作动作。

详细规则见 [鉴权规范](authorization-spec.md)。
共享 HTTP filter 和运行时请求上下文模型由
[请求过滤与运行时上下文规范](../design/foundation-request-context-spec.md)定义。

### 2.6 兼容性是 API 的一部分

Open API 必须作为长期兼容面进行审查。Admin API 和 Console API 可以演进
得更快，但当文档化用户可能依赖它们时，不兼容变更仍需要废弃说明或迁移指引。

已废弃端点应保留在兼容性章节中，而不是在代码仍支持它们时从文档中静默删除。

### 2.7 文档跟随规范

面向用户的文档应由规范生成，或至少根据规范和实现进行人工校验。当代码和
文档不一致时，应先分类再解决：

- `Normative Spec`：Nacos 有意承诺的行为。
- `Current Behavior`：当前已经实现，但尚未确认长期契约的行为。
- `Spec Decision Required`：在规范更新前，不应视为已承诺的行为。

### 2.8 Agent 指南与自动校验

Agent 指南文件、AI skill、Controller 模板和 API 合规校验工具，应把本规范
体系作为规则来源。

这些工具可以保留简短的实现检查清单，方便在本地上下文中使用，但不应定义
与规范冲突的 API 规则。如果 Agent 指南、模板、校验工具、网站文档或实现
与规范不一致，应修正错误的一方，或显式更新规范。

自动校验应将检查结果映射到具体规范规则，包括：

- 受众和路径前缀；
- 模块和资源命名；
- HTTP Method 语义；
- `Result<T>` 响应形态和已记录的例外；
- `@Secured` 声明、action、sign type 和 API type；
- 新增 Controller 方法上的 `@Since` 声明；
- 已废弃兼容端点及其迁移状态。

## 3. 当前 V3 文档

当前 v3 HTTP API 范围记录在 [V3 API 范围](v3-api-surface.md) 中。

更多细节规范：

- [鉴权规范](authorization-spec.md)
- [响应与错误规范](response-error-spec.md)
- [请求过滤与运行时上下文规范](../design/foundation-request-context-spec.md)

## 4. 新增或变更 HTTP API 的规则

1. 先选择受众：Open、Admin、Console 或 Auth。
2. 按稳定路径形态选择模块和资源路径。
3. 按第 2.3 节的语义使用 HTTP Method。
4. 声明鉴权和动作语义。
5. 新增 Controller 方法必须添加 `@Since`，声明该 API 起始支持的 Nacos 版本号。
6. JSON 响应使用 `Result<T>`，除非存在已记录的例外。
7. 在 form 对象或专用 validator 中实现参数校验。
8. 按照 [API 集成测试规范](../testing/api-integration-test-spec.md) 新增或
   更新 API IT，覆盖有意义变更的路由、校验、鉴权、响应形态和场景契约。
9. 在同一个变更中更新对应规范和网站文档。

新增 Open API 需要明确的兼容性说明。新增 Admin API 或 Console API 需要
明确的鉴权说明。新增非 `Result<T>` API 需要明确的响应形态说明。
