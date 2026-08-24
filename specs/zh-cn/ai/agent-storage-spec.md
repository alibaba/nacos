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

# Agent 存储规范

本文档定义 [Agent 管理规范](agent-management-spec.md)的内部持久化、运行时发布、
Naming 映射、codec、digest 和 revision 契约。
[RAD 协议规范](rad-protocol-spec.md)定义外部数据面消息；本文档定义 Nacos 如何生成其事实。

本文档是 Agent 模型迁移的规范目标契约。服务端在实现本契约要求的存储行为之前不得声明
支持 Agent 或 RAD 能力。在切换之前，现有 A2A 存储仍由
[A2A Agent 规范](a2a-agent-spec.md)约束。

## 1. 存储职责

Agent 状态按照生命周期和访问模式拆分：

```text
Agent metadata -----------------------> ai_resource
Agent Version metadata ---------------> ai_resource_version
CallInterface + DECLARED Endpoint ----> AI Storage
                                          |
                                          +-- built-in nacos_config provider
RUNTIME publisher contributions ------> Naming Client runtime state
```

| 存储 | 拥有 | 不拥有 |
| --- | --- | --- |
| `ai_resource` | Agent 身份、目录、治理、Version 摘要和派生的 online 目录。 | Version payload 或运行时健康状态。 |
| `ai_resource_version` | 精确 Version 身份、生命周期状态、作者、Storage pointer 和 Pipeline 状态。 | CallInterface payload 或 Runtime Endpoint。 |
| AI Storage | 一个 Version 的 canonical `AgentVersionContent` bytes。 | 资源身份、生命周期、label 或可见性。 |
| Naming Client 状态 | 活跃 publisher contribution、健康、enabled 状态和 singular runtime Version/range 事实。 | Agent 定义或 Version 生命周期。 |

服务端不得持久化合并后的 `AgentDiscoveryResult`。Summary、管控详情、Catalog、Discover 和
Watch 对象都是上述事实的读取投影。

## 2. AI Resource 持久化

### 2.1 Agent Resource 行

标准 Agent 身份是 `namespaceId + type=agent + name=agentName`，按以下方式映射到
`ai_resource`：

| `ai_resource` 字段 | Agent 映射 |
| --- | --- |
| `namespace_id`、`type`、`name` | `namespaceId`、常量 `agent`、原始 `agentName`。 |
| `c_desc` | `description`。 |
| `status` | `enable` 或 `disable`。 |
| `owner`、`scope` | 同名治理字段。 |
| `biz_tags` | 用户设置的公开 tag。 |
| `ext` | 强类型 `AgentResourceExt`。 |
| `c_from` | 创建、导入或同步来源。 |
| `version_info` | 共享的 editing、reviewing、online count 和 label 摘要。 |
| `meta_version` | 元数据 CAS 版本。 |
| `gmt_create`、`gmt_modified` | 审计时间。 |

`AgentResourceExt` 具有固定的 schema version 1 结构：

| 字段 | 所有者 | 含义 |
| --- | --- | --- |
| `schemaVersion` | 服务端 | 常量 `1`。 |
| `displayName`、`iconUrl`、`provider` | 用户，服务端校验 | 目录展示。 |
| `extensions` | 用户，服务端校验 | 公开 Agent 级扩展。 |
| `versionCatalog` | 服务端 | 派生的 online Version 目录。 |

`versionCatalog` 包含 `latestVersion` 和 `onlineVersions[]`；每个条目只包含
`version`、`labels[]` 和 `protocols[]`；`onlineVersions` 按 Agent Version 优先级降序
存储。Version status 和 `version_info.labels` 仍然是事实。Publish、online、offline、
delete、label 或 latest 变化时，目录作为一次 Resource 逻辑更新进行重建。

`biz_tags` 不保存服务端派生索引；写入和读取投影必须保持用户 tag 的值和顺序。

RAD 按 Protocol 筛选的逻辑来源是
`AgentResourceExt.versionCatalog.onlineVersions[].protocols`。实现可以为查询性能维护独立的
派生 Protocol 索引，但该索引不得编码进 `biz_tags`，也不得增加、删除或重新解释公开 tag。

AgentName 和 Version 身份在 DAO 查询、唯一约束、cache、label 和鉴权 key 中按大小写敏感
比较。实现不得依赖数据库默认的大小写不敏感 collation。

### 2.2 Agent Version 行

每个 Agent Version 映射为一行 `ai_resource_version`：

