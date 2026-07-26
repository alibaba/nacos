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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.exception.NacosException;
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
import io.agentscope.core.message.MsgRole;
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
    
    private static final String SKILL_MD_FILE_NAME = "SKILL.md";
    
    private static final String SKILL_MD_KEY = "skill-md";
    
    private static final String RESOURCE_KEYWORD_EN = "resource";
    
    private static final String RESOURCE_KEYWORD_ZH = "资源";
    
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
        
        // 2. Validate target file name (required)
        if (StringUtils.isBlank(request.getTargetFileName())) {
            callback.onError(new NacosException(NacosException.INVALID_PARAM,
                "Target file name is required. Please select a file to optimize."));
            return;
        }
        
        // 3. Check if Copilot is enabled
        if (!agentManager.isEnabled()) {
            callback.onError(new NacosException(NacosException.INVALID_PARAM,
                "AI 功能未启用：请配置 Copilot API Key。请设置 nacos.copilot.llm.apiKey 或环境变量 COPILOT_API_KEY"));
            return;
        }
        
        // 3. Get system prompt (hardcoded)
        String systemPrompt = SkillOptimizationPrompt.SYSTEM_PROMPT;
        
        // 4. Build conversation messages (user-assistant pairs)
        List<Msg> messages = buildConversationMessages(skill, request);
        
        // 5. Create agent with system prompt
        ReActAgent agent = agentManager.createAgent(systemPrompt);
        if (agent == null) {
            callback.onError(new NacosException(NacosException.INVALID_PARAM,
                "Failed to create Copilot agent. Please check configuration."));
            return;
        }
        
        // 6. Configure streaming options
        StreamOptions streamOptions = StreamOptions.builder()
            .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
            .incremental(true)
            .build();
        
        // 7. Call agent with stream response using message list
        // Frontend will accumulate and parse the content itself, so we don't need to accumulate fullContent
        Flux<io.agentscope.core.agent.Event> eventFlux = agent.stream(messages, streamOptions)
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
    
    /**
     * Build conversation messages as user-assistant-user pairs.
     * Message flow:
     * 1. First user message: Skill content
     * 2. Second assistant message: Acknowledge and ask "你希望我怎么优化这条skill"
     * 3. Third user message: Optimization requirements (goal/tools/history) based on actual situation
     *
     * @param skill the skill to optimize
     * @param request the optimization request
     * @return list of messages forming a conversation
     */
    @SuppressWarnings("PMD.MethodTooLongRule")
    private List<Msg> buildConversationMessages(Skill skill, SkillOptimizationRequest request) {
        List<Msg> messages = new ArrayList<>();
        
        // Check what information is available
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
        
        // Target file name is required (validated above)
        String targetFileName = request.getTargetFileName();
        
        // Message 1: User provides Skill information (only the target file's content)
        StringBuilder skillInfo = new StringBuilder();
        skillInfo.append("名称：").append(skill.getName()).append("\n");
        
        // Check if target file is SKILL.md or a resource file
        boolean isSkillMd =
            SKILL_MD_FILE_NAME.equals(targetFileName) || SKILL_MD_KEY.equals(targetFileName);
        if (isSkillMd) {
            // Target is SKILL.md, include description and full markdown content
            skillInfo.append("描述：").append(skill.getDescription()).append("\n");
            skillInfo.append("SKILL.md：\n").append(skill.getSkillMd()).append("\n");
        } else if (skill.getResource() != null && !skill.getResource().isEmpty()) {
            // Target is a resource file, find and include only that resource
            boolean found = false;
            for (java.util.Map.Entry<String, SkillResource> entry : skill.getResource()
                .entrySet()) {
                String key = entry.getKey();
                SkillResource res = entry.getValue();
                
                // Match by key or name
                boolean matchByKey = key.equals(targetFileName);
                boolean matchByName = res.getName() != null && res.getName().equals(targetFileName);
                if (matchByKey || matchByName) {
                    found = true;
                    skillInfo.append("\n目标文件：").append(key).append("\n");
                    skillInfo.append("文件名：").append(res.getName()).append("\n");
                    if (StringUtils.isNotBlank(res.getType())) {
                        skillInfo.append("类型：").append(res.getType()).append("\n");
                    }
                    if (StringUtils.isNotBlank(res.getContent())) {
                        skillInfo.append("内容：\n").append(res.getContent()).append("\n");
                    }
                    break;
                }
            }
            if (!found) {
                // File not found, include all resources for context
                skillInfo.append("\n注意：未找到指定的文件 ").append(targetFileName)
                    .append("，以下是所有资源文件供参考：\n");
                skill.getResource().forEach((key, resource) -> {
                    skillInfo.append("- ").append(key).append(": ")
                        .append(resource.getName());
                    if (StringUtils.isNotBlank(resource.getType())) {
                        skillInfo.append(" (type: ").append(resource.getType()).append(")");
                    }
                    skillInfo.append("\n");
                });
            }
        }
        
        messages.add(Msg.builder()
            .textContent(skillInfo.toString())
            .role(MsgRole.USER)
            .build());
        
        // Message 2: Assistant acknowledges and asks how to optimize
        messages.add(Msg.builder()
            .textContent("我已经收到了这个 Skill 的信息。你希望我怎么优化这条 skill？")
            .role(MsgRole.ASSISTANT)
            .build());
        
        // Message 3: User provides optimization requirements based on actual situation
        // Order: Conversation History > MCP Tools > Optimization Goal (last, highest priority via recency effect)
        // If no specific requirements, merge into a simpler structure
        if (!hasConversationHistory && !hasSelectedTools && !hasOptimizationGoal) {
            // Simplest case: user just wants to try the feature, check for obvious improvements
            String simpleRequest = "请帮我看看这个文件（" + targetFileName + "）有没有明显可以优化的地方。"
                + "请只优化这个文件的内容，其他文件保持不变。如果没有明显问题，保持原样即可。";
            messages.add(Msg.builder()
                .textContent(simpleRequest)
                .role(MsgRole.USER)
                .build());
        } else {
            // Complex case: build structured optimization request
            StringBuilder optimizationRequest = new StringBuilder();
            
            // Part 1: Conversation history (if available)
            if (hasConversationHistory) {
                optimizationRequest.append("以下是一段对话交互历史。请仔细分析这段对话，完成以下任务：\n");
                optimizationRequest.append("1. 分析对话中的交互场景：识别用户的需求、助手的处理逻辑、工具调用的模式和流程\n");
                optimizationRequest.append("2. 将对话场景沉淀为标准流程：提取出可复用的标准操作步骤和决策逻辑\n");
                optimizationRequest.append(
                    "3. 基于沉淀的标准流程优化 Skill：将分析出的标准流程融入到 Skill 的 instruction 中，确保 Skill 能够支持类似的对话场景\n\n");
                
                ConversationHistory history = request.getConversationHistory();
                if (StringUtils.isNotBlank(history.getTitle())) {
                    optimizationRequest.append("对话主题：").append(history.getTitle()).append("\n");
                }
                if (StringUtils.isNotBlank(history.getContext())) {
                    optimizationRequest.append("对话上下文：").append(history.getContext()).append("\n");
                }
                optimizationRequest.append("\n对话交互内容：\n");
                
                for (ConversationMessage message : history.getMessages()) {
                    if ("user".equalsIgnoreCase(message.getType())) {
                        optimizationRequest.append("用户：").append(message.getContent()).append("\n");
                    } else if ("tool_call".equalsIgnoreCase(message.getType())) {
                        optimizationRequest.append("工具调用：");
                        if (StringUtils.isNotBlank(message.getToolName())) {
                            optimizationRequest.append(message.getToolName());
                        }
                        if (message.getToolInput() != null && !message.getToolInput().isEmpty()) {
                            optimizationRequest.append("，参数：").append(message.getToolInput());
                        }
                        if (message.getToolOutput() != null) {
                            optimizationRequest.append("，结果：").append(message.getToolOutput());
                        }
                        optimizationRequest.append("\n");
                    } else if ("model".equalsIgnoreCase(message.getType())) {
                        optimizationRequest.append("助手：").append(message.getContent()).append("\n");
                    }
                }
            }
            
            // Part 2: MCP tools (if available)
            if (hasSelectedTools && selectedMcpTools != null) {
                if (hasConversationHistory) {
                    optimizationRequest.append("\n");
                }
                optimizationRequest.append("我希望将以下 MCP 工具整合到这个 Skill 中：\n\n");
                for (Map<String, Object> tool : selectedMcpTools) {
                    optimizationRequest.append("工具：").append(tool.get("name")).append("\n");
                    if (tool.get("description") != null) {
                        optimizationRequest.append("描述：").append(tool.get("description"))
                            .append("\n");
                    }
                    if (tool.get("inputSchema") != null) {
                        optimizationRequest.append("参数：").append(tool.get("inputSchema"))
                            .append("\n");
                    }
                    optimizationRequest.append("\n");
                }
            }
            
            // Part 3: Optimization goal (last, highest priority via recency effect)
            if (hasOptimizationGoal) {
                if (hasConversationHistory || hasSelectedTools) {
                    optimizationRequest.append("\n");
                }
                optimizationRequest.append("【重要】我的优化目标是：").append(request.getOptimizationGoal())
                    .append("\n");
                optimizationRequest.append("请优先考虑并聚焦于这个优化目标，所有优化建议和改动都应该围绕这个目标展开。");
                
                // 如果优化目标中包含"资源"相关关键词，特别强调不要添加SKILL.md
                String optimizationGoalLower = request.getOptimizationGoal().toLowerCase();
                boolean containsResourceKeyword =
                    optimizationGoalLower.contains(RESOURCE_KEYWORD_ZH)
                        || optimizationGoalLower.contains(RESOURCE_KEYWORD_EN)
                        || optimizationGoalLower.contains("增加")
                        || optimizationGoalLower.contains("添加")
                        || optimizationGoalLower.contains("add")
                        || optimizationGoalLower.contains("增加资源")
                        || optimizationGoalLower.contains("添加资源");
                if (containsResourceKeyword) {
                    optimizationRequest.append("\n【绝对禁止】如果优化目标涉及添加或增加资源，请注意：");
                    optimizationRequest.append("\n- 绝对不能将 SKILL.md 放在 resource 字段中");
                    optimizationRequest.append("\n- 绝对不能创建名为 SKILL.md 的资源文件");
                    optimizationRequest
                        .append("\n- 绝对不能将 SKILL.md 放在任何资源类型（template、data、script 等）下");
                    optimizationRequest
                        .append("\n- SKILL.md 是特殊的元数据文件，位于 skillMd 字段，不需要也不应该在 resource 中定义");
                    optimizationRequest
                        .append("\n- 只能添加真正的资源文件（如 .json、.yaml、.txt 等），绝对不能添加 SKILL.md");
                }
            }
            
            // Part 4: Final request with context-aware emphasis
            optimizationRequest.append("\n\n");
            
            // Add target file constraint (targetFileName is required)
            String targetFileConstraint = "【重要】请只优化文件 " + targetFileName + " 的内容，其他文件保持不变。";
            
            // Always add SKILL.md constraint
            optimizationRequest.append("\n【绝对禁止】无论优化目标是什么，都绝对不能：");
            optimizationRequest.append("\n- 将 SKILL.md 放在 resource 字段中");
            optimizationRequest.append("\n- 创建名为 SKILL.md 的资源文件");
            optimizationRequest.append("\n- 将 SKILL.md 放在任何资源类型下");
            
            if (hasOptimizationGoal) {
                optimizationRequest.append(targetFileConstraint).append(" ");
                optimizationRequest.append("请基于以上要求优化这个文件，务必优先满足我的优化目标");
                if (hasConversationHistory && hasSelectedTools) {
                    optimizationRequest.append("，同时将从对话历史中分析出的标准流程融入到优化方案中，并确保工具整合服务于优化目标");
                } else if (hasConversationHistory) {
                    optimizationRequest.append("，同时将从对话历史中分析出的标准流程融入到优化方案中");
                } else if (hasSelectedTools) {
                    optimizationRequest.append("，并确保工具整合服务于优化目标");
                }
                optimizationRequest.append("。");
            } else if (hasConversationHistory) {
                optimizationRequest.append(targetFileConstraint).append(" ");
                optimizationRequest.append("请基于以上要求，特别是从对话历史中分析出的标准流程，优化这个文件");
                if (hasSelectedTools) {
                    optimizationRequest.append("，并整合上述工具");
                }
                optimizationRequest.append("。");
            } else if (hasSelectedTools) {
                optimizationRequest.append(targetFileConstraint).append(" ");
                optimizationRequest.append("请基于以上要求，整合上述工具并优化这个文件。");
            } else {
                optimizationRequest.append(targetFileConstraint).append(" ");
                optimizationRequest.append("请基于以上要求优化这个文件。");
            }
            
            messages.add(Msg.builder()
                .textContent(optimizationRequest.toString())
                .role(MsgRole.USER)
                .build());
        }
        
        return messages;
    }
    
}
