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
| Naming Client 状态 | 活跃 publisher contribution、健康、enabled 状态和 Version binding。 | Agent 定义或 Version 生命周期。 |

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

批次中全部 Endpoint 共享 Version binding 和 protocol。单元素数组就是通用单 Endpoint 形式。
命令本身不持久化。服务端在原子应用前校验完整批次。批次包含重复自然键时整体拒绝。
注册只 upsert 列出的 contribution，不删除未列出的 Endpoint；重复提交相同内容成功且不产生
语义变化。

`AgentEndpointDeregistrationBatch` 只包含 `namespaceId`、`agentName`、`protocol` 和
`endpoints[] {uri, transport}`。对于当前 publisher，每个自然 Endpoint key 会删除该 publisher
的全部 Version binding group。调用方不提交或缓存 endpoint id、runtime Version、range、
metadata、priority 或 weight。

### 4.3 内部 Publisher Contribution

内部 publication group 身份为：

```text
publisherIdentity
+ namespaceId + agentName + protocol
+ runtimeVersion + canonicalVersionRange
```

Endpoint contribution 身份在此基础上追加公开 Endpoint 自然键。必须保持以下区分：

- 同一 publisher 可以通过多个 runtime-Version/range group 绑定相同 host、port 和 transport；
- Version binding 不创建按 Version 划分的 Naming Service，也不在目标发现结果中复制公开
  Endpoint；
- 注册相同 contribution 身份执行 upsert；
- 新的通用注销命令删除当前 publisher 下与该自然 Endpoint 匹配的全部 group。

兼容 Adapter 可以使用内部 group-delete 操作。该操作只删除一个精确 publication group，
不属于公开 RAD 命令集。旧 A2A 精确 Version 注销使用该操作，因此不会误删同一 publisher
在其他 Version 上的 contribution。

全部活跃 contribution 中，相同公开自然 Endpoint key 必须具有唯一的 canonical 公开 Endpoint
payload，不受 publisher identity 或 Version range 是否相交影响。如果 URI scheme、path、query、priority、
weight 或公开 metadata 与已有 payload 不同，则以 contribution 冲突拒绝注册。只有 canonical
Endpoint payload 完全相同时，contribution 才能为同一自然键增加不同 Version binding。

### 4.4 Binding 聚合

Naming publisher contribution 聚合为包含 canonical `bindings[]` 数组的 Endpoint 投影：

```json
[
  {"runtimeVersion":"1.0.6","versionRange":"[1.0.0,2.0.0)"}
]
```

数组去重，并先按 `runtimeVersion` 的 Agent SemVer 升序排序，再按 `versionRange` 的
大小写敏感字符串升序排序。该精确数组是 Version 匹配事实。

每条有效 publisher record 使用 `__nacos.agent.endpoint.bindings__` 保存该数组。数组恰好
包含一项时，同时写入以下诊断和首版兼容镜像：

```text
__nacos.agent.endpoint.version__       = runtimeVersion
__nacos.agent.endpoint.versionRange__  = versionRange
```

数组包含多项时移除两个 singular key。读取方在 `bindings` 存在时始终以其为准，不得将
过期 singular key 合并进去。

`RuntimeEndpointSnapshot` 聚合 publisher contribution，但不暴露 publisher identity。每个公开
自然 Endpoint key 恰好对应一项，包含 canonical Endpoint payload 和全部有效 `bindings[]`。
按 Version 过滤的 Snapshot 只保留命中的 binding，并在没有剩余 binding 时删除该项。

RAD Discover 先按目标 Version 过滤 binding，再将相同自然键聚合为一个公开 Endpoint。由于写入
阶段会拒绝任何内容不一致的 payload，因此同一目标 Version 不会为同一自然键生成两个
不同公开 payload。

### 4.5 预注册与生命周期

Runtime 发布与 Agent 定义创建解耦。即使 Agent、Version 或 CallInterface 不存在，服务端也
接受结构合法且鉴权通过的发布。注册成功表示运行时意图已接受，不表示当前可发现。

