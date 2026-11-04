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

# Nacos Raft 文件日志存储规范

本文细化 [CP 一致性规范](foundation-cp-consistency-spec.md)中的文件日志后端，
定义配置、文件格式、本地发布、恢复、故障状态和验证要求。

## 1. 范围

file 后端通过 JRaft 的 `LogStorage` 接口保存单个 Raft group 的日志。
Segment 保存日志记录，Manifest 发布本地保留范围，Index 提供可重建的位置索引。
本地日志发布不等同于 Raft 多数派提交；committed index、状态机 apply 和 snapshot
语义继续由 CP 协议及领域规范负责。

`FileJRaftServiceFactory` 只创建 `FileLogStorage`；元数据存储、快照存储和
LogEntry codec 继续委托 `DefaultJRaftServiceFactory`。

## 2. 配置与后端选择

[RaftConfig](../../../core/src/main/java/com/alibaba/nacos/core/distributed/raft/RaftConfig.java) 的 `data` 映射接收 `nacos.core.protocol.raft.data.*` 配置。默认值定义在 [RaftSysConstants](../../../core/src/main/java/com/alibaba/nacos/core/distributed/raft/RaftSysConstants.java)，同步选项由 [RaftOptionsBuilder](../../../core/src/main/java/com/alibaba/nacos/core/distributed/raft/utils/RaftOptionsBuilder.java) 写入 `RaftOptions`。

| 配置项 | 默认值 | 行为 |
| --- | --- | --- |
| `nacos.core.protocol.raft.data.log_storage_type` | `rocksdb` | 选择 `rocksdb` 或 `file`；去除首尾空白并忽略大小写；空串、空白或其他取值抛出 `IllegalArgumentException` |
| `nacos.core.protocol.raft.data.sync` | `true` | 控制日志同步写入；file 后端要求为 true |
| `nacos.core.protocol.raft.data.sync_meta` | `false` | 传递给默认元数据存储的同步选项；不改变 file 对 `sync=true` 的要求 |

file 后端配置：

```properties
nacos.core.protocol.raft.data.log_storage_type=file
nacos.core.protocol.raft.data.sync=true
```

配置模板位于 [bootstrap](../../../bootstrap/src/main/resources/application.properties) 和 [distribution](../../../distribution/conf/application.properties)。

[JRaftServer.init()](../../../core/src/main/java/com/alibaba/nacos/core/distributed/raft/JRaftServer.java) 在启动 RaftExecutor 之前选择工厂并检查同步选项。选定的工厂保存在服务器实例中，创建 group 时显式设置到复制后的 `NodeOptions`。运行期间没有重新读取后端选择的路径。

缺省选择 `rocksdb`，显式选择 `file` 时使用 `FileJRaftServiceFactory`。
每个 group 必须使用初始化选定的工厂，包括复制 `NodeOptions` 之后。
配置取值非法或 file 的 `sync=false` 时，必须在 Raft 运行时启动前失败。
不支持运行中热切换后端。

后端选择仅创建对应存储实现，不转换已有日志、元数据或快照。
修改配置不能作为已有节点的数据迁移方式。整套存储副本会保留原 storeId。

## 3. 存储布局与身份

`JRaftServer.createMultiRaftGroup()` 与 [JRaftUtils.initDirectory()](../../../core/src/main/java/com/alibaba/nacos/core/distributed/raft/utils/JRaftUtils.java) 为每个 group 设置独立目录；`FileLogStorage` 在 group 的 log URI 下创建 `file-log`：

```text
<nacos.home>/data/protocol/raft/<group>/
├── log/
│   └── file-log/
│       ├── LOCK
│       ├── manifest
│       ├── manifest.tmp
│       ├── segments/
│       │   └── 00000000000000000001.seg
│       └── index/
│           └── 00000000000000000001.idx
├── meta-data/
└── snapshot/
```

配置的 log URI 及其祖先目录允许包含目录软链接。构造函数只保存绝对配置路径；初始化时先解析到真实目录，再拼接 `file-log`。缺失的配置目录在已解析的现存祖先下创建。解析保留相对链接和 `..` 的文件系统语义，不自动创建断链的目标；链接循环、非目录目标、权限错误及其他解析错误均导致初始化失败。仅在属性读取明确报告路径不存在时才创建目录；创建与其他初始化者并发时，须重新解析并验证已存在对象为目录。

