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

package com.alibaba.nacos.persistence.datasource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Derby schema used by persistence tests matches the AI resource task contract.
 *
 * @author nacos
 */
class DerbyAiResourceTaskSchemaTest {
    
    @Test
    void testAiResourceTaskUsesGenericPayloadSchema() throws IOException {
        String schema = readSchema();
        
        assertTrue(schema.contains("CREATE TABLE ai_resource_task"));
        assertTrue(schema.contains("task_type varchar(64)"));
        assertTrue(schema.contains("task_stage varchar(32)"));
        assertTrue(schema.contains("task_payload CLOB"));
        assertTrue(schema.contains("task_result CLOB"));
        assertTrue(schema.contains("retry_count int"));
        assertTrue(schema.contains("lease_token bigint"));
        assertTrue(schema.contains("next_execute_at bigint"));
        assertTrue(schema.contains("lease_expire_at bigint"));
        assertTrue(schema.contains("idx_ai_resource_task_due"));
        assertTrue(schema.contains("idx_ai_resource_task_lease"));
        assertFalse(schema.contains("CREATE TABLE ai_resource_search_index_task"));
        assertFalse(schema.contains("attempt_count"));
        assertFalse(schema.contains("next_retry_time"));
        assertFalse(schema.contains("next_execute_time timestamp"));
        assertFalse(schema.contains("lease_until timestamp"));
    }
    
    private String readSchema() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader()
            .getResourceAsStream("META-INF/derby-schema.sql")) {
            assertNotNull(inputStream);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
