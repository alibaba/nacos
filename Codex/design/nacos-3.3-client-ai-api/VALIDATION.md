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

# 第一步实施验证

基线：`upstream/develop`，`3623b19db6be69545d7a5af36705b92274d10390`。
分支：`codex/client-ai-resource-services`。初始实施按要求仅做本地 commit；后续用户已明确要求提交 PR，提交前验证见文末。

## C1：接口和兼容委托

- 五个资源接口与固定 delegate；旧签名 default/deprecated；Agent 原生 API 调用点迁到 `agent()`。
- 迁移前后 36 个业务方法体去除格式差异后完全一致；未改 A2A wire 或资源业务算法。
- `mvn -B -pl api,client,test/java-sdk-test spotless:apply spotless:check`：通过。
- `mvn -B -pl api,client test -Dtest=AiServiceDefaultMethodTest,A2aServiceDefaultMethodTest,AiFactoryTest,NacosAiServiceTest,NacosAiServiceAgentSpecPropertyTest -Dsurefire.failIfNoSpecifiedTests=false`：通过，Client 89 项（含 3 项属性测试）。
- `mvn -B -pl test/java-sdk-test -am install ...`：依赖及 SDK IT test-compile 通过。包名通配符的 `-Dtest` 未匹配测试，不能算 UT 证据；UT 以以上明确类名的独立命令为准。
- 首次 reactor 校验被既有 `test/naming-test` 两个运行日志触发 RAT；已保留到 `/tmp/nacos-ai-phase1/preexisting-logs` 后重跑通过，没有关闭 RAT；收尾时已恢复这两个原有日志，保留临时备份。
- standalone、旧字节码和 transport 组合结果见下文 C2/C3。

## C2：资源 transport

- 五个 override 构造时严格校验；MCP/Agent/Prompt 独立 mode 和 AUTO 状态；Skill/AgentSpec 固定使用原 HTTP proxy；旧 A2A 保留原 gRPC。
- 连接能力检查保留旧 `NacosRuntimeException(500)` 类型、码和文案，仅增加 `CLIENT_DISCONNECT` cause 作为 AUTO 安全读回退证据。业务错误优先，写操作不重放。
- API 相关回归：536 项通过。Client 相关回归：528 项，527 通过、1 项既有 Disabled。最后连接竞态修正后的定向 UT：184 项全部通过。
- `spotless:apply` / `spotless:check`：api、client、test/java-sdk-test 通过；构建携带 Checkstyle/RAT 通过。
- SDK IT 使用 `release-nacos,!dev` 形态的 client jar；普通开发 install 的精简 POM 不携带未重定位 gRPC，不能混作外部 SDK 制品使用。
- 独立 standalone：当前仓库构建，HTTP 18488 / gRPC 19488 / Console 18080，默认鉴权开启；通过原 auth-it-identity.sh 建立业务、只读、无权限身份。
- `AiServiceJavaSdkITCase` 15 项、`McpHttpClientJavaSdkITCase` 3 项、`AgentPublishJavaSdkITCase` 5 项、`AiTransportResourceMatrixJavaSdkITCase` 6 项全部通过（29 个不同测试）。矩阵验证三种全局 mode、两组反向资源 override、gRPC 不可达时原生 HTTP 可用及旧 A2A 原异常契约。
- 真实环境暴露了旧测试断言误认为 A2A 断连返回 checked exception，已改为校验原 runtime exception；未改变旧异常契约。
- 历史增量构建残留的 datasource/client-test SPI 通过 clean 重建清除；未改服务端代码。一个既有 MCP 缓存 UT 的固定 110ms 等待改为有界条件等待，避免调度抖动。
- 原始日志与报告：`/tmp/nacos-ai-phase1/`。C3 的旧字节码、旧 SDK/旧服务端和组合回归见下文。

## C3：实际兼容 fixture 与组合回归

