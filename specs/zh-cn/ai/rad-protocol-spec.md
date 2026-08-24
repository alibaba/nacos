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

# Remote Agent Discovery 协议规范

| 项目 | 值 |
|---|---|
| 状态 | 实验性；对协议版本 `0.1.0` 具有规范性 |
| 协议版本 | `0.1.0` |
| 范围 | Remote Agent 搜索、发现、订阅和运行时端点发布 |
| 目标 | 通过少量稳定对象返回 Agent 调用描述和当前可用地址 |

本文定义 Nacos Remote Agent Discovery（RAD）协议与传输无关的核心语义。
HTTP、gRPC 和 SDK Binding 可以使用各自的本地类型，但线上字段和可观察行为必须与
本文等价。

## 1. 定位与范围

RAD 回答两个问题：

1. 目标未知时，哪些 Agent 可能满足需求；
2. 选定 Agent 版本后，哪些调用协议和端点当前可用。

RAD 返回调用远程 Agent 所需的元数据，但不代理调用，也不定义 Agent 消息、任务或
会话协议。

### 1.1 操作

RAD 0.1.0 定义五个操作：

| 操作 | 输入 | 输出 | 语义 |
|---|---|---|---|
| `Search` | `AgentSearchRequest` | `AgentCatalogPage` | 分页搜索候选 Agent |
| `Discover` | `AgentDiscoveryRequest` | `AgentDiscoveryResult` | 返回一个 Agent 版本的完整调用快照 |
| `Watch` | `AgentDiscoveryRequest` | `AgentDiscoveryResult` 流 | 返回初始和后续的完整替换快照 |
| `Register` | `AgentEndpointRegistrationBatch` | 成功或错误 | 完整替换当前发布者的运行时 Endpoint Batch |
| `Deregister` | `AgentEndpointDeregistrationBatch` | 成功或错误 | 从发布者期望 Batch 中移除 Endpoint 自然键 |

`Watch` 复用 `Discover` 的请求和结果。RAD 不在订阅快照外增加事件信封对象。

### 1.2 范围外内容

RAD 0.1.0 不定义 Agent 管理生命周期、客户端连接与重连、内部存储、历史兼容、
MCP、调用代理、凭据、重试或负载均衡。Agent 资源和版本语义由
[Agent 管理规范](./agent-management-spec.md)定义。

## 2. 公共约束

### 2.1 命名空间

每个操作都在且只在一个生效命名空间中执行。

- 顶层请求只携带一次 `namespaceId`。
- 嵌套的 Agent 引用、Filter 和 Endpoint 不重复携带该字段。
- Binding 可以从客户端配置或请求上下文取得该值，但必须在进入 RAD 核心语义前将
  缺省命名空间规范化为 `public`。
- 缓存、订阅、鉴权和发布者贡献键必须包含生效命名空间。

`namespaceId` 遵循 Nacos 公共 Namespace 契约，包含 1～128 个
`[A-Za-z0-9_-]` 字符。

### 2.2 Agent、Protocol 与 Label 身份

Agent 的公开身份是 `(namespaceId, agentName)`。

`agentName` 必须：

- 包含 1～64 个可打印 ASCII 字符；
- 至少包含一个非空格字符；
- 按大小写敏感的原值进行比较；
- 不执行 trim、转小写、slug 或其他改写。

`protocol` 包含 1～32 个字符并匹配
`[A-Za-z0-9][A-Za-z0-9-]{0,31}`。`label` 包含 1～64 个字符并匹配
`[A-Za-z0-9][A-Za-z0-9._-]{0,63}`。两者均大小写敏感。

`latest` 是保留 Label，用于解析 Agent 当前的 latest 版本。它不得出现在
`AgentCatalogVersion.labels` 中。

### 2.3 Agent 版本

Agent 版本采用 `MAJOR.MINOR.PATCH[-PRERELEASE]`，总长不超过 64 个字符。
核心数值标识不得包含前导零。Prerelease 标识由 `.` 分隔，每一项匹配
`[0-9A-Za-z-]+`；只包含数字的 Prerelease 标识不得带前导零，除非它恰好是 `0`。

RAD 0.1.0 不接受 build metadata。版本身份和比较均大小写敏感，顺序遵循 SemVer
优先级，不得先将版本转换为固定宽度整数。

### 2.4 版本范围

