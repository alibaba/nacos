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

import com.alibaba.nacos.api.config.listener.FuzzyWatchEventWatcher;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFuzzyWatcherWrapperTest {
    
    @Test
    void testGetUuidAndSyncGroupKeys() {
        FuzzyWatchEventWatcher watcher = Mockito.mock(FuzzyWatchEventWatcher.class);
        ConfigFuzzyWatcherWrapper wrapper = new ConfigFuzzyWatcherWrapper(watcher);
        assertNotNull(wrapper.getUuid());
        assertTrue(wrapper.getSyncGroupKeys().isEmpty());
    }
    
    @Test
    void testEqualsAndHashCode() {
        FuzzyWatchEventWatcher watcher = Mockito.mock(FuzzyWatchEventWatcher.class);
        ConfigFuzzyWatcherWrapper wrapper = new ConfigFuzzyWatcherWrapper(watcher);
        assertEquals(wrapper, wrapper);
        assertNotEquals(wrapper, null);
        assertNotEquals(wrapper, new Object());
        
        // different uuid means not equal even for same watcher
        ConfigFuzzyWatcherWrapper other = new ConfigFuzzyWatcherWrapper(watcher);
        assertNotEquals(wrapper, other);
        assertNotEquals(wrapper.hashCode(), other.hashCode());
        
        // same uuid, same watcher => equal
        ConfigFuzzyWatcherWrapper sameRef = new ConfigFuzzyWatcherWrapper(watcher);
        sameRef.uuid = wrapper.getUuid();
        assertEquals(wrapper, sameRef);
        assertEquals(wrapper.hashCode(), sameRef.hashCode());
        
        // different watcher => not equal
        FuzzyWatchEventWatcher watcher2 = Mockito.mock(FuzzyWatchEventWatcher.class);
        ConfigFuzzyWatcherWrapper diffWatcher = new ConfigFuzzyWatcherWrapper(watcher2);
        diffWatcher.uuid = wrapper.getUuid();
        assertNotEquals(wrapper, diffWatcher);
    }
}
