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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.consistency.ConsistencyService;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.ServiceManager.ServiceChecksum;
import com.alibaba.nacos.naming.misc.Message;
import com.alibaba.nacos.naming.misc.Synchronizer;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.alibaba.nacos.naming.misc.UtilsAndCommons.UPDATE_INSTANCE_METADATA_ACTION_REMOVE;
import static com.alibaba.nacos.naming.misc.UtilsAndCommons.UPDATE_INSTANCE_METADATA_ACTION_UPDATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServiceManagerTest extends BaseTest {
    
    private ServiceManager serviceManager;
    
    @Mock
    private ConsistencyService consistencyService;
    
    @Mock
    private Synchronizer synchronizer;
    
    @Mock
    private ServerMemberManager serverMemberManager;
    
    private Service service;
    
    private Cluster cluster;
    
    private Instance instance;
    
    private Instance instance2;
    
    private List<String> serviceNames;
    
    @Before
    public void before() {
        super.before();
        serviceManager = new ServiceManager(switchDomain, distroMapper, serverMemberManager, pushService, peerSet);
        ReflectionTestUtils.setField(serviceManager, "consistencyService", consistencyService);
        ReflectionTestUtils.setField(serviceManager, "synchronizer", synchronizer);
        mockInjectSwitchDomain();
        mockInjectDistroMapper();
        mockService();
        mockCluster();
        mockInstance();
        mockServiceName();
    }
    
    private void mockService() {
        service = new Service(TEST_SERVICE_NAME);
        service.setNamespaceId(TEST_NAMESPACE);
    }
    
    private void mockCluster() {
        cluster = new Cluster(TEST_CLUSTER_NAME, service);
    }
    
    private void mockInstance() {
        instance = new Instance("1.1.1.1", 1, TEST_CLUSTER_NAME);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        instance.setMetadata(metadata);
        instance2 = new Instance("2.2.2.2", 2);
    }
    
    private void mockServiceName() {
        serviceNames = new ArrayList<>(5);
        for (int i = 0; i < 32; i++) {
            serviceNames.add(String.valueOf(i));
        }
    }
    
    @Test
    public void testGetAllNamespaces() throws NacosException {
        assertTrue(serviceManager.getAllNamespaces().isEmpty());
        serviceManager.createEmptyService(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        assertFalse(serviceManager.getAllNamespaces().isEmpty());
        assertEquals(1, serviceManager.getAllNamespaces().size());
        assertEquals(TEST_NAMESPACE, serviceManager.getAllNamespaces().iterator().next());
    }
    
    @Test
    public void testGetAllServiceNames() throws NacosException {
        assertTrue(serviceManager.getAllServiceNames().isEmpty());
        serviceManager.createEmptyService(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        assertFalse(serviceManager.getAllServiceNames().isEmpty());
        assertEquals(1, serviceManager.getAllServiceNames().size());
        assertEquals(1, serviceManager.getAllServiceNames(TEST_NAMESPACE).size());
        assertEquals(TEST_SERVICE_NAME, serviceManager.getAllServiceNames(TEST_NAMESPACE).iterator().next());
    }
    
    @Test
    public void testGetAllServiceNamesOrder() throws NacosException {
        assertTrue(serviceManager.getAllServiceNames().isEmpty());
        for (String serviceName : serviceNames) {
            serviceManager.createEmptyService(TEST_NAMESPACE, serviceName, true);
        }
        assertFalse(serviceManager.getAllServiceNames().isEmpty());
        assertEquals(1, serviceManager.getAllServiceNames().size());
        assertEquals(serviceNames.size(), serviceManager.getAllServiceNames(TEST_NAMESPACE).size());
        Collections.sort(serviceNames);
        Iterator<String> iterator = serviceManager.getAllServiceNames(TEST_NAMESPACE).iterator();
        int index = 0;
        while (iterator.hasNext()) {
            String next = iterator.next();
            assertEquals(next, serviceNames.get(index));
            index++;
        }
    }
    
    @Test
    public void testGetAllServiceNameList() throws NacosException {
        assertTrue(serviceManager.getAllServiceNameList(TEST_NAMESPACE).isEmpty());
        serviceManager.createEmptyService(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        assertFalse(serviceManager.getAllServiceNameList(TEST_NAMESPACE).isEmpty());
        assertEquals(1, serviceManager.getAllServiceNameList(TEST_NAMESPACE).size());
        assertEquals(TEST_SERVICE_NAME, serviceManager.getAllServiceNameList(TEST_NAMESPACE).get(0));
    }
    
    @Test
    public void testGetAllServiceNameListOrder() throws NacosException {
        assertTrue(serviceManager.getAllServiceNameList(TEST_NAMESPACE).isEmpty());
        for (String serviceName : serviceNames) {
            serviceManager.createEmptyService(TEST_NAMESPACE, serviceName, true);
        }
        assertFalse(serviceManager.getAllServiceNameList(TEST_NAMESPACE).isEmpty());
        assertEquals(serviceNames.size(), serviceManager.getAllServiceNameList(TEST_NAMESPACE).size());
        List<String> allServiceNameList = serviceManager.getAllServiceNameList(TEST_NAMESPACE);
        Collections.sort(serviceNames);
        for (int i = 0; i < allServiceNameList.size(); i++) {
            assertEquals(allServiceNameList.get(i), serviceNames.get(i));
        }
    }
    
    @Test
    public void testGetResponsibleServices() throws NacosException {
        when(distroMapper.responsible(TEST_SERVICE_NAME)).thenReturn(true);
        assertEquals(0, serviceManager.getResponsibleServiceCount());
        serviceManager.createEmptyService(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        assertEquals(1, serviceManager.getResponsibleServiceCount());
        assertEquals(TEST_SERVICE_NAME,
                serviceManager.getResponsibleServices().get(TEST_NAMESPACE).iterator().next().getName());
    }
    
    @Test
    public void getResponsibleInstanceCount() throws NacosException {
        when(distroMapper.responsible(TEST_SERVICE_NAME)).thenReturn(true);
        assertEquals(0, serviceManager.getResponsibleInstanceCount());
        serviceManager.createEmptyService(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        Service service = serviceManager.getService(TEST_NAMESPACE, TEST_SERVICE_NAME);
        service.addCluster(cluster);
        ((Set<Instance>) ReflectionTestUtils.getField(cluster, "ephemeralInstances")).add(instance);
        assertEquals(1, serviceManager.getResponsibleInstanceCount());
    }
    
    @Test
    public void testCreateEmptyServiceForEphemeral() throws NacosException {
        assertFalse(serviceManager.containService(TEST_NAMESPACE, TEST_SERVICE_NAME));
        assertEquals(0, serviceManager.getServiceCount());
        serviceManager.createServiceIfAbsent(TEST_NAMESPACE, TEST_SERVICE_NAME, true,
                new Cluster(TEST_CLUSTER_NAME, service));
        assertTrue(serviceManager.containService(TEST_NAMESPACE, TEST_SERVICE_NAME));
        assertEquals(1, serviceManager.getServiceCount());
        verify(consistencyService).listen(eq(KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true)),
                any(Service.class));
        verify(consistencyService).listen(eq(KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, false)),
                any(Service.class));
        verify(consistencyService, never())
                .put(eq(KeyBuilder.buildServiceMetaKey(TEST_NAMESPACE, TEST_SERVICE_NAME)), any(Service.class));
    }
    
    @Test
    public void testCreateEmptyServiceForPersistent() throws NacosException {
        assertFalse(serviceManager.containService(TEST_NAMESPACE, TEST_SERVICE_NAME));
        assertEquals(0, serviceManager.getServiceCount());
        serviceManager.createServiceIfAbsent(TEST_NAMESPACE, TEST_SERVICE_NAME, false,
                new Cluster(TEST_CLUSTER_NAME, service));
        assertTrue(serviceManager.containService(TEST_NAMESPACE, TEST_SERVICE_NAME));
        assertEquals(1, serviceManager.getServiceCount());
        verify(consistencyService).listen(eq(KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true)),
                any(Service.class));
        verify(consistencyService).listen(eq(KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, false)),
                any(Service.class));
        verify(consistencyService)
                .put(eq(KeyBuilder.buildServiceMetaKey(TEST_NAMESPACE, TEST_SERVICE_NAME)), any(Service.class));
    }
    
    @Test
    public void testEasyRemoveServiceSuccessfully() throws Exception {
        serviceManager.createEmptyService(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        serviceManager.easyRemoveService(TEST_NAMESPACE, TEST_SERVICE_NAME);
        verify(consistencyService).remove(KeyBuilder.buildServiceMetaKey(TEST_NAMESPACE, TEST_SERVICE_NAME));
    }
    
    @Test
    public void testEasyRemoveServiceFailed() throws Exception {
        expectedException.expect(NacosException.class);
        expectedException.expectMessage("specified service not exist, serviceName : " + TEST_SERVICE_NAME);
        serviceManager.easyRemoveService(TEST_NAMESPACE, TEST_SERVICE_NAME);
    }
    
    @Test
    public void testRegisterInstance() throws NacosException {
        assertEquals(0, serviceManager.getInstanceCount());
        serviceManager.registerInstance(TEST_NAMESPACE, TEST_SERVICE_NAME, instance);
        String instanceListKey = KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        verify(consistencyService).put(eq(instanceListKey), any(Instances.class));
    }
    
    @Test
    public void testUpdateInstance() throws NacosException {
        serviceManager.createEmptyService(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        Service service = serviceManager.getService(TEST_NAMESPACE, TEST_SERVICE_NAME);
        service.addCluster(cluster);
        ((Set<Instance>) ReflectionTestUtils.getField(cluster, "ephemeralInstances")).add(instance);
        serviceManager.updateInstance(TEST_NAMESPACE, TEST_SERVICE_NAME, instance);
        String instanceListKey = KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        verify(consistencyService).put(eq(instanceListKey), any(Instances.class));
    }
    
    @Test
    public void testUpdateMetadata() throws NacosException {
        
        serviceManager.createEmptyService(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        
        List<Instance> instanceList = new LinkedList<>();
        Datum datam = new Datum();
        datam.key = KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        Instances instances = new Instances();
        instanceList.add(instance);
        instanceList.add(instance2);
        instances.setInstanceList(instanceList);
        datam.value = instances;
        when(consistencyService.get(KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true)))
                .thenReturn(datam);
        
        Instance updateMetadataInstance = new Instance();
        updateMetadataInstance.setIp(instance.getIp());
        updateMetadataInstance.setPort(instance.getPort());
        updateMetadataInstance.setClusterName(cluster.getName());
        updateMetadataInstance.setEphemeral(instance.isEphemeral());
        
        Map<String, String> updateMetadata = new HashMap<>(16);
        updateMetadata.put("key1", "new-value1");
        updateMetadata.put("key2", "value2");
        updateMetadataInstance.setMetadata(updateMetadata);
        
        //all=false, update input instances
        serviceManager
                .updateMetadata(TEST_NAMESPACE, TEST_SERVICE_NAME, true, UPDATE_INSTANCE_METADATA_ACTION_UPDATE, false,
                        Lists.newArrayList(updateMetadataInstance), updateMetadata);
        
        assertEquals(instance.getMetadata().get("key1"), "new-value1");
        assertEquals(instance.getMetadata().get("key2"), "value2");
        
        //all=true, update all instances
        serviceManager
                .updateMetadata(TEST_NAMESPACE, TEST_SERVICE_NAME, true, UPDATE_INSTANCE_METADATA_ACTION_UPDATE, true,
                        null, updateMetadata);
        
        assertEquals(instance2.getMetadata().get("key1"), "new-value1");
        assertEquals(instance2.getMetadata().get("key2"), "value2");
        
        Instance deleteMetadataInstance = new Instance();
        deleteMetadataInstance.setIp(instance.getIp());
        deleteMetadataInstance.setPort(instance.getPort());
        deleteMetadataInstance.setClusterName(cluster.getName());
        deleteMetadataInstance.setEphemeral(instance.isEphemeral());
        Map<String, String> deleteMetadata = new HashMap<>(16);
        deleteMetadata.put("key2", null);
        deleteMetadata.put("key3", null);
        updateMetadataInstance.setMetadata(deleteMetadata);
        
        serviceManager
                .updateMetadata(TEST_NAMESPACE, TEST_SERVICE_NAME, true, UPDATE_INSTANCE_METADATA_ACTION_REMOVE, false,
                        Lists.newArrayList(deleteMetadataInstance), deleteMetadata);
        
        assertEquals(instance.getMetadata().get("key1"), "new-value1");
        assertNull(instance.getMetadata().get("key2"));
        assertNull(instance.getMetadata().get("key3"));
        
        serviceManager
                .updateMetadata(TEST_NAMESPACE, TEST_SERVICE_NAME, true, UPDATE_INSTANCE_METADATA_ACTION_REMOVE, true,
                        null, deleteMetadata);
        
        assertEquals(instance2.getMetadata().get("key1"), "new-value1");
        assertNull(instance2.getMetadata().get("key2"));
        assertNull(instance2.getMetadata().get("key3"));
    }
    
    @Test
    public void testRemoveInstance() throws NacosException {
        serviceManager.createEmptyService(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        serviceManager.removeInstance(TEST_NAMESPACE, TEST_SERVICE_NAME, true, instance);
        String instanceListKey = KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        verify(consistencyService).put(eq(instanceListKey), any(Instances.class));
    }
    
    @Test
    public void testGetInstance() throws NacosException {
        assertNull(serviceManager.getInstance(TEST_NAMESPACE, TEST_SERVICE_NAME, TEST_CLUSTER_NAME, "1.1.1.1", 1));
        serviceManager.createEmptyService(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        assertNull(serviceManager.getInstance(TEST_NAMESPACE, TEST_SERVICE_NAME, TEST_CLUSTER_NAME, "1.1.1.1", 1));
        Service service = serviceManager.getService(TEST_NAMESPACE, TEST_SERVICE_NAME);
        service.addCluster(cluster);
        ((Set<Instance>) ReflectionTestUtils.getField(cluster, "ephemeralInstances")).add(instance);
        assertEquals(instance,
                serviceManager.getInstance(TEST_NAMESPACE, TEST_SERVICE_NAME, TEST_CLUSTER_NAME, "1.1.1.1", 1));
        assertNull(serviceManager.getInstance(TEST_NAMESPACE, TEST_SERVICE_NAME, TEST_CLUSTER_NAME, "2.2.2.2", 2));
    }
    
    @Test
    public void testUpdateIpAddresses() throws Exception {
        List<Instance> instanceList = serviceManager
                .updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD, true, instance);
        Assert.assertEquals(1, instanceList.size());
        Assert.assertEquals(instance, instanceList.get(0));
        Assert.assertEquals(1, service.getClusterMap().size());
        Assert.assertEquals(new Cluster(instance.getClusterName(), service),
                service.getClusterMap().get(TEST_CLUSTER_NAME));
        
        Datum datam = new Datum();
        datam.key = KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        Instances instances = new Instances();
        instanceList.add(instance2);
        instances.setInstanceList(instanceList);
        datam.value = instances;
        when(consistencyService.get(KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true)))
                .thenReturn(datam);
        service.getClusterMap().get(TEST_CLUSTER_NAME).updateIps(instanceList, true);
        instanceList = serviceManager
                .updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_REMOVE, true, instance);
        Assert.assertEquals(1, instanceList.size());
        Assert.assertEquals(instance2, instanceList.get(0));
        Assert.assertEquals(1, service.getClusterMap().size());
        Assert.assertEquals(new Cluster(instance.getClusterName(), service),
                service.getClusterMap().get(TEST_CLUSTER_NAME));
    }
    
    @Test
    public void testUpdateIpAddressesNoInstance() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException
                .expectMessage(String.format("ip list can not be empty, service: %s, ip list: []", TEST_SERVICE_NAME));
        serviceManager.updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD, true);
    }
    
    @Test
    public void testSearchServices() throws NacosException {
        serviceManager.createEmptyService(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        List<Service> actual = serviceManager
                .searchServices(TEST_NAMESPACE, Constants.ANY_PATTERN + TEST_SERVICE_NAME + Constants.ANY_PATTERN);
        assertEquals(1, actual.size());
        assertEquals(TEST_SERVICE_NAME, actual.get(0).getName());
    }
    
    @Test
    public void testGetPagedService() throws NacosException {
        serviceManager.createEmptyService(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        Service service = serviceManager.getService(TEST_NAMESPACE, TEST_SERVICE_NAME);
        service.addCluster(cluster);
        ((Set<Instance>) ReflectionTestUtils.getField(cluster, "ephemeralInstances")).add(instance);
        List<Service> actualServices = new ArrayList<>(8);
        int actualSize = serviceManager
                .getPagedService(TEST_NAMESPACE, 0, 10, StringUtils.EMPTY, "1.1.1.1:1", actualServices, true);
        assertEquals(1, actualSize);
        assertEquals(TEST_SERVICE_NAME, actualServices.get(0).getName());
    }
    
    @Test
    public void testSnowflakeInstanceId() throws Exception {
        Map<String, String> metaData = Maps.newHashMap();
        metaData.put(PreservedMetadataKeys.INSTANCE_ID_GENERATOR, Constants.SNOWFLAKE_INSTANCE_ID_GENERATOR);
        
        instance.setMetadata(metaData);
        
        instance2.setClusterName(TEST_CLUSTER_NAME);
        instance2.setMetadata(metaData);
        
        List<Instance> instanceList = serviceManager
                .updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD, true, instance, instance2);
        Assert.assertNotNull(instanceList);
        Assert.assertEquals(2, instanceList.size());
        int instanceId1 = Integer.parseInt(instance.getInstanceId());
        int instanceId2 = Integer.parseInt(instance2.getInstanceId());
        Assert.assertNotEquals(instanceId1, instanceId2);
    }
    
    @Test
    public void testUpdatedHealthStatus() {
        String namespaceId = "namespaceId";
        String serviceName = "testService";
        String serverIp = "127.0.0.1";
        String example = "{\"ips\":[\"127.0.0.1:8848_true\"]}";
        Message message = new Message();
        message.setData(example);
        when(synchronizer.get(serverIp, UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName)))
                .thenReturn(message);
        serviceManager.updatedHealthStatus(namespaceId, serviceName, serverIp);
    }
    
    @Test
    public void testSerializeServiceChecksum() {
        ServiceChecksum checksum = new ServiceChecksum();
        checksum.addItem("test", "1234567890");
        String actual = JacksonUtils.toJson(checksum);
        assertTrue(actual.contains("\"namespaceId\":\"public\""));
        assertTrue(actual.contains("\"serviceName2Checksum\":{\"test\":\"1234567890\"}"));
    }
    
    @Test
    public void testCheckServiceIsNull() throws NacosException {
        serviceManager.createEmptyService(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        String serviceName = "order-service";
        Service service = serviceManager.getService(TEST_NAMESPACE, serviceName);
        serviceManager.checkServiceIsNull(service, TEST_NAMESPACE, serviceName);
    }
}