`versionRange` 使用 Maven/POM 风格的区间括号，但每个边界必须是第 2.3 节定义的
Agent 版本，比较使用 RAD SemVer，而不是 Maven `ComparableVersion`。

| 形式 | 匹配规则 |
|---|---|
| `[1.0.6]` | 只匹配 `1.0.6` |
| `[1.0.0,1.0.6]` | `1.0.0 <= version <= 1.0.6` |
| `[1.0.0,2.0.0)` | `1.0.0 <= version < 2.0.0` |
| `[1.0.0,)` | `version >= 1.0.0` |
| `(,2.0.0)` | `version < 2.0.0` |

RAD 0.1.0 只接受一个精确版本或一个连续区间，不接受多个版本或区间的并集。表达式
不得包含空格，并且至少包含一个边界。缺失下界时使用 `(`，缺失上界时使用 `)`。

上下界都存在时，下界必须早于上界。只有两端均为闭区间时才允许上下界相等，此时
服务端将 `[version,version]` 规范化为 `[version]`；其他上下界相等的形式均非法。
`[1.0.0,2.0.0)` 只承诺所述 SemVer 比较，Prerelease 版本仍按 SemVer 优先级判断。
服务端存储并比较规范化后的形式。

### 2.5 协议版本协商

Binding 通过其文档或 Nacos 能力协商声明支持 RAD 0.1.0。RAD 根消息本身不携带
协议版本或 Schema 版本字段。

## 3. 公共模型

### 3.1 根消息

Schema 只暴露以下六个根消息：

| 根消息 | 用途 |
|---|---|
| `AgentSearchRequest` | `Search` 请求 |
| `AgentCatalogPage` | `Search` 结果 |
| `AgentDiscoveryRequest` | `Discover` 和 `Watch` 请求 |
| `AgentDiscoveryResult` | `Discover` 和 `Watch` 完整快照 |
| `AgentEndpointRegistrationBatch` | `Register` 的完整期望 Batch |
| `AgentEndpointDeregistrationBatch` | `Deregister` 的 Publisher Client 期望状态命令 |

语言 Binding 可以复用字段完全等价的本地类型。例如 Java 可以使用
`Page<AgentCatalogEntry>` 实现 `AgentCatalogPage`，不必再引入一个分页类。

### 3.2 通用 JSON 规则

- 可选值缺失时省略字段，不使用显式 `null`。
- 普通对象不接受未知属性。
- 只有 `nativeDescriptor` 和明确声明的 `metadata` Map 是开放内容。
- 可选请求数组一旦出现就至少包含一项；响应中的空集合明确返回 `[]`。
- 空 Filter 对象 `{}` 表示不进行过滤。
- 空 Metadata 对象 `{}` 规范化为省略该字段；空 `metadataSelector` 等价于不进行
  Metadata 过滤。

### 3.3 `AgentSearchRequest`

| 字段 | 必选 | 语义 |
|---|:---:|---|
| `namespaceId` | 是 | 生效命名空间 |
| `agentNameContains` | 否 | 对 `agentName` 执行大小写敏感的字面量子串匹配 |
| `tagsAll[]` | 否 | Agent 包含全部给定 Tag |
| `protocolsAny[]` | 否 | 至少一个 online Version 暴露任一给定调用协议 |
| `pageNo` | 否 | 从 1 开始的页码，缺省为 `1` |
| `pageSize` | 否 | 每页数量，缺省为 `20`，最大为 `100` |

按 Protocol 筛选是 RAD 的结果语义，不规定物理索引实现。实现可以读取 online Version
目录，也可以维护独立的派生索引，但不得把 Protocol 值编码为公开 Agent tag。

`%`、`_` 等对底层查询语言具有特殊含义的字符必须作为普通字面量处理。

### 3.4 `AgentCatalogPage`、`AgentCatalogEntry` 与 `AgentCatalogVersion`

`AgentCatalogPage` 包含：

```text
totalCount / pageNumber / pagesAvailable / pageItems[]
```

每个 `pageItems[]` 是一个相对于请求命名空间的 `AgentCatalogEntry`：

```text
agentName / displayName? / description? / iconUrl? / provider?
tags? / latestVersion
versions[] AgentCatalogVersion {
  version
  labels[]?
  protocols[]
}
```

规则：

