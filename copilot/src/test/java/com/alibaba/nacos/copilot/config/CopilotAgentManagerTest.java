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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Test for CopilotAgentManager.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class CopilotAgentManagerTest {
    
    @Mock
    private CopilotConfigStorage configStorage;
    
    @Mock
    private CopilotProperties defaultProperties;
    
    @Mock
    private Environment environment;
    
    private CopilotAgentManager copilotAgentManager;
    
    @BeforeEach
    void setUp() {
        copilotAgentManager =
            new CopilotAgentManager(configStorage, defaultProperties, environment);
    }
    
    @Test
    void testIsEnabledWhenDisabled() {
        // Given
        when(configStorage.isAvailable()).thenReturn(false);
        when(defaultProperties.isEnabled()).thenReturn(false);
        copilotAgentManager.refreshConfig();
        
        // When
        boolean result = copilotAgentManager.isEnabled();
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void testIsEnabledWhenNoApiKey() {
        // Given
        when(configStorage.isAvailable()).thenReturn(false);
        when(defaultProperties.isEnabled()).thenReturn(true);
        when(defaultProperties.getApiKey()).thenReturn(null);
        when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn(null);
        copilotAgentManager.refreshConfig();
        
        // When
        boolean result = copilotAgentManager.isEnabled();
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void testIsEnabledWhenApiKeyFromEnvironment() {
        // Given
        when(configStorage.isAvailable()).thenReturn(false);
        when(defaultProperties.isEnabled()).thenReturn(true);
        when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn("test-api-key");
        copilotAgentManager.refreshConfig();
        
        // When
        boolean result = copilotAgentManager.isEnabled();
        
        // Then
        assertTrue(result);
    }
    
    @Test
    void testIsEnabledWhenApiKeyFromConfig() {
        // Given
        when(configStorage.isAvailable()).thenReturn(false);
        when(defaultProperties.isEnabled()).thenReturn(true);
        when(defaultProperties.getApiKey()).thenReturn("config-api-key");
        when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn(null);
        copilotAgentManager.refreshConfig();
        
        // When
        boolean result = copilotAgentManager.isEnabled();
        
        // Then
        assertTrue(result);
    }
    
    @Test
    void testCreateAgentWhenDisabled() {
        // Given
        when(configStorage.isAvailable()).thenReturn(false);
        when(defaultProperties.isEnabled()).thenReturn(false);
        copilotAgentManager.refreshConfig();
        
        // When
        io.agentscope.core.ReActAgent agent = copilotAgentManager.createAgent("test prompt");
        
        // Then
        assertNull(agent);
    }
    
    @Test
    void testCreateAgentWhenNoApiKey() {
        // Given
        when(configStorage.isAvailable()).thenReturn(false);
        when(defaultProperties.isEnabled()).thenReturn(true);
        when(defaultProperties.getApiKey()).thenReturn(null);
        when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn(null);
        copilotAgentManager.refreshConfig();
        
        // When
        io.agentscope.core.ReActAgent agent = copilotAgentManager.createAgent("test prompt");
        
        // Then
        assertNull(agent);
    }
    
    @Test
    void testGetConfig() {
        // Given
        when(configStorage.isAvailable()).thenReturn(false);
        copilotAgentManager.refreshConfig();
        
        // When
        CopilotProperties config = copilotAgentManager.getConfig();
        
        // Then
        assertNotNull(config);
    }
    
    @Test
    void testRefreshConfig() {
        // Given
        when(configStorage.isAvailable()).thenReturn(false);
        
        // When
        copilotAgentManager.refreshConfig();
        
        // Then
        assertNotNull(copilotAgentManager.getConfig());
    }
}
