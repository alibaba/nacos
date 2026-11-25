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

# 集成与适配器规范

本文定义 Nacos 可选集成与适配器模块的共享规则。它覆盖暴露外部协议、消费外部资源来源，
或提供控制台侧 assistant workflow 的集成模块。

AI Registry adapter 已有独立的
[AI Registry 适配器规范](../ai/ai-registry-adaptor-spec.md)。本文只做关联，不重新定义 MCP
registry、skills.sh 或其他 AI Registry 协议兼容面。

## 1. 定位

集成适配器在外部系统/协议模型和 Nacos 标准领域模型之间进行转换。它不是 Nacos 领域语义的拥有者。

适配器责任：

- 暴露外部协议形态的读 API 或 push stream；
- 当某个集成是事实来源时，将外部资源投影为 Nacos 资源；
- 在已有 Nacos 领域之上提供可选 assistant 或管理 workflow；
- 记录启用方式、鉴权、响应形态和失败边界。

适配器不得在所属领域规范之外创建新的 Config、Naming、AI、安全或插件语义。

## 2. 通用规则

- Nacos 标准行为由领域规范定义，而不是由 adapter response payload 或 route convention 定义。
- 当外部协议要求其他响应形态时，外部协议 API 可以有意不使用 v3 `Result<T>` wrapper。
- 引入未鉴权端点、大范围数据暴露或额外端口的 adapter 应默认要求主动开启。
- 除非所属领域明确记录 adapter 是 source-of-truth writer，否则 adapter 失败必须与核心领域变更隔离。
- 双向或 ingest adapter 必须记录归属、reconciliation、幂等和删除行为。
- Adapter 鉴权、可见性和异常处理必须明确。外部协议接口面可以使用插件式 exception handler，
  但不得重新定义 v3 HTTP API 错误模型。
- 兼容与移除决策遵循[兼容与废弃策略规范](../design/compatibility-deprecation-spec.md)。

## 3. 当前集成模块

| 模块 | 状态 | 方向 | 标准语义归属 |
| --- | --- | --- | --- |
| Prometheus service discovery | 可选 adapter | Nacos Naming 到 Prometheus SD JSON | [Naming 规范](../naming/README.md) |
| CMDB compatibility | 兼容性集成 | 外部 CMDB label 到 Nacos 查询/过滤路径 | [Naming 规范](../naming/README.md) |
| Istio adapter | 可选 adapter | Nacos Naming 到 Istio MCP/xDS resource | [Naming 规范](../naming/README.md) |
| K8s Sync | 可选 ingest adapter | Kubernetes Service/Endpoints 到 Nacos Naming | [Naming 规范](../naming/README.md) |
| Copilot console integration | 可选控制台 assistant | Console workflow 到 LLM assistant service | [Console 规范](../console/console-spec.md)，[AI Registry 规范](../ai/README.md) |
| AI Registry adaptor | 可选协议 adapter | Nacos AI Registry 到外部 AI registry protocol | [AI Registry 适配器规范](../ai/ai-registry-adaptor-spec.md) |

## 4. Prometheus Service Discovery

`prometheus` 模块暴露从 Naming service 和 instance 数据生成的 Prometheus service-discovery payload。

当前启用方式和接口面：

- 通过 `nacos.prometheus.metrics.enabled=true` 启用；
- 暴露 `/prometheus`、`/prometheus/namespaceId/{namespaceId}` 和
  `/prometheus/namespaceId/{namespaceId}/service/{service}`；
- 返回 Prometheus 兼容 JSON，而不是 Nacos v3 `Result<T>`。

规则：

- Prometheus service discovery 是 Naming 数据的只读投影。
- Payload 形态遵循 Prometheus discovery 预期，不得作为标准 Naming API 使用。
- 当 Nacos auth 启用时，Prometheus 模块会为 Prometheus route 添加专用 Basic authentication 和
  authorization filter。
- `PrometheusApiExceptionHandler` 作为 adapter 专属 exception handler 是允许的，因为该接口面不是
  v3 HTTP API；它不得被复制到普通 Nacos 领域 controller。

## 5. CMDB Compatibility

