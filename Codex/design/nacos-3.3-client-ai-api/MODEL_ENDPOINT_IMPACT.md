# RAD 三层模型复用：二次影响面核查

核查日期：2026-09-14。基于 `codex/agent-model-consolidation` 当前未提交工作区，
不是 upstream/develop。已完成的 AgentSummary/版本摘要合并保持不动。
本次是源码、JSON 字段、Schema 引用和现有测试的静态核查，没有实施本轮模型变更或执行测试。

> 最新范围澄清：3.3 BETA 不考虑升级与旧存储兼容。本文件前文的旧存储保留/迁移选项不再作为
> 实施要求，最新存储结论见 §12。后续已确认 healthy 可写、Nacos 维护字段输入时忽略；
> 这一规则替代前文的只读字段拒绝建议。统一结论和完整测试门槛见
> [地址模型测试方案](MODEL_ENDPOINT_TEST_PLAN.md)。类型引用清单仍有效，内部引用和导出联动不代表设计阻塞。

基准方向：管理定义查询、管理运行查询、Discover/Watch 共用
`AgentCallInterface → EndpointSet → Endpoint`。管理查询仍分两个 API；RAD 发现层级和语义保持。
外层 Runtime 查询采用单个 CallInterface 还是列表，以及管理字段最终位置，仍属于待定细节。

## 1. 核查方法与结论范围

同时核对 tracked 与 untracked 的当前文件，排除已删除文件、target、node_modules 和生成产物：

- 第一轮追踪 CallInterface、Endpoint、EndpointSet、Snapshot 的实际 Java 类型及字段。
- 第二轮追踪包含它们的请求、返回值、继承类、RPC 信封、事件、Forms、Console 与导出链路。
- 补查 `declaredEndpoints`、`endpointSets`、`endpointSourceOrder`、`runtimeEndpointSnapshot` 等字符串，
  以及 JSON Schema 的 `$ref`，避免只修复 import 和泛型。
- 对照现有 UT/IT 的具体断言与条件跳过，区分已有行为覆盖和未来新结构需要补充的断言。

下文“直接调整”指当前方向落地时的类型/结构影响；“联动”不代表必须修改该文件的字段或方法签名。
调用方数量不能等同于最终修改文件数量。本次没有把词法命中数作为代码改动规模。

## 2. 直接收敛的 8 个现有模型

路径均相对于 `api/src/main/java/com/alibaba/nacos/api/ai/model/agent/`。

| 当前模型 | 收敛方向 | 必须同步检查 |
| --- | --- | --- |
| `AgentDefinitionCallInterface` | 与发现侧合为 `AgentCallInterface`；声明地址改由 DECLARED EndpointSet 承载 | 完整 endpointSourceOrder 仍是定义配置；只有声明结果不能覆盖来源策略 |
| `AgentDiscoveryCallInterface` | 合入同一个 `AgentCallInterface` | RAD 的四个协议字段、endpointSets 顺序和空集合行为保持 |
| `base.AbstractAgentCallInterface` | 两个具体类合一后可移除，仅为一个子类保留父类已无复用收益 | 不再引入一套等价的抽象/具体类型层级 |
| `Endpoint` | 成为统一地址类型；承接 bindings 及最终选定的管理字段 | 定义、注册、注销、发现、管理各自允许的字段必须明确 |
| `AgentDiscoveryEndpoint` | bindings 合入 Endpoint 后移除 | DECLARED 禁止 bindings；RUNTIME 发现要求非空 bindings；不丢失深拷贝字段 |
| `EndpointSet` | 保留；集合元素改为统一 Endpoint | source、revision、空 Set、排序和范围校验；管理 revision 不直接套用发现投影 |
| `RuntimeEndpointSnapshot` | 根身份可保留；把 items 地址路径换成统一的 CallInterface/EndpointSet 路径 | protocol 必填、version 可选、无定义/无实例也能查询；根结构最终形态待定 |
| `RuntimeEndpointSnapshotItem` | 退出公开结果结构；地址、bindings 与管理状态归入统一模型 | **内部 Runtime mapper、聚合和旧 A2A 迁移 comparator 也使用它**，不能只删除管理返回字段 |

按“删除两个 CallInterface、一个父类、DiscoveryEndpoint、SnapshotItem，新增 AgentCallInterface”
且不增加其他模型的最小方案计算，可净减少 4 个 Java 文件。这个数字是条件估算，管理状态字段
归属尚未定案，不能提前当作实施后的模型数量。

