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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.copilot.adapter.StreamResponseCallback;
import com.alibaba.nacos.copilot.capability.prompt.SkillGenerationPrompt;
import com.alibaba.nacos.copilot.config.CopilotAgentManager;
import com.alibaba.nacos.copilot.model.ConversationHistory;
import com.alibaba.nacos.copilot.model.ConversationMessage;
import com.alibaba.nacos.copilot.model.SkillGenerationRequest;
import com.alibaba.nacos.copilot.model.SkillGenerationResponse;
import com.alibaba.nacos.common.utils.StringUtils;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * Skill generation service implementation.
 *
 * @author nacos
 */
@Service
public class SkillGenerationServiceImpl implements SkillGenerationService {
    
    private final CopilotAgentManager agentManager;
    
    @Autowired
    public SkillGenerationServiceImpl(CopilotAgentManager agentManager) {
        this.agentManager = agentManager;
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
}
