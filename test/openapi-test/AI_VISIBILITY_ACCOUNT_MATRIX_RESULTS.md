<!--
  Copyright 1999-2026 Alibaba Group Holding Ltd.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# 默认鉴权与可见性插件：账号矩阵实测结果

- 执行日期：2026-10-28；服务端基线：`99f7cd8ba2c4b91735a66a5f19cc269359f7796a`。
- 方式：独立 standalone 服务端 + 外部 HTTP IT；测试类 `AiVisibilityAccountMatrixOpenApiITCase`。
- 两轮均启用 Client/Admin/Console 鉴权及权限缓存，使用默认 `auth:nacos`、`visibility:nacos`；分别关闭和开启匿名 AI 开关。
- API / Console / ARD 端口：18848 / 18080 / 19080；无生产代码修改。
- 本报告记录显式执行的专项回归；后续运行需设置 `nacos.test.visibility.matrix.enabled=true`。
  常规 CI 默认不执行此专项矩阵，跳过不计为通过；本报告中的失败断言保持有效。

## 结果

- 70 组计划组合：66 组全部通过、4 组失败；已按用户授权通过临时数据库夹具补齐 PUBLIC Prompt 的 7 组。
- 按检查 ID 去重后的独立检查：457 项通过、4 项失败。检查项可能包含多个 HTTP 断言；重复定向复验不重复计数。
- 详情读取、标签写入和删除三项共同符合预期的组合：70 组；搜索失败不会据此标记为整组通过。
- 初始纯 HTTP 运行是 59 组通过、4 组失败、7 组阻塞。PUBLIC Prompt 仍缺少创建/切换 scope 的公开 API，新增 7 组为数据库构造夹具的专项 HTTP 补测，不代表常规自动化 IT 已具备 PUBLIC Prompt 的准备入口。

| 匿名开关 | JUnit 参数化用例数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| off | 15 | 0 | 0 | 6 |
| on | 15 | 2 | 0 | 1 |
| on-agent-PRIVATE | 15 | 1 | 0 | 14 |
| on-agent-PRIVATE-attempt1 | 15 | 0 | 0 | 14 |
| on-agent-PRIVATE-attempt2 | 15 | 0 | 0 | 14 |

关闭匿名阶段跳过 5 个共享边界用例（它们在开启阶段执行）以及 PUBLIC Prompt；完整开启阶段只跳过 PUBLIC Prompt。三个 Agent 定向运行各跳过 14 个未选中的用例，不算新增通过覆盖。

## 未通过检查

| 检查 ID | 实际结果 |
| --- | --- |
| `anonymous-on/mcp/PRIVATE/owner/search` | {"pageItems":[],"pageNumber":1,"pagesAvailable":0,"totalCount":0} ==> expected: <1> but was: <0> |
| `anonymous-on/mcp/PRIVATE/admin/search` | {"pageItems":[],"pageNumber":1,"pagesAvailable":0,"totalCount":0} ==> expected: <1> but was: <0> |
| `anonymous-on/mcp/PRIVATE/reader/search` | {"pageItems":[],"pageNumber":1,"pagesAvailable":0,"totalCount":0} ==> expected: <1> but was: <0> |
| `anonymous-on/mcp/PRIVATE/writer/search` | {"pageItems":[],"pageNumber":1,"pagesAvailable":0,"totalCount":0} ==> expected: <1> but was: <0> |

## Agent 创建阶段异常

以下错误发生在 scope 设置及账号矩阵断言之前，不能计作权限通过。定向复验按独立运行保留，不覆盖这些失败记录。

- `on`：{"code":30000,"data":"Agent Version cannot be read after creation: vm-agent-4903b78d@1.0.0","message":"server error"} ==> expected: <200> but was: <500>
- `on-agent-PRIVATE`：{"code":30000,"data":"Agent Version cannot be read after creation: vm-agent-682e3229@1.0.0","message":"server error"} ==> expected: <200> but was: <500>

