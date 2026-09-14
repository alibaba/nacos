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

# Agent 地址模型统一：设计结论与改造前测试方案

日期：2026-09-14。基于 `codex/agent-model-consolidation` 当前未提交工作区。
本轮只有设计、源码核查和测试规划，没有执行下面的新测试，也没有修改 Java/TypeScript/Schema。
之前摘要合并的测试结果不能充当本轮地址模型统一的验收结果。

本文收敛 [影响面核查](MODEL_ENDPOINT_IMPACT.md) 与 [六类入口图](MODEL_ENDPOINT_PATHS.md)。
如果旧讨论中的“保留 BETA 存储格式”“注册禁止 healthy”“拒绝服务端维护字段”与本文不同，
以本文最新方向为准。其余路径清单仍可用于追踪调用方。

## 1. 已确认的方向与实现建议

### 1.1 统一模型没有已知结构性阻塞

| 事项 | 本轮结论 | 同步改动 |
| --- | --- | --- |
| 主干 | 管理与发现共用 `AgentCallInterface → EndpointSet → Endpoint`，不计根响应 | 合并两个 CallInterface、DiscoveryEndpoint 和 SnapshotItem；保留 Set 及其 source |
| 管理读取 | VersionDetail 只读定义/DECLARED；Runtime API 单独读取运行地址 | 两个入口共用三层类型，不要求聚合查询，也不要求定义存在才能查询运行地址 |
| 存储 | 保留内部 AgentVersionContent 容器，成员复用统一模型 | 保存完整定义及来源配置；读写同时适配新格式，不保存筛选后的 Discover 结果 |
| BETA | 不做 3.3 BETA 存储升级、旧 bytes 或旧导出格式兼容 | 允许新存储/导出结构和摘要改变；不添加 BETA 迁移框架 |
| Runtime/A2A 内部引用 | mapper、聚合器和迁移比较器可直接换成统一类型 | 原字段、自然键、聚合、迁移判等及状态机行为继续保持 |
| revision | 继续使用 EndpointSet.sourceRevision；不是新增管理版本系统 | RAD 比较规则保持；管理查询是否提供该字段单独明确 |
| Artifact | Nacos Agent 定义 JSON 同步采用统一结构 | builder、Schema、测试一起变；原生 A2A AgentCard 保持协议原形 |
| 其他领域 | 已完成的 AgentSummary/版本摘要合并保持；历史 A2A 公共类型保持 | 对索引、生命周期、旧 A2A facade 做回归；不顺带重构 MCP/Skill/Prompt |

需要改变的 JSON 结构和需要保持的业务语义应分别列清，不能用“所有响应字节不变”作为目标。
特别是：新定义存储 bytes 改变后 contentDigest 可以改变，引用该摘要的 DECLARED revision、
完整发现 fingerprint、索引 sourceDigest 和 Artifact URL 也可能随之改变。
应保证这些引用在本轮新系统内一致，不强求它们等于重构前的值。
仅 RUNTIME revision 中未改变的输入编码、固定向量应继续保持。

### 1.2 字段位置建议，实施前固定样例

这些是为了让测试可执行的最小实现建议，不表示 Java 已经修改：

- AgentCallInterface 保留协议四字段与 endpointSets；endpointSourceOrder 仅在定义上下文使用。
- Endpoint 合并 bindings、enabled、state；healthy 只保留一份，不再嵌套 endpoint 对象。
- 当前 lastUpdatedTime 实际是 Service 投影观察时间，建议在 EndpointSet 上返回一份。
- Runtime 查询仍只接受一个 protocol：可保留必要的根身份容器，使用一个 callInterface 字段
  替代 items；protocol 由该 CallInterface 承载。Console 包装继续保留 namingServiceRef。
- 无定义的 Runtime 查询允许缺少 descriptor；不能为了满足发现 Schema 而制造假 descriptor。
- 管理 sourceRevision 首轮建议允许省略。RAD 仍必填，定义响应的 DECLARED revision 来自 digest。
  如果决定管理也计算 revision，则需补充独立输入白名单和固定向量，不直接承诺覆盖整个响应 JSON。

在实施阶段开始时，用六类入口的固定请求/响应 JSON 锁定上述选择，再更新正式 Schema。
字段位置调整只需修改对应样例和断言，不再派生多套公开 Endpoint 类型。

### 1.3 Endpoint 可写字段：本轮新增行为

用户确认：healthy 可以写入；bindings、管理状态、观测时间由 Nacos 维护，用户修改时忽略。
这里“写入”指提交 API；修改已经返回的 Java 对象不会自动修改服务端。

