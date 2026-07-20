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

# 数据源方言插件规范

## 范围

数据源方言插件用于把数据库相关 SQL 行为从 Nacos 持久化逻辑中隔离出来。它覆盖 SQL 方言
函数、分页、生成主键，以及 Nacos 表对应的 mapper 实现。

这是互斥选择插件。活跃方言由 `nacos.plugin.datasource-dialect.type` 在启动时选择，
`spring.sql.init.platform` 继续作为历史 alias。通用生命周期和状态规则由
[Nacos 插件化规范](plugin-spec.md) 定义，内置数据库族由
[默认数据源方言插件实现规范](default-datasource-dialect-plugin-spec.md) 定义。

该插件的存在原因是：Nacos 持久化需要保持同一套逻辑 schema 和 repository 契约，同时允许
不同数据库使用不同 SQL 方言。数据源方言插件不是持久化领域的 owner；它把 repository 契约
翻译成数据库相关 SQL。持久化与 dump 边界由
[持久化与 Dump 规范](../design/foundation-persistence-dump-spec.md)定义。

领域模块仍然可以拥有具体持久化实现，因为存储记录通常承载领域语义。例如 Config repository service
拥有 Config 发布、历史、灰度和容量语义，而本插件只提供这些 repository 使用的数据库相关 SQL 方言和
mapper 层。

## 概念

| 概念 | 含义 |
|------|------|
| SQL platform | 部署选择的数据库类型，例如 `derby`、`mysql`、`postgresql`、`oracle`。 |
| Dialect | 数据库级 SQL 行为，例如分页、生成主键和函数。 |
| Mapper | 某个逻辑 Nacos 表在某个数据库类型下的表级 SQL provider。 |
| Logical schema | 所有数据库共享的 Nacos 表和列语义。 |

Repository 实现负责选择逻辑操作，并在需要时调用 mapper。Mapper 不得决定资源身份、鉴权、兼容策略或
用户可见的领域行为。

SQL platform 必须选择同一个数据库族的 `DatabaseDialect` 和 mapper 集合。混用一个数据库的
dialect 和另一个数据库的 mapper 是无效行为。

## SPI

方言实现提供 `DatabaseDialect`。

| 方法 | 要求 |
|------|------|
| `getType()` | 稳定数据库类型，例如 `derby`、`mysql`、`postgresql` 或 `oracle`。 |
| `getLimitTopSqlWithMark(sql)` | 增加基于占位符的 top limit SQL。 |
| `getLimitPageSqlWithMark(sql)` | 增加基于占位符的分页 SQL。 |
| `getLimitPageSql(sql, pageNo, pageSize)` | 增加带数字值的分页 SQL。 |
| `getLimitPageSqlWithOffset(sql, startOffset, pageSize)` | 增加 offset 分页 SQL。 |
| `getPagePrevNum(page, pageSize)` | 返回第一个分页参数。 |
| `getPageLastNum(page, pageSize)` | 返回第二个分页参数。 |
| `getReturnPrimaryKeys()` | 返回生成主键列。 |
| `getFunction(functionName)` | 将逻辑函数名映射到方言 SQL 函数。 |
| `isDuplicateKeyException(throwable)` | 判定数据源抛出的异常是否为唯一键重复冲突。默认识别异常因果链中的 Spring `DuplicateKeyException`，方言可重写以实现驱动级别的判定。 |

`isDuplicateKeyException(throwable)` 是 config 仓储判断插入失败是否为唯一键重复冲突的
统一入口。默认实现会遍历异常因果链，当发现 Spring 的 `DuplicateKeyException` 时返回
`true`，并通过类名匹配以保证数据源插件模块不引入 Spring 依赖。这复现了此前与数据库无关
的分类作为安全基线，并刻意不将裸的厂商 SQLState（如 `23505`）本身当作重复。

PostgreSQL、MySQL、Derby、Oracle 等方言可以重写该方法，在标准 Spring 异常转换不够精确
时进一步检查原始驱动异常（SQLState 或厂商错误码），通常会通过
`DatabaseDialect.super.isDuplicateKeyException(throwable)` 调用默认实现并与自身判定组合。
分类必须保持保守——非重复的完整性约束失败不得被误报为重复。

表级 mapper 插件实现 `com.alibaba.nacos.plugin.datasource.mapper.Mapper`，用于提供具体表的
SQL。一个数据库族的方言和 mapper 实现必须一起打包和加载。

Mapper 实现必须提供 repository 操作需要的基础 CRUD SQL 和表级专用 SQL。当前 mapper 族
覆盖：

- 当前配置数据、灰度数据、标签和历史；
- 命名空间和容量记录；
- AI 资源元数据和版本记录。

从 Nacos 3.3 版本线开始，数据源方言插件不再预期提供空 tenant/default namespace 重复记录或
legacy beta/tag 灰度表的运行时 Config 迁移查询。如果 pre-3.0 部署仍需要这些迁移，应作为升级
前置动作完成，而不是服务端运行时 mapper 的职责。

`MapperManager` 通过 SPI 加载 mapper，并按 `dataSource + tableName` 建立索引。
缺少数据源或表 mapper 是启动或操作错误，而不是空结果。

## 选择与状态

核心插件管理器以 `datasource-dialect` 类型暴露该插件。只有配置选中的方言启用。该插件
类型属于 critical，加载后必须保留一个被选中的实现。

方言 selector 只提供启动选择并需要重启生效。该互斥类型的持久化状态不能替代静态选择，
运行时 status API 必须拒绝选择变更。