本次打开使用的真实根目录固定，锁、Manifest、Segment、Index、回收和同步路径均从它派生。入口链接改指向不会重定向已打开实例；关闭或初始化失败后，下次初始化重新解析配置入口。同一目录的不同别名共享 storeId 和排他锁，该机制不执行数据迁移。不支持直接替换或搬迁已打开的真实存储目录。受管的 `file-log`、`segments`、`index` 必须是真实目录，受管文件保持既有 `NOFOLLOW_LINKS` 校验与回收规则。

`LOCK` 通过 `FileChannel.tryLock()` 提供排他锁。首次创建 Manifest 时生成 UUID `storeId`，后续 Segment Header 使用同一身份；恢复时校验 Manifest 的 groupId、Segment 的 storeId 和编号。 `storeId` 随数据持久化，复制整套文件会保留该身份。

Segment 与 Index 文件名使用补零到 20 位的 ASCII 十进制编号，后缀分别为 `.seg` 和 `.idx`。初始化在清理前扫描规范 Segment 文件名，结合 Manifest 中的最大编号计算下一编号。保留的未知归属文件也参与计算；编号溢出时初始化失败。非规范文件名不参与编号识别。

## 4. 二进制格式

多字节数值使用大端序，格式版本为 `1`。UUID 按高 64 位、低 64 位依次编码。 CRC32 使用 `java.util.zip.CRC32`，CRC64 使用 JRaft 的 `CrcUtil.crc64()`。以下区间采用左闭右开表示法，偏移量与长度的单位均为字节。

### 4.1 Segment Header

Segment 上限为 128 MiB，包含 40 字节 Header 和后续记录。生产存储使用该上限。

| 偏移 | 长度 | 字段 |
| --- | --- | --- |
| 0 | 4 | magic：`0x4e4c5331`（`NLS1`） |
| 4 | 2 | version |
| 6 | 2 | headerSize：40 |
| 8 | 16 | storeId |
| 24 | 8 | segmentId，必须大于 0 |
| 32 | 8 | CRC64，覆盖 `[0, 32)` |

### 4.2 日志记录

LogEntry 先通过工厂提供的 JRaft codec 编码为 payload，再包装为 file 记录。设 payload 长度为 `L`，总记录长度为 `24 + L`，上限为 64 MiB。

| 偏移 | 长度 | 字段 |
| --- | --- | --- |
| 0 | 4 | magic：`0x4e4c5231`（`NLR1`） |
| 4 | 2 | version |
| 6 | 2 | headerSize：16 |
| 8 | 4 | payloadLength：L |
| 12 | 4 | CRC32，覆盖 `[0, 12)` |
| 16 | L | JRaft LogEntry payload |
| 16 + L | 8 | payload CRC64 |

读取时先验证 Header、长度和记录边界，再分配并读取 payload。编解码均验证 index 大于 0、term 非负、条目类型有效；配置条目还需验证 peers、learners 及旧配置的成员关系。扫描恢复要求解码后的 index 与预期 index 连续一致。

### 4.3 Manifest

Manifest 保存 storeId、UTF-8 groupId、本地有效日志范围和有序 Segment 描述符。设 groupId 的 UTF-8 长度为 `G`，Segment 数量为 `N`，总长度为 `60 + G + 40N`。文件上限为 16 MiB，groupId 不能为空白，编码上限为 1024 字节；非法 UTF-8 或无法正确编码的字符串被拒绝。

| 偏移 | 长度 | 字段 |
| --- | --- | --- |
| 0 | 4 | magic：`0x4e4c4d31`（`NLM1`） |
| 4 | 2 | version |
| 6 | 2 | headerSize：52 |
| 8 | 4 | 文件总长度 |
| 12 | 16 | storeId |
| 28 | 8 | firstLogIndex |
| 36 | 8 | lastLogIndex |
| 44 | 4 | groupId 字节数 G |
| 48 | 4 | Segment 数 N |
| 52 | G | UTF-8 groupId |
| 52 + G | 40N | Segment 描述符序列 |
| 52 + G + 40N | 8 | 此前全部字节的 CRC64 |

每个描述符按顺序保存五个 long：`segmentId`、`firstIndex`、`lastIndex`、 `firstRecordOffset` 和 `publishedEndOffset`。编号严格递增，日志范围连续；每段有效字节范围为 `[firstRecordOffset, publishedEndOffset)`。全局首尾 index 必须与描述符范围一致。

Manifest 描述本地保留范围；JRaft 的多数派提交和状态机 apply 由上层协议处理。

### 4.4 Index

