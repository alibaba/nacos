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

This is an exclusive-selection plugin. The active dialect is selected at
startup by `nacos.plugin.datasource-dialect.type`;
`spring.sql.init.platform` remains a legacy alias. Common lifecycle and state
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
| `isDuplicateKeyException(throwable)` | Classify whether a datasource throwable is a duplicate unique-key conflict. The default recognizes a Spring `DuplicateKeyException` in the cause chain; dialects may override for driver-specific detection. |

`isDuplicateKeyException(throwable)` is the single entry point config repositories
use to decide whether a failed insert was a duplicate unique-key conflict. The
default implementation walks the throwable cause chain and returns `true` when it
finds Spring's `DuplicateKeyException`, matched by class name so the datasource
plugin modules stay free of a Spring dependency. This reproduces the previous
database-agnostic classification as the safe baseline and deliberately does not
treat a raw vendor SQLState such as `23505` as a duplicate on its own.

Dialects such as PostgreSQL, MySQL, Derby, or Oracle may override this to also
inspect the original driver exception (SQLState or vendor error code) when the
standard Spring exception translation is not precise enough, typically combining
their check with a call to the default via
`DatabaseDialect.super.isDuplicateKeyException(throwable)`. Classification must
remain conservative — non-duplicate integrity failures must not be reported as
duplicates.

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
Only the configured dialect is enabled. The type is critical and must retain
one selected implementation while loaded.

The dialect selector supplies bootstrap selection and requires restart.
Persisted state entries for this exclusive type do not replace the static
selection, and the runtime status API must reject selection changes.

When neither the standard selector nor its legacy alias is configured, the
selection follows the server storage default: standalone mode and cluster mode
with `-DembeddedStorage=true` select `derby`; ordinary cluster mode selects
`mysql`. This implicit selection is also snapshotted at startup.

The persistence subsystem always makes this critical type active. If the requested dialect is
disabled or missing, startup must fail explicitly and identify the selected dialect and selection
property. The server must not continue with another discovered dialect as a fallback.

Current `DatabaseDialectManager` checks unified plugin state for
`datasource-dialect:{databaseType}` before returning a dialect. A disabled
dialect must not participate in persistence operations.

## Configuration

The SQL platform is selected by:

```properties
nacos.plugin.datasource-dialect.type=${databaseType}
```

`spring.sql.init.platform` remains a legacy alias, with the standard key taking
precedence when both are present. The removed `spring.datasource.platform`
property is no longer read.

### Datasource Module Configuration

Datasource connection properties are owned by the Nacos persistence module and
the database driver. They are standardized under the following module prefix:

```text
nacos.plugin.datasource.db.{item}
```

This namespace does not make a database dialect configurable. The built-in
`datasource-dialect:{databaseType}` instances still expose
`configurable=false`, because connection credentials and pool settings belong
to one server datasource rather than to each loaded dialect. These settings are
static, take effect on restart, and are not accepted by the plugin detail/PUT
configuration API. A future management surface must first define one unique
datasource configuration owner instead of copying the same credentials into
every dialect.

The stable datasource module settings are:

| Canonical key or pattern | Legacy alias | Meaning |
|--------------------------|--------------|---------|
| `nacos.plugin.datasource.db.num` | `db.num` | Number of external datasource endpoints. It is required and positive for external storage. |
| `nacos.plugin.datasource.db.url.{index}` | `db.url.{index}` | JDBC URL for every index from `0` to `num - 1`. |
| `nacos.plugin.datasource.db.user[.{index}]` | `db.user[.{index}]` | Shared or per-index username. A missing index falls back to the shared value or index `0`. |
| `nacos.plugin.datasource.db.password[.{index}]` | `db.password[.{index}]` | Shared or per-index password, with the same fallback rule as `user`. This value is sensitive. |
| `nacos.plugin.datasource.db.pool.config.connection-timeout` | `db.pool.config.connectionTimeout` or kebab-case equivalent | Hikari connection timeout in milliseconds; default `3000`. |
| `nacos.plugin.datasource.db.pool.config.validation-timeout` | `db.pool.config.validationTimeout` or kebab-case equivalent | Hikari validation timeout in milliseconds; default `10000`. |
| `nacos.plugin.datasource.db.pool.config.idle-timeout` | `db.pool.config.idleTimeout` or kebab-case equivalent | Hikari idle timeout in milliseconds; default `600000`. |
| `nacos.plugin.datasource.db.pool.config.maximum-pool-size` | `db.pool.config.maximumPoolSize` or kebab-case equivalent | Hikari maximum pool size; default `20`. |
| `nacos.plugin.datasource.db.pool.config.minimum-idle` | `db.pool.config.minimumIdle` or kebab-case equivalent | Hikari minimum idle connections; default `2`. |
| `nacos.plugin.datasource.db.pool.config.driver-class-name` | `db.pool.config.driverClassName` or kebab-case equivalent | JDBC driver class. Blank uses the MySQL driver compatibility default. |
| `nacos.plugin.datasource.db.pool.config.connection-test-query` | `db.pool.config.connectionTestQuery` or kebab-case equivalent | Connection test query. Blank uses `SELECT 1`. |
| `nacos.plugin.datasource.db.query-timeout` | JVM property `QUERYTIMEOUT` | JDBC query timeout in seconds; default `3`. |

For each logical item, the canonical key takes precedence over its legacy alias
even when the two keys come from different Spring property sources. Indexed
items are resolved independently, so a canonical `url.0` may coexist with a
legacy `url.1` during migration. Legacy use emits a migration warning without
logging configuration values. Dotted and bracketed index notation remain
accepted, and a single unindexed `url` remains compatible with index `0`.

The `nacos.plugin.datasource.db.pool.config.{hikari-property}` prefix continues
to bind to the Hikari datasource after the legacy pool prefix is bound. This
preserves existing Hikari pass-through properties while allowing canonical
values to override matching legacy values. The supported implementation surface
is the Hikari JavaBean configuration accepted by the bundled version; only the
stable subset listed above is a long-term Nacos configuration contract.

`nacos.plugin.datasource.log.enabled` remains a separate datasource logging
switch. The embedded/external persistence mode is also outside dialect-private
configuration. A custom environment plugin that transforms encrypted datasource
credentials must declare the canonical password keys in its own `propertyKey()`
set; existing implementations that declare only `db.password.*` continue to
process legacy input only.

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
