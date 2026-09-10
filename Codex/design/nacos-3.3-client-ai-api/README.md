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

# Nacos 3.3 Client AI API 拆分与兼容设计

状态：第一步接口委托、资源 transport 和兼容测试已落地；后续 A2A/RAD 转换仍是设计草案。实际验证及缺口见 [VALIDATION.md](VALIDATION.md)。基线：2026-09-10 更新后的 `upstream/develop`，提交 `3623b19db6be69545d7a5af36705b92274d10390`。

目标是在 3.3 发布前收敛 Client AI API：按资源获取子服务、按资源选择 transport、保留已发布的扁平 API，并让 Agent 承接旧 A2A。兼容验证方案见 [COMPATIBILITY_IT.md](COMPATIBILITY_IT.md)。

**当前实施范围已收敛为第一步：接口委托、资源 transport、对应 UT/IT。旧 A2A 在所有服务器上继续使用现有 gRPC；不实现 A2A→RAD、HTTP 能力入口或新兼容能力位。** 详细范围、风险、测试门禁和 commit 划分以 [PHASE1_PLAN.md](PHASE1_PLAN.md) 为准。第 5 节及 A2A 专题文件保留为后续研究，不是第一步的实施要求。

本次评审修订：getter 统一为 `mcp()`；无 RAD 的服务端只支持 Agent 中的旧 A2A API；HTTP 能力发现、失败分类与迁移阶段决策细化于 [A2A_ROUTING.md](A2A_ROUTING.md)。相应规范修订提案见 [中文](../../../specs/zh-cn/ai/client-ai-api-evolution-spec.md) / [English](../../../specs/en/ai/client-ai-api-evolution-spec.md)；其中第一步已写入主规范，HTTP 能力发现与 A2A/RAD 转换仍未实现。

## 1. 实施前的基线事实与改动边界

| 实施前代码事实 | 对本次设计的影响 |
| --- | --- |
| `AiService extends AgentDiscoveryService, A2aService`；MCP、Skill、AgentSpec、Prompt 方法直接声明在 `AiService` 中 | 必须补四个资源接口；不能假设已经存在 Client `McpService`、`SkillService` 等独立实现 |
| `NacosAiService` 集中持有校验、缓存、Notifier、连接、publication manager | 只做委托所需的机械提取和调用转向，不重做资源业务逻辑或统一缓存框架 |
| `AiMaintainerService` 已用资源 getter 和 default 方法委托 | 复用接口组织方式，不把 Admin CRUD、审批、删除、Pipeline 搬到 Client |
| `nacosAiTransportMode` 默认 `grpc`；Agent 与 MCP 共用 `AgentGrpcTransport.mode` | 增加资源级配置，并分离资源路由策略与共享连接生命周期 |
| Skill ZIP 下载已经直走 HTTP；Skill 订阅和 AgentSpec 读取/订阅在非 HTTP 模式会走尚未实现的 gRPC 方法 | 只改 proxy 注入和 transport 选择，就能让这些路径稳定使用现有 HTTP 实现 |
| 旧 A2A 方法调用 `requireGrpcClient()`，即使总设置为 HTTP 也会启动 gRPC | 第一步原样保留；`agent` 子配置只控制新 Agent/RAD，旧 A2A 是明确的固定 gRPC 例外 |
| RAD、旧 A2A、迁移模式有不同契约 | 所有旧 A2A 操作改用现有 RAD，并不是无损的方法改名；见第 5 节 |

本次不增加通用 Service 注册器、插件 SPI、Builder、异步 API、新的领域 DTO 或通用 transport 框架；不重写校验、ZIP/MD5、轮询、缓存键、Endpoint manager、Redo、监听事件算法。不修改 Config/Naming/Lock，不增加 Client 管理功能。确需修改的 transport 装配、能力判断和兼容适配单独列出。

## 2. 目标公开接口

