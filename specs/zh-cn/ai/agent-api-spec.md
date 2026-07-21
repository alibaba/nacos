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

# Agent API 规范

| 项目 | 值 |
|---|---|
| 状态 | 实验性目标契约；不是当前已实现 API 清单 |
| 目标版本线 | Nacos 3.3 |
| 范围 | Agent 与 RAD 的 Client HTTP/gRPC、Admin HTTP/Maintainer SDK 和 Console HTTP Binding |

本文把 [Agent 管理规范](agent-management-spec.md)和
[RAD 协议规范](rad-protocol-spec.md)绑定到 Nacos API。实现声明新的 Agent/RAD
能力位后，必须遵循本文。在新 Binding 实现并完成能力协商前，现有 A2A API 仍由
[A2A Agent 规范](a2a-agent-spec.md)约束。

## 1. API 分层与公共规则

| 接口面 | 传输 | 主要调用方 | 职责 |
|---|---|---|---|
| Client | HTTP 和 gRPC | Agent 使用方与 Runtime 发布方 | Search、Discover、Watch、注册和注销 |
| Admin | HTTP | Maintainer SDK 与管理集成 | Agent CRUD、Version 生命周期和 Runtime 查看 |
| Console | HTTP | Nacos Console UI | 面向 UI 的 Admin 语义 Facade |

HTTP API 遵循 Nacos v3 约定：

- Client 路径以 `/v3/client/ai/agents` 开头；
- Admin 路径以 `/v3/admin/ai/agents` 开头；
- Console 路径以 `/v3/console/ai/agents` 开头；
- 响应使用 `Result<T>`；新 Controller 使用 `@NacosApi`、
  `@Since(version = "3.3.0")`、对应 `ApiType`、`SignType.AI` 以及 `READ` 或
  `WRITE` 鉴权；
- GET 输入使用 query，写入使用 JSON body；`agentName` 按原值比较，不作为
  PathVariable；
- gRPC 继续使用统一 Nacos `Payload` stream 和 `metadata.type`，不增加 proto
  service method。

六个 RAD 根消息直接复用，不再创建一套领域模型。Java 可以用字段等价的
`Page<AgentCatalogEntry>` 实现 `AgentCatalogPage`。`Result<T>`、gRPC wrapper、
`ClientLivenessInfo` 和 Console 专用视图属于 Binding 对象，不进入 RAD Schema。

### 1.1 Namespace 规则

| 调用方 | 规则 |
|---|---|
| 普通 Client SDK | SDK 实例绑定一个 namespace。公开方法不接受 namespace 参数；Proxy 复制请求并在传输前注入绑定值。 |
| Client HTTP 调用方 | 可以显式提交 `namespaceId`；省略时 Binding 在进入 RAD 前注入规范化默认值 `public`。 |
| Maintainer SDK 和 Admin API | Maintainer SDK 实例不绑定 namespace；每个请求都显式提交 `namespaceId`，不提供默认 namespace 重载。 |

如果普通 Client SDK 接受的模型中已带非空 `namespaceId`，它必须拒绝与 SDK namespace
不同的值，并且不得修改调用方原对象。

### 1.2 并发、结果与错误

Agent 元数据更新使用 `expectedMetaVersion`；draft 内容更新使用
`expectedContentDigest`。列表使用分页；`RuntimeEndpointSnapshot` 是完整、不分页的快照。

| 条件 | 必须返回的结果 |
|---|---|
| 字段缺失或非法、URI/range 非法、Endpoint 自然键重复 | 标准参数错误 |
| Discover 目标不可见或不存在 | `RESOURCE_NOT_FOUND`，不区分可见性 |
| 不存在 Agent 定义时 Endpoint 预注册 | 完成结构、鉴权、配额和冲突校验后接受 |
| 元数据 CAS、内容 CAS 或 Publisher payload 冲突 | `RESOURCE_CONFLICT` |
| Version 生命周期转换非法 | `ILLEGAL_STATE` |
| HTTP heartbeat 找不到 Client | HTTP 404 和独立应用码 `HTTP_CLIENT_NOT_FOUND` |
| 协商后的传输不支持能力 | 本地 `FEATURE_NOT_SUPPORTED`，不发送远程请求 |
| 注销不存在的 contribution | 成功且不发生变更 |
| 合法 Runtime 查询没有实例 | 成功返回 `items=[]` |
| Discover Filter 没有匹配 | 按 RAD 返回类型化空结果，不返回 `NOT_FOUND` |