两个连续定向复验（`on-agent-PRIVATE-attempt1` / `attempt2`）各通过 6 组账号组合和 38 项检查。首次请求也有成功记录，因此不能将上述 500 直接归因为冷启动；现阶段结论是间歇性创建失败，底层原因尚未确认。

## MCP 搜索问题的代码定位

PRIVATE MCP 已在线且可通过 Admin 详情和列表读取，但 owner、admin、`r` 与 `rw` 授权用户的 Client Search 在 15 秒收敛等待后仍返回 `totalCount=0`。同一测试环境的 PUBLIC MCP 搜索通过；两轮独立执行均复现 PRIVATE 搜索失败。

代码链路提供了一个原因判断：后台 `AiResourceIndexTaskConsumer.convergeBase` 调用 `AiResourceIndexServiceImpl.rebuildLatestAiResource`，MCP 投影器 `McpAiResourceSearchTypeHandler.project` 又调用 `McpLifecycleOperationService.getMcpServerDetail`。该详情接口执行请求身份的可见性校验；后台无请求身份时 PRIVATE 被拒绝为 404，投影器将 404 转为 `null`，索引服务随即删除该资源索引。这是结合实现的定位推断，本次未修改生产逻辑，也未通过绕过鉴权使测试通过。

对应位置：

- `ai/src/main/java/com/alibaba/nacos/ai/config/AiResourceIndexTaskConsumer.java:242`
- `ai/src/main/java/com/alibaba/nacos/ai/service/search/AiResourceIndexServiceImpl.java:90`
- `ai/src/main/java/com/alibaba/nacos/ai/service/search/McpAiResourceSearchTypeHandler.java:83`
- `ai/src/main/java/com/alibaba/nacos/ai/service/mcp/McpLifecycleOperationService.java:548`

### 与本次 scope SPI 改动的关系

对比改动前基线 `13c4c6e63b4e94449e217f3f0197ec1a6601c0c3` 与本次提交
`99f7cd8ba2c4b91735a66a5f19cc269359f7796a`，代码证据表明 MCP PRIVATE 搜索问题已存在于基线，
不属于本次 scope SPI 改动引入的回归：

- MCP Search Handler、生命周期读取服务、后台索引任务与索引写入服务均未修改。
- 默认可见性插件与 `AiResourceManager` 的单资源读权限校验均未修改；`VisibilityHelper`
  只新增 scope 声明查询方法，原有身份解析和读权限判断不变。
- 查询转换器虽扩展为多 scope，默认 `publicScopes` 仍为单项 `PUBLIC`，默认插件的查询语义保持一致。
- 基线已包含「后台读取受可见性校验的详情 → 404 → 空投影 → 删除索引」完整链路。

此归因来自提交差异和历史代码核对，未在该基线重新部署并运行相同 HTTP 用例。
原报告中的“发现 4 项失败”表示本轮测试暴露原有缺陷，不表示这 4 项由本次提交引入。

### 修复方案（分析完成，尚未修改生产代码）

四项失败来自同一条索引链路，应统一修复，而不是分别调整四类账号的权限：

1. 从 MCP 生命周期读取中抽出索引专用的内部加载路径，按 namespace、标准名称和版本读取权威 Resource/Version/Storage；继续校验启用状态、在线版本和 latest，但不依赖后台不存在的请求身份。
2. `McpAiResourceSearchTypeHandler.project` 使用该内部路径；`scan` 使用不附加用户可见性条件的内部分页读取；`exists` 只判断权威 Resource 是否存在。三处必须一起处理，否则回填会遗漏 PRIVATE，孤儿扫描还会将 PRIVATE 误判为已删除。
3. 用户搜索仍使用现有可见性查询条件和 `isCurrent` 的请求身份检查，Admin/Client 详情的 `ensureReadableOrNotFound` 保持有效。现有 Agent 与通用存储资源的 Search Handler 已采用内部投影、请求侧校验分离的结构，可按同一结构整理 MCP。
4. 更新投影版本并触发完整回填，恢复已缺失的 PRIVATE 索引；仅修正查询 SQL 无法找回未建立的文档。
5. 回归应覆盖无请求上下文的 PRIVATE 投影/扫描/存在性判断、owner/admin/r/rw 正向搜索、无授权及匿名负向搜索、授权撤销、PUBLIC→PRIVATE 切换、分页计数与重启回填。同步澄清中英文 MCP Search 规范中内部索引读取与用户请求授权的边界。

