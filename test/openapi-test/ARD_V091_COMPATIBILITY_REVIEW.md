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

# ARD v0.91 兼容审计

本表保留升级前代码事实，并记录当前实现的兼容决策。
HTTP 绑定结论来自真实请求基线；已实现测试和仍待完成的验收范围见测试设计§4。
本文件只维护BC01–BC20的“修改前事实、协议要求、变化与迁移、测试关联”。
目标契约见[中文设计](../../specs/zh-cn/ai/ard-v091-adaptation-spec.md)/
[英文设计](../../specs/en/ai/ard-v091-adaptation-spec.md)；
所有正式用例及错误等价类集中在[测试设计](ARD_V091_TEST_DESIGN.md)。
本文不包含本地执行矩阵或提交计划。

## 1. 审计依据与事实等级

版本以目标规范§1为准；S编号使用[正式测试来源索引](ARD_V091_TEST_DESIGN.md#protocol-sources)。
代码事实、HTTP实测、协议要求和Nacos自选策略分别记录，不用Java字段类型推测HTTP拒绝行为。

当前实现证据：

- [适配器service](../../ai-registry-adaptor/src/main/java/com/alibaba/nacos/airegistry/service/ard/ArdSearchServiceImpl.java)：
  validateAndBuildContext/ExploreContext、normalizePageSize、
  normalizeListPageSize、normalizeFacets、parseOrderBy、matchesProtocolFilters。
- [Controller](../../ai-registry-adaptor/src/main/java/com/alibaba/nacos/airegistry/controller/ArdSearchController.java)：
  namespace query/body绑定、GET参数转换。
- [共享Core](../../ai/src/main/java/com/alibaba/nacos/ai/service/search/AiResourceSearchService.java)：
  page/cursorIndex/listCursor、encodeCursor/decodeCursor、relevancePage。
- [异常映射](../../ai-registry-adaptor/src/main/java/com/alibaba/nacos/airegistry/controller/ArdExceptionHandler.java)；
  [现行适配器规范](../../specs/zh-cn/ai/ai-registry-adaptor-spec.md)、
  [共享检索规范](../../specs/zh-cn/ai/ai-resource-search-spec.md)、
  [兼容规范](../../specs/zh-cn/design/compatibility-deprecation-spec.md)。

“代码已确认”不等于HTTP已复现。数值字符串、浮点数等Jackson/Spring绑定行为及未知成员、
重复JSON key须以真实before HTTP记录为准；common JacksonUtils不等于MVC使用的converter。
这些待测项不授权新增全局严格ObjectMapper或整包请求Schema拦截器。

<a id="compatibility-audit"></a>

## 2. BC01–BC20逐项审计

分类：保持、新增、收紧、修复、待决。每项对应正式正常/错误或边界用例；
BC03是旧协议已要求的输入收紧，BC13是既有上限偏差修复，不能标成v0.91新增限制。
BC05须区分context缺省/显式null；BC09/14的HTTP绑定结果仍须在开工前采baseline。

| ID / 功能 | 升级前事实与兼容风险 | ARD依据及是否要求变化 | 兼容方案与迁移 | 正式案例 |
| --- | --- | --- | --- | --- |
| BC01 / Schema,发现入口 | 新模型复用可能使旧响应少字段或带入新扩展 | [§4.2–4.3][s03]、[结果Schema][results]允许部分字段，不要求精简 | 保持：新Manifest绑定新入口；旧catalog、共享Search/List保留旧投影；两版Schema与旧字段基线同时验证 | UT-C02-03、UT-C03-01、IT-C03-03、IT-C05-05、IT-C09-02 |
| BC02 / Search,Explore | Search缺query/text及blank报错、text trim；Explore无query/空text可聚合全量 | [§5.3.2][s10]/[§5.3.3][s06]分别required/optional；非空和trim为Nacos现有规则 | 保持：不得把Explore套Search的text必填，也不改变既有空白处理；空JSON/非法JSON沿现有错误映射 | UT-C05-06、IT-C05-06 |
| BC03 / Search | 标量非String/null原本400；数组非null元素会String.valueOf，null被丢弃 | [QueryModel][query]只允许string/string[]，旧OpenAPI同样要求；这是既有宽容收紧 | 收紧仅标准query.filter的非字符串数组元素（含null）->E400。升级前客户端改为真正字符串、移除null；不把它称作v0.91新增限制。legacy query.filters仍走旧转换，不能顺带收紧；PR/release note明确输入迁移 | UT-C05-07、IT-C05-07 |
| BC04 / Search | filter与filters共存；同原始fieldPath合并值为OR；legacy values非null优先于value，null条目跳过 | [§5.3.1][s05]仅定义filter；filters为Nacos扩展 | 保持无context历史合并/优先级；legacy fieldPath仍按旧字面字段解释。带context时仅解析标准filter，只有原key和解析字段都一致才与legacy同键合并；不同key同IRI仍AND，重绑字段不错误合并。不能因additionalProperties:false删除扩展 | UT-C05-08、IT-C05-08 |
| BC05 / Context 解析,Search | 升级前DTO没有context，旧节点没有新语义；新解析可能把以前被忽略的输入变成功或错误 | [§4.1][s04]/[QueryModel][query]定义URI/object/array与base；整体null不在类型内 | 新增：缺省始终base；显式合法context启用其语义，非法context返回E400。URI加载/array reset按已冻结目标规范§4.1：精确allowlist支持额外URI，reset后恢复base；解析失败/部署限制400而非静默忽略；客户端仅向已升级节点发送新能力请求 | UT-C04-01..04、IT-C05-01/04、IT-C05-09 |
| BC06 / Search,Explore | 旧合法未知裸路径对非空条件空匹配；原计划将未知显式IRI统一改400 | [§5.3.1][s05]对unsupported term的400是MAY，不是MUST | 保持：撤回统一400；语法合法但本地不支持的字段/IRI继续按无值处理，非空约束无匹配/无bucket。解析失败、空key、畸形path仍E400。已识别ARD别名/IRI新增正确匹配 | UT-C05-09、IT-C05-09 |
| BC07 / Search | 当前值匹配通常忽略大小写并trim；field key/metadata成员名有既有大小写规则；空数组行为因字段而异 | [§5.3.1][s05]规定IRI身份与OR/AND，没有统一空数组/大小写策略 | 保持值比较和空数组历史行为：type=[]为无类型结果，tags=[]等Core predicate无约束；不统一改400/全空/全量。IRI/命名空间大小写不能跟值一起lowercase；literal JSON成员不展开。metadata深路径未被当前投影支持时不新增存储/递归Core能力 | UT-C05-10、IT-C05-10 |
| BC08 / 发现入口,Search,Explore | namespace省略/blank->public；body非默认namespace必须有query且一致，否则400 | Nacos扩展，[兼容规范](../../specs/zh-cn/design/compatibility-deprecation-spec.md)，非ARD条款 | 保持query/body校验次序与资源可见性；不由context携带或覆盖namespace，不移除现有body兼容字段；同名跨namespace对照 | UT-C05-11、IT-C05-11 |
| BC09 / Search | 未知JSON成员、重复key、数字text等实际HTTP绑定需baseline；请求包含Nacos扩展 | [QueryModel/SearchRequest][query]封闭结构不等于必须全局开启严格校验 | 保持已记录的宽容绑定，不新增全请求Schema拒绝；只局部处理新增context及BC03。未知字段接受不代表被执行。若发现原有绑定缺陷单独立项，不借升级顺带修正 | UT-C05-11、IT-C05-11 |
| BC10 / Search | federation缺省/null/blank->auto，值trim，大小写敏感；三模式均本地fallback | [§5.3.2][s10]/[§5.4][federation]枚举；blank/trim为Nacos | 保持auto/referrals/none行为及空referrals；AUTO仍错误，不新增远端转发或因enum Schema拒绝历史blank | UT-C05-06、IT-C05-06 |
| BC11 / Explore | facets必需非空、field trim；limit缺省/非正20、上限100；minCount缺省1/小于1归1；重复原key后者覆盖 | [§5.3.3][s06]/[Facet][facet]没有规定全部拒绝策略 | 保持上述规则及旧key覆盖，不新增重复facet400；新增别名仍回显原key。全量聚合、enhancement默认关闭的cutoff验收不变 | UT-C06-03、IT-C06-03 |
| BC12 / 分页与地址 | GET filter接受date/instant、引号转义/AND；orderBy默认updatedAt desc，name/updated_at别名，非desc第二词按asc且尾词目前未拒绝 | [§5.3.4][list]/[Appendix A][list-filter]/[GET参数][get-params]不要求本轮改语法 | 保持GET与POST语法分离、现有别名/排序宽容；不借JSON-LD升级新增排序拒绝。原本畸形filter继续E400。若收紧多余排序词单列后续设计 | UT-C09-03、IT-C09-04 |
| BC13 / 分页与地址 | Search max50/default10；List max100/default20；null/非正默认、超限clamp | [§5.3.2][s10]新旧都max100；非正策略未指定 | 修复Search max100；50以内不变、51..100不再截50、101+截100；List不变。不接受增大上限的客户端可升级前显式pageSize=50；不要将“少于请求数”当结束，按token续页 | UT-C09-01、IT-C09-01/05 |
| BC14 / 分页与地址 | pageSize为Integer；JSON数值字符串/浮点等与GET字符转换不同，现有HTTP结果待测 | [SearchRequest][query]/[GET参数][get-params]为integer；未新加coercion规则 | 保持已有绑定配置；确定不可转换的输入受控错误。不得把数字字符串/浮点一律400写成已定兼容预期；记录实际before并原样回归，不把通用Search的正数/长度限制搬到ARD | UT-C09-04、IT-C09-05 |
| BC15 / Search,分页与地址 | 基础Search/List token仅Base64url文档id，无查询指纹/TTL/签名；空白token当首屏；锚点在当前集合有效即可续页 | [§5.3.2][s10]/[§5.3.4][list]未定义跨版ABI或绑定/过期规则 | 复用现有格式/decoder，不新增包裹、签名、TTL或“改查询必400”。基础Search改查询但锚点仍有效可续；失效按E400-T；List另校验namespace/type/predicate/currentness；不自动重置首屏，不保证跨版本复用 | UT/IT-C05-12/14 |
| BC16 / Search,分页与地址 | 增强token为id+relevanceV1，绑定Query/配置；不含pageSize；predicate/values顺序参与JSON hash | [共享检索增强规范](../../specs/zh-cn/ai/ai-resource-search-spec.md)，属于Nacos，不是ARD新要求 | 复用当前fingerprint和mapper顺序；同有效Query/config续页、变化则E400-T；调整pageSize可续。等价context映射Query不变可复用，不承诺filter重排或跨版本存活；必要语义改变指纹时按目标规范§4.5恢复 | UT/IT-C05-13/14 |
| BC17 / Search,完整验收 | 升级/回滚、id或增强指纹变化可能使token失效；旧节点不认识context；原计划要求跨版复用 | [§5.3.2][s10]/[§5.3.4][list]未规定跨版互通；错误类别见[Appendix B][errors]，恢复流程是Nacos选择 | 调整：不承诺跨版复用，不为兼容新增ABI转换/协商；可自然续用。新节点检测无效时E400-T，无静默重置；客户端清除旧结果与token，原条件无token重查，最多重开一次。context仅升级池启用；回滚先停新语义并主动清分页状态。真实双版本演练可选，不阻塞本版验收 | UT-C05-14、IT-C05-14；双版本演练可选，本版恢复必选 |
| BC18 / Search,分页与地址,完整验收 | token锚点不是快照；下线、删除、投影重建、权限变化可失效；协议前置无匹配分支可在解析token前返回空集合 | Nacos当前性/可见性；ARD无跨页快照承诺 | 保持每页重新授权/校验；冻结数据才断言全页无重漏，变动数据不承诺快照。权限收回不泄露数据；原前置空匹配仍200空，不因“坏token一律400”改变错误优先级。无效token错误后调用方明确重开，服务端不偷偷回第一页 | UT-C05-12/13、IT-C05-12/13、IT-C10-02 |
| BC19 / 查询示例,Trust,分页与地址 | 示例>5修复会截断旧展示；publisher改值会改URN；身份/loader新拒绝或URL重写会破坏运行 | [§4.2][s03]/[D.2][s07]、[§4.5][s08]、[Appendix C][urn] | 旧示例修复明确披露，索引不裁剪；publisher不自动迁移，Host/baseURL不重写URN；加载策略见目标规范§4.1；身份处理策略需先约定，未知框架不直接启动失败；artifact版本/digest/表示/地址保持 | UT-C07-02、UT-C08-02、IT-C08-01/02、IT-C09-03 |
| BC20 / 发现入口,Search,完整验收 | 新validator可能绕过鉴权或改401/400/500优先级、错误体/空结果；通用helper会禁止转发凭据 | [Appendix B][errors]、Nacos认证/适配器规范 | 保持ArdApi错误体和既有auth先后链；同时坏凭据/坏body按baseline，不新建路由旁路。400修复若发现GET绑定当前落500须独立记录及测试；无权限资源过滤与请求级401分开，不将错误文案逐字当契约 | UT-C05-11、IT-C05-11、IT-C10-02 |

## 3. 验证入口

- 正式用例按功能分组见[测试设计§4](ARD_V091_TEST_DESIGN.md#formal-cases)，
  其中[Search分页等价类](ARD_V091_TEST_DESIGN.md#token-cases)区分基础锚点、增强指纹、List和前置短路。
- token错误/客户端重查/升级启用规则仅在[目标契约§4.5](../../specs/zh-cn/ai/ard-v091-adaptation-spec.md#token-contract)维护；
  [IT-C05-14](ARD_V091_TEST_DESIGN.md#recovery-case)负责可控失效及恢复证据。
- 同版正常续页仍必测，跨版存活不是门禁；未运行的真实混跑不得标成已验证互通。
- API scenario/coverage依实际实现和执行结果更新；本设计状态Planned，执行为Not run，不提升覆盖率。

[s03]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L96-L124
[s04]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L88-L94
[s05]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L231-L268
[s06]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L347-L410
[s07]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L565-L570
[s08]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L183-L197
[s10]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L270-L301
[urn]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L534-L544
[list]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L412-L423
[errors]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L524-L532
[federation]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L431-L497
[query]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.openapi.yaml#L126-L188
[facet]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.openapi.yaml#L189-L263
[results]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.openapi.yaml#L264-L349

[get-params]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/schemas/ard.openapi.yaml#L74-L122
[list-filter]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md#L510-L522