- 增加 opt-in `ai-api-compatibility` profile 和 `run-ai-api-compatibility.sh`；旧业务类与第三方 AiService 实现只对 `nacos-api:3.2.4` 编译，source/target 8，再直接加载旧 class 到新 API/SDK。测试捕获 UOE/501 契约和旧 override 分派。
- 真实旧 SDK 使用独立、无父 POM 的 `nacos-client:3.2.4` 依赖树；避免当前 reactor 把 client-basic/auth 等替换成 3.3。保留 `legacy-dependency-tree.txt`、classpath 和各子进程 SDK code source。
- 当前 standalone（默认鉴权开启）验证旧业务字节码 + 新 SDK、真实旧 SDK + 新服务端；另外下载官方 3.2.4 发布包，以隔离端口 HTTP 18588 / gRPC 19588 / Console 18180 验证新 SDK + 无 RAD 旧服务端。旧实例关闭鉴权，仅供此一次性 smoke。
- 四个二进制/版本组合均通过；旧 MCP release/query/订阅和取消、旧 A2A Card/release/Endpoint 注册投影均有真实服务端断言。没有在 JUnit 中启动服务端。
- 资源矩阵从 6 项扩至 10 项：新增公开 Factory 对六个非法显式 mode 的受控异常，及 grpc/http/auto 下 Prompt/Skill/AgentSpec 缺失恢复、MD5 不重复通知、ZIP 内容、新旧入口交叉取消、重订阅与重复 shutdown 后停止通知。
- 轮询 fixture 的 Skill 后续版本按当前服务端契约显式指定 basedOnVersion，省略初始 skillCard，再 updateDraft；没有为测试改资源业务逻辑。

| 验证批次 | 结果与范围 | 日志 |
| --- | --- | --- |
| 默认 JSON C2 基础 IT | 29 个不同用例全部通过；AiService 15、MCP HTTP 3、Agent 发布 5、初始资源矩阵 6 | `c2-it.log`（首次失败已定位为旧 A2A 异常类型断言）、`c2-matrix-it.log`（矩阵修正后 6/6） |
| 默认 JSON C3 组合 | 22 项：21 通过、1 项既有 DAUTH-F05 禁用；二进制 4、轮询 3、鉴权 3、Agent 定向 12 | `c3-final-it.log` |
| Jackson 3 完整定向组合 | 52 项：51 通过、1 项既有 DAUTH-F05 禁用，0 失败/错误；AiService 15、MCP HTTP 3、Agent 发布 5、二进制 4、矩阵 10、鉴权 3、Agent 定向 12 | `c3-jackson3-it.log` |
| 兼容启动器最终调整 | 默认 JSON 5/5（含新增 Factory 边界），Jackson 3 二进制 4/4；子进程通过私有环境继承 IT 普通业务身份，新 SDK 使用指定 JSON adapter | `c3-final-fixture-default.log`、`c3-final-fixture-jackson3.log` |
| 轮询用例最终并发检查 | 回调先更新计数再发布可见事件，消除测试等待条件与计数的竞态；默认 JSON 三种 mode 3/3 通过 | `c3-last-polling-it.log` |
| API/client 最终静态检查 | compile、RAT、Checkstyle、SpotBugs、Spotless 全通过；SpotBugs 0 个问题 | `final-static-checks.log` |
| 根模块文档许可证检查 | `mvn -B -N apache-rat:check` 通过，未关闭检查 | `root-rat.log` |

### 验收项的实际覆盖边界

| 计划项 | 结论 |
| --- | --- |
| P01–P04 | API/client UT、公开接口 IT、旧实现及旧调用者字节码通过；新 Agent 调用迁移完成，编译通过。 |
| P05–P09 | 配置 UT、公开 Factory 错误、三总模式/两反向 override/不可达 gRPC 的原生 HTTP IT、资源 HTTP 退化及旧 A2A 原错误契约通过。 |
| P10–P11 | 独立 AUTO 状态与 pin/首用探测/连接竞态 UT 通过；真实 STARTING→HTTP、三 transport 鉴权 IT 通过。连接可用 AUTO Watch 用例因已有 DAUTH-F05 禁用，不计通过。 |
| P12 | Endpoint manager/共享 HTTP coordinator 的相关 UT 和普通 MCP/Agent publication/注销/关闭 IT 通过；真实 shared Agent/MCP 50404 重启 replay 所在既有方法受 DAUTH-F05 禁用，本轮未解除，保留缺口。 |
| P13 | namespace、参数、旧/新入口共享状态和取消、polling 三资源恢复、重复 shutdown、普通 Naming 隔离对照通过；既有 Agent Watch 身份问题不在本轮修复。 |
| P14 | 硬门禁通过：只对已发布 3.2.4 API 编译的第三方实现和旧业务 class 在新 SDK 上运行，无重新对新 API 编译。 |
| P15 | 代表线通过：真实 3.2.4 SDK 与新 SDK 连接当前服务端，新 SDK 连接官方 3.2.4 服务端，范围限旧 MCP/A2A。 |
| P16 | 当前 CANONICAL 服务端旧 A2A wire、exact version/multi-version legacy interoperability 和旧 SDK smoke 通过；专用 A2A/MCP migration-it.yml 的 SYNCING/QUIESCING/cutover、shadow、三节点与重启流程未运行，不能宣称这些环境重新验证通过。 |

