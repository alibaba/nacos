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

package com.alibaba.nacos.naming.healthcheck.interceptor;

import com.alibaba.nacos.consistency.ProtocolMetaData;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.MetadataKey;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.constants.Constants;
import com.alibaba.nacos.naming.healthcheck.NacosHealthCheckTask;
import com.alibaba.nacos.naming.healthcheck.heartbeat.ClientBeatCheckTaskV2;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceMetadataReadyInterceptorTest {
    
    @Mock
    private ConfigurableApplicationContext applicationContext;
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private CPProtocol cpProtocol;
    
    @Mock
    private NacosHealthCheckTask healthCheckTask;
    
    private ServiceMetadataReadyInterceptor interceptor;
    
    private ProtocolMetaData protocolMetaData;
    
    @BeforeEach
    void setUp() {
        ApplicationUtils.injectContext(applicationContext);
        interceptor = new ServiceMetadataReadyInterceptor();
        protocolMetaData = new ProtocolMetaData();
    }
    
    @AfterEach
    void tearDown() {
        ApplicationUtils.injectContext(null);
    }
    
    @Test
    void testInterceptBeforeServiceMetadataGroupIsReady() {
        prepareProtocolMetadata();
        
        assertTrue(interceptor.intercept(healthCheckTask));
    }
    
    @Test
    void testPassAfterServiceMetadataGroupIsReady() {
        prepareProtocolMetadata();
        Map<String, Object> groupMetadata = new HashMap<>();
        groupMetadata.put(MetadataKey.LEADER_META_DATA, "127.0.0.1:7848");
        Map<String, Map<String, Object>> metadata = new HashMap<>();
        metadata.put(Constants.SERVICE_METADATA, groupMetadata);
        protocolMetaData.load(metadata);
        
        assertFalse(interceptor.intercept(healthCheckTask));
    }
    
    @Test
    void testInterceptWhenReadinessCheckFails() {
        when(applicationContext.getBean(ProtocolManager.class))
            .thenThrow(new IllegalStateException("protocol unavailable"));
        
        assertTrue(interceptor.intercept(healthCheckTask));
    }
    
    @Test
    void testInterceptType() {
        assertTrue(interceptor.isInterceptType(HealthCheckTaskV2.class));
        assertFalse(interceptor.isInterceptType(ClientBeatCheckTaskV2.class));
    }
    
    private void prepareProtocolMetadata() {
        when(applicationContext.getBean(ProtocolManager.class)).thenReturn(protocolManager);
        when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        when(cpProtocol.protocolMetaData()).thenReturn(protocolMetaData);
    }
}