Index 没有文件头，每项 12 字节，依次为 long 类型的记录 offset 和 int 类型的记录总长度。槽位偏移为 `(logIndex - baseIndex) × 12`，文件长度由该段保留条目数量决定。读取时检查槽位、记录长度及其是否处于 Manifest 发布的字节范围。

Index 由 Segment 扫描重建。初始化对所有已发布 Segment 重建索引；追加时同步更新索引，但发布顺序不依赖 Index 刷盘。

## 5. 追加与范围变更

`FileLogStorage` 使用读写锁串行化变更。`getEntry()` 在读锁内定位 Segment、读取 Index、校验记录并解码，追加、截断、reset 和 shutdown 在写锁内执行。

### 5.1 批量追加

`appendEntries()` 先编码并校验整批条目：index 从 `lastLogIndex + 1` 连续递增，每条记录能放入空 Segment，计划中的 Manifest 满足格式及大小限制。校验失败返回 0，此时尚未写入批次文件。

写入与发布顺序为：

1. 向活动 Segment 追加记录，同时写入 Index；容量不足时刷盘旧段、关闭并创建新段。
2. 完成整批写入后刷盘最后一个 Segment。
3. 若新 Manifest 引用了新增 Segment，同步 `segments` 目录。
4. 写入 `manifest.tmp` 并执行 `force(true)`。
5. 使用 `ATOMIC_MOVE` 和 `REPLACE_EXISTING` 替换 `manifest`。
6. 同步 `file-log` 目录，再更新内存 Manifest，返回整批条目数量。

目录同步的平台策略和持久性边界遵循第 7 节。

### 5.2 前缀、后缀截断与 reset

| 操作 | 发布的范围 | 发布后的资源处理 |
| --- | --- | --- |
| `truncatePrefix(firstIndexKept)` | 保留目标及其后的条目；超过原末尾时发布从目标开始的空范围 | 重建边界段 Index，重开活动资源，回收失效段 |
| `truncateSuffix(lastIndexKept)` | 保留目标及其前的条目；低于原首 index 时发布以原首 index 开始的空范围 | 截断边界段并刷盘，截断 Index，重开活动资源，回收失效段 |
| `reset(nextLogIndex)` | 发布 `[nextLogIndex, nextLogIndex - 1]` 的空范围 | 关闭并重建空写入状态，回收原有段 |

截断边界位于已保留范围内时，先读取并校验对应记录，再发布新 Manifest。上述操作均先发布范围，再处理物理资源。reset 不创建边界占位条目；被移除条目和失效配置日志不得继续可读。
快照 index、term 和 membership 由 JRaft snapshot/LogManager 路径承接；
后续追加必须在立即操作和重启后均从 `nextLogIndex` 连续开始。空范围上的读取返回 null，`getTerm()` 返回 0。

日志 index、编号和偏移运算使用溢出检查。`reset(Long.MAX_VALUE)` 可以表示空范围，但在该位置追加会因下一 index 溢出而被拒绝。

## 6. 初始化与恢复

`init()` 执行以下步骤：

1. 检查同步写选项、groupId、ConfigurationManager 和 LogEntry codec。
2. 解析或创建配置的日志目录，派生并校验真实根目录、获取排他锁，创建 `segments` 和 `index`；同步两个子目录及真实根目录到文件系统根的祖先链。
3. 读取并验证 Manifest；没有 Manifest 时，仅允许空子目录、锁文件及普通临时 Manifest，随后创建空存储。
4. 在清理前计算 Segment 编号高水位。
5. 扫描全部已发布记录、验证存储身份和连续范围、重建 Index，并收集配置条目。
6. 检查未引用 Segment，确定可回收文件与需保留文件。
7. 同步已验证 Manifest 所在目录，建立恢复屏障。
8. 截断已发布 Segment 的未发布尾部并刷盘，回收失效段、无用 Index 和临时 Manifest。
9. 重开活动资源，将收集的配置条目加入 ConfigurationManager，确认资源完整后返回成功。

目录已存在时仍按相同顺序调用祖先链的同步入口。Windows 仅执行目录校验，不建立真实目录持久化屏障；其他平台执行目录同步。恢复屏障调用之前，Manifest 和 Segment 保持原状；派生 Index 可以重建。已发布数据缺失、校验错误、范围不连续或身份不符均导致初始化失败。平台持久性边界见第 7 节。

### 6.1 未引用 Segment 的分类

下表适用于 `segments` 中具有规范文件名、但未被 Manifest 引用的文件。