```java
// 所有接口仍位于 com.alibaba.nacos.api.ai。
public interface AiService extends McpService, A2aService, SkillService,
        AgentSpecService, PromptService {
    McpService mcp();
    AgentService agent();
    SkillService skill();
    AgentSpecService agentSpec();
    PromptService prompt();
    void shutdown() throws NacosException;
    // 保留旧方法签名，核心方法改为 default 委托；省略具体方法。
}

public interface AgentService extends A2aService, AgentDiscoveryService {
    AgentVersionDetail publishAgent(AgentPublishRequest request) throws NacosException;
}
```

上面展示接口形状；实际新增 getter 使用有明确“不支持”结果的 default 实现，避免要求已经编译的第三方 `AiService` 实现新增抽象方法。官方 `NacosAiService` 覆盖 getter 并返回长期复用的子服务实例。getter 不创建新连接，不抛受检异常；第三方未实现的新 getter 可抛 `UnsupportedOperationException`，与 Maintainer 的新增 getter 方式一致。

| 子接口 | 方法迁入范围 | 本次新增业务方法 |
| --- | --- | --- |
| `McpService` | 当前 15 个 MCP 方法签名，包括全部查询、release、endpoint、订阅重载及 `createDraft` | 无 |
| `SkillService` | 当前 5 个下载和订阅方法 | 无 |
| `AgentSpecService` | 当前 3 个加载和订阅方法 | 无 |
| `PromptService` | 当前 5 个查询和订阅方法 | 无 |
| `A2aService` | 保留当前 18 个方法签名和 default 参数行为 | 无 |
| `AgentService` | 继承上述 A2A 18 个方法、既有 `AgentDiscoveryService` 的 9 个方法，接收 `publishAgent` | 无；新增的是接口和入口 |

`AgentDiscoveryService` 保留为能力接口，避免连同现有模型再设计一套 API。`AiService` 不继承 `AgentService` 或 `AgentDiscoveryService`，3.3 新增的 Search/Discover/Watch/批量 publication/`publishAgent` 只通过 `agent()` 使用。当前 develop 中这些尚未发布的扁平调用点和 IT 同步迁移；不能把它们与已发布的 3.0～3.2 API 混为一类。

调用方式：

```java
AiService ai = AiFactory.createAiService(properties);
ai.mcp().getMcpServer("example-mcp", "1.0.0");
ai.skill().downloadSkillZip("example-skill");
ai.agent().getAgentCard("example-agent");        // A2A 兼容方法
ai.agent().discoverAgent(reference);             // 新 Agent API
ai.getAgentCard("example-agent");                // 旧写法继续可用
ai.shutdown();
```

Client 与 Maintainer 统一使用 `mcp()`；不再设计 `mcpServer()` 别名，也不增加 `a2a()` 同义入口。`agentSpec()` 与可调用 Agent 的 `agent()` 保持独立。

### 2.1 旧方法委托与 Deprecated

核心方法的目标方向：

```java
@Deprecated
@Override
default McpServerDetailInfo getMcpServer(String name, String version)
        throws NacosException {
    return mcp().getMcpServer(name, version);
}
```

旧 A2A 核心方法同样转到 `agent().同名方法(...)`，其他资源转到对应 getter。官方实现不得继续覆盖这些方法并绕过子服务。

兼容措施：

1. 已发布的签名、返回类型、受检异常、参数顺序和 default 参数保持不变。在 `AiService` 的旧扁平声明处加 `@Deprecated` 和指向子入口的 Javadoc，不把整个 `AiService` 或子服务方法标成废弃，不指定本轮未决定的删除版本。
2. 旧便利 default 方法保留“补参数后调用本对象核心方法”的分派。例如 `getMcpServer(name)` 仍调用 `getMcpServer(name, null)`，核心方法再委托子服务。这保证第三方旧实现覆盖的核心方法仍生效；不要把所有便利重载直接改成调用 getter。
3. MCP `createDraft=false` 的第三方 default 兼容行为、`createDraft=true` 未实现时的受控异常继续保留，不能误变为直接上线。该兼容 default 保持原分派，官方 `NacosAiService` 的五参方法保留纯桥接 override，转到 `mcp().releaseMcpServer(..., createDraft)`；桥接不含业务实现。
4. 新 getter 的默认“不支持”只影响第三方调用新入口；第三方已实现的旧方法及其便利重载仍可运行。需要测试实际旧实现字节码，不仅测试官方实现。
5. 所有子服务共享原 namespace、认证、缓存和生命周期。只有 `AiService.shutdown()` 负责关闭；不在每个子接口增加独立关闭方法或独立 factory。

