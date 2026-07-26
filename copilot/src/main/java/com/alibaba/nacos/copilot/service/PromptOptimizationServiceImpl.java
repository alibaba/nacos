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
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.copilot.adapter.StreamResponseCallback;
import com.alibaba.nacos.copilot.capability.prompt.PromptOptimizationPrompt;
import com.alibaba.nacos.copilot.config.CopilotAgentManager;
import com.alibaba.nacos.copilot.model.PromptOptimizationRequest;
import com.alibaba.nacos.copilot.model.PromptOptimizationResponse;
import com.alibaba.nacos.copilot.model.StreamResponseType;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Prompt optimization service implementation.
 *
 * @author nacos
 */
@Service
public class PromptOptimizationServiceImpl implements PromptOptimizationService {
    
    private final CopilotAgentManager agentManager;
    
    @Autowired
    public PromptOptimizationServiceImpl(CopilotAgentManager agentManager) {
        this.agentManager = agentManager;
    }
    
    @Override
    public void optimizePromptStream(PromptOptimizationRequest request,
        StreamResponseCallback<PromptOptimizationResponse> callback) {
        // 1. Validate request
        if (StringUtils.isBlank(request.getPrompt())) {
            callback
                .onError(new NacosException(NacosException.INVALID_PARAM, "Prompt is required"));
            return;
        }
        
        // 2. Check if Copilot is enabled
        if (!agentManager.isEnabled()) {
            callback.onError(new NacosException(NacosException.INVALID_PARAM,
                "AI 功能未启用：请配置 Copilot API Key。请设置 nacos.copilot.llm.apiKey 或环境变量 COPILOT_API_KEY"));
            return;
        }
        
        // 3. Get system prompt
        String systemPrompt = PromptOptimizationPrompt.SYSTEM_PROMPT;
        
        // 4. Build user message
        String userMessage = buildUserMessage(request);
        
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
        
        // 7. Create user message
        Msg userMsg = Msg.builder()
            .textContent(userMessage)
            .build();
        
        // 8. Call agent with stream response
        // Frontend will accumulate and parse the content itself
        Flux<io.agentscope.core.agent.Event> eventFlux = agent.stream(userMsg, streamOptions)
            .subscribeOn(Schedulers.boundedElastic());
        
        eventFlux.subscribe(StreamEventProcessor.createSubscriber(
            (type, content, done) -> {
                // Filter out THINKING type - don't expose to users
                if (type == StreamResponseType.THINKING) {
                    return null;
                }
                PromptOptimizationResponse response = new PromptOptimizationResponse();
                response.setType(type);
                response.setChunk(content);
                response.setDone(done);
                return response;
            },
            callback));
    }
    
    /**
     * Build user message for prompt optimization.
     *
     * @param request optimization request
     * @return formatted user message
     */
    private String buildUserMessage(PromptOptimizationRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("请优化以下 Prompt：\n\n");
        sb.append("【原始 Prompt】\n");
        sb.append(request.getPrompt());
        
        if (StringUtils.isNotBlank(request.getOptimizationGoal())) {
            sb.append("\n\n【优化目标】\n");
            sb.append(request.getOptimizationGoal());
        }
        
        return sb.toString();
    }
}
