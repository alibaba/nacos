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

package com.alibaba.nacos.client.naming.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NamingFuzzyWatchNotifyEventTest {
    
    @Test
    void buildWithoutWatcherUuid() {
        NamingFuzzyWatchNotifyEvent event = NamingFuzzyWatchNotifyEvent.build("scope", "pattern",
            "serviceKey", "ADD", "FULL");
        assertEquals("scope", event.scope());
        assertEquals("scope", event.getScope());
        assertEquals("pattern", event.getPattern());
        assertEquals("serviceKey", event.getServiceKey());
        assertEquals("ADD", event.getChangedType());
        assertEquals("FULL", event.getSyncType());
        assertNull(event.getWatcherUuid());
    }
    
    @Test
    void buildWithWatcherUuid() {
        NamingFuzzyWatchNotifyEvent event = NamingFuzzyWatchNotifyEvent.build("scope", "pattern",
            "serviceKey", "ADD", "FULL", "watcher-1");
        assertEquals("watcher-1", event.getWatcherUuid());
    }
}
