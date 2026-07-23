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

# Nacos 插件化规范

## 目的

Nacos 使用插件机制和 SPI 扩展，将横切基础能力和可替换的领域能力从固定核心中拆出。
插件可以提供鉴权、资源可见性、数据源方言、加解密、链路追踪、流量控制、环境适配、
AI pipeline、AI 存储、AI 资源导入或 Java 客户端侧请求适配等能力。

插件机制的目标，是在保持 Nacos 核心模型稳定的同时，让不同部署环境可以选择符合自身
身份系统、数据库、观测体系或扩展场景的实现。

## 插件身份

每个插件由以下字段唯一标识：

- `pluginType`：扩展类别，例如 `auth` 或 `visibility`。
- `pluginName`：该类别下的实现名称，例如 `nacos`。
- `pluginId`：运行时标识，格式为 `{pluginType}:{pluginName}`。

`pluginId` 用于管理 API、集群状态同步、插件状态持久化和面向用户的诊断信息。

## 插件类型

当前插件类型注册表由 `PluginType` 定义。

| 类型 | 目的 | 契约 |
|------|------|------|
| `auth` | 认证与授权实现。 | [鉴权插件规范](../auth/auth-plugin-spec.md) |
| `visibility` | 资源可见性与查询可见性建议。 | [可见性插件规范](../auth/visibility-plugin-spec.md) |
| `datasource-dialect` | 数据库方言与持久化适配。 | [数据源方言插件规范](datasource-dialect-plugin-spec.md) |
| `config-change` | 配置变更扩展。 | [配置变更插件规范](config-change-plugin-spec.md) |
| `encryption` | 加解密扩展。 | [配置加密插件规范](config-encryption-plugin-spec.md) |
| `trace` | 链路追踪与观测扩展。 | [Trace 插件规范](trace-plugin-spec.md) |
| `environment` | 环境适配扩展。 | [环境插件规范](environment-plugin-spec.md) |
| `control` | 流量与控制扩展。 | [Control 插件规范](control-plugin-spec.md) |
| `ai-pipeline` | AI 注册中心 pipeline 扩展。 | [AI 发布 Pipeline 插件规范](ai-pipeline-plugin-spec.md) |
| `ai-storage` | AI 注册中心存储扩展。 | [AI 存储插件规范](ai-storage-plugin-spec.md) |
| `ai-resource-import` | AI 注册中心外部资源导入扩展。 | [AI 资源导入插件规范](ai-resource-import-plugin-spec.md) |

各插件类别的领域契约由对应规范定义。本文档定义所有插件类别共享的运行时契约。

[寻址扩展](addressing-plugin-spec.md)为了和公开插件文档保持连续性，也放在插件
规范中记录；但当前服务端代码通过 `MemberLookup` 处理寻址，并未将其注册到
`PluginType`。

## 运行位置

Nacos 有两类插件式扩展面：

| 运行位置 | 加载模型 | 状态归属 | 示例 |
|----------|----------|----------|------|
| 服务端插件 | 领域 SPI 加 `PluginProvider`，在支持时可由服务端插件 API 列出和管理。 | Nacos 服务端进程；对可管理插件，还包括服务端插件状态。 | `auth`、`visibility`、`datasource-dialect`、`control`、`trace`。 |
| Java 客户端扩展 | 在客户端进程内通过 Java SPI 或 SDK API 加载。 | 客户端 classpath、客户端配置和 SDK 实例生命周期。 | `ServerListProvider`、`ClientAuthService`、`IConfigFilter`、客户端侧配置加密。 |

客户端扩展不由 `/v3/admin/core/plugin/*` 管理，也不具备服务端
`PluginStateCheckerHolder` 决策，除非对应服务端插件同时参与请求处理。它们仍必须遵守
Nacos 资源身份、鉴权和 payload 语义，因为它们会影响 SDK 发出的请求。

## 执行形态

插件类别并不都以同一种形态执行。每个插件类型都必须明确自身执行形态。

