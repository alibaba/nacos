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

# ARD v0.91 适配设计

已实现范围：双版本离线 Schema、新 host 发现入口、示例投影和 JSON-LD context 解析器、Search 查询 context 匹配、Explore 查询与 facet context 匹配、Search 最大 100 条分页。
信任声明按§5.2进行一致性检查；完整一致性验收仍待完成，不提升当前整体兼容基线。
本文件定义目标契约，不表示当前服务已经完整支持。
当前行为仍以 [AI Registry 适配器规范](ai-registry-adaptor-spec.md)为准。

## 1. 基线与交付范围

- 当前兼容基线：上游 5fa2f5aef790b478319f6a3b43adf4661b0ed0e0，对应 v0.9 Draft。
- 目标基线：上游 f073b1ea9b01a5f068d0b0986426f2ea49fa30b2，对应 v0.91 Proposal。
- v0.91 文档日期为 2026-08-26，目标 commit 日期为 2026-10-25；2026-11-04
  核对时 GitHub Releases 为空。OpenAPI info.version 仍为 0.5.0，
  旧 catalog specVersion=1.0 不是 ARD 规范版本。
- Schema 和 context 固定到上述目标 commit，构建与测试使用仓库中的原始离线文件。

本轮升级 Nacos 已有的资源发布、Search、Explore、List 与 Artifact 兼容面，
资源范围仍为 Agent、Skill、Prompt、MCP。沿用 Nacos canonical 资源、
namespace、权限、生命周期和共享 Search Core，不新增 ARD 专属持久索引。

交付名称为“ARD v0.91 本地资源适配”。外部 manifest/网页抓取、上游 Registry
管理和完整联邦不属于原有适配器范围；这些能力未实现前，不宣称完整 ARD Registry
conformance。Web Ingestion 在新旧规范中均为 Required，不能把它描述成可选的新版本变化。

## 2. 差异与设计决策

### 2.1 差异索引与协议依据

以下是目标契约的摘要；具体语义在§3–§7维护。协议引用固定到目标SHA，
测试输入/断言只在[统一测试设计](../../../test/openapi-test/ARD_V091_TEST_DESIGN.md)维护；
当前代码事实和迁移见[兼容审计](../../../test/openapi-test/ARD_V091_COMPATIBILITY_REVIEW.md)。
context 加载和信任声明策略分别见§4.1、§5.2。

| 差异 | ARD依据 | 目标摘要 | 强度/验证入口 |
| --- | --- | --- | --- |
| D01 / 最新规范与版本标识 | [Appendix D.1–D.4][s01] | 固定目标 commit、原始 Schema 和 context；分开记录协议版本、OpenAPI 版本、legacy catalog 版本。 | 契约来源；UT-C02-01 |
| D02 / 新 well-known 路径 | [§5.1][s02] | 新增 /.well-known/ard.json，保留旧路径；文档使用 rel=ard。 | consumer MUST；旧路径兼容是 Nacos 选择；IT-C03-01..03 |
| D03 / 独立 Entry/Manifest 模型 | [§4.2–4.3][s03]、[OpenAPI results][results] | 新 Manifest 只要求 entries；完整 Entry 与 Search/List 投影分开验证。 | 完整/部分约束不同；UT-C02-02..04 |
| D04 / Entry JSON-LD 上下文 | [§4.1][s04]、[基础 context][context] | 新 Manifest Entry 显式输出基础 context；旧封装不新增必需字段，不改变资源 URN。 | base 必须，wire context 可选；UT-C04-01..04 |
| D05 / query context 与 IRI 过滤 | [§5.3.1][s05]、[QueryModel][query] | 用标准 JSON-LD 1.1 处理器解析，转换为已有领域 predicate；不靠字符串替换实现。 | IRI/OR/AND；不支持字段400为MAY；UT/IT-C05 |
| D06 / Explore 字段路径 | [§5.3.3][s06] | 与 Search 共用解析；聚合完整匹配集，响应保留调用方 facet 字段拼写。 | 全集聚合与同路径语法；UT/IT-C06 |
| D07 / representativeQueries 约束放宽 | [§4.2][s03]、[D.2][s07] | 资源不会因查询示例缺失被隐藏；区分新版告警与旧版数量校验。 | 2..5为建议/告警；UT/IT-C07 |
| D08 / trustManifest 开放模型 | [§4.5.1–4.5.2][s08] | 保持 identity 必填语义；检验本地声明的 authority 绑定，验证框架与查询分数分离。 | binding MUST，验证框架外置；UT/IT-C08及§5.2的信任身份处理策略 |
| D09 / URI、URN、媒体类型澄清 | [§3.3][s09]、[§5.3][urls]、[Appendix C][urn] | 保持当前 registry base URL、artifact URL 和稳定 URN；接受合法扩展媒体类型，不创建未支持的资源类型。 | 类型/URL/URN；UT-C09-02、IT-C09-03 |
| D10 / Search pageSize 上限 | [§5.3.2][s10]、[§5.3.4][list] | 将现有 50 提升至规范正文的 100，并补边界和分页测试；属于既有偏差修复。 | 正文默认/上限；非正数策略为Nacos兼容；UT-C09-01 |
| D11 / 一致性测试更新 | [Appendix B][errors]、[D.4][runner] | 双版本 fixture、Java 契约测试与真实 HTTP IT 验证结构、非空发现和权限；上游工具仅作本地独立对照。 | 错误与工具；UT/IT-C10 |
| D12 / 抓取、联邦、tracing 与历史缺口 | [§5.2][ingest]、[§5.4–5.5][federation] | 显式登记范围和后续门禁，不用基础 runner 通过代替完整能力证明。 | 抓取Required，tracing Informative；IT-C10及范围声明 |

