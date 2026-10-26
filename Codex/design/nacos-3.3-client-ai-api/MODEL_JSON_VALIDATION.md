# Agent 去 Jackson 注解验证记录

日期：2026-09-16；分支 `codex/agent-model-consolidation`；起点 `44112e45b`。
以下为编码和验证阶段记录；该阶段未提交 commit、push 或 PR。下列结果来自本轮构件；约定范围内的矩阵已执行完成，已知失败与跳过单独保留。

## 已落地内容

- `api.ai.model.agent` 及其 base/admin/client 子包共 31 个文件不再依赖 Jackson 注解。
- Endpoint 使用基础类型：priority=0、weight=1、healthy=true、enabled=true；priority 越小越优先。
- AgentVersionInfo 改为 onlineCnt()/latestVersion()，缺省 0/null；派生值不进入 JSON。
- 注销只校验 uri/transport 自然键；查询返回的完整 Endpoint 可直接用于多项注销。
- 声明地址健康和管理字段仍不写入版本内容或 contentDigest；运行时健康仍可上报。
- RAD/管理/Watch/Artifact Schema 统一新增 0.3.0，相互引用同版契约，文件使用无版本目录的固定路径，payload schemaVersion 仍为 1.0。
- 中英文规范、HTTP/SDK 场景文档、前端 nullable 类型及生成资源同步更新。

历史 A2A、MCP 公开模型和全局 JsonUtils adapter 配置未修改。nativeDescriptor 仍沿用已有存储
序列化政策；本次没有把它改为原始字符串，也没有改变指纹算法。3.3-BETA 存储升级不在范围内。

## 已完成验证

| 检查 | 本轮结果 | 证据 |
| --- | --- | --- |
| Agent/AI/A2A 相关完整 UT 矩阵 | 545 类、4992 项：4989 通过、3 原有跳过，无 failure/error | ut-current-result.json、reports/ut-current |
| Schema / JSON adapter | 同一仓库的 8 项 Schema/往返 UT 分别强制 Jackson 2 和 Jackson 3，均通过 | schema-jackson2.log、schema-jackson3.log |
| 前端 | 5 组、88 项通过；TypeScript 和 Vite build 通过；同步 agentDetail.js | ui-test-final.log、ui-types-final.log、ui-build-final.log、ui-artifacts.json |
| 全仓构建/静态检查 | 61 模块 clean release install；43 模块 compile/RAT/Checkstyle/SpotBugs/Spotless 均通过 | release-install.log、static-checks.log |
| 构件核对 | 服务端内嵌 API/AI/Console/Adaptor jar 与当前产物完全一致 | embedded-jars.json |
| 契约扫描 | 31 个 Agent 文件无 Jackson 注解；153 个 Schema 引用有效；5 个历史/内部 Schema 内容未变 | contract-scan.json |

所有证据位于 `/tmp/nacos-agent-json-cleanup/`。UT 汇总按每个测试类本轮最后一次结果计算，
初次失败与修正复验日志保留，不重复累加测试项。3 项跳过是原有两个 SeedArchive fixture 缺失和
一个 Mcp cache Disabled 用例。新增 Schema 断言覆盖真实 POJO 的显式 null、缺省标量、非法值、
未知字段、版本/标签互斥、Watch 条件必填、定义存储、Artifact 和存储读回后的发现指纹往返。

## 外部矩阵结果

| 环境/范围 | 总数 | 通过 | 失败 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| HTTP Admin/Client/合并 Console/ARD，INDEX | 58 | 56 | 0 | 2 |
| 默认 JSON Java SDK，含旧 SDK、鉴权、三种 transport | 76 | 65 | 0 | 11 |
| Jackson 3 Java SDK，含旧 SDK、三种 transport | 70 | 60 | 0 | 10 |
| Maintainer SDK，两种 JSON adapter | 8 | 8 | 0 | 0 |
| 独立 Console | 14 | 11 | 3 | 0 |
| 正常 A2A 迁移，shadow 开/关 | 30 | 30 | 0 | 0 |
| SCAN，GRPC/HTTP/AUTO × 两种 JSON adapter | 6 | 6 | 0 | 0 |
| **合计** | **262** | **236** | **3** | **23** |

没有 error。唯一失败项均为已确认延期修复的 CONSOLE-ERR-01，原断言保留。
23 项跳过为原有 Disabled/环境门控，不算通过。私有授权异步 Watch DAUTH-F05 缺口继续保留，
PUBLIC Watch 的通过不能替代私有鉴权验证。未执行真实故障恢复、三节点故障注入或 3.3-BETA 升级。

正常迁移实际经过 LEGACY → SYNCING → QUIESCING → CANONICAL，覆盖历史数据双写、
多项注销、容量计数、关闭清理、Watch/Endpoint 跨切换与永久 marker；两种 shadow 配置各 15 项通过。
SCAN 重跑同名 Agent 的 namespace 隔离、完整 Endpoint 直接 3 删 2及重复注销，两个 adapter 各 3 项通过。

最终机器汇总为 `external-current-result.json`。服务端与独立 Console 均由本轮 runner 正常停止；
进程核对记录保存于 `cleanup-verification.json`。当前 HEAD 仍为 `44112e45b`，未 commit/push/PR。

## 本轮失败诊断与复验依据