### 2.2 最小实现组织

当前没有可直接返回的资源 Service 实例。必要改动是把既有业务方法机械地归入资源 delegate，由 getter 返回：

```text
旧 AiService 方法 -> 子服务 getter -> 资源 delegate -> 原有 holder / manager / proxy
新子服务调用 ---------------------> 同一个资源 delegate
```

优先在 `NacosAiService` 中用私有内部 delegate 实现资源接口，原字段及其初始化、关闭顺序尽量留在原位置。方法体可原样移入内部 delegate 并使用现有外层字段；不为此引入通用 Context 或新增连接。第一步的 Agent delegate 分别调用现有 A2A gRPC 路径与新 Agent router，不新增版本兼容路由。

若实现时选择包内独立 delegate，也只传入既有依赖，不能因此搬迁或重写 holder/manager。严禁“子服务回调旧 facade，而旧 facade 又回调子服务”的循环。重载之间的调用仍在同一资源 delegate 内收敛。机械归属调整与 transport 行为变更分开提交，便于检查非 transport 方法体是否保持一致。

## 3. 按资源选择 transport

保留现有 `AgentTransportMode` 枚举和值，不为命名美观另加一套枚举。新增常量放在既有 `AiConstants`。

| 配置键 | 常量建议 | 默认规则 |
| --- | --- | --- |
| `nacosAiTransportMode` | 既有 `AI_TRANSPORT_MODE` | `grpc`，保持当前默认 |
| `nacosAiMcpTransportMode` | `AI_MCP_TRANSPORT_MODE` | 继承总设置 |
| `nacosAiAgentTransportMode` | `AI_AGENT_TRANSPORT_MODE` | 继承总设置；第一步只约束新 Agent/RAD，旧 A2A 固定 gRPC |
| `nacosAiSkillTransportMode` | `AI_SKILL_TRANSPORT_MODE` | 继承总设置，再按支持矩阵退化 |
| `nacosAiAgentSpecTransportMode` | `AI_AGENT_SPEC_TRANSPORT_MODE` | 同上 |
| `nacosAiPromptTransportMode` | `AI_PROMPT_TRANSPORT_MODE` | 继承总设置 |

解析顺序：`requested(resource) = resourceOverride ?? global ?? grpc`；随后按资源实际支持的 binding 得到 `effective(resource)`。配置在构造时冻结，`Properties` 的后续变化不改变已有 publication 的归属。所有显式值先校验，允许大小写差异，不接受空串、首尾空白或未知值；即使 Skill 最终只能使用 HTTP，也不能忽略其非法配置。错误指出具体配置键。

| 资源/协议 | GRPC 请求模式 | HTTP 请求模式 | AUTO 请求模式 |
| --- | --- | --- | --- |
| MCP | gRPC | HTTP | 按本资源能力和连接状态优先 gRPC，安全只读回退 HTTP |
| 新 Agent/RAD | gRPC，要求 RAD 能力 | HTTP | 同上；Watch 另检查 Watch 能力 |
| Prompt | gRPC | HTTP | 连接可用优先 gRPC，连接类失败可 HTTP；没有独立 Prompt 能力位，不新增探测，复用原查询与轮询 |
| Skill | HTTP | HTTP | HTTP |
| AgentSpec | HTTP | HTTP | HTTP |
| 所有服务端：`A2aService` 部分（第一步） | 现有 A2A gRPC | 仍为现有 A2A gRPC；不可达时报错 | 仍为现有 A2A gRPC；不可达时报错 |
| 无 RAD 服务端：`AgentDiscoveryService` 全部业务能力及 `publishAgent` | 不支持 | 不支持 | 不支持 |