HTTP 状态与 `Result.code` 使用通用 v3 异常映射；gRPC Response 暴露等价错误类别。
新增应用错误码的数值由实现变更统一分配，但不得与普通 `RESOURCE_NOT_FOUND` 混用。

## 2. Client API

### 2.1 Java SDK 契约

面向用户的接口命名为 `AgentDiscoveryService`，应用代码不必感知 RAD 缩写。A2A
兼容期内：

```text
AiService extends AgentDiscoveryService, A2aService
```

| 能力 | 方法 | 输入 | 返回 |
|---|---|---|---|
| Search | `searchAgents` | 不允许调用方控制 namespace 的 `AgentSearchRequest` | `Page<AgentCatalogEntry>` |
| Discover | `discoverAgent` | `AgentReference` | `AgentDiscoveryResult` |
| 过滤 Discover | `discoverAgent` | `AgentReference`、`AgentDiscoveryFilter` | `AgentDiscoveryResult` |
| Watch | `subscribeAgent` | Reference、可选 Filter、Listener | 当前 `AgentDiscoveryResult` |
| 取消 Watch | `unsubscribeAgent` | 相同 Reference、Filter 和 Listener identity | `void` |
| 注册 | `registerAgentEndpoints` | `AgentEndpointRegistrationBatch` | `void` |
| 注销 | `deregisterAgentEndpoints` | `AgentEndpointDeregistrationBatch` | `void` |

`subscribeAgent` 返回当前完整结果，之后投递完整替换结果。`getAll`、
`selectOneHealthy`、协议选择、priority/weight 选址和实际 Agent Calling 是 SDK 本地
helper，不增加远程操作。

注册是按自然键 upsert，不替换未提交 Endpoint。SDK 将注册 Batch 保存为 redo 意图。
首个实现可以不增加通用 Agent 定义发布方法，但现有 `A2aService.releaseAgentCard`
必须通过兼容 Adapter 保持可用。后续 Client SDK 提供可选的代码式 Agent 发布：
`autoSubmit=false` 创建 draft，`autoSubmit=true` 执行普通 submit Pipeline；它不是
force-publish，注册 Endpoint 也不会隐式创建定义。

### 2.2 传输矩阵

| 能力 | HTTP | gRPC |
|---|:---:|:---:|
| Search | 是 | 是 |
| Discover | 是 | 是 |
| Watch 和 Push | 否 | 是 |
| 注册和注销 | 是 | 是 |
| Publisher heartbeat | 是 | 复用 gRPC connection lifecycle |

HTTP-only SDK 在本地拒绝 Watch，不得通过轮询伪装 Watch。写入超时后，只有 SDK
确定服务端未处理请求时才允许切换传输；结果未知的 gRPC 写入不得盲目通过 HTTP 重试。

### 2.3 Client HTTP 路径

| Method | Path | 输入 | 返回 |
|---|---|---|---|
| GET | `/v3/client/ai/agents/search` | RAD Search query | `Result<Page<AgentCatalogEntry>>` |
| GET | `/v3/client/ai/agents` | RAD Reference 和可选 Filter query | `Result<AgentDiscoveryResult>` |
| POST | `/v3/client/ai/agents/endpoints` | `AgentEndpointRegistrationBatch` | `Result<ClientLivenessInfo>` |
| DELETE | `/v3/client/ai/agents/endpoints` | JSON `AgentEndpointDeregistrationBatch` | `Result<Void>` |
| PUT | `/v3/client/ai/agents/endpoints/heartbeat` | 无 body | `Result<ClientLivenessInfo>` |

Search query 名称与 RAD 字段相同。重复 `tagsAll` 取 AND，重复 `protocolsAny` 取 OR；
`agentNameContains` 是大小写敏感的 literal substring。

Discover 直接映射 `agentName`、`version` 和 `label`。重复 Filter 参数为 `protocol`、
`transport` 和 `endpointSource`，`protocolVersion` 为单值。`metadataSelector` 使用一个
URL encoded JSON object，而不是动态 `metadata.<key>` 参数名。

