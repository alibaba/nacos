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

# Core 运维规范

本文档定义 Nacos Core Operations 领域。Core 运维负责 Config、Naming、AI Registry、
插件、[Console](../console/console-spec.md) 和 maintainer 工具共同使用的服务端控制面资源和
运维动作。

## 1. 范围

Core 运维负责：

- namespace 元数据和 namespace 生命周期；
- 集群 member 视图、member 元数据更新和 member lookup 模式控制；
- 服务端状态、liveness、readiness 和 module state 聚合；
- server loader 指标和连接重平衡操作；
- 插件清单、插件启用状态、插件配置状态，以及插件状态的集群同步；
- CP/Raft 维护命令、ID 生成器诊断、运行时日志级别调整等高风险服务端操作。

Core 运维不负责：

- namespace 内部的 Config、Naming 或 AI Registry 资源语义；
- 插件扩展契约，这些契约由[插件规范](../plugin/plugin-spec.md)和各插件规范定义；
- member 发现、内部 RPC、CP/AP 一致性、持久化、请求过滤或事件分发等底层基础协议。
  这些能力由基础规范定义，Core 运维只使用它们。

Core 运维天然属于管理能力，应通过 Admin API、Console API 或 Maintainer SDK 暴露，
不应通过运行时 Client SDK 暴露。

## 2. Namespace 操作

Namespace 是 Nacos 领域资源的顶层隔离边界。Core 运维负责 namespace metadata：

```text
namespaceId -> namespaceName, namespaceDesc, namespaceType
```

规则：

- 默认 namespace 是 global namespace，并且在逻辑上始终存在；
- 自定义 namespace 作为 tenant metadata 持久化；
- 创建 namespace 会分配或校验 `namespaceId`，存储展示元数据，并使该 namespace 可被
  领域资源使用；
- 更新 namespace 只修改 namespace 展示元数据；
- 删除 namespace 只移除 namespace metadata。除非资源所属领域明确规定，否则不得把它
  视为对 Config、Naming、AI Registry、Auth 或插件领域数据的级联删除；
- 请求过滤使用的 namespace 存在性校验是读侧保护，不得创建或修改 namespace metadata；
- namespace detail 注入可以丰富返回视图，但不得改变标准 namespace identity。

Namespace 校验、请求过滤和运行时上下文规则由
[请求过滤与运行时上下文规范](../design/foundation-request-context-spec.md)定义。

## 3. 集群成员操作

集群成员操作暴露并更新集群成员基础能力持有的 server member view。Core 运维负责该视图的
管理面，不负责 member 协议本身。

规则：

- self member 是当前服务端身份，必须来自本地 `ServerMemberManager`；
- member list 是运维视图，可以按 address 前缀或 node state 过滤；
- member update 接收合法 member 记录，将其纳入本地可用视图，重置失败计数，并忽略非法
  member 记录；
- member lookup mode 变更会改变集群成员发现方式，必须按集群控制操作处理；
- member 操作不得替代 CP group membership 变更或领域数据迁移。

详细 member 生命周期由[集群成员规范](../design/foundation-cluster-membership-spec.md)定义。

## 4. 服务端状态与健康

Server state 是运维诊断视图。它由已注册的 module state builder 聚合生成，包含适合运维人员和
控制台查看的 key-value runtime state。

规则：

- module state 是派生运行时状态，不是持久领域数据；
- module state 不得暴露密钥、完整用户载荷或高基数运行时数据；
- liveness 表示当前进程是否仍可被进程管理系统视为存活，不是领域健康断言；
- readiness 表示已注册模块是否可以接收流量。它由 module health checker 组合而成，当任一
  必需模块 not ready 时应失败；
- 健康探针端点可以有意作为基础设施探针暴露。任何公开健康面都必须保持最小信息量，并避免
  暴露敏感状态；
- 详细 server state 用于诊断、控制台和 maintainer 运维。

服务端生命周期、module state 和 readiness 基础规则由
[服务端生命周期与环境配置规范](../design/foundation-server-lifecycle-env-spec.md)和
[可观测钩子规范](../design/foundation-observability-hooks-spec.md)定义。

## 5. Server Loader 与连接重平衡

