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

# Agent 管理规范

本文档定义 Nacos AI Registry 中协议无关的 Agent 管理模型，并细化
[AI 资源模型规范](ai-resource-model-spec.md)、
[AI 资源生命周期规范](ai-resource-lifecycle-spec.md)和
[Naming 规范](../naming/README.md)。

本文档是 Agent 模型迁移的规范目标契约。服务端或 SDK 在对应行为实现之前不得声明支持
本契约。在相关能力声明之前，当前运行时契约仍以
[A2A Agent 规范](a2a-agent-spec.md)为准。

## 1. 范围与边界

本模型拆分三类具有不同生命周期的事实：

```text
Agent
  1 --- N AgentVersion
             1 --- N AgentCallInterface
                         1 --- N DECLARED Endpoint

Runtime publisher --- N RUNTIME Endpoint
RUNTIME Endpoint --- versionRange match ---> AgentVersion + AgentCallInterface
```

| 事实 | 职责 | 事实源 |
| --- | --- | --- |
| `Agent` | 稳定身份、目录元数据、owner、可见性和版本治理。 | `ai_resource` |
| `AgentVersion` | 一个版本化调用定义，只在 draft 阶段可修改。 | `ai_resource_version` 和 AI Storage |
| `AgentCallInterface` | 一个协议绑定及其声明地址。 | `AgentVersionContent` |
| `RuntimeEndpoint` | 活跃 publisher 的可调用地址和兼容版本范围。 | Naming 运行时状态 |

A2A 是首个协议 Adapter。通用 Agent 模型不得包含 A2A 专有 capability、skill、
security、task 或 message 字段的并集。这些字段保留在协议原生 descriptor 中。

本规范不定义 MCP 资源或实际远程调用。Nacos 返回调用元信息，但不代理 Agent message、
task、session、stream、retry 或 credential。Search、Discover、Watch 和运行时发布的
线上对象由 [RAD 协议规范](rad-protocol-spec.md)定义。Runtime 发布归属、物理存储、
Naming 映射、codec 和 revision 算法由
[Agent 存储规范](agent-storage-spec.md)定义。

## 2. 身份与校验

### 2.1 Agent 身份

Agent 标准身份为：

```text
namespaceId + resourceType=agent + agentName
```

`agentName` 是公开 `resourceName`，规则如下：

- 包含 1～64 个 `U+0020..U+007E` 范围内的可打印 ASCII 字符；
- 至少包含一个非空格字符；
- 按提交原值保存并区分大小写；
- 服务端不得 trim、lowercase、slug 或进行其他改写；
- 创建后不可变。

`displayName` 是可选的 Unicode 展示字段。`displayName` 缺失或为空白时，展示层必须
使用 `agentName`。`displayName` 不参与身份、鉴权、存储 key 或 Endpoint 匹配。

精确查询比较原始 `agentName`。过滤语义由对应 API Binding 定义：RAD Search 使用
literal substring 匹配；首版 Admin 列表复用共享 AI Resource 的名称模糊查询，
不新增 Agent 专用持久化运算符。

### 2.2 Version 身份

Agent Version 身份为：

```text
namespaceId + resourceType=agent + agentName + version
```

`version` 使用 `MAJOR.MINOR.PATCH[-PRERELEASE]`，最长 64 个字符：

- `MAJOR`、`MINOR`、`PATCH` 为 `0` 或不带前导零的正整数；
- `PRERELEASE` 包含一个或多个以点分隔的 `[0-9A-Za-z-]+` 标识，纯数字标识不得
  带前导零；
- 不接受由 `+` 引入的 build metadata；
- 原值保存并按大小写敏感比较；
- 包括兼容 facade 在内的所有 Agent 写入路径都执行这些规则。

Version 顺序首先按 major、minor、patch 数值比较。正式版高于同核心版本的 prerelease。
Prerelease 标识从左到右比较：纯数字标识按数值排序且低于非数字标识；非数字标识按
大小写敏感的 ASCII 顺序比较；其他部分相同时，标识更多的序列更高。

