/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.utils;

import org.junit.Assert;
import org.junit.Test;

public class TimeoutUtilsTest {
    
    @Test
    public void testAddTotalTime() {
        TimeoutUtils timeoutUtils = new TimeoutUtils(10, 1);
        timeoutUtils.initLastResetTime();
        timeoutUtils.addTotalTime(1);
        Assert.assertEquals(1L, timeoutUtils.getTotalTime().get());
    }
    
    @Test
    public void testIsTimeout() {
        TimeoutUtils timeoutUtils = new TimeoutUtils(10, 1);
        timeoutUtils.initLastResetTime();
        timeoutUtils.addTotalTime(1);
        Assert.assertFalse(timeoutUtils.isTimeout());
        timeoutUtils.addTotalTime(10);
        Assert.assertTrue(timeoutUtils.isTimeout());
    }
    
    @Test
    public void testResetTotalTime() {
        TimeoutUtils timeoutUtils = new TimeoutUtils(10, -1);
        timeoutUtils.initLastResetTime();
        timeoutUtils.addTotalTime(1);
        Assert.assertEquals(1L, timeoutUtils.getTotalTime().get());
        timeoutUtils.resetTotalTime();
        Assert.assertEquals(0L, timeoutUtils.getTotalTime().get());
    }
}
