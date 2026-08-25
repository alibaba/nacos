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

# Nacos Java SDK 实现规范

本文档定义 Java SDK 如何实现共享的 [SDK 规范](./sdk-spec.md)，覆盖 Java
Client SDK 和 Java Maintainer SDK。

Java SDK 的 JSON 序列化兼容模型由
[Java SDK JSON 适配规范](./sdk-java-json-adapter-spec.md)定义。

## 1. 范围

Java SDK 当前包含两类公开能力：

- Java Client SDK，主要由 `nacos-client` artifact 和 `api` 模块中的公开
  interface 提供。
- Java Maintainer SDK，由 `nacos-maintainer-client` artifact 和
  `maintainer-client` 模块中的公开 interface 提供。

Java Client SDK 是现有运行时应用行为的基准。它的连接、server list、能力协商、
本地缓存和 redo 行为由[客户端运行时规范](../client/README.md)定义。Java Maintainer SDK
是管理、UI、网关和运维场景的推荐 Java 接入方式。

当公开 SDK interface、factory、模型、监听行为、生命周期行为或异常映射发生
变化时，必须按照[Java SDK 集成测试规范](../testing/java-sdk-integration-test-spec.md)
使用场景化 IT 验证 Java SDK 行为。

## 2. Java Client SDK Factory 和生命周期

| Interface | Factory | 生命周期关闭方法 |
| --- | --- | --- |
| `ConfigService` | `NacosFactory.createConfigService(...)` | `shutDown()` |
| `NamingService` | `NacosFactory.createNamingService(...)` | `shutDown()` |
| `AiService` | `AiFactory.createAiService(Properties)` | `shutdown()` |
| `LockService` | `NacosLockFactory.createLockService(Properties)` 或 `NacosFactory.createLockService(Properties)` | `shutdown()` |
| `NamingMaintainService` | `NacosFactory.createMaintainService(...)` | `shutDown()` |

`NamingMaintainService` 在 3.3.0 后已废弃。新的管理类接入应使用
`nacos-maintainer-client`。

一个 Java Client SDK 实例绑定一个命名空间。需要访问多个命名空间的应用应创建多个
Client SDK 实例，并在不再使用时关闭实例。公开运行时接口不暴露 namespace 参数，
实现使用构造时绑定的 namespace。该规则不适用于 Maintainer SDK：其 Agent 管理接口
不绑定 namespace，可显式传入 namespace，并提供使用 `public` 的默认 namespace 重载。
Agent 管理 Request 和 Command 对象不包含 namespace；显式方法参数是自定义 namespace
的唯一来源。

## 3. Java Client SDK 配置模型

Java Client SDK 配置由 `NacosClientProperties` 表达。

默认配置查找顺序为：

```text
Properties -> JVM system properties -> environment variables -> defaults
```

第一个查找来源可通过 `nacos.env.first` 或 `NACOS_ENV_FIRST` 调整。

常见配置项包括：

| 配置项 | 范围 | 含义 |
| --- | --- | --- |
| `serverAddr` | 通用 | Nacos Server 地址列表。 |
| `contextPath` | 通用 | 服务端 context path，默认 `nacos`。 |
| `endpoint` 及 endpoint 相关配置 | 通用 | 动态服务端地址接入点。 |
| `namespace` | 通用 | 当前 SDK 实例绑定的命名空间 id。 |
| `username`, `password` | 通用 | 开启鉴权时的登录凭据。 |
| `accessKey`, `secretKey`, `ramRoleName`, `signatureRegionId` | 通用 | RAM 风格鉴权参数。 |
| `configRequestTimeout` | config | Config RPC 请求超时覆盖值。 |
| `namingRequestTimeout` | naming | Naming RPC 请求超时覆盖值。 |
| `nacos.server.grpc.port.offset` | 连接 | Java 客户端使用的 gRPC 端口偏移。 |

已废弃的历史配置项应继续兼容，但新增代码不应依赖这些配置引入新行为。

## 4. Java Client SDK 扩展点

Java Client SDK 扩展点运行在应用进程内。它们从客户端 classpath 加载，或通过 SDK API
注册，并随所属 SDK 实例关闭。它们不受服务端插件 Admin API 控制。

