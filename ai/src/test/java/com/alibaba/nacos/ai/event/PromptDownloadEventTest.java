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

package com.alibaba.nacos.ai.event;

import com.alibaba.nacos.common.notify.SlowEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for {@link PromptDownloadEvent}.
 *
 * @author nacos
 */
class PromptDownloadEventTest {
    
    @Test
    void testConstructorAndGetters() {
        PromptDownloadEvent event = new PromptDownloadEvent("public", "test-prompt", "0.0.1");
        
        assertEquals("public", event.getNamespaceId());
        assertEquals("test-prompt", event.getName());
        assertEquals("0.0.1", event.getVersion());
    }
    
    @Test
    void testConstructorAcceptsNullValues() {
        PromptDownloadEvent event = new PromptDownloadEvent(null, null, null);
        
        assertNull(event.getNamespaceId());
        assertNull(event.getName());
        assertNull(event.getVersion());
    }
    
    @Test
    void testIsSlowEventSubtype() {
        PromptDownloadEvent event = new PromptDownloadEvent("public", "test-prompt", "0.0.1");
        
        assertTrue(event instanceof SlowEvent);
    }
}