- `versions` 按 SemVer 降序列出全部在线版本。Version 不重复，`protocols` 至少包含
  一个不重复的值。
- 每个条目的在线版本数量没有产品级硬上限，列表不得被静默截断。Binding 的全局
  响应大小限制仍然生效；超限时返回该 Binding 的标准响应过大错误。
- 一个非保留 Label 最多指向一个 Version。`latest` 不得出现在 `labels` 中，且
  `latestVersion` 必须匹配一个已列出的 `version`。
- 条目不重复返回 `namespaceId`，也不返回协议描述、Endpoint、健康状态或管理字段。
- Search 不承诺当前存在健康 Endpoint，当前可调用性由 Discover 判断。

### 3.5 `AgentReference`

| 字段 | 必选 | 语义 |
|---|:---:|---|
| `agentName` | 是 | 生效命名空间中的 Agent 名称 |
| `version` | 否 | 选择一个在线的精确版本 |
| `label` | 否 | 在请求时将 Label 解析为一个在线版本 |

`version` 与 `label` 互斥。定义元数据始终解析为一个精确在线版本。两者都缺失时，
定义使用当前 latest，但 Runtime Endpoint 的兼容目标集合包含全部在线版本。显式
`label=latest` 的语义不同：定义和 Runtime Endpoint 都严格限制为当前 latest。
精确 version 或其他 label 同样只使用一个解析版本。

### 3.6 `AgentDiscoveryFilter`

Filter 的全部字段都是可选字段：

| 字段 | 语义 |
|---|---|
| `protocols[]` | 允许的调用协议 |
| `protocolVersion` | 与候选接口进行精确匹配 |
| `transports[]` | 允许的传输类型 |
| `endpointSources[]` | 允许的 `RUNTIME` 或 `DECLARED` 来源 |
| `metadataSelector` | Endpoint Metadata 包含全部精确键值 |

同一数组内按 OR 匹配，不同字段之间按 AND 匹配。Filter 只裁剪一次发现结果，不会
选择另一个 Agent 版本，也不执行负载均衡。

### 3.7 `Endpoint`

所有操作复用一个 `Endpoint` 模型：

| 字段 | 必选 | 语义 |
|---|:---:|---|
| `uri` | 是 | 完整绝对调用 URI，最长 2048 个字符 |
| `transport` | 是 | 规范化传输类型，包含 1～64 个 `[0-9A-Za-z+-]` 字符；例如 A2A `HTTP+JSON` |
| `priority` | 否 | 越小越优先，整数 `0..2147483647`，缺省为 `0` |
| `weight` | 否 | 同一 Priority 内的权重，数字 `0..10000`，缺省为 `1` |
| `metadata` | 否 | 最多 32 个扁平字符串键值 |
| `healthy` | 条件必选 | 只在 `RUNTIME` 发现结果中出现，并且必须出现 |

上下文规则：

- Register 不得提交 `healthy`。
- `DECLARED` Endpoint 不得包含 `healthy`。
- `RUNTIME` 发现结果中的 Endpoint 必须包含 `healthy`。
- Deregister 只提交 `uri` 和 `transport`，它们是公开对象中代表 Endpoint
  自然键的字段。它是 Publisher Client 的便利命令；Nacos Binding 先将其应用到
  本地期望状态，再发送完整替换 Batch。

运行时 Endpoint 不使用 `endpointId`。

发现结果使用 `AgentDiscoveryEndpoint`，它在这些 Endpoint 字段上增加
`bindings[] { runtimeVersion, versionRange }`。`DECLARED` Endpoint 不包含该字段，
每个 `RUNTIME` Endpoint 的该字段非空。它是使该 Endpoint 命中当前发现目标集合的
enabled publisher binding 的有序去重并集；不会暴露 publisher 身份或存活时间。

### 3.8 Endpoint 自然键与规范化

运行时 Endpoint 的自然键是：

```text
(namespaceId, agentName, protocol,
 normalizedHost(uri), effectivePort(uri), normalizedTransport)
```

Path、Query、Metadata、Priority 和 Weight 不参与身份。同一分组内的两个 Endpoint
不能只依靠不同 Path 并存。

规范化规则：

- URI 包含 Scheme 和 Host，并具有显式或可推导的有效端口。`http` 和 `ws` 推导
  端口 `80`，`https` 和 `wss` 推导端口 `443`；其他 Scheme 必须显式提供端口。
