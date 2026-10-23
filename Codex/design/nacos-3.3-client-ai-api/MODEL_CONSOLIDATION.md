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

# Agent / RAD Java 模型全量核查与收敛方案

> 2026-09-14：资源与版本摘要的后续合并已落地，当前类型和字段关系见 [MODEL_SUMMARY_MERGE.md](MODEL_SUMMARY_MERGE.md)。本文其余部分记录第一版包整合方案。

状态：已按确认方向完成本地试改与验证，所有改动保持未提交；执行结果和一项 MCP 首跑失败/复跑通过记录见 MODEL_VALIDATION.md。
更新：2026-09-11。代码基线：`1f51dfcc44e952b916ae32f70bd7ea0c8f4a06d3`。

本次完整检查 `api/.../ai/model/agent` 的 27 个文件和 `model/rad` 的 16 个文件，
合计 43 个文件，其中包含 3 个枚举和 1 个包内校验辅助类。
同时追踪 Client、Maintainer、HTTP/gRPC、事件、校验器、存储与关联 IT。
`a2a` 保留为历史兼容模型，不纳入类型合并。

用户已明确本轮可以忽略 `3.3.0-BETA` 的 Java 模型兼容。虽然当前 43 个文件中有 36 个
已出现在本地 BETA 标签，本方案不为其保留别名、旧包包装类或 Deprecated 过渡壳。
这一决定不改变旧 A2A 兼容要求，也不意味着可以改写已定义的 HTTP/gRPC 或存储格式。

## 1. 目标结构与复用原则

全部 Agent/RAD 具体业务模型统一到 `com.alibaba.nacos.api.ai.model.agent`，不保留
`model.rad`。仅用于字段复用的底层类放到 `model.agent.base`，全部声明为
`public abstract class AbstractAgent…`，不作为供用户直接构造的模型。
既有 RPC 信封继续放在 `api.ai.remote.request/response`；它们不是另一套领域模型。
Agent/MCP 共用的 `ClientLivenessInfo` 仍归入 AI 公共 `model` 包。

RAD 定义是共有业务概念与发现协议的依据。优先保留 RAD 的具体类型名，例如保留
`AgentCatalogVersion`、合并掉 `AgentVersionCatalogEntry`。
RAD 不定义审批、草稿、上下线等完整管理生命周期，Admin 可在公共对象之上增加其专用请求；
不要求每个 Admin 请求继承完整 RAD 根消息，也不把协议发现快照当成持久化定义。

### 1.1 底层 abstract 类候选

本地实现使用下列六个类提取现有重复字段。基础类是 Java 绑定实现，不新增协议根消息。
字段仅在表中指定的层声明；所有具体模型的扁平 JSON 属性集合保持现状。

| ID | `model.agent.base` 中的 abstract 类 | 自身声明的字段 | 具体使用者/下层关系 |
| --- | --- | --- | --- |
| B01 | `AbstractAgentMetadata` | agentName、displayName、description、iconUrl、provider、tags | AgentSummary、AgentCatalogEntry、AgentUpdateAdminRequest；AbstractAgentDraftRequest 继承它 |
| B02 | `AbstractAgentCallInterface` | protocol、protocolVersion、descriptorMediaType、nativeDescriptor | AgentDefinitionCallInterface、AgentDiscoveryCallInterface |
| B03 | `AbstractAgentSearchRequest` | agentNameContains、tagsAll、protocolsAny、pageNo、pageSize | AgentSearchClientRequest 与 RAD AgentSearchRequest 为并列子类 |
| B04 | `AbstractAgentEndpointRequest` | agentName、protocol、endpoints | 两个具体注销请求；AbstractAgentEndpointRegistrationRequest 继承它 |
| B05 | `AbstractAgentEndpointRegistrationRequest` | runtimeVersion、versionRange | 注册 ClientRequest 与 RAD RegistrationBatch 为并列子类 |
| B06 | `AbstractAgentDraftRequest` | extensions、version、callInterfaces、author、changeDescription、basedOnVersion | 继承 AbstractAgentMetadata；Admin 草稿创建与 Client publish 请求为并列子类 |

基类只共享同义字段与访问方法，不决定鉴权、默认 namespace、状态转换、网络调用、缓存或 redo。
原校验规则继续由具体请求和现有校验器执行；只有完全相同的公共约束才复用校验辅助逻辑。
B04 的 endpoints 在注册与注销中的允许字段不同，不能在基类统一其操作校验。
B06 中的 callInterfaces 使用具体 `AgentDefinitionCallInterface`，不能声明为抽象接口列表。