| 扩展点 | SPI 或 API | 契约 |
| --- | --- | --- |
| 寻址 | `ServerListProvider` | 选择和刷新 HTTP 与 gRPC client 使用的 server list。内置实现支持固定 `serverAddr` 和动态 `endpoint` 模式。 |
| 鉴权 | `AbstractClientAuthService` / `ClientAuthService` | 为 `RequestResource` 生成 access token、RAM 签名或 OIDC bearer token 等请求身份材料。 |
| 配置 filter | `IConfigFilter` 和 `ConfigService#addConfigFilter` | 按稳定顺序拦截配置发布请求和查询响应。 |
| 配置加密 | `ConfigEncryptionFilter` 加 `EncryptionPluginService` | 当算法插件存在时，在发布前加密 `cipher-{algorithm}-` 配置，并在查询后解密匹配配置。 |

客户端扩展不得重新定义 Nacos 资源身份，也不得扩大 Client SDK 的能力面。扩展如果需要管理
访问，应使用 Maintainer SDK 或 Admin API，而不是向运行时客户端增加高权限操作。

寻址扩展必须返回 Java HTTP 和 gRPC client 可解析的地址，并在动态发现变化时发布 server
list change 事件。鉴权扩展必须使用 `RequestResource` 进行资源感知签名，而不是自行解析
传输 payload。配置 filter 必须保持请求和响应字段语义；当必需的加密插件缺失时，应显式
失败。

### 4.1 内置客户端鉴权服务

Java 客户端当前通过 SPI 注册以下 `AbstractClientAuthService` 实现：

| 实现 | 身份材料 | 契约 |
| --- | --- | --- |
| `NacosClientAuthServiceImpl` | `username`、`password` 和 `accessToken`。 | 与默认 Nacos 鉴权插件的登录 API 集成，并在 token 过期前刷新。 |
| `RamClientAuthServiceImpl` | `accessKey`、`secretKey`、`ramRoleName`、`signatureRegionId`。 | 按 [RAM 鉴权插件规范](../auth/ram-auth-plugin-spec.md)通过 `RequestResource` 生成资源感知的 RAM 风格签名。 |
| `OidcClientAuthServiceImpl` | OIDC client credentials 和 bearer token。 | 按 [OIDC 鉴权插件规范](../auth/oidc-auth-plugin-spec.md)在配置了 OIDC 属性时使用 OAuth2 client credentials flow。 |

Java 客户端会合并所有已加载客户端鉴权服务的 identity 输出。未配置的实现应返回空 identity
context，而不是修改请求 payload 或让无关 SDK 调用失败。默认 Nacos 鉴权插件只拥有 Nacos
用户名/密码和 token 流程；[RAM](../auth/ram-auth-plugin-spec.md) 和
[OIDC](../auth/oidc-auth-plugin-spec.md) 是客户端鉴权扩展，只有在当前服务端鉴权插件或部署侧
身份校验器接受对应身份材料时才会生效。

## 5. Java Client SDK Interface

### 5.1 ConfigService

| 能力 | 方法 | 契约 |
| --- | --- | --- |
| 查询配置 | `getConfig`, `getConfigWithResult` | 按 `dataId` 和 `group` 查询单个已知配置；`getConfigWithResult` 额外返回 md5，用于 CAS。 |
| 查询并监听 | `getConfigAndSignListener` | 查询当前配置，并注册同一个 listener 接收后续变更。 |
| 监听 | `addListener`, `removeListener` | 添加或移除监听器。回调应优先使用 listener 提供的 executor。 |
| 发布 | `publishConfig`, `publishConfigCas` | 用于创建或更新配置的兼容写入面。CAS 发布必须比较上一次 md5。 |
| 删除 | `removeConfig` | 用于删除配置的兼容写入面。用户文档定义删除不存在的配置也视为成功。 |
| Filter | `addConfigFilter` | 添加客户端侧配置 filter。 |
| 模糊订阅 | `fuzzyWatch`, `fuzzyWatchWithGroupKeys`, `cancelFuzzyWatch` | 按 group 或 dataId pattern 订阅配置 key，接收 key 变更事件。 |
| 状态/生命周期 | `getServerStatus`, `shutDown` | 查询状态并释放资源。 |

配置标识遵循用户文档中对 `dataId`、`group` 和配置内容大小的约束。新的大范围
配置管理 API 应加入 Maintainer SDK，而不是扩展 `ConfigService`。

### 5.2 NamingService