| `ai_resource_version` 字段 | Agent Version 映射 |
| --- | --- |
| `namespace_id`、`type`、`name`、`version` | 精确 Version 身份。 |
| `status` | `draft`、`reviewing`、`reviewed`、`online` 或 `offline`。 |
| `author`、`c_desc` | 作者和变更说明。 |
| `storage` | Provider、opaque key、digest、媒体类型、schema 和大小。 |
| `publish_pipeline_info` | 审核执行过程和结果。 |
| `gmt_create`、`gmt_modified` | 审计时间。 |

Version 物理字段和所有新 Agent 写入使用相同的 64 字符上限。存储 Schema 不创建超出
Agent 管理契约的更宽公开身份空间。

Version 列表操作只读取 Resource 和 Version row。精确 Version detail 在定位 Version row 后
执行一次 AI Storage 读取。

## 3. AI Storage 中的 Agent Version 内容

### 3.1 内容对象与 Storage Pointer

一个 Version 恰好对应一个完整 Storage 对象：

```text
AgentVersionContent
  kind = AgentVersionContent
  schemaVersion = 1
  callInterfaces[]
    protocol / protocolVersion
    descriptorMediaType / nativeDescriptor
    endpointSourceOrder[]
    declaredEndpoints[]
```

服务端校验对象并构造下述存储投影，然后使用 Nacos 公共 JSON serializer 一次序列化为
UTF-8。同一份输出 bytes 传递给 AI Storage，并用于计算 `size` 和
`contentDigest=sha256:<lowercase hex>`。Agent Storage 不定义 JSON 语义规范化：两个解析后
等价的 JSON 表达不要求得到相同摘要。

序列化前，服务端先构造 storage projection：

1. 拒绝 envelope、CallInterface 和 Endpoint object 上的未知 schema property，再仅投影
   schema version 1 定义的字段；
2. 使用公共 Endpoint canonicalizer 规范每个 declared Endpoint 的 URI，并校验且保持其
   transport 原值；
3. 显式写入 Endpoint 的有效默认值 `priority=0` 和 `weight=1`；
4. 省略缺失或为空的 Endpoint `metadata` 和 `declaredEndpoints`；
5. 除上述规范化外，保持所有数组顺序和 descriptor JSON value 不变。

`nativeDescriptor` 的 JSON member 和 Endpoint `metadata` map entry 仍是开放内容，但必须
满足各自定义的 value 约束。

这些投影规则只规范 Agent 自有字段，不重排 `nativeDescriptor` 内部 member，也不改写协议
自有 JSON。读取时先对 AI Storage 返回的原始 bytes 计算摘要再解码，不得通过重新序列化
解码后的对象完成摘要校验。

Version 行的 `storage` JSON 包含：

| 字段 | 值或含义 |
| --- | --- |
| `provider` | Storage provider；内置值为 `nacos_config`。 |
| `key` | Provider opaque key。 |
| `keyFormat` | 内置 provider 使用 `agent-version-config-v1`。 |
| `agentNameCodec` | 内置 provider 使用 `rad-ascii-v1`。 |
| `contentDigest` | `sha256:<lowercase hex>`。 |
| `mediaType` | `application/vnd.nacos.agent-version+json`。 |
| `schemaVersion` | `1`。 |
| `size` | 持久化内容字节数。 |

Agent service 生成统一的、provider-neutral 的逻辑 `StorageKey.key`，并将其作为 opaque 值
传给所有 provider。替换 provider 时仍保持一个 Version 对应一个对象，并由 provider 管理
该逻辑 key 到物理 key 的映射。内置 provider 使用第 3.2 节的映射。

### 3.2 内置 Nacos Config 映射

`agent-version-config-v1` provider key 携带下列逻辑 Config 坐标。Agent service 负责生成逻辑
`StorageKey.key`；该值存入 descriptor 后，上层消费者只透传而不解析，内置 provider 仅在执行
下列映射时解析它。

| 逻辑值 | 逻辑 `config_info` 坐标 |
| --- | --- |
| `namespaceId` | `tenant_id=namespaceId`。 |
| 内容类别 | `group_id=agent-version`。 |
| `agentName`、`version` | `data_id=agent__<encodedAgentId>__<version>.json`。 |
| `AgentVersionContent` | `type=json` 的 UTF-8 JSON `content`。 |

内置 provider 随后对完整逻辑 group 和 data id 应用通用 `NacosAiConfigKeyCodec`。Config
限制内的安全值原样保存；超长 data id 使用 codec 确定性的 `sha256.<digest>` 物理回退。
因此物理 key 不一定可逆，任何上层都不得从中推导 Agent 身份。不能仅因为逻辑 data id
长于 Config 物理限制而拒绝合法 Agent 身份。

