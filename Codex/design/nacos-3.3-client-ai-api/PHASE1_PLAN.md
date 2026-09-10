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

# 第一步：接口委托、资源 transport 与兼容测试

状态：2026-09-10 第一步代码与兼容测试已落地，按三个本地 commit 交付。验证结果、既有 Disabled 和迁移/重启环境缺口见 [VALIDATION.md](VALIDATION.md)。本文优先于本目录之前关于 A2A/RAD 完整切换的实施安排。

## 1. 交付边界与验收结果

本轮完成五个资源入口、旧方法兼容委托、资源 transport 配置、共享生命周期的必要装配调整，以及对应 UT/IT。使用方式：

```java
ai.mcp().getMcpServer(name, version);
ai.skill().downloadSkillZip(skillName);
ai.agent().discoverAgent(reference);  // 新 Agent/RAD，由 agent transport 控制
ai.agent().getAgentCard(agentName);   // 旧 A2A，第一步始终为原 gRPC
ai.getAgentCard(agentName);           // Deprecated 桥接到上一行，同一路径
```

旧 A2A 在老服务器和 3.3 上均保留当前请求、Endpoint redo、Card 投影和监听行为。服务端当前的 LEGACY/AUTO/CANONICAL 处理保持现状；Client 不增加迁移判断。

| 本轮包含 | 留给后续 |
| --- | --- |
| `mcp()/agent()/skill()/agentSpec()/prompt()`；default 委托与 Deprecated | A2A→RAD DTO 转换及 publisher 隔离 |
| 新 Agent API 从 AiService 迁到 agent()；保留已发布旧 API | HTTP capabilities 入口、a2aCompatV1、迁移状态路由 |
| 五个资源 override；Skill/AgentSpec 的 HTTP 退化 | 新 HTTP API、服务端 Handler、存储或迁移算法改动 |
| AUTO 的资源隔离、共享连接需求与既有恢复行为 | 统一 Service 注册器、SPI、通用 transport 框架 |
| 公开方法、旧字节码、混合 transport、监听和生命周期验证 | 为未来新 wire 建立全量混合版本迁移测试矩阵 |

主要生产改动位于 `api`、`client`；其他模块原则上只调整尚未发布的新 Agent 调用点。`maintainer-client` 的接口和行为不改，`ai` 服务端不改。发现超出范围的问题单独记录，不能借此扩大第一步。

## 2. 接口与委托的具体安排

沿用总体设计的五个子接口；其中新建 McpService、SkillService、AgentSpecService、PromptService、AgentService，复用 A2aService 和 AgentDiscoveryService。

- AiService 继续具备 MCP/A2A/Skill/AgentSpec/Prompt 旧签名；在 facade 的旧声明上标记 Deprecated 并给出替代入口。子服务方法本身不废弃。
- AgentService 继承 A2aService 和 AgentDiscoveryService，接收 publishAgent；AiService 不继承二者中的新 Agent 接口。开发分支里未发布的 Search/Discover/Watch/publication/publish 调用同步迁到 agent()。
- getter 是兼容 default（默认明确不支持），官方实现返回固定 delegate；getter 不新建连接、缓存、监听器或 publication manager。
- 便利 default 仍补参数后调用 `this` 的核心方法，核心方法才委托 getter。不能让旧第三方实现的 override 失效。
- NacosAiService 使用私有资源 delegate，必要的方法体原样归入 delegate，原校验、参数默认、异常、缓存/Notifier/manager 和初始化/关闭顺序保持。delegate 不反调 facade。
- 官方旧方法若必须保留 override，只允许纯桥接；核心业务只存在一份。Agent delegate 的 A2A 分支仍调用 `requireGrpcClient()`，新 Agent 分支仍调用既有 router。

### 2.1 MCP createDraft 的特殊兼容处理

现有五参 `releaseMcpServer(..., createDraft)` default 在 false 时分派旧四参方法，在 true 时给未实现者返回不支持。不能统一改成 getter 委托后丢掉这项第三方行为。

方案：AiService 保留这个兼容 default；官方 NacosAiService 保留五参 override，但方法体只有 `return mcp().releaseMcpServer(..., createDraft)`。Mcp delegate 实现原业务。测试同时证明第三方旧 override 仍被调用，以及官方 true 确实创建 draft，没有循环或误上线。

### 2.2 调用点迁移

