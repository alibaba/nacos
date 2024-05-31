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

package com.alibaba.nacos.naming.healthcheck.heartbeat;

import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.metadata.InstanceMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.GlobalConfig;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientBeatCheckTaskV2Test {
    
    private static final String IP = "1.1.1.1";
    
    private static final int PORT = 10000;
    
    private static final String CLIENT_ID = IP + InternetAddressUtil.IP_PORT_SPLITER + PORT + "#true";
    
    private static final String SERVICE_NAME = "service";
    
    private static final String GROUP_NAME = "group";
    
    private static final String NAMESPACE = "namespace";
    
    private ClientBeatCheckTaskV2 beatCheckTask;
    
    @Mock
    private NamingMetadataManager namingMetadataManager;
    
    @Mock
    private GlobalConfig globalConfig;
    
    @Mock
    private ConfigurableApplicationContext applicationContext;
    
    @Mock
    private DistroMapper distroMapper;
    
    private IpPortBasedClient client;
    
    @BeforeEach
    void setUp() throws Exception {
        when(applicationContext.getBean(NamingMetadataManager.class)).thenReturn(namingMetadataManager);
        when(applicationContext.getBean(GlobalConfig.class)).thenReturn(globalConfig);
        when(applicationContext.getBean(DistroMapper.class)).thenReturn(distroMapper);
        when(distroMapper.responsible(anyString())).thenReturn(true);
        ApplicationUtils.injectContext(applicationContext);
        client = new IpPortBasedClient(CLIENT_ID, true);
        beatCheckTask = new ClientBeatCheckTaskV2(client);
    }
    
    @Test
    void testTaskKey() {
        assertEquals(KeyBuilder.buildServiceMetaKey(CLIENT_ID, "true"), beatCheckTask.taskKey());
    }
    
    @Test
    void testRunUnhealthyInstanceWithoutExpire() {
        injectInstance(false, 0);
        beatCheckTask.run();
        assertFalse(client.getAllInstancePublishInfo().isEmpty());
    }
    
    @Test
    void testRunHealthyInstanceWithoutExpire() {
        injectInstance(true, 0);
        beatCheckTask.run();
        assertFalse(client.getAllInstancePublishInfo().isEmpty());
        assertFalse(client.getInstancePublishInfo(Service.newService(NAMESPACE, GROUP_NAME, SERVICE_NAME)).isHealthy());
    }
    
    @Test
    void testRunUnHealthyInstanceWithExpire() {
        injectInstance(false, 0);
        when(globalConfig.isExpireInstance()).thenReturn(true);
        beatCheckTask.run();
        assertTrue(client.getAllInstancePublishInfo().isEmpty());
    }
    
    @Test
    void testRunHealthyInstanceWithExpire() {
        injectInstance(true, 0);
        when(globalConfig.isExpireInstance()).thenReturn(true);
        beatCheckTask.run();
        assertTrue(client.getAllInstancePublishInfo().isEmpty());
    }
    
    @Test
    void testRunHealthyInstanceWithHeartBeat() {
        injectInstance(true, System.currentTimeMillis());
        when(globalConfig.isExpireInstance()).thenReturn(true);
        beatCheckTask.run();
        assertFalse(client.getAllInstancePublishInfo().isEmpty());
        assertTrue(client.getInstancePublishInfo(Service.newService(NAMESPACE, GROUP_NAME, SERVICE_NAME)).isHealthy());
    }
    
    @Test
    void testRunHealthyInstanceWithTimeoutFromInstance() throws InterruptedException {
        injectInstance(true, System.currentTimeMillis()).getExtendDatum().put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, 800);
        when(globalConfig.isExpireInstance()).thenReturn(true);
        TimeUnit.SECONDS.sleep(1);
        beatCheckTask.run();
        assertFalse(client.getAllInstancePublishInfo().isEmpty());
        assertFalse(client.getInstancePublishInfo(Service.newService(NAMESPACE, GROUP_NAME, SERVICE_NAME)).isHealthy());
    }
    
    @Test
    void testRunHealthyInstanceWithTimeoutFromMetadata() throws InterruptedException {
        injectInstance(true, System.currentTimeMillis());
        Service service = Service.newService(NAMESPACE, GROUP_NAME, SERVICE_NAME);
        InstanceMetadata metadata = new InstanceMetadata();
        metadata.getExtendData().put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, 500L);
        String address = IP + InternetAddressUtil.IP_PORT_SPLITER + PORT + InternetAddressUtil.IP_PORT_SPLITER
                + UtilsAndCommons.DEFAULT_CLUSTER_NAME;
        when(namingMetadataManager.getInstanceMetadata(service, address)).thenReturn(Optional.of(metadata));
        when(globalConfig.isExpireInstance()).thenReturn(true);
        TimeUnit.SECONDS.sleep(1);
        beatCheckTask.run();
        assertFalse(client.getAllInstancePublishInfo().isEmpty());
        assertFalse(client.getInstancePublishInfo(Service.newService(NAMESPACE, GROUP_NAME, SERVICE_NAME)).isHealthy());
    }
    
    private HealthCheckInstancePublishInfo injectInstance(boolean healthy, long heartbeatTime) {
        HealthCheckInstancePublishInfo instance = new HealthCheckInstancePublishInfo(IP, PORT);
        instance.setHealthy(healthy);
        instance.setLastHeartBeatTime(heartbeatTime);
        instance.setCluster(UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        Service service = Service.newService(NAMESPACE, GROUP_NAME, SERVICE_NAME);
        client.addServiceInstance(service, instance);
        return instance;
    }
}
