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
| Client | HTTP 和 gRPC | Agent 使用方与 Runtime 发布方 | Search、Discover、注册和注销；SDK 后续通过 Discover 轮询提供本地订阅 |
| Admin | HTTP | Maintainer SDK 与管理集成 | Agent CRUD、Version 生命周期和 Runtime 查看 |
| Console | HTTP | Nacos Console UI | 面向 UI 的 Admin 语义 Facade |

HTTP API 遵循 Nacos v3 约定：

- Client 路径以 `/v3/client/ai/agents` 开头；
- Admin 路径以 `/v3/admin/ai/agents` 开头；
- Console 路径以 `/v3/console/ai/agents` 开头；
- 响应使用 `Result<T>`；新 Controller 使用 `@NacosApi`、
  `@Since(version = "3.3.0")`、对应 `ApiType`、`SignType.AI` 以及 `READ` 或
  `WRITE` 鉴权；
- GET 输入使用 query；其他 HTTP 输入编码由对应 Client、Admin 或 Console Binding
  分别定义；`agentName` 按原值比较，不作为 PathVariable；
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
| Maintainer SDK 和 Admin API | Maintainer SDK 实例不绑定 namespace。Admin HTTP Form 保留 `namespaceId`，省略或空白值统一规范化为 `public`。Maintainer Request 和 Command Payload 不包含 `namespaceId`：显式方法参数是自定义 namespace 的唯一来源，便利重载始终使用 `public`。 |

如果普通 Client SDK 接受的模型中已带非空 `namespaceId`，它必须拒绝与 SDK namespace
不同的值，并且不得修改调用方原对象。

### 1.2 并发、结果与错误

Agent 元数据更新复用当前共享的 AI Resource 更新流程。首版 Agent Admin 契约不暴露
Agent 专属的 `expectedMetaVersion`；条件更新能力后续随 `ai_resource` 和
`ai_resource_version` 的统一 CAS 能力定义。draft 内容只允许在目标 Version 等于
Resource 当前 `editingVersion` 且仍为 `draft` 状态时更新。列表使用分页；
`RuntimeEndpointSnapshot` 是完整、不分页的快照。

| 条件 | 必须返回的结果 |
|---|---|
| 字段缺失或非法、URI/range 非法、Endpoint 自然键重复 | 标准参数错误 |
| Discover 目标不可见或不存在 | `RESOURCE_NOT_FOUND`，不区分可见性 |
| 不存在 Agent 定义时 Endpoint 预注册 | 完成结构、鉴权和单 Batch 配额校验后接受 |
| 收敛后的 Runtime 投影包含 Publisher Payload 冲突 | `RESOURCE_CONFLICT` |
| Version 生命周期转换非法 | `ILLEGAL_STATE` |
| HTTP 注册未能建立或保留 Client，或 heartbeat 找不到 Client/Publication | HTTP 404 和独立应用码 `HTTP_CLIENT_NOT_FOUND (50404)` |
| 协商后的传输不支持能力 | 本地 `FEATURE_NOT_SUPPORTED`，不发送远程请求 |
| 注销不存在的 contribution | 成功且不发生变更 |
| 合法 Runtime 查询没有实例 | 成功返回 `items=[]` |
| Discover Filter 没有匹配 | 按 RAD 返回类型化空结果，不返回 `NOT_FOUND` |

HTTP 状态与 `Result.code` 使用通用 v3 异常映射；gRPC Response 暴露等价错误类别。
`HTTP_CLIENT_NOT_FOUND` 固定为 `50404`，不得与普通 `RESOURCE_NOT_FOUND` 混用。

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
| 轮询订阅 | `subscribeAgent` | Reference、可选 Filter、Listener | 当前 `AgentDiscoveryResult`，目标尚不存在时为 `null` |
| 取消轮询订阅 | `unsubscribeAgent` | 相同 Reference、Filter 和 Listener identity | `void` |
| 注册 | `registerAgentEndpoints` | `AgentEndpointRegistrationBatch` | `void` |
| 注销 | `deregisterAgentEndpoints` | `AgentEndpointDeregistrationBatch` | `void` |
| 代码式发布 | `publishAgent` | `AgentPublishRequest` | `AgentVersionDetail` |