| 输入字段 | Runtime 注册/完整替换 | 定义写入 | SDK 注销命令 |
| --- | --- | --- | --- |
| uri / transport | 校验并接受 | 校验并接受；遵循 descriptor 与声明地址一致性规则 | 自然键字段，校验并接受 |
| priority / weight / metadata | 校验并接受 | 校验并接受 | 保持现有不允许作为注销参数的规则 |
| healthy | 接受 true/false；缺省建议为 true | 可写范围先限定 Runtime；DECLARED 继续不含健康状态 | 不是注销参数，保持现有受控错误 |
| bindings | 忽略，由批次 runtimeVersion/versionRange 生成 | 忽略，不写入定义 | 忽略，不参与匹配 |
| enabled / state | 忽略，由 Naming 管理状态及投影生成 | 忽略，不写入定义 | 忽略，不参与匹配 |
| 观测时间 | 忽略，由 Nacos 生成 | 忽略，不写入定义 | 忽略，不参与匹配 |

定义请求携带响应 sourceRevision 时，建议同样忽略并重新生成，便于查询后编辑再提交。
这些忽略规则不适用于 namespace、身份、来源、descriptor、批次版本范围、URI、保留 metadata key
等仍受约束的输入，也不放宽未知字段或非法 JSON 类型的现有解析规则。

**healthy 的默认解释是注册/完整替换时上报的当前健康值，不是永久健康开关。**
后续仍由现有 Naming 活性规则维护；get-all/Watch 继续保留 unhealthy 地址，disabled 地址不进入发现。
用户此次没有要求将 healthy 设计成永久禁止恢复的开关，因此不新建第二份持久健康意图。
若后续需要这种含义，应另讨论，不混进类型合并。

健康断言拆为四项：缺省注册为 true；显式 false 在活跃发布者下真实可读；同一完整批次以
true/false 再提交可更新；仅 ACTIVE heartbeat 不擅自把 false 改回 true。HTTP 超时恢复按
现行活性规则重置健康的行为用可控状态组件测试描述，不在本轮增加真实超时/故障恢复 IT。

源码核查确认：RadModelValidator 当前拒绝注册 healthy，AgentRuntimeEndpointMapper.toInstance
当前固定 healthy=true；Naming ClientOperationService 会保留传入的 Instance health。
HTTP Publisher 从超时恢复时，HttpConnectionBasedClient.updatePublisherHealth 会按现行规则
恢复其贡献健康状态。普通 ACTIVE heartbeat 不会每次都重写所有 Endpoint health。
因此新测试必须证明显式 false 真正进入注册状态，并描述后续活性规则，不能只删一条校验。

服务端维护字段应在写入归一化时显式剔除：结构可反序列化的 bindings 即使携带不匹配版本，
也不能覆盖批次版本或导致业务校验失败；非法 JSON 类型仍走受控解析错误。
SDK 与直接 HTTP/gRPC 都执行同一输入政策。读取/缓存/回调复制则必须保留返回字段，不能复用
写入剔除逻辑。两种复制用途应有相反的字段保留断言。

## 2. 测试机制：基线、独立预期和验收证据

### 2.1 改实现前建立可比较的基线

1. 保存本轮开始时未提交工作区的源码状态标识、差异摘要及关键文件校验和，不把 HEAD 当成工作区。
2. 用 clean 构件执行既有受影响 UT 和普通 IT，留存测试方法名、参数、执行/失败/跳过原因。
3. 将下面各验收项映射到现有测试；缺少的行为先补基线测试。新增 healthy 输入用例属于预期行为变化，
   单独列出，不能要求旧实现通过，也不能放宽现有测试来假装它已实现。
4. 新结构样例预先手工审定，包括字段缺省、空集合、来源顺序、bindings、namespace 和错误结果。

不是每个 DTO 写 getter/setter 测试。重点是跨边界数据是否完整、派生计算是否正确、错误是否受控。

### 2.2 三种互补断言

| 断言方式 | 防止的问题 | 使用方式 |
| --- | --- | --- |
| 固定 JSON / bytes / token 向量 | 新客户端与新服务端同时漏字段仍互相兼容 | 预期文件独立于生产 serializer；实际 payload 逐字段验证 |
| 行为前后对照 | 修编译时误改 selector、binding、排序、迁移和索引语义 | 仅显式转换允许变化的访问路径；不能整体排序业务数组或随意删除不一致字段 |
| 外部完整流程 | Mock 绕过 Naming、存储、索引任务或 Console 远程转换 | 真实发布/注册 → 多入口读取 → 变化/注销 → 再读取，并验证最终副作用 |

不把两个返回值用同一个生产转换器处理后相等，作为唯一正确性证据。
Schema 验证要校验实际正反例实例，而不只检查 JSON 文件能解析或 `$ref` 能找到。
原生 A2A、管理、RAD、注册、存储和 Artifact 可以共用 Java 类，但各自的 Schema 上下文约束仍需验证。

