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

# Nacos 3.3 Client AI API 规范修订提案

| 项目 | 值 |
| --- | --- |
| 状态 | 资源接口、模型整合与 A2A/RAD 适配已实现 |
| 更新 | 2026-09-20 |
| 范围 | 子服务入口、资源 transport、A2A/RAD 能力发现与兼容决策、Agent/RAD Java 模型收敛 |

本文记录已实现的 Client API 演进。具体行为和验收覆盖见
[A2A 兼容契约](a2a-agent-spec.md)、[RAD 协议](rad-protocol-spec.md)和
[SDK 场景矩阵](../../../test/java-sdk-test/JAVA_SDK_IT_SCENARIOS.md)。

## 0. 当前阶段边界

资源接口、transport 覆盖和模型整合均已实现。首次能力解析确认支持 RAD 时，
旧 A2A 操作改走 RAD；旧服务端仍走历史 gRPC，客户端实例内固定选定的 A2A 模式。
第 3–5 节定义能力发现、适配与验证，第 6 节保留模型演进记录。

## 1. 对主规范的修订关系

| 主规范 | 拟替换或补充的条款 |
| --- | --- |
| [Java SDK 实现](../sdk/sdk-java-impl-spec.md) §5.3 | AiService 子入口与继承结构，保留已发布扁平 API 的 default 委托 |
| [SDK](../sdk/sdk-spec.md) §5 | MCP 入口统一为 mcp()，总 transport 增加资源 override |
| [Agent API](agent-api-spec.md) §2.1–2.2 | AgentService 承接 A2A 和 AgentDiscoveryService；无 RAD 时新能力不可用 |
| [客户端能力协商](../client/client-ability-negotiation-spec.md) | 补 HTTP binding 能力发现；区分能力、可达性及业务前置条件 |
| [A2A 兼容](a2a-agent-spec.md) | 新SDK采用明确的查询/发布降级；旧wire及迁移保护保持 |
| [RAD](rad-protocol-spec.md)、[Agent 存储](agent-storage-spec.md)、[Agent 管理](agent-management-spec.md) | O1：逐 Endpoint 生效绑定、Batch 字段默认值、enabled 可写及删除冗余 state；Console 同步派生标签 |
| [HTTP API 范围](../http-api/v3-api-surface.md) | Client capabilities 入口，遵循标准仅身份认证流程 |

## 2. 接口和 transport 目标

`AiService` 提供 `mcp()`、`agent()`、`skill()`、`agentSpec()`、`prompt()`，并继续继承
`McpService`、`A2aService`、`SkillService`、`AgentSpecService`、`PromptService`。
这些资源接口从现有方法拆出；除 getter 外不增加业务方法。

`AgentService extends A2aService, AgentDiscoveryService`，接收既有 `publishAgent`。
`AiService` 不再继承 `AgentDiscoveryService`，也不继承 `AgentService`；3.3 未发布的新 Agent
扁平调用迁到 `agent()`。已发布扁平签名保留并标记 Deprecated，核心方法通过子入口委托，
便利 default 重载保持对旧核心 override 的分派。新增 getter 使用兼容 default，避免破坏第三方旧实现。

MCP 五参 createDraft default 保留原 false→旧四参 override、true→未实现者不支持的行为；
官方实现保留一个纯桥接 override 转到 mcp()，业务实现只在 delegate 中存在。

保留 `nacosAiTransportMode=grpc` 默认值和 `grpc/http/auto`。资源覆盖键为
`nacosAiMcpTransportMode`、`nacosAiAgentTransportMode`、`nacosAiSkillTransportMode`、
`nacosAiAgentSpecTransportMode`、`nacosAiPromptTransportMode`。
未设置时继承总值；全部显式值先校验；构造时冻结。

Skill/AgentSpec 已知没有 gRPC 实现的路径直接注入 HTTP proxy，保留原缓存、轮询、MD5 和事件语义。
第一步旧 A2A 在所有服务端均为固定旧 gRPC 例外；没有 RAD 时新 Agent
Search/Discover/Watch/publication/publish 不允许模拟为旧 API。显式 GRPC 的网络故障不等于
单 binding 能力退化，不自动改用 HTTP。

Prompt 的直接查询和订阅轮询共用薄路由代理；AUTO 根据当前连接选择，连接类失败可 HTTP，
第一步未新增 Prompt gRPC 能力位；第3节的 HTTP prompt 声明不改变此事实。
Agent/MCP/Prompt 各自记录 AUTO 状态，共享连接只有在
无强制 GRPC/旧 A2A 需求、未曾连接、失败达原阈值且已使用 AUTO 资源均 HTTP 成功时才可暂停
初始重连；未使用资源首次使用时恢复必要探测，曾连接后的断线恢复规则不变。

子服务共享既有连接、namespace、认证、HTTP 活性协调与关闭流程。资源模式不能互相覆盖，
一个资源的 HTTP 成功不能停止另一个强制 GRPC 资源的重连。有状态 publication 保留协议和 owner，
写入结果未知时不跨协议/transport 重放。

