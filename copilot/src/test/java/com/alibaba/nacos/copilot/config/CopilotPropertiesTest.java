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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for CopilotProperties.
 *
 * @author nacos
 */
class CopilotPropertiesTest {
    
    @Test
    void testDefaultValues() {
        // When
        CopilotProperties properties = new CopilotProperties();
        
        // Then
        assertTrue(properties.isEnabled());
        assertEquals("public", properties.getDefaultNamespace());
        assertEquals("qwen-turbo", properties.getModel());
        assertEquals("NacosCopilot", properties.getStudioProject());
    }
    
    @Test
    void testEnabled() {
        // Given
        CopilotProperties properties = new CopilotProperties();
        
        // When
        properties.setEnabled(false);
        
        // Then
        assertFalse(properties.isEnabled());
        
        // When
        properties.setEnabled(true);
        
        // Then
        assertTrue(properties.isEnabled());
    }
    
    @Test
    void testDefaultNamespace() {
        // Given
        CopilotProperties properties = new CopilotProperties();
        
        // When
        properties.setDefaultNamespace("test-namespace");
        
        // Then
        assertEquals("test-namespace", properties.getDefaultNamespace());
    }
    
    @Test
    void testApiKey() {
        // Given
        CopilotProperties properties = new CopilotProperties();
        
        // When
        properties.setApiKey("test-api-key");
        
        // Then
        assertEquals("test-api-key", properties.getApiKey());
    }
    
    @Test
    void testModel() {
        // Given
        CopilotProperties properties = new CopilotProperties();
        
        // When
        properties.setModel("gpt-4");
        
        // Then
        assertEquals("gpt-4", properties.getModel());
    }
    
    @Test
    void testStudioUrl() {
        // Given
        CopilotProperties properties = new CopilotProperties();
        
        // When
        properties.setStudioUrl("http://localhost:8080");
        
        // Then
        assertEquals("http://localhost:8080", properties.getStudioUrl());
    }
    
    @Test
    void testStudioProject() {
        // Given
        CopilotProperties properties = new CopilotProperties();
        
        // When
        properties.setStudioProject("TestProject");
        
        // Then
        assertEquals("TestProject", properties.getStudioProject());
    }
}