### 2.3 测试层与各自职责

| 层 | 内容 | 不能替代 |
| --- | --- | --- |
| UT / 组件测试 | 转换、校验、Naming 映射、存储、revision、迁移比较器、索引任务和投影 | 不能代替真实 HTTP/SDK 解析与部署链路 |
| OpenAPI IT | Client/Admin/Console 原始 JSON、错误、鉴权和真实副作用 | 不能代替 SDK 泛型、JSON adapter、回调和旧字节码验证 |
| Java SDK / Maintainer IT | 公开工厂与方法、实际对象及字段、两种 JSON adapter、传输及新旧入口 | 不能通过反射或直接调用内部 service 绕过协议 |
| 专用迁移 IT | 历史 A2A → 统一模型 → 运行/管理/索引/导出，切流前后行为 | 普通 CANONICAL 模式 IT 不提供此证据 |
| Console 前端 | 转换函数、表单提交、详情反填、运行列表、真实页面与打包产物 | 仅 TypeScript 编译或源码字符串断言不能证明页面可用 |

## 3. 全链路验收矩阵

以下 16 组是本轮验收台账，**新模型的当前状态全部为 Pending**。已有测试名称表示复用基础，
不是本轮已执行证据。实施时每组记录具体方法及参数、结果 XML、样例/日志路径；缺少实际执行不标 Covered。

| ID | 链路与必须保持/新增的结果 | 已有测试落点 | 本轮新增重点 |
| --- | --- | --- | --- |
| EP-01 | 六类入口共用三层类型；读写字段缺省与空数组正确；ClientRequest 无 namespace | AgentContractModelTest、RadProtocolModelTest、AgentModelValidatorTest、RadModelValidatorTest | 新结构正反 JSON；继承/组合完整字段；管理字段不泄漏 RAD |
| EP-02 | 写入忽略 Nacos 维护字段；读取深拷贝保留它们；不修改调用方对象 | AgentModelUtilsTest、EndpointCanonicalizerTest、AgentEndpointPublicationManagerTest、AgentWatchManagerTest | 返回对象编辑后提交；忽略字段不能改绑定或管理状态；缓存/多 Listener 互不污染 |
| EP-03 | healthy 注册值有效，缺省 true；false 不被丢弃；HTTP/gRPC 一致 | AgentRuntimeEndpointMapperTest、AgentRuntimeRegistryServiceTest、AgentClientFormsTest、AgentClientRequestHandlerTest | SDK 与绕过 SDK 的输入；真实 false → true 完整替换；受控活性状态组件测试 |
| EP-04 | 版本定义新格式完整读写，digest 对实际 bytes 计算；Runtime 不进入定义 | AgentVersionContentSerializerTest、PreparedAgentVersionWriteTest、AgentVersionStorageServiceTest、AgentPersistenceServiceTest | 新固定 bytes/Schema、严格读回、损坏摘要拒绝、来源顺序、basedOnVersion、不依赖旧 BETA fixture |
| EP-05 | 草稿/更新/发布/上下线/标签/删除和摘要目录一致 | AgentOperationServiceTest、AgentPublishApplicationServiceTest、AgentAdminApiOpenApiITCase、AgentVersionAdminApiOpenApiITCase、AgentPublishJavaSdkITCase | 全部调用方构造新 CI；查询后编辑再提交；运行变化不改 contentDigest |
| EP-06 | 预注册、多发布者、完整替换、多项注销、跨协议/namespace/版本隔离 | AgentRuntimeRegistryServiceTest、AgentEndpointClientOpenApiITCase、AgentDiscoveryServiceJavaSdkITCase | 3 删 2 保留值/绑定/healthy；其他发布者贡献保留；冲突和非法批次不部分写入 |
| EP-07 | 管理 Runtime 支持无定义和空集合；disabled 可见；绑定按 version 筛选 | AgentRuntimeEndpointAdminApiOpenApiITCase、AgentMaintainerServiceMaintainerSdkITCase、AgentEndpointClientOpenApiITCase | 新三层非空 typed 解析；管理只读字段；不能只测空响应 |
| EP-08 | Discover 的版本/label、协议/来源/transport/metadata filter、排序与容量保持 | AgentDiscoveryApplicationServiceTest、AgentDiscoveryClientOpenApiITCase、AgentDiscoveryServiceJavaSdkITCase | 同地址双来源不混合；允许但为空的 Set 保留；无关绑定/disabled 排除、unhealthy 保留 |
| EP-09 | 查询和 Watch 快照同构；revision 与 fingerprint 只跟应观察事实变化 | AgentDiscoveryCanonicalizerTest、RuntimeEndpointRevisionTest、DefaultAgentProjectionProjectorTest、AgentProjectionEventSubscriberTest、AgentWatchClientOpenApiITCase、AgentWatchManagerTest | bindings/healthy 变化；无效重复提示不重复业务回调；最后地址移除仍有空 Set；取消订阅后不回调 |
| EP-10 | 旧 A2A 发布/查询/订阅/单条与批量地址操作不变，URL/SERVICE 都有效 | A2aCanonicalDefinitionConverterTest、A2aServerOperationServiceTest、A2aAdminApiOpenApiITCase、A2aConsoleApiOpenApiITCase、AiServiceJavaSdkITCase | AiService 直接入口与 agent() 继承的旧方法等价；已发布 SDK/字节码契约保持 |
| EP-11 | A2A 定义迁移完整、幂等，Runtime 比较器正确，切流不会因 DTO 变化误判 | migration 包现有全部相关 UT、A2aMigrationAdminApiOpenApiITCase、A2aUpgradeMigrationJavaSdkITCase | 见 §5；迁移生成的新定义与运行视图也用新结构验证 |
| EP-12 | Agent 索引构建、生命周期触发、重建和查询保持，Runtime 不污染目录 | AgentSearchIndexProjectorTest、AgentAiResourceSearchTypeHandlerTest、AiResourceIndexMaintenanceServiceImplTest、AiResourceIndexProjectionBuilderTest、AiResourceSearchClientOpenApiITCase | 见 §6；真实 INDEX 读取，不能用 SCAN 或空结果代替 |
| EP-13 | Artifact 表示、exact version/digest、Schema 与版本状态一致 | AgentArtifactBuilderTest、ArdArtifactServiceTest、ArdSearchControllerTest、ArdAdaptorOpenApiITCase | 新声明结构、原生 AgentCard、禁止 Runtime/管理字段、非空公共 Agent 导出 IT |
| EP-14 | Console inner/remote/noop、Maintainer 解析、页面行为及静态资源一致 | AgentProxyTest、AgentInnerHandlerTest、AgentRemoteHandlerTest、AgentNoopHandlerTest、ConsoleAgentControllerTest、AgentConsoleApiOpenApiITCase、前端 agent 相关测试 | 新建/编辑/复制/详情/运行页签/跳转；集成与独立 Console 两种路径 |
| EP-15 | HTTP/gRPC/AUTO、资源覆盖配置、JSON adapter、认证隔离保持 | AgentTransportRouterTest、AiTransportResourceMatrixJavaSdkITCase、AuthEnabledJavaSdkITCase、AgentMaintainerServiceMaintainerSdkITCase | 见 §7；真实 typed 结果与 raw JSON 交叉检查；业务错误不换 transport 重试 |
| EP-16 | 正式发行包可运行，移除旧 class 后所有消费者编译，新前端随包发布 | 受影响 reactor 编译、静态检查、打包检查与全部上述 IT | clean 防止旧 jar/class 掩盖缺失；启动包嵌入 jar 校验和一致；不增加构件依赖 |

