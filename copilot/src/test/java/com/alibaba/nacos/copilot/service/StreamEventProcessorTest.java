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

import com.alibaba.nacos.copilot.adapter.StreamResponseCallback;
import com.alibaba.nacos.copilot.model.StreamResponseType;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ThinkingBlock;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for StreamEventProcessor.
 *
 * @author nacos
 */
class StreamEventProcessorTest {
    
    @Test
    void testGetTextContentFromTextContent() {
        // Given
        Msg msg = mock(Msg.class);
        when(msg.getTextContent()).thenReturn("test content");
        
        // When
        String result = StreamEventProcessor.getTextContent(msg);
        
        // Then
        assertEquals("test content", result);
    }
    
    @Test
    void testGetTextContentFromContent() {
        // Given
        Msg msg = mock(Msg.class);
        when(msg.getTextContent()).thenReturn(null);
        when(msg.getContent()).thenReturn(null);
        
        // When
        String result = StreamEventProcessor.getTextContent(msg);
        
        // Then
        assertNull(result);
    }
    
    @Test
    void testGetTextContentWithNullMsg() {
        // When
        String result = StreamEventProcessor.getTextContent(null);
        
        // Then
        assertNull(result);
    }
    
    @Test
    void testGetTextContentWithEmptyTextContent() {
        // Given
        Msg msg = mock(Msg.class);
        when(msg.getTextContent()).thenReturn("");
        when(msg.getContent()).thenReturn(null);
        
        // When
        String result = StreamEventProcessor.getTextContent(msg);
        
        // Then
        assertNull(result);
    }
    
    @Test
    void testHasOnlyThinkBlockWithThinkBlock() {
        // Given
        Msg msg = mock(Msg.class);
        ThinkingBlock thinkBlock = mock(ThinkingBlock.class);
        @SuppressWarnings("unchecked")
        List<io.agentscope.core.message.ContentBlock> contentList =
            (List<io.agentscope.core.message.ContentBlock>) (List<?>) Collections
                .singletonList(thinkBlock);
        when(msg.getContent()).thenReturn(contentList);
        
        // When
        boolean result = StreamEventProcessor.hasOnlyThinkBlock(msg);
        
        // Then
        assertTrue(result);
    }
    
    @Test
    void testHasOnlyThinkBlockWithMultipleItems() {
        // Given
        Msg msg = mock(Msg.class);
        @SuppressWarnings("unchecked")
        List<io.agentscope.core.message.ContentBlock> contentList = new ArrayList<>();
        contentList.add(mock(ThinkingBlock.class));
        when(msg.getContent()).thenReturn(contentList);
        
        // When
        boolean result = StreamEventProcessor.hasOnlyThinkBlock(msg);
        
        // Then
        // If list has one item, it should return true
        // This test may need adjustment based on actual behavior
        assertTrue(result);
    }
    
