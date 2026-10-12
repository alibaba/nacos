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
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * End-to-end verification of the Copilot streaming subscriber on the AgentScope 2.0.3
 * {@code streamEvents()} typed-event model.
 *
 * <p>Every case drives {@link StreamEventProcessor#createSubscriber} with REAL 2.0.3
 * {@link AgentEvent} objects (not mocks) and records what reaches the
 * {@link StreamResponseCallback}, covering: text deltas streamed as {@code CONTENT} then a
 * terminal {@code DONE}, the cumulative {@code AgentResultEvent} skipped so content is not
 * duplicated, tool-result deltas as {@code TOOL_CALL}, call-site {@code THINKING} filtering,
 * and {@code onError} propagation. This is the follow-up verification for the
 * {@code stream()} to {@code streamEvents()} migration tracked in issue #15896.
 *
 * @author nacos
 */
class StreamEventProcessorCompatibilityTest {
    
    @Test
    void testSubscriberStreamsTextDeltasThenDone() {
        Recorder recorder = new Recorder();
        Subscriber<AgentEvent> subscriber =
            StreamEventProcessor.createSubscriber(recordingBuilder(recorder), recorder);
        
        subscriber.onNext(new TextBlockDeltaEvent("reply-1", "block-1", "Hello"));
        subscriber.onNext(new TextBlockDeltaEvent("reply-1", "block-1", " world"));
        subscriber.onComplete();
        
        assertEquals(
            List.of(StreamResponseType.CONTENT, StreamResponseType.CONTENT,
                StreamResponseType.DONE),
            recorder.types);
        assertEquals("Hello", recorder.contents.get(0));
        assertEquals(" world", recorder.contents.get(1));
        assertTrue(recorder.completed.get());
    }
    
    @Test
    void testSubscriberSkipsCumulativeResultEvent() {
        Recorder recorder = new Recorder();
        Subscriber<AgentEvent> subscriber =
            StreamEventProcessor.createSubscriber(recordingBuilder(recorder), recorder);
        
        subscriber.onNext(new TextBlockDeltaEvent("reply-1", "block-1", "answer"));
        // The cumulative final result must NOT produce a second CONTENT chunk.
        subscriber.onNext(new AgentResultEvent(
            Msg.builder().role(MsgRole.ASSISTANT).textContent("answer").build()));
        subscriber.onComplete();
        
        assertEquals(List.of(StreamResponseType.CONTENT, StreamResponseType.DONE), recorder.types);
        assertEquals("answer", recorder.contents.get(0));
    }
    
    @Test
    void testToolResultDeltaFlowsThroughSubscriber() {
        Recorder recorder = new Recorder();
        Subscriber<AgentEvent> subscriber =
            StreamEventProcessor.createSubscriber(recordingBuilder(recorder), recorder);
        
        subscriber.onNext(
            new ToolResultTextDeltaEvent("reply-1", "call-1", "web_search", "result chunk"));
        subscriber.onComplete();
        
        assertEquals(List.of(StreamResponseType.TOOL_CALL, StreamResponseType.DONE),
            recorder.types);
        assertEquals("result chunk", recorder.contents.get(0));
    }
    
    @Test
    void testThinkingDeltaIsFilteredByResponseBuilder() {
        // PromptOptimizationServiceImpl filters THINKING out at the builder level; verify the
        // processor still surfaces THINKING so that filtering stays a call-site decision.
        Recorder recorder = new Recorder();
        StreamEventProcessor.ResponseBuilder<StreamResponseType> filteringBuilder =
            (type, content, done) -> {
                if (type == StreamResponseType.THINKING) {
                    return null;
                }
                recorder.types.add(type);
                recorder.contents.add(content);
                return type;
            };
        Subscriber<AgentEvent> subscriber =
            StreamEventProcessor.createSubscriber(filteringBuilder, recorder);
        
        subscriber.onNext(new ThinkingBlockDeltaEvent("reply-1", "block-1", "internal reasoning"));
        subscriber.onComplete();
        
        assertEquals(List.of(StreamResponseType.DONE), recorder.types);
        assertTrue(recorder.completed.get());
    }
    
    @Test
    void testOnSubscribeRequestsUnbounded() {
        Recorder recorder = new Recorder();
        Subscriber<AgentEvent> subscriber =
            StreamEventProcessor.createSubscriber(recordingBuilder(recorder), recorder);
        
        Subscription subscription = mock(Subscription.class);
        subscriber.onSubscribe(subscription);
        
        verify(subscription).request(Long.MAX_VALUE);
    }
    
    @Test
    void testSubscriberPropagatesErrorToCallback() {
        Recorder recorder = new Recorder();
        Subscriber<AgentEvent> subscriber =
            StreamEventProcessor.createSubscriber(recordingBuilder(recorder), recorder);
        
        RuntimeException failure = new RuntimeException("stream failure");
        subscriber.onError(failure);
        
        assertSame(failure, recorder.error.get());
    }
    
    private static StreamEventProcessor.ResponseBuilder<StreamResponseType> recordingBuilder(
        Recorder recorder) {
        return (type, content, done) -> {
            recorder.types.add(type);
            recorder.contents.add(content);
            return type;
        };
    }
    
    /**
     * Records everything the subscriber emits (via the response builder) plus completion and error.
     */
    private static final class Recorder implements StreamResponseCallback<StreamResponseType> {
        
        private final List<StreamResponseType> types = new ArrayList<>();
        
        private final List<String> contents = new ArrayList<>();
        
        private final AtomicBoolean completed = new AtomicBoolean(false);
        
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        
        @Override
        public void onNext(StreamResponseType response) {
        }
        
        @Override
        public void onError(Throwable t) {
            error.set(t);
        }
        
        @Override
        public void onComplete() {
            completed.set(true);
        }
    }
}
