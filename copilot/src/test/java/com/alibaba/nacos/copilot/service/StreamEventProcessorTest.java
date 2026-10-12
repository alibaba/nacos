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

import com.alibaba.nacos.copilot.model.StreamResponseType;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.TextBlockStartEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link StreamEventProcessor} on the AgentScope 2.0.3
 * {@code streamEvents()} typed-event model.
 *
 * <p>Every case builds a REAL 2.0.3 {@link AgentEvent} and asserts how it maps onto
 * {@link StreamResponseType}: the three incremental delta events carry user-facing chunks,
 * while lifecycle / framing / cumulative-result events are skipped.
 *
 * @author nacos
 */
class StreamEventProcessorTest {
    
    @Test
    void testTextBlockDeltaMapsToContent() {
        AgentEvent event = new TextBlockDeltaEvent("reply-1", "block-1", "hello ");
        
        StreamEventProcessor.EventProcessResult result = StreamEventProcessor.processEvent(event);
        
        assertNotNull(result);
        assertEquals(StreamResponseType.CONTENT, result.getType());
        assertEquals("hello ", result.getContent());
    }
    
    @Test
    void testThinkingBlockDeltaMapsToThinking() {
        AgentEvent event = new ThinkingBlockDeltaEvent("reply-1", "block-1", "let me think");
        
        StreamEventProcessor.EventProcessResult result = StreamEventProcessor.processEvent(event);
        
        assertNotNull(result);
        assertEquals(StreamResponseType.THINKING, result.getType());
        assertEquals("let me think", result.getContent());
    }
    
    @Test
    void testToolResultTextDeltaMapsToToolCall() {
        AgentEvent event =
            new ToolResultTextDeltaEvent("reply-1", "call-1", "web_search", "tool output");
        
        StreamEventProcessor.EventProcessResult result = StreamEventProcessor.processEvent(event);
        
        assertNotNull(result);
        assertEquals(StreamResponseType.TOOL_CALL, result.getType());
        assertEquals("tool output", result.getContent());
    }
    
    @Test
    void testEmptyDeltaIsSkipped() {
        assertNull(StreamEventProcessor.processEvent(new TextBlockDeltaEvent("r", "b", "")));
    }
    
    @Test
    void testNullDeltaIsSkipped() {
        assertNull(StreamEventProcessor.processEvent(new TextBlockDeltaEvent("r", "b", null)));
    }
    
    @Test
    void testNullEventIsSkipped() {
        assertNull(StreamEventProcessor.processEvent(null));
    }
    
    @Test
    void testBlockStartEventIsSkipped() {
        // Framing events carry no incremental chunk.
        assertNull(
            StreamEventProcessor.processEvent(new TextBlockStartEvent("reply-1", "block-1")));
    }
    
    @Test
    void testAgentEndEventIsSkipped() {
        assertNull(StreamEventProcessor.processEvent(new AgentEndEvent("reply-1")));
    }
    
    @Test
    void testCumulativeAgentResultEventIsSkippedToAvoidDuplicateContent() {
        // The terminal result carries the FULL cumulative message; its text was already
        // streamed as TEXT_BLOCK_DELTA chunks, so it must be skipped (the streamEvents()
        // analogue of the old isLast() de-duplication).
        Msg full = Msg.builder().role(MsgRole.ASSISTANT).textContent("full accumulated answer")
            .build();
        
        assertNull(StreamEventProcessor.processEvent(new AgentResultEvent(full)));
    }
    
    @Test
    void testEventProcessResult() {
        StreamEventProcessor.EventProcessResult result =
            new StreamEventProcessor.EventProcessResult(StreamResponseType.CONTENT, "test content");
        
        assertEquals(StreamResponseType.CONTENT, result.getType());
        assertEquals("test content", result.getContent());
    }
}
