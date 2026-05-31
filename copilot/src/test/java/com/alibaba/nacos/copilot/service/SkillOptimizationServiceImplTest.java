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
import com.alibaba.nacos.copilot.adapter.StreamResponseCallback;
import com.alibaba.nacos.copilot.config.CopilotAgentManager;
import com.alibaba.nacos.copilot.model.SkillOptimizationRequest;
import com.alibaba.nacos.copilot.model.SkillOptimizationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for SkillOptimizationServiceImpl.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class SkillOptimizationServiceImplTest {
    
    @Mock
    private CopilotAgentManager agentManager;
    
    private SkillOptimizationServiceImpl skillOptimizationService;
    
    @BeforeEach
    void setUp() {
        skillOptimizationService = new SkillOptimizationServiceImpl(agentManager);
    }
    
    @Test
    void testOptimizeSkillStreamWithNullSkill() {
        // Given
        SkillOptimizationRequest request = new SkillOptimizationRequest();
        request.setSkill(null);
        StreamResponseCallback<SkillOptimizationResponse> callback =
            new StreamResponseCallback<SkillOptimizationResponse>() {
                
                @Override
                public void onNext(SkillOptimizationResponse response) {
                }
                
                @Override
                public void onError(Throwable t) {
                }
                
                @Override
                public void onComplete() {
                }
            };
        
        // When
        skillOptimizationService.optimizeSkillStream(request, callback);
        
        // Then
        verify(agentManager, never()).createAgent(anyString());
    }
    
    @Test
    void testOptimizeSkillStreamWhenCopilotDisabled() {
        // Given
        SkillOptimizationRequest request = createValidRequest();
        when(agentManager.isEnabled()).thenReturn(false);
        StreamResponseCallback<SkillOptimizationResponse> callback =
            new StreamResponseCallback<SkillOptimizationResponse>() {
                
                @Override
                public void onNext(SkillOptimizationResponse response) {
                }
                
                @Override
                public void onError(Throwable t) {
                }
                
                @Override
                public void onComplete() {
                }
            };
        
        // When
        skillOptimizationService.optimizeSkillStream(request, callback);
        
        // Then
        verify(agentManager, never()).createAgent(anyString());
    }
    
    @Test
    void testOptimizeSkillStreamWithOptimizationGoal() {
        // Given
        SkillOptimizationRequest request = createValidRequest();
        request.setOptimizationGoal("Improve clarity and add error handling");
        when(agentManager.isEnabled()).thenReturn(true);
        io.agentscope.core.ReActAgent mockAgent = mock(io.agentscope.core.ReActAgent.class);
        when(agentManager.createAgent(anyString())).thenReturn(mockAgent);
        
        reactor.core.publisher.Flux<io.agentscope.core.agent.Event> mockFlux =
            reactor.core.publisher.Flux.empty();
        when(mockAgent.stream(any(java.util.List.class),
            any(io.agentscope.core.agent.StreamOptions.class))).thenReturn(mockFlux);
        
        StreamResponseCallback<SkillOptimizationResponse> callback =
            new StreamResponseCallback<SkillOptimizationResponse>() {
                
                @Override
                public void onNext(SkillOptimizationResponse response) {
                }
                
                @Override
                public void onError(Throwable t) {
                }
                
                @Override
                public void onComplete() {
                }
            };
        
        // When
        skillOptimizationService.optimizeSkillStream(request, callback);
        
        // Then
        verify(agentManager, times(1)).createAgent(anyString());
    }
    
    @Test
    void testOptimizeSkillStreamWithConversationHistory() {
        // Given
        SkillOptimizationRequest request = createValidRequestWithHistory();
        when(agentManager.isEnabled()).thenReturn(true);
        io.agentscope.core.ReActAgent mockAgent = mock(io.agentscope.core.ReActAgent.class);
        when(agentManager.createAgent(anyString())).thenReturn(mockAgent);
        
        reactor.core.publisher.Flux<io.agentscope.core.agent.Event> mockFlux =
            reactor.core.publisher.Flux.empty();
        when(mockAgent.stream(any(java.util.List.class),
            any(io.agentscope.core.agent.StreamOptions.class))).thenReturn(mockFlux);
        
        StreamResponseCallback<SkillOptimizationResponse> callback =
            new StreamResponseCallback<SkillOptimizationResponse>() {
                
                @Override
                public void onNext(SkillOptimizationResponse response) {
                }
                
                @Override
                public void onError(Throwable t) {
                }
                
                @Override
                public void onComplete() {
                }
            };
        
        // When
        skillOptimizationService.optimizeSkillStream(request, callback);
        
        // Then
        verify(agentManager, times(1)).createAgent(anyString());
    }
    
    @Test
    void testOptimizeSkillStreamWithMcpTools() {
        // Given
        SkillOptimizationRequest request = createValidRequestWithMcpTools();
        when(agentManager.isEnabled()).thenReturn(true);
        io.agentscope.core.ReActAgent mockAgent = mock(io.agentscope.core.ReActAgent.class);
        when(agentManager.createAgent(anyString())).thenReturn(mockAgent);
        
        reactor.core.publisher.Flux<io.agentscope.core.agent.Event> mockFlux =
            reactor.core.publisher.Flux.empty();
        when(mockAgent.stream(any(java.util.List.class),
            any(io.agentscope.core.agent.StreamOptions.class))).thenReturn(mockFlux);
        
        StreamResponseCallback<SkillOptimizationResponse> callback =
            new StreamResponseCallback<SkillOptimizationResponse>() {
                
                @Override
                public void onNext(SkillOptimizationResponse response) {
                }
                
                @Override
                public void onError(Throwable t) {
                }
                
                @Override
                public void onComplete() {
                }
            };
        
        // When
        skillOptimizationService.optimizeSkillStream(request, callback);
        
        // Then
        verify(agentManager, times(1)).createAgent(anyString());
    }
    
    @Test
    void testOptimizeSkillStreamWithAllOptions() {
        // Given
        SkillOptimizationRequest request = createValidRequestWithAllOptions();
        when(agentManager.isEnabled()).thenReturn(true);
        io.agentscope.core.ReActAgent mockAgent = mock(io.agentscope.core.ReActAgent.class);
        when(agentManager.createAgent(anyString())).thenReturn(mockAgent);
        
        reactor.core.publisher.Flux<io.agentscope.core.agent.Event> mockFlux =
            reactor.core.publisher.Flux.empty();
        when(mockAgent.stream(any(java.util.List.class),
            any(io.agentscope.core.agent.StreamOptions.class))).thenReturn(mockFlux);
        
        StreamResponseCallback<SkillOptimizationResponse> callback =
            new StreamResponseCallback<SkillOptimizationResponse>() {
                
                @Override
                public void onNext(SkillOptimizationResponse response) {
                }
                
                @Override
                public void onError(Throwable t) {
                }
                
                @Override
                public void onComplete() {
                }
            };
        
        // When
        skillOptimizationService.optimizeSkillStream(request, callback);
        
        // Then
        verify(agentManager, times(1)).createAgent(anyString());
    }
    
    @Test
    void testOptimizeSkillStreamWhenAgentCreationFails() {
        // Given
        SkillOptimizationRequest request = createValidRequest();
        when(agentManager.isEnabled()).thenReturn(true);
        when(agentManager.createAgent(anyString())).thenReturn(null);
        
        StreamResponseCallback<SkillOptimizationResponse> callback =
            new StreamResponseCallback<SkillOptimizationResponse>() {
                
                @Override
                public void onNext(SkillOptimizationResponse response) {
                }
                
                @Override
                public void onError(Throwable t) {
                }
                
                @Override
                public void onComplete() {
                }
            };
        
        // When
        skillOptimizationService.optimizeSkillStream(request, callback);
        
        // Then
        verify(agentManager, times(1)).createAgent(anyString());
    }
    
    /**
     * Create a valid request for testing.
     */
    private SkillOptimizationRequest createValidRequest() {
        SkillOptimizationRequest request = new SkillOptimizationRequest();
        Skill skill = new Skill();
        skill.setName("test-skill");
        skill.setDescription("Test description");
        skill.setSkillMd(
            "---\nname: test-skill\ndescription: Test description\n---\n\nTest instruction");
        request.setSkill(skill);
        request.setTargetFileName("SKILL.md");
        return request;
    }
    
    /**
     * Create a request with conversation history.
     */
    private SkillOptimizationRequest createValidRequestWithHistory() {
        SkillOptimizationRequest request = createValidRequest();
        com.alibaba.nacos.copilot.model.ConversationHistory history =
            new com.alibaba.nacos.copilot.model.ConversationHistory();
        history.setTitle("Test Conversation");
        history.setContext("Test context");
        
        com.alibaba.nacos.copilot.model.ConversationMessage message =
            new com.alibaba.nacos.copilot.model.ConversationMessage();
        message.setType("user");
        message.setContent("User input");
        history.setMessages(java.util.Collections.singletonList(message));
        
        request.setConversationHistory(history);
        return request;
    }
    
    /**
     * Create a request with MCP tools.
     */
    private SkillOptimizationRequest createValidRequestWithMcpTools() {
        SkillOptimizationRequest request = createValidRequest();
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        java.util.List<java.util.Map<String, Object>> tools = new java.util.ArrayList<>();
        java.util.Map<String, Object> tool = new java.util.HashMap<>();
        tool.put("name", "test-tool");
        tool.put("description", "Test tool description");
        tools.add(tool);
        params.put("selectedMcpTools", tools);
        request.setParams(params);
        return request;
    }
    
    /**
     * Create a request with all options.
     */
    private SkillOptimizationRequest createValidRequestWithAllOptions() {
        SkillOptimizationRequest request = createValidRequestWithHistory();
        request.setOptimizationGoal("Improve clarity");
        
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        java.util.List<java.util.Map<String, Object>> tools = new java.util.ArrayList<>();
        java.util.Map<String, Object> tool = new java.util.HashMap<>();
        tool.put("name", "test-tool");
        tools.add(tool);
        params.put("selectedMcpTools", tools);
        request.setParams(params);
        
        return request;
    }
}