`subscribeAgent` 是 SDK 本地便利能力，不对应服务端 Watch/Push。SDK 周期执行与
调用方 Reference、Filter 相同的 Discover；首次目标不存在时返回 `null` 但保留轮询，
后续轮询仍为 `NOT_FOUND` 时不终止订阅，也不投递空快照。目标出现，或解析出的
Version、`contentDigest`、任一 `sourceRevision` 发生变化时，Listener 收到新的完整
替换结果。`getAll`、`selectOneHealthy`、协议选择、priority/weight 选址和实际 Agent
Calling 是 SDK 本地 helper，不增加远程操作。

`AgentReference` 同时省略 `version` 和 `label` 时使用面向发布切换安全的默认语义：
返回 latest 定义元数据，以及兼容任一当前在线版本的 Runtime Endpoint。显式
`label=latest` 请求严格的 latest-only Runtime 地址池；精确 version 和自定义 label
解析后仍保持精确语义。轮询订阅重复同一个 Discover Request，因此保持同样的区分。

一个 Registration Batch 是该 SDK Publisher 在
`(namespaceId, agentName, protocol)` 下的完整期望状态。Register 完整替换此前
Batch 及其唯一的 `runtimeVersion` 和 `versionRange`，未提交的 Endpoint 会被删除。
SDK 将该完整 Batch 保存为 redo 意图。

`deregisterAgentEndpoints` 保留为按自然键操作的便利方法。SDK 从期望 Batch 中删除
这些自然键，再通过 Register 发送完整的剩余 Batch；没有 Endpoint 剩余时发送整份
Publication 注销。现有 `A2aService.releaseAgentCard` 必须通过兼容 Adapter 保持可用。

`publishAgent` 是 namespace-bound 的可选定义发布步骤。`AgentPublishRequest` 复用
`AgentDraftCreateRequest` 的 Version 内容、`basedOnVersion`、作者、变更说明和首次 Agent
元数据字段，并增加默认值为 `false` 的 `autoSubmit`。调用方不能提交 namespace；Proxy
复制 Request 后使用 SDK namespace，且不得修改调用方对象。`autoSubmit=false` 只创建或
返回等价 draft；`autoSubmit=true` 在创建 draft 后执行普通 submit Pipeline，并返回最终可观察到的
`reviewing`、`reviewed` 或 `online` Version。该操作不是 force-publish，注册 Endpoint 也不会
隐式创建定义。

同一 namespace、Agent 和精确 Version 的等价重试必须收敛：draft 重试保持幂等，
`autoSubmit=true` 在先前请求已把等价内容推进到 `reviewing`、`reviewed` 或 `online` 时返回现有
Version；draft 后以相同 Request 改为 `autoSubmit=true` 必须继续 submit。内容、作者、变更说明
或调用方显式提供的首次元数据不等价时返回冲突。已推进 Version 上的
`autoSubmit=false`、以及 `offline` Version 上的任一代码式发布均返回非法状态或冲突。Submit
失败不得补偿删除已创建 draft。

### 2.2 传输矩阵

| 能力 | HTTP | gRPC |
|---|:---:|:---:|
| Search | 是 | 是 |
| Discover | 是 | 是 |
| 服务端 Watch 和 Push | 否 | 否 |
| SDK 本地轮询订阅 | 复用 Discover | 复用 Discover |
| 注册和注销 | 是 | 是 |
| 代码式定义发布 | 是 | 是 |
| Publisher heartbeat | 是 | 复用 gRPC connection lifecycle |

