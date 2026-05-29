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

package com.alibaba.nacos.naming.healthcheck.heartbeat;

import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceEnableBeatCheckInterceptorTest {
    
    private final Service service = Service.newService("namespace", "group", "service");
    
    private ServiceEnableBeatCheckInterceptor interceptor;
    
    @Mock
    private NamingMetadataManager metadataManager;
    
    @Mock
    private ConfigurableApplicationContext applicationContext;
    
    @Mock
    private InstanceBeatCheckTask task;
    
    @BeforeEach
    void setUp() {
        ApplicationUtils.injectContext(applicationContext);
        interceptor = new ServiceEnableBeatCheckInterceptor();
    }
    
    @Test
    void testInterceptType() {
        assertTrue(interceptor.isInterceptType(InstanceBeatCheckTask.class));
        assertFalse(interceptor.isInterceptType(Object.class));
    }
    
    @Test
    void testOrder() {
        assertEquals(Integer.MIN_VALUE, interceptor.order());
    }
    
    @Test
    void testInterceptWhenMetadataMissing() {
        mockMetadata(Optional.empty());
        
        assertFalse(interceptor.intercept(task));
    }
    
    @Test
    void testInterceptWhenEnableClientBeatMissing() {
        mockMetadata(Optional.of(new ServiceMetadata()));
        
        assertFalse(interceptor.intercept(task));
    }
    
    @Test
    void testInterceptWhenEnableClientBeatTrue() {
        ServiceMetadata metadata = new ServiceMetadata();
        metadata.getExtendData().put(UtilsAndCommons.ENABLE_CLIENT_BEAT, "true");
        mockMetadata(Optional.of(metadata));
        
        assertTrue(interceptor.intercept(task));
    }
    
    @Test
    void testInterceptWhenEnableClientBeatFalse() {
        ServiceMetadata metadata = new ServiceMetadata();
        metadata.getExtendData().put(UtilsAndCommons.ENABLE_CLIENT_BEAT, "false");
        mockMetadata(Optional.of(metadata));
        
        assertFalse(interceptor.intercept(task));
    }
    
    private void mockMetadata(Optional<ServiceMetadata> metadata) {
        when(applicationContext.getBean(NamingMetadataManager.class)).thenReturn(metadataManager);
        when(task.getService()).thenReturn(service);
        when(metadataManager.getServiceMetadata(service)).thenReturn(metadata);
    }
}
