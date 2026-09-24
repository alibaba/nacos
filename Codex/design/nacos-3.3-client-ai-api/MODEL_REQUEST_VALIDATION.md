# Agent 请求整合验证记录

日期：2026-09-15。分支 `codex/agent-model-consolidation`，checkpoint `429f96c70`。
本轮在既有未提交模型整合上完成请求分包及调用链调整。以下为本轮构件的实际结果，
不复用前轮通过数字。本轮未提交、未推送、未创建 PR。

## 实施范围

- `agent.admin` 下保留五个管理 Request，`agent.client` 仅保留 AgentPublishRequest；类名不重复 Admin/Client。
- 共用根包 AgentSearchRequest、AgentEndpointRegistrationBatch，业务模型不含 namespace。
- 注销公开入口为 `deregisterAgentEndpoints(agentName, protocol, List<Endpoint>)`，删除两个注销 DTO。
- base 仅保留 Metadata/Draft 两个 abstract 类；extensions 上移，共享校验归入 api.ai.utils。
- Search/Register 的 namespace 显式经过 HTTP 参数、RPC 信封、业务服务和 publication/redo 键。
- model.agent 共 31 个 Java 文件；HTTP 字段、完整替换/局部注销语义及历史 A2A API 保持。

调用关系复核：普通 client 生产代码不引用 agent.admin；maintainer-client 和 console 生产代码
不引用 agent.client。服务端内部 A2A 转换/迁移继续复用 admin 草稿输入，HTTP Form 独立解析。
本轮相对开始快照修改/删除 110 个 Java 文件，覆盖 api、auth、ai、client、maintainer-client、
console、adaptor 和外部 IT；不将前轮未提交改动计为本轮新设计。

## 新增与调整的验收

| 项目 | 测试落点 | 验收内容 |
| --- | --- | --- |
| 模型与继承 | AgentContractModelTest、AgentDiscoveryServiceDefaultMethodTest | 仅两个抽象字段基类；公共请求无 namespace；三参数注销签名 |
| RPC 信封与参数提取 | AgentClientBindingModelTest、AgentClientParamExtractorTest、AgentClientRequestHandlerTest | namespace 只在信封，默认 public / 自定义 namespace 传递一致 |
| 调用方输入隔离 | AgentModelUtilsTest、NacosAiServiceTest | Search/注册/注销复制输入，缺失或非法参数受控失败 |
| redo 隔离 | AiGrpcRedoServiceTest | 同一个 Batch 在两个 namespace 下形成独立记录，清理一条不影响另一条 |
| 同名资源隔离 | AgentDiscoveryServiceJavaSdkITCase.shouldIsolateSameAgentSearchAndPartialDeregistrationByClientNamespace | INDEX / SCAN × 默认 / Jackson 3 × GRPC / HTTP / AUTO；同名双 namespace 的 Search tags、共享注册对象、3 删 2、重复注销和全删互不干扰 |
| Schema 上下文组合 | AgentEndpointSchemaContractTest 与两种 adapter 的正反例 | namespace 由上下文与业务 JSON 组合；缺失/非法 namespace、分页、空 endpoints 仍被拒绝 |
| 完整回归 | [EP-01～EP-16 测试矩阵](MODEL_ENDPOINT_TEST_PLAN.md) | 存储/digest、索引、Artifact、生命周期、Runtime、Discover/Watch、Console、旧 A2A 与正常迁移 |

## 构建、UT 与前端

环境：macOS、JDK 17.0.8、Maven 3.9.11。用户已明确授权沙箱外执行。

| 批次 | 本轮结果 | 证据（均位于本轮临时目录） |
| --- | --- | --- |
| 受影响全矩阵 UT，按类合并最后结果 | **545 类、4988 项：4985 通过、0 failure/error、3 原有 skip** | ut-current-result.json、reports/ut-final、reports/ut-auth-schema、reports/ut-web |
| API 全量 UT | 1300 项通过；与上行重叠，不相加 | api-test-3.log |
| 两项端口 Web UT | ArdWebContextIsolationTest / ArdWebAuthenticationTest 各 1 项通过 | ut-web.log |
| 前端 UT / TypeScript / Vite | 5 组 87 项通过；构建及生成静态资源同步通过 | ui-test.log、ui-build.log |
| RAD Request Schema | Jackson 2 / Jackson 3 各 10 个正反实例通过；另有仓库 Schema UT 5 项 | schema-2.log、schema-jackson3.log、ut-auth-schema.log |
| clean 后全仓 package / install | 61 模块通过；三个外部 IT 模块编译通过 | release-package-2.log、release-install.log |
| 发行归档 | dir/tar.gz/zip 通过 | release-assembly.log、release-install.log |
| 格式与静态检查 | 受影响模块 Spotless apply → check；43 个 reactor 模块 compile / RAT / Checkstyle / SpotBugs / Spotless 通过 | format-final.log、format-auth-schema.log、static-checks.log |
| 运行/SDK 构件核对 | 1579 个 API/AI/Console/Adaptor/Maintainer 嵌入 class 与当前构建一致；API/Client/Maintainer 安装 jar 与当前产物一致 | embedded-jars.json、post-install-class-check.json |