Endpoint 路径只使用 POST 和 DELETE。POST 已经 upsert 完整 Endpoint 值，通用 PUT 会
引入不明确的局部更新语义。GET 也没有必要：消费者使用 Discover，管理员使用
`RuntimeEndpointSnapshot`。0.1 Binding 只定义带 JSON body 的 DELETE，并要求 Client
和 Gateway 保留该 body。

### 2.4 HTTP Publisher Identity 与活性

Endpoint 写入和 heartbeat 必须携带：

```text
X-Nacos-Client-Id: http-<ipToken>-<processToken>-<clientSequence>-<createTimestamp>
Request-Module: AI
```

服务端将 Client id 视为匹配 `[A-Za-z0-9._:-]+`、长度 1～256 的 opaque 值。
官方生成器只使用 `[A-Za-z0-9-]`，包含至少 96 bit 的进程随机熵，以
`clientSequence` 区分同进程的 SDK 实例，并可加入诊断用 PID token。重试、切换
Server 和 redo 保持 id；进程重启生成新 id。它是路由身份，不是 credential。

`ClientLivenessInfo` 只包含：

```text
heartbeatIntervalMillis < unhealthyTimeoutMillis < expireTimeoutMillis
```

最近一次成功注册或 heartbeat 响应决定调度。一个 heartbeat 维持整个 Client 活性，
与 Endpoint 数量无关；Endpoint 写入同样刷新活性。没有剩余 Endpoint 的 Client 被删除并停止 heartbeat。

| 状态 | Runtime 行为 |
|---|---|
| `ACTIVE` | Contribution 使用当前 Naming health。 |
| `UNHEALTHY` | 超过 `unhealthyTimeoutMillis` 后 Contribution 仍可发现，但 `healthy=false`。 |
| `EXPIRED` | 超过 `expireTimeoutMillis` 后删除 Client 拥有的全部 Contribution。 |

服务端根据 `clientId`，使用 Distro type `AI_AGENT_HTTP_CLIENT` 路由 HTTP Publisher
状态。只有责任节点持有 native Client、`lastActiveTime` 和超时任务；Peer 接收重建
Naming/RAD 投影所需的完整 Client state。新 Owner 只有在收到完整 Snapshot 后才启动
故障转移宽限，否则返回 `HTTP_CLIENT_NOT_FOUND`，Client 随后 redo 全部期望 Endpoint
分组。首次写入把 Client id 绑定到鉴权主体和 namespace；后续不匹配时拒绝。同一字符串
在其他模块不共享活性和清理状态。

### 2.5 gRPC Payload 与能力位

| Request | Response | 语义 |
|---|---|---|
| `AgentSearchRequest` | `AgentSearchResponse` | Search 并返回目录分页 |
| `AgentDiscoveryRequest` | `AgentDiscoveryResponse` | 一次 Discover |
| `AgentSubscribeRequest` | `AgentSubscribeResponse` | 订阅或取消；订阅成功时返回不透明 `watchKey` 和当前完整结果 |
| `AgentDiscoveryNotifyRequest` | `AgentDiscoveryNotifyResponse` | 为一个 `watchKey` Push `SNAPSHOT` 或 `TERMINATED` 事件并接收 ACK |
| `AgentEndpointRegisterRequest` | `AgentEndpointOperationResponse` | 注册一个 RAD Batch |
| `AgentEndpointDeregisterRequest` | `AgentEndpointOperationResponse` | 注销一个 RAD Batch |

所有 Request 的 module 为 `ai`。gRPC Endpoint Contribution 归属于
`RequestMeta.connectionId`，不增加 Client id 或 heartbeat Payload。连接断开后删除该
Connection 的 Contribution；重连取得新 connection id，并 redo Endpoint 和订阅。

`AgentSubscribeResponse.watchKey` 是 Binding 为已接受 Wire Subscription 定义的不透明
身份。SDK 将它映射到规范化本地 Watch 身份，不解析其内容。
`AgentDiscoveryNotifyRequest` 包含 `watchKey` 和 `eventType`：

- `SNAPSHOT` 必须携带一个完整 `AgentDiscoveryResult`，且不携带错误；
- `TERMINATED` 不携带 Result，并且本版本固定要求 `errorCode=NOT_FOUND`；
- 两种事件都使用 `AgentDiscoveryNotifyResponse` 确认；
- `TERMINATED` 只关闭共享 Payload Connection 上由 `watchKey` 标识的 Watch，不关闭
  Connection 或其他 Watch。

