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
import com.alibaba.nacos.copilot.model.CopilotConfigTestResponse;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.studio.StudioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;
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
            LOGGER.info("Initializing AgentScope Studio with URL: {}, Project: {}", studioUrl, studioProject);
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
     * Create AgentScope agent with current effective configuration.
     * 使用当前生效配置创建 AgentScope Agent。
     *
     * @param systemPrompt system prompt (optional)
     *                     系统提示词（可选）
     * @return ReActAgent instance, or null if not configured
     *         创建成功则返回 ReActAgent，否则返回 null
     */
    public ReActAgent createAgent(String systemPrompt) {
        // Keep the original behavior: create agent with current effective configuration.
        // 保留原有行为：默认使用当前生效配置创建 agent。
        return createAgent(getConfig(), systemPrompt);
    }

    /**
     * Create AgentScope agent with the specified configuration.
     * 使用指定配置创建 AgentScope Agent。
     *
     * @param config       copilot configuration
     *                     当前要使用的 Copilot 配置
     * @param systemPrompt system prompt (optional)
     *                     系统提示词（可选）
     * @return ReActAgent instance, or null if configuration is unavailable
     *         如果配置不可用则返回 null
     */
    private ReActAgent createAgent(CopilotProperties config, String systemPrompt) {
        // 1. Check whether configuration is null or Copilot is disabled.
        // 1. 判断配置是否为空，或者功能是否启用。
        if (config == null || !config.isEnabled()) {
            LOGGER.warn("Copilot is disabled or not configured");
            return null;
        }

        // 2. Resolve the effective API key.
        //    Environment variable takes precedence over config value.
        // 2. 获取真正生效的 API Key。
        //    优先读环境变量，其次读配置中的 apiKey。
        String apiKey = getApiKey(config);
        if (StringUtils.isBlank(apiKey)) {
            LOGGER.warn("Copilot API Key is not configured");
            return null;
        }

        // 3. Create the underlying model client based on current configuration.
        // 3. 基于当前配置创建底层模型客户端。
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.getModel())
                .stream(true)
                .enableThinking(true)
                .build();

        // 4. Create the agent based on the model client.
        // 4. 基于模型客户端创建 Agent。
        ReActAgent.Builder agentBuilder = ReActAgent.builder()
                .name("CopilotAgent")
                .model(model);

        // 5. Apply system prompt when it is provided.
        // 5. 如果传入了 system prompt，则设置到 agent 中。
        if (StringUtils.isNotBlank(systemPrompt)) {
            agentBuilder.sysPrompt(systemPrompt);
        }

        // 6. Return the constructed agent.
        // 6. 返回构建完成的 agent。
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

    /**
     * Resolve the source of effective API key.
     * 获取当前生效 API Key 的来源。
     *
     * @param config copilot configuration
     *               Copilot 配置
     * @return ENV if API key comes from environment variable,
     *         CONFIG if API key comes from request/config,
     *         NONE if no API key is available
     *         如果来自环境变量则返回 ENV，
     *         如果来自配置则返回 CONFIG，
     *         如果都没有则返回 NONE
     */
    private String resolveApiKeySource(CopilotProperties config) {
        // 1. Check whether API key is provided by environment variable first.
        // 1. 优先判断环境变量中是否配置了 API Key。
        String apiKey = environment.getProperty("COPILOT_API_KEY");
        if (StringUtils.isNotBlank(apiKey)) {
            return "ENV";
        }

        // 2. If not found in environment, check the submitted configuration.
        // 2. 如果没有，则判断配置中是否配置了 API Key。
        if (config != null && StringUtils.isNotBlank(config.getApiKey())) {
            return "CONFIG";
        }

        // 3. Return NONE when neither environment nor config provides API key.
        // 3. 如果环境变量和配置里都没有，则返回 NONE。
        return "NONE";
    }

    /**
     * Test whether the submitted Copilot configuration is usable.
     * 检测当前提交的 Copilot 配置是否可用。
     *
     * @param config submitted copilot configuration
     *               当前提交的 Copilot 配置
     * @return connection test result
     *         连接检测结果
     */
    public CopilotConfigTestResponse testConfig(CopilotProperties config) {
        CopilotConfigTestResponse result = new CopilotConfigTestResponse();

        // 0. Return failure immediately when request body is null.
        // 0. 请求体为空，直接返回失败结果。
        if (config == null) {
            result.setSuccess(false);
            result.setConfigValid(false);
            result.setLlmReachable(false);
            result.setApiKeySource("NONE");
            result.setStudioConfigured(false);
            result.setMessage("Configuration cannot be null");
            return result;
        }

        // 1. Resolve the source of effective API key.
        // 1. 解析当前生效 API Key 的来源。
        String apiKeySource = resolveApiKeySource(config);

        // 2. Check whether Studio URL is configured.
        // 2. 判断是否配置了 Studio URL。
        boolean studioConfigured = StringUtils.isNotBlank(config.getStudioUrl());

        // 3. Fill basic result fields before validation.
        // 3. 将基础信息先写入返回结果。
        result.setApiKeySource(apiKeySource);
        result.setStudioConfigured(studioConfigured);

        // 4. Validate that model name is provided.
        // 4. 校验模型名称是否为空。
        if (StringUtils.isBlank(config.getModel())) {
            result.setSuccess(false);
            result.setConfigValid(false);
            result.setLlmReachable(false);
            result.setMessage("Model is required");
            return result;
        }

        // 5. Validate that an effective API key is available.
        // 5. 校验当前是否存在可用的 API Key。
        if ("NONE".equals(apiKeySource)) {
            result.setSuccess(false);
            result.setConfigValid(false);
            result.setLlmReachable(false);
            result.setMessage("API Key is required");
            return result;
        }

        // 6. Reaching here means basic configuration validation passes.
        // 6. 如果走到这里，说明基础配置校验通过。
        result.setConfigValid(true);

        try {
            // 7. Create a test agent with the submitted configuration.
            // 7. 使用当前请求中的配置创建测试 agent。
            ReActAgent agent = createAgent(config, "You are a connectivity checker, reply with ok.");
            if (agent == null) {
                result.setSuccess(false);
                result.setLlmReachable(false);
                result.setMessage("Failed to create Copilot agent");
                return result;
            }

            // 8. Build a minimal test request.
            // 8. 构造一个最小测试请求。
            Msg userMsg = Msg.builder()
                    .textContent("ping")
                    .build();

            // 9. Configure stream options with reasoning and tool result events in incremental mode.
            // 9. 设置流式调用参数：关注推理事件和工具结果事件，并启用增量返回。
            StreamOptions streamOptions = StreamOptions.builder()
                    .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                    .incremental(true)
                    .build();

            // 10. Send a lightweight test request and wait up to 15 seconds.
            // 10. 发起一次轻量调用，最多等待 15 秒。
            agent.stream(userMsg, streamOptions).blockLast(Duration.ofSeconds(15));

            // 11. If no exception is thrown, the LLM service is reachable.
            // 11. 如果没有抛异常，说明 LLM 服务可达。
            result.setSuccess(true);
            result.setLlmReachable(true);
            result.setMessage("Copilot configuration is valid and LLM service is reachable.");
            return result;
        } catch (Exception e) {
            LOGGER.warn("Failed to test Copilot configuration", e);
            result.setSuccess(false);
            result.setLlmReachable(false);
            result.setMessage("Failed to access LLM service. Please check the API key and model configuration.");
            return result;
        }
    }
}