## 3. HTTP 能力发现

拟增加 GET /v3/client/ai/capabilities，Result.data 包含 schemaVersion=1 和 capabilities。
已确认同时声明五个 boolean 键：radV1、mcp、skill、prompt、agentSpec。
它们只表示响应节点的对应 Client HTTP binding，不表示 gRPC 支持/可达性、资源权限或管理能力。
MCP/Agent 已有 HTTP/gRPC 路径；Prompt 查询也有两种路径；Skill/AgentSpec 当前相关 SDK 路径仅 HTTP。
基础声明涵盖对应的 Client 查询/搜索，以及 Agent/MCP 的发布和 Endpoint 生命周期；
不推导所有未来增强操作。具体映射见能力设计表。
已有 agent 指历史 A2A，不能替代 radV1；新 HTTP 键不直接填入 gRPC 能力表。
接口不接收业务资源参数，不读取资源/索引、不续租；依据已实现/装配的 binding 声明，空库也可返回 true。
不为四个既有资源增加强制能力探测前置步骤；旧服务器缺接口或缺键时保持原有调用路径。
取消 a2aCompatV1 提案。Watch 内部协商只影响 Watch transport，不增加 A2A/RAD 模式门槛。

gRPC 使用当前连接 SERVER_RAD_V1；HTTP-only 不等待 gRPC，直接取 HTTP 能力。
每个键的有效true/false分别表示支持/不支持；缺字段/错误类型仅该键为UNKNOWN，其他合法键保留。
主结构非法或未知schema才令整个响应为UNKNOWN；忽略未知可选键，不把缺键当false。
404/405、认证失败、网络失败和业务not-found均不能单独证明无RAD。
能力缓存按实际目标/context/transport/身份隔离，换连接或身份刷新；不缓存权限/迁移写许可。

### 3.1 仅验证身份

能力接口属于 OPEN_API / AI / READ，使用 ONLY_IDENTITY，不使用 ALLOW_ANONYMOUS。
有效普通用户即使没有任何资源权限也能读取；缺失/无效/过期身份被拒绝。
具备能力查询权限不授予后续Agent读写权限。使用普通Client凭据和端口，不依赖Admin/Console。

复用HTTP Filter的validateIdentity后跳过validateAuthority。拟使用显式非资源parser，
保留AI类型、action和身份标签，不根据查询参数派生namespace/group/Agent。
不把DefaultResourceParser.class误当显式覆盖，也不以去除Secured实现身份接口。
当前Filter拒绝形状为HTTP403 + Result ACCESS_DENIED；不无依据改成插件内部401。

O6 已确认（2026-09-17）：能力接口遵循标准 Client auth 流程，默认/true 时按所选插件规则
校验身份，显式 false 按总开关放行；不另设强制身份验证。保持 ONLY_IDENTITY，不校验具体资源权限，
不添加 ALLOW_ANONYMOUS。Admin/Console 开关不替代 Client 开关。
插件启用条件、插件缺失/未激活及内部身份分支均沿用现有框架，不为能力接口新增检查或配置错误处理。
验证应区分“校验成功”和“标准流程跳过”，后者不能计为身份验证证据。

完整请求、无副作用、缓存及认证矩阵见
[A2A 兼容契约](a2a-agent-spec.md)。

## 4. A2A / RAD 映射

### 4.1 路由及降级

- 首次确认RAD支持：旧A2A与新Agent远端操作调用RAD，按agent资源配置选择transport。
- 首次选旧A2A的实例保持全部旧方法走旧gRPC，重连/升级/迁移完成不自动转RAD；重启或重新实例化再判断。
- 底层能力正常刷新；既有旧应用保持A2A链路，服务端负责迁移适配；不新增运行中启用原生RAD的交接机制。
- RAD不支持：旧A2A强制旧gRPC；AgentDiscoveryService与publishAgent远端方法unsupported。
- 全部不可达：明确连接错误。能力UNKNOWN不包装为“版本过低”。
- 继承的getAgentCard仍是旧A2A方法，AiService直调与agent()行为一致。
- 本地unsubscribe/shutdown不依赖远端能力。
- 业务权限/参数/冲突/容量/迁移错误不触发旧协议fallback；结果未知的写不跨owner重放。

### 4.2 查询和Watch

以下映射适用于已选RAD模式；旧模式实例保留原查询、注册redo及轮询生命周期。

旧查询未指定version使用显式label=latest，只选择a2a；从nativeDescriptor还原Card，
SERVICE使用Runtime地址，无运行地址回退声明Card。完整endpointSets来源顺序用于registrationType。
latestVersion在latest查询为true，exact查询固定null，不再补查latest。
订阅优先复用RAD Watch和同一投影；无Watch能力时才RAD Discover轮询。
初始缺失、恢复、取消重订阅、F/R入口监听共享及shutdown必须验证。

