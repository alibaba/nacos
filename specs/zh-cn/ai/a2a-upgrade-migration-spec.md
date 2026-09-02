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

# 历史 A2A 升级迁移规范

| 项目 | 值 |
| --- | --- |
| 状态 | 实验性升级契约 |
| 生效条件 | `nacos.ai.a2a.compatibility.mode=AUTO` |
| 来源版本 | Nacos 3.0～3.2 历史 A2A 存储 |
| 移除目标 | Nacos 4.0 删除迁移实现 |

本文定义从历史 A2A Config 定义和按精确 Version 划分的 Naming Service 到标准 Agent/RAD
模型的一次性升级，并细化 [A2A Agent 规范](a2a-agent-spec.md)、
[Agent 管理规范](agent-management-spec.md)和
[Agent 存储规范](agent-storage-spec.md)。

迁移实现属于临时兼容代码。标准 Agent、Agent Version、AI Storage、RAD Runtime 和纯
AgentCard Adapter 是长期能力，不随本迁移删除。

## 1. 兼容模式与事实权威

`nacos.ai.a2a.compatibility.mode` 继续按大小写不敏感解析，默认值仍是 `CANONICAL`：

| 模式 | 定义事实权威 | Runtime 发布 |
| --- | --- | --- |
| `LEGACY` | 历史 A2A Config 定义；不创建迁移状态。 | 只写历史精确 Version Naming Service。 |
| `CANONICAL` | 标准 Agent Resource、Version 和 AI Storage；不启动历史扫描。 | 只写标准 RAD Runtime。 |
| `AUTO` | 永久迁移 Marker 达到 `CANONICAL` 前，历史定义始终是权威。 | 按第 6 节迁移状态进行双物化。 |

终态完成前，把全部 Member 统一切回 `LEGACY` 可以停止迁移并保留历史权威。非终态 Marker
不覆盖显式 `LEGACY` 或 `CANONICAL`。持久化的终态 `CANONICAL` Marker 优先级高于本地模式：
有能力的 Member 一旦观察到终态，本进程永久使用标准权威；即使 Marker 后续被误删或本地配置
改变，也不得恢复 legacy-only 写路径。

`AUTO` 不执行请求级 fallback、合并读取或定义双写。每个旧 A2A 请求只选择一个完整定义权威。
Runtime 双物化是独立的连接态兼容行为，不创建第二个定义权威。

## 2. 配置与内部状态

### 2.1 配置

| 配置 | 默认值 | 规则 |
| --- | ---: | --- |
| `nacos.ai.a2a.compatibility.mode` | `CANONICAL` | `AUTO` 即对本迁移的明确授权。 |
| `nacos.ai.a2a.migration.legacy-naming-shadow-enabled` | `false` | 切流后是否保留精确 Version 历史 Naming 影子；值固化在迁移 Marker 中。 |
| `nacos.ai.a2a.migration.reconciliation.interval-seconds` | `300` | 全量对账间隔。 |
| `nacos.ai.a2a.migration.reconciliation.page-size` | `100` | 有界历史定义分页大小；实现设置安全上限。 |
| `nacos.ai.a2a.migration.quiescing-timeout-seconds` | `120` | 一代 Quiescing 超时后回到 `SYNCING` 的最大时间。 |

不新增独立迁移 Enable 开关。同一个迁移计划的全部 Member 必须使用 `AUTO` 和相同的固化
Shadow 策略；策略不一致会阻止 Quiescing 和切流。

### 2.2 内部 Config 对象

默认 Namespace 和内部 Group `nacos_internal` 最多包含三个有界控制对象：

| DataId | 含义 | 权威性 |
| --- | --- | --- |
| `nacos.ai.a2a.migration.v1` | State、Generation、固化 Shadow 策略和完成时间。 | 状态机权威 Marker。 |
| `nacos.ai.a2a.reconciliation.lease.v1` | 可续约单 Writer Lease。 | 只表示临时所有权。 |
| `nacos.ai.a2a.reconciliation.progress.v1` | 有界 Cursor、Counter、Conflict 和最近错误摘要。 | 只用于诊断。 |

Marker 和 Lease 使用 Config Compare-And-Set。写入结果不确定时，重读同一对象确认。迁移不创建
逐 Agent Row，也不使用 `ai_resource_task` 保存迁移任务；已有 Search Task 维持独立契约。

Marker Schema 为：