## 4. 核心固定场景与跨入口流程

### 4.1 共用数据集合

使用两个 namespace、同名 Agent、同一 Agent 的两个 online Version、一个非 online Version；
配置 a2a 与另一个合法协议，各保留明确 descriptor。将 RUNTIME-first、DECLARED-first、
RUNTIME-only、DECLARED-only 拆成小场景，避免把所有组合塞进一个巨大测试。

运行数据覆盖：P1 的 E1/E2/E3、P2 的同自然键等价贡献、独立地址、不同/重叠 versionRange；
包含显式 healthy=false、disabled（由 Naming 管理 API 设置）及可区分的 metadata/weight/priority。
URI 包含规范化 host、默认/显式 port、IPv6、path/query；自然键与 payload 分别断言。
声明地址从匹配的 nativeDescriptor 派生，不能用本就违反定义契约的 fixture 测正常流程。

### 4.2 六条必须真实走通的业务流程

| ID | 操作序列 | 关键交叉断言 |
| --- | --- | --- |
| FLOW-01 | Admin/Maintainer 建草稿 → 更新/复制 → 发布 → VersionDetail → Discover → Artifact | 完整 descriptor/来源配置/声明地址保留；新 JSON 路径一致；定义摘要与导出引用一致 |
| FLOW-02 | Client publishAgent → 管理读回 → 注册 E1/E2/E3 → Admin/Console Runtime 与 Discover | 发布定义不等于注册；管理与发现共享类型，但 descriptor/disabled 等场景字段各自正确 |
| FLOW-03 | HTTP 或 gRPC 注册 → 订阅 → 注销两项 → 查询/回调 → 注销最后一项 | E3 的 uri/metadata/weight/healthy/bindings 不变；批次版本保持；其他 Publisher 不被删除 |
| FLOW-04 | 查询 Endpoint → 本地改字段 → 提交 → 再查询 | setter 不触发请求；healthy 真正更新；伪造 bindings/state/时间不生效；对象/集合未被 SDK 原地改写 |
| FLOW-05 | 旧 AiService/A2aService 发布 URL、SERVICE → 新管理/发现读取 → 更新 Endpoint → 旧查询/订阅 | 原生 AgentCard 与 registrationType、exact version、setAsLatest 语义保持；不要求旧类型采用新 JSON |
| FLOW-06 | 定义与版本变化 → 等待索引 → RAD Search/通用 Search/ARD → 获取 Artifact → offline/delete | 索引全文与协议目录正确；旧条目/制品按现有契约消失；Runtime-only 变化不触发目录重建 |

