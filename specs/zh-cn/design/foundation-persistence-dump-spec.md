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

# Nacos 持久化与 Dump 规范

本文定义 Nacos 领域共用的持久化、dump、本地服务缓存和存储模式基础能力。它是
[基础能力规范](foundation-capabilities-spec.md)中持久化与 dump 部分的展开。

## 1. 定位

持久化与 dump 是基础能力，不是领域资源。

它们提供：

- 持久存储选择和生命周期；
- JDBC datasource 初始化、健康状态和事务访问；
- 面向持久化层交互的抽象，包括 database operation、row mapping、事务和 repository 支撑；
- 开启嵌入式集群存储时，通过 CP 复制嵌入式存储；
- 支撑高频读取的本地 dump 文件和 JVM 服务缓存；
- 从持久状态重建本地服务状态的启动和修复流程。

领域规范拥有逻辑 schema、资源身份、校验、鉴权和用户可见行为。持久化基础能力只拥有存储管线，不得
重新定义 Config、Naming、AI 或鉴权资源语义。

持久化中的数据通常带有领域语义。因此，具体 repository 接口和实现可以位于领域模块中，而不一定全部
位于 `persistence` 模块。例如 Config 拥有 `ConfigInfoPersistService` 及其 embedded/external
实现，因为这些操作理解 Config 记录、历史、灰度字段、容量元数据和 Config 可见性规则。

## 2. 存储模式

Nacos 支持两类存储模式：

| 模式 | 当前实现 | 持久事实来源 |
| --- | --- | --- |
| 嵌入式存储 | 通过 `LocalDataSourceServiceImpl` 使用 Derby；集群模式下写入通过 CP 协议排序。 | 单机 Derby，或 `nacos_config` CP group 加本地 Derby state machine。 |
| 外部存储 | 通过 `ExternalDataSourceServiceImpl` 使用 Hikari 管理的 JDBC datasource。 | 配置的数据源属性和方言规则选出的外部数据库。 |

选择规则：

- 单机模式默认使用嵌入式存储；
- 集群模式默认使用外部存储；
- datasource platform 非空且不是 `derby` 时，选择外部存储；
- 集群模式可以通过 embedded storage 配置开启嵌入式分布式存储；
- 存储模式确定后，`DynamicDataSource` 必须返回匹配的 `DataSourceService`，并在进程内只初始化一次。

存储模式是部署层面的运行选择。领域规范不得因为持久存储模式不同而暴露不同资源身份。

## 3. Datasource 契约

`DataSourceService` 提供存储运行时契约：

- 用于 SQL 执行的 `JdbcTemplate`；
- 用于事务写入的 `TransactionTemplate`；
- 用于诊断的数据源类型和当前 JDBC URL；
- 健康状态；
- 外部存储的主库可写检查。

外部存储规则：

- datasource 属性可以构造一个或多个 Hikari datasource；
- 当存在多个 datasource 时，主库选择使用写探测，并更新当前 `JdbcTemplate` 和 transaction manager；
- 每个 datasource 必须定期健康检查；
- 外部存储健康状态可以根据主库和从库可用性表现为 `UP`、`WARN` 或 `DOWN`；
- datasource reload 必须替换旧连接池，并安全地解绑旧 template。

嵌入式存储规则：

- 本地 Derby 通过 `derby-schema.sql` 初始化；
- 单机嵌入式存储直接访问本地 Derby；
- 分布式嵌入式存储启动时会清理并重新打开本地 Derby，因为有效状态必须来自 CP log replay 和
  snapshot recovery；
- 嵌入式 CP state machine 上报不可恢复 apply 错误时，Derby 健康状态必须变为 `DOWN`。

数据源方言插件位于 repository 与物理数据库族之间。它可以为数据库平台适配 SQL、mapper、分页、
生成主键和函数行为，但不得改变逻辑数据含义、schema 归属、鉴权或资源身份。参见
[数据源方言插件规范](../plugin/datasource-dialect-plugin-spec.md)。

## 4. Repository 契约

Repository service 在 datasource 细节之上定义领域存储语义。它是领域语义与持久化层抽象相遇的边界。

规则：

