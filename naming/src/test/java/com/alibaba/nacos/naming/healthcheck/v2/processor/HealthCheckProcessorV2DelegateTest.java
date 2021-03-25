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

import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckType;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.extend.HealthCheckExtendProvider;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HealthCheckProcessorV2DelegateTest {
    
    @Mock
    private HealthCheckExtendProvider healthCheckExtendProvider;
    
    @Mock
    private HealthCheckTaskV2 healthCheckTaskV2;
    
    @Mock
    private Service service;
    
    @Mock
    private ClusterMetadata clusterMetadata;
    
    private HealthCheckProcessorV2Delegate healthCheckProcessorV2Delegate;
    
    @Before
    public void setUp() {
        healthCheckProcessorV2Delegate = new HealthCheckProcessorV2Delegate(healthCheckExtendProvider);
        verify(healthCheckExtendProvider).init();
    }
    
    @Test
    public void testAddProcessor() throws NoSuchFieldException, IllegalAccessException {
        List<HealthCheckProcessorV2> list = new ArrayList<>();
        list.add(new TcpHealthCheckProcessor(null, null));
        healthCheckProcessorV2Delegate.addProcessor(list);
        
        Class<HealthCheckProcessorV2Delegate> healthCheckProcessorV2DelegateClass = HealthCheckProcessorV2Delegate.class;
        Field field = healthCheckProcessorV2DelegateClass.getDeclaredField("healthCheckProcessorMap");
        field.setAccessible(true);
        Map<String, HealthCheckProcessorV2> map = (Map<String, HealthCheckProcessorV2>) field
                .get(healthCheckProcessorV2Delegate);
        HealthCheckProcessorV2 healthCheckProcessorV2 = map.get(HealthCheckType.TCP.name());
        Assert.assertNotNull(healthCheckProcessorV2);
    }
    
    @Test
    public void testProcess() throws NoSuchFieldException, IllegalAccessException {
        testAddProcessor();
        when(clusterMetadata.getHealthyCheckType()).thenReturn(HealthCheckType.TCP.name());
        when(healthCheckTaskV2.getClient()).thenReturn(new IpPortBasedClient("127.0.0.1:80#true", true));
        
        healthCheckProcessorV2Delegate.process(healthCheckTaskV2, service, clusterMetadata);
        
        verify(clusterMetadata).getHealthyCheckType();
        verify(healthCheckTaskV2).getClient();
    }
    
    @Test
    public void testGetType() {
        Assert.assertNull(healthCheckProcessorV2Delegate.getType());
    }
}
