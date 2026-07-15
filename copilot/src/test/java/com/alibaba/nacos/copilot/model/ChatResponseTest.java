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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for ChatResponse.
 *
 * @author nacos
 */
class ChatResponseTest {
    
    @Test
    void testDefaultConstructor() {
        // When
        ChatResponse response = new ChatResponse();
        
        // Then
        assertNull(response.getType());
        assertNull(response.getChunk());
        assertFalse(response.isDone());
    }
    
    @Test
    void testConstructorWithParameters() {
        // When
        ChatResponse response = new ChatResponse(StreamResponseType.CONTENT, "Hello", false);
        
        // Then
        assertEquals(StreamResponseType.CONTENT, response.getType());
        assertEquals("Hello", response.getChunk());
        assertFalse(response.isDone());
    }
    
    @Test
    void testGettersAndSetters() {
        // Given
        ChatResponse response = new ChatResponse();
        
        // When
        response.setType(StreamResponseType.THINKING);
        response.setChunk("Thinking...");
        response.setDone(true);
        
        // Then
        assertEquals(StreamResponseType.THINKING, response.getType());
        assertEquals("Thinking...", response.getChunk());
        assertTrue(response.isDone());
    }
    
    @Test
    void testContentResponse() {
        // Given
        ChatResponse response =
            new ChatResponse(StreamResponseType.CONTENT, "This is a content chunk", false);
        
        // Then
        assertEquals(StreamResponseType.CONTENT, response.getType());
        assertEquals("This is a content chunk", response.getChunk());
        assertFalse(response.isDone());
    }
    
    @Test
    void testToolCallResponse() {
        // Given
        ChatResponse response = new ChatResponse();
        response.setType(StreamResponseType.TOOL_CALL);
        response.setChunk("Calling tool: get_weather");
        response.setDone(false);
        
        // Then
        assertEquals(StreamResponseType.TOOL_CALL, response.getType());
        assertEquals("Calling tool: get_weather", response.getChunk());
        assertFalse(response.isDone());
    }
    
    @Test
    void testDoneResponse() {
        // Given
        ChatResponse response = new ChatResponse(StreamResponseType.DONE, null, true);
        
        // Then
        assertEquals(StreamResponseType.DONE, response.getType());
        assertNull(response.getChunk());
        assertTrue(response.isDone());
    }
    
    @Test
    void testStreamingResponseFlow() {
        // Simulate a streaming response flow
        // First chunk
        ChatResponse chunk1 = new ChatResponse(StreamResponseType.CONTENT, "Hello", false);
        assertEquals(StreamResponseType.CONTENT, chunk1.getType());
        assertEquals("Hello", chunk1.getChunk());
        assertFalse(chunk1.isDone());
        
        // Second chunk
        ChatResponse chunk2 = new ChatResponse(StreamResponseType.CONTENT, " World", false);
        assertEquals(StreamResponseType.CONTENT, chunk2.getType());
        assertEquals(" World", chunk2.getChunk());
        assertFalse(chunk2.isDone());
        
        // Final done signal
        ChatResponse done = new ChatResponse(StreamResponseType.DONE, null, true);
        assertEquals(StreamResponseType.DONE, done.getType());
        assertTrue(done.isDone());
    }
}
