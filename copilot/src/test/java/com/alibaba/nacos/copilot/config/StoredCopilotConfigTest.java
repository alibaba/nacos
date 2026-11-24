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

package com.alibaba.nacos.copilot.config;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for StoredCopilotConfig.
 *
 * @author nacos
 */
class StoredCopilotConfigTest {
    
    @Test
    void testParseKeepsRawFieldsAndParsedValuesOfTheSameRead() {
        // Given
        String content = "{\"model\":\"qwen-max\",\"defaultNamespace\":\"team\"}";
        
        // When
        StoredCopilotConfig stored = StoredCopilotConfig.parse(content);
        
        // Then
        assertEquals(content, stored.getContent());
        assertEquals("qwen-max", stored.getRaw(StoredCopilotConfig.MODEL));
        assertEquals("team", stored.getProperties().getDefaultNamespace());
    }
    
    @Test
    void testDeclaresDistinguishesAbsentFieldFromExplicitNull() {
        // Given
        StoredCopilotConfig stored = StoredCopilotConfig.parse("{\"apiKey\":null}");
        
        // When / Then
        assertTrue(stored.declares(StoredCopilotConfig.API_KEY));
        assertNull(stored.getRaw(StoredCopilotConfig.API_KEY));
        assertFalse(stored.declares(StoredCopilotConfig.MODEL));
        assertNull(stored.getRaw(StoredCopilotConfig.MODEL));
    }
    
    @Test
    void testEmptyDocumentKeepsDefaultValues() {
        // When
        StoredCopilotConfig stored = StoredCopilotConfig.parse("{}");
        
        // Then
        assertTrue(stored.getFields().isEmpty());
        assertEquals("qwen-turbo", stored.getProperties().getModel());
    }
    
    @Test
    void testFieldsKeepDeclarationOrderAndAreUnmodifiable() {
        // Given
        StoredCopilotConfig stored = StoredCopilotConfig.parse(
            "{\"model\":\"qwen-max\",\"studioProject\":\"Demo\"}");
        
        // When
        Map<String, Object> fields = stored.getFields();
        Iterator<String> names = fields.keySet().iterator();
        
        // Then
        assertEquals(StoredCopilotConfig.MODEL, names.next());
        assertEquals(StoredCopilotConfig.STUDIO_PROJECT, names.next());
        assertThrows(UnsupportedOperationException.class,
            () -> fields.put(StoredCopilotConfig.MODEL, "other"));
    }
}