Version label 匹配 `[A-Za-z0-9][A-Za-z0-9._-]{0,63}` 并按大小写敏感比较。
`latest` 是服务端管理的保留指针，不能通过自定义 label 写入创建、替换或删除。

### 2.3 Endpoint 身份

DECLARED 和 RUNTIME 来源使用同一个 `Endpoint` 值对象。在一个 Agent 协议分组中，其
自然身份为：

```text
(namespaceId, agentName, protocol,
 normalizedHost(uri), effectivePort(uri), normalizedTransport)
```

不存在公开 `endpointId`。URI path、query、metadata、priority 和 weight 不参与身份，
同一 publisher 可以更新这些字段。

## 3. Agent 资源

Agent 资源包含以下字段：

| 字段 | 必选 | 含义 |
| --- | :---: | --- |
| `namespaceId` | 是 | Nacos Namespace 隔离边界；1～128 个 `[A-Za-z0-9_-]` 字符。 |
| `agentName` | 是 | 稳定公开身份。 |
| `displayName` | 否 | Unicode 展示名称。 |
| `description` | 否 | 目录描述。 |
| `iconUrl` | 否 | 目录图标 URI。 |
| `provider` | 否 | 提供方 `name` 和 `url`；它不是管理 owner。 |
| `tags[]` | 否 | 公开目录标签；RAD Search 使用精确匹配。 |
| `extensions` | 否 | 用于公开 Agent 级扩展的命名空间化 `Map<String, JsonValue>`。 |
| `status` | 是 | `enable` 或 `disable`。 |
| `owner` | 是 | 管理 owner。 |
| `scope` | 是 | 共享可见性 scope；本版本为 `PUBLIC` 或 `PRIVATE`。 |
| `versionInfo` | 只读 | 共享的 editing、reviewing、online count 和 label 摘要。 |
| `versionCatalog` | 只读 | online Version 和 protocol 的紧凑目录。 |
| `metaVersion` | 只读 | 与 AI Resource 模型共享的单调元数据修订号；首版 Agent Admin API 不暴露条件写入参数。 |
| `createTime`、`updateTime` | 只读 | 审计时间。 |

固定以下不变量：

- Agent 元数据不内嵌协议 descriptor、Endpoint、健康状态或完整版本历史。
- `tags` 是本版本唯一的公开通用分类列表。
- `extensions` 不影响身份、鉴权、版本选择、Endpoint 选择或默认检索。其中不得包含
  credential 或服务端内部状态。
- 更新目录或扩展字段会推进 `metaVersion`，但不会创建 Agent Version。
- 协议 Adapter 只能在首次创建 Agent 时使用 native descriptor 初始化调用方未提供的
  目录字段。后续 descriptor 更新不得覆盖独立治理的 Agent 元数据。

`versionCatalog` 包含 `latestVersion` 和 `onlineVersions[]`。每个 online 条目只包含
`version`、`labels[]` 和 `protocols[]`。它由服务端派生，不是客户端可写事实。

## 4. Agent Version 与生命周期

### 4.1 Version 元数据与内容

Agent Version 暴露以下元数据：

| 字段 | 必选 | 含义 |
| --- | :---: | --- |
| `namespaceId`、`agentName`、`version` | 是 | 精确版本身份。 |
| `status` | 是 | 共享 AI Resource Version 状态。 |
| `callInterfaces[]` | 是 | 有序协议绑定；至少一个。 |
| `author` | 否 | Version 作者。 |
| `changeDescription` | 否 | Version 变更说明。 |
| `contentDigest` | 只读 | Version 持久化内容 bytes 的 SHA-256 摘要。 |
| `createTime`、`updateTime` | 只读 | 审计时间。 |

完整存储 payload 是一个 `AgentVersionContent` 对象：

```text
AgentVersionContent
  kind = AgentVersionContent
  schemaVersion = 1
  callInterfaces[]
```

服务端将校验后的对象一次序列化为 UTF-8 JSON；同一份 bytes 用于持久化、计算 `size`，并以
`sha256:<lowercase hex>` 生成摘要。读取时直接校验 AI Storage 返回 bytes 的摘要，不重新
序列化对象。存储规范化和校验规则由 [Agent 存储规范](agent-storage-spec.md)定义。

