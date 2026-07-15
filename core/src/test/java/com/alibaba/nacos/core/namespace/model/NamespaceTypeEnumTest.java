/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.namespace.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NamespaceTypeEnumTest {
    
    @Test
    void testValues() {
        NamespaceTypeEnum[] values = NamespaceTypeEnum.values();
        assertNotNull(values);
        assertEquals(3, values.length);
        assertEquals(NamespaceTypeEnum.GLOBAL, values[0]);
        assertEquals(NamespaceTypeEnum.CUSTOM, values[1]);
        assertEquals(NamespaceTypeEnum.AI_MCP, values[2]);
    }
    
    @Test
    void testGlobal() {
        assertEquals(0, NamespaceTypeEnum.GLOBAL.getType());
        assertEquals("Global configuration", NamespaceTypeEnum.GLOBAL.getDescription());
    }
    
    @Test
    void testCustom() {
        assertEquals(1, NamespaceTypeEnum.CUSTOM.getType());
        assertEquals("Custom namespace for naming and config",
            NamespaceTypeEnum.CUSTOM.getDescription());
    }
    
    @Test
    void testAiMcp() {
        assertEquals(2, NamespaceTypeEnum.AI_MCP.getType());
        assertEquals("Default private namespace", NamespaceTypeEnum.AI_MCP.getDescription());
    }
    
    @Test
    void testValueOf() {
        assertEquals(NamespaceTypeEnum.GLOBAL, NamespaceTypeEnum.valueOf("GLOBAL"));
        assertEquals(NamespaceTypeEnum.CUSTOM, NamespaceTypeEnum.valueOf("CUSTOM"));
        assertEquals(NamespaceTypeEnum.AI_MCP, NamespaceTypeEnum.valueOf("AI_MCP"));
    }
}