注册校验 AgentName、runtime Version、range、protocol、Endpoint、鉴权、容量和 contribution
冲突。它不校验定义是否存在或 Version 生命周期状态。

Publisher identity 是内部状态：

- gRPC contribution 归属于 connection id；
- HTTP contribution 归属于校验后的 client id，并使用一个 Client 级 heartbeat；
- 公开管控和 RAD 对象不暴露 identity 或 publisher count。

断连或 Client 过期只删除该 publisher 的 contribution，其他相同 contribution 继续存在。只要
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

`clusterName` 是 normalized transport，匹配 `[0-9A-Za-z-]{1,64}`。Transport 同时存入
Cluster identity 和保留 metadata，读取时必须一致。

### 5.2 Instance 字段映射

| Agent 运行时字段 | Naming 字段 |
| --- | --- |
| `namespaceId` | Service namespace。 |
| 固定 group | `agent-endpoints`。 |
| 编码后的 Agent 和 protocol | 第 5.1 节的规范 serviceName。 |
| normalized transport | `Instance.clusterName`。 |
| normalized URI host 和 effective port | `Instance.ip`、`Instance.port`。 |
| URI path | `__nacos.agent.endpoint.path__`。 |
| normalized transport | `__nacos.agent.endpoint.transport__`。 |
| URI scheme | `__nacos.agent.endpoint.protocol__`。 |
| 旧 A2A protocol version | 可选 `__nacos.agent.endpoint.protocolVersion__`。 |
| HTTPS 状态 | `__nacos.agent.endpoint.supportTls__`。 |
| 原始 URI query | `__nacos.agent.endpoint.query__`。 |
| native tenant，非空时 | `__nacos.agent.endpoint.tenant__`。 |
| canonical binding | `__nacos.agent.endpoint.bindings__`。 |
| 单 binding 诊断镜像 | `__nacos.agent.endpoint.version__`、`__nacos.agent.endpoint.versionRange__`。 |
| priority | `__nacos.agent.endpoint.priority__`。 |
| weight | `Instance.weight`。 |
| 公开 Endpoint metadata | 其余 `Instance.metadata`。 |
| 运行状态 | `Instance.enabled`、`Instance.healthy`、`ephemeral=true`。 |

用户 metadata 不得覆盖任何 `__nacos.agent.endpoint.*__` key。服务端在接受 publication 前
构造并校验完整 Naming metadata。缺失的 range 输入在写 `bindings` 前完成 canonicalize。

`__nacos.agent.endpoint.protocolVersion__` 仅用于旧 A2A 兼容。只有 A2A 兼容 Adapter 可以写入；
它不属于公开 RAD Endpoint metadata，也不进入 Runtime revision。反向投影旧 A2A 响应时，Adapter
优先使用该值；缺失时回退到目标 CallInterface 的 `protocolVersion`。只有当一个聚合实例
中全部 A2A contribution 的该值相同时才写入这个单值 key；值不同时移除 key，并由每个精确
Version 投影使用目标 CallInterface fallback。该差异不构成公开 Endpoint payload 冲突。

公开自然键映射为 Service、Cluster、IP 和 port。Path 和 query 仍然是 payload metadata。
ServiceName 和 clusterName 都不包含 Version，因此 Service 数量不会随兼容 Agent Version 增长。

### 5.3 Naming 事实边界

包含 canonical binding 的 Naming Client publisher contribution 是 RUNTIME 事实源。普通 Naming
`ServiceInfo` 可能合并相同 IP 和 port、应用 selector 或健康保护行为，并且不能保留所有 Agent
publication group，因此不是 RAD 事实源。

Agent 运行时读取从 Naming Client/index 路径聚合原始 publisher contribution，再应用 binding 和
enabled filter。它们不得把标准 Naming Java SDK subscription 结果直接转发为 RAD Watch 快照。

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

针对每个 `(namespaceId, agentName, targetVersion, protocol, source=RUNTIME)`，服务端在完成
以下步骤后生成 opaque `sourceRevision`：

