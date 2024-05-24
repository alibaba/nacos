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
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void testRegisterInstance1() throws NacosException {
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
    void testRegisterInstance2() throws NacosException {
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
    void testRegisterInstance3() throws NacosException {
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
    void testRegisterInstance4() throws NacosException {
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
    void testRegisterInstance5() throws NacosException {
        //given
        String serviceName = "service1";
        Instance instance = new Instance();
        //when
        client.registerInstance(serviceName, instance);
        //then
        verify(proxy, times(1)).registerService(serviceName, Constants.DEFAULT_GROUP, instance);
    }
    
    @Test
    void testRegisterInstance6() throws NacosException {
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
    void testRegisterInstance7() throws NacosException {
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
    void testDeregisterInstance1() throws NacosException {
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
    void testDeregisterInstance2() throws NacosException {
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
    void testDeregisterInstance3() throws NacosException {
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
    void testDeregisterInstance4() throws NacosException {
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
    void testDeregisterInstance5() throws NacosException {
        //given
        String serviceName = "service1";
        Instance instance = new Instance();
        //when
        client.deregisterInstance(serviceName, instance);
        //then
        verify(proxy, times(1)).deregisterService(serviceName, Constants.DEFAULT_GROUP, instance);
    }
    
    @Test
    void testDeregisterInstance6() throws NacosException {
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
    void testGetAllInstances1() throws NacosException {
        //given
        String serviceName = "service1";
        //when
        client.getAllInstances(serviceName);
        //then
        verify(proxy, times(1)).subscribe(serviceName, Constants.DEFAULT_GROUP, "");
    }
    
    @Test
    void testGetAllInstances2() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        //when
        client.getAllInstances(serviceName, groupName);
        //then
        verify(proxy, times(1)).subscribe(serviceName, groupName, "");
    }
    
    @Test
    void testGetAllInstances3() throws NacosException {
        //given
        String serviceName = "service1";
        //when
        client.getAllInstances(serviceName, false);
        //then
        verify(proxy, times(1)).queryInstancesOfService(serviceName, Constants.DEFAULT_GROUP, "", false);
    }
    
    @Test
    void testGetAllInstances4() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        //when
        client.getAllInstances(serviceName, groupName, false);
        //then
        verify(proxy, times(1)).queryInstancesOfService(serviceName, groupName, "", false);
        
    }
    
    @Test
    void testGetAllInstances5() throws NacosException {
        //given
        String serviceName = "service1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        //when
        client.getAllInstances(serviceName, clusterList);
        //then
        verify(proxy, times(1)).subscribe(serviceName, Constants.DEFAULT_GROUP, "cluster1,cluster2");
    }
    
    @Test
    void testGetAllInstances6() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        //when
        client.getAllInstances(serviceName, groupName, clusterList);
        //then
        verify(proxy, times(1)).subscribe(serviceName, groupName, "cluster1,cluster2");
        
    }
    
    @Test
    void testGetAllInstances7() throws NacosException {
        //given
        String serviceName = "service1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        //when
        client.getAllInstances(serviceName, clusterList, false);
        //then
        verify(proxy, times(1)).queryInstancesOfService(serviceName, Constants.DEFAULT_GROUP, "cluster1,cluster2",
                false);
    }
    
    @Test
    void testGetAllInstances8() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        //when
        client.getAllInstances(serviceName, groupName, clusterList, false);
        //then
        verify(proxy, times(1)).queryInstancesOfService(serviceName, groupName, "cluster1,cluster2", false);
    }
    
    @Test
    void testGetAllInstanceFromFailover() throws NacosException {
        when(serviceInfoHolder.isFailoverSwitch()).thenReturn(true);
        ServiceInfo serviceInfo = new ServiceInfo("group1@@service1");
        serviceInfo.setHosts(Collections.singletonList(new Instance()));
        when(serviceInfoHolder.getFailoverServiceInfo(anyString(), anyString(), anyString())).thenReturn(serviceInfo);
        List<Instance> actual = client.getAllInstances("service1", "group1", false);
        verify(proxy, never()).queryInstancesOfService(anyString(), anyString(), anyString(), anyBoolean());
        assertEquals(1, actual.size());
        assertEquals(new Instance(), actual.get(0));
    }
    
    @Test
    void testGetAllInstanceFromFailoverEmpty() throws NacosException {
        when(serviceInfoHolder.isFailoverSwitch()).thenReturn(true);
        ServiceInfo serviceInfo = new ServiceInfo("group1@@service1");
        when(serviceInfoHolder.getFailoverServiceInfo(anyString(), anyString(), anyString())).thenReturn(serviceInfo);
        List<Instance> actual = client.getAllInstances("service1", "group1", false);
        verify(proxy).queryInstancesOfService(anyString(), anyString(), anyString(), anyBoolean());
        assertEquals(0, actual.size());
    }
    
    @Test
    void testSelectInstances1() throws NacosException {
        //given
        String serviceName = "service1";
        //when
        client.selectInstances(serviceName, true);
        //then
        verify(proxy, times(1)).subscribe(serviceName, Constants.DEFAULT_GROUP, "");
    }
    
    @Test
    void testSelectInstances2() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        //when
        client.selectInstances(serviceName, groupName, true);
        //then
        verify(proxy, times(1)).subscribe(serviceName, groupName, "");
    }
    
    @Test
    void testSelectInstances3() throws NacosException {
        //given
        String serviceName = "service1";
        //when
        client.selectInstances(serviceName, true, false);
        //then
        verify(proxy, times(1)).queryInstancesOfService(serviceName, Constants.DEFAULT_GROUP, "", false);
    }
    
    @Test
    void testSelectInstances4() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        //when
        client.selectInstances(serviceName, groupName, true, false);
        //then
        verify(proxy, times(1)).queryInstancesOfService(serviceName, groupName, "", false);
        
    }
    
    @Test
    void testSelectInstances5() throws NacosException {
        //given
        String serviceName = "service1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        //when
        client.selectInstances(serviceName, clusterList, true);
        //then
        verify(proxy, times(1)).subscribe(serviceName, Constants.DEFAULT_GROUP, "cluster1,cluster2");
    }
    
    @Test
    void testSelectInstances6() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        //when
        client.selectInstances(serviceName, groupName, clusterList, true);
        //then
        verify(proxy, times(1)).subscribe(serviceName, groupName, "cluster1,cluster2");
        
    }
    
    @Test
    void testSelectInstances7() throws NacosException {
        //given
        String serviceName = "service1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        //when
        client.selectInstances(serviceName, clusterList, true, false);
        //then
        verify(proxy, times(1)).queryInstancesOfService(serviceName, Constants.DEFAULT_GROUP, "cluster1,cluster2",
                false);
    }
    
    @Test
    void testSelectInstances8() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        //when
        client.selectInstances(serviceName, groupName, clusterList, true, false);
        //then
        verify(proxy, times(1)).queryInstancesOfService(serviceName, groupName, "cluster1,cluster2", false);
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
    void testSelectOneHealthyInstance1() throws NacosException {
        //given
        Instance healthyInstance = new Instance();
        healthyInstance.setIp("1.1.1.1");
        healthyInstance.setPort(1000);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(healthyInstance);
        ServiceInfo infoWithHealthyInstance = new ServiceInfo();
        infoWithHealthyInstance.setHosts(hosts);
        when(proxy.subscribe(anyString(), anyString(), anyString())).thenReturn(infoWithHealthyInstance);
        
        String serviceName = "service1";
        //when
        client.selectOneHealthyInstance(serviceName);
        //then
        verify(proxy, times(1)).subscribe(serviceName, Constants.DEFAULT_GROUP, "");
    }
    
    @Test
    void testSelectOneHealthyInstance2() throws NacosException {
        //given
        Instance healthyInstance = new Instance();
        healthyInstance.setIp("1.1.1.1");
        healthyInstance.setPort(1000);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(healthyInstance);
        ServiceInfo infoWithHealthyInstance = new ServiceInfo();
        infoWithHealthyInstance.setHosts(hosts);
        when(proxy.subscribe(anyString(), anyString(), anyString())).thenReturn(infoWithHealthyInstance);
        
        String serviceName = "service1";
        String groupName = "group1";
        //when
        client.selectOneHealthyInstance(serviceName, groupName);
        //then
        verify(proxy, times(1)).subscribe(serviceName, groupName, "");
    }
    
    @Test
    void testSelectOneHealthyInstance3() throws NacosException {
        //given
        Instance healthyInstance = new Instance();
        healthyInstance.setIp("1.1.1.1");
        healthyInstance.setPort(1000);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(healthyInstance);
        ServiceInfo infoWithHealthyInstance = new ServiceInfo();
        infoWithHealthyInstance.setHosts(hosts);
        when(proxy.queryInstancesOfService(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(
                infoWithHealthyInstance);
        
        String serviceName = "service1";
        //when
        client.selectOneHealthyInstance(serviceName, false);
        //then
        verify(proxy, times(1)).queryInstancesOfService(serviceName, Constants.DEFAULT_GROUP, "", false);
    }
    
    @Test
    void testSelectOneHealthyInstance4() throws NacosException {
        //given
        Instance healthyInstance = new Instance();
        healthyInstance.setIp("1.1.1.1");
        healthyInstance.setPort(1000);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(healthyInstance);
        ServiceInfo infoWithHealthyInstance = new ServiceInfo();
        infoWithHealthyInstance.setHosts(hosts);
        when(proxy.queryInstancesOfService(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(
                infoWithHealthyInstance);
        
        String serviceName = "service1";
        String groupName = "group1";
        //when
        client.selectOneHealthyInstance(serviceName, groupName, false);
        //then
        verify(proxy, times(1)).queryInstancesOfService(serviceName, groupName, "", false);
        
    }
    
    @Test
    void testSelectOneHealthyInstance5() throws NacosException {
        //given
        Instance healthyInstance = new Instance();
        healthyInstance.setIp("1.1.1.1");
        healthyInstance.setPort(1000);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(healthyInstance);
        ServiceInfo infoWithHealthyInstance = new ServiceInfo();
        infoWithHealthyInstance.setHosts(hosts);
        when(proxy.subscribe(anyString(), anyString(), anyString())).thenReturn(infoWithHealthyInstance);
        
        String serviceName = "service1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        //when
        client.selectOneHealthyInstance(serviceName, clusterList);
        //then
        verify(proxy, times(1)).subscribe(serviceName, Constants.DEFAULT_GROUP, "cluster1,cluster2");
    }
    
    @Test
    void testSelectOneHealthyInstance6() throws NacosException {
        //given
        Instance healthyInstance = new Instance();
        healthyInstance.setIp("1.1.1.1");
        healthyInstance.setPort(1000);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(healthyInstance);
        ServiceInfo infoWithHealthyInstance = new ServiceInfo();
        infoWithHealthyInstance.setHosts(hosts);
        when(proxy.subscribe(anyString(), anyString(), anyString())).thenReturn(infoWithHealthyInstance);
        
        String serviceName = "service1";
        String groupName = "group1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        //when
        client.selectOneHealthyInstance(serviceName, groupName, clusterList);
        //then
        verify(proxy, times(1)).subscribe(serviceName, groupName, "cluster1,cluster2");
        
    }
    
    @Test
    void testSelectOneHealthyInstance7() throws NacosException {
        //given
        Instance healthyInstance = new Instance();
        healthyInstance.setIp("1.1.1.1");
        healthyInstance.setPort(1000);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(healthyInstance);
        ServiceInfo infoWithHealthyInstance = new ServiceInfo();
        infoWithHealthyInstance.setHosts(hosts);
        when(proxy.queryInstancesOfService(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(
                infoWithHealthyInstance);
        
        String serviceName = "service1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        //when
        client.selectOneHealthyInstance(serviceName, clusterList, false);
        //then
        verify(proxy, times(1)).queryInstancesOfService(serviceName, Constants.DEFAULT_GROUP, "cluster1,cluster2",
                false);
    }
    
    @Test
    void testSelectOneHealthyInstance8() throws NacosException {
        //given
        Instance healthyInstance = new Instance();
        healthyInstance.setIp("1.1.1.1");
        healthyInstance.setPort(1000);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(healthyInstance);
        ServiceInfo infoWithHealthyInstance = new ServiceInfo();
        infoWithHealthyInstance.setHosts(hosts);
        when(proxy.queryInstancesOfService(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(
                infoWithHealthyInstance);
        
        String serviceName = "service1";
        String groupName = "group1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        //when
        client.selectOneHealthyInstance(serviceName, groupName, clusterList, false);
        //then
        verify(proxy, times(1)).queryInstancesOfService(serviceName, groupName, "cluster1,cluster2", false);
    }
    
    @Test
    void testSubscribe1() throws NacosException {
        //given
        String serviceName = "service1";
        EventListener listener = event -> {
        
        };
        //when
        client.subscribe(serviceName, listener);
        //then
        verify(changeNotifier, times(1)).registerListener(Constants.DEFAULT_GROUP, serviceName, "", listener);
        verify(proxy, times(1)).subscribe(serviceName, Constants.DEFAULT_GROUP, "");
    }
    
    @Test
    void testSubscribe2() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        EventListener listener = event -> {
        
        };
        //when
        client.subscribe(serviceName, groupName, listener);
        //then
        verify(changeNotifier, times(1)).registerListener(groupName, serviceName, "", listener);
        verify(proxy, times(1)).subscribe(serviceName, groupName, "");
    }
    
    @Test
    void testSubscribe3() throws NacosException {
        //given
        String serviceName = "service1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        EventListener listener = event -> {
        
        };
        //when
        client.subscribe(serviceName, clusterList, listener);
        //then
        verify(changeNotifier, times(1)).registerListener(Constants.DEFAULT_GROUP, serviceName, "cluster1,cluster2",
                listener);
        verify(proxy, times(1)).subscribe(serviceName, Constants.DEFAULT_GROUP, "cluster1,cluster2");
    }
    
    @Test
    void testSubscribe4() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        EventListener listener = event -> {
        
        };
        //when
        client.subscribe(serviceName, groupName, clusterList, listener);
        //then
        verify(changeNotifier, times(1)).registerListener(groupName, serviceName, "cluster1,cluster2", listener);
        verify(proxy, times(1)).subscribe(serviceName, groupName, "cluster1,cluster2");
    }
    
    @Test
    void testSubscribeWithNullListener() throws NacosException {
        String serviceName = "service1";
        String groupName = "group1";
        //when
        client.subscribe(serviceName, groupName, null);
        //then
        verify(changeNotifier, never()).registerListener(groupName, serviceName, "", null);
        verify(proxy, never()).subscribe(serviceName, groupName, "");
        
    }
    
    @Test
    void testUnSubscribe1() throws NacosException {
        //given
        String serviceName = "service1";
        EventListener listener = event -> {
        
        };
        client.unsubscribe(serviceName, listener);
        //then
        verify(changeNotifier, times(1)).deregisterListener(Constants.DEFAULT_GROUP, serviceName, "", listener);
        verify(proxy, times(1)).unsubscribe(serviceName, Constants.DEFAULT_GROUP, "");
    }
    
    @Test
    void testUnSubscribe2() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        EventListener listener = event -> {
        
        };
        client.unsubscribe(serviceName, groupName, listener);
        //then
        verify(changeNotifier, times(1)).deregisterListener(groupName, serviceName, "", listener);
        verify(proxy, times(1)).unsubscribe(serviceName, groupName, "");
    }
    
    @Test
    void testUnSubscribe3() throws NacosException {
        //given
        String serviceName = "service1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        EventListener listener = event -> {
        
        };
        client.unsubscribe(serviceName, clusterList, listener);
        //then
        verify(changeNotifier, times(1)).deregisterListener(Constants.DEFAULT_GROUP, serviceName, "cluster1,cluster2",
                listener);
        verify(proxy, times(1)).unsubscribe(serviceName, Constants.DEFAULT_GROUP, "cluster1,cluster2");
    }
    
    @Test
    void testUnSubscribe4() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        List<String> clusterList = Arrays.asList("cluster1", "cluster2");
        EventListener listener = event -> {
        
        };
        client.unsubscribe(serviceName, groupName, clusterList, listener);
        //then
        verify(changeNotifier, times(1)).deregisterListener(groupName, serviceName, "cluster1,cluster2", listener);
        verify(proxy, times(1)).unsubscribe(serviceName, groupName, "cluster1,cluster2");
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
}