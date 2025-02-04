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

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.model.config.Capacity;
import com.alibaba.nacos.maintainer.client.model.config.ConfigAdvanceInfo;
import com.alibaba.nacos.maintainer.client.model.config.ConfigAllInfo;
import com.alibaba.nacos.maintainer.client.model.config.ConfigHistoryInfo;
import com.alibaba.nacos.maintainer.client.model.config.ConfigInfo;
import com.alibaba.nacos.maintainer.client.model.config.ConfigInfo4Beta;
import com.alibaba.nacos.maintainer.client.model.config.ConfigInfoWrapper;
import com.alibaba.nacos.maintainer.client.model.config.GroupkeyListenserStatus;
import com.alibaba.nacos.maintainer.client.model.config.Page;
import com.alibaba.nacos.maintainer.client.model.config.SameConfigPolicy;
import com.alibaba.nacos.maintainer.client.model.config.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.maintainer.client.model.core.Connection;
import com.alibaba.nacos.maintainer.client.model.core.IdGeneratorVO;
import com.alibaba.nacos.maintainer.client.model.core.Member;
import com.alibaba.nacos.maintainer.client.model.core.ServerLoaderMetrics;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.sql.Timestamp;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NacosConfigMaintainerServiceTest {
    
    @Mock
    private ClientHttpProxy clientHttpProxy;
    
    private NacosConfigMaintainerService nacosConfigMaintainerService;
    
    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        Properties properties = new Properties();
        properties.setProperty("serverAddr", "localhost:8848");
        nacosConfigMaintainerService = new NacosConfigMaintainerService(properties);
        Field clientHttpProxyField = NacosConfigMaintainerService.class.getDeclaredField("clientHttpProxy");
        clientHttpProxyField.setAccessible(true);
        clientHttpProxyField.set(nacosConfigMaintainerService, clientHttpProxy);
    }
    
    @Test
    void testGetConfig() throws Exception {
        // Arrange
        String dataId = "testDataId";
        String groupName = "testGroup";
        String namespaceId = "testNamespace";
        ConfigAllInfo expectedConfig = new ConfigAllInfo();
        expectedConfig.setDataId(dataId);
        expectedConfig.setGroup(groupName);
        expectedConfig.setTenant(namespaceId);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(expectedConfig)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        ConfigAllInfo result = nacosConfigMaintainerService.getConfig(dataId, groupName, namespaceId);
        
        // Assert
        assertNotNull(result);
        assertEquals(dataId, result.getDataId());
        assertEquals(groupName, result.getGroup());
        assertEquals(namespaceId, result.getTenant());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testPublishConfig() throws Exception {
        // Arrange
        String dataId = "testDataId";
        String groupName = "testGroup";
        String namespaceId = "testNamespace";
        String content = "testContent";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(true)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        boolean result = nacosConfigMaintainerService.publishConfig(dataId, groupName, namespaceId, content);
        
        // Assert
        assertTrue(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testDeleteConfig() throws Exception {
        // Arrange
        String dataId = "testDataId";
        String groupName = "testGroup";
        String namespaceId = "testNamespace";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(true)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        boolean result = nacosConfigMaintainerService.deleteConfig(dataId, groupName, namespaceId);
        
        // Assert
        assertTrue(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testGetConfigAdvanceInfo() throws Exception {
        // Arrange
        final String dataId = "testId";
        final String groupName = "testGroupName";
        final String namespaceId = "testNamespace";
        String createUser = "testCreateUser";
        String createIp = "testCreateIp";
        ConfigAdvanceInfo expectedAdvanceInfo = new ConfigAdvanceInfo();
        expectedAdvanceInfo.setCreateUser(createUser);
        expectedAdvanceInfo.setCreateIp(createIp);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(expectedAdvanceInfo)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        ConfigAdvanceInfo result = nacosConfigMaintainerService.getConfigAdvanceInfo(dataId, groupName, namespaceId);
        
        // Assert
        assertNotNull(result);
        assertEquals(createUser, result.getCreateUser());
        assertEquals(createIp, result.getCreateIp());
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
        
        Page<ConfigInfo> expectedPage = new Page<>();
        expectedPage.setPageNumber(pageNo);
        expectedPage.setPagesAvailable(pageSize);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(expectedPage)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        Page<ConfigInfo> result = nacosConfigMaintainerService.searchConfigByDetails(dataId, groupName, namespaceId,
                configDetail, search, pageNo, pageSize);
        
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
        final String namespaceId = "testNamespace";
        final int sampleTime = 1;
        int collectionStatus = 1;
        Map<String, String> lisentersGroupkeyStatusMap = new HashMap<>();
        GroupkeyListenserStatus expectedStatus = new GroupkeyListenserStatus();
        expectedStatus.setCollectStatus(collectionStatus);
        expectedStatus.setLisentersGroupkeyStatus(lisentersGroupkeyStatusMap);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(expectedStatus)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        GroupkeyListenserStatus result = nacosConfigMaintainerService.getListeners(dataId, groupName, namespaceId, sampleTime);
        
        // Assert
        assertNotNull(result);
        assertEquals(collectionStatus, result.getCollectStatus());
        assertEquals(lisentersGroupkeyStatusMap, result.getLisentersGroupkeyStatus());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testStopBeta() throws Exception {
        // Arrange
        String dataId = "testDataId";
        String groupName = "testGroup";
        String namespaceId = "testNamespace";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(true)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        boolean result = nacosConfigMaintainerService.stopBeta(dataId, groupName, namespaceId);
        
        // Assert
        assertTrue(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testQueryBeta() throws Exception {
        // Arrange
        final String dataId = "testDataId";
        final String groupName = "testGroup";
        final String namespaceId = "testNamespace";
        
        ConfigInfo4Beta expectedConfig = new ConfigInfo4Beta();
        expectedConfig.setDataId(dataId);
        expectedConfig.setGroup(groupName);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(expectedConfig)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        ConfigInfo4Beta result = nacosConfigMaintainerService.queryBeta(dataId, groupName, namespaceId);
        
        // Assert
        assertNotNull(result);
        assertEquals(dataId, result.getDataId());
        assertEquals(groupName, result.getGroup());
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testImportAndPublishConfig() throws Exception {
        // Arrange
        final String namespaceId = "testNamespace";
        final String srcUser = "testUser";
        final SameConfigPolicy policy = SameConfigPolicy.ABORT;
        MultipartFile multipartFile = mock(MultipartFile.class);
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("testContent".getBytes()));
        
        Map<String, Object> expectedResult = new HashMap<>();
        expectedResult.put("success", true);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(expectedResult)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        Map<String, Object> result = nacosConfigMaintainerService.importAndPublishConfig(namespaceId, srcUser, policy, multipartFile);
        
        // Assert
        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testExportConfig() throws Exception {
        // Arrange
        String dataId = "testDataId";
        String groupName = "testGroup";
        String namespaceId = "testNamespace";
        List<Long> ids = Arrays.asList(1L, 2L, 3L);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData("testContent");
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        ResponseEntity<byte[]> result = nacosConfigMaintainerService.exportConfig(dataId, groupName, namespaceId, ids);
        
        // Assert
        assertNotNull(result);
        assertNotNull(result.getBody());
        assertEquals("testContent", new String(result.getBody()));
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }
    
    @Test
    void testCloneConfig() throws Exception {
        // Arrange
        final String namespaceId = "testNamespace";
        final List<SameNamespaceCloneConfigBean> configBeansList = new ArrayList<>();
        final String srcUser = "testUser";
        final SameConfigPolicy policy = SameConfigPolicy.ABORT;
        
        Map<String, Object> expectedResult = new HashMap<>();
        expectedResult.put("success", true);
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(JacksonUtils.toJson(new Result<>(expectedResult)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockHttpRestResult);
        
        // Act
        Map<String, Object> result = nacosConfigMaintainerService.cloneConfig(namespaceId, configBeansList, srcUser, policy);
        
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
        
        Page<ConfigHistoryInfo> expectedPage = new Page<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedPage)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Page<ConfigHistoryInfo> result = nacosConfigMaintainerService.listConfigHistory(dataId, groupName, namespaceId, pageNo, pageSize);
        
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
        
        ConfigHistoryInfo expectedConfig = new ConfigHistoryInfo();
        expectedConfig.setCreatedTime(new Timestamp(System.currentTimeMillis()));
        expectedConfig.setLastModifiedTime(new Timestamp(System.currentTimeMillis()));
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedConfig)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        ConfigHistoryInfo result = nacosConfigMaintainerService.getConfigHistoryInfo(dataId, groupName, namespaceId, nid);
        
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
        
        ConfigHistoryInfo expectedConfig = new ConfigHistoryInfo();
        expectedConfig.setCreatedTime(new Timestamp(System.currentTimeMillis()));
        expectedConfig.setLastModifiedTime(new Timestamp(System.currentTimeMillis()));
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedConfig)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        ConfigHistoryInfo result = nacosConfigMaintainerService.getPreviousConfigHistoryInfo(dataId, groupName, namespaceId, id);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetConfigListByNamespace() throws Exception {
        // Arrange
        String namespaceId = "testNamespace";
        
        List<ConfigInfoWrapper> expectedList = new ArrayList<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedList)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        List<ConfigInfoWrapper> result = nacosConfigMaintainerService.getConfigListByNamespace(namespaceId);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetCapacityWithDefault() throws Exception {
        // Arrange
        String groupName = "testGroup";
        String namespaceId = "testNamespace";
        
        Capacity expectedCapacity = new Capacity();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedCapacity)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Capacity result = nacosConfigMaintainerService.getCapacityWithDefault(groupName, namespaceId);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testInsertOrUpdateCapacity() throws Exception {
        // Arrange
        String groupName = "testGroup";
        String namespaceId = "testNamespace";
        Integer quota = 100;
        Integer maxSize = 200;
        Integer maxAggrCount = 300;
        Integer maxAggrSize = 400;
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(true)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        boolean result = nacosConfigMaintainerService.insertOrUpdateCapacity(groupName, namespaceId, quota, maxSize, maxAggrCount, maxAggrSize);
        
        // Assert
        assertTrue(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testUpdateLocalCacheFromStore() throws Exception {
        // Arrange
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        String result = nacosConfigMaintainerService.updateLocalCacheFromStore();
        
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
        String result = nacosConfigMaintainerService.setLogLevel(logName, logLevel);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testDerbyOps() throws Exception {
        // Arrange
        String sql = "SELECT * FROM table";
        
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>("success")));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Object result = nacosConfigMaintainerService.derbyOps(sql);
        
        // Assert
        assertEquals("success", result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testImportDerby() throws Exception {
        // Arrange
        MultipartFile multipartFile = mock(MultipartFile.class);
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("testContent".getBytes()));
        
        // Act
        DeferredResult<String> result = nacosConfigMaintainerService.importDerby(multipartFile);
        
        // Assert
        verify(clientHttpProxy, times(1)).executeAsyncHttpRequest(any(), any());
    }
    
    @Test
    void testGetAllSubClientConfigByIp() throws Exception {
        // Arrange
        String ip = "127.0.0.1";
        boolean all = true;
        String namespaceId = "testNamespace";
        int sampleTime = 1;
        
        GroupkeyListenserStatus expectedStatus = new GroupkeyListenserStatus();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedStatus)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        GroupkeyListenserStatus result = nacosConfigMaintainerService.getAllSubClientConfigByIp(ip, all, namespaceId, sampleTime);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetClientMetrics() throws Exception {
        // Arrange
        String ip = "127.0.0.1";
        String dataId = "testDataId";
        String groupName = "testGroup";
        String namespaceId = "testNamespace";
        
        Map<String, Object> expectedMap = new HashMap<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedMap)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Map<String, Object> result = nacosConfigMaintainerService.getClientMetrics(ip, dataId, groupName, namespaceId);
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
    
    @Test
    void testGetClusterMetrics() throws Exception {
        // Arrange
        String ip = "127.0.0.1";
        String dataId = "testDataId";
        String groupName = "testGroup";
        String namespaceId = "testNamespace";
        
        Map<String, Object> expectedMap = new HashMap<>();
        HttpRestResult<String> mockHttpRestResult = new HttpRestResult<>();
        mockHttpRestResult.setData(new ObjectMapper().writeValueAsString(new Result<>(expectedMap)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any())).thenReturn(mockHttpRestResult);
        
        // Act
        Map<String, Object> result = nacosConfigMaintainerService.getClusterMetrics(ip, dataId, groupName, namespaceId);
        
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
        String result = nacosConfigMaintainerService.raftOps(command, value, groupId);
        
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
        List<IdGeneratorVO> result = nacosConfigMaintainerService.getIdsHealth();
        
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
        nacosConfigMaintainerService.updateLogLevel(logName, logLevel);
        
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
        Member result = nacosConfigMaintainerService.getSelfNode();
        
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
        Collection<Member> result = nacosConfigMaintainerService.listClusterNodes(address, state);
        
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
        String result = nacosConfigMaintainerService.getSelfNodeHealth();
        
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
        Boolean result = nacosConfigMaintainerService.updateClusterNodes(nodes);
        
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
        Boolean result = nacosConfigMaintainerService.updateLookupMode(type);
        
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
        Map<String, Connection> result = nacosConfigMaintainerService.getCurrentClients();
        
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
        String result = nacosConfigMaintainerService.reloadConnectionCount(count, redirectAddress);
        
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
        String result = nacosConfigMaintainerService.smartReloadCluster(loaderFactorStr);
        
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
        String result = nacosConfigMaintainerService.reloadSingleClient(connectionId, redirectAddress);
        
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
        ServerLoaderMetrics result = nacosConfigMaintainerService.getClusterLoaderMetrics();
        
        // Assert
        assertNotNull(result);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any());
    }
}