全仓检索新 Agent 的公开方法、AiService 到 AgentDiscoveryService 的赋值/参数、示例和反射测试。特别检查 API 模块中的同名 `NacosAiService` 测试替身、Client 反射注入测试，以及 `JavaSdkBaseITCase` 的 `service.searchAgents()` readiness。

普通 RAD fixture 将 readiness 改为 `service.agent().searchAgents()`。测试无 gRPC、只有 Skill/AgentSpec 或旧服务端时使用现有 `createAiServiceWithoutReadiness()`，以被测资源自己的读操作等待就绪，不能让 Agent 探针污染路由证据。

## 3. 资源 transport 的确定规则

仍用 `AgentTransportMode` 和 `grpc/http/auto`，不新增枚举。全局键 `nacosAiTransportMode` 默认 grpc。资源键：

| 资源键 | 控制范围 |
| --- | --- |
| `nacosAiMcpTransportMode` | MCP 查询/release/Endpoint/订阅 |
| `nacosAiAgentTransportMode` | 新 Agent Search/Discover/Watch/Endpoint/publish；不控制旧 A2A |
| `nacosAiSkillTransportMode` | Skill 下载及查询/订阅；实际统一 HTTP |
| `nacosAiAgentSpecTransportMode` | AgentSpec 读取/订阅；实际统一 HTTP |
| `nacosAiPromptTransportMode` | Prompt 直接查询与订阅轮询 |

解析：`resourceOverride ?? global ?? grpc`，再按资源支持矩阵得到 effective。构造时冻结，全部显式值在创建有生命周期的对象前校验；沿用现有枚举解析，大小写兼容，空串/空白/首尾空格/未知值拒绝。即使 global 被所有子键覆盖，或 Skill 最终总用 HTTP，非法显式值也不能被掩盖。

| 方法族 | grpc | http | auto |
| --- | --- | --- | --- |
| MCP | gRPC | HTTP | 已有 MCP 能力与连接判断；安全读按连接错误回退 |
| 新 Agent/RAD | gRPC | HTTP | 已有 RAD 能力与连接判断；Watch 沿用原能力/回退规则 |
| Prompt | gRPC | HTTP | 连接可用先 gRPC，否则 HTTP；只读连接失败可 HTTP |
| Skill / AgentSpec | HTTP | HTTP | HTTP |
| 旧 A2A（含旧扁平入口） | 旧 gRPC | 旧 gRPC | 旧 gRPC |

Prompt 当前没有独立 SERVER_PROMPT 能力位；第一步不创造该能力或新探测协议。只加薄的 queryPrompt 路由代理，直接查询和原 cache holder 使用同一代理，按既有连接事实选路。服务器缺功能时返回实际错误，不把业务 unsupported 当网络错误处理。

Skill/AgentSpec 直接向现有 holder 注入共享 HTTP proxy，Skill ZIP 继续当前 HTTP 下载。HTTP 也不可用或服务端不支持时正常失败；“退化”不承诺模拟服务器缺失的功能。

### 3.1 错误与有状态操作

- 显式 GRPC 网络失败不改成 HTTP；AUTO 只允许等价只读操作在有连接类错误证据时回退。已识别的 401/403、参数、404、冲突和容量错误优先返回，不能仅凭恰好断连而改写成另一路成功。
- publish/release/注册等已发出的写结果不明时不换 transport 重发。Agent/MCP publication owner 一旦选定，替换、注销、心跳和 redo 继续原 manager 的规则。
- 原生 HTTP 不增加能力发现；gRPC 能力检查保持现状。旧服务端的新 Agent 操作不可用，但不在本轮统一不同 binding 的“不支持”错误码。
- `agent=http` 不代表禁止旧 A2A 使用 gRPC。全部资源 HTTP 且只使用原生 API 时不启动 gRPC；一旦调用旧 A2A，原 requireGrpcClient 路径可以启动/恢复它。getter 本身不触发该动作。

## 4. 共享连接的必要调整

当前 AgentGrpcTransport 混合了 mode、共享启动、AUTO HTTP 成功和暂停重连状态；MCP 从它读取 Agent mode。仅加配置键会导致资源串扰。

最小调整是将 Agent/MCP/Prompt 的 mode 与 AUTO 记录归属到各自资源路径，AgentGrpcTransport 继续持有唯一 AiGrpcClient 和共享启动/重连控制。允许少量固定资源状态或内部辅助结构，不升级为通用连接管理框架。

