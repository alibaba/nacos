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

# ARD v0.91 功能测试设计

本文同时列出已实现测试和待完成的验收设计；各功能的覆盖范围见§4。
覆盖等级由实际测试场景决定，不能由设计行数推导。
运行时规范见 [中文规范](../../specs/zh-cn/ai/ard-protocol-spec.md)。
所有上游引用固定到 f073b1ea9b01a5f068d0b0986426f2ea49fa30b2。
本文件是全部正式用例、共用fixture/错误断言和RG回归组的唯一维护位置；
用例按功能编排，保留已有测试 ID，便于关联代码、兼容审计和历史验证。
修改前代码事实与兼容影响见[兼容审计](ARD_V091_COMPATIBILITY_REVIEW.md)（BC01–BC20）；
运行时契约以双语 ARD 规范为准。

<a id="test-gates"></a>

## 1. 测试范围与环境

测试覆盖正常响应、空集合、边界与预期错误。用例描述前置数据、输入、断言、清理和兼容影响；
错误预期来自协议和公开的 Nacos 部署策略，不把协议允许的输入自行列为错误。
HTTP 契约由外部 standalone IT 验证；内部解析、索引和受控故障由 UT/组件测试覆盖。
离线 Schema 校验与 HTTP 验证分别记录，不能互相替代。

IT 使用独立服务器，不在测试类中启动 Spring/Nacos。鉴权配置以仓库 it-new.yml 为准。
适配器响应使用 ARD envelope，不使用 Nacos Result<T> 断言。

<a id="protocol-sources"></a>

## 2. 协议依据索引

| 来源 | 固定章节/定义 | 约束与测试用途 |
| --- | --- | --- |
| S01 | [Appendix D.1–D.4][s01] | 独立 Schema、工具和版本基线；OpenAPI info.version 不能代替规范 pin。 |
| S02 | [§5.1][s02] | Consumer MUST 解析 ard.json/rel=ard；发布说明指向新入口。旧路径回退为 MAY。 |
| S03 | [§4.2–4.3][s03]、[ArdEntry/ArdManifest][schema] | 完整 Entry 的必需字段、url/data 排他、根 entries；额外字段开放。 |
| S04 | [§4.1][s04]、[基础 context][context] | base expandContext，context 可省略；type 对应 mediaType，允许命名空间覆盖。 |
| S05 | [§5.3.1][s05]、[QueryModel][query] | IRI 匹配、opaque 子路径、同 key OR/跨 key AND；未知路径 400 是 MAY。 |
| S06 | [§5.3.3][s06]、[ExploreFacetRequest][facet] | 可选 text/filter、同字段路径语法、全量聚合、同 relevance cutoff。 |
| S07 | [§4.2][s03]、[Appendix D.2][s07] | 代表性查询建议 2..5；缺失/超范围是 warning，不是 schema error。 |
| S08 | [§4.5.1–4.5.2][s08]、[TrustManifest][schema] | identity、publisher authority 绑定；具体验证交给框架，不指定 DID 白名单。 |
| S09 | [§3.3][s09]、[§5.3][urls]、[Appendix C][urn] | 开放媒体类型、base/referral URL 区别、FQDN/multi-segment URN。 |
| S10 | [§5.3.2][s10]、[§5.3.4][list]、[SearchResultItem/ListResponse][results] | Search 10/100、List 20/100，结果投影与分数/来源；不规定非正数处理。 |
| S11 | [Appendix B][errors]、[Appendix D.4][runner] | 400/401/404/429/500 协议错误与官方 runner 模式。 |
| S12 | [§5.2][ingest]、[§5.4–5.5][federation] | Required 抓取、联邦三模式、informative tracing；决定范围声明而非新增行为。 |

与 Nacos 自身规则相连的部分还以
[适配器规范](../../specs/zh-cn/ai/ai-registry-adaptor-spec.md)、
[共享检索规范](../../specs/zh-cn/ai/ai-resource-search-spec.md)、
[API IT 规范](../../specs/zh-cn/testing/api-integration-test-spec.md)为依据。
namespaceId、精确 artifact version/digest、匿名访问、ARD 开关、旧 query.filters、
cursor 指纹和副作用清理均为 Nacos 约束，不冒充 ARD 条款。

<a id="test-data"></a>

## 3. 可复用数据及断言

- F-BOOL：同一 namespace 下三个公开、enabled、latest online Skill：
  S_AB 带 tags=[a,b]，S_A 带 tags=[a]，S_B 带 tags=[b]。
  三者描述包含同一唯一查询标记。UT 固定召回三个候选；IT 使用关键词模式并等待三者可见。
  复用 draft/force-publish，使用现有 PUT /v3/admin/ai/skill/biz-tags 设置 JSON 数组，
  参考 SkillAdminApiOpenApiITCase 的 skillBizTagsForm；等待新 tags 投影可检索后再断言。
- F-TYPE：复用现有发布 helper，建立纯 A2A Agent、多协议 Agent、
  latest 为 custom 而旧 online Version 支持 A2A 的 Agent，以及 Skill、Prompt、MCP。
  全部带同一查询标记；不要通过直接写 search table 准备 IT。
- F-AUTH：公开资源、显式授权的私有资源、无权限私有资源、不同 namespace 的同名资源。
  身份为匿名、合法读取身份、已认证但无权限身份、显式坏凭据；私有索引 DAUTH-F03 已恢复为实际 HTTP 场景；MCP Artifact 名称/UUID 缺口仍独立记录。
- F-PAGE：至少 105 个确定性公开候选，同一唯一标记；通过公开 API 创建及等待索引。
  UT 固定排序/分数；IT 比较完整 identifier 集合、无重复和稳定续页，不依赖任意排名分数。
- F-SCHEMA：最小合法完整 Entry、仅 identifier 的 List item、
  identifier+score+source 的 Search item，以及逐字段负例；分别应用对应 schema。
  这些最小投影仅用于验证新版validator，不是现有Search/List的目标输出。
  另取同一资源的现有完整响应校验两版Schema，作为共享endpoint的兼容样本。
