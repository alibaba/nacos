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

# Nacos 设计规范

本文档定义 Nacos 的顶层设计意图。[资源模型规范](./resource-model-spec.md)细化资源身份，
[HTTP API 规范](../http-api/api-spec.md)、[gRPC API 规范](../grpc-api/api-spec.md) 和
[SDK 规范](../sdk/sdk-spec.md)细化外部接口规则。

## 1. 定位

Nacos 是面向云原生和 AI 原生应用的动态服务发现、配置管理、服务管理和
AI Agent 管理平台。

Nacos 是以服务为中心的应用架构基础设施。这里的服务可以是微服务、RPC 服务、
DNS 可发现端点、网关后端、MCP Server、A2A Agent、Prompt、Skill、AgentSpec，
也可以是其他需要发现、动态配置、生命周期管理、治理和安全分发的运行时资源。

## 2. 设计目标

Nacos 应做到：

- 让运行时资源易于注册、发现、配置、治理和观测；
- 让应用无需重新部署即可改变配置、路由、发现结果和 AI 资源选择；
- 为配置、注册中心、服务元数据和 AI Registry 资源提供统一控制面；
- 通过 namespace 和权限边界支持多租户、多环境隔离；
- 在大规模集群和高并发工作负载下保持生产级可用性；
- 通过标准协议、SDK、API 和插件扩展点保持开放生态。

## 3. 设计原则

### 3.1 易于使用

Nacos 应为常见运行时和管理任务提供简单 API、SDK、命令行工具和控制台流程。
用户不应为了正确使用 Nacos 而理解内部存储、一致性协议或传输细节。

### 3.2 面向标准

Nacos 应尽量对齐广泛使用的标准和生态协议，包括云原生服务发现、HTTP API、
gRPC 传输、MCP、A2A，以及 Kubernetes、Spring、Dubbo、Spring AI 等集成模式。

当 Nacos 增加协议适配层时，Nacos 资源模型仍然是内部语义来源，协议模型只是
该资源模型上的适配。

### 3.3 运行时动态

Nacos 资源预期会在运行时发生变化。配置内容、服务实例、端点、AI 资源、标签、
可见性和元数据都可以在不重新部署应用的情况下变化。

面向运行时的 API 和 SDK 应在领域需要时支持订阅、推送、本地缓存和安全回退。

### 3.4 高可用

Nacos 应支持面向本地开发和测试的单机模式，也应支持面向生产环境的集群模式。
集群模式应根据不同领域的资源语义，通过合适的一致性和复制机制提供可用性与
水平扩展能力。

### 3.5 方便扩展

Nacos 应保持清晰模块边界，并为鉴权、可见性、数据源、加密、链路追踪、控制、
环境和 AI 发布流水线等横切能力提供插件扩展点。

扩展不应重新定义核心资源身份、API 响应语义或鉴权边界。

### 3.6 安全可治理

Nacos 管理敏感的运行时资产和 AI 资产。安全是设计的一部分，而不是可选附加项：

- 受保护 API 应显式启用鉴权和授权；
- AI 资源应支持 owner、可见性、版本治理、审核、分发和审计追溯；
- 大范围读取和管理 API 应属于 admin 或 maintainer 能力面；
- 运行时 client 能力面应只暴露最小权限能力。

## 4. 领域职责

Nacos 领域应围绕统一资源层次组织：

```text
NamespaceId -> Group/resourceType -> resourceName
```

其中 `NamespaceId` 是隔离边界；`Group/resourceType` 是第二层分类，微服务应用资源
使用 `Group`，AI Registry 资源使用 `resourceType`；`resourceName` 是领域内具体
资源名称。领域规范可以继续定义 version、label、status、visibility 等治理属性，
但不应打破这个顶层层次。

### 4.1 配置领域

配置领域管理由 namespace、group 和 dataId 标识的动态配置资源。它负责配置内容、
type、md5、元数据、监听、模糊订阅、灰度/beta 发布、历史、回滚、dump 和
failover 相关行为。详细规则由 [Config 规范](../config/config-spec.md)定义。

### 4.2 注册中心领域

注册中心领域管理由 namespace、group 和 service name 标识的服务发现资源。它负责
服务元数据、实例、集群、健康状态、临时服务和持久服务语义、订阅者、客户端视图和
服务变化推送。详细规则由 [Naming 规范](../naming/naming-spec.md)定义。

### 4.3 AI Registry 领域

[AI Registry 领域](../ai/ai-registry-spec.md)管理 MCP Server、A2A AgentCard、
Prompt、Skill 和 AgentSpec 等 AI 资源。AI 资源使用
`NamespaceId -> resourceType -> resourceName` 作为顶层身份。
它负责 AI 资源元数据、版本、标签、可见性、端点、工具或 Skill 描述、发布流水线
状态、下载分发和面向审计的追踪信息。

AI Registry 不是 Nacos 内部的独立产品模型，而是 Nacos 的一等领域，使用同一套
namespace、API、SDK、鉴权、插件和资源治理原则。

### 4.4 Core 和运维领域