`AgentService` 的继承关系不表示老服务端具备新能力：Search、Discover、Watch、RAD 批量注册/注销和新定义发布均不通过旧 A2A 模拟。第一步保留现有能力与错误处理：gRPC 使用原 RAD 能力检查，HTTP 返回实际请求错误；不新增 HTTP 探测，也不将所有 HTTP 404 统一改成 501。已有本地订阅和 publication 的关闭清理保持原流程。

第一步旧 A2A 在新旧服务端均保留原 Payload、缓存、轮询、redo 和 `requireGrpcClient()`。即使五个资源都设置 HTTP，只要调用旧 A2A，仍会按需启动共享 gRPC；仅获取 `agent()` 不启动。纯 HTTP 验收限定为原生 Agent/RAD、MCP、Prompt、Skill、AgentSpec 操作。旧 A2A 纯 HTTP 放到后续阶段。

退化分两种：

- **实现能力退化**：已知 Skill/AgentSpec 没有 gRPC 方法，构造时直接注入 HTTP proxy，不先发 gRPC 请求，不等报错后重试。保留其 ZIP、MD5、304、404、轮询调度和事件语义。
- **运行故障回退**：支持双 transport 的资源保持既有边界；显式 GRPC 不因断网自动 HTTP。AUTO 只允许安全读在连接类失败时换 transport。鉴权、参数、未找到、冲突、容量、普通服务端错误都不能触发切换；写入结果不明时不能换 transport 重发。

服务端本身没有 Skill/AgentSpec API，或者 HTTP 也不可用时，按现有错误契约失败；“自行退化”不承诺给旧服务端补新能力。

## 4. 共享连接与 transport 的最少必要改动

仅增加五个配置键不够：目前 MCP 通过 `sharedGrpcTransport.getMode()` 取到 Agent 模式，AUTO 的 HTTP 成功和初始重连暂停状态也共享。

必要调整如下：

1. `AgentTransportRouter`、`McpTransportRouter` 和 `AgentWatchTransportRouter` 使用各自所属资源解析后的模式。Prompt 使用一个薄的查询 proxy 选择实现。Skill/AgentSpec 分别注入 HTTP proxy，不再与 Prompt 共用总模式选出的 `aiClientProxy`。
2. 仍只持有一套 AI gRPC client、HTTP proxy 和 HTTP publication coordinator。资源 mode 不再从共享连接 holder 读取；连接 holder 只管启动、连接状态、协商能力与重连需求。
3. 构造时，任一可用 gRPC 资源选择 GRPC/AUTO，就按当前规则至多尝试一次初始连接；全部有效选择都是 HTTP 时不启动 gRPC。只有 Skill 退化为 HTTP，并不意味着可以关闭其他 GRPC 资源的连接。
4. 各资源单独保存 AUTO 路由决定和 HTTP 成功状态。没有 GRPC 强制需求/旧 A2A 需求，且已使用的 AUTO 资源均有自己的 HTTP 成功证据、从未连上并达到既有失败阈值时，才允许暂停共享初始重连。未使用资源不被标成成功；它第一次使用时恢复必要探测。某个资源 HTTP 成功不能使另一个活跃资源丢失 gRPC 重试。已连接过的 UNHEALTHY 重连规则不变，详见第一步状态表。
5. Agent/MCP publication 的 owner transport、期望状态、心跳与 redo 继续使用原 manager。不能因 getter 或资源配置不同创建重复 publisher。只在新 publication 建立前选路，未知结果的写入保持原 owner。
6. `shutdown()` 继续从 facade 幂等关闭全部资源。共享 HTTP client 丢失后的 Agent/MCP 一起标记失效并 replay 的行为保持不变。

配置日志只输出资源、requested/effective 模式及退化原因，不引入新的公开诊断 API。

## 5. Agent 内部的 A2A 版本兼容

