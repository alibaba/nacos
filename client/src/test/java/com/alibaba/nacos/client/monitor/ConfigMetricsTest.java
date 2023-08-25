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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class ConfigMetricsTest {
    
    private static String moduleName = null;
    
    @BeforeClass
    public static void setUp() {
        System.setProperty(MetricsMonitor.getNacosMetricsEnableProperty(), "true");
        System.setProperty(MetricsMonitor.getNacosOtelEnableProperty(), "true");
        MetricsMonitor.init();
        moduleName = ConfigMetrics.getMetricModuleName();
    }
    
    @AfterClass
    public static void tearDown() {
        System.clearProperty(MetricsMonitor.getNacosMetricsEnableProperty());
        System.clearProperty(MetricsMonitor.getNacosOtelEnableProperty());
    }
    
    @Test
    public void testListenerConfigCountGauge() {
        int testCase = 8849;
        ConfigMetrics.setListenerConfigCountGauge(testCase);
        String meterName = ConfigMetrics.getDefaultMeterName();
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Gauge gauge = r.find(meterName).tags("module", moduleName, "name", "listenerConfigCount").gauge();
            Assert.assertNotNull(gauge);
            Assert.assertEquals(testCase, (int) gauge.value());
        });
    }
    
    @Test
    public void testSyncWithServerCounter() {
        ConfigMetrics.incSyncWithServerCounter();
        String meterName = ConfigMetrics.getCounterMeterName();
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Counter counter = r.find(meterName).tags("module", moduleName, "name", "syncWithServer").counter();
            Assert.assertNotNull(counter);
            Assert.assertEquals(1, (int) counter.count());
        });
    }
    
    @Test
    public void testQuerySuccessCounter() {
        ConfigMetrics.incQuerySuccessCounter();
        String meterName = ConfigMetrics.getCounterMeterName();
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Counter counter = r.find(meterName).tags("module", moduleName, "name", "querySuccess").counter();
            Assert.assertNotNull(counter);
            Assert.assertEquals(1, (int) counter.count());
        });
    }
    
    @Test
    public void testQueryFailedCounter() {
        ConfigMetrics.incQueryFailedCounter();
        String meterName = ConfigMetrics.getCounterMeterName();
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Counter counter = r.find(meterName).tags("module", moduleName, "name", "queryFailed").counter();
            Assert.assertNotNull(counter);
            Assert.assertEquals(1, (int) counter.count());
        });
    }
    
    @Test
    public void testPublishSuccessCounter() {
        ConfigMetrics.incPublishSuccessCounter();
        String meterName = ConfigMetrics.getCounterMeterName();
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Counter counter = r.find(meterName).tags("module", moduleName, "name", "publishSuccess").counter();
            Assert.assertNotNull(counter);
            Assert.assertEquals(1, (int) counter.count());
        });
    }
    
    @Test
    public void testPublishFailedCounter() {
        ConfigMetrics.incPublishFailedCounter();
        String meterName = ConfigMetrics.getCounterMeterName();
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Counter counter = r.find(meterName).tags("module", moduleName, "name", "publishFailed").counter();
            Assert.assertNotNull(counter);
            Assert.assertEquals(1, (int) counter.count());
        });
    }
    
    @Test
    public void testRemoveSuccessCounter() {
        ConfigMetrics.incRemoveSuccessCounter();
        String meterName = ConfigMetrics.getCounterMeterName();
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Counter counter = r.find(meterName).tags("module", moduleName, "name", "removeSuccess").counter();
            Assert.assertNotNull(counter);
            Assert.assertEquals(1, (int) counter.count());
        });
    }
    
    @Test
    public void testRemoveFailedCounter() {
        ConfigMetrics.incRemoveFailedCounter();
        String meterName = ConfigMetrics.getCounterMeterName();
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Counter counter = r.find(meterName).tags("module", moduleName, "name", "removeFailed").counter();
            Assert.assertNotNull(counter);
            Assert.assertEquals(1, (int) counter.count());
        });
    }
    
    @Test
    public void testConfigRequestTimer() {
        long testCase = 111L;
        ConfigMetrics.recordConfigRequestTimer("GET", "/testConfigRequest", "NA", testCase);
        String meterName = ConfigMetrics.getTimerMeterName();
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Timer timer = r.find(meterName)
                    .tags("module", moduleName, "name", "configRequest", "method", "GET", "url", "/testConfigRequest",
                            "code", "NA").timer();
            Assert.assertNotNull(timer);
            Assert.assertEquals(testCase, (long) timer.totalTime(TimeUnit.MILLISECONDS));
        });
    }
    
    @Test
    public void testNotifyCostDurationTimer() {
        long testCase = 222L;
        ConfigMetrics.recordNotifyCostDurationTimer("testClientName", "testDataId", "testGroup", "testTenant",
                testCase);
        String meterName = ConfigMetrics.getTimerMeterName();
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Timer timer = r.find(meterName)
                    .tags("module", moduleName, "name", "notifyCostDuration", "clientName", "testClientName", "dataId",
                            "testDataId", "group", "testGroup", "tenant", "testTenant").timer();
            Assert.assertNotNull(timer);
            Assert.assertEquals(testCase, (long) timer.totalTime(TimeUnit.MILLISECONDS));
        });
    }
    
    @Test
    public void testRpcCostDurationTimer() {
        long testCase = 333L;
        ConfigMetrics.recordRpcCostDurationTimer("GRPC", "127.0.0.1", "200", testCase);
        String meterName = ConfigMetrics.getTimerMeterName();
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Timer timer = r.find(meterName)
                    .tags("module", moduleName, "name", "rpcCostDuration", "connectionType", "GRPC", "currentServer",
                            "127.0.0.1", "rpcResultCode", "200").timer();
            Assert.assertNotNull(timer);
            Assert.assertEquals(testCase, (long) timer.totalTime(TimeUnit.MILLISECONDS));
        });
    }
}