O4 已确认、待实现（2026-09-17）：endpointSourceOrder 仅表示默认推荐优先级，不是来源开关。
定义必须恰好包含 RUNTIME 和 DECLARED 各一次，仅允许两种排列；取消单来源定义和对应 UI 模式。
两种来源都可以查询，但实际地址可为空，不要求发布定义时已有运行时注册。
未指定 endpointSources 时按推荐顺序返回两个 Set，保留空 Set，不只返回首个有地址的来源。
显式 endpointSources=[RUNTIME] 或 [DECLARED] 时，只返回所选来源，不受定义优先级限制；
没有匹配地址仍是该来源空 Set，不自动加入另一来源。Filter 同时选两种来源仍保持定义推荐顺序，
Filter 数组表达集合而非新的优先级。Discover/Watch 的版本、权限、可见性和其他过滤规则不变。

A2A URL/SERVICE 定义转换已分别输出 [DECLARED,RUNTIME]/[RUNTIME,DECLARED]，继续沿用。
旧 getAgentCard 不指定类型时按推荐顺序选择，显式类型决定本次投影；读取完整来源顺序后
恢复返回的存储 registrationType。SERVICE 无匹配 Runtime 时仍沿用旧 Card 回退，
该兼容行为不扩展到原生单来源 Filter。网络/权限错误不能转为回退成功。
本结论取代“原生定义排除 Runtime 时接受旧 SERVICE 降级”的未采纳建议。

实施必须同步定义校验、Discover/Watch、管理/内部存储 Schema 及 Artifact 引用，
Console 新建/编辑选择器、EndpointSourceMode、双向转换、JSON 校验、详情标签/来源禁用提示和 i18n。
管理 Runtime 查询不受默认优先级限制。只取消定义中的单来源模式，不删除 EndpointSource 枚举值，
不取消查询单来源 Filter，也不改变版本存储只包含声明地址的规则。
不增加 BETA 存储升级逻辑或静默重写已发布摘要；既有单来源 fixture 按新契约调整。
详见 [A2A 兼容契约](a2a-agent-spec.md)
和 [SDK 场景矩阵](../../../test/java-sdk-test/JAVA_SDK_IT_SCENARIOS.md)。

### 4.3 Client发布

只有本次从“没有任何Version”成功新建首版，Client API才强制自动submit，
Admin/Console不改变创建草稿和显式提交行为。旧release将setAsLatest映射为autoSubmit；
后续新建版本及已有可编辑DRAFT一律按本次标志：false留DRAFT，true普通Pipeline。
已有DRAFT即使是唯一版本、由Admin创建或上次首版提交失败，也不再判首版。
原生publishAgent使用同一Client发布流程。已有可编辑DRAFT完整替换定义；
REVIEWING/REVIEWED/ONLINE/OFFLINE均成功no-op，不覆盖内容或重新上线。
普通submit可能等待审核，实际上线才成为latest；权限和迁移保护仍然执行。
旧server/旧wire release继续历史行为，不因新客户端选择RAD而改写。

首版判断统计全部状态，全部版本删除后由调用方主动新建按当前空状态处理，
不新增历史标记。服务端通过既有持久化流程核实首版及可编辑状态；并发冲突或读取后资源消失直接报错，
不自动重建、重新读取后重跑发布流程。创建、更新、submit失败直接返回错误，
不自动再次写入，也不以recoverEquivalent事后补读把失败转为成功；移除现有这层发布恢复行为。
不补偿删除已保存草稿，异常也不保证服务端未生效。响应丢失报告结果未知，不跨transport重放发布。
调用方之后主动再次调用是新调用：已有DRAFT按标志，非DRAFT no-op；不自动续提之前失败的首版。
该发布约束不改变Endpoint redo和Watch重连。

A2A release与原生Client publish都完整覆盖可编辑草稿定义，包括整个callInterfaces列表；
不按protocol或nativeDescriptor字段合并，遗漏协议移除。比如[a2a, protocolB]被仅含a2a的请求
替换后，只剩新a2a。不增加合并模式或调用来源标记。已有Agent owner/scope等治理属性不因此更新。
callInterfaces/basedOnVersion保持二选一，源版本解析成完整定义后使用同一替换规则；
作者和变更说明沿用相应创建/更新入口的既有字段规则。
具体状态表、失败边界见 [A2A 兼容契约](a2a-agent-spec.md)，
细分验收见 [SDK 场景矩阵](../../../test/java-sdk-test/JAVA_SDK_IT_SCENARIOS.md)。

### 4.4 Endpoint和A2A扩展

A2A适配层缓存各精确版本的原始注册，按Endpoint身份归并后完整提交；
旧注销删除该版本整份意图，保留其他版本，空时注销整份publication。
同址其他内容一致可归并，不同host/port/transport形成不同Endpoint。

