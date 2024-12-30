/*
 *
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
 *
 */

package com.alibaba.nacos.client.naming;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import com.alibaba.nacos.client.naming.event.InstancesChangeNotifier;
import com.alibaba.nacos.client.naming.remote.NamingClientProxy;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientProxy;
import com.alibaba.nacos.client.naming.selector.NamingSelectorFactory;
import com.alibaba.nacos.client.naming.selector.NamingSelectorWrapper;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import com.alibaba.nacos.common.utils.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.alibaba.nacos.client.naming.selector.NamingSelectorFactory.getUniqueClusterString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosNamingServiceTest {
    
    @Mock
    private NamingClientProxy proxy;
    
    @Mock
    private InstancesChangeNotifier changeNotifier;
    
    @Mock
    private ServiceInfoHolder serviceInfoHolder;
    
    private NacosNamingService client;
    
    @BeforeEach
    void before() throws NoSuchFieldException, NacosException, IllegalAccessException {
        Properties prop = new Properties();
        prop.setProperty("serverAddr", "localhost");
        prop.put(PropertyKeyConst.NAMESPACE, "test");
        client = new NacosNamingService(prop);
        injectMocks(client);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        client.shutDown();
    }
    
    private void injectMocks(NacosNamingService client) throws NoSuchFieldException, IllegalAccessException {
        // inject proxy
        Field serverProxyField = NacosNamingService.class.getDeclaredField("clientProxy");
        serverProxyField.setAccessible(true);
        try {
            ((NamingHttpClientProxy) serverProxyField.get(client)).shutdown();
        } catch (Throwable ignored) {
        }
        serverProxyField.set(client, proxy);
        
        // inject notifier
        doReturn(InstancesChangeEvent.class).when(changeNotifier).subscribeType();
        Field changeNotifierField = NacosNamingService.class.getDeclaredField("changeNotifier");
        changeNotifierField.setAccessible(true);
        changeNotifierField.set(client, changeNotifier);
        
        // inject service info holder
        Field serviceInfoHolderField = NacosNamingService.class.getDeclaredField("serviceInfoHolder");
        serviceInfoHolderField.setAccessible(true);
        try {
            ((ServiceInfoHolder) serviceInfoHolderField.get(client)).shutdown();
        } catch (Throwable ignored) {
        }
        serviceInfoHolderField.set(client, serviceInfoHolder);
    }
    
    @Test
    void testRegisterInstanceSingle() throws NacosException {
        //given
        String serviceName = "service1";
        String ip = "1.1.1.1";
        int port = 10000;
        //when
        client.registerInstance(serviceName, ip, port);
        //then
        verify(proxy, times(1)).registerService(eq(serviceName), eq(Constants.DEFAULT_GROUP),
                argThat(instance -> instance.getIp().equals(ip) && instance.getPort() == port
                        && Math.abs(instance.getWeight() - 1.0) < 0.01f && instance.getClusterName()
                        .equals(Constants.DEFAULT_CLUSTER_NAME)));
    }
    
    @Test
    void testRegisterInstanceSingleWithGroup() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        String ip = "1.1.1.1";
        int port = 10000;
        //when
        client.registerInstance(serviceName, groupName, ip, port);
        //then
        verify(proxy, times(1)).registerService(eq(serviceName), eq(groupName),
                argThat(instance -> instance.getIp().equals(ip) && instance.getPort() == port
                        && Math.abs(instance.getWeight() - 1.0) < 0.01f && instance.getClusterName()
                        .equals(Constants.DEFAULT_CLUSTER_NAME)));
    }
    
    @Test
    void testRegisterInstanceSingleWithCluster() throws NacosException {
        //given
        String serviceName = "service1";
        String clusterName = "cluster1";
        String ip = "1.1.1.1";
        int port = 10000;
        //when
        client.registerInstance(serviceName, ip, port, clusterName);
        //then
        verify(proxy, times(1)).registerService(eq(serviceName), eq(Constants.DEFAULT_GROUP),
                argThat(instance -> instance.getIp().equals(ip) && instance.getPort() == port
                        && Math.abs(instance.getWeight() - 1.0) < 0.01f && instance.getClusterName()
                        .equals(clusterName)));
    }
    
    @Test
    void testRegisterInstanceSingleFull() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        String clusterName = "cluster1";
        String ip = "1.1.1.1";
        int port = 10000;
        //when
        client.registerInstance(serviceName, groupName, ip, port, clusterName);
        //then
        verify(proxy, times(1)).registerService(eq(serviceName), eq(groupName),
                argThat(instance -> instance.getIp().equals(ip) && instance.getPort() == port
                        && Math.abs(instance.getWeight() - 1.0) < 0.01f && instance.getClusterName()
                        .equals(clusterName)));
    }
    
    @Test
    void testRegisterInstanceByInstanceOnlyService() throws NacosException {
        //given
        String serviceName = "service1";
        Instance instance = new Instance();
        //when
        client.registerInstance(serviceName, instance);
        //then
        verify(proxy, times(1)).registerService(serviceName, Constants.DEFAULT_GROUP, instance);
    }
    
    @Test
    void testRegisterInstanceByInstanceFullName() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        //when
        client.registerInstance(serviceName, groupName, instance);
        //then
        verify(proxy, times(1)).registerService(serviceName, groupName, instance);
    }
    
    @Test
    void testRegisterInstanceByInstanceWithCluster() throws NacosException {
        Throwable exception = assertThrows(NacosException.class, () -> {
            
            //given
            String serviceName = "service1";
            String groupName = "group1";
            Instance instance = new Instance();
            instance.setClusterName("cluster1,cluster2");
            //when
            client.registerInstance(serviceName, groupName, instance);
        });
        assertTrue(exception.getMessage().contains(
                "Instance 'clusterName' should be characters with only 0-9a-zA-Z-. (current: cluster1,cluster2)"));
    }
    
    @Test
    void testBatchRegisterInstance() throws NacosException {
        Instance instance = new Instance();
        String serviceName = "service1";
        String ip = "1.1.1.1";
        int port = 10000;
        instance.setServiceName(serviceName);
        instance.setEphemeral(true);
        instance.setPort(port);
        instance.setIp(ip);
        List<Instance> instanceList = new ArrayList<>();
        instanceList.add(instance);
        //when
        client.batchRegisterInstance(serviceName, Constants.DEFAULT_GROUP, instanceList);
        //then
        verify(proxy, times(1)).batchRegisterService(eq(serviceName), eq(Constants.DEFAULT_GROUP),
                argThat(instances -> CollectionUtils.isEqualCollection(instanceList, instances)));
    }
    
    @Test
    void testBatchRegisterInstanceWithGroupNamePrefix() throws NacosException {
        Instance instance = new Instance();
        String serviceName = "service1";
        String ip = "1.1.1.1";
        int port = 10000;
        instance.setServiceName(Constants.DEFAULT_GROUP + "@@" + serviceName);
        instance.setEphemeral(true);
        instance.setPort(port);
        instance.setIp(ip);
        List<Instance> instanceList = new ArrayList<>();
        instanceList.add(instance);
        //when
        client.batchRegisterInstance(serviceName, Constants.DEFAULT_GROUP, instanceList);
        //then
        verify(proxy, times(1)).batchRegisterService(eq(serviceName), eq(Constants.DEFAULT_GROUP),
                argThat(instances -> CollectionUtils.isEqualCollection(instanceList, instances)));
    }
    
    @Test
    void testBatchRegisterInstanceWithWrongGroupNamePrefix() throws NacosException {
        Instance instance = new Instance();
        String serviceName = "service1";
        String ip = "1.1.1.1";
        int port = 10000;
        instance.setServiceName("WrongGroup" + "@@" + serviceName);
        instance.setEphemeral(true);
        instance.setPort(port);
        instance.setIp(ip);
        List<Instance> instanceList = new ArrayList<>();
        instanceList.add(instance);
        //when
        try {
            client.batchRegisterInstance(serviceName, Constants.DEFAULT_GROUP, instanceList);
        } catch (Exception e) {
            assertTrue(e instanceof NacosException);
            assertTrue(e.getMessage().contains("wrong group name prefix of instance service name"));
        }
    }
    
    @Test
    void testBatchDeRegisterInstance() throws NacosException {
        Instance instance = new Instance();
        String serviceName = "service1";
        String ip = "1.1.1.1";
        int port = 10000;
        instance.setServiceName(serviceName);
        instance.setEphemeral(true);
        instance.setPort(port);
        instance.setIp(ip);
        List<Instance> instanceList = new ArrayList<>();
        instanceList.add(instance);
        //when
        try {
            client.batchDeregisterInstance(serviceName, Constants.DEFAULT_GROUP, instanceList);
        } catch (Exception e) {
            assertTrue(e instanceof NacosException);
            assertTrue(e.getMessage().contains("not found"));
        }
    }
    
    @Test
    void testDeregisterInstanceSingle() throws NacosException {
        //given
        String serviceName = "service1";
        String ip = "1.1.1.1";
        int port = 10000;
        //when
        client.deregisterInstance(serviceName, ip, port);
        //then
        verify(proxy, times(1)).deregisterService(eq(serviceName), eq(Constants.DEFAULT_GROUP),
                argThat(instance -> instance.getIp().equals(ip) && instance.getPort() == port
                        && Math.abs(instance.getWeight() - 1.0) < 0.01f && instance.getClusterName()
                        .equals(Constants.DEFAULT_CLUSTER_NAME)));
    }
    
    @Test
    void testDeregisterInstanceSingleWithGroup() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        String ip = "1.1.1.1";
        int port = 10000;
        //when
        client.deregisterInstance(serviceName, groupName, ip, port);
        //then
        verify(proxy, times(1)).deregisterService(eq(serviceName), eq(groupName),
                argThat(instance -> instance.getIp().equals(ip) && instance.getPort() == port
                        && Math.abs(instance.getWeight() - 1.0) < 0.01f && instance.getClusterName()
                        .equals(Constants.DEFAULT_CLUSTER_NAME)));
    }
    
    @Test
    void testDeregisterInstanceSingleWithCluster() throws NacosException {
        //given
        String serviceName = "service1";
        String clusterName = "cluster1";
        String ip = "1.1.1.1";
        int port = 10000;
        //when
        client.deregisterInstance(serviceName, ip, port, clusterName);
        //then
        verify(proxy, times(1)).deregisterService(eq(serviceName), eq(Constants.DEFAULT_GROUP),
                argThat(instance -> instance.getIp().equals(ip) && instance.getPort() == port
                        && Math.abs(instance.getWeight() - 1.0) < 0.01f && instance.getClusterName()
                        .equals(clusterName)));
    }
    
    @Test
    void testDeregisterInstanceSingleFull() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        String clusterName = "cluster1";
        String ip = "1.1.1.1";
        int port = 10000;
        //when
        client.deregisterInstance(serviceName, groupName, ip, port, clusterName);
        //then
        verify(proxy, times(1)).deregisterService(eq(serviceName), eq(groupName),
                argThat(instance -> instance.getIp().equals(ip) && instance.getPort() == port
                        && Math.abs(instance.getWeight() - 1.0) < 0.01f && instance.getClusterName()
                        .equals(clusterName)));
    }
    
    @Test
    void testDeregisterInstanceByInstanceOnlyService() throws NacosException {
        //given
        String serviceName = "service1";
        Instance instance = new Instance();
        //when
        client.deregisterInstance(serviceName, instance);
        //then
        verify(proxy, times(1)).deregisterService(serviceName, Constants.DEFAULT_GROUP, instance);
    }
    
    @Test
    void testDeregisterInstanceByInstanceFullName() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        //when
        client.deregisterInstance(serviceName, groupName, instance);
        //then
        verify(proxy, times(1)).deregisterService(serviceName, groupName, instance);
    }
    
    @Test
    void testGetAllInstancesOnlyService() throws NacosException {
        //given
        String serviceName = "service1";
        //when
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.addHost(new Instance());
        when(proxy.subscribe(serviceName, Constants.DEFAULT_GROUP, "")).thenReturn(serviceInfo);
        List<Instance> result = client.getAllInstances(serviceName);
        //then
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
    }
    
    @Test
    void testGetAllInstancesFullName() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        //when
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.setGroupName(groupName);
        serviceInfo.addHost(new Instance());
        when(proxy.subscribe(serviceName, groupName, "")).thenReturn(serviceInfo);
        List<Instance> result = client.getAllInstances(serviceName, groupName);
        //then
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
    }
    
    @Test
    void testGetAllInstancesOnlyServiceNotSubscribe() throws NacosException {
        //given
        String serviceName = "service1";
        //when
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.addHost(new Instance());
        when(proxy.queryInstancesOfService(serviceName, Constants.DEFAULT_GROUP, "", false)).thenReturn(serviceInfo);
        List<Instance> result = client.getAllInstances(serviceName, false);
        //then
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
    }
    
    @Test
    void testGetAllInstancesFullNameNotSubscribe() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        //when
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.setGroupName(groupName);
        serviceInfo.addHost(new Instance());
        when(proxy.queryInstancesOfService(serviceName, groupName, "", false)).thenReturn(serviceInfo);
        List<Instance> result = client.getAllInstances(serviceName, groupName, false);
        //then
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
        
    }
    
    @Test
    void testGetAllInstancesWithServiceAndClusters() throws NacosException {
        //given
        String serviceName = "service1";
        //when
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.addHost(mockInstance("cluster1", false));
        serviceInfo.addHost(mockInstance(Constants.DEFAULT_CLUSTER_NAME, false));
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        when(proxy.subscribe(serviceName, Constants.DEFAULT_GROUP, "")).thenReturn(serviceInfo);
        List<Instance> result = client.getAllInstances(serviceName, clusterList);
        //then
        assertEquals(1, result.size());
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
    }
    
    @Test
    void testGetAllInstancesWithFullNameAndClusters() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        // when
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.setGroupName(groupName);
        serviceInfo.addHost(mockInstance("cluster1", false));
        serviceInfo.addHost(mockInstance(Constants.DEFAULT_CLUSTER_NAME, false));
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        serviceInfo.getHosts().get(1).setClusterName(Constants.DEFAULT_CLUSTER_NAME);
        when(proxy.subscribe(serviceName, groupName, "")).thenReturn(serviceInfo);
        List<Instance> result = client.getAllInstances(serviceName, groupName, clusterList);
        //then
        assertEquals(1, result.size());
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
        
    }
    
    @Test
    void testGetAllInstancesWithServiceAndClustersNotSubscribe() throws NacosException {
        //given
        String serviceName = "service1";
        //when
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.addHost(mockInstance("cluster1", false));
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        when(proxy.queryInstancesOfService(serviceName, Constants.DEFAULT_GROUP, "cluster1,cluster2",
                false)).thenReturn(serviceInfo);
        List<Instance> result = client.getAllInstances(serviceName, clusterList, false);
        //then
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
    }
    
    @Test
    void testGetAllInstancesWithFullNameAndClustersNotSubscribe() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        // when
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.setGroupName(groupName);
        serviceInfo.addHost(mockInstance("cluster1", false));
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        when(proxy.queryInstancesOfService(serviceName, groupName, "cluster1,cluster2", false)).thenReturn(serviceInfo);
        List<Instance> result = client.getAllInstances(serviceName, groupName, clusterList, false);
        //then
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
    }
    
    @Test
    void testGetAllInstanceFromFailover() throws NacosException {
        when(serviceInfoHolder.isFailoverSwitch()).thenReturn(true);
        ServiceInfo serviceInfo = new ServiceInfo("group1@@service1");
        serviceInfo.setHosts(Collections.singletonList(new Instance()));
        when(serviceInfoHolder.getFailoverServiceInfo("service1", "group1", "")).thenReturn(serviceInfo);
        List<Instance> actual = client.getAllInstances("service1", "group1", false);
        verify(proxy, never()).queryInstancesOfService(anyString(), anyString(), anyString(), anyBoolean());
        assertEquals(1, actual.size());
        assertEquals(new Instance(), actual.get(0));
    }
    
    @Test
    void testGetAllInstanceFromFailoverEmpty() throws NacosException {
        when(serviceInfoHolder.isFailoverSwitch()).thenReturn(true);
        ServiceInfo serviceInfo = new ServiceInfo("group1@@service1");
        when(serviceInfoHolder.getFailoverServiceInfo("service1", "group1", "")).thenReturn(serviceInfo);
        List<Instance> actual = client.getAllInstances("service1", "group1", false);
        verify(proxy).queryInstancesOfService(anyString(), anyString(), anyString(), anyBoolean());
        assertEquals(0, actual.size());
    }
    
    @Test
    void testGetAllInstanceWithCacheAndSubscribeException() throws NacosException {
        String serviceName = "service1";
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.addHost(new Instance());
        when(serviceInfoHolder.getServiceInfo(serviceName, Constants.DEFAULT_GROUP, "")).thenReturn(serviceInfo);
        when(proxy.subscribe(serviceName, Constants.DEFAULT_GROUP, "")).thenThrow(new NacosException(500, "test"));
        List<Instance> result = client.getAllInstances(serviceName);
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
    }
    
    @Test
    void testGetAllInstanceWithoutCacheAndSubscribeException() throws NacosException {
        String serviceName = "service1";
        when(proxy.subscribe(serviceName, Constants.DEFAULT_GROUP, "")).thenThrow(new NacosException(500, "test"));
        assertThrows(NacosException.class, () -> client.getAllInstances(serviceName));
    }
    
    @Test
    void testGetAllInstanceWithCacheAndSubscribed() throws NacosException {
        String serviceName = "service1";
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.addHost(new Instance());
        when(serviceInfoHolder.getServiceInfo(serviceName, Constants.DEFAULT_GROUP, "")).thenReturn(serviceInfo);
        when(proxy.isSubscribed(serviceName, Constants.DEFAULT_GROUP, "")).thenReturn(true);
        List<Instance> result = client.getAllInstances(serviceName);
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
    }
    
    @Test
    void testSelectInstancesOnlyService() throws NacosException {
        //given
        String serviceName = "service1";
        //when
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.addHost(mockInstance(Constants.DEFAULT_CLUSTER_NAME, true));
        when(proxy.subscribe(serviceName, Constants.DEFAULT_GROUP, "")).thenReturn(serviceInfo);
        List<Instance> result = client.selectInstances(serviceName, true);
        //then
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
    }
    
    @Test
    void testSelectInstancesFullName() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        //when
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.addHost(mockInstance(Constants.DEFAULT_CLUSTER_NAME, true));
        when(proxy.subscribe(serviceName, groupName, "")).thenReturn(serviceInfo);
        List<Instance> result = client.selectInstances(serviceName, groupName, true);
        //then
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
    }
    
    @Test
    void testSelectInstancesOnlyServiceNotSubscribe() throws NacosException {
        //given
        String serviceName = "service1";
        //when
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.addHost(mockInstance(Constants.DEFAULT_CLUSTER_NAME, true));
        when(proxy.queryInstancesOfService(serviceName, Constants.DEFAULT_GROUP, "", false)).thenReturn(serviceInfo);
        List<Instance> result = client.selectInstances(serviceName, true, false);
        //then
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
    }
    
    @Test
    void testSelectInstancesFullNameNotSubscribe() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        //when
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.addHost(mockInstance(Constants.DEFAULT_CLUSTER_NAME, true));
        when(proxy.queryInstancesOfService(serviceName, groupName, "", false)).thenReturn(serviceInfo);
        List<Instance> result = client.selectInstances(serviceName, groupName, true, false);
        //then
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
        
    }
    
    @Test
    void testSelectInstancesWithServiceAndClusters() throws NacosException {
        //given
        String serviceName = "service1";
        //when
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.addHost(mockInstance("cluster1", true));
        serviceInfo.addHost(mockInstance("cluster1", false));
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        when(proxy.subscribe(serviceName, Constants.DEFAULT_GROUP, "")).thenReturn(serviceInfo);
        List<Instance> result = client.selectInstances(serviceName, clusterList, true);
        //then
        assertEquals(1, result.size());
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
    }
    
    @Test
    void testSelectInstancesWithFullNameAndClusters() throws NacosException {
        //given
        String serviceName = "service1";
        final String groupName = "group1";
        //when
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.addHost(mockInstance("cluster1", true));
        serviceInfo.addHost(mockInstance("cluster1", false));
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        when(proxy.subscribe(serviceName, groupName, "")).thenReturn(serviceInfo);
        List<Instance> result = client.selectInstances(serviceName, groupName, clusterList, true);
        //then
        assertEquals(1, result.size());
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
        
    }
    
    @Test
    void testSelectInstancesWithServiceAndClustersNotSubscribe() throws NacosException {
        //given
        String serviceName = "service1";
        //when
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.addHost(mockInstance("cluster1", true));
        serviceInfo.addHost(mockInstance("cluster1", false));
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        when(proxy.queryInstancesOfService(serviceName, Constants.DEFAULT_GROUP, "cluster1,cluster2",
                false)).thenReturn(serviceInfo);
        List<Instance> result = client.selectInstances(serviceName, clusterList, true, false);
        //then
        assertEquals(1, result.size());
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
    }
    
    @Test
    void testSelectInstancesWithFullNameAndClustersNotSubscribe() throws NacosException {
        //given
        String serviceName = "service1";
        final String groupName = "group1";
        //when
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.addHost(mockInstance("cluster1", true));
        serviceInfo.addHost(mockInstance("cluster1", false));
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        when(proxy.queryInstancesOfService(serviceName, groupName, "cluster1,cluster2", false)).thenReturn(serviceInfo);
        List<Instance> result = client.selectInstances(serviceName, groupName, clusterList, true, false);
        //then
        assertEquals(1, result.size());
        assertEquals(serviceInfo.getHosts().get(0), result.get(0));
    }
    
    @Test
    void testSelectInstancesWithHealthyFlag() throws NacosException {
        //given
        Instance healthyInstance = new Instance();
        healthyInstance.setHealthy(true);
        
        Instance instance1 = new Instance();
        instance1.setHealthy(false);
        
        Instance instance2 = new Instance();
        instance2.setHealthy(true);
        instance2.setEnabled(false);
        Instance instance3 = new Instance();
        instance3.setHealthy(true);
        instance3.setWeight(0.0);
        
        List<Instance> hosts = new ArrayList<>();
        hosts.add(healthyInstance);
        hosts.add(instance1);
        hosts.add(instance2);
        hosts.add(instance3);
        
        ServiceInfo info = new ServiceInfo();
        info.setHosts(hosts);
        String serviceName = "service1";
        String groupName = "group1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        when(proxy.queryInstancesOfService(serviceName, groupName, "cluster1,cluster2", false)).thenReturn(info);
        
        //when
        List<Instance> instances = client.selectInstances(serviceName, groupName, clusterList, true, false);
        //then
        assertEquals(1, instances.size());
        assertSame(healthyInstance, instances.get(0));
    }
    
    @Test
    void testSelectOneHealthyInstanceOnlyService() throws NacosException {
        //given
        ServiceInfo infoWithHealthyInstance = new ServiceInfo();
        infoWithHealthyInstance.addHost(mockInstance(Constants.DEFAULT_CLUSTER_NAME, true));
        when(proxy.subscribe(anyString(), anyString(), anyString())).thenReturn(infoWithHealthyInstance);
        
        String serviceName = "service1";
        //when
        Instance instance = client.selectOneHealthyInstance(serviceName);
        //then
        assertNotNull(instance);
    }
    
    @Test
    void testSelectOneHealthyInstanceFullName() throws NacosException {
        //given
        ServiceInfo infoWithHealthyInstance = new ServiceInfo();
        infoWithHealthyInstance.addHost(mockInstance(Constants.DEFAULT_CLUSTER_NAME, true));
        when(proxy.subscribe(anyString(), anyString(), anyString())).thenReturn(infoWithHealthyInstance);
        
        String serviceName = "service1";
        String groupName = "group1";
        //when
        Instance instance = client.selectOneHealthyInstance(serviceName, groupName);
        //then
        assertNotNull(instance);
    }
    
    @Test
    void testSelectOneHealthyInstanceOnlyServiceNotSubscribe() throws NacosException {
        //given
        ServiceInfo infoWithHealthyInstance = new ServiceInfo();
        infoWithHealthyInstance.addHost(mockInstance(Constants.DEFAULT_CLUSTER_NAME, true));
        when(proxy.queryInstancesOfService(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(
                infoWithHealthyInstance);
        
        String serviceName = "service1";
        //when
        Instance instance = client.selectOneHealthyInstance(serviceName, false);
        //then
        assertNotNull(instance);
    }
    
    @Test
    void testSelectOneHealthyInstanceFullNameNotSubscribe() throws NacosException {
        //given
        ServiceInfo infoWithHealthyInstance = new ServiceInfo();
        infoWithHealthyInstance.addHost(mockInstance(Constants.DEFAULT_CLUSTER_NAME, true));
        when(proxy.queryInstancesOfService(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(
                infoWithHealthyInstance);
        
        String serviceName = "service1";
        String groupName = "group1";
        //when
        Instance instance = client.selectOneHealthyInstance(serviceName, groupName, false);
        //then
        assertNotNull(instance);
        
    }
    
    @Test
    void testSelectOneHealthyInstanceWithServiceAndClusters() throws NacosException {
        //given
        ServiceInfo infoWithHealthyInstance = new ServiceInfo();
        infoWithHealthyInstance.addHost(mockInstance("cluster1", true));
        infoWithHealthyInstance.addHost(mockInstance(Constants.DEFAULT_CLUSTER_NAME, true));
        String serviceName = "service1";
        when(proxy.subscribe(serviceName, Constants.DEFAULT_GROUP, StringUtils.EMPTY)).thenReturn(
                infoWithHealthyInstance);
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        //when
        Instance instance = client.selectOneHealthyInstance(serviceName, clusterList);
        //then
        assertNotNull(instance);
        assertEquals("cluster1", instance.getClusterName());
    }
    
    @Test
    void testSelectOneHealthyInstanceWithFullNameAndClusters() throws NacosException {
        //given
        ServiceInfo infoWithHealthyInstance = new ServiceInfo();
        infoWithHealthyInstance.addHost(mockInstance("cluster1", true));
        infoWithHealthyInstance.addHost(mockInstance(Constants.DEFAULT_CLUSTER_NAME, true));
        when(proxy.subscribe(anyString(), anyString(), anyString())).thenReturn(infoWithHealthyInstance);
        
        String serviceName = "service1";
        String groupName = "group1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        //when
        Instance instance = client.selectOneHealthyInstance(serviceName, groupName, clusterList);
        //then
        assertNotNull(instance);
        assertEquals("cluster1", instance.getClusterName());
    }
    
    @Test
    void testSelectOneHealthyInstanceWithServiceAndClustersNotSubscribe() throws NacosException {
        //given
        ServiceInfo infoWithHealthyInstance = new ServiceInfo();
        infoWithHealthyInstance.addHost(mockInstance("cluster1", true));
        when(proxy.queryInstancesOfService(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(
                infoWithHealthyInstance);
        
        String serviceName = "service1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        //when
        Instance instance = client.selectOneHealthyInstance(serviceName, clusterList, false);
        //then
        assertNotNull(instance);
        assertEquals("cluster1", instance.getClusterName());
    }
    
    @Test
    void testSelectOneHealthyInstanceWithFullNameAndClustersNotSubscribe() throws NacosException {
        //given
        ServiceInfo infoWithHealthyInstance = new ServiceInfo();
        infoWithHealthyInstance.addHost(mockInstance("cluster1", true));
        when(proxy.queryInstancesOfService(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(
                infoWithHealthyInstance);
        
        String serviceName = "service1";
        String groupName = "group1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        //when
        Instance instance = client.selectOneHealthyInstance(serviceName, groupName, clusterList, false);
        //then
        assertNotNull(instance);
        assertEquals("cluster1", instance.getClusterName());
    }
    
    @Test
    void testSubscribeOnlyService() throws NacosException {
        //given
        String serviceName = "service1";
        EventListener listener = event -> {
        
        };
        //when
        client.subscribe(serviceName, listener);
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(serviceName, Constants.DEFAULT_GROUP, Constants.NULL,
                NamingSelectorFactory.newClusterSelector(Collections.emptyList()), listener);
        //then
        verify(changeNotifier, times(1)).registerListener(Constants.DEFAULT_GROUP, serviceName, wrapper);
        verify(proxy, times(1)).subscribe(serviceName, Constants.DEFAULT_GROUP, "");
    }
    
    @Test
    void testSubscribeFullName() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        EventListener listener = event -> {
        
        };
        //when
        client.subscribe(serviceName, groupName, listener);
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(serviceName, groupName, Constants.NULL,
                NamingSelectorFactory.newClusterSelector(Collections.emptyList()), listener);
        //then
        verify(changeNotifier, times(1)).registerListener(groupName, serviceName, wrapper);
        verify(proxy, times(1)).subscribe(serviceName, groupName, "");
    }
    
    @Test
    void testSubscribeWithServiceAndClusters() throws NacosException {
        //given
        String serviceName = "service1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        EventListener listener = event -> {
        
        };
        //when
        client.subscribe(serviceName, clusterList, listener);
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(serviceName, Constants.DEFAULT_GROUP, Constants.NULL,
                NamingSelectorFactory.newClusterSelector(clusterList), listener);
        //then
        verify(changeNotifier, times(1)).registerListener(Constants.DEFAULT_GROUP, serviceName, wrapper);
        verify(proxy, times(1)).subscribe(serviceName, Constants.DEFAULT_GROUP, Constants.NULL);
    }
    
    @Test
    void testSubscribeWithFullNameAndClusters() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        EventListener listener = event -> {
        
        };
        //when
        client.subscribe(serviceName, groupName, clusterList, listener);
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(serviceName, groupName,
                getUniqueClusterString(clusterList), NamingSelectorFactory.newClusterSelector(clusterList), listener);
        //then
        verify(changeNotifier, times(1)).registerListener(groupName, serviceName, wrapper);
        verify(proxy, times(1)).subscribe(serviceName, groupName, Constants.NULL);
    }
    
    @Test
    public void testSubscribeWithServiceAndCustomSelector() throws NacosException {
        String serviceName = "service1";
        EventListener listener = event -> {
        
        };
        //when
        client.subscribe(serviceName, NamingSelectorFactory.HEALTHY_SELECTOR, listener);
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(serviceName, Constants.DEFAULT_GROUP, Constants.NULL,
                NamingSelectorFactory.HEALTHY_SELECTOR, listener);
        //then
        verify(changeNotifier, times(1)).registerListener(Constants.DEFAULT_GROUP, serviceName, wrapper);
        verify(proxy, times(1)).subscribe(serviceName, Constants.DEFAULT_GROUP, Constants.NULL);
    }
    
    @Test
    public void testSubscribeWithFullNameAndCustomSelector() throws NacosException {
        String serviceName = "service1";
        String groupName = "group1";
        EventListener listener = event -> {
        
        };
        //when
        client.subscribe(serviceName, groupName, NamingSelectorFactory.HEALTHY_SELECTOR, listener);
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(serviceName, groupName, Constants.NULL,
                NamingSelectorFactory.HEALTHY_SELECTOR, listener);
        //then
        verify(changeNotifier, times(1)).registerListener(groupName, serviceName, wrapper);
        verify(proxy, times(1)).subscribe(serviceName, groupName, Constants.NULL);
    }
    
    @Test
    void testSubscribeWithNullListener() throws NacosException {
        String serviceName = "service1";
        String groupName = "group1";
        //when
        client.subscribe(serviceName, groupName, null);
        //then
        verify(changeNotifier, never()).registerListener(groupName, serviceName,
                new NamingSelectorWrapper(NamingSelectorFactory.newIpSelector(""), null));
        verify(proxy, never()).subscribe(serviceName, groupName, "");
        
    }
    
    @Test
    void testSubscribeDuplicate() throws NacosException {
        String serviceName = "service1";
        when(changeNotifier.isSubscribed(Constants.DEFAULT_GROUP, serviceName)).thenReturn(true);
        ServiceInfo serviceInfo = new ServiceInfo(Constants.DEFAULT_GROUP + "@@" + serviceName);
        serviceInfo.addHost(new Instance());
        when(serviceInfoHolder.getServiceInfo(serviceName, Constants.DEFAULT_GROUP, "")).thenReturn(serviceInfo);
        final AtomicBoolean flag = new AtomicBoolean(false);
        client.subscribe(serviceName, event -> flag.set(true));
        assertTrue(flag.get());
    }
    
    @Test
    void testUnSubscribeOnlyService() throws NacosException {
        //given
        String serviceName = "service1";
        EventListener listener = event -> {
        
        };
        when(changeNotifier.isSubscribed(Constants.DEFAULT_GROUP, serviceName)).thenReturn(false);
        //when
        client.unsubscribe(serviceName, listener);
        //then
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(
                NamingSelectorFactory.newClusterSelector(Collections.emptyList()), listener);
        verify(changeNotifier, times(1)).deregisterListener(Constants.DEFAULT_GROUP, serviceName, wrapper);
        verify(proxy, times(1)).unsubscribe(serviceName, Constants.DEFAULT_GROUP, Constants.NULL);
    }
    
    @Test
    void testUnSubscribeFullName() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        EventListener listener = event -> {
        
        };
        when(changeNotifier.isSubscribed(groupName, serviceName)).thenReturn(false);
        
        //when
        client.unsubscribe(serviceName, groupName, listener);
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(
                NamingSelectorFactory.newClusterSelector(Collections.emptyList()), listener);
        //then
        verify(changeNotifier, times(1)).deregisterListener(groupName, serviceName, wrapper);
        verify(proxy, times(1)).unsubscribe(serviceName, groupName, Constants.NULL);
    }
    
    @Test
    void testUnSubscribeWithServiceAndClusters() throws NacosException {
        //given
        String serviceName = "service1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        EventListener listener = event -> {
        
        };
        when(changeNotifier.isSubscribed(Constants.DEFAULT_GROUP, serviceName)).thenReturn(false);
        
        //when
        client.unsubscribe(serviceName, clusterList, listener);
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(NamingSelectorFactory.newClusterSelector(clusterList),
                listener);
        //then
        verify(changeNotifier, times(1)).deregisterListener(Constants.DEFAULT_GROUP, serviceName, wrapper);
        verify(proxy, times(1)).unsubscribe(serviceName, Constants.DEFAULT_GROUP, Constants.NULL);
    }
    
    @Test
    void testUnSubscribeWithFullNameAndClusters() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        EventListener listener = event -> {
        
        };
        when(changeNotifier.isSubscribed(groupName, serviceName)).thenReturn(false);
        
        //when
        client.unsubscribe(serviceName, groupName, clusterList, listener);
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(NamingSelectorFactory.newClusterSelector(clusterList),
                listener);
        //then
        verify(changeNotifier, times(1)).deregisterListener(groupName, serviceName, wrapper);
        verify(proxy, times(1)).unsubscribe(serviceName, groupName, Constants.NULL);
    }
    
    @Test
    public void testUnSubscribeWithServiceAndCustomSelector() throws NacosException {
        //given
        String serviceName = "service1";
        EventListener listener = event -> {
        
        };
        when(changeNotifier.isSubscribed(Constants.DEFAULT_GROUP, serviceName)).thenReturn(false);
        
        //when
        client.unsubscribe(serviceName, NamingSelectorFactory.HEALTHY_SELECTOR, listener);
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(NamingSelectorFactory.HEALTHY_SELECTOR, listener);
        //then
        verify(changeNotifier, times(1)).deregisterListener(Constants.DEFAULT_GROUP, serviceName, wrapper);
        verify(proxy, times(1)).unsubscribe(serviceName, Constants.DEFAULT_GROUP, Constants.NULL);
    }
    
    @Test
    public void testUnSubscribeWithFullNameAndCustomSelector() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        EventListener listener = event -> {
        
        };
        when(changeNotifier.isSubscribed(groupName, serviceName)).thenReturn(false);
        
        //when
        client.unsubscribe(serviceName, groupName, NamingSelectorFactory.HEALTHY_SELECTOR, listener);
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(NamingSelectorFactory.HEALTHY_SELECTOR, listener);
        //then
        verify(changeNotifier, times(1)).deregisterListener(groupName, serviceName, wrapper);
        verify(proxy, times(1)).unsubscribe(serviceName, groupName, Constants.NULL);
    }
    
    @Test
    void testUnSubscribeWithNullListener() throws NacosException {
        String serviceName = "service1";
        String groupName = "group1";
        //when
        client.unsubscribe(serviceName, groupName, null);
        //then
        verify(changeNotifier, never()).deregisterListener(groupName, serviceName,
                new NamingSelectorWrapper(NamingSelectorFactory.newIpSelector(""), null));
        verify(proxy, never()).unsubscribe(serviceName, groupName, "");
        
    }
    
    @Test
    void testGetServicesOfServer1() throws NacosException {
        //given
        int pageNo = 1;
        int pageSize = 10;
        //when
        client.getServicesOfServer(pageNo, pageSize);
        //then
        verify(proxy, times(1)).getServiceList(pageNo, pageSize, Constants.DEFAULT_GROUP, null);
    }
    
    @Test
    void testGetServicesOfServer2() throws NacosException {
        //given
        int pageNo = 1;
        int pageSize = 10;
        String groupName = "group1";
        //when
        client.getServicesOfServer(pageNo, pageSize, groupName);
        //then
        verify(proxy, times(1)).getServiceList(pageNo, pageSize, groupName, null);
    }
    
    @Test
    void testGetServicesOfServer3() throws NacosException {
        //given
        int pageNo = 1;
        int pageSize = 10;
        AbstractSelector selector = new AbstractSelector("aaa") {
            @Override
            public String getType() {
                return super.getType();
            }
        };
        //when
        client.getServicesOfServer(pageNo, pageSize, selector);
        //then
        verify(proxy, times(1)).getServiceList(pageNo, pageSize, Constants.DEFAULT_GROUP, selector);
    }
    
    @Test
    void testGetServicesOfServer4() throws NacosException {
        //given
        int pageNo = 1;
        int pageSize = 10;
        String groupName = "group1";
        
        AbstractSelector selector = new AbstractSelector("aaa") {
            @Override
            public String getType() {
                return super.getType();
            }
        };
        //when
        client.getServicesOfServer(pageNo, pageSize, groupName, selector);
        //then
        verify(proxy, times(1)).getServiceList(pageNo, pageSize, groupName, selector);
    }
    
    @Test
    void testGetSubscribeServices() {
        //when
        client.getSubscribeServices();
        //then
        verify(changeNotifier, times(1)).getSubscribeServices();
    }
    
    @Test
    void testGetServerStatus() {
        //given
        when(proxy.serverHealthy()).thenReturn(true);
        //when
        String serverStatus = client.getServerStatus();
        //then
        assertEquals("UP", serverStatus);
    }
    
    @Test
    void testGetServerStatusFail() {
        //given
        when(proxy.serverHealthy()).thenReturn(false);
        //when
        String serverStatus = client.getServerStatus();
        //then
        assertEquals("DOWN", serverStatus);
    }
    
    @Test
    void testShutDown() throws NacosException {
        //when
        client.shutDown();
        //then
        verify(proxy, times(1)).shutdown();
    }
    
    @Test
    void testConstructorWithServerList() throws NacosException, NoSuchFieldException, IllegalAccessException {
        NacosNamingService namingService = new NacosNamingService("localhost");
        try {
            Field namespaceField = NacosNamingService.class.getDeclaredField("namespace");
            namespaceField.setAccessible(true);
            String namespace = (String) namespaceField.get(namingService);
            assertEquals(UtilAndComs.DEFAULT_NAMESPACE_ID, namespace);
        } finally {
            namingService.shutDown();
        }
    }
    
    private Instance mockInstance(String clusterName, boolean healthy) {
        Instance instance = new Instance();
        instance.setClusterName(clusterName);
        instance.setHealthy(healthy);
        return instance;
    }
}