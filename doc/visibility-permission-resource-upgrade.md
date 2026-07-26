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

# Visibility Permission Resource Upgrade

This note applies to deployments that upgrade from a schema where
`permissions.resource` is shorter than 512 characters and then use explicit
visibility grants in the default auth plugin.

The visibility plugin stores the original canonical resource identifier in
`permissions.resource`, for example:

```text
@@visibility/{namespaceId}/{resourceType}/{resourceName}
```

Do not translate, escape, hash, or normalize the stored value beyond the
canonical resource construction rules. A hash-based resource key may be added
by a future persistence design, but it is not part of this upgrade.

## Upgrade Scripts

The upgrade scripts are delivered in the final distribution under `conf/`:

| Database | Script |
| --- | --- |
| MySQL | `conf/mysql-upgrade-visibility-permission-resource.sql` |
| Derby | `conf/derby-upgrade-visibility-permission-resource.sql` |
| PostgreSQL | `conf/pg-upgrade-visibility-permission-resource.sql` |
| Oracle | `conf/oracle-upgrade-visibility-permission-resource.sql` |

## MySQL Preflight

MySQL keeps the unique permission key on `(role, resource, action)`. Before
expanding `resource` to `VARCHAR(512)` with `utf8mb4`, verify that the current
InnoDB configuration can support the enlarged unique index.

Run these checks first:

```sql
SELECT VERSION();
SHOW VARIABLES LIKE 'innodb_page_size';
SHOW VARIABLES LIKE 'innodb_default_row_format';
SHOW CREATE TABLE permissions;
```

Configure a compatible storage mode before applying the migration. The provided
script sets `ROW_FORMAT=DYNAMIC` and uses `utf8mb4_bin` so visibility resource
matching stays exact and case-sensitive.

```sql
ALTER TABLE permissions
    ROW_FORMAT=DYNAMIC,
    MODIFY COLUMN resource VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;
```

## Derby

```sql
ALTER TABLE permissions ALTER COLUMN resource SET DATA TYPE VARCHAR(512);
```

## PostgreSQL

```sql
ALTER TABLE permissions ALTER COLUMN resource TYPE VARCHAR(512);
```

## Oracle

```sql
ALTER TABLE permissions MODIFY (resource VARCHAR2(512 CHAR) NOT NULL);
```

The upgrade only expands the raw canonical resource column. Grant-list-only
reverse indexes such as `permissions(resource, action, role)` and
`roles(role, username)` are intentionally not added.
