/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model.event;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFuzzyWatchEventTest {
    
    @Test
    void testConstructorAndGetters() {
        Set<String> keys = new HashSet<>(Collections.singletonList("gk1"));
        ConfigFuzzyWatchEvent event = new ConfigFuzzyWatchEvent("conn1", keys, "pattern*", true);
        assertEquals("conn1", event.getConnectionId());
        assertEquals(keys, event.getClientExistingGroupKeys());
        assertEquals("pattern*", event.getGroupKeyPattern());
        assertTrue(event.isInitializing());
    }
    
    @Test
    void testSetters() {
        Set<String> keys = new HashSet<>();
        ConfigFuzzyWatchEvent event = new ConfigFuzzyWatchEvent("conn1", keys, "p", false);
        event.setConnectionId("conn2");
        Set<String> newKeys = new HashSet<>(Collections.singletonList("k2"));
        event.setClientExistingGroupKeys(newKeys);
        event.setGroupKeyPattern("newPattern");
        event.setInitializing(true);
        assertEquals("conn2", event.getConnectionId());
        assertEquals(newKeys, event.getClientExistingGroupKeys());
        assertEquals("newPattern", event.getGroupKeyPattern());
        assertTrue(event.isInitializing());
    }
    
    @Test
    void testNotInitializing() {
        ConfigFuzzyWatchEvent event =
            new ConfigFuzzyWatchEvent("c", new HashSet<>(), "p", false);
        assertFalse(event.isInitializing());
    }
}
