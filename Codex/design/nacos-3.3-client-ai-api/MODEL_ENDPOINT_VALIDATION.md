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

# Agent 地址模型统一执行记录

更新：2026-09-15。分支 `codex/agent-model-consolidation`，前序 checkpoint 为 `429f96c70`。
本轮代码、测试、前端和规范改动均未提交、未推送；未创建 PR。

## 1. 实施结果

- 已统一 `AgentCallInterface → EndpointSet → Endpoint`，Agent 包 Java 模型 **43 → 39**。
  删除两个旧 CallInterface、DiscoveryEndpoint、SnapshotItem 及不再需要的 CallInterface 基类。
  [当前关系图](MODEL_ENDPOINT_PATHS.md) 已更新，旧图保留为改造前记录。
- 管理运行地址使用 `callInterface.endpointSets[].endpoints[]`；状态和绑定平铺到 Endpoint，
  观察时间放在 Set。VersionDetail 只保存 DECLARED 地址及完整来源顺序。
- Runtime 注册接受 healthy，默认 true；false 能进入 Naming 并被查询/Watch 观察。
  bindings/enabled/state 等由 Nacos 维护，写入时忽略；读取复制保留，调用方对象不被原地修改。
- 存储读写、A2A 转换/迁移、索引、Artifact、SDK、Console 和中英文规范/Schema 已适配。
- 普通业务回归、正常迁移两种 shadow 策略、真实 Console 页面联调通过。
  **不能据此声明所有既有 Agent/A2A 功能零故障**：独立 Console 三个错误码断言失败，
  以及原有 DAUTH-F03/F04/F05 测试缺口仍保留，见 §5。

## 2. 构件、UT、静态检查和覆盖

环境：macOS、JDK 17.0.8、Maven 3.9.11；UI 使用 Node 24.15.0。

| 验证 | 实际结果 |
| --- | --- |
| 全仓发行 `mvn -B '-Prelease-nacos,!dev' -DskipTests=true clean install` | 通过；所有消费者生产/测试源码编译，正式 shaded SDK 与运行包一致 |
| API/AI/Client/Maintainer/Console/Adaptor 定向 UT，含全部 AI、索引、迁移与关联 Naming UT | 按测试类合并最后结果：**544 类，4959 项，4956 通过、3 跳过，0 failure/error** |
| 受影响模块 Spotless apply → check | 通过；新增 UT/IT 后对相应模块再次执行 |
| 6 个受影响生产模块 RAT / Checkstyle / SpotBugs / Spotless | 通过；随后只补测试和文档，已打包生产代码未再修改 |
| 新增/修改生产可执行代码 JaCoCo | **208/208 行，110/110 分支**；不是整个模块或整个 PR 的覆盖率 |
| 运行包核对 | API/AI/Console/Adaptor 嵌入 jar 与本轮产物 SHA-256 一致；31 个受影响 Java 源的当前主 class 与已打包 class 一致 |
| 前端 UT | 5 个文件、87 项通过 |
| TypeScript + Vite 生产构建 | 通过；4 个 static/next/js 文件由构建同步 |

覆盖检查补上定义中的重复/错误来源 Set、定义 healthy、Runtime Set 形状、RAD 管理字段拒绝、
无声明地址时保留空 DECLARED 来源、存储非法 Set/缺失 endpoints 等分支。
没有用 getter 测试代替这些边界验证。

3 个 UT 跳过均为既有项：两个 SeedArchive 测试运行时未携带 bootstrap ZIP，
以及 `NacosMcpServerCacheHolderTest.processMcpServerDetailInfoWithException` 原有 Disabled。
Naming 活性规则通过 HttpConnectionBasedClientTest、EphemeralClientOperationServiceImplTest 回归。

## 3. 普通外部 IT 和页面

普通 IT 使用新发行包及隔离 Derby：服务端 **18848**、合并 Console **18080**、ARD **19080**，
独立 Console **18081** 远程连接 18848。默认鉴权、独立测试身份、AI anonymous 开启、
RAD Search **INDEX**；没有用 SCAN 或关闭鉴权替代普通索引验证。

| 批次 | 按方法/参数合并最后结果 | 范围 |
| --- | --- | --- |
| Client/Admin/合并 Console/ARD OpenAPI | **56 通过，2 跳过** | 13 类普通回归和新 Console 成功流程；原私有 Search/ARD Disabled 保留 |
| 独立 Console A2A/Agent | **11 通过，3 失败** | 非空 Runtime/定义复制成功；失败为 §5 的错误码链路 |
| Java SDK 默认 adapter | **62 通过，11 跳过** | 资源 transport 矩阵、新旧入口、发布/发现/端点、兼容字节码、PUBLIC Watch |
| Java SDK Jackson 3 | **57 通过，10 跳过** | 相同 AI 核心类；AuthEnabled 类只在默认批次执行 |
| Maintainer SDK 默认 / Jackson 3 | **各 4 通过** | Agent 生命周期和模型；非空 Runtime 另由 Java SDK 多项注销交叉验证 |