O1 已确认、待实现：版本绑定归属 Endpoint，复用 bindings/RuntimeVersionBinding；
Batch 保留 runtimeVersion/versionRange 作为批量默认值，不增加分组模型。
每组绑定逐字段继承：runtimeVersion 优先 Endpoint，缺失取 Batch；versionRange 优先 Endpoint，
缺失取 Batch，两层均未提供时才补 `[生效 runtimeVersion]`。Endpoint 未提供 bindings/null
时使用 Batch 默认字段生成绑定；空列表、null 列表项和空字符串不作为缺省输入接受。
先继承、再补默认、最后逐 Endpoint 校验：必须存在合法 runtimeVersion，范围必须包含该版本；
失败整批拒绝，不改变原注册。不能先把 Batch 缺失范围补成 `[Batch.runtimeVersion]`，
也不能自动扩大范围或替换为精确范围以通过校验。
例如 Batch 范围 `[1.0.0,2.0.0)` 配 Endpoint 版本 2.0.0 必须拒绝；闭区间
`[1.0.0,2.0.0]` 则可继承成功。Endpoint 可只覆盖任一字段，不要求整组覆盖。
同批所有 Endpoint 使用相同版本时仍只填写 Batch；全部自带完整绑定时无需 Batch 默认字段。
展开使用独立副本，缓存/注销/redo 使用生效绑定，直接 HTTP 与 SDK/gRPC 共享同一契约。

Runtime 注册接受 enabled，与 healthy 一样缺省 true、非 null。禁用贡献保留在管理查询，
发现排除该贡献；其他发布者不受影响。心跳不改 enabled，Naming 运维 metadata 保持既有覆盖优先级。
删除 Endpoint.state/RuntimeEndpointState 及冗余校验，Console 使用 enabled/healthy 派生标签：
禁用优先，其次不健康，最后可用；同步前端类型与三个展示分支测试。
EndpointSet.sourceRevision/lastUpdatedTime 仍由服务端生成，声明地址的默认值及存储规则不扩大。

O2 已确认、待实现：每 Publisher/Endpoint 注册一条生效绑定；查询仍聚合多个 Publisher 的绑定。
新 SDK 的 A2A 同址同内容注册使用连续闭区间，runtimeVersion 取当前有效注册版本的最大值，
按 RAD SemVer 比较；范围内未单独注册的定义版本也可在满足发现前提时命中。
注销只撤销目标版本的注册引用，仍有其他引用时不收缩已声明范围，也不挖洞。
例如 1.0.0/1.2.0 同址形成 [1.0.0,1.2.0]，仅注销 1.0.0 后范围保持；
仅注销最高版本时 runtimeVersion 按剩余引用更新，范围仍保持。
最后引用删除才移除 Endpoint 并清理范围记录；重新注册开启新的范围。
同址存续期间新增范围外版本扩展范围，重复注册不累加引用次数，列表替换撤销引用遵循同一规则。
缓存同时保存有效引用及生效绑定，redo 不得仅按剩余版本重建范围；确定失败不提交新的缓存状态。
原生 RAD 仍按完整 Batch 显式替换，可直接调整范围；旧服务器/旧 SDK wire 保持精确版本隔离。
不增加区间并集/离散集合语法，Naming Instance 沿用单对版本 metadata。
同实例同 Agent/protocol 的存活 Runtime publication 不混用 A2A 与原生 RAD 写：首次有效写选择
来源，第二来源在写前受控拒绝；确定失败不占用，未知保留 owner，最后注销确认后释放。
不同范围、只读和定义发布不受此限制，不新增通用 owner 框架。

tenant和Endpoint protocolVersion由A2A适配层自动注入公开metadata，统一键为
`__nacos.agent.endpoint.tenant__`和`__nacos.agent.endpoint.protocolVersion__`；读取/Watch自动还原，缺协议版本沿用CallInterface回退。
Naming 和公开 Endpoint metadata 复用同一套历史 Nacos 保留键，不引入别名优先级；
仅这两个兼容保留键允许写入，其他内部控制键仍禁止外部输入。非法值拒绝，值变更参与指纹。
不修改定义协议版本；直接RAD的Util/Builder暂缓。

### 4.5 迁移与规范变更边界

Client不复制迁移状态机，实际请求由服务端guard决定；不把SYNCING/QUIESCING当无RAD。
保留50105等机器可读detail；新RAD路径和未切流历史资源的权威/镜像关系仍需定向验证。
旧SDK服务端适配照常回归。此版本提案允许上述明确的Client发布/查询语义变化，
取代早期“所有旧语义无损”的前提。

