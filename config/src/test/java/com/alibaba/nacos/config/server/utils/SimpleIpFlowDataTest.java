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

public class SimpleIpFlowDataTest {
    
    @Test
    public void testIncrementAndGet() {
        
        SimpleIpFlowData simpleIpFlowData = new SimpleIpFlowData(5, 10000);
        Assert.assertEquals(1, simpleIpFlowData.incrementAndGet("127.0.0.1"));
        Assert.assertEquals(2, simpleIpFlowData.incrementAndGet("127.0.0.1"));
        Assert.assertEquals(3, simpleIpFlowData.incrementAndGet("127.0.0.1"));
        Assert.assertEquals(1, simpleIpFlowData.incrementAndGet("127.0.0.2"));
        Assert.assertEquals(2, simpleIpFlowData.incrementAndGet("127.0.0.2"));
        
    }
    
    @Test
    public void testGetCurrentCount() {
        SimpleIpFlowData simpleIpFlowData = new SimpleIpFlowData(3, 10000);
        simpleIpFlowData.incrementAndGet("127.0.0.1");
        simpleIpFlowData.incrementAndGet("127.0.0.1");
        simpleIpFlowData.incrementAndGet("127.0.0.1");
        Assert.assertEquals(3, simpleIpFlowData.getCurrentCount("127.0.0.1"));
        simpleIpFlowData.rotateSlot();
        Assert.assertEquals(0, simpleIpFlowData.getCurrentCount("127.0.0.1"));
        Assert.assertEquals(1, simpleIpFlowData.getAverageCount());
    }
    
}
