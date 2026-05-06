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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test for ChatMessage.
 *
 * @author nacos
 */
class ChatMessageTest {
    
    @Test
    void testDefaultConstructor() {
        // When
        ChatMessage message = new ChatMessage();
        
        // Then
        assertNull(message.getRole());
        assertNull(message.getContent());
    }
    
    @Test
    void testConstructorWithParameters() {
        // When
        ChatMessage message = new ChatMessage("user", "Hello, how can I help?");
        
        // Then
        assertEquals("user", message.getRole());
        assertEquals("Hello, how can I help?", message.getContent());
    }
    
    @Test
    void testGettersAndSetters() {
        // Given
        ChatMessage message = new ChatMessage();
        
        // When
        message.setRole("assistant");
        message.setContent("I can help you with that.");
        
        // Then
        assertEquals("assistant", message.getRole());
        assertEquals("I can help you with that.", message.getContent());
    }
    
    @Test
    void testSystemRole() {
        // Given
        ChatMessage message = new ChatMessage("system", "You are a helpful assistant.");
        
        // Then
        assertEquals("system", message.getRole());
        assertEquals("You are a helpful assistant.", message.getContent());
    }
    
    @Test
    void testUserRole() {
        // Given
        ChatMessage message = new ChatMessage("user", "What is the weather today?");
        
        // Then
        assertEquals("user", message.getRole());
        assertEquals("What is the weather today?", message.getContent());
    }
    
    @Test
    void testAssistantRole() {
        // Given
        ChatMessage message = new ChatMessage("assistant", "The weather is sunny.");
        
        // Then
        assertEquals("assistant", message.getRole());
        assertEquals("The weather is sunny.", message.getContent());
    }
    
    @Test
    void testUpdateContent() {
        // Given
        ChatMessage message = new ChatMessage("user", "Initial message");
        
        // When
        message.setContent("Updated message");
        
        // Then
        assertEquals("Updated message", message.getContent());
    }
    
    @Test
    void testUpdateRole() {
        // Given
        ChatMessage message = new ChatMessage("user", "Message");
        
        // When
        message.setRole("assistant");
        
        // Then
        assertEquals("assistant", message.getRole());
    }
}
