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

本文定义 Nacos AI Registry 中 MCP Server 资源的契约。首期迁移把 MCP 管理身份和
Version 治理接入通用 AI Resource 生命周期，同时保留现有 MCP Serving 与发现平面。

## 1. 范围与契约状态

首期迁移包含两种管理路由状态：

| 状态 | 管理路由 | Client 与网关 Serving 路由 |
| --- | --- | --- |
| `SYNCING` | 后台对账 Resource/Version row 期间，历史 MCP 管理路径仍是权威。 | 现有 Manifest、Config 和 Naming 行为不变。 |
| `LIFECYCLE_MANAGED` | 完整兼容管理操作集的读写统一使用通用 AI Resource 生命周期。 | 现有 Manifest、Config 和 Naming 行为仍然不变。 |

`LIFECYCLE_MANAGED` 不是数据面切换。它不会把历史 Manifest、Config 对象、
Direct Service、普通 Service Ref 或 Client 自有 Runtime Service 降级为可随时删除的投影。

每个请求只针对完整操作契约解析一次管理路由。节点绝不能把读操作路由到生命周期 Row、同时把写操作
路由到历史实现，也不能暴露其他混合事实源组合。

以下改动明确不属于首期迁移：

- 增加内部 `McpEndpointKind` 或
  `DIRECT/SERVICE_REF/RUNTIME_REF` 持久化模型；
- 把 Direct Endpoint 地址物化到 Version Server Config；
- 使用无 Version Service 替换当前按 Version 划分的 Runtime Service；
- 增加 `supportedTransports`、`versionRange` 或 MCP Runtime Range Binding；
- 退役 Direct 持久 Naming Service 或历史 Manifest；
- 修改 frontend/backend、订阅、重连、redo 或心跳行为。

这些改动必须经过独立兼容设计和消费者迁移窗口。

## 2. 事实所有权

首期迁移采用以下所有权边界：

| 事实 | 所有者 | 契约 |
| --- | --- | --- |
| MCP 管理身份 | `ai_resource` | `namespaceId + type=mcp + mcpName`。 |
| Enable 状态、owner、scope、label 和工作 Version 指针 | `ai_resource` | 通用 AI Resource metadata 与生命周期事实。 |
| Version 状态、author、Pipeline 状态和内容指针 | `ai_resource_version` | 通用 AI Resource Version 事实。 |
| Server、Tools 和 Resources payload | 现有 MCP Config 对象 | 保持坐标和字节不变。 |
| Published Version 集合与历史 latest 视图 | `mcp-server-versions` Manifest | 必须持续维护的兼容 Serving Index。 |
| Direct Endpoint 地址 | 现有持久 Naming Service 和 Instance | 当前 Direct Endpoint 事实，不是降级投影。 |
| 普通 REF backend | `serviceRef` 选择的用户自有 Naming Service | MCP 读取但不拥有被引用 Service。 |
| Frontend/backend 映射 | 现有 Server Config 和 Endpoint 查询逻辑 | `frontEndpointConfigList` 行为不变。 |
| Client Runtime Endpoint | 现有 Client 自有 Naming 状态 | ServiceName、Cluster、metadata、redo 和活性不变。 |
| Search 身份与索引维护 | `mcpName` 和共享异步索引服务 | Search 最终一致，绝不能作为身份来源。 |

AI Resource 托管 MCP 管理生命周期，不替换当前 MCP Serving 或发现数据面。

## 3. 身份与 AI Resource 映射

### 3.1 标准身份

Nacos 的标准管理身份为：

```text
namespaceId + type=mcp + name=mcpName
```

`mcpName` 区分大小写，并且作为身份字段不可修改。MCP 线协议没有定义公开的
MCP Server UUID；MCP 官方 Registry 使用受 Registry 范围约束的名称和 Version
作为公开坐标。因此 Nacos 使用自身 Namespace 限定 `mcpName`，不得把 Runtime
`serverInfo.name` 当作全局唯一或安全敏感身份。

该结论基于当前上游契约：