Provider-neutral 的 `StorageKey.key` 将逻辑身份序列化为
`<namespaceId>:agent-version:<logicalDataId>`。所有 provider 都接收该值，但只有内置 provider
将其解析为上述 Config 坐标。Namespace、编码后的 AgentName 和 Version 语法均排除 `:`，
因此该 key 必须恰好包含三个冒号分隔段。已有 Skill、Prompt 和 AgentSpec 的四段、五段 key
保持原解释方式。

更新 draft 时覆盖相同 key。Version 进入 reviewing 后内容不可变。`contentDigest` 不参与
data id，只用于校验实际持久化 bytes 和缓存相等性。读取、审核和发布操作必须校验
Storage pointer、byte count 和 digest。

### 3.3 RAD ASCII AgentName Codec

Config data id 和 Naming serviceName 共用 codec id 为 `rad-ascii-v1` 的
`RadAsciiAgentIdCodec`：

1. 输入是原始的 1～64 个可打印 ASCII 字符 `agentName`；
2. 整个输入匹配 `[A-Za-z0-9-]+` 时原样返回；
3. 否则输出 `enc-<body>`；
4. 编码形式保留 ASCII 字母和数字，其他所有字符（包括 `-`）都编码为 `-DDD`，其中
   `DDD` 是三位十进制 ASCII 值；
5. 保留字母大小写，永不 trim 或 lowercase；
6. 只解码已经确定使用该 codec 的物理段，并拒绝截断、非十进制、越界或非 canonical
   escape。

示例：

```text
Nacos-Agent  -> Nacos-Agent
Nacos Agent  -> enc-Nacos-032Agent
name-ok.1:2  -> enc-name-045ok-0461-0582
```

输出只包含 `[A-Za-z0-9-]`。Codec version 1 不保留以 `enc-` 开头的原始名称命名空间，
因此一个原始安全名称与另一个名称的编码结果理论上可能生成相同物理段。Version 1 接受该低概率
歧义，不定义冲突索引、前缀保留或原子的 encoded-id 映射。公开身份始终来自
`ai_resource.name`；代码不得通过解码无类型物理 key 推断身份。未来如需无冲突 codec，必须使用
新的 codec id 和显式迁移契约，不能原地改变 `rad-ascii-v1`。

Version 只使用字母、数字、`.` 和 `-`，不经过 AgentName codec。通用 Config 物理 key
codec 可以仅为满足物理长度限制而散列整个逻辑 data id；这不会截断、散列或改写任一公开
身份字段。

## 4. Runtime 发布模型

### 4.1 公开 Endpoint 与 Version Binding

DECLARED 和 RUNTIME 来源共用 Endpoint 值对象。在一个 Agent protocol 分组中，公开 Endpoint
自然键为：

```text
(namespaceId, agentName, protocol,
 normalizedHost(uri), effectivePort(uri), normalizedTransport)
```

URI path、query、priority、weight 和 metadata 是公开 Endpoint payload，但不参与自然键。
不存在公开 Endpoint id。

运行时 Version binding 包含：

| 字段 | 含义 |
| --- | --- |
| `runtimeVersion` | 实际运行的实现 Version。 |
| `versionRange` | 该 publication 可以服务的 Agent Version。 |

缺失 range 时规范化为精确 `[runtimeVersion]`。Range 是单个 Maven 风格连续区间，其边界和
比较使用大小写敏感的 Agent Version 规则，而不是 Maven `ComparableVersion`。

Canonical 形式包括精确 `[1.0.6]`、有界 `[1.0.0,2.0.0)`、仅下界 `[1.0.0,)` 和
仅上界 `(,2.0.0]`。其中不含空白，至少存在一个边界；相同且闭合的上下界使用精确形式。
区间并集和离散集合非法。`runtimeVersion` 必须命中其 range。

### 4.2 发布命令

`AgentEndpointRegistrationBatch` 包含：

```text
namespaceId / agentName / runtimeVersion / versionRange? / protocol
endpoints[1..1000]
```

批次中全部 Endpoint 共享一组 `runtimeVersion`/`versionRange` 和 protocol。单元素数组就是通用
单 Endpoint 形式。命令本身不持久化。该批次是当前 publisher 对组合后 Naming Service 的完整期望状态。服务端校验
完整批次后委托 Naming `batchRegisterInstance`；Naming 原子替换同一 Client 和 Service 的旧批次，
未列出的 Endpoint 会被删除。批次包含重复自然键时整体拒绝；重复提交相同内容具有幂等性。

