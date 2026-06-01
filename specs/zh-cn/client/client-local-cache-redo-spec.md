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

# Nacos 客户端本地缓存与 Redo 规范

本文定义客户端侧本地缓存、本地 failover、listener recovery、subscription recovery 和
redo 行为。本文展开[客户端运行时规范](client-runtime-spec.md)中的恢复部分。

## 1. 本地数据分类

客户端运行时使用多类本地数据：

| 数据类型 | 来源 | 目的 | 权威性 |
|----------|------|------|--------|
| Config failover file | 用户维护的本地文件 | 已知 Config item 的紧急覆盖。 | 最高本地读取优先级，但不会自动写回服务端。 |
| Config snapshot | 服务端查询响应 | 用于读取 fallback 的最后已知 Config content 和 encrypted data key。 | 仅恢复缓存。 |
| Config listener state | SDK listener 注册 | 跟踪已知 group key、listener MD5 和 fuzzy watch 状态。 | 仅运行时意图。 |
| Naming service-info cache | 服务端 push 或 query response | 订阅或查询服务的最后已知实例。 | 仅恢复缓存。 |
| Naming failover data | 用户或扩展提供的本地 failover source | failover switch 开启时覆盖 discovery view。 | 仅本地 discovery override。 |
| Redo data | SDK register、subscribe 或 endpoint 操作 | reconnect 后恢复运行时意图。 | 仅运行时意图。 |

除非领域规范显式说明，本地数据不得被视为服务端已提交状态。

## 2. Config 本地恢复

Config 读取优先级为：

1. 用户维护的本地 failover 文件；
2. 服务端查询；
3. 本地 snapshot。

failover 文件不会由客户端自动创建。它用于紧急场景：Nacos server 不可用或远端变更不安全时，
应用仍需要通过本地覆盖启动或继续运行。

snapshot 在服务端查询成功后写入，并在服务端确认 Config item 不存在时删除。Encrypted data key
snapshot 与 content snapshot 分开存储。Config filter，包括 encryption filter，会在选定本地或
远端 content 后执行。

Config listener 发送 listener check 前必须检查本地 failover 文件。当 failover 文件出现、变化
或消失时，必须更新 listener state，并可按 `CacheData` MD5 规则触发 listener callback。

## 3. Config Listener 与 Fuzzy Watch 恢复

Config gRPC client 注册 Config change notification、client metrics request 和 fuzzy watch
notification handler。连接建立时，客户端必须通知 listen context 和 fuzzy watch context，使已知
订阅重新同步。连接断开时，必须标记受影响的 `CacheData` entry 和 fuzzy watch context 与服务端
不一致。

Config listener recovery 不是写操作 redo，而是读/监听运行时意图的 resync。

## 4. Naming 本地缓存

Naming service-info cache 按 grouped service name 和 clusters 存储 `ServiceInfo`。服务端 push 或
query response 会更新内存 map，并在实例视图变化时写入磁盘缓存。

该缓存是恢复辅助：

- load-cache 选项开启时，可在启动时加载；
- 网络中断时，可提供临时 discovery view；
- 不得创建、更新或删除 Naming 服务端资源。

Push-empty protection 可以忽略空或无效 push，避免把已知可用视图意外替换为空视图。

## 5. Naming Failover 视图

Naming failover 是本地 discovery override。当 failover switch 开启且某服务存在有效 failover
data 时，SDK 可以返回 failover view，而不是正常的 server-driven view。

failover switch 或 failover data 变化导致可见实例集合变化时，应发布 instance-change event。
failover 关闭后，SDK 返回正常缓存的服务端视图；如果可见视图变化，也应通知 listener。

Naming failover 不得被用作服务端数据修复机制。

## 6. Redo 模型

Redo 用于连接丢失并重新建立后恢复运行时意图。Redo data 记录：

- 期望最终状态，例如 registered 或 unregistered；
- 数据是否已经在上一个 connection 上成功注册；
- 是否正在执行 unregister；
- 重放操作所需的领域 payload。

Redo operation 包括：

- 再次 register；
- 再次 unregister；
- 移除过期 redo data；
- 当当前运行时意图已经满足时不执行操作。

Redo task 仅能在运行时连接已连接时执行。连接断开时，已注册 redo data 必须标记为未注册，使下一次
connected period 可以修复服务端挂载状态。

## 7. 领域 Redo 规则

Naming redo 覆盖：

- 临时实例注册；
- 批量临时实例注册；
- 服务订阅；
- fuzzy watch 一致性状态。

持久 Naming service 状态由服务端持有，除非领域明确把某操作视为运行时意图，否则不应由客户端
redo 恢复。

AI redo 覆盖运行时 endpoint 和 subscription intent，例如 MCP 或 agent endpoint 注册。AI resource
publish/delete 语义仍由 [AI Registry 规范](../ai/ai-registry-spec.md)约束。

Config listener 通过 listener resync 和 fuzzy watch resync 恢复。Client SDK 不会自动 redo Config
publish/delete 操作。

## 8. Shutdown

SDK shutdown 必须清理内存 redo state、停止后台 retry task、关闭 transport client，并停止本地
cache/failover refresh task。除非用户显式调用缓存清理操作，shutdown 不应删除用户维护的 failover
文件或服务端派生 snapshot。

## 9. 待处理问题

- Naming redo 当前仍使用独立实现，较新的 AI redo 使用通用 redo 抽象。后续实现应收敛到共享 redo
  模型。
- Config listener recovery、Naming redo、AI redo 和
  [运行时推送与重连规范](runtime-push-reconnect-spec.md)定义的 runtime push recovery 应共享可观测字段。
- 多语言 SDK 应说明自己支持哪些本地缓存和 redo 行为，以及哪些行为有意与 Java 不同。
