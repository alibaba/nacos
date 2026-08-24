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

# API 集成测试规范

本规范定义 Nacos HTTP API 必须遵守的集成测试模型。凡是通过 HTTP
Controller 或 OpenAPI 文档暴露的 Open API、Admin API、Console API 和
Auth API 变更，都适用本规范。

API IT 的目标是 API 场景覆盖，不是行覆盖率或分支覆盖率。测试必须证明部署
后的服务端对外可见契约符合预期。

## 1. 范围

API IT 的主要位置是 `test/openapi-test`。该模块基于已经启动的单机 Nacos
服务，以外部 HTTP 客户端方式访问 API。

本规范覆盖：

- 新增、修改、删除或废弃 HTTP API 路由；
- 修改请求参数、校验规则、默认值、请求体结构、上传文件、请求头或查询参数
  序列化方式；
- 修改响应状态码、`Result<T>` 返回体结构、下载或流式响应结构、错误码、
  message 或领域字段；
- 修改 API 对外可见的业务行为、副作用、鉴权、兼容逻辑或生成的
  OpenAPI/Swagger 定义。

单元测试和 Controller 测试仍然可能是必要的，但不能替代 API 对外 HTTP 行为
的集成测试。

## 2. API 变更规则

在实现 API 新增、修改、删除或废弃前，变更负责人必须先完成 IT 影响分析：

1. 识别受影响的 API 面向对象以及现有 IT 类。
2. 阅读 Controller、Form/Request 模型、校验器、响应模型、Service 路径、
   异常处理和对应领域规范。
3. 形成场景矩阵，覆盖预期功能、边界/校验行为以及异常/错误处理。
4. 在同一个变更集中新增、更新或移除 `test/openapi-test` 用例，使其匹配新的
   API 契约。
5. 更新 `test/openapi-test` 下的覆盖索引，例如 `API_TEST_COVERAGE.md` 以及
   各 API 面向对象的场景文档。

如果功能成功路径在单机 IT 环境中难以实际执行，仍然必须尽可能覆盖校验、
边界、响应契约和受控错误场景。未覆盖的功能路径及原因必须记录在场景索引
或类 Javadoc 中。

## 3. 必须覆盖的场景组

每个 API IT 都应覆盖以下场景组，除非该组对该 API 不可观测。跳过的场景组
必须说明原因。

### 3.1 预期功能

测试必须证明 API 能完成设计目标。优先使用创建后查询、更新后查询、发布后
读取、删除后确认不存在、列表/过滤断言等方式验证持久副作用或返回的领域
状态。

断言必须检查重要响应字段，不能只判断 HTTP 成功。

### 3.2 边界和校验

测试必须根据代码分析覆盖重要请求边界，包括必填字段、可选默认值、空字符串、
枚举值、分页、命名空间/分组/名称规范化、异常 JSON、上传边界、版本选择、
过滤条件，以及被接受但忽略的参数。

当输入空间很大时，应覆盖契约等价类，并在场景文档中记录剩余风险。

### 3.3 异常和错误处理

测试必须验证关键失败分支是受控的：

- 参数校验失败应返回 HTTP 400，而不是 HTTP 500；
- 不存在、冲突、禁用、未授权或非法状态错误应符合 Controller 契约；
- 使用 `Result<T>` 的 JSON 错误返回应保持期望的 `code`、`message` 和
  `data` 结构；
- 下载或流式 API 在实现暴露错误返回时，也应对非法输入返回受控错误。

## 4. 测试组织

API IT 应按 API 面向对象和领域组织：

- Client OpenAPI：`com.alibaba.nacos.test.openapi.client.<domain>`
- Admin API：`com.alibaba.nacos.test.adminapi.<domain>`
- Console API：`com.alibaba.nacos.test.consoleapi.<domain>`
- Auth API：新增 Auth API IT 时使用
  `com.alibaba.nacos.test.authapi.<domain>`

建议一个 API 端点或一组强关联 API 工作流对应一个测试类。测试类可以使用辅助
API 创建前置资源或清理数据，但文档中的场景矩阵应聚焦在该类命名的 API 上。

多个 IT 类都需要的 HTTP 客户端构造、基础地址构造、JSON 断言、重试辅助方法
和清理逻辑，应抽象到基础类中复用。

## 5. 测试数据和运行规则

API IT 必须保持数据隔离和可重复执行：

- 对可变资源生成唯一名称；
- 只有 API 契约支持时，才使用 public 命名空间默认值；
- 使用 `finally` 或测试清理辅助方法清理创建的资源；
- 清理逻辑应容忍资源已经不存在；
- 避免修改共享运行时状态，除非被测 API 必须修改且测试会恢复原状态；
- 仅在异步服务端效果需要时使用有界重试。

