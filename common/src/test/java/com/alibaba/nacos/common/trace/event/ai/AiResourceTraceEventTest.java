/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.trace.event.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResourceTraceEventTest {
    
    @Test
    void testAiResourceTraceEvent() {
        AiResourceTraceEvent event = new AiResourceTraceEvent(1L, "admin", "skill",
            "demo-skill", "1.0.0", "PUBLISH", "SUCCESS", "127.0.0.1", "pipeline");
        
        assertEquals(AiResourceTraceEvent.AI_RESOURCE_TRACE_EVENT, event.getType());
        assertEquals(1L, event.getEventTime());
        assertEquals("", event.getNamespace());
        assertEquals("skill", event.getGroup());
        assertEquals("demo-skill", event.getName());
        assertEquals("admin", event.getOperator());
        assertEquals("skill", event.getResourceType());
        assertEquals("demo-skill", event.getResourceId());
        assertEquals("1.0.0", event.getVersion());
        assertEquals("PUBLISH", event.getOperation());
        assertEquals("SUCCESS", event.getStatus());
        assertEquals("127.0.0.1", event.getClientIp());
        assertEquals("pipeline", event.getExt());
        assertTrue(event.isPluginEvent());
    }
}
