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

# Client AI 新旧接口与 Transport 兼容 IT 设计

基线与目标范围见 [README.md](README.md)，HTTP 探测与迁移决策见 [A2A_ROUTING.md](A2A_ROUTING.md)。Client MCP getter 统一为 `mcp()`。本文件保留完整测试设计；第一步已经新增并执行部分用例，具体证据、覆盖边界和未执行项以 [VALIDATION.md](VALIDATION.md) 为准，不能将后续阶段的设计视为已通过。

**当前仅实施第一步，验收范围以 [PHASE1_PLAN.md](PHASE1_PLAN.md) 的 P01–P16 为准。** 第 3、4 节是接口/transport 的主要基础；第 5 节只复用现有旧 A2A 语义回归，新 A2A→RAD 适配断言留后续；第 8 节 HTTP 能力发现全部留后续。不得用未来 A/D 矩阵阻塞第一步，也不得把第一步通过标成完整 A2A/RAD 切换完成。

## 1. 现有 IT 应如何使用

| 当前测试 | 已有可复用的场景 | 本次最小调整 |
| --- | --- | --- |
| `AiServiceJavaSdkITCase` | MCP release/query/subscribe、draft、Endpoint、缺失和参数；A2A Card、重复 release、latest、批量 Endpoint、TLS、取消监听 | 保留旧扁平调用作为回归锚点；用相同场景和新子服务对照，不整类改成新入口而失去旧入口覆盖 |
| `AiTransportResourceMatrixJavaSdkITCase` | 三种总模式下五类 AI 资源及普通 Naming 隔离 | RAD 调用迁到 `agent()`；Skill 订阅与 AgentSpec 在 GRPC/AUTO 下的 `SERVER_NOT_IMPLEMENTED` 预期改成 HTTP 成功；补资源级 override |
| `AgentDiscoveryServiceJavaSdkITCase` | 新旧 A2A 互通、Search/Discover、namespace、完整批次/部分注销、容量、Watch、缺失、生命周期、重连/集群定向场景 | 新 Agent 调用改为 `agent()`；保留 A2A 断言；增加旧、新入口在同一个实例上的交叉操作 |
| `AgentPublishJavaSdkITCase` | draft/submit、冲突、HTTP/gRPC 一致、两版本旧 Endpoint、latest/exact/取消重订阅 | `publishAgent` 迁到 `agent()`；旧 release 继续单独断言直接 online/no-op/setAsLatest，不能复用新 publish 的预期 |
| `McpHttpClientJavaSdkITCase` | HTTP MCP 与 Client 生命周期 | 使用 `mcp()` 的新增场景，保留必要旧入口交叉注销 |
| `A2aUpgradeMigrationJavaSdkITCase` | AUTO/SYNCING、QUIESCING、cutover、shadow、容量、重连与集群迁移 | 增加新 SDK 的能力路由验证；保留真实 legacy wire 参与者，不能把原 legacy 参与者全部改成 RAD |
| `McpUpgradeMigrationJavaSdkITCase` | MCP 历史数据迁移与 serving 行为 | 保持迁移语义，仅补旧扁平/子入口一致性，不因拆接口改业务断言 |
| `AuthEnabledJavaSdkITCase` | 默认鉴权下的 SDK 行为 | 补新子入口与旧入口权限一致，拒绝业务错误触发 transport/protocol fallback |

上述文件位于 `test/java-sdk-test/src/test/java/com/alibaba/nacos/test/sdk/ai/`；鉴权测试位于相邻 `auth/`。具体复用以当前类的方法与场景文档为准，未执行的定向场景不能算本轮已通过。

特别注意：`JavaSdkBaseITCase.createAiService()` 当前用 `service.searchAgents()` 做 readiness。拆接口后需改用 `service.agent().searchAgents()`，但此探针仍只适用于 RAD 服务端。老服务端兼容测试必须使用 `createAiServiceWithoutReadiness()`，通过老版支持的公开读操作进行有界 readiness；资源混合模式也不能用 Agent 成功代表所有资源可用。

## 2. 三种兼容验证必须分开