此方案不需要给后台线程伪造 admin 身份，也不需要放开 PRIVATE 的公开读权限。

## 逐组合结果

`other` 为无资源授权的非 owner；`reader` / `writer` 为分别获得 `r` / `rw` 的非 owner。
开关 off 的匿名请求全部预期拒绝；on 仅允许通过已 opt-in 的入口读取 PUBLIC。AgentSpec 匿名搜索没有 opt-in，搜索栏 PASS 表示验证拒绝，读取能力由 Client 下载和 Admin 列表验证。

| 资源 | scope | 匿名开关 | 账号 | 预期读/写 | 详情 | 列表分页 | 搜索 | 标签写 | 删除 | 整组 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| agent | PRIVATE | on | owner | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| agent | PRIVATE | on | admin | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| agent | PRIVATE | on | other | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| agent | PRIVATE | on | reader | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| agent | PRIVATE | on | writer | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| agent | PRIVATE | on | anonymous | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| agent | PRIVATE | off | anonymous | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| agent | PUBLIC | on | owner | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| agent | PUBLIC | on | admin | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| agent | PUBLIC | on | other | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| agent | PUBLIC | on | reader | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| agent | PUBLIC | on | writer | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| agent | PUBLIC | on | anonymous | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| agent | PUBLIC | off | anonymous | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| mcp | PRIVATE | on | owner | 允许/允许 | PASS | PASS | FAIL | PASS | PASS | FAIL |
| mcp | PRIVATE | on | admin | 允许/允许 | PASS | PASS | FAIL | PASS | PASS | FAIL |
| mcp | PRIVATE | on | other | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| mcp | PRIVATE | on | reader | 允许/拒绝 | PASS | PASS | FAIL | PASS | PASS | FAIL |
| mcp | PRIVATE | on | writer | 允许/允许 | PASS | PASS | FAIL | PASS | PASS | FAIL |
| mcp | PRIVATE | on | anonymous | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| mcp | PRIVATE | off | anonymous | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| mcp | PUBLIC | on | owner | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| mcp | PUBLIC | on | admin | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| mcp | PUBLIC | on | other | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| mcp | PUBLIC | on | reader | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| mcp | PUBLIC | on | writer | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| mcp | PUBLIC | on | anonymous | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| mcp | PUBLIC | off | anonymous | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| prompt | PRIVATE | on | owner | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| prompt | PRIVATE | on | admin | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| prompt | PRIVATE | on | other | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| prompt | PRIVATE | on | reader | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| prompt | PRIVATE | on | writer | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| prompt | PRIVATE | on | anonymous | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| prompt | PRIVATE | off | anonymous | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| prompt | PUBLIC | on | owner | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS（DB 夹具） |
| prompt | PUBLIC | on | admin | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS（DB 夹具） |
| prompt | PUBLIC | on | other | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS（DB 夹具） |
| prompt | PUBLIC | on | reader | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS（DB 夹具） |
| prompt | PUBLIC | on | writer | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS（DB 夹具） |
| prompt | PUBLIC | on | anonymous | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS（DB 夹具） |
| prompt | PUBLIC | off | anonymous | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS（DB 夹具） |
| skill | PRIVATE | on | owner | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| skill | PRIVATE | on | admin | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| skill | PRIVATE | on | other | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| skill | PRIVATE | on | reader | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| skill | PRIVATE | on | writer | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| skill | PRIVATE | on | anonymous | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| skill | PRIVATE | off | anonymous | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| skill | PUBLIC | on | owner | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| skill | PUBLIC | on | admin | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| skill | PUBLIC | on | other | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| skill | PUBLIC | on | reader | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| skill | PUBLIC | on | writer | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| skill | PUBLIC | on | anonymous | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| skill | PUBLIC | off | anonymous | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| agentspec | PRIVATE | on | owner | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| agentspec | PRIVATE | on | admin | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| agentspec | PRIVATE | on | other | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| agentspec | PRIVATE | on | reader | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| agentspec | PRIVATE | on | writer | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| agentspec | PRIVATE | on | anonymous | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| agentspec | PRIVATE | off | anonymous | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| agentspec | PUBLIC | on | owner | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| agentspec | PUBLIC | on | admin | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| agentspec | PUBLIC | on | other | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| agentspec | PUBLIC | on | reader | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| agentspec | PUBLIC | on | writer | 允许/允许 | PASS | PASS | PASS | PASS | PASS | PASS |
| agentspec | PUBLIC | on | anonymous | 允许/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |
| agentspec | PUBLIC | off | anonymous | 拒绝/拒绝 | PASS | PASS | PASS | PASS | PASS | PASS |

