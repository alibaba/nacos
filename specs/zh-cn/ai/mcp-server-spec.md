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

# MCP Server 规范

| 项目 | 值 |
| --- | --- |
| 状态 | 实验性目标迁移契约 |
| 标准资源类型 | `mcp` |
| 迁移路由状态 | `SYNCING` 或 `CANONICAL` |
| Direct 兼容状态 | `SYNCING`、`CANONICAL_COMPAT` 或未来的 `PROJECTION_RETIRED` |

本文定义 MCP Server 资源在 Nacos AI Registry 中的契约，包括从历史 Config metadata
迁移到标准 AI Resource 生命周期的规则。在迁移实现完成前，历史实现仍是实际生效的代码路径；
服务端不得把本文的目标契约宣告为已实现能力。

该迁移只解决 MCP 自身问题，不引入所有 AI 资源共享的新上层抽象，不改变 Naming 语义，
也不会仅为获得不同物理 key 而搬运历史 MCP payload。

## 1. 范围与事实边界

MCP 状态按所有权和生命周期拆分：

```text
MCP metadata --------------------------> ai_resource
MCP Version 治理 ----------------------> ai_resource_version
Server / Tools / Resources 内容 -------> 原有 Config 坐标
Direct Endpoint 事实 ------------------> Version Server Config
Direct 降级投影 -----------------------> 持久 Naming Service
普通 Service Ref ---------------------> 外部所有的 Naming Service
Runtime Endpoint publication ---------> Naming Client 运行时状态
```

| 事实 | 切换后的标准所有者 |
| --- | --- |
| MCP 身份、状态、owner、scope、tag、label 和工作 Version 指针 | `ai_resource` |
| Version 状态、author、描述、pipeline 状态和 storage pointer | `ai_resource_version` |
| Version Server、Tools 和 Resources 内容 | Version storage descriptor 选中的原有 Config 对象 |
| Direct Endpoint 地址 | Version Server Config 中的 `endpointKind` 和 `directEndpoints` |
| Direct 持久 Naming Service | MCP 所有的降级投影；标准读路径绝不依赖它 |
| 普通被引用 Service | 该 Service 的 Naming 用户 |
| Runtime Endpoint | Naming Client publisher 及其 connection/liveness 生命周期 |
| 历史 `mcp-server-versions` 对象 | 切换后的兼容投影；不再参与标准决策 |

MCP Registry 兼容发现仍是[AI Registry 适配器规范](ai-registry-adaptor-spec.md)定义的
可选适配表面，不是第二套 MCP 资源存储。

## 2. 身份与 AI Resource 映射

### 2.1 资源身份

标准身份为：

```text
namespaceId + type=mcp + name=mcpName
```

`mcpName` 区分大小写，且作为身份字段不可修改。历史 UUID 形态的 `mcpId` 只在
`AiResource.ext` 中作为 API 和存储兼容别名保留；它不参与标准身份、鉴权、label 或
Runtime Service 组合。

Schema Version 1 的扩展为：

```json
{
  "schemaVersion": 1,
  "mcpId": "4d7939c0-72ea-4ef4-b232-418d1e16b45c"
}
```

对应机器可读契约为
[`mcp-resource-ext.schema.json`](../../schemas/ai/mcp/internal/v1/mcp-resource-ext.schema.json)。

Resource row 按以下规则映射 MCP 字段：

| `AiResource` 字段 | MCP 映射 |
| --- | --- |
| `namespaceId`、`type`、`name` | Namespace、固定值 `mcp` 和 `mcpName`。 |
| `desc` | MCP 描述。 |
| `status` | 历史 `enabled=true` 映射为 `enable`，否则为 `disable`。 |
| `owner` | 创建或导入操作人；历史同步使用 `nacos`。 |
| `scope` | 新资源使用 Visibility 默认值；历史同步使用 `PUBLIC`。 |
| `bizTags` | 公开 MCP 业务 tag，无值时为空集合。 |
| `ext` | 包含兼容 `mcpId` 的 `McpResourceExt`。 |
| `from` | create、import 或 `legacy-mcp` 同步来源。 |
| `versionInfo` | 标准 editing、reviewing、online count 和 label 摘要。 |

### 2.2 Version 身份与状态

每个 MCP Version 对应一条 `AiResourceVersion` row，精确身份为：