O5 当前代码核查（保护缺口待实现补足）：迁移来源定义完整投影后可被 RAD 读取，但切流前旧定义仍为主，
查询可能尚未可见或落后。已有 mutation guard 只拒绝已标记迁移来源的定义写；
尚未投影的同名历史资源不受该检查完整保护，新建标准定义可能与之后对账冲突。
原生 RAD Endpoint 注册只写标准 Runtime，不进入旧 A2A 的历史主写/标准镜像 Router；
不能保证迁移中旧消费者能看到新地址，通用范围也不能直接展开为旧精确版本 Shadow。
用户已明确不能限制 SDK 升级顺序，撤回先完成迁移再升级的前提。
O5 已确定本阶段采用实例保留策略：首次可靠识别旧服务端并选择旧 A2A 后，同一 AiService
实例的扁平/子服务全部旧方法继续旧 gRPC，直到业务重启或重新实例化。重连到新节点及迁移完成
均不自动转 RAD；保留旧 Endpoint redo/注销、轮询及 listener，不转换为 O2 Batch 或 RAD Watch。
既有应用能在旧服务端正常运行时使用旧 A2A；原生 Agent API 在旧服务端不支持。
本轮保持旧接口及服务端适配，不为运行中混用两套 API 增加交接流程；底层能力刷新和其他资源保持原行为。
超时、认证失败、HTTP 404 或缺 radV1 key 本身不能固定旧模式；未决时保持真实错误及 UNKNOWN，
并发首次调用共享模式决定。保留旧模式后仅 HTTP 可达仍报旧 gRPC 连接错误。
新实例重新判断，原实例模式不受影响；不自动搬运注册/订阅，关闭和重新登记沿用正常生命周期。
已选 RAD 的请求不因业务错误反向 fallback，有状态请求仍遵循原 owner 和结果未知约束。
最终复核已确认节点级统一门禁：有效 A2A 权威仍为历史模式时，统一拒绝新 RAD 业务，
包括无关标准资源，不进行逐资源历史查询。Search、Discover、Publish、Register/完整替换、
Watch 新建及后续业务读取均保留 AGENT_MIGRATION_IN_PROGRESS（50105），SDK 透传；
不要求客户端等待迁移、双写或回退旧 API。写在业务副作用前拒绝，读取不伪装成功空结果。
能力查询保留，radV1 不因迁移变 false；整份注销/本地取消/关闭及合法 owner 续租按既有规则保留。
SDK 局部注销若需 Register 剩余列表则仍返回50105，保持原注册/缓存，不得自动扩大删除；
不得借心跳/清理新建 Client/Publisher 或补注册，不影响共享 MCP。HTTP/gRPC 原鉴权顺序不变。
复用有效模式及终态优先级：显式 LEGACY、AUTO 无计划或非终态拒绝；全新 CANONICAL 与终态
放行，再执行正常资源/业务检查。不能仅以 Marker 缺失或 resolveConfigured() 的 null 判断放行。
门禁放在原生 RAD 外部 binding 和后续 Watch 授权读取路径，不误拦旧 wire、Admin/Console、
内部迁移或索引；其既有 guard 仍有效。所有原生 RAD 业务入口遵循相同的准入规则。
无需迁移就绪位或切换定时器；旧链路原有迁移写屏障仍生效，不承诺迁移期所有旧请求成功。
本轮保留旧 A2A 接口及链路兼容，后续数个大版本推广后再单独评估移除；不绑定移除版本。
临时历史存储迁移组件的移除计划不等于旧公共接口和标准 AgentCard 适配器的移除。
客户端实例首次解析能力后选择旧 A2A 或 RAD 链路。
详见
[A2A 兼容契约](a2a-agent-spec.md)。

对应改动：本规范、Agent API的Client publish、A2A新SDK映射、RAD注册与公开metadata、
Agent Storage/Management 的绑定和状态规则、客户端能力协商及HTTP授权规范。
中英文主规范、迁移保护契约、RAD/管理/内部存储 Schema、Artifact 引用及契约测试
共同定义已实现的行为。
详细边界见 [A2A 兼容契约](a2a-agent-spec.md)。

## 5. 验证门禁

[Java SDK 场景](../../../test/java-sdk-test/JAVA_SDK_IT_SCENARIOS.md)、
[Client HTTP 场景](../../../test/openapi-test/CLIENT_API_TEST_SCENARIOS.md)和
[迁移场景](../../../test/java-sdk-test/A2A_MIGRATION_SDK_IT_SCENARIOS.md)
覆盖 18 个 A2A、10 个原生 Agent 签名、其他 AI 回归及 8 个受影响 HTTP 操作。
分别验证 HTTP、gRPC、AUTO→gRPC、AUTO→HTTP、旧扁平与资源入口、两种 JSON adapter、
以及不支持 RAD 的真实旧服务端。

能力接口验证无资源授权身份、无效/缺失身份、匿名 AI 隔离、不续租和凭据缓存隔离。
Admin/Console 首版草稿不得自动提交。旧字节码、旧 SDK 进程、真实重启、跨节点 Watch
及迁移必须使用对应的专用环境；条件跳过不计为通过。既有无关禁用项和 Derby 集群
Search 迁移切流缺口在覆盖记录中明确保留。

行为变更同步主规范、Schema 和测试，阶段验证后再执行完整受影响矩阵。
RAD、Watch、管理和 Artifact 公开 Schema 使用固定路径与统一契约版号 0.5.0；
payload schemaVersion 保留独立含义。

