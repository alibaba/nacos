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

# A2A Agent Binding 与兼容规范

| 项目 | 值 |
| --- | --- |
| 状态 | 实验性 Binding 与升级兼容契约 |
| 生效条件 | `nacos.ai.a2a.compatibility.mode`，默认 `CANONICAL` |

本文定义 A2A 作为 Nacos 标准 Agent 资源的一种协议 Binding，并规定历史 AgentCard API
的兼容 facade。标准模型由 [Agent 管理规范](agent-management-spec.md)定义；远程发现遵循
[RAD 协议规范](rad-protocol-spec.md)。

## 1. 生效、当前基线与身份

旧 A2A 接口通过 `nacos.ai.a2a.compatibility.mode` 选择一套完整的定义实现：

| 模式 | 兼容实现 |
| --- | --- |
| `CANONICAL` | 标准 Agent Metadata、Version 存储与 RAD Runtime Endpoint。它仍是默认静态模式，不扫描历史数据。 |
| `LEGACY` | 历史 AgentCard Config group 与按精确 Version 划分的 Naming Endpoint。旧实现保持不变。 |
| `AUTO` | 运行一次性历史升级状态机。`SYNCING` 和 `QUIESCING` 期间历史 Config 定义仍是权威；只有永久、零差异的 `CANONICAL` Marker 才切换完整定义 Facade。 |

模式 token 大小写不敏感。一次请求必须完整路由到同一分支，不进行按操作混用、回退、
定义合并读取或定义双写。`AUTO` 由
[历史 A2A 升级迁移规范](a2a-upgrade-migration-spec.md)规定：后台对账历史定义，使用明确
Member Ability 和短暂定义写屏障，绝不只按成员版本切流。迁移期间的 Runtime 双物化是连接态
兼容投影，不是第二个定义权威。

持久化的终态迁移 Marker 优先于本地模式配置。有能力节点一旦观察到终态，本进程永久把 A2A
定义操作路由到 `CANONICAL`；Marker 被删除或配置变化时也不得恢复 Legacy-only 写入。非终态
Marker 不覆盖显式选择的静态模式。

第 2～7 节对路由到 `CANONICAL` 的请求生效；路由到 `LEGACY` 的请求完整保留历史 Config
定义和按 Version 划分的 Naming Endpoint 行为。`AUTO` 同步期间，定义读写保持完整历史行为；
终态 Marker 后，完整 Facade 使用与
`CANONICAL` 相同的分支。唯一例外是 `QUIESCING`：定义 Mutation 以可重试
`AGENT_MIGRATION_IN_PROGRESS` Detail Error 暂时拒绝，读取和 Runtime 操作继续。

A2A 不是顶层 AI 资源类型。标准身份为：

```text
namespaceId -> agent -> agentName -> version -> protocol=a2a
```

历史 `namespaceId -> a2a -> agentName` 身份仅用于兼容。所有旧请求都适配到
`type=agent`；启用标准写路径后，不得继续创建新的 `a2a` 元数据或版本存储。

## 2. A2A CallInterface

A2A Binding 是一个 `AgentCallInterface`：

| Agent 字段 | A2A 映射 |
| --- | --- |
| `protocol` | 标准 token `a2a`。 |
| `protocolVersion` | 用于快速过滤的规范化 A2A 协议版本。 |
| `descriptorMediaType` | AgentCard JSON 媒体类型。 |
| `nativeDescriptor` | 完整规范化 AgentCard，不丢失已支持的上游字段。 |
| `declaredEndpoints` | 从 root URL 和 supported/additional interfaces 派生。 |
| `endpointSourceOrder` | 从兼容 registration type 派生。 |

当前 descriptor 基线支持 A2A 1.0 字段和现有 0.x 兼容字段。Adapter 规范化时不得
用拼装出的通用 Agent 对象替换保存的 native descriptor。

common latest 精确 Version 中的 A2A CallInterface 只有通过该版本基线的完整 AgentCard 校验时，
才声明 ARD 表示 `application/a2a-agent-card+json`。Artifact 直接返回保存的 native descriptor，
不得把多协议 Nacos Agent 外层对象伪装成 AgentCard。旧 online Version 支持 A2A 只影响 RAD
`protocolsAny=a2a`；如果 common latest 不含合法 AgentCard，则不产生当前 A2A ARD 表示。