| 形态 | 含义 | 示例 |
|------|------|------|
| `EXCLUSIVE` | 在进程或请求范围内选择一个实现，其他已加载实现不参与该次判断。 | `auth`、`datasource-dialect`、`control` |
| `ROUTED` | 可以加载多个实现，但领域根据配置、资源元数据或请求上下文选择一个服务。 | `encryption`、`visibility`、`ai-storage`、`ai-resource-import` |
| `CHAIN` | 多个匹配插件按稳定顺序执行。每个节点可以贡献结果，失败是否中断由领域定义。 | `config-change`、`environment`、`ai-pipeline` |
| `BROADCAST` | 多个订阅者观察同一个事件或 trace 点，不拥有主决策权。 | `trace`、事件型扩展 |

对于链式插件，领域 SPI 必须定义：

- 如何根据资源或 pointcut 选择候选插件。
- 哪个字段控制顺序，例如 `getPreferOrder()` 或 `getOrder()`。
- 执行方式是串行还是并行。
- 某个插件失败时，是中断链路还是只记录失败结果。
- 如何持久化和暴露部分执行结果。

核心插件管理器记录插件的加载状态和启用状态，本身不定义执行形态。领域管理器负责稳定地
应用对应执行形态。

执行形态和关键能力属于插件类型，而不是某个内置实现。共享 `PluginType` 必须暴露
`executionMode` 和 `critical`；已有 `exclusive` 信息继续由
`executionMode == EXCLUSIVE` 推导，以保持 API 兼容。插件实现是否可配置由
`PluginConfigSpec.isConfigurable()` 决定，同一插件类型下允许同时存在可配置和零配置实现。

## SPI 层次

Nacos 插件包含两个相关的 SPI 层次：

1. 领域 SPI，例如 `AuthPluginService` 或 `VisibilityService`，定义所属领域需要的行为。
2. 核心插件 SPI，即 `PluginProvider`，将插件实例暴露给核心插件管理器，用于列表查询、
   状态管理、配置管理和运行时观测。

已接入统一配置的领域插件 SPI 统一继承 `PluginConfigSpec`。该契约的兼容默认实现返回空
definitions、空 current map，并提供空 apply 回调，因此按旧版领域 SPI 编译的实现和新版
零配置实现都会保持 `configurable=false`。声明至少一个 `ConfigItemDefinition` 的插件属于
可配置实现，必须实现 current-map 和 apply 回调。`environment`、`control` 在统一 bootstrap
配置生命周期完成设计前继续作为例外；`ai-resource-import` 在自身重构前仍不进入统一管理。
支持启停状态判断的插件类别，应通过 `PluginStateCheckerHolder` 获取状态，而不是维护一套
独立状态来源。

`PluginConfigDefinitionSpec` 是仅暴露 definitions 的父契约，供必须在创建实例之前声明
配置元数据的 factory 使用。参与统一配置生命周期的运行时插件实例仍必须实现完整的
`PluginConfigSpec`；只实现 definition contract 的 factory 不接收也不持有 effective config。

## 加载与生命周期

插件实现通过 Nacos SPI 加载。部署时可以从 classpath 或服务端插件目录提供插件。
插件实现必须能在不修改 Nacos 服务端代码的情况下被加载。

核心 `PluginManager` 会在 Spring 上下文刷新完成后先发现轻量 `PluginProvider` 实现。
只有领域 policy 当前允许加载的插件类型才会立即调用 `getAllPlugins`；active critical 类型
不受可选加载判据影响，必须加载。对于被延迟的非 critical 类型，后续服务配置刷新使加载
判据变为 true 时，必须先发现实现、恢复持久化实现 state、解析 effective config 并调用
`applyConfig`，然后才能让这些实现参与执行。类型一旦加载，加载判据再次变为 false 时不卸载
实例，仍由所属领域入口总开关阻止执行。

加载判据不能替代实现级 state。为保持二进制兼容，其默认值为 true；只有拥有类型级模块或
能力总开关的领域才应覆盖该判据。尚未发现的实现不能通过插件 API 定向启用，延迟类型应先
通过其静态或领域总开关开启。

