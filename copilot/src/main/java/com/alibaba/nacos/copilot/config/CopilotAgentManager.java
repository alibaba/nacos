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
import com.alibaba.nacos.copilot.connection.CopilotModelConnection;
import com.alibaba.nacos.copilot.connection.CopilotModelResolver;
import com.alibaba.nacos.copilot.connection.CopilotModelRouter;
import com.alibaba.nacos.copilot.connection.ProviderReadContext;
import com.alibaba.nacos.copilot.connection.ProviderReaderAggregator;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.studio.StudioManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

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
     * successful initialization. This method must not be called while holding the
     * configuration lock.
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
        if (studioUrl.equals(activeStudioUrl) && studioProject.equals(activeStudioProject)) {
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
     * Reload the effective configuration and rebuild the connection table.
     */
    public void refreshConfig() {
        CopilotProperties config;
        ConnectionTable connectionTable;
        lock.writeLock().lock();
        try {
            config = getEffectiveConfig();
            connectionTable = providerReaders.readAll(new ProviderReadContext(config));
            snapshot = new RuntimeSnapshot(config, connectionTable);
        } finally {
            lock.writeLock().unlock();
        }
        warnOnIgnoredSelection(config, connectionTable);
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
     * Get the effective configuration: stored configuration, then deployment configuration,
     * then defaults.
     *
     * @return effective CopilotProperties
     */
    private CopilotProperties getEffectiveConfig() {
        // First try to get from Nacos Config
        if (configStorage != null && configStorage.isAvailable()) {
            CopilotProperties config = configStorage.getConfig();
            if (config != null) {
                LOGGER.debug("Using Copilot config from Nacos Config");
                return config;
            }
        }
        
        // Re-bind the deployment configuration so a refresh also applies changed
        // nacos.copilot.* fields, not only the connection table.
        BindResult<CopilotProperties> bound = Binder.get(environment)
            .bind(CONFIG_PREFIX, Bindable.of(CopilotProperties.class));
        if (bound.isBound()) {
            LOGGER.debug("Using Copilot config from deployment configuration");
            return bound.get();
        }
        
        // Fallback to default properties
        LOGGER.debug("Using default Copilot config");
        return defaultProperties;
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
            new RuntimeSnapshot(null, ConnectionTable.empty());
        
        private final CopilotProperties config;
        
        private final ConnectionTable connectionTable;
        
        private RuntimeSnapshot(CopilotProperties config, ConnectionTable connectionTable) {
            this.config = config;
            this.connectionTable = connectionTable;
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
    }
}
