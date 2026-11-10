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

import com.alibaba.nacos.copilot.connection.ConnectionSource;
import com.alibaba.nacos.copilot.connection.ConnectionTable;
import com.alibaba.nacos.copilot.connection.CopilotConnectionValidator;
import com.alibaba.nacos.copilot.connection.CopilotModelConnection;
import com.alibaba.nacos.copilot.connection.CopilotModelResolver;
import com.alibaba.nacos.copilot.connection.CopilotModelRouter;
import com.alibaba.nacos.copilot.connection.DefaultCopilotModelRouter;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    
    @Mock
    private CopilotModelRouter modelRouter;
    
    private CopilotAgentManager copilotAgentManager;
    
    @BeforeEach
    void setUp() {
        copilotAgentManager = new CopilotAgentManager(configStorage, defaultProperties,
            providerReaders, modelResolver, modelRouter);
    }
    
    @Test
    void testIsEnabledWhenDisabled() {
        // Given
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        when(defaultProperties.isEnabled()).thenReturn(false);
        copilotAgentManager.refreshConfig();
        
        // When
        boolean result = copilotAgentManager.isEnabled();
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void testIsEnabledWithoutRoutableConnection() {
        // Given
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        when(defaultProperties.isEnabled()).thenReturn(true);
        when(modelRouter.route(any(), any())).thenReturn(null);
        copilotAgentManager.refreshConfig();
        
        // When
        boolean result = copilotAgentManager.isEnabled();
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void testIsEnabledWithAvailableConnection() {
        // Given
        CopilotModelConnection connection = buildConnection("test-api-key");
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(tableOf(connection));
        when(defaultProperties.isEnabled()).thenReturn(true);
        when(modelRouter.route(any(), any())).thenReturn(connection);
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
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
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
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        when(defaultProperties.isEnabled()).thenReturn(true);
        when(modelRouter.route(any(), any())).thenReturn(null);
        copilotAgentManager.refreshConfig();
        
        // When
        ReActAgent agent = copilotAgentManager.createAgent("test prompt");
        
        // Then
        assertNull(agent);
    }
    
    @Test
    void testCreateAgentAllowsConnectionWithoutApiKey() {
        // Given
        CopilotModelConnection connection = buildConnection(null, ConnectionSource.DEPLOYMENT);
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(tableOf(connection));
        when(defaultProperties.isEnabled()).thenReturn(true);
        when(modelRouter.route(any(), any())).thenReturn(connection);
        when(modelResolver.resolve(connection)).thenReturn(mockModel());
        copilotAgentManager.refreshConfig();
        
        // When
        ReActAgent agent = copilotAgentManager.createAgent("test prompt");
        
        // Then
        assertNotNull(agent);
        verify(modelResolver).resolve(connection);
    }
    
    @Test
    void testCreateAgentSkipsUnavailableConnection() {
        // Given
        CopilotModelConnection connection = buildConnection("test-api-key")
            .withAvailability(false, "missing provider");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage, defaultProperties,
            providerReaders, modelResolver, new DefaultCopilotModelRouter());
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(tableOf(connection));
        when(defaultProperties.isEnabled()).thenReturn(true);
        manager.refreshConfig();
        
        // When
        ReActAgent agent = manager.createAgent("test prompt");
        
        // Then
        assertNull(agent);
        verifyNoInteractions(modelResolver);
    }
    
    @Test
    void testCreateAgentUsesRoutedConnection() {
        // Given
        CopilotModelConnection connection = buildConnection("test-api-key");
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(tableOf(connection));
        when(defaultProperties.isEnabled()).thenReturn(true);
        when(defaultProperties.getSelectedConnection()).thenReturn("selected");
        when(modelRouter.route(any(), eq("selected"))).thenReturn(connection);
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
        when(providerReaders.readAll(any())).thenReturn(tableOf(connection));
        when(defaultProperties.isEnabled()).thenReturn(true);
        when(modelRouter.route(any(), any())).thenReturn(connection);
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
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
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
        CopilotConnectionValidator validator = new CopilotConnectionValidator(
            new CopilotModelResolver());
        ProviderReaderAggregator realReaders = new ProviderReaderAggregator(
            Collections.singletonList(new LegacyConfigProviderReader(environment)), validator);
        CopilotAgentManager realManager = new CopilotAgentManager(configStorage, defaultProperties,
            realReaders, new CopilotModelResolver(), new DefaultCopilotModelRouter());
        realManager.refreshConfig();
        
        // When
        ReActAgent agent = realManager.createAgent("test prompt");
        
        // Then
        assertNotNull(agent);
    }
    
    @Test
    void testRefreshReadsStoredConfigurationOnce() {
        CopilotProperties stored = new CopilotProperties();
        stored.setEnabled(true);
        stored.setModel("qwen-max");
        stored.setApiKey("stored-key");
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getConfig()).thenReturn(stored, (CopilotProperties) null);
        when(modelResolver.canResolve(any())).thenReturn(true);
        Environment environment = mock(Environment.class);
        CopilotConnectionValidator validator = new CopilotConnectionValidator(modelResolver);
        ProviderReaderAggregator realReaders = new ProviderReaderAggregator(
            Collections.singletonList(new LegacyConfigProviderReader(environment)), validator);
        CopilotAgentManager realManager = new CopilotAgentManager(configStorage, defaultProperties,
            realReaders, modelResolver, new DefaultCopilotModelRouter());
        
        realManager.refreshConfig();
        
        verify(configStorage, times(1)).getConfig();
        assertEquals("qwen-max", realManager.getConfig().getModel());
        assertTrue(realManager.isEnabled());
    }
    
    private CopilotModelConnection buildConnection(String apiKey) {
        return buildConnection(apiKey, ConnectionSource.LEGACY);
    }
    
    private CopilotModelConnection buildConnection(String apiKey, ConnectionSource source) {
        boolean deployment = ConnectionSource.DEPLOYMENT == source;
        return CopilotModelConnection.builder()
            .name(deployment ? "prod-qwen" : "legacy")
            .providerId("dashscope")
            .model("qwen-plus")
            .source(source)
            .priority(deployment ? 10 : 100)
            .apiKey(apiKey)
            .stream(Boolean.TRUE)
            .enableThinking(Boolean.TRUE)
            .build();
    }
    
    private ConnectionTable tableOf(CopilotModelConnection connection) {
        return new ConnectionTable(Collections.singletonList(connection));
    }
    
    private Model mockModel() {
        return mock(Model.class);
    }
}