轮询订阅使用 SDK 已选择的 Discover 传输，不新增 HTTP 路径、gRPC Payload、能力位或
Publisher 续约行为。普通 Discover 和订阅轮询只刷新 HTTP Client 本身，不刷新其中的
Publisher。写入超时后，只有 SDK 确定服务端未处理请求时才允许切换传输；结果未知的
gRPC 写入不得盲目通过 HTTP 重试。

### 2.3 Client HTTP 路径

| Method | Path | 输入 | 返回 |
|---|---|---|---|
| GET | `/v3/client/ai/agents/search` | RAD Search query | `Result<Page<AgentCatalogEntry>>` |
| GET | `/v3/client/ai/agents` | RAD Reference 和可选 Filter query | `Result<AgentDiscoveryResult>` |
| POST | `/v3/client/ai/agents` | Form：`AgentPublishRequest`，复杂字段使用 JSON 字符串 | `Result<AgentVersionDetail>` |
| POST | `/v3/client/ai/agents/endpoints` | Form：完整 `AgentEndpointRegistrationBatch`，其中 `endpoints` 为 JSON 字符串 | `Result<ClientLivenessInfo>` |
| DELETE | `/v3/client/ai/agents/endpoints` | Form：`namespaceId + agentName + protocol` Publication Identity | `Result<Void>` |
| PUT | `/v3/client/ai/agents/endpoints/heartbeat` | 无 body | `Result<ClientLivenessInfo>` |

Search query 名称与 RAD 字段相同。重复 `tagsAll` 取 AND，重复 `protocolsAny` 取 OR；
`agentNameContains` 是大小写敏感的 literal substring。

Agent Search 是共享 Search Core 上固定 `resourceType=agent` 的资源专用 Facade，不维护第二套
索引或在索引分页后执行二次业务过滤。`agentNameContains`、`tagsAll` 和 `protocolsAny` 必须
在 total 与分页截断前转换为 [AI 资源检索规范](ai-resource-search-spec.md)的类型化 predicate。
通用 AI Resource Search 只查询 Agent 时，候选资格、可见性和当前性必须与本 API 一致；响应 DTO、
排序和 numbered page 仍遵循 RAD。

`nacos.ai.rad.search.mode=INDEX` 且 Agent projection 未 READY 时，HTTP 和 gRPC Binding 返回
明确的 service unavailable 等价错误；`AUTO` 在 readiness 前使用完整旧扫描，READY 后使用索引。
Binding 不暴露当前物理路径，也不得因索引调用失败在一次请求内降级或混合结果。

Discover 直接映射 `agentName`、`version` 和 `label`。重复 Filter 参数为 `protocol`、
`transport` 和 `endpointSource`，`protocolVersion` 为单值。`metadataSelector` 使用一个
URL encoded JSON object，而不是动态 `metadata.<key>` 参数名。

Endpoint 路径只使用 POST 和 DELETE。POST 完整替换当前 Publisher 对一个 Agent 和
Protocol 的 Batch，因此通用 PUT 只会重复相同替换操作。GET 也没有必要：消费者使用
Discover，管理员使用 `RuntimeEndpointSnapshot`。

Endpoint HTTP 写入使用独立 Form，不直接绑定公共 RAD Request，也不使用 `@RequestBody`。
POST 使用 `application/x-www-form-urlencoded`：`namespaceId`、`agentName`、
`runtimeVersion`、`versionRange` 和 `protocol` 是普通字段，`endpoints` 是 JSON 数组
字符串。DELETE 使用普通 `namespaceId`、`agentName` 和 `protocol` Form 参数。
Form 负责将省略或空白的 namespace 规范化为 `public`。

DELETE 删除当前 HTTP Publisher 对给定 Agent 和 Protocol 的整份 Publication，不接受
Endpoint 自然键。官方 SDK 的部分注销先更新本地期望 Batch，再通过 POST 提交完整
剩余内容；只有剩余 Batch 为空时才调用 DELETE。直接 HTTP 调用方同样自行维护完整
期望 Batch。该三字段 DELETE Form 是 Binding 对象，不替代面向应用的
`AgentEndpointDeregistrationBatch` RAD 模型。