## 3. 直接持有与继承联动的模型

### 3.1 另有 4 个字段声明需要调整

| 模型 | 当前包含关系 | 影响 |
| --- | --- | --- |
| `AgentVersionDetail` | `List<AgentDefinitionCallInterface> callInterfaces` | 管理 Version 结果改用统一 CallInterface；版本元数据保持 |
| `AgentDiscoveryResult` | `List<AgentDiscoveryCallInterface> callInterfaces` | Java 泛型/访问类型改变，目标是保持 RAD JSON 内容和层级 |
| `base.AbstractAgentDraftRequest` | `List<AgentDefinitionCallInterface> callInterfaces` | **读模型同时也是写模型依赖**；直接影响 Client 发布、Admin/Console 草稿创建 |
| `AgentDraftUpdateAdminRequest` | 同上 | 草稿更新 JSON、构造与校验同步；不能只改查询输出 |

### 3.2 2 个请求继承上述变化，不需要另造请求类

`AgentDraftCreateAdminRequest`、`AgentPublishClientRequest` 都继承 AbstractAgentDraftRequest。
即使自身文件无需改动，其公开可构造对象、JSON 输入和字段校验也已受到影响。
`basedOnVersion` 复制路径同样需要回归，因为它读取并复用 Version 的 CallInterface。

### 3.3 注册/注销的 6 个类型只需检查契约，不顺带重构

- `base.AbstractAgentEndpointRequest`：`agentName / protocol / endpoints[]`。
- `base.AbstractAgentEndpointRegistrationRequest`：增加 `runtimeVersion / versionRange`。
- `AgentEndpointRegistrationClientRequest`、`AgentEndpointRegistrationBatch`。
- `AgentEndpointDeregistrationClientRequest`、`AgentEndpointDeregistrationBatch`。

它们仍提交 Endpoint 列表，不需要在注册命令中新增 CallInterface/EndpointSet。Client 输入仍无
namespace，Batch 保留 namespace；注册的 runtimeVersion/versionRange 仍是整个 Batch 的一组值。
新的 Endpoint 字段不能因此变成可写。Deregister 继续只接受 uri/transport，SDK 完整替换剩余 Batch。

### 3.4 可保留结构的相关模型

| 模型 | 结论 |
| --- | --- |
| `RuntimeVersionBinding` | 保留 runtimeVersion/versionRange；改变的是引用它的位置，不是范围算法 |
| `EndpointSource` | 保留 RUNTIME/DECLARED，继续由 EndpointSet 持有 |
| `RuntimeEndpointState` | 管理状态枚举可继续保留，字段最终归属待定，不顺带修改状态语义 |
| `AgentReference`、`AgentDiscoveryFilter` | 选择器、过滤器结构保持；endpointSources 仍过滤 Set |
| `AgentSummary`、`AgentVersionInfo`、`AgentVersionSummary`、`AgentOverview` | 不包含完整 CallInterface 地址链路；不需要再合并或扩字段 |
| `AgentSearchClientRequest`、`AgentSearchRequest` | 请求结构不变；Search 不加入 Endpoint/健康状态 |
| `AgentUpdateAdminRequest`、`AgentLabelsUpdateAdminRequest`、`AgentVersionAdminRequest` | 管理元数据、标签和版本动作不需要新增地址字段 |

## 4. agent 包以外的模型与信封

| 位置 / 模型 | 已确认的影响 | 建议边界 |
| --- | --- | --- |
| ai：`AgentVersionContent` | **直接包含旧 DefinitionCallInterface**；既负责存储读取，也被 draft/copy/migration 创建 | 类型可适配，内部 v1 bytes/字段不自动跟随管理返回 JSON 改变 |
| console：`ConsoleRuntimeEndpointView` | 包装 RuntimeEndpointSnapshot | 外层包装与 namingServiceRef 可保留，内部路径联动；不复制另一套 Console Endpoint |
| api RPC：`AgentPublishRpcRequest`、`AgentPublishRpcResponse` | 分别嵌套 PublishClientRequest、VersionDetail | RPC 外层不必变，定义 payload 会联动 |
| api RPC：`AgentDiscoveryResponse` | 嵌套 AgentDiscoveryResult | 外层保持；保护 discovery payload 和指纹 |
| api RPC：`AgentEndpointRegisterRpcRequest` | 嵌套 RegistrationBatch | 外层保持；统一 Endpoint 的只读字段校验联动 |
| api listener：`NacosAgentDiscoveryEvent` | 内含 AgentDiscoveryResult | 事件类型/回调签名可保持，结果复制与类型联动 |
| Watch 请求/响应及 `AgentWatchBatchItem/Request/Response` | 主要承载 request、身份、generation、fingerprint、变化提示 | 无需加入 CallInterface/Endpoint；不能把通知改成管理或发现数据流 |
| ARD：`ArdArtifact` | 内容是泛型/开放对象，实际 Agent payload 来自 AgentArtifactBuilder | 类名/字段可能不变，但线上导出内容会受影响，不能漏检 |