OpenAPI 选择 A2aAdminApiOpenApiITCase、AgentAdminApiOpenApiITCase、AgentVersionAdminApiOpenApiITCase、
AgentRuntimeEndpointAdminApiOpenApiITCase、AuthScopeGuardITCase、ArdAdaptorOpenApiITCase、
AgentPublishClientOpenApiITCase、AgentEndpointClientOpenApiITCase、AiResourceSearchClientOpenApiITCase、
AgentDiscoveryClientOpenApiITCase、AgentWatchClientOpenApiITCase、A2aConsoleApiOpenApiITCase、
AgentConsoleApiOpenApiITCase。

SDK 选择 AgentDiscoveryServiceJavaSdkITCase、AgentPublishJavaSdkITCase、
AiTransportResourceMatrixJavaSdkITCase、AiServiceJavaSdkITCase、AiServiceBinaryCompatibilityJavaSdkITCase、
AuthEnabledJavaSdkITCase；Maintainer 选择 AgentMaintainerServiceMaintainerSdkITCase。
旧字节码批次先按兼容模块 POM 生成 3.2.4 classpath：旧 SDK → 新服务端、旧调用字节码 → 新 SDK
均通过；新 SDK → 真实旧服务端的可选测试未设置环境。

新增/加固的非空流程：

- `shouldReplaceAndPartiallyDeregisterCompletePublications`：GRPC/HTTP/AUTO 下 3 删 2，
  剩余 metadata/weight/healthy/bindings 保持，伪造管理字段无效，Maintainer typed Runtime 交叉读取。
- `testReportedHealthAndIgnoredManagementFieldsAcrossReadSurfaces`：直接 HTTP false、
  ACTIVE heartbeat 保持 false、Admin/Discover 读取、完整替换 true。
- `shouldWatchPublicAgentHealthAndPartialDeregistrationWithUnifiedModels`：PUBLIC 资源在
  GRPC/HTTP × 默认/Jackson 3 下注册三项、移除两项、false → true、最后移除、取消订阅；
  回调与同步查询 fingerprint 一致，管理状态不泄漏。
- `testPublicAgentIndexTracksUnifiedVersionCatalog`：真实 INDEX 的 Search/RAD Search、
  tags/namespace、增加版本、latest/自定义标签、offline/delete 收敛。
- `testPublicAgentIndexAndUnifiedArtifacts`：真实 ARD 非空公开条目、两种表示、exact digest
  制品、新 DECLARED 结构、无运行/管理字段、offline 后不可读。
- `testUnifiedDefinitionAndNonEmptyRuntimeAcrossConsoleDeploymentModes`：两种部署均完成
  定义发布、非空 unhealthy Runtime、Naming 引用、basedOnVersion 复制及摘要核对。

PUBLIC fixture 通过现有 A2A API 创建，再走新 Agent API；没有解除原私有资源 Disabled。

真实页面使用发行包 `/next/`：登录、新建、编辑声明 URI/来源顺序、草稿反填、发布后查看
DECLARED 及两个 Runtime 地址、AVAILABLE/UNHEALTHY、Naming 跳转、下线后 DISABLED、
基于 1.0.0 创建 2.0.0 草稿并保留原定义。原始 API 也核对了保存结构。
临时页面资源为 `endpoint-model-ui-0915`。

## 4. 正常 A2A 迁移

复用 migration-it.yml 阶段准备；两个独立新数据目录分别使用 shadow=true/false，
端口为 **18858/18082/19082** 和 **18868/18083/19083**。
专用迁移环境按原 workflow 关闭鉴权，普通默认鉴权结果独立由 §3 提供。
正常停启用于 LEGACY → AUTO 配置切换；没有启用故障重启或集群用例。

| 阶段 | 实际用例 | 结果 |
| --- | --- | --- |
| LEGACY HTTP | Admin legacy/v1 AgentCard 注册读取；Console legacy 注册读取 | 3 通过；最初另执行的两项旧更新/列表流程也通过，准备问题见 §6 |
| LEGACY SDK | release/query/subscribe、latest/version、批量 Endpoint、unsubscribe | 4 通过 |
| SYNCING HTTP | HistoricalMutationConvergesAcrossCanonicalSurfaces、NamespaceServiceAndCanonicalConflictRemainIsolated、MalformedHistoricalSourceBlocksOneCycleAndRecovers | 3 通过 |
| SYNCING SDK | DualMaterializeReplaceDeregisterAndChargeCapacityOnce、CleanBothLayoutsOnDisconnectAndAllowFreshPublisher | 2 通过 |
| QUIESCING，两种 shadow | QuiescingFencesHistoricalMutationsAndKeepsReadsAvailable | 各 1 通过；旧定义 mutation 为 50105，允许的读取继续可用 |
| 跨切流，两种 shadow | KeepGrpcHttpWatchAndEndpointAvailableThroughPermanentCutover | 各 1 通过；同一连接 Watch/替换/注销正常，运行布局符合策略 |
| CANONICAL，两种 shadow | TerminalCanonicalMarkerAndCrossSurfaceProjectionArePermanent | 各 1 通过；终态、版本更新、Search/RAD/ARD、删除收敛 |