`ApplicationReadyEvent` 只作为非标准嵌入启动流程的幂等兜底。领域管理器也可以通过 SPI
提前构造自身领域服务，但选择延迟加载的类型在加载判据为 false 时不得自行实例化实现。
服务端进入可用状态前的最终配置和是否可参与请求处理，仍必须遵守核心插件管理器的统一结果。

插件启动必须具备确定性：

- 一个插件类型和插件名称组合只能对应一个运行时插件实例。
- 同一插件类型下重复的插件名称不适合稳定运行。
- 插件实现不得改变 Nacos 共享资源标识、响应封装或错误约定的含义。

## 状态与配置

插件状态分为两个层次：

- 已加载：实现存在于运行时。
- 已启用：实现可以参与请求处理。

核心模块开关和插件状态是两个独立层次。`nacos.core.auth.enabled`、
`nacos.core.auth.admin.enabled` 和 `nacos.core.auth.console.enabled` 等模块开关决定
核心请求链路是否调用插件，不属于插件实现配置，也不得由插件管理 API 修改。模块关闭时，
插件仍可以保持加载、启用和完成配置初始化。

每个纳入统一管理的插件类型都可以提供一个由领域模块持有的内部 `PluginTypePolicy`，而不是
由核心插件管理器维护领域判断。该 policy 负责定义：

- 当前领域是否需要该插件类型；
- 非 critical 类型当前是否允许加载实现；
- 每个已发现实现的初始 enabled 状态；
- critical 类型处于 active 状态时必需存在的具体实现名称；
- 诊断信息中使用的选择配置和激活原因。

核心在发现插件前对每个 policy 执行一次初始化。具有 `RESTART` 语义的实现选择和 provider
配置必须在该阶段形成快照；后续服务配置刷新可以重新判断动态模块开关是否 active，但在
Nacos 重启前不得改变要求的具体实现。

`PluginType.isCritical()` 继续作为“该类型可能是服务正确运行所必需”的唯一静态声明。
只有领域 policy 处于 active 状态时，core 才校验该 critical 类型。通用校验由核心管理器
完成，核心管理器不得继续持有各插件类型的属性 key 或选择分支。

Nacos 报告启动成功前，每个 active critical 类型都必须已经发现并启用 policy 要求的全部
具体实现。active 互斥类型没有选择实现、要求的实现不存在，或要求的实现被禁用，均属于
启动错误。错误必须明确给出插件类型、要求的实现及相关选择配置；Nacos 不得静默选择或重新
启用任意 fallback 实现。

若 provider 在 Spring context refresh 前已经可以暴露可用实例，所属 policy 必须支持
pre-refresh 校验，使 auth、datasource 等缺失实现在依赖它们的业务 Bean 创建前直接失败。
若实现必须依赖 Spring 管理的资源才能构建，policy 必须声明不支持 pre-refresh 校验；统一
管理器在 context refresh 后、Nacos 报告启动成功前继续校验该类型。延后校验不得弱化对必需
实现及 enabled 状态的约束。

运行时状态变更生效前、状态快照恢复后，以及服务配置刷新导致 policy active 状态变化后，
都必须执行同一校验。校验失败时不得应用候选插件状态。插件 detail 中的 `critical` 表示该
具体 enabled 实现当前是否必需，而不是仅表示其类型是否曾有可能成为 critical。

持久化状态变更必须遵循 validate、persist、apply 顺序：先校验完整候选终态，再写入持久化
状态；只有持久化成功后才能修改管理器内存状态。持久化失败时，内存保持原值并允许重试。
`localOnly` 状态变更按定义跳过持久化，只应用到当前节点。

