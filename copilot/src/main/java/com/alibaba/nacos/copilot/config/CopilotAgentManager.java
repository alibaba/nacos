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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.copilot.config;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.copilot.connection.ConnectionTable;
import com.alibaba.nacos.copilot.connection.CopilotConnectionInfo;
import com.alibaba.nacos.copilot.connection.CopilotConnectionStatus;
import com.alibaba.nacos.copilot.connection.CopilotModelConnection;
import com.alibaba.nacos.copilot.connection.CopilotModelResolver;
import com.alibaba.nacos.copilot.connection.CopilotModelRouter;
import com.alibaba.nacos.copilot.connection.LegacyConfigProviderReader;
import com.alibaba.nacos.copilot.connection.ProviderReadContext;
import com.alibaba.nacos.copilot.connection.ProviderReaderAggregator;
import com.alibaba.nacos.copilot.connection.SelectionSource;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.studio.StudioManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Copilot agent manager that manages AgentScope agents with dynamic configuration.
 *
 * @author nacos
 */
@Component
public class CopilotAgentManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CopilotAgentManager.class);
    
    private static final String CONFIG_PREFIX = "nacos.copilot";
    
    private final CopilotConfigStorage configStorage;
    private final CopilotProperties defaultProperties;
    private final ProviderReaderAggregator providerReaders;
    private final CopilotModelResolver modelResolver;
    private final CopilotModelRouter modelRouter;
    private final ConfigurableEnvironment environment;
    
    private volatile RuntimeSnapshot snapshot = RuntimeSnapshot.empty();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    private String activeStudioUrl;
    private String activeStudioProject;
    
    @Autowired
    public CopilotAgentManager(CopilotConfigStorage configStorage,
        CopilotProperties defaultProperties, ProviderReaderAggregator providerReaders,
        CopilotModelResolver modelResolver, CopilotModelRouter modelRouter,
        ConfigurableEnvironment environment) {
        this.configStorage = configStorage;
        this.defaultProperties = defaultProperties;
        this.providerReaders = providerReaders;
        this.modelResolver = modelResolver;
        this.modelRouter = modelRouter;
        this.environment = environment;
    }
    
    /**
     * Initialize AgentScope Studio if studioUrl is configured and changed since the last
     * successful initialization, or if the initialized state was cleared after a failure.
     * This method must not be called while holding the configuration lock.
     */
    private synchronized void initStudio() {
        CopilotProperties config = snapshot.getConfig();
        if (config == null) {
            return;
        }
        
        String studioUrl = config.getStudioUrl();
        if (StringUtils.isBlank(studioUrl)) {
            LOGGER.debug("Studio URL is not configured, skipping Studio initialization");
            return;
        }
        
        String studioProject = config.getStudioProject();
        if (StringUtils.isBlank(studioProject)) {
            studioProject = "NacosCopilot";
        }
        if (studioUrl.equals(activeStudioUrl) && studioProject.equals(activeStudioProject)
            && StudioManager.isInitialized()) {
            LOGGER.debug("AgentScope Studio is already initialized for {}", studioUrl);
            return;
        }
        
        try {
            LOGGER.info("Initializing AgentScope Studio with URL: {}, Project: {}", studioUrl,
                studioProject);
            StudioManager.init()
                .studioUrl(studioUrl)
                .project(studioProject)
                .runName("nacos_copilot_" + System.currentTimeMillis())
                .initialize()
                .block();
            activeStudioUrl = studioUrl;
            activeStudioProject = studioProject;
            LOGGER.info("AgentScope Studio initialized successfully");
        } catch (Exception e) {
            // AgentScope shuts Studio down when initialization fails, so the cached
            // configuration no longer describes an active Studio and must not be reused.
            activeStudioUrl = null;
            activeStudioProject = null;
            LOGGER.warn("Failed to initialize AgentScope Studio: {}", e.getMessage(), e);
        }
    }
    
    @PostConstruct
    public void init() {
        refreshConfig();
    }
    
    /**
     * Get current configuration.
     *
     * @return current CopilotProperties
     */
    public CopilotProperties getConfig() {
        return snapshot.getConfig();
    }
    
    /**
     * Get the sanitized connection metadata for the console.
     *
     * <p>The returned view never carries a credential, so it can be published through the
     * console API.
     *
     * @return connections in selection order and the one serving requests
     */
    public CopilotConnectionStatus getConnectionStatus() {
        RuntimeSnapshot current = snapshot;
        CopilotProperties config = current.getConfig();
        String selectedConnection = config == null ? null : config.getSelectedConnection();
        ConnectionTable connectionTable = current.getConnectionTable();
        List<CopilotConnectionInfo> connections = new ArrayList<>();
        for (CopilotModelConnection connection : connectionTable.getAll()) {
            connections.add(CopilotConnectionInfo.from(connection));
        }
        CopilotModelConnection active =
            modelRouter.route(connectionTable, selectedConnection);
        return new CopilotConnectionStatus(connections,
            active == null ? null : active.getName(), selectedConnection,
            current.getSelectionSource());
    }
    
    /**
     * Reload the effective configuration and rebuild the connection table.
     */
    public void refreshConfig() {
        CopilotProperties config;
        ConnectionTable connectionTable;
        lock.writeLock().lock();
        try {
            EffectiveConfig effective = resolveEffectiveConfig();
            config = effective.properties;
            connectionTable = providerReaders.readAll(
                new ProviderReadContext(config, effective.overrides.apiKey));
            snapshot = new RuntimeSnapshot(config, connectionTable,
                resolveSelectionSource(config, effective.overrides.selectedConnection));
        } finally {
            lock.writeLock().unlock();
        }
        warnOnIgnoredSelection(config, connectionTable);
        warnOnLegacyConnection(connectionTable);
        LOGGER.info("Copilot configuration refreshed");
        // Re-initialize Studio if URL changed (outside lock to avoid blocking)
        initStudio();
    }
    
    private void warnOnIgnoredSelection(CopilotProperties config,
        ConnectionTable connectionTable) {
        if (config == null || StringUtils.isBlank(config.getSelectedConnection())) {
            return;
        }
        String selected = config.getSelectedConnection();
        CopilotModelConnection connection = connectionTable.get(selected);
        if (connection == null) {
            LOGGER.warn("Selected Copilot connection [{}] does not exist, routing falls back "
                + "to the default selection order", selected);
        } else if (!connection.isEnabled()) {
            LOGGER.warn("Selected Copilot connection [{}] is disabled, routing falls back to "
                + "the default selection order", selected);
        } else if (!connection.isAvailable()) {
            LOGGER.warn("Selected Copilot connection [{}] is unavailable ({}), routing falls "
                + "back to the default selection order", selected,
                connection.getUnavailableReason());
        }
    }
    
    private void warnOnLegacyConnection(ConnectionTable connectionTable) {
        CopilotModelConnection legacy =
            connectionTable.get(LegacyConfigProviderReader.CONNECTION_NAME);
        if (legacy == null) {
            return;
        }
        LOGGER.warn("Copilot is using the deprecated legacy connection [{}]. Define connections "
            + "with nacos.copilot.connections.* and remove the legacy apiKey/model settings; "
            + "the legacy connection keeps working during the deprecation window.",
            legacy.getName());
    }
    
    /**
     * Create AgentScope agent with current configuration.
     *
     * @param systemPrompt system prompt (optional)
     * @return ReActAgent instance, or null if not configured
     */
    public ReActAgent createAgent(String systemPrompt) {
        RuntimeSnapshot current = snapshot;
        CopilotProperties config = current.getConfig();
        
        if (config == null || !config.isEnabled()) {
            LOGGER.warn("Copilot is disabled or not configured");
            return null;
        }
        
        CopilotModelConnection connection = modelRouter.route(current.getConnectionTable(),
            config.getSelectedConnection());
        if (connection == null) {
            LOGGER.warn("No available Copilot model connection");
            return null;
        }
        
        Model model;
        try {
            model = modelResolver.resolve(connection);
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to create Copilot model from connection {}",
                connection.getName(), e);
            return null;
        }
        
        // Create agent
        ReActAgent.Builder agentBuilder = ReActAgent.builder()
            .name("CopilotAgent")
            .model(model);
        
        if (StringUtils.isNotBlank(systemPrompt)) {
            agentBuilder.sysPrompt(systemPrompt);
        }
        
        return agentBuilder.build();
    }
    
    /**
     * Check if Copilot is enabled and configured.
     *
     * @return true if enabled and configured
     */
    public boolean isEnabled() {
        RuntimeSnapshot current = snapshot;
        CopilotProperties config = current.getConfig();
        if (config == null || !config.isEnabled()) {
            return false;
        }
        return modelRouter.route(current.getConnectionTable(),
            config.getSelectedConnection()) != null;
    }
    
    /**
     * Get the effective configuration: deployment configuration overrides the stored
     * configuration for the fields it declares, and the stored configuration overrides the
     * defaults. The stored configuration is read once per refresh.
     *
     * @return effective CopilotProperties
     */
    private SelectionSource resolveSelectionSource(CopilotProperties config,
        boolean deploymentDeclared) {
        if (config == null || StringUtils.isBlank(config.getSelectedConnection())) {
            return SelectionSource.NONE;
        }
        return deploymentDeclared ? SelectionSource.DEPLOYMENT : SelectionSource.STORED;
    }
    
    private EffectiveConfig resolveEffectiveConfig() {
        CopilotProperties stored = null;
        if (configStorage != null && configStorage.isAvailable()) {
            stored = configStorage.getConfig();
        }
        CopilotProperties effective =
            stored == null ? new CopilotProperties() : copyOf(stored);
        DeploymentOverrides overrides = applyDeploymentConfig(effective);
        if (stored == null && !overrides.applied) {
            return new EffectiveConfig(defaultProperties, overrides);
        }
        return new EffectiveConfig(effective, overrides);
    }
    
    /**
     * Apply the deployment configuration fields that are explicitly set, so that every
     * other field keeps its stored or default value.
     *
     * @param target configuration to update
     * @return whether the deployment configuration declares at least one field
     */
    private DeploymentOverrides applyDeploymentConfig(CopilotProperties target) {
        Binder binder = Binder.get(environment);
        BindResult<Boolean> enabled =
            binder.bind(CONFIG_PREFIX + ".enabled", Boolean.class);
        enabled.ifBound(target::setEnabled);
        BindResult<String> defaultNamespace =
            binder.bind(CONFIG_PREFIX + ".default-namespace", String.class);
        defaultNamespace.ifBound(target::setDefaultNamespace);
        BindResult<String> apiKey = binder.bind(CONFIG_PREFIX + ".api-key", String.class);
        apiKey.ifBound(target::setApiKey);
        BindResult<String> model = binder.bind(CONFIG_PREFIX + ".model", String.class);
        model.ifBound(target::setModel);
        BindResult<String> studioUrl = binder.bind(CONFIG_PREFIX + ".studio-url", String.class);
        studioUrl.ifBound(target::setStudioUrl);
        BindResult<String> studioProject =
            binder.bind(CONFIG_PREFIX + ".studio-project", String.class);
        studioProject.ifBound(target::setStudioProject);
        BindResult<String> selectedConnection =
            binder.bind(CONFIG_PREFIX + ".selected-connection", String.class);
        selectedConnection.ifBound(target::setSelectedConnection);
        DeploymentOverrides overrides = new DeploymentOverrides();
        overrides.apiKey = apiKey.isBound();
        overrides.selectedConnection = selectedConnection.isBound();
        overrides.applied = enabled.isBound() || defaultNamespace.isBound()
            || apiKey.isBound() || model.isBound() || studioUrl.isBound()
            || studioProject.isBound() || selectedConnection.isBound();
        return overrides;
    }
    
    private CopilotProperties copyOf(CopilotProperties source) {
        CopilotProperties copy = new CopilotProperties();
        copy.setEnabled(source.isEnabled());
        copy.setDefaultNamespace(source.getDefaultNamespace());
        copy.setApiKey(source.getApiKey());
        copy.setApiKeyCleared(source.getApiKeyCleared());
        copy.setModel(source.getModel());
        copy.setStudioUrl(source.getStudioUrl());
        copy.setStudioProject(source.getStudioProject());
        copy.setSelectedConnection(source.getSelectedConnection());
        return copy;
    }
    
    /**
     * Release the AgentScope Studio client on shutdown.
     */
    @PreDestroy
    public void destroy() {
        if (StudioManager.isInitialized()) {
            StudioManager.shutdown();
        }
    }
    
    /**
     * Configuration and connection table published together, so a request never mixes two
     * configuration generations.
     */
    private static final class RuntimeSnapshot {
        
        private static final RuntimeSnapshot EMPTY =
            new RuntimeSnapshot(null, ConnectionTable.empty(), SelectionSource.NONE);
        
        private final CopilotProperties config;
        
        private final ConnectionTable connectionTable;
        
        private final SelectionSource selectionSource;
        
        private RuntimeSnapshot(CopilotProperties config, ConnectionTable connectionTable,
            SelectionSource selectionSource) {
            this.config = config;
            this.connectionTable = connectionTable;
            this.selectionSource = selectionSource;
        }
        
        private static RuntimeSnapshot empty() {
            return EMPTY;
        }
        
        private CopilotProperties getConfig() {
            return config;
        }
        
        private ConnectionTable getConnectionTable() {
            return connectionTable;
        }
        
        private SelectionSource getSelectionSource() {
            return selectionSource;
        }
    }
    
    /**
     * Effective configuration plus the fields the deployment configuration declared.
     */
    private static final class EffectiveConfig {
        
        private final CopilotProperties properties;
        
        private final DeploymentOverrides overrides;
        
        private EffectiveConfig(CopilotProperties properties, DeploymentOverrides overrides) {
            this.properties = properties;
            this.overrides = overrides;
        }
    }
    
    /**
     * Deployment fields that were explicitly bound during this refresh.
     */
    private static final class DeploymentOverrides {
        
        private boolean applied;
        
        private boolean apiKey;
        
        private boolean selectedConnection;
    }
}
