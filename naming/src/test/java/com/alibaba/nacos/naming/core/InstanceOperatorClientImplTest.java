/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingResponseCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.InstanceMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataOperateService;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.ClientOperationServiceProxy;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.pojo.InstanceOperationInfo;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.pojo.instance.BeatInfoInstanceBuilder;
import com.alibaba.nacos.naming.push.UdpPushService;
import com.alibaba.nacos.naming.selector.SelectorManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link InstanceOperatorClientImpl} unit tests.
 *
 * @author chenglu
 * @date 2021-08-03 22:46
 */
@RunWith(MockitoJUnitRunner.class)
public class InstanceOperatorClientImplTest {
    
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    
    @InjectMocks
    private InstanceOperatorClientImpl instanceOperatorClient;
    
    @Mock
    private ClientManagerDelegate clientManager;
    
    @Mock
    private ClientOperationServiceProxy clientOperationService;
    
    @Mock
    private ServiceStorage serviceStorage;
    
    @Mock
    private NamingMetadataOperateService metadataOperateService;
    
    @Mock
    private NamingMetadataManager metadataManager;
    
    @Mock
    private SwitchDomain switchDomain;
    
    @Mock
    private UdpPushService pushService;
    
    @Mock
    private SelectorManager selectorManager;
    
    @Mock
    private ConfigurableApplicationContext context;
    
    @Before
    public void setUp() {
        Service service = Service.newService("A", "DEFAULT_GROUP", "C");
        ServiceManager.getInstance().getSingleton(service);
        EnvUtil.setEnvironment(new MockEnvironment());
        ApplicationUtils.injectContext(context);
        when(context.getBean(SelectorManager.class)).thenReturn(selectorManager);
        when(selectorManager.select(any(), any(), any()))
                .then((Answer<List<Instance>>) invocationOnMock -> invocationOnMock.getArgument(2));
    }
    
    @After
    public void tearDown() {
        Service service = Service.newService("A", "DEFAULT_GROUP", "C");
        ServiceManager.getInstance().removeSingleton(service);
    }
    
    @Test
    public void testRegisterInstance() throws NacosException {
        instanceOperatorClient.registerInstance("A", "B", new Instance());
        
        Mockito.verify(clientOperationService).registerInstance(Mockito.any(), Mockito.any(), Mockito.anyString());
    }
    
    @Test
    public void testRegisterInstanceWithInvalidClusterName() throws NacosException {
        expectedException.expect(NacosException.class);
        expectedException.expectMessage("Instance 'clusterName' should be characters with only 0-9a-zA-Z-. (current: cluster1,cluster2)");
        
        Instance instance = new Instance();
        instance.setEphemeral(true);
        instance.setClusterName("cluster1,cluster2");
        new InstanceOperatorClientImpl(null, null, null, null, null, null, null).registerInstance("ns-01",
                "serviceName01", instance);
    }
    
    @Test
    public void testRemoveInstance() {
        when(clientManager.contains(Mockito.anyString())).thenReturn(true);
        
        instanceOperatorClient.removeInstance("A", "B", new Instance());
        
        Mockito.verify(clientOperationService).deregisterInstance(Mockito.any(), Mockito.any(), Mockito.anyString());
    }
    
    @Test
    public void testUpdateInstance() throws NacosException {
        Instance instance = new Instance();
        instance.setServiceName("C");
        instanceOperatorClient.updateInstance("A", "C", instance);
        
        Mockito.verify(metadataOperateService).updateInstanceMetadata(Mockito.any(), Mockito.any(), Mockito.any());
    }
    
    @Test
    public void testPatchInstance() throws NacosException {
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(8848);
        instance.setClusterName("C");
        List<Instance> instances = Collections.singletonList(instance);
        
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(instances);
        when(serviceStorage.getData(Mockito.any())).thenReturn(serviceInfo);
        
        instanceOperatorClient.patchInstance("A", "B", new InstancePatchObject("C", "1.1.1.1", 8848));
        
        Mockito.verify(metadataOperateService)
                .updateInstanceMetadata(Mockito.any(), Mockito.anyString(), Mockito.any());
    }
    