`AgentSummary`、`AgentVersionSummary`、`Endpoint` 本身有独立的响应/值对象意义，仍是具体类，
可保留 Agent→AgentSummary、VersionDetail→VersionSummary、DiscoveryEndpoint→Endpoint 的已有拟议复用。
不再为每两个相同字段建立一个 BaseId、BaseVersion、BaseNamespace 或泛型多层继承体系。
上表最长链为两层抽象类再接一个具体模型；不为了凑统一继承强加新层级。

### 1.2 对使用者可见的具体类型

```text
model.agent
  AgentSearchClientRequest      // SDK 构造，无 namespace
  AgentSearchRequest            // RAD 完整请求，含 namespace
  AgentCatalogEntry
  AgentCatalogVersion
  AgentReference
  AgentDiscoveryFilter
  AgentDiscoveryRequest
  AgentDiscoveryResult
  AgentDefinitionCallInterface
  AgentDiscoveryCallInterface
  AgentEndpointRegistrationClientRequest
  AgentEndpointRegistrationBatch
  AgentEndpointDeregistrationClientRequest
  AgentEndpointDeregistrationBatch
  AgentDraftCreateAdminRequest
  AgentPublishClientRequest
  ...其余有独立用途的具体模型/枚举
  base/
    AbstractAgentMetadata
    AbstractAgentCallInterface
    AbstractAgentSearchRequest
    AbstractAgentEndpointRequest
    AbstractAgentEndpointRegistrationRequest
    AbstractAgentDraftRequest
```

包划分服务于理解成本，不引入新的 Maven module。
抽象基类必须 public 才能供父包中的类继承，但使用 protected 无参构造器、明确的 Javadoc 和
独立 base 子包表达用途；不声称 abstract 或子包能完全隐藏 Java 类型。
base 可以引用稳定值对象，例如 AgentProvider、Endpoint；不能依赖 Client/Admin 专属请求。
这不是两个相互转换的领域模型包。

公开 SDK 参数、返回值、DTO 成员及列表元素均使用具体类，不暴露 AbstractAgent… 类型、
`List<AbstractAgent…>`、通用 Map 或要求用户选择子类型的 polymorphic API。
继承的 getter/setter 仍能操作完整业务字段。Jackson 对具体类型及其父类属性正常绑定，
不引入 `@JsonTypeInfo`、类型判别字段、自定义子类工厂或展开 JSON 的技巧。

### 1.3 namespace 与请求继承示例

```java
// package ...model.agent.base
public abstract class AbstractAgentSearchRequest implements Serializable {
    // RAD 定义的五个查询字段；不含 namespaceId。
}

// package ...model.agent
public class AgentSearchClientRequest extends AbstractAgentSearchRequest {
    // 独立 Client 入参类型，不直接接受完整 RAD 请求。
}

public class AgentSearchRequest extends AbstractAgentSearchRequest {
    private String namespaceId;
}
```

SDK 用 ClientRequest 的业务字段构造新的 RAD Request，并绑定实例 namespace。
二者是共享基类的并列子类，不能彼此赋值。不能为了省一次复制，把公共方法改成接收抽象基类。
原先 `AgentSearchRequest extends AgentSearchQuery` 的方向被此结构替代；注册/注销同理。
Client publish 和 Admin 草稿创建也各自继承公共草稿基类，避免 Client 依赖 AdminRequest。

具体请求按 `Agent + 操作/内容 + ClientRequest/AdminRequest` 命名，RAD 标准请求保留规范名称。
只在存在实际边界时使用两个具体类型，不为完全一致的操作机械增加成对空包装类。
例如 Search 的两个具体类型承担 namespace 隔离，虽字段位于基类，仍有明确存在理由。

本轮重点是把公共字段藏到单独的 abstract 基础层，而非只压低源文件总数。
旧方案的“六组复用、42 个文件、39 处字段”统计不再作为新方案规模承诺；最终以本节和全量清单为准。

## 2. model.agent：27 个文件逐项结论

字段数不计 `serialVersionUID`，仅计算当前类自身声明的业务字段。
“保留”表示经过审查后不合并，不代表没有检查重复。

