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

package com.alibaba.nacos.naming.healthcheck;

import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.naming.misc.GlobalConfig;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.push.UdpPushService;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ClientBeatCheckTaskTest {
    
    @InjectMocks
    @Spy
    private ClientBeatCheckTask clientBeatCheckTask;
    
    @Mock
    private DistroMapper distroMapperSpy;
    
    @Mock
    private Service serviceSpy;
    
    @Mock
    private GlobalConfig globalConfig;
    
    @Mock
    private UdpPushService pushService;
    
    @Mock
    private SwitchDomain switchDomain;
    
    @Mock
    private UpgradeJudgement upgradeJudgement;
    
    @Mock
    private ConfigurableApplicationContext context;
    
    @Before
    public void init() {
        ReflectionTestUtils.setField(clientBeatCheckTask, "service", serviceSpy);
        Mockito.doReturn(distroMapperSpy).when(clientBeatCheckTask).getDistroMapper();
        Mockito.doReturn(globalConfig).when(clientBeatCheckTask).getGlobalConfig();
        Mockito.doReturn(pushService).when(clientBeatCheckTask).getPushService();
        Mockito.doReturn(switchDomain).when(clientBeatCheckTask).getSwitchDomain();
        ApplicationUtils.injectContext(context);
        when(context.getBean(UpgradeJudgement.class)).thenReturn(upgradeJudgement);
    }
    
    @Test
    public void testHeartBeatNotTimeout() {
        Instance instance = new Instance();
        instance.setLastBeat(System.currentTimeMillis());
        instance.setMarked(false);
        instance.setHealthy(true);
        Map<String, String> metadata = new HashMap<>();
        metadata.put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, "1000000000");
        instance.setMetadata(metadata);
        
        Mockito.doReturn("test").when(serviceSpy).getName();
        Mockito.doReturn(true).when(distroMapperSpy).responsible(Mockito.anyString());
        clientBeatCheckTask.run();
        Assert.assertTrue(instance.isHealthy());
    }
    
    @Test
    public void testHeartBeatTimeout() {
        Instance instance = new Instance();
        instance.setLastBeat(System.currentTimeMillis() - 1000);
        instance.setMarked(false);
        instance.setHealthy(true);
        Map<String, String> metadata = new HashMap<>();
        metadata.put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, "10");
        instance.setMetadata(metadata);
        List<Instance> instances = new ArrayList<>();
        instances.add(instance);
        Mockito.doReturn("test").when(serviceSpy).getName();
        Mockito.doReturn(true).when(distroMapperSpy).responsible(Mockito.anyString());
        Mockito.doReturn(true).when(switchDomain).isHealthCheckEnabled();
        
        when(serviceSpy.allIPs(true)).thenReturn(instances);
        
        clientBeatCheckTask.run();
        Assert.assertFalse(instance.isHealthy());
    }
    
    @Test
    public void testIpDeleteTimeOut() {
        Instance instance = new Instance();
        instance.setLastBeat(System.currentTimeMillis());
        instance.setMarked(true);
        instance.setHealthy(true);
        Map<String, String> metadata = new HashMap<>();
        metadata.put(PreservedMetadataKeys.IP_DELETE_TIMEOUT, "10");
        instance.setMetadata(metadata);
        Mockito.doReturn(true).when(distroMapperSpy).responsible(null);
        
        clientBeatCheckTask.run();
    }
    
    @Test
    public void testIpDeleteNotTimeOut() {
        Instance instance = new Instance();
        instance.setLastBeat(System.currentTimeMillis());
        instance.setMarked(true);
        instance.setHealthy(true);
        Map<String, String> metadata = new HashMap<>();
        metadata.put(PreservedMetadataKeys.IP_DELETE_TIMEOUT, "10000");
        instance.setMetadata(metadata);
        
        Mockito.doReturn(true).when(distroMapperSpy).responsible(null);
        
        clientBeatCheckTask.run();
    }
}