`cmdb` 模块围绕外部 CMDB label 和 entity lookup 提供兼容性集成。它包含 `CmdbReader`、
`CmdbWriter` SPI、本地加载任务，以及 `/v1/cmdb/ops/label` 下的运维查询 route。

规则：

- CMDB label 是可选外部 metadata，不是标准 Naming service、instance 或 cluster metadata 模型。
- 新的 Naming selector 或 filtering 行为不得依赖 CMDB 作为标准路径。
- 除非后续 Naming 规范提升新的资源模型，否则 CMDB 集成应保持兼容性定位。

## 6. Istio Adapter

`istio` 模块将 Nacos Naming 资源映射为 Istio MCP 和 xDS resource stream。

当前启用方式和接口面：

- 模块加载由 `nacos.extension.naming.istio.enabled=true` 控制；
- 模块要求 Naming 或 microservice function mode；
- 独立 gRPC server 由 `nacos.istio.mcp.server.enabled` 控制；
- `nacos.istio.mcp.server.port` 默认是 `18848`；
- 模块根据 Nacos service 信息生成 ServiceEntry 相关 MCP/xDS payload 等 Istio resource。

规则：

- Nacos service 和 instance 语义仍由 Naming 规范定义。
- Istio MCP/xDS 响应形态遵循 Istio 和 Envoy 协议预期。
- Istio MCP/xDS stream 使用的初始快照和后续快照必须按已配置的 Istio 设置生成资源，
  包括在后续 Naming 变更事件发生前的首次连接。
- 实例注册或注销时，必须在对应 Naming 发布者索引更新后刷新 Istio 视图。
- 服务变化事件也会在客户端释放、健康状态及实例元数据变化时刷新 Istio 视图；服务元数据变化仍需订阅。
- Adapter 必须通过 debounce 和 push 行为容忍 Naming 变化，且不得成为权威 Naming store。
- 当启用该 adapter 时，部署文档必须记录端口暴露、鉴权和网络放置方式。

## 7. K8s Sync

`k8s-sync` 模块把 Kubernetes Service 和 Endpoints resource 投影到 Nacos Naming resource。

当前启用方式和行为：

- 通过 `nacos.k8s.sync.enabled=true` 启用；
- 可以在 Kubernetes 集群内运行，也可以通过 `nacos.k8s.sync.outsideCluster=true` 和
  `nacos.k8s.sync.kubeConfig` 在集群外运行；
- 使用 Kubernetes informer 监听所有 namespace；
- 在 `DEFAULT_GROUP` 中创建 Nacos service；
- 创建 `ephemeral=false` 的持久 Nacos instance。

规则：

- Kubernetes 是这条 adapter 路径的上游来源。Nacos 存储其投影后的 Naming 视图。
- Kubernetes informer 可能重放 add、update 和 delete event，因此更新必须具备幂等性。
- 删除处理必须移除由 Kubernetes resource 拥有的投影 Nacos instance/service。
- 没有明确 reconciliation 规则时，运维人员不应混合手动管理同一个投影 service。

## 8. Copilot Console Integration

`copilot` 模块为 prompt debug、prompt optimization、skill generation 和 skill optimization
提供控制台 assistant workflow。

当前启用方式和接口面：

- 自动配置默认启用，除非设置 `nacos.copilot.enabled=false`；
- 当 `nacos.deployment.type=server` 时不会加载该模块；
- 控制台 route 位于 `/v3/console/copilot/*`；
- 流式操作使用 server-sent events，而不是普通 JSON response wrapper；
- LLM 访问通过 `nacos.copilot.connections.<name>.*` 配置的部署侧模型连接提供；没有可用部署侧
  连接时回退到由已声明的 `nacos.copilot.apiKey`、`nacos.copilot.model`、已存旧字段或
  `COPILOT_API_KEY` 映射出的 `legacy` 连接；没有真实的旧配置声明时，不生成虚构的 `legacy`
  连接，也不输出废弃告警；
- `nacos.copilot.selectedConnection` 指定控制台选中的连接；
- AgentScope Studio 通过 `nacos.copilot.studioUrl` 和 `nacos.copilot.studioProject` 配置。

