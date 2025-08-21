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

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientPublisherInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientServiceInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientSubscriberInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientSummaryInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        Page<ServiceView> expected = new Page<>();
        expected.getPageItems().add(new ServiceView());
        expected.getPageItems().get(0).setName("testService");
        expected.getPageItems().get(0).setGroupName("testGroup");
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expected)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Page<ServiceView> result = nacosNamingMaintainerService.listServices("testNamespace");
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.getPageItems().size());
        assertEquals("testService", result.getPageItems().get(0).getName());
        assertEquals("testGroup", result.getPageItems().get(0).getGroupName());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testListServicesWithDetail() throws Exception {
        // Arrange
        Page<ServiceDetailInfo> expected = new Page<>();
        List<ServiceDetailInfo> expectedItem = new ArrayList<>();
        expected.setPageItems(expectedItem);
        expected.setTotalCount(1);
        expectedItem.add(new ServiceDetailInfo());
        expectedItem.get(0).setNamespaceId("testNamespace");
        expectedItem.get(0).setServiceName("testService");
        expectedItem.get(0).setGroupName("testGroup");
        expectedItem.get(0).setMetadata(Collections.singletonMap("key", "value"));
        expectedItem.get(0).setClusterMap(Collections.emptyMap());
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expected)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Page<ServiceDetailInfo> result = nacosNamingMaintainerService.listServicesWithDetail("testNamespace");
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.getPageItems().size());
        assertEquals("testNamespace", result.getPageItems().get(0).getNamespaceId());
        assertEquals("testService", result.getPageItems().get(0).getServiceName());
        assertEquals("testGroup", result.getPageItems().get(0).getGroupName());
        assertEquals(Collections.singletonMap("key", "value"), result.getPageItems().get(0).getMetadata());
        assertEquals(Collections.emptyMap(), result.getPageItems().get(0).getClusterMap());
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
        String serviceName = "testService";
        String ip = "127.0.0.1";
        int port = 8080;
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.deregisterInstance(serviceName, ip, port);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testRegisterInstanceWithCluster() throws Exception {
        String serviceName = "testService";
        String ip = "127.0.0.1";
        int port = 8080;
        String clusterName = "testCluster";
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        String result = nacosNamingMaintainerService.registerInstance(serviceName, ip, port, clusterName);
        
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testDeregisterInstanceWithCluster() throws Exception {
        String serviceName = "testService";
        String ip = "127.0.0.1";
        int port = 8080;
        String clusterName = "testCluster";
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        String result = nacosNamingMaintainerService.deregisterInstance(serviceName, ip, port, clusterName);
        
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testRegisterInstanceWithEphemeral() throws Exception {
        Instance instance = new Instance();
        instance.setEphemeral(true);
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        String result = nacosNamingMaintainerService.registerInstance("testService", instance);
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testDeregisterInstanceWithEphemeral() throws Exception {
        Instance instance = new Instance();
        instance.setEphemeral(true);
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        String result = nacosNamingMaintainerService.deregisterInstance("testService", instance);
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testUpdateInstance() throws Exception {
        // Arrange
        final String service = "testService";
        String ip = "127.0.0.1";
        int port = 8080;
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        instance.setMetadata(Collections.singletonMap("key", "value"));
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.updateInstance(service, instance);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testBatchUpdateInstanceMetadata() throws Exception {
        // Arrange
        Service service = new Service();
        service.setName("testService");
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(8848);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key", "value");
        
        InstanceMetadataBatchResult expectedVo = new InstanceMetadataBatchResult();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        Result<InstanceMetadataBatchResult> expectedResult = Result.success(expectedVo);
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedResult));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        InstanceMetadataBatchResult result = nacosNamingMaintainerService.batchUpdateInstanceMetadata(service,
                Collections.singletonList(instance), metadata);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testBatchUpdateInstanceMetadataWithoutNewMeta() throws Exception {
        Service service = new Service();
        service.setName("testService");
        assertThrows(NacosApiException.class,
                () -> nacosNamingMaintainerService.batchUpdateInstanceMetadata(service, null, null),
                "Parameter `newMetadata` can't be null");
    }
    
    @Test
    void testBatchUpdateInstanceMetadataWithoutInstance() throws Exception {
        Service service = new Service();
        service.setName("testService");
        InstanceMetadataBatchResult result = nacosNamingMaintainerService.batchUpdateInstanceMetadata(service,
                Collections.emptyList(), new HashMap<>());
        assertTrue(result.getUpdated().isEmpty());
    }
    
    @Test
    void testBatchDeleteInstanceMetadata() throws Exception {
        // Arrange
        Service service = new Service();
        service.setName("testService");
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(8848);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key", "value");
        
        InstanceMetadataBatchResult expectedVo = new InstanceMetadataBatchResult();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        Result<InstanceMetadataBatchResult> expectedResult = Result.success(expectedVo);
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedResult));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        InstanceMetadataBatchResult result = nacosNamingMaintainerService.batchDeleteInstanceMetadata(service,
                Collections.singletonList(instance), metadata);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testBatchDeleteInstanceMetadataWithoutNewMeta() throws Exception {
        Service service = new Service();
        service.setName("testService");
        assertThrows(NacosApiException.class,
                () -> nacosNamingMaintainerService.batchDeleteInstanceMetadata(service, null, null),
                "Parameter `newMetadata` can't be null");
    }
    
    @Test
    void testBatchDeleteInstanceMetadataWithoutInstance() throws Exception {
        Service service = new Service();
        service.setName("testService");
        InstanceMetadataBatchResult result = nacosNamingMaintainerService.batchDeleteInstanceMetadata(service,
                Collections.emptyList(), new HashMap<>());
        assertTrue(result.getUpdated().isEmpty());
    }
    
    @Test
    void testPartialUpdateInstance() throws Exception {
        // Arrange
        Service service = new Service();
        service.setName("testService");
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(8848);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key", "value");
        instance.setMetadata(metadata);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.partialUpdateInstance(service, instance);
        
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
        String serviceName = "testService";
        boolean healthyOnly = true;
        List<Instance> result = nacosNamingMaintainerService.listInstances(serviceName, "", healthyOnly);
        
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
        String serviceName = "testService";
        String ip = "127.0.0.1";
        int port = 8080;
        
        Instance expectedVo = new Instance();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        Result<Instance> expectedResult = Result.success(expectedVo);
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedResult));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Instance result = nacosNamingMaintainerService.getInstanceDetail(serviceName, ip, port);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetInstanceDetailWithClusterName() throws Exception {
        // Arrange
        String serviceName = "testService";
        String ip = "127.0.0.1";
        int port = 8080;
        String clusterName = "testCluster";
        
        Instance expectedVo = new Instance();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        Result<Instance> expectedResult = Result.success(expectedVo);
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedResult));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Instance result = nacosNamingMaintainerService.getInstanceDetail(serviceName, ip, port, clusterName);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testUpdateInstanceHealthStatus() throws Exception {
        // Arrange
        Service service = new Service();
        service.setName("testService");
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(8848);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.updateInstanceHealthStatus(service, instance);
        
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
        String serviceName = "testService";
        String clusterName = "testCluster";
        final int checkPort = 8080;
        final Map<String, String> metadata = new HashMap<>();
        Service service = new Service();
        service.setName(serviceName);
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setClusterName(clusterName);
        clusterInfo.setUseInstancePortForCheck(true);
        clusterInfo.setHealthyCheckPort(checkPort);
        clusterInfo.setMetadata(metadata);
        clusterInfo.setHealthChecker(new AbstractHealthChecker.None());
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.updateCluster(service, clusterInfo);
        
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
        String clientId = "1.1.1.1:8848#true";
        ClientSummaryInfo clientSummaryInfo = new ClientSummaryInfo();
        clientSummaryInfo.setClientId(clientId);
        clientSummaryInfo.setLastUpdatedTime(1L);
        clientSummaryInfo.setEphemeral(true);
        clientSummaryInfo.setClientType("ipPort");
        
        Result<ClientSummaryInfo> expectedNode = Result.success(clientSummaryInfo);
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedNode));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        ClientSummaryInfo result = nacosNamingMaintainerService.getClientDetail(clientId);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
        assertEquals(clientId, result.getClientId());
        assertEquals(1L, result.getLastUpdatedTime());
        assertTrue(result.isEphemeral());
        assertEquals("ipPort", result.getClientType());
    }
    
    @Test
    void testGetPublishedServiceList() throws Exception {
        // Arrange
        List<ClientServiceInfo> expectedList = new ArrayList<>();
        expectedList.add(new ClientServiceInfo());
        expectedList.get(0).setNamespaceId("public");
        expectedList.get(0).setGroupName("testGroup");
        expectedList.get(0).setServiceName("testService");
        expectedList.get(0).setPublisherInfo(new ClientPublisherInfo());
        expectedList.get(0).getPublisherInfo().setIp("1.1.1.1");
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(Result.success(expectedList)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        List<ClientServiceInfo> result = nacosNamingMaintainerService.getPublishedServiceList("testClient");
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
        assertEquals("public", result.get(0).getNamespaceId());
        assertEquals("testGroup", result.get(0).getGroupName());
        assertEquals("testService", result.get(0).getServiceName());
        assertEquals("1.1.1.1", result.get(0).getPublisherInfo().getIp());
        assertEquals(0, result.get(0).getPublisherInfo().getPort());
    }
    
    @Test
    void testGetSubscribeServiceList() throws Exception {
        // Arrange
        List<ClientServiceInfo> expectedList = new ArrayList<>();
        expectedList.add(new ClientServiceInfo());
        expectedList.get(0).setNamespaceId("public");
        expectedList.get(0).setGroupName("testGroup");
        expectedList.get(0).setServiceName("testService");
        expectedList.get(0).setSubscriberInfo(new ClientSubscriberInfo());
        expectedList.get(0).getSubscriberInfo().setAddress("1.1.1.1");
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(Result.success(expectedList)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        List<ClientServiceInfo> result = nacosNamingMaintainerService.getSubscribeServiceList("testClient");
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
        assertEquals("public", result.get(0).getNamespaceId());
        assertEquals("testGroup", result.get(0).getGroupName());
        assertEquals("testService", result.get(0).getServiceName());
        assertEquals("1.1.1.1", result.get(0).getSubscriberInfo().getAddress());
    }
    
    @Test
    void testGetPublishedClientList() throws Exception {
        // Arrange
        String ip = "127.0.0.1";
        Integer port = 8080;
        
        List<ClientPublisherInfo> expectedList = new ArrayList<>();
        expectedList.add(new ClientPublisherInfo());
        expectedList.get(0).setIp(ip);
        expectedList.get(0).setPort(port);
        expectedList.get(0).setClientId("127.0.0.1:8080#true");
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(Result.success(expectedList)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String namespaceId = "testNamespace";
        String groupName = "testGroup";
        String serviceName = "testService";
        List<ClientPublisherInfo> result = nacosNamingMaintainerService.getPublishedClientList(namespaceId, groupName,
                serviceName, ip, port);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
        assertEquals(ip, result.get(0).getIp());
        assertEquals(port, result.get(0).getPort());
        assertEquals("127.0.0.1:8080#true", result.get(0).getClientId());
    }
    
    @Test
    void testGetSubscribeClientList() throws Exception {
        // Arrange
        String ip = "127.0.0.1";
        
        List<ClientSubscriberInfo> expectedList = new ArrayList<>();
        expectedList.add(new ClientSubscriberInfo());
        expectedList.get(0).setAddress(ip);
        expectedList.get(0).setAppName("unknown");
        expectedList.get(0).setAgent("Nacos-Java-Client:v3.0.0");
        expectedList.get(0).setClientId("127.0.0.1:8080#true");
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(Result.success(expectedList)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String namespaceId = "testNamespace";
        String groupName = "testGroup";
        String serviceName = "testService";
        List<ClientSubscriberInfo> result = nacosNamingMaintainerService.getSubscribeClientList(namespaceId, groupName,
                serviceName, ip, null);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
        assertEquals(ip, result.get(0).getAddress());
        assertEquals("unknown", result.get(0).getAppName());
        assertEquals("Nacos-Java-Client:v3.0.0", result.get(0).getAgent());
        assertEquals("127.0.0.1:8080#true", result.get(0).getClientId());
    }
}