| 编号 | 编译与运行组合 | 验证目的 |
| --- | --- | --- |
| C1 | 当前 3.3 SDK，旧扁平方法与新子入口，在同一服务器运行 | 方法委托、状态与事件一致；这是当前多数 IT 能扩展覆盖的内容 |
| C2 | 业务 fixture 用已发布旧 API 编译，**不重新编译**，替换成 3.3 SDK 运行 | JVM 二进制链接兼容、default 分派、旧方法签名与异常；补一个旧第三方 `AiService` 实现 fixture |
| C3 | 真实旧 SDK JAR + 它的依赖，在独立 JVM 中连接 3.3；与 3.3 新 SDK JVM 互通 | 旧 wire protocol 与新服务端兼容、旧新进程共同发布与查询；不是把新 SDK 的方法写成旧写法 |

旧版 fixture 以实际发布制品为准固定版本和校验值：MCP 最早的 3.0.3、A2A 3.1.0、批量 Endpoint 3.1.1、3.2.x 的 Skill/Prompt/AgentSpec、resourceSpecification、Skill 订阅等按各版本实际存在的方法编译。至少保留“最早支持该能力的已发布版本”与“最新已发布 3.2.x”两条代表线；不把 `@Since` 注解当成对应制品已发布的证明。

每个旧版本只测试当时存在的能力；老服务端不支持 Skill 等新资源时是明示的能力限制，不要求客户端模拟。C2 用 fixture 的同一份 class/JAR 先在旧运行时建立基线，再在新运行时比较。C3 不能在一个 classpath 同时引入两版 `com.alibaba.nacos.*`；需检查依赖树，避免 maintainer 或 reactor 依赖悄悄将旧 client/api 替换成 3.3。

现有两个 migration IT 类都使用当前 reactor SDK，不能取代 C2/C3。

## 3. 接口兼容与共享状态矩阵

建议新增一个紧凑的 `AiServiceInterfaceCompatibilityJavaSdkITCase`，复用原资源 fixture。只为新委托边界增加交叉场景，不复制全部原业务测试。

| ID | 必须覆盖的公开行为 | 当前状态 | 已有基础 / 新增断言 |
| --- | --- | --- | --- |
| I01 | Factory + 五个 getter + 生命周期 | Pending | 同一 getter 重复获取可复用同一子服务；getter 不产生额外连接；关闭 facade 后任务停止，重复关闭安全 |
| I02 | MCP 的 15 个签名与新旧入口等价 | Partial | 旧接口已有业务 IT；补旧 release→新 query、新 subscribe→旧 unsubscribe、反向组合；校验 ID、version、tools/resources/endpoints、draft flag，而非仅成功返回 |
| I03 | A2A 的 18 个签名与新旧入口等价 | Partial | 补 `ai.getAgentCard` 与 `ai.agent().getAgentCard`、旧注册→新查询/注销的交叉行为；全部便利参数默认值保持一致 |
| I04 | Skill 的 5 个签名 | Partial | 补新旧 ZIP 内容对照、version/label/latest、订阅缺失返回 null、创建后回调、内容变化、无变化不重复通知和交叉取消 |
| I05 | AgentSpec 的 3 个签名 | Partial | 补新旧对象字段与 resources 对照，缺失/恢复、订阅及交叉取消；不为测试增加公开查询重载 |
| I06 | Prompt 的 5 个签名 | Partial | 补 latest/version/label、内容与变量、订阅变更和交叉取消；保持已有 selector 优先级或冲突错误，不另定规则 |
| I07 | listener identity 在新旧入口共享 | Pending | 同 listener 不重复通知；不同 listener 互不影响；从另一入口取消后停止；取消后重订阅恢复；不同 namespace 隔离 |
| I08 | namespace 与调用方对象不被修改 | Partial | 沿用 RAD defensive-copy 和 namespace 校验；新旧入口不能访问其他 namespace，也不能修改请求对象 |
| I09 | 参数/异常契约 | Partial | blank/null name、缺必需 version、null listener、无效 IP/port、批量混版本等复用原断言；比较 `NacosException` 类别/错误码和必要字段，不只用 assertThrows(Exception) |
| I10 | 旧二进制及第三方实现 | Pending | C2：方法链接无 `NoSuchMethodError`/`AbstractMethodError`；旧 convenience 方法仍派发给第三方核心 override；未实现的新 getter 返回明确 unsupported，不能影响旧方法 |
| I11 | 新 Agent 只从子服务进入 | Partial | 既有 RAD 业务 IT 改调用入口；编译检查中 `AiService` 不再是 `AgentDiscoveryService`/`AgentService` 子类型，`AgentService` 同时具备 A2A/RAD |

