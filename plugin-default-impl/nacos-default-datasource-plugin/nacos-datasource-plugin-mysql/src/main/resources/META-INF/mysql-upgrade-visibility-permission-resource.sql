/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- MySQL upgrade script for deployments that store explicit visibility grants
-- in the default RBAC permissions table.
--
-- IMPORTANT:
-- 1. The visibility plugin stores the original canonical resource identifier in
--    permissions.resource, for example:
--    @@visibility/{namespaceId}/{resourceType}/{resourceName}
-- 2. Review the InnoDB index-length capability before applying this script.
--    The permissions table keeps UNIQUE(role, resource, action), so resource
--    needs an InnoDB row format that supports long utf8mb4 indexes.
-- 3. Configure a compatible InnoDB mode before applying this migration. For
--    example, use a row format such as DYNAMIC and verify that the existing
--    server/page-size combination accepts the enlarged unique index.
-- 4. utf8mb4_bin keeps resource matching exact and case-sensitive.
--
-- Suggested pre-checks:
-- SELECT VERSION();
-- SHOW VARIABLES LIKE 'innodb_page_size';
-- SHOW VARIABLES LIKE 'innodb_default_row_format';
-- SHOW CREATE TABLE permissions;

ALTER TABLE permissions
    ROW_FORMAT=DYNAMIC,
    MODIFY COLUMN resource VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;