生效配置在每次刷新时各读取一次三路输入，再按字段解析：部署配置、`copilot-config.json` 与
纯内置默认值（不能使用已绑定部署值的 properties bean）。全局字段按部署声明、已存值、默认值
的优先级解析；部署侧声明的凭据与选择同时带出来源，控制台可以区分部署值与已存值。因此升级时
已存在的旧 `copilot-config.json` 不会掩盖新部署配置中显式设置的值。

字符串字段为三态：某来源缺失该字段时继承更低来源；声明为 `null`、空串或纯空白表示清除，
并阻断所有更低来源。`false` 与 `0` 是实值，类型与必填校验仍然保留。已存文档按原始 JSON
读取，因此"未包含该字段"与"显式清除该字段"可以区分；清除也不通过丢掉声明来表达，否则旧值
会重新出现。已存 JSON `null` 清除声明在保存其他字段时仍会保留。设置、连接表及其来源一次
发布到同一快照。

升级影响：部署配置中的空白占位值现在表示清除，不再继承已存值；旧连接凭据优先级改为部署、
已存、环境变量，不再是环境变量优先。升级前请检查已有占位值与凭据。

`nacos.copilot.selectedConnection` 遵循同一三态规则：部署侧空值现在表示清除，路由使用
`Automatic`，而不再像上一版本那样让已存选择继续生效。连接表按来源独立合并：部署侧连接以完整
定义替换归一化名字相同的连接，不继承任何已存或旧配置字段。

模型连接规则：

- 部署侧连接使用 `nacos.copilot.connections.<name>` 前缀，字段包括 `providerId`、`model`、
  `apiKey`、`baseUrl`、`endpointPath`、`displayName`、`enabled`、`stream`、
  `enableThinking` 与 `options`；`<name>` 是连接名，`legacy` 是旧配置映射出的连接名，同名部署侧
  连接会替换它。
- 连接名按 Spring 的 relaxed key 规则解析：`Prod-Qwen`、`PROD-QWEN` 与 `prod-qwen` 指同一个
  连接，`selectedConnection` 在合并、查找与选择前去掉首尾空白并按 `Locale.ROOT` 转小写。
  `<name>` 是配置 key 的一个元素；含点号或非 ASCII 字符的声明 Spring 无法解析，会被忽略。
  下划线不是连接名分隔符：`prod_qwen` 解析为另一个连接 `prodqwen`，不是 `prod-qwen`。
- 同一连接以多种拼写声明时，各拼写属于同一块配置，字段合并到同一个连接；不再按拼写择一，
  也不忽略其余拼写的字段。
- 连接块中的字段按 Spring 的优先级解析：JVM 系统属性高于环境变量，环境变量高于配置文件；
  环境变量把点号与连字符换成下划线，例如 `NACOS_COPILOT_CONNECTIONS_PROD_QWEN_MODEL` 与
  `NACOS_COPILOT_CONNECTIONS_PROD_QWEN_API_KEY`。高优先级的显式空值会阻止更低优先级取值。
  该规则只作用于 key，模型、凭据与选择等取值按原样使用，不做 relaxed 转换。
- 连接按来源合并：部署侧连接替换同名的旧配置连接；聚合器对任一 reader 返回的重复连接
  保留第一个并告警。
- 选择顺序为：`nacos.copilot.selectedConnection` 指定的连接、第一个可用部署侧连接、
  第一个可用 `legacy` 连接。连接先按优先级、再按连接名排序，因此同一来源内的并列顺序是
  确定的。
- 加载期校验每个连接：`providerId` 与 `model` 必填；`legacy` 凭据按部署、已存、
  `COPILOT_API_KEY` 的顺序解析。最高声明层的显式清除阻断所有更低来源；已存清除不能抹掉
  部署凭据。清除凭据后该连接不可用，而不是把凭据交给 provider 自己的环境变量
  兜底；先确认 classpath 上存在对应 provider
  （`canResolve`），再用 `resolve` 探测构造错误；显式设置的 `baseUrl` 必须为 https 且解析
  到公网地址，除非其 host 命中 `nacos.copilot.base-url.allowlist`。
