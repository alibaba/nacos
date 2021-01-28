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

package com.alibaba.nacos.naming.healthcheck.interceptor;

import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.metadata.InstanceMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.naming.healthcheck.heartbeat.ClientBeatCheckTaskV2;
import com.alibaba.nacos.naming.misc.GlobalConfig;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HealthCheckTaskInterceptWrapperTest {
    
    private static final String IP = "1.1.1.1";
    
    private static final int PORT = 10000;
    
    private static final String CLIENT_ID = IP + ":" + PORT + "#true";
    
    private static final String SERVICE_NAME = "service";
    
    private static final String GROUP_NAME = "group";
    
    private static final String NAMESPACE = "namespace";
    
    private HealthCheckTaskInterceptWrapper taskWrapper;
    
    @Mock
    private NamingMetadataManager namingMetadataManager;
    
    @Mock
    private GlobalConfig globalConfig;
    
    @Mock
    private SwitchDomain switchDomain;
    
    @Mock
    private DistroMapper distroMapper;
    
    @Mock
    private ConfigurableApplicationContext applicationContext;
    
    @Mock
    private UpgradeJudgement upgradeJudgement;
    
    private IpPortBasedClient client;
    
    @Before
    public void setUp() throws Exception {
        when(applicationContext.getBean(NamingMetadataManager.class)).thenReturn(namingMetadataManager);
        when(applicationContext.getBean(GlobalConfig.class)).thenReturn(globalConfig);
        when(applicationContext.getBean(SwitchDomain.class)).thenReturn(switchDomain);
        when(applicationContext.getBean(DistroMapper.class)).thenReturn(distroMapper);
        when(applicationContext.getBean(UpgradeJudgement.class)).thenReturn(upgradeJudgement);
        ApplicationUtils.injectContext(applicationContext);
        client = new IpPortBasedClient(CLIENT_ID, true);
        when(switchDomain.isHealthCheckEnabled()).thenReturn(true);
        when(distroMapper.responsible(client.getResponsibleId())).thenReturn(true);
        when(upgradeJudgement.isUseGrpcFeatures()).thenReturn(true);
        ClientBeatCheckTaskV2 beatCheckTask = new ClientBeatCheckTaskV2(client);
        taskWrapper = new HealthCheckTaskInterceptWrapper(beatCheckTask);
    }
    
    @Test
    public void testRunWithDisableHealthCheck() {
        when(switchDomain.isHealthCheckEnabled()).thenReturn(false);
        taskWrapper.run();
        verify(distroMapper, never()).responsible(client.getResponsibleId());
    }
    
    @Test
    public void testRunWithoutResponsibleClient() {
        when(distroMapper.responsible(client.getResponsibleId())).thenReturn(false);
        taskWrapper.run();
        verify(globalConfig, never()).isExpireInstance();
    }
    
    @Test
    public void testRunUnhealthyInstanceWithoutExpire() {
        injectInstance(false, 0);
        taskWrapper.run();
        assertFalse(client.getAllInstancePublishInfo().isEmpty());
    }
    
    @Test
    public void testRunHealthyInstanceWithoutExpire() {
        injectInstance(true, 0);
        taskWrapper.run();
        assertFalse(client.getAllInstancePublishInfo().isEmpty());
        assertFalse(client.getInstancePublishInfo(Service.newService(NAMESPACE, GROUP_NAME, SERVICE_NAME)).isHealthy());
    }
    
    @Test
    public void testRunUnHealthyInstanceWithExpire() {
        injectInstance(false, 0);
        when(globalConfig.isExpireInstance()).thenReturn(true);
        taskWrapper.run();
        assertTrue(client.getAllInstancePublishInfo().isEmpty());
    }
    
    @Test
    public void testRunHealthyInstanceWithExpire() {
        injectInstance(true, 0);
        when(globalConfig.isExpireInstance()).thenReturn(true);
        taskWrapper.run();
        assertTrue(client.getAllInstancePublishInfo().isEmpty());
    }
    
    @Test
    public void testRunHealthyInstanceWithHeartBeat() {
        injectInstance(true, System.currentTimeMillis());
        when(globalConfig.isExpireInstance()).thenReturn(true);
        taskWrapper.run();
        assertFalse(client.getAllInstancePublishInfo().isEmpty());
        assertTrue(client.getInstancePublishInfo(Service.newService(NAMESPACE, GROUP_NAME, SERVICE_NAME)).isHealthy());
    }
    
    @Test
    public void testRunHealthyInstanceWithTimeoutFromInstance() throws InterruptedException {
        injectInstance(true, System.currentTimeMillis()).getExtendDatum()
                .put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, 1000);
        when(globalConfig.isExpireInstance()).thenReturn(true);
        TimeUnit.SECONDS.sleep(1);
        taskWrapper.run();
        assertFalse(client.getAllInstancePublishInfo().isEmpty());
        assertFalse(client.getInstancePublishInfo(Service.newService(NAMESPACE, GROUP_NAME, SERVICE_NAME)).isHealthy());
    }
    
    @Test
    public void testRunHealthyInstanceWithTimeoutFromMetadata() throws InterruptedException {
        InstancePublishInfo instance = injectInstance(true, System.currentTimeMillis());
        Service service = Service.newService(NAMESPACE, GROUP_NAME, SERVICE_NAME);
        InstanceMetadata metadata = new InstanceMetadata();
        metadata.getExtendData().put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, 1000L);
        when(namingMetadataManager.getInstanceMetadata(service, instance.getMetadataId())).thenReturn(Optional.of(metadata));
        when(globalConfig.isExpireInstance()).thenReturn(true);
        TimeUnit.SECONDS.sleep(1);
        taskWrapper.run();
        assertFalse(client.getAllInstancePublishInfo().isEmpty());
        assertFalse(client.getInstancePublishInfo(Service.newService(NAMESPACE, GROUP_NAME, SERVICE_NAME)).isHealthy());
    }
    
    private HealthCheckInstancePublishInfo injectInstance(boolean healthy, long heartbeatTime) {
        Service service = Service.newService(NAMESPACE, GROUP_NAME, SERVICE_NAME);
        HealthCheckInstancePublishInfo instance = new HealthCheckInstancePublishInfo(IP, PORT);
        instance.setHealthy(healthy);
        instance.setLastHeartBeatTime(heartbeatTime);
        client.addServiceInstance(service, instance);
        return instance;
    }
}
