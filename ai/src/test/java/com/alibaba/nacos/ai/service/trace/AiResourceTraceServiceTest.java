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

package com.alibaba.nacos.ai.service.trace;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.trace.event.ai.AiResourceTraceEvent;
import com.alibaba.nacos.plugin.trace.spi.NacosTraceSubscriber;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResourceTraceServiceTest {
    
    @Test
    void testBuildTraceEvent() {
        AiResourceTraceEvent event = AiResourceTraceService.buildTraceEvent("skill",
            "demo-skill", "1.0.0", AiResourceTraceService.OP_PUBLISH,
            AiResourceTraceService.STATUS_SUCCESS, "admin", "127.0.0.1", "pipeline");
        
        assertEquals("skill", event.getResourceType());
        assertEquals("demo-skill", event.getResourceId());
        assertEquals("1.0.0", event.getVersion());
        assertEquals(AiResourceTraceService.OP_PUBLISH, event.getOperation());
        assertEquals(AiResourceTraceService.STATUS_SUCCESS, event.getStatus());
        assertEquals("admin", event.getOperator());
        assertEquals("127.0.0.1", event.getClientIp());
        assertEquals("pipeline", event.getExt());
    }
    
    @Test
    void testAiResourceTraceLogSubscriber() {
        AiResourceTraceLogSubscriber subscriber = new AiResourceTraceLogSubscriber();
        AiResourceTraceEvent event = new AiResourceTraceEvent(0L, "", "skill",
            "demo-skill", "1.0.0", "PUBLISH", "SUCCESS", "", "pipeline");
        
        assertEquals(AiResourceTraceLogSubscriber.NAME, subscriber.getName());
        assertEquals(1, subscriber.subscribeTypes().size());
        assertTrue(subscriber.subscribeTypes().contains(AiResourceTraceEvent.class));
        
        Map<String, Object> logEntry = AiResourceTraceLogSubscriber.buildLogEntry(event);
        assertEquals("1970-01-01T00:00:00Z", logEntry.get("timestamp"));
        assertEquals("-", logEntry.get("operator"));
        assertEquals("skill", logEntry.get("resource_type"));
        assertEquals("demo-skill", logEntry.get("resource_id"));
        assertEquals("1.0.0", logEntry.get("version"));
        assertEquals("PUBLISH", logEntry.get("operation"));
        assertEquals("SUCCESS", logEntry.get("status"));
        assertEquals("-", logEntry.get("ip"));
        assertEquals("pipeline", logEntry.get("ext"));
    }
    
    @Test
    void testAiResourceTraceLogSubscriberSkipBlankOptionalFields() {
        AiResourceTraceEvent event = new AiResourceTraceEvent(0L, "admin", "skill",
            "demo-skill", "", "PUBLISH", "SUCCESS", "127.0.0.1", "");
        
        Map<String, Object> logEntry = AiResourceTraceLogSubscriber.buildLogEntry(event);
        assertFalse(logEntry.containsKey("version"));
        assertFalse(logEntry.containsKey("ext"));
    }
    
    @Test
    void testAiResourceTraceLogSubscriberLoadedBySpi() {
        boolean loaded = false;
        for (NacosTraceSubscriber subscriber : NacosServiceLoader
            .load(NacosTraceSubscriber.class)) {
            if (AiResourceTraceLogSubscriber.NAME.equals(subscriber.getName())) {
                loaded = true;
                break;
            }
        }
        assertTrue(loaded);
    }
}