```text
namespaceId + type=mcp + mcpName + version
```

MCP Version 非空、区分大小写且最长 64 个字符，从而无需修改表结构即可写入共享 Version row。
建议使用严格 SemVer，Range 匹配也要求 SemVer；但长度范围内的历史非 SemVer 值仍是有效的
精确身份，迁移不得改写这些值。超过长度限制的历史值属于非法数据，在修复前会阻止切换。

历史已发布 Version 以 `online` 状态进入标准模型。新的管理操作使用
[AI 资源生命周期规范](ai-resource-lifecycle-spec.md)中的 `draft`、`reviewing`、
`reviewed`、`online` 和 `offline` 状态。MCP Registry 内容中的 `active`、
`deprecated` 等状态仍属于 Version 内容事实，不能替代 AI Resource 生命周期状态。

运行时查询要求 Resource 已启用且 Version 为 online。省略 Version 时解析服务端管理的
`latest` label。管理查询可以读取所有生命周期状态。

### 2.3 Latest 选择

首个 online Version 自动成为 `latest`。标准 publish、force-publish 和 online 操作会把
`latest` 移动到目标 Version。旧 direct-online 更新使用历史 `latest=false` 参数时，
可以保留当前仍有效的 label。

删除当前 latest 或将其 offline 时，按以下顺序选择替代者：

1. 按 SemVer 优先级选择最大的合法 SemVer；
2. 没有 SemVer 时，按数字 `N` 选择最大的 `vN`；或
3. 两者都不存在时，按稳定且区分大小写的序数比较选择最大字符串。

没有 online Version 时移除 `latest`。非 SemVer Version 可以精确查询，但绝不参与
Version Range。

## 3. Version 内容与 Storage

### 3.1 原有物理坐标

迁移保留现有 MCP Config group 和 data id：

| 内容 | Config group | Data id |
| --- | --- | --- |
| 历史 Version manifest | `mcp-server-versions` | `<mcpId>-mcp-versions.json` |
| Version Server | `mcp-server` | `<mcpId>-<version>-mcp-server.json` |
| Version Tools | `mcp-tools` | `<mcpId>-<version>-mcp-tools.json` |
| Version Resources | `mcp-resources` | `<mcpId>-<version>-mcp-resources.json` |

Manifest 降为兼容 metadata，三份 Version 内容仍保留在当前坐标。

### 3.2 Storage Descriptor

`AiResourceVersion.storage` 保存一份 Schema Version 1 descriptor：

```json
{
  "provider": "nacos_config",
  "keyFormat": "mcp-config-v1",
  "serverKey": "public:mcp-server:<mcpId>-<version>-mcp-server.json",
  "toolKey": "public:mcp-tools:<mcpId>-<version>-mcp-tools.json",
  "resourceKey": "public:mcp-resources:<mcpId>-<version>-mcp-resources.json",
  "schemaVersion": 1
}
```

`serverKey` 必填；对应内容不存在时省略 `toolKey` 或 `resourceKey`。内置 provider
只解析前两个 `:` 分隔符，并把剩余部分整体作为 Config data id。它只接受上述三个
MCP 自有 group；该契约不得变成访问任意用户 Config 的
`namespace:group:dataId` 旁路。

三个 key 使用同一个已持久化 provider。首轮迁移只支持 `nacos_config`；其他 provider
下 MCP 多对象内容格式需要后续独立设计。对应 Schema 为
[`mcp-version-storage.schema.json`](../../schemas/ai/mcp/internal/v1/mcp-version-storage.schema.json)。

### 3.3 字节保持与写入顺序

迁移只创建指向现有 Config 对象的 descriptor，不调用 storage `save`，也不复制内容。
Tools 和 Resources 字节保持不变，非 Direct 的历史 Server Config 也保持不变。

唯一的历史内容变更是第 4.2 节的 Direct Endpoint 物化。它使用 Config CAS 增加等价且
自包含的快照，同时保留原 `serviceRef` 和所有无关 JSON 字段。

创建或更新 Draft 时按以下顺序写入：

1. 完整校验 Server、可选 Tools、可选 Resources 和 Endpoint kind；
2. 保存 Tools 和 Resources；
3. 保存引用这些内容的 Server 对象；Direct 同时保存完整快照；
4. 使用 storage descriptor 创建或更新 Version row；
5. CAS 更新 Resource `versionInfo`，再调度 Search 和兼容投影。

