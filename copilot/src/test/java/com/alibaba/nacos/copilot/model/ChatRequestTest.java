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

package com.alibaba.nacos.copilot.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for ChatRequest.
 *
 * @author nacos
 */
class ChatRequestTest {
    
    @Test
    void testDefaultConstructor() {
        // When
        ChatRequest request = new ChatRequest();
        
        // Then
        assertNull(request.getSessionId());
        assertNull(request.getMessage());
        assertNull(request.getContext());
        assertNull(request.getHistory());
        assertNull(request.getSystemPrompt());
        assertFalse(request.isStream());
    }
    
    @Test
    void testSessionId() {
        // Given
        ChatRequest request = new ChatRequest();
        
        // When
        request.setSessionId("session-123");
        
        // Then
        assertEquals("session-123", request.getSessionId());
    }
    
    @Test
    void testMessage() {
        // Given
        ChatRequest request = new ChatRequest();
        
        // When
        request.setMessage("What is the weather?");
        
        // Then
        assertEquals("What is the weather?", request.getMessage());
    }
    
    @Test
    void testContext() {
        // Given
        ChatRequest request = new ChatRequest();
        
        // When
        request.setContext("User is asking about weather in Beijing");
        
        // Then
        assertEquals("User is asking about weather in Beijing", request.getContext());
    }
    
    @Test
    void testHistory() {
        // Given
        ChatRequest request = new ChatRequest();
        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage("user", "Hello"));
        history.add(new ChatMessage("assistant", "Hi there!"));
        
        // When
        request.setHistory(history);
        
        // Then
        assertEquals(2, request.getHistory().size());
        assertEquals("Hello", request.getHistory().get(0).getContent());
        assertEquals("Hi there!", request.getHistory().get(1).getContent());
    }
    
    @Test
    void testStream() {
        // Given
        ChatRequest request = new ChatRequest();
        
        // When
        request.setStream(true);
        
        // Then
        assertTrue(request.isStream());
        
        // When
        request.setStream(false);
        
        // Then
        assertFalse(request.isStream());
    }
    
    @Test
    void testSystemPrompt() {
        // Given
        ChatRequest request = new ChatRequest();
        
        // When
        request.setSystemPrompt("You are a helpful assistant.");
        
        // Then
        assertEquals("You are a helpful assistant.", request.getSystemPrompt());
    }
    
    @Test
    void testCompleteChatRequest() {
        // Given
        ChatRequest request = new ChatRequest();
        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage("system", "You are a helpful assistant."));
        history.add(new ChatMessage("user", "What is 2+2?"));
        
        // When
        request.setSessionId("session-456");
        request.setMessage("What is 3+3?");
        request.setContext("Math questions");
        request.setHistory(history);
        request.setStream(true);
        request.setSystemPrompt("You are a math tutor.");
        
        // Then
        assertEquals("session-456", request.getSessionId());
        assertEquals("What is 3+3?", request.getMessage());
        assertEquals("Math questions", request.getContext());
        assertEquals(2, request.getHistory().size());
        assertTrue(request.isStream());
        assertEquals("You are a math tutor.", request.getSystemPrompt());
    }
}
