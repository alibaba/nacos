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

class AccumulateStatCountTest {
    
    @Test
    void testIncrease() {
        AccumulateStatCount accumulateStatCount = new AccumulateStatCount();
        long result = accumulateStatCount.increase();
        assertEquals(1, result);
    }
    
    @Test
    void testStat() {
        AccumulateStatCount accumulateStatCount = new AccumulateStatCount();
        long stat = accumulateStatCount.stat();
        assertEquals(0, stat);
        accumulateStatCount.increase();
        stat = accumulateStatCount.stat();
        assertEquals(1, stat);
        accumulateStatCount.increase();
        stat = accumulateStatCount.stat();
        assertEquals(1, stat);
    }
}