`AgentEndpointDeregistrationBatch` 只包含 `namespaceId`、`agentName`、`protocol` 和
`endpoints[] {uri, transport}`。它是 SDK 侧的操作意图，不是服务端局部删除命令。SDK 从 redo
状态移除这些自然键，再通过同一注册路径提交保留后的完整批次；没有 Endpoint 时注销整个
Client 和 Service publication。服务端不会为局部注销读取并合并旧批次。

### 4.3 内部 Publisher Contribution

Naming publication 身份为：

```text
publisherIdentity
+ namespaceId + agentName + protocol
```

Naming 为该 Client 和 Service 只保存一份 `BatchInstancePublishInfo`。其中 Instance 共享一组
`runtimeVersion` 和 canonical `versionRange`。后续注册完整替换该记录及这两个共享字段。
因此首版同一 publisher、Agent 和 protocol 只能保存一组 singular
`runtimeVersion`/`versionRange`。如果未来需要同时保存多组，应由客户端提供完整快照 wire model，
而不是由服务端执行 read-merge-write。

Agent 层不会读取旧 publisher 记录，不直接依赖 `ClientServiceIndexesManager`，不增加 Service 锁，
也不在写入前扫描其他 publisher。Naming 负责完整替换、连接清理、索引、事件和 Distro AP 收敛；
Agent 层只校验和转换完整批次。

不同 publisher 的 Instance 可能在收敛后映射到同一公开自然 Endpoint key。读取投影聚合 canonical
payload 完全相同的 contribution。如果收敛后的 Naming 状态对同一自然键包含不同 URI payload 字段、
priority、weight 或公开 metadata，读取返回 `RESOURCE_CONFLICT`，不得任意选择。该检查用于保证
投影安全，不是写时 reservation 或 CP 约束。

### 4.4 Binding 聚合

每条 Naming Instance 使用以下 singular metadata 保存一个 canonical binding：

```text
__nacos.agent.endpoint.version__       = runtimeVersion
__nacos.agent.endpoint.versionRange__  = canonicalVersionRange
```

Naming metadata 中没有序列化的 `bindings` 值。只有读取 Naming Service 投影时才创建
`RuntimeVersionBinding` 对象和公开 `bindings[]`。Binding 去重后先按 `runtimeVersion` 的
Agent SemVer 升序排序，再按 `versionRange` 的大小写敏感字符串升序排序。

`RuntimeEndpointSnapshot` 从 `ServiceStorage` 读取完整的 Naming 内部 Service 投影，再聚合其中的
Instance，但不暴露 publisher identity。每个公开自然 Endpoint key 恰好对应一项，包含 canonical
Endpoint payload 和全部有效 `bindings[]`。按 Version 过滤的 Snapshot 只保留命中的 binding，并在没有
剩余 binding 时删除该项。`ServiceStorage` 可以去重完全相同的 Instance；由于相同 Instance 的 payload、
bindings、enabled 和 healthy 最终本就聚合为同一公开项，这不会改变公开投影。

RAD Discover 先按目标 Version 过滤 binding，再将相同自然键聚合为一个公开 Endpoint。投影重建时
拒绝 AP 收敛后可见的不一致 payload。因此成功生成的同一目标 Version 投影不会为同一自然键包含
两个不同公开 payload。

### 4.5 预注册与生命周期

Runtime 发布与 Agent 定义创建解耦。即使 Agent、Version 或 CallInterface 不存在，服务端也
接受结构合法且鉴权通过的发布。注册成功表示运行时意图已接受，不表示当前可发现。

注册校验 AgentName、runtime Version、range、protocol、Endpoint、鉴权和批次容量。它不校验
定义是否存在、Version 生命周期状态或其他 publisher 的当前值。

Publisher identity 是内部状态：

- gRPC contribution 归属于 connection id；
- HTTP contribution 归属于通用 `HTTP_CLIENT@@<externalClientId>` Naming Client，并使用
  一个 Client 级 Publisher heartbeat；
- 公开管控和 RAD 对象不暴露 identity 或 publisher count。

断连、Publisher 过期或 Client 过期只删除该 publisher 的 contribution，其他相同 contribution
继续存在。HTTP 查询只续约 Client，不续约、恢复或保留 Publisher。只要
至少一个匹配的活跃 contribution 健康，聚合 `healthy` 就为 true；只有全部不健康时才为 false。
仅 heartbeat 或 publisher 数量变化不会改变公开投影。