### 4.2 生命周期规则

创建 draft 是 Resource 和 Version 的统一创建入口：

- Agent metadata 不存在时，创建 draft 会同时创建 `ai_resource` metadata。首个 draft
  必须直接包含 `callInterfaces`；因为不存在可属于该 Agent 的源 Version，
  `basedOnVersion` 非法。同一请求可以初始化可选目录 metadata；enabled 状态、当前 owner
  和默认 scope 由服务端派生。请求上下文没有 identity（例如关闭鉴权）时，服务端使用
  `nacos` 作为 owner；
- Agent metadata 已存在时，draft 创建遵循普通 editing slot 规则，并在直接内容和一个
  精确源 Version 之间二选一。目录 metadata 属于 Agent 更新生命周期，后续 draft 请求
  不接受这些字段。

本版本不提供独立的 metadata-only 或 `createAgent` 操作。首个和后续 draft 创建均返回
`AgentVersionDetail`。

Agent Version 使用共享生命周期：

| 状态 | 内容可修改 | 可进入普通 RAD 发现 |
| --- | :---: | :---: |
| `draft` | 是 | 否 |
| `reviewing` | 否 | 否 |
| `reviewed` | 否 | 否 |
| `online` | 否 | 是 |
| `offline` | 否 | 否 |

`ai_resource_version.status` 是生命周期事实源。`publishPipelineInfo` 只记录审核执行过程
和结果。

一个 Agent 最多存在一个 editing Version 和一个 reviewing Version。Draft 进入
reviewing 后内容冻结。Reviewed、online 和 offline 内容不得原地更新。本版本契约不提供
强制替换同 Version 内容的操作。

`latest` 是服务端管理的 label，并且必须始终指向 online Version。以下 Agent
专属规则细化通用 AI 生命周期规则：

- 每次标准 publish 或 online 转换成功后，都将目标 Version 设为 `latest`；
- legacy A2A 发布的 `setAsLatest=false` 是首版唯一例外，并保留当前有效的
  `latest`；
- 即使通过上述兼容路径发布，首个 online Version 仍然会建立 `latest`；
- 删除或下线当前 `latest` 时，选择剩余 online Version 中 SemVer 最大的一个；
- 删除最后一个 online Version 时删除 `latest`；
- 删除或下线当前 `latest` 之外的 online Version 时，不触发重算。

online 状态或 label 变化时，服务端必须在一次逻辑更新中重建 `versionCatalog`。只要存在
online Version，就必须存在且仅存在一个有效的 `latestVersion`，并且它必须出现在
`onlineVersions` 中。

Agent 元数据、Agent Version 定义和 Runtime Endpoint 之间不互相拥有生命周期。删除或
disable Agent 定义会改变读取投影，但不会删除仍然活跃的运行时 publisher 状态。

Agent 目录 Search document 是可重建派生状态，不是新的事实源。以下成功提交会按
`(namespaceId, agent, agentName)` 调度一个合并的 `search_index` 任务：创建 Agent、目录
metadata 或治理字段更新、Version publish/online/offline/delete、common latest 或自定义
label 变化、legacy A2A facade 产生 canonical 定义变化，以及 Agent 删除。任务重新读取最新事实，
投影 common latest 和全部 online Version 的目录；连续变化只推进任务 revision。

Runtime Endpoint register/deregister、Publisher heartbeat、健康状态和 Runtime revision 不得调度
该任务，也不得写入 Agent 目录索引。调度失败不回滚已经成功的 Agent 生命周期操作，持久任务重试和
Reconciliation 按 [AI 资源检索规范](ai-resource-search-spec.md)最终收敛。Agent 不存在、disabled
或没有 online Version 时，正确的索引结果是删除派生文档。

## 5. CallInterface 与 Declared Endpoint

### 5.1 AgentCallInterface

每个 Agent Version 包含一个有序、非空的 `callInterfaces[]` 列表。每项包含：