| 检查结果 | 行为 |
| --- | --- |
| Header 有效，storeId 与文件名编号均匹配 | 屏障之后可回收 |
| 普通文件 Header 不完整，magic、头长度或头部校验损坏 | 原地保留并记录包含路径的 WARN；继续恢复已发布日志 |
| Header 标明其他 storeId、编号与文件名不符、版本不支持或身份非法 | 初始化失败 |
| 非普通文件、符号链接或实际 I/O 错误 | 初始化失败 |

未知归属文件不会被重放；其规范文件名继续参与后续初始化的编号计算。

## 7. 文件系统与平台约束

| 操作 | 代码路径 | 失败处理 |
| --- | --- | --- |
| 排他锁 | `acquireDirectoryLock()` 对 `LOCK` 调用 `tryLock()` | 初始化失败并释放已获取资源 |
| 文件刷盘 | `Segment.force()` 与临时 Manifest 的 `FileChannel.force(true)` | 初始化或变更返回失败 |
| 目录刷盘 | `forceDirectory()` 先以 `NOFOLLOW_LINKS` 校验真实目录；Windows 校验后跳过，其余平台以 `READ`、`NOFOLLOW_LINKS` 打开并调用 `force(true)` | 路径校验失败仍向上传递；非 Windows 初始化、发布和恢复屏障中的同步失败向上传递，回收后的同步失败记录 WARN |
| Manifest 原子替换 | `writeManifest()` 使用同目录临时文件，执行 `ATOMIC_MOVE`、`REPLACE_EXISTING` | 发布失败，已有磁盘变更由下次恢复校验 |
| 文件类型检查 | Segment、Manifest 和目录检查使用 `NOFOLLOW_LINKS` | 要求普通文件或真实目录的路径不满足条件时拒绝打开 |

这些操作直接作用于已解析真实路径所在的文件系统。初始化按相同顺序调用 `segments`、`index`、`file-log` 及其全部祖先目录的同步入口，即使目录已存在也执行。通过 JRaft `Platform.isWindows()` 识别 Windows；该平台校验目录后自动跳过目录同步，不新增配置，其他平台保留严格同步。普通文件刷盘、排他锁和同目录 Manifest 原子替换仍然必需。

Windows 每次实际初始化成功时记录一条 WARN，包含存储路径及目录同步被跳过的断电持久性限制；幂等初始化和追加不重复输出。Windows 不能保证依赖目录同步屏障的断电恢复：已确认范围可能丢失，Manifest 与截断、回收结果不一致时可能恢复失败。进程强杀测试只验证进程故障恢复，不能作为断电安全证明。

## 8. 失败状态与诊断

参数、编码或批次规划失败在写文件前返回失败，存储仍可继续使用。实际变更中的必要 I/O 或资源重开失败会关闭活动 Segment/Index，并清空写入状态，后续追加、截断及 reset 均被拒绝。目录锁保留到 `shutdown()`；关闭后重新 `init()` 按磁盘状态恢复。

Manifest 移动后，目录同步或后续资源处理仍可能失败。返回失败时磁盘可能已包含新 Manifest，恢复以读取并验证后的磁盘状态为准。失效文件删除和回收后的 Segment 目录同步失败记录 WARN，已发布范围保持有效。

### 8.1 运行状态与返回值

| 状态或操作 | 可观察行为 |
| --- | --- |
| 初始化成功 | `init()` 返回 true，持有目录锁并具备完整读写资源 |
| 初始化失败 | `init()` 释放资源、清空实际根目录的运行时引用并返回 false；异常路径记录包含配置 log URI、可用的真实根目录及原始异常的 ERROR |
| 未打开或已关闭 | 首尾 index 返回 0，读取返回 null，追加返回 0，截断和 reset 返回 false |
| 范围外读取 | `getEntry()` 返回 null，`getTerm()` 返回 0 |
| 读取 I/O 或解码失败 | 记录含目录、index 和异常的 ERROR，读取返回 null |
| 批次预校验失败 | 记录 WARN，返回 0；批次尚未写入 |
| 必要变更步骤失败 | 记录 ERROR，关闭活动资源，禁止后续变更；目录锁继续持有 |
| 失效文件回收失败 | 记录 WARN，已发布范围继续有效 |
| 关闭 | `shutdown()` 关闭活动资源、释放锁并清空运行状态 |

变更失败后的恢复流程是先 `shutdown()`，再使用原数据执行 `init()`；重初始化会重新解析配置入口，运维须确保入口仍指向预期的数据目录。持锁期间重复调用 `init()` 只检查现有运行状态，不会重新执行恢复。