- 初轮 HTTP/SDK 的字段存在性与 enabled=null 断言使用旧契约，已分别调整为允许普通引用 null、enabled=true；不放宽非法 URI、范围、类型、权限或副作用断言。健康变更用例等待完整目标快照（healthy/weight/priority），避免读到前一次健康已恢复但权重尚未更新的结果。
- clean 清除了旧 SDK 3.2.4 的 compatibility classpath 文件，已按仓库兼容 runner 重新生成并核对全部 24 个依赖；首次失败保留，最终以复验结果计数。
- 私有 Agent 共享监听器初轮曾在首次 subscribe 成功后立即收到 `TERMINATED/-404`，第二次 subscribe 按 LOCAL_PENDING 契约返回 null。`private-watch-diagnostic.log` 保存客户端事件；服务端 `DefaultVisibilityService.checkResourcePermission` 仍从 RequestContextHolder 取身份，与已有 DAUTH-F05 一致，本轮未改动该实现。共享监听器改用 PUBLIC fixture 验证，保留快照/监听器隔离/部分取消订阅断言；私有授权异步 Watch 不计为已通过。
- 独立 Console 本轮 14 项中 11 通过、3 失败，仍是 CONSOLE-ERR-01：上游 23000/20004/50100 被包装为 30000。原断言保留，问题按用户要求延期处理。

本轮追加注销边界：非自然键 priority=-1 按契约忽略且输入不变，非法 URI/transport 仍拒绝。
默认 SDK 全批次的该方法随后单独复验，最终汇总用 `sdk-boundary-final` 覆盖同名用例，
其余结果取 `sdk-default-final`；不累加重复执行数量。

## 复验入口与环境

- JDK 17 / Maven 3.9.11；发行包由 `mvn -o -B '-Prelease-nacos,!dev' -DskipTests clean install` 生成。
- 静态检查范围：`-pl api,ai,client,maintainer-client,console,ai-registry-adaptor -am`，执行 compile、apache-rat:check、checkstyle:check、spotbugs:check、spotless:check。最新 IT 修改另跑两个 test 模块的 Spotless apply/check，verify 会重新编译。
- 默认合并服务端启用鉴权、anonymous AI、ARD 与 INDEX；Client 使用实际非管理员账号，namespace 隔离用例有独立授权。后续将同一新包切换为 SCAN 复验三种 transport × 两种 adapter。
- Java SDK profiles：java-sdk-integration-test、ai-api-compatibility；Jackson 3 额外启用 jackson3-sdk-test。Maintainer 分别用 maintainer-sdk-integration-test 及其 Jackson 3 组合。
- 旧 SDK classpath 通过 `test/java-sdk-test/src/test/compatibility/pom.xml` 的 dependency:build-classpath/tree 准备，保持旧依赖闭包。
- 迁移使用隔离无鉴权 standalone、历史写入口和真实持久 marker，依次运行 LEGACY、SYNCING、QUIESCING、永久 CANONICAL，分别使用 shadow=true/false。这不是多节点故障或 BETA 存储升级模拟。
- 每次外部测试的命令和新生成 Failsafe XML 分别保存为 `<label>-command.json` 和 `reports/<label>`；最终按环境、类、方法合并，跳过及失败单独统计。

## Review 后统一公开契约版本（2026-09-16，目录简化前的阶段记录）

RAD spec、RAD Schema、Watch binding、Agent 管理和 Artifact 统一采用 `0.3.0`。
本轮未发布的三个 `0.2.0` Schema 移至 `0.3.0`，Artifact 引用同目录管理 Schema；
基线的五份历史/内部 Schema 字节完全不变，Artifact payload schemaVersion 仍为 1.0。
同步中英文规范、Schema 索引、Java 校验器版本说明、契约测试和覆盖登记。

本次仅调整契约版本元数据及引用：四份 Schema 的规范化校验规则对照一致，344 处 Schema
引用和 18 处领域规范到 Schema 的链接均有效。Spotless apply/check 通过，Schema 契约 UT
8 项通过；同一套用例分别强制 Jackson 2、Jackson 3，各 8 项全部通过。证据位于
`/tmp/nacos-agent-schema-030/`。先前完整矩阵结果保留为模型行为改动的验证；本次按实际
影响复验 Schema，不重复计数或重跑服务端 IT。没有新 commit。

## 公共 Schema 目录简化（2026-09-16）

- 当前只保留 agent/ 下的管理、Artifact，以及 rad/ 下的协议、rad/watch/ 下的 Watch binding 四份公开 Schema。
- 删除四份历史公开 Schema 副本，移除公共版本目录；历史定义改为按 Git tag/commit 追溯，保留文件内 0.3.0 元数据。
- `$id` 与文件固定路径一致，`$ref`、契约测试、中英文规范、Schema 索引和设计稿链接均同步。可复现校验必须从同一个固定 Git revision 加载整组 Schema。
- `agent/internal/v1/agent-storage.schema.json` 与基线字节一致，Artifact payload 的 schemaVersion 仍为 1.0；MCP Schema 未改动。

本次四份当前 Schema 在解析引用后与搬迁前的校验规则完全一致，193 处 Schema 引用、
18 处领域规范到 Schema 的链接有效，全仓文本扫描无遗留的公开版本目录引用。
Spotless apply/check 通过；Schema 契约 UT 8 项通过，分别强制 Jackson 2、Jackson 3 各 8 项通过。
证据位于 `/tmp/nacos-agent-schema-flat/`。此次只改变目录和契约定位方式，未重跑服务端 IT，
未提交 commit、push 或 PR；之前模型行为完整矩阵的结果及已知缺口继续保留。

## Review 后提交检查（2026-09-16）

按用户要求，将以上 Agent 注解、Endpoint 默认值及 Schema 目录简化作为独立 commit 提交。
提交前对 api、ai、client、console、ai-registry-adaptor 及两个 IT 模块重新执行 Spotless
apply/check，随后 Schema 契约 UT 8 项全部通过；完整矩阵证据沿用上述记录。
CONSOLE-ERR-01 在此次提交之后单独修复。检查日志位于 `/tmp/nacos-agent-commit-console-fix/`。