- [MCP 协议 Schema](https://github.com/modelcontextprotocol/modelcontextprotocol/blob/main/schema/2026-07-28/schema.ts)
  暴露 Implementation Name 和 Version，但没有 MCP Server UUID；
- [MCP Tools 规范](https://github.com/modelcontextprotocol/modelcontextprotocol/blob/main/docs/specification/2026-07-28/server/tools.mdx)
  明确 Server 自报 Name 不保证在多个 Server 之间唯一；
- [官方 Registry API](https://github.com/modelcontextprotocol/registry/blob/main/docs/reference/api/official-registry-api.md)
  和[当前 API 类型](https://github.com/modelcontextprotocol/registry/blob/main/pkg/api/v0/types.go)
  暴露 Name 与 Version 坐标；Registry
  [Migration 009](https://github.com/modelcontextprotocol/registry/blob/main/internal/database/migrations/009_separate_official_metadata.sql)
  删除了早期 UUID 字段，改用 Server Name 与 Version 自然键。

历史 UUID 形态的 `mcpId` 保留为内部物理存储别名和已废弃兼容字段。它不参与标准身份、
鉴权、Visibility、label、Search 文档身份或 Runtime Naming 身份。

Schema Version 1 的 Resource 扩展为：

```json
{
  "schemaVersion": 1,
  "mcpId": "4d7939c0-72ea-4ef4-b232-418d1e16b45c"
}
```

对应的机器可读契约为
[`mcp-resource-ext.schema.json`](../../schemas/ai/mcp/internal/v1/mcp-resource-ext.schema.json)。

### 3.2 Resource 映射

Resource row 按以下规则映射 MCP 字段：

| `AiResource` 字段 | MCP 映射 |
| --- | --- |
| `namespaceId`、`type`、`name` | Namespace、固定值 `mcp` 和 `mcpName`。 |
| `desc` | MCP 描述。 |
| `status` | 历史 `enabled=true` 映射为 `enable`，否则为 `disable`。 |
| `owner` | 创建或导入操作人；历史对账使用 `nacos`。 |
| `scope` | 新资源使用 Visibility 默认值；历史对账使用 `PUBLIC`。 |
| `bizTags` | MCP 公开业务 Tag，无值时为空集合。 |
| `ext` | 包含内部 `mcpId` 别名的 `McpResourceExt`。 |
| `from` | 本地创建、导入来源或 `legacy-mcp` 对账来源。 |
| `versionInfo` | 通用 editing、reviewing、online count 和 label 摘要。 |

一个 Namespace 内，同一 `mcpName` 只能有一个有效的 `type=mcp` Resource。由于
当前物理唯一性包含 `from`，对账必须检测同名多来源 row 并阻止完成，不能静默选择一条。

### 3.3 Version 映射

每个 MCP Version 对应一条 `AiResourceVersion` row，精确身份为：

```text
namespaceId + type=mcp + mcpName + version
```

历史已发布 Version 以 `online` 状态进入生命周期。新的管理 API 使用通用
`draft`、`reviewing`、`reviewed`、`online` 和 `offline` 状态。
Version 字符串保持不变；在共享 Version 字段长度范围内，历史非 SemVer 值继续作为有效的精确身份。
首期迁移不引入 MCP Version Range。

Runtime 查询仍然只暴露 Enable Resource 和 Online Version。省略 Version 时解析服务端管理的
`latest` label；管理读取可以检查所有生命周期状态。

Latest 选择遵循通用生命周期，并包含以下 MCP 兼容细化：

- 标准 publish、force-publish 和 online 操作把 `latest` 移动到目标 Version；
- 历史 direct-online 更新可以按现有 latest 参数保留当前仍然有效的指针；
- 删除当前 latest 或将其 offline 时，依次选择最大的剩余 Online SemVer、最大的数字
  `vN`、最大的稳定且区分大小写字符串。没有 Online Version 时移除 `latest`。

## 4. 物理内容与 Storage 边界

### 4.1 保持不变的坐标

迁移保留以下 Config group 和 data id：

| 内容 | Config group | Data id |
| --- | --- | --- |
| Published-Version Manifest | `mcp-server-versions` | `<mcpId>-mcp-versions.json` |
| Version Server | `mcp-server` | `<mcpId>-<version>-mcp-server.json` |
| Version Tools | `mcp-tools` | `<mcpId>-<version>-mcp-tools.json` |
| Version Resources | `mcp-resources` | `<mcpId>-<version>-mcp-resources.json` |

历史对账只创建指针，不得复制、移动、重写或扩展 Server、Tools、Resources 的 payload 字节，
也不得修改任何 Naming Service 或 Instance。

Manifest 继续作为直接读取 Config/Naming 的 Client 和网关使用的兼容 Serving Index。
它不是标准管理身份或生命周期存储。

### 4.2 Version Storage Descriptor

`AiResourceVersion.storage` 保存 Schema Version 1 Descriptor：

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

`serverKey` 必填；对应内容不存在时省略 `toolKey` 或 `resourceKey`。内置 Provider
只拆分前两个 `:` 分隔符，并把剩余部分整体作为 Config data id。它只接受上述三个
MCP 自有 group，不得变成访问任意用户 Config 的 `namespace:group:dataId` 旁路。

所有 key 使用 Version row 中持久化的 Provider。首期迁移支持 `nacos_config`；
其他 AI Storage Provider 下的 MCP 多对象格式需要独立设计。机器可读契约为
[`mcp-version-storage.schema.json`](../../schemas/ai/mcp/internal/v1/mcp-version-storage.schema.json)。

### 4.3 强制分层

迁移可以复用历史模型转换、JSON 处理、Manifest 选主、Endpoint 和 Naming 逻辑，但物理
Config 访问必须下沉到以下边界：

```text
MCP lifecycle/application service
        -> MCP Version Storage / MCP Serving Manifest Storage
        -> AI Resource Storage router 或 Config implementation
```

规范规则：

- MCP Version Storage 根据 Version row 中的 Descriptor 加载、保存和删除 Server、
  可选 Tools 以及可选 Resources。
- MCP Serving Manifest Storage 封装 `mcp-server-versions` 的读取、发布和删除。
  Manifest 是 Serving 兼容索引，不是身份解析器。
- MCP lifecycle 和 operation service 不得直接对四个 MCP Config group 执行 Config CRUD。
- Service 不得接收 `mcpId`、拼接 data id 并绕过持久化 Version Descriptor。
- Direct、REF 和 Client Runtime Naming 状态不进入通用 `AiResourceStorage` SPI；
  MCP 专用所有权清理参与通用生命周期删除流程。

保留 Config 和 Naming 指的是保留物理兼容性，而不是保留 Service 直接访问 Config 的错误分层。

## 5. Endpoint 与 Serving 兼容

公开 Endpoint 模型和当前解析算法保持不变：

1. `frontEndpointConfigList` 决定向调用者返回哪种 frontend endpoint。
2. Direct 固定地址继续由当前按 Version 划分的持久 Naming Service 和 Instance 表示；
   Server Config 保留当前 `serviceRef`。
3. REF 继续读取 `serviceRef` 选择的普通 Naming Service；Nacos MCP 不拥有该 Service
   或其中的 Instance。
4. `BACKEND` frontend 条目继续直接使用解析出的 backend endpoint。
5. 网关代理场景中，网关作为 frontend，`remoteServerConfig.serviceRef` 继续选择真实 backend。
6. Client API Endpoint 注册继续使用当前按 Version 划分的 Runtime Service、Cluster 和
   Instance metadata。
7. `subscribeMcpServer` 继续轮询完整 MCP Query 投影，不直接订阅底层 Naming Service。

Direct 持久 Service 是当前 MCP 数据和外部消费者契约，不是降级投影。Offline 会从
Serving Manifest 中移除 Version，但保留内容和 Direct Service，以便后续再次 Online。

已知网关集成，包括 Higress 和基于 Istio 的网关，可能不调用 MCP 专用 Query API，
而是直接读取该 Serving 平面。兼容流程为：

1. 列举 `mcp-server-versions` Config；
2. 读取并监听 `<mcpId>-mcp-versions.json`；
3. 根据 Published Version 拼接精确 Server 和 Tools DataId；
4. 读取 `remoteServerConfig.serviceRef`；
5. Query 或 Subscribe 被引用的 Naming Service；
6. 构建网关 Frontend Route，同时保留被引用 Backend。

因此生命周期托管不能要求这些消费者仅为保持当前发现能力而协商新的 Nacos Ability 或发布新版本。

## 6. 生命周期与兼容 Facade

### 6.1 标准管理生命周期

MCP 使用通用 Draft、Submit、Review、Publish、Force Publish、Redraft、Online、Offline、
Label 和 Delete 规则。通过标准生命周期 API 发布的内容不可变；修改内容需要创建新 Version
或经过允许的 Redraft 状态转换。

Admin 前缀为 `/v3/admin/ai/mcp`，Console 在 `/v3/console/ai/mcp` 下镜像相同的
相对操作。精确路由由 [V3 HTTP API 范围](../http-api/v3-api-surface.md)定义。

这些标准路由只在管理权威达到 `LIFECYCLE_MANAGED` 后启用。Embedded 和 Standalone Console
直接使用相同的 Application Service。Console-only Remote 部署必须使用 Typed Maintainer Lifecycle
Transport，且不得回退到 Legacy Write。该 Transport 把类型化 Request Object 映射到同一组
Form/Query Admin Route，不引入第二套 JSON Body HTTP 契约。

`McpMaintainerService` 提供显式 Namespace Lifecycle 方法和默认 Namespace 便利重载。
Draft 内容使用 `McpLifecycleDraftRequest`，精确 Version 状态转换使用
`McpLifecycleVersionCommand`，Label 替换使用 `McpLifecycleLabelsUpdateRequest`。
这些模型不新增顶层 `namespaceId` 或 `mcpId` 选择器；Namespace 作为独立方法参数，标准
Resource 身份只使用 `mcpName`。复用 `McpServerBasicInfo` Payload 内的历史 `id` 或
`namespaceId` 字段只属于兼容内容：服务端不使用它们解析身份，而是写入 Lifecycle Target
解析出的内部坐标。

Submit 使用资源类型 `MCP` 构造 `ResourceFilesPipelineContext`，包含保持原样的 Server、
可选 Tools 和可选 Resources Payload。没有 Enable 且支持 MCP 的 Pipeline Node 时，Submit
遵循通用 Direct-publish 路径；否则 Version 进入 `reviewing`，Approved 或 Rejected 回调都将其
转换为 `reviewed`，只有后续显式且已批准的 Publish 才更新 Online Lifecycle State 和兼容
Manifest。Force-publish 继续作为需要审计的 Pipeline Bypass。

### 6.2 历史 Direct-Online Facade

现有 Admin、Console、Maintainer SDK、Java Client SDK 和 gRPC wire shape 保持兼容，
并映射到生命周期 Application Service：

| 历史操作 | 托管后行为 |
| --- | --- |
| 创建或发布 MCP | 创建 Resource 和 Version，按历史契约立即 Online、设置 latest，并返回历史响应形态。 |
| 使用新 Version 更新 | 创建 Online Version，并应用历史 latest 参数。 |
| 更新已存在的精确 Version | 仅兼容的同 Version 覆盖，通过 MCP Storage 执行；保持生命周期状态和历史 latest 行为。 |
| 查询 | 返回与迁移前相同的 Serving 投影和响应形态。 |
| 删除精确 Version | 停止 Manifest 暴露，通过托管删除流程清理 MCP 自有 Direct 状态和 Version 内容，再删除 Version row。 |
| 删除 MCP | 停止 Manifest 暴露，通过带 MCP Storage 清理的通用 Resource-with-Versions 流程删除，再移除 metadata row。 |

同 Version 覆盖是需要审计的兼容例外，标准生命周期 API 绝不能复用该放宽。

兼容覆盖在 `isPublish=false` 时必须保留当前 Version Lifecycle Status、Manifest Presentation、
Latest Pointer 和已有 Release Metadata。只有后续显式 Publish 后，被覆盖的 Version 内容才成为
已发布 Presentation。

兼容 Direct-online Create 可以临时使用 Resource `editingVersion` Pointer 作为进行中的重试标记；
只有 Manifest 重读验证成功后才清除该 Pointer。已经完成或被有意 Offline 的 Resource 不含此标记，
重复 Create 仍返回冲突。

### 6.3 Draft 与 Publish 顺序

Draft 按以下顺序写入：

1. 解析或生成内部 `mcpId`；
2. 通过 MCP Version Storage 保存 Server 和可选 Tools/Resources；
3. 使用同一个 Descriptor 创建或更新 `draft` Version row；
4. 更新 Resource 工作指针。

Draft 不加入历史 Manifest。

删除精确 Draft 时，先通过 MCP Storage 清理内容，清理成功后删除 Version Row，最后清除匹配的
Resource Working Pointer。Storage 或 Row 删除中断时，保留的 Pointer 是重试锚点；如果 Row 已经
删除，重试可以在不再依赖已删除 Content Descriptor 的情况下清除 Pointer。

Publish 或 Online 按以下顺序执行：

1. 通过 MCP Version Storage 加载并校验 Version 内容；
2. 校验现有 Direct 或 REF Endpoint 事实，不改写它们；
3. 转换 Version 状态并更新服务端管理的 Label；
4. 根据完整 Online Version 集合重建兼容 Manifest；
5. 最后通过 MCP Serving Manifest Storage 发布 Manifest；
6. 重读并验证 Serving View 后才返回成功。

Online 生命周期 Row 是耐久期望状态。Manifest 发布或校验失败时，操作返回失败并保留该 Row；幂等重试
或托管 Reconciler 根据它重建缺失的 Serving 投影。Search 索引只在业务变更后异步调度，不参与
Publish 成功判定。

### 6.4 Offline 与 Delete

Offline 先把 Version 收敛为耐久的 `offline` 生命周期状态，再重建并验证不包含该 Version 的
Manifest Serving View。它不隐式 Disable Resource，并保留 Server/Tools/Resources 内容和 Direct
持久 Service。Manifest 收敛失败时操作返回失败，而保留的 Offline Row 为重试和对账提供唯一明确的目标。

Version 删除流程：

1. 加载并保留该 Version 的 Storage Descriptor；
2. 把 Version 收敛为 `offline`、修复 Label，并移除和验证它的 Manifest 暴露；
3. 调用 MCP 专用清理 Hook，清理该 Version 拥有的 Direct 状态；
4. 通过 MCP Version Storage 删除 Server/Tools/Resources；
5. 只有全部物理清理成功后才删除 Version Row；
6. 异步调度 Search 维护。

完整 Resource 删除流程：

1. 通过名称或已废弃兼容 ID 解析并鉴权标准 Resource，并加载全部 Version Descriptor；
2. Disable Resource 并把其 Version 收敛为 `offline`，让生命周期 Row 耐久表达非 Serving 目标；
3. 删除并验证 Serving Manifest，让网关停止发现；
4. 使用 MCP Storage Deleter 调用通用 Resource-with-Versions 删除流程；
5. 对每个 Version，Deleter 校验 Descriptor、清理 MCP 自有 Direct 状态，并通过
   Storage 删除 Resources、Tools 和 Server 内容；
6. 只有全部 Callback 成功后才删除 Resource 和 Version Row。

任一 Manifest、Endpoint 或内容清理失败都返回失败，并保留重试所需的 Disabled/Offline Resource、
Version Row 和 Storage Descriptor。这些生命周期状态本身也是耐久恢复意图，因此不需要额外的 MCP
操作日志或 Manifest Tombstone。ID-only 重试仍通过 `AiResource.ext` 解析。普通 REF Service 和
Client 自有 Runtime Instance 保持现有所有权，不随 MCP Version 删除。

## 7. 已废弃 `mcpId` 兼容

### 7.1 保留用途

`mcpId` 仍然用于：

- 拼接现有 Config data id；
- 让 Version 和 Manifest Storage 定位历史 Config；
- 保持现有 Admin、Console、Maintainer、Client model、event 和响应形态；
- 保持直接读取 Config/Naming 的消费者兼容。

它不得成为新 API、Search 文档、鉴权规则、Visibility 规则、Label 或生命周期操作的身份。

### 7.2 管理身份解析

新的生命周期 API 接受 `namespaceId + mcpName (+ version)`，不增加 `mcpId` 参数。
已经接受 ID-only 输入的 Admin、Console 和 Maintainer HTTP 路径继续兼容：

- name-only 按 Namespace、`type=mcp` 和 name 精确查询 `AiResource`；
- name 加 ID 先按 name 精确查询，再校验 `ext.mcpId` 一致；
- ID-only 分页读取当前 Namespace 的 `type=mcp` Resource row，解析
  `ext.mcpId` 并要求唯一命中；
- Alias 缺失、非法、重复或冲突时返回受控参数错误或完整性错误。

协议 Filter 先按现有 Wire 契约完成请求身份认证。对于 ID-only 输入，Lifecycle Locator 随后解析
Canonical Resource，并在读取任何内容或执行变更前，针对该标准名称再次执行精确的 Identity 与
Authority 校验；之后与 Name 输入执行相同的 Visibility 和生命周期操作。这个顺序既避免未认证的
Alias 枚举，也防止空 Wire Name 绕过标准名称鉴权。ID 查询不得使用 Search Index、Manifest、
Config 或历史 MCP 内存 Index。不为这个低频已废弃路径新增表、字段或 JSON Index。
`SYNCING` 期间历史 Index 可以继续服务完整的历史管理路径；
`LIFECYCLE_MANAGED` 后任何管理正确性路径都不再依赖它。

现有 Create/Release 响应和 DTO 继续返回 ID 字段；现有仅兼容的自定义 UUID 输入能力不扩展。
移除 `mcpId` 必须等待物理 Config 坐标和直读消费者完成后续迁移；标记废弃不代表首期可以删除。

### 7.3 gRPC 字段区分

三个 wire 字段具有不同兼容状态：

1. 当前 MCP Request 顶层继承的 `AbstractMcpRequest.mcpId` 继续保持 Ignored 和
   Deprecated；Handler 不增加 ID 查询，并保持现有 name 必填规则；
2. 嵌套 `McpServerBasicInfo.id` 在当前请求实际使用的地方继续作为 Active
   Compatibility Input 或模型字段，name 与 ID 必须一致；
3. `ReleaseMcpServerResponse.mcpId` 继续作为 Active Compatibility Output。

字段号和 wire shape 保持不变。后续独立的 SDK Proto 变更可以为 Dormant 顶层字段增加
Deprecated Option，但生命周期迁移不依赖该版本发布。

## 8. 历史对账与托管切换

### 8.1 Marker 与 Lease

不存在由运维选择的 Storage Mode。单向管理完成 Marker 是内部 Config 对象：

```text
group  = nacos_internal
dataId = nacos.ai.mcp.resource.migration.v1
content = {"schemaVersion":1,"state":"LIFECYCLE_MANAGED","completedAt":<epochMillis>}
```

该永久 Marker 表示管理 row 已完整托管，不授权删除或修改 Serving Config/Naming 数据。
可续约集群 Lease 使用 `nacos.ai.mcp.resource.reconciliation.lease.v1`。系统仍在同步时，
任务可以在 `nacos.ai.mcp.resource.reconciliation.progress.v1` 持久化
`state=SYNCING` 的非权威诊断信息。两者都不是完成 Marker；失去 Lease 只停止当前 Writer，
不删除 MCP 内容。

### 8.2 对账流程

根 `ApplicationReadyEvent` 后，后台任务：

1. 获取并续约集群 Lease；
2. 分页遍历所有 Namespace，通过 Manifest Storage 扫描 `mcp-server-versions`，
   不能只信任 MCP 内存 Index；
3. 通过 Version Storage 校验 Server、可选 Tools 和可选 Resources；
4. 为每个历史 Version 幂等 Upsert `online` row，Descriptor 指向现有内容；
5. 最后 Upsert Resource，写入 name、内部 ID、Enable 状态、latest、online count 和
   `from=legacy-mcp`；
6. 按标准 `mcpName` 调度共享异步 Search 对账；
7. 检测内容缺失、身份冲突、多来源重复 row、非法 Version 和 pending delete；
8. 通过通用生命周期 Delete/Recovery 流程处理已删除的 `legacy-mcp` row，
   但不删除独立创建的资源；
9. 完成一轮零差异校验；
10. 只有所有已知集群成员都支持托管写入和写后对账 Hook 时才写完成 Marker。

Version/Resource Upsert 阶段只创建指针，绝不保存或重写历史 payload，也不修改 Naming。
在所有 Member 都具备标准名称 Search Projector 和通用生命周期 Delete/Recovery Handler 前，
`SYNCING` Reconciler 把 Search Backfill、额外 Version 和孤儿 `legacy-mcp` 工作记录为阻断性
诊断；不得调度以 ID 为 Key 的 Search Task，也不得直接删除 Resource/Version Row、Payload
Config 或 Naming 状态。该部分同步状态绝不能写入完成 Marker。在基于名称的 Projector
落地前，即使生命周期 Row 已达到零差异，进度记录也保持 `searchBackfillPending=true` 和
`managedCutoverReady=false`。

### 8.3 `SYNCING` 期间写入

`SYNCING` 期间，历史管理响应保持完整的历史视图；不把部分 Resource row 暴露为管理权威。
新节点通过 MCP Storage 执行当前物理兼容写入，随后调用相同的单 Resource Reconciler；
周期扫描补齐旧节点写入。托管切换前不开放新的 Lifecycle Write API。混合版本集群保持
`SYNCING`。

该状态下 Lifecycle 对账是次级收敛步骤。失败会进入诊断并由周期扫描修复，但不能重新解释或回滚
已经成功的权威历史写入。

兼容 Facade 在该状态下把完整读写操作契约路由到历史实现。只有全部托管操作及其恢复路径都可用后，
永久 Marker 才能把完整契约切换到生命周期实现；不能独立切换单个方法。

切换要求：

- 每个历史 Manifest 恰好存在一个等价 Resource；
- 每个历史 Version 都有等价 Version row 和正确 Descriptor；
- name、内部 ID、Enable 状态、latest、online count 和 Version 集合一致；
- 没有多来源重复 row、内容缺失、身份冲突或 pending delete；
- 最后一轮扫描零差异；
- 所有集群成员支持 MCP Storage、Lifecycle Facade、写后对账和标准名称 Search Task；
- 托管后的 MCP Service 路径没有绕过 Storage 的 Config CRUD。

外部网关读取的 Serving 契约没有变化，因此不参与该能力门禁。

Marker 永久保留，不自动回滚。Marker 存在后，缺少托管写入能力的 Nacos Member 不得处理
MCP 管理流量，因为未接入 Hook 的历史写入可能导致 Lifecycle Row 分叉。该限制不会给外部
Config/Naming 消费者增加新的协商要求。

## 9. Search、Import 与 Adaptor 规则

MCP 通过同一个 Index 和 Query Planner 参与通用 AI Resource Search 与 MCP 专用
Search Facade。标准 Search `resourceName` 是 `mcpName`，绝不能是 `mcpId`。

MCP Search Projector 与管理流量使用同一个完整兼容操作 Router。`SYNCING` 期间，它按标准名称
通过 MCP Storage 投影完整历史视图，避免部分完成对账的 Resource Row 隐藏 MCP Server；进入
`LIFECYCLE_MANAGED` 后，同一个 Router 根据持久化 Storage Descriptor 加载可见 Resource、
Online Version 和内容。Projector 输入和 Search Identity 绝不使用 `mcpId`；`SYNCING` 策略仍可
解析读取未变更 Manifest/Config 坐标所需的内部兼容 Alias。它可以投影公开 Description、Tools、
Resources、Tag、Protocol 和 Capability；Credential、Runtime Instance 和敏感 Auth Metadata 不进入
Search Chunk。

每次成功的 Create、Update、Publish、Online、Offline、Delete、Enable/Disable、Label 或 Import
变更，都按 `namespaceId + type=mcp + mcpName` 调度耐久异步维护任务。任务可以合并连续更新并重试失败；
业务请求不等待索引完成。最终一致的 Search 状态绝不能用于身份解析、鉴权、Visibility 或写入正确性。

历史 Backfill 重建以名称为 Key 的文档；Projection Version 对账和 Orphan Sweep 删除历史
ID-Keyed 文档及任务，系统不得长期保留两个标准 Search 身份。

外部导入使用
[AI Resource Import Plugin 规范](../plugin/ai-resource-import-plugin-spec.md)。
Plugin 只生成 Artifact，绝不直接写 MCP Storage。MCP Resource Operator 通过 Lifecycle
Application Service 和 MCP Storage 应用 Artifact，同时保持现有 Manifest、Config 和 Naming
Serving 输出。

Console 专用 `GET /v3/console/ai/mcp/importToolsFromMcp` Helper 保持现有出站网络策略：
运维可以禁用；私有或本地目标需要运维 Allowlist；Endpoint 不能覆盖 `baseUrl` Origin；
禁止 Redirect。

可选 AI Registry Adaptor 保持外部响应形态。此次管理迁移不要求 Adaptor 消费者协商新版本。

## 10. API 与 SDK 边界

首期迁移修改管理实现，并在后续阶段增加标准管理生命周期操作：

- Admin 和 Console 历史方法保持请求、响应、错误和 Direct-Online 兼容语义，同时进入同一个
  Lifecycle Service。
- Maintainer SDK 二进制签名和历史 Overload 保持兼容；可以增加与标准 Admin 语义一致的
  Name/Version Typed Lifecycle 方法。
- Import 收敛到 Lifecycle Service 和 MCP Storage。
- Console UI 只在对应 API 可用后切换到 Lifecycle View。

首期迁移不修改：

- Java Client `AiService` 现有 MCP Public Interface；
- Query、Release 或 Endpoint gRPC wire layout 和 field number；
- Client Endpoint Register/Deregister、Subscription、Reconnect、Redo 或 Heartbeat；
- 当前 Runtime ServiceName、Cluster 或 Metadata；
- MCP Client HTTP API；
- AI Registry Adaptor 响应形态。

Client HTTP 与 gRPC 对齐以及复用 Agent HTTP Publisher 心跳/续约是后续独立工作。

## 11. Tool Schema 兼容

MCP Tool `outputSchema` 是 JSON Schema。Nacos 必须保留合法 Type Union，包括
`{"type":["string","null"]}` 形态的 Nullable Property。Console Load/Save 和
OpenAPI Import 不得把它收窄为单个字符串类型。

## 12. 必须验证的场景

Implementation PR 至少覆盖：

- 精确 Resource/Version 映射，包括历史非 SemVer Version 字符串；
- 从 Resource row 执行 name-only、name+ID 和 legacy ID-only 解析，协议身份认证后针对 ID-only
  标准名称执行精确二次鉴权，并处理身份冲突；
- Manifest/Server/Tools/Resources 坐标和字节保持不变；
- 对账不修改 Naming，Direct、REF、frontend/backend、Runtime、订阅、重连和 redo 行为保持不变；
- 所有 Server/Tools/Resources 和 Manifest Config 访问经过 MCP Storage，不存在 Service
  直接 Config CRUD；
- Draft 到 Publish 生命周期、历史同 Version 覆盖隔离、Latest 选择和 Manifest-Last 发布恢复；
- Offline 保留内容和 Direct Service；
- Version 与完整 Resource 删除、物理清理失败时通用 Row 保留、Manifest 删除后按 Deprecated ID
  重试，以及不删除普通 REF 或 Client Runtime 状态；
- 异步对账幂等、Lease 接管、混合节点门禁、零差异完成、重启和
  `LIFECYCLE_MANAGED` 持久化；
- 标准名称异步 Search、失败重试、Backfill 和历史 ID-Keyed Orphan 清理；
- Admin、Console、Maintainer、Client、Import、Search 和 Adaptor 兼容投影等价；
- 现有 MCP Java Client 行为涉及的默认 JSON 和 Jackson 3 Adapter。

异步断言使用有界轮询公开行为，不依赖固定 Sleep、内部任务顺序，也不依赖最终一致 Search
保证身份正确性。

## 13. 延后演进项

以下内容需要后续独立设计：

- MCP Client HTTP Query、Release、Endpoint、Subscription 和 Heartbeat/Renewal 对齐；
- Endpoint Kind 持久化和 Direct Endpoint 物化；
- 历史 Manifest 或 Direct Service 的退役与版本协商；
- 无 Version Runtime Publication、多 Transport Metadata 和 SemVer Range Binding；
- 非 Config MCP 多对象 Storage；
- 移除已废弃的物理 `mcpId` 别名。

上游 MCP Tool、Resource、Transport、Auth 和 Registry 格式可能继续演进。相关变更必须
保持 Nacos 身份和所有权边界，或发布明确的 Schema 与迁移修订。
