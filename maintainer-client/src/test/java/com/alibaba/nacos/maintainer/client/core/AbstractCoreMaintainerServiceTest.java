/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.ConnectionInfo;
import com.alibaba.nacos.api.model.response.IdGeneratorInfo;
import com.alibaba.nacos.api.model.response.NacosMember;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.api.model.response.ServerLoaderMetrics;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractCoreMaintainerServiceTest {
    
    @Mock
    private ClientHttpProxy clientHttpProxy;
    
    private AbstractCoreMaintainerService coreMaintainerService;
    
    @BeforeEach
    void setUp() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", "localhost:8848");
        coreMaintainerService = new AbstractCoreMaintainerService(properties) {
        };
        Field clientHttpProxyField = AbstractCoreMaintainerService.class.getDeclaredField("clientHttpProxy");
        clientHttpProxyField.setAccessible(true);
        clientHttpProxyField.set(coreMaintainerService, clientHttpProxy);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        coreMaintainerService.shutdown();
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
        String result = coreMaintainerService.raftOps(command, value, groupId);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetIdGenerators() throws Exception {
        // Arrange
        List<IdGeneratorInfo> expectedList = new ArrayList<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedList)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        List<IdGeneratorInfo> result = coreMaintainerService.getIdGenerators();
        
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
        coreMaintainerService.updateLogLevel(logName, logLevel);
        
        // Assert
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testListClusterNodes() throws Exception {
        // Arrange
        String address = "127.0.0.1:8848";
        String state = "UP";
        
        Collection<NacosMember> expectedMembers = new ArrayList<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedMembers)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Collection<NacosMember> result = coreMaintainerService.listClusterNodes(address, state);
        
        // Assert
        assertNotNull(result);
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
        Boolean result = coreMaintainerService.updateLookupMode(type);
        
        // Assert
        assertTrue(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetCurrentClients() throws Exception {
        // Arrange
        Map<String, ConnectionInfo> expectedMap = new HashMap<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedMap)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Map<String, ConnectionInfo> result = coreMaintainerService.getCurrentClients();
        
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
        String result = coreMaintainerService.reloadConnectionCount(count, redirectAddress);
        
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
        String result = coreMaintainerService.smartReloadCluster(loaderFactorStr);
        
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
        String result = coreMaintainerService.reloadSingleClient(connectionId, redirectAddress);
        
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
        ServerLoaderMetrics result = coreMaintainerService.getClusterLoaderMetrics();
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetNamespaceList() throws Exception {
        // Arrange
        List<Namespace> expectedNamespaces = Arrays.asList(
                new Namespace("namespace-1", "test-namespace-1", "description-1", 100, 10, 0),
                new Namespace("namespace-2", "test-namespace-2", "description-2", 200, 20, 1)
        );
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedNamespaces)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        List<Namespace> result = coreMaintainerService.getNamespaceList();
        
        // Assert
        assertNotNull(result);
        assertEquals(expectedNamespaces.size(), result.size());
        assertEquals(expectedNamespaces.get(0).getNamespace(), result.get(0).getNamespace());
        assertEquals(expectedNamespaces.get(0).getNamespaceShowName(), result.get(0).getNamespaceShowName());
        assertEquals(expectedNamespaces.get(0).getNamespaceDesc(), result.get(0).getNamespaceDesc());
        assertEquals(expectedNamespaces.get(0).getQuota(), result.get(0).getQuota());
        assertEquals(expectedNamespaces.get(0).getConfigCount(), result.get(0).getConfigCount());
        assertEquals(expectedNamespaces.get(0).getType(), result.get(0).getType());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetNamespace() throws Exception {
        // Arrange
        String namespaceId = "test-namespace-id";
        Namespace expectedNamespace = new Namespace(namespaceId, "test-namespace-name", "test-namespace-desc", 100, 10, 0);
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedNamespace)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Namespace result = coreMaintainerService.getNamespace(namespaceId);
        
        // Assert
        assertNotNull(result);
        assertEquals(expectedNamespace.getNamespace(), result.getNamespace());
        assertEquals(expectedNamespace.getNamespaceShowName(), result.getNamespaceShowName());
        assertEquals(expectedNamespace.getNamespaceDesc(), result.getNamespaceDesc());
        assertEquals(expectedNamespace.getQuota(), result.getQuota());
        assertEquals(expectedNamespace.getConfigCount(), result.getConfigCount());
        assertEquals(expectedNamespace.getType(), result.getType());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testCreateNamespace() throws Exception {
        // Arrange
        String namespaceName = "test-namespace-name";
        String namespaceDesc = "test-namespace-desc";
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(true)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Boolean result = coreMaintainerService.createNamespace(namespaceName, namespaceDesc);
        
        // Assert
        assertTrue(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testUpdateNamespace() throws Exception {
        // Arrange
        String namespaceId = "test-namespace-id";
        String namespaceName = "updated-namespace-name";
        String namespaceDesc = "updated-namespace-desc";
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(true)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Boolean result = coreMaintainerService.updateNamespace(namespaceId, namespaceName, namespaceDesc);
        
        // Assert
        assertTrue(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testDeleteNamespace() throws Exception {
        // Arrange
        String namespaceId = "test-namespace-id";
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(true)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Boolean result = coreMaintainerService.deleteNamespace(namespaceId);
        
        // Assert
        assertTrue(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testCheckNamespaceIdExist() throws Exception {
        // Arrange
        String namespaceId = "test-namespace-id";
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(1))); // 1表示存在
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Boolean result = coreMaintainerService.checkNamespaceIdExist(namespaceId);
        
        // Assert
        assertTrue(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testCheckNamespaceIdNotExist() throws Exception {
        // Arrange
        String namespaceId = "non-existent-namespace-id";
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(0))); // 0表示不存在
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Boolean result = coreMaintainerService.checkNamespaceIdExist(namespaceId);
        
        // Assert
        assertFalse(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void getServerState() throws JsonProcessingException, NacosException {
        Map<String, String> serverState = new HashMap<>();
        serverState.put("key", "value");
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(serverState))); // 0表示不存在
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        Map<String, String> result = coreMaintainerService.getServerState();
        
        assertEquals(1, result.size());
        assertEquals("value", result.get("key"));
    }
    
    @Test
    void liveness() throws NacosException {
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setCode(200);
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        Boolean result = coreMaintainerService.liveness();
        assertTrue(result);
        
        mockHttpRestResult.setCode(500);
        result = coreMaintainerService.liveness();
        assertFalse(result);
    }
    
    @Test
    void readiness() throws NacosException {
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setCode(200);
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        Boolean result = coreMaintainerService.readiness();
        assertTrue(result);
        
        mockHttpRestResult.setCode(500);
        result = coreMaintainerService.readiness();
        assertFalse(result);
    }
}