<a id="compatibility-policy"></a>

### 2.2 兼容投入原则

本计划采用“最新协议优先、保留低成本兼容”。[§5.1][s02]允许旧发现路径仅作为可选兼容，
[§4.1][s04]又明确支持无context的旧Entry；按具体条款执行，不从Proposal推导统一兼容保证。
旧catalog路由/投影、共同合法的Search/List字段和legacy filters沿用现有实现，
不另建长期双协议引擎、版本协商或token转换层。协议必要变化优先，逐项记录迁移和预期错误；
新Schema允许省略字段不构成删除旧字段的必要理由。

token的同版正确性、跨版边界及重新查询统一按[§4.5](#token-contract)执行。
鉴权、namespace、资源身份、制品与主服务/SDK仍按Nacos规范回归；必要UT/IT不因兼容投入缩减而取消。

<a id="response-contract"></a>

## 3. 发现入口与响应模型

| 路径 | 目标行为 |
| --- | --- |
| GET /.well-known/ard.json | 标准 host 级 ARD Manifest；仍只发布本 Registry Entry。 |
| GET /.well-known/ai-catalog.json | 保留 legacy host catalog，specVersion=1.0 与 host 字段继续有效。 |
| GET /v3/ai/ard/ai-catalog.json | 保留 legacy namespace catalog。 |
| POST /v3/ai/ard/search、POST /v3/ai/ard/explore、GET /v3/ai/ard/agents | 保留路径，增加新版查询语义。 |
| GET /v3/ai/ard/artifacts | 保持版本、digest、representation 和权限契约。 |

ARD §5.1 不要求新增 Nacos namespace 路径。原计划的 /v3/ai/ard/ard.json
从本轮必做范围移除，旧 namespace catalog 继续保留。

新入口使用原适配器端口与开关、ArdApi 错误处理和 Open API 身份验证。
必须登记到适配器 ControllerMethodsCache，不能因为是 well-known 而跳过显式凭据校验。
部署文档说明域名根路径到适配器的代理映射；不新增 HTML 页面或 DNS 管理功能。
新旧入口直接返回各自文档，不使用重定向，不新增协议版本协商参数。

模型建议：ArdEntryFields 表达公共字段，ArdEntry 表达完整发布条目，
ArdManifest 表达 entries 容器；legacy ArdCatalog/ArdHostInfo 保留专用封装。
Search 与 List 保持各自响应 DTO；允许公共字段复用，禁止 score/source 泄漏到 Manifest。
实现可复用原 DTO，不以重命名全部内部类为交付目标。

新内部模型不等于所有 HTTP API 更换响应模型。对外绑定明确如下：

| API | 对外响应及兼容要求 |
| --- | --- |
| 新 GET /.well-known/ard.json | 使用新 ArdManifest/完整 Entry 契约；新 context 或扩展字段按新版规则发布。 |
| 旧 GET /.well-known/ai-catalog.json、GET /v3/ai/ard/ai-catalog.json | 保留 ArdCatalog/ArdHostInfo 和旧 Entry 投影；specVersion=1.0、host 与既有字段语义保持。 |
| 既有 POST /v3/ai/ard/search、GET /v3/ai/ard/agents | 保留 results/items、分页字段和现有 Entry 字段；Search 保留 score/source。响应同时符合旧、新约束，不因新版放宽必需字段而精简。 |
| 既有 POST /v3/ai/ard/explore、GET /v3/ai/ard/artifacts | 保留聚合响应/制品格式；不套用新 Manifest 封装。 |

Search/List 继续输出 displayName、type、url 等现有字段及其类型/语义，
保留 null/省略/空集合的既有序列化规则。新版 Schema 允许部分结果不等于要求裁剪字段。
新 Manifest 的 @context、额外 trust 字段及新版示例数量规则不得因公共 DTO 复用
自动泄漏到旧 catalog 或共享 Search/List；后两者继续使用兼容投影。
规范放宽允许采用同时符合两版约束的响应；无需为此新增 Search/List API 或协商参数。
不得根据之前访问过哪条发现路径，或请求是否含 @context，切换 Search/List 响应版本。

UT-C02-03、UT-C03-01、IT-C03-03、IT-C05-05、IT-C09-02 必须证明这些边界：
真实共享响应同时校验两版 Schema，并与旧版字段/序列化基线比较；仅新版校验通过不够。
旧 DTO 反序列化成功也不足以证明兼容，仍需断言旧字段没有消失或改变语义。
若后续协议必要变化确实无法保持共同响应契约，应明确记录字段变化、客户端迁移和测试；
不预设必须增加版本化/能力协商，也不为了精简响应主动破坏共同契约。
请求校验收紧、页大小等行为变更仍按D05/D10单独评审；响应模型兼容不等于全部行为零变化。

完整 Entry 必须包含 identifier、displayName、type，且 url/data 恰有一个。
Nacos 本轮继续使用 url 交付，无需为升级新增 inline data 发布 API。
Search item 必须包含 identifier、整数 score（0..100）和绝对 URI source；
List item 至少包含 identifier。新版允许省略其他字段，但 Nacos 继续返回现有选择所需字段，
避免旧客户端丢失 type/url/displayName。列表仍为 items，搜索仍为 results。

新 Manifest Entry 可输出 @context=官方基础 context URI；@id 暂不输出。
未来输出 @id 时必须与 identifier 表示同一资源。旧 Entry 无 @context 也具有默认上下文。
不自动透传任意 Nacos metadata 为顶层 JSON-LD 扩展，不导出内部索引、管理或 Runtime 字段。

## 4. 查询解析与共享检索边界

### 4.1 Context 处理

ArdSearchQuery 增加 @context，使用能保留 string/object/array 形态的 JSON 模型。
缺省值应用随版本固定的基础 expandContext，再应用请求 context。
URI 与命名空间按 JSON-LD 语义处理，不进行全文小写化或简单冒号切分。
基础 type 映射到 ns#mediaType，而不是 JSON-LD @type。
上下文重定义核心字段时以有效 IRI 为准，不能回退匹配原字段字符串。

适配器内部 ArdContextResolver 封装固定版本 Titanium JSON-LD **1.7.0**
（Apache-2.0，Java 11+，已验证JDK 17），采用 Parsson **1.1.7** 提供 JSON-P。
依赖仅位于Java 17适配器；传递的JCS/RDF工具API不引入RDF数据库或签名服务。

以下 context 加载设置属于 Nacos 部署策略，不是ARD要求的数值限制或访问限制：

| `nacos.ai.ard.context.` 下的配置 | 默认及行为 |
| --- | --- |
| `allowed-uris` | 默认空，逗号分隔的精确HTTP(S) URI允许清单。官方基础URI始终映射到固定内置文档，不联网。运维可允许其他可信文档及每个传递引用；不支持通配符、userinfo、fragment、重定向或HTTP Link跳转；不转发Nacos凭据。 |
| `max-bytes` | 单次解析累计1048576 UTF-8字节，计入inline context及每个不同的下载文档；内置base和重复缓存文档不重复计费。可配置为其他正数。 |
| `timeout-ms` | 单次解析远程获取累计3000 ms；完整响应future和请求超时使用剩余预算。可配置为其他正数。 |

文档仅在单次解析内缓存，不跨请求、配置变化或进程重启复用外部文档。
JSON-LD解析/循环错误，以及拒绝访问、不可达、非法或超限文档均显式失败；
HTTP接入后使用既有ARD 400封装，不能回退为无context查询。固定处理器还限制
远程context遍历（256），属于实现限制而非ARD协议最大值；受支持的合法远程URI必须成功。

context缺省使用base；显式整体null/number/boolean不符合QueryModel。
数组reset先处理null，再恢复固定base，之后应用后续条目；远程context顶层reset同样处理。
这协调了JSON-LD reset与ARD §4.1“base不能被移除”的要求：reset前的请求别名清除，
核心词恢复，后续定义仍可覆盖。protected term不允许reset的错误仍保留。
显式将某个term定义为null则移除该term，不能回退原字段名。
不改写term的scoped context或opaque JSON值。

UT-C04-01..04覆盖解析器与加载策略；Search/Explore 的 HTTP 场景见
[测试设计](../../../test/openapi-test/ARD_V091_TEST_DESIGN.md#context-processing)。

### 4.2 字段解析和过滤

ArdTermPathResolver 先识别完整 IRI、compact IRI 或定义的 term。
不能先按点拆分 https URI。只对解析到 metadata、trustManifest、data 的根
解释后续 literal JSON path；支持根 term 别名，例如 m.location。
JSON 成员名大小写保持不变，数组路径按任一元素匹配。

ArdQueryMapper 将有效字段映射到 Search Core：

- type/mediaType -> resourceType 与 Agent artifactKinds，并在分页前选择表示；
- version -> resourceVersion，metadata.resourceType -> resourceType；
- tags、capabilities、displayName 等 -> 已有标准 predicate；
- source -> Registry endpoint；publisher、publisherId、trustManifest.* 缺少资源级数据，明确返回400（§5.2）；
- 已支持 metadata.* -> 原有领域 facet，不建立 ARD IRI 索引。

本轮只使已有可检索字段具有正确的 IRI 语义，不增加任意外部词汇的存储与发布入口。
合法但未索引的字段/IRI与资源中缺失的字段按无值处理；非空约束无匹配。
撤回此前统一拒绝未知显式IRI的设计：§5.3.1仅允许MAY 400，本轮保留空匹配兼容。
真正context解析失败或畸形路径仍400；空数组维持各字段既有行为，不能笼统当空匹配。
保留legacy query.filters；新规范输入使用query.filter，不新增对旧扩展字段的依赖。

同一原始 key 的值使用 OR，不同 key 使用 AND。两个 key 即使解析成同一 IRI，
也不得简单并集成更宽的 OR。对单值 type 可求交；对 tags 等多值字段保留独立
predicate 分组，避免用交集错误排除同时拥有两个 tag 的资源。
标准query.filter只接受字符串/字符串数组，数组非字符串（含null）收紧为400；
这是旧协议已要求、旧实现曾宽容的输入兼容变化，需迁移说明，客户端提前改为字符串。
legacy query.filters的既有值转换、values优先和同原key合并规则保留，详见BC03/04。
不对整包HTTP请求直接启用封闭Schema校验，以免拒绝namespaceId等Nacos兼容扩展。

分页绑定、失效和恢复统一见[§4.5](#token-contract)。

### 4.3 Explore

Explore 的 query.filter 和 resultType.facets.field 共用上述 resolver。
内部聚合可以复用同一 canonical 字段，响应 facets key 保留原请求拼写。
两个不同别名可返回相同统计值；完全重复的原 facet key 保持现有处理行为。
ARD §5.3.3/OpenAPI 没有规定重复字段必须 400，本轮不增加这种拒绝。
语法合法的未支持字段/IRI与缺值字段返回空buckets，不新增统一400。
仍对完整可见且 current 的匹配集聚合，不能统计 Search 单页。

GET /agents 没有 query context，本轮保持原有 EBNF filter 与 orderBy 语法；
不把新增 POST context 语法悄悄应用于 GET filter。

### 4.4 请求与升级兼容门禁

逐项前后差异、协议强度、迁移及UT/IT见
[兼容审计](../../../test/openapi-test/ARD_V091_COMPATIBILITY_REVIEW.md)的BC01..20。
保留Search/Explore各自text规则、namespace query/body绑定、federation默认与trim、
Explore limit/minCount与重复key处理、GET filter/orderBy兼容语法；不套用其他Facade限制。
HTTP数值字符串/浮点/未知成员等converter行为须开工前真实采baseline，不能从Java类型猜400。
固定数据集才要求全页无重漏；数据、权限、索引锚点变化不承诺跨页快照。
<a id="token-contract"></a>

### 4.5 Token失效与重新查询契约

映射后的Query交给既有Core分页，复用编解码，不为版本边界主动让所有旧token失效；
不新增ARD token格式或统一查询绑定。
当前基础Search/List token只有文档锚点：改变查询但锚点仍有效时可续页，
不能承诺语义变化一律400。增强token另有relevanceV1查询/配置指纹，指纹改变才按原规则拒绝。
两种token及校验分支按BC15..18分别测试，协议前置无匹配的短路仍返回空集合。
复用当前predicate/values顺序与hash输入，不为兼容新增规范化或转换层；
若必要语义变化改变指纹，按下文失效和恢复规则处理，不承担跨版本ABI承诺。
等价前缀/context声明顺序/URI-inline得到相同Query时可续页；调换filter键或值顺序
可能改变增强指纹，不能笼统承诺所有语义等价形式都可复用token。
pageSize没有进入增强指纹，续页调整页大小继续可用；不新增TTL、签名或自动重置首屏。

[§5.3.2][s10]/[§5.3.4][list]没有规定跨版本token ABI、TTL或快照保证。
以下错误和恢复规则是Nacos适配器设计；沿用[Appendix B][errors]的400类别和当前错误封装。
请求通过鉴权、绑定和语法校验并进入分页校验后，格式不可解码、锚点失效/不可见、
增强模式指纹不符或基础/增强token混用，返回HTTP 400：

```json
{"errorCode":"INVALID_ARGUMENT","message":"Invalid discovery cursor"}
```

message示例来自当前实现；正式契约要求非空并说明cursor/token无效，不承诺文案逐字稳定。
不新增TOKEN_EXPIRED错误码、可恢复标志、TTL、版本协商、持久快照或旧token转换器。
错误响应不携带成功结果或新的pageToken；服务端不将无效token静默改为首屏或正常空结果。
若既有协议前置过滤已确定无匹配且尚未进入分页校验，仍返回200空结果/无token；
鉴权/其他请求错误保留原优先级，不能将它们重写为token失效。

客户端升级指引及IT中的恢复流程：

1. 把pageToken当不透明值；遇到已确认的无效分页状态，或主动跨版本/配置切换时，
   丢弃旧token及本次分页已累计的结果/计数，重新开始一次查询。
2. 保留原query（含context和filter）、namespace、federation、List filter/orderBy和选定pageSize，
   使用当前有效身份，省略pageToken请求首屏；不移除过滤条件、扩大权限或拼接旧结果。
3. 仅使用新响应发出的token继续；以响应是否提供token判断完成，空页/短页不一定结束。
   重开的查询反映当前数据与权限，不保证与旧分页共享快照。
4. INVALID_ARGUMENT也表示其他请求错误，不能把任意400一律自动重试，更不能仅匹配message。
   本轮不新增公共SDK自动恢复逻辑。若调用方选择自动恢复，至多重新开始一次；
   重开失败或再次续页失效就停止并暴露错误，核查请求、路由与配置，禁止循环重试。

正式UT/IT-C05-14覆盖有效对照、确定失效、原条件重查及不会盲目重试其他400。
同版无变化的正常token不能被“允许跨版失效”掩盖；升级前已发token可自然续用，
但跨版存活不作承诺。旧服务器的历史错误行为由实际版本决定，本设计不要求回改旧发行包。

跨版本token复用不作为交付门禁，真实旧->新/新->旧/交替节点仅做可选迁移演练，
环境缺失不阻塞本版一致性验收；必须验证本版正常续页、确定失效及重新查询流程。
context请求须等目标节点全部升级或路由到升级池后启用；回滚前停止新语义请求，
清空分页状态并在目标版本重新查询，不把带context的请求静默降级后发给旧节点。

## 5. 发布质量、身份与兼容

### 5.1 Representative queries

ARD §4.2/Appendix D.2 将 2..5 定义为建议，缺失、1 条或 6 条都不应导致新版 Schema 失败。
新版完整 Entry 保留实际有效示例并按数量诊断，不为符合协议而截断 6 条为 5 条。
Legacy catalog 为满足旧 Schema，稳定选择最多 5 条，不足 2 条省略；
现有 Search/List 继续使用兼容投影。此选择属于 Nacos 兼容策略，不是新版 ARD 要求。
共享索引的完整内容不变，不编造示例凑数。
为 Registry Entry 配置两个真实描述其检索能力的静态示例。
数量不足是告警，不影响资源可见性、online 状态或检索资格。
投影层保持原始字符串及顺序，不新增空白过滤或去重规则；既有资源projector的处理不变。
这不是新增资源Manifest路由：公开新版host入口仅Registry，其他完整Entry投影通过组件测试验证。

### 5.2 Publisher 与 trustManifest

ARD 的绑定单位是单个 Entry：trust identity 的 authority 对应同一条目的 publisher。
Registry 自身是一个独立资源，收录资源不会继承它的发布方或信任声明。
当前 Nacos 尚未提供贯通各资源类型的发布方授权、资源级 trust 存储和查询索引。
owner、版本 author、Agent provider、请求 Host 和制品 URL 均不作为发布方身份来源。

`nacos.ai.ard.registry.publisher` 只指定 Registry 自身 Entry 的 publisher，
显式配置时必须是 ASCII FQDN（可用 punycode），统一为小写并去掉一个末尾根点。
它不是访问地址，不校验 DNS 存在或域名控制权，也不会改变普通资源的 identifier。
未配置 publisher 且未配置 trust 时，Registry 保留旧标识以兼容既有部署，
不因此声称默认 `nacos` 满足 ARD 的 FQDN 语义。

普通资源暂时保留原有 identifier 编码，包括 `nacos.ai.ard.catalog.host.identifier`
历史前缀。这是兼容标识，不是已经建立的真实资源发布方记录；不应将该配置用于给资源声明发布身份。
不要在升级时改变此前缀，否则旧资源标识会变化。资源发布方登记、旧 URN 迁移及缺少发布方时的
严格 ARD 导出仍待支持；本适配器不声称已完成完整 publisher-authority 合规。

`nacos.ai.ard.catalog.trust.identity` 和 `identity-type` 仅描述 Registry 自身。
identity 未配置或空白时省略 Registry 的 trustManifest；配置 identity 必须同时显式配置
registry.publisher，不回退到旧资源前缀。普通资源始终不从这些设置复制 trustManifest。

| 身份形式 | 对 Registry 声明进行的离线检查 |
| --- | --- |
| HTTPS URI | host 与 Registry publisher 对应；拒绝 user-info、无效 host/端口。 |
| SPIFFE ID | 小写 trust-domain authority；拒绝 user-info、端口、query、fragment。 |
| did:web | 第一个 method-specific 段中的 ASCII DNS 域名（含 punycode），不接受 IP；可选端口以 `%3A` 分隔，后续可有路径段。 |
| 其他 scheme 或 DID method | 保留为未验证声明，不因为未知而拒绝或标为已验证。 |

HTTPS/did:web 比较忽略大小写、一个末尾 DNS 根点和有效端口；SPIFFE trust-domain 保留点号。
不作后缀/子域匹配、DNS查询、远程 DID 解析、委托推断或证书/签名验证。
identity-type 只是提示，不能绕过已识别 identity 的检查；authority 相等不证明所有权。

Registry publisher 无效、配置 identity 却未显式配置 publisher、已识别 identity 畸形或
冲突时，包含 Registry 声明的 Manifest、host Catalog 和 namespace Catalog 返回
HTTP500 / INTERNAL_ERROR。namespace Catalog 包含 Registry Entry，因此也拒绝导出这个
不一致的文档；不悄悄删除声明后返回部分成功。服务仍可启动，认证401优先。
普通 Search、List、Explore、原始 Artifact 和主服务资源管理不读取这些 Registry 声明，
在正常请求和权限条件下继续工作，包括空结果。此500是服务端配置错误，不是 ARD 强制状态码。
修正配置并重启可恢复 Registry 文档；回滚不等于信任验证成功。

资源 publisher/publisherId 和 trustManifest 路径尚无权威数据来源，Search/Explore filter、
Explore facet 及旧 List filter 使用这些维度时返回 HTTP400 / INVALID_ARGUMENT，
包括别名、完整 IRI 和旧 query.filters；不以全局常量伪造匹配或统计。
source 仍表示 Registry endpoint，支持过滤和聚合。普通资源缺省 trust 不影响自身查询资格。

新版 Registry Manifest 保留非空自定义 identity-type；旧 Registry Catalog/host 提示保持
spiffe、did、https、other 四值兼容规则。开放 trust envelope 仍支持框架扩展，
但不新增资源 trustSchema/attestation 管理 API。Search score 始终仅为语义相关度。
资源声明的授权、准入拒绝、版本/digest 关联、独立验证结果和框架验签是后续能力，
不能通过全局配置或任意扩展 JSON 自动获得。依据 [ARD §4.5.1–4.5.2][s08] 与 [Appendix C][urn]。

固定上游 EntryFields Schema 使用大写 TrustManifest，正文/context 使用小写。
保留原始 fixture 并独立校验小写成员；Schema 通过不证明 authority、所有权或签名有效。

### 5.3 URI、媒体类型与分页

Registry Entry url 保持 base URL，客户端追加 /search；
referral url 若未来生成，必须是完整 Search URL，不可再次追加。
source 保持当前 registry 绝对 URI。Artifact 保持适配器归属、精确版本、digest、
representation 与 offline 失效行为。

保留 MCP Server Card、A2A Card、Skill ZIP 和 Nacos vendor media types。
合法但本地无资源表示的 media type filter 返回空集合；不能因为上游列出某类型
就宣称 Nacos 支持导出该 artifact。保留已有多段 URN 和 Unicode 无碰撞编码。

Search pageSize 默认 10、最大 100；List 默认 20、最大 100。
省略、非正数和超过上限继续使用现有归一化策略：缺省/非正数回到默认，超限截至最大。
实际不可解析的JSON/参数须受控处理；数值字符串/浮点等既有绑定宽容先按BC14采baseline，
不一律新增400。Search 51..100由截50变为按新上限返回，需在迁移说明中披露；
需要保留最多50条的客户端升级前显式设置50。不能用短页判断结束，空页也可携带续页token。

## 6. 验收矩阵

验收包括正确性 UT、独立服务器 HTTP IT、部署组合与既有功能回归。
正式用例、数据、断言、测试类和编号见
[ARD v0.91 测试设计](../../../test/openapi-test/ARD_V091_TEST_DESIGN.md)。
错误预期遵循公开的运行时契约；场景和覆盖记录区分已实现、已执行及尚待完成的用例。
Controller mock 不替代 HTTP 证据。

| 场景 | 最低验收 |
| --- | --- |
| A01 基线 | 新旧 fixture 原始哈希、离线引用解析、版本标识分离；Schema/context 不在线下载 main。 |
| A02 发布 | 新 host 与旧 host/namespace 三个入口；host 仅 Registry，namespace 跨 100 条完整枚举。 |
| A03 路由鉴权 | 独立端口、ARD-only 开关、显式无效凭据 401、匿名策略、不同身份可见性。 |
| A04 Entry | 完整/部分两种约束；url/data 排他；没有 score/source 泄漏；扩展属性与小写 trust 校验。 |
| A05 Context | 缺省/URI/object/array、term 与 prefix 别名、完整 IRI、重定义、失败及限额。 |
| A06 Path | metadata/trust 根别名、literal 子路径、数组、大小写、URI 中的点、无值与不支持字段。 |
| A07 Boolean | scalar/array、同 key OR、跨 key AND、别名碰撞、多值 tag 与单值 type 的不同处理。 |
| A08 Cursor | 基础锚点/增强指纹、同版正常续页、确定失效400与原条件重查；等价映射、改页大小、空页；跨版复用不设门禁，混跑演练可选。 |
| A09 Explore | 别名 facet 输出、重复/未知字段、空 buckets、完整集合统计和 Search 基础匹配集一致。 |
| A10 Query examples | 0/1/2/5/6 条、去重、legacy/new 区别、缺失不隐藏资源、Registry 示例。 |
| A11 Trust | 缺失 identity、authority 匹配/冲突/未知、框架开放性；仅含Registry的文档拒绝无效Registry身份并返回500；普通查询不继承trust且保持可用，不支持的资源身份维度返回400，新旧提示投影兼容。 |
| A12 URI/media | base URL 与 search endpoint、扩展媒体类型、多段/Unicode URN、稳定 identifier。 |
| A13 分页 | Search 10/50/51/100/101、0/负数/错误类型，List 边界和 namespace 过滤。 |
| A14 Artifact | 四类非空资源；纯 A2A/多协议/旧版本仅 A2A；ZIP、版本/digest、offline、Runtime 排除。 |
| A15 历史回归 | 无 context 旧请求、旧 catalog Schema、所有 federation 值、协议错误体、ARD 关闭不影响 RAD。 |
| A16 契约校验 | 现有 Java UT 与 HTTP IT 共用固定 Schema；缺失、篡改、未知引用及非法 URI/date-time 明确失败。上游工具的局部结果不代表完整能力。 |

Java 契约测试验证结构、格式和协议语义；真实 HTTP IT 从 well-known 的 Registry 地址
查询非空公共 Agent，并读取返回的 Artifact URL，对照主资源 API 的名称、版本和 digest。
外部请求助手不继承主服务管理员凭据；鉴权 IT 使用受目标约束的适配器请求和专用测试身份。

上游工具仅用于本地独立对照，使用固定版本的完整上游目录，不修改其内部校验或 HTTP 行为。
manifest、registry 和 publisher 的结果分别记录；缺依赖或 Schema 导致跳过时不算通过。
publisher 模式需要 HTTPS 域名；受控 resolver fixture 仅证明该测试环境，
localhost 直连结果不代表公网 publisher 验证。

ARD 提供 HTTP 发现接口；资源写入继续使用已有的主服务 API 和 SDK。

## 7. 支持限制与后续能力

- 私有 MCP 的共享索引完整召回、MCP Artifact 的 canonical 名称与 UUID 映射仍待完善。
  DAUTH-F03 保持 Disabled；公共 Agent 验证不覆盖这些缺口，不代表完整资源一致性验收。
- 旧、新上游均要求 Search 与 Explore 使用一致 relevance cutoff。
  当前可选 Jev 增强只作用 Search，属于已有偏离；严格基础一致性测试使用默认关闭状态。
  要把增强模式纳入同一兼容声明，须单独设计完整集合过滤及成本预算，不能拿 Search 单页算 facets。
- 外部 Web Ingestion 包括 manifest 和页面 JSON-LD；未来需定义源管理、抓取限制、
  provenance、外部资源生命周期和权限，完成前不宣称完整 Registry conformance。
- 联邦后续需设计 upstream 配置、auto 合并/去重/分页、referrals、循环与跳数、
  共享截止时间、部分失败和凭据隔离；现阶段无 upstream 时保留本地模式和空 referrals。
- 自定义命名空间持久投影及完整 trust framework 属于独立后续设计，不因 DTO 开放自动支持。
- W3C Trace Context 属于上游 informative 建议，可独立增加，不作为本轮发布门禁。

## 8. 来源

- [上游规范](https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md)
- [Entry Schema](https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard-entry.schema.json)
- [OpenAPI](https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.openapi.yaml)
- [基础 context](https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.context.jsonld)
- [Conformance](https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/conformance/README.md)
- [AI 资源检索规范](ai-resource-search-spec.md)
- [API IT 规范](../testing/api-integration-test-spec.md)
- [兼容策略](../design/compatibility-deprecation-spec.md)
- [Titanium JSON-LD处理器](https://github.com/filip26/titanium-json-ld)

[s01]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L546-L588
[s02]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L206-L218
[s03]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L96-L124
[s04]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L88-L94
[s05]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L231-L268
[s06]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L347-L410
[s07]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L565-L570
[s08]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L183-L197
[s09]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L43-L59
[s10]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L270-L301
[urls]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L227-L229
[urn]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L534-L544
[list]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L412-L423
[errors]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L524-L532
[runner]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L579-L588
[ingest]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L220-L224
[federation]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L431-L497
[context]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.context.jsonld
[query]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.openapi.yaml#L126-L188
[results]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.openapi.yaml#L264-L349