| 条件 | 必须保持的动作 |
| --- | --- |
| 至少一个有效 GRPC/AUTO 资源 | 按当前构造规则启动共享 gRPC，至多启动一次 |
| 全部有效资源 HTTP，未调用旧 A2A | 不启动 gRPC；只设置 Skill=grpc 也不会创造 gRPC 需求 |
| 任一资源显式 GRPC | 注册强制需求；其他资源 HTTP 成功不能暂停共享重连 |
| 首次使用旧 A2A | 沿用 requireGrpcClient 的黏性需求，恢复必要重连；不新增复杂引用计数 |
| 某 AUTO 资源 HTTP 成功 | 只更新自己的状态，不能使其他资源的 AUTO 判定直接变为 HTTP 稳定 |
| 从未连上、失败达到既有阈值、无强制/旧 A2A 需求、所有已使用 AUTO 资源均 HTTP 成功 | 可以暂停初始重连；未使用资源不算成功，首次使用时恢复其必要探测 |
| 已经连接过后断线 | 保留原 UNHEALTHY 重连行为，不纳入“初始探测失败”暂停 |
| 新旧 getter 混用、关闭或 HTTP clientId 失效 | 共用原缓存和 owner；统一关闭；Agent/MCP 继续共同恢复，不能多建心跳循环 |

HTTP 成功的记录不能调用阻塞网络；共享状态锁内不执行新 HTTP 请求或用户回调。变化只服务于资源隔离，不改连接重试间隔、阈值、Naming/Config 连接或 server list 算法。

## 5. 风险与控制点

| 风险 | 控制 / 验证 |
| --- | --- |
| default 方法环路、便利重载绕过旧 override | API 单测、旧第三方实现字节码；createDraft 单列用例 |
| 提取 delegate 后改了监听身份或持有第二份缓存 | 新旧入口交叉订阅/取消、同对象交叉读写、shutdown 后观察窗口 |
| MCP/Prompt 被 Agent mode 或 HTTP 成功带偏 | 混合模式成对测试；定向验证实际请求而非只断言结果成功 |
| HTTP 配置下旧 A2A 启动 gRPC 引起误解 | 配置 Javadoc/例外说明；HTTP-only 与旧 A2A 测试分开 |
| AUTO 暂停了其他资源/旧 A2A 所需重连 | 连接状态表 UT；阻断/恢复 gRPC 的外部 IT；首次使用休眠资源测试 |
| Skill/AgentSpec 改走 HTTP 后身份/可见性或 304 处理不同 | 原 Client 身份，不借管理员查询；数据/MD5/缺失恢复和授权失败验证 |
| 旧 facade 和新子接口表面相同但二进制不兼容 | 用已发布 API 编译业务和第三方实现；不重新编译，用新 SDK 运行 |
| 未发布新 Agent 的调用点漏迁移 | API/Client/test reactor 编译；检索示例、测试替身、公共参数类型 |
| 为“整理代码”扩大到缓存/迁移/业务模型重构 | C1 比对移动前后方法体；C2 仅 transport 注入与生命周期必要部分 |

## 6. 测试项与对应落点

下表是第一步验收清单。实际执行证据记录于 VALIDATION.md；未执行项不能记 Covered。