`registrationType=URL` 映射为 `[DECLARED,RUNTIME]`，
`registrationType=SERVICE` 映射为 `[RUNTIME,DECLARED]`。Registration type 是旧投影
字段，不参与 Agent 身份，也不进入新 API。

## 3. 旧定义写入

旧 AgentCard release 和 Admin update 使用与标准 Agent API 相同的 AgentName 与 Version
校验。写入成功时创建或复用 Agent 元数据行，保存一个 A2A CallInterface，并将目标 Version
直接置为 online，不增加独立的旧 draft Pipeline。

固定规则：

- 首个 online Version 总是成为 `latest`；
- 新增后续 Version 时，`setAsLatest=true` 移动 `latest`，`false` 保留当前有效指针；
- 标准 Agent publish 或 online 操作总是移动 `latest`；
- 删除或下线当前 latest 时，选择剩余 online Agent Version 中最大的一个；没有剩余版本时删除 `latest`；
- Client SDK 重复 release 已包含 A2A CallInterface 且 online 的精确 Version 时成功 no-op，
  不比较或覆盖内容，也不移动 latest；
- Admin 更新已存在精确 Version，或 Client release 命中不包含 A2A CallInterface 的精确 Version 时，
  canonical 内容不同返回冲突；0.1.0 不提供同版本强制覆盖；
- 只有历史 API 已承诺幂等删除时，删除不存在的 Agent 或 Version 才成功 no-op。

直接上线、冲突拒绝、删除和 latest 变化必须写审计日志，但不得记录完整 descriptor 或敏感 Endpoint metadata。

## 4. 旧 Runtime Endpoint 写入

`CANONICAL` 分支把旧单条、批量和注销请求适配到标准 RAD Runtime Naming layout：

```text
group=agent-endpoints
serviceName=rad-<encodedAgentId>-a2a
runtimeVersion=<exactVersion>
versionRange=[<exactVersion>]
```

旧 SDK 的 redo 和替换身份是 `(connection, namespaceId, agentName, exactVersion)`，而标准
Runtime Service 对一个 Naming publisher 只保存一份完整批次。兼容层因此为每个旧精确 Version
创建一个确定性的内部子 publisher，并把它绑定到原始 AI gRPC connection。单条注册把该子
publication 替换为一个 Endpoint；批量注册以提交的完整 Batch 覆盖同一子 publication；旧
deregister 注销该精确 Version 的完整子 publication。不同 Version 的子 publisher 写入同一个
标准 Service，但不会互相覆盖。原始 connection 断开时，其全部子 publisher 一并释放，并继续
复用 Naming 的 ClientData Distro、索引、事件和清理能力；兼容层不得读取旧 publication 后合并。

转换后的每个 Naming Instance 使用标准 singular `runtimeVersion`/`versionRange` metadata；旧
`protocolVersion` 和 `tenant` 仅作为 A2A 反向投影用的保留 metadata，不进入公开 RAD Endpoint
或 Runtime revision。旧 `AgentEndpoint` 的 URI、transport、健康和权重继续按标准 Runtime
映射校验。

`LEGACY` 分支保持原 Handler 和 `<legacyEncodedAgentName>::<exactVersion>` Naming Service
实现不变。显式 `CANONICAL` 分支只写标准 Service。

`AUTO` 在这两个不变物理实现之上增加临时迁移 Router。`SYNCING` 和 `QUIESCING` 使用历史
Service 主写、标准 Service 必需镜像；终态切流后标准 RAD 为主，固化的迁移策略可以选择继续保留
历史精确 Version Naming Shadow。一个逻辑 Publication 只校验和计数一次；两个物理 Child
Publisher 都绑定原始 Connection 并幂等清理。Mirror 或 Shadow 失败不回滚成功主写，而是进入
有界 Connection 内重试。精确顺序、切流门禁、Shadow 范围和回滚边界遵循
[历史 A2A 升级迁移规范](a2a-upgrade-migration-spec.md)。

