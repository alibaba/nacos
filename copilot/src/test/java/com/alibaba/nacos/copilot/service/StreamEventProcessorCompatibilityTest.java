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
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compatibility verification for Copilot streaming on AgentScope 2.0.3.
 *
 * <p>In AgentScope 2.0.3 {@link Msg#getContent()} returns a {@code List<ContentBlock>}
 * (as it already did in 1.0.7, so the {@code instanceof String} branch this upgrade
 * removed from {@code StreamEventProcessor.getTextContent()} was dead code in both
 * versions), and stream events are still delivered as {@link Event} by the retained
 * {@code stream()} API. Unlike the
 * mock-based {@link StreamEventProcessorTest}, every case here builds REAL 2.0.3
 * {@link Event} and {@link Msg} objects and drives them through {@link StreamEventProcessor},
 * so it proves that message handling, text chunks, thinking, tool results, stream end and
 * error signalling all keep working after the 1.0.7 to 2.0.3 upgrade. This is the
 * "verify existing Copilot functionality stays compatible" check requested in issue #15757.
 *
 * @author nacos
 */
class StreamEventProcessorCompatibilityTest {
    
    private static Msg textMsg(String text) {
        return Msg.builder()
            .role(MsgRole.ASSISTANT)
            .textContent(text)
            .build();
    }
    
    private static Msg thinkingOnlyMsg(String thinking) {
        return Msg.builder()
            .role(MsgRole.ASSISTANT)
            .content(ThinkingBlock.builder().thinking(thinking).build())
            .build();
    }
    
    private static Msg thinkingAndTextMsg(String thinking, String text) {
        return Msg.builder()
            .role(MsgRole.ASSISTANT)
            .content(ThinkingBlock.builder().thinking(thinking).build(),
                TextBlock.builder().text(text).build())
            .build();
    }
    
    @Test
    void testGetTextContentFromRealTextMsg() {
        assertEquals("hello world", StreamEventProcessor.getTextContent(textMsg("hello world")));
    }
    
    @Test
    void testGetTextContentNullForRealThinkingOnlyMsg() {
        // In 2.0.3 getContent() is a List<ContentBlock>; a thinking-only message carries no
        // TextBlock, so getTextContent must be null. This locks in that dropping the obsolete
        // String-content fallback does not change behaviour on the real message model.
        assertNull(StreamEventProcessor.getTextContent(thinkingOnlyMsg("pondering")));
    }
    
    @Test
    void testReasoningTextEventMapsToContent() {
        Event event = new Event(EventType.REASONING, textMsg("answer chunk"), false);
        
        StreamEventProcessor.EventProcessResult result = StreamEventProcessor.processEvent(event);
        
        assertNotNull(result);
        assertEquals(StreamResponseType.CONTENT, result.getType());
        assertEquals("answer chunk", result.getContent());
    }
    
    @Test
    void testThinkingOnlyEventMapsToThinking() {
        Msg msg = thinkingOnlyMsg("let me think");
        assertTrue(StreamEventProcessor.hasOnlyThinkBlock(msg));
        assertEquals("let me think", StreamEventProcessor.getThinkingContent(msg));
        
        Event event = new Event(EventType.REASONING, msg, false);
        StreamEventProcessor.EventProcessResult result = StreamEventProcessor.processEvent(event);
        
        assertNotNull(result);
        assertEquals(StreamResponseType.THINKING, result.getType());
        assertEquals("let me think", result.getContent());
    }
    
    @Test
    void testMixedThinkingAndTextEventMapsToContent() {
        // A real 2.0.3 multi-block message ([ThinkingBlock, TextBlock]) is not "only think
        // block", so the reasoning event must fall through to the text CONTENT branch.
        Msg msg = thinkingAndTextMsg("thinking part", "text part");
        assertFalse(StreamEventProcessor.hasOnlyThinkBlock(msg));
        
        Event event = new Event(EventType.REASONING, msg, false);
        StreamEventProcessor.EventProcessResult result = StreamEventProcessor.processEvent(event);
        
        assertNotNull(result);
        assertEquals(StreamResponseType.CONTENT, result.getType());
        assertEquals("text part", result.getContent());
    }
    
    @Test
    void testToolResultEventMapsToToolCall() {
        Event event = new Event(EventType.TOOL_RESULT, textMsg("tool output"), false);
        
        StreamEventProcessor.EventProcessResult result = StreamEventProcessor.processEvent(event);
        
        assertNotNull(result);
        assertEquals(StreamResponseType.TOOL_CALL, result.getType());
        assertEquals("tool output", result.getContent());
    }
    
    @Test
    void testLastEventIsSkippedToAvoidDuplicateContent() {
        Event event = new Event(EventType.AGENT_RESULT, textMsg("full accumulated answer"), true);
        
        assertNull(StreamEventProcessor.processEvent(event));
    }
    
    @Test
    void testSubscriberDeliversContentChunkThenDone() {
        List<StreamResponseType> emittedTypes = new ArrayList<>();
        List<String> emittedContents = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        
        StreamEventProcessor.ResponseBuilder<StreamResponseType> builder =
            (type, content, done) -> {
                emittedTypes.add(type);
                emittedContents.add(content);
                return type;
            };
        StreamResponseCallback<StreamResponseType> callback =
            new RecordingCallback(completed);
        
        Subscriber<Event> subscriber =
            StreamEventProcessor.createSubscriber(builder, callback);
        subscriber.onNext(new Event(EventType.REASONING, textMsg("chunk-1"), false));
        subscriber.onComplete();
        
        assertEquals(List.of(StreamResponseType.CONTENT, StreamResponseType.DONE), emittedTypes);
        assertEquals("chunk-1", emittedContents.get(0));
        assertTrue(completed.get());
    }
    
    @Test
    void testSubscriberPropagatesErrorToCallback() {
        AtomicReference<Throwable> captured = new AtomicReference<>();
        StreamEventProcessor.ResponseBuilder<StreamResponseType> builder =
            (type, content, done) -> type;
        StreamResponseCallback<StreamResponseType> callback =
            new StreamResponseCallback<StreamResponseType>() {
                
                @Override
                public void onNext(StreamResponseType response) {
                }
                
                @Override
                public void onError(Throwable t) {
                    captured.set(t);
                }
                
                @Override
                public void onComplete() {
                }
            };
        
        Subscriber<Event> subscriber =
            StreamEventProcessor.createSubscriber(builder, callback);
        RuntimeException failure = new RuntimeException("stream failure");
        subscriber.onError(failure);
        
        assertSame(failure, captured.get());
    }
    
    /**
     * Callback that records only stream completion; content is captured via the response builder.
     */
    private static final class RecordingCallback
        implements StreamResponseCallback<StreamResponseType> {
        
        private final AtomicBoolean completed;
        
        private RecordingCallback(AtomicBoolean completed) {
            this.completed = completed;
        }
        
        @Override
        public void onNext(StreamResponseType response) {
        }
        
        @Override
        public void onError(Throwable t) {
        }
        
        @Override
        public void onComplete() {
            completed.set(true);
        }
    }
}
