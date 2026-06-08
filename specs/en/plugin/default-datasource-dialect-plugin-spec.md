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

# Default Data Source Dialect Implementation Spec

## Scope

The built-in data source dialect implementations live under
`plugin-default-impl/nacos-default-datasource-plugin`. They provide the database
dialects and table mappers bundled with the Nacos server distribution. They are
the bundled implementations of the
[Data Source Dialect Plugin Spec](datasource-dialect-plugin-spec.md).

The default implementation set is a persistence compatibility layer. It must
not introduce database-specific resource semantics.

## Built-In Database Types

The current implementation packages these database families:

| Database type | Dialect provider | Mapper package |
|---------------|------------------|----------------|
| `derby` | `DerbyDatabaseDialect` | `impl.derby` |
| `mysql` | `MysqlDatabaseDialect` and `DefaultDatabaseDialect` | `impl.mysql` |
| `postgresql` | `PostgresqlDatabaseDialect` | `impl.postgresql` |
| `oracle` | `OracleDatabaseDialect` | `impl.oracle` |

Each database package must register both
`com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect` and
`com.alibaba.nacos.plugin.datasource.mapper.Mapper` SPI files.

## Mapper Coverage

Each built-in database family is expected to provide mapper implementations for:

- current config tables: `config_info`, `config_info_gray`,
  `config_tags_relation`, `his_config_info`;
- capacity and namespace tables: `tenant_info`, `tenant_capacity`,
  `group_capacity`;
- AI registry tables: AI resource metadata and AI resource versions.

Starting with the Nacos 3.3 line, built-in database families are not required to
provide runtime Config migration mappers for default-namespace storage
duplicates or legacy beta/tag gray tables. Deployments that still carry
pre-3.0 `config_info_beta` or `config_info_tag` data must migrate those rows
before upgrading to a 3.3 server that relies on the current mapper set.

If a future Nacos version adds a persistent table, the built-in database
families must add the corresponding mapper before that table becomes a
documented server feature.

## Selection

Nacos selects the database type from `spring.sql.init.platform`, with legacy
compatibility for `spring.datasource.platform`.

When the selected type is `mysql`, MySQL mappers and MySQL-compatible default
dialect behavior are used. When the selected type is `derby`, Derby remains the
embedded default for standalone development and local testing.

## Compatibility

Built-in implementations must:

- preserve Nacos logical schema across database families;
- keep SQL placeholders and parameter order compatible with repository code;
- preserve generated key columns expected by insert operations;
- keep AI resource mapper behavior aligned with the
  [resource model](../design/resource-model-spec.md);
- add migration notes when a database family requires schema changes.

Built-in database dialect plugins are critical while the server is using them.
They cannot be disabled through plugin state without breaking persistence.