这些状态描述本次目标的覆盖差距，`Partial` 仅表示存在相关旧行为测试，不表示新入口已实现或本轮已运行。该表 11 项中 Covered=0、Partial=8、Pending=3：严格覆盖率 0%，有效覆盖率约 36.4%；这不是整个 SDK 覆盖率。

注解位置、Java 8 编译、完整签名/default 方法清单和 getter 委托可用小范围 API/客户端单元测试验证，不需要为每个简单转发增加重复真实服务端 IT。所有已发布 overload 至少在编译 fixture 中调用一次；业务 IT 选择有不同语义的 overload，避免镜像实现式测试。

## 4. Transport 组合与失败边界

扩展 `AiTransportResourceMatrixJavaSdkITCase`，通过公开 Factory 的 `Properties` 创建实例；子配置不在构造后修改。

| ID | 设置 / 条件 | 预期 |
| --- | --- | --- |
| T01 | 总设置和子设置均缺省 | MCP/Agent/Prompt 保持默认 GRPC；Skill/AgentSpec 经 HTTP 成功 |
| T02 | 总设置分别为 grpc/http/auto，子设置均缺省 | 五个子服务继承总设置，按支持矩阵执行；新旧入口等价 |
| T03 | global=grpc，agent=http，mcp=grpc，prompt=http，skill=grpc，agentSpec=auto | Agent/Prompt/Skill/AgentSpec 使用 HTTP，MCP 使用 gRPC；任一 getter 不改变其他资源策略 |
| T04 | global=http，agent=grpc，mcp=http，prompt=grpc | Agent/Prompt 可启动共享 gRPC；MCP 始终 HTTP；每种 owner 均能注册/查询/注销 |
| T05 | Agent 与 MCP 互换 grpc/http/auto 子设置 | 验证 MCP 不再读取 Agent mode；Agent Watch 跟随 Agent mode，而不是总 mode |
| T06 | 单资源未覆盖 + 相邻资源有覆盖 | 未覆盖资源继承 global；覆盖不反向修改 global；Skill 强制 GRPC 仍按能力退化 HTTP |
| T07 | 大小写有效值；空串/空白/带空格/未知值 | 有效大小写被接受；global 或任意子键非法均在 Factory 构造时受控失败并指出键；失败不留下线程或连接 |
| T08 | 所有有效资源 HTTP，gRPC 端口不可达；不调用旧 A2A | 原生 Agent/RAD、MCP、Prompt、Skill、AgentSpec 的读、订阅和 publication 完成；不因 readiness/getter 启动 gRPC；本轮不新增能力探测 |
| T09 | AUTO 初始 gRPC 不可用，HTTP 可用；同实例另有强制 GRPC 资源 | AUTO 资源可 HTTP；强制 GRPC 继续重连，不能被其他资源 HTTP 成功暂停 |
| T10 | AUTO 已连接后掉线、恢复 | 只读允许连接类失败回退；恢复规则按原契约；publication 维持既有 owner，无重复注册 |
| T11 | 401/403、业务 404、参数、冲突、容量、普通 5xx | 原错误可见；不触发 transport 或 A2A/RAD 降级，不变成成功/空结果 |
| T12 | 写操作超时，结果未知 | 不换 transport 重发；保留原有 redo/确认行为；单服务端断言无重复或覆盖其他 publisher |
| T13 | Skill/AgentSpec 显式 grpc/auto | 下载/读取及订阅都成功，不再期待 SERVER_NOT_IMPLEMENTED；304 无重复事件，404/恢复与 HTTP 模式一致 |
| T14 | Agent 与 MCP 共享 HTTP owner，服务器重启导致 clientId 失效 | 两类 publication 一起恢复，不能只恢复触发心跳的一方；子 getter 不创建额外 clientId/心跳循环 |
| T15 | 新旧服务端，agent=grpc/http/auto 下调用旧 A2A | 第一步均固定旧 gRPC；旧扁平与 agent() 同一路径；HTTP 能访问而 gRPC 不可达时 A2A 失败、原生 HTTP 不受影响，不调用 Admin API |

