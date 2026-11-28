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

package com.alibaba.nacos.plugin.control.context;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlContextTest {
    
    @Test
    void testEmptyContext() {
        ControlContext context = ControlContext.empty();
        
        assertTrue(context.isEmpty());
        assertEquals(0L, context.getTimestamp());
        assertEquals(0L, context.getMetric(ControlContext.PUSH_PENDING_TASKS));
        assertFalse(context.hasMetric(ControlContext.PUSH_PENDING_TASKS));
        assertNull(context.getAttribute(ControlContext.PUSH_SERVICE_NAME));
        assertTrue(context.getMetrics().isEmpty());
        assertTrue(context.getAttributes().isEmpty());
    }
    
    @Test
    void testBuilderAndAccessors() {
        ControlContext context = ControlContext.builder()
            .timestamp(100L)
            .metric(ControlContext.PUSH_PENDING_TASKS, 12L)
            .metric(ControlContext.PUSH_TARGET_SUBSCRIBERS, 3L)
            .attribute(ControlContext.PUSH_SERVICE_NAMESPACE, "public")
            .attribute(ControlContext.PUSH_SERVICE_NAME, "demo")
            .build();
        
        assertFalse(context.isEmpty());
        assertEquals(100L, context.getTimestamp());
        assertEquals(12L, context.getMetric(ControlContext.PUSH_PENDING_TASKS));
        assertTrue(context.hasMetric(ControlContext.PUSH_PENDING_TASKS));
        assertEquals(0L, context.getMetric("unknown"));
        assertFalse(context.hasMetric("unknown"));
        assertEquals("public", context.getAttribute(ControlContext.PUSH_SERVICE_NAMESPACE));
        assertEquals(2, context.getMetrics().size());
        assertEquals(2, context.getAttributes().size());
    }
    
    @Test
    void testContextIsImmutableAndCopied() {
        ControlContext.Builder builder = ControlContext.builder()
            .metric(ControlContext.CONNECTION_CURRENT_TOTAL, 1L);
        ControlContext context = builder.build();
        builder.metric(ControlContext.CONNECTION_CURRENT_TOTAL, 2L);
        
        assertEquals(1L, context.getMetric(ControlContext.CONNECTION_CURRENT_TOTAL));
        assertThrows(UnsupportedOperationException.class, () -> context.getMetrics().clear());
        assertThrows(UnsupportedOperationException.class, () -> context.getAttributes().clear());
    }
    
    @Test
    void testNullNamesAreIgnored() {
        ControlContext context = ControlContext.builder()
            .metric(null, 1L)
            .attribute(null, "value")
            .build();
        
        assertTrue(context.isEmpty());
    }
    
    @Test
    void testEmptyContextIsShared() {
        assertEquals(Collections.emptyMap(), ControlContext.empty().getMetrics());
    }
}