| 字段 | 必选 | 含义 |
| --- | :---: | --- |
| `protocol` | 是 | 规范 protocol token；同一 Version 内唯一。 |
| `protocolVersion` | 否 | 快速协议协商值；不作为接口身份。 |
| `descriptorMediaType` | 是 | `nativeDescriptor` 的媒体类型。 |
| `nativeDescriptor` | 是 | 完整协议原生 descriptor。 |
| `endpointSourceOrder[]` | 是 | `RUNTIME` 和 `DECLARED` 的非空有序集合。 |
| `declaredEndpoints[]` | 否 | Adapter 派生的静态 Endpoint 投影。 |

规范 protocol token 匹配 `[A-Za-z0-9][A-Za-z0-9-]{0,31}`，并按大小写敏感比较。
CallInterface 唯一性、Endpoint 发布、RAD filter 和 Naming serviceName 组合使用相同 token。

`callInterfaces[]` 顺序是默认协议偏好。重新排序会改变 `contentDigest`。第一个仍有可用
Endpoint 的接口是 SDK 默认选择候选。

`endpointSourceOrder` 不包含重复项，语义如下：

- `[RUNTIME, DECLARED]` 优先使用运行时地址，并以声明地址作为后备；
- `[DECLARED, RUNTIME]` 优先使用声明地址；
- `[RUNTIME]` 或 `[DECLARED]` 在普通发现中只允许对应来源。

来源顺序属于单个 CallInterface，而不是整个 Version。它不阻止运行时发布。即使某个
CallInterface 不包含 `RUNTIME`，管控查询仍可查看 Runtime Endpoint。

### 5.2 Endpoint 值对象

| 字段 | 必选 | 含义 |
| --- | :---: | --- |
| `uri` | 是 | 完整可调用 URI。 |
| `transport` | 是 | 规范 transport token。 |
| `priority` | 否 | 数值越小优先级越高。 |
| `weight` | 否 | 同一 priority 内 Endpoint 的负载权重。 |
| `metadata` | 否 | 扁平的 zone、environment、data center 和扩展 label。 |

URI 包含非空 scheme 和 host。port 必须显式给出，或能根据 scheme 解析成 `1..65535`
范围内的有效默认值。DNS host 使用大小写无关的 canonical 形式；IP literal 使用稳定的
IPv4 或 IPv6 表达。

Adapter 从 `nativeDescriptor` 派生并校验 `declaredEndpoints`。客户端不得独立编辑这两种
表达。同一自然 Endpoint 多次出现时，第一次 descriptor 出现位置决定列表位置，而 native
descriptor 仍由 canonical content 完整表达。

## 6. 管控面读取模型

管控 API 使用有界视图，而不是一个无界聚合对象：

| 视图 | 包含 | 不包含 |
| --- | --- | --- |
| `AgentSummary` | 展示、治理和 Version Catalog 摘要。 | Descriptor、Endpoint、完整历史、extensions。 |
| `AgentOverview` | 完整 Agent 和有界的 Version Summary page。 | Version payload 和 Runtime Endpoint。 |
| `AgentVersionSummary` | Version、status、author、change description、digest 和时间。 | CallInterface payload。 |
| `AgentVersionDetail` | 精确 Version 元数据和完整 CallInterface。 | Runtime Endpoint。 |
| `RuntimeEndpointSnapshot` | 一个 Agent 和 protocol 的原始运行时快照，可按 Version 过滤。 | Descriptor、publisher identity、最终可发现性结论。 |

`RuntimeEndpointSnapshot` 不分页，包含：

```text
namespaceId / agentName / protocol / version?
items[] {
  endpoint, bindings[] { runtimeVersion, versionRange },
  state, enabled, healthy, lastUpdatedTime
}
state = AVAILABLE | DISABLED | UNHEALTHY
```

状态按顺序判定：`enabled=false` 为 `DISABLED`；否则 `healthy=false` 为 `UNHEALTHY`；
其他项为 `AVAILABLE`。`lastUpdatedTime` 使用本次构建 Snapshot 的 Naming `ServiceInfo.lastRefTime`，
因此同一 Snapshot 的全部 item 共享同一个 Service 投影观察时间。它不是单个 Endpoint 的分布式事实或
缓存校验器；Naming 每次重建该 Service 投影时都可能改变它。跨节点相等性和 Watch 去重使用由内容
派生的 `sourceRevision`。