未改任何 `@Disabled`：本轮选中的既有跳过是
`AgentDiscoveryServiceJavaSdkITCase.shouldUseGrpcForAutoWhenInitialConnectionIsAvailable`
（DAUTH-F05）。完整鉴权套件的 DAUTH-F04 和其余 DAUTH-F05 不在本轮通过范围。
此次是第一步定向验证，不是全仓 UT/全量 SDK IT 或完整迁移认证。

### 可复现入口与报告

- 执行方法和历史依赖边界：`test/java-sdk-test/AI_API_COMPATIBILITY.md`。
- 本次命令均使用 JDK 17；API/client 编译仍遵守 Java 8 target，旧 fixture 也明确为 8。
- 本次原始构建/IT 日志位于 `/tmp/nacos-ai-phase1/`；JUnit XML 与子进程日志在 `test/java-sdk-test/target/failsafe-reports/`，组合批次另行复制到临时报告目录以免后续定向运行覆盖。
- 不提交凭证、服务端数据、下载的发布包或 target 产物。旧版本测试依赖是 opt-in，不影响正常 SDK IT 的依赖树。

本次默认 JSON 的多批次结果合并去重，与 Jackson 3 对应相同的 52 个用例：
51 项通过、1 项既有 Disabled。默认组合分批运行，不将重复执行计为新增覆盖。
所有新增兼容/轮询用例均启用并通过，已有 migration/reliability 环境用例没有被改成假通过。

收尾：已按 PID/目录核验后 SIGTERM 关闭本轮两个隔离服务端（33581 / 42300），
HTTP/gRPC/Console 六个端口均已释放；原始报告保留，不再有本任务的服务端后台进程。

## Review 修正：Client 入参移除 namespaceId

- `searchAgents` 改用 `model.agent.AgentSearchQuery`；注册/注销分别改用
  `AgentEndpointRegistration` 和 `AgentEndpointDeregistration`。三个输入类型均没有
  namespace 字段或访问器，也不继承原传输模型，不保留带 namespace 的公开重载。
- 仅 3.3 未发布的原生 Agent 输入签名改变，旧 A2A/3.2.x API 不变。
- Client 委托入口复制输入并将实例 namespace 写入原有 Search/Endpoint 传输 DTO；
  深拷贝、校验和 canonicalization 复用原实现。原 namespace 冲突校验随不再可设置的字段删除。
- 没有修改服务端、Maintainer、HTTP/gRPC wire DTO 或内部 publication/redo 算法。
  本次 IT 复用上轮已构建的 3.3 服务端，直接验证新 Client 与原协议互通。
- API 契约/委托 UT：14 通过；Client 复制/委托 UT：101 通过。
- wire 模型/校验 UT：32 通过；HTTP/gRPC Proxy 和 publication manager UT：65 通过。
  两批共 212 个不同 UT 通过。整理测试 import 后，3 项 API 契约用例再次通过。
- api/client 的 RAT、Checkstyle、SpotBugs、Spotless 通过；Java 8 target 编译和 SDK IT
  test-compile 通过。安装的是 `release-nacos,!dev` SDK 制品。