如果请求的方言被禁用，启动或持久化操作必须显式失败。如果请求的方言缺失，当前 manager
会查找其他已启用方言并记录 fallback。该 fallback 属于兼容行为；新部署应明确配置受支持的
SQL platform。

当前 `DatabaseDialectManager` 在返回 dialect 前，会检查
`datasource-dialect:{databaseType}` 的统一插件状态。被禁用的 dialect 不得参与持久化
操作。

## 配置

SQL platform 通过以下配置选择：

```properties
nacos.plugin.datasource-dialect.type=${databaseType}
```

`spring.sql.init.platform` 继续作为历史 alias；二者同时存在时标准 key 优先。已移除的
`spring.datasource.platform` 不再读取。

### Datasource 模块配置

数据源连接属性由 Nacos persistence 模块和数据库驱动持有，并统一使用以下模块前缀：

```text
nacos.plugin.datasource.db.{item}
```

该命名空间不会让数据库方言变为可配置插件。内置
`datasource-dialect:{databaseType}` 仍以 `configurable=false` 暴露，因为连接凭据和连接池
参数属于服务端唯一数据源，而不是分别属于每个已加载方言。这些配置均为静态配置，只在重启后
生效，当前不进入插件 detail/PUT 配置 API。未来若要提供统一管理入口，必须先定义唯一的
datasource 配置 owner，不能把同一份凭据复制到所有方言。

稳定的 datasource 模块配置如下：

| 标准 key 或 pattern | 历史 alias | 含义 |
|---------------------|------------|------|
| `nacos.plugin.datasource.db.num` | `db.num` | 外部数据源节点数量；使用外部存储时必填且必须为正数。 |
| `nacos.plugin.datasource.db.url.{index}` | `db.url.{index}` | 从 `0` 到 `num - 1` 每个 index 的 JDBC URL。 |
| `nacos.plugin.datasource.db.user[.{index}]` | `db.user[.{index}]` | 共享或按 index 配置的用户名；缺少某个 index 时回退共享值或 index `0`。 |
| `nacos.plugin.datasource.db.password[.{index}]` | `db.password[.{index}]` | 共享或按 index 配置的密码，回退规则与 `user` 相同；该值属于敏感信息。 |
| `nacos.plugin.datasource.db.pool.config.connection-timeout` | `db.pool.config.connectionTimeout` 或对应 kebab-case | Hikari 连接超时，单位毫秒，默认 `3000`。 |
| `nacos.plugin.datasource.db.pool.config.validation-timeout` | `db.pool.config.validationTimeout` 或对应 kebab-case | Hikari 校验超时，单位毫秒，默认 `10000`。 |
| `nacos.plugin.datasource.db.pool.config.idle-timeout` | `db.pool.config.idleTimeout` 或对应 kebab-case | Hikari 空闲超时，单位毫秒，默认 `600000`。 |
| `nacos.plugin.datasource.db.pool.config.maximum-pool-size` | `db.pool.config.maximumPoolSize` 或对应 kebab-case | Hikari 最大连接数，默认 `20`。 |
| `nacos.plugin.datasource.db.pool.config.minimum-idle` | `db.pool.config.minimumIdle` 或对应 kebab-case | Hikari 最小空闲连接数，默认 `2`。 |
| `nacos.plugin.datasource.db.pool.config.driver-class-name` | `db.pool.config.driverClassName` 或对应 kebab-case | JDBC 驱动类；为空时使用 MySQL 驱动兼容默认值。 |
| `nacos.plugin.datasource.db.pool.config.connection-test-query` | `db.pool.config.connectionTestQuery` 或对应 kebab-case | 连接测试 SQL；为空时使用 `SELECT 1`。 |
| `nacos.plugin.datasource.db.query-timeout` | JVM 参数 `QUERYTIMEOUT` | JDBC 查询超时，单位秒，默认 `3`。 |

对每个逻辑配置项，标准 key 的优先级都高于历史 alias，即使二者来自不同 Spring property
source。索引项按 index 独立解析，因此迁移期间可以同时使用标准 `url.0` 和历史 `url.1`。
读取历史配置时会输出迁移 WARN，但日志不得包含配置值。点号和方括号 index 写法都继续
兼容；未携带 index 的单个 `url` 继续兼容 index `0`。

`nacos.plugin.datasource.db.pool.config.{hikari-property}` 会在旧连接池前缀绑定后继续绑定到
Hikari datasource，从而保留已有 Hikari 属性透传能力，并让标准值覆盖同名旧值。当前实现可
接受随附 Hikari 版本提供的 JavaBean 配置面，但只有上表明确列出的稳定子集属于 Nacos 长期
配置契约。

`nacos.plugin.datasource.log.enabled` 仍是独立的数据源日志开关。embedded/external
persistence 模式同样不属于方言私有配置。负责转换加密数据源凭据的 custom environment
插件，需要在自身 `propertyKey()` 中声明新的标准 password key；只声明 `db.password.*` 的
现有实现仍只处理旧格式输入。

## 兼容性规则

数据库插件必须保持 Nacos 表语义、事务预期、分页顺序和乐观更新行为。方言插件不得改变逻辑
schema 或 [资源模型](../design/resource-model-spec.md)。

实现必须：

- 保持逻辑表名和列语义稳定；
- 运行时值使用占位符 SQL；
- 同一查询顺序下保持分页确定性；
- 保持 repository 期望的生成主键行为；
- 通过 `getFunction(functionName)` 隐藏 SQL 函数差异；
- 记录数据库版本要求和迁移要求。