| 能力 | 方法 | 契约 |
| --- | --- | --- |
| 注册 | `registerInstance`, `batchRegisterInstance` | 在 service 和 group 下注册一个或多个实例。 |
| 注销 | `deregisterInstance`, `batchDeregisterInstance` | 移除一个或多个实例。 |
| 查询实例 | `getAllInstances`, `selectInstances`, `selectOneHealthyInstance` | 按 cluster、health、subscribe 等选项查询缓存或远端服务信息。 |
| 订阅 | `subscribe`, `unsubscribe` | 接收服务实例变化事件。取消订阅需要使用同一个 listener 实例。 |
| 模糊订阅 | `fuzzyWatch`, `fuzzyWatchWithServiceKeys`, `cancelFuzzyWatch` | 按 group 或 service pattern 订阅服务 key，接收服务级事件。 |
| 列举服务 | `getServicesOfServer` | 兼容性大范围查询面。新的大范围列举应使用 Maintainer SDK。 |
| 本地状态 | `getSubscribeServices`, `getServerStatus`, `shutDown` | 查询已订阅服务、状态并释放资源。 |

`getServicesOfServer` 的 selector overload 已废弃，仅作为兼容面保留。

### 5.3 AiService、AgentDiscoveryService 和 A2aService

本节的 Agent/RAD 契约是目标契约，不是当前已经实现的 Java 方法清单。只有新的
Agent/RAD 能力完成实现并经过协商后才生效；在此之前，现有 `AiService` 和
`A2aService` 方法仍是生效的兼容面。

目标继承关系为：

```text
AiService extends AgentDiscoveryService, A2aService
```

增加该父接口时，不能让已经编译的第三方 `AiService` 实现立即发生 linkage failure。新增的
继承方法使用兼容 default bridge，在实现未 override 时报告不支持；Nacos 官方实现 override
完整目标接口面。

`AiService` 直接提供 namespace-bound 的
`publishAgent(AgentPublishRequest)`，返回 `AgentVersionDetail`。该新增方法使用同样的兼容
default bridge；它不放入 `AgentDiscoveryService`，因为定义发布不是发现操作。官方实现复制
Request、注入 SDK namespace，并按 `autoSubmit` 创建 draft 或执行普通 submit Pipeline，且不
修改调用方对象。等价重试、冲突和状态收敛遵循 [Agent API 规范](../ai/agent-api-spec.md)。

`AgentTransportMode` 是 API 模块中的 Java 8 兼容枚举，公开 `GRPC`、`HTTP`、`AUTO`，并可通过
`getValue()` 写入 `nacosAiTransportMode`。模式在 `AiService` 创建时冻结；非法值在 Factory
创建阶段失败。Transport 生命周期、AUTO 探测与操作 fallback 的具体规则由
[Agent API 规范](../ai/agent-api-spec.md)定义。

`AgentDiscoveryService` 提供以下 namespace-bound 方法：

| 能力 | 方法 | 契约 |
| --- | --- | --- |
| Search | `searchAgents` | 接受 `AgentSearchRequest`，返回 `Page<AgentCatalogEntry>`。 |
| Discover | `discoverAgent` 重载 | 接受 `AgentReference` 和可选 `AgentDiscoveryFilter`，返回一个完整 `AgentDiscoveryResult`。 |
| Watch | `subscribeAgent` 重载 | 接受相同 Reference、可选 Filter 和 Listener；返回当前完整结果，后续传递完整替换结果。 |
| 取消 Watch | `unsubscribeAgent` 重载 | 按相同 Reference、Filter 和 Listener identity 移除 Watch。 |
| 注册 Endpoint | `registerAgentEndpoints` | 注册一个 `AgentEndpointRegistrationBatch`，并保留为 redo 意图。 |
| 注销 Endpoint | `deregisterAgentEndpoints` | 注销该 SDK Publisher 拥有的一个 `AgentEndpointDeregistrationBatch`。 |

这些公开方法不接受 `namespaceId`。Proxy 复制调用方的 Request 或 Batch，把 SDK
namespace 注入传输对象，并且不修改调用方对象。如果共享输入模型已经携带与 SDK namespace
不同的非空值，Proxy 在本地拒绝。目标 Watch、Cache 和 Redo 行为遵循
[客户端本地缓存与 Redo 规范](../client/client-local-cache-redo-spec.md)和
[运行时推送与重连规范](../client/runtime-push-reconnect-spec.md)。

继承的 `A2aService` 继续作为兼容 Facade。新的 Agent 应用使用
`AgentDiscoveryService`；现有 AgentCard 调用继续通过 A2A 兼容 Adapter 工作。

