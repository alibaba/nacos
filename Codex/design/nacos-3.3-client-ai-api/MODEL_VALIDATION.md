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

# Agent/RAD 模型本地试改验证记录

> 前半部分为上一版包整合结果。2026-09-14 的资源/版本摘要合并结果见文末，不能混用两轮测试数量。

状态：本地试改及验证完成，所有改动未提交。扩展 SDK IT 有一项首次失败、原用例复跑通过，详见下文。
分支：`codex/agent-model-consolidation`；基线：`1f51dfcc44e952b916ae32f70bd7ea0c8f4a06d3`。
环境：JDK 17、Maven 3.9.11；API/Client 编译目标为 Java 8。

## 已完成检查

- 48 个相关模块的生产源码和测试源码编译通过。
- 从 clean 产物运行相关 AI 单元测试；7 个改动生产模块通过 RAT、Checkstyle、Spotless 与 SpotBugs。
- 全量 43 个原模型按类型重命名映射核对，继承后可见的业务字段集合和字段类型保持不变，
  没有父子类字段重复遮蔽，具体 DTO 未使用抽象属性类型。
- 194 处业务字段声明收敛为 130 处；固定 JSON 契约、Client 无 namespace、并列继承、摘要/详情、
  定义/发现、管理 labels 空数组/RAD 省略规则均有测试。
- `model.a2a` 和已发布 `A2aService` 无源码变动。

| 模块 | 测试总数 | 失败/错误 | 跳过 |
| --- | ---: | ---: | ---: |
| api | 680 | 0/0 | 0 |
| client | 562 | 0/0 | 1 |
| ai | 2866 | 0/0 | 2 |
| maintainer-client | 207 | 0/0 | 0 |
| console | 441 | 0/0 | 0 |
| ai-registry-adaptor | 162 | 0/0 | 0 |
| auth | 25 | 0/0 | 0 |
| common / plugin-ai | 1 / 24 | 0/0 | 0 |

合计 4968 项，4965 项执行通过，3 项沿用既有跳过条件。
跳过项为 Client 的既有 MCP cache exception 用例，以及 AI 的两个依赖打包资源的 archive 用例。
未修改它们的执行条件。

## standalone IT

| 服务端 / 调用方 | 通过 | 原有跳过 | 结果 |
| --- | ---: | ---: | --- |
| 重构前服务端 / 新 SDK 定向 wire 回归 | 15 | 0 | Search 与多项注销各 grpc/http/auto、跨 transport 复杂指纹、发布 5 项、MCP HTTP 3 项 |
| 重构前服务端 / 新 Maintainer SDK | 4 | 0 | 完整 Agent 管理流程；首次复用环境中该批次独立通过 |
| 本轮新服务端 / 新 Maintainer SDK | 4 | 0 | 具体 Admin 请求、继承字段、摘要/详情及生命周期 |
| 本轮新服务端 / HTTP Client/Admin/Console | 25 | 2 | 原始 JSON 字段边界、定义/运行 Endpoint/发现/Watch 及错误形状 |
| 本轮新服务端 / 默认 SDK 与旧字节码扩展回归 | 57 | 10 | 共 68 项：1 项既有 MCP 发布后立即查询首次报错，其余执行项通过；失败项见下方 |
| 同一 MCP 原用例定向复跑 | 1 | 0 | 未改代码、未放宽断言，原用例通过 |
| 本轮新服务端 / Jackson 3 Maintainer SDK | 4 | 0 | Admin 请求与具体摘要/详情继承字段通过 |
| 本轮新服务端 / Jackson 3 SDK 定向回归 | 29 | 1 | 三种 transport 的关键模型流程、资源矩阵、MCP 与旧字节码通过；跳过可选 3.2.4 服务端用例 |

HTTP 的两个跳过项为既有 Watch capacity/cancellation 条件场景，未更改跳过条件。
默认 SDK 跳过 9 项既有 Watch/故障恢复/集群条件场景及 1 项需要独立 3.2.4 服务端的可选用例。
旧 3.2.4 调用字节码 + 新 SDK，以及原版 3.2.4 SDK + 本轮服务端的三个启用场景均通过。

