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

package com.alibaba.nacos.plugin.datasource.impl.postgresql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresqlTenantMigrationScriptTest {
    
    @Test
    void testMigrationScriptExistsAndCoversAllTenantTables() throws IOException {
        try (InputStream inputStream =
            getClass().getClassLoader()
                .getResourceAsStream("META-INF/pg-upgrade-null-tenant-id.sql")) {
            assertNotNull(inputStream);
            String sql = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(
                sql.contains("ALTER TABLE config_info ALTER COLUMN tenant_id SET DEFAULT ''"));
            assertTrue(
                sql.contains("ALTER TABLE config_info_gray ALTER COLUMN tenant_id SET DEFAULT ''"));
            assertTrue(sql.contains(
                "ALTER TABLE config_tags_relation ALTER COLUMN tenant_id SET DEFAULT ''"));
            assertTrue(
                sql.contains("ALTER TABLE his_config_info ALTER COLUMN tenant_id SET DEFAULT ''"));
        }
    }
}