旧 A2A Endpoint redo 按 namespace-bound SDK 内的 `(agentName, exactVersion)` 区分意图，
并保存 Endpoint Payload 的防御性快照。旧 AgentCard 订阅必须同时正确处理 exact Version、latest
指针变化和取消后以已有 Cache 重新订阅；`shutdown()` 必须停止其轮询任务。Endpoint 可以先于
Agent 定义发布，且不得隐式创建定义。

资源语义由 [AI Registry 规范](../ai/ai-registry-spec.md)、
[Agent API 规范](../ai/agent-api-spec.md)、[RAD 协议规范](../ai/rad-protocol-spec.md)
以及各 AI 资源类型规范定义。当前已经实现的兼容方法包括：

| 能力 | 方法 | 契约 |
| --- | --- | --- |
| MCP 查询 | `getMcpServer` | 按名称和可选版本查询 MCP Server 详情。 |
| MCP 发布 | `releaseMcpServer` | 创建 MCP Server 或发布新版本。同版本已存在时保持幂等。 |
| MCP endpoint | `registerMcpServerEndpoint`, `deregisterMcpServerEndpoint` | 注册或移除当前客户端拥有的 endpoint。 |
| MCP 订阅 | `subscribeMcpServer`, `unsubscribeMcpServer` | 订阅 MCP 详情变化。 |
| A2A AgentCard 查询 | `getAgentCard` | 按名称、可选版本和 registration type 查询 AgentCard。 |
| A2A AgentCard 发布 | `releaseAgentCard` | 创建 AgentCard 或发布新版本；`setAsLatest` 只影响新版本。 |
| A2A endpoint | `registerAgentEndpoint`, `deregisterAgentEndpoint` | 注册或移除当前客户端拥有的 endpoint。批量注册会替换当前客户端此前为该 Agent 注册的 endpoints。 |
| A2A 订阅 | `subscribeAgentCard`, `unsubscribeAgentCard` | 订阅 AgentCard 变化。 |
| Skill | `downloadSkillZip`, `downloadSkillZipByVersion`, `downloadSkillZipByLabel` | 按 latest、版本或标签下载 Skill zip 字节。 |
| AgentSpec | `loadAgentSpec`, `subscribeAgentSpec`, `unsubscribeAgentSpec` | 加载组装后的 AgentSpec，并订阅其变化。 |
| Prompt | `getPrompt`, `getPromptByVersion`, `getPromptByLabel`, `subscribePrompt`, `unsubscribePrompt` | 按 key、版本或标签查询和订阅 Prompt。 |

当前 Java 实现在 interface 背后可以混合使用 gRPC、HTTP 和 config 组装。公开
interface 契约应独立于具体传输方式保持稳定。

### 5.4 LockService

`LockService` 是实验性运行时原语，其领域语义由[分布式锁规范](../lock/lock-spec.md)定义。

| 能力 | 方法 | 契约 |
| --- | --- | --- |
| 用户加锁 | `lock` | 通过 `LockInstance#lock` 获取锁。 |
| 用户解锁 | `unLock` | 通过 `LockInstance#unLock` 释放锁。 |
| 远程加锁 | `remoteTryLock` | 发送 gRPC lock operation 请求。 |
| 远程解锁 | `remoteReleaseLock` | 发送 gRPC unlock operation 请求。 |
| 生命周期 | `shutdown` | 释放客户端资源。 |

## 6. Java Maintainer SDK Factory 和生命周期

| Interface | Factory | 生命周期关闭方法 |
| --- | --- | --- |
| `ConfigMaintainerService` | `NacosMaintainerFactory.createConfigMaintainerService(...)` 或 `ConfigMaintainerFactory.createConfigMaintainerService(...)` | `close()` |
| `NamingMaintainerService` | `NamingMaintainerFactory.createNamingMaintainerService(...)` | `close()` |
| `AiMaintainerService` | `AiMaintainerFactory.createAiMaintainerService(...)` | 当前 interface 未暴露 |

Maintainer service 在适用场景下继承 `CoreMaintainerService`。它们属于高权限
客户端，应使用管理类凭据进行配置。

## 7. Java Maintainer SDK Interface

### 7.1 CoreMaintainerService

`CoreMaintainerService` 暴露服务端和集群维护能力：

- 服务端状态、liveness、readiness、ID 生成器状态和 loader metrics；
- 日志级别更新；
- 集群节点列表和 lookup mode 更新；
- 当前客户端连接查看和客户端 reload 操作；
- 命名空间列表、查询、创建、更新、删除和存在性检查；
- 面向管理场景的 raft operation 转发。

