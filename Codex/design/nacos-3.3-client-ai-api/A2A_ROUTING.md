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

# A2A 与 RAD 模式决策细化

状态：2026-09-10 评审修订提案，未实现。补充 [总体设计](README.md) 和 [兼容 IT](COMPATIBILITY_IT.md)。

**已移至后续阶段。** 当前第一步只做接口委托、资源 transport 及测试，旧 A2A 固定现有 gRPC。本文件的 HTTP 能力入口、兼容能力位和协议选择不在第一步实施；当前计划见 [PHASE1_PLAN.md](PHASE1_PLAN.md)。

补充评审：[客户端转换边界](CLIENT_MAPPING.md)已将纯 DTO/状态转换与实际契约缺口分开。下文的“完整 A2A 适配”指 Client 与 Server 共同保持完整旧行为，不要求每个旧方法都有新兼容 RPC；`a2aCompatV1` 是待定的整体承诺，不能作为 URI、批量注销或来源顺序转换必须放到服务端的理由。

## 1. 结论与职责

采用“按目标和 transport 发现能力，按方法选择契约，由服务端处理迁移前置条件”的方式。

必须独立看待三件事：

| 判断 | 例子 | 负责方 |
| --- | --- | --- |
| 实现了什么契约 | RAD v1、Watch、完整 A2A 适配 | HTTP 能力发现 / 当前 gRPC 协商 |
| 当前能否到达 | HTTP 正常但 gRPC 端口未开放 | Client transport；不能据失败修改能力结论 |
| 这次操作当前是否合法 | 历史定义仍由 Legacy 管理、QUIESCING 禁止定义写 | 服务端已有兼容路由与 mutation guard |

不让 Client 根据 LEGACY/SYNCING/QUIESCING/CANONICAL 维护另一套迁移状态机；不引入一个同时代表“支持、联网、迁移就绪”的 `radEnabled` 布尔值。

公开接口仍为 `AgentService extends A2aService, AgentDiscoveryService`。**继承只提供方法集合，不向旧服务端补功能**：没有 RAD 的服务器只可使用其中的旧 A2A gRPC 方法，全部新发现/Watch/批量 publication/`publishAgent` 不可用；本地取消和 shutdown 仍应能清理存量状态。

## 2. gRPC 不可用时的 HTTP 能力发现

### 2.1 推荐增加一个小的 Client 能力入口

建议新增 `GET /v3/client/ai/capabilities`，复用 `Result<T>`，不依赖资源是否存在，不创建 Client/Publisher，不续租 publication，不扫描迁移数据。以下是拟议契约，不是现有 API：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "schemaVersion": 1,
    "capabilities": {
      "radV1": true,
      "radWatchV1": true,
      "a2aCompatV1": true
    }
  }
}
```

- 响应只声明**响应节点的 HTTP binding** 能力；不声明 gRPC 对当前客户端可达，不代表整个集群。
- `radV1`、`radWatchV1` 复用已有能力的契约定义和命名，但必须按真实 HTTP binding 过滤，不能直接返回完整 gRPC Ability Map。
- `a2aCompatV1` 是拟新增的完整 A2A 适配契约：旧 Card 投影、release、exactVersion publication、订阅与当前迁移阶段都已正确适配。gRPC 可增加对应 `SERVER_A2A_COMPAT_V1`。它与 `radV1` 分开，只有适配闭环完成才声明 true。
- `true` 是 SUPPORTED，`false` 是 NOT_SUPPORTED，缺字段、未知 schema 或不能解析为本契约均为 UNKNOWN；不要把缺字段解释成 false。允许忽略未知可选字段，不能猜测未知主 schema 版本。
- 不返回 `migrationState`、`radReady` 或所有资源的可写结论。静态契约支持不等于每个资源当前能写；业务操作仍可能因前置条件被拒绝。
- 使用 Client 身份策略，不要求 Admin 权限或任一 Agent 的读取权限。允许匿名 Client 的部署也需允许相应的能力发现；这必须成为新 HTTP API 的明确鉴权与 IT 契约。
- 路径与反向代理使用普通 Client API 相同的 context path、TLS 和 server list。不能访问 Console/Admin 端口作为必要依赖。

静态能力可复用既有 ability 定义和注册来源；首轮只覆盖 AI 需要的几个 flag，不建设全模块 discovery 框架，不增加公开 `AiService.getCapabilities()`。

### 2.2 用户给定场景

```text
gRPC 端口未开放
    -> 不把 gRPC 失败判定为无 RAD
    -> HTTP GET capabilities
       -> radV1=true：新 Agent API 可以走 HTTP
       -> 同时 a2aCompatV1=true：旧 A2A Java API 可走新 HTTP 适配 binding
       -> 只有 radV1=true：新 Agent API 可用，但完整旧 A2A 仍需旧 gRPC