## 补充边界

| 检查 ID | 结果 |
| --- | --- |
| `anonymous-on/agent/PUBLIC/B08/grant-management` | PASS |
| `anonymous-on/agent/PUBLIC/B03/invalid-credentials` | PASS |
| `anonymous-on/agent/PUBLIC/B07/adjacent-resource` | PASS |
| `anonymous-on/agent/PUBLIC/B09/base-permission` | PASS |
| `anonymous-on/agent/PUBLIC/B05/downgrade` | PASS |
| `anonymous-on/agent/PUBLIC/B04/revoke` | PASS |
| `anonymous-on/agent/PUBLIC/B06/scope-transition` | PASS |
| `anonymous-on/mcp/PRIVATE/B08/grant-management` | PASS |
| `anonymous-on/mcp/PRIVATE/B03/invalid-credentials` | PASS |
| `anonymous-on/mcp/PRIVATE/B07/adjacent-resource` | PASS |
| `anonymous-on/mcp/PRIVATE/B09/base-permission` | PASS |
| `anonymous-on/mcp/PRIVATE/B05/downgrade` | PASS |
| `anonymous-on/mcp/PRIVATE/B04/revoke` | PASS |
| `anonymous-on/mcp/PRIVATE/B06/scope-transition` | PASS |
| `anonymous-on/mcp/PUBLIC/B08/grant-management` | PASS |
| `anonymous-on/mcp/PUBLIC/B03/invalid-credentials` | PASS |
| `anonymous-on/mcp/PUBLIC/B07/adjacent-resource` | PASS |
| `anonymous-on/mcp/PUBLIC/B09/base-permission` | PASS |
| `anonymous-on/mcp/PUBLIC/B05/downgrade` | PASS |
| `anonymous-on/mcp/PUBLIC/B04/revoke` | PASS |
| `anonymous-on/mcp/PUBLIC/B06/scope-transition` | PASS |
| `anonymous-on/prompt/PRIVATE/B08/grant-management` | PASS |
| `anonymous-on/prompt/PRIVATE/B03/invalid-credentials` | PASS |
| `anonymous-on/prompt/PRIVATE/B07/adjacent-resource` | PASS |
| `anonymous-on/prompt/PRIVATE/B09/base-permission` | PASS |
| `anonymous-on/prompt/PRIVATE/B05/downgrade` | PASS |
| `anonymous-on/prompt/PRIVATE/B04/revoke` | PASS |
| `anonymous-on/skill/PRIVATE/B08/grant-management` | PASS |
| `anonymous-on/skill/PRIVATE/B03/invalid-credentials` | PASS |
| `anonymous-on/skill/PRIVATE/B07/adjacent-resource` | PASS |
| `anonymous-on/skill/PRIVATE/B09/base-permission` | PASS |
| `anonymous-on/skill/PRIVATE/B05/downgrade` | PASS |
| `anonymous-on/skill/PRIVATE/B04/revoke` | PASS |
| `anonymous-on/skill/PRIVATE/B06/scope-transition` | PASS |
| `anonymous-on/skill/PUBLIC/B08/grant-management` | PASS |
| `anonymous-on/skill/PUBLIC/B03/invalid-credentials` | PASS |
| `anonymous-on/skill/PUBLIC/B07/adjacent-resource` | PASS |
| `anonymous-on/skill/PUBLIC/B09/base-permission` | PASS |
| `anonymous-on/skill/PUBLIC/B05/downgrade` | PASS |
| `anonymous-on/skill/PUBLIC/B04/revoke` | PASS |
| `anonymous-on/skill/PUBLIC/B06/scope-transition` | PASS |
| `anonymous-on/agentspec/PRIVATE/B08/grant-management` | PASS |
| `anonymous-on/agentspec/PRIVATE/B03/invalid-credentials` | PASS |
| `anonymous-on/agentspec/PRIVATE/B07/adjacent-resource` | PASS |
| `anonymous-on/agentspec/PRIVATE/B09/base-permission` | PASS |
| `anonymous-on/agentspec/PRIVATE/B05/downgrade` | PASS |
| `anonymous-on/agentspec/PRIVATE/B04/revoke` | PASS |
| `anonymous-on/agentspec/PRIVATE/B06/scope-transition` | PASS |
| `anonymous-on/agentspec/PUBLIC/B08/grant-management` | PASS |
| `anonymous-on/agentspec/PUBLIC/B03/invalid-credentials` | PASS |
| `anonymous-on/agentspec/PUBLIC/B07/adjacent-resource` | PASS |
| `anonymous-on/agentspec/PUBLIC/B09/base-permission` | PASS |
| `anonymous-on/agentspec/PUBLIC/B05/downgrade` | PASS |
| `anonymous-on/agentspec/PUBLIC/B04/revoke` | PASS |
| `anonymous-on/agentspec/PUBLIC/B06/scope-transition` | PASS |
| `boundary/agent/B10/mixed-pages` | PASS |
| `boundary/agent/B10/name-filter` | PASS |
| `boundary/agent/B10/owner-scope-filter` | PASS |
| `boundary/agent/B07/cross-type` | PASS |
| `boundary/agent/B07/cross-namespace` | PASS |
| `boundary/mcp/B10/mixed-pages` | PASS |
| `boundary/mcp/B10/name-filter` | PASS |
| `boundary/mcp/B07/cross-type` | PASS |
| `boundary/mcp/B07/cross-namespace` | PASS |
| `boundary/prompt/B10/mixed-pages` | PASS |
| `boundary/prompt/B10/name-filter` | PASS |
| `boundary/prompt/B07/cross-type` | PASS |
| `boundary/prompt/B07/cross-namespace` | PASS |
| `boundary/skill/B10/mixed-pages` | PASS |
| `boundary/skill/B10/name-filter` | PASS |
| `boundary/skill/B10/owner-scope-filter` | PASS |
| `boundary/skill/B07/cross-type` | PASS |
| `boundary/skill/B07/cross-namespace` | PASS |
| `boundary/agentspec/B10/mixed-pages` | PASS |
| `boundary/agentspec/B10/name-filter` | PASS |
| `boundary/agentspec/B10/owner-scope-filter` | PASS |
| `boundary/agentspec/B07/cross-type` | PASS |
| `boundary/agentspec/B07/cross-namespace` | PASS |
| `anonymous-on/agent/PRIVATE/B08/grant-management` | PASS |
| `anonymous-on/agent/PRIVATE/B03/invalid-credentials` | PASS |
| `anonymous-on/agent/PRIVATE/B07/adjacent-resource` | PASS |
| `anonymous-on/agent/PRIVATE/B09/base-permission` | PASS |
| `anonymous-on/agent/PRIVATE/B05/downgrade` | PASS |
| `anonymous-on/agent/PRIVATE/B04/revoke` | PASS |
| `anonymous-on/agent/PRIVATE/B06/scope-transition` | PASS |

