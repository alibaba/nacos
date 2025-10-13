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

package com.alibaba.nacos.api.config.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigFuzzyWatchChangeEventTest {
    
    @Test
    void testBuildWithValidParameters() {
        ConfigFuzzyWatchChangeEvent event = ConfigFuzzyWatchChangeEvent.build(
                "test-namespace", "test-group", "test-dataId", "ADD_CONFIG", 
                "FUZZY_WATCH_INIT_NOTIFY");
        
        assertNotNull(event);
        assertEquals("test-namespace", event.getNamespace());
        assertEquals("test-group", event.getGroup());
        assertEquals("test-dataId", event.getDataId());
        assertEquals("ADD_CONFIG", event.getChangedType());
        assertEquals("FUZZY_WATCH_INIT_NOTIFY", event.getSyncType());
    }
    
    @Test
    void testBuildWithNullParameters() {
        ConfigFuzzyWatchChangeEvent event = ConfigFuzzyWatchChangeEvent.build(
                null, null, null, null, null);
        
        assertNotNull(event);
        assertNull(event.getNamespace());
        assertNull(event.getGroup());
        assertNull(event.getDataId());
        assertNull(event.getChangedType());
        assertNull(event.getSyncType());
    }
    
    @Test
    void testToString() {
        ConfigFuzzyWatchChangeEvent event = ConfigFuzzyWatchChangeEvent.build(
                "test-namespace", "test-group", "test-dataId", "ADD_CONFIG", 
                "FUZZY_WATCH_INIT_NOTIFY");
        
        String expected = "ConfigFuzzyWatchChangeEvent{group='test-group', dataId='test-dataId', "
                + "namespace='test-namespace', changedType='ADD_CONFIG', syncType='FUZZY_WATCH_INIT_NOTIFY'}";
        assertEquals(expected, event.toString());
    }
}