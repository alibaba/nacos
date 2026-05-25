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

import com.alibaba.nacos.common.trace.event.ai.AiResourceTraceEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void testBuildImportTraceEvent() {
        AiResourceTraceEvent event = AiResourceTraceService.buildTraceEvent("skill",
            "demo-skill", "1.0.0", AiResourceTraceService.OP_IMPORT_EXECUTE,
            AiResourceTraceService.STATUS_SKIPPED, "admin", "127.0.0.1",
            "{\"source_id\":\"skills-sh\"}");
        
        assertEquals(AiResourceTraceService.OP_IMPORT_EXECUTE, event.getOperation());
        assertEquals(AiResourceTraceService.STATUS_SKIPPED, event.getStatus());
        assertEquals("{\"source_id\":\"skills-sh\"}", event.getExt());
    }
}
