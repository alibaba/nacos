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
import com.alibaba.nacos.copilot.config.CopilotAgentManager;
import com.alibaba.nacos.copilot.model.ConversationHistory;
import com.alibaba.nacos.copilot.model.ConversationMessage;
import com.alibaba.nacos.copilot.model.SkillGenerationRequest;
import com.alibaba.nacos.copilot.model.SkillGenerationResponse;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for SkillGenerationServiceImpl.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class SkillGenerationServiceImplTest {
    
    @Mock
    private CopilotAgentManager agentManager;
    
    private SkillGenerationServiceImpl skillGenerationService;
    
    @BeforeEach
    void setUp() {
        skillGenerationService = new SkillGenerationServiceImpl(agentManager);
    }
    
    @Test
    void testGenerateSkillWithNullRequest() {
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> skillGenerationService.generateSkill(null));
        assertTrue(exception.getCause() instanceof NacosException);
        assertEquals(NacosException.INVALID_PARAM,
            ((NacosException) exception.getCause()).getErrCode());
    }
    
    @Test
    void testGenerateSkillWithBlankBackgroundInfo() {
        // Given
        SkillGenerationRequest request = new SkillGenerationRequest();
        request.setBackgroundInfo("");
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> skillGenerationService.generateSkill(request));
        assertTrue(exception.getCause() instanceof NacosException);
    }
    
    @Test
    void testGenerateSkillWhenCopilotDisabled() {
        // Given
        SkillGenerationRequest request = createValidRequest();
        when(agentManager.isEnabled()).thenReturn(false);
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> skillGenerationService.generateSkill(request));
        assertTrue(exception.getCause() instanceof NacosException);
        assertTrue(exception.getMessage().contains("AI 功能未启用"));
    }
    
    @Test
    void testGenerateSkillWhenAgentCreationFails() {
        // Given
        SkillGenerationRequest request = createValidRequest();
        when(agentManager.isEnabled()).thenReturn(true);
        when(agentManager.createAgent(anyString())).thenReturn(null);
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> skillGenerationService.generateSkill(request));
        assertTrue(exception.getCause() instanceof NacosException);
        assertTrue(exception.getMessage().contains("Failed to create Copilot agent"));
    }
    
    @Test
    void testGenerateSkillStreamWithNullRequest() {
        // Given
        StreamResponseCallback<SkillGenerationResponse> callback =
            new StreamResponseCallback<SkillGenerationResponse>() {
                
                @Override
                public void onNext(SkillGenerationResponse response) {
                }
                
                @Override
                public void onError(Throwable t) {
                }
                
                @Override
                public void onComplete() {
                }
            };
        
        // When
        skillGenerationService.generateSkillStream(null, callback);
        
        // Then
        // Callback should be called with error
        verify(agentManager, never()).createAgent(anyString());
    }
    
    @Test
    void testGenerateSkillStreamWhenCopilotDisabled() {
        // Given
        SkillGenerationRequest request = createValidRequest();
        when(agentManager.isEnabled()).thenReturn(false);
        StreamResponseCallback<SkillGenerationResponse> callback =
            new StreamResponseCallback<SkillGenerationResponse>() {
                
                @Override
                public void onNext(SkillGenerationResponse response) {
                }
                
                @Override
                public void onError(Throwable t) {
                }
                
                @Override
                public void onComplete() {
                }
            };
        
        // When
        skillGenerationService.generateSkillStream(request, callback);
        
        // Then
        verify(agentManager, never()).createAgent(anyString());
    }
    
    @Test
    void testGenerateSkillStreamWithConversationHistory() {
        // Given
        SkillGenerationRequest request = createValidRequestWithHistory();
        when(agentManager.isEnabled()).thenReturn(true);
        io.agentscope.core.ReActAgent mockAgent = mock(io.agentscope.core.ReActAgent.class);
        when(agentManager.createAgent(anyString())).thenReturn(mockAgent);
        
        reactor.core.publisher.Flux<io.agentscope.core.agent.Event> mockFlux =
            reactor.core.publisher.Flux.empty();
        when(mockAgent.stream(any(io.agentscope.core.message.Msg.class),
            any(io.agentscope.core.agent.StreamOptions.class))).thenReturn(mockFlux);
        
        StreamResponseCallback<SkillGenerationResponse> callback =
            new StreamResponseCallback<SkillGenerationResponse>() {
                
                @Override
                public void onNext(SkillGenerationResponse response) {
                }
                
                @Override
                public void onError(Throwable t) {
                }
                
                @Override
                public void onComplete() {
                }
            };
        
        // When
        skillGenerationService.generateSkillStream(request, callback);
        
        // Then
        verify(agentManager, times(1)).createAgent(anyString());
    }
    
    @Test
    void testGenerateSkillStreamWithMcpTools() {
        // Given
        SkillGenerationRequest request = createValidRequestWithMcpTools();
        when(agentManager.isEnabled()).thenReturn(true);
        io.agentscope.core.ReActAgent mockAgent = mock(io.agentscope.core.ReActAgent.class);
        when(agentManager.createAgent(anyString())).thenReturn(mockAgent);
        
        reactor.core.publisher.Flux<io.agentscope.core.agent.Event> mockFlux =
            reactor.core.publisher.Flux.empty();
        when(mockAgent.stream(any(io.agentscope.core.message.Msg.class),
            any(io.agentscope.core.agent.StreamOptions.class))).thenReturn(mockFlux);
        
        StreamResponseCallback<SkillGenerationResponse> callback =
            new StreamResponseCallback<SkillGenerationResponse>() {
                
                @Override
                public void onNext(SkillGenerationResponse response) {
                }
                
                @Override
                public void onError(Throwable t) {
                }
                
                @Override
                public void onComplete() {
                }
            };
        
        // When
        skillGenerationService.generateSkillStream(request, callback);
        
        // Then
        verify(agentManager, times(1)).createAgent(anyString());
    }
    
    /**
     * Create a valid request for testing.
     */
    private SkillGenerationRequest createValidRequest() {
        SkillGenerationRequest request = new SkillGenerationRequest();
        request.setBackgroundInfo("Test background information");
        return request;
    }
    
    /**
     * Create a request with conversation history.
     */
    private SkillGenerationRequest createValidRequestWithHistory() {
        SkillGenerationRequest request = createValidRequest();
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
    private SkillGenerationRequest createValidRequestWithMcpTools() {
        SkillGenerationRequest request = createValidRequest();
        java.util.List<java.util.Map<String, Object>> tools = new java.util.ArrayList<>();
        java.util.Map<String, Object> tool = new java.util.HashMap<>();
        tool.put("name", "test-tool");
        tool.put("description", "Test tool description");
        tool.put("inputSchema", "{}");
        tools.add(tool);
        request.setSelectedMcpTools(tools);
        return request;
    }
    
    @Test
    void testGenerateSkillSuccess() throws Exception {
        // Given
        SkillGenerationRequest request = createValidRequest();
        when(agentManager.isEnabled()).thenReturn(true);
        ReActAgent mockAgent = mock(ReActAgent.class);
        when(agentManager.createAgent(anyString())).thenReturn(mockAgent);
        
        // Create mock events with valid JSON response
        String jsonResponse =
            "{\"skill\":{\"name\":\"test-skill\",\"description\":\"Test skill\"}}";
        Event mockEvent = mock(Event.class);
        Msg mockMsg = mock(Msg.class);
        when(mockEvent.getMessage()).thenReturn(mockMsg);
        when(mockMsg.getTextContent()).thenReturn(jsonResponse);
        
        Flux<Event> mockFlux = Flux.just(mockEvent);
        when(mockAgent.stream(any(Msg.class), any(StreamOptions.class))).thenReturn(mockFlux);
        
        // When
        SkillGenerationResponse response = skillGenerationService.generateSkill(request);
        
        // Then
        assertNotNull(response);
        assertTrue(response.isDone());
        assertNotNull(response.getSkill());
        assertEquals("test-skill", response.getSkill().getName());
    }
    
    @Test
    void testGenerateSkillWithJsonCodeBlock() throws Exception {
        // Given
        SkillGenerationRequest request = createValidRequest();
        when(agentManager.isEnabled()).thenReturn(true);
        ReActAgent mockAgent = mock(ReActAgent.class);
        when(agentManager.createAgent(anyString())).thenReturn(mockAgent);
        
        String jsonResponse = "```json\n{\"skill\":{\"name\":\"test-skill\"}}\n```";
        Event mockEvent = mock(Event.class);
        Msg mockMsg = mock(Msg.class);
        when(mockEvent.getMessage()).thenReturn(mockMsg);
        when(mockMsg.getTextContent()).thenReturn(jsonResponse);
        
        Flux<Event> mockFlux = Flux.just(mockEvent);
        when(mockAgent.stream(any(Msg.class), any(StreamOptions.class))).thenReturn(mockFlux);
        
        // When
        SkillGenerationResponse response = skillGenerationService.generateSkill(request);
        
        // Then
        assertNotNull(response);
        assertTrue(response.isDone());
    }
    
    @Test
    void testGenerateSkillWithErrorInStream() {
        // Given
        SkillGenerationRequest request = createValidRequest();
        when(agentManager.isEnabled()).thenReturn(true);
        ReActAgent mockAgent = mock(ReActAgent.class);
        when(agentManager.createAgent(anyString())).thenReturn(mockAgent);
        
        Flux<Event> mockFlux = Flux.error(new RuntimeException("Stream error"));
        when(mockAgent.stream(any(Msg.class), any(StreamOptions.class))).thenReturn(mockFlux);
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> skillGenerationService.generateSkill(request));
        assertTrue(exception.getMessage().contains("Failed to generate skill"));
    }
    
    @Test
    void testGenerateSkillStreamWithAgentCreationFailure() {
        // Given
        SkillGenerationRequest request = createValidRequest();
        when(agentManager.isEnabled()).thenReturn(true);
        when(agentManager.createAgent(anyString())).thenReturn(null);
        
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        StreamResponseCallback<SkillGenerationResponse> callback =
            new StreamResponseCallback<SkillGenerationResponse>() {
                
                @Override
                public void onNext(SkillGenerationResponse response) {
                }
                
                @Override
                public void onError(Throwable t) {
                    errorRef.set(t);
                }
                
                @Override
                public void onComplete() {
                }
            };
        
        // When
        skillGenerationService.generateSkillStream(request, callback);
        
        // Then
        assertNotNull(errorRef.get());
        assertTrue(errorRef.get() instanceof NacosException);
    }
    
    @Test
    void testBuildUserMessageWithAllMessageTypes() throws Exception {
        // Given
        final SkillGenerationRequest request = createValidRequest();
        ConversationHistory history = new ConversationHistory();
        history.setTitle("Test Title");
        history.setContext("Test Context");
        
        List<ConversationMessage> messages = new ArrayList<>();
        
        // User message
        ConversationMessage userMsg = new ConversationMessage();
        userMsg.setType("user");
        userMsg.setContent("User input");
        messages.add(userMsg);
        
        // Tool call message
        ConversationMessage toolMsg = new ConversationMessage();
        toolMsg.setType("tool_call");
        toolMsg.setToolName("test-tool");
        toolMsg.setToolInput(Collections.singletonMap("param", "value"));
        toolMsg.setToolOutput("output");
        messages.add(toolMsg);
        
        // Model message
        ConversationMessage modelMsg = new ConversationMessage();
        modelMsg.setType("model");
        modelMsg.setContent("Model response");
        messages.add(modelMsg);
        
        // Other type message
        ConversationMessage otherMsg = new ConversationMessage();
        otherMsg.setType("other");
        otherMsg.setContent("Other content");
        messages.add(otherMsg);
        
        history.setMessages(messages);
        request.setConversationHistory(history);
        
        // Use reflection to test private method
        Method method = SkillGenerationServiceImpl.class.getDeclaredMethod("buildUserMessage",
            SkillGenerationRequest.class);
        method.setAccessible(true);
        
        // When
        String result = (String) method.invoke(skillGenerationService, request);
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("Test Title"));
        assertTrue(result.contains("Test Context"));
        assertTrue(result.contains("User input"));
        assertTrue(result.contains("test-tool"));
        assertTrue(result.contains("Model response"));
        assertTrue(result.contains("Other content"));
    }
    
    @Test
    void testBuildUserMessageWithMcpTools() throws Exception {
        // Given
        SkillGenerationRequest request = createValidRequest();
        List<Map<String, Object>> tools = new ArrayList<>();
        Map<String, Object> tool1 = new HashMap<>();
        tool1.put("name", "tool1");
        tool1.put("description", "Description 1");
        tool1.put("inputSchema", "{\"type\":\"object\"}");
        tools.add(tool1);
        
        Map<String, Object> tool2 = new HashMap<>();
        tool2.put("name", "tool2");
        tools.add(tool2);
        
        request.setSelectedMcpTools(tools);
        
        // Use reflection to test private method
        Method method = SkillGenerationServiceImpl.class.getDeclaredMethod("buildUserMessage",
            SkillGenerationRequest.class);
        method.setAccessible(true);
        
        // When
        String result = (String) method.invoke(skillGenerationService, request);
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("tool1"));
        assertTrue(result.contains("Description 1"));
        assertTrue(result.contains("tool2"));
    }
    
    @Test
    void testParseGenerationResultWithSkillField() throws Exception {
        // Given
        String jsonContent = "{\"skill\":{\"name\":\"test-skill\",\"description\":\"Test\"}}";
        SkillGenerationResponse response = new SkillGenerationResponse();
        
        // Use reflection to test private method
        Method method = SkillGenerationServiceImpl.class.getDeclaredMethod("parseGenerationResult",
            String.class, SkillGenerationResponse.class);
        method.setAccessible(true);
        
        // When
        method.invoke(skillGenerationService, jsonContent, response);
        
        // Then
        assertTrue(response.isDone());
        assertNotNull(response.getSkill());
        assertEquals("test-skill", response.getSkill().getName());
    }
    
    @Test
    void testParseGenerationResultWithoutSkillField() throws Exception {
        // Given
        String jsonContent = "{\"name\":\"test-skill\",\"description\":\"Test\"}";
        SkillGenerationResponse response = new SkillGenerationResponse();
        
        // Use reflection to test private method
        Method method = SkillGenerationServiceImpl.class.getDeclaredMethod("parseGenerationResult",
            String.class, SkillGenerationResponse.class);
        method.setAccessible(true);
        
        // When
        method.invoke(skillGenerationService, jsonContent, response);
        
        // Then
        assertTrue(response.isDone());
        assertNotNull(response.getSkill());
        assertEquals("test-skill", response.getSkill().getName());
    }
    
    @Test
    void testParseGenerationResultWithInvalidJson() throws Exception {
        // Given
        String invalidJson = "not a json";
        SkillGenerationResponse response = new SkillGenerationResponse();
        
        // Use reflection to test private method
        Method method = SkillGenerationServiceImpl.class.getDeclaredMethod("parseGenerationResult",
            String.class, SkillGenerationResponse.class);
        method.setAccessible(true);
        
        // When
        method.invoke(skillGenerationService, invalidJson, response);
        
        // Then
        assertTrue(response.isDone());
    }
    
    @Test
    void testExtractJsonFromContentWithJsonCodeBlock() throws Exception {
        // Given
        String content = "Some text\n```json\n{\"key\":\"value\"}\n```\nMore text";
        
        // Use reflection to test private method
        Method method = SkillGenerationServiceImpl.class.getDeclaredMethod("extractJsonFromContent",
            String.class);
        method.setAccessible(true);
        
        // When
        String result = (String) method.invoke(skillGenerationService, content);
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("key"));
    }
    
    @Test
    void testExtractJsonFromContentWithCodeBlock() throws Exception {
        // Given
        String content = "Some text\n```\n{\"key\":\"value\"}\n```\nMore text";
        
        // Use reflection to test private method
        Method method = SkillGenerationServiceImpl.class.getDeclaredMethod("extractJsonFromContent",
            String.class);
        method.setAccessible(true);
        
        // When
        String result = (String) method.invoke(skillGenerationService, content);
        
        // Then
        assertNotNull(result);
    }
    
    @Test
    void testExtractJsonFromContentWithJsonObject() throws Exception {
        // Given
        String content = "Some text {\"key\":\"value\"} more text";
        
        // Use reflection to test private method
        Method method = SkillGenerationServiceImpl.class.getDeclaredMethod("extractJsonFromContent",
            String.class);
        method.setAccessible(true);
        
        // When
        String result = (String) method.invoke(skillGenerationService, content);
        
        // Then
        assertNotNull(result);
    }
    
    @Test
    void testNormalizeResourceStructureWithNestedResources() throws Exception {
        // Given
        Map<String, Object> skillMap = new HashMap<>();
        Map<String, Object> resources = new HashMap<>();
        Map<String, Object> scripts = new HashMap<>();
        Map<String, Object> script = new HashMap<>();
        script.put("type", "script");
        script.put("path", "/scripts/test.sh");
        script.put("content", "echo test");
        scripts.put("test", script);
        resources.put("scripts", scripts);
        skillMap.put("resources", resources);
        
        // Use reflection to test private method
        Method method = SkillGenerationServiceImpl.class
            .getDeclaredMethod("normalizeResourceStructure", Map.class);
        method.setAccessible(true);
        
        // When
        method.invoke(skillGenerationService, skillMap);
        
        // Then
        assertNull(skillMap.get("resources"));
        assertNotNull(skillMap.get("resource"));
        @SuppressWarnings("unchecked")
        Map<String, Object> resourceMap = (Map<String, Object>) skillMap.get("resource");
        assertNotNull(resourceMap.get("test"));
    }
    
    @Test
    void testNormalizeResourceStructureWithoutResources() throws Exception {
        // Given
        Map<String, Object> skillMap = new HashMap<>();
        skillMap.put("name", "test-skill");
        
        // Use reflection to test private method
        Method method = SkillGenerationServiceImpl.class
            .getDeclaredMethod("normalizeResourceStructure", Map.class);
        method.setAccessible(true);
        
        // When
        method.invoke(skillGenerationService, skillMap);
        
        // Then
        assertNotNull(skillMap.get("resource"));
    }
    
}
