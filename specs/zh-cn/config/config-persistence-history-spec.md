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

# Config 持久化、Dump 与历史规范

本文定义 Config 持久化、本地 dump 缓存和历史语义。

## 1. 持久化记录

Config 领域通过 repository service 存储可靠状态。主要记录族包括：

| 记录族 | 目的 |
| --- | --- |
| `config_info` | 正式 Config 内容、md5、类型、元数据、来源信息、namespace、group 和 encrypted data key。 |
| `config_info_gray` | 灰度 Config 内容、md5、灰度名称、序列化灰度规则、namespace、group 和 encrypted data key。 |
| `his_config_info` | 正式和灰度发布或删除操作的历史变更记录。 |
| `tenant_capacity` / `group_capacity` | namespace、group 和 cluster 范围的容量限制和用量计数。 |

Repository 接口定义 Config 语义。具体 SQL 和数据库方言属于实现细节，由
[数据源方言插件规范](../plugin/datasource-dialect-plugin-spec.md)覆盖。

## 2. 本地 Dump 缓存

每个服务端节点维护本地服务状态：

- 以内部 group key 为键的 JVM 缓存，包含 md5、时间戳、encrypted data key、内容类型和灰度规则状态；
- 保存正式和灰度配置内容的本地 dump 文件。

启动阶段必须将持久化的正式和灰度配置 dump 到本地服务状态。运行时查询读取缓存和本地 dump 文件，
不做大范围数据库查询。

## 3. 变更 Dump 流程

写入成功后，Config 发布变更事件。Dump service 将事件转化为 dump 任务。任务从持久化层重新加载
受影响的正式或灰度记录，并更新本地缓存和本地 dump 文件。
本地事件行为由[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)定义，
dump task 行为由[任务执行规范](../design/foundation-task-execution-spec.md)定义。

Dump 必须忽略过期时间戳，并保留更新的本地状态。如果 md5 未变化但时间戳更新，只更新时间戳状态
而不重写内容。如果内容变化，则同时更新本地文件内容和 JVM md5 状态。

## 4. 嵌入式与外部存储

Config 支持嵌入式和外部存储模式：

- 嵌入式存储必须等到 CP 协议已有可读数据后，才完成启动 dump；
- 外部存储直接从配置的数据源初始化 dump；
- 历史清理和管理类存储操作必须只在存储模式策略选中的节点上执行。

详细 Config 一致性契约由
[Config 一致性、Dump 与可见性规范](config-consistency-dump-visibility-spec.md)定义。嵌入式存储
的 CP 基础见[CP 一致性规范](../design/foundation-cp-consistency-spec.md)，Config Notify 传播见
[AP 一致性规范](../design/foundation-ap-consistency-spec.md)。持久化、dump、任务和事件边界由
[持久化与 Dump 规范](../design/foundation-persistence-dump-spec.md)、
[任务执行规范](../design/foundation-task-execution-spec.md)和
[事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)定义。

## 5. 历史

V3 Admin 和 Console 的历史详情及上一版本响应，从所选历史记录的 `extInfo.c_schema`
提取并返回 `schema`，同时原样保留 `extInfo`。不得使用当前配置的 schema 填充历史响应。
扩展信息缺失、不是合法 JSON 或没有文本类型的 `c_schema` 时，模型字段为 null，
响应可遵循默认序列化规则省略 `schema`，历史查询仍应成功。
Schema 文本按该条历史记录中的存储值返回。

历史记录必须保留足够信息以便检查和恢复配置变化：

- `dataId`、`groupName` 和 `namespaceId`；
- 内容和 md5；
- 来源用户和来源 IP；
- 操作类型，例如发布或删除；
- 发布类型，例如正式或灰度；
- 适用时的 `grayName` 和扩展信息；
- 创建和修改时间。

历史列表和详情 API 属于管理 API。分页大小必须有边界。历史清理使用配置的保留窗口，默认 `30` 天。

### 发布操作说明

本节为 #10978 的首期目标契约。维护者已确认控制台在新建和删除流程不提供说明入口，
但后台普通发布及 CAS 发布 API 对插入和更新采用相同处理，不根据是否首次创建而忽略说明。
删除、Beta/灰度和回滚不纳入首期。首期只要求历史详情展示，不要求历史列表展示。

#### 操作范围与输入

| 操作 | 首期行为 |
| --- | --- |
| 普通发布 API（插入或更新） | 可记录本次操作的说明，插入与更新使用相同处理。 |
| CAS 发布 API | CAS 校验及写入成功后记录说明；失败时不记录。 |
| 控制台新建流程 | 不提供说明输入；底层 API 不因此增加创建/更新分支。 |
| 删除配置 | 不增加说明输入或记录能力。 |
| Beta、Tag/灰度发布及回滚 | 首期不增加说明输入或记录能力。 |
| 仅元数据更新、导入、导出和克隆 | 不增加说明输入，也不复制历史说明。 |

普通与 CAS 发布 API 只要接收到可选说明，均按同一方式处理，不检查资源是否首次创建。
控制台不在创建表单提供输入，但其他 API 调用方可以在创建时提交说明。导入和克隆不增加
说明输入或复制历史说明；说明不得跨操作沿用。

