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
| 互斥选择 | 在进程或请求范围内选择一个实现，其他已加载实现不参与该次判断。 | `auth`、`datasource-dialect` |
| 配置选择的单服务 | 可以加载多个实现，但领域根据配置或请求上下文选择一个服务。 | `visibility`、`ai-resource-import` |
| 有序链式执行 | 多个匹配插件按稳定顺序执行。每个节点可以贡献结果，失败是否中断由领域定义。 | `ai-pipeline`、`config-change` |
| 订阅或广播 | 多个订阅者观察同一个事件或 trace 点，不拥有主决策权。 | `trace`、事件型扩展 |

对于链式插件，领域 SPI 必须定义：

- 如何根据资源或 pointcut 选择候选插件。
- 哪个字段控制顺序，例如 `getPreferOrder()` 或 `getOrder()`。
- 执行方式是串行还是并行。
- 某个插件失败时，是中断链路还是只记录失败结果。
- 如何持久化和暴露部分执行结果。

核心插件管理器记录插件的加载状态和启用状态，本身不定义执行形态。领域管理器负责稳定地
应用对应执行形态。

## SPI 层次

Nacos 插件包含两个相关的 SPI 层次：

1. 领域 SPI，例如 `AuthPluginService` 或 `VisibilityService`，定义所属领域需要的行为。
2. 核心插件 SPI，即 `PluginProvider`，将插件实例暴露给核心插件管理器，用于列表查询、
   状态管理、配置管理和运行时观测。

需要动态配置的插件应实现 `PluginConfigSpec`。支持启停状态判断的插件类别，应通过
`PluginStateCheckerHolder` 获取状态，而不是维护一套独立状态来源。

## 加载与生命周期

插件实现通过 Nacos SPI 加载。部署时可以从 classpath 或服务端插件目录提供插件。
插件实现必须能在不修改 Nacos 服务端代码的情况下被加载。

核心 `PluginManager` 会在服务端应用就绪后发现 `PluginProvider` 实现。领域管理器也可以
通过 SPI 加载自身领域服务，但是否可参与请求处理，仍必须遵守核心插件管理器维护的启停
状态。

插件启动必须具备确定性：

- 一个插件类型和插件名称组合只能对应一个运行时插件实例。
- 同一插件类型下重复的插件名称不适合稳定运行。
- 插件实现不得改变 Nacos 共享资源标识、响应封装或错误约定的含义。

## 状态与配置

插件状态分为两个层次：

- 已加载：实现存在于运行时。
- 已启用：实现可以参与请求处理。

大多数插件类型在加载后默认启用。互斥插件类型会选择一个默认实现：

| 类型 | 默认选择规则 |
|------|--------------|
| `auth` | 由 `nacos.core.auth.system.type` 指定，默认 `nacos`。 |
| `datasource-dialect` | 由 SQL platform 配置指定，默认 `derby`。 |

只有 `auth` 和 `datasource-dialect` 在插件管理模型中标记为互斥类型。没有持久化插件状态时，
历史选择配置用于生成初始状态；启动加载完成后，持久化状态优先。通过插件管理 API 选择插件
是标准管理路径，读取到历史选择配置时服务端应记录迁移提示日志。

当服务端依赖某些插件维持基本运行能力时，这些插件不能被禁用。当前关键插件集合包括内置
数据源方言，以及服务端需要的默认 AI 存储插件。

实现 `PluginConfigSpec` 的插件应暴露配置定义、当前配置和配置应用行为。除非请求明确
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
状态。当前满足该契约的运行时路由型插件包括 `auth`、`datasource-dialect`、
`encryption`、`trace`、`visibility`、`config-change`、`ai-pipeline` 和
`ai-storage`。被禁用的插件仍保持加载并可由管理 API 查询，但不得参与领域执行。

插件类型是否互斥属于类型能力，必须由共享的 `PluginType` 定义提供。Core 和 Console
的 API 适配层不得分别维护硬编码的互斥类型列表。

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

1. 启动时先将 `plugin-configs.json` 中的全部内容装载到 runtime persisted source，
   再开始应用插件配置。
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
覆盖，可以更新来源视图；effective config 未变化时不需要再次调用插件。

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