普通 IT 断言资源的可观察结果；路由器单元/协议测试断言具体 proxy/Payload 选择、fallback 次数、初始化次数和共享连接暂停条件。纯 HTTP 定向运行由外部 harness 阻断 gRPC 端口作为额外证据。不能仅凭一次返回成功就宣称验证了实际 transport。

现有 Naming 生命周期控制场景继续保留；不因为修改 AI routing 扩大到重写 Naming IT。

## 5. A2A ↔ RAD 必须保留的语义

这组用于完整适配验收，包括客户端转换和必要的服务端支持。选择方案 B 时，要按实际方法标明验证了原生 RAD 转换还是旧 A2A 路径，不能把局部成功标记为“全部 RAD 自动切换”。转换边界见 [CLIENT_MAPPING.md](CLIENT_MAPPING.md)。

| ID | 输入与状态 | 断言 |
| --- | --- | --- |
| A01 | 老服务端；RAD key 缺失/不支持；旧 registry 支持 | 仅原 A2A gRPC 可用；逐项断言 Search/Discover/Watch/RAD 批量注册/注销及 publishAgent 不可用且不会转换为旧请求；旧查询、release、endpoint、订阅与现有结果相同 |
| A02 | 未建立连接、能力未决、HTTP 探测网络失败 | 不误判老服务器，不发送试探写请求；受控连接错误 |
| A03 | RAD + 完整 A2A 适配能力的服务器 | 新旧 Java 入口走同一适配；协议测试确认未偷偷继续发旧 A2A Payload；能力不足则按已定义的兼容分支处理 |
| A04 | v1、v2 均 online，各自一个 Runtime Endpoint，latest=v2 | 旧省略 version 只返回 v2 池；RAD 省略 selector 仍保持跨 online 池，显式 label=latest 只返回 v2；不能统一两者预期 |
| A05 | URL/SERVICE/空 registrationType，Runtime 有/无 | native descriptor 字段完整；Runtime 投影和 DECLARED 回退一致；supportedInterfaces/additionalInterfaces/root URL、存储 registrationType、latestVersion 真值/null 保持旧契约 |
| A06 | 首次 release；新版本 setAsLatest=false/true；重复已有 online 版本且内容不同 | 首次 online + latest；false 不移动已有 latest；true 在新增版本时移动；重复已 online A2A 版本仍 no-op，无覆盖、无误移动 |
| A07 | 新 `publishAgent(autoSubmit=false/true)`；服务端启用审批 | draft/ordinary-submit 契约不变；旧 release 适配不能把通用 publish 改成强制上线，也不能把旧 release 变成 draft/reviewing |
| A08 | 同 SDK 旧接口同时注册 exact v1/v2，再用新 RAD 注册 a2a publication | 三份归属互不覆盖；重连后都恢复；旧注销只清理目标版本的旧 publication |
| A09 | 旧单条→旧批量→旧注销；与新部分注销对照 | 旧单/批注册按旧版本批次替换；旧 canonical 注销整版本；新 RAD 按自然键部分注销，二者不混用 |
| A10 | Endpoint 先于定义；定义后发布；两个 SDK 同时贡献 | 预注册不创建定义；定义可见后地址出现；一个 client 关闭/注销不影响另一个 |
| A11 | TLS、path、query、transport、protocolVersion、tenant 及无效值 | URI 和反投影完整；无效值仍受控拒绝，适配前后校验顺序与错误类别尽量保持旧契约 |
| A12 | latest/exact 订阅、指针移到已缓存版本、缺失后出现 | 旧 Card 事件正确；同一旧 listener 不重复；取消/重订阅恢复；不向旧 listener 投递新事件类型或伪造空 Card |
| A13 | 不含 A2A binding 的 Agent；禁用或不可见 Agent | 旧接口按旧 not-found/visibility 契约处理；不能把空 callInterfaces 当成合法 AgentCard |
| A14 | 新旧节点之间重连或旧节点升级 | 连接能力重新判断；存量 publication 保留已选择的协议和 owner，不能未经清理跨协议双写；新 publication 根据新连接选择；RAD owner 遇旧节点受控失败，不改写为旧 publication |
| A15 | 3.3 LEGACY / AUTO-SYNCING / QUIESCING / CANONICAL；有无 shadow | 新 A2A 适配由既有服务端路由选择权威，不要求 Client 切模式；QUIESCING 历史定义 mutation 返回 AGENT_MIGRATION_IN_PROGRESS；迁移来源通用写受保护，独立标准 Agent 不被一并封禁；runtime 镜像/shadow/容量/清理规则保持 |
| A16 | URL/SERVICE 的 Runtime 与 DECLARED 都为空，或一个来源为空 | 无来源 Filter 的 endpointSets 仍保持来源及顺序；Client 恢复存储 registrationType，显式查询 override 不改返回存储类型；修正初稿将空集合误判为缺信息的结论 |
| A17 | 单版本、无旧专用字段、已隔离 owner 的标准 RAD 转换 | URI/IPv6/TLS/path/query/transport 和 exact range 正确；单条覆盖批量、批量覆盖单条；旧注销一个参数仍清理该 owner 完整批次；通过独立读者和协议测试证明实际调用 RAD |
| A18 | 同实例旧 v1/v2 与原生 a2a publication 并存；同地址不同 path；重连/关闭 | 不因仅修改本地 key 而假通过；若选择真实独立 owner，验证各身份心跳、失效和容量归属；若选择逻辑 owner，验证共享 parent 清理；不得加宽 range 或修改 protocol 规避隔离 |
| A19 | 真实旧 SDK 发布 protocolVersion/tenant，新 SDK 读取；新 SDK 发布，旧 SDK 读取 | 双向字段完整，覆盖 Endpoint 值与 CallInterface 值不一致；旧保留字段不能用新私有 metadata 约定冒充兼容；仅字段变化时旧订阅也有相应行为 |
| A20 | exact GET 额外读取 latest 期间指针变化/第二次读失败；latest-only 变化 | 明确两次读的一致性与异常处理，不悄悄填 false；保持 true/null 形状；复用旧轮询时不依赖 exact RAD 指纹才刷新 latest 标记 |
| A21 | release 查询与写入间被并发发布；审批开启；已有 latest | 不因 Client 预查询把内容冲突全吞为旧 no-op；不临时移动 latest 再补偿；直接 online 等承诺必须由实际支持的写路径保证 |

