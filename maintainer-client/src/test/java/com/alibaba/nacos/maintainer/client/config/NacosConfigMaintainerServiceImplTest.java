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

package com.alibaba.nacos.maintainer.client.config;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigCloneInfo;
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.config.model.ConfigGrayInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryDetailInfo;
import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.core.AbstractCoreMaintainerService;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
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
class NacosConfigMaintainerServiceImplTest {
    
    @Mock
    private ClientHttpProxy clientHttpProxy;
    
    private NacosConfigMaintainerServiceImpl nacosConfigMaintainerServiceImpl;
    
    @BeforeEach
    void setUp() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", "localhost:8848");
        nacosConfigMaintainerServiceImpl = new NacosConfigMaintainerServiceImpl(properties);
        Field clientHttpProxyField = AbstractCoreMaintainerService.class.getDeclaredField("clientHttpProxy");
        clientHttpProxyField.setAccessible(true);
        clientHttpProxyField.set(nacosConfigMaintainerServiceImpl, clientHttpProxy);
    }
    
    @Test
    void testGetConfig() throws Exception {
        // Arrange
        String dataId = "testDataId";
        ConfigDetailInfo expectedConfig = new ConfigDetailInfo();
        expectedConfig.setDataId(dataId);
        expectedConfig.setGroupName(Constants.DEFAULT_GROUP);
        expectedConfig.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(expectedConfig)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        ConfigDetailInfo result = nacosConfigMaintainerServiceImpl.getConfig(dataId);
        
        // Assert
        assertNotNull(result);
        assertEquals(dataId, result.getDataId());
        assertEquals(Constants.DEFAULT_GROUP, result.getGroupName());
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, result.getNamespaceId());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testPublishConfig() throws Exception {
        // Arrange
        String dataId = "testDataId";
        String content = "testContent";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(true)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        boolean result = nacosConfigMaintainerServiceImpl.publishConfig(dataId, content);
        
        // Assert
        assertTrue(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testPublishBetaConfig() throws Exception {
        String dataId = "testDataId";
        String content = "testContent";
        String betaIps = "127.0.0.1";
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(true)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        boolean result = nacosConfigMaintainerServiceImpl.publishBetaConfig(dataId, Constants.DEFAULT_GROUP,
                Constants.DEFAULT_NAMESPACE_ID, content, null, null, null, null, null, betaIps);
        
        // Assert
        assertTrue(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testPublishBetaConfigWithoutBetaIps() throws Exception {
        String dataId = "testDataId";
        String content = "testContent";
        assertThrows(NacosException.class,
                () -> nacosConfigMaintainerServiceImpl.publishBetaConfig(dataId, Constants.DEFAULT_GROUP,
                        Constants.DEFAULT_NAMESPACE_ID, content, null, null, null, null, null, ""),
                "betaIps is empty, not publish beta configuration, please use `publishConfig` directly");
    }
    
    @Test
    void testDeleteConfig() throws Exception {
        // Arrange
        String dataId = "testDataId";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(true)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        boolean result = nacosConfigMaintainerServiceImpl.deleteConfig(dataId);
        
        // Assert
        assertTrue(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testDeleteConfigs() throws Exception {
        List<Long> ids = new ArrayList<>();
        ids.add(1L);
        ids.add(2L);
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(true)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        boolean result = nacosConfigMaintainerServiceImpl.deleteConfigs(ids);
        
        assertTrue(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testListConfigs() throws Exception {
        // Arrange
        final String namespaceId = "testNamespace";
        int pageNo = 1;
        int pageSize = 100;
        
        Page<ConfigBasicInfo> expectedPage = new Page<>();
        expectedPage.setPageNumber(pageNo);
        expectedPage.setPagesAvailable(pageSize);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(expectedPage)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        Page<ConfigBasicInfo> result = nacosConfigMaintainerServiceImpl.listConfigs(namespaceId);
        
        // Assert
        assertNotNull(result);
        assertEquals(pageNo, result.getPageNumber());
        assertEquals(pageSize, result.getPagesAvailable());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testSearchConfigs() throws Exception {
        // Arrange
        final String dataId = "testDataId";
        final String groupName = "testGroup";
        final String namespaceId = "testNamespace";
        int pageNo = 1;
        int pageSize = 100;
        
        Page<ConfigBasicInfo> expectedPage = new Page<>();
        expectedPage.setPageNumber(pageNo);
        expectedPage.setPagesAvailable(pageSize);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(expectedPage)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        Page<ConfigBasicInfo> result = nacosConfigMaintainerServiceImpl.searchConfigs(dataId, groupName, namespaceId);
        
        // Assert
        assertNotNull(result);
        assertEquals(pageNo, result.getPageNumber());
        assertEquals(pageSize, result.getPagesAvailable());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testSearchConfigByDetails() throws Exception {
        // Arrange
        final String dataId = "testDataId";
        final String groupName = "testGroup";
        final String namespaceId = "testNamespace";
        final String configDetail = "testDetail";
        final String search = "testSearch";
        int pageNo = 1;
        int pageSize = 10;
        
        Page<ConfigBasicInfo> expectedPage = new Page<>();
        expectedPage.setPageNumber(pageNo);
        expectedPage.setPagesAvailable(pageSize);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(expectedPage)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        Page<ConfigBasicInfo> result = nacosConfigMaintainerServiceImpl.searchConfigByDetails(dataId, groupName,
                namespaceId, configDetail, search, "", "", "", pageNo, pageSize);
        
        // Assert
        assertNotNull(result);
        assertEquals(pageNo, result.getPageNumber());
        assertEquals(pageSize, result.getPagesAvailable());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testGetListeners() throws Exception {
        // Arrange
        final String dataId = "testDataId";
        final String groupName = "testGroup";
        Map<String, String> lisentersGroupkeyStatusMap = new HashMap<>();
        ConfigListenerInfo expectedStatus = new ConfigListenerInfo();
        expectedStatus.setQueryType(ConfigListenerInfo.QUERY_TYPE_CONFIG);
        expectedStatus.setListenersStatus(lisentersGroupkeyStatusMap);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(expectedStatus)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        ConfigListenerInfo result = nacosConfigMaintainerServiceImpl.getListeners(dataId, groupName);
        
        // Assert
        assertNotNull(result);
        assertEquals(ConfigListenerInfo.QUERY_TYPE_CONFIG, result.getQueryType());
        assertEquals(lisentersGroupkeyStatusMap, result.getListenersStatus());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testStopBeta() throws Exception {
        // Arrange
        String dataId = "testDataId";
        String groupName = "testGroup";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(true)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        boolean result = nacosConfigMaintainerServiceImpl.stopBeta(dataId, groupName);
        
        // Assert
        assertTrue(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testQueryBeta() throws Exception {
        // Arrange
        final String dataId = "testDataId";
        final String groupName = "testGroup";
        
        ConfigGrayInfo expectedConfig = new ConfigGrayInfo();
        expectedConfig.setDataId(dataId);
        expectedConfig.setGroupName(groupName);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(expectedConfig)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        ConfigGrayInfo result = nacosConfigMaintainerServiceImpl.queryBeta(dataId, groupName);
        
        // Assert
        assertNotNull(result);
        assertEquals(dataId, result.getDataId());
        assertEquals(groupName, result.getGroupName());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testCloneConfig() throws Exception {
        // Arrange
        final String namespaceId = "testNamespace";
        final List<ConfigCloneInfo> configBeansList = new ArrayList<>();
        final String srcUser = "testUser";
        final SameConfigPolicy policy = SameConfigPolicy.ABORT;
        
        Map<String, Object> expectedResult = new HashMap<>();
        expectedResult.put("success", true);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(expectedResult)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        Map<String, Object> result = nacosConfigMaintainerServiceImpl.cloneConfig(namespaceId, configBeansList, srcUser,
                policy);
        
        // Assert
        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testListConfigHistory() throws Exception {
        // Arrange
        String dataId = "testDataId";
        String groupName = "testGroup";
        String namespaceId = "testNamespace";
        int pageNo = 1;
        int pageSize = 10;
        
        Page<ConfigHistoryBasicInfo> expectedPage = new Page<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedPage)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Page<ConfigHistoryBasicInfo> result = nacosConfigMaintainerServiceImpl.listConfigHistory(dataId, groupName,
                namespaceId, pageNo, pageSize);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetConfigHistoryInfo() throws Exception {
        // Arrange
        final String dataId = "testDataId";
        final String groupName = "testGroup";
        final String namespaceId = "testNamespace";
        final Long nid = 1L;
        
        ConfigHistoryDetailInfo expectedConfig = new ConfigHistoryDetailInfo();
        expectedConfig.setCreateTime(System.currentTimeMillis());
        expectedConfig.setModifyTime(System.currentTimeMillis());
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedConfig)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        ConfigHistoryDetailInfo result = nacosConfigMaintainerServiceImpl.getConfigHistoryInfo(dataId, groupName,
                namespaceId, nid);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetPreviousConfigHistoryInfo() throws Exception {
        // Arrange
        final String dataId = "testDataId";
        final String groupName = "testGroup";
        final String namespaceId = "testNamespace";
        final Long id = 1L;
        
        ConfigHistoryDetailInfo expectedConfig = new ConfigHistoryDetailInfo();
        expectedConfig.setCreateTime(System.currentTimeMillis());
        expectedConfig.setModifyTime(System.currentTimeMillis());
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedConfig)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        ConfigHistoryDetailInfo result = nacosConfigMaintainerServiceImpl.getPreviousConfigHistoryInfo(dataId,
                groupName, namespaceId, id);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetConfigListByNamespace() throws Exception {
        // Arrange
        String namespaceId = "testNamespace";
        
        List<ConfigBasicInfo> expectedList = new ArrayList<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedList)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        List<ConfigBasicInfo> result = nacosConfigMaintainerServiceImpl.getConfigListByNamespace(namespaceId);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testUpdateLocalCacheFromStore() throws Exception {
        // Arrange
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosConfigMaintainerServiceImpl.updateLocalCacheFromStore();
        
        // Assert
        assertEquals("success", result);
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
        String result = nacosConfigMaintainerServiceImpl.setLogLevel(logName, logLevel);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetAllSubClientConfigByIp() throws Exception {
        
        ConfigListenerInfo expectedStatus = new ConfigListenerInfo();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        expectedStatus.setQueryType(ConfigListenerInfo.QUERY_TYPE_IP);
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedStatus)));
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        // Arrange
        String ip = "127.0.0.1";
        boolean all = true;
        String namespaceId = "testNamespace";
        
        // Act
        ConfigListenerInfo result = nacosConfigMaintainerServiceImpl.getAllSubClientConfigByIp(ip, all, namespaceId,
                true);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
}