SDK 对 `SNAPSHOT` 原子替换缓存结果。对于 `TERMINATED`，SDK 投递终止状态，仅删除该
Watch 及其 Redo State，再发送 ACK。Reconnect 后，SDK 丢弃旧 Connection 维度的
`watchKey`，使用规范化本地 Watch 身份重新订阅，并保存新 Response 中的 `watchKey`
和当前结果。这些 Request 和 Response 是 Nacos gRPC Binding 对象，不增加 RAD 的六个
根消息。

目标能力位如下：

| 常量 | Wire key | 含义 |
|---|---|---|
| `SERVER_AGENT_DISCOVERY_V1` | `agentDiscoveryV1` | Server 接受 RAD Search、Discover 和 Watch Payload |
| `SERVER_AGENT_ENDPOINT_V1` | `agentEndpointV1` | Server 接受 RAD Endpoint Publication Payload |
| `SDK_AGENT_DISCOVERY_V1` | `agentDiscoveryV1` | SDK 接受 RAD Discovery Push |

旧 `SERVER_AGENT_REGISTRY`、`SERVER_AGENT_CARD_V1` 和 `SDK_AGENT_REGISTRY` 只约束
旧 A2A 契约。新能力位缺失时，不得通过旧能力位 fallback 发送 RAD Payload。

### 2.6 幂等与 Redo

| 事件 | 必须行为 |
|---|---|
| 重复相同 Register | 成功且语义不变 |
| Register 修改非身份字段 | Upsert 当前 Publisher Contribution |
| 同一 Batch 出现重复自然键 | 拒绝整个 Batch |
| 重复 Deregister | 成功且语义不变 |
| 重复 heartbeat | 只刷新 Client 活性 |
| HTTP timeout | 保持 Client id 和 payload，退避重试 |
| `HTTP_CLIENT_NOT_FOUND` | 将全部本地 Endpoint 意图标记为未注册，并按完整分组 redo |
| gRPC reconnect | 使用新 connection id redo Endpoint 和订阅 |
| 跨传输注销 | 禁止；一个 Publisher identity 不能删除另一传输的 Contribution |

SDK 在第一次写入前记录期望状态。Shutdown 执行 best-effort 注销，expire 作为清理兜底。
参数、鉴权和 Publisher 冲突错误不进入无限 redo。

## 3. Admin API 与 Maintainer SDK

Admin 读取不隐式执行数据面 Discover，也不把 Runtime Endpoint 注入 Version descriptor。

### 3.1 Agent 与读取视图

| Method | Path | 动作 | 返回 |
|---|---|---|---|
| POST | `/v3/admin/ai/agents` | 原子创建 Agent 和 initial draft | `Result<AgentOverview>` |
| GET | `/v3/admin/ai/agents` | 读取 Agent 和首个有界 Version Summary page | `Result<AgentOverview>` |
| PUT | `/v3/admin/ai/agents` | 使用 metadata CAS 更新 Agent 可写字段 | `Result<Agent>` |
| DELETE | `/v3/admin/ai/agents` | 删除 Agent 定义及 Version 内容 | `Result<Void>` |
| GET | `/v3/admin/ai/agents/list` | 筛选和分页 Agent Summary | `Result<Page<AgentSummary>>` |
| GET | `/v3/admin/ai/agents/versions` | 分页读取 Version Summary | `Result<Page<AgentVersionSummary>>` |
| GET | `/v3/admin/ai/agents/version` | 读取一个精确 Version 定义 | `Result<AgentVersionDetail>` |
| GET | `/v3/admin/ai/agents/runtime-endpoints` | 读取一个 Protocol 的完整 Runtime Snapshot，可按 Version 过滤 | `Result<RuntimeEndpointSnapshot>` |

Runtime 查询输入为 `namespaceId + agentName + protocol + version?`；`protocol` 必填。
省略 `version` 时，对该 Protocol 的每个 Endpoint 自然键返回一项及其全部 Binding；提交时只保留匹配 Binding。
查询不应用 `endpointSourceOrder`，不要求定义存在，没有 Instance 时返回空 items。

Create 包含 Agent 可写字段和必填 `initialDraft`。Agent、Version row 与 Storage 写入
具有一个逻辑原子结果，并补偿局部失败。Update 可以修改展示信息、tags、extensions、
enabled 状态、owner 和 scope，但不能修改身份、Version 内容、label 或派生 Catalog。
删除定义后，普通 Discover 立即不可见，但不会删除生命周期独立的 Runtime Publication。

