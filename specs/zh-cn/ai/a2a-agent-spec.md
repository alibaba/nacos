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
| 状态 | 实验性目标兼容契约 |
| 生效条件 | `nacos.ai.a2a.compatibility.mode`，默认 `CANONICAL` |

本文定义 A2A 作为 Nacos 标准 Agent 资源的一种协议 Binding，并规定历史 AgentCard API
的兼容 facade。标准模型由 [Agent 管理规范](agent-management-spec.md)定义；远程发现遵循
[RAD 协议规范](rad-protocol-spec.md)。

## 1. 生效、当前基线与身份

旧 A2A 接口通过 `nacos.ai.a2a.compatibility.mode` 选择一套完整的定义实现：

| 模式 | 定义事实源 |
| --- | --- |
| `CANONICAL` | 标准 Agent metadata 与 Version 存储。当前版本不支持该功能的滚动升级，因此默认使用此模式。 |
| `LEGACY` | 历史 AgentCard metadata 与精确 Version 内容对应的 Config group。 |
| `AUTO` | 从 `LEGACY` 启动；全部已知集群成员都上报 3.3.0 或更高版本后，仅单向切换一次到 `CANONICAL`。成员版本缺失或非法时继续使用旧分支。 |

模式 token 大小写不敏感。一次请求必须完整路由到同一分支，不进行按操作混用、回退、
双读或双写。`AUTO` 只预留保守的未来切流入口，不构成滚动升级保证；单向切换也不会迁移
历史 Config 数据。在独立迁移契约落地前，选择 `LEGACY` 或后续从 `LEGACY` 切到
`CANONICAL` 的可见性后果由运维方承担。

第 2～7 节对路由到 `CANONICAL` 的请求生效；路由到 `LEGACY` 的请求保留历史 Config
定义行为。三种模式下，旧 Runtime Endpoint 操作都继续使用按 Version 划分的 Naming 布局。

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

首个兼容阶段中，旧单条和批量 Endpoint 操作继续使用现有的按 Version 划分 Naming layout：

```text
publisher + namespaceId + agentName + exactVersion + protocol=a2a

group=agent-endpoints
serviceName=<legacyEncodedAgentName>::<exactVersion>
```

单条注册委托 Naming 现有的单实例操作，将该 publication 替换为一个 Endpoint；批量注册委托
Naming Batch 注册，以提交的完整 Batch 覆盖旧 Batch。旧 deregister 删除该精确 Version
Service 对应的完整 publication。

兼容 Handler 不把这些写入重定向到新的无 Version RAD Runtime Service，不写入
`runtimeVersion` 或 `versionRange` metadata，也不进入新的 RAD Runtime 写入路径。旧 A2A
Client 无法提交该 Service 要求的完整跨 Version Publisher Batch；直接重定向会导致一个
Version 注册覆盖另一个 Version。历史 Service 的迁移、双读或双写、切流、回滚和清理必须由
独立的滚动升级契约定义。

Endpoint 可以先于 Agent 或 Version 定义发布，但不得隐式创建 Agent 定义。

旧 Java SDK 为每个 `(agentName, exactVersion)` 独立保存 Endpoint redo，且保存调用时 Payload
的防御性快照；不同 Version 的发布意图不得因重连缓存 key 冲突而丢失。

## 5. 旧查询投影

兼容查询先选择一个包含合法 `protocol=a2a` CallInterface 的 online Version。显式 Version
执行大小写敏感精确查询；未指定时使用 Agent `latest`。Client 运行时读取还要求 Agent enabled 且可见。

投影规则：

| 查询模式 | 结果 |
| --- | --- |
| `URL` | 返回保存的 native AgentCard 及其声明 interfaces。 |
| `SERVICE` 且存在匹配 Runtime Endpoint | 将确定性 Runtime Endpoint 集合投影到 AgentCard interfaces 和 root URL。 |
| `SERVICE` 且无匹配 Runtime Endpoint | 回退到保存的声明 AgentCard。 |

Runtime 投影排除 `enabled=false`，保留 `healthy=false`，因为旧 DTO 没有健康字段。投影先按
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

历史数据迁移、混合版本双读双写、回滚和清理仍属于滚动升级设计，不由本 API 兼容规范定义。
上述模式开关只选择实现，不提供这些能力。

## 7. 演进

上游 AgentCard 字段或 A2A 协议版本变化由 A2A Adapter 和版本化 Agent CallInterface 处理，
不得重新定义标准 Agent 身份或协议无关的 RAD 结果。
