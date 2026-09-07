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

# 默认鉴权 IT 改造中发现的既有产品问题

## 1. 目的与处理原则

本文记录 Nacos 3.3 Client API 默认鉴权与统一 IT 改造过程中暴露、但不属于本次改造范围的产品问题。
本次变更只修改鉴权默认值、IT 基础设施、测试用例、测试文档和 CI，不在未单独讨论的情况下修改这些
产品行为。

处理原则如下：

1. 曾为使整套 IT 通过而临时加入的产品修复、配套单元测试及追认式规范均恢复到
   `upstream/develop@208a317a406a065f857d40bb947b8a0a69c29def` 的行为；
2. 先以回滚后的产品代码运行完整测试，只有稳定复现失败的用例才允许临时
   `@Disabled`、`@Ignore` 或通过等价的显式条件跳过；
3. 每个被跳过的测试必须引用本文编号，说明现象、风险和恢复条件，不能删除测试或弱化断言；
4. 每项产品问题后续单独完成设计/规范讨论、实现、单元测试和定向 IT，修复合入后立即恢复对应测试；
5. 本文是问题清单，不代表对产品修复方案或契约变更的批准。

## 2. 问题清单

| 编号 | 现象与影响 | 已撤回的临时改动 | 本次 IT 处理 | 独立修复完成条件 |
| --- | --- | --- | --- | --- |
| `DAUTH-F01` | Client Skill 查询使用 `name` 参数，而当前鉴权资源解析只读取 `skillName`。在默认鉴权开启时可能解析出错误资源，造成合法请求被拒绝或资源授权边界失真。 | `AiHttpResourceParser` 的参数回退及其单测。 | 回滚后复跑 Skill Client API 的身份和资源边界场景；若失败，仅禁用精确场景。 | 先确认 Client/Admin Skill 参数契约，再修正 parser，覆盖两种入口的正向、相邻资源和越权测试。 |
| `DAUTH-F02` | MCP 生命周期写入 Manifest 后立即读取本地 Config 视图，短暂未收敛时可能把成功写入报告为失败，导致完整 MCP 生命周期 IT 不稳定。 | `McpLifecycleOperationService` 中约 2 秒的有界重读以及相应单测、MCP 中英文规范。 | 回滚后复跑 MCP create/update/publish/online 场景；只对稳定失败的场景做显式跳过。 | 讨论一致性契约和等待上限，加入可中断、有界且可观测的实现及定向测试。 |
| `DAUTH-F03` | MCP Search 的 projector、backfill 和 freshness 检查运行在线程上下文之外，却通过调用者可见性路径读取资源；Private 资源可能被误判为不存在并从索引移除。 | MCP canonical detail 入口、Search handler/resource manager 注入、backfill/search 构造调整、相关单测及 MCP 中英文规范。 | 回滚后复跑 MCP Search、回填和分页场景；记录因最终一致性导致的精确失败。 | 先确定后台任务身份与前台可见性边界，再实现 canonical 投影读取，并验证 Private 资源不会泄漏给无权限查询者。 |
| `DAUTH-F04` | Java Client 显式配置错误凭据且登录失败后，身份上下文可能为空；访问允许匿名的 API 时可能降级成匿名请求，而不是保持错误身份的 fail-closed 语义。 | `NacosClientAuthServiceImpl` 的非敏感用户名标记、配套单测及默认鉴权插件规范段落。 | 回滚后复跑 Java SDK 与 Maintainer SDK 的“错误凭据不得匿名降级”场景；若失败，保留测试并引用本编号禁用。 | 讨论登录失败后的客户端身份契约，确保不保存密码、不泄漏凭据，并覆盖登录失败、刷新恢复及匿名端点。 |
| `DAUTH-F05` | `DefaultVisibilityService` 的 SPI 已接收显式 `identity`，但 RBAC 权限检查仍只读取 `RequestContextHolder`。同步 gRPC、异步 Agent Watch 或重连线程没有对应 HTTP 请求上下文时，会误拒绝已授权的非 owner。 | `DefaultVisibilityService` 的显式 identity 权限上下文、配套单测及鉴权中英文规范段落。 | 回滚后复跑授权非 owner 的同步读取、gRPC Watch、重连与集群切换场景；稳定失败时按场景禁用。 | 讨论 Visibility SPI 的 identity 权威来源和信任边界，完成异步/同步、owner/RBAC、撤权及无凭据测试。 |
| `DAUTH-F06` | `NacosAiService.shutdown()` 未注销全局 `AiChangeNotifier`。同一 JVM 重复创建/关闭客户端时可能累积订阅者和回调，影响后续 IT 隔离及长期进程资源释放。 | shutdown 中的 notifier 注销及相应单测。 | 回滚后按真实失败结果处理依赖同 JVM 隔离的 AI SDK 用例，不以产品改动清场。 | 明确全局订阅者生命周期，验证幂等 shutdown、关闭后无回调及多实例互不干扰。 |
| `DAUTH-F07` | 同步/异步 REST template 的 debug 日志会输出完整请求 body；登录表单可能因此暴露密码。该风险独立于默认鉴权开关，但默认鉴权会增加登录请求频率。 | 两个 REST template 的 body-present 日志及相应单测。 | IT 和 CI 继续禁止 debug 输出及日志制品泄密；本项不通过放宽测试解决。 | 以独立安全修复统一 URL/query/body/header 的脱敏规则，并验证同步、异步和 multipart 日志。 |

