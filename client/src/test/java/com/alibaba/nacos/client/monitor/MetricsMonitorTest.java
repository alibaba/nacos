/*
 *
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
 *
 */

package com.alibaba.nacos.client.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class MetricsMonitorTest {
    
    @Test
    public void testServiceInfoMapSizeMonitor() {
        int testCase = 8848;
        MetricsMonitor.setServiceInfoMapSizeMonitor(testCase);
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Gauge gauge = r.find("nacos.monitor").tags("module", "naming", "name", "serviceInfoMapSize").gauge();
            Assert.assertNotNull(gauge);
            Assert.assertEquals(testCase, (int) gauge.value());
        });
    }
    
    @Test
    public void testListenerConfigCountMonitor() {
        int testCase = 8849;
        MetricsMonitor.setListenerConfigCountMonitor(testCase);
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Gauge gauge = r.find("nacos.monitor").tags("module", "config", "name", "listenerConfigCount").gauge();
            Assert.assertNotNull(gauge);
            Assert.assertEquals(testCase, (int) gauge.value());
        });
    }
    
    @Test
    public void testConfigRequestMonitor() {
        long testCase = 111L;
        MetricsMonitor.recordConfigRequestMonitor("GET", "/testConfigRequest", "NA", testCase);
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Timer timer = r.find("nacos.client.request")
                    .tags("module", "config", "method", "GET", "url", "/testConfigRequest", "code", "NA").timer();
            Assert.assertNotNull(timer);
            Assert.assertEquals(testCase, (long) timer.totalTime(TimeUnit.MILLISECONDS));
        });
    }
    
    @Test
    public void testNamingRequestMonitor() {
        long testCase = 222L;
        MetricsMonitor.recordNamingRequestMonitor("GET", "/testNamingRequest", "NA", testCase);
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Timer timer = r.find("nacos.client.request")
                    .tags("module", "naming", "method", "GET", "url", "/testNamingRequest", "code", "NA").timer();
            Assert.assertNotNull(timer);
            Assert.assertEquals(testCase, (long) timer.totalTime(TimeUnit.MILLISECONDS));
        });
    }
}