### 3.2 Version 生命周期路径

| Method | Path | 转换或动作 | 返回 |
|---|---|---|---|
| POST | `/v3/admin/ai/agents/draft` | 创建新 draft，可复制一个精确 Version | `Result<AgentVersionDetail>` |
| PUT | `/v3/admin/ai/agents/draft` | 使用 content-digest CAS 更新 draft | `Result<AgentVersionDetail>` |
| DELETE | `/v3/admin/ai/agents/draft` | 删除 draft | `Result<Void>` |
| POST | `/v3/admin/ai/agents/submit` | `draft -> reviewing`，或统一的无 Pipeline 转换 | `Result<AgentVersionSummary>` |
| POST | `/v3/admin/ai/agents/publish` | `reviewed -> online` | `Result<AgentVersionSummary>` |
| POST | `/v3/admin/ai/agents/force-publish` | 经审计地绕过 Pipeline 到 `online` | `Result<AgentVersionSummary>` |
| POST | `/v3/admin/ai/agents/redraft` | `reviewed -> draft` | `Result<AgentVersionSummary>` |
| POST | `/v3/admin/ai/agents/online` | `offline -> online` | `Result<AgentVersionSummary>` |
| POST | `/v3/admin/ai/agents/offline` | `online -> offline` | `Result<AgentVersionSummary>` |
| PUT | `/v3/admin/ai/agents/labels` | 更新自定义 label；`latest` 仍由 Server 管理 | `Result<Agent>` |

每个动作都以 `namespaceId + agentName + exact version` 标识目标；写入时省略 Version
永远不表示 latest。`force-publish` 使用普通 Agent WRITE 权限，不增加权限点，但成功和
失败都记录调用主体、资源身份、原状态、目标状态、结果、request id 和时间。审计不记录
descriptor 或敏感 metadata。首版不提供同 Version 强制替换内容的 API。

### 3.3 Maintainer SDK

`AiMaintainerService.agent()` 返回 `AgentMaintainerService`；兼容期内继续保留
`AiMaintainerService.a2a()`。Agent Maintainer 接口一一映射 Admin HTTP，复杂写入使用
Request/Command 对象。它不绑定 namespace，每次调用都要求 `namespaceId`，不增加
Maintainer gRPC 传输。

## 4. Console API

Console 使用 `/v3/console/ai/agents`，镜像 Admin 的每个相对路径、请求、结果、生命周期
和鉴权意图。它是 UI Facade，不是第二套 Agent Application Service。

唯一 Console 专用响应为 `ConsoleRuntimeEndpointView`，它包装
`RuntimeEndpointSnapshot` 并增加：

```text
namingServiceRef { namespaceId, groupName, serviceName }
```

Backend 计算该引用，Browser 不实现 AgentName codec 或 Naming service composer。
Version 页面先读取 `AgentVersionDetail`，从 `callInterfaces[]` 构建 Protocol 页签，并在
选中页签时按当前 Version 懒加载一个 Runtime Snapshot。即使 CallInterface 没有
`RUNTIME`，它也可以查询并单独展示已注册状态，同时说明这些地址当前不会进入该 Version
的 Discover。首版 Agent Console API 不提供 Runtime 修改；UI 跳转 Naming Instance
页面执行 enable 或 disable。

Console 不提供 RAD Search、Discover、Watch、Endpoint Publication 或远程 Agent Calling。

## 5. 实现与兼容要求

实现必须一起完成以下工作，才能声明 Agent 或 RAD 能力：

1. API 对象、校验、错误映射、鉴权和审计；
2. gRPC Payload 注册与能力协商；
3. HTTP Publisher Distro 状态、活性、幂等和 redo；
4. Java SDK namespace 绑定、缓存、Watch、重连和 Endpoint redo；
5. Admin/Maintainer 与 Console 契约；
6. 旧 A2A Facade 转换；
7. OpenAPI、Java SDK 和 Maintainer SDK 集成测试场景矩阵与 coverage registry。

旧 Console A2A API 支持到 Nacos 3.4 版本线；旧 Admin 和 Maintainer A2A API 保留到
Nacos 4.0 兼容边界。历史数据迁移和混合版本滚动升级属于独立规范，不得从本 API-only
契约推断。
