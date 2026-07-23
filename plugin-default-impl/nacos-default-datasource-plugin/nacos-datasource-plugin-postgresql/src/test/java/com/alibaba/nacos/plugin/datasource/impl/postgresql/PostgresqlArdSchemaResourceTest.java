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

class PostgresqlArdSchemaResourceTest {
    
    @Test
    void testPgvectorSchemaIsIndependentFromMainSchema() throws IOException {
        String mainSchema = readSchema("META-INF/pg-schema.sql");
        String vectorSchema = readSchema("META-INF/pg-ard-vector-schema.sql");
        
        assertTrue(mainSchema.contains("CREATE TABLE \"ai_resource_ard_entry\""));
        assertTrue(mainSchema.contains("CREATE TABLE \"ai_resource_ard_chunk\""));
        assertTrue(mainSchema.contains("CREATE TABLE \"ai_resource_ard_index_task\""));
        assertFalse(mainSchema.contains("CREATE EXTENSION IF NOT EXISTS vector"));
        assertFalse(mainSchema.contains("\"ai_resource_ard_embedding_pg\""));
        assertFalse(mainSchema.contains("\"embedding\" vector"));
        
        assertTrue(vectorSchema.contains("CREATE EXTENSION IF NOT EXISTS vector"));
        assertTrue(vectorSchema.contains("CREATE TABLE \"ai_resource_ard_embedding_pg\""));
        assertTrue(vectorSchema.contains("\"embedding\" vector NOT NULL"));
    }
    
    private String readSchema(String resource) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(inputStream);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