| # | 当前类型 | 字段数 | 用途及重复关系 | 本轮改造方式 |
| --- | --- | ---: | --- | --- |
| A01 | `Agent` | 16 | 比 `AgentSummary` 仅多 `extensions` | 改为继承 AgentSummary；自身只保留 extensions，公共展示字段由 AbstractAgentMetadata 间接提供 |
| A02 | `AgentAdminRequestUtils` | 0 | 包内辅助校验，不是模型；同包请求直接调用，无 import 不代表未使用 | 保留，不借本轮搬移工具类 |
| A03 | `AgentCallInterface` | 6 | 版本定义：协议描述、来源顺序、声明地址；与发现接口共同拥有四个描述字段 | 重命名为 AgentDefinitionCallInterface，继承 AbstractAgentCallInterface，只声明来源顺序与声明地址 |
| A04 | `AgentDraftCreateRequest` | 12 | 初始/后续草稿；`callInterfaces` 与 `basedOnVersion` 二选一；含初始 Agent 元数据 | 重命名为 AgentDraftCreateAdminRequest，继承 AbstractAgentDraftRequest；保留创建校验 |
| A05 | `AgentDraftUpdateRequest` | 4 | 指定版本草稿完整替换，必须提供 `callInterfaces` | 重命名为 AgentDraftUpdateAdminRequest；保留四字段及替换校验，不继承暴露初始元数据/basedOnVersion 的创建请求 |
| A06 | `AgentEndpointDeregistration` | 3 | Client 自然键注销意图 | 重命名为 AgentEndpointDeregistrationClientRequest，继承 AbstractAgentEndpointRequest，无 namespace |
| A07 | `AgentEndpointRegistration` | 5 | Client 完整注册批次，含运行版本与版本范围 | 重命名为 AgentEndpointRegistrationClientRequest，继承 AbstractAgentEndpointRegistrationRequest，无 namespace |
| A08 | `AgentLabelsUpdateRequest` | 2 | 自定义标签完整替换；禁止用户写 `latest` | 重命名为 AgentLabelsUpdateAdminRequest，保留标签专属校验 |
| A09 | `AgentOverview` | 2 | 完整 Agent 加有界的版本摘要页 | 保留组合；不摊平为包含所有版本内容的大对象 |
| A10 | `AgentProvider` | 2 | name/url 值对象，已由 Agent 与 RAD 共用 | 保留；不依赖历史 `a2a.AgentProvider` |
| A11 | `AgentPublishRequest` | 1 | 已继承草稿创建请求，只补 `autoSubmit=false` | 重命名为 AgentPublishClientRequest，继承 AbstractAgentDraftRequest，只补 autoSubmit；不再继承 Admin 具体请求 |
| A12 | `AgentSearchQuery` | 5 | Client 无 namespace 查询条件 | 重命名为 AgentSearchClientRequest，继承 AbstractAgentSearchRequest；无 namespace |
| A13 | `AgentSummary` | 15 | 管理列表摘要，无 extensions | 继承 AbstractAgentMetadata，自身保留九个管理字段；保留真实摘要响应类型 |
| A14 | `AgentUpdateRequest` | 8 | 资源可写字段完整替换，status 必填校验 | 重命名为 AgentUpdateAdminRequest，继承 AbstractAgentMetadata，只补 extensions/status；校验保持 |
| A15 | `AgentVersionCatalog` | 2 | latestVersion + onlineVersions，管理及存储的派生目录 | 保留容器与 latestVersion/onlineVersions JSON 名称；条目统一使用 AgentCatalogVersion |
| A16 | `AgentVersionCatalogEntry` | 3 | version/labels/protocols，与 RAD AgentCatalogVersion 同构 | 合并到 RAD 基准 AgentCatalogVersion 后删除，管理/存储泛型及校验引用同步替换 |
| A17 | `AgentVersionCommand` | 2 | 精确版本生命周期操作目标 | 重命名为 AgentVersionAdminRequest；保留精确版本身份，不继承宽松的 AgentReference |
| A18 | `AgentVersionDetail` | 11 | 比版本摘要多 namespaceId/agentName/callInterfaces | 改为继承 `AgentVersionSummary`，自身保留这三个字段 |
| A19 | `AgentVersionInfo` | 4 | editing/reviewing/onlineCnt/labels 生命周期摘要 | 保留；不同于在线目录，跨模块 ResourceVersionInfo 的重复见第 5 节 |
| A20 | `AgentVersionSummary` | 8 | 有界版本列表，无 CallInterface 内容 | 保留为详情父类；列表读取仍不加载 AI Storage 内容 |
| A21 | `ClientLivenessInfo` | 3 | HTTP Client 心跳/不健康/过期时限，实际被 Agent 与 MCP 共用 | 移到 `com.alibaba.nacos.api.ai.model.ClientLivenessInfo`；只改引用，不改协调与续租逻辑 |
| A22 | `Endpoint` | 6 | uri/transport/priority/weight/metadata/healthy；多操作复用并按上下文校验 | 保留；不新加 EndpointKey，不将 bindings 下沉到此类 |
| A23 | `EndpointSource` | — | RUNTIME/DECLARED 来源 | 保留；不是健康状态，也不是 Watch 事件 |
| A24 | `RuntimeEndpointSnapshot` | 5 | 管理侧原始运行快照，不作最终可发现性决策 | 保留；不能与 Discover 结果合并 |
| A25 | `RuntimeEndpointSnapshotItem` | 6 | Endpoint + bindings + enabled/healthy/state/观察时间 | 保留组合；不把管理状态塞入 AgentDiscoveryEndpoint |
| A26 | `RuntimeEndpointState` | — | AVAILABLE/DISABLED/UNHEALTHY | 保留；由 enabled/healthy 按优先级派生，不能替代来源枚举 |
| A27 | `RuntimeVersionBinding` | 2 | 一对运行版本和匹配范围，发现/管理结果已共用 | 保留；注册载荷中的同名字段保持平铺，不新增嵌套 JSON |