- URI 不得包含 User-info 或 Fragment。
- Scheme 和 DNS Host 转为小写，DNS Host 使用 ASCII A-label。
- IPv4 和 IPv6 使用稳定文本形式。
- 输出 URI 必须显式包含生效端口。
- Transport 使用 Registry 接受的规范值，不自动转换大小写。
- Priority 和 Weight 在比较前分别物化为 `0` 和 `1`。
- Metadata 按 Key 排序后比较，Map 顺序不影响相等性。

### 3.9 `EndpointSet`

声明来源和运行时来源复用一个对象：

```text
EndpointSet {
  source = DECLARED | RUNTIME
  sourceRevision
  endpoints[] AgentDiscoveryEndpoint
}
```

`source` 决定 `healthy` 约束。`AgentDiscoveryResult` 不返回
`endpointSourceOrder`。Registry 按选中 Agent 版本声明的来源顺序输出
`endpointSets[]`，应用 Filter 后保持剩余来源的相对顺序。已经声明但当前为空的来源
仍以 `endpoints=[]` 和稳定的 `sourceRevision` 返回。

### 3.10 `AgentDiscoveryCallInterface` 与 `AgentDiscoveryResult`

```text
AgentDiscoveryResult
├── namespaceId / agentName / version / contentDigest
└── callInterfaces[] AgentDiscoveryCallInterface
    ├── protocol / protocolVersion?
    ├── descriptorMediaType / nativeDescriptor
    └── endpointSets[]
        ├── source / sourceRevision
        └── endpoints[]
```

`AgentDiscoveryCallInterface` 是数据面投影视图，有意区别于
[Agent 管理规范](./agent-management-spec.md)定义的管控面 `AgentCallInterface`：
发现视图不包含管理字段和来源顺序字段，而是包含已经解析的 EndpointSet。

规则：

- `version` 是提供定义元数据的在线精确版本。省略选择器时它仍为当前 latest，即使
  Runtime Endpoint 可以同时服务多个在线版本。
- 一个版本最多包含 16 个调用接口，Protocol 不重复。
- 调用接口保持 Agent 版本定义中的顺序。
- `nativeDescriptor` 可以是任意非 null JSON 值。
- `descriptorMediaType` 描述 `nativeDescriptor`。
- `endpointSets[]` 是本次发现快照的权威地址，`nativeDescriptor` 中的地址不得覆盖它。
- 结果不返回展示字段、Owner、Scope、Extensions 或发布者身份。

### 3.11 Digest 与 Revision

`contentDigest` 标识完整且不可变的版本内容：

- 格式为 `sha256:` 加 64 位小写十六进制字符。
- 覆盖有序调用接口、`nativeDescriptor`、内部来源顺序和声明 Endpoint。
- 不覆盖状态、latest、Label、管理元数据或运行时 Endpoint。
- Consumer 只比较完整值，不自行计算。

每个发现投影具有一个 `sourceRevision`，其作用域包含 namespace、Agent、定义版本、
protocol、source 和选择器语义：

- 它是不透明的相等性 Token，不能排序或跨作用域比较。
- Endpoint 成员、URI、Transport、Priority、Weight、公开 Metadata、健康状态或返回的
  Runtime binding 来源变化时必须改变。
- 心跳时间、发布者数量或内部存储 Revision 本身不要求它改变。
- 空 EndpointSet 也具有稳定 Revision。
- `DECLARED` 集合使用 Version `contentDigest`。Nacos `RUNTIME` 集合使用
  `murmur3-x64-128-v1:<32 lowercase hex>`，并按照
  [Agent 存储规范](agent-storage-spec.md)中的确定性投影契约生成。Consumer 仍把两种形式
  都视为 opaque，不自行计算。

### 3.12 Endpoint Batch

`AgentEndpointRegistrationBatch` 包含：

```text
namespaceId / agentName / runtimeVersion / protocol
versionRange?            # 缺省为精确范围 [runtimeVersion]
endpoints[]              # 1..1000
```

`runtimeVersion` 是实际部署的实现版本。`versionRange` 描述该部署可以服务的 Agent
版本。字段缺失时，服务端将其规范化为 `[runtimeVersion]`。`runtimeVersion` 必须包含
在生效范围内。