1. 聚合活跃 publisher contribution；
2. 选择包含目标 Version 的 binding；
3. 规范每个 Endpoint URI，校验并保持 transport，显式写入有效默认值 `priority=0` 和
   `weight=1`，并要求 `healthy` 存在；
4. 校验每个自然键只有一个 canonical payload；
5. 移除 `enabled=false`，并保留两种健康状态；
6. 按自然键排序 Endpoint，并按 UTF-16 code-unit ordinal 顺序排序 metadata key；
7. 对下文定义的 revision bytes 计算 MurmurHash3 x64 128。

在同一个投影中，自然键依次按 `normalizedHost` 的 UTF-16 code-unit ordinal 顺序、
`effectivePort` 的数值顺序、transport 的 UTF-16 code-unit ordinal 顺序比较。实现不得使用
locale-sensitive collation。URI path 和 query 不属于自然键，因此不参与排序。

外部 token 为：

```text
murmur3-x64-128-v1:<32 lowercase hex>
```

Revision 输入包含 URI、transport、effective priority 和 weight、公开 Endpoint metadata 和
`healthy`。它不包含 runtimeVersion、versionRange、publisher identity 和 count、heartbeat
时间、last-updated time 或 Naming 内部 revision。Runtime Version 和 range 不进入 hash，
因为目标投影已经完成过滤。Range 或 enabled 变化会改变成员；health 变化会改变返回内容。
目标投影变化时，两者都会推进 revision。

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

空集合恰好为 `uint32be(0)`。Murmur 结果先输出 `h1` 再输出 `h2`，每个都是 unsigned
八字节 big-endian 值，最后编码为小写十六进制。内部 Storage Schema version 1 同时以
机器可读形式记录这些规则。

实现对语义投影标脏、合并突发变化并缓存结果，不得对每次 heartbeat 或每次 Discover 读取都
执行 hash。

持久化 AgentVersion 内容继续使用 SHA-256。DECLARED Endpoint set 使用 Version
`contentDigest` 作为 opaque source revision。

## 8. 读写、缓存与一致性路径

| 读取 | 读取事实 | AI Storage 读取 |
| --- | --- | :---: |
| 管控 Agent 列表或 RAD Search | `ai_resource` page。 | 否 |
| Agent overview | Resource 和有界 Version-row page。 | 否 |
| 精确 Version detail | 一行 Version。 | 一次 |
| Runtime Endpoint Snapshot | 一个 protocol 的原始 Naming publisher contribution；可选 binding filter。 | 否 |
| RAD Discover | Resource、online Version、缓存内容和符合条件的 runtime 投影。 | Digest 未命中时一次 |

| 变化 | 写入目标 | 一致性规则 |
| --- | --- | --- |
| Agent 目录、治理、extensions | `ai_resource`。 | `metaVersion` CAS。 |
| 创建或更新 draft | AI Storage 固定 key 和 Version row。 | Pointer、bytes、size 和 digest 一致。 |
| Publish、online、offline、delete、label/latest | Version row 和 Resource 摘要。 | 重建派生目录。 |
| Runtime register、heartbeat、deregister | Naming Client 运行时状态。 | 不写 AI Resource 或 Storage。 |

缓存校验值跟随事实：

| 事实 | 校验值 |
| --- | --- |
| Agent 元数据 | `metaVersion`。 |
| Agent Version 内容 | `contentDigest`。 |
| 目标 Runtime 投影 | `sourceRevision`。 |

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

旧 single 和 batch 注册保留精确 Version replacement scope：

```text
(publisherIdentity, namespaceId, agentName, protocol=a2a,
 runtimeVersion=version, versionRange=[version])
```

兼容 Adapter 替换该内部 group。旧注销只删除该精确 group，即使同一 publisher 和物理
Endpoint 还存在其他 Version binding 也不会误删。新的 RAD 注销则删除当前 publisher 下所提交
自然 Endpoint 的全部 binding。

切换后，兼容写入使用新的 AI Resource、AI Storage 和 Naming layout。历史 Config row、历史
Naming Service、混合集群双读或双写、切换、回滚和异常历史身份处理属于独立的滚动升级与
迁移契约。