Forms 同步核对 `AbstractAgentDraftForm`、`AgentDraftCreateForm`、`AgentDraftUpdateForm`、
`AgentPublishForm` 和 `AgentEndpointRegistrationForm`。HTTP 仍按现有 Form 字段传输复杂 JSON，
不为统一模型顺带修改 HTTP method、URL、参数绑定或 gRPC 信封。

## 5. 六个生产模块与 Console 前端的联动

| 模块 | 重点调用点 | 需要修改 / 验证的内容 |
| --- | --- | --- |
| api | AgentModelValidator、RadModelValidator、AgentDiscoveryCanonicalizer、EndpointCanonicalizer、AgentWatchLogUtils | 上下文校验、字段白名单、复制、完整快照 fingerprint、日志计数；EndpointNaturalKey 身份保持 |
| ai | AgentDiscoveryApplicationService、AgentRuntimeRegistryService、AgentRuntimeEndpointMapper | 定义/声明 Set 转换；管理与发现各自的 runtime 投影；自然键合并、bindings、disabled/healthy 行为 |
| ai | AgentPersistenceService、AgentOperationService、AgentVersionContentSerializer | 创建/更新/复制/发布读取与写入、内部存储显式双向转换 |
| ai | RuntimeEndpointRevision、DefaultAgentProjectionProjector | 发现 revision 固定向量、Watch 依赖协议集合、完整结果一致性 |
| client | AgentModelUtils、AgentEndpointPublicationManager、AgentWatchManager、HTTP/gRPC transport 与 redo | 参数复制/校验、查询反序列化、缓存/回调隔离；不扩大为 transport 路由或恢复算法改造 |
| maintainer-client | AgentMaintainerService / Impl | 公共构造与返回类型、CallInterface/Runtime JSON 解析；方法名、namespace 绑定可保持 |
| console | Controller → Proxy → Handler（inner/remote/noop） | 管理返回嵌套结构、Console 专用包装、独立部署通过 Maintainer 的解析 |
| ai-registry-adaptor | ArdArtifactService → AgentArtifactBuilder | Agent Artifact 的真实返回内容、exact version/contentDigest 引用和 schema 一致性 |
| console-ui-next | types/agent.ts、agentDetail、newAgent/agent-console-model.ts、newAgent 页面、相关 store/测试 | TS 模型、声明地址编辑/预览、草稿反填、运行列表读取、Naming 跳转引用；实施后重建静态产物 |

额外核对了 Search：AgentSearchIndexProjector 使用 CallInterface 提取协议和 descriptor 中的能力，
需要换类型并验证结果，但 Search DTO、线上检索行为和索引存储不应加入 runtime 字段。

## 6. 二次核查发现的关键风险

### R1. 存储写入显式映射，但读取仍直接绑定模型

AgentVersionContentSerializer.serialize 使用白名单生成旧 `declaredEndpoints` 存储字段；
deserialize 校验 JSON 形状后却直接 `JacksonUtils.toObj(bytes, AgentVersionContent.class)`。
因此只更改写投影不够。新公共模型改成 endpointSets 后，旧 bytes 必须仍能通过显式读取转换
恢复定义。必须保留字段名、数组顺序、默认值、空字段省略及 contentDigest，不能悄悄升级存储 schemaVersion。

### R2. Endpoint 读写合并会扩大所有写请求的可见字段

当前 AgentModelUtils.copyEndpoint、EndpointCanonicalizer.canonicalize 手工复制已知字段。
新增 bindings/管理状态后，如果先复制时丢弃再校验，会把非法输入静默“洗掉”；反过来全部
复制却未补上下文校验，又会让只读字段进入注册/注销。要同时覆盖字段保留和字段拒绝，不能只补 getter。
发现 Canonicalizer 也有独立的手工复制路径，必须保留 bindings，防止复制后触发假变更或丢失版本兼容信息。

