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

package com.alibaba.nacos.naming.healthcheck.v2;

import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class HealthCheckTaskV2Test {
    
    private HealthCheckTaskV2 healthCheckTaskV2;
    
    @Mock
    private IpPortBasedClient ipPortBasedClient;
    
    @Mock
    private ConfigurableApplicationContext context;
    
    @Mock
    private SwitchDomain switchDomain;
    
    @Mock
    private Service service;
    
    @BeforeEach
    void setUp() {
        ApplicationUtils.injectContext(context);
        when(context.getBean(SwitchDomain.class)).thenReturn(switchDomain);
        when(switchDomain.getTcpHealthParams()).thenReturn(new SwitchDomain.TcpHealthParams());
        when(context.getBean(NamingMetadataManager.class)).thenReturn(new NamingMetadataManager());
        healthCheckTaskV2 = new HealthCheckTaskV2(ipPortBasedClient);
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Test
    void testDoHealthCheck() {
        when(ipPortBasedClient.getAllPublishedService()).thenReturn(returnService());
        
        healthCheckTaskV2.setCheckRtWorst(1);
        healthCheckTaskV2.setCheckRtLastLast(1);
        assertEquals(1, healthCheckTaskV2.getCheckRtWorst());
        assertEquals(1, healthCheckTaskV2.getCheckRtLastLast());
        
        healthCheckTaskV2.run();
        healthCheckTaskV2.passIntercept();
        healthCheckTaskV2.doHealthCheck();
        
        verify(ipPortBasedClient, times(3)).getAllPublishedService();
        verify(switchDomain, times(3)).isHealthCheckEnabled(service.getGroupedServiceName());
    }
    
    private List<Service> returnService() {
        return Collections.singletonList(service);
    }
    
    @Test
    void testGetClient() {
        assertNotNull(healthCheckTaskV2.getClient());
    }
    
    @Test
    void testGetAndSet() {
        healthCheckTaskV2.setCheckRtBest(1);
        healthCheckTaskV2.setCheckRtNormalized(1);
        healthCheckTaskV2.setCheckRtLast(1);
        healthCheckTaskV2.setCancelled(true);
        healthCheckTaskV2.setStartTime(1615796485783L);
        
        assertEquals(1, healthCheckTaskV2.getCheckRtBest());
        assertEquals(1, healthCheckTaskV2.getCheckRtNormalized());
        assertEquals(1, healthCheckTaskV2.getCheckRtLast());
        assertTrue(healthCheckTaskV2.isCancelled());
        assertEquals(1615796485783L, healthCheckTaskV2.getStartTime());
    }
    
    @Test
    void testAfterIntercept() {
        healthCheckTaskV2.afterIntercept();
    }
}
