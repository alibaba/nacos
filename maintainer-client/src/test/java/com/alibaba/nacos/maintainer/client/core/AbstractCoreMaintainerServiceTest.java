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

import com.alibaba.nacos.api.model.response.IdGeneratorInfo;
import com.alibaba.nacos.api.model.response.ServerLoaderMetrics;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.maintainer.client.model.core.Connection;
import com.alibaba.nacos.maintainer.client.model.core.Member;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
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
        
        Collection<Member> expectedMembers = new ArrayList<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedMembers)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Collection<Member> result = coreMaintainerService.listClusterNodes(address, state);
        
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
        Map<String, Connection> expectedMap = new HashMap<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedMap)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Map<String, Connection> result = coreMaintainerService.getCurrentClients();
        
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
}