### R3. 共用 EndpointSet 不能直接共用同一个校验入口

RAD 要求 descriptor 必填、RUNTIME 健康/bindings 必填、sourceRevision 必填；定义写入时
revision 尚未生成，管理预注册查询又可能没有任何定义。声明只读 Set 的 revision 与写入字段的
区别、无定义运行查询的 descriptor 规则、管理状态必选性必须在上下文校验中体现。
定义仍需完整 endpointSourceOrder，尤其 RUNTIME-only 与 RUNTIME-first 不能因只读 DECLARED 而改变。

### R4. 管理 revision 与发现 revision 不能视为同一投影

管理可含 disabled 贡献，发现会排除；管理还有状态与观察时间。RuntimeEndpointRevision 当前
只编码 RAD 公开字段，不编码 enabled/state/观察时间，不能直接拿它声称覆盖整个管理结果。
先确定管理 revision 的作用域与字段集合，再实现；不能为统一类名改变既有 RAD 固定向量。
Watch 的完整 fingerprint 也必须保持 RAD 白名单，避免管理只读字段污染监听结果。

### R5. SnapshotItem 同时是内部 mapper 的中间对象

AgentRuntimeEndpointMapper.fromInstance 返回 RuntimeEndpointSnapshotItem，供 RuntimeRegistry 的
管理聚合与发现聚合共同使用；A2aRuntimeSnapshotComparator 也直接消费它。
其 public 类型移除需要替换这些内部接口或表示，但保留 enabled/healthy 聚合、binding 筛选、
payload 冲突判断和 legacy migration 校验。不是只改 Controller 的 JSON 字段。

### R6. Artifact Schema 直接引用管理模型

AgentArtifactBuilder.buildNacosAgentArtifact 直接把 VersionDetail.callInterfaces 放入导出 Map；
`agent-artifact.schema.json` 又 `$ref` 管理 schema 的 AgentCallInterface。
管理响应变化会自然传播到 Artifact 内容。需要明确导出同步升级还是保留显式旧投影及稳定 schema，
不能仅修改管理 schema 就视作“仅管理 API 改动”。A2A 原生 AgentCard 导出应保持原契约。

### R7. 旧 A2A 模型不合并，但内部适配并非零影响

A2aCanonicalDefinitionConverter、A2aServerOperationService 和 A2aMigrationTargetStore 使用
DefinitionCallInterface；migration comparator 使用 SnapshotItem。需适配类型及地址访问，
保留 registrationType、nativeDescriptor、exact version 和旧响应行为，不扩展为 A2A/RAD 兼容重设计。

## 7. Schema 与规范的具体影响

| 文件 | 影响 |
| --- | --- |
| `specs/schemas/ai/agent/agent-management.schema.json` | 直接调整 AgentCallInterface、VersionDetail 引用、RuntimeEndpointSnapshot/Item/Endpoint 的结构；不是只重命名 Java 类 |
| `specs/schemas/ai/rad/rad-protocol.schema.json` | 作为三层结构基准；保护既有 DECLARED/RUNTIME 校验和不接受未知管理字段。共用 Java 类型不要求放宽 RAD JSON |
| `specs/schemas/ai/agent/agent-artifact.schema.json` | 管理 CallInterface 的直接 `$ref`，必须联动评审；artifact payload 的 schemaVersion 为 1.0，与文件内契约版本元数据分开；历史结构按 Git revision 追溯 |
| `specs/schemas/ai/agent/internal/v1/agent-storage.schema.json` | 应保护现有内部格式，通过显式转换与查询 DTO 隔离；不能用管理 schema 替换它 |

中英文 Agent 管理/API、RAD Java 模型绑定、Client AI API evolution、SDK Java implementation、
gRPC payload 说明、Agent Storage 和 ARD/Artifact 相关规范需要同步核对。Java 公共类型改名及
管理 JSON 属于本次明确的变更；RAD Wire 与存储格式的保持需要用测试证明。

## 8. 测试影响矩阵：已有基础与需要加固的内容

本表是下一轮实施的测试计划，不提高现有 coverage registry 状态，也不把以前通过的测试当作新模型的验证。

