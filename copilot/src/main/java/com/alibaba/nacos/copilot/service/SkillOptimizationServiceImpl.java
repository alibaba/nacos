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

package com.alibaba.nacos.copilot.service;

import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.copilot.adapter.StreamResponseCallback;
import com.alibaba.nacos.copilot.config.CopilotAgentManager;
import com.alibaba.nacos.copilot.capability.prompt.SkillOptimizationPrompt;
import com.alibaba.nacos.copilot.model.SkillOptimizationRequest;
import com.alibaba.nacos.copilot.model.SkillOptimizationResponse;
import com.alibaba.nacos.copilot.model.StreamResponseType;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ThinkingBlock;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill optimization service implementation.
 *
 * @author nacos
 */
@Service
public class SkillOptimizationServiceImpl implements SkillOptimizationService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillOptimizationServiceImpl.class);
    
    private final CopilotAgentManager agentManager;
    
    @Autowired
    public SkillOptimizationServiceImpl(CopilotAgentManager agentManager) {
        this.agentManager = agentManager;
    }
    
    @Override
    public void optimizeSkillStream(SkillOptimizationRequest request, 
                                    StreamResponseCallback<SkillOptimizationResponse> callback) {
        // 1. Validate request
        Skill skill = request.getSkill();
        if (skill == null) {
            callback.onError(new NacosException(NacosException.INVALID_PARAM,
                    "Skill object is required in request"));
            return;
        }
        
        // 2. Check if Copilot is enabled
        if (!agentManager.isEnabled()) {
            callback.onError(new NacosException(NacosException.INVALID_PARAM,
                    "AI 功能未启用：请配置 Copilot API Key。请设置 nacos.copilot.llm.apiKey 或环境变量 COPILOT_API_KEY"));
            return;
        }
        
        // 3. Get system prompt (hardcoded)
        String systemPrompt = SkillOptimizationPrompt.SYSTEM_PROMPT;
        
        // 4. Build user message
        String userMessage = buildUserMessage(skill, request);
        
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
                    if (msg == null) {
                        return;
                    }
                    
                    // First determine response type based on event type and message structure
                            StreamResponseType type = StreamResponseType.CONTENT;
                    String content = null;
                    
                            if (event.getType() == EventType.TOOL_RESULT) {
                        // Tool call: get content from textContent
                                type = StreamResponseType.TOOL_CALL;
                        content = getTextContent(msg);
                    } else if (event.getType() == EventType.REASONING && hasOnlyThinkBlock(msg)) {
                        // Thinking: get content from thinkblock
                                type = StreamResponseType.THINKING;
                        content = getThinkingContent(msg);
                    } else {
                        // Final response or other content: get content from textContent
                        type = StreamResponseType.CONTENT;
                        content = getTextContent(msg);
                            }
                    
                    // Only process if content is not empty
                    if (content != null && !content.isEmpty()) {
                        fullContent.append(content);
                            
                            // Convert to SkillOptimizationResponse
                            SkillOptimizationResponse optResponse = new SkillOptimizationResponse();
                            optResponse.setType(type);
                            optResponse.setChunk(content);
                            optResponse.setDone(false);
                            
                            callback.onNext(optResponse);
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
                SkillOptimizationResponse finalResponse = new SkillOptimizationResponse();
                finalResponse.setType(StreamResponseType.DONE);
                finalResponse.setDone(true);
                
                parseFinalResult(fullContent.toString(), finalResponse);
                callback.onNext(finalResponse);
                callback.onComplete();
            }
        });
    }
    
    private String buildUserMessage(Skill skill, SkillOptimizationRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("请优化以下 Agent Skill：\n\n");
        sb.append("Skill 信息：\n");
        sb.append("- 名称：").append(skill.getName()).append("\n");
        sb.append("- 描述：").append(skill.getDescription()).append("\n");
        sb.append("- 指令：\n").append(skill.getInstruction()).append("\n\n");
        
        if (skill.getResource() != null && !skill.getResource().isEmpty()) {
            sb.append("资源列表：\n");
            skill.getResource().forEach((key, resource) -> {
                sb.append("- ").append(key).append(": ")
                  .append(resource.getName()).append(" (type: ")
                  .append(StringUtils.isNotBlank(resource.getType()) ? resource.getType() : "N/A")
                  .append(")\n");
                if (StringUtils.isNotBlank(resource.getContent())) {
                    sb.append("  内容：").append(resource.getContent()).append("\n");
                }
            });
            sb.append("\n");
        }
        
        if (StringUtils.isNotBlank(request.getOptimizationGoal())) {
            sb.append("优化目标：").append(request.getOptimizationGoal()).append("\n\n");
        }
        
        // Add MCP tools information if provided
        if (request.getParams() != null) {
            Object selectedMcpToolsObj = request.getParams().get("selectedMcpTools");
            if (selectedMcpToolsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> selectedMcpTools = (List<Map<String, Object>>) selectedMcpToolsObj;
                if (selectedMcpTools != null && !selectedMcpTools.isEmpty()) {
                    sb.append("可用的 MCP 工具（可根据 Skill 功能需求合理选择使用）：\n");
                    for (Map<String, Object> tool : selectedMcpTools) {
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
                    sb.append("2. 如果工具对实现 Skill 功能有帮助，则在优化后的 instruction 中详细说明如何调用这些工具，包括：\n");
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
            }
        }
        
        sb.append("请根据 Agent Skill 的最佳实践，优化这个 Skill。");
        
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
    
    /**
     * Check if Msg contains only one thinkblock.
     *
     * @param msg message to check
     * @return true if msg contains only one thinkblock, false otherwise
     */
    private boolean hasOnlyThinkBlock(Msg msg) {
        if (msg == null) {
            return false;
        }
        
        try {
            // Try to get thinkblocks from msg
            // In agentscope, thinkblocks might be accessed via reflection or specific method
            Object content = msg.getContent();
            if (content == null) {
                return false;
            }
            
            // Check if content is a list/array with only one thinkblock element
            if (content instanceof List) {
                List<?> contentList = (List<?>) content;
                if (contentList.size() == 1) {
                    Object firstElement = contentList.get(0);
                    // Check if the element is a thinkblock by checking class name or structure
                    // This might need to be adjusted based on actual agentscope implementation
                    String className = firstElement.getClass().getSimpleName().toLowerCase();
                    return className.contains("think") || className.contains("reasoning");
                }
            }
            
            // Alternative: check via reflection if agentscope provides getThinkBlocks method
            try {
                java.lang.reflect.Method getThinkBlocksMethod = msg.getClass().getMethod("getThinkBlocks");
                if (getThinkBlocksMethod != null) {
                    Object thinkBlocks = getThinkBlocksMethod.invoke(msg);
                    if (thinkBlocks instanceof List) {
                        return ((List<?>) thinkBlocks).size() == 1;
                    }
                }
            } catch (NoSuchMethodException e) {
                // Method doesn't exist, try alternative approach
            }
            
            // If we can't determine, return false to be safe
            return false;
        } catch (Exception e) {
            LOGGER.debug("Failed to check thinkblock in msg", e);
            return false;
        }
    }
    
    /**
     * Extract thinking content from Msg (from thinkblock).
     *
     * @param msg message containing thinkblock
     * @return thinking content, or null if not available
     */
    private String getThinkingContent(Msg msg) {
        if (msg == null) {
            return null;
        }
        
        try {
            // Get thinkblock from msg content
            Object content = msg.getContent();
            if (content instanceof List) {
                List<?> contentList = (List<?>) content;
                if (contentList.size() == 1) {
                    Object element = contentList.get(0);
                    if (element instanceof ThinkingBlock) {
                        ThinkingBlock thinkBlock = (ThinkingBlock) element;
                        return thinkBlock.getThinking();
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            LOGGER.debug("Failed to extract thinking content from msg", e);
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private void parseFinalResult(String fullContent, SkillOptimizationResponse response) {
        try {
            // Try to extract JSON from the content
            String jsonContent = extractJsonFromContent(fullContent);
            
            Map<String, Object> result = JacksonUtils.toObj(jsonContent, Map.class);
            
            // Parse optimizedSkill (only field required)
            Map<String, Object> optimizedSkillMap = (Map<String, Object>) result.get("optimizedSkill");
            if (optimizedSkillMap != null) {
                // Normalize resource structure: handle nested resources (resources.scripts.xxx) to flat structure (resource.xxx)
                normalizeResourceStructure(optimizedSkillMap);
                
                Skill optimizedSkill = JacksonUtils.toObj(JacksonUtils.toJson(optimizedSkillMap), Skill.class);
                response.setOptimizedSkill(optimizedSkill);
            } else {
                // If optimizedSkill is not found, try to parse the entire result as optimizedSkill
                normalizeResourceStructure(result);
                Skill optimizedSkill = JacksonUtils.toObj(JacksonUtils.toJson(result), Skill.class);
                response.setOptimizedSkill(optimizedSkill);
            }
            
            response.setDone(true);
            
        } catch (Exception e) {
            LOGGER.warn("Failed to parse final result from LLM response: {}", fullContent, e);
            // Set done flag even if parsing failed
            response.setDone(true);
            
            // Try to extract optimizedSkill from the content even if JSON parsing failed
            try {
                // Try to extract JSON from markdown code blocks or find JSON object
                String jsonContent = extractJsonFromContent(fullContent);
                if (jsonContent != null && !jsonContent.isEmpty()) {
                    Map<String, Object> result = JacksonUtils.toObj(jsonContent, Map.class);
                    Map<String, Object> optimizedSkillMap = (Map<String, Object>) result.get("optimizedSkill");
                    if (optimizedSkillMap != null) {
                        normalizeResourceStructure(optimizedSkillMap);
                        Skill optimizedSkill = JacksonUtils.toObj(JacksonUtils.toJson(optimizedSkillMap), Skill.class);
                        response.setOptimizedSkill(optimizedSkill);
                    }
                }
            } catch (Exception parseException) {
                LOGGER.warn("Failed to extract optimizedSkill from content", parseException);
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
            // Find the matching closing ```
            int end = findMatchingCodeBlockEnd(content, start);
            if (end > start) {
                String extracted = content.substring(start, end).trim();
                if (isValidJson(extracted)) {
                    return extracted;
                }
            }
        } else if (content.contains("```")) {
            int start = content.indexOf("```") + 3;
            // Find the matching closing ```
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
        // The deserialization will fail gracefully in parseFinalResult
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
            // Check if this is a closing marker (not part of the content)
            // Simple heuristic: if there's a newline before it, it's likely a closing marker
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
        
        // If we didn't find a matching closing brace, return null
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
                skillMap.put("resource", new HashMap<>());
            }
            return;
        }
        
        // If resources is not a Map, skip normalization
        if (!(resourcesObj instanceof Map)) {
            return;
        }
        
        Map<String, Object> resources = (Map<String, Object>) resourcesObj;
        Map<String, Object> flatResourceMap = new HashMap<>();
        
        // Recursively flatten nested resource structure
        flattenResources(resources, flatResourceMap, "");
        
        // Replace "resources" with "resource" (singular) and use flattened structure
        skillMap.remove("resources");
        skillMap.put("resource", flatResourceMap);
    }
    
    /**
     * Recursively flatten nested resource structure.
     * 
     * @param nestedResources nested resource structure
     * @param flatMap output flat resource map
     * @param prefix prefix for resource keys (currently unused, kept for API consistency)
     */
    @SuppressWarnings({"unchecked", "unused"})
    private void flattenResources(Map<String, Object> nestedResources, Map<String, Object> flatMap, String prefix) {
        for (Map.Entry<String, Object> entry : nestedResources.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                Map<String, Object> valueMap = (Map<String, Object>) value;
                
                // Check if this is a resource object (has type, path, name, content, etc.)
                // A resource object should have at least one of: type, path, name, content
                boolean isResourceObject = valueMap.containsKey("type") 
                        || valueMap.containsKey("path") 
                        || valueMap.containsKey("name")
                        || valueMap.containsKey("content");
                
                if (isResourceObject) {
                    // This is a resource object, convert it to SkillResource format
                    Map<String, Object> resourceObj = new HashMap<>();
                    
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
                            } else if ("document".equals(type) || "md".equals(type) || "documentation".equals(type)) {
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
                LOGGER.warn("Unexpected resource value type for key {}: {}", key, value.getClass().getName());
            }
        }
    }
    
}
