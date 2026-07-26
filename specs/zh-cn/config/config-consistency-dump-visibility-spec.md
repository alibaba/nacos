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

# Config 一致性、Dump 与可见性规范

本文定义 Config 写入可见性、本地 dump 顺序、集群变更传播和运行时查询可见性。本文细化
[Config 规范](config-spec.md)和
[Config 持久化、Dump 与历史规范](config-persistence-history-spec.md)。

## 1. 范围

本文负责：

- Config 写入如何对本地读取和 listener 可见；
- 外部存储和嵌入式存储模式如何传播变更通知；
- 本地 dump file 和本地 cache 如何刷新；
- 灰度配置可见性如何与正式配置可见性组合。

本文不重新定义：

- Config 资源身份模型；
- Config content 语义；
- 存储引擎内部实现；
- 客户端本地 failover 文件。

## 2. 权威状态

Config resource 的权威状态是配置的持久化层：

- 外部存储模式使用配置的外部数据库；
- 嵌入式存储模式使用 Config model Raft group 的 CP 路径；
- 本地磁盘 dump 是服务缓存，不是权威存储。

运行时读取可以在 dump path 已经把持久化记录加载后，从本地 dump/cache 提供。过期或缺失的本地
dump 必须从持久化层修复，不得被视为新的权威状态。

## 3. 写入路径

Config publish 或 delete 操作必须：

1. 写入前校验 identity、参数、容量和鉴权；
2. 通过 repository layer 持久化正式或灰度状态；
3. 按 Config 操作规则记录历史和 trace 事实；
4. 当写入需要刷新服务缓存并通知 listener 时，发布 `ConfigDataChangeEvent` 或等价集群通知。

CAS 写入只有在持久化层确认 expected MD5 时才成功。CAS 失败不得发布变更事件。

聚合配置不属于标准 Config 能力模型，不得被引入新的 consistency 规则；其兼容状态遵循
[兼容与废弃策略规范](../design/compatibility-deprecation-spec.md)。

## 4. 外部存储可见性

外部存储模式下，所有节点共享外部数据库作为 durable storage。写入成功后，写入节点发布本地
`ConfigDataChangeEvent`，并向其他集群节点发送 `ConfigChangeClusterSyncRequest`。

每个收到变更事件的节点必须为正式或灰度 config key 创建 dump task。某节点的运行时查询视图在该
节点完成从持久化层到本地服务 cache 的 dump 后更新。

外部存储传播是基于 cluster request path 的 AP 风格通知。节点可能在收到通知、重试通知，或被周期性
full dump/change dump worker 修复前短暂落后。

## 5. 嵌入式存储可见性

嵌入式存储模式下，持久顺序来自 Config model CP group。服务端必须等待 CP metadata 表明 leader
可用后，startup dump 才能安全读取数据。Dump path 会标记 read context，使 startup dump 等待到
数据可读。

嵌入式存储路径写入 commit 后，必须通过 dump 刷新本地服务 cache，该节点的运行时 query 和 listener
视图才被视为已更新。变更通知不得绕过 CP commit 结果。

嵌入式模式下，leader-owned maintenance task 只能由 leader 执行，例如历史清理。本地 dump 仍是每个
节点自己的服务状态。

## 6. Dump 顺序

Dump ordering 按 Config identity 生效：

- 正式配置使用 `dataId`、`groupName` 和 `namespaceId`；
- 灰度配置还包含 `grayName`；
- 同一个 task key 上后来的 dump task 可以按 task manager 语义替换或合并更早的 pending work；
- dump task 必须读取该 identity 的最新持久化状态。

Dump 完成后更新本地 content cache 和本地磁盘 dump。Listener 和 fuzzy watch 通知必须从本地变更
可见性发出，而不是从未提交的写入意图发出。

## 7. 灰度可见性

灰度配置从属于正式 Config identity。灰度 publish 或 delete 必须刷新对应 `grayName` 的灰度服务
cache。运行时查询选择先按[Config 灰度发布规范](config-gray-release-spec.md)评估灰度规则，然后再
fallback 到正式配置。

从 Nacos 3.3 版本线开始，一致性和 dump 路径不再把 legacy beta/tag 存储行转换为
`grayName`，也不再同步空 tenant 与 `public` 之间的默认 namespace 重复记录。Dump task
只处理当前模型下持久化的 Config identity。

## 8. 失败与恢复

- 如果本地磁盘无法安全保存 dump 内容，服务端必须将其视为致命问题，因为运行时查询依赖本地服务
  cache。
- 如果集群通知失败，必须使用有界或退避调度重试，并由周期性 dump path 修复。
- 节点重启时，startup dump 必须从持久化层重建本地服务 cache，节点才应被视为具备 Config 查询正确性。
- 如果客户端错过 push，必须按[运行时推送与重连规范](../client/runtime-push-reconnect-spec.md)通过
  listener resync 和 query 恢复。

## 9. 相关规范

- [Config 发布与查询规范](config-publish-query-spec.md)
- [Config 监听与订阅规范](config-listener-watch-spec.md)
- [Config 灰度发布规范](config-gray-release-spec.md)
- [持久化与 Dump 规范](../design/foundation-persistence-dump-spec.md)
- [CP 一致性规范](../design/foundation-cp-consistency-spec.md)
- [AP 一致性规范](../design/foundation-ap-consistency-spec.md)
- [内部 RPC 与集群请求规范](../design/foundation-internal-rpc-spec.md)
