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
import com.alibaba.nacos.naming.boot.SpringContext;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.misc.GlobalConfig;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.push.PushService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doReturn;

/**
 * @author caoyixiong
 * @author jifengnan 2019-07-14
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ClientBeatCheckTaskTest {

    @InjectMocks
    private ClientBeatCheckTask clientBeatCheckTask;
    @Mock
    private Service serviceSpy;
    @Mock
    private GlobalConfig globalConfig;
    @Mock
    private DistroMapper distroMapper;
    @Spy
    private ApplicationContext context;
    @Spy
    private SwitchDomain switchDomain;
    @Mock
    private PushService pushService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        new SpringContext().setApplicationContext(context);
        doReturn(globalConfig).when(context).getBean(GlobalConfig.class);
        doReturn(switchDomain).when(context).getBean(SwitchDomain.class);
        doReturn(pushService).when(context).getBean(PushService.class);
        doReturn(distroMapper).when(context).getBean(DistroMapper.class);
    }

    @Test
    public void testHeartBeatNotTimeout() {
        List<Instance> instances = new ArrayList<>();
        Instance instance = createInstance();
        instance.setLastBeat(System.currentTimeMillis());
        instance.setMarked(false);
        instance.setHealthy(true);
        Map<String, String> metadata = new HashMap<>();
        metadata.put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, "1000000000");
        instance.setMetadata(metadata);
        instances.add(instance);
        Mockito.when(serviceSpy.allIPs(true)).thenReturn(instances);

        Mockito.doReturn("test").when(serviceSpy).getName();
        Mockito.doReturn(true).when(distroMapper).responsible(Mockito.anyString());
        clientBeatCheckTask.run();
        Assert.assertTrue(instance.isHealthy());
    }

    @Test
    public void testHeartBeatTimeout() {
        List<Instance> instances = new ArrayList<>();
        Instance instance = createInstance();
        instance.setLastBeat(System.currentTimeMillis() - 1000);
        instance.setMarked(false);
        instance.setHealthy(true);
        Map<String, String> metadata = new HashMap<>();
        metadata.put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, "10");
        instance.setMetadata(metadata);
        instances.add(instance);
        Mockito.when(serviceSpy.getName()).thenReturn("test");
        Mockito.doReturn(true).when(distroMapper).responsible(Mockito.anyString());

        Mockito.when(serviceSpy.allIPs(true)).thenReturn(instances);

        clientBeatCheckTask.run();
        Assert.assertFalse(instance.isHealthy());
    }

    @Test
    public void testIpDeleteTimeOut() {
        List<Instance> instances = new ArrayList<>();
        Instance instance = createInstance();
        instance.setLastBeat(System.currentTimeMillis());
        instance.setMarked(true);
        instance.setHealthy(true);
        Map<String, String> metadata = new HashMap<>();
        metadata.put(PreservedMetadataKeys.IP_DELETE_TIMEOUT, "10");
        instance.setMetadata(metadata);
        instances.add(instance);
        Mockito.doReturn(true).when(distroMapper).responsible(null);
        Mockito.doReturn(true).when(globalConfig).isExpireInstance();
        Mockito.when(serviceSpy.allIPs(true)).thenReturn(instances);

        clientBeatCheckTask.run();
    }

    @Test
    public void testIpDeleteNotTimeOut() {
        List<Instance> instances = new ArrayList<>();
        Instance instance = createInstance();
        instance.setLastBeat(System.currentTimeMillis());
        instance.setMarked(true);
        instance.setHealthy(true);
        Map<String, String> metadata = new HashMap<>();
        metadata.put(PreservedMetadataKeys.IP_DELETE_TIMEOUT, "10000");
        instance.setMetadata(metadata);
        instances.add(instance);

        Mockito.when(serviceSpy.getName()).thenReturn("test");
        Mockito.doReturn(true).when(distroMapper).responsible(null);
        Mockito.when(globalConfig.isExpireInstance ()).thenReturn(true);
        Mockito.when(serviceSpy.allIPs(true)).thenReturn(instances);

        clientBeatCheckTask.run();
    }

    private Instance createInstance() {
        Service service = new Service("test-service");
        Cluster cluster = new Cluster("test-cluster", service);
        service.addCluster(cluster);
        return new Instance("1.1.1.1", 1, cluster);
    }
}
