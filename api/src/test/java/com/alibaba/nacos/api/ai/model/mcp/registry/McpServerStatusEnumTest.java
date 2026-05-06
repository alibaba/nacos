/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.model.mcp.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class McpServerStatusEnumTest {
    
    @Test
    void testStatusValues() {
        assertEquals("active", McpServerStatusEnum.ACTIVE.getName());
        assertEquals("deleted", McpServerStatusEnum.DELETED.getName());
        assertEquals("deprecated", McpServerStatusEnum.DEPRECATED.getName());
    }
    
    @Test
    void testParseStatusActive() {
        McpServerStatusEnum status = McpServerStatusEnum.parseStatus("active");
        assertEquals(McpServerStatusEnum.ACTIVE, status);
    }
    
    @Test
    void testParseStatusDeleted() {
        McpServerStatusEnum status = McpServerStatusEnum.parseStatus("deleted");
        assertEquals(McpServerStatusEnum.DELETED, status);
    }
    
    @Test
    void testParseStatusDeprecated() {
        McpServerStatusEnum status = McpServerStatusEnum.parseStatus("deprecated");
        assertEquals(McpServerStatusEnum.DEPRECATED, status);
    }
    
    @Test
    void testParseStatusInvalid() {
        McpServerStatusEnum status = McpServerStatusEnum.parseStatus("invalid");
        assertNull(status);
    }
    
    @Test
    void testParseStatusNull() {
        McpServerStatusEnum status = McpServerStatusEnum.parseStatus(null);
        assertNull(status);
    }
    
    @Test
    void testParseStatusCaseSensitive() {
        // The parsing should be case-sensitive
        McpServerStatusEnum status = McpServerStatusEnum.parseStatus("ACTIVE");
        assertNull(status);
    }
    
    @Test
    void testAllEnumValues() {
        McpServerStatusEnum[] values = McpServerStatusEnum.values();
        assertEquals(3, values.length);
        
        assertEquals(McpServerStatusEnum.ACTIVE, values[0]);
        assertEquals(McpServerStatusEnum.DELETED, values[1]);
        assertEquals(McpServerStatusEnum.DEPRECATED, values[2]);
    }
    
    @Test
    void testEnumOrdinal() {
        assertEquals(0, McpServerStatusEnum.ACTIVE.ordinal());
        assertEquals(1, McpServerStatusEnum.DELETED.ordinal());
        assertEquals(2, McpServerStatusEnum.DEPRECATED.ordinal());
    }
}