定义发布 POST 使用独立 Form，不使用 JSON body。`provider`、`tags`、`extensions` 和
`callInterfaces` 编码为 JSON 字符串，其余字段为普通 Form 字段。Form 只调用一次
`toRequest()`；该方法完成反序列化和校验，Controller 不再单独调用 `validate()`。
定义发布是持久 Agent/Version 操作，不要求 Endpoint Publisher 使用的
`X-Nacos-Client-Id` 或 `Request-Module` Header。

### 2.4 HTTP Publisher Identity 与活性

Endpoint 写入和 Publisher heartbeat 必须携带：

```text
X-Nacos-Client-Id: http-<ipToken>-<processToken>-<clientSequence>-<createTimestamp>
Request-Module: AI
```

服务端将 Client id 视为匹配 `[A-Za-z0-9._:-]+`、长度 1～256 的 opaque 值。
官方生成器只使用 `[A-Za-z0-9-]`，包含至少 96 bit 的进程随机熵，以
`clientSequence` 区分同进程的 SDK 实例，并可加入诊断用 PID token。重试、切换
Server 和 redo 保持 id；进程重启生成新 id。它是路由身份，不是 credential。

服务端将外部值包装为 `HTTP_CLIENT@@<externalClientId>` Naming 内部 Client id。
Search 和 Discover 可以携带相同 Header；如果对应 Client 已存在，查询只刷新 Client 活性，
不创建空 Client，也不修改该 Client 中任何 Publisher 的活性、健康或 revision。AI 模块自己的
Distro Filter 根据该内部 id 把有状态请求路由到责任节点；它不扩展 Naming HTTP API 的
Distro Filter。

`ClientLivenessInfo` 只包含：

```text
heartbeatIntervalMillis < unhealthyTimeoutMillis < expireTimeoutMillis
```

首版 Naming HTTP Client 使用固定有效值 5000、15000 和 30000 毫秒，请求方不能覆盖。
返回这些值是为了避免 SDK 硬编码服务端策略；如果后续 Naming 提供配置能力，响应返回
当时的服务端有效值而不改变协议字段。

HTTP Client 分别维护 Client 活性和 Publisher 活性。合法查询只刷新 Client 活性；
Endpoint 写入和 Publisher heartbeat 同时刷新 Client 以及该 Client 的全部 Publisher 活性。
Publisher heartbeat 与 Endpoint 数量无关。没有剩余 Endpoint 且没有 subscriber state 的
Client 被删除并停止 heartbeat。

| 状态 | Runtime 行为 |
|---|---|
| `ACTIVE` | Publisher 活跃，Contribution 使用当前 Naming health。 |
| `UNHEALTHY` | Publisher 超过 `unhealthyTimeoutMillis` 后 Contribution 仍可发现，但 `healthy=false`；查询不能恢复它。 |
| `EXPIRED` | Publisher 超过 `expireTimeoutMillis` 后删除 Client 拥有的全部 Contribution，但仍有 subscriber state 的 Client 可以保留。 |

HTTP Client 复用 Naming 的 `Nacos:Naming:v2:ClientData`、
`DistroClientDataProcessor`、Client snapshot、verify 和 repair，不增加 Agent 专用 Distro
type。`HttpConnectionBasedClientManager` 与现有 `ConnectionBasedClientManager` 同级并由
`ClientManagerDelegate` 根据内部 id 路由。只有责任节点执行 native Client 和 Publisher
超时任务；Peer 接收重建 Naming/RAD 投影所需的标准 Client state。责任转移后以 replica verify
时间作为本地超时下界，Client 不再维护另一个 ownership 标记；该正常 Distro 故障转移不定义
混合版本兼容路径。