这些 API 本质上属于管理能力，不应复制到 Client SDK。

### 7.2 ConfigMaintainerService

`ConfigMaintainerService` 包含：

- 配置获取、发布、删除和按 namespace 限定的批量删除；
- 按 namespace、dataId、group、type、tag、app 等条件进行配置列表和搜索；
- clone、import/export 等管理模型；
- 通过 `BetaConfigMaintainerService` 提供 beta 和灰度发布能力；
- 通过 `ConfigHistoryMaintainerService` 提供历史查询和回滚相关访问；
- 通过 `ConfigOpsMaintainerService` 提供 dump、listener、log 和操作端点；
- 配置描述、标签等元数据更新。

管理类写入和大范围查询应加入这里，而不是继续扩展 `ConfigService`。
按存储 ID 批量删除必须显式传入或默认出 namespace；未传 namespace 的便捷方法只表示默认
namespace，不表示跨 namespace 全局删除。
按存储 ID 克隆必须显式传入或默认出源 namespace 和目标 namespace。旧的单 namespace 克隆方法只表示
同 namespace 克隆，不表示按 ID 跨 namespace 读取源配置。
Maintainer SDK 中暴露存储 ID 选择器的方法，例如批量删除中的 `ids`，属于兼容方法并待移除。
新的 maintainer 契约应按 `namespaceId`、`groupName`、`dataId`，或这些身份元组的显式列表选择配置。

### 7.3 NamingMaintainerService

`NamingMaintainerService` 包含：

- 服务创建、更新、删除、详情查询和列表查询；
- 实例注册、注销、更新、列表和元数据维护；
- 通过 `NamingClientMaintainerService` 提供订阅者和客户端查询；
- 注册中心 metrics 和日志级别操作；
- 持久化实例健康状态更新；
- 健康检查器列表和集群元数据更新。

运行时实例注册仍可保留在 `NamingService` 中，但服务管理、大范围列表、订阅者
查看和健康检查维护属于 Maintainer SDK。

### 7.4 AiMaintainerService

`AiMaintainerService` 暴露类型化 delegate：

- `mcp()`：MCP Server 列表、搜索、详情、创建、更新和删除；
- `a2a()`：AgentCard 注册、查询、更新、删除、版本、搜索和列表；
- `prompt()`：Prompt 管理；
- `skill()`：Skill 管理；
- `agentSpec()`：AgentSpec 管理；
- `pipeline()`：Pipeline 管理。

Agent 管理委托为 `agent()`，返回 `AgentMaintainerService`，并与 Agent Admin HTTP
API 一一映射。实例不绑定 namespace；各操作提供显式 namespace 形式，以及使用默认
namespace `public` 的便利重载。Agent Request 和 Command 对象不包含 `namespaceId`；
显式重载将其作为独立方法参数。Agent 定义统一通过 `createDraft` 创建：首个 draft
在 metadata 不存在时创建 Agent，后续 draft 复用已有 metadata。`a2a()` 在兼容窗口内
继续保留。

运行时 AI 注册和订阅可以继续保留在 `AiService`；大范围 AI 资源管理属于
`AiMaintainerService`。

## 8. Java 兼容规则

- `api`、`client` 和 `plugin` 模块保持 Java 8 兼容，除非模块策略发生变化。
- Java SDK 的 JSON 序列化与反序列化必须通过
  [Java SDK JSON 适配规范](./sdk-java-json-adapter-spec.md)定义的中立 JSON
  adapter 模型。新的公开 SDK API 不得暴露具体 Jackson core/databind 类型。
- 服务端和 maintainer 模块遵循仓库 Java 版本策略。
- Client SDK 和 Maintainer SDK 的 service interface（`XxxService`）新增 API 方法
  时，必须添加 `@Since`，声明该方法起始支持的 Nacos 版本号。
- 已废弃的 Client SDK 方法应尽量保持二进制兼容，但新的设计应引导调用方使用
  Maintainer SDK。
- 公开模型变更应尽量保持源码和二进制兼容，尤其是 HTTP 和 gRPC API 共享的对象。

## 9. 文档参考

- Java Client SDK 用户文档：Nacos 文档项目中的
  `src/content/docs/next/zh-cn/manual/user/java-sdk`。
- Java Maintainer SDK 用户文档：Nacos 文档项目中的
  `src/content/docs/next/zh-cn/manual/admin/maintainer-sdk.md`。
