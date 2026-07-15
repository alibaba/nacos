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

package com.alibaba.nacos.naming.monitor;

import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.core.monitor.NacosMeterRegistryCenter;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.ref.Reference;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NamingDynamicMeterRefreshServiceTest {
    
    private final NamingDynamicMeterRefreshService refreshService =
        new NamingDynamicMeterRefreshService();
    
    private CompositeMeterRegistry meterRegistry;
    
    private SimpleMeterRegistry simpleMeterRegistry;
    
    @BeforeEach
    void setUp() {
        meterRegistry = NacosMeterRegistryCenter.getMeterRegistry(
            NacosMeterRegistryCenter.TOPN_SERVICE_CHANGE_REGISTRY);
        meterRegistry.clear();
        simpleMeterRegistry = new SimpleMeterRegistry();
        meterRegistry.add(simpleMeterRegistry);
    }
    
    @AfterEach
    void tearDown() {
        meterRegistry.clear();
        meterRegistry.remove(simpleMeterRegistry);
    }
    
    @Test
    void testRefreshTopnServiceChangeCountRegistersGauge() {
        Service service = Service.newService("namespace", "group", "service");
        String serviceKey = "namespace" + UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR
            + service.getGroupedServiceName();
        // Micrometer gauges weakly reference their backing number.
        AtomicInteger serviceChangeCount = new AtomicInteger(7);
        ServiceTopNCounter counter = Mockito.mock(ServiceTopNCounter.class);
        Mockito.when(counter.getCounterOfTopN(10))
            .thenReturn(Collections.singletonList(Pair.with(serviceKey, serviceChangeCount)));
        try (MockedStatic<MetricsMonitor> metricsMonitor =
            Mockito.mockStatic(MetricsMonitor.class)) {
            metricsMonitor.when(MetricsMonitor::getServiceChangeCount).thenReturn(counter);
            refreshService.refreshTopnServiceChangeCount();
        }
        
        Gauge gauge = simpleMeterRegistry.find("service_change_count")
            .tag("service", serviceKey)
            .gauge();
        assertNotNull(gauge);
        assertEquals(7D, gauge.value());
        Reference.reachabilityFence(serviceChangeCount);
    }
    
    @Test
    void testResetTopnServiceChangeCountClearsCounter() {
        ServiceTopNCounter counter = Mockito.mock(ServiceTopNCounter.class);
        try (MockedStatic<MetricsMonitor> metricsMonitor =
            Mockito.mockStatic(MetricsMonitor.class)) {
            metricsMonitor.when(MetricsMonitor::getServiceChangeCount).thenReturn(counter);
            refreshService.resetTopnServiceChangeCount();
        }
        Mockito.verify(counter).reset();
    }
}