HTTP 方法在 A2aMigrationAdminApiOpenApiITCase，SDK 方法在 A2aUpgradeMigrationJavaSdkITCase。
显式设置 syncing/prepare/verify 及 SDK 对应开关；上表方法实际执行，未把 gated skip 算成通过。

## 5. 16 组验收状态与剩余问题

| 组 | 实际状态与证据 |
| --- | --- |
| EP-01 模型/Schema | 通过：固定 JSON、Schema 正反实例、Validator、ClientRequest 边界 |
| EP-02 读写/复制 | 通过：Canonicalizer/AgentModelUtils、直接注册与双 transport typed 读回 |
| EP-03 healthy | 通过：mapper/registry/Naming UT、直接 HTTP、SDK/Watch false → true |
| EP-04 存储/digest | 通过：固定 bytes、读回、非法形状、显式空 Set、摘要/Artifact Schema |
| EP-05 生命周期 | 通过：管理/发布 UT、Admin/SDK/Maintainer/Console 外部流程 |
| EP-06 发布/注销 | 通过：多发布者、预注册/替换、3 删 2、最后移除、transport 矩阵 |
| EP-07 Runtime | 通过：无定义/空结果、非空 typed 状态/绑定/时间、两种 Console 部署 |
| EP-08 Discover | 本轮契约通过；MODEL-D01 保留，没有修改默认协议描述选择算法 |
| EP-09 Watch | 新模型/PUBLIC 四组合通过；私有授权异步 Watch DAUTH-F05 缺口保留 |
| EP-10 旧 A2A | 通过：普通及 LEGACY API/SDK、AiService 直接/资源入口、旧 SDK/字节码 |
| EP-11 迁移 | 正常流程通过：相关 UT、SYNCING/QUIESCING、两种 shadow 切流与终态 |
| EP-12 索引 | 组件/公开 INDEX/迁移联动通过；原私有跨类型 DAUTH-F03 仍 Disabled |
| EP-13 Artifact | Schema/组件/公开 ARD 通过；原私有 live DAUTH-F03 缺口保留 |
| EP-14 Console | 双部署成功流程及页面通过；独立 Console 3 个错误码断言失败 |
| EP-15 transport/adapter/auth | 资源矩阵/双 adapter 通过；DAUTH-F04/F05、远程错误映射未通过 |
| EP-16 发行/消费者 | 通过：clean release、静态检查、前端打包、嵌入 jar/当前 class 核对 |

**独立 Console 错误码丢失：**服务端原始 50100、23000、20004 最终成为 Console 30000。
正文含原 Result，但外层变成 “No available server after 3 retries”。源码路径为
maintainer-client 的 `ClientHttpProxy.executeSyncHttpRequest` 将远程错误转换为普通
`NacosException` 并重试，再由标注 `@NacosApi` 的 Console 接口使用的
`NacosApiExceptionHandler.handleNacosException` 映射为业务码 30000，保留 HTTP 状态码。
此前将最终处理器写为 `ConsoleExceptionHandler.handleException` 不准确，在此纠正。
这两个生产文件相对 checkpoint 无 diff，属于现有通路；本轮没有另用旧二进制复现。
保留三条严格失败断言，未添加 Disabled/放宽预期，也未扩大模型改造去修改共享重试策略。
2026-09-15 用户决定等当前 review 改动全部完成后再修复；后续范围和验收记录于
[请求整合验证记录的 CONSOLE-ERR-01 待办](MODEL_REQUEST_VALIDATION.md)。

**原有跳过与延期：**DAUTH-F03 私有 Search/ARD 两项 Disabled；DAUTH-F04 错误凭据降级
anonymous 一项 Disabled；DAUTH-F05 授权异步 Watch 的 existing/AUTO/版本演进/容量等
原用例仍 Disabled。PUBLIC 新流程不替代私有授权缺口。真实故障恢复、三节点/滚动/跨节点
重启继续延期；MODEL-D01 单独保留；不测 BETA 存储/导出升级；真实旧服务端可选 IT 未执行。

