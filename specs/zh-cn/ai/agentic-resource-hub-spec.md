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

# Agentic Resource Hub 规范

2026-10-29 会议范围：Skill、MCP、Agent、Prompt、AgentSpec 均支持 Hub。只有 Skill 支持克隆。

- Hub 使用保留 namespace `__nacos_ai_hub__`，按资源类型在现有列表、详情、版本和 Client 读取接口分流；普通 namespace 不改变。
- 共享条目引用固定源资源 ID、版本 ID 和明确 online 版本。源删除、禁用、变为 private（Prompt 除外）或共享版本下线后，条目不可读。不能回退 latest 或同名重建资源。
- 四类新增资源提供 share、adjust、unshare、force-unshare、source-share、governance-state 管理操作；提供从 Hub 导入独立草稿，不提供源资源直接 copy 或 clone。
- 共享要求源 READ 和 WRITE；读取 Hub 使用 Hub READ 和共享有效性守卫，不要求源 namespace READ。治理下架单独鉴权并审计。Prompt 共享不增加 PUBLIC 条件或 scope 操作。
- Hub 按源资源已有详情结构展示共享版本。MCP 保留原有服务引用、后端/前端端点、路径、请求头和客户端配置；不新增实例明细表，不扩展普通 MCP 详情响应，不增加普通读取的 Naming 查询。共享读取不创建服务或修改源实例。
- Agent Hub 的 runtime-endpoints 读取固定源版本的原 Runtime Snapshot，保留 Endpoint 状态、绑定和来源；不创建 Hub Publisher 或虚构 Hub Naming Service。
- Skill 原跨 Namespace 复制按钮改为“克隆”，沿用既有 `/clone` 契约和源 READ、目标 WRITE 校验，创建独立草稿。
- 四类资源的目录按有效性、权限和查询条件筛选后排序分页；来源展示与内部身份分开，禁止公开 storage、数据库身份或未授权源治理信息。

验证必须区分单元/组件测试、编译和真实部署 API IT；未运行部署测试不能宣称集成验收完成。

共享读取沿用 Skill 的条件路由：同一 URL 用
`@GetMapping(params="namespaceId=__nacos_ai_hub__")` 单独匹配共享读取方法，并在
`@Secured` 注解声明 `ONLY_IDENTITY` 与已有匿名准入策略。普通 Namespace 仍进入原方法和
原资源鉴权；不修改公共鉴权过滤器。共享条件路由还须在委托业务读取前校验绑定的
namespaceId 严格等于保留 Hub Namespace，拒绝重复或冲突参数（HTTP 400 / 20002）；
不能仅依赖 SPECIFIED 身份准入下不会调用的 AI 资源解析器。
身份验证成功后，由 Hub 服务检查共享开关、来源固定身份、在线版本与有效性；不额外
要求普通源资源权限，写请求和无效身份不适用该准入。
Client Agent 与 AgentSpec 保持原生内容 DTO，不要求增加顶层 `hub`；来源导航授权在原有
管理/搜索 DTO 的 Hub 元信息中校验。Agent 的空元数据标签投影为 RAD Catalog 时使用 null，
与普通 Agent 发现一致，不改变源元数据。

服务端 Hub 开关关闭时，目录返回空页，内容与 Hub 管理操作返回 HTTP 501 /
`API_FUNCTION_DISABLED`，包括取消共享、强制取消与两类状态查询。此开关与源资源的
private/offline/disable 状态不同：Hub 开启时，源变为不可共享状态仍允许有权限者取消共享。

同样遵循 Skill 的评审约束：导入回滚先清理目标内容，成功后才删除版本及 disabled 占位；
清理失败保留版本 storage 描述符和占位，不能丢失后续重试依据。来源元数据/版本查询的
系统故障返回脱敏 5xx 并保留内部 cause；目录不得把系统故障当作失效来源而返回空页或部分页。
普通写入口（包括 A2A、Prompt 兼容入口）必须在存储或委托前拒绝 Hub 保留 Namespace；
Hub 内部发布继续使用专用持久化路径。


各资源按 Skill 的层次分别提供固定路由 Controller、状态 Controller、Proxy、Handler 和 Service。禁止使用 `{resourceType}` 通配路由合并业务接口。管理 SDK 方法位于各资源既有 facade。Skill 使用 `POST /v3/{admin|console}/ai/hub/skills/clone`；旧 `/copy` 路由移除，SDK 为 `cloneHubSkill`。


四类资源均提供独立的固定路由 ImportController 和类型化导入请求，接入各自 Proxy、Handler、Service、SDK facade。导入创建目标命名空间中的独立草稿，校验 Hub 读取和目标 WRITE，冲突不覆盖。