```json
{
  "schemaVersion": 1,
  "state": "SYNCING | QUIESCING | CANONICAL",
  "generation": "opaque-generation",
  "legacyNamingShadow": false,
  "startedAt": 0,
  "updatedAt": 0,
  "completedAt": null
}
```

`CANONICAL` 状态必须包含 `completedAt`。Progress 有界保存，不得随 Agent 数量线性增长。

## 3. 状态机与集群能力

```text
ABSENT -- AUTO creates plan --> SYNCING -- all gates --> QUIESCING
                                  ^                        |
                                  | timeout/member change | all-member ACK
                                  +------------------------+ + final zero diff
                                                           |
                                                           v
                                                       CANONICAL
                                                        terminal
```

| 状态 | 旧 A2A 定义读取 | 旧 A2A 定义 Mutation | Endpoint 操作 |
| --- | --- | --- | --- |
| `SYNCING` | 历史 Config。 | 先提交历史写；成功后异步发起单资源对账。 | 历史主写 + 必需标准镜像。 |
| `QUIESCING` | 历史 Config。 | 以可重试 Detail Error `AGENT_MIGRATION_IN_PROGRESS`（`50105`）拒绝。 | 双物化继续，不中断 Runtime。 |
| `CANONICAL` | 标准 Agent 投影。 | 标准 Agent 兼容 Facade。 | 标准主写 + Marker 中可选的历史 Shadow。 |

进入 `QUIESCING` 前，Member Metadata 必须包含：

- `supportA2aMigrationV1=true`；
- 由 Mode、Marker Schema 和固化 Shadow 策略生成的 `a2aMigrationPolicyHash`；
- Member 安装定义写屏障并验证本地目标可读后上报
  `a2aMigrationAck=<generation>:READY`。

服务端版本字符串不能代替这些能力。Member Metadata 缺失、非法、不一致或成员集合为空时，
`AUTO` 保持 `SYNCING`。不理解终态 Marker 的 Member 不得在已完成集群中处理 A2A 管理流量。

## 4. 历史定义对账

### 4.1 来源扫描与指纹

Lease Owner 分页遍历 Namespace，并直接扫描历史 Config `group=agent`。公开 AgentName 必须
从 Summary 内容取得，不能通过解码历史 DataId 推导。每个 Summary 列出的
`group=agent-version` 对象都必须完成校验。

迁移私有 Source Fingerprint 覆盖 Summary 内容、Enable、Latest、Version Set 和每个 Version
Config MD5。读取后再次计算；并发源变化会使本次结果失效并进入重试。该 Fingerprint 仅用于迁移
防竞争，不属于 RAD Revision 或公开 Cache Token。

AgentName 非法、Version 非法、Version 内容缺失、Latest 非法、未知 Registration Type、JSON
损坏或 AgentCard 无法按当前 Adapter 规范化时，记录为阻断冲突。迁移不得 Trim、改名、虚构
Version 或静默丢字段。

### 4.2 标准投影

每个合法历史 Agent 按以下规则投影：

- `type=agent`，保持原始公开 AgentName；
- `owner=nacos`、`scope=PUBLIC`，并保持历史 Enable 状态；
- 来源 `legacy-a2a-migration-v1`；
- 每个历史 Version 都是 `online`；
- 通用 `latest` 严格使用历史指针；
- 一个 `protocol=a2a` CallInterface，包含完整 AgentCard；
- Registration Type 通过现有 A2A Adapter 映射为 Declared Endpoint 和 Source Order；
- 不虚构自定义 Label。

实现必须与标准兼容 Facade 共用同一份纯 Legacy-to-Canonical Converter。转换逻辑不是迁移状态，
临时迁移 Package 删除后仍可复用。

### 4.3 Storage 与可见顺序

Reconciler 为每个 Version 准备标准 `AgentVersionContent`，通过当前 AI Storage Provider 写入，
再用同一 Key 读回并校验 Bytes、Size、SHA-256 Digest 和反序列化结果，随后幂等写 Version Row。
只有全部 Version 完整后，最后写 Resource Row 和派生 Version Catalog。

如果 Source Fence 变化或进程失败只留下 Version-first Row，后续对账只有在这些 Row 构成当前
历史源的精确标准子集时才能续写；出现额外 Version、Storage Descriptor 被改动、内容差异或元数据
差异时，仍必须记录阻断冲突。

不同 Storage 一致性按以下方式处理：

