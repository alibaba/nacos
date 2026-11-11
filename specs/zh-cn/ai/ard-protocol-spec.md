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

# ARD 协议规范

## 1. 范围

Nacos 支持本地 Agent、Skill、Prompt 和 MCP 资源的 ARD v0.91 发现。
协议术语和 Schema 遵循 [f073b1ea 对应的上游规范][ard]。
本文定义 Nacos 的接入契约及支持范围。

[AI Registry 适配器](ai-registry-adaptor-spec.md)负责 HTTP 发布和协议映射；
资源身份、生命周期、权限、索引与检索由 [AI Registry](ai-registry-spec.md)和
[AI 资源检索](ai-resource-search-spec.md)负责。资源写入使用 Nacos API 和 SDK。

## 2. 发现入口与响应

`nacos.ai.ard.enabled=true` 时，ARD 在适配器端口提供服务，并要求基础资源检索开启。
所有入口遵循 Open API 鉴权，well-known 路径也校验显式凭据；匿名访问由部署策略决定。
错误使用 `{"errorCode":"...","message":"..."}`，不包裹 Nacos `Result<T>`。
凭据被拒绝时返回 401 / `UNAUTHENTICATED`。

| 方法与路径 | 响应 |
| --- | --- |
| `GET /.well-known/ard.json` | `entries` 仅包含本 Registry Entry 的 Manifest。 |
| `GET /.well-known/ai-catalog.json` | 带 `specVersion=1.0` 和 host 元数据的旧版 host catalog。 |
| `GET /v3/ai/ard/ai-catalog.json` | namespace catalog，跨页枚举全部符合条件的条目。 |
| `POST /v3/ai/ard/search` | `results` 返回最新 online 资源，`score` 为 0..100 整数，`source` 为 Registry 绝对 URI。 |
| `POST /v3/ai/ard/explore` | 对完整、可见且当前有效的匹配集聚合 facets。 |
| `GET /v3/ai/ard/agents` | `items` 返回过滤、分页后的资源。 |
| `GET /v3/ai/ard/artifacts` | 返回条目引用的精确资源版本及表示。 |

公开域名根路径 `/.well-known/ard.json` 应代理到适配器。
`nacos.ai.ard.catalog.base-url` 表示适配器公开 origin 和可选代理前缀，不附加主服务 context path。
Registry Entry 的 URL 是 base URL，消费者追加一次 `/search`。

完整 Entry 包含 `identifier`、`displayName`、`type`，且 `url`/`data` 恰有一个；
Nacos 使用 URL 发布。Host Manifest 带官方基础 `@context`，不含旧 `specVersion/host`
和搜索 `score/source`。Catalog、Search、List 使用各自投影，保留现有 `displayName`、
`type`、`url` 及序列化规则；请求 context 不选择响应版本。
Identifier 对 Nacos URN 分段使用稳定、无碰撞编码。

Manifest Entry 保留原始 `representativeQueries` 及顺序，数量不在建议的 2–5 条时仅告警，
不影响可见性或检索资格。Registry 提供两个发现示例。旧 catalog、Search 和 List 最多返回
前五条，不足两条时省略该字段；标准索引与制品保留完整示例。

## 3. 查询与上下文

Search 要求非空白 query text；Explore 允许缺省 query 或空 text。
标准 `query.filter` 值为字符串或字符串数组；同一原始 key 内 OR，不同 key 间 AND，
两个 key 指向同一字段时仍为独立条件。旧 `query.filters` 保留字面路径、值转换及 `values`
优先级；仅原始 key 和解析字段均相同的条件合并为 OR。

JSON-LD 1.1 先应用内置基础 context，再应用请求 `@context`（URI、对象或数组）。
完整 IRI、compact IRI 和别名先于路径拆分解析；`type` 映射到媒体类型 IRI。
仅 `metadata`、`trustManifest`、`data` 根允许字面 JSON 成员后缀，保留成员名大小写。
数组 reset 清除请求别名、恢复基础 context，再处理后续定义；定义为 null 的 term 保持移除。
非法 context 或路径返回 400 / `INVALID_ARGUMENT`。