同一数据从管理与发现读取时，比较它们共有的地址与命中 binding，不要求不同查询作用域的
完整 JSON、健康聚合结果或时间戳相等。管理可含 disabled，而发现不含，这是保留的业务规则。

### 4.3 边界与受控错误

- 缺失/非法 namespace、Agent/version/protocol、selector 冲突、未找到、草稿不可发现及版本状态错误。
- 重复 CI protocol、重复来源、非法顺序、重复自然键、无效 URI/port/transport、非法权重/metadata。
- 容量采用等价类与边界值，重点在 UT；真实 IT 验证代表性批次超限及拒绝后原数据完整。
- 验证字段结构正确但值应被忽略的 Nacos 维护字段；另测无法反序列化的类型仍是受控错误。
- HTTP 检查状态码与 Result/detail code；SDK 检查 NacosException；ARD 保持自己的协议错误形状。
- 错误预期取自现有 Controller/Validator/spec，不把全部失败统一期待为 400 或 500。

## 5. A2A 迁移专门验收

**不测 BETA 数据升级，不等于不测 3.0～3.2 历史 A2A 向标准 Agent 的迁移。**
后者是现有产品功能，引用了被合并的模型，本轮必须覆盖。

### 5.1 组件测试：模型替换后仍能正确判定

| ID | 数据与动作 | 必须断言 | 优先复用 |
| --- | --- | --- | --- |
| MIG-01 | URL/SERVICE、多 namespace、多 exact version、setAsLatest、声明地址及扩展 descriptor 转换 | 新版本内容完整；不丢未知原生 descriptor 字段；旧 public 响应保持 | A2aCanonicalDefinitionConverterTest、A2aHistoricalDefinitionScannerTest、A2aHistoricalDefinitionReconcilerTest |
| MIG-02 | 迁移写入新结构 → Storage 读回 → Version-first/Resource-last | bytes/size/digest 一致，完整前不暴露可读 Agent；重复执行无重复版本 | A2aMigrationTargetStoreTest、A2aMigrationStorageVerifierTest |
| MIG-03 | 两套运行布局等价；再逐项改变 uri/transport/weight/metadata/协议版本/tenant/enabled/healthy | 等价时通过；业务字段不同或非法历史数据时不能误判已收敛；观察时间不制造差异 | A2aRuntimeSnapshotComparatorTest、A2aMigrationRuntimeReadinessGateTest |
| MIG-04 | SYNCING/QUIESCING/终态的注册、完整替换、注销及 shadow 两种策略 | 主写/镜像方向、精确版本子发布者清理、容量只算一次保持 | A2aMigrationEndpointRouterTest、A2aMigrationRemovalContractTest |
| MIG-05 | 定义/Storage/Runtime/Search 任一 gate 未就绪或冲突 | 不因新的空集合/字段缺省误过 gate；源和目标不被覆盖；完整后允许切流 | A2aMigrationDefinitionReadinessValidatorTest、A2aMigrationSearchReadinessGateTest、A2aMigrationCutoverCoordinatorTest |
| MIG-06 | 正常状态推进、重复对账、源更新/删除、旧通知 | 不复活旧版本；最新定义进入索引；Shadow 不发重复业务事件 | A2aMigrationReconciliationTaskTest、A2aMigrationDefinitionHintReconcilerTest、现有 Watch/索引 UT |

保留并执行现有状态机、CAS 和错误分支 UT；可以用确定性的内存 fixture/可控时间验证，
不要求为了这些分支做真实宕机注入。此处不重新设计 A2A/RAD 客户端模式决策。

### 5.2 独立环境的实际迁移流程

普通 IT 使用 CANONICAL 环境。迁移验收复用 `.github/workflows/migration-it.yml` 的数据准备、
阶段开关和 HTTP/SDK 交接；只在隔离数据目录执行，不能修改用户已有服务端。