| 目标 | 已核对的现有基础 | 新结构需要的断言 |
| --- | --- | --- |
| 统一模型 JSON / Jackson | AgentContractModelTest、RadProtocolModelTest、AgentModelValidatorTest、RadModelValidatorTest | 管理/发现/写入各自字段白名单；声明无 bindings/healthy；运行结果有 bindings；管理字段不泄漏 |
| 复制、规范化、指纹 | AgentModelUtilsTest、AgentDiscoveryCanonicalizerTest、RuntimeEndpointRevisionTest | 新字段不丢失；输入不可变；RAD 固定 token/完整指纹保持；非法只读字段不能复制后被静默忽略 |
| 内部存储 | AgentVersionContentSerializerTest、AgentVersionStorageServiceTest、PreparedAgentVersionWriteTest、AgentPersistenceServiceTest | 原始旧 JSON → 新模型读取；新模型写出预期旧 bytes；digest/数组顺序/默认值/复制 draft 一致 |
| 管理版本读写 | AgentAdminApiOpenApiITCase、AgentVersionAdminApiOpenApiITCase | 创建/更新/读取/copy 同一 CallInterface 类型；DECLARED Set；来源配置保留；runtime 不进入版本 |
| Runtime 管理边界 | AgentRuntimeEndpointAdminApiOpenApiITCase | 无定义、无实例、指定/未指定 version、非法 protocol/version 均保持正确形态和错误 |
| 管理与发现联合路径 | AgentEndpointClientOpenApiITCase.testCompletePublisherLifecycleAndQueryIsolation | 该用例已验证真实注册后的 Admin/Console Snapshot；改为同一地址结构并加 bindings/状态/来源断言；注销后空 Set |
| Console | AgentConsoleApiOpenApiITCase、AgentProxyTest、前端 agent-console-model/store 测试 | 新嵌套结构、namingServiceRef 保留、草稿编辑/反填、Runtime 页签和 disabled 展示 |
| Client Publish | AgentPublishJavaSdkITCase | Client/管理调用构造同一 CallInterface；发布、重试、copy、namespace 和受控错误；grpc/http 输出一致 |
| Discover / Watch | AgentDiscoveryServiceJavaSdkITCase 的传输等价、复杂指纹、预注册、完整替换和多项注销场景 | 同步与回调同构；DECLARED/RUNTIME、空 Set、绑定、健康状态完整；grpc/http/auto 契约保持 |
| Maintainer SDK | AgentMaintainerServiceMaintainerSdkITCase | 当前直接覆盖空 Runtime Snapshot；应补一个真实注册后的非空 Snapshot 的 typed 解析，不能只依赖原 JsonNode 跨 API 用例 |
| A2A 内部适配 | A2aServerOperationServiceTest、A2aMigrationStorageVerifierTest、A2aMigrationTargetStoreTest、AgentDiscoveryServiceJavaSdkITCase.shouldInteroperateWithLegacyA2aSdk | 旧公开模型保持；新管理/发现结构变化不改变 legacy 响应、注册类型和迁移判等 |
| Artifact | AgentArtifactBuilderTest、ArdArtifactServiceTest、ArdAdaptorOpenApiITCase | 管理 schema 与实际导出 payload 对齐；无 runtime 管理字段；exact version/digest 选择正确 |

注意已知测试边界：ArdAdaptorOpenApiITCase 的主成功用例当前因 DAUTH-F03 显式 Disabled，
不能把它列为可直接通过的发布门槛；Artifact 至少需要确定性 UT 和 schema 实例校验，
该 IT 阻塞按原原因记录。故障恢复、真实重启、集群场景继续沿用既有开关，本轮不新增或解除跳过。

实施时更新 SDK/OpenAPI/Maintainer 场景文档和 coverage registry，记录新断言与剩余缺口。
编译/UT 范围是 api、ai、client、console、maintainer-client、ai-registry-adaptor；前端另做
类型检查、相关测试、构建，并按项目流程重建 Console 资源。涉及序列化的 SDK/Maintainer 检查
继续覆盖现有 Jackson adapter 配置。

## 9. 明确排除与实施前待定项

- 排除同名误报：`model.a2a.AgentVersionDetail` 与本次 `model.agent.AgentVersionDetail` 是不同类型；
  `AgentCardVersionInfo.versionDetails` 使用前者。Istio/Core 的 Endpoint 同名项也不是本次地址模型。
