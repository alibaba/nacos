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
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified stream event processor for the AgentScope {@code streamEvents()} typed-event model.
 * Maps the incremental {@link AgentEvent} deltas onto Copilot's {@link StreamResponseType}.
 *
 * @author nacos
 */
public class StreamEventProcessor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamEventProcessor.class);
    
    /**
     * Process a single typed AgentEvent and determine its response type and content.
     *
     * <p>Only the incremental delta events carry user-facing chunks. Lifecycle and framing events
     * (agent / model-call / block start and end, tool-call framing) and the terminal cumulative
     * {@code AgentResultEvent} are skipped: the deltas have already been streamed and the frontend
     * accumulates them, so forwarding the cumulative result would duplicate content. This is the
     * {@code streamEvents()} analogue of skipping the old {@code Event.isLast()} message.
     *
     * @param event the event to process
     * @return EventProcessResult containing type and content, or null if the event should be skipped
     */
    public static EventProcessResult processEvent(AgentEvent event) {
        if (event == null) {
            return null;
        }
        
        StreamResponseType type;
        String content;
        
        if (event instanceof TextBlockDeltaEvent) {
            // Assistant text delta -> CONTENT
            type = StreamResponseType.CONTENT;
            content = ((TextBlockDeltaEvent) event).getDelta();
        } else if (event instanceof ThinkingBlockDeltaEvent) {
            // Reasoning delta -> THINKING
            type = StreamResponseType.THINKING;
            content = ((ThinkingBlockDeltaEvent) event).getDelta();
        } else if (event instanceof ToolResultTextDeltaEvent) {
            // Tool output delta -> TOOL_CALL
            type = StreamResponseType.TOOL_CALL;
            content = ((ToolResultTextDeltaEvent) event).getDelta();
        } else {
            // Lifecycle / framing / cumulative-result events carry no incremental chunk.
            return null;
        }
        
        // Only process if content is not empty
        if (content == null || content.isEmpty()) {
            return null;
        }
        
        return new EventProcessResult(type, content);
    }
    
    /**
     * Response builder interface for creating response objects.
     *
     * @param <T> response type
     */
    public interface ResponseBuilder<T> {
        
        /**
         * Create a response object with the given type and content.
         *
         * @param type response type
         * @param content content chunk (null for DONE)
         * @param done whether the response is complete
         * @return response object
         */
        T build(StreamResponseType type, String content, boolean done);
    }
    
    /**
     * Create a Subscriber for processing {@code streamEvents()} events with a generic response type.
     *
     * @param responseBuilder builder for creating response instances
     * @param callback callback for sending responses
     * @param <T> response type
     * @return Subscriber instance
     */
    public static <T> Subscriber<AgentEvent> createSubscriber(
        ResponseBuilder<T> responseBuilder,
        StreamResponseCallback<T> callback) {
        
        return new Subscriber<AgentEvent>() {
            
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }
            
            @Override
            public void onNext(AgentEvent event) {
                try {
                    EventProcessResult result = processEvent(event);
                    if (result != null) {
                        T response =
                            responseBuilder.build(result.getType(), result.getContent(), false);
                        // Skip if response is null (e.g., filtered out by builder)
                        if (response != null) {
                            callback.onNext(response);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to process stream event", e);
                }
            }
            
            @Override
            public void onError(Throwable t) {
                LOGGER.error("Error in AgentScope stream response", t);
                callback.onError(t);
            }
            
            @Override
            public void onComplete() {
                // Frontend will parse the accumulated content itself, so we just send DONE signal
                T finalResponse = responseBuilder.build(StreamResponseType.DONE, null, true);
                callback.onNext(finalResponse);
                callback.onComplete();
            }
        };
    }
    
    /**
     * Result of processing an event.
     */
    public static class EventProcessResult {
        
        private final StreamResponseType type;
        private final String content;
        
        public EventProcessResult(StreamResponseType type, String content) {
            this.type = type;
            this.content = content;
        }
        
        public StreamResponseType getType() {
            return type;
        }
        
        public String getContent() {
            return content;
        }
    }
}