单机测试环境通常关闭鉴权。需要鉴权的场景必须显式处理 token，并与关闭鉴权
环境下的 API 契约测试隔离。

## 6. API 删除和废弃

删除 API 路由时，必须在同一变更中删除或更新对应 IT 覆盖。如果兼容行为仍然
保留，应为废弃或兼容路由补充 IT，并记录迁移预期。

删除、重命名请求或响应字段，或改变字段语义时，IT 必须验证新契约；必要时
还要验证旧契约的兼容或拒绝行为。

## 7. 场景文档

每个 API IT 都必须让维护者能够看到它覆盖了哪些场景。较小的测试类可以使用
类 Javadoc 的 `Scenario coverage` 小节；较大的 API 面应更新
`test/openapi-test` 下的 Markdown 场景索引。

文档必须说明验证了什么，而不是只列测试方法名。文档还必须记录有意未覆盖的
分支、被接受但忽略的参数，以及单机环境限制。

## 8. 验证

API IT 变更需要对 `test/openapi-test` 运行格式化和编译验证。在单机 Nacos
服务可用时，应运行相关 Failsafe IT 选择或对应 API 面的全量选择。

仅修改 IT 覆盖索引文档时，最低验证要求是受影响模块的 license 和格式检查。

## 9. AI Resource Search 与 Agent 场景

共享 Search Core、Agent projection 或 ARD Agent 表示变更时，OpenAPI IT 场景矩阵至少覆盖：

- ARD 关闭但 `nacos.ai.resource.search.enabled=true` 时，RAD 和资源专用 Search 仍可使用基础索引；
- Agent 名称 literal contains、Tag ALL、Protocol ANY、组合 AND、大小写及 `%`、`_`、`\\`
  字面量，首/中/尾/越界页与正确 total；
- 创建、metadata 更新、Version publish/online/offline/delete、latest/label 变化后的有界等待收敛；
- Endpoint register/deregister/heartbeat 只改变 Discover，不改变目录 Search document；
- `AUTO` 或 `INDEX` 未 READY 时成功返回且不混合的当前快照、最终完整收敛，以及 `SCAN` 始终走兼容路径；
- 通用 Search 指定单一 Agent、AgentSpec、Skill、Prompt 或 MCP 时，与对应资源专用 Search 的候选
  资格、可见性和当前性结果一致；
- ARD 纯 A2A、多协议和只有旧 online Version 支持 A2A 的 type filter、primary 表示、稳定
  identifier、representation-specific Artifact URL、offline/digest 失效和 Runtime 状态排除。

测试异步索引时只允许有界轮询公开 API 可见结果，不得依赖固定 sleep、数据库内部行或任务执行顺序。

## 10. MCP 迁移与生命周期场景

实现 MCP 标准迁移或管理生命周期时，OpenAPI IT 场景矩阵至少覆盖：

- 切换前后现有 Admin/Console create/update/query/list/delete 响应形态，包括兼容专用的同 Version
  overwrite 和 latest 参数；
- 新 Version list/detail 以及 Draft、Submit、Reviewed/Publish、Force Publish、Redraft、
  Online/Offline、自定义 Label 和非法状态路径，并保证 Admin 与 Console 语义等价；
- Enable Resource 通过历史运行时投影只暴露 online Version，Draft/Reviewing/Reviewed/Offline
  只通过新的管理读取暴露；
- 历史 Fixture 在 `SYNCING` 期间保持完整可见、异步对账幂等、全节点门禁、零差异自动切换、
  重启后状态保持，以及标准路径不回退历史数据；
- Server/Tools/Resources Config 坐标和字节不变，唯一例外是确定性的 Direct Server 快照扩展；
- 历史单/多 Instance Direct 快照物化、标准读取不再依赖 Naming、切换时保留 Direct 投影，
  以及带 owner/hash 隔离的删除重试；
- 普通 Service Ref 的非所有权边界，且不误删外部所有的 Naming 数据；
- 内容缺失、非法 Manifest、row 冲突、Storage 部分删除失败和 Direct 不一致均表现为受控行为，
  同时阻止切换；
- 通用/MCP 专用 Search、统一 Import 和 Registry Adaptor 结果在路由切换期间保持候选资格和当前性。

迁移测试只把公开行为和重启后的耐久结果作为断言契约。测试准备可以写入文档化的历史 Fixture，
但不能用直接数据库 row 断言作为成功标准。所有异步条件都使用有界轮询，不使用固定 sleep。