## 3. model.rad：16 个文件逐项结论

| # | 当前类型 | 字段数 | 用途及重复关系 | 本轮改造方式 |
| --- | --- | ---: | --- | --- |
| R01 | `AgentCatalogEntry` | 8 | Search 展示信息与在线版本目录；不是管理 AgentSummary | 移到 model.agent 并继承 AbstractAgentMetadata，只声明 latestVersion/versions；继续使用 AgentCatalogVersion |
| R02 | `AgentCatalogVersion` | 3 | 与 AgentVersionCatalogEntry 完全同构 | 移到 model.agent，作为唯一版本目录条目；吸收 AgentVersionCatalogEntry 的使用方 |
| R03 | `AgentDiscoveryCallInterface` | 5 | 协议描述及已解析的 endpointSets | 移到 model.agent，继承 AbstractAgentCallInterface，只声明 endpointSets；不继承定义接口 |
| R04 | `AgentDiscoveryEndpoint` | 1 | 已继承 Endpoint，只扩展 bindings | 移到 `model.agent`，保留现有继承 |
| R05 | `AgentDiscoveryFilter` | 5 | 已选版本内的协议/transport/来源/metadata 过滤 | 移到 `model.agent`；不与 Search 条件合并 |
| R06 | `AgentDiscoveryRequest` | 3 | namespace + reference + filter 的协议组合 | 移到 model.agent；保持 namespace/reference/filter 组合，SDK 继续使用 Reference/Filter 参数而非此完整请求 |
| R07 | `AgentDiscoveryResult` | 5 | 版本描述与实时地址集合的完整快照 | 移到 `model.agent`；不继承 AgentVersionDetail |
| R08 | `AgentEndpointDeregistrationBatch` | 4 | 带 namespace 的 SDK 内部注销意图；不是服务端逐项删除 RPC | 移到 model.agent，继承 AbstractAgentEndpointRequest，只补 namespaceId；保留 SDK 期望集语义 |
| R09 | `AgentEndpointRegistrationBatch` | 6 | 带 namespace 的完整协议注册批次 | 移到 model.agent，继承 AbstractAgentEndpointRegistrationRequest，只补 namespaceId |
| R10 | `AgentReference` | 3 | agentName + exact version 或 label，也允许省略选择器 | 移到 `model.agent`；不继承要求 version 必填的 AgentVersionCommand |
| R11 | `AgentSearchRequest` | 6 | 带 namespace 的协议查询条件 | 移到 model.agent，继承 AbstractAgentSearchRequest，只补 namespaceId，与 ClientRequest 并列 |
| R12 | `AgentWatchBatchItem` | 3 | clientWatchId、发现请求、materializedFingerprint | 移到 model.agent，保留 HTTP Watch 绑定项，不与 DiscoveryRequest 合并 |
| R13 | `AgentWatchBatchRequest` | 3 | generation、timeoutMillis、watches | 移到 model.agent，保留 HTTP Watch 批次控制语义 |
| R14 | `AgentWatchBatchResponse` | 3 | generation、changed、changedClientWatchIds | 移到 model.agent，保留刷新提示语义，不与完整发现结果合并 |
| R15 | `AgentWatchEventType` | — | INVALIDATE/REVALIDATE/TERMINATED，gRPC Wire Hint 语义 | 移到 model.agent，保留 Wire 事件含义，不与 SDK SNAPSHOT/UNAVAILABLE 合并 |
| R16 | `EndpointSet` | 3 | source/sourceRevision/endpoints，发现结果中的分来源地址集合 | 移到 `model.agent`；不与 RuntimeEndpointSnapshot 合并 |