- `persistence` 模块可以提供 database operation、row mapper 注册、存储常量和异步执行等通用基础能力；
- 当存储数据带有领域含义时，领域模块可以定义具体 repository 接口和实现，例如 Config 的
  `ConfigInfoPersistService`；
- repository 接口拥有 add、update、remove、query、history、capacity、metadata persistence 等逻辑操作；
- embedded 和 external storage 的 repository 实现可以不同，但必须保持同一领域语义；
- repository 实现可以使用数据源方言 mapper 构造数据库相关 SQL，但校验、资源身份、历史、trace 和鉴权语义
  必须留在领域 repository 层；
- controller、request handler 和领域 service 应依赖领域 repository 接口，而不是直接调用 mapper 或方言 SPI；
- 当领域规范要求时，用户可见写入必须在同一领域操作中记录所需 history、trace、capacity 和变更元数据；
- 大范围 list 和 search 操作必须分页或通过其他方式限制边界；
- repository 代码不得把公开 API 输入中的原始 SQL 片段未经校验和方言控制就用于查询构造；
- 数据源方言插件和 mapper 不得成为定义领域行为或兼容策略的替代位置；
- 仅为兼容保留的 schema 字段不得成为新的语义契约，除非领域规范显式提升其语义。

Config 的 repository 语义由
[Config 持久化、Dump 与历史规范](../config/config-persistence-history-spec.md)定义。

## 5. 分布式嵌入式存储

分布式嵌入式存储使用 CP 基础能力进行持久写入排序。当前 Config 嵌入式存储 group 是
`nacos_config`。

模型如下：

```text
Repository operation
  -> ModifyRequest list
  -> DatabaseOperate.update / blockUpdate
  -> CP WriteRequest(group = nacos_config)
  -> JRaft commit
  -> DistributedDatabaseOperateImpl.onApply
  -> local Derby transaction
  -> embedded apply hooks
```

规则：

- 分布式嵌入式写入必须先经过 CP commit，才能将本地 Derby apply 视为持久成功；
- 当实现要求顺序时，`ModifyRequest` 必须按 execute number 排序后确定性执行；
- SQL limiter 必须在执行前拒绝不支持的 query 或 modify 形式；
- apply hook 必须在 committed apply 之后运行，不得用慢任务阻塞 state machine；
- `BadSqlGrammarException` 和 `DataIntegrityViolationException` 应作为操作失败返回，而不是停止 Raft
  state machine；
- 严重 datasource 访问失败可以表现为 consistency error，并必须更新可观测的存储健康状态。

启用分布式嵌入式存储时，读路径使用 CP read。启动 dump 可以设置阻塞读上下文，让节点等到已有可读数据
后再重建服务缓存。

Snapshot 规则由[CP 一致性规范](foundation-cp-consistency-spec.md)定义。嵌入式存储必须提供足够
snapshot 数据，使本地 Derby state 可以在重启或成员变化后重建。

## 6. Dump 与本地服务缓存

Dump 是本地服务状态的重建和刷新机制，不是第二套持久事实来源。

当前 Config dump 模型如下：

```text
Durable write or cluster change notification
  -> ConfigDataChangeEvent or ConfigChangeClusterSyncRequest
  -> DumpRequest
  -> DumpTask keyed by groupKey or groupKey + "+gray+" + grayName
  -> repository reload
  -> ConfigDumpEvent
  -> ConfigCacheService
  -> local disk dump and JVM cache
  -> LocalDataChangeEvent
  -> listener/watch push
```

启动 dump 规则：

- 启动阶段必须清理旧的正式和灰度 dump 文件，再从持久化层重建本地 dump 数据；
- 启动阶段必须将正式和灰度记录 dump 到本地磁盘和 JVM 缓存；
- 嵌入式集群启动必须等待 CP group 已有可读数据后，才完成启动 dump；
- 外部存储启动可以直接从配置的数据源执行 dump；
- 启动 dump 失败是服务端启动失败。

运行时 dump 规则：

- 持久写成功后，领域必须为受影响资源调度 dump refresh；
- 集群变更通知可以要求 peer 节点从持久化层重新加载本地 dump；通知 payload 不是权威内容；
- full dump 任务是修复和漂移控制机制；
- change dump task 应带有 key，使同一资源的重复变更能按
  [任务执行](foundation-task-execution-spec.md)规则合并或替换；