**后续阶段讨论，第一步不实施。** 下文有关能力入口、完整适配、新 binding 和语义转换的选择均不阻塞第一步验收。

### 5.1 能力、可达性与迁移状态分开决策

`AgentService extends A2aService` 解决 API 组织；内部需要独立的兼容路由，先决定协议，再决定该协议使用的 transport：

```text
旧 AiService.getAgentCard(...) / agent().getAgentCard(...)
    -> Agent 中的 A2A 兼容路由
       -> 无 RAD：原 A2A binding / 原实现
       -> 有 RAD 且兼容条件满足：RAD + A2A 兼容适配

agent().discoverAgent(...) -> 既有 RAD 实现；无 RAD 时受控报不支持
```

gRPC 使用当前连接的 `SERVER_RAD_V1`；HTTP 使用拟新增的轻量 Client 能力入口。二者属于各自的 transport/目标节点，不能把 gRPC 连接失败当 HTTP 无 RAD，也不能用 HTTP 成功替代当前 gRPC 的能力检查。详细状态与错误矩阵见 [A2A 路由设计](A2A_ROUTING.md)。

- 能力使用 SUPPORTED / NOT_SUPPORTED / UNKNOWN；可达性单独保存。`isAbilitySupportedByServer()` 的 boolean 视图不足以区分 UNKNOWN 和未连接，必要时补内部读取方法。
- 新 Agent API 只要求基础 RAD 契约；旧 A2A 自动改用新 binding 还要求完整 A2A 适配契约，建议新增 `a2aCompatV1` 能力。不能以 `radV1` 推导全部旧语义已适配。
- 无 gRPC、HTTP 有 RAD：HTTP/AUTO 下新 Agent API 可用；若 HTTP 同时具有完整 A2A 适配能力，旧 A2A API 也可用。只有基础 RAD 而没有适配时，不能承诺旧 A2A 纯 HTTP 可用。
- 迁移状态由服务端每次业务请求中的已有路由/写屏障判定。客户端不复制迁移状态机，不缓存 `radReady`，也不因 `QUIESCING` 将能力改成不支持。
- 完整 A2A 适配 binding 必须在 LEGACY、SYNCING、QUIESCING、CANONICAL 各阶段保持旧契约；其内部调用既有兼容服务，而不是绕过它直接调用 canonical 业务实现。
- 未完成完整适配时，保留旧 A2A gRPC；新 RAD API 对迁移来源的写入可能被服务端明确拒绝。受控报错是允许的，但不能静默换协议绕过保护。

### 5.2 客户端能转换什么，实际缺口是什么

**Endpoint 最终应映射到标准 Runtime Endpoint；存在适配工作不等于必须扩展服务端。** 本节修正初稿过宽的判断，详细映射与反例见 [CLIENT_MAPPING.md](CLIENT_MAPPING.md)。下表讨论 canonical 事实可用时的转换；迁移中的历史权威和 mirror/shadow 仍需按 5.1 处理。