3 个 UT 跳过为已有项：两个 SeedArchive 测试未携带所需 bootstrap ZIP 运行 fixture，
以及 NacosMcpServerCacheHolderTest.processMcpServerDetailInfoWithException 的原有 Disabled。

## 普通外部矩阵

本轮新发行包，独立 Derby 数据。服务端 18848、合并 Console 18080、ARD 19080，
独立 Console 18081 远程连接 18848。默认鉴权、独立测试身份及 AI anonymous 开启。
普通全批次使用 INDEX；完成迁移后正常停启普通实例切换 SCAN，执行专门交叉验证。

| 批次 | 通过 | 失败 | 跳过 | 证据 |
| --- | ---: | ---: | ---: | --- |
| Client/Admin/合并 Console/ARD OpenAPI，13 类 | 56 | 0 | 2 | http-agent-regression.log |
| Java SDK 默认 adapter，6 类 | 65 | 0 | 11 | sdk-default.log |
| Java SDK Jackson 3，5 个 AI 核心类 | 60 | 0 | 10 | sdk-jackson3.log |
| Maintainer SDK 默认 / Jackson 3 | 4 / 4 | 0 | 0 | maintainer-default.log、maintainer-jackson3.log |
| 独立 Console A2A/Agent | 11 | **3** | 0 | console-independent.log |
| SCAN namespace/三参数注销，默认 / Jackson 3，各三种 transport | 3 / 3 | 0 | 0 | sdk-scan-default.log、sdk-scan-jackson3.log |

OpenAPI 选择 A2aAdminApiOpenApiITCase、AgentAdminApiOpenApiITCase、AgentVersionAdminApiOpenApiITCase、
AgentRuntimeEndpointAdminApiOpenApiITCase、AuthScopeGuardITCase、ArdAdaptorOpenApiITCase、
AgentPublishClientOpenApiITCase、AgentEndpointClientOpenApiITCase、AiResourceSearchClientOpenApiITCase、
AgentDiscoveryClientOpenApiITCase、AgentWatchClientOpenApiITCase、A2aConsoleApiOpenApiITCase、
AgentConsoleApiOpenApiITCase。

Java SDK 选择 AgentDiscoveryServiceJavaSdkITCase、AgentPublishJavaSdkITCase、
AiTransportResourceMatrixJavaSdkITCase、AiServiceJavaSdkITCase、AiServiceBinaryCompatibilityJavaSdkITCase；
AuthEnabledJavaSdkITCase 另在默认批次执行。Maintainer 选择 AgentMaintainerServiceMaintainerSdkITCase。
旧 SDK 3.2.4 的独立依赖树已核对（24 个 classpath 条目，全部存在）；兼容 profile 重新编译旧 API
字节码，验证旧字节码 → 新 SDK、旧 SDK → 新服务端。真实旧服务端的可选环境没有设置。

前轮新增的公开 Watch、false/true health、3 删 2、非空管理 Runtime、INDEX 标签/版本变更、
exact digest Artifact 与 Console 定义复制均随本轮批次复验。本轮未再次手工操作浏览器页面；
前端 UT/build 与两种 Console 部署的 API 成功流程提供本轮证据。

## 正常 A2A 迁移

两个独立目录对应 shadow=true / false，端口分别为 18858/18082/19082 与 18868/18083/19083。
专用迁移环境按 migration-it.yml 关闭鉴权，普通鉴权矩阵另列于上节。
配置切换使用正常停启；没有执行真实故障恢复、进程强杀或集群用例。