Draft 重试覆盖确定性 key。Version row 建立前失败可能留下可重试 orphan 内容。
标准 API 绝不覆盖 reviewing、reviewed、online 或 offline 内容。

删除前先加载每个完整 storage descriptor，并尝试删除所有引用内容。任一内容删除失败都保留
Resource 和 Version row 以供重试。Direct Naming 投影是派生状态而非 Version storage：
它的物理清理失败不会复活或回滚已成功的标准业务删除，但必须创建带 owner 校验的耐久清理重试。

## 4. Endpoint 模型

### 4.1 公开形态与内部 Kind

公开 `McpEndpointSpec.type` 保持 `DIRECT` 或 `REF`。MCP 内部解析三种 Kind：

| 内部 Kind | 含义 | 所有者 | 删除 Version 时 |
| --- | --- | --- | --- |
| `DIRECT` | 已知地址属于该 Version。 | MCP Version | 只删除 owner/hash 匹配的投影。 |
| `SERVICE_REF` | Version 引用普通已有 Naming Service，包括 HTTP-to-MCP 场景。 | Naming 用户 | 绝不更新或删除。 |
| `RUNTIME_REF` | MCP Client API 发布临时运行实例。 | Naming Client/connection | 由显式注销、过期或断连清理，不随 Version 删除。 |

新 Server 内容持久化明确的 `endpointKind`。对缺少该字段的历史内容，兼容读取器按以下顺序解析：

1. 被引用 Service 带 `__nacos.ai.mcp.service__=true` 时为 `DIRECT`；
2. 没有该标记的 `mcp-endpoints / mcpName::version` 为 `RUNTIME_REF`；
3. 其他所有 `REF` 为 `SERVICE_REF`。

任何推断都不得删除或修改 Service。Service 缺失、Direct 标记缺失或事实冲突时阻断迁移，
不能猜测。

### 4.2 Direct Endpoint 事实

Direct 地址保存在原 Version Server Config 中：

```json
{
  "endpointKind": "DIRECT",
  "directEndpoints": [
    {
      "address": "10.0.0.8",
      "port": 8080,
      "transportProtocol": "sse"
    }
  ],
  "remoteServerConfig": {
    "serviceRef": {
      "namespaceId": "public",
      "groupName": "mcp-endpoints",
      "serviceName": "demo::1.0.0",
      "transportProtocol": "sse"
    }
  }
}
```

`directEndpoints` 是完整 Version 快照。条目按
`address + port + transportProtocol` 去重，并依次按 address 序数、port 数值和
transport 序数排序。标准 Query 和 Subscribe 投影直接读取该快照，不查询持久 Naming Service。

对历史 Direct 内容，同步必须：

1. 校验 `mcp-endpoints / mcpName::version` Service 及其 Direct 标记；
2. 读取所有持久 instance，将每个 address/port 与 `serviceRef.transportProtocol` 组合；
3. 使用 CAS 增加 `endpointKind` 和确定性快照，不修改旧 `serviceRef` 或其他字段；
4. 重建或修复 Direct 兼容投影 metadata；
5. 重读并校验 Config 与 Naming 等价后，该 Version 才能通过迁移终验。

旧 Jackson 服务端会忽略两个未知字段，仍可通过 `serviceRef` 读取。这支持对 online 兼容视图
进行应急整集群降级读取；不承诺混合版本滚动降级，也不承诺旧实现写入后再次升级仍能无损。
旧实现写回时可能序列化掉新增字段。

### 4.3 Direct Naming 兼容投影

首个标准版本仍保留按 Version 创建的持久 Naming Service，其 Service metadata 为：

| Key | 值 |
| --- | --- |
| `__nacos.ai.mcp.service__` | `true` |
| `__nacos.ai.mcp.id__` | 兼容 `mcpId` |
| `__nacos.ai.mcp.version__` | 精确 MCP Version |
| `__nacos.ai.mcp.endpointSnapshotHash__` | `sha256:<64 位小写十六进制>` |

摘要是排序、去重后的 `directEndpoints` storage projection 经 Nacos 通用 JSON serializer
序列化后 UTF-8 字节的 SHA-256。Serializer 只能按顺序输出 `address`、`port` 和
`transportProtocol`。Fixture 必须冻结实际输出字节。