- 历史 A2A 公开类型、MCP/Skill/Prompt 公共模型、Naming 持久化布局、Client transport 选择算法不扩大修改。
- AgentSummary/VersionInfo/Summary 已完成的元数据合并不重复设计；MODEL-D01 继续延期。
- 实施前需确定：Runtime 根结果的单个/列表 CallInterface 形态；管理状态/观察时间归属；无定义 descriptor
  缺省规则；管理 sourceRevision；定义写入对只读 revision 的处理；Artifact 内容/schema 的保持或升级策略。
- 已确认的方向是三个核心类型共用。上述待定字段不阻塞本次影响面核查，但没有确定前不能声称已具备完整编码契约。

## 10. 关键源码定位

- [版本内容模型](../../../ai/src/main/java/com/alibaba/nacos/ai/model/agent/AgentVersionContent.java)
- [版本存储读写](../../../ai/src/main/java/com/alibaba/nacos/ai/service/agent/storage/AgentVersionContentSerializer.java)
- [Endpoint 入参复制](../../../client/src/main/java/com/alibaba/nacos/client/ai/utils/AgentModelUtils.java)
- [Endpoint 规范化](../../../api/src/main/java/com/alibaba/nacos/api/ai/utils/EndpointCanonicalizer.java)
- [RAD 上下文校验](../../../api/src/main/java/com/alibaba/nacos/api/ai/utils/RadModelValidator.java)
- [运行事实转换](../../../ai/src/main/java/com/alibaba/nacos/ai/service/agent/runtime/AgentRuntimeEndpointMapper.java)
- [运行地址聚合](../../../ai/src/main/java/com/alibaba/nacos/ai/service/agent/runtime/AgentRuntimeRegistryService.java)
- [A2A 迁移比对](../../../ai/src/main/java/com/alibaba/nacos/ai/service/a2a/migration/A2aRuntimeSnapshotComparator.java)
- [Agent Artifact 构建](../../../ai/src/main/java/com/alibaba/nacos/ai/service/agent/AgentArtifactBuilder.java)
- [ARD Artifact 返回](../../../ai-registry-adaptor/src/main/java/com/alibaba/nacos/airegistry/service/ard/ArdArtifactService.java)
- [已有跨管理面/发现面的注册 IT](../../../test/openapi-test/src/test/java/com/alibaba/nacos/test/openapi/client/ai/AgentEndpointClientOpenApiITCase.java)

## 11. 五项影响的展开讨论（候选建议，尚未实施）

以下给出可评审的选择，不表示用户已经确认所有字段决策；中英文管理规范 §6.2 同步记录候选方向。

### 11.1 存储：建议保持内部 v1 格式，补齐双向转换

仅列相关字段，旧存储是 `callInterfaces[].declaredEndpoints[]`，新公开结果是
`callInterfaces[].endpointSets[source=DECLARED].endpoints[]`。删掉 declaredEndpoints 后，直接
反序列化可能报错或丢字段，具体取决于 serializer 设置；两种都不能作为兼容读取方案。

可选方式：

| 方式 | 成本与结果 |
| --- | --- |
| 保留存储 v1，做双向转换（建议） | 在现有 serializer/assembler 内转换，不新增一套公共 Model；历史内容、存储指针与 digest 语义保持 |
| 存储也改成 EndpointSet | 需定义新 storage schemaVersion、旧数据读取/迁移、digest/pointer 更新与回滚策略；扩大本次范围 |

建议流程：旧 bytes 严格校验 → 读取并转为统一定义模型；新定义模型严格校验 → 提取声明地址、
完整来源配置及 descriptor → 按旧格式写入。sourceRevision 在读取响应组装时，根据已确认的
版本 contentDigest 补齐；不能参与用于计算自身 contentDigest 的存储内容。

该选择保护的是持久化内容，不要求为 3.3 BETA 公共 Java 类保留别名。无论选择哪种方案，
runtime 地址/管理观测都不进入版本存储。验证须包含固定旧 JSON 读取、新写固定 bytes、
来源顺序与默认值保持、basedOnVersion 复制，以及 runtime 变动不改变版本 digest。

### 11.2 写请求：统一类型，按操作校验字段

建议沿用现有校验工具，增加明确的操作上下文；不为每个字段组合再造一组公开 Endpoint 子类。

