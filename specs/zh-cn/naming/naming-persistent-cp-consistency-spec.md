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

# Naming 持久服务 CP 一致性规范

本文定义 Naming 领域中持久服务的 CP 一致性规则。本文细化共享的
[CP 一致性规范](../design/foundation-cp-consistency-spec.md)和
[Naming 一致性与客户端状态规范](naming-consistency-client-spec.md)。

## 1. 范围

本文负责：

- 持久实例 write、apply、snapshot 和 recovery 语义；
- service metadata 和 instance metadata CP group 边界；
- 持久实例和 metadata 变更如何对 Naming 派生服务状态可见；
- 持久服务中未支持或待完成的操作。

本文不定义临时 Distro 同步、主动健康检查算法或客户端 SDK redo 行为。

## 2. CP Groups

Naming 为持久服务数据和 metadata 使用独立 CP group：

| Group 常量 | 责任 |
| --- | --- |
| `Constants.NAMING_PERSISTENT_SERVICE_GROUP_V2` | 持久实例发布状态。 |
| `Constants.SERVICE_METADATA` | Service 和 cluster metadata。 |
| `Constants.INSTANCE_METADATA` | Instance metadata。 |

这些 group 是独立的一致性域。新行为不得假设某个 group 的写入 commit 会与另一个 group 的写入原子
提交。

## 3. 持久实例写入

持久实例 register、update 和 deregister 操作会被序列化为 instance store request，并提交到
`Constants.NAMING_PERSISTENT_SERVICE_GROUP_V2`。

持久实例操作必须拒绝已存在且 service type 为 ephemeral 的 service。同一个 namespace 与 groupName
范围内的 service identity 不得同时是 persistent 和 ephemeral。

Apply 持久实例写入时，必须更新 persistent IP-port client state，并发布本地 Naming event，使
publisher index、service storage、metadata overlay 和 push view 可以重建。

Persistent subscription state 不支持。订阅仍属于 connection-based runtime state。

## 4. Metadata 写入

Service metadata 和 cluster metadata 通过 `Constants.SERVICE_METADATA` 写入。Instance metadata
通过 `Constants.INSTANCE_METADATA` 写入。

Service metadata change 在覆盖已有 metadata 时必须保留 service type 字段。Metadata 写入可以按需
创建或连接 service singleton，使后续 Naming view 能解析 service identity。

Instance metadata add 或 change 必须发布 service change event，使 discovery view 可以刷新。Instance
metadata delete 会删除该 instance 的运维态 metadata overlay。

最终 instance view 中，运维态 metadata 的优先级高于运行时注册 metadata，规则见
[Naming 元数据与 Selector 规范](naming-metadata-selector-spec.md)。

## 5. Snapshot 与恢复

持久实例状态必须提供 CP snapshot operation。当前实现将持久实例 snapshot 数据保存为带校验的
snapshot file，并恢复到 persistent client manager。

加载持久实例 snapshot 时必须：

1. 根据 snapshot data 更新已有 persistent clients；
2. 根据 snapshot data 创建缺失的 persistent clients；
3. 移除 snapshot 中不存在的 clients；
4. 产生修复派生索引和 service storage 所需的本地 Naming event。

Metadata groups 也必须提供 snapshot。加载 metadata snapshot 时，必须重建内存 metadata map，并保持
service identity 与 metadata 的连接。

## 6. 可见性

CP write 成功表示操作已经被对应 CP group 接受。运行时 query 和 push 可见性仍取决于本地 apply 和
派生服务状态：

- apply path 更新 client 或 metadata state；
- 本地 event 更新 index 和 service storage；
- discovery query 读取派生 service view；
- subscriber push 跟随 service change event。

持久服务和 metadata 的范围查询应声明其读取的是本地派生状态还是 CP read state。在某个 query 明确
通过 CP read 语义路由前，应将其视为本地服务状态视图。

## 7. 失败与未支持行为

- 如果 CP group 没有可用 leader 或无法 commit，写入必须失败，不得 fallback 到 Distro。
- 持久 batch registration 不是已完成能力，完成前不得文档化为标准行为。
- Persistent client verify 和 sync-client ownership 不属于 Distro 行为。
- Persistent subscription 不支持。

## 8. 相关规范

- [Naming 资源规范](naming-resource-spec.md)
- [Naming 实例生命周期规范](naming-instance-lifecycle-spec.md)
- [Naming 健康检查与保护规范](naming-health-protection-spec.md)
- [Naming 元数据与 Selector 规范](naming-metadata-selector-spec.md)
- [Naming 一致性与客户端状态规范](naming-consistency-client-spec.md)
- [运行时推送与重连规范](../client/runtime-push-reconnect-spec.md)
- [CP 一致性规范](../design/foundation-cp-consistency-spec.md)
- [任务执行规范](../design/foundation-task-execution-spec.md)
- [事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)
