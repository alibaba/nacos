# Agent/RAD 模型整合 PR 提交验证

本次 PR 对应 #14804，面向 `develop`。Console 错误码修复独立提交为
`b3a6c1875`，与此前三个模型整合提交共同提交。

## 最新上游集成

提交前合并 `upstream/develop` 的 `946138fb9`，保留上游新增的 Agent scope
管理、PUBLIC 默认值和相关 API/SDK 测试。合并时将新增重试路径中的旧 `Agent`
引用及其测试改为 `AgentSummary`；前端源代码合并后重新测试、构建并生成静态资源。
因此生成资源也包含上游已合入的 rollback 确认文案。

## 提交前验证

- 相关模块 Spotless apply/check 通过。
- 按 `.github/workflows/ci.yml` 执行完整 Maven 检查，61 个模块通过：
  `mvn -B clean compile apache-rat:check checkstyle:check spotbugs:check spotless:check -e`。
  本机没有 mvnd，使用等效 mvn；没有跳过上述检查。
- Agent 相关 UT 矩阵共 547 个目标测试类全部执行，5,070 项用例中 5,067 通过、3 跳过、
  0 failure、0 error。跳过的是原有 MCP cache 异常用例及 Skill/AgentSpec bundled archive
  两项环境相关用例。首次执行发现上游新增测试引用旧 `Agent` 类型，修正后只补跑此前
  未执行的 362 个测试类；与已通过的 185 个测试类合并统计，不重复计数。
- 前端 5 组 Agent 相关测试共 89 项通过；TypeScript/Vite 构建通过。
- 61 个模块的 release install 通过。外部 IT 使用该次新包；API、Maintainer、AI、Console、
  Adaptor 五个嵌入 JAR 均与当前模块构建结果逐字节比对一致。

外部 IT 共 305 项：279 通过、26 跳过、0 failure、0 error。

| 验证 | 总数 | 通过 | 失败/error | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| HTTP 合并部署 | 62 | 60 | 0 | 2 |
| HTTP 独立 Console | 15 | 15 | 0 | 0 |
| Java SDK 默认 adapter | 68 | 58 | 0 | 10 |
| Java SDK Jackson 3 | 68 | 58 | 0 | 10 |
| Maintainer 默认 adapter | 46 | 44 | 0 | 2 |
| Maintainer Jackson 3 | 46 | 44 | 0 | 2 |

合并部署与独立 Console 的 Agent/A2A 各 15 项均通过，包括原三项业务错误码断言和
上游新增 scope 往返测试。跳过原因沿用测试中的既有鉴权/PRIVATE 投影缺口及 opt-in
真实故障恢复开关，完整名称见原始 XML；未将跳过计为通过。两个本地测试进程均已停止。

## 已有矩阵与范围说明

模型和注解调整阶段的完整矩阵见 [MODEL_JSON_VALIDATION.md](MODEL_JSON_VALIDATION.md)、
[MODEL_ENDPOINT_VALIDATION.md](MODEL_ENDPOINT_VALIDATION.md) 和
[MODEL_REQUEST_VALIDATION.md](MODEL_REQUEST_VALIDATION.md)。其中正常 A2A 迁移 30 项、
SCAN 查询 6 项、旧 3.2.4 SDK 二进制兼容及双 adapter Schema 契约验证已完成；
本次提交验证重跑上游合并所影响的 UT、UI、HTTP 和 SDK 链路，不将历史通过数重复计入。
未执行用户要求暂缓的真实故障恢复场景。

三项 Console 业务错误码修复及旧构件对照见
[CONSOLE_ERROR_VALIDATION.md](CONSOLE_ERROR_VALIDATION.md)。另外发现的
`CONSOLE-NAMING-01` 已由旧构件复现，属于独立 Console 对空 cluster 的历史处理差异，
不在本次修改范围内。原失败断言保留；本次最终 Agent/A2A 回归未重复计入该 Naming 用例。
独立 Console 仍是本地双部署验证，不代表仓库 CI 已配置对应独立部署任务。

本轮原始日志与 XML：`/tmp/nacos-agent-pr-submit/`。测试结果与 GitHub CI 分开记录，
本地通过不等同于 PR CI 已通过。
