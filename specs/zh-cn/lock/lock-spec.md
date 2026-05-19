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

# 分布式锁规范

本文定义 Nacos 分布式锁领域。分布式锁是 Nacos 3.0 引入的实验性能力。当前实现和功能
范围都比较小，后续版本可能根据社区反馈引入不兼容变更，调整资源模型，收紧安全语义，甚至在
社区不再需要 Nacos 提供该原语时移除整个模块。

## 1. 范围

分布式锁为客户端提供一个简单的分布式互斥原语，用于通过 Nacos 集群协调短时间临界区。该
领域由服务端 `lock` 模块和 Java 客户端 `LockService` 承担。

分布式锁负责：

- 锁身份、锁类型、加锁、解锁和租约超时语义；
- 通过 [CP 一致性规范](../design/foundation-cp-consistency-spec.md)复制服务端锁状态；
- 通过 [SDK 规范](../sdk/sdk-spec.md)和
  [Java SDK 实现规范](../sdk/sdk-java-impl-spec.md)提供可选 Java SDK 访问；
- 通过[可观测钩子规范](../design/foundation-observability-hooks-spec.md)记录锁请求指标。

分布式锁不负责：

- 配置中心、注册中心、AI Registry、Namespace 或插件资源生命周期；
- 业务事务、数据库事务、任务调度或工作流编排；
- fencing token、锁持有者 token、锁续约、锁查询、等待队列、公平性或可重入语义；
- 用于大范围锁列表、迁移或手动状态修改的 HTTP 管理 API。

## 2. 实验状态

在社区明确将分布式锁提升为稳定能力之前，`lock` 模块必须被视为实验性功能。
实验能力的兼容预期遵循[兼容与废弃策略规范](../design/compatibility-deprecation-spec.md)。

当前尚不承诺以下兼容性：

- 除现有客户端和服务端互通以外的稳定 wire payload；
- `lockType + key` 之外的稳定锁资源身份；
- 覆盖所有锁操作的稳定鉴权行为；
- 稳定的锁扩展 SPI 行为；
- 多语言 SDK 一致性。

需要强生产级锁语义的应用，应在依赖该模块前根据自身故障模型验证当前行为。

## 3. 资源模型

当前锁资源身份为：

```text
lockType -> key
```

| 概念 | 含义 |
| --- | --- |
| `lockType` | 锁实现类型，内置类型为 `NACOS_LOCK`。 |
| `key` | `lockType` 范围内由用户定义的锁名称。 |
| `params` | 可选的可序列化扩展参数，内置互斥锁不解析该字段。 |
| `expiredTime` | 期望租约时长，单位毫秒。尽管当前字段名如此，服务端按时长而非绝对时间戳解释。 |

当前模型在 Nacos 集群内全局生效，不包含 `namespaceId`、`groupName`、资源 owner 或租户
身份。后续如果补充这些维度，属于资源模型变更，可能不兼容。

## 4. 锁语义

内置锁类型是简单互斥锁：

- 锁为空时，加锁成功；
- 已有锁过期时，加锁成功；
- 已有锁未过期且被占用时，加锁失败；
- 解锁尝试将锁从占用状态切换为空状态；
- 空锁或过期锁可以从内存锁表中清理；
- 加锁和解锁结果均为 boolean 成功值。

内置实现当前不校验解锁请求是否来自锁持有者。能够发送同一 `lockType + key` 解锁请求的
客户端可以释放该锁。这属于实验状态的一部分，不应被视为最终安全契约。

## 5. 租约与过期

服务端使用服务端时间计算真实过期时间：

```text
endTime = serverCurrentTimeMillis + leaseDurationMillis
```

规则：

- 请求的租约时长为负数时，服务端使用 `nacos.lock.default_expire_time`；
- 当前默认租约时长为 `30000` 毫秒；
- 服务端使用 `nacos.lock.max_expire_time` 限制请求的最大租约时长；
- 当前最大租约时长为 `1800000` 毫秒；
- 过期检查是惰性的，在加锁、解锁、清理或快照相关路径访问锁状态时触发。

