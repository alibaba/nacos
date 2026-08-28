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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlAiResourceSearchSchemaResourceTest {
    
    @Test
    void testAiResourceSearchTablesUseCaseSensitiveCollation() throws IOException {
        String schema = readResource("META-INF/mysql-schema.sql");
        assertTrue(schema.contains(
            "COLLATE=utf8mb4_bin COMMENT='AI资源检索文档表'"));
        assertTrue(schema.contains(
            "COLLATE=utf8mb4_bin COMMENT='AI资源检索分片表'"));
        assertTrue(schema.contains(
            "COLLATE=utf8mb4_bin COMMENT='AI资源持久化异步任务表'"));
        assertTrue(schema.contains("KEY `idx_search_document_type_status` "
            + "(`namespace_id`,`resource_type`,`status`,`resource_name`,`id`)"));
    }
    
    private String readResource(String resourceName) throws IOException {
        try (InputStream inputStream =
            getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(inputStream);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