对于同一 Publisher 和 `(namespaceId, agentName, protocol)`，该数组是完整的期望
Endpoint Batch。Register 完整替换此前 Batch，未提交的 Endpoint 会被删除。因此同一
Publisher 在该范围内同时只有一个有效 `runtimeVersion` 和 `versionRange`；修改其中
任一值都是完整替换，不是内部 Group 更新。

`AgentEndpointDeregistrationBatch` 包含：

```text
namespaceId / agentName / protocol
endpoints[] { uri, transport }
```

`AgentEndpointDeregistrationBatch` 继续作为面向应用的便利对象。Publisher Client
从本地缓存的 Registration Batch 中删除给定自然键，再注册完整的剩余 Batch。没有
Endpoint 剩余时，注销 `(namespaceId, agentName, protocol)` 下该 Publisher 的整份
Publication。Nacos Server 不针对该对象执行局部 read-merge-write。

## 4. Search

Search 必须：

1. 只返回调用方可见、已启用、至少存在一个在线版本且 latest 有效的 Agent；
2. 应用 `agentNameContains`、`tagsAll` 和 `protocolsAny`；
3. 按原始 `agentName` 进行大小写敏感的 ASCII 升序排序；
4. 对相同请求和数据快照提供稳定分页；
5. 返回 `totalCount`、`pageNumber`、`pagesAvailable` 和 `pageItems`；
6. 不加载或返回完整描述和 Endpoint。

`pageNo` 缺省为 `1`，`pageSize` 缺省为 `20` 且最大为 `100`。

Search 复用 [AI 资源检索规范](ai-resource-search-spec.md)定义的共享 Search Core，并将
请求固定为 `resourceType=agent`：

- `agentNameContains` 映射为大小写敏感的 `LITERAL_CONTAINS`，其中 `%`、`_` 和 escape
  char 均为普通字符；
- `tagsAll` 映射为大小写敏感的 `EXACT_ALL`；
- `protocolsAny` 映射为大小写敏感的 `EXACT_ANY`；
- 多类 filter 使用 AND 组合；过滤、可见性和当前性校验都必须在 total 和页截断前完成；
- Agent 的完整 online Version 目录来自当前 Search document，Runtime Endpoint、健康状态、
  Publisher 和心跳不进入 Search 索引或响应。

`nacos.ai.rad.search.mode` 可以选择 `AUTO`、`INDEX` 或 `SCAN`。`AUTO` 和 `INDEX` 在
Agent projection readiness 前后都使用共享索引；generation 未 READY 时返回当前快照，total
和分页可能不完整，同时输出不包含查询内容的限频诊断。`SCAN` 显式选择旧兼容路径。索引调用
失败不得按请求回退，单次请求也不得混合两条路径。三种模式必须保持本节的过滤、排序、可见性
和版本目录语义。

## 5. Discover

`AgentDiscoveryRequest` 包含：

```text
namespaceId
reference: AgentReference
filter?: AgentDiscoveryFilter
```

Registry 按以下顺序执行 Discover：

1. 在生效命名空间中按原值查找 `agentName`。
2. 使用 `version`、`label` 或 latest 解析一个定义版本。
3. 校验可见性、Agent Enabled 状态和定义版本 Online 状态。
4. 按定义版本顺序加载调用接口。这些接口是权威定义；latest 已移除的 protocol 不会
   被旧 Runtime Publication 重新带回。
5. 构建 Runtime 兼容目标集合。`version` 和 `label` 都缺失时包含全部当前在线版本；
   其他情况只包含解析出的精确版本，包括显式 `label=latest`。
6. 保留 `versionRange` 至少包含一个目标版本的 Runtime binding，并在每个 Endpoint
   返回命中 binding 的并集。
7. 排除 `enabled=false` 的运行时 Instance，保留 `healthy=true` 和
   `healthy=false` 的 Instance。
8. 聚合具有相同公开 Endpoint 自然键的匹配贡献。
9. 应用可选 Filter。
10. 按调用接口、来源、Priority 和稳定自然键返回完整快照。

过滤后无匹配结果的固定形态为：

| 无匹配层级 | 返回形态 |
|---|---|
| `protocols` 或 `protocolVersion` | `callInterfaces=[]` |
| `endpointSources` | 保留接口并返回 `endpointSets=[]` |
| `transports` 或 `metadataSelector` | 保留 EndpointSet 并返回 `endpoints=[]` |