```

`agent=http` 直接使用 HTTP，不先等 gRPC 超时；`agent=auto` 按既有连接策略尝试 gRPC，失败时对 HTTP 独立取证。`agent=grpc` 保持显式 GRPC 契约，端口不可达时报连接错误；HTTP 探测成功不会擅自覆盖用户要求的 transport，用户可选择 HTTP/AUTO。

### 2.3 能力入口不存在或被网关遮挡

HTTP 能力入口返回 404/405、HTML、超时或 5xx，只能说明“本次没有取得能力声明”，不能证明服务器没有 RAD。资源查询的业务 404 同样不能作为版本探测。

兼容已有 RAD HTTP 实现时，需要区分：

1. **调用的是新 Agent API**：方法本身已经指定 RAD 语义。如果没有能力入口，可以保持现有 Client 直接执行该方法的 HTTP 契约，按真实响应返回结果；这是用户要求的业务请求，不是用于探测的额外写请求。已明确声明不支持时则提前返回 unsupported。不能回退到旧 A2A。
2. **调用的是旧 A2A API，需要自动选新旧模式**：没有完整适配证据就不转换。若旧 gRPC 已建立且支持旧 A2A，使用原路径；否则返回能力未决/连接错误，并说明缺少可用兼容路径。不能用一次 RAD Search 成功证明旧 release 等价。
3. 能力请求的 401/403 应按认证/授权错误处理，不通过换模式或扩大权限绕过。不能因能力探测引入比该 Client 原功能更高的权限门槛。

成功且结构有效的 RAD 业务读响应可以作为**该操作**支持的正向证据，不能推导写、Watch 或旧 A2A 适配均受支持。普通 `OPTIONS`/`Allow`、健康检查和版本字符串都不能证明这些业务契约。

## 3. 方法族与模式决策

| 条件 | 新 Agent API | 旧 A2A API |
| --- | --- | --- |
| 选定 binding 有 RAD + 完整 A2A 适配 | 使用原 RAD 契约 | 使用新兼容 binding，保持全部旧语义 |
| 有 RAD，无完整 A2A 适配 | 使用原 RAD 契约 | 使用旧 A2A gRPC；无可用 gRPC 就报错 |
| 确认无 RAD，有旧 A2A gRPC | SERVER_NOT_IMPLEMENTED，不映射到旧 A2A | 使用旧 A2A gRPC |
| HTTP 能力未知，旧 gRPC 可用 | HTTP 保持原生调用兼容规则；gRPC 新操作仍严格能力检查 | 使用既有旧 A2A 路径，不宣称已经证明整个集群无 RAD |
| 能力未知且没有可用旧路径 | 返回真实请求/连接/能力未决错误 | 不转换、不试探写，返回明确错误 |

旧连接没有 Ability table 或新 key 时，UNKNOWN 的 legacy fallback 只用于旧 A2A，并沿用现有领域约定；不能让新 Agent gRPC Payload 绕过 `SERVER_RAD_V1` 的严格检查。

对于旧 A2A，第一次调用前根据选定目标选 binding；同一有状态 publication 后续操作保留协议和 owner。getter 调用不做全资源能力检查，不因为 AgentDiscovery 不可用而让 MCP、Skill 或整个 Factory 不可用。

## 4. gRPC 故障处理矩阵

| 情况 | 决策 |
| --- | --- |
| 尚未建连、端口拒绝、连接级 UNAVAILABLE | 不改变能力为 false；AUTO 可选独立验证的 HTTP；GRPC 返回连接错误并按原策略重连 |
| 已建立连接，缺 RAD key/明确不支持 | 该 gRPC binding 不使用新 RAD；旧 A2A 可按 legacy 契约执行；HTTP 能力独立判断 |
| TLS/证书错误、协议配置错误 | 返回配置/安全连接错误，不按“老服务器”处理，不自动降级到明文 HTTP |
| 认证/授权失败、参数错误、资源缺失、冲突、容量限制 | 返回原错误，不切换协议；鉴权失败不能靠换 transport 获得成功 |
| 基础 RAD 有，Watch 能力没有 | 可以依既有规则使用 HTTP Watch 或基于 RAD Discover 的有界轮询；无基础 RAD 时不能用旧 A2A 模拟 Watch |
| AUTO 安全读遭遇已识别的连接类失败 | 可走具有同等语义的 HTTP 路径；不因任意 SERVER_ERROR/5xx 就判定需切换 |
| 定义写/注册已发出，响应丢失或 deadline 超时 | 结果未知，保留既有幂等/redo机制；不因另一个 transport 可达就跨协议或跨 owner 重放 |
| 当前连接明确不支持某项请求 | 返回受控 unsupported；可使该目标的能力缓存失效重查，但不能把失败写请求自动转换成旧写 |

区分“不支持”和“暂时不可达”也是通用 RPC 原则。gRPC 的 `UNIMPLEMENTED` 与 `UNAVAILABLE` 含义不同，且不可用错误并不意味着非幂等操作能安全重试。[gRPC 状态码](https://grpc.io/docs/guides/status-codes/)

`NacosException.SERVER_NOT_IMPLEMENTED` 当前为 501；它只用于已确认不支持/既有严格 gRPC 能力规则，不用来包装网络故障。错误信息应包含调用方法、目标、选定协议/transport 和失败原因，避免只显示“server version too low”。

## 5. 迁移阶段的处理：服务端兼容，必要时明确报错

**推荐的完整适配契约在所有阶段都存在，不随迁移阶段翻转能力位。** 新 A2A binding 委托到现有 `A2aCompatibilityOperationService` 及 Endpoint 兼容/迁移路径，复用旧行为。新 wire 不意味着立即改为 canonical 数据权威。

| 服务端状态 | 旧 A2A 方法经兼容 binding | 新 Agent/RAD 方法 | Client 行为 |
| --- | --- | --- | --- |
| 真正的旧服务端，无 RAD | 仅既有 A2A gRPC | 不支持 | 新 API 报 unsupported；旧 API 照常 |
| 3.3 显式 LEGACY | 由服务端执行历史定义/Endpoint 契约 | RAD 仍可存在；不会自动看见只在历史存储中的定义 | 保持调用者选定的契约；不将 RAD not-found 转换为旧查询 |
| AUTO / SYNCING | 定义保持历史权威；Endpoint 使用既有镜像规则 | 标准资源可按当前事实读取；迁移投影可能尚未齐全；迁移来源资源的通用写受既有 guard 保护 | 旧调用继续；新调用不把暂缺投影当能力缺失 |
| AUTO / QUIESCING | 读取、Runtime 操作按现有规则继续；定义写被迁移写屏障拒绝 | 读取按当前标准事实执行；迁移来源定义 mutation 被拒绝；非迁移来源资源遵循正常契约 | 将迁移错误交给调用方，标明可稍后重试；不切协议绕过写屏障 |
| CANONICAL / 永久切流完成 | 服务端使用 canonical 兼容实现，仍保留旧 release/latest/版本 publisher 语义 | 标准 RAD 正常工作 | 无需新建 Client 或翻转全局模式；现有 owner 不迁移 |

因此没有必要在 LEGACY/SYNCING 期间对整个 `AgentService` 简单报错，也不应承诺所有操作都成功。**旧方法尽量由现有兼容服务处理；无法满足语义或被写屏障阻止的具体请求，明确报错。**

当前代码已经有两道关键保护：

- `A2aMigrationLegacyMutationGuard` 在 QUIESCING 拒绝历史定义写。
- `A2aMigrationAgentMutationGuard` 在非 CANONICAL 阶段拒绝修改 `from=legacy-a2a-migration-v1` 的标准资源，不是禁止所有普通 Agent 写。

迁移错误是 `AGENT_MIGRATION_IN_PROGRESS (50105)`，不同于不支持功能的 501。需要保留可机器识别的错误而非解析英文文案：旧 release gRPC 已专门保留 50105；当前 HTTP `resolveAgentResponse()` 对许多业务错误只保留 HTTP 状态，不能声称各 transport 已统一保留此 detail。若实施完整适配，仅在相关 binding 错误映射中补齐该 detail/旧 SDK 可观察错误约定，不改所有异常类型。新的 HTTP 旧 A2A 适配应让旧方法仍识别到 50105；通用 HTTP 路径可通过已有 `NacosApiException` 的 status/detail 区分两层信息。

Client 默认不为这类定义写新建无限重试队列；返回明确迁移错误。应用可按原操作的幂等约定稍后重试，已有 publication 的恢复继续交给原 manager。

### 5.1 “先探测再请求”的竞态

即使探测时是 CANONICAL、下一次请求落到尚未同步的节点，也必须由处理请求的节点检查本地权威状态、永久 Marker 和写屏障。能力探测只是选路依据，不是一次性写入授权；缓存 TTL 再短也不能取代服务端校验。

这也是不建议返回 `canWrite=true` 并让 Client 缓存的原因。无需为本轮额外引入分布式 capability lease、epoch 协议或新的迁移协调器。

## 6. 缓存、混合版本与有状态操作

1. gRPC 能力只属于当前 connection；重连/换节点即重新协商。HTTP 能力按 `(目标地址、context path、transport、客户端身份上下文)` 保存，不能把 A 节点的结果应用到 B 节点或不同凭据。
2. HTTP 使用小的内部缓存和一次在途探测合并，不加公开配置和独立轮询线程。建议正向结果最长 30 秒，明确 false 或“能力入口缺失”观察最多 5 秒；超时/401/403不缓存为不支持。期限只是缓存寿命，不代表契约授权。
3. 在实际选中的 HTTP server 上探测并执行；不能用现有自动轮询所有 server 的请求包装取到一个结果，却不知道它来自哪个节点。仅给能力读取补目标关联，不重写整个 HTTP 重试框架。
4. 在入口背后存在不透明负载均衡时，一个节点的能力响应不能证明所有后端支持。新 binding 发布阶段要求可达后端能力一致或有明确会话黏性；否则允许请求受控失败，不承诺自动迁移在任意混合池上都可用。服务端仍逐请求校验，Client 不做整个集群的能力求并集。
5. 基础 RAD 与完整 A2A 适配各自缓存；不能因 MCP/Skill 成功就激活 Agent 新模式。HTTP 成功不能替代 gRPC 协商，反之也不证明 HTTP 路由已暴露。
6. publication 首次提交前选定协议+transport，其替换、注销、心跳、redo保持 owner。旧 API 与新 API 形成的 publication 身份继续隔离。某次重连看到新能力，只影响可重新选择的无状态读/新 publication，不能把存量批次双写。
7. 存量订阅切换仍由原有 listener/watch manager 管理，必须保留 listener identity 与去重；本轮不承诺跨协议随时迁移旧 A2A 订阅。已有 route 可维持至取消/重订阅，关闭时无需重新探测能力。

## 7. 可借鉴的通用方法与本次取舍

| 方法 | 优点 / 局限 | 本次建议 |
| --- | --- | --- |
| 显式 capability discovery | 不依赖业务资源存在；能声明完整契约；需一个小的服务端入口 | 主方案，配合已有 gRPC Ability |
| 真实只读请求的正向证据 | 可兼容没有 discovery 的旧实现；只证明该读操作 | 保留给原生 RAD HTTP 兼容，不能证明旧 A2A 全量适配 |
| 业务响应附带 capability hint | 可在正常请求中刷新缓存，少一次探测；首次调用与写入前仍无证据 | 后续优化，本轮不增加统一响应头 |
| OPTIONS / Allow / OpenAPI / gRPC reflection | 可了解路由、方法或 schema；通常不能证明业务语义等价、动态写入条件或实际可达性 | 不作为切换依据 |
| 比较服务端版本 | 实现看似简单；灰度、回移植、模块关闭和网关配置均可使版本与能力不一致 | 只用于日志与运维诊断 |
| 显式 `auto/legacy/rad` 语义策略 | 能让运维固定选择，减少升级期不确定性；不能创造不支持的能力，配置矩阵也会扩大 | 可选后备方案，暂不加入 3.3 必需配置；与 transport 的 grpc/http/auto 绝不能混为一项 |
| 服务端统一兼容 facade | 客户端只选 wire；动态权威和迁移保护留在已有服务端 | 与 capability discovery 配合，是本轮最重要的简化 |

Kubernetes 的 Discovery API 也是让客户端读取服务端支持的资源、版本和操作，而不是只比较产品版本；这里借鉴能力发现原则，不引入其完整发现框架。[Kubernetes API discovery](https://kubernetes.io/docs/concepts/overview/kubernetes-api/#discovery-api)

HTTP OPTIONS 可描述通信选项，但 HTTP 没有为其定义统一的应用能力描述格式；仅凭 Allow 中出现 GET/POST 无法证明 RAD/A2A 语义兼容，这是针对本设计的推论。[RFC 9110 §9.3.7](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.3.7)

## 8. 实施范围和待定项

推荐本轮定稿：统一 `mcp()`、无 RAD 仅保留 A2A、HTTP 能力入口、能力/可达性分离、迁移决策放在服务端，以及对应 IT 场景。能力入口可以先上线，不依赖完整 A2A 适配完成；未完成时不要声明 `a2aCompatV1=true`。

仍需单独细化的是 **旧 release、隐藏兼容字段、共享连接下版本 owner 和迁移权威的最小支持**。可直接表达的 Endpoint/查询/监听转换优先留在 Client；多 owner 可选择 Client 独立真实身份或必要 binding 支持，不能提前断言所有转换都要新 wire。能力发现解决“如何决定”，不补齐这些契约。在完整适配完成前，基础 RAD 纯 HTTP 可用，但不能保证完整旧 A2A 纯 HTTP 可用。

相关源码依据：

- [ServerAbilities](../../../api/src/main/java/com/alibaba/nacos/api/ability/register/impl/ServerAbilities.java)、[能力协商规范](../../../specs/zh-cn/client/client-ability-negotiation-spec.md)。
- [AiHttpClientProxy](../../../client/src/main/java/com/alibaba/nacos/client/ai/remote/AiHttpClientProxy.java)、[AgentClientController](../../../ai/src/main/java/com/alibaba/nacos/ai/controller/AgentClientController.java)。
- [A2aCompatibilityModeResolver](../../../ai/src/main/java/com/alibaba/nacos/ai/service/a2a/A2aCompatibilityModeResolver.java)、[A2aCompatibilityOperationService](../../../ai/src/main/java/com/alibaba/nacos/ai/service/a2a/A2aCompatibilityOperationService.java)。
- [历史定义写保护](../../../ai/src/main/java/com/alibaba/nacos/ai/service/a2a/migration/A2aMigrationLegacyMutationGuard.java)、[迁移来源 Agent 写保护](../../../ai/src/main/java/com/alibaba/nacos/ai/service/a2a/migration/A2aMigrationAgentMutationGuard.java)、[旧 release 错误映射](../../../ai/src/main/java/com/alibaba/nacos/ai/remote/handler/a2a/ReleaseAgentCardRequestHandler.java)。