- 默认 JSON 定向 IT：24 项，23 通过、1 项条件跳过。通过项包括三模式 namespace 隔离、
  注册/注销、输入 JSON 不变、搜索过滤/分页、参数边界、资源/鉴权矩阵和旧字节码兼容。
  跳过的是 `newSdkRetainsOldWireOnDisposableOldServer`：本轮未启动 3.2.4 服务端，
  上轮证据不计为本轮执行；当前服务端上的旧 SDK 与旧调用者兼容仍实际执行并通过。
- 本轮日志位于 `/tmp/nacos-ai-namespace/`：`build-ut.log`、`wire-static.log`、
  `default-it.log`、`jackson3-it.log`、`final-api-contract.log`；沿用已有 Disabled，
  不扩大到专用 migration/restart/cluster 场景。

- Jackson 3 运行同一组 24 项定向 IT：23 通过、1 项同样的旧服务端条件跳过，0 失败/错误。
  `shouldSearchDiscoverAndIsolateNamespaces` 的三个 mode 在两种 JSON 下均实际通过。
- 本轮修复作为第四个独立本地 commit 交付，未改写此前三个 commit，未推送或创建 PR。
- 本轮隔离服务端 PID 48972 已正常关闭，HTTP/gRPC/Console/Registry 等六个测试端口均释放。

## Review 加固：多项注销与 transport 矩阵

- 只修改既有 PublicationManager UT、AgentDiscovery SDK IT 和测试文档，生产代码与接口不变。
- UT 在 HTTP/gRPC 两种 owner 下注册三个 Endpoint，一次移除两个已知自然键并混入未知键；
  验证只提交完整剩余 Batch、没有调用整份注销、没有重新选择 owner，重复注销不发额外请求。
  保留项的 URI、transport、priority、weight、metadata 和 Batch 版本字段保持不变。
- IT 将完整注册、幂等、覆盖、单项/多项部分注销、最后一项注销、批量全部注销和重复注销
  参数化为 grpc/http/auto。多项场景验证 `[E1,E2,E3] - [E2,unknown,E1] = [E3]`，
  输入 JSON 不变，保留项字段与 Runtime Version bindings 不变，另一 protocol 的 Endpoint
  保留到它自己的批量注销。
- `AgentEndpointPublicationManagerTest`：33 项全部通过，0 失败/错误/跳过。
- 默认 JSON 与 Jackson 3 定向 IT：各 3 项全部通过，分别为 grpc/http/auto，均为 0 失败/错误/跳过。
- client 与 test/java-sdk-test 的 Spotless apply/check、编译、RAT、Checkstyle 全通过。
- 日志目录：`/tmp/nacos-ai-endpoint-tests/`，包括 `format.log`、`ut.log`、
  `default-it.log` 和 `jackson3-it.log`；两种 adapter 的 XML 单独保存。
- 可复现：JDK 17，`mvn -B -pl client test -Dtest=AgentEndpointPublicationManagerTest`；
  SDK IT 使用 `-pl test/java-sdk-test -Pjava-sdk-integration-test -DskipTests=false
  -Dit.test=AgentDiscoveryServiceJavaSdkITCase#shouldReplaceAndPartiallyDeregisterCompletePublications verify`，
  Jackson 3 再启用 `jackson3-sdk-test`。本轮复用鉴权开启的隔离服务端 HTTP 18488 / Console 18080，
  普通读写身份运行 Client，管理员只用于 fixture，凭证通过私有环境文件传入。
- 按用户要求暂缓部分注销后的故障恢复加固，没有新增故障注入或修改既有 Disabled；
  文档明确区分通用 replacement/redo UT 与尚未直接验证的部分注销故障恢复组合。
- 本轮隔离服务端 PID 92467 已正常关闭，六个测试端口均已释放；作为第五个独立本地 commit 交付，不推送、不创建 PR。

## PR 提交前验证

- 关联现有议题 #14804，目标分支为 `alibaba/nacos:develop`。
- 五个实现和 review 提交已无冲突 rebase 到最新 `upstream/develop`
  `88cf7477498c70f699407f5b37aff6f9367eca77`；新增上游内容仅为 UI 依赖更新。