## 4. 容易误合并的字段组

### 4.1 元数据展示字段重复

共同的 agentName/displayName/description/iconUrl/provider/tags 提取到
`base.AbstractAgentMetadata`。CatalogEntry、管理摘要和可写元数据请求继承同一组同义字段，
但状态、owner/scope、namespace、extensions、版本内容不混入这层。
字段复用不让 Admin 查询沿用 RAD Search 的可见性、在线筛选或匹配行为。
各具体操作继续独立校验，继承不代表继承另一个 API 的操作权限。

### 4.2 CallInterface 的四个描述字段

两个 CallInterface 的 protocol/protocolVersion/descriptorMediaType/nativeDescriptor
移到 `base.AbstractAgentCallInterface`。定义与发现视图是并列的具体子类，分别添加
endpointSourceOrder/declaredEndpoints 和 endpointSets。
这既复用 RAD 已定义的描述语义，也保留定义内容与解析结果的区别。

不能让 AgentVersionDetail 继承 AgentDiscoveryResult 并重写不同元素类型的 callInterfaces：
`List<AgentDefinitionCallInterface>` 不能替代 `List<AgentDiscoveryCallInterface>` 的 getter 返回类型。
公共基类不携带这两个相异的列表，也不为此引入面向使用者的泛型结果继承树。

### 4.3 注册与注销、选择器与精确身份

- 注册是完整替换，注销是自然键删除意图。共同字段可放 AbstractAgentEndpointRequest，但两个具体操作请求不相互继承，基类不合并校验语义。
- 注销先更新 SDK 期望集；有剩余则重新注册完整剩余集，无剩余才注销整个 publication。
  不能因 Batch 类型整理而将其改成服务端逐项删除。
- AgentReference 允许省略选择器、使用标签；AgentVersionCommand 要求精确版本。
  两者不合并成所有字段可空的“通用身份”。
- runtimeVersion/versionRange 在注册中是该批次的事实，在发现结果 bindings 中是多个发布者的
  去重来源集合。复用 RuntimeVersionBinding 的集合结果，但不改变注册的平铺字段格式。

### 4.4 目录、状态、来源、摘要并不等价

- VersionInfo 的 labels 包含系统 latest；在线目录条目的 labels 排除 latest。
  VersionInfo 还表达 editing/reviewing/onlineCnt，不能替代 VersionCatalog。
- 管理目录要求 labels 数组（允许空）；RAD 条目的 labels 可以省略。共用 Java 条目类型后，
  继续由各自构造和校验路径处理 `[]` 与 null，不能直接返回存储目录对象。
- AgentCatalogEntry 只返回面向发现的在线目录与展示信息，不能继承含 owner/scope/status 的管理摘要。
- 来源顺序、健康状态和 Watch 生命周期是三个不同维度，不合并枚举。

## 5. 关联边界的补充核查

| 关联对象/链路 | 发现 | 本轮处理 |
| --- | --- | --- |
| 服务端 `service.resource.ResourceVersionInfo` | 与 API AgentVersionInfo 的四个字段同构，当前有双向转换并复制 labels | 保留模块边界；不让 api 依赖服务端，也不为本次整理将全部 AI 资源版本信息迁入 api |
| `AgentResourceExt` / `AgentVersionContent` / `AgentVersionStorageDescriptor` | 持久化信封、版本内容及存储定位，分别有固定 schema/digest | 保留服务端类型和存储序列化，不与 SDK 响应合并 |
| `AgentSearchRpcRequest` 等 gRPC Request/Response | RPC 信封包含业务 DTO；最外层类型名用于 Payload metadata.type | 保留信封类名、嵌套字段和能力位，模型搬包只更新引用 |
| HTTP Client/Admin/Console Forms | HTTP 参数绑定及校验对象，部分字段与 DTO 重叠 | 保留绑定层；不为减少字段把公共请求变成 Spring Form |
| `NacosAgentDiscoveryEvent` / `NacosAgentDiscoveryEventType` | 向用户交付完整 SNAPSHOT 或 UNAVAILABLE；不是 Wire Hint | 只更新结果模型 import，保留类型、回调及异常语义 |
| `ClientLivenessInfo` 的 MCP 使用 | 当前 McpClientController、McpTransport、McpEndpointPublicationManager 等依赖 agent 包 | 移到 AI 公共 model，只替换 imports/type references，不修改 MCP 业务实现 |
| 历史 `a2a` 包 | 包含 AgentProvider、AgentVersionDetail 等同名类型，但已是旧接口契约 | 保持独立；显式 import，禁止自动替换同名旧类型 |