- 每个 IT 使用唯一名称/namespace，finally 清理；缓存/异步索引使用有界轮询，
  失败输出预期集合与实际集合，不使用固定 sleep 掩盖问题。
- E400：HTTP 400，errorCode=INVALID_ARGUMENT，message 非空，无 Result code/data。
  E400-T为token失效场景别名，wire错误码仍是INVALID_ARGUMENT；无成功结果或新token。
  具体契约见[ARD 规范§4](../../specs/zh-cn/ai/ard-protocol-spec.md#token-contract)，
  参数化输入及可控恢复步骤见[Search分页组](#token-cases)，不把任意400当token错误。
  E401：HTTP 401，errorCode=UNAUTHENTICATED；E404：404/NOT_FOUND。
  不匹配合法过滤条件是 200 空集合，不能作为 E400。错误消息不锁死随机资源名或堆栈。
- 429/500 由异常映射 UT/组件验证，除非已有确定性的外部触发手段；不故意破坏真实存储做 IT。
- before/after 只归一化测试名称、运行时间、部署 origin 等环境值。
  不归一化 type、资源集合、错误码、版本、digest、cursor 行为或待测字段来掩盖变化。

关键 Search 请求模板（外层均为 query，text=<唯一查询标记>；每个案例重新创建独立数据）：

| 对应案例 | query 中的其余成员 | after 精确预期 | before 记录 |
| --- | --- | --- | --- |
| UT-C05-01 / IT-C05-02 | `"filter":{"tags":["a","b"]}` | S_AB、S_A、S_B | 既有 OR 应保持 |
| UT-C05-01 / IT-C05-02 | `"@context":{"t":"https://agenticresourcediscovery.org/ns#tags"},"filter":{"tags":["a"],"t":["b"]}` | 仅 S_AB | 原无别名语义，记录实际空集/拒绝，不假定旧版正确 |
| UT-C05-02 / IT-C05-01 | `"@context":{"m":"https://agenticresourcediscovery.org/ns#mediaType"},"filter":{"m":["application/a2a-agent-card+json"]}` | F-TYPE 中仅当前可导出 A2A 的逻辑 Agent；与裸 type 等价 | 比较旧裸 type 的正确集合与旧别名的实际行为 |
| UT-C05-04 / IT-C05-04 | `"filter":{"tags":["a",1]}` | E400，禁止把数字转为字符串 | 记录旧转换行为；这是明确允许改变的输入 |
| UT-C05-03 / IT-C05-05 | `"filter":{"metadata.missingField":["x"]}` | 200，results=[] | 既有合法缺字段保持 |

分页测试先用同一请求取得非空pageToken，再只替换等价context；改变filter后分别验证
基础锚点是否仍有效、增强指纹是否变化，不再要求两者一律400。详见BC15..18；
不能用伪造token代替真实续页和可控失效；原条件重新查询属于必选正式测试，
跨版本token复用不作保证；升级/回滚说明及本版恢复测试必选，真实双版本演练可选。
无公开 API 可设置的深层 trust/data/示例数量由 UT 输入受控模型；IT 不直接写库。

<a id="formal-cases"></a>

## 4. 正式 UT 与 IT 设计

每行可展开为参数化案例；UT/IT/DOC ID 是本文件定义的稳定测试标识，
不代表交付阶段，也不表示存在同名 Java 方法。未决策略必须注明具体缺口，不能先写成强制失败预期。

| 功能 | 当前测试范围 |
| --- | --- |
| 基线、Schema、发现与示例发布 | 已有离线契约、renderer、路由/鉴权 UT 和公开资源 HTTP IT。示例的精确数量组合由组件测试验证，公开 API 可能派生额外示例。 |
| Context 解析 | `ArdContextResolverTest` 覆盖固定 base、IRI、reset 和受控 loader 成功/错误；loopback loader UT 不计为 API IT。 |
| Search | `ArdQueryMapperTest`、服务 UT 及 `ArdAdaptorOpenApiITCase` 覆盖 context/过滤、绑定兼容、namespace 和分页恢复。增强模式另需确定性 provider 环境；普通部署不代表增强模式已执行。 |
| Explore | 服务 UT 和 HTTP IT 覆盖 query/facet 别名、独立选项与完整集合统计。 |
| 分页 | 服务 UT 与 105 条资源 HTTP IT 覆盖 Search 100 上限、List 既有规则、续页与原条件重查。 |
| Trust | `ArdTrustIdentityValidatorTest`、服务/契约 UT 与部署参数化 HTTP IT 覆盖 authority 一致性、开放结构、未知框架、配置错误及新旧投影。 |
| 最终一致性验收 | 完整一致性验收仍待完成；`DAUTH-F03` 已恢复，私有 MCP 索引与权限由专项 HTTP 场景验证，MCP Artifact 仍未修复。 |

HTTP 场景及覆盖限制统一见[场景索引](AI_REGISTRY_ADAPTOR_API_TEST_SCENARIOS.md)。
设计、实现和执行分开记录；具体运行结果以对应提交的测试报告为准。

<a id="baseline-design"></a>

<a id="schemas"></a>

### 基线与 Schema

| ID | 前置与输入 | 应提交的断言 | 修改前 → 修改后 |
| --- | --- | --- | --- |
| DOC-C01-01 | 双语设计、pin、章节链接和案例 ID | 文档检查；无运行时变化，不新增镜像文档内容的 UT/IT | 无逐项链路 -> 有 协议来源、差异和测试的对应关系 |
| UT-C02-01 | 新旧原始 fixture、脱网 resolver | SHA-256/来源匹配，引用本地闭合；篡改或缺少引用明确失败，不下载 main | 只有旧 fixture -> 两版独立可验证 |
| UT-C02-02 | F-SCHEMA：完整 Entry 与 entries 容器 | 最小合法通过；identifier/displayName/type 缺失、url/data 同时/均无失败；空 entries 合法 | 新 Schema 无覆盖 -> 完整模型有正负例 |
| UT-C02-03 | List 仅 identifier；Search 额外 score/source | 部分结果通过；Search 缺少 score/source、score=-1/101/1.5、相对 source 失败；List 缺 identifier 失败 | 旧全 Entry 约束 -> 分别应用新版结果约束 |
| UT-C02-04 | context/扩展字段、trustManifest 小写、额外 trust 成员 | 新版扩展字段合法；缺 identity 的小写 trust 由独立语义校验报错；原上游 fixture 不修改 | Schema 大小写漏洞 -> 显式补充语义证据 |

UT-C02-03另验证共享响应契约的交集：现有完整Search/List响应在两版Schema均通过；
从响应中删掉displayName/type/url等旧必需字段的样本，新版可能通过而旧版必须失败。
此负对照防止把“新Schema合法”错误等同于“旧客户端兼容”。

依据 S01/S03/S08/S10/S11。优先扩展 ArdOpenApiContractTest 和现有 schema helper。
离线 Schema 不新增 HTTP endpoint；发现入口和 Search 的 IT 使用同一验证器校验真实响应，不伪造 HTTP 测试。

<a id="discovery"></a>

### 发现入口及旧封装

| ID | 前置与输入 | 应提交的断言 | 修改前 → 修改后 |
| --- | --- | --- | --- |
| UT-C03-01 | host/namespace 投影，F-TYPE | 新 Manifest 的 entries 都是完整 Entry；host 仅 Registry；Search score/source 不泄漏；旧 root 仍 specVersion=1.0 | 只有旧 catalog DTO -> 新旧封装分离 |
| UT-C03-02 | 新 Controller operation、ARD 开关 | 新入口继承条件装配、ArdApi 和认证登记；关闭时不装配；不复制 Search Core | 无新路由 -> 正确进入同一上下文 |
| IT-C03-01 | GET /.well-known/ard.json，follow Registry url + /search | 200、新 schema、绝对 base URL；实际 POST Search 成功，不出现 /search/search | 旧基线无该路由 -> 新入口可发现可用 |
| IT-C03-02 | 新入口，鉴权开启，显式坏凭据；匿名开/关两种配置 | 坏凭据始终 E401；允许匿名时成功，不允许时缺凭据 E401；与旧路由的认证语义一致 | 无新入口 -> 有受保护新入口 |
| IT-C03-03 | 两条旧 ai-catalog.json 路由，F-TYPE/F-PAGE | 继续 200，旧 Schema/host/specVersion 保持；namespace 超 100 完整枚举，当前身份不可见资源不出现 | 既有成功 -> 相同既有成功 |

UT-C03-01与IT-C03-03同时冻结旧字段的名称、类型、null/省略/空数组规则；
序列化新版Manifest后再取旧响应，证明共享模型不会带入@context、额外trust成员
或新版示例数量规则。旧catalog不换ArdManifest；不因上下文缓存或调用顺序改变响应版本。

依据 S02/S03/S04；保留旧路径和 namespace 规则是 Nacos 兼容要求。
复用 ArdWellKnownControllerTest、ArdSearchControllerTest、ArdWebAuthenticationTest、
ArdSearchServiceImplTest 及 ArdAdaptorOpenApiITCase。
新增 namespace ard.json 并非协议要求，已经移出必做；不能把它的缺失算升级失败。

<a id="context-processing"></a>

### Context 解析与 HTTP 集成

| ID | 前置与输入 | 应提交的断言 | 修改前 → 修改后 |
| --- | --- | --- | --- |
| UT-C04-01 | 缺省 context、官方 URI、inline prefix、等价 context array | type 与 a:mediaType/full IRI 解析成同一 ARD IRI，核心词未丢失 | 未解析 -> 标准上下文解析 |
| UT-C04-02 | a 和 b 同指 ARD ns；type 被重定义为另一 namespace | 前缀拼写不影响 IRI；重定义不得仍命中原 type；IRI 大小写/URI 点号不被误改 | 字面字段 -> IRI 身份 |
| UT-C04-03 | query.@context=123、整体 null、JSON 语法错误、非法 term definition | 前两者不符合 QueryModel 类型；非法 context 明确解析错误，不回退无 context；HTTP 错误由 IT-C05-04 验证 | 可能忽略未知输入 -> 可验证的语义错误 |
| UT-C04-04 | 本地受控 context loader，解析成功/引用环/找不到文档 | 成功解析；环和实际解析失败受控，不能吞错；资源限额和网络策略按 ARD 规范§3验证边界 | 无 loader -> 有可测试加载边界 |

依据 S04/S05 和上游 JSON-LD 语义；加载策略见双语 ARD 规范§3。
以下用例覆盖解析器以及 Search/Explore 的 HTTP 集成边界。

| 所属ID | 补充输入/清理 | 精确预期 |
| --- | --- | --- |
| UT-C04-01/02 | 数组中alias→null→后续alias，远程顶层同样reset；protected term→reset；term=null | reset移除旧alias并恢复base，后续alias可用；protected reset失败；显式term=null不回退 |
| UT-C04-04 | 测试自建loopback HTTP文档，精确allowlist；嵌套相对引用；重复URI；下一请求改变文档 | 允许的URI/相对引用成功；单次只下载一次，下次重新加载；测试finally停止server，不访问公网 |
| UT-C04-04 | 不在allowlist、不同path、file URI、userinfo、fragment；302到未允许地址、Link头 | 拒绝且不跟随；不传播请求凭据；官方base始终离线 |
| UT-C04-03/04 | 非法JSON、缺@context、非法term、直接/间接循环、404/连接或读取失败 | IllegalArgumentException可由ARD handler映射400，不能空匹配/静默忽略 |
| UT-C04-04 | 自定义小字节预算，UTF-8边界N与N+1；inline+remote累计；重复URI不重复计费 | N成功，N+1失败；默认1048576是部署值不是协议oracle；连接/读取超时映射受控错误 |
| IT-C05-01/04/09、IT-C06-02 | 实际POST含缺省、官方URI、inline、array/reset、重绑、term=null、整体null/123、无授权URI | 正确查询非空/重绑后空结果/非法输入ARD400分别断言，响应不泄漏context；清理创建资源 |
| IT-C05-09（受控环境） | standalone显式允许本地fixture HTTP URI及传递URI；切换错误正文/302/超限/不可达 | 真正非空成功与ARD400错误均验证；外部fixture server独立启动和清理，不在IT类启动Nacos |

预算和 allowlist 属于 Nacos 部署策略，不能说成 ARD 禁止任意 HTTPS 或数组 null。

<a id="search"></a>

### Search 语义

升级前 HTTP 基线（2026-11-04，旧服务器81fcef03f）：真实HTTP表明POST pageSize数字字符串、
小数、null/0/负数可绑定，boolean/object/超int范围400；text数字/boolean转字符串，null/array/blank400；
未知成员忽略，重复text以后值为准。federation null/blank/trim沿用auto，AUTO400。
旧context被忽略，标准filter数组数字/boolean/object/null曾接受；这两类按BC03/05明确升级。
Explore query缺省/空/blank均200；无效凭据先于坏JSON返回401。
GET pageSize非整数/boolean/超范围当前500（不是400），空串使用缺省；这是既有错误映射缺口，
该兼容行为保持不变。namespace对照需有效身份，匿名跨namespace401
不能误记为绑定失败。测试记录不包含凭据。

Search使用原始key+解析字段标识合并组，同组OR，组间AND；单值type选择逐组求交，
多值tags保留独立predicate。标准filter严格string/string[]，legacy沿用旧转换与values优先。
Search 与 Explore 复用查询/Facet 解析。下表定义 Search 场景；
基础/增强 token 必须分别经过真实 Core 和 HTTP 续页及恢复验证。

| ID | 前置与输入 | 应提交的断言 | 修改前 → 修改后 |
| --- | --- | --- | --- |
| UT-C05-01 | F-BOOL：tags=[a,b]；tags=a AND alias(tags)=b | 单 key OR 命中三者；跨 key AND 只命中 S_AB，不能并成 OR 或错误值交集 | 字符串处理 -> 正确别名分组 |
| UT-C05-02 | F-TYPE：type=skill AND alias(type)=mcp | 空集合；多值 type 单 key OR 保留对应两类；表示选择在分页前 | 原 type 过滤 -> 别名等价过滤 |
| UT-C05-03 | m 指向 ARD metadata，m.resourceType=skill；t 指向 trustManifest | 根按 IRI、后缀 literal；嵌套数组按 any；缺失值为空；非 JSON 根的 IRI 中点不拆成路径 | 裸路径 -> 根别名仍正确 |
| UT-C05-04 | 标准filter数字/对象/非字符串数组、metadata..x、未支持扩展IRI；legacy对照 | 标准值类型/畸形path报错；合法未知IRI非空条件空匹配；legacy数组转换保持 | BC03收紧标准数组并给迁移说明，BC06撤回未知IRI统一400 |
| UT-C05-06 | BC02/10；Search/Explore各类空text、federation缺省/blank/trim/大小写 | 保留Search必填与trim、Explore可省略；blank模式归auto、AUTO非法，合法三模式同本地结果 | BC02/10；前后差异见兼容审计，正常场景对照保持 |
| UT-C05-07 | BC03；标准filter字符串/数组、数组1/true/object/nested/null、标量非字符串 | 标准合法保持；混合数组由强转/丢null改E400；legacy数组保留旧转换；错误不影响其他合法请求 | BC03；前后差异见兼容审计，正常场景对照保持 |
| UT-C05-08 | BC04；filter+filters同/不同key、values优先/null条目、context重绑与别名碰撞 | 无context同原key合并OR；跨key AND；legacy fieldPath不被context重绑；重绑后不错误合并约束 | BC04；前后差异见兼容审计，正常场景对照保持 |
| UT-C05-09 | BC05/06；无/合法/非法context，已知IRI、未知IRI/未知裸路径、畸形path | 已知别名匹配；语法合法未知非空条件无匹配，不一律400；真正解析/路径错误受控；加载限制按 ARD 规范§3验证 | BC05/06；前后差异见兼容审计，正常场景对照保持 |
| UT-C05-10 | BC07；type/tags/source/trust条件[]、空字符串、值大小写/空白、大小写不同IRI/成员 | 冻结字段各自空数组语义；值比较保持；IRI身份/JSON成员不被lowercase；只支持已有可检索投影 | BC07；前后差异见兼容审计，正常场景对照保持 |
| UT-C05-11 | BC08/09/20；Controller namespace绑定、原有mapper范围与错误映射 | query/body匹配规则保持；局部validator不影响全局；400/401/404/429/500映射与空结果分别断言 | BC08/09/20；前后差异见兼容审计，正常场景对照保持 |
| UT-C05-12 | BC15/18；真实Core生成基础token，改变query但保留锚点/移除锚点，非法/空白token、前置无匹配 | 保留锚点可续页，失效E400；空白首屏；前置无匹配仍空200；证明不新增查询hash或自动重置 | BC15/18；前后差异见兼容审计，正常场景对照保持 |
| UT-C05-13 | BC16；现有AiResourceSearchRelevanceTest确定性provider；指纹字段、仅limit变化、prefix等价、filters重排 | 保留relevanceV1计算；limit变化可续；等价映射可续；指纹/配置改变和模式混用400；空页推进与fallback保持 | BC16；前后差异见兼容审计，正常场景对照保持 |
| UT-C05-14 | BC17/18/20；复用现有Core和ArdExceptionHandlerTest，确定无效格式/缺锚点/增强指纹不符及有效token、其他参数/鉴权错误对照 | 失效映射E400-T，无成功结果/new token；有效样本不被重置；其他400/401不误标成可恢复token错误。替代原跨版ABI断言，不新增编码桥接测试 | BC17/18/20；前后差异见兼容审计，正常场景对照保持 |
| IT-C05-01 | F-TYPE：plain type、inline a:mediaType、官方 context 数组/full IRI | 各请求 200，identifier 集合及表示相同且非空；不能只比较成功码 | 别名未正确生效 -> 与规范字段等价 |
| IT-C05-02 | F-BOOL：上述两种 OR/AND 请求、合法不匹配 type | 返回精确预期集合；合法无结果为 200/results=[] | 别名逻辑不可用 -> 可验证逻辑 |
| IT-C05-03 | 同查询分页、等价前缀；随后改filter；基础/增强分别部署 | 相同映射Query可续页；基础改查询按锚点有效性，增强按指纹；不强加统一400 | 保留两种token契约，按BC15..18细化 |
| IT-C05-04 | 缺 query、缺/空 text、错误 context 类型、非法 JSON/过滤值、未知 federation | 全部 E400，无 Nacos Result；适用的正常对照请求同时成功 | 保留原校验并加入新版参数验证 |
| IT-C05-05 | legacy query.filters、未知裸路径、无 context 普通 Search | 既有语义保持；无 context 标准请求与新版等价请求集合一致 | 既有成功/空匹配 -> 保持 |
| IT-C05-06 | 同上经实际POST；正常唯一marker对照 | 正确非空、Explore全量、必填错误E400；不是只测DTO赋值 | 前后差异见兼容审计，正常场景对照保持 |
| IT-C05-07 | 原始JSON发送上述数组；同资源分别数值1和字符串"1"、legacy filters | 升级前记录数值转换，升级后标准数组E400、字符串成功、legacy保持；不要由客户端SDK预先转字符串 | 前后差异见兼容审计，正常场景对照保持 |
| IT-C05-08 | F-BOOL；两个入口组合、只传legacy、共同key冲突和等价alias | 精确资源集合；既有legacy请求保持；新增组合遵守分组规则，不静默抛弃任一条件 | 前后差异见兼容审计，正常场景对照保持 |
| IT-C05-09 | 同上，Search与Explore非空对照 | 成功/空集合/400三类分开；新节点识别context，远程加载按 ARD 规范§3，不以空集合掩盖加载失败 | 前后差异见兼容审计，正常场景对照保持 |
| IT-C05-10 | F-BOOL/F-TYPE；同上可公开准备的值和字段 | 对照before集合、原case匹配；合法输入不因统一validator变400；深层不可写数据由UT承担 | 前后差异见兼容审计，正常场景对照保持 |
| IT-C05-11 | 两namespace同名；body/query冲突；未知成员、duplicate key、数字text、坏凭据+坏body | namespace确定规则正负例；绑定宽容/优先级采before后冻结；不凭common mapper猜HTTP值；无凭据泄漏 | 前后差异见兼容审计，正常场景对照保持 |
| IT-C05-12 | F-PAGE，基础模式；同/变化query、改pageSize、跨ns/生命周期、非法token加无匹配type | 静态同查询无重漏；变化查询按本次集合；404制品与token400分开；不宣称任意token改字节必拒绝 | 前后差异见兼容审计，正常场景对照保持 |
| IT-C05-13 | 独立增强开启部署+受控provider；实际取token后换pageSize/query/config与等价context | 非空及空页续页均正确；仅能本地固定provider时明确环境条件，不用空库跳过增强；静态集合无重漏 | 前后差异见兼容审计，正常场景对照保持 |
| IT-C05-14 | BC17；同版standalone的Search基础/增强及List；先取真实token成功续页，再按[Search恢复步骤](#recovery-case)固定原因使其失效；保留原请求无token重查 | 无效token返回E400-T且无静默首屏；清旧结果后按原query/ns/排序/身份重查并遍历当前预期全集；非token400移除token仍报错、不循环重试。无双版本环境依赖，不将此称作混跑覆盖 | BC17；前后差异见兼容审计，正常场景对照保持 |

IT-C05-01/05还应把有/无context的真实Search响应同时交给旧、新Schema校验，
比较旧字段集、类型和意义（displayName/type/url及score/source等），不能因新Schema
允许省略而裁剪响应；旧DTO可反序列化但关键字段为null仍算失败。

依据 S05/S10/S11。值类型与输入错误测试必须真正经过 HTTP JSON 绑定；
UT 仅证明 service 异常不足以替代 IT。优先扩展 ArdSearchServiceImplTest、
ArdSearchControllerTest、ArdAdaptorOpenApiITCase；各token分支与恢复步骤统一见下表。
兼容边界以 ARD 规范§4和BC15–BC18为准。

<a id="token-cases"></a>

#### Search 分页等价类与错误优先级

本表是UT/IT-C05-12/13/14的参数化输入分类；对外契约以
[ARD 规范§4](../../specs/zh-cn/ai/ard-protocol-spec.md#token-contract)为准。
E400-T仅为测试标签，不是新的wire错误码。

以下错误结论以请求已通过鉴权/绑定/语法校验、且到达Core分页分支为前提：

| 输入/变化 | 基础Search | 增强Search | List |
| --- | --- | --- | --- |
| 无/空/空白token | 首屏 | 首屏 | 首屏 |
| 非法Base64/非JSON/无可解析id | E400 | E400，缺fingerprint也不接受 | E400 |
| 相同Query/config，锚点有效 | 续页 | 续页 | 续页 |
| 只改pageSize | 续页，取新的有效页大小 | 续页，limit未进fingerprint | 续页 |
| 改text/filter，锚点仍有效 | 可能续页，不强制400 | Query指纹改变则E400 | 仍满足新predicate可续页；排序按本次请求 |
| 改Query/config，锚点消失/不可见 | E400 | E400 | E400 |
| 基础/增强token混用 | 含relevanceV1则拒绝 | 缺/不符relevanceV1则拒绝 | 含relevanceV1则拒绝 |
| 协议常量/type前置过滤无匹配 | 保留200空且无token，不进入Core | 同左，不假称已校验token | 同左 |

非结构性token改动若恰好成为另一个有效锚点，不能宣称必定400；锚点仍受当前身份和namespace限制。
不新增TTL：同版同库重启且锚点/配置不变仍可续页；跨版不承诺复用，也不主动强制失效。
进入分页校验的失效按E400-T；重建id、权限或配置变化需先固定场景再断言，不放宽所有正常token。
增强空页可能带token，必须继续；客户端不使用results.length<pageSize作为通用终止判据。

<a id="recovery-case"></a>

#### IT-C05-14 失效与重新查询的确定性步骤

- 正常对照：F-PAGE限定唯一marker及namespace；Search pageSize=1、List pageSize=1，
  先在同版稳定环境取真实token并成功续页。增强用IT-C05-13的受控provider/独立配置；
  不能用空库或仅伪造token代替成功链路。
- 基础Search/List锚点失效：pageSize=1得到一个条目及token，通过公开生命周期API
  将该条目下线/删除；用独立无token查询等待索引收敛后，再带原token查询。
  fixture须保留其他匹配条目，以免触发前置无匹配短路。失效预期为E400-T。
- 增强失效：用原query从配置A获取token；在同版受控环境配置B已生效且按既有规则改变
  fingerprint后重放；无token对照B仍非空。先冻结配置变化及oracle，不能让provider随机变化。
  无法在公共API控制配置时，由外部部署fixture准备，测试类不启动Spring/Nacos。
- 格式错误：补确定不能解码的token，并验证非空正常查询；这是负例，不能替代真实token失效。
- 恢复：丢弃旧遍历结果，原请求仅去掉pageToken，在固定的新可见数据/配置下取首屏并
  使用新token遍历，核对预先确定的identifier全集及无重复。排序、过滤、namespace、
  context、pageSize、federation与身份均保留；新增/删除的资源按新的fixture集合断言。
- 其他错误对照：合法可续token配非法标准filter（List用非法filter语法）仍为参数400；
  去掉token也不能修复，记录请求次数并停止，不把错误当成功或改查询条件。401保持独立验证。
- 在同版有效token不失效、特定失效E400-T、重新查询成功三个步骤分别保留证据；
  所有资源finally清理。旧/新真实版本演练可选，不为升级存活写固定 ABI 快照断言。

<a id="explore"></a>

### Explore

验证范围：ARD §5.3.1/5.3.3按同一context解释过滤及facet；保留original key与
resolved field，聚合id使用独立前缀+original key以隔离两个别名的limit/minCount和内部type聚合键；重复原key后者覆盖。
`exploreShouldAggregateAllPagesAndKeepAliasOptionsIndependent`验证501条跨仓储页，
`exploreShouldPreserveFacetLimitAndMinCountBoundsForAliases`验证105个bucket及边界；
正式HTTP由`testExploreResolvesContextAndKeepsFacetOptionsIndependent`验证非空对照、
独立选项、Search集合一致、pageSize无关与错误。基线alias无bucket，目标与规范字段等价。

| ID | 前置与输入 | 应提交的断言 | 修改前 → 修改后 |
| --- | --- | --- | --- |
| UT-C06-01 | type、a:mediaType 两个 facet，query 含别名 filter | 规范化字段相同，输出保留各原 key，bucket/count 相同；原 key 重复保留既有行为，不新增 400 | literal facet -> IRI facet |
| UT-C06-02 | 多页候选，过滤后完整集合；空集合 | buckets/count/otherCount 对完整可见匹配集正确；无结果 buckets=[] | 保留聚合边界 |
| UT-C06-03 | BC11；facet null/空/重复，limit null/0/-1/20/100/101，minCount null/0/-1/1 | 原默认20/cap100和min1，空必需结构错误、原key后者覆盖；别名聚合不改变这些规则 | BC11；前后差异见兼容审计，正常场景对照保持 |
| IT-C06-01 | F-PAGE/F-TYPE，以裸/别名 filter 和 facet 交叉请求 | 200、等价 counts；pageSize 变化不改变 Explore；基础 Search 全页并集与统计相符 | 别名不可用 -> 一致聚合 |
| IT-C06-02 | query 省略/空；缺 resultType、空 facets、空 field、非法 context | 前两者聚合全部可见；缺必需结构或非法输入 E400；有值的正确对照成功 | 保留原默认/验证，新增 context 校验 |
| IT-C06-03 | F-PAGE/F-TYPE，同上经HTTP，裸/别名交叉 | 按完整集合计数；错误路径有正常对照；GET/其他API不被新校验连带改变 | 前后差异见兼容审计，正常场景对照保持 |

依据 S05/S06 和 ExploreRequest/ExploreFacetRequest；空 facets 等既有收紧标为 Nacos。
增加到原 Explore 服务测试和现有 IT。不要为协议没要求的“重复 facet 拒绝”编写新错误断言。

<a id="query-examples"></a>

### 查询示例

| ID | 前置与输入 | 应提交的断言 | 修改前 → 修改后 |
| --- | --- | --- | --- |
| UT-C07-01 | 示例缺失、0/1/2/5/6 条，分别过旧/新 schema | 新版这些数量均结构合法，runner 对缺失/非2..5告警；不能把 6 判错或强制截断为协议要求 | 旧 schema 2..5限制 -> 新 schema 放宽 |
| UT-C07-02 | 实际 1/6 条进入新 Manifest 与 legacy renderer | 新版保留实际有效示例并诊断；legacy 为兼容选最多5、少于2省略；资源资格/共享索引不变 | 少于2统一省略/大于5直出 -> 分版投影 |
| IT-C07-01 | 通过现有发布 API 准备可产生不同示例的资源 | Search/List 的非空发现与 artifact 不受例子数量影响；新 host Registry 示例真实描述检索用途 | 不引入资源隐藏规则 |
| IT-C07-02 | 不存在能精确设置示例数量的公共接口时 | 数量组合保留 UT/组件验证；IT 验证实际可公开准备的数据，不新增专用写 API 或伪造数据库 | 明确 IT 可观测边界 |

依据 S07。新 well-known 仅 Registry，不能在该入口假称验证了所有资源示例；
资源完整 Entry 的细粒度新版验证使用 renderer/Schema UT，必要时独立评审 namespace 扩展。

<a id="trust"></a>

### Trust 契约

| ID | 前置与输入 | 应提交的断言 | 修改前 → 修改后 |
| --- | --- | --- | --- |
| UT-C08-01 | 无 trust、开放成员、缺失/非字符串 identity、畸形 trust 类型 | identity 必填；自定义 identityType 和嵌套框架成员按新版原始 Schema 校验；小写字段独立检查 | 原封闭 Schema -> 新版开放结构 |
| UT-C08-02 | Registry FQDN、显式publisher缺失、HTTPS/SPIFFE/did:web、未知框架/提示；两个普通资源及查询别名 | 只校验Registry声明；含Registry文档拒绝畸形/冲突，普通查询含空集合可用；资源不继承trust且ID不随Registry配置变化；资源身份filter/facet包括短路/别名/IRI返回400；source聚合保持 | 全局混用 -> Registry归属与资源缺口分离 |
| IT-C08-01 | Registry trust缺省/匹配/未知；publisher/base URL/Host独立变化 | 三个Registry文档按配置声明；非空Search/List/type Explore与Artifact可用；普通资源无trust且ID稳定，新旧hint投影兼容 | 全局身份 -> 仅Registry身份 |
| IT-C08-02 | Registry publisher缺失/畸形、authority冲突/畸形；资源身份维度查询；有效/坏凭据 | 服务可启动；仅含Registry的三个文档500；普通查询/Artifact可用；资源publisher/trust filter/facet400；坏凭据401和请求校验优先 | 全局拒绝 -> 声明文档拒绝且普通查询隔离 |

部署策略及迁移见 ARD 规范§5；Registry声明错误来自服务端配置，返回500；
请求未支持的资源身份维度返回400，两者不能混淆。资源publisher/trust注册、存储、授权、
索引和旧标识迁移待实现，不能用Registry配置的测试替代资源级能力验收。
比较器不访问网络、不验证签名/证书/委托，不把未知DID方法当非法；不修改上游fixture。
HTTP测试通过外部部署参数指定预期配置，不在测试类中启动服务或修改服务器配置。

<a id="pagination"></a>

### 分页、地址与类型

验证范围：仅Search上限50→100。`searchAndListShouldNormalizePageSizes`以105条真实Core
候选参数化验证；修改前51/100/101/MAX四组失败（实际50），修改后全部满足目标。
正式HTTP扩展`testLegacyCatalogEnumeratesMoreThanOneHundredPublicResources`的既有105条资源：
9组页大小、调整续页大小、Search/List全集与排序、数值字符串/浮点/非法类型、List失效与重查。
GET非整数500保持并明确标为既有错误映射缺口；URL/URN/artifact由原有回归覆盖。

| ID | 前置与输入 | 应提交的断言 | 修改前 → 修改后 |
| --- | --- | --- | --- |
| UT-C09-01 | pageSize 缺省、0、-1、50、51、100、101 | Search 分别10/10/10/50/51/100/100；List 20默认/100上限保持 | Search 51..100被截50 -> 最大100 |
| UT-C09-02 | base/referral URL、扩展 media type、Unicode/多段 URN | registry 为 base；未来 referral 值为完整端点；合法未知类型不因 enum 拒绝；标识稳定且无碰撞 | 保留已合规部分 |
| UT-C09-03 | BC12；GET filter日期/转义/AND、orderBy默认/别名/第二词非desc/尾词 | 既有合法和宽容行为保持、已拒绝语法仍拒绝；不改成POST JSON-LD grammar | BC12；前后差异见兼容审计，正常场景对照保持 |
| UT-C09-04 | BC13/14；已绑定Integer null/-1/0/1/50/51/100/101/MAX | Search10默认/100上限，List20默认/100上限；不把UT Integer结果误写成原始HTTP绑定证据 | BC13/14；前后差异见兼容审计，正常场景对照保持 |
| IT-C09-01 | F-PAGE，各页大小及所有续页 | 实际候选足够时51/100请求返回对应数量；总并集无重漏；101按Nacos兼容截100；类型转换沿BC14先采HTTP baseline再冻结 | 明确页大小变化，避免无依据收紧绑定 |
| IT-C09-02 | GET /agents 的时间过滤、排序、引号/AND/非法表达式 | 原成功结果保持；非法表达式 E400；不引入 POST context 参数 | 既有行为保持 |
| IT-C09-03 | F-TYPE，跟随 url 下载；切换表示/精确版本/digest/offline | 两表示 identifier 相同、URL不同；正确制品内容/类型；错误digest/下线 E404；缺必需参数 E400；ZIP资源齐全 | 既有 Artifact 语义保持 |
| IT-C09-04 | 固定不同name/time资源，真实GET排序/过滤 | 集合和顺序符合before；错误filter受控，大小写/时间边界可重现 | 前后差异见兼容审计，正常场景对照保持 |
| IT-C09-05 | 105条候选，GET/POST各发整数、字符串"10"、10.5、true、对象、null、超int边界 | 整数确定预期；模糊coercion先采before、after保持；实际不可转换错误单列原有错误映射缺口；页大小跨50迁移与每页调整 | 前后差异见兼容审计，正常场景对照保持 |

IT-C09-02的List响应同样做双版Schema与旧字段基线比较，items内保留完整既有投影，
不得退化为仅identifier。空集合、可选字段缺省与有值样本都需覆盖；D07已登记的旧示例
数量修复单独断言，不以笼统“快照变化允许”放过其他字段变化。

依据 S09/S10/S11。非正数默认化/101截断为既有 Nacos 策略；
不是 ARD 明定。List items、Search results、source 和整数 score 同时断言。

<a id="conformance"></a>

### 完整验收与私有资源完整性

| ID | 前置与输入 | 应提交的断言 | 修改前 → 修改后 |
| --- | --- | --- | --- |
| UT-C10-01 | 复用 ArdOpenApiContractTest/ArdContractSchemas：缺失/篡改 fixture、未知引用、合法与非法 URI/date-time | 离线校验明确失败；完整 Entry 与部分响应使用各自 Schema | 已有契约测试补齐格式反例 |
| IT-C10-01 | 独立部署的新 well-known -> Registry Search -> 公共 Agent artifact | 精确非空集合，读取返回的 URL，名称/版本/digest 与主资源 API 一致；显式坏凭据 E401 | 空结果路由验证 -> 可用资源完整链路 |
| IT-C10-02 | F-AUTH，各种身份与生命周期变化 | 私有授权可见、无权不可见、bad credential E401；分页前可见性；下线/删除后的 artifact 失效 | 私有索引旧 Disabled 用例已恢复；MCP Artifact 生命周期仍待验证 |
| IT-C10-03 | ARD关闭、SearchCore开启；共享资源API | 主服务资源Search/RAD继续工作，适配器未暴露；不改变SDK生命周期 | 既有模块隔离保持 |

契约验证复用现有 Java UT；真实服务场景由现有 standalone HTTP IT 执行。
上游固定版本工具只作本地独立对照，保留原目录结构和执行逻辑，
manifest/registry/publisher 分别记录结果；依赖或 Schema 跳过不计为通过。

DAUTH-F03 的两个索引用例已恢复；私有 MCP 召回与搜索权限由专项 HTTP 场景验证。
TODO：MCP Artifact canonical 名称/UUID 映射及其生命周期仍待修复和验证；公共 Agent 链路不覆盖该缺口。
编译失败、环境失败、既有缺陷、新回归和未实现能力分别记录。

<a id="regressions"></a>

## 5. 既有功能回归清单

所有相关组在实现前保存 baseline、实现后复跑。仅新增测试通过不满足回归门禁。

| ID | 已有测试定位 | 不允许改变的预期 | 适用功能变化 |
| --- | --- | --- | --- |
| RG01 | ArdSearchServiceImplTest 的 catalogShouldExposeRegistryAndLocalEntries、hostCatalogShouldExposeOnlyRegistryEntry、catalogShouldNotTruncateAfterOneHundredEntries | 旧封装、host范围、跨页枚举 | Schema/发现入口/查询示例/完整验收 |
| RG02 | 同类 searchShouldApplyStructuredMetadataFilters、searchShouldApplyFieldPathFilters、searchShouldAcceptExtensionFieldPathSegments、listShouldRejectMalformedFilterInsteadOfSilentlyMisparsing | 旧query.filters、metadata/未知裸路径、GET filter | Context 解析/Search/Explore/分页与地址 |
| RG03 | ArdWebAuthenticationTest、ArdExceptionHandlerTest、ArdSearchControllerTest、AuthScopeGuardITCase | 身份传递、显式拒绝、错误体、外部helper不继承凭据 | 发现入口/Search/Explore/Trust/完整验收 |
| RG04 | 同类 searchShouldSelectDeterministicAgentRepresentationWithoutDuplicatingIdentifier、searchShouldPreferA2aForPureA2aAndFilterUnavailableRepresentation、catalogIdentifiersShouldBeSchemaValidAndCollisionSafe | Agent表示选择、稳定URN及无碰撞 | 发现入口/Search/Trust/分页与地址 |
| RG05 | ArdArtifactServiceTest、ArdWebContextIsolationTest、ArdAdaptorOpenApiITCase.testPublicAgentIndexAndUnifiedArtifacts | ZIP、精确版本/digest、Runtime排除、端口归属、offline404 | 发现入口/Search/分页与地址/完整验收 |
| RG06 | ArdSearchServiceImplTest 的 searchShouldPageResultsWithPageToken、searchShouldRejectInvalidPageToken、listShouldFilterOrderAndPageEntries | 同版有效cursor/无效错误、排序与List默认；Search上限按D10变化；不加入跨版本ABI承诺 | Search/Explore/分页与地址 |
| RG07 | 同类 searchShouldAcceptAllStandardFederationModesWithLocalFallback、searchShouldRejectUnknownFederation、searchShouldSkipEntryWhenVersionIsNotLatest | 三种模式、本地fallback、latest/currentness | Search/Explore/完整验收 |
| RG08 | 同类 vector/ranking测试；AiResourceSearchRelevanceTest、AiResourceRelevanceFilterTest；现有testSearchEnhancementStatusAndCursorBoundary | 召回/分数/保序、默认关闭、增强header及token | Search/Explore/查询示例/分页与地址/完整验收 |
| RG09 | AiResourceSearchServiceTest、AiResourceSearchApplicationServiceTest、AgentSearchIndexProjectorTest、McpAiResourceSearchTypeHandlerTest；AiResourceSearchClientOpenApiITCase | 共享Core、canonical投影、可见性、其他资源Facade | 任何共享Core变更及完整验收 |
| RG10 | AgentDiscoveryServiceJavaSdkITCase、McpHttpClientJavaSdkITCase | 已有SDK可观察行为不变；不新增ARD SDK接口测试 | 共享Core/公共模型被触及时及最终代表回归 |

RG08 在增强开启时只验证既有语义没有回归；不能谎称 Search-only 增强已与 Explore cutoff
一致。RG09/RG10 包含的原有 Disabled/环境缺口必须逐项报告，不计为本轮通过。
以关键方法为入口，但运行对应类可覆盖同类其他既有场景；不复制出第二套等价测试类。

分页回归还复用AiResourceSearchServiceTest的listShouldContinueAfterOpaqueResourceCursor、
listShouldRejectMalformedCursorAsParameterValidationError，以及AiResourceSearchRelevanceTest的
changedConfigurationInvalidatesCursorAndDisabledConfigurationSkipsProvider；
namespace绑定继续复用ArdSearchControllerTest。这些是RG06/08/09的执行入口，不新增重复案例ID。

<a id="execution-record"></a>

## 6. 测试证据与覆盖边界

验证结果应关联协议章节、正式用例和真实测试类/方法，并说明部署条件、已知限制和数据清理结果。
有 HTTP 行为的场景以独立服务器执行结果为准；只有编译或 Controller mock 不能计为 HTTP 通过。
场景文档与覆盖登记按实际结果维护；设计中尚未实现的场景和 Disabled 用例不计入通过数量。

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
[schema]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard-entry.schema.json
[context]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.context.jsonld
[query]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.openapi.yaml#L126-L188
[facet]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.openapi.yaml#L189-L263
[results]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.openapi.yaml#L264-L349
