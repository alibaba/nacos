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

import com.alibaba.nacos.core.monitor.NacosMeterRegistryCenter;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.sys.env.EnvUtil;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamingDynamicMeterRefreshServiceTest {
    
    private final NamingDynamicMeterRefreshService refreshService =
        new NamingDynamicMeterRefreshService();
    
    private ConfigurableEnvironment cachedEnvironment;
    
    private SimpleMeterRegistry simpleMeterRegistry;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new MockEnvironment());
        simpleMeterRegistry = new SimpleMeterRegistry();
        NacosMeterRegistryCenter.getMeterRegistry(
            NacosMeterRegistryCenter.TOPN_SERVICE_CHANGE_REGISTRY).add(simpleMeterRegistry);
        MetricsMonitor.getServiceChangeCount().reset();
        NacosMeterRegistryCenter.clear(NacosMeterRegistryCenter.TOPN_SERVICE_CHANGE_REGISTRY);
    }
    
    @AfterEach
    void tearDown() {
        MetricsMonitor.getServiceChangeCount().reset();
        NacosMeterRegistryCenter.clear(NacosMeterRegistryCenter.TOPN_SERVICE_CHANGE_REGISTRY);
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void testRefreshTopnServiceChangeCountRegistersGauge() {
        Service service = Service.newService("namespace", "group", "service");
        MetricsMonitor.getServiceChangeCount().set(service, 7);
        
        refreshService.refreshTopnServiceChangeCount();
        
        CompositeMeterRegistry registry = NacosMeterRegistryCenter.getMeterRegistry(
            NacosMeterRegistryCenter.TOPN_SERVICE_CHANGE_REGISTRY);
        Gauge gauge = registry.find("service_change_count")
            .tag("service",
                "namespace" + UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR
                    + service.getGroupedServiceName())
            .gauge();
        assertNotNull(gauge);
        assertEquals(7D, gauge.value());
    }
    
    @Test
    void testResetTopnServiceChangeCountClearsCounter() {
        MetricsMonitor.getServiceChangeCount()
            .set(Service.newService("namespace", "group", "service"), 3);
        
        refreshService.resetTopnServiceChangeCount();
        
        assertTrue(MetricsMonitor.getServiceChangeCount().getCounterOfTopN(10).isEmpty());
    }
}