首次有状态写入把 Client id 绑定到鉴权主体和 namespace，后续不匹配时拒绝。其他模块使用相同
external Client id 时复用同一个 HTTP Client 生命周期。旧节点没有对应 Agent Client HTTP API
能力；本规范不为 API 尚未可用的升级中集群定义兼容执行路径。

### 2.5 gRPC Payload 与能力位

| Request | Response | 语义 |
|---|---|---|
| `AgentSearchRpcRequest` | `AgentSearchResponse` | Search 并返回目录分页 |
| `AgentDiscoveryRpcRequest` | `AgentDiscoveryResponse` | 一次 Discover |
| `AgentPublishRpcRequest` | `AgentPublishRpcResponse` | 代码式创建 Agent draft，并按 `autoSubmit` 可选执行普通 submit |
| `AgentEndpointRegisterRpcRequest` | `AgentEndpointOperationResponse` | 完整替换该 Connection 对一个 Agent 和 Protocol 的 RAD Batch |
| `AgentEndpointDeregisterRpcRequest` | `AgentEndpointOperationResponse` | 删除该 Connection 对一个 Agent 和 Protocol 的整份 Publication |

所有 Request 的 module 为 `ai`。gRPC Endpoint Contribution 归属于
`RequestMeta.connectionId`，不增加 Client id 或 heartbeat Payload。连接断开后删除该
Connection 的 Contribution；重连取得新 connection id，并 redo Endpoint。SDK 本地
轮询订阅不属于 Connection 维度的服务端状态。

`RpcRequest` 后缀用于区分 Nacos Payload Wrapper 与传输无关的 RAD 根消息。Search 和
Discover Wrapper 分别携带对应 RAD Request；Register 携带一个
`AgentEndpointRegistrationBatch`；Deregister 直接携带
`namespaceId + agentName + protocol`，不增加独立 Identity 对象，也不接受局部
Endpoint Key。

Endpoint Handler 是 Naming Adapter。Register 校验完整 Endpoint Batch，将其转换为
Naming Instance，再调用 Naming Batch Register；Deregister 调用 Naming 的整份
Publication 注销。写入时不读取或合并此前 Publisher Batch、不增加 Agent Service
Lock、不直接查询 Naming Client Index，也不扫描其他 Publisher。

Runtime Snapshot 和 Discover 从 Naming `ServiceStorage` 读取完整内部投影，
根据每个 Instance 的 singular runtime Version 和 Version-range metadata 构造一个 Binding，
保留 Range 命中目标 Version 的项，再按公开 Endpoint 自然键聚合 `bindings[]` 和健康状态。

本版本不定义 `AgentSubscribeRequest`、`AgentDiscoveryNotifyRequest`、`watchKey`、
Push ACK 或 Connection 维度的 Watch Redo State。轮询调度、完整结果缓存和变化去重是
Java SDK 本地实现，不扩展 RAD 的六个根消息。

目标能力位如下：

| 常量 | Wire key | 含义 |
|---|---|---|
| `SERVER_AGENT_DISCOVERY_V1` | `agentDiscoveryV1` | Server 接受 RAD Search 和 Discover Payload |
| `SERVER_AGENT_ENDPOINT_V1` | `agentEndpointV1` | Server 接受 RAD Endpoint Publication Payload |
| `SERVER_AGENT_PUBLISH_V1` | `agentPublishV1` | Server 接受通用 Agent 代码式发布 Payload |

旧 `SERVER_AGENT_REGISTRY`、`SERVER_AGENT_CARD_V1` 和 `SDK_AGENT_REGISTRY` 只约束
旧 A2A 契约。新能力位缺失时，不得通过旧能力位 fallback 发送 RAD Payload。

### 2.6 幂等与 Redo

