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

# Data Source Dialect Plugin Spec

## Scope

The data source dialect plugin type isolates database-specific SQL behavior from
Nacos persistence logic. It covers SQL dialect functions, pagination, generated
primary keys, and mapper implementations for Nacos tables.

This is an exclusive-selection plugin. The active dialect is selected by the SQL
platform configuration, currently `spring.sql.init.platform` with legacy
compatibility for `spring.datasource.platform`. Common lifecycle and state
rules are defined by the [Nacos Plugin Spec](plugin-spec.md), and bundled
database families are defined by the
[Default Data Source Dialect Implementation Spec](default-datasource-dialect-plugin-spec.md).

The plugin exists because Nacos persistence should keep one logical schema and
one repository contract while allowing different database dialects. A dialect
plugin is not a persistence domain owner; it translates the repository contract
into database-specific SQL. The persistence and dump boundary is defined by the
[Persistence And Dump Spec](../design/foundation-persistence-dump-spec.md).

Domain modules may still own concrete persistence implementations because stored
records usually carry domain semantics. For example, Config repository services
own Config publish, history, gray release, and capacity semantics, while this
plugin only supplies the database-specific SQL dialect and mapper layer used by
those repositories.

## Concepts

| Concept | Meaning |
|---------|---------|
| SQL platform | Deployment-selected database type, such as `derby`, `mysql`, `postgresql`, or `oracle`. |
| Dialect | Database-level SQL behavior such as pagination, generated keys, and functions. |
| Mapper | Table-level SQL provider for one logical Nacos table and one database type. |
| Logical schema | Nacos table and column semantics shared by all databases. |

Repository implementations choose logical operations and invoke mappers where
needed. Mappers must not decide resource identity, authorization, compatibility
policy, or user-visible domain behavior.

The SQL platform must select both a `DatabaseDialect` and the mapper set for the
same database family. Mixing a dialect from one database with mappers from
another database is invalid.

## SPI

Dialect implementations provide `DatabaseDialect`.

| Method | Requirement |
|--------|-------------|
| `getType()` | Stable database type, such as `derby`, `mysql`, `postgresql`, or `oracle`. |
| `getLimitTopSqlWithMark(sql)` | Add placeholder-based top limit SQL. |
| `getLimitPageSqlWithMark(sql)` | Add placeholder-based page SQL. |
| `getLimitPageSql(sql, pageNo, pageSize)` | Add page SQL with numeric values. |
| `getLimitPageSqlWithOffset(sql, startOffset, pageSize)` | Add offset page SQL. |
| `getPagePrevNum(page, pageSize)` | Return first pagination parameter. |
| `getPageLastNum(page, pageSize)` | Return second pagination parameter. |
| `getReturnPrimaryKeys()` | Return generated key columns. |
| `getFunction(functionName)` | Map logical function names to dialect SQL functions. |

Table mapper plugins implement `com.alibaba.nacos.plugin.datasource.mapper.Mapper`
for table-specific SQL. Dialect and mapper implementations must be packaged and
loaded together for a database family.

Mapper implementations must provide base CRUD SQL and table-specific SQL for
repository operations. Current mapper families cover:

- current config data, gray data, tags, and history;
- namespace and capacity records;
- AI resource metadata and version records.

Starting with the Nacos 3.3 line, datasource dialect plugins are not expected to
provide runtime Config migration queries for empty-tenant/default-namespace
duplicates or legacy beta/tag gray tables. Such migration, if needed for a
pre-3.0 deployment, is an upgrade prerequisite rather than a server runtime
mapper responsibility.

`MapperManager` loads mapper SPI implementations and indexes them by
`dataSource + tableName`. Missing data source or table mapper is a startup or
operation error, not an empty result.

## Selection And State

The core plugin manager exposes this plugin type as `datasource-dialect`.
Only the configured dialect should be enabled by default. Built-in critical
dialects required by the server cannot be disabled while in use.

If a requested dialect is disabled, startup or persistence operations must fail
explicitly. If the requested dialect is missing, the current manager searches for
another enabled dialect and logs the fallback. This fallback is compatibility
behavior; new deployments should configure an explicit supported SQL platform.

Current `DatabaseDialectManager` checks unified plugin state for
`datasource-dialect:{databaseType}` before returning a dialect. A disabled
dialect must not participate in persistence operations.

## Configuration

The SQL platform is selected by:

```properties
spring.sql.init.platform=${databaseType}
```

For compatibility with older deployments:

```properties
spring.datasource.platform=${databaseType}
```

Datasource connection properties remain owned by Nacos persistence configuration
and the database driver. The dialect plugin must not reinterpret unrelated
database connection settings.

## Compatibility Rules

Database plugins must preserve Nacos table semantics, transaction expectations,
pagination order, and optimistic update behavior. A dialect plugin must not
change the logical schema or [resource model](../design/resource-model-spec.md).

Implementations must:

- keep logical table names and column semantics stable;
- use placeholder-based SQL for runtime values;
- keep pagination deterministic for the same query order;
- preserve generated primary key behavior expected by repositories;
- keep SQL function names behind `getFunction(functionName)`;
- document database version requirements and migration requirements.