`healthy=false` 的运行时 Endpoint 仍保留在结果中。只选择健康 Instance、应用
Priority 和 Weight，以及无健康 Instance 时是否回退，都属于 Consumer 职责。

## 6. Watch

Watch 使用与 Discover 相同的请求和结果。

- Registry 首先执行 Discover；如果返回 `NOT_FOUND`，则不创建订阅。
- 成功的 Watch 首先发送当前完整 `AgentDiscoveryResult`。
- 后续每次通知都是不带事件信封的完整替换结果。
- 解析出的版本、`contentDigest` 或任一 `sourceRevision` 发生变化时产生新快照。
- 匹配的运行时注册、更新、注销或存活状态变化在公开投影变化时产生新快照。
- 未改变公开投影的内部变化不应产生重复通知。
- 之前可发现的目标变为 `NOT_FOUND` 时，Binding 发送终止 `NOT_FOUND` 状态并关闭
  Watch。
- Consumer 收到每个新结果后，整体替换旧快照。
- 订阅者身份、确认、重连、重放和背压由 Binding 定义。

Binding 可以使用自己的传输信封投递快照与终止状态。该信封不属于 RAD 公共模型，
也不扩展第 3.1 节的六个根消息。

等价的取消键包含 `namespaceId`、规范化 `AgentReference`、Filter 和订阅者身份。

## 7. Register 与 Deregister

### 7.1 校验与预注册

Register 校验：

- 请求结构、Endpoint 约束、权限和容量；
- `runtimeVersion` 和 `versionRange` 合法，并且范围包含 Runtime Version；
- 同一 Batch 不存在重复自然键；
- 请求不提交或覆盖 `protocolVersion`。

Register 校验只面向本次提交的完整 Batch，不扫描其他 Publisher，也不在写入前为
Endpoint 自然键建立 Reservation。

Register 不要求 Agent、Runtime Version、范围边界、范围内 Version 或对应调用接口
已经存在，因此支持 Endpoint 预注册。

预注册只创建运行时 Publication，不隐式创建 Agent、Version 或调用接口，也不会提前
进入普通 Discover。Discover 仍然要求 Agent 可见且 Enabled、目标 Version Online，
并且调用接口允许 `RUNTIME` 来源；它使用目标版本自身的描述和 `protocolVersion`。

Agent 和 Version 定义不拥有 Publication 生命周期。定义的创建、上线、下线或删除只
改变发现投影；Publication 本身由 Register、Deregister 和发布者存活状态管理。

### 7.2 Batch、幂等与原子性

一个注册 Batch 是当前 Publisher 对以下身份提交的完整期望 Publication：

```text
(namespaceId, agentName, protocol)
```

`runtimeVersion` 和 `versionRange` 是整份 Batch 共享的内容，不属于额外的 Publication
身份，也不形成服务端管理的 Binding Group。

规则：

- Binding 先校验全部 Endpoint，再为当前 Publisher 和 Publication 身份原子应用一个完整 Batch。
- Register 替换此前 Batch，未提交的 Endpoint 会被删除。
- 同一发布者重复提交相同内容时成功但不产生变化。
- Endpoint 字段、Runtime Version 或 Range 的变化通过完整 Batch 替换表达。
- 同一 Batch 出现重复自然键时整体拒绝。
- Publisher Client 串行修改本地期望 Batch。部分 Deregister 和 Version 替换都在
  本地计算新 Batch，再使用 Register 完整覆盖。
- 期望 Batch 变空时，Binding 删除当前 Publisher 在该 Service 下的整份 Publication。
- 注销本地不存在的 Contribution 成功且不发起远程变更。

单 Endpoint 操作使用长度为 1 的 `endpoints[]`；RAD 不定义单独的单条命令。

Nacos Server 路径只是 Naming 上的数据结构 Adapter：它把完整 Endpoint Batch
转换成 Naming Instance，再调用 Naming Batch Register 或整份 Publication
Deregister。它不读取此前 Publisher Payload、不做增量 Merge、不增加 Agent Service
Lock，也不在写入时扫描其他 Publisher。Admission 路径只统计当前 Publisher Client
全部已发布完整 Agent Batch 中的 Runtime Endpoint 条目，并结合操作前条目数以及目标
Batch 的既有和请求条目数执行每 Client 软水位判断；该检查与同一 Client 的 Naming Batch
替换必须串行执行。