## 6. Agent / RAD Java 模型收敛提案

本节记录已确认并在 2026-09-11 本地试改版实现的模型契约：统一 agent 包、RAD 定义优先和
abstract 基础层。完整 43 文件清单、改造结论和 M01–M15 验证计划见
[模型收敛设计](../../../Codex/design/nacos-3.3-client-ai-api/MODEL_CONSOLIDATION.md)。
用户确认本轮可以忽略 `3.3.0-BETA` 的 Java 模型兼容，不保留旧包别名或兼容壳。
已发布旧 A2A 契约及现有 HTTP/gRPC、存储格式仍受保护。

### 6.1 统一模型与抽象基础层

将现有 `model.rad` 的全部具体模型/枚举迁入 `com.alibaba.nacos.api.ai.model.agent`，
不保留两个并列的 Agent 模型包。只用于字段复用的底层模型放入 `model.agent.base`，使用
`public abstract class AbstractAgent…`，构造器为 protected。
跨 Agent/MCP 共用的 ClientLivenessInfo 移到 AI 公共 model 包，既有 RPC 信封仍在 remote 包。

共享基础类及字段分配为：

| 抽象类 | 自身声明的字段 | 复用关系 |
| --- | --- | --- |
| AbstractAgentMetadata | agentName/displayName/description/iconUrl/provider/tags | 管理摘要、RAD CatalogEntry、Admin 元数据更新；草稿基类继承它 |
| AbstractAgentSearchRequest | 五个 Search 条件及分页字段，无 namespace | Client Search 与完整 RAD Search 为并列具体子类 |
| AbstractAgentEndpointRequest | agentName/protocol/endpoints | 注销具体请求与注册基类复用；不共享注册/注销的操作校验 |
| AbstractAgentEndpointRegistrationRequest | runtimeVersion/versionRange | 继承 Endpoint 请求基类，供 Client 注册和 RAD RegistrationBatch 使用 |
| AbstractAgentDraftRequest | extensions/version/callInterfaces/author/changeDescription/basedOnVersion | 继承元数据基类，供 Admin 草稿创建和 Client publish 使用 |

公共基类可引用稳定值对象，不能依赖 Client/Admin 专用请求，不引入独立 Maven module。
基类共享同义字段和访问方法，不拥有鉴权、namespace 默认、状态转换、transport、缓存、redo。
具体操作继续调用原校验规则；相同公共校验可以复用，不因父类复用放宽上下文约束。

公开 SDK 参数/返回值、DTO 成员与列表元素必须使用具体业务类型，不暴露 AbstractAgent…
或抽象元素列表，不添加 JsonTypeInfo、类型判别字段或多态构造工厂。
从固定 JSON 反序列化具体类型时，应自然绑定其所有继承属性。
具有独立返回/值对象含义的 AgentSummary、AgentVersionSummary、Endpoint 保留具体类；
不为每一两个相同字段再引入身份、版本、namespace 等通用基类。

### 6.2 具体命名与继承方向

初版试改按 RAD 概念保留 AgentCatalogVersion、删除 AgentVersionCatalogEntry；
本次资源/版本整合以 §6.5 为准，统一使用 AgentVersionSummary。初版的其他合并为：
管理/存储的目录容器也使用前者，同时保留各自的校验和 JSON 结构。
后续地址模型统一替代两个 CallInterface 并列子类：共用 AgentCallInterface → EndpointSet → Endpoint，
字段按查询上下文约束；完整管理详情与发现结果不直接相互继承。

Client 专用请求拟统一为 AgentSearchClientRequest、AgentEndpointRegistrationClientRequest、
AgentEndpointDeregistrationClientRequest、AgentPublishClientRequest。
Admin 专用请求拟统一为 AgentDraftCreateAdminRequest、AgentDraftUpdateAdminRequest、
AgentUpdateAdminRequest、AgentLabelsUpdateAdminRequest、AgentVersionAdminRequest。
RAD AgentSearchRequest、DiscoveryRequest、Endpoint Batch 等名称保留。
不为没有独立边界的操作机械增加成对空包装类。

Client 与 RAD Search 请求分别继承 AbstractAgentSearchRequest；只有完整 RAD 请求补 namespaceId。
Endpoint 注册/注销采用同一并列关系。SDK 只接受具体 ClientRequest，复制业务内容后注入实例
namespace；不能改为接收抽象基类或直接接受完整 RAD 请求。
Client publish 与 Admin 草稿创建分别继承公共草稿基类，不让 Client 继承 Admin 专用请求。
Maintainer 的 namespace 继续由显式方法参数给出，HTTP Form 独立承担字符串解析和参数绑定。

### 6.3 协议、领域与验证边界

RAD 是共享概念及发现契约的基准，不包含完整管理生命周期。Admin 状态操作可组合/继承公共
对象，但不能把所有 Admin API 建立在完整 RAD 根请求或在线发现视图之上。
协议优先不要求每个 Schema 概念都拥有独立 Java 类，也不意味着重写已有版本化 Schema。

