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

package com.alibaba.nacos.copilot.service;

import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.copilot.adapter.StreamResponseCallback;
import com.alibaba.nacos.copilot.config.CopilotAgentManager;
import com.alibaba.nacos.copilot.capability.prompt.SkillGenerationPrompt;
import com.alibaba.nacos.copilot.model.SkillGenerationRequest;
import com.alibaba.nacos.copilot.model.SkillGenerationResponse;
import com.alibaba.nacos.copilot.model.StreamResponseType;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Skill generation service implementation.
 *
 * @author nacos
 */
@Service
public class SkillGenerationServiceImpl implements SkillGenerationService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillGenerationServiceImpl.class);
    
    private final CopilotAgentManager agentManager;
    
    @Autowired
    public SkillGenerationServiceImpl(CopilotAgentManager agentManager) {
        this.agentManager = agentManager;
    }
    
    @Override
    public SkillGenerationResponse generateSkill(SkillGenerationRequest request) {
        // 1. Validate request
        if (request == null || StringUtils.isBlank(request.getBackgroundInfo())) {
            throw new RuntimeException(new NacosException(NacosException.INVALID_PARAM,
                    "Background information is required"));
        }
    
        // 2. Check if Copilot is enabled
        if (!agentManager.isEnabled()) {
            throw new RuntimeException(new NacosException(NacosException.INVALID_PARAM,
                    "AI 功能未启用：请配置 Copilot API Key。请设置 nacos.copilot.llm.apiKey 或环境变量 COPILOT_API_KEY"));
        }
        
        // 3. Get system prompt
        String systemPrompt = SkillGenerationPrompt.SYSTEM_PROMPT;
        
        // 4. Build user message
        String userMessage = buildUserMessage(request);
        
        // 5. Create agent with system prompt
        ReActAgent agent = agentManager.createAgent(systemPrompt);
        if (agent == null) {
            throw new RuntimeException(new NacosException(NacosException.INVALID_PARAM,
                    "Failed to create Copilot agent. Please check configuration."));
        }
        
        // 6. Create user message
        Msg userMsg = Msg.builder()
                .textContent(userMessage)
                .build();
        
        // 7. Call agent (non-streaming, collect all results)
        try {
            StreamOptions streamOptions = StreamOptions.builder()
                    .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                    .incremental(true)
                    .build();
            
            StringBuilder fullContent = new StringBuilder();
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            
            Flux<io.agentscope.core.agent.Event> eventFlux = agent.stream(userMsg, streamOptions)
                    .subscribeOn(Schedulers.boundedElastic());
            
            eventFlux.subscribe(
                    event -> {
                        try {
                            Msg msg = event.getMessage();
                            if (msg != null) {
                                String content = getTextContent(msg);
                                if (content != null && !content.isEmpty()) {
                                    fullContent.append(content);
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Failed to process stream event", e);
                        }
                    },
                    error -> {
                        errorRef.set(error);
                        latch.countDown();
                    },
                    () -> {
                        latch.countDown();
                    }
            );
            
            // Wait for completion (with timeout)
            boolean completed = latch.await(60, TimeUnit.SECONDS);
            if (!completed) {
                throw new RuntimeException(new NacosException(NacosException.SERVER_ERROR,
                        "Skill generation timeout after 60 seconds"));
            }
            
            if (errorRef.get() != null) {
                throw new RuntimeException(new NacosException(NacosException.SERVER_ERROR,
                        "Failed to generate skill: " + errorRef.get().getMessage()));
            }
            
            // 8. Parse response
            SkillGenerationResponse generationResponse = new SkillGenerationResponse();
            parseGenerationResult(fullContent.toString(), generationResponse);
            
            return generationResponse;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(new NacosException(NacosException.SERVER_ERROR,
                    "Skill generation interrupted: " + e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("Failed to generate skill", e);
            throw new RuntimeException(new NacosException(NacosException.SERVER_ERROR,
                    "Failed to generate skill: " + e.getMessage()));
        }
    }
    
    @Override
    public void generateSkillStream(SkillGenerationRequest request, 
                                    StreamResponseCallback<SkillGenerationResponse> callback) {
        // 1. Validate request
        if (request == null || StringUtils.isBlank(request.getBackgroundInfo())) {
            callback.onError(new NacosException(NacosException.INVALID_PARAM,
                    "Background information is required"));
            return;
        }
    
        // 2. Check if Copilot is enabled
        if (!agentManager.isEnabled()) {
            callback.onError(new NacosException(NacosException.INVALID_PARAM,
                    "AI 功能未启用：请配置 Copilot API Key。请设置 nacos.copilot.llm.apiKey 或环境变量 COPILOT_API_KEY"));
            return;
        }
        
        // 3. Get system prompt
        String systemPrompt = SkillGenerationPrompt.SYSTEM_PROMPT;
        
        // 4. Build user message
        String userMessage = buildUserMessage(request);
        
        // 5. Create agent with system prompt
        ReActAgent agent = agentManager.createAgent(systemPrompt);
        if (agent == null) {
            callback.onError(new NacosException(NacosException.INVALID_PARAM,
                    "Failed to create Copilot agent. Please check configuration."));
            return;
        }
        
        // 6. Create user message
        Msg userMsg = Msg.builder()
                .textContent(userMessage)
                .build();
        
        // 7. Configure streaming options
        StreamOptions streamOptions = StreamOptions.builder()
                .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                .incremental(true)
                .build();
        
        // 8. Call agent with stream response
        StringBuilder fullContent = new StringBuilder();
        Flux<io.agentscope.core.agent.Event> eventFlux = agent.stream(userMsg, streamOptions)
                .subscribeOn(Schedulers.boundedElastic());
        
        eventFlux.subscribe(new Subscriber<io.agentscope.core.agent.Event>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }
            
            @Override
            public void onNext(io.agentscope.core.agent.Event event) {
                try {
                    Msg msg = event.getMessage();
                    if (msg != null) {
                        String content = getTextContent(msg);
                        if (content != null && !content.isEmpty()) {
                            fullContent.append(content);
                            
                            // Determine response type based on event type
                            StreamResponseType type = StreamResponseType.CONTENT;
                            if (event.getType() == EventType.TOOL_RESULT) {
                                type = StreamResponseType.TOOL_CALL;
                            } else if (event.getType() == EventType.REASONING) {
                                type = StreamResponseType.THINKING;
                            }
                            
                            // Convert to SkillGenerationResponse
                            SkillGenerationResponse genResponse = new SkillGenerationResponse();
                            genResponse.setType(type);
                            genResponse.setChunk(content);
                            genResponse.setDone(false);
                            
                            callback.onNext(genResponse);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to process stream event", e);
                }
            }
            
            @Override
            public void onError(Throwable t) {
                LOGGER.error("Error in AgentScope stream response", t);
                callback.onError(t);
            }
            
            @Override
            public void onComplete() {
                // Parse final result
                SkillGenerationResponse finalResponse = new SkillGenerationResponse();
                finalResponse.setType(StreamResponseType.DONE);
                finalResponse.setDone(true);
                
                try {
                    parseGenerationResult(fullContent.toString(), finalResponse);
                    callback.onNext(finalResponse);
                    callback.onComplete();
                } catch (Exception e) {
                    LOGGER.error("Failed to parse generation result", e);
                    callback.onError(new NacosException(NacosException.SERVER_ERROR,
                            "Failed to parse generated skill: " + e.getMessage()));
                }
            }
        });
    }
    
    private String buildUserMessage(SkillGenerationRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下背景信息生成一个 Claude Skill：\n\n");
        sb.append("背景信息：\n");
        sb.append(request.getBackgroundInfo()).append("\n\n");
        sb.append("请根据 Claude Skill 的最佳实践，生成一个完整的 Skill。");
        
        return sb.toString();
    }
    
    /**
     * Extract text content from Msg.
     */
    private String getTextContent(Msg msg) {
        if (msg == null) {
            return null;
        }
        
        String textContent = msg.getTextContent();
        if (textContent != null && !textContent.isEmpty()) {
            return textContent;
        }
        
        Object content = msg.getContent();
        if (content instanceof String) {
            return (String) content;
        }
        
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private void parseGenerationResult(String fullContent, SkillGenerationResponse response) {
        try {
            // Try to extract JSON from the content
            String jsonContent = extractJsonFromContent(fullContent);
            
            Map<String, Object> result = JacksonUtils.toObj(jsonContent, Map.class);
            
            // Parse skill
            Map<String, Object> skillMap = (Map<String, Object>) result.get("skill");
            if (skillMap != null) {
                Skill skill = JacksonUtils.toObj(JacksonUtils.toJson(skillMap), Skill.class);
                response.setSkill(skill);
            }
            
            // Parse explanation
            String explanation = (String) result.get("explanation");
            if (explanation != null) {
                response.setExplanation(explanation);
            } else {
                response.setExplanation("Skill 已生成完成。");
            }
            
        } catch (Exception e) {
            LOGGER.warn("Failed to parse generation result from LLM response: {}", fullContent, e);
            try {
                throw new NacosException(NacosException.SERVER_ERROR,
                        "Failed to parse generated skill: " + e.getMessage());
            } catch (NacosException nacosException) {
                throw new RuntimeException(nacosException);
            }
        }
    }
    
    private String extractJsonFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        // Try to extract JSON from markdown code blocks
        if (content.contains("```json")) {
            int start = content.indexOf("```json") + 7;
            int end = findMatchingCodeBlockEnd(content, start);
            if (end > start) {
                String extracted = content.substring(start, end).trim();
                if (isValidJson(extracted)) {
                    return extracted;
                }
            }
        } else if (content.contains("```")) {
            int start = content.indexOf("```") + 3;
            int end = findMatchingCodeBlockEnd(content, start);
            if (end > start) {
                String extracted = content.substring(start, end).trim();
                if (isValidJson(extracted)) {
                    return extracted;
                }
            }
        }
        
        // Try to find JSON object by properly matching braces
        String jsonObject = extractJsonObject(content);
        if (jsonObject != null && isValidJson(jsonObject)) {
            return jsonObject;
        }
        
        // If no valid JSON found, return the original content
        return content;
    }
    
    /**
     * Find the matching closing ``` for a code block.
     */
    private int findMatchingCodeBlockEnd(String content, int startPos) {
        int pos = startPos;
        while (pos < content.length()) {
            int nextBacktick = content.indexOf("```", pos);
            if (nextBacktick == -1) {
                return -1;
            }
            if (nextBacktick > startPos
                    && (nextBacktick == 0
                            || content.charAt(nextBacktick - 1) == '\n'
                            || content.substring(Math.max(0, nextBacktick - 10), nextBacktick).trim().isEmpty())) {
                return nextBacktick;
            }
            pos = nextBacktick + 3;
        }
        return -1;
    }
    
    /**
     * Extract JSON object by properly matching braces.
     */
    private String extractJsonObject(String content) {
        int start = content.indexOf("{");
        if (start < 0) {
            return null;
        }
        
        // Find the matching closing brace
        int braceCount = 0;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (inString) {
                continue;
            }
            
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    return content.substring(start, i + 1);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if a string is valid JSON by trying to parse it.
     */
    private boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        try {
            JacksonUtils.toObj(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
