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
        MetricsMonitor.setServiceInfoMapSizeMonitor(8848);
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Gauge gauge = r.find("nacos.monitor").tags("module", "naming", "name", "serviceInfoMapSize").gauge();
            Assert.assertNotNull(gauge);
            Assert.assertEquals(8848, (int) gauge.value());
        });
    }
    
    @Test
    public void testListenerConfigCountMonitor() {
        MetricsMonitor.setListenerConfigCountMonitor(8849);
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Gauge gauge = r.find("nacos.monitor").tags("module", "config", "name", "listenerConfigCount").gauge();
            Assert.assertNotNull(gauge);
            Assert.assertEquals(8849, (int) gauge.value());
        });
    }
    
    @Test
    public void testConfigRequestMonitor() {
        MetricsMonitor.recordConfigRequestMonitor("GET", "/testConfigRequest", "NA", 111L);
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Timer timer = r.find("nacos.client.request")
                    .tags("module", "config", "method", "GET", "url", "/testConfigRequest", "code", "NA").timer();
            Assert.assertNotNull(timer);
            Assert.assertEquals(111L, (long) timer.totalTime(TimeUnit.MILLISECONDS));
        });
    }
    
    @Test
    public void testNamingRequestMonitor() {
        MetricsMonitor.recordNamingRequestMonitor("GET", "/testNamingRequest", "NA", 222L);
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Timer timer = r.find("nacos.client.request")
                    .tags("module", "naming", "method", "GET", "url", "/testNamingRequest", "code", "NA").timer();
            Assert.assertNotNull(timer);
            Assert.assertEquals(222L, (long) timer.totalTime(TimeUnit.MILLISECONDS));
        });
    }
}