对于读的字段一致性，比较完整公开 DTO/ZIP 的语义内容，排除真正非确定性字段；不要只比较 name/version。对于发布与注销，必须通过其他公开查询/独立 client 观察服务端状态，不以 void 方法未抛异常作为成功判据。

A16–A21 是本次进一步细化的 Pending 场景，不计入第 3 节接口覆盖率。URI/DTO 映射与具体请求选择由小范围协议测试覆盖；多 owner、混合旧 JAR、远端生命周期和字段往返由真实 IT 覆盖。

## 6. 服务端与 SDK 版本运行矩阵

| SDK 程序 | 旧服务端（按能力选已发布版本） | 新 3.3 CANONICAL | 新 3.3 迁移模式 |
| --- | --- | --- | --- |
| C3 真实旧 SDK JAR | 建立旧行为基线 | 旧 wire 兼容回归 | 真实 legacy 参与者验证切流、shadow 与重连 |
| C2 旧业务 class + 新 SDK | 证明旧二进制仍能使用旧能力 | 证明旧二进制可以经新委托/适配运行 | 证明 SDK 升级不绕过迁移契约 |
| C1 新 SDK，旧 Java 写法 | 旧 A2A/MCP 等已支持能力 | 对照新子入口 | 对照 migration 场景 |
| C1 新 SDK，新子入口 | 子入口上的旧能力可用；新 RAD 方法明确不支持 | 完整五资源、transport、RAD 场景 | 新旧互通及迁移门禁；不能预设所有路径均可直接 RAD |

