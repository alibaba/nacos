/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.naming.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FuzzyWatchChangeEventTest {
    
    @BeforeEach
    void setUp() throws Exception {
    }
    
    @Test
    void testFuzzyWatchChangeEventWithEmptyConstructor() {
        FuzzyWatchChangeEvent event = new FuzzyWatchChangeEvent();
        assertNull(event.getServiceName());
        assertNull(event.getGroupName());
        assertNull(event.getNamespace());
        assertNull(event.getChangeType());
        assertNull(event.getSyncType());
    }
    
    @Test
    void testFuzzyWatchChangeEventWithFullConstructor() {
        FuzzyWatchChangeEvent event = new FuzzyWatchChangeEvent("service", "group", "namespace", "ADD_SERVICE",
                "FUZZY_WATCH_INIT_NOTIFY");
        assertEquals("service", event.getServiceName());
        assertEquals("group", event.getGroupName());
        assertEquals("namespace", event.getNamespace());
        assertEquals("ADD_SERVICE", event.getChangeType());
        assertEquals("FUZZY_WATCH_INIT_NOTIFY", event.getSyncType());
    }
    
    @Test
    void testToString() {
        FuzzyWatchChangeEvent event = new FuzzyWatchChangeEvent("service", "group", "namespace", "ADD_SERVICE",
                "FUZZY_WATCH_INIT_NOTIFY");
        String expected = "FuzzyWatchChangeEvent{serviceName='service', groupName='group', namespace='namespace',"
                + " changeType='ADD_SERVICE', syncType='FUZZY_WATCH_INIT_NOTIFY'}";
        assertEquals(expected, event.toString());
    }
    
    @Test
    void testFuzzyWatchChangeEventWithNullValues() {
        FuzzyWatchChangeEvent event = new FuzzyWatchChangeEvent(null, null, null, null, null);
        assertNull(event.getServiceName());
        assertNull(event.getGroupName());
        assertNull(event.getNamespace());
        assertNull(event.getChangeType());
        assertNull(event.getSyncType());
    }
    
}