以下配置属于 Nacos 部署策略：

| `nacos.ai.ard.context.` 下的配置 | 默认与行为 |
| --- | --- |
| `allowed-uris` | 默认空的精确 HTTP(S) URI 允许清单，每个外部引用均需获准。官方基础 URI 始终离线解析。不支持通配符、userinfo、fragment、重定向、HTTP Link 跳转或转发 Nacos 凭据。 |
| `max-bytes` | 每次解析累计 1048576 UTF-8 字节，计入 inline context 和不同远程文档，内置 base 与重复缓存文档除外；必须为正数。 |
| `timeout-ms` | 每次解析远程获取累计 3000 ms；必须为正数。 |

外部文档仅在单次解析内缓存。拒绝访问、不可达、非法或超限 context 返回 400，
不回退成无 context 查询。

解析后的字段映射到既有资源类型/版本、tags、capabilities、displayName、Registry source
及受支持的 metadata predicate。合法但未支持的字段或缺失值对非空约束无匹配，facet 返回空桶。
资源 `publisher`、`publisherId`、`trustManifest` 维度的 filter/facet 则返回 400，
包括别名、IRI、旧 filter 和 List。

Explore 共用字段解析，对完整匹配集聚合。Facet key 保留请求的原始拼写并 trim；不同别名
独立应用选项，重复原 key 以后者为准。Facet limit 默认 20、上限 100，minCount 最小 1。
`GET /agents` 使用独立且感知引号的 filter/orderBy 语法，支持单引号等值条件以 `AND` 连接
及 ISO date/instant 比较，不接收 POST 风格的 context 参数。

<a id="token-contract"></a>

## 4. 分页

Search 默认 10 条，List 默认 20 条，`pageSize` 上限均为 100。
缺省或非正数使用默认值，超限截至上限。以 `pageToken` 判断是否继续，短页或空页仍可能
携带续页 token；续页可调整 pageSize。每页使用当前数据与权限，不提供跨页快照保证。

Token 不透明，复用共享 Search Core：基础 token 标识锚点，增强 token 额外绑定查询/配置指纹。
进入分页校验后，解码失败、锚点无效或不可见、增强指纹不匹配、token 模式混用均返回
400 / `INVALID_ARGUMENT`。鉴权与请求校验优先，协议空匹配短路可能先于 token 校验返回。
服务端不会静默重新开始分页。

确认分页状态失效后，调用方丢弃 token 和已累计结果，用原有过滤、context、namespace、
选项和有效身份重新查询，之后只使用新返回的 token。其他 400 不作为自动重试信号；
自动恢复最多重新开始一次。Token 不保证跨版本有效。

## 5. Registry 身份与信任

`nacos.ai.ard.registry.publisher` 仅标识 Registry 自身的发布方，显式值必须是 ASCII FQDN
（支持 punycode），归一为小写并去掉一个末尾根点。
`nacos.ai.ard.catalog.trust.identity` 要求显式配置该 publisher；identity 缺省或空白时
省略 `trustManifest`，`identity-type` 仅为提示。

已识别身份接受离线 authority 一致性检查：HTTPS host 和 `did:web` DNS authority 须与
publisher 匹配；SPIFFE 使用小写 trust domain。HTTPS 拒绝 userinfo、非法 host/端口；
SPIFFE 另拒绝端口、query 和 fragment。`did:web` 接受 DNS 名称、可选 `%3A` 端口及路径段，
不接受 IP。HTTPS/did:web 比较忽略大小写、一个末尾根点和有效端口；SPIFFE 保留域名点号。
未知 scheme 保持未验证声明。这些检查不证明域名所有权、委托或签名有效性。

