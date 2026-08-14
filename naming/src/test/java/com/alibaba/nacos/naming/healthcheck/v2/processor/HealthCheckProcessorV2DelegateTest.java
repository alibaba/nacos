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
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.extend.HealthCheckExtendProvider;
import com.alibaba.nacos.naming.healthcheck.extend.HealthCheckProcessorExtendV2;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HealthCheckProcessorV2DelegateTest {
    
    @Mock
    private HealthCheckExtendProvider healthCheckExtendProvider;
    
    @Mock
    private HealthCheckProcessorExtendV2 healthCheckProcessorExtendV2;
    
    @Mock
    private HealthCheckCommonV2 healthCheckCommonV2;

    @Mock
    private HealthCheckTaskV2 healthCheckTaskV2;
    
    @Mock
    private Service service;
    
    @Mock
    private ClusterMetadata clusterMetadata;
    
    @Mock
    private HealthCheckProcessorV2 noneHealthCheckProcessor;

    @Mock
    private HealthCheckProcessorV2 activeHealthCheckProcessor;

    @Mock
    private IpPortBasedClient client;

    @Mock
    private InstancePublishInfo instance;

    private HealthCheckProcessorV2Delegate healthCheckProcessorV2Delegate;
    
    @BeforeEach
    void setUp() {
        healthCheckProcessorV2Delegate = new HealthCheckProcessorV2Delegate(healthCheckExtendProvider,
                healthCheckProcessorExtendV2, healthCheckCommonV2);
        verify(healthCheckExtendProvider).init();
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Test
    void testAddProcessor() throws NoSuchFieldException, IllegalAccessException {
        List<HealthCheckProcessorV2> list = new ArrayList<>();
        list.add(new TcpHealthCheckProcessor(null, null));
        healthCheckProcessorV2Delegate.addProcessor(list);
        
        Class<HealthCheckProcessorV2Delegate> healthCheckProcessorV2DelegateClass = HealthCheckProcessorV2Delegate.class;
        Field field = healthCheckProcessorV2DelegateClass.getDeclaredField("healthCheckProcessorMap");
        field.setAccessible(true);
        Map<String, HealthCheckProcessorV2> map = (Map<String, HealthCheckProcessorV2>) field.get(healthCheckProcessorV2Delegate);
        HealthCheckProcessorV2 healthCheckProcessorV2 = map.get(HealthCheckType.TCP.name());
        assertNotNull(healthCheckProcessorV2);
    }
    
    @Test
    void testProcess() throws NoSuchFieldException, IllegalAccessException {
        testAddProcessor();
        when(clusterMetadata.getHealthyCheckType()).thenReturn(HealthCheckType.TCP.name());
        when(healthCheckTaskV2.getClient()).thenReturn(new IpPortBasedClient("127.0.0.1:80#true", true));
        
        healthCheckProcessorV2Delegate.process(healthCheckTaskV2, service, clusterMetadata);
        
        verify(clusterMetadata).getHealthyCheckType();
        verify(healthCheckTaskV2, times(2)).getClient();
    }

    @Test
    void testProcessValidAddress() {
        mockActiveHealthCheck("127.0.0.1");

        healthCheckProcessorV2Delegate.process(healthCheckTaskV2, service, clusterMetadata);

        verify(activeHealthCheckProcessor).process(healthCheckTaskV2, service, clusterMetadata);
        verify(healthCheckCommonV2, never()).checkFailNow(healthCheckTaskV2, service,
                HealthCheckProcessorV2Delegate.INVALID_ADDRESS_MESSAGE);
    }

    @Test
    void testProcessInvalidAddress() {
        mockActiveHealthCheck("rogue-mysql:3306?allowLoadLocalInfile=true#");

        healthCheckProcessorV2Delegate.process(healthCheckTaskV2, service, clusterMetadata);

        verify(activeHealthCheckProcessor, never()).process(healthCheckTaskV2, service, clusterMetadata);
        verify(healthCheckCommonV2).checkFailNow(healthCheckTaskV2, service,
                HealthCheckProcessorV2Delegate.INVALID_ADDRESS_MESSAGE);
    }

    @Test
    void testProcessFallbackToNoneProcessor() {
        when(noneHealthCheckProcessor.getType()).thenReturn(NoneHealthCheckProcessor.TYPE);
        when(clusterMetadata.getHealthyCheckType()).thenReturn("UNKNOWN");
        healthCheckProcessorV2Delegate.addProcessor(Collections.singletonList(noneHealthCheckProcessor));

        healthCheckProcessorV2Delegate.process(healthCheckTaskV2, service, clusterMetadata);

        verify(noneHealthCheckProcessor).process(healthCheckTaskV2, service, clusterMetadata);
    }
    
    @Test
    void testGetType() {
        assertNull(healthCheckProcessorV2Delegate.getType());
    }

    private void mockActiveHealthCheck(String address) {
        when(activeHealthCheckProcessor.getType()).thenReturn(HealthCheckType.TCP.name());
        when(clusterMetadata.getHealthyCheckType()).thenReturn(HealthCheckType.TCP.name());
        when(healthCheckTaskV2.getClient()).thenReturn(client);
        when(client.getInstancePublishInfo(service)).thenReturn(instance);
        when(instance.getIp()).thenReturn(address);
        healthCheckProcessorV2Delegate.addProcessor(Collections.singletonList(activeHealthCheckProcessor));
    }
}