- 校验失败的连接只标记为不可用并保留诊断原因，不影响其他连接继续服务；上游 provider
  的诊断信息按原样保留，不得替换为通用错误。
- 生效的 `enabled` 为 false 时 Copilot 拒绝新请求，因此路由不返回活动连接，但连接表
  仍保留供管理查看。
- 连接字段只在运行时从部署配置读取；通过通用配置接口写入 `copilot-config.json` 的同名字段
  不会覆盖部署连接。
- 刷新只作用于当前节点；在一个控制台保存不会隐式刷新其他控制台。各节点需要相同的连接配置与
  provider 依赖，并对齐存储服务地址、命名空间及读写权限；共享已存配置不等于运行快照同步。

示例：

```properties
nacos.copilot.connections.prod-qwen.providerId=dashscope
nacos.copilot.connections.prod-qwen.model=qwen-max
nacos.copilot.connections.prod-qwen.apiKey=<api-key>
nacos.copilot.selectedConnection=prod-qwen
```

配置刷新规则：

- merged 部署在配置文件变化时发布 `ServerConfigChangeEvent`，Copilot 订阅该事件并重新绑定
  `nacos.copilot.*`，连接表、`selectedConnection`、`enabled` 等部署配置一并生效。
- `selectedConnection` 指定的连接不存在、被禁用或不可用时，路由回退到默认选择顺序，并在
  刷新时输出告警。
- 纯控制台部署不监听配置文件，必须重启生效。
- 运行中删除的配置键不会在刷新时消失，需要重启；环境变量不会触发热更新。

控制台配置 API 规则：

- `GET /v3/console/copilot/config` 返回可编辑的已存字段（`model`、`studioUrl`、
  `studioProject`、`selectedConnection`）、生效选择 `effectiveSelectedConnection` 及其
  来源 `selectedConnectionSource`、脱敏后的连接列表，以及路由得到的
  `activeConnection`（Copilot 被禁用或没有可用连接时为 `null`）。每个连接只暴露 `name`、`providerId`、`displayName`、`model`、
  `source`、`enabled`、`available`、`unavailableReason`、`apiKeyConfigured` 和
  `actualSource`；已保存的 apiKey 以掩码返回，连接元数据中既不包含凭据，也不包含
  `actualSource` 背后的值。`legacy` 连接从 `COPILOT_API_KEY` 解析到密钥时
  `actualSource` 为 `ENVIRONMENT`，密钥由部署配置声明时为 `DEPLOYMENT`，来自控制台
  已存值时为 `CONSOLE`，连接没有凭据时为 `null`。
- `selectedConnectionSource` 在部署配置声明了 `nacos.copilot.selectedConnection` 时为
  `DEPLOYMENT`，生效选择来自控制台已存值时为 `STORED`，没有配置任何选择时为 `NONE`。
  部署配置优先于已存值，因此控制台保存的选择会保留，但在部署声明存在期间不生效；
  `Automatic` 同理，它会清除已存选择，但不能覆盖部署值。API 同时返回生效选择和来源，
  让控制台可以解释该状态，而不是把已存值当作正在使用的值展示。部署值为空或仅含空白时
  表示清除，选择变为 `Automatic`，已存选择不再生效；由于没有选中连接，生效来源仍为 `NONE`。
- `POST /v3/console/copilot/config` 只接受 `apiKey`、`model`、`studioUrl`、
  `studioProject` 和 `selectedConnection`，其他字段被忽略；请求中缺失的字段保留已存值。
  只有提交的字段会写入已存文档，未提交的字段不会用部署值或默认值回填。
- apiKey 为三态：缺失或掩码值保留已存密钥；非空值替换密钥；空值清除已存密钥，并标记
  `legacy` 连接不再回退到 `COPILOT_API_KEY`。没有已存密钥时提交空值是无操作，避免未改动的
  旧版控制台表单开始阻断环境变量。部署配置显式设置的 `nacos.copilot.apiKey` 仍按既有优先级
  生效。
