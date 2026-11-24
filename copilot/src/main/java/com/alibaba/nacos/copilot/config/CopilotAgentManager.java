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
import com.alibaba.nacos.copilot.connection.ConnectionSource;
import com.alibaba.nacos.copilot.connection.ConnectionTable;
import com.alibaba.nacos.copilot.connection.CopilotConnectionInfo;
import com.alibaba.nacos.copilot.connection.CopilotConnectionStatus;
import com.alibaba.nacos.copilot.connection.CopilotModelConnection;
import com.alibaba.nacos.copilot.connection.CopilotModelResolver;
import com.alibaba.nacos.copilot.connection.CopilotModelRouter;
import com.alibaba.nacos.copilot.connection.CredentialSource;
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
import java.util.function.Consumer;

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
        ProviderReaderAggregator providerReaders, CopilotModelResolver modelResolver,
        CopilotModelRouter modelRouter, ConfigurableEnvironment environment) {
        this.configStorage = configStorage;
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
            config != null && config.isEnabled() ? modelRouter.route(connectionTable,
                selectedConnection) : null;
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
            ConfigurableEnvironment deployment = ProviderReadContext.captureDeployment(environment);
            EffectiveConfig effective = resolveEffectiveConfig(deployment);
            config = effective.properties;
            connectionTable = providerReaders.readAll(new ProviderReadContext(deployment, config,
                effective.legacyDeclared, effective.credentialSource));
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
        if (legacy == null || legacy.getSource() != ConnectionSource.LEGACY) {
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
     * Report where the effective selection comes from.
     *
     * @param config             effective configuration
     * @param deploymentDeclared whether the deployment configuration declared a non-blank
     *                           selection
     * @return selection origin
     */
    private SelectionSource resolveSelectionSource(CopilotProperties config,
        boolean deploymentDeclared) {
        if (config == null || StringUtils.isBlank(config.getSelectedConnection())) {
            return SelectionSource.NONE;
        }
        return deploymentDeclared ? SelectionSource.DEPLOYMENT : SelectionSource.STORED;
    }
    
    /**
     * Get the effective configuration: each source is read once per refresh, then merged
     * field by field. The deployment configuration overrides the stored configuration for
     * the fields it declares, and the stored configuration overrides the defaults. An
     * explicit clear at the highest declaring source keeps the field empty and blocks the
     * lower sources.
     *
     * @return effective configuration with the deployment fields that were applied
     */
    private EffectiveConfig resolveEffectiveConfig(ConfigurableEnvironment deployment) {
        StoredCopilotConfig stored = readStoredConfig();
        CopilotProperties effective = new CopilotProperties();
        applyStoredConfig(stored, effective);
        DeploymentOverrides overrides = applyDeploymentConfig(deployment, effective);
        effective.setApiKeyCleared(resolveApiKeyCleared(stored, overrides, effective)
            ? Boolean.TRUE : null);
        String environmentKey = deployment.getProperty(ProviderReadContext.ENV_API_KEY);
        boolean legacyDeclared = overrides.apiKey || overrides.model
            || (stored != null && (stored.declares(StoredCopilotConfig.API_KEY)
                || stored.declares(StoredCopilotConfig.MODEL)
                || Boolean.TRUE.equals(stored.getProperties().getApiKeyCleared())))
            || StringUtils.isNotBlank(environmentKey);
        CredentialSource credentialSource = null;
        if (StringUtils.isNotBlank(effective.getApiKey())) {
            credentialSource = overrides.apiKey
                ? CredentialSource.DEPLOYMENT : CredentialSource.CONSOLE;
        } else if (!Boolean.TRUE.equals(effective.getApiKeyCleared())
            && StringUtils.isNotBlank(environmentKey)) {
            credentialSource = CredentialSource.ENVIRONMENT;
        }
        return new EffectiveConfig(effective, overrides, legacyDeclared, credentialSource);
    }
    
    private StoredCopilotConfig readStoredConfig() {
        if (configStorage == null || !configStorage.isAvailable()) {
            return null;
        }
        return configStorage.getSnapshot();
    }
    
    /**
     * Apply the stored fields the document actually declares. Values are kept as declared,
     * so a blank value clears the field instead of falling back to a lower source.
     */
    private void applyStoredConfig(StoredCopilotConfig stored, CopilotProperties target) {
        if (stored == null) {
            return;
        }
        if (stored.declares(StoredCopilotConfig.ENABLED)) {
            Object enabled = stored.getRaw(StoredCopilotConfig.ENABLED);
            if (enabled instanceof Boolean) {
                target.setEnabled((Boolean) enabled);
            } else if (enabled != null) {
                LOGGER.warn("Ignoring stored Copilot enabled value [{}] because it is not a "
                    + "boolean", enabled);
            }
        }
        if (stored.declares(StoredCopilotConfig.DEFAULT_NAMESPACE)) {
            target.setDefaultNamespace(
                clearable(stored.getRaw(StoredCopilotConfig.DEFAULT_NAMESPACE)));
        }
        if (stored.declares(StoredCopilotConfig.API_KEY)) {
            target.setApiKey(clearable(stored.getRaw(StoredCopilotConfig.API_KEY)));
        }
        if (stored.declares(StoredCopilotConfig.MODEL)) {
            target.setModel(clearable(stored.getRaw(StoredCopilotConfig.MODEL)));
        }
        if (stored.declares(StoredCopilotConfig.STUDIO_URL)) {
            target.setStudioUrl(clearable(stored.getRaw(StoredCopilotConfig.STUDIO_URL)));
        }
        if (stored.declares(StoredCopilotConfig.STUDIO_PROJECT)) {
            target.setStudioProject(clearable(stored.getRaw(StoredCopilotConfig.STUDIO_PROJECT)));
        }
        if (stored.declares(StoredCopilotConfig.SELECTED_CONNECTION)) {
            target.setSelectedConnection(
                clearable(stored.getRaw(StoredCopilotConfig.SELECTED_CONNECTION)));
        }
    }
    
    /**
     * Normalize a declared string value: a missing or blank value means the field is
     * cleared.
     */
    private String clearable(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
    
    /**
     * The effective credential is blocked from the environment fallback when the highest
     * source that declares it clears it.
     */
    private boolean resolveApiKeyCleared(StoredCopilotConfig stored,
        DeploymentOverrides overrides, CopilotProperties effective) {
        if (overrides.apiKey) {
            return StringUtils.isBlank(effective.getApiKey());
        }
        if (stored != null && stored.declares(StoredCopilotConfig.API_KEY)) {
            return StringUtils.isBlank(effective.getApiKey());
        }
        return stored != null && Boolean.TRUE.equals(stored.getProperties().getApiKeyCleared());
    }
    
    /**
     * Apply the deployment configuration fields that are declared, so that every other
     * field keeps its stored or default value. A declared blank string clears the field
     * instead of counting as not declared.
     *
     * @param target configuration to update
     * @return which fields the deployment configuration declared
     */
    private DeploymentOverrides applyDeploymentConfig(ConfigurableEnvironment deployment,
        CopilotProperties target) {
        Binder binder = Binder.get(deployment);
        applyDeploymentBoolean(binder, CONFIG_PREFIX + ".enabled", target);
        applyDeploymentString(binder, CONFIG_PREFIX + ".default-namespace",
            target::setDefaultNamespace);
        BindResult<String> apiKey = binder.bind(CONFIG_PREFIX + ".api-key", String.class);
        if (apiKey.isBound()) {
            target.setApiKey(clearable(apiKey.get()));
        }
        boolean modelDeclared =
            applyDeploymentString(binder, CONFIG_PREFIX + ".model", target::setModel);
        applyDeploymentString(binder, CONFIG_PREFIX + ".studio-url", target::setStudioUrl);
        applyDeploymentString(binder, CONFIG_PREFIX + ".studio-project",
            target::setStudioProject);
        BindResult<String> selectedConnection =
            binder.bind(CONFIG_PREFIX + ".selected-connection", String.class);
        if (selectedConnection.isBound()) {
            target.setSelectedConnection(clearable(selectedConnection.get()));
        }
        DeploymentOverrides overrides = new DeploymentOverrides();
        overrides.apiKey = apiKey.isBound();
        overrides.model = modelDeclared;
        // A blank deployment selection clears the stored selection, so it only counts as a
        // deployment-scope value when it actually selects a connection.
        overrides.selectedConnection =
            selectedConnection.isBound() && StringUtils.isNotBlank(selectedConnection.get());
        return overrides;
    }
    
    private boolean applyDeploymentString(Binder binder, String key, Consumer<String> setter) {
        BindResult<String> result = binder.bind(key, String.class);
        if (result.isBound()) {
            setter.accept(clearable(result.get()));
        }
        return result.isBound();
    }
    
    private void applyDeploymentBoolean(Binder binder, String key, CopilotProperties target) {
        try {
            BindResult<Boolean> result = binder.bind(key, Boolean.class);
            result.ifBound(target::setEnabled);
        } catch (RuntimeException e) {
            LOGGER.warn("Ignoring deployment Copilot value for [{}] because it is not a "
                + "boolean: {}", key, e.getMessage());
        }
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
        
        private final boolean legacyDeclared;
        
        private final CredentialSource credentialSource;
        
        private EffectiveConfig(CopilotProperties properties, DeploymentOverrides overrides,
            boolean legacyDeclared, CredentialSource credentialSource) {
            this.properties = properties;
            this.overrides = overrides;
            this.legacyDeclared = legacyDeclared;
            this.credentialSource = credentialSource;
        }
    }
    
    /**
     * Deployment fields that were explicitly bound during this refresh.
     */
    private static final class DeploymentOverrides {
        
        private boolean apiKey;
        
        private boolean model;
        
        private boolean selectedConnection;
    }
}