    @Test
    public void testListInstance() {
        when(pushService.canEnablePush(Mockito.anyString())).thenReturn(true);
        
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setGroupName("DEFAULT_GROUP");
        serviceInfo.setName("B");
        when(serviceStorage.getData(Mockito.any())).thenReturn(serviceInfo);
        
        ServiceMetadata metadata = new ServiceMetadata();
        when(metadataManager.getServiceMetadata(Mockito.any())).thenReturn(Optional.of(metadata));
        
        Subscriber subscriber = new Subscriber("2.2.2.2", "", "app", "1.1.1.1", "A", "B", 8848);
        instanceOperatorClient.listInstance("A", "B", subscriber, "C", true);
        
        Mockito.verify(clientOperationService).subscribeService(Mockito.any(), Mockito.any(), Mockito.anyString());
    }
    
    @Test
    public void testHandleBeat() throws NacosException {
        IpPortBasedClient ipPortBasedClient = Mockito.mock(IpPortBasedClient.class);
        when(clientManager.getClient(Mockito.anyString())).thenReturn(ipPortBasedClient);
        
        when(ipPortBasedClient.getAllPublishedService()).thenReturn(Collections.emptyList());
        
        RsInfo rsInfo = new RsInfo();
        rsInfo.setMetadata(new HashMap<>(1));
        int res = instanceOperatorClient
                .handleBeat("A", "C", "1.1.1.1", 8848, "D", rsInfo, BeatInfoInstanceBuilder.newBuilder());
        
        Assert.assertEquals(NamingResponseCode.OK, res);
    }
    
    @Test
    public void testGetHeartBeatInterval() {
        InstanceMetadata instanceMetadata = new InstanceMetadata();
        Map<String, Object> map = new HashMap<>(2);
        instanceMetadata.setExtendData(map);
        when(metadataManager.getInstanceMetadata(Mockito.any(), Mockito.anyString()))
                .thenReturn(Optional.of(instanceMetadata));
        
        when(switchDomain.getClientBeatInterval()).thenReturn(100L);
        
        long interval = instanceOperatorClient.getHeartBeatInterval("A", "C", "1.1.1.1", 8848, "D");
        
        Assert.assertEquals(100L, interval);
    }
    
    @Test
    public void testListAllInstances() throws NacosException {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(Collections.emptyList());
        
        when(serviceStorage.getData(Mockito.any())).thenReturn(serviceInfo);
        
        List<? extends Instance> instances = instanceOperatorClient.listAllInstances("A", "C");
        
        Assert.assertEquals(0, instances.size());
    }
    
    @Test
    public void testBatchUpdateMetadata() throws NacosException {
        Instance instance = new Instance();
        instance.setServiceName("C");
        instance.setIp("1.1.1.1");
        instance.setPort(8848);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(Collections.singletonList(instance));
        when(serviceStorage.getData(Mockito.any())).thenReturn(serviceInfo);
        
        InstanceOperationInfo instanceOperationInfo = new InstanceOperationInfo();
        List<String> res = instanceOperatorClient.batchUpdateMetadata("A", instanceOperationInfo, new HashMap<>());
        
        Assert.assertEquals(1, res.size());
    }
    
    @Test
    public void testBatchDeleteMetadata() throws NacosException {
        Instance instance = new Instance();
        instance.setServiceName("C");
        instance.setIp("1.1.1.1");
        instance.setPort(8848);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(Collections.singletonList(instance));
        when(serviceStorage.getData(Mockito.any())).thenReturn(serviceInfo);
        
        List<String> res = instanceOperatorClient
                .batchDeleteMetadata("A", new InstanceOperationInfo(), new HashMap<>());
        
        Assert.assertEquals(1, res.size());
    }
}
