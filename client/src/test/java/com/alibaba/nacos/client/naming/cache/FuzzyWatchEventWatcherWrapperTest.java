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

package com.alibaba.nacos.client.naming.cache;

import com.alibaba.nacos.api.naming.listener.FuzzyWatchEventWatcher;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuzzyWatchEventWatcherWrapperTest {
    
    @Test
    void testGetUuidAndSyncServiceKeys() {
        FuzzyWatchEventWatcher watcher = Mockito.mock(FuzzyWatchEventWatcher.class);
        FuzzyWatchEventWatcherWrapper wrapper = new FuzzyWatchEventWatcherWrapper(watcher);
        assertNotNull(wrapper.getUuid());
        assertTrue(wrapper.getSyncServiceKeys().isEmpty());
    }
    
    @Test
    void testEqualsAndHashCode() {
        FuzzyWatchEventWatcher watcher = Mockito.mock(FuzzyWatchEventWatcher.class);
        FuzzyWatchEventWatcherWrapper wrapper = new FuzzyWatchEventWatcherWrapper(watcher);
        assertEquals(wrapper, wrapper);
        assertNotEquals(wrapper, null);
        assertNotEquals(wrapper, new Object());
        
        // equals only compares the watcher reference
        FuzzyWatchEventWatcherWrapper sameWatcher = new FuzzyWatchEventWatcherWrapper(watcher);
        assertEquals(wrapper, sameWatcher);
        assertEquals(wrapper.hashCode(), sameWatcher.hashCode());
        
        FuzzyWatchEventWatcher otherWatcher = Mockito.mock(FuzzyWatchEventWatcher.class);
        FuzzyWatchEventWatcherWrapper diff = new FuzzyWatchEventWatcherWrapper(otherWatcher);
        assertNotEquals(wrapper, diff);
    }
}
