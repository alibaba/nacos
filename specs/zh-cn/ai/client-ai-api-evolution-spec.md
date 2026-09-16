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
| 状态 | 第一步接口/transport 已实现并同步主规范；第 3、4、6 节仍为待实施草案 |
| 更新 | 2026-09-11 |
| 范围 | 子服务入口、资源 transport、A2A/RAD 能力发现与兼容决策、Agent/RAD Java 模型收敛 |

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

## 6. Agent / RAD Java 模型收敛提案

本节记录已确认并在 2026-09-11 本地试改版实现的模型契约：统一 agent 包、RAD 定义优先和
abstract 基础层。完整 43 文件清单、改造结论和 M01–M15 验证计划见
[模型收敛设计](../../../Codex/design/nacos-3.3-client-ai-api/MODEL_CONSOLIDATION.md)。
用户确认本轮可以忽略 `3.3.0-BETA` 的 Java 模型兼容，不保留旧包别名或兼容壳。
已发布旧 A2A 契约及现有 HTTP/gRPC、存储格式仍受保护。

### 6.1 统一模型与抽象基础层

将现有 `model.rad` 的全部具体模型/枚举迁入 `com.alibaba.nacos.api.ai.model.agent`，
不保留两个并列的 Agent 模型包。只用于字段复用的底层模型放入 `model.agent.base`，使用
`public abstract class AbstractAgent…`，构造器为 protected。
跨 Agent/MCP 共用的 ClientLivenessInfo 移到 AI 公共 model 包，既有 RPC 信封仍在 remote 包。

共享基础类及字段分配为：

| 抽象类 | 自身声明的字段 | 复用关系 |
| --- | --- | --- |
| AbstractAgentMetadata | agentName/displayName/description/iconUrl/provider/tags | 管理摘要、RAD CatalogEntry、Admin 元数据更新；草稿基类继承它 |
| AbstractAgentSearchRequest | 五个 Search 条件及分页字段，无 namespace | Client Search 与完整 RAD Search 为并列具体子类 |
| AbstractAgentEndpointRequest | agentName/protocol/endpoints | 注销具体请求与注册基类复用；不共享注册/注销的操作校验 |
| AbstractAgentEndpointRegistrationRequest | runtimeVersion/versionRange | 继承 Endpoint 请求基类，供 Client 注册和 RAD RegistrationBatch 使用 |
| AbstractAgentDraftRequest | extensions/version/callInterfaces/author/changeDescription/basedOnVersion | 继承元数据基类，供 Admin 草稿创建和 Client publish 使用 |

公共基类可引用稳定值对象，不能依赖 Client/Admin 专用请求，不引入独立 Maven module。
基类共享同义字段和访问方法，不拥有鉴权、namespace 默认、状态转换、transport、缓存、redo。
具体操作继续调用原校验规则；相同公共校验可以复用，不因父类复用放宽上下文约束。

公开 SDK 参数/返回值、DTO 成员与列表元素必须使用具体业务类型，不暴露 AbstractAgent…
或抽象元素列表，不添加 JsonTypeInfo、类型判别字段或多态构造工厂。
从固定 JSON 反序列化具体类型时，应自然绑定其所有继承属性。
具有独立返回/值对象含义的 AgentSummary、AgentVersionSummary、Endpoint 保留具体类；
不为每一两个相同字段再引入身份、版本、namespace 等通用基类。

### 6.2 具体命名与继承方向

初版试改按 RAD 概念保留 AgentCatalogVersion、删除 AgentVersionCatalogEntry；
本次资源/版本整合以 §6.5 为准，统一使用 AgentVersionSummary。初版的其他合并为：
管理/存储的目录容器也使用前者，同时保留各自的校验和 JSON 结构。
后续地址模型统一替代两个 CallInterface 并列子类：共用 AgentCallInterface → EndpointSet → Endpoint，
字段按查询上下文约束；完整管理详情与发现结果不直接相互继承。

Client 专用请求拟统一为 AgentSearchClientRequest、AgentEndpointRegistrationClientRequest、
AgentEndpointDeregistrationClientRequest、AgentPublishClientRequest。
Admin 专用请求拟统一为 AgentDraftCreateAdminRequest、AgentDraftUpdateAdminRequest、
AgentUpdateAdminRequest、AgentLabelsUpdateAdminRequest、AgentVersionAdminRequest。
RAD AgentSearchRequest、DiscoveryRequest、Endpoint Batch 等名称保留。
不为没有独立边界的操作机械增加成对空包装类。

Client 与 RAD Search 请求分别继承 AbstractAgentSearchRequest；只有完整 RAD 请求补 namespaceId。
Endpoint 注册/注销采用同一并列关系。SDK 只接受具体 ClientRequest，复制业务内容后注入实例
namespace；不能改为接收抽象基类或直接接受完整 RAD 请求。
Client publish 与 Admin 草稿创建分别继承公共草稿基类，不让 Client 继承 Admin 专用请求。
Maintainer 的 namespace 继续由显式方法参数给出，HTTP Form 独立承担字符串解析和参数绑定。

