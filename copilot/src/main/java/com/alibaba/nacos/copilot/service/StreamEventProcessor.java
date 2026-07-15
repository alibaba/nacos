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
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ThinkingBlock;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Unified stream event processor for handling AgentScope stream events.
 * Provides common logic for processing events and extracting content.
 *
 * @author nacos
 */
public class StreamEventProcessor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamEventProcessor.class);
    
    /**
     * Extract text content from Msg.
     *
     * @param msg message to extract content from
     * @return text content, or null if not available
     */
    public static String getTextContent(Msg msg) {
        if (msg == null) {
            return null;
        }
        
        String textContent = msg.getTextContent();
        if (textContent != null && !textContent.isEmpty()) {
            return textContent;
        }
        
        Object content = msg.getContent();
        if (content instanceof String) {
            return (String) content;
        }
        
        return null;
    }
    
    /**
     * Check if Msg contains only one thinkblock.
     *
     * @param msg message to check
     * @return true if msg contains only one thinkblock, false otherwise
     */
    public static boolean hasOnlyThinkBlock(Msg msg) {
        if (msg == null) {
            return false;
        }
        
        try {
            Object content = msg.getContent();
            if (content instanceof List) {
                List<?> contentList = (List<?>) content;
                return contentList.size() == 1 && contentList.get(0) instanceof ThinkingBlock;
            }
            return false;
        } catch (Exception e) {
            LOGGER.debug("Failed to check thinkblock in msg", e);
            return false;
        }
    }
    
    /**
     * Extract thinking content from Msg (from thinkblock).
     *
     * @param msg message containing thinkblock
     * @return thinking content, or null if not available
     */
    public static String getThinkingContent(Msg msg) {
        if (msg == null) {
            return null;
        }
        
        try {
            // Get thinkblock from msg content
            Object content = msg.getContent();
            if (content instanceof List) {
                List<?> contentList = (List<?>) content;
                if (contentList.size() == 1) {
                    Object element = contentList.get(0);
                    if (element instanceof ThinkingBlock) {
                        ThinkingBlock thinkBlock = (ThinkingBlock) element;
                        return thinkBlock.getThinking();
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            LOGGER.debug("Failed to extract thinking content from msg", e);
            return null;
        }
    }
    
    /**
     * Process a single event and determine its type and content.
     *
     * @param event the event to process
     * @return EventProcessResult containing type and content, or null if event should be skipped
     */
    public static EventProcessResult processEvent(io.agentscope.core.agent.Event event) {
        // Check if this is the last message, which contains full content
        // If it's the last message, skip sending chunk to avoid duplicate content
        if (event.isLast()) {
            return null;
        }
        
        Msg msg = event.getMessage();
        if (msg == null) {
            return null;
        }
        
        // First determine response type based on event type and message structure
        StreamResponseType type = StreamResponseType.CONTENT;
        String content = null;
        
        if (event.getType() == EventType.TOOL_RESULT) {
            // Tool call: get content from textContent
            type = StreamResponseType.TOOL_CALL;
            content = getTextContent(msg);
        } else if (event.getType() == EventType.REASONING && hasOnlyThinkBlock(msg)) {
            // Thinking: get content from thinkblock
            type = StreamResponseType.THINKING;
            content = getThinkingContent(msg);
        } else {
            // Final response or other content: get content from textContent
            type = StreamResponseType.CONTENT;
            content = getTextContent(msg);
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
     * Create a Subscriber for processing stream events with a generic response type.
     *
     * @param responseBuilder builder for creating response instances
     * @param callback callback for sending responses
     * @param <T> response type
     * @return Subscriber instance
     */
    public static <T> Subscriber<io.agentscope.core.agent.Event> createSubscriber(
        ResponseBuilder<T> responseBuilder,
        StreamResponseCallback<T> callback) {
        
        return new Subscriber<io.agentscope.core.agent.Event>() {
            
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }
            
            @Override
            public void onNext(io.agentscope.core.agent.Event event) {
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
