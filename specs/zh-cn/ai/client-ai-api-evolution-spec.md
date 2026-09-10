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

# Nacos 3.3 Client AI API 规范修订提案

| 项目 | 值 |
| --- | --- |
| 状态 | 第一步接口/transport 已实现并同步主规范；第 3、4 节仍为待实施草案 |
| 更新 | 2026-09-10 |
| 范围 | 子服务入口、资源 transport、A2A/RAD 能力发现与兼容决策 |

本提案区分第一步已实现契约与后续兼容设计；后续条款不替代当前行为。详细设计见
[API 设计](../../../Codex/design/nacos-3.3-client-ai-api/README.md)、
[A2A 决策](../../../Codex/design/nacos-3.3-client-ai-api/A2A_ROUTING.md)和
[IT 矩阵](../../../Codex/design/nacos-3.3-client-ai-api/COMPATIBILITY_IT.md)。

## 0. 当前阶段约束

本次已收敛为第一步：接口委托、资源 transport 和对应 UT/Java SDK IT，细节见
[第一步计划](../../../Codex/design/nacos-3.3-client-ai-api/PHASE1_PLAN.md)。旧 A2A 在新旧服务端
上均固定现有 gRPC，与 agent/global 的 grpc/http/auto 设置无关；这些配置第一步只控制新 Agent/RAD。
getter 不触发连接，实际 A2A 调用保留 requireGrpcClient 的按需启动和重连需求。

第 3、4 节的 HTTP capabilities、完整兼容能力、A2A→RAD、版本 owner/旧字段/release 适配
全部留后续，不是第一步门禁。第一步不改服务端 API、迁移算法或旧 A2A 业务实现。
原生 HTTP 的缺功能响应保持现状，不引入探测或统一改写为 unsupported。

## 1. 对主规范的修订关系

| 主规范 | 拟替换或补充的条款 |
| --- | --- |
| [Java SDK 实现](../sdk/sdk-java-impl-spec.md) §5.3 | AiService 子入口与继承结构，保留已发布扁平 API 的 default 委托 |
| [SDK](../sdk/sdk-spec.md) §5 | MCP 入口统一为 mcp()，总 transport 增加资源 override |
| [Agent API](agent-api-spec.md) §2.1–2.2 | AgentService 承接 A2A 和 AgentDiscoveryService；无 RAD 时新能力不可用 |
| [客户端能力协商](../client/client-ability-negotiation-spec.md) | 补 HTTP binding 能力发现；区分能力、可达性及业务前置条件 |
| [A2A 兼容](a2a-agent-spec.md) | 新 binding 适配仍执行全部旧契约，迁移权威继续在服务端判断 |
| [HTTP API 范围](../http-api/v3-api-surface.md) | 待实施 Client capabilities 入口；尚未加入已实现路径清单 |

## 2. 接口和 transport 目标

`AiService` 提供 `mcp()`、`agent()`、`skill()`、`agentSpec()`、`prompt()`，并继续继承
`McpService`、`A2aService`、`SkillService`、`AgentSpecService`、`PromptService`。
这些资源接口从现有方法拆出；除 getter 外不增加业务方法。

`AgentService extends A2aService, AgentDiscoveryService`，接收既有 `publishAgent`。
`AiService` 不再继承 `AgentDiscoveryService`，也不继承 `AgentService`；3.3 未发布的新 Agent
扁平调用迁到 `agent()`。已发布扁平签名保留并标记 Deprecated，核心方法通过子入口委托，
便利 default 重载保持对旧核心 override 的分派。新增 getter 使用兼容 default，避免破坏第三方旧实现。

MCP 五参 createDraft default 保留原 false→旧四参 override、true→未实现者不支持的行为；
官方实现保留一个纯桥接 override 转到 mcp()，业务实现只在 delegate 中存在。

保留 `nacosAiTransportMode=grpc` 默认值和 `grpc/http/auto`。资源覆盖键为
`nacosAiMcpTransportMode`、`nacosAiAgentTransportMode`、`nacosAiSkillTransportMode`、
`nacosAiAgentSpecTransportMode`、`nacosAiPromptTransportMode`。
未设置时继承总值；全部显式值先校验；构造时冻结。

Skill/AgentSpec 已知没有 gRPC 实现的路径直接注入 HTTP proxy，保留原缓存、轮询、MD5 和事件语义。
第一步旧 A2A 在所有服务端均为固定旧 gRPC 例外；没有 RAD 时新 Agent
Search/Discover/Watch/publication/publish 不允许模拟为旧 API。显式 GRPC 的网络故障不等于
单 binding 能力退化，不自动改用 HTTP。

Prompt 的直接查询和订阅轮询共用薄路由代理；AUTO 根据当前连接选择，连接类失败可 HTTP，
不新增当前不存在的 Prompt 能力位。Agent/MCP/Prompt 各自记录 AUTO 状态，共享连接只有在
无强制 GRPC/旧 A2A 需求、未曾连接、失败达原阈值且已使用 AUTO 资源均 HTTP 成功时才可暂停
初始重连；未使用资源首次使用时恢复必要探测，曾连接后的断线恢复规则不变。

子服务共享既有连接、namespace、认证、HTTP 活性协调与关闭流程。资源模式不能互相覆盖，
一个资源的 HTTP 成功不能停止另一个强制 GRPC 资源的重连。有状态 publication 保留协议和 owner，
写入结果未知时不跨协议/transport 重放。

## 3. HTTP 能力发现目标（后续阶段）

拟增加 `GET /v3/client/ai/capabilities`，返回 `Result<T>`；data 包含
`schemaVersion=1` 和 `capabilities` 布尔 Map。首批键为 `radV1`、`radWatchV1`、
拟新增的 `a2aCompatV1`。只声明响应节点 HTTP binding 的实际支持，不声明整个集群或 gRPC 可达性。