`plugin_state` 一致性组可能在 Spring context 创建期间、统一管理器尚未发现插件实现时恢复
快照。该阶段管理器必须先校验快照值格式，并暂存或持久化完整状态，不能把空 registry 误判为
critical 实现缺失。统一启动流程随后发现 provider、合并暂存状态，并在 Nacos 报告启动成功前
执行同一套严格 critical 校验。管理器初始化完成后的快照恢复仍必须在应用候选终态前完成校验。
快照中的 `states` map 表示完整的持久化 override，而不是增量 patch：恢复时必须整体替换本机
持久化 map；快照中缺失的条目用于移除本机陈旧 override，已加载的非互斥实现恢复为启动 policy
默认状态；尚未加载实现的条目继续持久化，并在对应插件类型后续加载时应用。互斥实现的选择仍由
要求重启的选择配置控制。

统一状态迁移阶段对现有内置开关的排查结论如下：

| 配置 | 归属与迁移行为 |
|------|----------------|
| `nacos.core.auth.enabled`、`nacos.core.auth.admin.enabled`、`nacos.core.auth.console.enabled` | 核心请求入口开关，不进入 plugin state。 |
| `nacos.extension.ai.enabled` | AI 模块开关，不进入 plugin state。 |
| `nacos.core.config.plugin.{name}.enabled` | 历史实现开关，仅作为 `nacos.plugin.config-change.{name}.enabled` 的初始状态兼容 alias。 |
| `nacos.plugin.visibility.enabled`、`nacos.plugin.ai-pipeline.enabled` | 已有领域能力入口开关；分别决定核心链路是否进入 visibility 或 AI pipeline，保留动态读取且不转换为子插件状态。 |
| `nacos.plugin.visibility.type` | 历史 visibility 选择 key，仅用于推导对应实现的初始状态；运行时路由从 enabled 实现中按领域输入选择。 |
| `nacos.plugin.ai-pipeline.type` | 历史 Pipeline 链成员输入，仅由 Core 按 `RESTART` 推导实现初始状态；实现配置和顺序统一使用各节点的 `PluginConfigSpec`。 |
| `nacos.plugin.datasource.log.enabled` | 数据源行为和日志配置，不是实现启停状态。 |
| `nacos.ai.resource.import.enabled` | 历史 AI import 链路，随 AI importer 重构另行移除或迁移。 |

后续不得新增与逐实现 state 含义重复的插件族开关。核心模块或领域能力入口开关可以决定是否
进入整项能力，但不能选择或启停某个具体实现；具体实现是否参与执行只能由逐实现 plugin
state 表达。

已接入统一启动选择的互斥插件类型通过以下标准静态 key 选择实现：

```text
nacos.plugin.{pluginType}.type={pluginName}
```

该选择属于启动期配置，统一按 `RESTART` 生效。历史选择 key 仅作为 alias：

| 类型 | 标准 key | 历史 alias | 默认值 |
|------|----------|------------|--------|
| `auth` | `nacos.plugin.auth.type` | `nacos.core.auth.system.type` | `nacos` |
| `datasource-dialect` | `nacos.plugin.datasource-dialect.type` | `spring.sql.init.platform` | `derby` |

标准 key 与 alias 同时存在时标准 key 优先，读取 alias 时服务端应记录迁移提示日志。当前
互斥类型的选择会影响 Spring Bean、数据源等启动资源，插件 status API 不得把切换报告为
运行时已生效；修改选择必须更新上述静态 key 并重启。只有领域实现具备受控重建生命周期后，
才能进一步开放对应类型的运行时切换。
`control` 仍属于 bootstrap 特例，当前选择 key 为
`nacos.plugin.control.manager.type`。在 control manager 具备受控重建生命周期且选择 key
迁移为标准格式之前，管理 API 只反映启动时选中的 builder，并拒绝运行时状态切换。

非互斥插件实现可以通过以下标准静态 key 提供初始启用状态：

```text
nacos.plugin.{pluginType}.{pluginName}.enabled=true|false
```

运行时由插件管理 API 和统一 plugin state 管理。存在持久化状态时，持久化状态优先于静态
初始值。`nacos.plugin.{pluginType}.enabled` 这类不包含实现名称的 key 不属于统一实现状态；
已有 key 若实际承担核心模块或领域能力入口门禁，应继续由所属领域读取，且不得被持久化子
插件状态绕过。链式和广播型插件的全部 enabled 实现参与执行；路由型插件只允许从 enabled
候选中选择实际实现。

