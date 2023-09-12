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

package com.alibaba.nacos.client.monitor.naming;

import com.alibaba.nacos.client.monitor.MetricsMonitor;
import com.alibaba.nacos.common.utils.HttpMethod;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class NamingMetricsTest {
    
    private static String moduleName = null;
    
    @BeforeClass
    public static void setUp() {
        System.setProperty(MetricsMonitor.getNacosMetricsEnableProperty(), "true");
        System.setProperty(MetricsMonitor.getNacosOtelEnableProperty(), "true");
        MetricsMonitor.init();
        moduleName = NamingMetrics.getMetricModuleName();
    }
    
    @AfterClass
    public static void tearDown() {
        System.clearProperty(MetricsMonitor.getNacosMetricsEnableProperty());
        System.clearProperty(MetricsMonitor.getNacosOtelEnableProperty());
    }
    
    @Test
    public void testServiceInfoMapSizeGauge() {
        int testCase = 8848;
        NamingMetrics.setServiceInfoMapSizeGauge(testCase);
        String meterName = NamingMetrics.getDefaultMeterName();
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Gauge gauge = r.find(meterName).tags("module", moduleName, "name", "serviceInfoMapSize").gauge();
            Assert.assertNotNull(gauge);
            Assert.assertEquals(testCase, (int) gauge.value());
        });
    }
    
    @Test
    public void testServiceInfoFailoverCacheSizeGauge() {
        int testCase = 8849;
        NamingMetrics.setServiceInfoFailoverCacheSizeGauge(testCase);
        String meterName = NamingMetrics.getCacheMeterName();
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Gauge gauge = r.find(meterName).tags("module", moduleName, "name", "serviceInfoFailoverCacheSize").gauge();
            Assert.assertNotNull(gauge);
            Assert.assertEquals(testCase, (int) gauge.value());
        });
    }
    
    @Test
    public void testServerNumberGauge() {
        int testCase = 8850;
        NamingMetrics.setServerNumberGauge(testCase);
        String meterName = NamingMetrics.getCommonMeterName();
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Gauge gauge = r.find(meterName).tags("module", moduleName, "name", "serverNumber").gauge();
            Assert.assertNotNull(gauge);
            Assert.assertEquals(testCase, (int) gauge.value());
        });
    }
    
    @Test
    public void testGaugeCollectionSize() {
        ArrayList<Integer> testList = new ArrayList<>();
        for (int i : new int[] {1, 2, 3, 4, 5}) {
            testList.add(i);
        }
        String meterName = NamingMetrics.getCommonMeterName();
        NamingMetrics.gaugeCollectionSize(meterName, "testCollectionTagName", testList);
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Gauge gauge = r.find(meterName).tags("module", moduleName, "name", "testCollectionTagName").gauge();
            Assert.assertNotNull(gauge);
            Assert.assertEquals(testList.size(), (int) gauge.value());
        });
    }
    
    @Test
    public void testGaugeMapSize() {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("testKey1", "testValue1");
        testMap.put("testKey2", "testValue2");
        testMap.put("testKey3", "testValue3");
        String meterName = NamingMetrics.getCommonMeterName();
        NamingMetrics.gaugeMapSize(meterName, "testMapTagName", testMap);
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Gauge gauge = r.find(meterName).tags("module", moduleName, "name", "testMapTagName").gauge();
            Assert.assertNotNull(gauge);
            Assert.assertEquals(testMap.size(), (int) gauge.value());
        });
    }
    
    @Test
    public void testNamingRequestTimer() {
        long testCase = 222L;
        NamingMetrics.recordNamingRequestTimer(HttpMethod.GET, "/testNamingRequest", "NA", testCase);
        String meterName = NamingMetrics.getTimerMeterName();
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Timer timer = r.find(meterName)
                    .tags("module", moduleName, "name", "namingRequest", "method", "GET", "url", "/testNamingRequest",
                            "code", "NA").timer();
            Assert.assertNotNull(timer);
            Assert.assertEquals(testCase, (long) timer.totalTime(TimeUnit.MILLISECONDS));
        });
    }
    
    @Test
    public void testRpcCostDurationTimer() {
        long testCase = 333L;
        NamingMetrics.recordRpcCostDurationTimer("GRPC", "127.0.0.1", "200", testCase);
        String meterName = NamingMetrics.getTimerMeterName();
        
        MetricsMonitor.getNacosMeterRegistry().getRegistries().forEach(r -> {
            Timer timer = r.find(meterName)
                    .tags("module", moduleName, "name", "rpcCostDuration", "connectionType", "GRPC", "currentServer",
                            "127.0.0.1", "rpcResultCode", "200").timer();
            Assert.assertNotNull(timer);
            Assert.assertEquals(testCase, (long) timer.totalTime(TimeUnit.MILLISECONDS));
        });
    }
    
}