`protocol` 必填。没有 `version` 时，Snapshot 对该 protocol 下每个 Endpoint 自然键返回一个有效项
及其全部 Version binding；指定 `version` 时，只保留命中该 Version 的 binding，并在没有
剩余 binding 时移除该项。没有实例时返回空 `items[]`。Snapshot 不应用
`endpointSourceOrder`，也不声明某一项可发现。Console 只把 Version detail 和 Snapshot 作为
独立读取事实进行组合。

RAD Catalog、Discover 和 Watch 对象属于数据面视图，只由
[RAD 协议规范](rad-protocol-spec.md)定义。特别是，`AgentDiscoveryResult` 将一个 online
Version 定义与允许的 DECLARED 和 RUNTIME Endpoint set 组合，但不作为事实保存。

## 7. 容量与安全

目标管控模型在写入任何 Agent 或 Version 事实之前执行以下上限：

| 字段 | 上限 |
| --- | ---: |
| `displayName`、`provider.name` | 128 Unicode code point。 |
| `description` | 2048 字符。 |
| Icon、provider 或 declared Endpoint URI | 2048 字符。 |
| 公开 tag | 32 项，每项 64 字符。 |
| Agent `extensions` | 32 项；key 128 字符；序列化后的 UTF-8 JSON 合计 16 KiB。 |
| `protocol`、`protocolVersion` | 32 和 64 字符。 |
| 每个 Version 的 CallInterface | 16。 |
| 每个 CallInterface 的 Declared Endpoint | 64。 |
| Endpoint metadata | 32 项；key 64、value 256 字符。 |
| `AgentVersionContent` | 1 MiB。 |

`biz_tags` 只保存用户设置的公开 tag，不保存服务端派生索引；序列化后的 JSON 不得超过
1024 字符。

Descriptor、extension 和 Endpoint metadata 不得包含明文 credential。审计记录不得记录
完整 native descriptor、security scheme 或敏感 Endpoint metadata。Runtime 发布和物理存储
上限由 [Agent 存储规范](agent-storage-spec.md)定义。

## 8. A2A 兼容边界

迁移后，旧 A2A API 是 Agent 模型上的兼容 facade，不再创建第二个 AgentCard 事实源。

| A2A 值 | Agent 模型投影 |
| --- | --- |
| AgentCard name 和 version | `agentName` 和 Agent Version 身份。 |
| 完整 AgentCard | A2A CallInterface `nativeDescriptor`。 |
| A2A protocol version | CallInterface `protocolVersion` 和 native descriptor。 |
| Root URL 和 supported/additional interface | Adapter 派生的 declared Endpoint。 |
| `registrationType=URL` | Declared-first 来源顺序。 |
| `registrationType=SERVICE` | Runtime-first 来源顺序。 |
| Runtime A2A endpoint version | `runtimeVersion` 和精确 `[version]` range。 |

首个实现只支持 A2A protocol，因此旧 A2A latest 和通用 Agent latest 使用同一个 label。
Adapter 根据 native descriptor 和适用的 Endpoint 投影重建旧查询 DTO。URL 形态读取使用
declared 地址，service 形态读取使用 runtime 地址；不存在 runtime 地址时使用 declared 地址
作为兼容后备。

通过旧 API 发起的新写入执行本规范的身份、Version、不可变和容量规则。为保留代码优先的
A2A 发布能力，它们可以使用有审计的内部直接 online 转换，但不得覆盖已经发布 Version 中
不同的内容。

Runtime A2A 发布和注销投影由 [Agent 存储规范](agent-storage-spec.md)定义。

历史 Config row、历史 Naming layout、混合版本集群双读或双写、事实源切换、回滚和异常历史
身份属于独立的滚动升级与数据迁移契约。本目标模型不因此放宽规则。

Agent 和 AgentSpec 可以通过通用资源关系互相引用，但不互相拥有生命周期。本版本不增加
Agent 专用 `sourceRef`、`defaultInterfaceId`、`interfaceId`、`descriptorDigest` 或随机
Endpoint 标识。