[Core 运维领域](../core/core-operations-spec.md)负责 namespace 管理、
[集群成员](foundation-cluster-membership-spec.md)、服务端状态、
readiness/liveness、[服务端生命周期与环境](foundation-server-lifecycle-env-spec.md)、
[连接管理](foundation-remote-connection-spec.md)、
[请求过滤与运行时上下文](foundation-request-context-spec.md)、
[内部 RPC](foundation-internal-rpc-spec.md)、日志级别操作、插件状态和其他服务端控制面资源。

这些能力天然属于管理能力，应通过 Admin API、Console API 或 Maintainer SDK 暴露，
而不是通过运行时 Client SDK 暴露。

### 4.5 安全和可见性领域

安全领域负责认证、授权、身份传递、API 分类、动作分类和可见性校验。它应一致
作用于 HTTP API、gRPC 调用、SDK、控制台行为以及插件提供的 API。

## 5. 接口架构

Nacos 通过多类接口暴露同一套资源语义：

- HTTP API，用于 Open、Admin、Console、Auth 和插件 API；
- gRPC API，用于高频运行时通信、推送、订阅和客户端-服务端控制消息；
- Client SDK，用于运行时应用和 Agent Framework；
- Maintainer SDK，用于管理、UI、网关和运维接入；
- 控制台和 CLI，用于人和自动化流程。

不同接口可以使用不同传输模型，但必须保持一致的资源身份、校验、鉴权、生命周期
和错误语义。

## 6. 模块架构

Nacos 模块应遵循以下职责边界：

- `api`、`client` 和 `client-basic` 定义公开客户端模型、SDK interface 和传输契约。
- `config`、`naming` 和 `ai` 负责各自领域资源行为。
- `core` 负责集群、namespace、服务端、插件和运维基础能力。
- `common`、`consistency` 和 `persistence` 提供事件、任务、AP/CP 协议和存储等共享基础能力。
  Core member 和连接边界进一步由[集群成员规范](foundation-cluster-membership-spec.md)和
  [远程连接生命周期规范](foundation-remote-connection-spec.md)定义，服务端生命周期和请求上下文边界由
  [服务端生命周期与环境配置规范](foundation-server-lifecycle-env-spec.md)和
  [请求过滤与运行时上下文规范](foundation-request-context-spec.md)定义，服务端间请求边界由
  [内部 RPC 与集群请求规范](foundation-internal-rpc-spec.md)定义，AP/CP 一致性边界由
  [AP 一致性规范](foundation-ap-consistency-spec.md)和
  [CP 一致性规范](foundation-cp-consistency-spec.md)定义，持久化与 dump 边界由
  [持久化与 Dump 规范](foundation-persistence-dump-spec.md)定义，任务和事件边界由
  [任务执行规范](foundation-task-execution-spec.md)和
  [事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)定义，可观测边界由
  [可观测钩子规范](foundation-observability-hooks-spec.md)定义，其他基础能力边界由
  [基础能力规范](foundation-capabilities-spec.md)定义。
- `auth` 和 plugin 模块负责可扩展的安全与策略行为。
- `maintainer-client` 基于 Admin API 语义暴露类型化 Java 管理入口。
- `console` 暴露面向 UI 的后端 API，不应独立重新定义领域语义。

共享模型应放在 API 兼容要求清晰的位置。服务端内部实现细节不应泄露到公开 SDK
契约中。

## 7. 一致性和存储

每个领域应根据资源语义选择一致性和存储行为：

- 配置资源需要持久存储、版本/历史感知和可靠变更通知；
- 注册中心资源需要快速运行时更新、健康状态驱动的可用性以及清晰的临时/持久化语义；
- AI 资源需要持久元数据、适用场景下不可变的已发布版本、基于标签的路由、可见性
  和审核/审计元数据；
- 服务端和集群资源需要显式管理控制和运维安全。

实现可以使用数据库持久化、本地缓存、Distro、Raft 或其他机制，但公开语义必须
通过领域规范表达，而不是通过存储实现细节表达。[服务端生命周期](foundation-server-lifecycle-env-spec.md)、
[member](foundation-cluster-membership-spec.md)、[连接生命周期](foundation-remote-connection-spec.md)、
[请求过滤](foundation-request-context-spec.md)、[内部 RPC](foundation-internal-rpc-spec.md)、
[AP 一致性](foundation-ap-consistency-spec.md)、
[CP 一致性](foundation-cp-consistency-spec.md)、
[持久化与 dump](foundation-persistence-dump-spec.md)、
[任务执行](foundation-task-execution-spec.md)和
[事件分发](foundation-event-dispatch-spec.md)，以及
[可观测钩子](foundation-observability-hooks-spec.md)的通用基础要求由
[基础能力规范](foundation-capabilities-spec.md)及其子规范定义。

## 8. 新功能设计规则

每个 Nacos 新功能都应定义：

- 功能所属领域；
- 资源类型和资源身份；
- 功能面向运行时、管理侧，还是两者都面向；
- API 受众：Open、Admin、Console、Auth、plugin、gRPC、Client SDK 或 Maintainer SDK；
- namespace、group、version、label、status 和 visibility 行为；
- 认证、授权和审计要求；
- 对已有 API 和 SDK 的兼容及废弃影响；
- 能让规范可执行的测试或校验规则。

如果一个功能无法回答这些问题，它还不应成为稳定的 Nacos 契约。
