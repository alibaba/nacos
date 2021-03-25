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
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HealthCheckCommonV2Test {
    
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
    
    private HealthCheckCommonV2 healthCheckCommonV2;
    
    @Before
    public void setUp() {
        healthCheckCommonV2 = new HealthCheckCommonV2();
        when(healthCheckTaskV2.getClient()).thenReturn(ipPortBasedClient);
        when(ipPortBasedClient.getInstancePublishInfo(service)).thenReturn(healthCheckInstancePublishInfo);
        when(healthCheckInstancePublishInfo.getFailCount()).thenReturn(new AtomicInteger());
    }
    
    @Test
    public void testReEvaluateCheckRT() {
        healthCheckCommonV2.reEvaluateCheckRT(1, healthCheckTaskV2, healthParams);
        
        verify(healthParams, times(2)).getMax();
        verify(healthParams, times(1)).getMin();
        verify(healthParams, times(2)).getFactor();
        
        verify(healthCheckTaskV2).getCheckRtWorst();
        verify(healthCheckTaskV2).getCheckRtBest();
        verify(healthCheckTaskV2).getCheckRtNormalized();
    }
    
    @Test
    public void testCheckOk() {
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
    public void testCheckFail() {
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
    public void testCheckFailNow() {
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
}
