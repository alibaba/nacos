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

import com.alibaba.nacos.copilot.connection.ConnectionTable;
import com.alibaba.nacos.copilot.connection.CopilotModelConnection;
import com.alibaba.nacos.copilot.connection.CopilotModelResolver;
import com.alibaba.nacos.copilot.connection.LegacyConfigProviderReader;
import com.alibaba.nacos.copilot.connection.ProviderReaderAggregator;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    private ProviderReaderAggregator providerReaders;
    
    @Mock
    private CopilotModelResolver modelResolver;
    
    private CopilotAgentManager copilotAgentManager;
    
    @BeforeEach
    void setUp() {
        copilotAgentManager = new CopilotAgentManager(configStorage, defaultProperties,
            providerReaders, modelResolver);
    }
    
    @Test
    void testIsEnabledWhenDisabled() {
        // Given
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll()).thenReturn(ConnectionTable.empty());
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
        CopilotModelConnection connection = buildConnection(null);
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll()).thenReturn(tableOf(connection));
        when(defaultProperties.isEnabled()).thenReturn(true);
        copilotAgentManager.refreshConfig();
        
        // When
        boolean result = copilotAgentManager.isEnabled();
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void testIsEnabledWhenApiKeyConfigured() {
        // Given
        CopilotModelConnection connection = buildConnection("test-api-key");
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll()).thenReturn(tableOf(connection));
        when(defaultProperties.isEnabled()).thenReturn(true);
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
        when(providerReaders.readAll()).thenReturn(ConnectionTable.empty());
        when(defaultProperties.isEnabled()).thenReturn(false);
        copilotAgentManager.refreshConfig();
        
        // When
        ReActAgent agent = copilotAgentManager.createAgent("test prompt");
        
        // Then
        assertNull(agent);
    }
    
    @Test
    void testCreateAgentWhenNoConnection() {
        // Given
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll()).thenReturn(ConnectionTable.empty());
        when(defaultProperties.isEnabled()).thenReturn(true);
        copilotAgentManager.refreshConfig();
        
        // When
        ReActAgent agent = copilotAgentManager.createAgent("test prompt");
        
        // Then
        assertNull(agent);
    }
    
    @Test
    void testCreateAgentWhenNoApiKey() {
        // Given
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll()).thenReturn(tableOf(buildConnection(null)));
        when(defaultProperties.isEnabled()).thenReturn(true);
        copilotAgentManager.refreshConfig();
        
        // When
        ReActAgent agent = copilotAgentManager.createAgent("test prompt");
        
        // Then
        assertNull(agent);
    }
    
    @Test
    void testCreateAgentUsesDefaultConnection() {
        // Given
        CopilotModelConnection connection = buildConnection("test-api-key");
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll()).thenReturn(tableOf(connection));
        when(defaultProperties.isEnabled()).thenReturn(true);
        when(modelResolver.resolve(connection)).thenReturn(mockModel());
        copilotAgentManager.refreshConfig();
        
        // When
        ReActAgent agent = copilotAgentManager.createAgent("test prompt");
        
        // Then
        assertNotNull(agent);
        verify(modelResolver).resolve(connection);
    }
    
    @Test
    void testCreateAgentWhenResolveFails() {
        // Given
        CopilotModelConnection connection = buildConnection("test-api-key");
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll()).thenReturn(tableOf(connection));
        when(defaultProperties.isEnabled()).thenReturn(true);
        when(modelResolver.resolve(connection))
            .thenThrow(new IllegalArgumentException("no provider"));
        copilotAgentManager.refreshConfig();
        
        // When
        ReActAgent agent = copilotAgentManager.createAgent("test prompt");
        
        // Then
        assertNull(agent);
    }
    
    @Test
    void testGetConfig() {
        // Given
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll()).thenReturn(ConnectionTable.empty());
        copilotAgentManager.refreshConfig();
        
        // When
        CopilotProperties config = copilotAgentManager.getConfig();
        
        // Then
        assertNotNull(config);
    }
    
    @Test
    void testCreateAgentWithRealModelRegistry() {
        // Given
        Environment environment = mock(Environment.class);
        when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn("test-api-key");
        when(configStorage.isAvailable()).thenReturn(false);
        when(defaultProperties.isEnabled()).thenReturn(true);
        when(defaultProperties.getModel()).thenReturn("qwen-plus");
        ProviderReaderAggregator realReaders = new ProviderReaderAggregator(
            Collections.singletonList(
                new LegacyConfigProviderReader(configStorage, defaultProperties, environment)));
        CopilotAgentManager realManager = new CopilotAgentManager(configStorage, defaultProperties,
            realReaders, new CopilotModelResolver());
        realManager.refreshConfig();
        
        // When
        ReActAgent agent = realManager.createAgent("test prompt");
        
        // Then
        assertNotNull(agent);
    }
    
    private CopilotModelConnection buildConnection(String apiKey) {
        CopilotModelConnection connection = new CopilotModelConnection();
        connection.setName("legacy");
        connection.setProviderId("dashscope");
        connection.setModel("qwen-plus");
        connection.setApiKey(apiKey);
        connection.setStream(Boolean.TRUE);
        connection.setEnableThinking(Boolean.TRUE);
        return connection;
    }
    
    private ConnectionTable tableOf(CopilotModelConnection connection) {
        return new ConnectionTable(Collections.singletonList(connection));
    }
    
    private Model mockModel() {
        return mock(Model.class);
    }
}