MCP Hub 导入按服务所有权处理，仍创建私有独立草稿，不直接发布、不复制 online/latest。STDIO 只复制启动/包配置，不创建 Naming 状态。普通 REF 完整保留 serviceRef 的 namespaceId/groupName/serviceName；即使名称类似 Direct 服务，没有源所有权标记也不能当作 Direct。

源 backend 仅在 namespace、mcp-endpoints group、源名称::源固定版本均匹配且拥有 MCP Direct 服务标记时作为自有 Direct 处理。从其持久、默认 Direct Cluster 的实例读取固定地址，包含非健康/禁用实例；必须恰有一个可还原的持久实例，缺失、非法或多义则导入失败。不得取健康 Endpoint 列表首项，不复制临时实例、客户端、Publisher、心跳或动态活性。目标服务使用目标 namespace、目标名称::目标版本，并注册独立固定地址实例。地址相同不表示部署了新的 MCP 应用。

完整保留 remoteServerConfig 的其他字段：协议、路径、请求头、前端 REF/DIRECT，以及 BACKEND 配置。BACKEND 通过新的目标后端解析，前端 REF 不重绑。Tools/Resources 独立存储。共享到 Hub 的读取与实例展示仍访问源服务，无 Naming 写操作。

目标元数据名称预留成功后，才允许创建目标 Direct 服务和内容。目标 Naming 服务已存在（包括孤立服务）时返回冲突，不添加或替换实例。新服务同时带 MCP Direct 标记与本次导入的唯一所有权标记。导入失败只清理本次拥有的服务/固定实例及目标内容；若所有权改变或清理失败，保留禁用名称预留，不误删并发数据。提交前后重新检查共享固定身份/权限和源 Direct 地址。目标离线保留服务，删除目标仅清理目标自有服务，不删除源或 REF 服务；源取消共享不删除已导入的资源。REF 的运行可用性仍依赖原服务。

Hub 详情必须展示源固定版本已有的全部只读内容与原有交互，不能为了 Hub 给源详情加字段或删减源展示。MCP 共用源详情内容组件；Agent 共用运行时端点展示并通过 Hub ID 读取源快照；Prompt 保留模板调试、变量、审核结果、业务标签和该版本标签；AgentSpec 保留 AGENTS.md、资源浏览和版本审核信息。只有来源、下载计数和编辑/治理权限等 Hub 特有操作可以不同。

Agent Hub 的运行时快照保留源 namespace 和名称，Console 服务引用链接仍指向源真实 Naming 服务；输入的 Hub ID 和固定版本仍须通过共享授权校验。

Agent 的源详情与 Hub 必须共用 Agent 专用三列布局（左侧两列、右侧一列）、支持协议卡片，以及“Agent 元数据 / 自定义版本标签”侧栏。Hub 不得套用通用“基本信息 / 业务标签 / 版本标签”侧栏。Hub 在原有自定义版本标签卡片内以只读标签展示标签名，仅显示指向共享固定版本的自定义标签，不显示 latest 或其他版本的标签；没有标签时显示空态，不提供编辑和保存。源资源保留 JSON 编辑方式；下载计数显示在 Hub 来源区域。

五类资源统一提供“查看固定源版本”：入口由服务端单独校验源资源 READ 授权与可见性，不要求 WRITE 或管理员权限。仅有 Hub READ 不授予源详情读取。四类非 Skill 资源在 hub 元信息中返回 sourceReadable；仅在允许时返回 sourceNamespaceId/sourceName。跳转携带共享固定版本，不使用 latest。拒绝或校验不可用时隐藏入口且不影响合法 Hub 内容读取；源页面继续按普通接口重新鉴权。

MCP Hub 不显示源详情没有的通用“基本信息 / 业务标签 / 版本标签”侧栏卡片；右侧沿用源详情的客户端配置、端点信息和治理信息组件，不额外叠加通用资源侧栏。

MCP Hub 的自定义版本标签位于右侧资源治理信息下方，与 Agent 共用只读自定义版本标签卡片和样式，不再显示于顶部。读取固定版本响应中的 labels，仅显示指向共享固定版本的自定义标签，排除 latest 和其他版本的标签；无匹配标签时沿用 Agent 空态，不以 serving 详情中缺少标签字段判定为无标签。

Hub 标签只展示标签名，不拼接目标版本号；详情标题区不额外显示固定版本号徽标。此显示调整不改变固定版本读取、源版本跳转、下载或导入语义，源详情原有内容保持不变。
