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

package com.alibaba.nacos.client.config.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeNotifyBlockEventTest {
    
    @Test
    void testGettersAndSetters() {
        ChangeNotifyBlockEvent event = new ChangeNotifyBlockEvent("listener", "dataId", "group",
            "tenant", 1L, 2L, "stack");
        assertEquals("dataId", event.getDataId());
        assertEquals("group", event.getGroup());
        assertEquals("tenant", event.getTenant());
        assertEquals(1L, event.getStartTime());
        assertEquals(2L, event.getCurrentTime());
        assertEquals("stack", event.getBlockStack());
        
        event.setDataId("dataId2");
        event.setGroup("group2");
        event.setTenant("tenant2");
        event.setStartTime(10L);
        event.setCurrentTime(20L);
        event.setBlockStack("stack2");
        
        assertEquals("dataId2", event.getDataId());
        assertEquals("group2", event.getGroup());
        assertEquals("tenant2", event.getTenant());
        assertEquals(10L, event.getStartTime());
        assertEquals(20L, event.getCurrentTime());
        assertEquals("stack2", event.getBlockStack());
    }
    
    @Test
    void testToString() {
        ChangeNotifyBlockEvent event = new ChangeNotifyBlockEvent("listener", "dataId", "group",
            "tenant", 1L, 2L, "stack");
        String s = event.toString();
        assertTrue(s.contains("dataId='dataId'"));
        assertTrue(s.contains("group='group'"));
        assertTrue(s.contains("tenant='tenant'"));
        assertTrue(s.contains("listener='listener'"));
        assertTrue(s.contains("blockStack='stack'"));
    }
}