Endpoint 可以先于 Agent 或 Version 定义发布，但不得隐式创建 Agent 定义。

旧 Java SDK 为每个 `(agentName, exactVersion)` 独立保存 Endpoint redo，且保存调用时 Payload
的防御性快照；不同 Version 的发布意图不得因重连缓存 key 冲突而丢失。内部子 publisher 是
服务端实现细节，不进入公开 Payload、Redo key、鉴权资源或管理查询。

## 5. 旧查询投影

兼容查询先选择一个包含合法 `protocol=a2a` CallInterface 的 online Version。显式 Version
执行大小写敏感精确查询；未指定时使用 Agent `latest`。Client 运行时读取还要求 Agent enabled 且可见。

投影规则：

| 查询模式 | 结果 |
| --- | --- |
| `URL` | 返回保存的 native AgentCard 及其声明 interfaces。 |
| `SERVICE` 且存在匹配 Runtime Endpoint | 将确定性 Runtime Endpoint 集合投影到 AgentCard interfaces 和 root URL。 |
| `SERVICE` 且无匹配 Runtime Endpoint | 回退到保存的声明 AgentCard。 |

`CANONICAL` 查询从 `rad-<encodedAgentId>-a2a` 读取并按目标精确 Version 的 binding 过滤；
`LEGACY` 查询继续读取旧按 Version 划分的 Service。Runtime 投影排除 `enabled=false`，保留
`healthy=false`，因为旧 DTO 没有健康字段。投影先按
priority、再按 Endpoint 自然键稳定排序。source revision、health、priority、weight 和通用 metadata
等 RAD 新字段不进入旧 DTO。

为保持线上协议兼容，完整的 Runtime Endpoint 投影集合必须同时通过
`supportedInterfaces` 和历史字段 `additionalInterfaces` 返回。root URL 与首选传输从同一集合中
选择一个成员，被选中的成员不得从 `additionalInterfaces` 中移除。

旧 list/version-list 从 Agent 元数据和 online A2A Version 投影。旧订阅事件必须经过与 GET
相同的投影。初始目标不存在时，旧订阅可以继续保留；这是兼容行为，不属于 RAD Watch 契约。
exact Version 与 latest 订阅使用独立身份。Version 当前是否为 latest 不能决定事件只投递给哪一个
身份；latest 指针切换到已有 exact Cache 时也必须触发 latest 订阅。取消后重新订阅必须恢复轮询，
SDK shutdown 必须停止所有旧 AgentCard 轮询任务。

## 6. 兼容表面

| 表面 | 状态与窗口 |
| --- | --- |
| Java `A2aService` 和旧 A2A gRPC Payload | 仅兼容；当前不设删除版本。 |
| Admin `/v3/admin/ai/a2a` 和 `A2aMaintainerService` | 兼容到 4.0.x 窗口。 |
| Console `/v3/console/ai/a2a` | 兼容到 3.4.x 窗口。 |

兼容窗口内，旧路径、Payload type、DTO、能力位、鉴权身份和响应包装保持稳定。新 Agent/RAD API
不得暴露 `registrationType`、`setAsLatest` 或 AgentCard 专属列表包装。

历史 3.0～3.2 数据对账、混合 Member 运行、安全切流、可选历史 Naming Shadow、回滚边界和
延期清理由[历史 A2A 升级迁移规范](a2a-upgrade-migration-spec.md)定义。迁移专用实现代码及配置
计划在 Nacos 4.0 删除；完成迁移后的标准 Agent/RAD 事实和仍处于兼容期的公开 A2A Facade
不依赖这些临时代码。

## 7. 演进

上游 AgentCard 字段或 A2A 协议版本变化由 A2A Adapter 和版本化 Agent CallInterface 处理，
不得重新定义标准 Agent 身份或协议无关的 RAD 结果。ARD 使用的 AgentCard media type 与固定
上游 Schema 基线由 [AI Registry 适配器规范](ai-registry-adaptor-spec.md)共同版本化；变化时必须
同步更新 adapter fixture、校验器、规范和一致性测试。