| 旧 A2A 方法/结果 | 客户端可完成的转换 | 现有契约的剩余限制 |
| --- | --- | --- |
| `getAgentCard(name)` | 使用 `label=latest`、`protocol=a2a`，将 native descriptor 投影为 Card | 不把 RAD 省略 selector 的跨版本池用于旧查询 |
| `registrationType` 和 URL/SERVICE 投影 | 不过滤来源时，`endpointSets` 的顺序保留存储来源顺序，空集合也保留；可恢复类型，并执行 Runtime/声明回退 | 原文“空 Endpoint 集无法恢复来源顺序”不成立；旧专用 Endpoint 字段另见下行 |
| `latestVersion` | latest 查询直接标 true；exact 查询可额外读取 latest 或 Search catalog 后比较 | 单次 exact Discover 不包含 latest 指针；额外读的竞态和错误不能忽略，但不是绝对无法转换 |
| `releaseAgentCard(..., setAsLatest)` | AgentCard 可转换为 A2A CallInterface/native descriptor | 直接 online、保留 latest、重复 online 版本无条件 no-op 不等价于普通 publish；客户端转换无法提供缺失的服务端写语义 |
| 单版本旧 Endpoint 注册 | `version` → batch 的 `runtimeVersion` 与 exact `versionRange`，URI/transport → Endpoint | 在 owner 独立、无旧专用字段缺口时可调用现有 RAD Register |
| 同 SDK 两个 exact version 并存 | 客户端能分别构造两个正确 Batch | 同一 gRPC connection 的同 Agent/protocol 只有一个 publisher batch，第二次会覆盖第一次；需独立 owner，不能仅加本地 map key |
| 旧单条/批量注册和注销 | 单条转长度 1 的完整批次；批量覆盖；旧注销清空该版本的本地完整意图并注销其 owner | 操作本身可适配；前提是版本 owner 已隔离，不能只把注销参数转成“移除这一个 Endpoint” |
| `subscribeAgentCard` | 可复用旧轮询/缓存/监听器，仅替换查询代理和 Card 转换；缺失后恢复亦可在客户端处理 | 依赖完整 GET 投影；不能用不含旧字段的 RAD 指纹保证旧字段变动通知，无需为此强制重做 Watch |
| URI、TLS、path、query、transport | 按现有 canonical 转换规则组成 `uri` 并保留 transport | 属于纯客户端转换，不是新增服务端 API 的理由 |
| Endpoint 的 `protocolVersion`、`tenant` | 双方新 SDK 可以约定公共 metadata 编码，但那只是新约定 | 现有旧写路径使用保留 metadata，RAD 不返回它们；普通 Register 禁止提交保留键。新旧 SDK 混用时无法仅靠客户端无损恢复 |

最明确的服务端契约缺口是旧 release 和未暴露的兼容字段；多版本 Endpoint 是 **共享连接约束下的 publisher 身份缺口**。新增真实连接/HTTP 身份能在客户端隔离 owner，但会扩大连接、心跳、容量、redo 和清理范围，不能称为简单 DTO 转换。不能据此把所有 A2A 方法都归为“必须新增兼容 RPC”。

### 5.3 兼容适配的范围选择

本设计保留两个实施范围；优先采用客户端语义转换，仅对 5.2 中证实无法保持契约的部分补必要支持。完整适配能力位表示整体承诺，不表示每个旧方法都要新增兼容 RPC；能力位和 wire 扩展均尚未定稿。

**方案 A：完整满足新服务端统一走新 Agent/RAD binding（推荐的目标方案）。** 在 binding 边界补 A2A 兼容适配，旧业务实现保持原样：

- 保留公共 `A2aService` DTO；不向通用 `AgentPublishRequest` 或六个 RAD 根模型塞入旧专用开关。
- 能直接表达的 Endpoint/查询/监听转换留在 Client，复用现有 RAD。只有旧 release、未公开的投影字段、共享连接下的版本 owner 或迁移权威需要补契约时，才考虑最小 binding 支持，并复用已有兼容服务，不给每个旧方法复制一套业务规则。
- 新 binding 需显式声明完整 A2A 适配能力，不能把已有 `radV1` 自动视为这些尚未实现的补充能力。HTTP 通过只读能力响应判断，gRPC 通过协商判断。静态能力不随迁移阶段翻转，动态权威分支和写保护由既有兼容服务处理。
- HTTP 旧 A2A 运行时 publication 若要与 gRPC 等价，必须把子 publisher 生命周期绑定到 HTTP client 活性，并覆盖失效 replay；现有实现主要绑定 gRPC connection，这部分属于必要的 transport 适配，不能假设已具备。
- 旧业务校验、存储、直接上线和迁移算法不变。兼容请求可复用已有对象，只有绑定信封、能力声明及 owner 适配是新增范围。
- 对拥有基础 RAD、但没有完整适配能力的服务器，继续旧 A2A 分支；新接口仍使用原 RAD。这是明确的能力退化，不以尝试业务写入探测能力。