## 3. 回滚范围

下列生产文件及其直接配套单元测试已恢复到分析基线：

- `ai`：MCP 生命周期、Search projector/backfill/freshness；
- `auth`：Skill HTTP 资源解析；
- `client-basic`：默认客户端登录失败后的身份上下文；
- `client`：`NacosAiService` shutdown；
- `common`：同步与异步 REST debug 日志；
- `plugin-default-impl`：默认 visibility 的异步身份处理。

同时撤回仅用于说明上述临时修复的 MCP、鉴权和默认鉴权插件规范段落。默认开启 auth 的正式规范、
产品默认值及测试规范仍属于本次变更。

## 4. 测试处置记录

本节在回滚后测试执行时持续更新。尚未复现的潜在问题不得提前禁用测试。

| 编号 | 测试入口 | 当前结果 | 临时禁用项 | 恢复条件 |
| --- | --- | --- | --- | --- |
| `DAUTH-F01` | OpenAPI Skill Client API | 已复现：5 个 Skill Client 功能用例通过，但精确资源授权后仍返回 403。 | `ResourceAuthorizationITCase#testAiRequestPermissionAndVisibilityGrantBoundaries` | parser 独立修复及 Client/Admin 参数边界 IT 通过。 |
| `DAUTH-F02` | OpenAPI/Java SDK/Maintainer SDK MCP 生命周期 | 未复现稳定失败：MCP publish 定向 2/2 通过，相关全量套件通过。 | 无 | 若后续出现确定性失败，再补充精确用例和复现条件。 |
| `DAUTH-F03` | OpenAPI/Java SDK MCP Search | 已复现：私有资源跨类型 Search 与 ARD 共享索引投影不完整。 | `AiResourceSearchClientOpenApiITCase#testCrossTypeSearchSpecificFacadesFiltersAndCursor`；`ArdAdaptorOpenApiITCase#testLiveAdaptorUsesSharedIndexAndExactArtifacts` | canonical 后台投影独立修复，且私有资源可见性与防泄漏 IT 通过。 |
| `DAUTH-F04` | Java SDK、Maintainer SDK auth matrix | 已复现：显式错误凭据访问允许匿名的 AI API 时降级为匿名成功。 | `AuthEnabledJavaSdkITCase#shouldRejectInvalidCredentialsInsteadOfDowngradingToAnonymousAi`；`AuthEnabledMaintainerSdkITCase#shouldFailClosedForInvalidCredentialsOnAnonymousAiRead` | 客户端登录失败 fail-closed 契约及两套 SDK IT 通过。 |
| `DAUTH-F05` | Java SDK Agent Watch 与可靠性套件 | 已复现：5 个普通 Watch/版本/容量场景、standalone 重启恢复以及 cluster 固定节点初始读取失败；cluster rolling restart 与 peer restart 通过。 | `AgentDiscoveryServiceJavaSdkITCase` 中 7 个带本编号的精确方法；可靠性报告同步记录 2 个 disabled 场景。 | Visibility identity 独立修复后恢复 7 个方法，并重跑 standalone 与 cluster 深度套件。 |
| `DAUTH-F06` | Java SDK AI 生命周期 | 默认与 Jackson 3 全量 SDK 回归未出现稳定失败。 | 无 | 独立补充 notifier 生命周期测试后再决定产品修复。 |
| `DAUTH-F07` | CI 日志安全检查 | 已确认 Java IT 的 DEBUG 请求日志会输出临时密码；产品代码保持回滚状态。 | 不禁用测试；增加测试侧 `logback-test.xml`，将根日志限制为 INFO，最终全量日志扫描未发现请求密码 body。 | 独立安全修复及同步/异步 REST 日志脱敏测试通过。 |

`DAUTH-F05` 当前精确禁用的方法如下，均保留原断言：

- `shouldSurfaceServerWatchCapacityAndReuseSlot`；
- `shouldWatchExistingAgentOnlyWhenCompleteFingerprintChanges`；
- `shouldTrackVersionEvolutionAcrossRegistrationOrders`；
- `shouldSeparateDefaultRolloutPoolFromExplicitLatest`；
- `shouldUseGrpcForAutoWhenInitialConnectionIsAvailable`；
- `shouldRestoreGrpcAndHttpPublicationsAndWatchesAfterRealServerRestart`；
- `shouldConvergePinnedNodeDefinitionAndRuntimeChanges`。

可靠性 runner 不会调用后两个已禁用方法等待编排 marker，而是在各自报告目录写入包含 finding、测试方法
和原因的 `status.txt`。同套件中的 cluster rolling restart 与 peer restart 仍实际执行并通过。
