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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.copilot.adapter.StreamResponseCallback;
import com.alibaba.nacos.copilot.capability.prompt.SkillOptimizationPrompt;
import com.alibaba.nacos.copilot.config.CopilotAgentManager;
import com.alibaba.nacos.copilot.model.ConversationHistory;
import com.alibaba.nacos.copilot.model.ConversationMessage;
import com.alibaba.nacos.copilot.model.SkillOptimizationRequest;
import com.alibaba.nacos.copilot.model.SkillOptimizationResponse;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Skill optimization service implementation.
 *
 * @author nacos
 */
@Service
public class SkillOptimizationServiceImpl implements SkillOptimizationService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillOptimizationServiceImpl.class);
    
    private static final String JSON_CODE_BLOCK = "```json";
    
    private static final String CODE_BLOCK = "```";
    
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
        // Frontend will accumulate and parse the content itself, so we don't need to accumulate fullContent
        Flux<io.agentscope.core.agent.Event> eventFlux = agent.stream(userMsg, streamOptions)
                .subscribeOn(Schedulers.boundedElastic());
        
        eventFlux.subscribe(StreamEventProcessor.createSubscriber(
                (type, content, done) -> {
                    SkillOptimizationResponse response = new SkillOptimizationResponse();
                    response.setType(type);
                    response.setChunk(content);
                    response.setDone(done);
                    return response;
                },
                callback));
    }
    
    @SuppressWarnings("PMD.MethodTooLongRule")
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
        
        // Build optimization instructions based on user input
        boolean hasOptimizationGoal = StringUtils.isNotBlank(request.getOptimizationGoal());
        boolean hasSelectedTools = false;
        List<Map<String, Object>> selectedMcpTools = null;
        boolean hasConversationHistory = request.getConversationHistory() != null
                && request.getConversationHistory().getMessages() != null
                && !request.getConversationHistory().getMessages().isEmpty();

        if (request.getParams() != null) {
            Object selectedMcpToolsObj = request.getParams().get("selectedMcpTools");
            if (selectedMcpToolsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tools = (List<Map<String, Object>>) selectedMcpToolsObj;
                if (tools != null && !tools.isEmpty()) {
                    hasSelectedTools = true;
                    selectedMcpTools = tools;
                }
            }
        }

        // Add conversation history analysis if provided
        if (hasConversationHistory) {
            sb.append("对话历史分析（请充分理解这段对话历史，判断是否适合对当前 Skill 进行优化）：\n\n");
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
            sb.append("2. 分析这段对话历史是否适合对当前 Skill 进行优化\n");
            sb.append("3. 如果适合优化，请识别对话历史中的关键信息：\n");
            sb.append("   - 用户的实际需求和意图\n");
            sb.append("   - 工具调用的模式和逻辑\n");
            sb.append("   - 模型回复的策略和方式\n");
            sb.append("   - 对话中体现出的 Skill 应该具备的能力\n");
            sb.append("4. 基于对话历史分析，对当前 Skill 进行针对性优化，确保优化后的 Skill 能够更好地支持类似的对话场景\n");
            sb.append("5. 如果对话历史中涉及工具调用，请确保优化后的 Skill 能够正确使用这些工具\n");
            sb.append("6. 如果对话历史中体现了特定的处理逻辑或策略，请在优化后的 Skill instruction 中体现这些逻辑\n\n");
        }

        // Add optimization goal with emphasis
        if (hasOptimizationGoal) {
            sb.append("用户优化目标（请聚焦于此方向进行优化）：\n");
            sb.append(request.getOptimizationGoal()).append("\n");
            sb.append("重要提示：整体优化思路应该聚焦于用户的优化方向，所有优化建议和改动都应该围绕这个目标展开。\n\n");
        }

        // Add MCP tools information if provided
        if (hasSelectedTools) {
            sb.append("用户选择的 MCP 工具（用户希望在 Skill 中增加对这些工具的调用）：\n");
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
            sb.append("工具集成要求：\n");
            sb.append("1. 用户选择了这些工具，说明用户希望在优化后的 Skill 中增加对这些工具的调用\n");
            sb.append("2. 请在优化后的 instruction 中详细说明如何调用这些工具，包括：\n");
            sb.append("   - 工具名称和用途\n");
            sb.append("   - 调用时机（在什么情况下调用该工具）\n");
            sb.append("   - 输入参数说明（每个参数的含义、类型、是否必需、如何获取）\n");
            sb.append("   - 输出结果处理（如何处理工具返回的结果，如何解析和使用返回数据）\n");
            sb.append("   - 错误处理（工具调用失败时的处理方式和备选方案）\n");
            sb.append("3. 确保工具调用逻辑清晰、可执行，工具应该与 Skill 功能紧密结合\n");
            if (selectedMcpTools.size() > 1) {
                sb.append("4. 在 instruction 中明确说明工具调用的步骤和流程，包括工具调用的顺序\n");
            }
            sb.append("5. 提供具体的工具调用示例，说明如何构造参数、调用工具、处理结果\n\n");
        }

        // Build final instruction
        if (hasOptimizationGoal && hasSelectedTools) {
            sb.append("请根据用户的优化目标和选择的工具，结合 Agent Skill 的最佳实践，优化这个 Skill。");
        } else if (hasOptimizationGoal) {
            sb.append("请聚焦于用户的优化目标，结合 Agent Skill 的最佳实践，优化这个 Skill。");
        } else if (hasSelectedTools) {
            sb.append("请根据用户选择的工具，结合 Agent Skill 的最佳实践，优化这个 Skill，确保在 Skill 中增加对这些工具的调用。");
        } else {
            sb.append("请根据 Agent Skill 的最佳实践，优化这个 Skill。");
        }
        
        return sb.toString();
    }
    
    
    private String extractJsonFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        // Try to extract JSON from markdown code blocks
        if (content.contains(JSON_CODE_BLOCK)) {
            int start = content.indexOf(JSON_CODE_BLOCK) + JSON_CODE_BLOCK.length();
            // Find the matching closing ```
            int end = findMatchingCodeBlockEnd(content, start);
            if (end > start) {
                String extracted = content.substring(start, end).trim();
                if (isValidJson(extracted)) {
                    return extracted;
                }
            }
        } else if (content.contains(CODE_BLOCK)) {
            int start = content.indexOf(CODE_BLOCK) + CODE_BLOCK.length();
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
            int nextBacktick = content.indexOf(CODE_BLOCK, pos);
            if (nextBacktick == -1) {
                return -1;
            }
            // Check if this is a closing marker (not part of the content)
            // Simple heuristic: if there's a newline before it, it's likely a closing marker
            @SuppressWarnings("PMD.AvoidComplexConditionRule")
            boolean isClosingMarker = nextBacktick > startPos
                    && (nextBacktick == 0
                            || content.charAt(nextBacktick - 1) == '\n'
                            || content.substring(Math.max(0, nextBacktick - 10), nextBacktick).trim().isEmpty());
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