`enabled` 是独立的 Naming 运维状态，不被 heartbeat 覆盖。Agent Endpoint metadata 不得设置
Naming heartbeat interval、heartbeat timeout 或 instance-delete timeout key。显式注销、
publisher 丢失或 Naming cleanup 会结束运行时状态。Agent disable、Version offline 或定义删除
只会把它从适用的发现投影中移除。

## 5. Runtime 到 Naming 的映射

### 5.1 Service 与 Cluster 身份

Naming 逻辑范围为：

```text
namespaceId
+ groupName=agent-endpoints
+ serviceName=radServiceName(encodedAgentId, protocol)
+ clusterName=normalizedTransport
```

规范 protocol token 匹配 `[A-Za-z0-9][A-Za-z0-9-]{0,31}`。ServiceName 算法为：

```text
rad-<encodedAgentId>-<protocol>
```

结果保留大小写，只包含 `[A-Za-z0-9-]`，以字母或数字开头；protocol token 可以以 `-`
结尾。结果不包含 Version；按字段长度限制计算，实际最大长度为 297，低于 Naming 的 512
字符限制。

示例：

```text
Nacos-Agent / a2a -> rad-Nacos-Agent-a2a
Nacos Agent / a2a -> rad-enc-Nacos-032Agent-a2a
```

Version 1 优先保持物理名称简洁可读，不为 `encodedAgentId` 和 protocol 增加长度 framing。
因此 `(A, B-C)` 与 `(A-B, C)` 都会生成 `rad-A-B-C`。Version 1 接受该低概率冲突，不定义
冲突索引或额外消歧逻辑，也不得从 serviceName 反解两个组成部分。读取方使用已知的 AgentName
和 protocol 重新组合并比较。未来如需无冲突组合规则，必须使用新的 composer id 和显式迁移契约。

该字符集只保证 `lb://<serviceName>` 能作为 Gateway URI 正常解析；它不定义 DNS 名称，也不
lowercase 大小写敏感的 Nacos Service 身份。会把 service id 规范化为小写的集成不在该兼容
保证内。

公开 normalized transport 匹配 `[0-9A-Za-z+-]{1,64}`。Naming `clusterName` 使用
`RadAsciiAgentIdCodec.encode(transport)` 生成，因此只包含 `[A-Za-z0-9-]`；例如
`HTTP+JSON -> enc-HTTP-043JSON`。原始 transport 同时存入保留 metadata，读取时必须重新编码
metadata transport 并与 clusterName 交叉校验，不能从 clusterName 反向推断公开 transport。

### 5.2 Instance 字段映射

| Agent 运行时字段 | Naming 字段 |
| --- | --- |
| `namespaceId` | Service namespace。 |
| 固定 group | `agent-endpoints`。 |
| 编码后的 Agent 和 protocol | 第 5.1 节的规范 serviceName。 |
| encoded normalized transport | `Instance.clusterName`。 |
| normalized URI host 和 effective port | `Instance.ip`、`Instance.port`。 |
| URI path | `__nacos.agent.endpoint.path__`。 |
| normalized transport | `__nacos.agent.endpoint.transport__`。 |
| URI scheme | `__nacos.agent.endpoint.protocol__`。 |
| 旧 A2A protocol version | 可选 `__nacos.agent.endpoint.protocolVersion__`。 |
| HTTPS 状态 | `__nacos.agent.endpoint.supportTls__`。 |
| 原始 URI query | `__nacos.agent.endpoint.query__`。 |
| native tenant，非空时 | `__nacos.agent.endpoint.tenant__`。 |
| runtime Version | `__nacos.agent.endpoint.version__`。 |
| canonical Version range | `__nacos.agent.endpoint.versionRange__`。 |
| priority | `__nacos.agent.endpoint.priority__`。 |
| weight | `Instance.weight`。 |
| 公开 Endpoint metadata | 其余 `Instance.metadata`。 |
| 运行状态 | `Instance.enabled`、`Instance.healthy`、`ephemeral=true`。 |

用户 metadata 不得覆盖任何 `__nacos.agent.endpoint.*__` key。服务端在接受 publication 前
构造并校验完整 Naming metadata。缺失的 range 输入在写 `versionRange` 前完成 canonicalize。

