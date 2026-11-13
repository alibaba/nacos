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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.copilot.connection.ConnectionSource;
import com.alibaba.nacos.copilot.connection.ConnectionTable;
import com.alibaba.nacos.copilot.connection.CopilotConnectionInfo;
import com.alibaba.nacos.copilot.connection.CopilotConnectionStatus;
import com.alibaba.nacos.copilot.connection.CopilotConnectionValidator;
import com.alibaba.nacos.copilot.connection.CopilotModelConnection;
import com.alibaba.nacos.copilot.connection.CopilotModelResolver;
import com.alibaba.nacos.copilot.connection.CopilotModelRouter;
import com.alibaba.nacos.copilot.connection.DefaultCopilotModelRouter;
import com.alibaba.nacos.copilot.connection.DeploymentConfigProviderReader;
import com.alibaba.nacos.copilot.connection.LegacyConfigProviderReader;
import com.alibaba.nacos.copilot.connection.ProviderReaderAggregator;
import com.alibaba.nacos.copilot.connection.ProviderReadContext;
import com.alibaba.nacos.copilot.connection.SelectionSource;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.studio.StudioManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
    
    private final AtomicBoolean studioInitialized = new AtomicBoolean();
    
    @BeforeEach
    void setUp() {
        copilotAgentManager = new CopilotAgentManager(configStorage, defaultProperties,
            providerReaders, modelResolver, modelRouter, new MockEnvironment());
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
            providerReaders, modelResolver, new DefaultCopilotModelRouter(),
            new MockEnvironment());
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
    void testGetConnectionStatusReturnsSanitizedConnections() {
        // Given
        CopilotModelConnection connection = buildConnection("super-secret-key");
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(tableOf(connection));
        when(defaultProperties.getSelectedConnection()).thenReturn("legacy");
        when(defaultProperties.isEnabled()).thenReturn(true);
        when(modelRouter.route(any(), eq("legacy"))).thenReturn(connection);
        copilotAgentManager.refreshConfig();
        
        // When
        CopilotConnectionStatus status = copilotAgentManager.getConnectionStatus();
        
        // Then
        assertEquals(1, status.getConnections().size());
        CopilotConnectionInfo info = status.getConnections().get(0);
        assertEquals("legacy", info.getName());
        assertEquals("dashscope", info.getProviderId());
        assertEquals("qwen-plus", info.getModel());
        assertEquals(ConnectionSource.LEGACY, info.getSource());
        assertTrue(info.isEnabled());
        assertTrue(info.isAvailable());
        assertNull(info.getUnavailableReason());
        assertTrue(info.isApiKeyConfigured());
        assertEquals("legacy", status.getActiveConnection());
        assertFalse(JacksonUtils.toJson(status).contains("super-secret-key"));
    }
    
    @Test
    void testGetConnectionStatusReportsNoActiveConnection() {
        // Given
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        copilotAgentManager.refreshConfig();
        
        // When
        CopilotConnectionStatus status = copilotAgentManager.getConnectionStatus();
        
        // Then
        assertTrue(status.getConnections().isEmpty());
        assertNull(status.getActiveConnection());
    }
    
    @Test
    void testGetConnectionStatusReportsDeploymentSelection() {
        CopilotProperties stored = new CopilotProperties();
        stored.setSelectedConnection("alpha");
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.selectedConnection", "beta");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage, defaultProperties,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getConfig()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        CopilotConnectionStatus status = manager.getConnectionStatus();
        
        assertEquals("beta", status.getEffectiveSelectedConnection());
        assertEquals(SelectionSource.DEPLOYMENT, status.getSelectionSource());
    }
    
    @Test
    void testRefreshPassesDeploymentApiKeyOriginToReaders() {
        CopilotProperties stored = new CopilotProperties();
        stored.setApiKey("console-key");
        stored.setApiKeyCleared(Boolean.TRUE);
        MockEnvironment environment =
            new MockEnvironment().withProperty("nacos.copilot.apiKey", "deploy-key");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage, defaultProperties,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getConfig()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        ArgumentCaptor<ProviderReadContext> captor =
            ArgumentCaptor.forClass(ProviderReadContext.class);
        
        manager.refreshConfig();
        
        verify(providerReaders).readAll(captor.capture());
        assertTrue(captor.getValue().isLegacyApiKeyFromDeployment());
        assertEquals("deploy-key", captor.getValue().getLegacyConfig().getApiKey());
    }
    
    @Test
    void testGetConnectionStatusReportsStoredSelection() {
        CopilotProperties stored = new CopilotProperties();
        stored.setSelectedConnection("alpha");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage, defaultProperties,
            providerReaders, modelResolver, modelRouter, new MockEnvironment());
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getConfig()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        CopilotConnectionStatus status = manager.getConnectionStatus();
        
        assertEquals("alpha", status.getEffectiveSelectedConnection());
        assertEquals(SelectionSource.STORED, status.getSelectionSource());
    }
    
    @Test
    void testGetConnectionStatusReportsNoSelection() {
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        copilotAgentManager.refreshConfig();
        
        CopilotConnectionStatus status = copilotAgentManager.getConnectionStatus();
        
        assertNull(status.getEffectiveSelectedConnection());
        assertEquals(SelectionSource.NONE, status.getSelectionSource());
    }
    
    @Test
    void testGetConnectionStatusKeepsStoredSelectionWhenDeploymentSelectionIsEmpty() {
        CopilotProperties stored = new CopilotProperties();
        stored.setSelectedConnection("beta");
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.selectedConnection", "");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage, defaultProperties,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getConfig()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        CopilotConnectionStatus status = manager.getConnectionStatus();
        
        assertEquals("beta", status.getEffectiveSelectedConnection());
        assertEquals(SelectionSource.STORED, status.getSelectionSource());
    }
    
    @Test
    void testGetConnectionStatusKeepsStoredSelectionWhenDeploymentSelectionIsWhitespace() {
        CopilotProperties stored = new CopilotProperties();
        stored.setSelectedConnection("beta");
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.selectedConnection", "   ");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage, defaultProperties,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getConfig()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        CopilotConnectionStatus status = manager.getConnectionStatus();
        
        assertEquals("beta", status.getEffectiveSelectedConnection());
        assertEquals(SelectionSource.STORED, status.getSelectionSource());
    }
    
    @Test
    void testGetConnectionStatusReturnsNoActiveConnectionWhenDisabled() {
        CopilotProperties stored = new CopilotProperties();
        stored.setEnabled(false);
        stored.setSelectedConnection("prod-qwen");
        CopilotModelConnection connection = buildConnection("test-api-key",
            ConnectionSource.DEPLOYMENT);
        CopilotAgentManager manager = new CopilotAgentManager(configStorage, defaultProperties,
            providerReaders, modelResolver, new DefaultCopilotModelRouter(),
            new MockEnvironment());
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getConfig()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(tableOf(connection));
        
        manager.refreshConfig();
        CopilotConnectionStatus status = manager.getConnectionStatus();
        
        assertNull(status.getActiveConnection());
        assertEquals("prod-qwen", status.getEffectiveSelectedConnection());
        assertEquals(1, status.getConnections().size());
    }
    
    @Test
    void testGetConnectionStatusRoutesWhenEnabled() {
        CopilotProperties stored = new CopilotProperties();
        stored.setEnabled(true);
        stored.setSelectedConnection("prod-qwen");
        CopilotModelConnection connection = buildConnection("test-api-key",
            ConnectionSource.DEPLOYMENT);
        CopilotAgentManager manager = new CopilotAgentManager(configStorage, defaultProperties,
            providerReaders, modelResolver, new DefaultCopilotModelRouter(),
            new MockEnvironment());
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getConfig()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(tableOf(connection));
        
        manager.refreshConfig();
        CopilotConnectionStatus status = manager.getConnectionStatus();
        
        assertEquals("prod-qwen", status.getActiveConnection());
        assertEquals("prod-qwen", status.getEffectiveSelectedConnection());
    }
    
    @Test
    void testRefreshKeepsClearedApiKeyMarker() {
        // Given
        CopilotProperties stored = new CopilotProperties();
        stored.setApiKey("");
        stored.setApiKeyCleared(Boolean.TRUE);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getConfig()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        // When
        copilotAgentManager.refreshConfig();
        
        // Then
        assertEquals(Boolean.TRUE, copilotAgentManager.getConfig().getApiKeyCleared());
        assertEquals("", copilotAgentManager.getConfig().getApiKey());
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
            realReaders, new CopilotModelResolver(), new DefaultCopilotModelRouter(),
            new MockEnvironment());
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
            realReaders, modelResolver, new DefaultCopilotModelRouter(), new MockEnvironment());
        
        realManager.refreshConfig();
        
        verify(configStorage, times(1)).getConfig();
        assertEquals("qwen-max", realManager.getConfig().getModel());
        assertTrue(realManager.isEnabled());
    }
    
    @Test
    void testRefreshRebindsDeploymentConfiguration() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.copilot.selectedConnection", "prod-qwen");
        environment.setProperty("nacos.copilot.connections.prod-qwen.model", "qwen-max");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage, defaultProperties,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        
        assertEquals("prod-qwen", manager.getConfig().getSelectedConnection());
    }
    
    @Test
    void testDeploymentSelectionOverridesStoredConfiguration() {
        CopilotProperties stored = new CopilotProperties();
        stored.setApiKey("old-key");
        stored.setModel("qwen-turbo");
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.selectedConnection", "beta");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage, defaultProperties,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getConfig()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        
        assertEquals("beta", manager.getConfig().getSelectedConnection());
        assertEquals("qwen-turbo", manager.getConfig().getModel());
        assertEquals("old-key", manager.getConfig().getApiKey());
    }
    
    @Test
    void testDeploymentConfigurationOverridesStoredValues() {
        CopilotProperties stored = new CopilotProperties();
        stored.setEnabled(false);
        stored.setDefaultNamespace("legacy-namespace");
        stored.setApiKey("old-key");
        stored.setModel("qwen-turbo");
        stored.setSelectedConnection("stored-choice");
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.enabled", "true")
            .withProperty("nacos.copilot.model", "qwen-max")
            .withProperty("nacos.copilot.selectedConnection", "beta");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage, defaultProperties,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getConfig()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        
        CopilotProperties config = manager.getConfig();
        assertTrue(config.isEnabled());
        assertEquals("qwen-max", config.getModel());
        assertEquals("beta", config.getSelectedConnection());
        assertEquals("old-key", config.getApiKey());
        assertEquals("legacy-namespace", config.getDefaultNamespace());
    }
    
    @Test
    void testDeploymentSelectionChangeAppliesWhileStoredConfigurationExists() {
        CopilotProperties stored = new CopilotProperties();
        stored.setApiKey("old-key");
        stored.setModel("qwen-turbo");
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.selectedConnection", "alpha");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage, defaultProperties,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getConfig()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        assertEquals("alpha", manager.getConfig().getSelectedConnection());
        
        environment.setProperty("nacos.copilot.selectedConnection", "beta");
        manager.refreshConfig();
        
        assertEquals("beta", manager.getConfig().getSelectedConnection());
        verify(configStorage, times(2)).getConfig();
    }
    
    @Test
    void testStoredConfigurationRemainsEffectiveWithoutDeploymentOverrides() {
        CopilotProperties stored = new CopilotProperties();
        stored.setEnabled(false);
        stored.setApiKey("old-key");
        stored.setModel("qwen-turbo");
        stored.setSelectedConnection("stored-choice");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage, defaultProperties,
            providerReaders, modelResolver, modelRouter, new MockEnvironment());
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getConfig()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        
        CopilotProperties config = manager.getConfig();
        assertFalse(config.isEnabled());
        assertEquals("qwen-turbo", config.getModel());
        assertEquals("old-key", config.getApiKey());
        assertEquals("stored-choice", config.getSelectedConnection());
    }
    
    @Test
    void testMixedCaseConnectionAndSelectionRouteEndToEnd() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.Prod-Qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.Prod-Qwen.model", "qwen-max")
            .withProperty("nacos.copilot.connections.Prod-Qwen.apiKey", "secret")
            .withProperty("nacos.copilot.selectedConnection", "PROD-QWEN");
        when(configStorage.isAvailable()).thenReturn(false);
        when(modelResolver.canResolve(any())).thenReturn(true);
        when(modelResolver.resolve(any())).thenReturn(mock(Model.class));
        CopilotConnectionValidator validator = new CopilotConnectionValidator(modelResolver);
        ProviderReaderAggregator realReaders = new ProviderReaderAggregator(
            Arrays.asList(new DeploymentConfigProviderReader(environment),
                new LegacyConfigProviderReader(environment)),
            validator);
        CopilotAgentManager manager = new CopilotAgentManager(configStorage, defaultProperties,
            realReaders, modelResolver, new DefaultCopilotModelRouter(), environment);
        
        manager.refreshConfig();
        
        assertEquals("PROD-QWEN", manager.getConfig().getSelectedConnection());
        assertTrue(manager.isEnabled());
    }
    
    @Test
    void testRefreshWarnsWhenSelectedConnectionIsUnavailable() {
        CopilotModelConnection connection =
            buildConnection("test-api-key", ConnectionSource.DEPLOYMENT)
                .withAvailability(false, "missing provider");
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(tableOf(connection));
        when(defaultProperties.getSelectedConnection()).thenReturn("prod-qwen");
        
        List<ILoggingEvent> warnings = refreshAndCaptureWarnings();
        
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).getFormattedMessage().contains("prod-qwen"));
        assertTrue(warnings.get(0).getFormattedMessage().contains("missing provider"));
    }
    
    @Test
    void testRefreshWarnsWhenSelectedConnectionDoesNotExist() {
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        when(defaultProperties.getSelectedConnection()).thenReturn("unknown");
        
        List<ILoggingEvent> warnings = refreshAndCaptureWarnings();
        
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).getFormattedMessage().contains("unknown"));
    }
    
    @Test
    void testRefreshDoesNotWarnWithoutSelection() {
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        assertTrue(refreshAndCaptureWarnings().isEmpty());
    }
    
    @Test
    void testRefreshWarnsAboutDeprecatedLegacyConnection() {
        CopilotModelConnection legacy = buildConnection("test-api-key");
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(tableOf(legacy));
        
        List<ILoggingEvent> warnings = refreshAndCaptureWarnings();
        
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).getFormattedMessage().contains("legacy"));
        assertTrue(warnings.get(0).getFormattedMessage().contains("nacos.copilot.connections"));
    }
    
    @Test
    void testRefreshDoesNotWarnAboutLegacyForDeploymentConnection() {
        CopilotModelConnection deployment =
            buildConnection("test-api-key", ConnectionSource.DEPLOYMENT);
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(tableOf(deployment));
        
        assertTrue(refreshAndCaptureWarnings().isEmpty());
    }
    
    @Test
    void testInitInitializesStudioOnce() {
        try (MockedStatic<StudioManager> studio = mockStatic(StudioManager.class)) {
            StudioManager.Builder builder = mockStudio(studio);
            when(configStorage.isAvailable()).thenReturn(false);
            when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
            when(defaultProperties.getStudioUrl()).thenReturn("http://studio");
            when(defaultProperties.getStudioProject()).thenReturn("project");
            
            copilotAgentManager.init();
            
            studio.verify(StudioManager::init, times(1));
            verify(builder, times(1)).initialize();
        }
    }
    
    @Test
    void testRefreshKeepsStudioWhenConfigurationIsUnchanged() {
        try (MockedStatic<StudioManager> studio = mockStatic(StudioManager.class)) {
            StudioManager.Builder builder = mockStudio(studio);
            when(configStorage.isAvailable()).thenReturn(false);
            when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
            when(defaultProperties.getStudioUrl()).thenReturn("http://studio");
            when(defaultProperties.getStudioProject()).thenReturn("project");
            
            copilotAgentManager.refreshConfig();
            copilotAgentManager.refreshConfig();
            
            studio.verify(StudioManager::init, times(1));
            verify(builder, times(1)).initialize();
        }
    }
    
    @Test
    void testRefreshReinitializesStudioWhenUrlChanges() {
        try (MockedStatic<StudioManager> studio = mockStatic(StudioManager.class)) {
            StudioManager.Builder builder = mockStudio(studio);
            when(configStorage.isAvailable()).thenReturn(false);
            when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
            when(defaultProperties.getStudioUrl()).thenReturn("http://studio", "http://studio-2");
            when(defaultProperties.getStudioProject()).thenReturn("project");
            
            copilotAgentManager.refreshConfig();
            copilotAgentManager.refreshConfig();
            
            studio.verify(StudioManager::init, times(2));
            verify(builder, times(2)).initialize();
        }
    }
    
    @Test
    void testRefreshRecoversStudioAfterFailedReconfiguration() {
        try (MockedStatic<StudioManager> studio = mockStatic(StudioManager.class)) {
            int[] attempts = {0};
            StudioManager.Builder builder = mockStudio(studio, invocation -> {
                attempts[0]++;
                if (attempts[0] == 2) {
                    // AgentScope calls StudioManager.shutdown() when initialization fails.
                    studioInitialized.set(false);
                    return Mono.error(new IllegalStateException("studio unavailable"));
                }
                studioInitialized.set(true);
                return Mono.empty();
            });
            when(configStorage.isAvailable()).thenReturn(false);
            when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
            when(defaultProperties.getStudioUrl()).thenReturn("http://studio-a",
                "http://studio-b", "http://studio-a");
            when(defaultProperties.getStudioProject()).thenReturn("project");
            
            copilotAgentManager.refreshConfig();
            copilotAgentManager.refreshConfig();
            copilotAgentManager.refreshConfig();
            
            studio.verify(StudioManager::init, times(3));
            verify(builder, times(3)).initialize();
            assertTrue(studioInitialized.get());
        }
    }
    
    @Test
    void testRefreshReinitializesStudioWhenInitializedStateIsCleared() {
        try (MockedStatic<StudioManager> studio = mockStatic(StudioManager.class)) {
            StudioManager.Builder builder = mockStudio(studio);
            when(configStorage.isAvailable()).thenReturn(false);
            when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
            when(defaultProperties.getStudioUrl()).thenReturn("http://studio");
            when(defaultProperties.getStudioProject()).thenReturn("project");
            
            copilotAgentManager.refreshConfig();
            studioInitialized.set(false);
            copilotAgentManager.refreshConfig();
            
            studio.verify(StudioManager::init, times(2));
            verify(builder, times(2)).initialize();
        }
    }
    
    @Test
    void testRefreshRetriesStudioAfterFailureClearsCachedConfiguration() {
        try (MockedStatic<StudioManager> studio = mockStatic(StudioManager.class)) {
            int[] attempts = {0};
            StudioManager.Builder builder = mockStudio(studio, invocation -> {
                attempts[0]++;
                if (attempts[0] == 2) {
                    return Mono.error(new IllegalStateException("studio unavailable"));
                }
                studioInitialized.set(true);
                return Mono.empty();
            });
            when(configStorage.isAvailable()).thenReturn(false);
            when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
            when(defaultProperties.getStudioUrl()).thenReturn("http://studio-a",
                "http://studio-b", "http://studio-a");
            when(defaultProperties.getStudioProject()).thenReturn("project");
            
            copilotAgentManager.refreshConfig();
            copilotAgentManager.refreshConfig();
            copilotAgentManager.refreshConfig();
            
            studio.verify(StudioManager::init, times(3));
            verify(builder, times(3)).initialize();
        }
    }
    
    private List<ILoggingEvent> refreshAndCaptureWarnings() {
        Logger logger = (Logger) LoggerFactory.getLogger(CopilotAgentManager.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            copilotAgentManager.refreshConfig();
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
        return events.list.stream().filter(event -> Level.WARN.equals(event.getLevel()))
            .collect(Collectors.toList());
    }
    
    private StudioManager.Builder mockStudio(MockedStatic<StudioManager> studio) {
        return mockStudio(studio, invocation -> {
            studioInitialized.set(true);
            return Mono.empty();
        });
    }
    
    private StudioManager.Builder mockStudio(MockedStatic<StudioManager> studio,
        Answer<Mono<Void>> initializeAnswer) {
        StudioManager.Builder builder = mock(StudioManager.Builder.class);
        studio.when(StudioManager::init).thenReturn(builder);
        studio.when(StudioManager::isInitialized)
            .thenAnswer(invocation -> studioInitialized.get());
        when(builder.studioUrl(anyString())).thenReturn(builder);
        when(builder.project(anyString())).thenReturn(builder);
        when(builder.runName(anyString())).thenReturn(builder);
        when(builder.initialize()).thenAnswer(initializeAnswer);
        return builder;
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
