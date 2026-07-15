/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link RemoteUtils} unit tests.
 *
 * @author chenglu
 * @date 2021-06-10 13:17
 */
class RemoteUtilsTest {
    
    @Test
    void testGetRemoteExecutorTimesOfProcessors() {
        int defaultExpectVal = 1 << 4;
        int defaultVal = RemoteUtils.getRemoteExecutorTimesOfProcessors();
        assertEquals(defaultExpectVal, defaultVal);
        
        System.setProperty("remote.executor.times.of.processors", "10");
        int val1 = RemoteUtils.getRemoteExecutorTimesOfProcessors();
        assertEquals(10, val1);
        
        System.setProperty("remote.executor.times.of.processors", "-1");
        int val2 = RemoteUtils.getRemoteExecutorTimesOfProcessors();
        assertEquals(defaultExpectVal, val2);
    }
    
    @Test
    void testGetRemoteExecutorQueueSize() {
        int defaultExpectVal = 1 << 14;
        int defaultVal = RemoteUtils.getRemoteExecutorQueueSize();
        assertEquals(defaultExpectVal, defaultVal);
        
        System.setProperty("remote.executor.queue.size", "10");
        int val1 = RemoteUtils.getRemoteExecutorQueueSize();
        assertEquals(10, val1);
        
        System.setProperty("remote.executor.queue.size", "-1");
        int val2 = RemoteUtils.getRemoteExecutorQueueSize();
        assertEquals(defaultExpectVal, val2);
    }
    
    @Test
    void testGetRemoteExecutorTimesOfProcessorsWhenNotDigits() {
        System.clearProperty("remote.executor.times.of.processors");
        System.setProperty("remote.executor.times.of.processors", "not-a-number");
        try {
            int val = RemoteUtils.getRemoteExecutorTimesOfProcessors();
            assertEquals(1 << 4, val);
        } finally {
            System.clearProperty("remote.executor.times.of.processors");
        }
    }
    
    @Test
    void testGetRemoteExecutorQueueSizeWhenNotDigits() {
        System.clearProperty("remote.executor.queue.size");
        System.setProperty("remote.executor.queue.size", "not-a-number");
        try {
            int val = RemoteUtils.getRemoteExecutorQueueSize();
            assertEquals(1 << 14, val);
        } finally {
            System.clearProperty("remote.executor.queue.size");
        }
    }
}