### 8.2 恢复诊断

以下消息由 `FileLogStorage` 的初始化与恢复检查产生：

| 消息或检查 | 对应状态 |
| --- | --- |
| `File log directory is already in use` | 未能取得排他锁；锁文件是否存在与锁是否正在持有分别判断 |
| `Manifest is missing with existing data` | 无 Manifest，但 `segments` 或 `index` 已有内容 |
| `Manifest group does not match JRaft group` | Manifest 的 groupId 与初始化上下文不同 |
| `Preserving unpublished segment with unknown ownership` | 未引用普通文件的 Header 不完整或损坏；保留文件并继续恢复已发布日志 |
| `Foreign segment in file log directory` | 未引用 Segment 的有效 Header 属于其他 storeId，初始化失败 |
| `Segment id does not match its file name` | 未引用 Segment 的 Header 编号与规范文件名不同，初始化失败 |
| `Segment identity does not match manifest` | 已发布 Segment 的 storeId 或编号与 Manifest 不同 |
| `Segment ID exhausted` / `File ID overflow` | 编号无法继续分配或文件名编号超过 long 范围 |

## 9. 验证要求

文件日志后端变更必须包含聚焦测试，覆盖：

- 默认后端、大小写和空白处理、非法配置、同步写约束，以及所有 group 的工厂选择；
- 默认元数据、快照和 codec 委托，以及隔离 RocksDB 类后的属性绑定、group 装配、
  写入、重启恢复和继续提交，包括安装目录和数据目录链接场景；
- 连续追加、term 读取、Segment 滚动、整批校验失败，以及失败批次不发布部分记录；
- 段内和跨段前后缀截断、空 reset、失效配置清除、重开后连续追加，以及 index、
  Segment 编号和偏移溢出边界；
- Header、CRC、长度、UTF-8 和身份校验、分配 payload 前的边界校验、派生 Index 重建，
  以及短读写、EOF 和零进展处理；
- 追加、截断和关闭期间的并发读取，只能观察到完整条目或对应的范围外、关闭状态；
- 新建和已存在目录的祖先链同步顺序、目录锁互斥，以及必要同步失败时禁止提前发布或开放写入；
- 上级或 log URI 链接下的首次创建和重启、相对与多级链接、`..` 解析、跨别名的身份与完整日志、
  别名间锁互斥、入口改指向后的目录固定、关闭或初始化失败后的重新解析、真实祖先同步失败及重试、
  断链/循环/非目录路径拒绝，以及受管对象的链接保护和外部目标保持不变；
- 已发布数据损坏、未引用文件分类、未知归属文件保留、编号高水位，以及恢复屏障前
  Manifest 和 Segment 字节保持不变；
- Segment 创建、部分 Header、完整 Header、Manifest 替换、尾部截断和回收窗口的
  进程中断，以及重复恢复和继续写入；
- 必要文件 I/O、目录同步和资源重开失败后的变更禁用、已发布范围不回滚，以及回收失败的尽力处理；
- LogManager 接入后的失败传播、reset 与 snapshot 边界、term 和配置历史承接；
- 真实 RPC 复制、落后节点安装快照、冲突后缀替换、reset 后立即复制及重启复制、
  成员变更、Leader/Follower 强杀后追平和全量重启后的继续提交；
- Linux 和 Windows JDK 17 的存储生命周期与进程恢复；局部平台 Mock 验证 Windows
  不打开目录通道、仍校验路径，其他平台保留目录同步和错误传播，普通文件刷盘失败在两者上均不可绕过。

集成验证必须区分真实文件、真实 RPC 和独立节点进程，不能用 Mock 替代对应的恢复断言。
进程控制与诊断必须遵守共同的超时预算，保留失败现场，并在单节点清理失败时继续清理其他节点。
Linux 链接测试必须使用真实软链接；Windows 在具备链接创建权限时运行真实链接测试，能力不足导致的跳过须单独记录，不计为通过。
局部平台 Mock 不替代平台实机验证；目录同步入口被调用不代表执行了真实刷盘。
进程强杀不模拟操作系统崩溃或断电，不能证明断电安全。

## 10. 相关规范

- [CP 一致性规范](foundation-cp-consistency-spec.md)
- [持久化与 Dump 规范](foundation-persistence-dump-spec.md)
- [服务端生命周期与环境配置规范](foundation-server-lifecycle-env-spec.md)
