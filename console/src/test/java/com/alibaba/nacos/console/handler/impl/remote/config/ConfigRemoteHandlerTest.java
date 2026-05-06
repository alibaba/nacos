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

package com.alibaba.nacos.console.handler.impl.remote.config;

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
import com.alibaba.nacos.console.handler.impl.remote.AbstractRemoteHandlerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigRemoteHandlerTest extends AbstractRemoteHandlerTest {
    
    @Mock
    ConfigImportAndExportService importAndExportService;
    
    ConfigRemoteHandler configRemoteHandler;
    
    @BeforeEach
    void setUp() {
        super.setUpWithConfig();
        configRemoteHandler = new ConfigRemoteHandler(clientHolder, importAndExportService);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void getConfigListWithPattern() throws NacosException {
        Page<ConfigBasicInfo> mockPage = new Page<>();
        when(configMaintainerService.searchConfigByDetails(eq("dataId*"), eq("group"), eq("namespaceId"), eq("blur"),
                any(), any(), any(), any(), eq(1), eq(100))).thenReturn(mockPage);
        Page<ConfigBasicInfo> actual = configRemoteHandler.getConfigList(1, 100, "dataId*", "group", "namespaceId",
                new HashMap<>());
        assertEquals(mockPage, actual);
    }
    
    @Test
    void getConfigListWithoutPattern() throws NacosException {
        Page<ConfigBasicInfo> mockPage = new Page<>();
        when(configMaintainerService.searchConfigByDetails(eq("dataId"), eq("group"), eq("namespaceId"), eq("accurate"),
                any(), any(), any(), any(), eq(1), eq(100))).thenReturn(mockPage);
        Page<ConfigBasicInfo> actual = configRemoteHandler.getConfigList(1, 100, "dataId", "group", "namespaceId",
                new HashMap<>());
        assertEquals(mockPage, actual);
    }
    
    @Test
    void getConfigDetail() throws NacosException {
        ConfigDetailInfo mock = new ConfigDetailInfo();
        when(configMaintainerService.getConfig(eq("dataId"), eq("group"), eq("namespaceId"))).thenReturn(mock);
        ConfigDetailInfo actual = configRemoteHandler.getConfigDetail("dataId", "group", "namespaceId");
        assertEquals(mock, actual);
    }
    
    @Test
    void getConfigDetailNotFound() throws NacosException {
        when(configMaintainerService.getConfig(eq("dataId"), eq("group"), eq("namespaceId"))).thenThrow(
                new NacosException(404, "not found"));
        assertNull(configRemoteHandler.getConfigDetail("dataId", "group", "namespaceId"));
    }
    
    @Test
    void getConfigDetailWithException() throws NacosException {
        when(configMaintainerService.getConfig(eq("dataId"), eq("group"), eq("namespaceId"))).thenThrow(
                new NacosException(403, "test"));
        assertThrows(NacosException.class, () -> configRemoteHandler.getConfigDetail("dataId", "group", "namespaceId"));
    }
    
    @Test
    void publishConfig() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("dataId");
        configForm.setGroup("group");
        configForm.setNamespaceId("namespaceId");
        configForm.setContent("content");
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        when(configMaintainerService.publishConfig(eq("dataId"), eq("group"), eq("namespaceId"), eq("content"), any(),
                any(), any(), any(), any())).thenReturn(true);
        assertTrue(configRemoteHandler.publishConfig(configForm, configRequestInfo));
    }
    
    @Test
    void publishConfigBeta() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("dataId");
        configForm.setGroup("group");
        configForm.setNamespaceId("namespaceId");
        configForm.setContent("content");
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setBetaIps("127.0.0.1");
        when(configMaintainerService.publishBetaConfig(eq("dataId"), eq("group"), eq("namespaceId"), eq("content"),
                any(), any(), any(), any(), any(), eq("127.0.0.1"))).thenReturn(true);
        assertTrue(configRemoteHandler.publishConfig(configForm, configRequestInfo));
    }
    
    @Test
    void deleteConfig() throws NacosException {
        when(configMaintainerService.deleteConfig(eq("dataId"), eq("group"), eq("namespaceId"))).thenReturn(true);
        assertTrue(configRemoteHandler.deleteConfig("dataId", "group", "namespaceId", null, null, null));
    }
    
    @Test
    void batchDeleteConfigs() throws NacosException {
        when(configMaintainerService.deleteConfigs(any())).thenReturn(true);
        assertTrue(configRemoteHandler.batchDeleteConfigs(Collections.singletonList(1L), null, null));
    }
    
    @Test
    void getConfigListByContent() throws NacosException {
        Page<ConfigBasicInfo> mockPage = new Page<>();
        when(configMaintainerService.searchConfigByDetails(eq("dataId"), eq("group"), eq("namespaceId"), eq("blur"),
                eq("test"), any(), any(), any(), eq(1), eq(100))).thenReturn(mockPage);
        Page<ConfigBasicInfo> actual = configRemoteHandler.getConfigListByContent("blur", 1, 100, "dataId", "group",
                "namespaceId", Collections.singletonMap("content", "test"));
        assertEquals(mockPage, actual);
    }
    
    @Test
    void getListeners() throws Exception {
        ConfigListenerInfo mock = new ConfigListenerInfo();
        when(configMaintainerService.getListeners(eq("dataId"), eq("group"), eq("namespaceId"), eq(true))).thenReturn(
                mock);
        ConfigListenerInfo actual = configRemoteHandler.getListeners("dataId", "group", "namespaceId", true);
        assertEquals(mock, actual);
    }
    
    @Test
    void getAllSubClientConfigByIp() throws NacosException {
        ConfigListenerInfo mock = new ConfigListenerInfo();
        when(configMaintainerService.getAllSubClientConfigByIp(eq("127.0.0.1"), eq(true), eq("namespaceId"),
                eq(true))).thenReturn(mock);
        ConfigListenerInfo actual = configRemoteHandler.getAllSubClientConfigByIp("127.0.0.1", true, "namespaceId",
                true);
        assertEquals(mock, actual);
    }
    
    @Test
    void exportConfig() throws Exception {
        // remove lenient warning
        clientHolder.getConfigMaintainerService();
        ResponseEntity<byte[]> mock = new ResponseEntity<>(new byte[0], HttpStatus.OK);
        when(importAndExportService.exportConfig("dataId", "group", "namespaceId", "appName",
                Collections.singletonList(1L))).thenReturn(mock);
        ResponseEntity<byte[]> actual = configRemoteHandler.exportConfig("dataId", "group", "namespaceId", "appName",
                Collections.singletonList(1L));
        assertEquals(mock, actual);
    }
    
    @Test
    void importAndPublishConfig() throws NacosException {
        // remove lenient warning
        clientHolder.getConfigMaintainerService();
        Result<Map<String, Object>> mock = Result.success();
        when(importAndExportService.importConfig(any(), eq("namespaceId"), eq(SameConfigPolicy.OVERWRITE),
                any(MultipartFile.class), any(), any())).thenReturn(mock);
        MultipartFile mockFile = Mockito.mock(MultipartFile.class);
        Result<Map<String, Object>> actual = configRemoteHandler.importAndPublishConfig("srcUser", "namespaceId",
                SameConfigPolicy.OVERWRITE, mockFile, "srcIp", "requestIpApp");
        assertEquals(mock, actual);
    }
    
    @Test
    void cloneConfig() throws NacosException {
        SameNamespaceCloneConfigBean sameNamespaceCloneConfigBean = new SameNamespaceCloneConfigBean();
        sameNamespaceCloneConfigBean.setCfgId(1L);
        when(configMaintainerService.cloneConfig(eq("namespaceId"), any(), eq("srcUser"),
                eq(SameConfigPolicy.OVERWRITE))).thenReturn(Collections.singletonMap("1", 1));
        Result<Map<String, Object>> actual = configRemoteHandler.cloneConfig("srcUser", "namespaceId",
                Collections.singletonList(sameNamespaceCloneConfigBean), SameConfigPolicy.OVERWRITE, "srcIp",
                "requestIpApp");
        assertEquals(0, actual.getCode());
        assertEquals(1, actual.getData().size());
    }
    
    @Test
    void removeBetaConfig() throws NacosException {
        when(configMaintainerService.stopBeta(eq("dataId"), eq("group"), eq("namespaceId"))).thenReturn(true);
        assertTrue(configRemoteHandler.removeBetaConfig("dataId", "group", "namespaceId", "remoteIp", "requestIpApp",
                "srcUser"));
    }
    
    @Test
    void queryBetaConfig() throws NacosException {
        ConfigGrayInfo mock = new ConfigGrayInfo();
        when(configMaintainerService.queryBeta(eq("dataId"), eq("group"), eq("namespaceId"))).thenReturn(mock);
        ConfigGrayInfo actual = configRemoteHandler.queryBetaConfig("dataId", "group", "namespaceId");
        assertEquals(mock, actual);
    }
    
    @Test
    void queryBetaConfigNotFound() throws NacosException {
        when(configMaintainerService.queryBeta(eq("dataId"), eq("group"), eq("namespaceId"))).thenThrow(
                new NacosException(404, "test"));
        assertNull(configRemoteHandler.queryBetaConfig("dataId", "group", "namespaceId"));
    }
    
    @Test
    void queryBetaConfigWithException() throws NacosException {
        when(configMaintainerService.queryBeta(eq("dataId"), eq("group"), eq("namespaceId"))).thenThrow(
                new NacosException(403, "test"));
        assertThrows(NacosException.class, () -> configRemoteHandler.queryBetaConfig("dataId", "group", "namespaceId"));
    }
}