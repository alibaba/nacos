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

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.maintainer.client.model.core.Connection;
import com.alibaba.nacos.maintainer.client.model.core.IdGeneratorVO;
import com.alibaba.nacos.maintainer.client.model.core.Member;
import com.alibaba.nacos.maintainer.client.model.core.ServerLoaderMetrics;
import com.alibaba.nacos.maintainer.client.model.naming.InstanceDetailInfoVo;
import com.alibaba.nacos.maintainer.client.model.naming.InstanceMetadataBatchOperationVo;
import com.alibaba.nacos.maintainer.client.model.naming.MetricsInfoVo;
import com.alibaba.nacos.maintainer.client.model.naming.ServiceDetailInfo;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NacosNamingMaintainerServiceImplTest {
    
    @Mock
    private ClientHttpProxy clientHttpProxy;
    
    private NamingMaintainerService nacosNamingMaintainerService;
    
    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        Properties properties = new Properties();
        properties.setProperty("serverAddr", "localhost:8848");
        nacosNamingMaintainerService = new NacosNamingMaintainerServiceImpl(properties);
        Field clientHttpProxyField = NacosNamingMaintainerServiceImpl.class.getDeclaredField("clientHttpProxy");
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
        String namespaceId = "testNamespace";
        String groupName = "testGroup";
        String serviceName = "testService";
        String metadata = "testMetadata";
        boolean ephemeral = true;
        float protectThreshold = 0.5f;
        String selector = "testSelector";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.updateService(namespaceId, groupName, serviceName, metadata,
                ephemeral, protectThreshold, selector);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testRemoveService() throws Exception {
        // Arrange
        String namespaceId = "testNamespace";
        String groupName = "testGroup";
        String serviceName = "testService";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.removeService(namespaceId, groupName, serviceName);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetServiceDetail() throws Exception {
        // Arrange
        String namespaceId = "testNamespace";
        String groupName = "testGroup";
        String serviceName = "testService";
        
        ServiceDetailInfo expectedDetail = new ServiceDetailInfo();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedDetail)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        ServiceDetailInfo result = nacosNamingMaintainerService.getServiceDetail(namespaceId, groupName, serviceName);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testListServices() throws Exception {
        // Arrange
        String namespaceId = "testNamespace";
        String groupName = "testGroup";
        String selector = "testSelector";
        int pageNo = 1;
        int pageSize = 10;
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Object result = nacosNamingMaintainerService.listServices(namespaceId, groupName, selector, pageNo, pageSize);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testSearchService() throws Exception {
        // Arrange
        String namespaceId = "testNamespace";
        String expr = "testExpr";
        
        ObjectNode expectedNode = new ObjectMapper().createObjectNode();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedNode));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        ObjectNode result = nacosNamingMaintainerService.searchService(namespaceId, expr);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetSubscribers() throws Exception {
        // Arrange
        final String namespaceId = "testNamespace";
        final String groupName = "testGroup";
        final String serviceName = "testService";
        final int pageNo = 1;
        final int pageSize = 10;
        final boolean aggregation = true;
        
        Result<ObjectNode> expectedResult = new Result<>();
        Result.success(new ObjectMapper().createObjectNode());
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedResult));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Result<ObjectNode> result = nacosNamingMaintainerService.getSubscribers(namespaceId, groupName, serviceName,
                pageNo, pageSize, aggregation);
        
        // Assert
        assertNotNull(result);
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
        
        MetricsInfoVo expectedMetrics = new MetricsInfoVo();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedMetrics));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        MetricsInfoVo result = nacosNamingMaintainerService.getMetrics(onlyStatus);
        
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
        String result = nacosNamingMaintainerService.registerInstance(namespaceId, groupName, serviceName, clusterName,
                ip, port, weight, healthy, enabled, ephemeral, metadata);
        
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
        String weight = "1.0";
        boolean healthy = true;
        boolean enabled = true;
        String ephemeral = "true";
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
        
        InstanceMetadataBatchOperationVo expectedVo = new InstanceMetadataBatchOperationVo();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedVo));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        InstanceMetadataBatchOperationVo result = nacosNamingMaintainerService.batchUpdateInstanceMetadata(namespaceId,
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
        
        InstanceMetadataBatchOperationVo expectedVo = new InstanceMetadataBatchOperationVo();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedVo));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        InstanceMetadataBatchOperationVo result = nacosNamingMaintainerService.batchDeleteInstanceMetadata(namespaceId,
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
        String namespaceId = "testNamespace";
        String groupName = "testGroup";
        String serviceName = "testService";
        String clusterName = "testCluster";
        String ip = "127.0.0.1";
        int port = 8080;
        boolean healthyOnly = true;
        
        ServiceInfo expectedInfo = new ServiceInfo();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedInfo));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        ServiceInfo result = nacosNamingMaintainerService.listInstances(namespaceId, groupName, serviceName,
                clusterName, ip, port, healthyOnly);
        
        // Assert
        assertNotNull(result);
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
        
        InstanceDetailInfoVo expectedVo = new InstanceDetailInfoVo();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(expectedVo));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        InstanceDetailInfoVo result = nacosNamingMaintainerService.getInstanceDetail(namespaceId, groupName,
                serviceName, clusterName, ip, port);
        
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
        String clusterName = "testCluster";
        Integer checkPort = 8080;
        Boolean useInstancePort4Check = true;
        String healthChecker = "testChecker";
        Map<String, String> metadata = new HashMap<>();
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.updateCluster(namespaceId, groupName, clusterName, checkPort,
                useInstancePort4Check, healthChecker, metadata);
        
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
    
    @Test
    void testRaftOps() throws Exception {
        // Arrange
        String command = "testCommand";
        String value = "testValue";
        String groupId = "testGroup";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.raftOps(command, value, groupId);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetIdsHealth() throws Exception {
        // Arrange
        List<IdGeneratorVO> expectedList = new ArrayList<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedList)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        List<IdGeneratorVO> result = nacosNamingMaintainerService.getIdsHealth();
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testUpdateLogLevel() throws Exception {
        // Arrange
        String logName = "testLog";
        String logLevel = "INFO";
        
        // Act
        nacosNamingMaintainerService.updateLogLevel(logName, logLevel);
        
        // Assert
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetSelfNode() throws Exception {
        // Arrange
        Member expectedMember = new Member();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedMember)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Member result = nacosNamingMaintainerService.getSelfNode();
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testListClusterNodes() throws Exception {
        // Arrange
        String address = "127.0.0.1:8848";
        String state = "UP";
        
        Collection<Member> expectedMembers = new ArrayList<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedMembers)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Collection<Member> result = nacosNamingMaintainerService.listClusterNodes(address, state);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetSelfNodeHealth() throws Exception {
        // Arrange
        String expectedHealth = "HEALTHY";
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedHealth)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.getSelfNodeHealth();
        
        // Assert
        assertEquals(expectedHealth, result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testUpdateClusterNodes() throws Exception {
        // Arrange
        List<Member> nodes = new ArrayList<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(true)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Boolean result = nacosNamingMaintainerService.updateClusterNodes(nodes);
        
        // Assert
        assertTrue(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testUpdateLookupMode() throws Exception {
        // Arrange
        String type = "testType";
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(true)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Boolean result = nacosNamingMaintainerService.updateLookupMode(type);
        
        // Assert
        assertTrue(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetCurrentClients() throws Exception {
        // Arrange
        Map<String, Connection> expectedMap = new HashMap<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedMap)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Map<String, Connection> result = nacosNamingMaintainerService.getCurrentClients();
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testReloadConnectionCount() throws Exception {
        // Arrange
        Integer count = 10;
        String redirectAddress = "localhost:8848";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.reloadConnectionCount(count, redirectAddress);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testSmartReloadCluster() throws Exception {
        // Arrange
        String loaderFactorStr = "testFactor";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.smartReloadCluster(loaderFactorStr);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testReloadSingleClient() throws Exception {
        // Arrange
        String connectionId = "testConnectionId";
        String redirectAddress = "localhost:8848";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosNamingMaintainerService.reloadSingleClient(connectionId, redirectAddress);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetClusterLoaderMetrics() throws Exception {
        // Arrange
        ServerLoaderMetrics expectedMetrics = new ServerLoaderMetrics();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedMetrics)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        ServerLoaderMetrics result = nacosNamingMaintainerService.getClusterLoaderMetrics();
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
}