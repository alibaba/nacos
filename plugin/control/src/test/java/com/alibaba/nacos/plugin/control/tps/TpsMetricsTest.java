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

package com.alibaba.nacos.plugin.control.tps;

import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TpsMetricsTest {
    
    @Test
    void testMetricsAccessorsAndMessage() {
        TpsMetrics metrics = new TpsMetrics("point", "monitor", 0L, TimeUnit.SECONDS);
        TpsMetrics.Counter counter = new TpsMetrics.Counter(3, 1);
        metrics.setCounter(counter);
        
        String expectedTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(0L));
        assertEquals(expectedTime, metrics.getTimeFormatOfSecond(0L));
        assertEquals("point|monitor|SECONDS|" + expectedTime + "|3|1", metrics.getMsg());
        assertTrue(metrics.toString().contains("pointName='point'"));
        assertEquals("3|1", counter.getSimpleLog());
        assertEquals("{passCount=3, deniedCount=1}", counter.toString());
        
        metrics.setPointName("newPoint");
        metrics.setType("newType");
        metrics.setTimeStamp(1000L);
        metrics.setPeriod(TimeUnit.MINUTES);
        counter.setPassCount(5);
        counter.setDeniedCount(2);
        
        assertEquals("newPoint", metrics.getPointName());
        assertEquals("newType", metrics.getType());
        assertEquals(1000L, metrics.getTimeStamp());
        assertEquals(TimeUnit.MINUTES, metrics.getPeriod());
        assertEquals(counter, metrics.getCounter());
        assertEquals(5, counter.getPassCount());
        assertEquals(2, counter.getDeniedCount());
    }
}
