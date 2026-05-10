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
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.studio.StudioManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
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
    
    private final CopilotConfigStorage configStorage;
    private final CopilotProperties defaultProperties;
    private final Environment environment;
    
    private volatile CopilotProperties currentConfig;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    @Autowired
    public CopilotAgentManager(CopilotConfigStorage configStorage,
        CopilotProperties defaultProperties,
        Environment environment) {
        this.configStorage = configStorage;
        this.defaultProperties = defaultProperties;
        this.environment = environment;
    }
    
    /**
     * Initialize AgentScope Studio if studioUrl is configured.
     * This method should be called without holding any locks.
     */
    private void initStudio() {
        CopilotProperties config = currentConfig;
        if (config == null) {
            return;
        }
        
        String studioUrl = config.getStudioUrl();
        if (StringUtils.isBlank(studioUrl)) {
            LOGGER.debug("Studio URL is not configured, skipping Studio initialization");
            return;
        }
        
        try {
            String studioProject = config.getStudioProject();
            if (StringUtils.isBlank(studioProject)) {
                studioProject = "NacosCopilot";
            }
            LOGGER.info("Initializing AgentScope Studio with URL: {}, Project: {}", studioUrl,
                studioProject);
            StudioManager.init()
                .studioUrl(studioUrl)
                .project(studioProject)
                .runName("nacos_copilot_" + System.currentTimeMillis())
                .initialize()
                .block();
            LOGGER.info("AgentScope Studio initialized successfully");
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize AgentScope Studio: {}", e.getMessage(), e);
        }
    }
    
    @PostConstruct
    public void init() {
        refreshConfig();
        initStudio();
    }
    
    /**
     * Get current configuration.
     *
     * @return current CopilotProperties
     */
    public CopilotProperties getConfig() {
        lock.readLock().lock();
        try {
            return currentConfig;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Refresh configuration from storage.
     */
    public void refreshConfig() {
        lock.writeLock().lock();
        try {
            CopilotProperties config = getEffectiveConfig();
            currentConfig = config;
            LOGGER.info("Copilot configuration refreshed");
        } finally {
            lock.writeLock().unlock();
        }
        // Re-initialize Studio if URL changed (outside lock to avoid blocking)
        initStudio();
    }
    
    /**
     * Create AgentScope agent with current configuration.
     *
     * @param systemPrompt system prompt (optional)
     * @return ReActAgent instance, or null if not configured
     */
    public ReActAgent createAgent(String systemPrompt) {
        CopilotProperties config = getConfig();
        
        if (config == null || !config.isEnabled()) {
            LOGGER.warn("Copilot is disabled or not configured");
            return null;
        }
        
        String apiKey = getApiKey(config);
        if (StringUtils.isBlank(apiKey)) {
            LOGGER.warn("Copilot API Key is not configured");
            return null;
        }
        
        // Create model
        DashScopeChatModel model = DashScopeChatModel.builder()
            .apiKey(apiKey)
            .modelName(config.getModel())
            .stream(true)
            .enableThinking(true)
            .build();
        
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
        CopilotProperties config = getConfig();
        if (config == null || !config.isEnabled()) {
            return false;
        }
        
        String apiKey = getApiKey(config);
        return StringUtils.isNotBlank(apiKey);
    }
    
    /**
     * Get effective configuration (from Nacos Config or default).
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
        
        // Fallback to default properties
        LOGGER.debug("Using default Copilot config");
        return defaultProperties;
    }
    
    /**
     * Get API key from environment variable or config.
     *
     * @param config CopilotProperties
     * @return API key
     */
    private String getApiKey(CopilotProperties config) {
        // First try environment variable
        String apiKey = environment.getProperty("COPILOT_API_KEY");
        if (StringUtils.isNotBlank(apiKey)) {
            return apiKey;
        }
        
        // Then try config property
        if (config != null) {
            return config.getApiKey();
        }
        
        return null;
    }
}