默认 SDK 扩展回归中的 `AiServiceJavaSdkITCase.testReleaseQueryAndSubscribeMcpServer`
首次在 release 之后立即 `mcp().getMcpServer` 时收到 `MCP Server content does not exist`。
同一用例定向复跑通过；MCP HTTP、混合 transport 及旧字节码路径的 MCP 场景也通过。
Jackson 3 定向回归中该 MCP 用例也通过。这表现为偶发的发布后读取问题，本轮未证明其根因，
未修改 MCP 存储/查询实现或测试断言。
因此不能把首次扩展回归描述为一次性全绿；失败日志及复跑日志均保留。
使用独立进程启动服务端，测试通过公开 SDK 或真实 HTTP 调用，
不在 IT 类中启动 Nacos/Spring。不扩展故障恢复或集群注入场景。

## 本地环境问题与处置

- 首轮 reactor install 的 RAT 被两份 2026-09-08 历史运行日志阻塞；已将日志保留到
  `/tmp/nacos-agent-model/preserved-runtime-logs/` 后重跑，未跳过许可证检查。
- 首轮外部 SDK IT 使用普通 development 构建产物，缺少 gRPC 可选依赖；改用
  `client -Prelease-nacos,!dev install` 的正式 shaded 包。相关测试重新执行。
- 复用历史 standalone 数据时，Raft 分组持续报告 Unknown leader，metadata 请求失败；
  中止无效回归，保留原目录，在 `/tmp/nacos-agent-model/fresh-server` 创建新数据并固定
  `nacos.inetutils.ip-address=127.0.0.1` 后重跑。
- 上述失败尝试不计入模型兼容通过的证据，也未通过业务代码修改或放宽断言掩盖它们。

## 日志

本轮临时构建、测试与检查日志位于 `/tmp/nacos-agent-model/`：
`compile-2.log`、`unit.log`、`unit-resume.log`、`static.log`、`client-release.log`、
`server-build.log`、`root-rat.log`、`final-model-test.log`、`old-fresh-sdk.log`、`new-server-compat.log`、
`new-server-mcp-recheck.log`、`new-server-http.log`、`new-server-maintainer.log`、
`new-server-maintainer-jackson3.log`、`new-server-jackson3-compat.log`。
各批次的原始 XML 报告已在对应 `*-reports` 目录留档。新增/加强的场景已同步 Java SDK、Maintainer SDK 和 OpenAPI 覆盖登记；
没有将现有 Partial 或已知跳过项改标为 Covered。

## 最终工作区状态

代码和文档保留在 `codex/agent-model-consolidation`，没有暂存或提交，也没有创建 PR。
standalone 测试进程在验证结束后关闭，临时目录保留用于复查；没有改写原历史服务端数据目录。
A2A/RAD 模式决策、故障恢复注入及集群回归保持本轮范围之外。


## 2026-09-14：Agent 与版本摘要合并

对应 [MODEL_SUMMARY_MERGE.md](MODEL_SUMMARY_MERGE.md)。在上一次未提交试改上继续合并
Agent/AgentSummary/AgentCatalogEntry，以及版本目录和版本摘要，仍然没有提交或暂存。
本轮没有修改 CallInterface、Endpoint、Runtime 模型和其 Schema 定义，已与本轮起点逐文件核对。
A2A 公开接口及历史模型源码不变；服务实现仅迁移到统一模型的字段路径。

### Java 与前端验证

生产及测试调用方编译通过；API/Client 保持 Java 8 编译目标。
删除的旧模型 class 已从 clean API jar 和正式 shaded Client jar 中排除。
6 个改动生产模块通过 Spotless、RAT、Checkstyle 与 SpotBugs。

| 模块 | 用例数 | 执行通过 | 跳过 |
| --- | ---: | ---: | ---: |
| api | 348 | 348 | 0 |
| ai | 1724 | 1723 | 1 |
| client | 515 | 515 | 0 |
| console | 140 | 140 | 0 |
| maintainer-client | 119 | 119 | 0 |
| ai-registry-adaptor | 162 | 162 | 0 |
| 合计 | 3008 | 3007 | 1 |

这是受影响模型/AI 路径的定向单测，按最后通过的 XML 去重统计，不表示仓库所有单测。
保留的 AI skip 是既有打包资源条件，不修改其条件。首轮测试迁移遗漏的索引夹具、
已合并字段的重复初始化与静态断言 import 均已修正，相关模块复跑通过。
新增断言覆盖唯一 JSON 表示、派生 latest/count、非在线标签保留、Search 字段边界、
旧存储 latest/count 不一致拒绝、索引投影与 sourceDigest 不受管理字段影响。