`critical=true` 表示已激活的插件类型必须保留 policy 要求的可用实现，而不是所有内置实现
都不能关闭。当前关键类型包括 `auth`、`datasource-dialect` 和 `ai-storage`。所属领域 policy
负责判断类型何时 active 以及要求哪些具体实现，核心管理器负责在这些实现缺失或被禁用时拒绝
启动。管理 API 同样必须拒绝会使 active critical 类型失去所需可用实现的更新。模块总开关
继续归核心领域所有，而不会被插件状态反向接管。

已有响应字段 `critical` 继续表示“当前具体实现是否不能被单独关闭”，因此它会随同类型其他
实现的状态动态变化。列表和详情响应追加 `typeCritical` 和 `executionMode`；已有
`exclusive` 字段保留并从执行形态推导。

`PluginConfigSpec.isConfigurable()` 返回 `true` 的插件应暴露配置定义、当前配置和配置应用
行为；默认实现仅在 `getConfigDefinitions()` 非空且非空列表时返回 `true`。除非请求明确
声明为仅本机生效，否则集群级状态或配置变更必须通过插件状态操作链路进行同步。

### 配置定义

插件配置项由 `ConfigItemDefinition` 描述。`key` 表示插件实现内部的 canonical item
key，不携带 `nacos.plugin.{pluginType}.{pluginName}.` 前缀。静态配置推荐使用以下
normalized full key：

```text
nacos.plugin.{pluginType}.{pluginName}.{itemKey}
```

配置定义可以声明以下元数据：

| 字段 | 含义 |
|------|------|
| `aliases` | 历史静态配置 key，用于兼容读取和迁移提示。 |
| `sensitive` | 是否为敏感值。查询 API 返回前必须脱敏。 |
| `effectMode` | 生效模式，`RUNTIME` 表示可运行时生效，`RESTART` 表示需要重启。 |

`aliases` 用于静态配置兼容读取，也可以作为迁移兼容的 API 输入。使用 alias 时应记录
迁移提示日志。完成归一化后，alias
不应写入运行时持久化文件或 local-only 内存表。如果输入同时包含同一配置项的多个
alias，则按定义中的声明顺序取第一个生效，并由服务端记录其余 alias 被忽略的日志。
`enabled` 是插件实现统一状态的保留 item key，插件不得在 `ConfigItemDefinition` 中将其
声明为普通配置项。

### 配置来源与值元数据

插件配置的 effective value 由统一解析流程计算。配置来源优先级为：

```text
LOCAL_ONLY > RUNTIME_PERSISTED > STATIC > DEFAULT
```

| 来源 | 含义 |
|------|------|
| `DEFAULT` | 来自 `ConfigItemDefinition.defaultValue`。 |
| `STATIC` | 来自 `application.properties`、环境变量、JVM 参数或 Spring 参数等静态配置。 |
| `RUNTIME_PERSISTED` | 来自集群级运行时 override，当前可由 `plugin-configs.json` 记录终态内容。 |
| `LOCAL_ONLY` | 当前节点的本机 override，只用于诊断或应急处理，不同步到集群。 |

插件详情返回模型可以追加以 canonical item key 为索引的 `configValueMetas` map。每个
`PluginConfigValueMeta` 用于描述对应配置项的当前值来源和是否存在多来源覆盖。
`overridden` 忽略 `DEFAULT`，只有同一 key 同时存在多个非默认来源时才应为 `true`。

运行时持久化配置和 local-only 配置只保存 `pluginId + itemKey` 对应的值，不保存
normalized full key、alias key、source 或版本信息。

runtime persisted source resolver 负责完整的持久化生命周期：在插件配置初始化前加载
全部 source；单插件更新时先持久化归一化后的完整 map，再替换内存 source；为一致性
快照导出内存终态 map；恢复快照时先完整替换持久化 source，再应用插件配置。
插件编排层不得直接读写 `plugin-configs.json`。插件 enabled state 的持久化仍由状态管理
链路负责。