| Provider 一致性 | 对账行为 |
| --- | --- |
| `STRONG` | Save 返回后可以立即读回校验。 |
| `EVENTUAL_WITH_NOTIFICATION` | Notification 用于唤醒重试，读回校验仍是权威。 |
| `EVENTUAL_WITHOUT_NOTIFICATION` | 允许有界退避轮询，随后由后续全量扫描继续处理。 |

Storage 暂不可见只阻塞该 Agent 和全局切流，不破坏历史读取。内部 Batch Persistence 入口只能
创建或修复 `legacy-a2a-migration-v1` 事实，不能成为通用 Agent 生命周期绕过入口。

### 4.4 幂等、冲突与删除

| 标准目标 | 固定行为 |
| --- | --- |
| 不存在 | 创建完整的迁移来源 Agent。 |
| 同来源且等价 | 成功 No-op。 |
| 同来源但历史源已变化 | Reconciler 在重新校验 Source 后可以修复。 |
| 独立标准 Agent 且严格等价 | 保留其 Owner、Scope 和 Source，计为外部等价。 |
| 独立标准 Agent 只部分等价或内容不同 | 两边都不覆盖，记录阻断冲突。 |
| 目标 Storage 损坏 | 只修复迁移来源；否则记录冲突。 |

严格等价包含 AgentName、Enable、完整 Online Version 集、Latest、规范化 A2A 内容和 Declared
Endpoint；不比较格式、时间戳和内部数据库 ID。

历史删除只能清理迁移来源的目标事实。非完整扫描不得删除 Orphan；只有连续完整扫描都确认源缺失
后才能清理，防止来源暂时不可见导致误删标准状态。

`SYNCING` 期间，有能力节点的历史 Mutation 成功提交旧写后，再向有界合并内存队列提交对账提示。
提示失败不改变响应；周期全量扫描补齐旧节点写入和丢失提示。通用 Agent API 修改迁移来源 Agent
时返回 `AGENT_MIGRATION_IN_PROGRESS`；不相关 Agent 和其他 AI Resource 保持可用。

## 5. Search、事件与读取投影

迁移来源 Agent 完整落地后，可以在全局权威仍为 `SYNCING` 时原子出现在标准 Agent、ARD、Search
和 RAD 读取中；部分写入的 Agent 不得出现。旧 A2A 读取在切流前始终使用完整历史视图。

Resource-last 可见后，调度已有共享 Search Task 和 AI Resource Change Notifier。Runtime 注册、
历史 Shadow、迁移 Lease 和 Progress 不进入 Agent Search Document 或定义 Change Fingerprint。

启用 Search 时，每个迁移 Agent 的当前 Projection 完成是一项一次性切流门禁；日常 Search 在
Not Ready 时仍按契约快速返回当前部分结果。关闭 Search 时跳过该迁移门禁。

## 6. Runtime Endpoint 双物化

Runtime Endpoint 属于活跃 Naming Publisher，不作为历史持久数据复制。有能力节点把一个校验后的
逻辑旧 A2A Publication 按状态路由到物理 Layout：

```text
SYNCING / QUIESCING: legacy primary -> required canonical mirror
CANONICAL:           canonical primary -> optional legacy shadow
```

逻辑请求只执行一次校验和容量计数。每个物理 Layout 使用绑定原始 AI Connection 的确定性 Child
Publisher。Register、完整 Batch Replace、Deregister、Disconnect、Client Expire 和 Server
Shutdown 都幂等清理两个 Child。

`SYNCING`/`QUIESCING` 中，标准镜像失败不回滚已经成功的历史主写；完整 Batch 进入有界 Connection
内重试，并在收敛前阻止切流。`CANONICAL` 中，历史 Shadow 失败不回滚标准主写，也不改变 RAD 读取；
它在连接内重试，并通过有界日志和 Metric 报告。

可选 Shadow 只覆盖天然携带一个精确 Version 的旧 A2A 单条、批量和注销操作。通用 RAD Range
不得展开成多个历史 Version Service；Declared Endpoint、其他 Protocol 和定义生命周期变化都不得
创建历史 Naming Instance。

切流后，RAD Discover、Watch、Health、Binding 选择和 Source Revision 始终使用标准 Runtime
Service。Shadow 只向直接历史 Naming 消费者可见，不产生重复 RAD 事件。