不能把当前 test reactor 整体替换为旧 client dependency 后称为旧版验证：当前测试使用大量 3.3 模型，且 maintainer 会污染依赖。旧进程 fixture 单独构建和启动，使用小的场景参数/结果文件与外部 harness 协作。服务端启动、停机、旧版本切换、端口阻断均由 harness 管理，JUnit 不嵌入 Spring/Nacos 服务器。

新旧 wire 的精确选择由协议层测试证明；状态一致性、真实旧 jar 加载和重连恢复由独立进程 IT 证明，两者一起构成门禁。

## 7. 数据、鉴权与验证安排

- 新普通 IT 使用 JUnit 5，命名为 `*JavaSdkITCase`；继续放在 `test/java-sdk-test`，仅通过公开 Factory 和 service 接口测试 Client 契约。
- 资源名使用任务前缀与 UUID；同时覆盖默认和自定义 namespace。创建前注册可容忍不存在的 cleanup；所有客户端在基类 cleanup 阶段关闭；不共享可变静态数据。
- 沿用现有普通 CI 的默认鉴权、Client/Admin 分离和可见性授权。Maintainer 只准备/删除 fixture，不用管理员身份代替被测 Client。HTTP/gRPC 及新旧入口都验证授权失败不会退化为另一条成功路径。
- 异步查询使用有界 retry、清楚的失败原因；重连使用既有外部 ready/restarted marker。停止回调测试采用固定观察窗口，不能无限 sleep。
- 在 `JAVA_SDK_IT_SCENARIOS.md`、`JAVA_SDK_IT_COVERAGE.md` 和受影响的 Agent/MCP/A2A 场景文档中更新已实现的覆盖项；只使用 Covered/Partial/Pending，并同时报告严格覆盖率和有效覆盖率。不能通过移除旧负面断言而提高覆盖率，必须补正面的 HTTP 退化场景。

实现后的验证顺序：

1. API/Client 编译与针对性单元测试：签名/default 分派、配置解析、能力路由、transport 归属与失败边界。确认 Java 8 API 兼容，`@Deprecated` 不使用 Java 9 专有参数。
2. `mvn -pl test/java-sdk-test -DskipTests test-compile`，验证入口迁移和 fixture 编译。若依赖尚未安装，先按仓库标准构建对应依赖，不能把解析失败报成编译通过。
3. 在外部启动并配置好鉴权的单机服务器运行 `mvn -pl test/java-sdk-test -Pjava-sdk-integration-test -DskipTests=false verify`；凭据、容量等沿用 `.github/workflows/it-new.yml` 的环境配置。按现有工作流再跑 `jackson3-sdk-test` 组合。
4. 外部 harness 运行纯 HTTP/gRPC 不可达、旧服务端、C2/C3 独立 JVM、重连和迁移矩阵；复用 `.github/workflows/migration-it.yml` 的适用阶段。旧版运行 profile/harness 尚待实现，本文不伪造现成命令。
5. 保留每一类的编译日志、Failsafe 报告、实际 SDK/server 版本、依赖树、路由证据和被跳过的场景。只有全部范围内门禁通过，才称为 3.3 新旧 API 兼容完成。

## 8. HTTP 能力发现与决策补充矩阵

以下均为 Pending，独立于第 3 节接口覆盖计算；HTTP 能力接口与完整 A2A binding 尚未实现。

