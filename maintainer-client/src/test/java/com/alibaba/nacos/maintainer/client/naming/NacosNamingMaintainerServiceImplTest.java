/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.naming;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.maintainer.InstanceMetadataBatchResult;
import com.alibaba.nacos.api.naming.pojo.maintainer.MetricsInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceView;
import com.alibaba.nacos.api.naming.pojo.maintainer.SubscriberInfo;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.api.selector.Selector;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.maintainer.client.core.AbstractCoreMaintainerService;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NacosNamingMaintainerServiceImplTest {
    
    @Mock
    private ClientHttpProxy clientHttpProxy;
    
    private NamingMaintainerService nacosNamingMaintainerService;
    
    @BeforeEach
    void setUp() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", "localhost:8848");
        nacosNamingMaintainerService = new NacosNamingMaintainerServiceImpl(properties);
        Field clientHttpProxyField = AbstractCoreMaintainerService.class.getDeclaredField("clientHttpProxy");
        clientHttpProxyField.setAccessible(true);
        clientHttpProxyField.set(nacosNamingMaintainerService, clientHttpProxy);
    }
    
    @Test
    void testCreateService() throws Exception {
        // Arrange
        String serviceName = "testService";
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.createService(serviceName);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testUpdateService() throws Exception {
        // Arrange
        String serviceName = "testService";
        Map<String, String> metadata = Collections.singletonMap("key", "value");
        float protectThreshold = 0.5f;
        Selector selector = new NoneSelector();
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.updateService(serviceName, metadata, protectThreshold, selector);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testRemoveService() throws Exception {
        // Arrange
        String serviceName = "testService";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.removeService(serviceName);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetServiceDetail() throws Exception {
        // Arrange
        String serviceName = "testService";
        
        ServiceDetailInfo expectedDetail = new ServiceDetailInfo();
        expectedDetail.setServiceName(serviceName);
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedDetail)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        ServiceDetailInfo result = nacosNamingMaintainerService.getServiceDetail(serviceName);
        
        // Assert
        assertNotNull(result);
        assertEquals(serviceName, result.getServiceName());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testListServices() throws Exception {
        List<ServiceView> expected = new ArrayList<>();
        expected.add(new ServiceView());
        expected.get(0).setName("testService");
        expected.get(0).setGroupName("testGroup");
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expected)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        List<ServiceView> result = nacosNamingMaintainerService.listServices("testNamespace");
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("testService", result.get(0).getName());
        assertEquals("testGroup", result.get(0).getGroupName());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testListServicesWithDetail() throws Exception {
        // Arrange
        String namespaceId = "testNamespace";
        List<ServiceDetailInfo> expected = new ArrayList<>();
        expected.add(new ServiceDetailInfo());
        expected.get(0).setNamespaceId(namespaceId);
        expected.get(0).setServiceName("testService");
        expected.get(0).setGroupName("testGroup");
        expected.get(0).setMetadata(Collections.singletonMap("key", "value"));
        expected.get(0).setClusterMap(Collections.emptyMap());
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expected)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        List<ServiceDetailInfo> result = nacosNamingMaintainerService.listServicesWithDetail(namespaceId);
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(namespaceId, result.get(0).getNamespaceId());
        assertEquals("testService", result.get(0).getServiceName());
        assertEquals("testGroup", result.get(0).getGroupName());
        assertEquals(Collections.singletonMap("key", "value"), result.get(0).getMetadata());
        assertEquals(Collections.emptyMap(), result.get(0).getClusterMap());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetSubscribers() throws Exception {
        // Arrange
        final String serviceName = "testService";
        
        Page<SubscriberInfo> expectedPage = new Page<>();
        expectedPage.setPageNumber(1);
        expectedPage.setPagesAvailable(1);
        expectedPage.setTotalCount(1);
        expectedPage.setPageItems(Collections.singletonList(new SubscriberInfo()));
        expectedPage.getPageItems().get(0).setServiceName(serviceName);
        Result<Page<SubscriberInfo>> expectedResult = Result.success(expectedPage);
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedResult));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Page<SubscriberInfo> result = nacosNamingMaintainerService.getSubscribers(serviceName);
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.getPageNumber());
        assertEquals(1, result.getPagesAvailable());
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getPageItems().size());
        assertEquals(serviceName, result.getPageItems().get(0).getServiceName());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testListSelectorTypes() throws Exception {
        // Arrange
        List<String> expectedList = Arrays.asList("type1", "type2");
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedList));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        List<String> result = nacosNamingMaintainerService.listSelectorTypes();
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetMetrics() throws Exception {
        // Arrange
        boolean onlyStatus = true;
        
        MetricsInfo expectedMetrics = new MetricsInfo();
        Result<MetricsInfo> expectedResult = Result.success(expectedMetrics);
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedResult));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        MetricsInfo result = nacosNamingMaintainerService.getMetrics(onlyStatus);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testSetLogLevel() throws Exception {
        // Arrange
        String logName = "testLog";
        String logLevel = "INFO";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        nacosNamingMaintainerService.setLogLevel(logName, logLevel);
        
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testRegisterInstance() throws Exception {
        // Arrange
        String serviceName = "testService";
        String ip = "127.0.0.1";
        int port = 8080;
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.registerInstance(serviceName, ip, port);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testDeregisterInstance() throws Exception {
        // Arrange
        String namespaceId = "testNamespace";
        String groupName = "testGroup";
        String serviceName = "testService";
        String clusterName = "testCluster";
        String ip = "127.0.0.1";
        int port = 8080;
        String weight = "1.0";
        boolean healthy = true;
        boolean enabled = true;
        String ephemeral = "true";
        String metadata = "testMetadata";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.deregisterInstance(namespaceId, groupName, serviceName,
                clusterName, ip, port, weight, healthy, enabled, ephemeral, metadata);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testUpdateInstance() throws Exception {
        // Arrange
        String namespaceId = "testNamespace";
        String groupName = "testGroup";
        String serviceName = "testService";
        String clusterName = "testCluster";
        String ip = "127.0.0.1";
        int port = 8080;
        double weight = 1.0D;
        boolean healthy = true;
        boolean enabled = true;
        boolean ephemeral = true;
        String metadata = "testMetadata";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.updateInstance(namespaceId, groupName, serviceName, clusterName,
                ip, port, weight, healthy, enabled, ephemeral, metadata);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testBatchUpdateInstanceMetadata() throws Exception {
        // Arrange
        final String namespaceId = "testNamespace";
        final String groupName = "testGroup";
        final String serviceName = "testService";
        final String instance = "testInstance";
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key", "value");
        String consistencyType = "testType";
        
        InstanceMetadataBatchResult expectedVo = new InstanceMetadataBatchResult();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        Result<InstanceMetadataBatchResult> expectedResult = Result.success(expectedVo);
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedResult));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        InstanceMetadataBatchResult result = nacosNamingMaintainerService.batchUpdateInstanceMetadata(namespaceId,
                groupName, serviceName, instance, metadata, consistencyType);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testBatchDeleteInstanceMetadata() throws Exception {
        // Arrange
        final String namespaceId = "testNamespace";
        final String groupName = "testGroup";
        final String serviceName = "testService";
        final String instance = "testInstance";
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key", "value");
        String consistencyType = "testType";
        
        InstanceMetadataBatchResult expectedVo = new InstanceMetadataBatchResult();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        Result<InstanceMetadataBatchResult> expectedResult = Result.success(expectedVo);
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedResult));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        InstanceMetadataBatchResult result = nacosNamingMaintainerService.batchDeleteInstanceMetadata(namespaceId,
                groupName, serviceName, instance, metadata, consistencyType);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testPartialUpdateInstance() throws Exception {
        // Arrange
        String namespaceId = "testNamespace";
        String serviceName = "testService";
        String clusterName = "testCluster";
        int ip = 127;
        int port = 8080;
        double weight = 1.0;
        boolean enabled = true;
        String metadata = "testMetadata";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.partialUpdateInstance(namespaceId, serviceName, clusterName, ip,
                port, weight, enabled, metadata);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testListInstances() throws Exception {
        // Arrange
        
        List<Instance> expectedInfo = new ArrayList<>();
        expectedInfo.add(new Instance());
        expectedInfo.get(0).setIp("11.1.1.1");
        expectedInfo.get(0).setPort(8848);
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(Result.success(expectedInfo)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String namespaceId = "testNamespace";
        String groupName = "testGroup";
        String serviceName = "testService";
        String clusterName = "testCluster";
        boolean healthyOnly = true;
        List<Instance> result = nacosNamingMaintainerService.listInstances(namespaceId, groupName, serviceName,
                clusterName, healthyOnly);
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("11.1.1.1", result.get(0).getIp());
        assertEquals(8848, result.get(0).getPort());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetInstanceDetail() throws Exception {
        // Arrange
        String namespaceId = "testNamespace";
        String groupName = "testGroup";
        String serviceName = "testService";
        String clusterName = "testCluster";
        String ip = "127.0.0.1";
        int port = 8080;
        
        Instance expectedVo = new Instance();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        Result<Instance> expectedResult = Result.success(expectedVo);
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedResult));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Instance result = nacosNamingMaintainerService.getInstanceDetail(namespaceId, groupName, serviceName,
                clusterName, ip, port);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testUpdateInstanceHealthStatus() throws Exception {
        // Arrange
        String namespaceId = "testNamespace";
        String groupName = "testGroup";
        String serviceName = "testService";
        String clusterName = "testCluster";
        String metadata = "testMetadata";
        boolean ephemeral = true;
        float protectThreshold = 0.5f;
        String selector = "testSelector";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.updateInstanceHealthStatus(namespaceId, groupName, serviceName,
                clusterName, metadata, ephemeral, protectThreshold, selector);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetHealthCheckers() throws Exception {
        // Arrange
        Map<String, AbstractHealthChecker> expectedCheckers = new HashMap<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedCheckers));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Map<String, AbstractHealthChecker> result = nacosNamingMaintainerService.getHealthCheckers();
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testUpdateCluster() throws Exception {
        // Arrange
        String namespaceId = "testNamespace";
        String groupName = "testGroup";
        String serviceName = "testService";
        String clusterName = "testCluster";
        Integer checkPort = 8080;
        Boolean useInstancePort4Check = true;
        String healthChecker = "testChecker";
        Map<String, String> metadata = new HashMap<>();
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.updateCluster(namespaceId, groupName, serviceName, clusterName,
                checkPort, useInstancePort4Check, healthChecker, metadata);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetClientList() throws Exception {
        // Arrange
        List<String> expectedList = Arrays.asList("client1", "client2");
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedList));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        List<String> result = nacosNamingMaintainerService.getClientList();
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetClientDetail() throws Exception {
        // Arrange
        String clientId = "testClient";
        
        ObjectNode expectedNode = new ObjectMapper().createObjectNode();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedNode));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        ObjectNode result = nacosNamingMaintainerService.getClientDetail(clientId);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetPublishedServiceList() throws Exception {
        // Arrange
        String clientId = "testClient";
        
        List<ObjectNode> expectedList = new ArrayList<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedList));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        List<ObjectNode> result = nacosNamingMaintainerService.getPublishedServiceList(clientId);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetSubscribeServiceList() throws Exception {
        // Arrange
        String clientId = "testClient";
        
        List<ObjectNode> expectedList = new ArrayList<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedList));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        List<ObjectNode> result = nacosNamingMaintainerService.getSubscribeServiceList(clientId);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetPublishedClientList() throws Exception {
        // Arrange
        String namespaceId = "testNamespace";
        String groupName = "testGroup";
        String serviceName = "testService";
        boolean ephemeral = true;
        String ip = "127.0.0.1";
        Integer port = 8080;
        
        List<ObjectNode> expectedList = new ArrayList<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedList));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        List<ObjectNode> result = nacosNamingMaintainerService.getPublishedClientList(namespaceId, groupName,
                serviceName, ephemeral, ip, port);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetSubscribeClientList() throws Exception {
        // Arrange
        String namespaceId = "testNamespace";
        String groupName = "testGroup";
        String serviceName = "testService";
        boolean ephemeral = true;
        String ip = "127.0.0.1";
        Integer port = 8080;
        
        List<ObjectNode> expectedList = new ArrayList<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedList));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        List<ObjectNode> result = nacosNamingMaintainerService.getSubscribeClientList(namespaceId, groupName,
                serviceName, ephemeral, ip, port);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetResponsibleServerForClient() throws Exception {
        // Arrange
        String ip = "127.0.0.1";
        String port = "8080";
        
        ObjectNode expectedNode = new ObjectMapper().createObjectNode();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedNode));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        ObjectNode result = nacosNamingMaintainerService.getResponsibleServerForClient(ip, port);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
}