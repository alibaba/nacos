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

这是互斥选择插件。当前活跃方言由 SQL platform 配置选择，目前为
`spring.sql.init.platform`，并兼容旧配置 `spring.datasource.platform`。通用生命周期和
状态规则由 [Nacos 插件化规范](plugin-spec.md) 定义，内置数据库族由
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

核心插件管理器以 `datasource-dialect` 类型暴露该插件。只有配置选中的方言默认启用。服务端
运行所依赖的内置关键方言在使用期间不能被禁用。

如果请求的方言被禁用，启动或持久化操作必须显式失败。如果请求的方言缺失，当前 manager
会查找其他已启用方言并记录 fallback。该 fallback 属于兼容行为；新部署应明确配置受支持的
SQL platform。

当前 `DatabaseDialectManager` 在返回 dialect 前，会检查
`datasource-dialect:{databaseType}` 的统一插件状态。被禁用的 dialect 不得参与持久化
操作。

## 配置

SQL platform 通过以下配置选择：

```properties
spring.sql.init.platform=${databaseType}
```

为了兼容旧部署，也支持：

```properties
spring.datasource.platform=${databaseType}
```

数据源连接属性仍由 Nacos 持久化配置和数据库驱动管理。方言插件不得重新解释无关的数据库
连接配置。

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
