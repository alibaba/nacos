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

import com.alibaba.nacos.naming.core.v2.metadata.InstanceMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstanceEnableBeatCheckInterceptorTest {
    
    private static final Service SERVICE = Service.newService("namespace", "group", "service");
    
    private static final String IP = "1.1.1.1";
    
    private static final int PORT = 10000;
    
    @Mock
    private NamingMetadataManager metadataManager;
    
    @Mock
    private ConfigurableApplicationContext applicationContext;
    
    private InstanceEnableBeatCheckInterceptor interceptor;
    
    @BeforeEach
    void setUp() {
        lenient().when(applicationContext.getBean(NamingMetadataManager.class))
            .thenReturn(metadataManager);
        ApplicationUtils.injectContext(applicationContext);
        interceptor = new InstanceEnableBeatCheckInterceptor();
    }
    
    @Test
    void testInterceptUsesMetadataExtendDataFirst() {
        HealthCheckInstancePublishInfo instance = newInstance();
        InstanceMetadata metadata = new InstanceMetadata();
        metadata.getExtendData().put(UtilsAndCommons.ENABLE_CLIENT_BEAT, "true");
        instance.getExtendDatum().put(UtilsAndCommons.ENABLE_CLIENT_BEAT, false);
        when(metadataManager.getInstanceMetadata(SERVICE, instance.getMetadataId()))
            .thenReturn(Optional.of(metadata));
        
        assertTrue(interceptor.intercept(new InstanceBeatCheckTask(null, SERVICE, instance)));
    }
    
    @Test
    void testInterceptUsesInstanceExtendDataWhenMetadataMissing() {
        HealthCheckInstancePublishInfo instance = newInstance();
        instance.getExtendDatum().put(UtilsAndCommons.ENABLE_CLIENT_BEAT, "true");
        when(metadataManager.getInstanceMetadata(SERVICE, instance.getMetadataId()))
            .thenReturn(Optional.empty());
        
        assertTrue(interceptor.intercept(new InstanceBeatCheckTask(null, SERVICE, instance)));
    }
    
    @Test
    void testInterceptReturnsFalseWhenNoBeatSetting() {
        HealthCheckInstancePublishInfo instance = newInstance();
        when(metadataManager.getInstanceMetadata(SERVICE, instance.getMetadataId()))
            .thenReturn(Optional.empty());
        
        assertFalse(interceptor.intercept(new InstanceBeatCheckTask(null, SERVICE, instance)));
    }
    
    @Test
    void testOrder() {
        assertTrue(interceptor.order() < Integer.MIN_VALUE + 2);
    }
    
    private HealthCheckInstancePublishInfo newInstance() {
        HealthCheckInstancePublishInfo result = new HealthCheckInstancePublishInfo(IP, PORT);
        result.setCluster(UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        return result;
    }
}