Console TypeScript 检查、5 个相关测试文件共 87 项测试和生产构建通过。
6 个变化的生成文件已同步到后端 static/next，完整目录与生产构建结果逐字节一致；
Agent 列表和详情 bundle 已不再引用 versionCatalog。未修改依赖声明。
7 份 AI Schema 的 JSON 语法和 186 个本地引用检查通过；此检查不替代完整 JSON Schema 实例验证。

### 独立服务端 IT

| 调用方 / 批次 | 执行通过 | 既有跳过 |
| --- | ---: | ---: |
| HTTP Client/Admin/Console，含受影响 A2A 管理互通场景 | 28 | 2 |
| 默认 Java SDK，含资源 transport 矩阵和已发布 AiService 字节码 | 58 | 10 |
| 默认 Maintainer SDK | 4 | 0 |
| Jackson 3 Maintainer SDK | 4 | 0 |
| Jackson 3 Java SDK：三种 transport Search、目录生命周期和发布 | 9 | 0 |
| 合计 | 103 | 12 |

覆盖新旧 AiService 入口、grpc/http/auto、命名空间隔离、完整/在线标签投影、
多项端点注销、多版本上下线、订阅/指纹及管理摘要/详情。
管理端下线版本后，onlineVersions 为空而自定义标签仍保留的流程通过两种 JSON 适配器验证。
未新增故障恢复注入或修改既有 skip 条件；跳过项仍包含需要独立旧服务端、重启/集群
控制与 Watch 容量/取消条件的场景，覆盖登记不提升这些项的状态。

首次 HTTP 尝试使用了 bootstrap 缓存的旧依赖包，表现为实际响应仍包含 versionCatalog
和顶层 latestVersion/versions。核对嵌入 API/AI jar 的校验和后确认是旧构建产物，
已对 server/bootstrap 执行 clean 打包，并核对 API、AI、Console、adaptor 嵌入 jar
与已验证构件完全一致后重跑；首次尝试不计入上述通过数。
重启沿用同一隔离数据库；旧资源行的字段兼容由固定 JSON 存储向量单测验证，
本轮没有另行构造保留 Agent 资源的跨版本升级 IT。
最终包含新 Console 静态产物的服务端包亦重新 clean 构建。

### 本轮留档

日志、起点备份和原始 XML 位于 `/tmp/nacos-agent-summary-merge/`。
主要文件：`unit-tests.log`、`unit-resume.log`、`unit-remaining.log`、
`unit-final-modules.log`、`api-final.log`、`unit-summary.json`、`unit-reports/`、
`static-checks.log`、`frontend-typecheck-2.log`、`frontend-tests.log`、`frontend-build.log`、
`server-build-clean.log`、`final-server-package.log`、`http-current.log`、
`sdk-current-compat.log`、`maintainer-current.log`、`maintainer-jackson3.log`、`sdk-jackson3.log`
及各批次 `*-reports/`。旧依赖包失败尝试单独留在 `http-stale-server-reports/`。
独立 IT 进程已关闭，工作区保留全部未提交的源码、规范、测试和生成产物。

## 2026-09-14：地址模型统一前的 checkpoint

用户要求先将此前试改提交，再落地地址模型统一和新增测试。提交范围为已有 Agent/RAD 包整合、
AgentSummary/版本摘要合并、对应测试/前端/规范，以及下一轮测试设计；不包含尚未开始的
CallInterface/Endpoint 结构统一或 healthy 输入行为。

提交前对 10 个受影响 Java/IT 模块运行 Spotless apply、Spotless check，未产生额外源码改动；
48 个模块生产/测试源码编译通过。重新验证定向 3008 项 UT，3007 项执行通过、1 项既有跳过。
此前 103 项 IT 与 87 项前端测试的证据见上文；这次 checkpoint 没有重复执行它们。

首次独立 AI 单测遇到本地 Maven 缓存 API 包缺少新模型类，改为同一 reactor 后消除。
Reactor 中两项 adaptor Web 测试因沙箱禁止绑定端口而启动失败，确认 SocketException 后仅重跑
这两项，允许临时本地端口后通过；未修改业务代码、测试预期或跳过条件。
日志与去重 XML 保存在 `/tmp/nacos-endpoint-unification/precommit-*`。