- `selectedConnection` 写入前会去除首尾空白；空值为 `Automatic` 保存 JSON `null` 清除声明，
  保存其他字段时仍保留。部署未声明选择时路由回到默认顺序。非空值必须
  指向存在、已启用且可用的连接：保存的是连接的声明名；选择不存在、被禁用或不可用时返回
  HTTP 400，而不是保存该值。
- 选择校验读取最近一次刷新得到的连接表，校验本身不做 provider 探测或 DNS 查询。但发布触发
  的刷新仍在该请求线程上同步执行完才返回，刷新中的连接校验可能解析显式设置的 `baseUrl`，
  因此整个保存请求并非不含可用性检查 I/O；配置了 Studio 时也会等待其初始化。因此
  "提交新凭据并同时选择 `legacy`" 会被拒绝，需要先保存凭据、待刷新把该连接标记为可用后再选择；控制台按两次请求提交。清空凭据并同时
  提交同一选择仍是合法的配置更新，在随后一次刷新后生效。保存请求返回时已经完成由发布触发
  的刷新，`GET` 直接反映结果表，不存在"校验中"的中间状态。异步保存/校验及 `configValid`
  迁移仍留待 Step5；当前 API 继续使用 `available` 与 `unavailableReason`。
- 已存文档无法读取或不是合法 JSON 对象时，读取接口返回 HTTP 503 / `SERVER_ERROR`，保存
  请求在写入前被拒绝；不返回凭证细节或虚构默认值。JSON 后的多余内容也视为非法文档。
- 保存依次执行：校验补丁、读取已存文档、仅持久化补丁字段（条件更新或受保护的首次创建），然后
  统一解析并原子激活新快照，才返回成功。持久化前读取失败时拒绝保存，不写入；持久化失败时
  内存配置不变。本地及远程保存都以已存文档的 MD5 做 compare-and-set，冲突时重新读取并应用
  补丁；并发首次创建也不能相互覆盖。持久化成功后本地读取偶发失败仍是延后处理的缺口：只记录
  日志，不做补偿，也不引入额外状态协议。运行时激活抛出异常时，API 返回 HTTP 500 /
  `SERVER_ERROR`，明确说明 "stored but not activated" 并附脱敏原因；节点保留上一份快照，
  直到再次刷新。
- 已存的 apiKey 与 model 作为废弃迁移字段保留可编辑，即使当前没有运行时 `LEGACY` 连接。
  新部署应声明 `nacos.copilot.connections.*`。
- 真实的 `LEGACY` 连接每次刷新都会输出带 `nacos.copilot.connections.*` 迁移提示的废弃告警，
  弃用窗口内继续服务。纯默认值，以及仅命名为 `legacy` 的部署连接，都不会触发该告警。

规则：

- Copilot 是控制台侧 assistant 集成。它不重新定义 AI Registry resource lifecycle、Config
  语义或 Naming 语义。
- Copilot console route 仍必须遵循 Console API 鉴权和 AI `SignType` 规则。
- Copilot 返回的 prompt/skill artifact 必须通过所属 AI resource API 校验后，才能成为标准资源。
- API key 和模型凭据不得通过 trace、metrics、server state 或 assistant stream payload 暴露。
  凭据管理响应必须保持为带明确读写鉴权的 Console API 操作。

## 9. 与 AI Registry Adaptor 的边界

AI Registry 协议兼容由
[AI Registry 适配器规范](../ai/ai-registry-adaptor-spec.md)负责。该 adapter 可以暴露外部 registry
protocol route、绑定额外端口，或遵循外部响应形态。其行为仍必须遵守本文的主动开启、安全和
事实来源规则。

## 10. 相关规范

- [Nacos 设计规范](../design/nacos-design-spec.md)
- [兼容与废弃策略规范](../design/compatibility-deprecation-spec.md)
- [Naming 规范](../naming/README.md)
- [Console 规范](../console/console-spec.md)
- [AI Registry 规范](../ai/README.md)
- [AI Registry 适配器规范](../ai/ai-registry-adaptor-spec.md)
- [可观测钩子规范](../design/foundation-observability-hooks-spec.md)