`__nacos.agent.endpoint.protocolVersion__` 仅用于旧 A2A 兼容。只有 A2A 兼容 Adapter 可以写入；
它不属于公开 RAD Endpoint metadata，也不进入 Runtime revision。反向投影旧 A2A 响应时，
兼容 Adapter 优先使用该值；缺失时回退到目标 CallInterface 的 `protocolVersion`。

公开自然键映射为 Service、Cluster、IP 和 port。Path 和 query 仍然是 payload metadata。
ServiceName 和 clusterName 都不包含 Version，因此 Service 数量不会随兼容 Agent Version 增长。

### 5.3 Naming 事实边界

Naming Client 状态仍是 RUNTIME 写入事实，负责 publisher identity、连接或分层 heartbeat 活性、失活清理，
完整批次替换、索引、事件和 AP 收敛。注册把完整 Agent Endpoint 批次转换为 Naming Instance 并调用
Naming 一次；完整注销移除 Client 和 Service publication。Agent 服务端不读取或合并旧 publisher 记录。

HTTP publication 复用 Naming 通用 `HttpConnectionBasedClient`、
`ClientManagerDelegate` 和 `Nacos:Naming:v2:ClientData` Distro 链路。AI 模块只负责
external Client id 校验、自己的 Distro Filter 路由，以及 Agent Endpoint 到 Naming Instance 的转换；
不维护 Agent 专用 ClientData processor 或 Distro resource type。

Runtime Snapshot 和 Discover 从 Naming `ServiceStorage` 缓存读取完整的内部 Service 投影。该投影由
service-scoped Client index 构建，并包含运维 Instance metadata。Agent 层随后解析每条 Instance 的
singular binding、应用可选的目标 Version filter、校验 payload 一致性，并按公开 Endpoint 自然键聚合。
`ServiceStorage` 内部对完全相同 Instance 的去重是安全的，因为冗余且完全相同的 publication 不会
改变任何公开聚合字段。

`ServiceStorage` 返回的内部 `ServiceInfo` 容器与通过 Naming SDK、HTTP API、selector 或健康保护链路
对外返回的 Naming 结果不是同一契约。不得把外部或已经过后处理的 Naming `ServiceInfo` 作为 Runtime
事实源，也不得将其直接转发为 RAD Watch 快照。

`enabled` 和 `weight` 的 Naming 运维 metadata 按普通规则优先于运行时 publication 值。Agent
投影仍然保留 unhealthy Instance 并暴露其原始聚合健康状态，不应用 Naming 健康保护回退。

## 6. Runtime 发现投影

RUNTIME Endpoint 只有在同时满足以下条件时才进入一个目标发现结果：

1. Agent 存在、调用者可见且已 enable；
2. 目标 Version 为 online；
3. 目标 Version 存在相同 protocol 的 CallInterface，并允许 `RUNTIME` 来源；
4. 至少一个有效 binding 包含目标 Version；
5. Naming Endpoint 的 `enabled=true`。

符合条件但 `healthy=false` 的 Endpoint 仍然进入 RAD 输出。SDK `selectOneHealthy` 会过滤它；
get-all 和 Watch 保留它。Disabled Endpoint 不出现。

投影使用目标 Version 的 CallInterface 获取 protocol Version、descriptor 和 endpoint source order。
Runtime contribution 永远不覆盖这些定义字段；RAD 忽略 Naming 中仅供旧兼容使用的 protocol-version
metadata。

## 7. Runtime Source Revision

针对每个 Runtime 发现投影，服务端在完成以下步骤后生成 opaque `sourceRevision`：

1. 从 `ServiceStorage` 读取完整的 Naming 内部 Service 投影；
2. 选择至少包含一个兼容目标 Version 的 binding；
3. 规范每个 Endpoint URI，校验并保持 transport，显式写入有效默认值 `priority=0` 和
   `weight=1`，并要求 `healthy` 存在；
4. 校验每个自然键只有一个 canonical payload；
5. 移除 `enabled=false`，并保留两种健康状态；
6. 为每个 enabled Endpoint 附加有序去重的命中 binding 并集；
7. 按自然键排序 Endpoint，并按 UTF-16 code-unit ordinal 顺序排序 metadata key；
8. 对下文定义的 revision bytes 计算 MurmurHash3 x64 128。

在同一个投影中，自然键依次按 `normalizedHost` 的 UTF-16 code-unit ordinal 顺序、
`effectivePort` 的数值顺序、transport 的 UTF-16 code-unit ordinal 顺序比较。实现不得使用
locale-sensitive collation。URI path 和 query 不属于自然键，因此不参与排序。

外部 token 为：

```text
murmur3-x64-128-v1:<32 lowercase hex>
```