### 7.3 Naming Publication 与多发布者

Publisher Transport 提供不透明的发布者身份和存活语义，发布者身份不进入发现结果。

每个 Publisher 对一个 `(namespaceId, agentName, protocol)` Naming Service 最多
贡献一份完整 Batch，并且该 Batch 只有一组 singular `runtimeVersion` 和 `versionRange`。
不同 Publisher 可以贡献不同 pair。Nacos 读取路径从 Naming `ServiceStorage` 加载完整内部
Service 投影，保留 Range 命中目标 Version 的 Contribution，再按公开 Endpoint 自然键聚合
查询时 Binding。Agent 代码不直接遍历 Naming Client Index。

AP 收敛后能够投影为同一个公开 Endpoint 的 Contribution 必须具有相同的规范化 URI、
Transport、Priority、Weight 和 Metadata。Register 不执行跨 Publisher 的写前扫描。
当收敛后的 `ServiceStorage` 投影包含冲突 Payload 时，受影响的读取或 Watch 返回
`CONFLICT`，不得任意选择一个值。移除任一冲突 Publication 后，投影随 Naming
正常收敛恢复。健康状态在匹配的有效 Contribution 间聚合：

- 至少一个健康贡献时得到 `healthy=true`；
- 全部贡献都不健康时得到 `healthy=false`；
- 移除一个发布者时只移除其贡献；
- 发布者数量变化但公开 Endpoint 未变化时，不改变 `sourceRevision`。

## 8. 顺序与容量

调用接口使用版本定义顺序，EndpointSet 使用声明的来源顺序，Endpoint 先按 Priority
升序，再按稳定自然键排序。健康状态不改变顺序，Weight 不参与 Registry 排序。

一个版本最多包含 16 个调用接口；一个声明 EndpointSet 最多包含 64 个 Endpoint；
一个运行时 EndpointSet 和一个 Endpoint Batch 分别最多包含 1000 个 Endpoint。
在线版本列表没有独立的产品上限，遵循第 3.4 节的响应大小规则。

每个 Publisher Client 默认具有 100 个 Runtime Endpoint 发布条目的软水位。Server
`application.properties` 使用
`nacos.ai.rad.capacity.publication.max-publications-per-client` 配置该水位。准入在一个完整
Registration Batch 执行前读取 Client 当前条目数；当前值低于水位时，整个已校验 Batch
原子放行，即使完成后超过水位。一旦 Client 已达到或超过水位，仍允许对已有
`(namespaceId, agentName, protocol)` Batch 进行等量替换或缩容，但以
`RESOURCE_EXHAUSTED` 原子拒绝新 Batch 或扩容替换，不创建局部 Naming Publication。
删除 Endpoint 条目或 Owner 断开会降低后续准入观察到的计数。

## 9. Binding Profile

Binding 声明其支持的 Profile 和可选能力：

| Profile 或能力 | 必须支持的操作 |
|---|---|
| Consumer Profile | `Search`、`Discover` |
| Publisher Profile | `Register`、`Deregister` |
| Watch 能力 | `Watch` |

符合 RAD 的 Binding 至少实现一个 Profile。Watch 在 RAD 核心层是可选能力。Nacos
首版 HTTP 和 gRPC Binding 都只实现 Consumer 与 Publisher Profile，不暴露服务端
Watch/Push 操作。Java SDK 后续可以通过周期执行 Discover 提供本地订阅便利能力；
这种轮询不表示对应传输支持 RAD Watch，也不增加 Watch Wire 消息。

## 10. 错误语义

Binding 将以下抽象类别映射到具体响应模型：

| 类别 | 典型场景 |
|---|---|
| `INVALID_ARGUMENT` | 字段非法、互斥字段冲突、自然键重复、范围非法或 Runtime Version 不在范围内 |
| `NOT_FOUND` | Discover 目标不存在、不可见、禁用或未上线；已订阅目标之后消失 |
| `PERMISSION_DENIED` | 调用方无权操作目标命名空间 |
| `RESOURCE_EXHAUSTED` | Endpoint 或 Publication 容量已满，或完整响应超过 Binding 限制 |
| `CONFLICT` | 收敛后的 Runtime Contribution 对同一 Endpoint 自然键包含不兼容 Payload |
| `UNSUPPORTED_CAPABILITY` | Binding 不支持请求的操作 |
| `UNAVAILABLE` | Registry 当前无法读取 Naming 状态或应用写入 |