Publisher 无效或已识别 identity 畸形/冲突时，Manifest 和两个包含 Registry 的 catalog
返回 500 / `INTERNAL_ERROR`，鉴权优先。服务仍可启动，普通 Search/List/Explore/Artifact
继续可用。修正配置并重启后恢复 Registry 文档。Manifest 保留自定义 identity-type 提示，
旧投影使用 `spiffe`、`did`、`https` 或 `other`。

资源不继承 Registry 的 publisher/trust。现有资源标识（含 `nacos.ai.ard.catalog.host.identifier`
前缀）仍是兼容标识，修改此前缀会改变资源 ID。Registry 未配置 publisher/trust 时也保留旧标识。
资源 owner、author、provider、请求 Host 和制品 URL 均不能建立发布方身份。
Search score 只表示相关度。

## 6. 制品

Artifact URL 指向适配器的 `/v3/ai/ard/artifacts`，携带精确版本、content digest 和表示 key。
版本 offline、digest 不匹配或表示不可用时返回 404 / `NOT_FOUND`。主服务与适配器可使用独立端口。
Skill 返回完整 `application/agent-skills+zip` 包，Prompt 和 MCP 使用各自原生表示。
MCP 的 `resourceName` 是 namespace 内的标准名称，不是兼容 UUID；可选 `mcpName` 非空时
必须与其完全一致，否则返回 400 / `INVALID_ARGUMENT`。下载仍检查当前调用者的读取权限。

Agent 的 common latest 版本含完整合法 A2A Card 时支持 `application/a2a-agent-card+json`；
`application/vnd.nacos.ai-agent+json` 返回版本化 Nacos 定义，遵循 [Agent 制品 Schema][agent-artifact]，
不包含 Runtime Endpoint、健康观测、Publisher、心跳或管理字段。声明地址的默认值不代表实时探活。

一次请求中每个逻辑 Agent 至多返回一个 Entry。未指定 type 时，纯 A2A latest 优先 A2A Card，
多协议或自定义协议 latest 优先 Nacos 定义；指定 type 时，按确定性 primary 顺序选择实际可用
表示。选择使用 `resourceType=agent` 与 `artifactKinds`，在 total 和分页计算前完成。
表示选择改变 Artifact URL，不改变逻辑 identifier。

## 7. 支持限制

发现接口读取当前共享索引，收敛前结果可能不完整。MCP 制品的生命周期约束
尚未完整实现。外部 manifest/网页抓取、完整上游联邦、任意词汇存储、资源级 publisher/trust
管理尚不支持，因此不声明完整 Registry 一致性。Federation 的 `auto`（默认）、`referrals`、
`none` 当前均执行本地检索，referrals 为空。

可选 Jev 相关性过滤仅作用于 Search，增强 Search 与 Explore 不共享相关性 cutoff。
`X-Nacos-ARD-Enhancement` 返回 `disabled`、`applied`、`partial` 或 `bypassed`，原始 score 不变，
默认关闭。共享运行时契约见 [AI 资源检索规范](ai-resource-search-spec.md)。

## 8. 兼容说明

此前适配器基于 ARD v0.9，本次更新后在上述范围内支持 v0.91。
已有 catalog 路径和资源标识继续保留，旧 catalog 的 `specVersion=1.0` 不是 ARD 协议版本。

客户端应在标准 filter 数组中使用字符串；如依赖旧页上限，显式设置 `pageSize=50`；声明 Registry
trust 时配置 `registry.publisher`。带 context 的查询只发往已升级节点，版本切换后重新开始分页。
详细场景与迁移证据见[测试设计](../../../test/openapi-test/ARD_V091_TEST_DESIGN.md)和
[兼容审计](../../../test/openapi-test/ARD_V091_COMPATIBILITY_REVIEW.md)。

[ard]: https://github.com/ards-project/ard-spec/blob/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/spec/ard.md
[agent-artifact]: ../../schemas/ai/agent/agent-artifact.schema.json#/$defs/NacosAgentArtifact