### 5.1 namespace 边界的准确范围

当前已实现并写入主规范的约束是：Client 的用户构造入参不暴露 namespace，SDK 复制并注入实例 namespace。
所有 Maintainer 请求/命令同样不内嵌 namespace，由方法的显式参数给出。

当前 `AgentDiscoveryResult` 和 publish 返回的 `AgentVersionDetail` 仍带 namespaceId，
这属于返回资源归属，并非让用户传入 namespace。本次核查明确记录这一事实，不声称所有 Client
返回模型也已经隐藏 namespace。若要进一步隐藏返回对象中的 namespace，需要单独区分公开结果与
协议/管理结果，不能直接给共享模型添加 JsonIgnore 或删除协议字段；本轮去重不改变返回契约。

Client 与 RAD 请求是共享抽象基类的并列子类，SDK 不接受完整 RAD 请求。转换仍只复制支持的业务字段并注入实例 namespace；不得为复用基类放宽公共方法的参数类型。

## 6. 必须保持的兼容与实现约束

1. **源码迁移**：model.rad 全部合入 model.agent、ClientLivenessInfo 搬包、请求类型重命名，
   以及管理侧 `List<AgentVersionCatalogEntry>` 改为 `List<AgentCatalogVersion>`，需更新 imports/泛型并重新编译。
   本轮按用户确认不提供 BETA Java 兼容壳；旧 A2A 已发布 API 不在此豁免范围。
2. **JSON 与校验**：保留字段名、平铺/嵌套结构、null/空数组规则、缺省值、枚举文本、类型限制和异常码。
   保留 RAD AgentCatalogVersion 概念与 Java 名称；抽象基类是 Java 绑定实现，不新增或改写 Schema 根消息。
3. **摘要投影**：Agent 继承 Summary 后，不能把实际 Agent 实例直接当 Summary 返回；Jackson 仍可能
   序列化 extensions。Version 列表同理，必须构造真实 Summary，避免泄漏 CallInterface 内容或新增存储读取。
4. **对象所有权**：保留 lists/maps/endpoints/nativeDescriptor 的现有复制和规范化；不共享存储缓存与返回 DTO。
5. **存储与 fingerprint**：AgentVersionContentSerializer 使用显式存储投影，不能改为直接序列化整个 DTO。
   验证已有存储字节读取、contentDigest、sourceRevision、完整 Watch fingerprint 不变。
6. **Java 序列化**：保留 serialVersionUID 不代表继承链和包迁移后的 ObjectStream 兼容。
   当前 HTTP/gRPC 使用 JSON；本方案不承诺 BETA Java 对象流兼容，也不增加 readObject/writeObject 适配。
7. **算法边界**：不更改 A2A/MCP/Skill 业务实现、双 transport 决策、migration、缓存、Watch、publication/redo 算法。
   必要的模型引用、泛型、投影构造及校验参数类型修改在本轮范围内。

## 7. UT / IT 回归矩阵

以下为本轮模型整理的验证矩阵。新增/加强的测试已落地，执行结果和限制单独记录在
[本地试改验证记录](MODEL_VALIDATION.md)，不将历史通过状态当成本次证据。