进入 Quiescing 前，至少连续两轮完整比较所有可识别的活跃历史精确 Version Service 与对应标准
精确 Version 投影。规范化后比较 URI、Transport、旧 Protocol Version、Tenant、Enabled、Health、
Priority、Weight 和公开 Metadata；忽略 Publisher Id、Child Id、时间戳和仅供实现使用的 Metadata。
无法解析的历史 Service 或 Instance 属于阻断冲突。

## 7. 安全切流

Lease Owner 只有在以下条件全部满足时才能进入 `QUIESCING`：

1. 全部已知 Member 上报迁移能力、`AUTO` 和相同 Policy Hash；
2. 至少连续两轮完整全 Namespace 扫描定义零差异；
3. 不存在非法来源、冲突、缺失 Version、Storage 校验失败或 Pending Delete；
4. 已启用的 Search Projection 已 Current；
5. 每个活跃历史 Runtime Snapshot 在标准 RAD 中等价，必需镜像重试队列为空；
6. 本轮 Lease 和成员视图稳定。

Owner 通过 CAS 创建新的 Quiescing Generation。每个 Member 安装写屏障、验证本地所有目标
Resource/Version/Storage 可读、确认本地必需 Runtime Mirror Queue，并发布该 Generation ACK。
Generation 对外保持不透明，内部绑定稳定 Member View Digest 和一次性 Nonce，因此旧成员视图的
ACK 不能满足当前写屏障。当前成员全集 ACK 后，Owner 再完成一次最终定义、Storage、Search 和
Runtime 零差异扫描。

成功时写入带 `completedAt` 的终态 `CANONICAL` Marker。超时、成员或策略变化、ACK 缺失或最终
出现任一差异时，通过 CAS 回到 `SYNCING` 并解除定义写屏障。Quiescing 全程保持读取、Discover、
Watch、Endpoint 发布和已有 Runtime 流量可用。

两阶段写屏障避免一个 Member 已接受标准写而另一个仍接受历史定义写。终态 Marker 传播期间，最终
扫描已证明两种读取等价，且两套 Runtime 都已物化，因此读取安全。Config Notification 加速 Marker
感知，每个节点默认同时每 3 秒低频复核。临时内部配置
`nacos.ai.a2a.migration.quiescing-check-interval-seconds` 可在受控验证或运维时调整该周期，但不会
放宽成员、ACK、Lease、Search、Runtime 或最终扫描的任何门禁。

## 8. 故障、回滚与清理

| 故障 | 可用性规则 |
| --- | --- |
| Lease Owner 丢失 | 历史读写和 Runtime 继续；Lease 到期后其他 Member 恢复。 |
| Namespace/Page 扫描不完整 | 本轮不删除，也不计入零差异轮次。 |
| 标准写部分成功 | 不计完成；幂等修复迁移来源事实。 |
| 历史写成功但 Hint 失败 | 返回历史成功；周期扫描修复。 |
| 必需 Runtime Mirror 失败 | 历史 Endpoint 可用，切流被阻止。 |
| 切流后可选 Shadow 失败 | 标准 RAD 可用，告警并重试。 |
| Quiescing 超时或成员变化 | 回到 `SYNCING` 并恢复历史 Mutation。 |
| 冲突或数据损坏 | 保留两边并阻止切流，等待运维修复。 |
| Restart | 重载 Marker；活跃 Client 通过 Redo 重建 Runtime。 |

终态切流前，运维可以把全部 Member 设为 `LEGACY` 并滚动回退，迁移来源目标数据可保留供下次幂等
运行。终态切流后不自动删除或降级 Marker，历史 Config 保留但冻结；只支持回退到理解标准 Agent/RAD
和终态 Marker 的二进制，Legacy-only 二进制不得重新加入。

本契约不执行破坏性来源清理。后续清理必须具备备份、Dry Run、逐 Namespace 统计、稳定兼容窗口、
无旧 Server/Client/直接 Config 消费者、明确 Shadow 决策和防止来源复活的 Tombstone。

日志、Metric、Marker、Lease 和 Progress 使用有界资源摘要或 Hash，不得暴露完整 AgentCard、
Credential、Token 或敏感 Endpoint Metadata。

## 9. 升级 Runbook

### 9.1 历史 3.0～3.2 集群