| Endpoint 字段 | 定义写入 | Register | Deregister | RUNTIME 发现读取 | Runtime 管理读取 |
| --- | --- | --- | --- | --- | --- |
| uri / transport | 必填 | 必填 | 必填 | 返回 | 返回 |
| priority / weight / metadata | 可写 | 可写 | 禁止 | 返回有效值 | 返回有效值 |
| bindings | 禁止 | 禁止 | 禁止 | 必须非空 | 返回匹配贡献 |
| healthy | 禁止 | 禁止 | 禁止 | 必填 | 返回 |
| enabled / 管理 state / 观察信息 | 禁止 | 禁止 | 禁止 | 不输出 | 依最终字段设计返回 |

bindings 的禁止不影响 RegistrationBatch 顶层已有的 runtimeVersion/versionRange。
声明结果仍不返回 bindings/healthy。EndpointSet.sourceRevision 是响应生成字段，建议定义
写请求出现时受控拒绝；Console 从查询结果构造 draft 时显式只选可写字段。

顺序建议：先检查原输入中非法只读字段，再复制并规范化，最后执行 URI/自然键/range 等完整
校验。或者复制时保留全部已知字段再校验，但不能先静默丢弃只读字段。SDK 本地错误与服务端
直接 HTTP/gRPC 写入都要覆盖；不能仅依赖 SDK 帮调用方清洗数据。完整拒绝一个非法 Batch，
不产生部分写入，调用方对象及集合保持不变。

### 11.3 SnapshotItem：建议统一 Endpoint 承接，不合并两种聚合算法

当前 `fromInstance()` 的结构为 `SnapshotItem { endpoint, bindings, enabled, healthy,
state, lastUpdatedTime }`。可调整为返回包含地址、单份 binding、enabled/healthy 的统一
Endpoint；管理和发现随后各自聚合，移除 endpoint 外套一层 Item 的公开访问方式。
若内部确有无法放进公共对象的字段，可使用包内实现对象，但不能重新暴露一套公共地址模型。

两个发布者具有相同自然键、相同 payload 且 binding 均命中查询时，当前代码存在以下区别：

| 贡献 | enabled | healthy |
| --- | --- | --- |
| P1 | false | true |
| P2 | true | false |

管理聚合对全部匹配贡献分别 OR，得到 enabled=true、healthy=true，state=AVAILABLE。
发现排除 P1，只保留 P2，因此返回 healthy=false。这个例子来自当前聚合分支的静态推导，
不是新执行的测试；说明管理 state 不能当成最终可发现性。若要改变该管理聚合语义，应单独讨论，
本次模型替换不能顺带改变结果。

state 可以继续由 enabled/healthy 派生，避免出现第三份独立状态事实。现有 lastUpdatedTime
实际是整份 Service 投影的观察时间；若保留，可考虑移到 EndpointSet，而不是每个地址重复。
这属于字段位置选择，尚未实施。A2aRuntimeSnapshotComparator 需换取值方式并保持迁移判等结果。

### 11.4 管理 revision：比较范围独立，算法可以共用

当前管理 Snapshot 本身没有 sourceRevision；采用完整 EndpointSet 契约后才需要决定如何提供它。

| 方式 | 取舍 |
| --- | --- |
| 管理响应省略 sourceRevision | 最小实现，但需要管理 Schema 允许缺省，只有 Java 外形共用，不能声称与 RAD Set 字段约束完全相同 |
| 计算管理语义地址集 revision（建议） | 保持 Set 字段使用一致；复用现有哈希原语，但对管理结果定义单独的输入白名单和作用域 |

建议管理 revision 覆盖规范地址、priority、weight、metadata、bindings、enabled、healthy；
若 state 完全派生，不必重复进入输入。观察时间只表示读取/投影刷新时间，排除在语义地址集
revision 外，并明确 revision 不是完整 JSON 响应字节的 ETag。

同一查询作用域下：仅观察时间变化不改 revision；公开地址、binding 或 enabled/healthy
发生有效变化则改 revision；空集合稳定。作用域包括管理/发现视图、namespace、Agent、protocol
和 version 过滤语义；不同作用域不能互比。可以复用 MurmurHash 算法和排序工具，不需要重做
一套版本系统。现有 RAD 输入布局、token、Watch fingerprint 保持，管理新增信息不得混入其中。

### 11.5 Artifact：建议同步采用统一定义结构，显式选择导出字段