Revision 输入包含 URI、transport、effective priority 和 weight、公开 Endpoint metadata、
`healthy`，以及返回的每个 `runtimeVersion` 和规范化 `versionRange` binding。它不包含
publisher identity 和 count、heartbeat 时间、last-updated time 或 Naming 内部 revision。
binding 或在线兼容目标集合变化时，只要发现可见投影改变，即使 Endpoint payload 未变也会
推进 revision。

公开 Endpoint metadata 缺失或为空时，metadata entry count 均编码为零。

空集合具有稳定 revision。增加或删除冗余 publisher 不改变它。Token 只用于 cache equality 和
Watch 去重，不用于身份、鉴权、CAS 或防篡改。

所有节点使用 seed `0` 和以下固定的 big-endian 二进制布局：

| 元素 | 编码 |
| --- | --- |
| Endpoint 数量 | unsigned 四字节整数。 |
| `uri`、`transport` | unsigned 四字节 UTF-8 byte length，随后写入 bytes。 |
| `priority` | signed 四字节整数。 |
| `weight` | 八字节 IEEE-754 binary64 bits；negative zero 规范为 positive zero。 |
| metadata | unsigned 四字节 entry count；随后按上述 string 编码依次写入有序 key 和 value。 |
| `healthy` | 一个 byte：false 为 `0`，true 为 `1`。 |
| bindings | unsigned 四字节 binding count；随后按上述 string 编码写入每个有序 `runtimeVersion` 和规范化 `versionRange`。 |

空集合恰好为 `uint32be(0)`。Murmur 结果先输出 `h1` 再输出 `h2`，每个都是 unsigned
八字节 big-endian 值，最后编码为小写十六进制。内部 Storage Schema 同时以机器可读
形式记录这些规则。

Naming `ServiceStorage` 提供当前已缓存的 Service 投影。Agent 读取路径直接从该结果派生公开
Endpoint set 及其 revision，不维护第二份投影缓存。

持久化 AgentVersion 内容继续使用 SHA-256。DECLARED Endpoint set 使用 Version
`contentDigest` 作为 opaque source revision。

## 8. 读写、缓存与一致性路径

| 读取 | 读取事实 | AI Storage 读取 |
| --- | --- | :---: |
| 管控 Agent 列表 | `ai_resource` page。 | 否 |
| RAD 或通用 Agent Search | 当前 Agent Search document；`AUTO/INDEX` 在未 READY 时可返回部分当前快照，`SCAN` 显式使用兼容扫描。 | 否 |
| Agent overview | Resource 和有界 Version-row page。 | 否 |
| 精确 Version detail | 一行 Version。 | 一次 |
| Runtime Endpoint Snapshot | 一个 protocol 的完整内部 `ServiceStorage` 投影；可选 binding filter。 | 否 |
| RAD Discover | Resource、online Version、缓存内容和符合条件的 runtime 投影。 | Digest 未命中时一次 |

| 变化 | 写入目标 | 一致性规则 |
| --- | --- | --- |
| Agent 目录、治理、extensions | `ai_resource`。 | `metaVersion` CAS。 |
| 创建或更新 draft | AI Storage 固定 key 和 Version row。 | Pointer、bytes、size 和 digest 一致。 |
| Publish、online、offline、delete、label/latest | Version row 和 Resource 摘要。 | 重建派生目录。 |
| Runtime register、Publisher heartbeat、deregister | Naming Client 运行时状态。 | 不写 AI Resource 或 Storage。 |
| Agent 目录或 Version 生命周期提交 | `ai_resource_task` 中合并的 `search_index` revision。 | 异步重读事实并重建派生索引。 |

缓存校验值跟随事实：

| 事实 | 校验值 |
| --- | --- |
| Agent 元数据 | `metaVersion`。 |
| Agent Version 内容 | `contentDigest`。 |
| 目标 Runtime 投影 | `sourceRevision`。 |

Naming `ServiceStorage` 负责完整的 per-Service 投影缓存。Agent 层不维护额外 Runtime registry 或
投影缓存；每次从当前 `ServiceStorage` 结果派生所需 Agent 投影，并根据最终公开 Endpoint set 计算
`sourceRevision`。

AI Storage provider 保证单个 StorageKey 的原子 bytes 和它声明的读取一致性。Agent Registry
负责跨 Resource、Version、Storage pointer、digest 和派生目录的编排，并执行校验、幂等重试
和失败补偿。Publish 前必须重新读取内容并校验 digest。