由于过期时间使用服务端时间，客户端不应假定本地时钟决定锁有效窗口。

## 6. 一致性与恢复

分布式锁是 CP 能力。锁状态变更通过 lock 模块使用的 CP 协议组提交。集群必须优先保证正确性：
当 CP 路径无法提交写入时，加锁或解锁应失败，而不能产生分裂的锁持有状态。

当前实现：

- 为加锁和解锁注册 CP request processor；
- 将锁操作请求序列化为 CP write request；
- 在服务端 lock manager 中保存活跃锁状态；
- 通过 CP snapshot 机制保存和加载锁状态；
- 使用 `nacos_lock.zip` 作为 snapshot archive 名称。

锁状态由进程内存加 CP 日志和 snapshot 共同承载。它不是关系型数据库资源，不像 Config 或
Naming 领域数据那样受[持久化与 Dump 规范](../design/foundation-persistence-dump-spec.md)
约束。

## 7. 客户端与传输边界

分布式锁通过运行时 SDK 暴露给客户端，而不是作为大范围管理 API 暴露。Java 客户端使用
[gRPC API 规范](../grpc-api/api-spec.md)定义的 gRPC 请求路径。客户端必须在发送锁操作前检查
服务端是否支持 `SERVER_DISTRIBUTED_LOCK` ability，并遵循
[客户端能力协商规范](../client/client-ability-negotiation-spec.md)。

公开 SDK 边界为：

- 创建 `LockService`；
- 创建 lock instance，Java 客户端通常通过 `NLockFactory` 创建；
- 加锁；
- 解锁；
- 关闭客户端资源。

SDK 不应将 CP group 名称、snapshot 文件、lock manager map 或底层 request processor 等
服务端内部实现暴露为稳定用户契约。

## 8. 扩展边界

服务端提供以 `lockType` 为键的 `LockFactory` SPI。内置实现注册 `NACOS_LOCK`，并创建互斥锁。

扩展规则：

- 锁类型使用 `params` 前，必须先定义该字段语义；
- 除非后续版本引入独立的类型化契约，否则锁类型必须保持加锁和解锁的 boolean 契约；
- 锁类型必须在 CP 写入顺序下保持安全；
- 锁类型不能重新定义 Config、Naming、AI 或 Core 资源归属；
- 锁扩展行为仍然是实验性的，可能随 lock 模块一起变化。

## 9. 安全与可见性

加锁和解锁是对锁资源的写操作，应按照 `SignType.LOCK` 和写动作语义进行鉴权。Java 客户端通过
与其他运行时客户端相同的 security proxy 模式传递安全 header。

当前实现状态：

- lock gRPC handler 中仍包含 `TODO Support auth` 标记；
- 默认鉴权实现中存在历史的 `grpc/lock` 操作点；
- 锁请求尚未具备完整的 owner token 校验或解锁持有者校验。

在安全契约补齐之前，部署侧应将分布式锁视为可信客户端场景下的实验能力。

## 10. 可观测性

lock 模块应暴露低基数的操作次数、成功次数和 handler 延迟指标。当前实现记录：

- 加锁请求总数；
- 加锁成功请求数；
- 解锁请求总数；
- 解锁成功请求数；
- lock handler 延迟。

指标标签不得包含原始锁 key、params、凭据或用户负载。

## 11. 待处理问题

- 判断分布式锁是否应继续作为 Nacos core 能力存在，迁移为扩展模块，或被移除。
- 定义稳定的解锁所有权语义，包括 owner token、fencing token、连接绑定或其他社区认可机制。
- 判断锁身份是否必须包含 `namespaceId`、租户或资源 owner。
- 补齐 lock gRPC 操作鉴权，并与 `SignType.LOCK` 对齐。
- 判断续约、查询、watch、公平性或可重入语义是否属于 Nacos 范围。
- 仅在服务端语义稳定后，再定义多语言 SDK 契约。
- 重新评估 `expiredTime` 等字段命名，该字段当前实际表示租约时长。