| 事件 | 必须行为 |
|---|---|
| 重复相同 Register | 成功且语义不变 |
| Register 修改内容、Runtime Version 或 Range | 替换该 Publisher 的完整 Service Batch |
| 同一 Batch 出现重复自然键 | 拒绝整个 Batch |
| SDK 部分 Deregister | 从本地期望状态删除自然键，再 Register 完整剩余 Batch |
| SDK 注销最后一项或直接远程 Deregister | 删除该 Publisher 的整份 Service Publication |
| 重复整份 Publication Deregister | 成功且语义不变 |
| 重复 Publisher heartbeat | 刷新 Client 与 Publisher 活性，不修改 Publisher payload 或 revision |
| 携带已存在 Client id 的重复查询 | 只刷新 Client 活性，不创建 Client 或刷新 Publisher |
| HTTP timeout | 保持 Client id 和 payload，退避重试 |
| `HTTP_CLIENT_NOT_FOUND` | 将本地 Endpoint 意图标记为未注册，并 redo 每个完整 Service Batch |
| gRPC reconnect | 使用新 connection id redo 完整 Endpoint Batch；本地轮询订阅不需要服务端 redo |
| 跨传输注销 | 禁止；一个 Publisher identity 不能删除另一传输的 Contribution |

SDK 在第一次写入前记录期望状态，并按 Agent 和 Protocol 串行修改期望 Batch。
Shutdown 执行 best-effort 整份 Publication 注销，expire 作为清理兜底。参数和鉴权
错误不进入无限 redo。

## 3. Admin API 与 Maintainer SDK

Admin 读取不隐式执行数据面 Discover，也不把 Runtime Endpoint 注入 Version descriptor。

### 3.1 Agent 与读取视图

| Method | Path | 动作 | 返回 |
|---|---|---|---|
| GET | `/v3/admin/ai/agents` | 读取 Agent 和首个有界 Version Summary page | `Result<AgentOverview>` |
| PUT | `/v3/admin/ai/agents` | 通过共享 AI Resource 更新流程修改 Agent 可写字段 | `Result<Agent>` |
| DELETE | `/v3/admin/ai/agents` | 删除 Agent 定义及 Version 内容 | `Result<Void>` |
| GET | `/v3/admin/ai/agents/list` | 筛选和分页 Agent Summary | `Result<Page<AgentSummary>>` |
| GET | `/v3/admin/ai/agents/versions` | 分页读取 Version Summary | `Result<Page<AgentVersionSummary>>` |
| GET | `/v3/admin/ai/agents/version` | 读取一个精确 Version 定义 | `Result<AgentVersionDetail>` |
| GET | `/v3/admin/ai/agents/runtime-endpoints` | 读取一个 Protocol 的完整 Runtime Snapshot，可按 Version 过滤 | `Result<RuntimeEndpointSnapshot>` |

首版 Admin 列表复用共享 AI Resource 查询契约。`agentName` 是名称模糊过滤条件，
可选的 `bizTag` 是单个业务标签模糊过滤条件；该 Binding 不引入多标签 AND 匹配或
Agent 专用 collation 规则。`scope` 和 `owner` 是业务筛选条件，并与 Visibility Plugin
返回的可见性约束取交集后再执行稳定分页。首版不提供 `ai_resource.status` 列表过滤。

Admin 写入使用 `application/x-www-form-urlencoded`。身份、治理和生命周期字段使用
普通 form 参数。HTTP Form 包含 `namespaceId`，由 Form 生成的 Request 和 Command
对象不包含该字段。以下复杂字段使用 JSON 字符串：

- Agent 更新：`provider`、`tags` 和 `extensions`；
- draft 创建：`provider`、`tags`、`extensions` 和 `callInterfaces`；
- draft 更新：`callInterfaces`；
- label 更新：`labels`。

Form 大小复用 Nacos 统一 HTTP form-size 策略；序列化后的 AgentVersion 内容仍独立遵循
Agent 管理契约的容量限制。

