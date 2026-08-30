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

import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadataProcessor;
import com.alibaba.nacos.naming.core.v2.service.impl.PersistentClientOperationServiceImpl;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceMetadataReadyInterceptorTest {
    
    @Mock
    private ConfigurableApplicationContext applicationContext;
    
    @Mock
    private PersistentClientOperationServiceImpl persistentClientProcessor;
    
    @Mock
    private ServiceMetadataProcessor serviceMetadataProcessor;
    
    @Mock
    private NacosHealthCheckTask healthCheckTask;
    
    private ServiceMetadataReadyInterceptor interceptor;
    
    @BeforeEach
    void setUp() {
        ApplicationUtils.injectContext(applicationContext);
        ApplicationUtils.setStarted(false);
        interceptor = new ServiceMetadataReadyInterceptor();
    }
    
    @AfterEach
    void tearDown() {
        ApplicationUtils.setStarted(false);
        ApplicationUtils.injectContext(null);
    }
    
    @Test
    void testInterceptBeforeSnapshotsAreLoaded() {
        prepareProcessors();
        when(healthCheckTask.getTaskId()).thenReturn("persistent-client-task");
        when(persistentClientProcessor.isSnapshotLoaded()).thenReturn(false);
        when(serviceMetadataProcessor.isSnapshotLoaded()).thenReturn(true);
        
        assertTrue(interceptor.intercept(healthCheckTask));
    }
    
    @Test
    void testPassAfterBothSnapshotsAreLoaded() {
        prepareProcessors();
        when(persistentClientProcessor.isSnapshotLoaded()).thenReturn(true);
        when(serviceMetadataProcessor.isSnapshotLoaded()).thenReturn(true);
        
        assertFalse(interceptor.intercept(healthCheckTask));
    }
    
    @Test
    void testPassAfterApplicationStartedWithoutSnapshots() {
        ApplicationUtils.setStarted(true);
        
        assertFalse(interceptor.intercept(healthCheckTask));
    }
    
    @Test
    void testReleaseRemainsOpenAfterSnapshotsAreUnavailable() {
        prepareProcessors();
        when(persistentClientProcessor.isSnapshotLoaded()).thenReturn(true, false);
        when(serviceMetadataProcessor.isSnapshotLoaded()).thenReturn(true, false);
        
        assertFalse(interceptor.intercept(healthCheckTask));
        assertFalse(interceptor.intercept(healthCheckTask));
    }
    
    @Test
    void testInterceptType() {
        assertTrue(interceptor.isInterceptType(HealthCheckTaskV2.class));
        assertFalse(interceptor.isInterceptType(ClientBeatCheckTaskV2.class));
    }
    
    private void prepareProcessors() {
        when(applicationContext.getBean(PersistentClientOperationServiceImpl.class))
            .thenReturn(persistentClientProcessor);
        when(applicationContext.getBean(ServiceMetadataProcessor.class))
            .thenReturn(serviceMetadataProcessor);
    }
    
}
