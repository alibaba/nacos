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

# Config 规范

本文定义 Nacos Config 领域的顶层规范。它基于
[核心功能规范](../design/core-capabilities-spec.md)和
[资源模型规范](../design/resource-model-spec.md)，进一步定义动态配置能力。

## 1. 定位

Nacos Config 是动态配置领域。它以持久化资源的形式存储配置内容，并提供发布、查询、订阅分发、
灰度发布、删除、历史、导入、导出、克隆、容量和运维诊断等生命周期能力。

Config 是 Nacos 的一级领域。它不是通用文档库、对象存储、密钥管理系统、服务发现模型或 AI
资源模型。

## 2. 资源身份

Config 使用微服务资源层次：

```text
namespaceId -> groupName -> dataId
```

具体身份规则由 [Config 资源规范](config-resource-spec.md)定义。

## 3. 责任范围

| 责任 | 含义 | 详细规范 |
| --- | --- | --- |
| 资源模型 | 定义 Config 身份、元数据、内容、md5、类型、标签和校验规则。 | [Config 资源规范](config-resource-spec.md) |
| 发布与查询 | 定义创建、更新、CAS、删除、查询、列表、导入、导出、克隆和查询链行为。 | [Config 发布与查询规范](config-publish-query-spec.md) |
| 监听与订阅 | 定义精确配置监听、变更推送、模糊订阅和客户端同步语义。 | [Config 监听与订阅规范](config-listener-watch-spec.md) |
| 灰度发布 | 定义正式配置、灰度配置、beta、tag、规则匹配和灰度查询优先级。 | [Config 灰度发布规范](config-gray-release-spec.md) |
| 持久化与历史 | 定义持久化存储、本地 dump 缓存、md5 状态、历史、恢复和清理预期。 | [Config 持久化、Dump 与历史规范](config-persistence-history-spec.md) |
| 一致性与可见性 | 定义写入可见性、dump 顺序、集群传播和运行时查询可见性。 | [Config 一致性、Dump 与可见性规范](config-consistency-dump-visibility-spec.md) |
| 容量与运维 | 定义配额、大小限制、用量统计、指标、监听诊断、本地缓存操作和 Derby 运维边界。 | [Config 容量与运维规范](config-capacity-ops-spec.md) |

## 4. 设计原则

### 4.1 配置内容是黑盒

Config 将 `content` 作为黑盒整体处理。Nacos 负责配置资源的生命周期，包括发布、查询、订阅
分发、灰度发布、删除、历史和相关管理操作。Nacos 不应解析、合并、局部更新或围绕配置文件内部
的某个业务配置项定义行为。

`type` 字段只描述内容类型，用于展示和响应处理；它不表示 Nacos 拥有配置内容内部的业务 schema。
如果部署场景必须感知具体内部配置项，进行校验、转换或触发副作用，应通过扩展或下游系统自行
处理。社区不应定义或开发要求 Nacos 理解特定内部配置项的 Config 核心能力。

### 4.2 持久化为源，运行时走缓存

Config 内容必须持久化保存。运行时读取通过 Config 缓存和本地 dump 文件提供，避免高频客户端
查询和变更检查依赖大范围数据库查询。

持久化层是可靠的数据源。本地 dump 缓存是服务端查询和恢复层，必须在启动阶段和变更事件后从
持久化数据刷新。本地变更事件语义由
[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)定义；后台 dump
和刷新执行由[任务执行规范](../design/foundation-task-execution-spec.md)定义。

### 4.3 以 md5 表达内容版本

Config 使用 `md5` 作为内容版本标识，用于客户端变更检测和 CAS 发布。监听时比较客户端持有的
md5 和服务端状态；CAS 发布时比较请求携带的 md5 和已存储 md5，匹配后才允许更新。

### 4.4 变更推送只是提示

Config 变更推送只通知客户端某个资源可能发生变化。推送内容不能视为权威配置内容。客户端收到
变更通知后必须再次查询对应 Config 资源。