不可见资源与不存在资源都表现为 `NOT_FOUND`，避免可见性侧信道。Filter 无匹配不是
错误，必须使用第 5 节规定的空结果形态。

## 11. 安全规则

Registry 在每个操作前执行命名空间和权限校验。Search、Discover 和 Watch 还要应用
资源可见性。Agent 定义尚不存在时，Register 也不能跳过权限校验。发布者身份不是
调用凭据。

Descriptor、URI 和 Metadata 都是不可信输入，不得保存明文凭据。发现结果不得暴露
连接归属、发布者身份、心跳或内部路由信息。Endpoint Metadata 不得使用 Nacos 内部
保留 Key。

## 12. Schema 与演进

规范性配套文件是使用 JSON Schema Draft 2020-12 的
[RAD 0.1.0 JSON Schema](../../schemas/ai/rad/0.1.0/rad-protocol.schema.json)。
普通对象使用严格属性集合，只有 Metadata Map 和 `nativeDescriptor` 是开放内容。
Schema Default 只是注解，生效值由实现物化。

新增字段、改变 `required`、扩大联合类型或改变枚举都需要新的 RAD 协议版本。领域
校验还要检查 SemVer、Version/Label 互斥、保留 Label、Endpoint 自然键、
Source/Health 条件、范围边界、顺序、Runtime Version 包含关系、Batch 原子性和容量。
JSON Schema 只校验 Version Range 字符串的粗略语法，不能替代领域校验。

## 13. 示例

### 13.1 Discover 请求

```json
{
  "namespaceId": "public",
  "reference": {"agentName": "Order Agent", "label": "latest"},
  "filter": {
    "protocols": ["a2a"],
    "transports": ["JSONRPC"],
    "endpointSources": ["RUNTIME", "DECLARED"],
    "metadataSelector": {"zone": "cn-hangzhou-h"}
  }
}
```

### 13.2 Discover 结果

```json
{
  "namespaceId": "public",
  "agentName": "Order Agent",
  "version": "1.0.6",
  "contentDigest": "sha256:1111111111111111111111111111111111111111111111111111111111111111",
  "callInterfaces": [{
    "protocol": "a2a",
    "protocolVersion": "1.0",
    "descriptorMediaType": "application/json",
    "nativeDescriptor": {"name": "Order Agent", "version": "1.0.6"},
    "endpointSets": [{
      "source": "RUNTIME",
      "sourceRevision": "murmur3-x64-128-v1:0123456789abcdef0123456789abcdef",
      "endpoints": [{
        "uri": "https://10.0.0.8:8443/a2a",
        "transport": "JSONRPC",
        "priority": 0,
        "weight": 1,
        "metadata": {"zone": "cn-hangzhou-h"},
        "healthy": true,
        "bindings": [{
          "runtimeVersion": "1.0.6",
          "versionRange": "[1.0.0,2.0.0)"
        }]
      }]
    }, {
      "source": "DECLARED",
      "sourceRevision": "sha256:1111111111111111111111111111111111111111111111111111111111111111",
      "endpoints": []
    }]
  }]
}
```

### 13.3 Register 请求

```json
{
  "namespaceId": "public",
  "agentName": "Order Agent",
  "runtimeVersion": "1.0.6",
  "versionRange": "[1.0.0,2.0.0)",
  "protocol": "a2a",
  "endpoints": [{
    "uri": "https://10.0.0.8:8443/a2a",
    "transport": "JSONRPC",
    "metadata": {"zone": "cn-hangzhou-h"}
  }]
}
```

### 13.4 Deregister 请求

```json
{
  "namespaceId": "public",
  "agentName": "Order Agent",
  "protocol": "a2a",
  "endpoints": [{
    "uri": "https://10.0.0.8:8443/a2a",
    "transport": "JSONRPC"
  }]
}
```

这是面向应用的 SDK 命令。SDK 从缓存 Batch 中删除该自然键，并发送完整的剩余
Register 请求；剩余 Batch 为空时，Nacos Binding 按 `namespaceId`、`agentName` 和
`protocol` 发送整份 Publication 注销。