| ID | 验证目标 | 复用入口与补强方式 |
| --- | --- | --- |
| M01 | 抽象字段复用及具体视图的完整字段、缺省值、null/空集合；父类 getter/setter 被正确序列化 | `AgentContractModelTest`、`RadProtocolModelTest`；用预期 JSON 字段断言和反序列化，不能只自序列化再自反序列化 |
| M02 | SDK 公开输入无 namespace；转换后使用实例 namespace；输入和集合不被修改 | `AgentModelUtilsTest`、`AgentContractModelTest`；验证 Client/RAD 为并列类型、Client 没有 namespace accessor，转换后实例 namespace 与深拷贝有效 |
| M03 | 管理摘要不会多出 extensions/CallInterface，详情完整保留；版本列表不加载内容 | `AgentPersistenceServiceTest`、管理 OpenAPI IT、`AgentMaintainerServiceMaintainerSdkITCase`；断言具体 JSON 字段集合与受控存储访问 |
| M04 | 合并后的版本条目保留 labels/排序/latest 语义；管理空数组与 RAD 省略规则 | `AgentVersionCatalogBuilderTest`、`AgentResourceExtSerializerTest`、`AgentDiscoveryApplicationServiceTest`、Search SDK/OpenAPI IT |
| M05 | 定义/发现 CallInterface 字段边界、DECLARED/RUNTIME、bindings/healthy、空来源集合不变 | `AgentModelValidatorTest`、`RadModelValidatorTest`、`AgentDiscoveryServiceJavaSdkITCase`；验证完整返回及过滤结果 |
| M06 | 老 JSON 能读、新 JSON 结构兼容；RPC envelope type/字段不变 | `AgentClientBindingModelTest`、`AgentWatchRpcBindingTest`、`AgentWatchBatchModelsTest`；保留变更前固定载荷，不改 expected 来迎合实现 |
| M07 | 模型整理不改变存储字节与摘要，也不污染管理/发现对象 | `AgentVersionContentSerializerTest`、`AgentResourceExtSerializerTest`、canonicalizer/fingerprint UT；比较固定存储向量及跨 transport 指纹 |
| M08 | Search、Discover、订阅事件和 publish 的 Client 真实返回字段 | `AgentDiscoveryServiceJavaSdkITCase`、`AgentPublishJavaSdkITCase`；grpc/http/auto，默认/自定义 namespace，包含结果和回调字段 |
| M09 | 注册三个 Endpoint 后注销两个，保留一个及所有字段；最后注销、未知键、协议隔离 | 复用 `shouldReplaceAndPartiallyDeregisterCompletePublications`，grpc/http/auto；实际 Discover 验证服务端效果 |
| M10 | 公开入口及共享 Client 活性不因包迁移改变 | `AiServiceJavaSdkITCase`、`AiTransportResourceMatrixJavaSdkITCase`、MCP HTTP IT；验证旧 AiService 调用与资源子接口调用、Agent/MCP HTTP 联合使用 |
| M11 | Maintainer 草稿创建/更新/publish、资源元数据更新及返回详情/摘要无回归 | `AgentMaintainerServiceMaintainerSdkITCase`；覆盖 direct/basedOnVersion 校验及 namespace 参数，维持治理字段权限边界 |
| M12 | HTTP Client/Admin/Console 响应形状保持 | AgentDiscovery/Endpoint/Publish/Watch Client OpenAPI IT，AgentAdmin/Version/RuntimeEndpoint/Console OpenAPI IT；检查精确字段与错误响应 |
| M13 | 包和泛型引用完整更新，旧 A2A 同名模型未误替换 | api/client/ai/maintainer-client/console/ai-registry-adaptor 及三个 IT 模块编译；旧 A2A 模型/API/委托回归 |
| M14 | 默认 JSON 与 Jackson 3 适配一致，SDK 仍是 Java 8 | 复用现有 JSON adapter 测试配置；两个适配运行相关 UT/SDK IT，确认客户端编译目标 |
| M15 | 基础层不可实例化；公开 SDK 签名、DTO 成员和列表元素使用具体类型 | 结构检查 base 下所有模型 abstract、protected 构造；检查公开参数/返回及成员类型；从固定 JSON 直接反序列化具体请求，无 type discriminator |

实施时同步 `JAVA_SDK_IT_COVERAGE.md`、`JAVA_SDK_IT_SCENARIOS.md`、
`AGENT_DISCOVERY_SDK_IT_SCENARIOS.md`、`AGENT_PUBLISH_SDK_IT_SCENARIOS.md`，
以及受影响的 Maintainer SDK 和 OpenAPI 场景/覆盖登记。
本轮不扩展真实服务端故障恢复注入；既有跳过/环境限制继续如实记录。

## 8. 后续可选 commit 顺序

本轮按用户要求仅做本地试改，不执行下表的提交操作。