保持 JSON 属性、层级、可选/缺省值、枚举、RPC 信封类型、错误码和 Endpoint 发布语义。
管理目录 labels 必须为数组（允许空），RAD 允许省略；公共类型不消除上下文校验。
具体管理摘要仍按有界投影构造，不用实际详情实例强转；版本列表不额外读取 AI Storage。
当前 Discover/publish 返回的 namespace 字段保持，不能通过共享模型的 JsonIgnore 隐藏。
保留显式存储投影、字节、摘要、sourceRevision、Watch fingerprint 和深拷贝。
本轮不改变 A2A/MCP/Skill 业务实现、迁移、transport 路由、Watch 或 redo 算法。

新增 M15 验证 base 类型均为 abstract、构造受保护、公开 API/DTO 使用具体类型，以及具体模型
无需多态 discriminator 就能反序列化。M01–M14 继续覆盖继承属性、namespace 隔离、旧 JSON、
目录规则、有界摘要、存储向量、默认/Jackson 3、Client/Maintainer/OpenAPI 的真实场景。
新增项在执行前保持 Pending，实施时同步场景/覆盖登记，不扩展真实故障注入范围。
方案采纳与实现时同步双语 Java SDK 实现、Agent API、Agent 管理和 RAD 主规范的 Java 绑定映射。

### 6.4 后续评审修订：三层发现模型（待实施）

§6.1–6.3 记录已有本地试改，本节记录后续确认的模型简化目标，尚未改变现有 Wire 或运行行为。
资源信息合并后统一使用 AgentSummary；版本元数据收敛为
`AgentSummary.versionInfo: AgentVersionInfo → onlineVersions[]: AgentVersionSummary`。
查询场景继续约束返回字段，Client 用户构造的入参仍不暴露 namespace。

公共发现结果主干为 `AgentDiscoveryResult → callInterfaces[]: AgentCallInterface → endpoints[]: Endpoint`。
不插入 versions[] 或 EndpointSet 导航层。固定与 Runtime 地址共用 Endpoint，由 source 属性
表达 DECLARED/RUNTIME；不同查询入口不再产生不同的公开 CallInterface 或 Endpoint 类型。
VersionDetail 先以只返回固定地址的方案评估，Runtime 获取方式另行评估；不强制增加管理聚合查询，
也不将实时地址写入版本存储或 contentDigest。

来源顺序、空来源及 sourceRevision 的字段承载和内部 Wire 映射仍待设计，不能因公开结构压平
而丢失原契约信息。实施前明确是保留 Wire 适配还是同步修改 Schema，并更新相应双语规范与
SDK/OpenAPI 场景；目前不将该目标描述成已实现的 HTTP/gRPC 结构。

默认 Discover 受 latest 的协议定义、来源顺序和固定地址限制的问题记录为 MODEL-D01，
详见[关系图 §12](../../../Codex/design/nacos-3.3-client-ai-api/MODEL_RELATIONSHIPS.md)。
后续再讨论全部在线版本 Endpoint 的覆盖、descriptor 归属、去重及 Watch 依赖；
本次模型简化不顺带修改跨版本发现算法，也不通过新增结果层级预先解决该问题。

### 6.5 本轮实施：资源摘要与版本信息合并

本节是资源与版本元数据的现行合并规则，替代本章前面初版试改中对应的类型拆分；其他请求和协议模型的约束继续有效。

本轮只实施 Agent/版本元数据的收敛，CallInterface、Endpoint 和 MODEL-D01 保持现状。
删除公开 Agent、AgentCatalogEntry、AgentVersionCatalog、AgentCatalogVersion；资源统一为
AgentSummary（包含可选 extensions），版本集合统一为 AgentVersionInfo，单版本条目复用
AgentVersionSummary（增加 protocols、labels）。AgentVersionDetail 保留为包含协议内容的详情。

公开 JSON 使用 versionInfo，其中包含 editingVersion、reviewingVersion、labels 和 onlineVersions。
latest 从 labels["latest"] 派生，在线数量从 onlineVersions 派生，不再存放两套公共字段。
Search 同样返回 AgentSummary，但省略 namespace、管理字段、extensions、editing/reviewing；
其标签映射只包含在线版本指向。列表投影省略 extensions，详情/更新结果按原规则返回。

本次调整对应的 Search/Admin/Console JSON 结构及 Java 泛型，不增加旧 BETA 公开模型壳。
持久化仍使用原 version_info 和 ext.versionCatalog 的显式投影，保留原 schemaVersion、
字段格式和版本内容 bytes；读取时校验旧字段一致性后组装新模型。发现选择器、地址、Watch、
A2A、transport 及发布算法不变。UT/IT 必须验证新响应结构、字段边界、完整标签、旧存储读取
及派生目录一致性；本次新增执行结果单独记录，不沿用上一轮验证结论。

