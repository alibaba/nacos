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
import com.alibaba.nacos.copilot.connection.CredentialSource;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    
    private MockEnvironment environment;
    
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
        environment = new MockEnvironment();
        copilotAgentManager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, modelRouter, environment);
    }
    
    @Test
    void testIsEnabledWhenDisabled() {
        // Given
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        environment.setProperty("nacos.copilot.enabled", "false");
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
        environment.setProperty("nacos.copilot.enabled", "true");
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
        environment.setProperty("nacos.copilot.enabled", "true");
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
        environment.setProperty("nacos.copilot.enabled", "false");
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
        environment.setProperty("nacos.copilot.enabled", "true");
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
        environment.setProperty("nacos.copilot.enabled", "true");
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
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, new DefaultCopilotModelRouter(),
            new MockEnvironment());
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(tableOf(connection));
        environment.setProperty("nacos.copilot.enabled", "true");
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
        environment.setProperty("nacos.copilot.enabled", "true");
        environment.setProperty("nacos.copilot.selectedConnection", "selected");
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
        environment.setProperty("nacos.copilot.enabled", "true");
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
        environment.setProperty("nacos.copilot.selectedConnection", "legacy");
        environment.setProperty("nacos.copilot.enabled", "true");
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
        StoredCopilotConfig stored = snapshotOf("{\"selectedConnection\":\"alpha\"}");
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.selectedConnection", "beta");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        CopilotConnectionStatus status = manager.getConnectionStatus();
        
        assertEquals("beta", status.getEffectiveSelectedConnection());
        assertEquals(SelectionSource.DEPLOYMENT, status.getSelectionSource());
    }
    
    @Test
    void testRefreshPassesDeploymentApiKeyOriginToReaders() {
        StoredCopilotConfig stored = snapshotOf("{\"apiKey\":\"console-key\"}");
        MockEnvironment environment =
            new MockEnvironment().withProperty("nacos.copilot.apiKey", "deploy-key");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        ArgumentCaptor<ProviderReadContext> captor =
            ArgumentCaptor.forClass(ProviderReadContext.class);
        
        manager.refreshConfig();
        
        verify(providerReaders).readAll(captor.capture());
        assertEquals(CredentialSource.DEPLOYMENT, captor.getValue().getLegacyCredentialSource());
        assertEquals("deploy-key", captor.getValue().getLegacyConfig().getApiKey());
    }
    
    @Test
    void testGetConnectionStatusReportsStoredSelection() {
        StoredCopilotConfig stored = snapshotOf("{\"selectedConnection\":\"alpha\"}");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, modelRouter, new MockEnvironment());
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(stored);
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
    void testGetConnectionStatusClearsStoredSelectionWhenDeploymentSelectionIsEmpty() {
        StoredCopilotConfig stored = snapshotOf("{\"selectedConnection\":\"beta\"}");
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.selectedConnection", "");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        CopilotConnectionStatus status = manager.getConnectionStatus();
        
        assertNull(status.getEffectiveSelectedConnection());
        assertEquals(SelectionSource.NONE, status.getSelectionSource());
    }
    
    @Test
    void testGetConnectionStatusClearsStoredSelectionWhenDeploymentSelectionIsWhitespace() {
        StoredCopilotConfig stored = snapshotOf("{\"selectedConnection\":\"beta\"}");
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.selectedConnection", "   ");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        CopilotConnectionStatus status = manager.getConnectionStatus();
        
        assertNull(status.getEffectiveSelectedConnection());
        assertEquals(SelectionSource.NONE, status.getSelectionSource());
    }
    
    @Test
    void testGetConnectionStatusReturnsNoActiveConnectionWhenDisabled() {
        StoredCopilotConfig stored = snapshotOf(
            "{\"enabled\":false,\"selectedConnection\":\"prod-qwen\"}");
        CopilotModelConnection connection = buildConnection("test-api-key",
            ConnectionSource.DEPLOYMENT);
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, new DefaultCopilotModelRouter(),
            new MockEnvironment());
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(tableOf(connection));
        
        manager.refreshConfig();
        CopilotConnectionStatus status = manager.getConnectionStatus();
        
        assertNull(status.getActiveConnection());
        assertEquals("prod-qwen", status.getEffectiveSelectedConnection());
        assertEquals(1, status.getConnections().size());
    }
    
    @Test
    void testGetConnectionStatusRoutesWhenEnabled() {
        StoredCopilotConfig stored = snapshotOf(
            "{\"enabled\":true,\"selectedConnection\":\"prod-qwen\"}");
        CopilotModelConnection connection = buildConnection("test-api-key",
            ConnectionSource.DEPLOYMENT);
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, new DefaultCopilotModelRouter(),
            new MockEnvironment());
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(tableOf(connection));
        
        manager.refreshConfig();
        CopilotConnectionStatus status = manager.getConnectionStatus();
        
        assertEquals("prod-qwen", status.getActiveConnection());
        assertEquals("prod-qwen", status.getEffectiveSelectedConnection());
    }
    
    @Test
    void testRefreshKeepsClearedApiKeyMarker() {
        // Given
        StoredCopilotConfig stored = snapshotOf("{\"apiKey\":\"\",\"apiKeyCleared\":true}");
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        // When
        copilotAgentManager.refreshConfig();
        
        // Then
        assertEquals(Boolean.TRUE, copilotAgentManager.getConfig().getApiKeyCleared());
        assertNull(copilotAgentManager.getConfig().getApiKey());
    }
    
    @Test
    void testCreateAgentWithRealModelRegistry() {
        // Given
        MockEnvironment environment = new MockEnvironment()
            .withProperty("COPILOT_API_KEY", "test-api-key");
        when(configStorage.isAvailable()).thenReturn(false);
        environment.setProperty("nacos.copilot.enabled", "true");
        CopilotConnectionValidator validator = new CopilotConnectionValidator(
            new CopilotModelResolver());
        ProviderReaderAggregator realReaders = new ProviderReaderAggregator(
            Collections.singletonList(new LegacyConfigProviderReader()), validator);
        CopilotAgentManager realManager = new CopilotAgentManager(configStorage,
            realReaders, new CopilotModelResolver(), new DefaultCopilotModelRouter(), environment);
        realManager.refreshConfig();
        
        // When
        ReActAgent agent = realManager.createAgent("test prompt");
        
        // Then
        assertNotNull(agent);
    }
    
    @Test
    void testRefreshReadsStoredConfigurationOnce() {
        StoredCopilotConfig stored = snapshotOf(
            "{\"enabled\":true,\"model\":\"qwen-max\",\"apiKey\":\"stored-key\"}");
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(stored);
        when(modelResolver.canResolve(any())).thenReturn(true);
        CopilotConnectionValidator validator = new CopilotConnectionValidator(modelResolver);
        ProviderReaderAggregator realReaders = new ProviderReaderAggregator(
            Collections.singletonList(new LegacyConfigProviderReader()), validator);
        CopilotAgentManager realManager = new CopilotAgentManager(configStorage,
            realReaders, modelResolver, new DefaultCopilotModelRouter(), new MockEnvironment());
        
        realManager.refreshConfig();
        
        verify(configStorage, times(1)).getSnapshot();
        assertEquals("qwen-max", realManager.getConfig().getModel());
        assertTrue(realManager.isEnabled());
    }
    
    @Test
    void testRefreshRebindsDeploymentConfiguration() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.copilot.selectedConnection", "prod-qwen");
        environment.setProperty("nacos.copilot.connections.prod-qwen.model", "qwen-max");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        
        assertEquals("prod-qwen", manager.getConfig().getSelectedConnection());
    }
    
    @Test
    void testDeploymentSelectionOverridesStoredConfiguration() {
        StoredCopilotConfig stored = snapshotOf(
            "{\"apiKey\":\"old-key\",\"model\":\"qwen-turbo\"}");
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.selectedConnection", "beta");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        
        assertEquals("beta", manager.getConfig().getSelectedConnection());
        assertEquals("qwen-turbo", manager.getConfig().getModel());
        assertEquals("old-key", manager.getConfig().getApiKey());
    }
    
    @Test
    void testDeploymentConfigurationOverridesStoredValues() {
        StoredCopilotConfig stored = snapshotOf("{\"enabled\":false,"
            + "\"defaultNamespace\":\"legacy-namespace\",\"apiKey\":\"old-key\","
            + "\"model\":\"qwen-turbo\",\"selectedConnection\":\"stored-choice\"}");
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.enabled", "true")
            .withProperty("nacos.copilot.model", "qwen-max")
            .withProperty("nacos.copilot.selectedConnection", "beta");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(stored);
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
        StoredCopilotConfig stored = snapshotOf(
            "{\"apiKey\":\"old-key\",\"model\":\"qwen-turbo\"}");
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.selectedConnection", "alpha");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(stored);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        assertEquals("alpha", manager.getConfig().getSelectedConnection());
        
        environment.setProperty("nacos.copilot.selectedConnection", "beta");
        manager.refreshConfig();
        
        assertEquals("beta", manager.getConfig().getSelectedConnection());
        verify(configStorage, times(2)).getSnapshot();
    }
    
    @Test
    void testStoredConfigurationRemainsEffectiveWithoutDeploymentOverrides() {
        StoredCopilotConfig stored = snapshotOf("{\"enabled\":false,\"apiKey\":\"old-key\","
            + "\"model\":\"qwen-turbo\",\"selectedConnection\":\"stored-choice\"}");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, modelRouter, new MockEnvironment());
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(stored);
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
        environment.setProperty("nacos.copilot.enabled", "true");
        CopilotConnectionValidator validator = new CopilotConnectionValidator(modelResolver);
        ProviderReaderAggregator realReaders = new ProviderReaderAggregator(
            Arrays.asList(new DeploymentConfigProviderReader(),
                new LegacyConfigProviderReader()),
            validator);
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
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
        environment.setProperty("nacos.copilot.selectedConnection", "prod-qwen");
        
        List<ILoggingEvent> warnings = refreshAndCaptureWarnings();
        
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).getFormattedMessage().contains("prod-qwen"));
        assertTrue(warnings.get(0).getFormattedMessage().contains("missing provider"));
    }
    
    @Test
    void testRefreshWarnsWhenSelectedConnectionDoesNotExist() {
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        environment.setProperty("nacos.copilot.selectedConnection", "unknown");
        
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
            environment.setProperty("nacos.copilot.studioUrl", "http://studio");
            environment.setProperty("nacos.copilot.studioProject", "project");
            
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
            environment.setProperty("nacos.copilot.studioUrl", "http://studio");
            environment.setProperty("nacos.copilot.studioProject", "project");
            
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
            environment.setProperty("nacos.copilot.studioUrl", "http://studio");
            environment.setProperty("nacos.copilot.studioProject", "project");
            
            copilotAgentManager.refreshConfig();
            environment.setProperty("nacos.copilot.studioUrl", "http://studio-2");
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
            environment.setProperty("nacos.copilot.studioUrl", "http://studio-a");
            environment.setProperty("nacos.copilot.studioProject", "project");
            
            copilotAgentManager.refreshConfig();
            environment.setProperty("nacos.copilot.studioUrl", "http://studio-b");
            copilotAgentManager.refreshConfig();
            environment.setProperty("nacos.copilot.studioUrl", "http://studio-a");
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
            environment.setProperty("nacos.copilot.studioUrl", "http://studio");
            environment.setProperty("nacos.copilot.studioProject", "project");
            
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
            environment.setProperty("nacos.copilot.studioUrl", "http://studio-a");
            environment.setProperty("nacos.copilot.studioProject", "project");
            
            copilotAgentManager.refreshConfig();
            environment.setProperty("nacos.copilot.studioUrl", "http://studio-b");
            copilotAgentManager.refreshConfig();
            environment.setProperty("nacos.copilot.studioUrl", "http://studio-a");
            copilotAgentManager.refreshConfig();
            
            studio.verify(StudioManager::init, times(3));
            verify(builder, times(3)).initialize();
        }
    }
    
    @Test
    void testStoredConfigurationInheritsDefaultsForAbsentFields() {
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot())
            .thenReturn(snapshotOf("{\"selectedConnection\":\"alpha\"}"));
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        copilotAgentManager.refreshConfig();
        
        assertEquals("qwen-turbo", copilotAgentManager.getConfig().getModel());
        assertEquals("alpha", copilotAgentManager.getConfig().getSelectedConnection());
    }
    
    @Test
    void testStoredNullClearsFieldInsteadOfInheritingDefault() {
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(snapshotOf("{\"model\":null}"));
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        copilotAgentManager.refreshConfig();
        
        assertNull(copilotAgentManager.getConfig().getModel());
    }
    
    @Test
    void testStoredBlankStringClearsFieldInsteadOfInheritingDefault() {
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(
            snapshotOf("{\"studioUrl\":\"   \",\"studioProject\":\"   \"}"));
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        copilotAgentManager.refreshConfig();
        
        assertNull(copilotAgentManager.getConfig().getStudioUrl());
        assertNull(copilotAgentManager.getConfig().getStudioProject());
    }
    
    @Test
    void testStoredFalseIsARealValue() {
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(snapshotOf("{\"enabled\":false}"));
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        environment.setProperty("nacos.copilot.enabled", "true");
        
        copilotAgentManager.refreshConfig();
        
        assertFalse(copilotAgentManager.isEnabled());
    }
    
    @Test
    void testStoredBlankApiKeyBlocksEnvironmentFallback() {
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(snapshotOf("{\"apiKey\":\"\"}"));
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        copilotAgentManager.refreshConfig();
        
        assertNull(copilotAgentManager.getConfig().getApiKey());
        assertEquals(Boolean.TRUE, copilotAgentManager.getConfig().getApiKeyCleared());
    }
    
    @Test
    void testDeploymentBlankApiKeyClearsStoredCredential() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.apiKey", "");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot())
            .thenReturn(snapshotOf("{\"apiKey\":\"console-key\"}"));
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        
        assertNull(manager.getConfig().getApiKey());
        assertEquals(Boolean.TRUE, manager.getConfig().getApiKeyCleared());
    }
    
    @Test
    void testStoredClearDoesNotOverrideDeploymentValue() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.apiKey", "deploy-key");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(snapshotOf("{\"apiKey\":null}"));
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        
        manager.refreshConfig();
        
        assertEquals("deploy-key", manager.getConfig().getApiKey());
        assertNull(manager.getConfig().getApiKeyCleared());
    }
    
    @ParameterizedTest
    @MethodSource("legacyCredentialCases")
    void testLegacyCredentialPrecedence(String storedJson, boolean deploymentDeclared,
        String deploymentKey, String expectedKey, CredentialSource expectedSource) {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("COPILOT_API_KEY", "env-key");
        if (deploymentDeclared) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("nacos.copilot.apiKey", deploymentKey);
            environment.getPropertySources().addFirst(new MapPropertySource("deployment", fields));
        }
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(snapshotOf(storedJson));
        CopilotAgentManager manager = managerWithRealReaders(environment);
        
        manager.refreshConfig();
        
        CopilotConnectionInfo legacy = manager.getConnectionStatus().getConnections().get(0);
        assertEquals(expectedSource, legacy.getActualSource());
        assertEquals(expectedKey != null, legacy.isApiKeyConfigured());
        assertEquals(expectedKey != null, legacy.isAvailable());
        if (expectedKey != null) {
            ArgumentCaptor<CopilotModelConnection> captor =
                ArgumentCaptor.forClass(CopilotModelConnection.class);
            verify(modelResolver).canResolve(captor.capture());
            assertEquals(expectedKey, captor.getValue().getApiKey());
        } else {
            assertEquals(Boolean.TRUE, manager.getConfig().getApiKeyCleared());
            verifyNoInteractions(modelResolver);
        }
    }
    
    private static Stream<Arguments> legacyCredentialCases() {
        return Stream.of(
            Arguments.of("{\"apiKey\":\"stored-key\"}", true, "deploy-key", "deploy-key",
                CredentialSource.DEPLOYMENT),
            Arguments.of("{\"apiKey\":\"stored-key\"}", false, null, "stored-key",
                CredentialSource.CONSOLE),
            Arguments.of("{}", false, null, "env-key", CredentialSource.ENVIRONMENT),
            Arguments.of("{\"apiKey\":\"stored-key\"}", true, "", null, null),
            Arguments.of("{\"apiKey\":\"stored-key\"}", true, "   ", null, null),
            Arguments.of("{\"apiKey\":\"stored-key\"}", true, null, null, null),
            Arguments.of("{\"apiKey\":null}", false, null, null, null),
            Arguments.of("{\"apiKey\":\"\"}", false, null, null, null),
            Arguments.of("{\"apiKey\":\"   \"}", false, null, null, null),
            Arguments.of("{\"apiKeyCleared\":true}", false, null, null, null),
            Arguments.of("{\"apiKey\":null}", true, "deploy-key", "deploy-key",
                CredentialSource.DEPLOYMENT),
            Arguments.of("{\"apiKeyCleared\":true}", true, "deploy-key", "deploy-key",
                CredentialSource.DEPLOYMENT));
    }
    
    @Test
    void testRefreshUsesPureDefaultsAfterCurrentDeclarationsAreRemoved() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.enabled", "false")
            .withProperty("nacos.copilot.model", "old-model")
            .withProperty("nacos.copilot.apiKey", "old-key")
            .withProperty("nacos.copilot.defaultNamespace", "old-namespace")
            .withProperty("nacos.copilot.studioProject", "old-project")
            .withProperty("nacos.copilot.selectedConnection", "old-choice");
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, modelRouter, environment);
        when(configStorage.isAvailable()).thenReturn(false);
        when(providerReaders.readAll(any())).thenReturn(ConnectionTable.empty());
        manager.refreshConfig();
        environment.getPropertySources().remove("mockProperties");
        
        manager.refreshConfig();
        
        CopilotProperties config = manager.getConfig();
        assertTrue(config.isEnabled());
        assertEquals("qwen-turbo", config.getModel());
        assertEquals("public", config.getDefaultNamespace());
        assertEquals("NacosCopilot", config.getStudioProject());
        assertNull(config.getApiKey());
        assertNull(config.getSelectedConnection());
        assertEquals(SelectionSource.NONE, manager.getConnectionStatus().getSelectionSource());
    }
    
    @ParameterizedTest
    @MethodSource("legacyDeclarationCases")
    void testLegacyExistsOnlyForActualDeclarations(String storedJson, String deploymentModel,
        boolean expectedLegacy) {
        MockEnvironment environment = new MockEnvironment();
        if (deploymentModel != null) {
            environment.setProperty("nacos.copilot.model", deploymentModel);
        }
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot()).thenReturn(snapshotOf(storedJson));
        CopilotAgentManager manager = managerWithRealReaders(environment);
        
        List<ILoggingEvent> warnings = refreshAndCaptureWarnings(manager);
        
        assertEquals(expectedLegacy ? 1 : 0, manager.getConnectionStatus().getConnections().size());
        assertEquals(expectedLegacy ? 1 : 0, warnings.size());
        if (expectedLegacy) {
            assertFalse(manager.getConnectionStatus().getConnections().get(0).isAvailable());
        }
        verifyNoInteractions(modelResolver);
    }
    
    private static Stream<Arguments> legacyDeclarationCases() {
        return Stream.of(Arguments.of("{}", null, false),
            Arguments.of("{\"selectedConnection\":null}", null, false),
            Arguments.of("{\"model\":\"qwen-turbo\"}", null, true),
            Arguments.of("{}", "qwen-turbo", true),
            Arguments.of("{\"model\":null}", null, true),
            Arguments.of("{}", "", true),
            Arguments.of("{\"apiKey\":null}", null, true));
    }
    
    @Test
    void testDeploymentConnectionNamedLegacyDoesNotWarnOrInheritLegacyFields() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.LEGACY.model", "deployment-model");
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot())
            .thenReturn(snapshotOf("{\"apiKey\":\"stored-key\",\"model\":\"stored-model\"}"));
        CopilotAgentManager manager = managerWithRealReaders(environment);
        
        List<ILoggingEvent> warnings = refreshAndCaptureWarnings(manager);
        
        assertTrue(warnings.isEmpty());
        CopilotConnectionInfo connection = manager.getConnectionStatus().getConnections().get(0);
        assertEquals(ConnectionSource.DEPLOYMENT, connection.getSource());
        assertEquals("deployment-model", connection.getModel());
        assertNull(connection.getProviderId());
        assertFalse(connection.isApiKeyConfigured());
        assertFalse(connection.isAvailable());
        verifyNoInteractions(modelResolver);
    }
    
    @Test
    void testRefreshReadersUseSameCapturedGenerationDespiteEnvironmentMutation() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.selectedConnection", "alpha")
            .withProperty("nacos.copilot.model", "qwen-plus")
            .withProperty("COPILOT_API_KEY", "env-before")
            .withProperty("MODEL", "qwen-max")
            .withProperty("nacos.copilot.connections.alpha.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.alpha.model", "${MODEL}")
            .withProperty("nacos.copilot.connections.alpha.apiKey", "${COPILOT_API_KEY}");
        when(configStorage.isAvailable()).thenReturn(false);
        CopilotConnectionValidator validator = mock(CopilotConnectionValidator.class);
        when(validator.validate(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ProviderReaderAggregator realReaders = new ProviderReaderAggregator(Arrays.asList(
            new DeploymentConfigProviderReader(),
            new LegacyConfigProviderReader()), validator);
        when(providerReaders.readAll(any())).thenAnswer(invocation -> {
            environment.setProperty("nacos.copilot.selectedConnection", "beta");
            environment.setProperty("nacos.copilot.model", "qwen-turbo");
            environment.setProperty("COPILOT_API_KEY", "env-after");
            environment.setProperty("MODEL", "qwen-turbo");
            ConnectionTable table = realReaders.readAll(invocation.getArgument(0));
            assertEquals("qwen-max", table.get("alpha").getModel());
            assertEquals("env-before", table.get("alpha").getApiKey());
            assertEquals("qwen-plus", table.get("legacy").getModel());
            assertEquals("env-before", table.get("legacy").getApiKey());
            return table;
        });
        CopilotAgentManager manager = new CopilotAgentManager(configStorage,
            providerReaders, modelResolver, new DefaultCopilotModelRouter(), environment);
        
        manager.refreshConfig();
        
        assertEquals("alpha", manager.getConfig().getSelectedConnection());
        assertEquals("alpha", manager.getConnectionStatus().getActiveConnection());
        assertEquals(SelectionSource.DEPLOYMENT,
            manager.getConnectionStatus().getSelectionSource());
    }
    
    @Test
    void testCapturedSourcesKeepRelaxedPrecedenceAndResolvePlaceholders() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.api-key", "lower-key")
            .withProperty("nacos.copilot.selected-connection", "lower-choice")
            .withProperty("nacos.copilot.connections.alpha.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.alpha.model", "lower-model")
            .withProperty("nacos.copilot.connections.alpha.apiKey", "lower-key")
            .withProperty("KEY", "lower-placeholder");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("nacos.copilot.apiKey", "${KEY}");
        fields.put("nacos.copilot.selectedConnection", "alpha");
        fields.put("nacos.copilot.connections.alpha.model", "higher-model");
        fields.put("nacos.copilot.connections.alpha.apiKey", "");
        fields.put("KEY", "higher-placeholder");
        environment.getPropertySources().addFirst(new MapPropertySource("higher", fields));
        when(configStorage.isAvailable()).thenReturn(false);
        CopilotAgentManager manager = managerWithRealReaders(environment);
        
        manager.refreshConfig();
        
        assertEquals("higher-placeholder", manager.getConfig().getApiKey());
        assertEquals("alpha", manager.getConfig().getSelectedConnection());
        CopilotConnectionInfo alpha = manager.getConnectionStatus().getConnections().get(0);
        assertEquals("higher-model", alpha.getModel());
        assertEquals("dashscope", alpha.getProviderId());
        assertFalse(alpha.isApiKeyConfigured());
        assertTrue(alpha.isAvailable());
    }
    
    @Test
    void testDeploymentNullClearBlocksLowerPropertySources() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.api-key", "lower-key")
            .withProperty("nacos.copilot.selected-connection", "lower-choice")
            .withProperty("COPILOT_API_KEY", "env-key");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("nacos.copilot.apiKey", null);
        fields.put("nacos.copilot.selectedConnection", null);
        environment.getPropertySources().addFirst(new MapPropertySource("higher", fields));
        when(configStorage.isAvailable()).thenReturn(true);
        when(configStorage.getSnapshot())
            .thenReturn(snapshotOf("{\"apiKey\":\"stored-key\","
                + "\"selectedConnection\":\"stored\"}"));
        CopilotAgentManager manager = managerWithRealReaders(environment);
        
        manager.refreshConfig();
        
        assertNull(manager.getConfig().getApiKey());
        assertNull(manager.getConfig().getSelectedConnection());
        assertEquals(SelectionSource.NONE, manager.getConnectionStatus().getSelectionSource());
        CopilotConnectionInfo legacy = manager.getConnectionStatus().getConnections().get(0);
        assertFalse(legacy.isApiKeyConfigured());
        assertNull(legacy.getActualSource());
        verifyNoInteractions(modelResolver);
    }
    
    private CopilotAgentManager managerWithRealReaders(MockEnvironment environment) {
        CopilotConnectionValidator validator = new CopilotConnectionValidator(modelResolver);
        // Model-only/cleared connections must fail existing validation before probing.
        org.mockito.Mockito.lenient().when(modelResolver.canResolve(any())).thenReturn(true);
        ProviderReaderAggregator readers = new ProviderReaderAggregator(Arrays.asList(
            new DeploymentConfigProviderReader(),
            new LegacyConfigProviderReader()), validator);
        return new CopilotAgentManager(configStorage, readers,
            modelResolver, new DefaultCopilotModelRouter(), environment);
    }
    
    private StoredCopilotConfig snapshotOf(String json) {
        return StoredCopilotConfig.parse(json);
    }
    
    private List<ILoggingEvent> refreshAndCaptureWarnings() {
        return refreshAndCaptureWarnings(copilotAgentManager);
    }
    
    private List<ILoggingEvent> refreshAndCaptureWarnings(CopilotAgentManager manager) {
        Logger logger = (Logger) LoggerFactory.getLogger(CopilotAgentManager.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            manager.refreshConfig();
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
