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

package com.alibaba.nacos.client.ai.cache;

import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.event.PromptChangedEvent;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Nacos AI module prompt cache holder.
 *
 * @author nacos
 */
public class NacosPromptCacheHolder implements Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(NacosPromptCacheHolder.class);
    
    /**
     * Fixed group for all prompt configurations.
     */
    private static final String PROMPT_GROUP = "nacos-ai-prompt";
    
    private final ConfigService configService;
    
    private final String namespaceId;
    
    private final Map<String, Prompt> promptCache;
    
    private final Map<String, Listener> listenerMap;
    
    public NacosPromptCacheHolder(ConfigService configService, String namespaceId) {
        this.configService = configService;
        this.namespaceId = namespaceId;
        this.promptCache = new ConcurrentHashMap<>(4);
        this.listenerMap = new ConcurrentHashMap<>(4);
    }
    
    /**
     * Get prompt from server.
     *
     * @param promptKey prompt key
     * @return Prompt object, null if not found
     * @throws NacosException if query fails
     */
    public Prompt getPrompt(String promptKey) throws NacosException {
        String dataId = buildDataId(promptKey);
        String content = configService.getConfig(dataId, PROMPT_GROUP, 5000);
        if (StringUtils.isBlank(content)) {
            return null;
        }
        return parsePromptContent(promptKey, content);
    }
    
    /**
     * Subscribe prompt and start listening for configuration changes.
     *
     * @param promptKey prompt key
     * @return current Prompt object, null if not found
     * @throws NacosException if error occurs
     */
    public Prompt subscribePrompt(String promptKey) throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                    "Required parameter `promptKey` not present");
        }
        
        // Check if already subscribed
        if (listenerMap.containsKey(promptKey)) {
            return promptCache.get(promptKey);
        }
        
        // Load prompt initially
        Prompt prompt = getPrompt(promptKey);
        
        // Create listener
        String dataId = buildDataId(promptKey);
        Listener listener = new PromptConfigListener(promptKey);
        
        try {
            configService.addListener(dataId, PROMPT_GROUP, listener);
            listenerMap.put(promptKey, listener);
        } catch (NacosException e) {
            LOGGER.warn("Failed to add listener for prompt: promptKey={}, error={}", promptKey, e.getMessage());
        }
        
        // Cache prompt
        if (prompt != null) {
            promptCache.put(promptKey, prompt);
        }
        
        LOGGER.info("Subscribed prompt: {}", promptKey);
        return prompt;
    }
    
    /**
     * Unsubscribe prompt and remove listener.
     *
     * @param promptKey prompt key
     */
    public void unsubscribePrompt(String promptKey) {
        if (StringUtils.isBlank(promptKey)) {
            return;
        }
        
        Listener listener = listenerMap.remove(promptKey);
        if (listener != null) {
            String dataId = buildDataId(promptKey);
            configService.removeListener(dataId, PROMPT_GROUP, listener);
        }
        
        promptCache.remove(promptKey);
        LOGGER.info("Unsubscribed prompt: {}", promptKey);
    }
    
    @Override
    public void shutdown() throws NacosException {
        // Remove all listeners
        for (Map.Entry<String, Listener> entry : listenerMap.entrySet()) {
            String dataId = buildDataId(entry.getKey());
            configService.removeListener(dataId, PROMPT_GROUP, entry.getValue());
        }
        listenerMap.clear();
        promptCache.clear();
    }
    
    private String buildDataId(String promptKey) {
        return promptKey + ".json";
    }
    
    /**
     * Parse prompt content from JSON string.
     *
     * @param promptKey prompt key
     * @param content   JSON content
     * @return Prompt object, null if parse fails
     */
    public Prompt parsePromptContent(String promptKey, String content) {
        try {
            JsonNode node = JacksonUtils.toObj(content, JsonNode.class);
            Prompt prompt = new Prompt();
            prompt.setNamespaceId(namespaceId);
            prompt.setPromptKey(promptKey);
            if (node.has("version")) {
                prompt.setVersion(node.get("version").asText());
            }
            if (node.has("template")) {
                prompt.setTemplate(node.get("template").asText());
            }
            if (node.has("commitMsg")) {
                prompt.setCommitMsg(node.get("commitMsg").asText());
            }
            return prompt;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse prompt content: promptKey={}, error={}", promptKey, e.getMessage());
            return null;
        }
    }
    
    /**
     * Prompt config listener.
     */
    private class PromptConfigListener implements Listener {
        
        private final String promptKey;
        
        public PromptConfigListener(String promptKey) {
            this.promptKey = promptKey;
        }
        
        @Override
        public Executor getExecutor() {
            return null;
        }
        
        @Override
        public void receiveConfigInfo(String configInfo) {
            LOGGER.info("Received prompt config change: promptKey={}", promptKey);
            
            Prompt prompt = null;
            if (StringUtils.isNotBlank(configInfo)) {
                prompt = parsePromptContent(promptKey, configInfo);
            }
            
            // Update cache
            if (prompt != null) {
                promptCache.put(promptKey, prompt);
            } else {
                promptCache.remove(promptKey);
            }
            
            // Publish change event
            NotifyCenter.publishEvent(new PromptChangedEvent(promptKey, prompt));
        }
    }
}