当前 AgentArtifactBuilder 直接导出 VersionDetail.callInterfaces，管理 Schema 又被 Artifact
Schema 引用，所以不做处理时会自动从 declaredEndpoints 变成 endpointSets。

| 方式 | 取舍 |
| --- | --- |
| 保持旧 Artifact 外形 | 增加显式旧格式投影，固定旧 Artifact Schema 引用；外部格式稳定，但维护一种额外表示 |
| Artifact 同步使用统一 CallInterface（建议） | 符合当前统一模型目标；只输出定义、DECLARED Set 和完整来源配置，明确记录格式/schema 变更 |

两种方式都应显式构建导出投影，避免以后给共享模型新增管理字段时泄漏。不要把整个管理结果
或 RUNTIME Set 直接序列化成 Artifact。原生 A2A AgentCard 表示保持不变。

contentDigest 标识 Version 持久化内容，不等于导出 JSON 的字节摘要。存储格式保持时，导出
结构变化不必改变版本 digest，但同一 Artifact 定位键可能得到不同 JSON 外形；必须明确格式
版本/representation 的处理，不能把 Java 改名当作对外无变化。公开 Schema 采用固定路径，历史版本通过 Git 追溯，
payload.schemaVersion 为 1.0，两者不是同一字段；如果需要区分新旧表示，应明确版本策略，
本轮讨论不擅自指定新版本号。忽略 BETA 兼容并不要求保留旧表示，但仍需更新 Schema、规范和
导出实例校验。旧 A2A 表示、exact version/digest 选择和 runtime 变化不影响定义 Artifact 均需回归。

## 12. 不考虑 BETA 升级后的设计澄清

用户明确 3.3 BETA 是不支持升级的测试版本，因此撤回 §11.1 优先保持旧存储格式的建议，
不为本轮统一模型增加旧格式双向兼容、迁移、回滚或旧 bytes 恒等测试。前文的引用分析仍然有效。

1. **存储模型**：保留内部 `AgentVersionContent` 作为存储内容容器即可，其成员可以直接共用
   `AgentCallInterface / EndpointSet / Endpoint`，无需再复制一套内部同名领域模型。
   存储格式可随统一结构调整。保存完整定义、descriptor、全部声明地址、完整 endpointSourceOrder
   和业务顺序不会丢失定义信息；直接保存已按版本/来源/Filter 裁剪的 Discover 结果才会丢信息。
   RUNTIME、健康/管理观察字段和派生 sourceRevision 不属于版本定义；不保存它们不构成定义丢失。
   存储仍须选择定义字段，并对实际保存 bytes 计算/校验 digest，避免摘要自引用。
2. **可写含义**：指服务端注册或定义写入接口允许提交的字段。Java setter 只是修改本地对象，
   不会自动请求服务端；将对象放进注册 Batch 并调用注册 API 才会修改发布者状态。注册是完整
   Batch 替换。新增 readonly 字段的输入校验仍需要，但不要求把查询对象做成不可变类。
3. **Snapshot 收敛**：SnapshotItem 可以被统一 Endpoint 承接，Snapshot 的地址访问路径可以
   换成统一 CallInterface/EndpointSet。内部 mapper、聚合和 A2A comparator 直接适配即可；
   没有仅因内部引用就必须保留旧公开模型的理由。既有字段与筛选/聚合语义的保持属于实现检查。
4. **revision 含义**：当前字段是 `EndpointSet.sourceRevision`，表示某个来源地址集合的变化
   标记；当前 RuntimeEndpointSnapshot 不含该字段。“管理 revision”是管理结果改用 Set 后
   对这个字段赋值的讨论，不是现有独立模型，也不要求新增一套管理版本系统。管理是否提供及
   如何计算可单独细化，不应把它描述为模型合并阻塞。
5. **Artifact 含义**：ARD 的 artifact HTTP 入口返回可供消费者读取/下载的 Agent 定义 JSON。
   Nacos Agent 表示当前直接引用 VersionDetail.callInterfaces，因此统一模型时其 JSON 形状也
   会变化。建议本轮一起使用统一定义结构，并同步 Schema 和测试，不为 BETA 保留旧导出表示。
   它仍只导出定义/声明地址；原生 A2A AgentCard 是另一个协议表示，保持该协议原形。

本节更新设计建议，不表示已实施 Java、Schema 或存储代码变更。管理字段位置等尚未确定的
细节继续讨论，但不再额外引入 BETA 数据升级或导出兼容工作。