更新或删除投影时必须同时满足 Direct 标记、`mcpId`、Version 和预期快照摘要匹配，
防止延迟重试误删同名重建 Service。

投影兼容状态独立于读路由状态：

| 兼容状态 | Direct 事实 | 持久 Naming 行为 |
| --- | --- | --- |
| `SYNCING` | 正在物化，Naming 可能仍是历史事实。 | 禁止删除。 |
| `CANONICAL_COMPAT` | Server Config 是标准事实。 | 维护 online 视图的降级投影，切换时不批量删除。 |
| `PROJECTION_RETIRED` | Server Config 仍是标准事实。 | 未来经显式门禁后可停止创建并清理投影。 |

首轮实现只进入 `CANONICAL_COMPAT`。创建 Draft 不建立投影；publish、force-publish、
online 和旧 direct-online 写入必须保证投影存在。Offline 从旧 Manifest 投影移除该 Version，
但保留持久 Direct Service 以便再次 online。业务删除立即移除 Manifest 条目并调度投影清理。

`PROJECTION_RETIRED` 需要后续 Spec 和实现：明确结束旧存储降级支持，对全部服务端执行清理版本
门禁，退役旧 Manifest 投影，校验所有 Direct 快照，并且只处理带完整 owner/hash metadata
的对象。仅切换到 `CANONICAL` 绝不构成删除存活 Direct Service 的授权。

### 4.4 普通 Service Ref

`SERVICE_REF` 保存用户提供的 `namespaceId`、`groupName`、`serviceName` 和
`transportProtocol`。MCP 只读取该 Service，绝不创建、覆盖或删除其 Service/instance。
MCP Client Endpoint register/deregister 对该 Kind 返回不支持。普通服务继续通过 Naming API 注册。

### 4.5 Runtime Ref 与 Naming 布局

目标 Runtime 布局为：

```text
group       = mcp-endpoints
serviceName = mcpName
cluster     = DEFAULT
instance    = ephemeral
```

Version、protocol 和 transport 都不参与 Service 或 Cluster 身份。Runtime instance metadata
固定使用以下保留 key：

```text
__nacos.mcp.endpoint.supportedTransports__
__nacos.mcp.endpoint.version__
__nacos.mcp.endpoint.versionRange__
```

`supportedTransports__` 是一个标准逗号分隔值：`sse`、`streamable-http` 或
`sse,streamable-http`。值必须小写、去重、无空格和空 token，并使用该固定顺序。
缺失表示 transport 不受限的兼容语义。

Version Binding 只有一组，不保存 `versionBindings__` 数组：

| `version__` | `versionRange__` | 语义 |
| --- | --- | --- |
| 不存在 | 不存在 | 兼容所有当前及未来 Version。 |
| 任意精确字符串 | 不存在 | 精确匹配，包括历史非 SemVer Version。 |
| SemVer | 标准 Range | 运行时位于该 Version 并适配该 Range；Range 必须包含它。 |
| 不存在 | 存在 | 非法。 |
| 非 SemVer | 存在 | 非法。 |

Range 语法和比较复用 Agent/RAD 标准 Range parser。只有新 Version key 缺失时才读取
历史 `_mcp_server_version` metadata，并将其视为精确 Binding。两者都缺失时为全 Version。

对目标 Version `V` 和 transport `T`，Runtime 查询只保留 enabled、transport 不受限或包含
`T`、且精确/Range Binding 接受 `V` 的 instance。结果保留健康状态，服务端不做负载均衡。

不会仅为修改旧的 `mcpName::version` Service Ref 而重写历史 Version 内容。兼容期同时读取
新的无 Version Service 和该历史引用 Service，优先使用新 Service 贡献，并按 IP+port 去重。
旧临时 instance 在 Client 断连后自然过期；SDK 重连 redo 写入新布局。

MCP 不提供底层 Naming Service 直接订阅。`subscribeMcpServer` 继续轮询完整 MCP 查询投影。

## 5. 标准生命周期与兼容 Facade

### 5.1 标准管理生命周期

MCP 使用通用 Draft、Submit、Review、Publish、Force Publish、Redraft、Online、Offline、
Label 和 Delete 规则。通过标准 API 发布后的内容不可变；修改时创建新 Version，或把 reviewed
Version redraft。