### 6.3 协议、领域与验证边界

RAD 是共享概念及发现契约的基准，不包含完整管理生命周期。Admin 状态操作可组合/继承公共
对象，但不能把所有 Admin API 建立在完整 RAD 根请求或在线发现视图之上。
协议优先不要求每个 Schema 概念都拥有独立 Java 类，也不意味着重写已有版本化 Schema。

保持 JSON 属性、层级、可选/缺省值、枚举、RPC 信封类型、错误码和 Endpoint 发布语义。
管理目录 labels 必须为数组（允许空），RAD 允许省略；公共类型不消除上下文校验。
具体管理摘要仍按有界投影构造，不用实际详情实例强转；版本列表不额外读取 AI Storage。
当前 Discover/publish 返回的 namespace 字段保持，不能通过共享模型的 JsonIgnore 隐藏。
保留显式存储投影、字节、摘要、sourceRevision、Watch fingerprint 和深拷贝。
本轮不改变 A2A/MCP/Skill 业务实现、迁移、transport 路由、Watch 或 redo 算法。

新增 M15 验证 base 类型均为 abstract、构造受保护、公开 API/DTO 使用具体类型，以及具体模型
无需多态 discriminator 就能反序列化。M01–M14 继续覆盖继承属性、namespace 隔离、旧 JSON、
目录规则、有界摘要、存储向量、默认/Jackson 3、Client/Maintainer/OpenAPI 的真实场景。
新增项在执行前保持 Pending，实施时同步场景/覆盖登记，不扩展真实故障注入范围。
方案采纳与实现时同步双语 Java SDK 实现、Agent API、Agent 管理和 RAD 主规范的 Java 绑定映射。

### 6.4 后续评审修订：三层发现模型（待实施）

§6.1–6.3 记录已有本地试改，本节记录后续确认的模型简化目标，尚未改变现有 Wire 或运行行为。
资源信息合并后统一使用 AgentSummary；版本元数据收敛为
`AgentSummary.versionInfo: AgentVersionInfo → onlineVersions[]: AgentVersionSummary`。
查询场景继续约束返回字段，Client 用户构造的入参仍不暴露 namespace。

公共发现结果主干为 `AgentDiscoveryResult → callInterfaces[]: AgentCallInterface → endpoints[]: Endpoint`。
不插入 versions[] 或 EndpointSet 导航层。固定与 Runtime 地址共用 Endpoint，由 source 属性
表达 DECLARED/RUNTIME；不同查询入口不再产生不同的公开 CallInterface 或 Endpoint 类型。
VersionDetail 先以只返回固定地址的方案评估，Runtime 获取方式另行评估；不强制增加管理聚合查询，
也不将实时地址写入版本存储或 contentDigest。

来源顺序、空来源及 sourceRevision 的字段承载和内部 Wire 映射仍待设计，不能因公开结构压平
而丢失原契约信息。实施前明确是保留 Wire 适配还是同步修改 Schema，并更新相应双语规范与
SDK/OpenAPI 场景；目前不将该目标描述成已实现的 HTTP/gRPC 结构。

默认 Discover 受 latest 的协议定义、来源顺序和固定地址限制的问题记录为 MODEL-D01，
详见[关系图 §12](../../../Codex/design/nacos-3.3-client-ai-api/MODEL_RELATIONSHIPS.md)。
后续再讨论全部在线版本 Endpoint 的覆盖、descriptor 归属、去重及 Watch 依赖；
本次模型简化不顺带修改跨版本发现算法，也不通过新增结果层级预先解决该问题。

### 6.5 本轮实施：资源摘要与版本信息合并

本节是资源与版本元数据的现行合并规则，替代本章前面初版试改中对应的类型拆分；其他请求和协议模型的约束继续有效。

本轮只实施 Agent/版本元数据的收敛，CallInterface、Endpoint 和 MODEL-D01 保持现状。
删除公开 Agent、AgentCatalogEntry、AgentVersionCatalog、AgentCatalogVersion；资源统一为
AgentSummary（包含可选 extensions），版本集合统一为 AgentVersionInfo，单版本条目复用
AgentVersionSummary（增加 protocols、labels）。AgentVersionDetail 保留为包含协议内容的详情。

公开 JSON 使用 versionInfo，其中包含 editingVersion、reviewingVersion、labels 和 onlineVersions。
latest 从 labels["latest"] 派生，在线数量从 onlineVersions 派生，不再存放两套公共字段。
Search 同样返回 AgentSummary，但省略 namespace、管理字段、extensions、editing/reviewing；
其标签映射只包含在线版本指向。列表投影省略 extensions，详情/更新结果按原规则返回。

本次调整对应的 Search/Admin/Console JSON 结构及 Java 泛型，不增加旧 BETA 公开模型壳。
持久化仍使用原 version_info 和 ext.versionCatalog 的显式投影，保留原 schemaVersion、
字段格式和版本内容 bytes；读取时校验旧字段一致性后组装新模型。发现选择器、地址、Watch、
A2A、transport 及发布算法不变。UT/IT 必须验证新响应结构、字段边界、完整标签、旧存储读取
及派生目录一致性；本次新增执行结果单独记录，不沿用上一轮验证结论。