| 环境阶段 | 必测行为 | 验收信号 |
| --- | --- | --- |
| LEGACY | 旧 SDK/Admin/Console 的 URL/SERVICE 定义与地址读写 | 历史响应正确；不宣称必须能做 RAD 发现 |
| AUTO / SYNCING | 历史更新/删除对账；完整 Agent 进入管理/索引；运行双物化 | 新结构定义/Runtime 与历史语义等价；无半成品、无重复容量 |
| AUTO / QUIESCING | 旧定义写屏障；读取、Discover、Watch、Endpoint 替换/注销 | 定义 mutation 仍为 detail code 50105，其他允许操作正常 |
| 终态，shadow=false | 同一发布连接跨切流后替换/注销 | RAD/Watch 正常，遗留历史 child 被清理 |
| 终态，shadow=true | 同一发布连接跨切流后替换/注销 | 历史 shadow 与标准 Runtime 对应，业务回调不重复 |

复用 A2aMigrationAdminApiOpenApiITCase 和 A2aUpgradeMigrationJavaSdkITCase 中正常迁移用例。
已有 @EnabledIfSystemProperty 默认会跳过这些方法；执行器必须核对预期方法真的运行，
不能把“类被选中了，但全 skipped”视作成功。配置环境所需的正常停启不算故障恢复测试。
真实异常重启、断网、租约故障、三节点滚动升级与故障恢复继续延期，不自动启用这些用例。

## 6. Agent 索引与 Artifact 专门验收

### 6.1 索引不只测 Search 返回非空

| ID | 验证层 | 必须断言 |
| --- | --- | --- |
| IDX-01 | Projector UT | common latest 的内容、全部 online Version 的协议并集、A2A capability/skill/example、artifactKinds、目录和业务字段完整 |
| IDX-02 | 生命周期 + 调度 UT | 创建、metadata/governance、publish/online/offline/delete、latest/label、A2A canonical 更新、删除均调度正确资源键 |
| IDX-03 | Runtime + 调度 UT | register/deregister/heartbeat/healthy 变化不调度目录任务、不把运行地址/状态写入文档/chunk/facet |
| IDX-04 | Repository/任务组件测试 | document/chunks/facets 完整替换；重复投影无重复数据；索引缺失后的 backfill/reconcile 可从新定义重建 |
| IDX-05 | 摘要/当前性 UT | runtime/观察时间不改 sourceDigest；metadata、版本目录、定义 digest 等应观察事实改变时更新；旧文档不误判 current |
| IDX-06 | 非空 Search IT | 实际 INDEX 路径查询已发布 Agent，验证 protocol/tags/namespace/分页和 latest；不以 SCAN 绕过索引构建 |
| IDX-07 | 版本与资源变化 IT | 添加第二 online Version、切换 latest/label、下线、disable/enable、删除后，目录按当前语义收敛；无重复命中 |
| IDX-08 | 迁移联动 | 迁移完成定义和索引 readiness；缺失/陈旧索引阻止相关 gate；正常补建后管理、RAD Search、ARD 对应 |

组件测试核对调度与持久索引内容，外部 IT 只通过 API 断言实际检索结果，不通过内部数据库或
service 读取代替 Search。准备公开资源并使用符合环境的身份，异步结果采用有界轮询。

### 6.2 Artifact 必须实际返回定义

- 用固定 VersionDetail 构建两种表示：Nacos Agent 三层声明结构、原生 A2A AgentCard。
- 实际导出 JSON 通过各自 Schema；不包含 Runtime、健康、bindings、管理状态或观测字段。
- 按 exact version/contentDigest/representation 获取；不匹配、offline、表示不存在时沿用 ARD 错误。
- 运行地址更新不改变同版本 Artifact；版本定义对应的 digest 与 URL 一致。
- 由真正 ARD HTTP 入口读取至少一个非空公开 Agent Artifact，避免只依赖 builder Mock。

**已知覆盖缺口不能被历史报告掩盖：**AiResourceSearchClientOpenApiITCase 的主要复合用例、
ArdAdaptorOpenApiITCase 的 live 成功用例当前因 DAUTH-F03 显式 Disabled。
优先在现有类中增加独立的公开 Agent 非空成功场景，保持默认鉴权，不解除原私有资源失败用例，
不通过关闭鉴权替代。若新公开场景也被同一产品问题阻塞，记录实际失败与剩余缺口；UT/Schema
可以提供部分证据，但不能把 INDEX/Artifact 真实链路验收标为通过。

## 7. 入口、传输和部署矩阵

不用把所有维度做完整笛卡尔积：核心 JSON/字段/注册流程覆盖双传输与双 adapter，其他业务场景
在一条真实主路径详测，再以另一传输的同构样例验证边界。映射关系如下。