| 阶段 | 验证行为 | shadow=true | shadow=false |
| --- | --- | ---: | ---: |
| LEGACY HTTP | 历史/v1 AgentCard 注册读取、Console 历史卡读取 | 3 通过 | 3 通过 |
| LEGACY SDK | release/query/subscribe、latest/version、批量 Endpoint、unsubscribe | 4 通过 | 4 通过 |
| SYNCING HTTP | 旧写入跨面收敛、namespace/冲突隔离、非法源阻止当前周期及修正后继续 | 3 通过 | 3 通过 |
| SYNCING SDK | 双布局替换/注销/容量一次计费、连接关闭清理并允许新发布者 | 2 通过 | 2 通过 |
| QUIESCING | 拒绝历史定义写入（50105），允许读取 | 1 通过 | 1 通过 |
| 跨切流 | 同一连接 HTTP/gRPC Watch、Endpoint 替换/注销及终态布局 | 1 通过 | 1 通过 |
| CANONICAL | 永久终态、版本/跨面 Search/RAD/ARD 投影及删除收敛 | 1 通过 | 1 通过 |

每种策略 15 项，总计 **30 项通过，无失败或跳过**。显式设置各状态 gating 属性，
不把禁用的迁移用例计为通过。方法位于 A2aMigrationAdminApiOpenApiITCase、
A2aUpgradeMigrationJavaSdkITCase；LEGACY smoke 复用普通 A2A HTTP / AiService SDK 类。
LEGACY smoke 的 canonical cleanup helper 无法清理旧布局；进入迁移前仅通过旧 A2A API
删除本轮前后差集内的 4 个 SDK 测试 Agent，记录精确名称，不清空配置存储或覆盖测试断言。

## 仍然存在的缺口

1. **独立 Console 的三个既有错误码断言仍失败。**50100 / 23000 / 20004 经
   ClientHttpProxy 转为普通 NacosException 并重试后，由 NacosApiExceptionHandler 的
   handleNacosException 映射成 30000，HTTP 状态码保留，与前轮一致。这两个共享生产路径相对
   checkpoint 未修改。本轮没有用旧二进制再对照，也没有放宽断言或扩大范围改动重试策略。
   失败方法为 A2aConsoleApiOpenApiITCase.testGetListAndVersionListValidationAndNotFoundErrors，
   AgentConsoleApiOpenApiITCase.testAllConsoleManagementPathsAndRuntimeNamingReference，
   AgentConsoleApiOpenApiITCase.testConsoleBindingValidationAndExplicitRuntimeNamespace。
2. DAUTH-F03 私有 Search/ARD、DAUTH-F04 错误凭据 anonymous 降级、DAUTH-F05 私有授权
   异步 Watch 等原有 Disabled 保留；PUBLIC 新模型测试不替代这些鉴权缺口。
3. 按既定范围不执行真实故障恢复、三节点/滚动/跨节点重启、真实旧服务端可选 IT，以及
   3.3-BETA 存储/导出升级。MODEL-D01 默认发现协议描述选择问题继续单独记录。

本轮自动化矩阵的结果包含上述已知失败和明确排除项。
全部本轮外部参数组合共 262 项：**236 通过、3 个既有失败、23 个既有跳过、0 error**。

## 待办 CONSOLE-ERR-01：独立 Console 错误码透传

**当前状态（2026-09-16）：错误码透传已修复并验证，按用户要求单独提交；独立 Console CI 待处理。**
Agent 模型改动已先提交 `757cd8daa`。复用原 IT 验证三项错误全部通过，旧构件对照复现
原失败；386项UT及两套Maintainer全量IT通过，扩展Naming回归发现另一项既有问题。
详见[Console 错误透传验证](./CONSOLE_ERROR_VALIDATION.md)。

以下保留2026-09-15延期时的范围和验收计划；原轮次的失败统计不追溯改写。

问题：已有 OpenAPI IT 在合并部署下通过，在独立 Console 下，业务码
50100（A2A AgentCard 不存在）、23000（草稿未经审核发布）、20004（Agent 版本不存在）
被共享 Maintainer HTTP 转发链路丢失，最终成为 30000；HTTP 404/400/404 保留。
具体用例见上方缺口清单，GitHub 工作流未覆盖独立 Console 的证据见下方部署覆盖核对。
三项失败继续保留，不放宽断言、不添加 Disabled，也不计为通过。

后续修复范围：

- ClientHttpProxy 同时解析普通 HTTP 的 message 和文件上传的 data 中的错误响应；使用
  JsonUtils，保留 HTTP 状态、业务码、错误摘要及详情，最终抛出时保留异常类型。
- 复用 NacosApiException，评估补充接收原始业务码的构造函数，使未知业务码也能透传。
- 保持现有重试、重新登录与节点切换策略；保留纯文本、空响应和非标准 JSON 的回退行为。
- getErrCode() 继续表示 HTTP 状态，保护 Pipeline 旧路径回退和 Config 未找到处理；
  保留旧 A2A 通过错误详情字段名触发的旧格式回退。

后续验收与提交：