### 4.5 运行面与管理面分离

运行时客户端应查询已知配置，并监听已知配置或模式匹配的配置。大范围列表、搜索、导入、导出、
克隆、监听诊断、历史、容量、指标和本地缓存操作属于管理能力，应通过 Admin API、Console API
或 Maintainer SDK 暴露。
运行时客户端连接、listener recovery、snapshot 和 failover 行为由
[客户端运行时规范](../client/README.md)定义。

### 4.6 横切能力通过扩展接入

Config 可以集成扩展机制，但 Config 领域的归属不转移：

| 关注点 | 规则 |
| --- | --- |
| 加密 | Config 拥有内容身份和持久化；加密算法由[配置加密插件规范](../plugin/config-encryption-plugin-spec.md)定义。 |
| 配置变更通知 | Config 拥有由[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)定义的本地变更事件；外部回调由[配置变更插件规范](../plugin/config-change-plugin-spec.md)定义。 |
| 数据源方言 | Config 拥有 repository 语义；SQL 方言由[数据源方言插件规范](../plugin/datasource-dialect-plugin-spec.md)定义。 |
| 鉴权 | Config API 和 gRPC handler 使用 `SignType.CONFIG`，并遵循[鉴权与权限规范](../auth/auth-permission-spec.md)。 |
| Control | 高频发布、查询、监听、推送和模糊订阅流程应暴露稳定的 Control 点，遵循 [Control 插件规范](../plugin/control-plugin-spec.md)。 |

## 5. 接口面

| 接口面 | 范围 |
| --- | --- |
| HTTP Open API | `/v3/client/cs/config` 面向自定义 HTTP 客户端查询单个配置。不提供 HTTP 长轮询或大范围管理能力。 |
| HTTP Admin API | `/v3/admin/cs/*` 提供配置 CRUD、列表/搜索、历史、监听诊断、容量、指标和运维操作。 |
| gRPC API | 提供运行时查询、兼容发布、兼容删除、精确监听、模糊订阅和服务端推送消息。参见 [gRPC API 规范](../grpc-api/api-spec.md)。 |
| Client SDK | 通过 `ConfigService` 面向运行时应用提供查询、监听、本地快照、filter 和兼容写入方法。参见 [SDK 规范](../sdk/sdk-spec.md)。 |
| Maintainer SDK | 通过 `ConfigMaintainerService` 等服务提供管理类接入。 |
| Console API | 面向 UI 的管理流程。Console API 可以调整展示形态，但不能重新定义 Config 语义。 |

## 6. 边界

- Config 不拥有服务发现、服务实例生命周期或健康检查，这些属于 Naming。
- Config 不拥有 AI 资源身份。AI 资源使用的存储兼容映射不应让该 AI 资源在新规范中变成普通
  Config 资源。
- Config 加密通过插件保护配置内容，但 Config 不是完整的密钥生命周期或 KMS 领域。
- `appName`、`desc`、`configTags`、`type`、`use`、`effect`、`schema` 等元数据不改变资源身份。
- 灰度发布状态是 Config 资源的从属状态，不应创建第二套顶层 Config 身份。

## 7. 基础能力对齐

共享 datasource、嵌入式/外部存储、repository、dump 和 cache 边界由
[持久化与 Dump 规范](../design/foundation-persistence-dump-spec.md)定义。
Config 特有的写入可见性、dump 恢复和集群变更传播由
[Config 一致性、Dump 与可见性规范](config-consistency-dump-visibility-spec.md)定义。
共享任务执行和本地事件边界由[任务执行规范](../design/foundation-task-execution-spec.md)和
[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)定义。
共享可观测边界由[可观测钩子规范](../design/foundation-observability-hooks-spec.md)定义。
Config trace 与审计字段应遵循该规范中的共享字段指引，并且不得包含完整 Config content。