## PUBLIC Prompt 数据库夹具补测

用户明确授权临时修改数据库后，在同一隔离 standalone 的嵌入式 Derby 中执行，保持默认鉴权、默认可见性和权限缓存开启：

1. 通过正常 HTTP 创建独立 owner、普通用户、r/rw 授权用户、无 API 权限用户，以及 9 个 PRIVATE Prompt（主资源、7 个独立删除副本、1 个 PRIVATE 对照）。
2. 停服后，用 JDBC 事务将主资源及 7 个删除副本共 8 行设为 PUBLIC，记录原 scope/owner/meta_version。每行按 namespace、type、完整 name、owner 和旧 scope 精确匹配，影响行数必须为 1；owner 不变，meta_version 加 1，gmt_modified 更新。PRIVATE 对照保持不变。
3. 启动匿名关闭配置，执行匿名 PUBLIC 的 6 项拒绝检查；再以匿名开启配置启动，执行六类身份的详情、列表分页、搜索、标签写、删除，以及匿名入口限制和授权边界。
4. 7 组全部通过，43 项检查全部通过；其中 6 项额外边界为授权管理限制、空/非法凭据、相邻 PRIVATE 隔离、基础 API 权限、rw 降级和授权撤销。与初始轮合并后，边界检查共 91 项通过。