`a2aCompatV1` 表示完整旧 A2A 语义已在新 binding 中适配，包括各迁移阶段；gRPC 使用对应的
拟议 `SERVER_A2A_COMPAT_V1`。只具备基础 RAD 的实现不得声明完整适配能力。
声明 true/false 分别为 SUPPORTED/NOT_SUPPORTED，字段缺失或声明不可解析为 UNKNOWN。

能力入口不依赖业务资源存在，不要求 Admin 或具体 Agent 权限；遵循明确的 Client 身份策略，
匿名 Client 策略需同步兼容。它不创建 Client/Publisher，不续租、不扫描数据，不输出迁移写许可。

404/405、网关页面、网络错误、401/403 不推导为“无 RAD”。无此入口的既有 RAD HTTP 实现，
仍可处理用户明确调用的新 Agent 方法；这是实际请求，不是写探测，也不允许转成旧 A2A。
旧方法的自动转换则必须具有完整适配证据，否则保留原 A2A 路径或受控失败。

能力按当前目标和 transport 保存；gRPC 重新连接必须刷新，HTTP 缓存有界并关联实际地址、
context path 和身份。单次响应不能代表不透明负载均衡池的全部后端；新 binding 应部署在能力一致
或有明确黏性的后端池，否则允许受控失败。业务处理节点仍需执行全部权威校验。

## 4. 迁移与错误目标（后续阶段）

基础能力是软件契约声明，不能将 `radV1` 与 CANONICAL 等同。完整 A2A 适配通过既有
`A2aCompatibilityOperationService` 及 Endpoint 兼容路径处理，不在 Client 复制迁移状态机：

- LEGACY/SYNCING：旧定义继续历史权威；Runtime 使用对应旧/镜像语义。新 RAD 读取标准当前事实，
  不承诺未完成迁移的历史定义已全部可见。
- QUIESCING：历史定义写拒绝，读取和 Runtime 按现有规则继续。迁移来源资源的通用写受原 guard
  保护；独立标准 Agent 不因这项规则被全部禁用。
- CANONICAL：服务端执行 canonical 兼容行为，仍保持旧 release/latest/多版本 publisher 的契约。

保留 `AGENT_MIGRATION_IN_PROGRESS=50105` 的机器可读含义，区别于
`SERVER_NOT_IMPLEMENTED=501` 和网络错误。现有 HTTP 错误映射尚未保证保留所有业务 detail，
实施时只补必要绑定的映射，不以文案解析决策，不因迁移冲突切协议，也不增加无限重试。

接口拆分、能力入口和完整 A2A 适配可分阶段交付。实际缺口对应的最小支持和 owner 生命周期
尚待定稿；完成前不得声称完整旧 A2A 已支持纯 HTTP 或开启完整适配能力位。

### 4.1 客户端转换优先与已核实的限制

本提案不要求每个旧方法新增兼容 RPC。详见
[客户端转换边界](../../../Codex/design/nacos-3.3-client-ai-api/CLIENT_MAPPING.md)：

- 旧 Endpoint 的 URI/TLS/path/query/transport 和 exact version 可转换为现有 RAD Endpoint/Batch。
  单条为完整单元素批次；旧批量完整覆盖；owner 隔离后旧注销可由 Client 清空完整版本意图。
- 多个 exact version 在一个标准 publisher 中不能同时独立保存：版本是 batch 内容而非身份。
  单纯修改 Client map key 或合并批次无效；可用独立真实连接/HTTP 身份解决，但会扩大生命周期
  管理，或在共享连接下补逻辑 owner 支持。此选择仍待定，不能声称任何 Client 方案都不可行。
- 未过滤来源的 endpointSets 保留顺序及空来源，可恢复旧转换定义的 registrationType；原设计中
  “空 Endpoint 时来源顺序丢失”的判断撤回。exact 查询的 latestVersion 可通过额外查询取得，
  但不保证两次读属于同一快照，必须约定竞态和失败行为。
- 旧 Endpoint 的 protocolVersion/tenant 目前只存在于保留 metadata，标准 RAD 不接受/返回
  这些键。新 SDK 私有普通 metadata 约定不能解决真实旧 SDK 双向互通；需最小映射/暴露支持
  或保留旧路径，不能从定义的 protocolVersion 或本地缓存伪造其他发布者的字段。
- 订阅回调及缺失恢复可复用 Client 原轮询与监听器；不要求改成 RAD Watch，完整 GET 字段仍是
  前提。旧保留字段不在 RAD 指纹中，不承诺其变化能通过原生 Watch 被感知。
- 旧 release 的直接 online、setAsLatest=false 和重复 online 无条件 no-op 无法由现有普通
  publish 完整表达；保留对应服务端写契约。迁移历史权威及 mirror/shadow 亦不能靠 DTO 转换补齐。

完整适配能力位只声明最终契约，不规定所有转换必须放在 Server。不得因本节澄清而修改通用 RAD
的版本范围、metadata、publish 或 publisher 语义；任何必要扩展应单独定稿并保持旧业务实现。

## 5. 验证门禁

第一步使用 PHASE1_PLAN 的 P01–P16：接口/default/旧字节码、混合资源模式、Skill/AgentSpec
HTTP 退化、旧 A2A 固定 gRPC、共享连接/owner/监听生命周期和必要旧 wire 回归。第一步没有新
HTTP API，不新增对应 OpenAPI 场景；更新 SDK 场景和覆盖表，使用默认及 Jackson 3 组合验证。

后续再使用完整 A/D 矩阵验证能力发现、A2A 新 binding、迁移竞态等；HTTP 能力接口实施时补
OpenAPI IT。所有新项在实施前保持 Pending，不修改已实现覆盖率。
