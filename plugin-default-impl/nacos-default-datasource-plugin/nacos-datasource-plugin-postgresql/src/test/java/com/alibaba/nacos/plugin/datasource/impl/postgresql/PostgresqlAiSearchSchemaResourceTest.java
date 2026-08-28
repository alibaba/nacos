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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresqlAiSearchSchemaResourceTest {
    
    @Test
    void testMainSchemaDoesNotRequirePgvector() throws IOException {
        String mainSchema = readSchema("META-INF/pg-schema.sql");
        
        assertTrue(mainSchema.contains("CREATE TABLE \"ai_resource_search_document\""));
        assertTrue(mainSchema.contains("CREATE TABLE \"ai_resource_search_chunk\""));
        assertTrue(mainSchema.contains("CREATE TABLE \"ai_resource_task\""));
        assertTrue(mainSchema.contains("\"task_type\" varchar(64)"));
        assertTrue(mainSchema.contains("\"task_stage\" varchar(32)"));
        assertTrue(mainSchema.contains("\"task_payload\" text"));
        assertTrue(mainSchema.contains("\"task_result\" text"));
        assertTrue(mainSchema.contains("\"retry_count\" int4"));
        assertTrue(mainSchema.contains("\"lease_token\" int8"));
        assertTrue(mainSchema.contains("\"next_execute_at\" int8"));
        assertTrue(mainSchema.contains("\"lease_expire_at\" int8"));
        assertFalse(mainSchema.contains("\"next_execute_time\" timestamp(6)"));
        assertFalse(mainSchema.contains("\"lease_until\" timestamp(6)"));
        assertFalse(mainSchema.contains("\"resource_type\" varchar(32) NOT NULL,"
            + System.lineSeparator() + "  \"resource_name\" varchar(256) NOT NULL,"
            + System.lineSeparator() + "  \"task_stage\" varchar(32)"));
        assertFalse(mainSchema.contains("CREATE EXTENSION IF NOT EXISTS vector"));
        assertFalse(mainSchema.contains("\"ai_resource_search_embedding_pg\""));
        assertFalse(mainSchema.contains("\"embedding\" vector"));
        assertTrue(mainSchema.contains("CREATE INDEX \"idx_search_document_type_status\" "
            + "ON \"ai_resource_search_document\" USING btree ("
            + System.lineSeparator() + "  \"namespace_id\"," + System.lineSeparator()
            + "  \"resource_type\"," + System.lineSeparator() + "  \"status\","
            + System.lineSeparator() + "  \"resource_name\"," + System.lineSeparator()
            + "  \"id\"" + System.lineSeparator() + ");"));
    }
    
    private String readSchema(String resource) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(inputStream);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