- 本机没有 `mvnd`，使用 JDK 17 和 Maven 运行 `.github/workflows/ci.yml`
  的同等完整检查：
  `mvn -B clean compile apache-rat:check checkstyle:check spotbugs:check spotless:check -e
  -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn`。
- 为避免既有运行日志触发 RAT，检查期间将 `test/naming-test/derby.log` 和
  `test/naming-test/logs/access_log.2026-09-08.log` 原样保留到临时目录，结束后恢复；
  未关闭任何静态检查。日志保存在 `/tmp/nacos-ai-pr-submit/full-static.log`。
- 提交前对齐 CI 的 Endpoint 软水位 3：正常注销用例显式设置 Client 水位 3，
  并调整另一 protocol 的扩容顺序，避免把正常注销流程误写成容量拒绝测试；服务端也以水位 3 定向复验。
- 完整 CI 同等检查：61/61 模块通过，总耗时 12 分 16 秒；两份既有日志已按 SHA-256 核验后原样恢复。
- 水位 3 定向复验：默认 JSON 与 Jackson 3 各 3/3 通过，0 失败/错误/跳过；SDK IT 编译、RAT、Checkstyle、Spotless 通过。
- 本轮水位验证服务端 PID 23068 已关闭，六个端口均释放；生产代码不变，CI 水位适配作为独立测试提交。


## PR Codecov 覆盖率加固

- PR #15839 在 `6db898306` 的 Codecov 反馈为增量覆盖率 46.63677%，119 行缺失或部分覆盖。
  本轮仅补充 UT 和验证记录，未改生产代码、公开契约、IT 场景或覆盖率排除配置。
- 在既有测试中补充所有 AiService 默认委托的参数、返回值、异常和 listener 透传，
  MCP 默认重载及 draft 兼容行为；三个 namespace-free Client 输入补齐完整 JSON
  round-trip、无 namespaceId、未设置字段省略和显式空列表保留。
- 加固 MCP/Prompt 路由和连接错误分类：固定 transport 失败不跨协议重放、HTTP 失败不计探测成功、
  业务错误优先于连接 cause、publication owner 不随新的 transport 选择而改变。
- 补齐 NacosAiService 具体资源委托：Skill 新旧入口的初始订阅、参数边界、最后监听器注销；
  MCP 查询异常、空初始结果，以及五类资源在初始读取返回前已经通知时不重复回调。
  此处以同步受控回调覆盖调用顺序，不新增故障注入或恢复 IT。
- 新增 68 个 UT invocation。最终同一 reactor 中 API AI 674/674 通过；Client AI
  562 项中 561 通过、1 项既有 Disabled，0 失败/错误；共 1,235 通过、1 跳过。
- JDK 17 / Java 8 target，最终命令（没有跳过静态检查）：
  `mvn -B -pl api,client spotless:apply spotless:check
  -Dtest='com.alibaba.nacos.api.ai.**,com.alibaba.nacos.client.ai.**'
  test apache-rat:check checkstyle:check spotbugs:check`。
  两个模块编译、Spotless、RAT、Checkstyle、SpotBugs 全部通过。
- 本地 JaCoCo 与 `git diff --unified=0 $(git merge-base upstream/develop HEAD)` 的新增行交集：
  整个 PR 的生产代码增量可执行行 **462/462（100%）**，这些行上的分支
  **211/211（100%）**，没有漏行或部分覆盖行。
  这是本地统计；PR 中 Codecov 的汇总由新提交的 CI 上传后重新计算。
- Codecov 列出的八个文件，在本地均达到整文件行覆盖率 100%；有分支的
  AiService、McpService、AiTransportExceptionUtils、McpTransportRouter 和 PromptTransportRouter
  也均达到整文件分支覆盖率 100%。额外补充的 NacosAiService 整文件行覆盖率
  338/338，PR 增量行 239/239；剩余 6 个未覆盖分支位于本 PR 未修改的代码行，未扩大测试范围。
- 可核对证据保存在 `/tmp/nacos-ai-coverage/`：`codecov-before.md`、
  `api-before.xml` / `client-before.xml`、`api-after.xml` / `client-after.xml`、
  `patch_coverage.py`、`final-reactor.log`。本轮是 UT 加固，未重复运行已有 IT。