### 6.6 请求分包补充提案（2026-09-15）

本节是请求模型调用关系核查后的分包提案，尚未实施 Java 迁包或改名；实施时替代 §6.2
对应命名，并同步 Agent API、Java SDK 实现规范及相关 IT 场景/覆盖登记。完整调用方、
例外、校验和验证阶段见[请求分包核查](../../../Codex/design/nacos-3.3-client-ai-api/MODEL_REQUEST_PACKAGES.md)。

保持 `nacos-api` 模块，根包 `model.agent` 保留 RAD 协议、共享值对象及结果模型；
共享抽象类继续位于 `model.agent.base`。将 Admin/Client 区别放在 package 中：

- `model.agent.admin`：AgentDraftCreateRequest、AgentDraftUpdateRequest、AgentUpdateRequest、
  AgentLabelsUpdateRequest、AgentVersionRequest。
- `model.agent.client`：AgentPublishRequest、AgentSearchRequest、AgentEndpointRegistrationRequest、
  AgentEndpointDeregistrationRequest。

这些类分别对应现有同名 AdminRequest/ClientRequest，去掉场景后缀，不新增成对包装。
根包 AgentSearchRequest 仍是含 namespace 的完整 RAD 请求；client.AgentSearchRequest
仍不包含 namespace，两者保持抽象基类的并列子类。转换处显式区分全限定类型。
Admin 请求仍由 Maintainer、Console、服务端共享；A2A 内部定义转换可继续复用草稿输入。
HTTP Form 并不与每个 SDK 请求直接映射，特别是 Client 局部注销仍先计算剩余完整集合。

复用优先调整已有基类：extensions 从三个直接子类上移到 AbstractAgentMetadata；
Admin 创建/Client 发布的相同草稿校验移到 AbstractAgentDraftRequest。不新增身份/版本基类，
不改变具体操作的字段边界。包内 AgentAdminRequestUtils 的访问必须随迁包处理，不能公开
该工具来代替正确的校验归属。

AgentEndpointDeregistrationBatch 是 SDK 内部带 namespace 的删除意图，服务端并不接收它。
其内收 client 实现模块作为独立后续步骤，须同步专用校验，禁止 api 反向依赖 client。

本提案只调整 Java 类型归属与共享声明，不改变 HTTP JSON、RPC 信封名、namespace 绑定、
生命周期、局部注销、存储摘要或 RAD revision。Java 调用方需更新 import 并重新编译；
按已有约束不增加 BETA 兼容壳，历史 A2A 公开契约继续保护。验证在实际执行前保持 Pending。

### 6.7 请求合并与 namespace 上下文（当前实现）

本节替代 §6.1、6.2、6.6 中初版请求分层；前文保留设计演进记录。

Java 模型以 `com.alibaba.nacos.api.ai.model.agent` 为根包。RAD 通用模型、Search 和
RegistrationBatch 留在根包；五个管理请求放到 `agent.admin`，名称为
`AgentDraftCreateRequest`、`AgentDraftUpdateRequest`、`AgentUpdateRequest`、
`AgentLabelsUpdateRequest`、`AgentVersionRequest`；发布请求为 `agent.client.AgentPublishRequest`。
`agent.base` 只保留 `AbstractAgentMetadata` 和 `AbstractAgentDraftRequest`，均为 abstract，
构造器为 protected。Metadata 共享元数据及 extensions；Draft 共享版本定义字段和草稿校验。
Client 发布与 Admin 草稿创建为并列具体子类，公开 API 使用具体类型。
共享校验集中在 `com.alibaba.nacos.api.ai.utils.AgentValidationUtils`，不在 model 内维护工具类。
Form 独立承担 HTTP 字符串解析；Admin 模型仍供 Maintainer SDK、Console 和服务端使用，
namespace 来自 Form 或显式方法参数。JSON 转换使用 `JsonUtils`/`NacosTypeReference`。

Search 和完整注册分别使用根包 `AgentSearchRequest`、`AgentEndpointRegistrationBatch`，
只包含业务字段，不含 namespace 字段或访问器。局部注销使用
`deregisterAgentEndpoints(String agentName, String protocol, List<Endpoint> endpoints)`，
不再定义注销 Java Request/Batch。SDK 对调用方内容做防御性复制，从实例取得 namespace，
通过 HTTP 参数或 RPC 信封显式传入查询/注册服务；PublicationKey 和 redo 数据独立保留 namespace。
局部注销仍计算剩余完整 Batch，非空则重新注册，为空则整份注销，不修改调用方对象或集合。
HTTP 参数、鉴权、完整替换和错误语义保持不变；Search/Register 的 RPC namespace 位于信封，
不再嵌套于业务请求。3.3 BETA Java 类型不保留兼容包装，历史 A2A 公开契约保持不变。

完整 RAD 逻辑 Schema 的 namespace 要求保持不变；Java 模型与上下文一起构成完整请求。
验证结果单独记录，尚未执行的矩阵项不作为通过证据。
