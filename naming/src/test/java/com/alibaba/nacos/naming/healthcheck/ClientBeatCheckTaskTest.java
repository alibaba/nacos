package com.alibaba.nacos.naming.healthcheck;

import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.misc.GlobalConfig;
import com.alibaba.nacos.naming.push.PushService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author caoyixiong
 */
@RunWith(MockitoJUnitRunner.Silent.class)
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
    private PushService pushService;


    @Before
    public void init() {
        ReflectionTestUtils.setField(clientBeatCheckTask, "service", serviceSpy);
        Mockito.doReturn(distroMapperSpy).when(clientBeatCheckTask).getDistroMapper();
        Mockito.doReturn(globalConfig).when(clientBeatCheckTask).getGlobalConfig();
        Mockito.doReturn(pushService).when(clientBeatCheckTask).getPushService();
    }

    @Test
    public void testHeartBeatNotTimeout() {
        List<Instance> instances = new ArrayList<>();
        Instance instance = new Instance();
        instance.setLastBeat(System.currentTimeMillis());
        instance.setMarked(false);
        instance.setHealthy(true);
        Map<String, String> metadata = new HashMap<>();
        metadata.put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, "1000000000");
        instance.setMetadata(metadata);
        instances.add(instance);
        Mockito.when(serviceSpy.allIPs(true)).thenReturn(instances);

        Mockito.doReturn("test").when(serviceSpy).getName();
        Mockito.doReturn(true).when(distroMapperSpy).responsible(Mockito.anyString());
        clientBeatCheckTask.run();
        Assert.assertTrue(instance.isHealthy());
    }

    @Test
    public void testHeartBeatTimeout() {
        List<Instance> instances = new ArrayList<>();
        Instance instance = new Instance();
        instance.setLastBeat(System.currentTimeMillis() - 1000);
        instance.setMarked(false);
        instance.setHealthy(true);
        Map<String, String> metadata = new HashMap<>();
        metadata.put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, "10");
        instance.setMetadata(metadata);
        instances.add(instance);
        Mockito.doReturn("test").when(serviceSpy).getName();
        Mockito.doReturn(true).when(distroMapperSpy).responsible(Mockito.anyString());

        Mockito.when(serviceSpy.allIPs(true)).thenReturn(instances);

        clientBeatCheckTask.run();
        Assert.assertFalse(instance.isHealthy());
    }

    @Test
    public void testIpDeleteTimeOut() {
        List<Instance> instances = new ArrayList<>();
        Instance instance = new Instance();
        instance.setLastBeat(System.currentTimeMillis());
        instance.setMarked(true);
        instance.setHealthy(true);
        Map<String, String> metadata = new HashMap<>();
        metadata.put(PreservedMetadataKeys.IP_DELETE_TIMEOUT, "10");
        instance.setMetadata(metadata);
        instances.add(instance);
        Mockito.doReturn(true).when(distroMapperSpy).responsible(null);
        Mockito.doReturn(true).when(globalConfig).isExpireInstance();
        Mockito.when(serviceSpy.allIPs(true)).thenReturn(instances);

        clientBeatCheckTask.run();
    }

    @Test
    public void testIpDeleteNotTimeOut() {
        List<Instance> instances = new ArrayList<>();
        Instance instance = new Instance();
        instance.setLastBeat(System.currentTimeMillis());
        instance.setMarked(true);
        instance.setHealthy(true);
        Map<String, String> metadata = new HashMap<>();
        metadata.put(PreservedMetadataKeys.IP_DELETE_TIMEOUT, "10000");
        instance.setMetadata(metadata);
        instances.add(instance);

        Mockito.doReturn(true).when(distroMapperSpy).responsible(null);
        Mockito.doReturn(true).when(globalConfig).isExpireInstance();
        Mockito.when(serviceSpy.allIPs(true)).thenReturn(instances);

        clientBeatCheckTask.run();
    }
}
