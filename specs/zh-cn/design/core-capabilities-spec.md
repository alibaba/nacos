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

# Nacos 核心功能规范

本文定义 Nacos 顶层功能边界。[Nacos 设计规范](nacos-design-spec.md)定义整体设计意图，
[资源模型规范](resource-model-spec.md)定义共享资源身份，[基础能力规范](foundation-capabilities-spec.md)
定义共享基础设施，各领域规范定义具体行为。

## 1. 功能分层

Nacos 功能从产品意图到具体接口按如下层次组织：

```text
设计意图
  -> 资源模型
  -> 基础能力
  -> 领域功能
  -> HTTP / gRPC / SDK 接口
  -> 扩展与安全规则
```

领域功能规范负责定义资源和行为的语义。接口规范负责定义这些语义如何暴露。插件规范负责定义
扩展点，不应重新定义领域归属。基础能力提供
[服务端生命周期与环境](foundation-server-lifecycle-env-spec.md)、
[集群成员](foundation-cluster-membership-spec.md)、
[远程连接](foundation-remote-connection-spec.md)、
[请求过滤与运行时上下文](foundation-request-context-spec.md)、
[内部 RPC](foundation-internal-rpc-spec.md)、
[AP 一致性](foundation-ap-consistency-spec.md)、
[CP 一致性](foundation-cp-consistency-spec.md)、
[持久化与 dump](foundation-persistence-dump-spec.md)、
[任务](foundation-task-execution-spec.md)和
[事件](foundation-event-dispatch-spec.md)，以及
[可观测](foundation-observability-hooks-spec.md)基础设施；它们支撑领域，但不拥有领域资源语义。

## 2. 核心领域

| 领域 | 主要责任 | 资源身份 | 详细规范 |
| --- | --- | --- | --- |
| 配置中心 | 动态配置存储、发布、查询、订阅、灰度分发、历史、容量和审计。 | `namespaceId -> groupName -> dataId` | [Config 规范](../config/config-spec.md) |
| 注册中心 | 服务发现、服务元数据、实例、健康状态、订阅和运行时推送。 | `namespaceId -> groupName -> serviceName` | [Naming 规范](../naming/naming-spec.md) |
| AI Registry | MCP、A2A、Prompt、Skill、AgentSpec、版本、标签、可见性和发布治理。 | `namespaceId -> resourceType -> resourceName` | [AI Registry 规范](../ai/ai-registry-spec.md) |
| Core 运维 | Namespace、[集群成员](foundation-cluster-membership-spec.md)、服务端状态、readiness、liveness、插件状态和运维控制。 | 领域特定的管理资源。 | [Core 运维规范](../core/core-operations-spec.md) |
| Console | Web UI、Console API 后端、部署桥接，以及面向领域资源的 UI 工作流适配。 | 领域资源之上的 UI 工作流。 | [Console 规范](../console/console-spec.md) |
| 分布式锁 | 基于 CP 状态的实验性短临界区互斥能力。 | `lockType -> key` | [分布式锁规范](../lock/lock-spec.md) |
| 安全与可见性 | 认证、鉴权、权限、API 分类、资源可见性和身份传播。 | 结构化 Nacos 资源身份。 | [鉴权与权限规范](../auth/auth-permission-spec.md)，[可见性插件规范](../auth/visibility-plugin-spec.md) |
| 扩展 | 服务端和客户端扩展点，包括鉴权、可见性、数据源、加密、Trace、Control、寻址、AI Pipeline 等。 | 插件类型身份加领域拥有的资源身份。 | [插件规范](../plugin/plugin-spec.md) |

## 3. 跨领域规则

- 领域拥有自身资源语义、生命周期、校验规则和可观测状态。
- `core`、`common`、`persistence`、`consistency`、`auth` 和 `plugin` 模块提供共享基础设施；
  共享基础设施规则见[基础能力规范](foundation-capabilities-spec.md)，其中包含
  [服务端生命周期与环境](foundation-server-lifecycle-env-spec.md)、
  [集群成员](foundation-cluster-membership-spec.md)、
  [远程连接生命周期](foundation-remote-connection-spec.md)、
  [请求过滤与运行时上下文](foundation-request-context-spec.md)、
  [内部 RPC](foundation-internal-rpc-spec.md)、
  [AP 一致性](foundation-ap-consistency-spec.md)、
  [CP 一致性](foundation-cp-consistency-spec.md)和
  [持久化与 dump](foundation-persistence-dump-spec.md)、
  [任务执行](foundation-task-execution-spec.md)和
  [事件分发](foundation-event-dispatch-spec.md)，以及
  [可观测钩子](foundation-observability-hooks-spec.md)。除非领域规范明确委托，否则它们不拥有 Config、Naming
  或 AI 资源语义。
- 运行时客户端接口应只暴露面向已知资源的最小必要能力。大范围列表、导出、克隆、迁移、容量和
  运维 API 属于 Admin API、Console API 或 Maintainer SDK。
- 所有领域 API 必须保持共享资源模型，并遵循
  [HTTP API 规范](../http-api/api-spec.md)、[gRPC API 规范](../grpc-api/api-spec.md)和
  [SDK 规范](../sdk/sdk-spec.md)。
- 鉴权、可见性、加密、数据源方言、Trace 和 Control 等横切行为应通过对应规范实现，而不是在
  每个领域中重复定义规则。

## 4. 功能边界检查项

每个新功能都应明确：

- 归属领域和模块；
- 资源身份，以及第二层是 `groupName` 还是 `resourceType`；
- 面向运行时、管理面还是运维面；
- HTTP、gRPC、Client SDK、Maintainer SDK、Console 或插件接口；
- 持久化、缓存、事件、一致性和恢复预期；
- 鉴权、可见性、审计、Trace 和 Control 要求；
- 对已有 API、SDK、存储和插件的兼容性影响。