1. 错误透传修复单独 commit，包含 UT 和相关规范说明；验证真实 HTTP 错误结构、上传、
   未知业务码、非标准响应、重试结束后的异常保留及上述兼容分支。
2. 独立 Console CI 单独 commit，复用已有 IT，补齐跨领域代表性错误场景及覆盖矩阵。
3. 原有三项失败必须通过；执行完整模型测试矩阵、Maintainer 全量默认/Jackson 3 IT，
   以及 Console 双部署回归。实现时同步相关 API/SDK 场景文档和 coverage registry。

错误码生产修复已通过本轮验收；独立 Console CI 和其他已知缺口仍独立保留。

## 中间问题与证据

- 首轮 UT 暴露旧 mock 参数下标、RPC helper namespace setter 等适配遗漏，修正后复验通过。
- 续租继续使用 DiscoveryRequest 已归一的 namespace；未把它误改成未归一的 HTTP Form 值。
- 新增同对象双 namespace redo UT 发现父类 equals/hashCode 仅比较业务对象，可能合并记录；
  已把完整 publication key 纳入比较，保留父类对状态和内容的比较，最终测试通过。
- 自动审批多次超时的命令均未执行；补充本地范围核验或按提示重试后获准。
  沙箱内两项 Web UT 的端口错误已由沙箱外实际通过结果替换。

日志和 XML 位于 `/tmp/nacos-agent-request-unification/`；before/ 保存本轮开始时 5645 个
源码/文本文件，各历史变换脚本不应重复执行。it-current-result.json 按本轮日志对应的实际
测试类/方法/参数保存结果；运行器只复制本次 Maven 执行期间写入的报告，不统计残留 XML。
迁移报告位于 migration-shadow-on/reports 与 migration-shadow-off/reports。
普通批次有非敏感的 *-command.json，记录选择器、profile、端口及 gating 参数。

验证收尾：两套迁移服务、普通服务及独立 Console 均正常退出；日志和隔离数据目录保留。
本轮未提交、未推送、未创建 PR。

## GitHub IT 部署覆盖核对（2026-09-15）

只读查询 alibaba/nacos GitHub develop，核对时 HEAD 为
`946138fb955b2c7d9d960126aaa21117201d5cfb`。以下五份工作流的 Git blob SHA 与本地文件
一致；鉴权可靠性脚本和 distribution/bin/startup.sh 也与远端一致。工作流、该脚本及
启动脚本相对模型改造前 `1f51dfcc4` 无 diff。

| 工作流 | 实际部署与执行范围 | 独立 Console |
| --- | --- | --- |
| .github/workflows/it-new.yml | 第 115 行以 startup.sh -m standalone 启动一次，运行 OpenAPI、Java SDK、Maintainer SDK | 未覆盖 |
| .github/workflows/migration-it.yml | 单机服务多阶段正常启停，运行 MCP/A2A 迁移 IT | 未覆盖 |
| .github/workflows/default-auth-reliability-it.yml | 调用 test/scripts/default-auth-reliability-it.sh；start_server 仅指定 -m standalone 或 -m cluster | 未覆盖 |
| .github/workflows/pr-e2e-test.yml | cluster / standalone / standalone_auth，引用 nacos-group/nacos-e2e 的 Helm chart | 未覆盖 |
| .github/workflows/ci.yml | 编译、静态检查、UT；没有外部独立 Console 启动步骤 | 未覆盖 |

startup.sh 第 232 行将 DEPLOYMENT 初始化为 merged；-m 只设置运行模式，-d 才设置
部署类型。前三组工作流均未传 -d console，也没有单独启动 Console 进程。
旧 E2E 的外部 chart 已核对 main `475391c0496e916f35777b466f578bbcff06395d` 下
cicd/helm/templates/statefulset.yaml 和 values.yaml：没有单独 Console 工作负载或部署参数。

it-new.yml 执行 OpenAPI 全套，三个失败断言所属类在已有 IT 中，未被工作流选择器排除。
CI 访问的 8080 是 merged 进程的 Console 端口，不代表独立 Console 部署。因此这套 CI
配置覆盖 Console API 用例，但不会经过独立 Console 的 Maintainer HTTP 转发链路。
本轮本地扩展部署矩阵才覆盖该链路；上一轮 Endpoint 整合也已记录相同三项失败。
本次未修改工作流或生产代码；未重跑改造前发行包，不将源码对照记作旧二进制实测。

日报同步已在本次部署覆盖核对后完成：将验证结果及 GitHub IT 覆盖结论合并至
weekly-reports/daily/2026-09-15.md 的既有 Agent 模型任务条目。
此前三次同步尝试曾因自动权限审核超时未执行；本次实际写入成功后更新此状态。