Server loader 操作管理运行时 gRPC SDK 连接。它们是连接分布的运维控制，不应被理解为
Naming、Config 或 AI resource ownership 变化。

规则：

- current-client 视图来自本地 connection manager；
- cluster loader metrics 是从 member 响应中 best-effort 聚合得到的，member 超时或失败时可能
  不完整；
- reload single connection 会要求指定客户端连接重连，可选择重定向到指定地址；
- reload by count 会保留最多指定数量的本地连接，并重定向超出的连接；
- smart reload 会比较集群连接数，把连接从过载 member 重定向到低负载 member；
- 连接重平衡不得丢弃领域数据。重连后的客户端需要通过各自领域协议重建订阅、watch 或注册；
- loader 控制属于写操作，需要管理员权限。

远程连接生命周期规则由
[远程连接生命周期规范](../design/foundation-remote-connection-spec.md)定义。

## 6. 插件状态操作

Core 运维负责已发现插件的运维状态。插件契约仍属于扩展模型，但 enable state 和可变插件配置
属于控制面状态。

规则：

- 插件通过 `PluginProvider` 发现，使用 `pluginType:pluginName` 作为身份；
- 互斥插件类型默认只启用配置指定的实现。当前 Auth 和 datasource dialect 插件是互斥类型；
- 非互斥插件默认启用，除非持久化状态覆盖；
- critical plugin 不得通过常规操作禁用；
- 只有 configurable plugin 可以接收配置更新，且配置必须满足插件声明的 config definitions；
- 集群模式下插件状态和配置变更必须通过 CP `plugin_state` group 复制，并通过 CP snapshot 恢复；
- 单机模式下插件状态和配置变更持久化到本地；
- `localOnly` 操作是应急本地节点变更。它绕过集群同步，必须被视为临时运维覆盖。

插件类型语义由[插件规范](../plugin/plugin-spec.md)定义。CP 复制边界由
[CP 一致性规范](../design/foundation-cp-consistency-spec.md)定义。

## 7. Core 维护操作

Core 维护操作覆盖影响服务端运行时或一致性基础设施的高风险控制。

规则：

- CP/Raft 维护命令是 operator-only 控制，必须限定在明确 group 和支持的 command 内；
- Raft 维护变更可能影响 leader、snapshot、peer 或 group recovery，不得暴露给运行时客户端；
- ID 生成器诊断是读侧运维状态，本身不分配领域 ID；
- 运行时日志级别更新是本地进程控制，除非另有同步规则定义；
- 维护操作应输出日志，并在存在审计机制时被审计。

CP 协议行为由 [CP 一致性规范](../design/foundation-cp-consistency-spec.md)定义。

## 8. 控制台与 Maintainer 面

Console API 和 Maintainer SDK 可以用更适合 UI 或类型化调用的方式暴露 Core 运维。它们必须
保留相同操作边界：

- 控制台可以聚合本地和远端 server state，但不得发明新的资源生命周期语义；
- announcement、UI guide 等控制台内容是 UI 展示数据，不是标准 Core 运维状态；
- Maintainer SDK 操作应与 Admin API 语义和权限要求保持一致。

## 9. 安全与兼容规则

- 修改型 Core 运维需要管理员写权限。
- 诊断型读取需要管理员读权限，除非它是有意保持最小信息量的公开健康探针。
- 公开健康探针不得暴露敏感 module state。
- 除非兼容面或健康探针明确使用其他形态，Core 运维必须遵循 HTTP API 响应和错误规则。
- 运行时 Client SDK 不得暴露宽泛 Core 运维能力。

## 10. 待处理问题

- Namespace id 校验仍有一部分在 controller 路径中实现，应迁移到共享参数校验。
- Namespace 删除应为仍在已删除 namespace 下保留数据的领域定义明确兼容行为。
- Server loader 输入需要定义更严格的连接数、重定向目标和 smart reload factor 校验。
- 公开与鉴权的 server state 暴露边界需要持续文档化，避免未来 module state 字段泄露敏感数据。
- `localOnly` 插件操作需要明确审计、可见性和恢复指引。
- Raft 维护命令应保持显式 allowlist 和操作安全说明。