| 维度 | 必须执行 | 断言边界 |
| --- | --- | --- |
| 新 Agent 入口 | `AiService.agent()` 的 publish/search/discover/subscribe/register/deregister | AiService 没有继承新增 AgentDiscovery 方法，不编造对应直接调用 |
| 旧 A2A 入口 | AiService 直接旧方法、agent() 继承的旧方法、已发布 SDK/已编译旧字节码 | URL/SERVICE、单条/批量、latest/version、监听、受控错误；保留旧 gRPC 路径 |
| Agent transport | 显式 GRPC、HTTP；AUTO 路由回归；资源覆盖总配置/缺省继承 | raw HTTP 与 gRPC typed 结果同构；Endpoint.transport 与 Nacos 通信 transport 分开测试 |
| 新客户端 JSON | 默认 adapter、jackson3-sdk-test | 注册新字段、复杂发现/Watch、非空 Runtime、定义发布与 Maintainer 读回 |
| 管理入口 | Admin、Maintainer、Console inner 与独立 Console remote | 根包装/身份、namingServiceRef、字段缺省和错误映射一致 |
| 身份与隔离 | 默认/显式 namespace，同名不同 namespace；默认鉴权的合法、无权限和错误身份 | ClientRequest 不公开 namespace；服务端身份不能由忽略字段规则绕过 |
| 其他资源 | 已有 AiTransportResourceMatrixJavaSdkITCase 的资源与普通 Naming 主流程 | 共用 HTTP Client/heartbeat、序列化基础工具没有被 Agent 改动破坏 |

不增加“新模型兼容 3.3 BETA 服务端”组合。原版稳定 A2A SDK 与当前服务端、旧字节码与当前 SDK
属于不同的兼容保证，仍需保留已有验证。可选独立旧服务端反向组合沿用既有边界。

MODEL-D01 继续保持现状：无 version/label 的发现中，协议描述/来源配置取 latest，而运行地址
兼容目标可覆盖全部 online Version；不把旧在线版本独有协议缺失的问题顺带修掉。
需要专用审核 Pipeline 插件的 reviewed→publish/redraft 成功路径沿用既有环境限制：组件测试
覆盖状态转换，普通 standalone 覆盖已有成功路径及受控错误，不宣称已执行缺少插件的成功分支。

Console 前端需验证真实提交 payload 和实际渲染数据，不能只查源文件里有没有字段名。
覆盖新建、草稿反填/编辑/复制、协议与来源顺序、运行地址空/非空/disabled、健康显示及 Naming 跳转。
发布前用本轮后端与构建后的页面完成一次这些关键交互；mock 前端测试不能替代该联调记录。
`npm run build` 当前会同步 static/next，验收时核对 dist 与后端静态目录，而不是手改生成文件。

## 8. 分阶段实施与退出条件

以下是后续改造顺序，不代表本轮授权提交；当前仍不提交。

| 阶段 | 内容 | 退出条件 |
| --- | --- | --- |
| T0 契约与基线 | 固定六入口样例、字段规则、索引/迁移输入与预期，补既有语义缺失测试 | 基线可复跑；新行为预期与既有失败/跳过分开记录 |
| T1 结构统一 | 一起替换 API、server、SDK、Console、迁移/索引/导出中的类型及访问路径，更新 Schema/样例 | 所有受影响生产/测试源码编译；EP-01/02/04～14 的定向 UT 及 Schema 实例通过；同一阶段不能只改 API 留调用方不可编译 |
| T2 输入语义 | 单独完成 healthy 可写和维护字段忽略的归一化/校验/mapper 改动 | EP-02/03 的字段隔离和双传输真实注册通过；Naming 活性行为无隐式改变 |
| T3 外部流程与交付 | OpenAPI/SDK/Maintainer、索引/Artifact、迁移专用环境、Console 联调、发行包 | 16 组有对应证据，必须执行项没有无解释 skip，差异仅限已确认契约 |

每次变更优先运行受影响 UT，通过后再进入下一层。阶段性运行有明确输入/目的；没有新改动或
未解失败时不反复跑同一批次。修复影响公共 serializer、canonicalizer 或 mapper 后，重新执行其
所有上下游定向测试；最终整体通过一次即可。Java 格式化顺序遵循仓库要求：Spotless apply、
Spotless check，再执行相关验证。

### 8.1 执行入口和构件要求

根据现有 POM 使用以下命令入口；实施时补充具体 `-Dtest`/`-Dit.test` 和环境参数。
不把下面未运行的命令当作验证记录。