Draft 更新先校验目标 Version 等于 Resource 当前 `editingVersion` 且仍为 draft，再覆盖该
Version 已有的固定 StorageKey，最后复用 AI Resource 现有的 `updateStorageAndDesc` 更新
Storage pointer 与说明。Agent 层不增加资源专用的 compare-and-set 机制。跨
`ai_resource_version` 和 AI Storage 的条件更新属于通用 AI Resource 能力，后续必须由
Agent、Prompt、Skill 和 AgentSpec 统一采用。

Storage 写入成功但元数据写入失败时，形成可观测的不完整操作，并通过重试或孤儿内容清理处理。
Digest 不一致时不得返回未校验内容。`versionCatalog` 和 Resource Version 摘要是可重建
派生数据；其一致性不得下放给 Storage provider。

Agent Search 投影同样是可重建派生状态。每个 `(namespaceId, agentName)` 最多存在一个当前
document，其 `resourceVersion` 为 common latest 指向的精确 online Version，并包含完整、确定性
排序的 online Version 目录、`protocols` 和 `artifactKinds` facets、projection version 与
source digest。Source digest 由 canonical Agent metadata、Version 目录、common latest、latest
Version `contentDigest`、artifactKinds 和 projection version 计算，不以修改时间作为唯一依据。

关系 document、chunks 和内嵌 facets 在一个事务中完整替换。索引不保存 Runtime Endpoint、健康、
Publisher、心跳或 Runtime revision。Backfill/Reconciliation 通过资源类型处理器按有界 resource-key
批次扫描，并按 `(agent, projectionVersion)` 维护集群 readiness；具体任务、lease 和读模式语义由
[AI 资源检索规范](ai-resource-search-spec.md)定义。

## 9. 容量与安全

| Runtime 或物理字段 | 上限 |
| --- | ---: |
| 序列化后的 `biz_tags` JSON | 1024 字符；只包含用户 tag。 |
| `runtimeVersion` | 64 字符。 |
| Canonical `versionRange` | 256 字符；一个连续区间。 |
| 注册批次 | 1～1000 个 Endpoint。 |
| 每个 Agent 和 protocol 的 Runtime Endpoint | 1000，可由集群配额调低。 |
| 最终 Endpoint metadata | 32 个公开项；key 64、value 256 字符。 |
| 最终 Naming metadata | key 和 value 的 Java `String.length()` 合计 1024。 |
| Agent Version 物理 Config data id | 255 字符，由 `NacosAiConfigKeyCodec` 保证；超长逻辑 id 使用其 SHA-256 回退。 |
| Agent Version content | 1 MiB。 |

服务端在写 Naming 前校验包含保留 key 的完整生成 metadata。超限时拒绝，不得截断或静默
丢弃字段。

AI Storage content、Endpoint metadata 和 publisher state 不得包含明文 credential。日志和审计
事件不得向普通用户暴露完整 native descriptor、security scheme、publisher identity 或敏感
Endpoint metadata。

## 10. A2A Runtime 兼容边界

A2A Adapter 是本存储契约的首个使用方：

| 旧 A2A 事实 | 新存储投影 |
| --- | --- |
| AgentCard 定义 | Version content 中的 A2A `AgentCallInterface.nativeDescriptor`。 |
| Root 和 additional interface | Adapter 派生的 DECLARED Endpoint。 |
| Runtime AgentEndpoint Version | `runtimeVersion=version`、`versionRange=[version]`。 |
| Runtime 调用 protocol | 规范 Agent protocol token `a2a`。 |
| 旧 Endpoint transport 和 URI 部分 | 通用 Endpoint 和 Naming 保留 metadata。 |

`CANONICAL` 兼容分支把旧 A2A Runtime 注册写入公共 Version-neutral Service。为满足本规范
“每个 publisher 对一个 Service 只有一份完整 singular binding 批次”的约束，Adapter 按
`(原 connection, namespaceId, agentName, exactVersion)` 派生内部子 publisher。每个子 publisher
提交该精确 Version 的完整批次，因此不同 Version 不会覆盖，也不需要服务端 read-merge-write；
原 connection 断开时释放全部子 publisher。

`LEGACY` 分支仍完整使用历史按 Version 划分的 Naming layout。Beta 的 `CANONICAL` 分支不向历史
Service 双写。直接依赖旧 Naming serviceName 的 Gateway 调用方兼容、混合集群双读或双写、回滚、
旧 Service 清理和异常历史身份处理属于独立的 Beta 后滚动升级与迁移契约。