| ID | 必须验证的行为 | 主要层次 / 落点 |
| --- | --- | --- |
| P01 | 已发布签名、返回值、异常、默认参数；getter 默认不支持；MCP createDraft 分派 | `api` 的 AiServiceDefaultMethodTest、A2aServiceDefaultMethodTest、Factory 替身 |
| P02 | 官方单向委托、固定 delegate、参数校验与 DTO 行为相同 | NacosAiServiceTest；必要的 getter/default 分派测试 |
| P03 | 五资源新旧入口互查、交叉注册/注销、订阅/取消，同一 listener 不重复 | 复用 AiServiceJavaSdkITCase 和 AiTransportResourceMatrixJavaSdkITCase；不额外拆重复 fixture |
| P04 | 未发布 Agent 调用全迁移、AgentService 两组能力、AiService 不再继承新 Agent 接口 | 编译测试 + 现有 AgentDiscoveryService/AgentPublish IT 迁移 |
| P05 | 全局继承、单键覆盖、有效大小写、全部非法显式值、构造冻结 | 配置解析 UT；Factory 负面 IT；不穷举 3^5 笛卡尔积 |
| P06 | global=grpc：agent=http、mcp=grpc、prompt=http、skill=grpc、agentSpec=auto；反向组合 | 扩展 AiTransportResourceMatrixJavaSdkITCase；router UT 证明选中 binding |
| P07 | Skill/AgentSpec 在三个 requested mode 都通过 HTTP 成功；Prompt 查询和轮询一致 | 修改原“不支持 gRPC”断言为 HTTP 正向；覆盖 304、404→恢复、ZIP/MD5/内容与 listener |
| P08 | 五资源有效 HTTP、gRPC 端口完全不可达 | 外部 HTTP-only harness；只执行原生方法，验证无隐式 A2A/readiness 连接 |
| P09 | 旧 A2A 在 grpc/http/auto 下固定旧 gRPC；新旧 Java 入口完全一致 | 协议 UT 校验旧 Payload；普通 A2A IT 对照。阻断 gRPC 时 A2A 失败，原生 HTTP 仍可用 |
| P10 | AUTO 独立成功状态、强制 GRPC/旧 A2A pin、休眠资源首用、从未连接与曾连接断线 | AgentGrpcTransport/AgentTransportRouter/McpTransportRouter UT，加 Prompt 路由测试及定向重连 IT |
| P11 | 认证/参数/未找到/容量/冲突不回退；连接失败安全读回退；写结果未知不重发 | router/协议 UT 控制竞态，auth IT 验证公共异常与真实身份 |
| P12 | Agent/MCP owner 固定；共享 HTTP 失效后共同 replay；重连和关闭无互相覆盖 | 复用现有 Endpoint manager、McpHttpClient 和 Agent 重连 IT，不改注册语义 |
| P13 | namespace、默认参数、防御性快照、取消后重订阅、幂等 shutdown 与停止回调 | 既有资源 IT + 新入口交叉断言；保留普通 Naming 隔离对照 |
| P14 | 已发布旧业务 class + 第三方 AiService 实现替换新 SDK 后运行 | 总体 IT 中的兼容组合 C2：独立 classpath fixture；校验 NoSuchMethodError/AbstractMethodError、旧 override，不用同名新测试替身冒充 |
| P15 | 代表旧服务器上的旧能力；真实旧 SDK 与新 SDK 在新服务器上的旧 wire | 小范围独立 JVM smoke，按方法实际发布版本固定依赖；不扩成未来新 wire 的全量迁移矩阵 |
| P16 | 既有 A2A/MCP migration 用例仍走旧兼容路径；错误、shadow、两版本旧注册不变 | 现有定向 migration 回归，仅改编译必需的新 Agent 调用点 |

旧字节码兼容（P14）是接口拆分的硬门禁。P15 只选与本轮签名/委托相关的旧版本代表线，精确坐标和依赖树在实现时冻结，不依赖“@Since 就证明已发布”。P16 按已有 harness 执行，不新建迁移机制。环境不可用时记录缺口，不能宣称第一步已全部验证。

测试职责：UT 证明路由、default 分派、并发/失败边界；IT 只从公开 Factory/service 观察返回值、回调和远端副作用。路由实际选择通过协议测试和端口阻断证明，普通 IT 不反射 SDK 内部字段。Maintainer 仅准备/清理 fixture，Client 使用原授权身份。

同步更新 `JAVA_SDK_IT_SCENARIOS.md`、`JAVA_SDK_IT_COVERAGE.md` 和受影响的 AI 场景文档。不存在本轮新 HTTP API，因此不新增 OpenAPI Controller 场景；既有 HTTP binding 的 Client 消费行为由 SDK IT 验证。完整 A2A 新 binding 的 A/D 矩阵留后续，不作为第一步门禁。

## 7. 第一步的 commit 划分

建议第一步三个实现 commit；每个 commit 都携带对应测试及规范/场景文档，不留“先破编译、最后统一修测试”的中间点。按以下边界分别提交，用户要求仅创建本地 commit，不提交 PR。

