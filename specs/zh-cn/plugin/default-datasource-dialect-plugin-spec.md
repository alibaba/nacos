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

# 默认数据源方言插件实现规范

## 范围

内置数据源方言实现位于 `plugin-default-impl/nacos-default-datasource-plugin`。它提供随
Nacos 服务端发行包一起发布的数据库方言和表级 mapper，是
[数据源方言插件规范](datasource-dialect-plugin-spec.md)的内置实现。

默认实现集合是持久化兼容层，不得引入数据库特有的资源语义。

## 内置数据库类型

当前实现包含以下数据库族：

| 数据库类型 | Dialect provider | Mapper 包 |
|------------|------------------|-----------|
| `derby` | `DerbyDatabaseDialect` | `impl.derby` |
| `mysql` | `MysqlDatabaseDialect` 和 `DefaultDatabaseDialect` | `impl.mysql` |
| `postgresql` | `PostgresqlDatabaseDialect` | `impl.postgresql` |
| `oracle` | `OracleDatabaseDialect` | `impl.oracle` |

每个数据库包必须同时注册
`com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect` 和
`com.alibaba.nacos.plugin.datasource.mapper.Mapper` SPI 文件。

## Mapper 覆盖范围

每个内置数据库族都应提供以下 mapper 实现：

- 当前配置表：`config_info`、`config_info_gray`、`config_tags_relation`、`his_config_info`；
- 容量和命名空间表：`tenant_info`、`tenant_capacity`、`group_capacity`；
- AI Registry 表：AI 资源元数据和 AI 资源版本。

从 Nacos 3.3 版本线开始，内置数据库族不再要求提供默认 namespace 存储重复记录或 legacy
beta/tag 灰度表的运行时 Config 迁移 mapper。仍保留 pre-3.0 `config_info_beta` 或
`config_info_tag` 数据的部署，必须在升级到依赖当前 mapper 集合的 3.3 服务端前完成迁移。

如果未来 Nacos 版本新增持久化表，在该表成为文档化服务端能力之前，内置数据库族必须补充
对应 mapper。

## 选择

Nacos 从 `spring.sql.init.platform` 选择数据库类型，并兼容旧配置
`spring.datasource.platform`。

当选择类型为 `mysql` 时，使用 MySQL mapper 和 MySQL 兼容的默认 dialect 行为。当选择类型
为 `derby` 时，Derby 仍是 standalone 开发和本地测试的嵌入式默认数据库。

## 兼容性

内置实现必须：

- 在不同数据库族之间保持 Nacos 逻辑 schema；
- 保持 SQL 占位符和参数顺序与 repository 代码兼容；
- 保持插入操作期望的生成主键列；
- 保持 AI 资源 mapper 行为与[资源模型](../design/resource-model-spec.md)对齐；
- 当某个数据库族需要 schema 变化时补充迁移说明。

服务端正在使用的内置数据库方言插件属于关键插件，不能通过插件状态禁用，否则会破坏持久化。