方案 A 是 **Nacos 的 Agent/RAD binding 兼容扩展**，不是声称现有纯 RAD 0.1.0 五种操作已经能表达全部旧契约。实施前需把 binding 路径/信封与能力位定稿，并同步相关中英文规范。

**方案 B：严格限定 Client 和已有协议。** 先完成接口委托、资源 transport 与 Agent 接口；对满足完整语义和迁移前提的方法优先客户端转换到 RAD，有缺口的方法保留既有调用。不能把“只允许单版本、不带旧字段”的子集包装成已发布 A2A 方法的完整兼容；也不因发现第二个版本才把已有 publication 临时切协议。此方案不完整满足“全部旧操作切 RAD”的目标，不能用“服务端已存 canonical 数据”当成“客户端已调用 RAD”。

不采用逐个业务方法静默改语义、写失败跨协议重试、按版本新建多个完整 `AiService`、查询后合并多个版本批次、轮询 Admin API 等替代方式。

## 6. 实施顺序与改动清单

| 阶段 | 必要改动 | 验收出口 |
| --- | --- | --- |
| 1. 当前交付 | 接口委托、五资源 transport、旧 A2A 固定 gRPC、对应 UT/IT 与场景文档 | [PHASE1_PLAN.md](PHASE1_PLAN.md) 的三个 commit 与阶段门禁通过，即可独立完成 |
| 2. 后续契约与支持 | 定稿 A2A/RAD 转换边界、能力发现及确需的服务端最小支持 | 不改变旧 SDK 默认语义，协议与兼容 IT 明确；可以先提供支持而不切换 Client |
| 3. 后续 Client 切换 | 无损转换、模式决策、完整旧 API/HTTP/混合版本验证 | 第 5 节适用的全量兼容门禁通过；若范围可控，可与阶段 2 合并 |

本次以 `specs/{zh-cn,en}/ai/client-ai-api-evolution-spec.md` 纳入同一变更集中的规范修订提案，列明对 SDK、能力协商、Agent/A2A 和 HTTP binding 的覆盖关系。评审定稿后将目标条款合并入对应主规范；设计阶段不把目标行为写成已实现现状，不提前把 IT 覆盖标成 Covered。

## 7. 核对依据

以下链接均指向本设计基线中的仓库文件；实现时以代码与对应规范共同校验：

- [AiService](../../../api/src/main/java/com/alibaba/nacos/api/ai/AiService.java)、[A2aService](../../../api/src/main/java/com/alibaba/nacos/api/ai/A2aService.java)、[AgentDiscoveryService](../../../api/src/main/java/com/alibaba/nacos/api/ai/AgentDiscoveryService.java)。
- [AiMaintainerService](../../../maintainer-client/src/main/java/com/alibaba/nacos/maintainer/client/ai/AiMaintainerService.java)、[NacosAiService](../../../client/src/main/java/com/alibaba/nacos/client/ai/NacosAiService.java)。
- [AgentGrpcTransport](../../../client/src/main/java/com/alibaba/nacos/client/ai/remote/AgentGrpcTransport.java)、[McpTransportRouter](../../../client/src/main/java/com/alibaba/nacos/client/ai/remote/McpTransportRouter.java)、[AiGrpcClient](../../../client/src/main/java/com/alibaba/nacos/client/ai/remote/AiGrpcClient.java)。
- [A2A 兼容规范](../../../specs/zh-cn/ai/a2a-agent-spec.md)、[Agent API 规范](../../../specs/zh-cn/ai/agent-api-spec.md)、[RAD 规范](../../../specs/zh-cn/ai/rad-protocol-spec.md)。
- [A2aCompatibilityOperationService](../../../ai/src/main/java/com/alibaba/nacos/ai/service/a2a/A2aCompatibilityOperationService.java)、[A2aServerOperationService](../../../ai/src/main/java/com/alibaba/nacos/ai/service/a2a/A2aServerOperationService.java)、[AgentPublishApplicationService](../../../ai/src/main/java/com/alibaba/nacos/ai/service/agent/AgentPublishApplicationService.java)。