| Commit | 内容 | 该 commit 的验证出口 |
| --- | --- | --- |
| C1 `refactor(client): expose AI resource services with legacy delegates` | 五资源接口、AiService 继承/default/deprecation、官方 delegate 与 createDraft 桥接；迁移全部未发布 Agent 调用点、API 替身、readiness；补 P01–P04 与基本交叉 IT，更新接口规范/场景表。transport 暂保持原状 | API/Client UT，受影响模块及 SDK IT test-compile，新旧入口基本 IT；方法体机械迁移审查；已有 Skill/AgentSpec 的旧 transport 预期在此 commit 仍保留 |
| C2 `feat(client): isolate AI resource transport policies` | 五个属性、预校验、各 router mode、Prompt 薄路由、Skill/AgentSpec HTTP 注入、共享启动/重连需求；P05–P13 中相关 UT 与资源矩阵 IT 同时修改，A2A 固定旧 gRPC；同步 transport 规范 | 混合配置、HTTP-only、AUTO 故障、授权、owner 恢复与生命周期通过；Skill/AgentSpec 正向 HTTP 用例取代旧不支持断言 |
| C3 `test(client): verify AI facade and transport compatibility` | P14–P16 的旧字节码/独立进程 smoke 与现有迁移定向回归接入；补跨模块组合验证和最终覆盖记录。前两 commit 的功能修复回填相应 commit，C3 不承载遗漏的生产实现 | 默认 JSON 和 jackson3-sdk-test 组合、旧字节码 fixture、代表旧服务器/旧 SDK、定向迁移回归；保留版本、报告及实际跳过项 |

三个 commit 可作为同一个第一步 PR 顺序评审。若 C2 因共享状态改动过大，可把其内部“配置/装配”和“故障边界验证”拆成额外 commit，但不把相关功能的必要测试全部推给 C3。

后续阶段：第二步定稿并提供 A2A/RAD 最小契约/能力支持，第三步才启用 Client 转换和模式切换；若范围可控可合并这两步。第一步不依赖它们完成。

## 8. 验证顺序与完成定义

1. 实施前固化基线签名/旧行为和本阶段场景表。实施后先跑受影响 API/Client UT；Java 8 API 源/目标兼容、default 继承和测试替身必须通过。
2. 每个 Java commit 前按仓库要求执行相应模块的 `spotless:apply`、`spotless:check`，再做相关编译与测试。跨模块依赖按现有 reactor 构建，不用旧本地制品掩盖问题。
3. `mvn -pl test/java-sdk-test -DskipTests test-compile` 验证调用点迁移；依赖未安装时先构建对应依赖。
4. 外部 standalone 服务端准备好原鉴权/容量环境后，运行 `mvn -pl test/java-sdk-test -Pjava-sdk-integration-test -DskipTests=false verify`；再按现有工作流验证 `-Pjava-sdk-integration-test,jackson3-sdk-test`。
5. 用外部 harness 跑 P08/P10/P12/P14–P16；复用 `.github/workflows/it-new.yml` 和 `migration-it.yml` 的启动、权限、ready/restarted marker 和报告约定，不在 JUnit 中启动服务端。不伪造尚未实现的旧制品 fixture 命令。
6. 提交 PR 前执行仓库要求的 compile/RAT/Checkstyle/SpotBugs/Spotless 等检查，保存相关 UT/Failsafe/独立 JVM 报告。验证通过后不无理由重复扩大测试。

第一步完成意味着：五入口和旧签名可用、旧 A2A 固定原 gRPC、资源配置不串扰、HTTP-only 原生路径成立、Skill/AgentSpec 退化成功、共享 owner/监听/关闭不变、必要兼容门禁有证据。所有未来 capability、新 wire 和语义转换仍单独标为未实现。

## 9. Review 修正：Client 入参不暴露 namespaceId

Search/Endpoint 三类入参改为 `model.agent.AgentSearchQuery`、
`AgentEndpointRegistration` 和 `AgentEndpointDeregistration`。不从带 namespace 的传输类继承，
不保留旧传输类型的公开重载；它们属于 3.3 尚未发布的 Agent API，不影响 3.2.x A2A。
Client 复制入参后，把实例 namespace 注入原有 `model.rad` 请求/Batch。
HTTP/gRPC、服务端、Maintainer 以及内部 publication/redo 的模型和算法不变。

验证：公开类型无 namespace JavaBean 属性；Search 列表及 Endpoint/metadata 防御复制；
空入参及既有业务校验；三种 mode 下 default/custom namespace 的搜索、注册、注销隔离，
以及原参数 JSON 不被修改。原同值/异值 namespace 入参测试由这些场景取代。
