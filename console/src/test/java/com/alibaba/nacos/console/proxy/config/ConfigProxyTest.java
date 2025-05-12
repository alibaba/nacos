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

package com.alibaba.nacos.console.proxy.config;

import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.config.model.ConfigGrayInfo;
import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.console.handler.config.ConfigHandler;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConfigProxyTest {
    
    private static final String DATA_ID = "testDataId";
    
    private static final String GROUP = "testGroup";
    
    private static final String NAMESPACE_ID = "testNamespaceId";
    
    private static final String TAG = "testTag";
    
    private static final String CLIENT_IP = "127.0.0.1";
    
    private static final String SRC_USER = "testUser";
    
    private static final String DATA_ID_B = "dataId";
    
    private static final String GROUP_B = "group";
    
    private static final String NAMESPACE_ID_B = "namespaceId";
    
    @Mock
    private ConfigHandler configHandler;
    
    private ConfigProxy configProxy;
    
    @Mock
    private MultipartFile file;
    
    @BeforeEach
    public void setUp() {
        configProxy = new ConfigProxy(configHandler);
    }
    
    @Test
    public void getConfigDetail() throws NacosException {
        // 准备
        String dataId = DATA_ID;
        String group = GROUP;
        String namespaceId = NAMESPACE_ID;
        ConfigDetailInfo expectedConfigDetailInfo = new ConfigDetailInfo();
        expectedConfigDetailInfo.setDataId(dataId);
        expectedConfigDetailInfo.setGroupName(group);
        expectedConfigDetailInfo.setNamespaceId(namespaceId);
        
        when(configHandler.getConfigDetail(dataId, group, namespaceId)).thenReturn(expectedConfigDetailInfo);
        
        // 执行
        ConfigDetailInfo actualConfigDetailInfo = configProxy.getConfigDetail(dataId, group, namespaceId);
        
        // 断言
        assertEquals(expectedConfigDetailInfo, actualConfigDetailInfo);
    }
    
    @Test
    public void publishConfig() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        
        when(configHandler.publishConfig(configForm, configRequestInfo)).thenReturn(true);
        
        Boolean result = configProxy.publishConfig(configForm, configRequestInfo);
        
        assertTrue(result);
        verify(configHandler, times(1)).publishConfig(configForm, configRequestInfo);
    }
    
    @Test
    public void getConfigList() throws IOException, ServletException, NacosException {
        Page<ConfigBasicInfo> expectedPage = new Page<>();
        expectedPage.setPageNumber(1);
        expectedPage.setPagesAvailable(1);
        expectedPage.setTotalCount(10);
        
        when(configHandler.getConfigList(anyInt(), anyInt(), anyString(), anyString(), anyString(),
                any(Map.class))).thenReturn(expectedPage);
        
        Page<ConfigBasicInfo> result = configProxy.getConfigList(1, 10, DATA_ID, GROUP, NAMESPACE_ID, new HashMap<>());
        
        assertNotNull(result);
        assertEquals(expectedPage.getPageNumber(), result.getPageNumber());
        assertEquals(expectedPage.getPagesAvailable(), result.getPagesAvailable());
        assertEquals(expectedPage.getTotalCount(), result.getTotalCount());
    }
    
    @Test
    public void deleteConfig() throws NacosException {
        when(configHandler.deleteConfig(DATA_ID, GROUP, NAMESPACE_ID, TAG, CLIENT_IP, SRC_USER)).thenReturn(true);
        
        Boolean result = configProxy.deleteConfig(DATA_ID, GROUP, NAMESPACE_ID, TAG, CLIENT_IP, SRC_USER);
        
        assertTrue(result);
        verify(configHandler, times(1)).deleteConfig(DATA_ID, GROUP, NAMESPACE_ID, TAG, CLIENT_IP, SRC_USER);
    }
    
    @Test
    public void getConfigListByContent() throws NacosException {
        Page<ConfigBasicInfo> expectedPage = new Page<>();
        expectedPage.setPageItems(new ArrayList<>());
        expectedPage.setPageNumber(1);
        expectedPage.setPagesAvailable(1);
        expectedPage.setTotalCount(0);
        
        when(configHandler.getConfigListByContent(anyString(), anyInt(), anyInt(), anyString(), anyString(),
                anyString(), anyMap())).thenReturn(expectedPage);
        
        Page<ConfigBasicInfo> result = configProxy.getConfigListByContent("search", 1, 10, DATA_ID_B, GROUP_B,
                NAMESPACE_ID_B, new HashMap<>());
        
        assertEquals(expectedPage, result);
        verify(configHandler, times(1)).getConfigListByContent("search", 1, 10, DATA_ID_B, GROUP_B, NAMESPACE_ID_B,
                new HashMap<>());
    }
    
    @Test
    public void batchDeleteConfigs() throws NacosException {
        List<Long> ids = Arrays.asList(1L, 2L, 3L);
        String clientIp = CLIENT_IP;
        String srcUser = SRC_USER;
        
        when(configHandler.batchDeleteConfigs(ids, clientIp, srcUser)).thenReturn(true);
        
        Boolean result = configProxy.batchDeleteConfigs(ids, clientIp, srcUser);
        
        assertTrue(result);
        verify(configHandler, times(1)).batchDeleteConfigs(ids, clientIp, srcUser);
    }
    
    @Test
    public void getListeners() throws Exception {
        String dataId = DATA_ID;
        String group = GROUP;
        String namespaceId = NAMESPACE_ID;
        boolean aggregation = true;
        ConfigListenerInfo expectedInfo = new ConfigListenerInfo();
        expectedInfo.setQueryType("config");
        
        when(configHandler.getListeners(dataId, group, namespaceId, aggregation)).thenReturn(expectedInfo);
        
        ConfigListenerInfo actualInfo = configProxy.getListeners(dataId, group, namespaceId, aggregation);
        
        assertEquals(expectedInfo, actualInfo);
        verify(configHandler, times(1)).getListeners(dataId, group, namespaceId, aggregation);
    }
    
    @Test
    public void getAllSubClientConfigByIp() throws NacosException {
        String ip = "192.168.1.1";
        boolean all = true;
        String namespaceId = "testNamespace";
        boolean aggregation = false;
        ConfigListenerInfo expectedInfo = new ConfigListenerInfo();
        expectedInfo.setQueryType("config");
        
        when(configHandler.getAllSubClientConfigByIp(ip, all, namespaceId, aggregation)).thenReturn(expectedInfo);
        
        ConfigListenerInfo result = configProxy.getAllSubClientConfigByIp(ip, all, namespaceId, aggregation);
        
        assertEquals(expectedInfo.getQueryType(), result.getQueryType());
    }
    
    @Test
    public void exportConfigV2() throws Exception {
        String dataId = DATA_ID;
        String group = GROUP;
        String namespaceId = NAMESPACE_ID;
        String appName = "testAppName";
        List<Long> ids = Arrays.asList(1L, 2L, 3L);
        
        byte[] expectedBody = "testBody".getBytes();
        ResponseEntity<byte[]> expectedResponse = ResponseEntity.ok(expectedBody);
        
        when(configHandler.exportConfig(dataId, group, namespaceId, appName, ids)).thenReturn(expectedResponse);
        
        ResponseEntity<byte[]> actualResponse = configProxy.exportConfigV2(dataId, group, namespaceId, appName, ids);
        
        assertEquals(expectedResponse, actualResponse);
        verify(configHandler, times(1)).exportConfig(dataId, group, namespaceId, appName, ids);
    }
    
    @Test
    public void importAndPublishConfig() throws NacosException {
        String srcUser = SRC_USER;
        String namespaceId = "testNamespace";
        SameConfigPolicy policy = SameConfigPolicy.OVERWRITE;
        String srcIp = CLIENT_IP;
        String requestIpApp = "testApp";
        
        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("status", "success");
        Result<Map<String, Object>> expectedResult = Result.success(expectedData);
        
        when(configHandler.importAndPublishConfig(any(), any(), any(), any(), any(), any())).thenReturn(expectedResult);
        
        Result<Map<String, Object>> actualResult = configProxy.importAndPublishConfig(srcUser, namespaceId, policy,
                file, srcIp, requestIpApp);
        
        assertEquals(expectedResult, actualResult);
        verify(configHandler, times(1)).importAndPublishConfig(srcUser, namespaceId, policy, file, srcIp, requestIpApp);
    }
    
    @Test
    public void cloneConfig() throws NacosException {
        String srcUser = SRC_USER;
        String namespaceId = "testNamespace";
        List<SameNamespaceCloneConfigBean> configBeansList = new ArrayList<>();
        SameConfigPolicy policy = SameConfigPolicy.OVERWRITE;
        String srcIp = CLIENT_IP;
        String requestIpApp = "testApp";
        
        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("key", "value");
        Result<Map<String, Object>> expected = Result.success(expectedData);
        
        when(configHandler.cloneConfig(srcUser, namespaceId, configBeansList, policy, srcIp, requestIpApp)).thenReturn(
                expected);
        
        Result<Map<String, Object>> actual = configProxy.cloneConfig(srcUser, namespaceId, configBeansList, policy,
                srcIp, requestIpApp);
        
        assertEquals(expected, actual);
        verify(configHandler, times(1)).cloneConfig(srcUser, namespaceId, configBeansList, policy, srcIp, requestIpApp);
    }
    
    @Test
    public void queryBetaConfig() throws NacosException {
        ConfigGrayInfo expectedConfigGrayInfo = new ConfigGrayInfo();
        expectedConfigGrayInfo.setAppName("testApp");
        expectedConfigGrayInfo.setConfigTags(TAG);
        expectedConfigGrayInfo.setContent("testContent");
        
        when(configHandler.queryBetaConfig(DATA_ID_B, GROUP_B, NAMESPACE_ID_B)).thenReturn(expectedConfigGrayInfo);
        
        ConfigGrayInfo actualConfigGrayInfo = configProxy.queryBetaConfig(DATA_ID_B, GROUP_B, NAMESPACE_ID_B);
        
        assertEquals(expectedConfigGrayInfo, actualConfigGrayInfo);
        verify(configHandler, times(1)).queryBetaConfig(DATA_ID_B, GROUP_B, NAMESPACE_ID_B);
    }
    
    @Test
    public void removeBetaConfig() throws NacosException {
        String dataId = DATA_ID;
        String group = GROUP;
        String namespaceId = NAMESPACE_ID;
        String remoteIp = CLIENT_IP;
        String requestIpApp = "testApp";
        String srcUser = SRC_USER;
        
        when(configHandler.removeBetaConfig(dataId, group, namespaceId, remoteIp, requestIpApp, srcUser)).thenReturn(
                true);
        
        boolean result = configProxy.removeBetaConfig(dataId, group, namespaceId, remoteIp, requestIpApp, srcUser);
        
        assertTrue(result);
        verify(configHandler, times(1)).removeBetaConfig(dataId, group, namespaceId, remoteIp, requestIpApp, srcUser);
    }
}