普通及 CAS 发布接收可选文本 `changeMessage`，最多 512 个 Unicode 码点。超长返回参数
校验错误；缺省、空串或纯空白表示未填写；非空白文本保留原文和换行。
说明属于成功的更新操作，不属于配置快照或配置描述，不影响正文、md5、dump 和监听载荷。
校验或 CAS 失败不得新增说明或历史；内部跳过历史的操作保持原行为。

#### JSON 存储与历史详情

在已有 `his_config_info.ext_info` 中使用 JSON 对象属性 `change_message`，不直接存储裸文本。
例如：

```json
{"change_message": "调整连接超时时间"}
```

写入必须保留同一历史记录原有的快照扩展属性，包括 `c_schema` 和未知属性。未填写说明时不
继承旧说明。不得为了合并说明而将已有非对象或损坏的扩展内容覆盖成空对象；无法安全合并时，
保留原扩展内容并省略本次说明。说明与历史记录沿用现有发布事务，不增加一次独立的配置写入。

更新历史行仍按既有规则保存旧正文，说明描述生成该历史行的更新操作；首次插入历史行保存
新正文，说明描述该次创建。说明不得从当前配置或相邻历史行补取。V3 Admin 和 Console 历史详情从所选行提取可选 `changeMessage`，同时保留
原有 `extInfo`。扩展缺失、不是 JSON 对象、JSON 损坏或属性不是文本时返回 null（序列化可
省略），历史查询仍应成功。不回填旧记录；保留窗口保持不变。历史列表响应不增加说明字段；
历史详情及上一版本详情可按所选历史行返回说明。不提供按说明检索、排序或索引。

#### 数据源插件与升级兼容

首期复用已有历史扩展列，不要求为说明新增数据库列、索引或执行表结构迁移。内置适配器仍须
验证扩展信息读写；不能由内置 schema 已存在该列推断第三方插件已支持此能力。

`ext_info` 已由历史 Mapper 作为普通字符串列读写；将其 JSON 扩展对象增加可选属性不改变
SQL、Mapper SPI 或数据源插件接口，不需要新增能力协商。适配器若按既有契约读写 `ext_info`，
即可保存和返回该属性；若插件自行过滤或不识别该属性，历史详情可不显示说明，但配置发布和
历史写入仍遵循原有流程。扩展属性读取缺失或异常时，按旧历史无说明处理。

不得以捕获任意数据库异常并重试发布实现降级。连接、SQL、事务或配置写入的真实故障仍按原
契约报告，不得吞错或重复执行已经成功的更新。第三方插件对 `ext_info` 的既有实现仍需通过
插件验证确认；本功能不声称覆盖违反既有历史列契约的插件。

旧客户端不传参数时保持原行为。混合版本集群和旧独立控制台不保证保存或展示说明；说明
缺失不能改变配置更新结果。依赖此功能前，应完成相关节点升级及数据源适配验证。

#### 验收场景

以下为待实现和验证的场景，不是已有测试覆盖或通过声明：

| 场景 | 预期 |
| --- | --- |
| 普通更新及成功 CAS 更新 | 说明属于对应操作行，历史详情可读，原扩展属性保留。 |
| CAS 不匹配或参数校验失败 | 配置及历史不变，不保存说明。 |
| API 首次创建附带说明、普通更新及成功 CAS 更新 | 使用同一持久化逻辑写入，详情读取对应操作的说明。 |
| 控制台创建流程、删除及首期范围外操作 | 控制台不提供说明入口；其他范围外操作不新增说明能力。 |
| 缺省、空白、512/513 码点、中文、补充平面字符及换行 | 按输入规则处理，码点计数和文本往返正确。 |
| 旧历史、空扩展、损坏 JSON、非对象或非文本属性 | 查询成功且无说明，不覆盖原始扩展内容。 |
| 内置数据源与第三方数据源 | 验证其是否按既有 ext_info 契约保留并返回属性；不改变 Mapper SPI。 |
| 数据库故障或事务失败 | 保留原错误及原事务语义，不因说明功能吞错或重试发布。 |
| 旧客户端、混合版本及合并/独立控制台 | 配置更新语义不变；说明可缺失，详情以纯文本展示。 |

实现 HTTP 合同时必须同步更新 `test/openapi-test` 的场景文档、覆盖登记和测试；如新增
公开 SDK 合同，还须更新相应 SDK IT、场景及覆盖登记。插件兼容和事务边界另由持久化测试
验证，不能用 HTTP 成功响应代替这些验证。

## 6. 恢复与运维

Admin 本地缓存操作可以触发从持久化层到本地缓存的全量 dump。该操作是管理修复机制，不应作为正常
发布链路使用。

当本地磁盘已满或无法安全保存 dump 内容时，服务端必须将其视为致命条件，因为运行时查询正确性依赖
本地服务状态。

## 7. 相关规范

- [Config 一致性、Dump 与可见性规范](config-consistency-dump-visibility-spec.md)
- [持久化与 Dump 规范](../design/foundation-persistence-dump-spec.md)
- [CP 一致性规范](../design/foundation-cp-consistency-spec.md)
- [AP 一致性规范](../design/foundation-ap-consistency-spec.md)
- [内部 RPC 与集群请求规范](../design/foundation-internal-rpc-spec.md)
- [任务执行规范](../design/foundation-task-execution-spec.md)
- [事件分发与 NotifyCenter 规范](../design/foundation-event-dispatch-spec.md)
- [数据源方言插件规范](../plugin/datasource-dialect-plugin-spec.md)