本次使用的条件化更新形式：

```sql
UPDATE ai_resource
SET scope = 'PUBLIC', meta_version = meta_version + 1, gmt_modified = CURRENT_TIMESTAMP
WHERE namespace_id = 'public' AND type = 'prompt'
  AND name = ? AND owner = ? AND scope = 'PRIVATE';
```

唯一测试前缀为 `vm-db-prompt-674a08899e-`。完成后删除本次资源、测试账号和角色/授权，服务端已停止，原启动配置已恢复。最终数据库核对：资源 0、测试用户 0、角色绑定 0、资源授权 0。

**清理阶段另有异常：** 管理员调用授权撤销接口返回 403，提示 `No permission to manage visibility grants`。本次 43 项矩阵检查均已通过，但清理脚本因此先以失败退出；之后停服核对并定向删除了本次剩余的 4 条资源权限和 3 条内部角色绑定，重新核对为零。这不是矩阵通过项，也不因后续清理完成而掩盖。该管理员授权管理路径需另行排查；现有 `ONLY_IDENTITY` 入口与 `isGlobalAdmin` 标记装载的组合值得复核。

本地证据包含 43 项检查的 JSONL、`summary.json`、修改前后行快照、精确更新记录与清理计数。
一次性 Python/JDBC 夹具脚本和原始日志未纳入仓库；本报告保留执行步骤、SQL、逐组合结果和清理结论。
矩阵断言结果与后续人工清理结果分别记录，原脚本的清理失败未被后续清理成功覆盖。

## 范围与证据

- [测试设计、前置条件和运行命令](AI_VISIBILITY_ACCOUNT_MATRIX.md)。此次写操作矩阵覆盖标签与删除，未声称覆盖每个账号的所有生命周期、运行地址和 Console API。
- 本地原始证据含 `off-checks.jsonl`、`on-checks.jsonl`、`matrix-results.json`、两轮 Maven 日志和 JUnit XML，未纳入仓库。本报告保留逐组合及边界结果；复现时可用测试设计文档中的 JSONL 输出参数重新记录。
- 首轮请求构造错误记录保留在 `initial-run/` 以及带时间戳的 JSONL 中；权限结果合并修正后的完整轮及定向复验；创建阶段的 500 异常另外保留。

## 静态验证

`test/openapi-test` 的 Spotless apply/check、test-compile、Checkstyle 和 Apache RAT 均通过。HTTP IT 保持上述真实失败状态。
