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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleFlowDataTest {
    
    @Test
    void testAddAndGet() {
        
        SimpleFlowData simpleFlowData = new SimpleFlowData(5, 10000);
        assertEquals(10, simpleFlowData.addAndGet(10));
        assertEquals(20, simpleFlowData.addAndGet(10));
    }
    
    @Test
    void testIncrementAndGet() {
        
        SimpleFlowData simpleFlowData = new SimpleFlowData(5, 10000);
        assertEquals(1, simpleFlowData.incrementAndGet());
        assertEquals(2, simpleFlowData.incrementAndGet());
        assertEquals(3, simpleFlowData.incrementAndGet());
        
    }
    
    @Test
    void testGetSlotInfo() {
        SimpleFlowData simpleFlowData = new SimpleFlowData(5, 10000);
        simpleFlowData.incrementAndGet();
        simpleFlowData.incrementAndGet();
        simpleFlowData.incrementAndGet();
        assertEquals("0 0 0 0 3", simpleFlowData.getSlotInfo());
    }
    
    @Test
    void testGetSlotInfo2() {
        SimpleFlowData simpleFlowData = new SimpleFlowData(5, 10000);
        simpleFlowData.incrementAndGet();
        simpleFlowData.rotateSlot();
        simpleFlowData.addAndGet(9);
        simpleFlowData.rotateSlot();
        simpleFlowData.incrementAndGet();
        assertEquals("0 0 1 9 1", simpleFlowData.getSlotInfo());
        assertEquals(1, simpleFlowData.getCurrentCount());
        assertEquals(2, simpleFlowData.getAverageCount());
        assertEquals(5, simpleFlowData.getSlotCount());
    }
    
    @Test
    void testGetCount() {
        SimpleFlowData simpleFlowData = new SimpleFlowData(5, 10000);
        simpleFlowData.addAndGet(2);
        simpleFlowData.rotateSlot();
        simpleFlowData.addAndGet(3);
        simpleFlowData.rotateSlot();
        simpleFlowData.incrementAndGet();
        assertEquals("0 0 2 3 1", simpleFlowData.getSlotInfo());
        assertEquals(2, simpleFlowData.getCount(2));
    }
    
}