| Commit | 内容 | 独立完成条件 |
| --- | --- | --- |
| 1 | 本文全量清单、双语规范修订提案、迁移与测试计划 | 模型 43/43 有结论，明确删除/继承/包迁移及保持的协议约束 |
| 2 | 全部 RAD 模型归入 agent；保留 AgentCatalogVersion、删除重复目录条目；共享活性模型搬包与引用更新 | Spotless apply/check，相关 UT，受影响生产与 IT 编译通过 |
| 3 | 提取 base 下抽象类；统一 Client/Admin 具体请求命名和签名；校验/复制适配及必要 UT | 公开接口只使用具体模型；无 namespace 泄漏；模型/RPC/事件 UT、全部关联模块编译通过 |
| 4 | 补齐 M01–M15 中尚未由前两次代码提交覆盖的契约回归和 IT 场景登记 | 模型/存储/默认 JSON/Jackson 3 回归及外部服务器三类 IT 通过，限制如实记录 |

每个代码 commit 都包含使其独立可验证的必要测试，不把已知编译失败或序列化破坏留给最后一个 commit。
暂不实施 A2A→RAD 转换；模型整理完成后再继续原兼容专题。

## 9. 证据索引与规范关系

- [模型与字段](../../../api/src/main/java/com/alibaba/nacos/api/ai/model/agent/Agent.java)、
  [管理摘要](../../../api/src/main/java/com/alibaba/nacos/api/ai/model/agent/AgentSummary.java)、
  [版本详情](../../../api/src/main/java/com/alibaba/nacos/api/ai/model/agent/AgentVersionDetail.java)、
  [版本摘要](../../../api/src/main/java/com/alibaba/nacos/api/ai/model/agent/AgentVersionSummary.java)。
- [公开 AgentDiscoveryService](../../../api/src/main/java/com/alibaba/nacos/api/ai/AgentDiscoveryService.java)、
  [SDK 输入复制/绑定](../../../client/src/main/java/com/alibaba/nacos/client/ai/utils/AgentModelUtils.java)。
- [目录与发现投影](../../../ai/src/main/java/com/alibaba/nacos/ai/service/agent/AgentDiscoveryApplicationService.java)、
  [管理读取与 ResourceVersionInfo 转换](../../../ai/src/main/java/com/alibaba/nacos/ai/service/agent/AgentPersistenceService.java)。
- [目录构建](../../../ai/src/main/java/com/alibaba/nacos/ai/service/agent/metadata/AgentVersionCatalogBuilder.java)、
  [存储结构校验](../../../ai/src/main/java/com/alibaba/nacos/ai/service/agent/metadata/AgentResourceExtSerializer.java)、
  [版本内容与摘要](../../../ai/src/main/java/com/alibaba/nacos/ai/service/agent/storage/AgentVersionContentSerializer.java)。
- [RAD 协议](../../../specs/en/ai/rad-protocol-spec.md)、
  [Agent 管理](../../../specs/en/ai/agent-management-spec.md)、
  [Agent API](../../../specs/en/ai/agent-api-spec.md)、
  [Agent 存储](../../../specs/en/ai/agent-storage-spec.md)、
  [Java SDK](../../../specs/en/sdk/sdk-java-impl-spec.md)。
- 本轮对应修订提案：[中文 §6](../../../specs/zh-cn/ai/client-ai-api-evolution-spec.md#6-agent--rad-java-模型收敛提案)、
  [English §6](../../../specs/en/ai/client-ai-api-evolution-spec.md#6-agent--rad-java-model-consolidation-proposal)。
  这些是待评审补充，不将尚未实现的继承/包迁移描述成当前行为。

## 10. 本地实现补充

工作分支为 `codex/agent-model-consolidation`，从本文代码基线对应的 `upstream/develop` 创建。
初始 43 个 Agent/RAD Java 文件，合并一个重复条目类、增加六个 abstract 基类后为 48 个文件
（包含迁到 AI 公共 model 的 ClientLivenessInfo）。业务字段声明从 194 处减少至 130 处，
统计不含 serialVersionUID；公开字段和线上 JSON 形状保持原契约。

Client 发布不再继承 Admin 草稿请求后，有两处必要的内部依赖调整：

- HTTP Form 提取 `AbstractAgentDraftForm` 共享字段和 JSON 解析，两个具体 Form 各自构造、校验
  Client/Admin 请求；不把抽象类型暴露为 HTTP 请求体或 SDK 入参。
- AgentOperationService 提供具体 Client 发布草稿入口与原 Admin 入口，共用私有
  `createValidatedDraft` 和原持久化流程；`autoSubmit` 仍只由原发布应用服务处理。

未将 A2A→RAD 模式决策纳入本次试改，也未调整 MCP/Skill、迁移或 redo 算法。