```bash
mvn -pl api,ai,client,maintainer-client,console,ai-registry-adaptor -am -DskipTests test-compile
mvn -pl test/openapi-test -Pintegration-test -DskipTests=false verify
mvn -pl test/java-sdk-test -Pjava-sdk-integration-test -DskipTests=false verify
mvn -pl test/java-sdk-test -Pjava-sdk-integration-test,jackson3-sdk-test -DskipTests=false verify
mvn -pl test/maintainer-sdk-test -Pmaintainer-sdk-integration-test -DskipTests=false verify
mvn -pl test/maintainer-sdk-test -Pmaintainer-sdk-integration-test,jackson3-sdk-test -DskipTests=false verify
```

UT 为 api/ai/client/maintainer-client/console/ai-registry-adaptor 中所有受影响 Agent/A2A/索引/导出
及共同基础测试；Naming mapper/health 桥接即使不改 Naming 代码，也补执行关联的 Naming 组件测试。
不跳过 RAT、Checkstyle、SpotBugs 或 Spotless 来获得绿灯。API/Client 维持 Java 8 编译目标。
正式 shaded client、bootstrap/server 需 clean 构建；保存 API/AI/Console/adaptor 的嵌入 jar 校验和，
确认运行的是本轮新包。已有旧 jar 曾导致错误验收，因此该检查列为固定步骤。

外部 IT 不在测试类内启动 Spring/Nacos；执行器启动隔离 standalone，记录数据库、模式、
鉴权、API/Console/ARD 端口、JSON adapter、传输和测试选择。migration 的状态准备复用原流程，
不在普通 IT 中直接改全局迁移 marker。所有资源带唯一前缀，先登记清理再做断言，关闭客户端。

### 8.2 完成标准

- 当前 16 个验收组全部有可追溯的场景、具体断言与实际结果；不能只记录总通过数量或行覆盖率。
- 新增核心序列化、复制、字段规则、健康映射、迁移比较和索引投影分支有直接测试；JaCoCo 用于
  发现遗漏，不用 getter 覆盖或平均覆盖率替代场景。未覆盖的受影响可执行分支逐项说明。
- Schema 正反实例、编译、静态检查、核心 UT、普通 IT、迁移正常流程和 Console 联调通过；
  必选测试全跳过、只有空数据成功或使用陈旧构件均不算通过。
- 测试 XML 按方法/参数去重留档，记录新增/删除/跳过差异和每次失败原因；重跑通过不能抹去首轮失败。
- 保持故障恢复/集群延期、MODEL-D01 以及私有 Search/Artifact DAUTH-F03 的独立状态。
  若某项必选功能仍未实际验证，报告该项未完成，不能声明 Agent/A2A 全链路已经验证无回归。

这套机制用于约束本次改造的回归风险，不作“任何环境都绝无故障”的保证。

## 9. 文档、规范与覆盖登记

实施时同时更新中英文 Agent management/API/storage、RAD、A2A migration 相关模型引用，
以及管理、RAD、内部存储和 Artifact Schema。正常 A2A wire、索引业务语义、Watch 规则保持。
本轮在对应规范加入评审草案与测试门槛，不提前把现行 Java/Schema 标成已经实现。

以下登记文档链接到本文，本轮不提升原有 Covered/Partial/Pending 状态或覆盖率：

- OpenAPI：CLIENT/ADMIN/CONSOLE/AI_REGISTRY_ADAPTOR_API_TEST_SCENARIOS、
  A2A_MIGRATION_API_TEST_SCENARIOS、API_TEST_COVERAGE。
- Java SDK：AGENT_DISCOVERY/AGENT_PUBLISH/A2A_MIGRATION_SDK_IT_SCENARIOS、
  JAVA_SDK_IT_SCENARIOS、JAVA_SDK_IT_COVERAGE。
- Maintainer：MAINTAINER_SDK_IT_SCENARIOS、MAINTAINER_SDK_IT_COVERAGE。

后续实施按既有 API surface 更新，不通过重复增加同一接口的行来提高比例；严格覆盖率为
Covered/total，有效覆盖率为 (Covered + Partial × 0.5)/total。不同测试层的比例分别统计。

核查依据除上述测试外，包括：
[RAD](../../../specs/zh-cn/ai/rad-protocol-spec.md)、
[管理](../../../specs/zh-cn/ai/agent-management-spec.md)、
[API](../../../specs/zh-cn/ai/agent-api-spec.md)、
[存储](../../../specs/zh-cn/ai/agent-storage-spec.md)、
[历史 A2A 迁移](../../../specs/zh-cn/ai/a2a-upgrade-migration-spec.md)、
[共享索引](../../../specs/zh-cn/ai/ai-resource-search-spec.md)、
[Artifact](../../../specs/zh-cn/ai/ai-registry-adaptor-spec.md)、
[OpenAPI 测试规范](../../../specs/zh-cn/testing/api-integration-test-spec.md)、
[SDK 测试规范](../../../specs/zh-cn/testing/java-sdk-integration-test-spec.md)。
