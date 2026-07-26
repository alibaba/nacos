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

package com.alibaba.nacos.naming.healthcheck.v2.processor;

import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import com.alibaba.nacos.naming.healthcheck.v2.PersistentHealthStatusSynchronizer;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class HealthCheckCommonV2Test {
    
    @Mock
    private SwitchDomain.HealthParams healthParams;
    
    @Mock
    private HealthCheckTaskV2 healthCheckTaskV2;
    
    @Mock
    private Service service;
    
    @Mock
    private IpPortBasedClient ipPortBasedClient;
    
    @Mock
    private HealthCheckInstancePublishInfo healthCheckInstancePublishInfo;
    
    @Mock
    private DistroMapper distroMapper;
    
    @Mock
    private SwitchDomain switchDomain;
    
    @Mock
    private PersistentHealthStatusSynchronizer healthStatusSynchronizer;
    
    private HealthCheckCommonV2 healthCheckCommonV2;
    
    @BeforeEach
    void setUp() {
        healthCheckCommonV2 = new HealthCheckCommonV2();
        ReflectionTestUtils.setField(healthCheckCommonV2, "distroMapper", distroMapper);
        ReflectionTestUtils.setField(healthCheckCommonV2, "switchDomain", switchDomain);
        ReflectionTestUtils.setField(healthCheckCommonV2, "healthStatusSynchronizer",
            healthStatusSynchronizer);
        when(healthCheckTaskV2.getClient()).thenReturn(ipPortBasedClient);
        when(ipPortBasedClient.getInstancePublishInfo(service))
            .thenReturn(healthCheckInstancePublishInfo);
        when(healthCheckInstancePublishInfo.getOkCount()).thenReturn(new AtomicInteger());
        when(healthCheckInstancePublishInfo.getFailCount()).thenReturn(new AtomicInteger());
    }
    
    @Test
    void testReEvaluateCheckRt() {
        healthCheckCommonV2.reEvaluateCheckRt(1, healthCheckTaskV2, healthParams);
        
        verify(healthParams, times(2)).getMax();
        verify(healthParams, times(1)).getMin();
        verify(healthParams, times(2)).getFactor();
        
        verify(healthCheckTaskV2).getCheckRtWorst();
        verify(healthCheckTaskV2).getCheckRtBest();
        verify(healthCheckTaskV2).getCheckRtNormalized();
    }
    
    @Test
    void testReEvaluateCheckRtUpdatesBest() {
        when(healthCheckTaskV2.getCheckRtBest()).thenReturn(10L);
        when(healthParams.getMax()).thenReturn(100);
        
        healthCheckCommonV2.reEvaluateCheckRt(1L, healthCheckTaskV2, healthParams);
        
        verify(healthCheckTaskV2).setCheckRtBest(1L);
    }
    
    @Test
    void testReEvaluateCheckRtUsesMin() {
        when(healthParams.getMax()).thenReturn(100);
        when(healthParams.getMin()).thenReturn(10);
        
        healthCheckCommonV2.reEvaluateCheckRt(1L, healthCheckTaskV2, healthParams);
        
        verify(healthCheckTaskV2).setCheckRtNormalized(10L);
    }
    
    @Test
    void testCheckOk() {
        healthCheckCommonV2.checkOk(healthCheckTaskV2, service, "test checkOk");
        
        verify(healthCheckTaskV2).getClient();
        verify(service).getGroupedServiceName();
        verify(ipPortBasedClient).getInstancePublishInfo(service);
        verify(healthCheckInstancePublishInfo).isHealthy();
        verify(healthCheckInstancePublishInfo).getCluster();
        verify(healthCheckInstancePublishInfo).resetFailCount();
        verify(healthCheckInstancePublishInfo).finishCheck();
        
    }
    
    @Test
    void testCheckFail() {
        when(healthCheckInstancePublishInfo.isHealthy()).thenReturn(true);
        healthCheckCommonV2.checkFail(healthCheckTaskV2, service, "test checkFail");
        
        verify(healthCheckTaskV2).getClient();
        verify(service).getGroupedServiceName();
        verify(ipPortBasedClient).getInstancePublishInfo(service);
        verify(healthCheckInstancePublishInfo).isHealthy();
        verify(healthCheckInstancePublishInfo).getCluster();
        verify(healthCheckInstancePublishInfo).resetOkCount();
        verify(healthCheckInstancePublishInfo).finishCheck();
    }
    
    @Test
    void testCheckFailNow() {
        when(healthCheckInstancePublishInfo.isHealthy()).thenReturn(true);
        healthCheckCommonV2.checkFailNow(healthCheckTaskV2, service, "test checkFailNow");
        
        verify(healthCheckTaskV2).getClient();
        verify(service).getGroupedServiceName();
        verify(ipPortBasedClient).getInstancePublishInfo(service);
        verify(healthCheckInstancePublishInfo).isHealthy();
        verify(healthCheckInstancePublishInfo).getCluster();
        verify(healthCheckInstancePublishInfo).resetOkCount();
        verify(healthCheckInstancePublishInfo).finishCheck();
    }
    
    @Test
    void testCheckOkChangesHealthWhenThresholdReached() {
        HealthCheckInstancePublishInfo instance = newHealthCheckInstance(false);
        mockHealthyCheckContext(instance, 1, true, false, true);
        
        healthCheckCommonV2.checkOk(healthCheckTaskV2, service, "ok");
        
        verify(healthStatusSynchronizer).instanceHealthStatusChange(true, ipPortBasedClient,
            service, instance);
    }
    
    @Test
    void testCheckOkOnlyIncreasesOkCountBeforeThreshold() {
        HealthCheckInstancePublishInfo instance = newHealthCheckInstance(false);
        mockHealthyCheckContext(instance, 2, true, false, true);
        
        healthCheckCommonV2.checkOk(healthCheckTaskV2, service, "ok");
        
        verifyNoInteractions(healthStatusSynchronizer);
    }
    
    @Test
    void testCheckFailChangesHealthWhenThresholdReached() {
        HealthCheckInstancePublishInfo instance = newHealthCheckInstance(true);
        mockHealthyCheckContext(instance, 1, true, false, true);
        
        healthCheckCommonV2.checkFail(healthCheckTaskV2, service, "fail");
        
        verify(healthStatusSynchronizer).instanceHealthStatusChange(false, ipPortBasedClient,
            service, instance);
    }
    
    @Test
    void testCheckFailOnlyIncreasesFailCountBeforeThreshold() {
        HealthCheckInstancePublishInfo instance = newHealthCheckInstance(true);
        mockHealthyCheckContext(instance, 2, true, false, true);
        
        healthCheckCommonV2.checkFail(healthCheckTaskV2, service, "fail");
        
        verifyNoInteractions(healthStatusSynchronizer);
    }
    
    @Test
    void testCheckFailNowChangesHealthImmediately() {
        HealthCheckInstancePublishInfo instance = newHealthCheckInstance(true);
        mockHealthyCheckContext(instance, 2, true, false, true);
        
        healthCheckCommonV2.checkFailNow(healthCheckTaskV2, service, "fail now");
        
        verify(healthStatusSynchronizer).instanceHealthStatusChange(false, ipPortBasedClient,
            service, instance);
    }
    
    @Test
    void testCheckReturnsWhenInstanceMissing() {
        when(ipPortBasedClient.getInstancePublishInfo(service)).thenReturn(null);
        
        healthCheckCommonV2.checkOk(healthCheckTaskV2, service, "missing");
        healthCheckCommonV2.checkFail(healthCheckTaskV2, service, "missing");
        healthCheckCommonV2.checkFailNow(healthCheckTaskV2, service, "missing");
        
        verifyNoInteractions(healthStatusSynchronizer);
    }
    
    private HealthCheckInstancePublishInfo newHealthCheckInstance(boolean healthy) {
        HealthCheckInstancePublishInfo result = new HealthCheckInstancePublishInfo("1.1.1.1",
            8848);
        result.setHealthy(healthy);
        result.setCluster("DEFAULT");
        result.initHealthCheck();
        return result;
    }
    
    private void mockHealthyCheckContext(HealthCheckInstancePublishInfo instance, int checkTimes,
        boolean healthCheckEnabled, boolean cancelled, boolean responsible) {
        when(ipPortBasedClient.getInstancePublishInfo(service)).thenReturn(instance);
        when(ipPortBasedClient.getResponsibleId()).thenReturn("1.1.1.1:8848");
        when(service.getGroupedServiceName()).thenReturn("DEFAULT_GROUP@@service");
        when(service.getNamespace()).thenReturn("public");
        when(service.getGroup()).thenReturn("DEFAULT_GROUP");
        when(service.getName()).thenReturn("service");
        when(switchDomain.getCheckTimes()).thenReturn(checkTimes);
        when(switchDomain.isHealthCheckEnabled("DEFAULT_GROUP@@service"))
            .thenReturn(healthCheckEnabled);
        when(healthCheckTaskV2.isCancelled()).thenReturn(cancelled);
        when(distroMapper.responsible("1.1.1.1:8848")).thenReturn(responsible);
    }
}