- dump apply 必须在本地服务状态更新后，按
  [事件分发](foundation-event-dispatch-spec.md)规则发布本地变更事件。

对于当前 Config 实现，运行时客户端读取从本地 cache 和 dump 文件服务。持久化层仍然是持久事实来源。

## 7. 本地 Dump 存储

本地 dump 存储由 `ConfigDiskServiceFactory` 选择。

规则：

- `rawdisk` 是默认的 `config_disk_type`；未知值也回退到 raw disk 行为；
- `rocksdb` 可以作为另一种本地 dump backend；
- 本地 dump 存储必须实现正式和灰度记录的保存、删除、读取和清理；
- raw disk 存储将正式和灰度内容保存在 Nacos home 下的数据目录，并区分带 namespace 和不带
  namespace 的路径；
- dump 存储 key 必须从经过校验和编码的 Config 身份字段派生；
- 切换本地 dump backend 不得改变 Config 资源身份、md5 语义、鉴权或持久事实来源。

本地 dump 存储属于服务缓存路径，不是 repository 契约，也不得作为独立持久数据库使用。

## 8. 缓存更新规则

本地服务缓存必须为每个资源 key 保留单调可见性。

规则：

- `lastModified` 更旧的 dump 必须忽略；
- 内容 md5 变化时，dump 必须同时更新本地磁盘内容和 JVM cache md5 状态；
- md5 未变化但时间戳更新时，可以只更新时间戳而不重写内容；
- remove dump 必须删除本地磁盘数据并移除 JVM cache 状态；
- 灰度 dump 还必须在对应字段变化时更新 gray rule、encrypted data key 和灰度排序；
- 同一资源 key 的 cache 和 dump 变更必须由本地读写锁保护；
- `LocalDataChangeEvent` 是本地可见性事件，不得视为跨节点复制保证；参见
  [事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)。

如果本地磁盘已满或无法安全保存正式 dump 内容，服务端必须将其视为致命条件，因为运行时查询路径依赖
本地服务状态。

## 9. 维护与清理

持久化与 dump 维护操作属于管理能力。

规则：

- history cleanup 必须只在存储模式选出的 owner 节点执行：
  - 嵌入式单机：唯一节点；
  - 嵌入式集群：Config storage group 的 CP leader；
  - 外部存储：按 member 顺序选出的第一个 Nacos member；
- local-cache full dump 是修复操作，不得替代正常发布或删除路径；
- Derby import 和 query 操作是 maintainer-only 操作，必须受显式运维开关和权限保护；
- 大批量 import 和 dump 操作必须批量化并设置边界；
- 维护 API 必须报告失败，不得把部分成功的存储修复静默视为成功。

## 10. 边界规则

- 持久化是 durable storage 管线；领域规范定义资源含义。
- 除非领域规范另有说明，dump 文件和 JVM cache 都是派生服务状态。
- 分布式嵌入式存储的持久性来自 CP commit 和 snapshot recovery，而不是单次本地 Derby 写入。
- 外部存储的持久性来自配置的外部数据库，而不是 Nacos 集群复制。
- 数据源方言插件适配 SQL，不适配领域语义。
- 除非接口规范显式暴露，本地事件、dump task 和 cache update 都是实现内部细节。
- 当 schema 兼容字段不再代表领域语义时，必须记录为兼容字段或待移除项。

## 11. 相关规范

- [基础能力规范](foundation-capabilities-spec.md)
- [CP 一致性规范](foundation-cp-consistency-spec.md)
- [AP 一致性规范](foundation-ap-consistency-spec.md)
- [内部 RPC 与集群请求规范](foundation-internal-rpc-spec.md)
- [任务执行规范](foundation-task-execution-spec.md)
- [事件分发与 NotifyCenter 规范](foundation-event-dispatch-spec.md)
- [Config 持久化、Dump 与历史规范](../config/config-persistence-history-spec.md)
- [Config 监听与 Watch 规范](../config/config-listener-watch-spec.md)
- [数据源方言插件规范](../plugin/datasource-dialect-plugin-spec.md)
- [Config 加密插件规范](../plugin/config-encryption-plugin-spec.md)
