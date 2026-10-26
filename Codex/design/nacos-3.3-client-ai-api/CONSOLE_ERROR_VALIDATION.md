# 独立 Console 错误码修复（CONSOLE-ERR-01）

Agent 模型及 Schema 改动已单独提交为 `757cd8daa`。本轮仅修复共享 Maintainer HTTP
异常传递；不改变 Agent/A2A 模型、业务流程、重试/重新登录/节点切换策略。

## 根因与修复

普通 HTTP 的非 200 响应由 AbstractResponseHandler 放入 HttpRestResult.message；
文件上传响应放入 data。原 ClientHttpProxy 仅尝试解析 data 的错误文本，并将所有错误
包装为普通 NacosException，丢失业务码。Console 统一异常处理器据此返回 30000。

复用 NacosApiException，补充接收原始业务码和摘要的构造函数。代理从两个响应入口
解析标准 Result 错误，在重试结束时保留异常类型。getErrCode 继续是 HTTP 状态，
getDetailErrCode 是业务码；getErrMsg 保留字符串详情，无详情时使用摘要。非标准错误沿用通用回退。

## 测试矩阵

| 层次 | 场景 | 验收 |
| --- | --- | --- |
| API UT | 新构造函数、未知业务码 | HTTP/业务码/摘要/详情独立保存 |
| Proxy UT | 普通 HTTP message、上传 data、23000/20004/50100/未知码 | 重试后仍是 NacosApiException，字段不丢失 |
| Proxy UT | 空/纯文本/非法 JSON/缺少或成功 code/非字符串 data | 可控回退；不误认成功为业务错误 |
| Proxy UT | 重试成功、重试耗尽、403 重新登录、5xx 节点切换、超时 | 保留原策略 |
| 兼容 UT | A2A 旧格式、Pipeline 旧路由、Config | HTTP 状态和旧字段错误匹配继续有效 |
| HTTP IT | 合并及独立 Console 的 Agent/A2A 原14项 | 三个失败转为通过，原断言不放宽 |
| HTTP IT | Config/Naming 代表场景，双部署 | 共享代理没有跨领域回归 |
| Maintainer IT | 全量默认和 Jackson 3 | 公共 SDK 调用/异常行为正确 |

本轮按共享错误传递影响面验证，Agent 模型矩阵已有前一提交的完整记录；不重复执行
无关的迁移、索引、Watch、UI 矩阵。独立 Console CI 部署任务仍可单独提交，不作为本次
错误修复的一部分。

## 实测结果

**三项目标错误已修复并通过验证；按用户要求单独提交，再纳入 Agent 模型整合 PR。**原失败和日志保留在
`/tmp/nacos-agent-json-cleanup/`，本轮证据存放于 `/tmp/nacos-agent-commit-console-fix/`。

| 验证 | 总数 | 通过 | 失败 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| API/Core UT | 18 | 18 | 0 | 0 |
| Maintainer 全量 UT | 368 | 368 | 0 | 0 |
| 合并 Console HTTP IT | 22 | 22 | 0 | 0 |
| 独立 Console HTTP IT | 22 | 21 | 1 | 0 |
| Maintainer 默认 adapter 全量 IT | 46 | 44 | 0 | 2 |
| Maintainer Jackson 3 全量 IT | 46 | 44 | 0 | 2 |

所有当前构件测试均无 error。两种 Console 部署中的 Agent/A2A 各14项全部通过，原来的
23000/20004/50100 三项断言没有放宽。每套 Maintainer 的2项跳过分别为已有 DAUTH-F04
和未启用的真实重启测试；没有执行故障恢复。全量 SDK 包含成功流程、鉴权、Multipart、
Config 未找到与 Naming 管理；本轮加强了 Agent 异常类型、HTTP 状态、业务码及摘要断言。

Spotless apply/check、61模块 release install、API/Core/Maintainer 的 RAT、Checkstyle、
SpotBugs 和 Spotless 均通过。运行包中的 API/Maintainer/AI/Console/Adaptor JAR 已逐个
比对当前构建产物。服务端、独立 Console 和对照 Console 三个测试进程均已停止。

证据：`fix-results.json`、`fix-static.log`、`test-summary.json`、`embedded-jars.json`、
`baseline-comparison.json`、`cleanup.json`、`baseline-cleanup.json`；每批 Failsafe/Surefire
XML 存放在 `reports/<label>/`。当前构件结果与对照构件结果分别计数。

## 旧构件对照与额外发现：CONSOLE-NAMING-01

为判断扩展回归中的 Naming 失败是否由本次修改引入，使用前一轮留存的原始发行包，
以独立 Console 方式连接同一个服务端、运行相同22项 HTTP IT。该旧构件结果为
18通过、4失败、0error：既有三项 Agent/A2A 错误码失败全部复现，Naming 也同样失败。
当前构件为21通过、1失败。旧/新 Maintainer JAR 哈希保存在 `baseline-build.json`。

唯一剩余失败为 `InstanceConsoleApiOpenApiITCase.testListUpdateAndDeletePersistentInstance`：
用 Admin API 将持久实例注册到自定义 cluster 后，Console 不传 cluster 列举实例，独立
部署返回 HTTP404、业务30000、`cluster DEFAULT is not found!`；合并部署通过。

链路：`InstanceRemoteHandler.listInstances` 将空 cluster 交给 Maintainer；
`NacosNamingMaintainerServiceImpl.listInstances` 把空值改成 `DEFAULT` 后调用 Admin API。
合并部署的 `InstanceInnerHandler` 则直接使用 Catalog 查询全部 cluster。这些生产文件本轮
未改。旧构件的同一失败和请求参数链路证明这是另一个已有的双部署语义差异；保留原 IT，
单独记录待修复，不扩大此次错误码透传的生产改动。此处30000是上游实际返回的业务码，
与被修复的23000/20004/50100丢失不是同一问题。

独立 Console 的 CI 自动部署仍待单独处理；本次本地双部署通过不代表 CI 已覆盖该模式。

提交前再次执行 API/Core/Maintainer/SDK IT 模块 Spotless apply/check，随后相关异常、HTTP代理、
A2A/Pipeline兼容UT全部通过。记录位于 `/tmp/nacos-agent-pr-submit/`。