### 6.6 请求分包补充提案（2026-09-15）

本节是请求模型调用关系核查后的分包提案，尚未实施 Java 迁包或改名；实施时替代 §6.2
对应命名，并同步 Agent API、Java SDK 实现规范及相关 IT 场景/覆盖登记。完整调用方、
例外、校验和验证阶段见[请求分包核查](../../../Codex/design/nacos-3.3-client-ai-api/MODEL_REQUEST_PACKAGES.md)。

保持 `nacos-api` 模块，根包 `model.agent` 保留 RAD 协议、共享值对象及结果模型；
共享抽象类继续位于 `model.agent.base`。将 Admin/Client 区别放在 package 中：

- `model.agent.admin`：AgentDraftCreateRequest、AgentDraftUpdateRequest、AgentUpdateRequest、
  AgentLabelsUpdateRequest、AgentVersionRequest。
- `model.agent.client`：AgentPublishRequest、AgentSearchRequest、AgentEndpointRegistrationRequest、
  AgentEndpointDeregistrationRequest。

这些类分别对应现有同名 AdminRequest/ClientRequest，去掉场景后缀，不新增成对包装。
根包 AgentSearchRequest 仍是含 namespace 的完整 RAD 请求；client.AgentSearchRequest
仍不包含 namespace，两者保持抽象基类的并列子类。转换处显式区分全限定类型。
Admin 请求仍由 Maintainer、Console、服务端共享；A2A 内部定义转换可继续复用草稿输入。
HTTP Form 并不与每个 SDK 请求直接映射，特别是 Client 局部注销仍先计算剩余完整集合。

复用优先调整已有基类：extensions 从三个直接子类上移到 AbstractAgentMetadata；
Admin 创建/Client 发布的相同草稿校验移到 AbstractAgentDraftRequest。不新增身份/版本基类，
不改变具体操作的字段边界。包内 AgentAdminRequestUtils 的访问必须随迁包处理，不能公开
该工具来代替正确的校验归属。

AgentEndpointDeregistrationBatch 是 SDK 内部带 namespace 的删除意图，服务端并不接收它。
其内收 client 实现模块作为独立后续步骤，须同步专用校验，禁止 api 反向依赖 client。

本提案只调整 Java 类型归属与共享声明，不改变 HTTP JSON、RPC 信封名、namespace 绑定、
生命周期、局部注销、存储摘要或 RAD revision。Java 调用方需更新 import 并重新编译；
按已有约束不增加 BETA 兼容壳，历史 A2A 公开契约继续保护。验证在实际执行前保持 Pending。

### 6.7 请求合并与 namespace 上下文（当前实现）

本节替代 §6.1、6.2、6.6 中初版请求分层；前文保留设计演进记录。

Java 模型以 `com.alibaba.nacos.api.ai.model.agent` 为根包。RAD 通用模型、Search 和
RegistrationBatch 留在根包；五个管理请求放到 `agent.admin`，名称为
`AgentDraftCreateRequest`、`AgentDraftUpdateRequest`、`AgentUpdateRequest`、
`AgentLabelsUpdateRequest`、`AgentVersionRequest`；发布请求为 `agent.client.AgentPublishRequest`。
`agent.base` 只保留 `AbstractAgentMetadata` 和 `AbstractAgentDraftRequest`，均为 abstract，
构造器为 protected。Metadata 共享元数据及 extensions；Draft 共享版本定义字段和草稿校验。
Client 发布与 Admin 草稿创建为并列具体子类，公开 API 使用具体类型。
共享校验集中在 `com.alibaba.nacos.api.ai.utils.AgentValidationUtils`，不在 model 内维护工具类。
Form 独立承担 HTTP 字符串解析；Admin 模型仍供 Maintainer SDK、Console 和服务端使用，
namespace 来自 Form 或显式方法参数。JSON 转换使用 `JsonUtils`/`NacosTypeReference`。

Search 和完整注册分别使用根包 `AgentSearchRequest`、`AgentEndpointRegistrationBatch`，
只包含业务字段，不含 namespace 字段或访问器。局部注销使用
`deregisterAgentEndpoints(String agentName, String protocol, List<Endpoint> endpoints)`，
不再定义注销 Java Request/Batch。SDK 对调用方内容做防御性复制，从实例取得 namespace，
通过 HTTP 参数或 RPC 信封显式传入查询/注册服务；PublicationKey 和 redo 数据独立保留 namespace。
局部注销仍计算剩余完整 Batch，非空则重新注册，为空则整份注销，不修改调用方对象或集合。
HTTP 参数、鉴权、完整替换和错误语义保持不变；Search/Register 的 RPC namespace 位于信封，
不再嵌套于业务请求。3.3 BETA Java 类型不保留兼容包装，历史 A2A 公开契约保持不变。

完整 RAD 逻辑 Schema 的 namespace 要求保持不变；Java 模型与上下文一起构成完整请求。
验证结果单独记录，尚未执行的矩阵项不作为通过证据。
