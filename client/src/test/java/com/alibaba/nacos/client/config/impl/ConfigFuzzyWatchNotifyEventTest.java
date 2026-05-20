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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigFuzzyWatchNotifyEventTest {
    
    @Test
    void testDefaultConstructor() {
        ConfigFuzzyWatchNotifyEvent event = new ConfigFuzzyWatchNotifyEvent();
        assertNotNull(event);
        assertNull(event.getGroupKey());
        assertNull(event.getGroupKeyPattern());
        assertNull(event.getChangedType());
        assertNull(event.getSyncType());
        assertNull(event.getClientUuid());
        assertNull(event.getWatcherUuid());
    }
    
    @Test
    void testBuildEventWithoutWatcher() {
        ConfigFuzzyWatchNotifyEvent event = ConfigFuzzyWatchNotifyEvent.buildEvent("groupKey",
            "pattern", "ADD", "FULL", "clientUuid");
        assertEquals("groupKey", event.getGroupKey());
        assertEquals("pattern", event.getGroupKeyPattern());
        assertEquals("ADD", event.getChangedType());
        assertEquals("FULL", event.getSyncType());
        assertEquals("clientUuid", event.getClientUuid());
        assertNull(event.getWatcherUuid());
    }
    
    @Test
    void testBuildEventWithWatcher() {
        ConfigFuzzyWatchNotifyEvent event = ConfigFuzzyWatchNotifyEvent.buildEvent("groupKey",
            "pattern", "ADD", "FULL", "clientUuid", "watcherUuid");
        assertEquals("watcherUuid", event.getWatcherUuid());
    }
}