| ID | 场景 | 断言 / 验证层次 |
| --- | --- | --- |
| D01 | gRPC 完全不可达；HTTP 声明 radV1=true | 新 Agent 的读、Watch、发布和 Endpoint 通过 HTTP/AUTO 工作；显式 GRPC 仍报连接错误；旧 A2A 没有适配时不能被当成可用 |
| D02 | 同上，另有 a2aCompatV1=true | 旧 A2A 新旧 Java 入口经 HTTP 保留 release/latest/Card/多版本 endpoint/事件语义；harness 证明没有 gRPC 连接 |
| D03 | capabilities=200；true / false / 缺字段 / 未知 schema / HTML / 非法类型 | 仅合法 true 为支持，合法 false 为不支持；其他保持 UNKNOWN，不能由缺字段造出无 RAD 结论 |
| D04 | capabilities 404/405，但已有 RAD HTTP route 正常 | 原生新 Agent API 保持现有调用能力；不因为缺 discovery 拒绝全部 HTTP 功能；旧 A2A 自动转换仍不允许 |
| D05 | capabilities 超时/5xx/401/403；业务资源 404 | 不缓存为 rad=false，不改走旧写；认证失败按身份策略处理；资源不存在不用于探测版本 |
| D06 | 只有旧 A2A 能力；grpc/http/auto 三模式 | 仅旧 A2A gRPC 可用；全部新方法受控失败且无旧 RPC；已有本地状态仍可取消/关闭 |
| D07 | 基础 RAD 有，Watch 或 A2A 适配能力没有 | Watch 按既有 RAD 回退规则，A2A 保留旧路径；不得用 Search 成功推导全部能力支持 |
| D08 | 连续请求、并发请求、TTL 过期、换身份/地址/连接 | 同目标在途探测合并；缓存有界且隔离；gRPC 重连刷新；不复制独立线程/连接，不在业务写中做试探 |
| D09 | 探测节点 A 支持，实际请求节点 B 不支持；能力不一致的网关后端 | 能力不被提升为集群结论；业务请求受控失败且不跨协议重放写；目标关联/限制在协议 UT 和定向 IT 中验证 |
| D10 | 探测成功后状态进入 QUIESCING，再发送写 | 服务端现有 guard 拒绝，Client 不凭缓存能力跳过保护；无新 epoch/租约依赖 |
| D11 | 使用新兼容 binding 横跨 LEGACY→SYNCING→QUIESCING→CANONICAL | 契约支持位不翻转；旧定义权威、镜像和写屏障保持；Client 不因迁移错误永久缓存 legacy/rad 选择 |
| D12 | 迁移来源与独立标准 Agent 同时存在 | 来源写保护仅影响应受保护的对象；其他资源正常；SYNCING 阶段新读的当前事实不被冒充为完整历史数据 |
| D13 | 迁移错误经旧 gRPC、新兼容 gRPC/HTTP、原生 HTTP 返回 | 50105 可机器识别，区别于 501 unsupported 和网络错误；校验 status/detail，避免仅比较消息或把所有 409 当迁移错误 |
| D14 | capabilities 接口访问、资源权限不足、匿名策略、恶意/错误参数 | 只要求约定的 Client 身份策略，不要求 Admin 或具体 Agent 读取权限；匿名部署兼容；只暴露契约能力，不读资源/创建 Client/续租 Publisher |

拟新增 HTTP 能力接口的 OpenAPI IT 应独立验证 D03、D05、D14 的响应结构、鉴权和无状态性，并在实施时更新 `test/openapi-test/CLIENT_API_TEST_SCENARIOS.md`、`API_TEST_COVERAGE.md`。Java SDK IT 验证消费这些响应后的公开行为；假网关/超时/竞态可由外部 harness 或协议层测试可控注入，不能靠修改 SDK 私有字段冒充真实 IT。

## 9. 本轮设计核验状态

已核对公开接口、factory 使用方式、facade 实现、transport/router、能力读取、相关缓存/监听路径、服务端 A2A/RAD 关键语义、现有 IT、Maven profiles 和 CI 配置。

本轮仅产生设计文件和明确标为草案的中英文规范修订；生产代码、测试代码及 CI 配置没有变更，因此未编译或运行服务器 IT。真实旧制品版本固定、完整 A2A binding 信封/HTTP owner 实现和新路由验证留给后续阶段，不标记为已完成。
