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

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link RemoteUtils} unit tests.
 *
 * @author chenglu
 * @date 2021-06-10 13:17
 */
public class RemoteUtilsTest {
    
    @Test
    public void testGetRemoteExecutorTimesOfProcessors() {
        int defaultExpectVal = 1 << 4;
        int defaultVal = RemoteUtils.getRemoteExecutorTimesOfProcessors();
        Assert.assertEquals(defaultExpectVal, defaultVal);
        
        System.setProperty("remote.executor.times.of.processors", "10");
        int val1 = RemoteUtils.getRemoteExecutorTimesOfProcessors();
        Assert.assertEquals(10, val1);
        
        System.setProperty("remote.executor.times.of.processors", "-1");
        int val2 = RemoteUtils.getRemoteExecutorTimesOfProcessors();
        Assert.assertEquals(defaultExpectVal, val2);
    }
    
    @Test
    public void testGetRemoteExecutorQueueSize() {
        int defaultExpectVal = 1 << 14;
        int defaultVal = RemoteUtils.getRemoteExecutorQueueSize();
        Assert.assertEquals(defaultExpectVal, defaultVal);
        
        System.setProperty("remote.executor.queue.size", "10");
        int val1 = RemoteUtils.getRemoteExecutorQueueSize();
        Assert.assertEquals(10, val1);
        
        System.setProperty("remote.executor.queue.size", "-1");
        int val2 = RemoteUtils.getRemoteExecutorQueueSize();
        Assert.assertEquals(defaultExpectVal, val2);
    }
}