Runtime 查询输入为 `namespaceId + agentName + protocol + version?`；`protocol` 必填。
省略 `version` 时，对该 Protocol 的每个 Endpoint 自然键返回一项及其全部 Binding；指定
`version` 时只保留匹配 Binding。
查询不应用 `endpointSourceOrder`，不要求定义存在，没有 Instance 时返回空 items。

不再提供独立的 `createAgent` 操作。`POST /draft` 是唯一创建入口：

- Agent 不存在时，在一个逻辑操作中创建 Agent metadata、首个 Version row 和 Storage
  内容。请求必须直接提交 `callInterfaces`，不得使用 `basedOnVersion`；可以提交
  `displayName`、`description`、`iconUrl`、`provider`、`tags` 和 `extensions`
  等可选展示 metadata。服务端初始化 `status=enable`，将 owner 初始化为当前调用者身份，
  并通过共享默认可见性规则初始化 scope；
- Agent 已存在时，从直接 `callInterfaces` 或一个精确 `basedOnVersion` 创建后续 draft。
  首建专用展示 metadata 必须被拒绝，不能静默忽略。

首建 Agent、Version row 与 Storage 写入具有一个逻辑原子结果，并补偿局部失败。
Agent Update 可以修改展示字段、tags、extensions 和 enabled 状态，但不能修改身份、
owner、scope、Version 内容、labels 或派生 catalog。owner 在首建时由服务端初始化，
首版不提供 owner 转移能力；scope 变更属于独立的公开/私有可见性操作，不进入通用
metadata CAS，首版 Agent API 暂不暴露该操作。删除定义会立即阻止普通发现，但不会
删除由独立 Publisher 拥有的 Runtime Publication。

### 3.2 Version 生命周期路径

| Method | Path | 转换或动作 | 返回 |
|---|---|---|---|
| POST | `/v3/admin/ai/agents/draft` | Agent 不存在时创建 Agent 和首个直接内容 draft；存在时创建后续直接内容或复制 draft | `Result<AgentVersionDetail>` |
| PUT | `/v3/admin/ai/agents/draft` | 覆盖当前精确 draft 的内容；不得创建缺失的 Agent 或 Version，也不修改 Agent metadata | `Result<AgentVersionDetail>` |
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

Agent metadata 更新与 Draft 内容更新是两类不同操作：`PUT /agents` 只更新
`ai_resource` 中的展示、目录和资源启停字段并推进 `metaVersion`，保留已有 owner 和
scope；`PUT /agents/draft` 只更新当前精确 Draft 的 CallInterface 内容、change
description 和 `contentDigest`。

### 3.3 Maintainer SDK

`AiMaintainerService.agent()` 返回 `AgentMaintainerService`；兼容期内继续保留
`AiMaintainerService.a2a()`。Agent Maintainer 接口一一映射 Admin HTTP，复杂写入使用
Request/Command 对象。它不绑定 namespace；每个操作同时提供显式 namespace 形式，以及
将省略 namespace 规范化为 `public` 的便利形式。Request 和 Command 对象不包含
`namespaceId`，方法参数是自定义 namespace 的唯一来源；不增加 Maintainer gRPC 传输。

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
4. Java SDK namespace 绑定、缓存、Discover 轮询订阅、重连和 Endpoint redo；
5. Admin/Maintainer 与 Console 契约；
6. 旧 A2A Facade 转换；
7. OpenAPI、Java SDK 和 Maintainer SDK 集成测试场景矩阵与 coverage registry。

旧 Console A2A API 支持到 Nacos 3.4 版本线；旧 Admin 和 Maintainer A2A API 保留到
Nacos 4.0 兼容边界。兼容期内，旧 A2A Endpoint API 保持当前带 Version 的 Naming
Layout 和替换范围，不改写到新的无 Version Agent Naming Service；旧 Client 无法构造
该 Service 要求的完整跨 Version Publisher Batch。历史数据迁移和混合版本滚动升级
属于独立规范，不得从本 API-only 契约推断。
