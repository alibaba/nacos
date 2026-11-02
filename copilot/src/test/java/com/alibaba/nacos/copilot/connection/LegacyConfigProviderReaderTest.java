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

package com.alibaba.nacos.copilot.connection;

import com.alibaba.nacos.copilot.config.CopilotConfigStorage;
import com.alibaba.nacos.copilot.config.CopilotProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Test for LegacyConfigProviderReader.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class LegacyConfigProviderReaderTest {
    
    @Mock
    private CopilotConfigStorage configStorage;
    
    @Mock
    private CopilotProperties defaultProperties;
    
    @Mock
    private Environment environment;
    
    private LegacyConfigProviderReader reader;
    
    @BeforeEach
    void setUp() {
        reader = new LegacyConfigProviderReader(configStorage, defaultProperties, environment);
    }
    
    @Test
    void testOrder() {
        assertEquals(100, reader.order());
    }
    
    @Test
    void testReadUsesDefaultProperties() {
        when(configStorage.isAvailable()).thenReturn(false);
        when(defaultProperties.getModel()).thenReturn("qwen-turbo");
        when(defaultProperties.getApiKey()).thenReturn("config-key");
        when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn(null);
        
        List<CopilotModelConnection> connections = reader.read();
        
        assertEquals(1, connections.size());
        CopilotModelConnection connection = connections.get(0);
        assertEquals(LegacyConfigProviderReader.CONNECTION_NAME, connection.getName());
        assertEquals("dashscope:qwen-turbo", connection.getModelId());
        assertEquals("config-key", connection.getApiKey());
        assertEquals("Legacy DashScope", connection.getDisplayName());
        assertTrue(connection.isEnabled());
        assertTrue(connection.getStream());
        assertTrue(connection.getEnableThinking());
    }
    
    @Test
    void testReadPrefersEnvironmentApiKey() {
        when(configStorage.isAvailable()).thenReturn(false);
        when(defaultProperties.getModel()).thenReturn("qwen-turbo");
        when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn("env-key");
        
        CopilotModelConnection connection = reader.read().get(0);
        
        assertEquals("env-key", connection.getApiKey());
    }
    
    @Test
    void testReadUsesStoredConfig() {
        CopilotProperties stored = new CopilotProperties();
        stored.setModel("qwen-max");
        stored.setApiKey("stored-key");
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getConfig()).thenReturn(stored);
        when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn(null);
        
        CopilotModelConnection connection = reader.read().get(0);
        
        assertEquals("dashscope:qwen-max", connection.getModelId());
        assertEquals("stored-key", connection.getApiKey());
    }
    
    @Test
    void testReadFallsBackWhenStoredConfigMissing() {
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getConfig()).thenReturn(null);
        when(defaultProperties.getModel()).thenReturn("qwen-plus");
        when(defaultProperties.getApiKey()).thenReturn("config-key");
        when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn(null);
        
        CopilotModelConnection connection = reader.read().get(0);
        
        assertEquals("dashscope:qwen-plus", connection.getModelId());
        assertEquals("config-key", connection.getApiKey());
    }
}