## 6. 中间失败与证据位置

保留每轮日志，最终通过不抹去中间问题：

1. 初轮 Adaptor 4 个错误来自 SchemaRegistry 错选 JSON 文本重载、Artifact fixture 缺少定义
   字段；改用 SchemaLocation、补完整 fixture 后通过。Console 声明距离/Collections import
   问题已修复；最终整体 UT 通过。
2. INDEX IT 最初错误断言 RAD Search 含 namespace；改为普通 Search 核对 namespace、
   RAD Search 核对不透出，并等待标签索引收敛，重跑通过。
3. SDK 旧健康边界改为新契约要求的“注销禁止 healthy”；旧字节码批次先缺预生成 classpath，
   按兼容模块 POM 补齐后通过。
4. 新 Console 测试最初误把 Client 请求发往 Console 端口，改为服务端 BASE_URL，两种部署通过。
5. LEGACY 误选了带 canonical Console 断言的 v1 用例，旧读取成功后新管理查询失败；改为
   纯历史 API 选择。旧更新/SDK 清理还残留 6 个测试定义、13 条历史 Config，其中有缺失版本
   引用，阻止 SYNCING 全局 failed 归零及 orphan 清理。只清除本次隔离环境精确识别的残留，
   随后 3 项 SYNCING HTTP 均通过。
6. 新 PUBLIC Watch 测试缺 assertNotEquals import，一度阻止 shadow=false SDK 编译；
   修复、Spotless 后，该迁移与新 Watch 双 adapter 均通过。

原始日志、XML、方法/参数去重 JSON、覆盖及构件证据保存在
`/tmp/nacos-endpoint-unification/`。它是临时目录，本文保留持久结论与选择范围。
核心文件：`ut-coverage-final.log`、`ut-coverage-gaps.log`、`ut-storage-final.log`、
`release-build.log`、`static-checks.log`、`http-agent-regression.log`、`sdk-default*.log`、
`sdk-jackson3.log`、`sdk-public-watch-*.log`、`maintainer-*.log`、`console-*.log`；
迁移日志位于 migration-shadow-on/、migration-shadow-off/。

方法/参数和最后结果为 `MODEL_ENDPOINT_TEST_RESULTS.json`，新增行/分支为
`final-changed-coverage.json`，构件为 embedded-jars.json、final-compiled-class-check.json。
复跑入口是既有 Maven IT profiles/migration workflow；临时执行器和隔离参数记录于
it-runner.py、migration-runner.py。XML 只按对应日志实际执行的类读取，不统计残留 XML。

验证收尾：UI 临时 Agent/发布者已通过 API 清理，四个本次临时服务已正常退出；
日志和隔离数据目录保留供排查。工作区仍停留在 checkpoint 之后的未提交状态。

## 7. Form/AdminRequest 与 JSON 工具 review 补充（2026-09-15）

AdminRequest 是 Maintainer SDK、Console 和服务端共享的类型化入参，继续保留在 nacos-api。Form 属于 HTTP Binding：namespace 单独传递，复杂字段由 JSON 字符串转成 List/Map/模型对象，不能直接拿 Form 替代 SDK Request。当前 nacos-ai 不依赖 maintainer-client，将这些公共请求移入后者会新增服务端对 SDK 的依赖。

本轮进一步把 Agent 服务、Admin Form、相关 A2A 转换/迁移目标及 Agent 索引投影中的通用序列化改用 JsonUtils，泛型捕获改用 NacosTypeReference，共涉及 17 个生产文件。严格存储 JSON 语法检查仍使用底层 parser，以保留重复 key、尾随 JSON 等拒绝规则。没有修改 JSON 形状、默认值或摘要算法。新增非空嵌套 CallInterface/EndpointSet/Endpoint 的 Form 泛型解析测试，并复用既有存储 golden bytes、迁移、索引和错误映射 UT。

本节晚于上面的完整端到端验收。上面的发行包/覆盖/IT 数字是 JSON 工具替换前的证据，不能自动视为本节新改动的验证结果。首次检查发现重复 JsonUtils import，已修正。修正后在沙箱内离线执行 13 个相关测试类：287 项全部通过，无失败、错误或跳过；编译、Checkstyle、RAT、SpotBugs、Spotless 均通过。覆盖 Form 嵌套泛型及错误映射、存储 golden bytes/摘要与严格语法校验、持久化与发布、A2A 转换/迁移、索引、发现和 Artifact。

本轮完整 AI 模块重跑的自动审批两次超时，命令未启动；以上为实际完成的定向回归，不代表重新执行完整模块或端到端 IT，也未重新计算覆盖率。日志目录为 `/tmp/nacos-agent-json-review/`，本轮通过证据为 `scoped-validation.log` 和 `format-final.log`。