批准的 Admin 前缀为 `/v3/admin/ai/mcp`。Console 在 `/v3/console/ai/mcp` 下镜像相同
相对操作。精确路由见 [V3 HTTP API 范围](../http-api/v3-api-surface.md)。

### 5.2 历史 Direct-Online 映射

现有 Admin、Console、Maintainer SDK、Java Client SDK 和 gRPC 形态保持 wire-compatible，
并按以下规则适配：

| 历史操作 | 标准行为 |
| --- | --- |
| 创建/发布 MCP | 创建 Resource 和 Version，立即 online 并设置 `latest`。 |
| 更新为新 Version | 创建 online Version；仅在历史参数要求时移动 `latest`。 |
| 更新已有精确 Version | 仅兼容的同 Version 覆盖；保持原状态，并应用历史 latest 参数。 |
| 删除精确 Version | 删除其 storage 和 owner/hash 匹配的 Direct 投影。 |
| 删除 MCP | 对全部 Version 和兼容投影执行通用 Resource 删除。 |
| 运行时查询 | 只返回 enable + online；省略 Version 时解析 `latest`。 |
| 历史 `allVersions` | 只投影 online Version。 |
| Subscribe | 继续轮询完整结果，不直接订阅 Naming。 |

同 Version 覆盖是只在历史更新 Facade 中提供且需要审计的例外，标准生命周期服务不得复用该放宽。
Draft、reviewing、reviewed 和 offline Version 通过新的管理 Version API 查询，不能在旧 DTO 中
伪装成已发布条目。

Client HTTP 与 gRPC 对齐、transport 选择以及心跳复用均延期到管理迁移完成后再设计。
现有 Java SDK 公开接口在可以表达旧契约时应保持不变。

## 6. 历史同步与自动切换

### 6.1 路由状态、完成标记与租约

不提供运维控制的 `nacos.ai.mcp.storage.mode`。集群只有一个持久、单向的路由状态：

| 状态 | 读写路由 |
| --- | --- |
| `SYNCING` | 历史 MCP 事实仍是权威源，后台对账标准 row。 |
| `CANONICAL` | 所有 MCP 表面统一使用 Resource/Version 事实，不回退读取历史数据。 |

完成标记是内部 Config：

```text
group  = nacos_internal
dataId = nacos.ai.mcp.resource.migration.v1
content = {"schemaVersion":1,"state":"CANONICAL","completedAt":<epochMillis>}
```

该标记永久保留，任务结束时绝不删除。独立可续租 lease 使用 group `nacos_internal` 和
data id `nacos.ai.mcp.resource.migration.lock.v1`；租约过期后其他节点可继续对账。
丢失租约会终止当前写入者，但绝不删除 MCP 内容。

### 6.2 异步对账

根 `ApplicationReadyEvent` 之后，后台任务定期执行：

1. 完成标记已存在时立即结束；
2. 获取并续期集群 lease；
3. 分页扫描全部 Namespace 和权威 `mcp-server-versions` Config group，不只信任进程内索引；
4. 校验每个 Manifest 以及全部被引用的 Server、Tools 和 Resources 对象；
5. 首先物化并校验历史 Direct 快照；
6. 将全部 Version row 幂等 upsert 为 `online`，descriptor 直接指向现有内容；
7. 最后 upsert Resource row，写入历史 enabled/latest 事实和 `from=legacy-mcp`；
8. 清理历史来源已删除的 `legacy-mcp` row，但不删除独立创建的标准资源；
9. 执行零差异终验；
10. 只有数据条件和全节点能力门禁都通过后才发布完成标记。

没有历史数据也是新集群的合法零差异结果。非法 Manifest、内容缺失、冲突、Direct 不一致、
待处理删除或租约丢失都会让集群保持 `SYNCING` 并重试，绝不静默跳过。

### 6.3 `SYNCING` 期间的写入

`SYNCING` 期间所有 Query、List、Subscribe 和 Search 都完整使用历史路径，绝不暴露部分标准 row。
能力满足的新节点执行历史写入成功后，会调用同一个单 Resource reconciler。周期全量扫描继续修复
旧节点产生的写入。

Resource row 最后建立，因此 Version-first 中途失败不会通过历史路由暴露。对账必须幂等：
遇到已有 row 时比较等价性，不得覆盖不同 MCP 身份或 content pointer。