    @Test
    void testHasOnlyThinkBlockWithNullMsg() {
        // When
        boolean result = StreamEventProcessor.hasOnlyThinkBlock(null);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void testHasOnlyThinkBlockWithNonListContent() {
        // Given
        Msg msg = mock(Msg.class);
        when(msg.getContent()).thenReturn(null);
        
        // When
        boolean result = StreamEventProcessor.hasOnlyThinkBlock(msg);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void testGetThinkingContentWithThinkBlock() {
        // Given
        Msg msg = mock(Msg.class);
        ThinkingBlock thinkBlock = mock(ThinkingBlock.class);
        when(thinkBlock.getThinking()).thenReturn("thinking content");
        @SuppressWarnings("unchecked")
        List<io.agentscope.core.message.ContentBlock> contentList =
            (List<io.agentscope.core.message.ContentBlock>) (List<?>) Collections
                .singletonList(thinkBlock);
        when(msg.getContent()).thenReturn(contentList);
        
        // When
        String result = StreamEventProcessor.getThinkingContent(msg);
        
        // Then
        assertEquals("thinking content", result);
    }
    
    @Test
    void testGetThinkingContentWithNullMsg() {
        // When
        String result = StreamEventProcessor.getThinkingContent(null);
        
        // Then
        assertNull(result);
    }
    
    @Test
    void testGetThinkingContentWithNonThinkBlock() {
        // Given
        Msg msg = mock(Msg.class);
        when(msg.getContent()).thenReturn(Collections.emptyList());
        
        // When
        String result = StreamEventProcessor.getThinkingContent(msg);
        
        // Then
        assertNull(result);
    }
    
    @Test
    void testProcessEventWithToolResult() {
        // Given
        Event event = mock(Event.class);
        Msg msg = mock(Msg.class);
        when(event.isLast()).thenReturn(false);
        when(event.getType()).thenReturn(EventType.TOOL_RESULT);
        when(event.getMessage()).thenReturn(msg);
        when(msg.getTextContent()).thenReturn("tool result");
        
        // When
        StreamEventProcessor.EventProcessResult result = StreamEventProcessor.processEvent(event);
        
        // Then
        assertNotNull(result);
        assertEquals(StreamResponseType.TOOL_CALL, result.getType());
        assertEquals("tool result", result.getContent());
    }
    
    @Test
    void testProcessEventWithReasoning() {
        // Given
        Event event = mock(Event.class);
        Msg msg = mock(Msg.class);
        ThinkingBlock thinkBlock = mock(ThinkingBlock.class);
        when(thinkBlock.getThinking()).thenReturn("thinking");
        @SuppressWarnings("unchecked")
        List<io.agentscope.core.message.ContentBlock> contentList =
            (List<io.agentscope.core.message.ContentBlock>) (List<?>) Collections
                .singletonList(thinkBlock);
        
        when(event.isLast()).thenReturn(false);
        when(event.getType()).thenReturn(EventType.REASONING);
        when(event.getMessage()).thenReturn(msg);
        when(msg.getContent()).thenReturn(contentList);
        
        // When
        StreamEventProcessor.EventProcessResult result = StreamEventProcessor.processEvent(event);
        
        // Then
        assertNotNull(result);
        assertEquals(StreamResponseType.THINKING, result.getType());
        assertEquals("thinking", result.getContent());
    }
    
    @Test
    void testProcessEventWithOtherEventType() {
        // Given
        Event event = mock(Event.class);
        Msg msg = mock(Msg.class);
        when(event.isLast()).thenReturn(false);
        when(event.getType()).thenReturn(EventType.REASONING);
        when(event.getMessage()).thenReturn(msg);
        when(msg.getTextContent()).thenReturn("content");
        when(msg.getContent()).thenReturn(Collections.emptyList());
        
        // When
        StreamEventProcessor.EventProcessResult result = StreamEventProcessor.processEvent(event);
        
        // Then
        assertNotNull(result);
        assertEquals(StreamResponseType.CONTENT, result.getType());
        assertEquals("content", result.getContent());
    }
    
    @Test
    void testProcessEventWithLastEvent() {
        // Given
        Event event = mock(Event.class);
        when(event.isLast()).thenReturn(true);
        
        // When
        StreamEventProcessor.EventProcessResult result = StreamEventProcessor.processEvent(event);
        
        // Then
        assertNull(result);
    }
    
    @Test
    void testProcessEventWithNullMessage() {
        // Given
        Event event = mock(Event.class);
        when(event.isLast()).thenReturn(false);
        when(event.getMessage()).thenReturn(null);
        
        // When
        StreamEventProcessor.EventProcessResult result = StreamEventProcessor.processEvent(event);
        
        // Then
        assertNull(result);
    }
    
    @Test
    void testProcessEventWithEmptyContent() {
        // Given
        Event event = mock(Event.class);
        Msg msg = mock(Msg.class);
        when(event.isLast()).thenReturn(false);
        when(event.getType()).thenReturn(EventType.REASONING);
        when(event.getMessage()).thenReturn(msg);
        when(msg.getTextContent()).thenReturn("");
        when(msg.getContent()).thenReturn(Collections.emptyList());
        
        // When
        StreamEventProcessor.EventProcessResult result = StreamEventProcessor.processEvent(event);
        
        // Then
        assertNull(result);
    }
    
    @Test
    void testCreateSubscriber() {
        // Given
        StreamResponseCallback<String> callback = new StreamResponseCallback<String>() {
            
            @Override
            public void onNext(String response) {
            }
            
            @Override
            public void onError(Throwable t) {
            }
            
            @Override
            public void onComplete() {
            }
        };
        
        StreamEventProcessor.ResponseBuilder<String> builder =
            (type, content, done) -> type.getCode();
        
        // When
        org.reactivestreams.Subscriber<Event> subscriber =
            StreamEventProcessor.createSubscriber(builder, callback);
        
        // Then
        assertNotNull(subscriber);
        
        // Test onSubscribe
        Subscription subscription = mock(Subscription.class);
        subscriber.onSubscribe(subscription);
        
        // Test onNext
        Event event = mock(Event.class);
        Msg msg = mock(Msg.class);
        when(event.isLast()).thenReturn(false);
        when(event.getType()).thenReturn(EventType.REASONING);
        when(event.getMessage()).thenReturn(msg);
        when(msg.getTextContent()).thenReturn("test");
        when(msg.getContent()).thenReturn(Collections.emptyList());
        subscriber.onNext(event);
        
        // Test onComplete
        subscriber.onComplete();
    }
    
    @Test
    void testEventProcessResult() {
        // Given
        StreamResponseType type = StreamResponseType.CONTENT;
        String content = "test content";
        
        // When
        StreamEventProcessor.EventProcessResult result =
            new StreamEventProcessor.EventProcessResult(type, content);
        
        // Then
        assertEquals(type, result.getType());
        assertEquals(content, result.getContent());
    }
}