1. 备份数据库和历史 A2A Config，盘点直接历史 Naming Service 消费者；
2. 选择切流后的 Legacy Naming Shadow 策略；
3. 新 Member 全部配置 `mode=AUTO` 和相同策略；
4. 滚动升级；混合版本期间历史定义继续作为权威，有能力节点对账并双物化 Runtime；
5. 观察 Progress、Conflict、Storage、Search、Member Policy 和 Mirror 诊断；
6. 只有全部门禁满足后才由系统 Quiesce 并切流；
7. 交叉验证旧 A2A、标准 Agent、RAD、Watch，以及启用时的历史 Naming Gateway。

### 9.2 已使用早期 3.3 `AUTO` 的集群

早期 `AUTO` 实现可能只按版本切流。升级到本契约前，必须先把每个旧 Member 固定为 `LEGACY`、
重启，并确认历史 Config 是预期权威，然后执行第 9.1 节。新节点不猜测旧节点的内存路由状态。

### 9.3 已有标准集群

没有导入需求的集群保持默认 `CANONICAL`，不创建迁移控制对象。需要导入仍保留的历史 Config 时，
先执行 Dry Run 再使用 `AUTO`；同名但内容不同的两侧事实不自动 Merge。

## 10. 临时实现与移除

迁移生产代码集中在 A2A Migration Package；每个临时类或迁移专用方法都包含统一声明：

```java
/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 */
```

长期主流程中无法避免的分支前使用：

```java
// TODO(remove in 4.0): Temporary migration path for Nacos 3.0-3.2 A2A data.
// Keep canonical behavior independent from this branch.
```

每个 Integration Point 都登记在 Migration Removal Inventory 中，包含删除动作、保留的标准依赖、
应删除和应保留的测试。专用静态测试校验声明和 Inventory 一致性。

Migration Package、状态机、来源扫描、Lease/Progress、迁移写保护、Runtime 双物化过渡、可选历史
Shadow 及其配置计划在 Nacos 4.0 删除。删除时不得改写已经标准化的 Resource、Version、Storage
或 Runtime 数据，也不得在没有独立废弃决策的情况下删除仍处于兼容期的公开 A2A Facade。

## 11. 必须测试矩阵

每个实现 Commit 必须完成并记录自身负责的场景，才可以进入下一 Commit。异步断言使用有界轮询和
公开或稳定内部事实，不以固定 Sleep 作为成功条件。

### 11.1 Unit 场景

| ID | 必须验证的行为 |
| --- | --- |
| `M-UT-01` | Mode、Marker 优先级和终态进程 Latch。 |
| `M-UT-02` | Member Ability/Policy 校验，以及缺失或非法 Metadata 保守处理。 |
| `M-UT-03` | Marker/Lease CAS、续约、失主、结果不确定和终态不可逆。 |
| `M-UT-04` | 完整 Legacy Summary/Version 转换、Codec 边界和严格校验。 |
| `M-UT-05` | 全部 AI Storage 一致性模式和 Byte/Size/Digest 读回。 |
| `M-UT-06` | Version-first/Resource-last、并发源变化和幂等恢复。 |
| `M-UT-07` | 同来源修复、外部等价、冲突和安全 Orphan 清理。 |
| `M-UT-08` | 写后 Hint 失败隔离、合并和周期修复。 |
| `M-UT-09` | Syncing 的 Legacy-primary/Canonical-mirror 顺序和部分失败。 |
| `M-UT-10` | Canonical-primary/Optional-shadow 顺序和部分失败。 |
| `M-UT-11` | Register/Replace/Deregister/Disconnect 双清理和单次容量计数。 |
| `M-UT-12` | Runtime 语义等价和非法历史 Instance 阻断。 |
| `M-UT-13` | Quiescing Fence、ACK、Timeout 和成员变化回退。 |
| `M-UT-14` | Search/Notifier/Watch 权威不包含 Shadow 重复事件。 |
| `M-UT-15` | 非 A2A Agent 和其他 AI Resource 完全隔离。 |
| `M-UT-16` | 临时声明和 Removal Inventory 完整。 |

### 11.2 单机与集群场景

可执行场景的详细归属在 OpenAPI 与 Java SDK IT Registry 中，编号为 `M-ST-01..10` 和
`M-CL-01..10`。矩阵覆盖完整迁移、历史并发 Mutation、非法/冲突数据、Crash Recovery、跨接口读取、
两套 Runtime Layout、Shadow On/Off、Reconnect/Redo、Quiescing 可用性、混合 Member 滚动升级、
跨节点对账、Leader/Owner 重启、ACK/Marker 传播、负载均衡读取、回滚边界和全部无关资源回归。
