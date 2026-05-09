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
import com.alibaba.nacos.copilot.model.ConversationHistory;
import com.alibaba.nacos.copilot.model.ConversationMessage;
import com.alibaba.nacos.copilot.model.SkillGenerationRequest;
import com.alibaba.nacos.copilot.model.SkillGenerationResponse;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
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
    
    private static final String JSON_CODE_BLOCK = "```json";
    
    private static final String CODE_BLOCK = "```";
    
    private final CopilotAgentManager agentManager;
    
    @Autowired
    public SkillGenerationServiceImpl(CopilotAgentManager agentManager) {
        this.agentManager = agentManager;
    }
    
    @Override
    @SuppressWarnings("PMD.MethodTooLongRule")
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
                            String content = StreamEventProcessor.getTextContent(msg);
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
                });
            
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
    @SuppressWarnings("PMD.MethodTooLongRule")
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
        // Frontend will accumulate and parse the content itself, so we don't need to accumulate fullContent
        Flux<io.agentscope.core.agent.Event> eventFlux = agent.stream(userMsg, streamOptions)
            .subscribeOn(Schedulers.boundedElastic());
        
        eventFlux.subscribe(StreamEventProcessor.createSubscriber(
            (type, content, done) -> {
                SkillGenerationResponse response = new SkillGenerationResponse();
                response.setType(type);
                response.setChunk(content);
                response.setDone(done);
                return response;
            },
            callback));
    }
    
    @SuppressWarnings("PMD.MethodTooLongRule")
    private String buildUserMessage(SkillGenerationRequest request) {
        StringBuilder sb = new StringBuilder();
        boolean hasConversationHistory = request.getConversationHistory() != null
            && request.getConversationHistory().getMessages() != null
            && !request.getConversationHistory().getMessages().isEmpty();
        
        // Add conversation history analysis if provided
        if (hasConversationHistory) {
            sb.append("对话历史分析（请充分理解这段对话历史，判断是否适合沉淀为一个 Skill）：\n\n");
            ConversationHistory history = request.getConversationHistory();
            if (StringUtils.isNotBlank(history.getTitle())) {
                sb.append("对话主题：").append(history.getTitle()).append("\n");
            }
            if (StringUtils.isNotBlank(history.getContext())) {
                sb.append("对话上下文：").append(history.getContext()).append("\n");
            }
            sb.append("\n对话内容：\n");
            int messageIndex = 1;
            for (ConversationMessage message : history.getMessages()) {
                sb.append("[").append(messageIndex++).append("] ");
                if ("user".equalsIgnoreCase(message.getType())) {
                    sb.append("用户输入：").append(message.getContent()).append("\n");
                } else if ("tool_call".equalsIgnoreCase(message.getType())) {
                    sb.append("工具调用：");
                    if (StringUtils.isNotBlank(message.getToolName())) {
                        sb.append(message.getToolName());
                    }
                    if (message.getToolInput() != null && !message.getToolInput().isEmpty()) {
                        sb.append("，输入参数：").append(message.getToolInput());
                    }
                    if (message.getToolOutput() != null) {
                        sb.append("，输出结果：").append(message.getToolOutput());
                    }
                    sb.append("\n");
                } else if ("model".equalsIgnoreCase(message.getType())) {
                    sb.append("模型回复：").append(message.getContent()).append("\n");
                } else {
                    sb.append(message.getType()).append("：");
                    if (StringUtils.isNotBlank(message.getContent())) {
                        sb.append(message.getContent());
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n对话历史分析要求：\n");
            sb.append("1. 请充分理解这段对话历史，包括用户输入、工具调用、模型回复的完整流程\n");
            sb.append("2. 判断这段对话历史是否适合沉淀为一个 Skill\n");
            sb.append("3. 如果适合，请识别对话历史中的关键信息：\n");
            sb.append("   - 用户的实际需求和意图\n");
            sb.append("   - 工具调用的模式和逻辑\n");
            sb.append("   - 模型回复的策略和方式\n");
            sb.append("   - 对话中体现出的 Skill 应该具备的核心能力\n");
            sb.append("4. 基于对话历史分析，生成一个能够复现类似对话场景的 Skill\n");
            sb.append("5. 如果对话历史中涉及工具调用，请在生成的 Skill instruction 中详细说明如何调用这些工具\n");
            sb.append("6. 如果对话历史中体现了特定的处理逻辑或策略，请在生成的 Skill instruction 中体现这些逻辑\n\n");
        }
        
        sb.append("请根据以下背景信息生成一个 Agent Skill：\n\n");
        sb.append("背景信息：\n");
        sb.append(request.getBackgroundInfo()).append("\n\n");
        
        // Add MCP tools information if provided
        if (request.getSelectedMcpTools() != null && !request.getSelectedMcpTools().isEmpty()) {
            sb.append("可用的 MCP 工具（可根据 Skill 功能需求合理选择使用）：\n");
            for (Map<String, Object> tool : request.getSelectedMcpTools()) {
                sb.append("- 工具名称：").append(tool.get("name")).append("\n");
                if (tool.get("description") != null) {
                    sb.append("  描述：").append(tool.get("description")).append("\n");
                }
                if (tool.get("inputSchema") != null) {
                    sb.append("  输入参数：").append(tool.get("inputSchema")).append("\n");
                }
                sb.append("\n");
            }
            sb.append("工具使用说明：\n");
            sb.append("1. 请根据 Skill 的功能需求和上下文，合理判断是否需要使用这些工具\n");
            sb.append("2. 如果工具对实现 Skill 功能有帮助，则在 instruction 中详细说明如何调用这些工具，包括：\n");
            sb.append("   - 工具名称和用途\n");
            sb.append("   - 调用时机（在什么情况下调用该工具）\n");
            sb.append("   - 输入参数说明（每个参数的含义、类型、是否必需、如何获取）\n");
            sb.append("   - 输出结果处理（如何处理工具返回的结果，如何解析和使用返回数据）\n");
            sb.append("   - 错误处理（工具调用失败时的处理方式和备选方案）\n");
            sb.append("3. 如果工具对实现 Skill 功能没有帮助，则不需要在 instruction 中提及这些工具\n");
            sb.append("4. 如果使用了工具，确保工具调用逻辑清晰、可执行，工具应该与 Skill 功能紧密结合\n");
            sb.append("5. 如果使用了多个工具，在 instruction 中明确说明工具调用的步骤和流程，包括工具调用的顺序\n");
            sb.append("6. 如果使用了工具，提供具体的工具调用示例，说明如何构造参数、调用工具、处理结果\n\n");
        }
        
        sb.append("请根据 Agent Skill 的最佳实践，生成一个完整、高质量、可直接使用的 Skill。");
        
        return sb.toString();
    }
    
    @SuppressWarnings("unchecked")
    private void parseGenerationResult(String fullContent, SkillGenerationResponse response) {
        try {
            // Try to extract JSON from the content
            String jsonContent = extractJsonFromContent(fullContent);
            
            Map<String, Object> result = JacksonUtils.toObj(jsonContent, Map.class);
            
            // Parse skill (only field required)
            Map<String, Object> skillMap = (Map<String, Object>) result.get("skill");
            if (skillMap != null) {
                // Normalize resource structure: handle nested resources (resources.scripts.xxx) to flat structure (resource.xxx)
                normalizeResourceStructure(skillMap);
                normalizeSkillMdField(skillMap);
                
                Skill skill = JacksonUtils.toObj(JacksonUtils.toJson(skillMap), Skill.class);
                response.setSkill(skill);
            } else {
                // If skill is not found, try to parse the entire result as skill
                normalizeResourceStructure(result);
                normalizeSkillMdField(result);
                Skill skill = JacksonUtils.toObj(JacksonUtils.toJson(result), Skill.class);
                response.setSkill(skill);
            }
            
            response.setDone(true);
            
        } catch (Exception e) {
            LOGGER.warn("Failed to parse generation result from LLM response: {}", fullContent, e);
            // Set done flag even if parsing failed
            response.setDone(true);
            
            // Try to extract skill from the content even if JSON parsing failed
            try {
                // Try to extract JSON from markdown code blocks or find JSON object
                String jsonContent = extractJsonFromContent(fullContent);
                if (jsonContent != null && !jsonContent.isEmpty()) {
                    Map<String, Object> result = JacksonUtils.toObj(jsonContent, Map.class);
                    Map<String, Object> skillMap = (Map<String, Object>) result.get("skill");
                    if (skillMap != null) {
                        normalizeResourceStructure(skillMap);
                        normalizeSkillMdField(skillMap);
                        Skill skill =
                            JacksonUtils.toObj(JacksonUtils.toJson(skillMap), Skill.class);
                        response.setSkill(skill);
                    }
                }
            } catch (Exception parseException) {
                LOGGER.warn("Failed to extract skill from content", parseException);
            }
        }
    }
    
    private String extractJsonFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        // Try to extract JSON from markdown code blocks
        if (content.contains(JSON_CODE_BLOCK)) {
            int start = content.indexOf(JSON_CODE_BLOCK) + JSON_CODE_BLOCK.length();
            int end = findMatchingCodeBlockEnd(content, start);
            if (end > start) {
                String extracted = content.substring(start, end).trim();
                if (isValidJson(extracted)) {
                    return extracted;
                }
            }
        } else if (content.contains(CODE_BLOCK)) {
            int start = content.indexOf(CODE_BLOCK) + CODE_BLOCK.length();
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
            int nextBacktick = content.indexOf(CODE_BLOCK, pos);
            if (nextBacktick == -1) {
                return -1;
            }
            @SuppressWarnings("PMD.AvoidComplexConditionRule")
            boolean isClosingMarker = nextBacktick > startPos
                && (nextBacktick == 0
                    || content.charAt(nextBacktick - 1) == '\n'
                    || content.substring(Math.max(0, nextBacktick - 10), nextBacktick).trim()
                        .isEmpty());
            if (isClosingMarker) {
                return nextBacktick;
            }
            pos = nextBacktick + CODE_BLOCK.length();
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
    
    /**
     * Normalize resource structure from nested format to flat format.
     * Handles cases where LLM returns nested resources like:
     * {
     *   "resources": {
     *     "scripts": {
     *       "check_permission": { "type": "script", "path": "/scripts/check_permission.sh" }
     *     }
     *   }
     * }
     * Converts to flat format:
     * {
     *   "resource": {
     *     "check_permission": {
     *       "name": "check_permission.sh",
     *       "type": "script",
     *       "content": ""
     *     }
     *   }
     * }
     */
    @SuppressWarnings("unchecked")
    private void normalizeResourceStructure(Map<String, Object> skillMap) {
        // Check if there's a nested "resources" structure
        Object resourcesObj = skillMap.get("resources");
        if (resourcesObj == null) {
            // No nested resources, check if "resource" already exists
            Object resourceObj = skillMap.get("resource");
            if (resourceObj == null || !(resourceObj instanceof Map)) {
                skillMap.put("resource", new HashMap<>(16));
            }
            return;
        }
        
        // If resources is not a Map, skip normalization
        if (!(resourcesObj instanceof Map)) {
            return;
        }
        
        Map<String, Object> resources = (Map<String, Object>) resourcesObj;
        Map<String, Object> flatResourceMap = new HashMap<>(16);
        
        // Recursively flatten nested resource structure
        flattenResources(resources, flatResourceMap, "");
        
        // Replace "resources" with "resource" (singular) and use flattened structure
        skillMap.remove("resources");
        skillMap.put("resource", flatResourceMap);
    }
    
    /**
     * Accept legacy "instruction" field from model output and map it to skillMd.
     */
    private void normalizeSkillMdField(Map<String, Object> skillMap) {
        if (skillMap == null) {
            return;
        }
        Object skillMd = skillMap.get("skillMd");
        if (skillMd instanceof String && !((String) skillMd).isEmpty()) {
            return;
        }
        Object instruction = skillMap.get("instruction");
        if (instruction instanceof String) {
            skillMap.put("skillMd", instruction);
        }
    }
    
    /**
     * Recursively flatten nested resource structure.
     *
     * @param nestedResources nested resource structure
     * @param flatMap output flat resource map
     * @param prefix prefix for resource keys (currently unused, kept for API consistency)
     */
    @SuppressWarnings({"unchecked", "unused"})
    private void flattenResources(Map<String, Object> nestedResources, Map<String, Object> flatMap,
        String prefix) {
        for (Map.Entry<String, Object> entry : nestedResources.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                Map<String, Object> valueMap = (Map<String, Object>) value;
                
                // Check if this is a resource object (has type, path, name, content, etc.)
                // A resource object should have at least one of: type, path, name, content
                @SuppressWarnings("PMD.AvoidComplexConditionRule")
                boolean isResourceObject = valueMap.containsKey("type")
                    || valueMap.containsKey("path")
                    || valueMap.containsKey("name")
                    || valueMap.containsKey("content");
                
                if (isResourceObject) {
                    // This is a resource object, convert it to SkillResource format
                    Map<String, Object> resourceObj = new HashMap<>(8);
                    
                    // Extract name from path or use key
                    String name = (String) valueMap.get("name");
                    if (name == null || name.isEmpty()) {
                        String path = (String) valueMap.get("path");
                        if (path != null && !path.isEmpty()) {
                            // Extract filename from path
                            int lastSlash = path.lastIndexOf('/');
                            name = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
                        } else {
                            // Use key as name, add appropriate extension based on type
                            String type = (String) valueMap.getOrDefault("type", "");
                            if ("script".equals(type) || "sh".equals(type)) {
                                name = key + ".sh";
                            } else if ("template".equals(type) || "json".equals(type)) {
                                name = key + ".json";
                            } else if ("document".equals(type) || "md".equals(type)
                                || "documentation".equals(type)) {
                                name = key + ".md";
                            } else {
                                name = key;
                            }
                        }
                    }
                    resourceObj.put("name", name);
                    resourceObj.put("type", valueMap.getOrDefault("type", ""));
                    // Try to get content from multiple possible fields
                    String content = (String) valueMap.get("content");
                    if (content == null || content.isEmpty()) {
                        content = (String) valueMap.get("text");
                    }
                    if (content == null || content.isEmpty()) {
                        content = (String) valueMap.get("body");
                    }
                    if (content == null || content.isEmpty()) {
                        content = (String) valueMap.get("data");
                    }
                    resourceObj.put("content", content != null ? content : "");
                    resourceObj.put("metadata", valueMap.getOrDefault("metadata", null));
                    
                    // Use the resource key (not prefix_key) as the map key
                    // This ensures resources.scripts.check_permission becomes resource.check_permission
                    flatMap.put(key, resourceObj);
                } else {
                    // This is a nested category (like "scripts", "documentation"), continue flattening
                    // Don't add prefix for category names, just pass them through
                    flattenResources(valueMap, flatMap, "");
                }
            } else {
                // Not a Map, skip
                LOGGER.warn("Unexpected resource value type for key {}: {}", key,
                    value.getClass().getName());
            }
        }
    }
}