每个内部 source resolver 都必须通过 `getConfig(PluginInfo)` 返回使用 canonical
item key 的完整 map。读取能力与写入能力相互独立：`DEFAULT` 从 definition 读取默认值，
`STATIC` 根据标准 key 和 alias 从环境读取，两个运行时 source 读取各自内部 map。
`isUpdatable` 只在替换 source map 时检查。每次更新完整替换该 source 的 map；传入空
map 表示清空该插件在该 source 下的全部 override，不额外提供 remove 或 restore 操作。

core source registry 统一持有已启用 resolver 及其固定顺序。四个内置来源必须按上述
顺序注册；内部存储实现可以替换同一 source 层的 resolver，但不能在 `LOCAL_ONLY` 之上
插入新优先级，也不能把 `DEFAULT` 合并进 `STATIC`。source 实现的选择属于启动期行为，
插件配置更新 API 不负责动态切换 source 实现。

### 运行时状态约束

对于每次运行时操作都会重新选择实现的插件类型，领域执行链路必须在调用扩展前检查统一插件
状态。当前使用该门禁的插件包括 `auth`、`datasource-dialect`、`encryption`、`trace`、
`visibility`、`config-change`、`ai-pipeline` 和 `ai-storage`。被禁用的插件仍保持加载并
可由管理 API 查询，但不得参与领域执行。门禁不等于实现选择：互斥类型仍按启动期 `type`
选择，路由类型仍由领域路由决定，链式和广播类型执行全部 enabled 实现。

插件类型的执行形态和 critical 能力必须由共享的 `PluginType` 定义提供。Core 和 Console
的 API 适配层不得分别维护硬编码的互斥类型或关键实现列表。

启动期或构建期插件不能通过较晚的运行时检查满足该契约。`control` 会在统一持久化状态加载前
构建并缓存 manager，`environment` 会在 core 插件管理器就绪前转换 Spring 属性。管理 API
能够把这两类插件的状态更新报告为已生效之前，必须先定义其状态能力和重启/bootstrap 语义。
`ai-resource-import` 当前没有通过 `PluginProvider` 暴露，不属于统一状态管理范围。

### 配置更新兼容性

插件详情 API 应保持 additive 兼容：已有 `config` 和 `configDefinitions` 字段继续
保留，其中 `config` 可以表示当前 effective config，新增的 `configValueMetas` map 按
canonical item key 提供 source 和 overridden 等元信息。

`PUT /v3/admin/core/plugin/config` 和对应 Console API 保持现有完整 override map
更新语义。`localOnly=true` 表示只更新当前节点 local-only override；否则更新集群级
runtime persisted override。key 归一化和 `effectMode` 校验由服务端内部完成，不作为
新的 API 参数暴露。`effectMode=RESTART` 的字段不应通过运行时更新立即生效。
服务端应比较目标 source 更新前后的完整 map，因此新增、修改或移除 `RESTART` 配置项
都必须拒绝。提交的完整 map 中省略某个 key，只有在该配置项支持运行时生效时才表示
移除对应 override。
canonical item key、normalized full key 和兼容 alias key 应在校验及存储前统一归一化为
item key。请求包含未定义 key，或者 alias 歧义命中多个配置项时，应返回参数校验错误。

对于声明为 `sensitive=true` 的配置项，提交值只要包含统一的 `******` marker，就按
脱敏展示值处理。如果当前目标 source 已经包含该 key，服务端应保留该 source 中的原始
值；如果目标 source 不包含该 key，则忽略这项输入，继续保持该 source 不存在此 key。
该判断同时覆盖 `******`、`a******z` 和 `ab******yz`，且不得把 `STATIC` 等其他
source 的 effective value 复制成 runtime override。服务端应记录 WARN 日志，但只记录
`pluginId`、item key 和目标 source，不得打印配置值。

### 初始化与运行时应用

