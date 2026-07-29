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

package com.alibaba.nacos.plugin.datasource.impl.mysql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlVisibilityPermissionSchemaResourceTest {
    
    @Test
    void testPermissionResourceColumnSupportsCanonicalVisibilityResource() throws IOException {
        String schema = readResource("META-INF/mysql-schema.sql");
        assertTrue(schema.contains(
            "`resource` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL"));
        assertTrue(schema.contains("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC"));
        assertFalse(schema.contains("idx_permission_resource"));
        assertFalse(schema.contains("idx_role_user"));
    }
    
    @Test
    void testPermissionResourceUpgradeScriptDocumentsIndexPreChecks() throws IOException {
        String sql = readResource("META-INF/mysql-upgrade-visibility-permission-resource.sql");
        assertTrue(sql.contains("SELECT VERSION();"));
        assertTrue(sql.contains("SHOW VARIABLES LIKE 'innodb_page_size';"));
        assertTrue(sql.contains("SHOW VARIABLES LIKE 'innodb_default_row_format';"));
        assertTrue(sql.contains("SHOW CREATE TABLE permissions;"));
        assertTrue(sql.contains("Configure a compatible InnoDB mode before applying"));
        assertTrue(sql.contains("ROW_FORMAT=DYNAMIC"));
        assertTrue(sql.contains(
            "MODIFY COLUMN resource VARCHAR(512) CHARACTER SET utf8mb4 "
                + "COLLATE utf8mb4_bin NOT NULL"));
        assertFalse(sql.contains("idx_permission_resource"));
    }
    
    private String readResource(String resourceName) throws IOException {
        try (InputStream inputStream =
            getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(inputStream);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