滚动升级期间保持 `SYNCING`，直到每个已知成员都报告支持 MCP 标准能力的最低版本，并且之后至少
一轮全量扫描没有 create、update、delete、pending 或 failed。成员版本缺失或非法时门禁失败。
完成标记发布后，低于最低版本的服务端不得加入并处理 MCP 流量。

### 6.4 完成条件

只有同时满足以下条件才允许切换：

- 每个历史 Manifest 恰好存在一个等价 MCP Resource；
- 每个历史 Version 都有等价 Version row 和正确 descriptor；
- `mcpId`、enabled、latest label、online count 和 Version 集合一致；
- Direct 快照完整，保留投影等价，且 owner/hash metadata 完整；
- 不存在非法、缺失、冲突、pending 或已删除但未对账的事实；
- 最终对账轮为零差异；
- 全部集群成员支持标准读写和 `SYNCING` 写后同步钩子。

完成后，标准 row 缺失是完整性错误，不能从旧 Manifest 复活数据。旧 Manifest 可以从 online
标准事实重建为降级投影，但投影失败不改变持久路由状态。重启不会从 `CANONICAL` 回退，
不支持自动回滚。

迁移诊断至少暴露 state、total、scanned、created、updated、deleted、pending、failed 和
最后成功时间；非法事实还需要 namespace/mcpId/version 上下文。

## 7. Tool Schema、Search、Import 与适配器规则

MCP Tool 的 `outputSchema` 是 JSON Schema。Nacos 必须保留合法的类型联合，包括
`{"type":["string","null"]}` 这类可空字段。Console 加载/保存和 OpenAPI 导入不得把它收窄为
单个字符串类型。

MCP 通过同一个共享索引和 Query Planner 参与通用 AI Resource Search 与 MCP 专用 Search Facade。
标准 Search 身份是 `mcpName`，不是 `mcpId`。Search 可以投影公开的 Server 描述、Tools、
Resources、tag、protocol 和 capability；credential、运行时 instance 和敏感 auth metadata
绝不进入检索 chunk。`SYNCING` 期间 handler 可以读取兼容存储，但必须产生相同投影。

外部导入使用[AI 资源导入插件规范](../plugin/ai-resource-import-plugin-spec.md)。插件只生成 artifact，
不得直接写 MCP storage。MCP Resource Operator 通过当前 MCP Facade 应用 artifact，切换后由该
Facade 路由到标准生命周期。

Console 专用 `GET /v3/console/ai/mcp/importToolsFromMcp` 辅助接口保持现有出站网络策略：
运维可以关闭它；私网/本地目标要求命中运维白名单；endpoint 不得覆盖 `baseUrl` origin；
禁止重定向。

## 8. 必需验证

后续实现 PR 至少覆盖：

- Config group/data-id 坐标不变，Tools/Resources 字节不变；
- Direct 是唯一 Server Config 扩展，旧模型可忽略未知字段并读取；
- Draft 到 Publish 生命周期、latest 选择、旧覆盖隔离和 storage 删除重试；
- `SYNCING` 历史可见性、幂等异步对账、混合节点门禁、零差异切换、重启和无回退；
- 多 instance Direct 物化、切换时保留投影、owner/hash 清理隔离和清理重试；
- 普通 Service Ref 非所有权边界；
- 无 Version Runtime publication、transport/Version Binding 校验、新旧 Service 合并、
  断连、重连和 redo；
- 默认 JSON 与 Jackson 3 Client adapter；
- Admin、Console、Maintainer SDK、Java SDK、Search、Import 和 Registry Adaptor 等价投影。

异步断言只使用对公开行为的有界轮询，不得依赖固定 sleep 或内部任务顺序。

## 9. 演进与延期工作

首轮迁移不定义非 Config AI Storage provider 下的 MCP 多对象格式、多个不连续 Runtime Range、
强制删除所有 connection 所拥有的 Runtime publication，也不退役 Direct 投影。

Client HTTP API 对齐和复用 Agent HTTP Publisher 心跳/续约属于已记录的后续工作。
它们要在标准管理迁移完成后独立设计；本文刻意不冻结其 path、payload、transport 协商或心跳周期。

上游 MCP Tool、Resource、Transport、Auth 和 Registry 格式仍可能演进。这些变化必须保持标准身份
和所有权边界，或发布明确的新 Schema 与迁移版本。