启动和运行时更新复用同一套 source resolver 与 effective config 计算逻辑：

1. runtime persisted source resolver 先将 `plugin-configs.json` 中的全部内容装载到
   source，再开始应用插件配置。
2. 随后对每个已加载的可配置插件执行 resolve 和 apply，即使该插件没有持久化
   override 也要处理。启动属于初始化阶段，可以同时应用 `RUNTIME` 和 `RESTART`
   字段。
3. 运行时请求完整替换一个 `RUNTIME_PERSISTED` 或 `LOCAL_ONLY` source map，随后
   重新解析全部来源；每次接受的请求都调用插件实现，包括使用相同完整 map 发起的
   手动重试。

`STATIC` resolver 应维护每个插件的已接受快照，而不是让每次 detail 查询独立读取实时
环境值。启动时捕获全部已定义静态字段，并允许应用两种 effect mode。启动完成后，
`ServerConfigChangeEvent` 刷新该快照；对于 effective runtime config 发生变化的每个
可配置插件，复用同一套 resolve、validate、apply 流程。

静态刷新时只接受声明为 `effectMode=RUNTIME` 的字段。新增、修改或移除 `RESTART`
字段时，运行中快照继续保留启动值，直到服务端重启，并输出只包含 plugin ID 和 item
keys、不包含配置值的 WARN。detail 查询继续返回已接受的 effective 快照，不能把尚未
应用的 restart-required 环境值报告为已生效。静态字段变化如果被更高优先级 source
覆盖，可以更新来源视图；effective config 与最近一次成功 apply 的快照相同时不需要再次调用
插件。若静态快照已接受但 apply 失败，插件继续保留此前成功应用的配置；后续刷新时，只要解析
所得 effective config 仍与该成功快照不同，就应再次尝试 apply。

同一插件的更新应串行执行。runtime persisted 更新先持久化归一化后的完整 source
map，再替换 resolver source、解析并校验 effective config，最后应用到插件。持久化
失败时不得修改 resolver source 或插件，也不执行回滚。source 更新成功但 apply 失败
时，已接受的 source map 保持持久化和可解析状态，服务端不自动发起回滚或补偿更新；
API 应明确返回“配置已更新但 apply 失败”的服务端错误，日志记录 plugin ID 和 source
且不记录配置值。再次提交相同完整 map 可以手动重试 apply。`LOCAL_ONLY` 更新执行相同
的 replace、resolve、apply 流程，但不持久化、不同步；apply 失败后新的本机 source map
同样保留。

## 管理 API

核心插件管理 API 如下：

| 方法 | 路径 | 目的 |
|------|------|------|
| `GET` | `/v3/admin/core/plugin/list` | 查询已加载插件，可按类型过滤。 |
| `GET` | `/v3/admin/core/plugin/detail` | 查询单个插件详情，返回 effective config 和可选值元数据。 |
| `PUT` | `/v3/admin/core/plugin/status` | 启用或禁用插件。 |
| `PUT` | `/v3/admin/core/plugin/config` | 更新插件配置。 |

这些端点属于 Admin API，并要求符合 [HTTP 鉴权规范](../http-api/authorization-spec.md)
中的控制台域鉴权。插件管理 API 必须使用标准 v3
[响应与错误模型](../http-api/response-error-spec.md)。

## 设计要求

插件实现必须遵守以下规则：

- 使用已有 Nacos [资源标识](../design/resource-model-spec.md)和领域模型，不为同一
  资源发明不兼容的新模型。
- 插件提供的 HTTP API 必须保持 v3 [HTTP API](../http-api/api-spec.md) 响应、错误和
  鉴权约定。
- 仅通过 `PluginConfigSpec` 暴露插件自身拥有的配置。
- 除调用方明确要求本机操作用于诊断或应急处理外，集群级状态变更必须保持同步。
- 安全敏感的默认值和部署要求必须在插件实现规范中说明。

插件机制是扩展边界，不是绕过 Nacos 资源、API 或安全规则的通道。
