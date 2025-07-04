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

package com.alibaba.nacos.console.handler.impl.inner.config;

import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.config.model.ConfigGrayInfo;
import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.ConfigMetadata;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigMigrateService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.listener.ConfigListenerStateDelegate;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.YamlParserUtil;
import com.alibaba.nacos.config.server.utils.ZipUtils;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.sys.env.EnvUtil;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigInnerHandlerTest {
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private ConfigDetailService configDetailService;
    
    @Mock
    private ConfigListenerStateDelegate configListenerStateDelegate;
    
    @Mock
    private ConfigMigrateService configMigrateService;
    
    @Mock
    private NamespacePersistService namespacePersistService;
    
    @Mock
    private ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    @Mock
    private ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    ConfigInnerHandler configInnerHandler;
    
    private boolean cachedGrayCompatibleModel;
    
    private ConfigurableEnvironment cachedEnv;
    
    @BeforeEach
    void setUp() {
        cachedEnv = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new MockEnvironment());
        cachedGrayCompatibleModel = PropertyUtil.isGrayCompatibleModel();
        configInnerHandler = new ConfigInnerHandler(configOperationService, configInfoPersistService,
                configDetailService, namespacePersistService, configInfoBetaPersistService,
                configInfoGrayPersistService, configListenerStateDelegate, configMigrateService);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnv);
        PropertyUtil.setGrayCompatibleModel(cachedGrayCompatibleModel);
        ReflectionTestUtils.setField(configInnerHandler, "oldTableVersion", false);
    }
    
    @Test
    void getConfigList() throws ServletException, IOException, NacosException {
        Page<ConfigInfo> mockPage = new Page<>();
        mockPage.setTotalCount(1);
        mockPage.setPagesAvailable(1);
        mockPage.setPageItems(List.of(mockConfigInfo()));
        mockPage.setPageNumber(1);
        when(configInfoPersistService.findConfigInfoLike4Page(1, 10, "dataId", "group", "tenant",
                new HashMap<>())).thenReturn(mockPage);
        Page<ConfigBasicInfo> actual = configInnerHandler.getConfigList(1, 10, "dataId", "group", "tenant",
                new HashMap<>());
        assertEquals(1, actual.getTotalCount());
        assertEquals(1, actual.getPagesAvailable());
        assertEquals(1, actual.getPageNumber());
        assertEquals(mockPage.getPageItems().get(0).getId(), actual.getPageItems().get(0).getId());
        assertEquals(mockPage.getPageItems().get(0).getDataId(), actual.getPageItems().get(0).getDataId());
        assertEquals(mockPage.getPageItems().get(0).getGroup(), actual.getPageItems().get(0).getGroupName());
        assertEquals(mockPage.getPageItems().get(0).getTenant(), actual.getPageItems().get(0).getNamespaceId());
    }
    
    @Test
    void getConfigDetail() throws NacosException {
        ConfigAllInfo mockConfig = mockConfigAllInfo();
        when(configInfoPersistService.findConfigAllInfo("dataId", "group", "tenant")).thenReturn(mockConfig);
        ConfigDetailInfo actual = configInnerHandler.getConfigDetail("dataId", "group", "tenant");
        assertNotNull(actual);
        assertEquals(mockConfig.getId(), actual.getId());
        assertEquals(mockConfig.getDataId(), actual.getDataId());
        assertEquals(mockConfig.getGroup(), actual.getGroupName());
        assertEquals(mockConfig.getTenant(), actual.getNamespaceId());
        assertEquals(mockConfig.getContent(), actual.getContent());
    }
    
    @Test
    void getConfigDetailNotFound() throws NacosException {
        assertNull(configInnerHandler.getConfigDetail("dataId", "group", "tenant"));
    }
    
    @Test
    void publishConfig() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("dataId");
        configForm.setContent("content");
        configForm.setEncryptedDataKey("");
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        when(configOperationService.publishConfig(configForm, configRequestInfo, "")).thenReturn(true);
        assertTrue(configInnerHandler.publishConfig(configForm, configRequestInfo));
    }
    
    @Test
    void deleteConfig() throws NacosException {
        when(configOperationService.deleteConfig("dataId", "group", "tenant", "", "clientIp", "srcUser",
                Constants.HTTP)).thenReturn(true);
        assertTrue(configInnerHandler.deleteConfig("dataId", "group", "tenant", "", "clientIp", "srcUser"));
    }
    
    @Test
    void batchDeleteConfigs() {
        when(configInfoPersistService.findConfigInfo(1L)).thenReturn(mockConfigInfo());
        assertTrue(configInnerHandler.batchDeleteConfigs(List.of(1L, 2L), "clientIp", "srcUser"));
        verify(configOperationService).deleteConfig(anyString(), anyString(), anyString(), any(), anyString(),
                anyString(), anyString());
    }
    
    @Test
    void getConfigListByContent() throws NacosException {
        Page<ConfigInfo> mockPage = new Page<>();
        mockPage.setTotalCount(1);
        mockPage.setPagesAvailable(1);
        mockPage.setPageItems(List.of(mockConfigInfo()));
        mockPage.setPageNumber(1);
        when(configDetailService.findConfigInfoPage("blur", 1, 10, "dataId", "group", "tenant",
                new HashMap<>())).thenReturn(mockPage);
        Page<ConfigBasicInfo> actual = configInnerHandler.getConfigListByContent("blur", 1, 10, "dataId", "group",
                "tenant", new HashMap<>());
        assertEquals(1, actual.getTotalCount());
        assertEquals(1, actual.getPagesAvailable());
        assertEquals(1, actual.getPageNumber());
        assertEquals(mockPage.getPageItems().get(0).getId(), actual.getPageItems().get(0).getId());
        assertEquals(mockPage.getPageItems().get(0).getDataId(), actual.getPageItems().get(0).getDataId());
        assertEquals(mockPage.getPageItems().get(0).getGroup(), actual.getPageItems().get(0).getGroupName());
        assertEquals(mockPage.getPageItems().get(0).getTenant(), actual.getPageItems().get(0).getNamespaceId());
    }
    
    @Test
    void getConfigListByContentWithException() throws NacosException {
        when(configDetailService.findConfigInfoPage("blur", 1, 10, "dataId", "group", "tenant",
                new HashMap<>())).thenThrow(new NacosRuntimeException(500, "test"));
        assertThrows(NacosRuntimeException.class,
                () -> configInnerHandler.getConfigListByContent("blur", 1, 10, "dataId", "group", "tenant",
                        new HashMap<>()));
    }
    
    @Test
    void getListeners() throws Exception {
        ConfigListenerInfo mock = new ConfigListenerInfo();
        when(configListenerStateDelegate.getListenerState("dataId", "group", "tenant", true)).thenReturn(mock);
        assertEquals(mock, configInnerHandler.getListeners("dataId", "group", "tenant", true));
    }
    
    @Test
    void getAllSubClientConfigByIpEmpty() {
        ConfigListenerInfo mock = new ConfigListenerInfo();
        when(configListenerStateDelegate.getListenerStateByIp("127.0.0.1", true)).thenReturn(mock);
        assertEquals(mock, configInnerHandler.getAllSubClientConfigByIp("127.0.0.1", true, "tenant", true));
    }
    
    @Test
    void getAllSubClientConfigByIpWithAll() {
        ConfigListenerInfo mock = new ConfigListenerInfo();
        Map<String, String> configMd5Status = new HashMap<>();
        configMd5Status.put("dataId+group+tenant", "1");
        configMd5Status.put("dataId+group+aaa", "2");
        mock.setListenersStatus(configMd5Status);
        when(configListenerStateDelegate.getListenerStateByIp("127.0.0.1", false)).thenReturn(mock);
        ConfigListenerInfo actual = configInnerHandler.getAllSubClientConfigByIp("127.0.0.1", true, "tenant", false);
        assertEquals(ConfigListenerInfo.QUERY_TYPE_IP, actual.getQueryType());
        assertEquals(2, actual.getListenersStatus().size());
        assertEquals("1", actual.getListenersStatus().get("dataId+group+tenant"));
        assertEquals("2", actual.getListenersStatus().get("dataId+group+aaa"));
    }
    
    @Test
    void getAllSubClientConfigByIpWithoutAllEmptyNamespace() {
        ConfigListenerInfo mock = new ConfigListenerInfo();
        Map<String, String> configMd5Status = new HashMap<>();
        configMd5Status.put("dataId+group", "1");
        configMd5Status.put("dataId+group+aaa", "2");
        mock.setListenersStatus(configMd5Status);
        when(configListenerStateDelegate.getListenerStateByIp("127.0.0.1", false)).thenReturn(mock);
        ConfigListenerInfo actual = configInnerHandler.getAllSubClientConfigByIp("127.0.0.1", false, "", false);
        assertEquals(ConfigListenerInfo.QUERY_TYPE_IP, actual.getQueryType());
        assertEquals(1, actual.getListenersStatus().size());
        assertEquals("1", actual.getListenersStatus().get("dataId+group"));
    }
    
    @Test
    void getAllSubClientConfigByIpWithoutAllTargetNamespace() {
        final ConfigListenerInfo mock = new ConfigListenerInfo();
        Map<String, String> configMd5Status = new HashMap<>();
        configMd5Status.put("dataId+group", "1");
        configMd5Status.put("dataId+group+aaa", "2");
        configMd5Status.put("dataId+group+tenant", "3");
        mock.setListenersStatus(configMd5Status);
        when(configListenerStateDelegate.getListenerStateByIp("127.0.0.1", false)).thenReturn(mock);
        ConfigListenerInfo actual = configInnerHandler.getAllSubClientConfigByIp("127.0.0.1", false, "aaa", false);
        assertEquals(ConfigListenerInfo.QUERY_TYPE_IP, actual.getQueryType());
        assertEquals(2, actual.getListenersStatus().size());
        assertEquals("1", actual.getListenersStatus().get("dataId+group"));
        assertEquals("2", actual.getListenersStatus().get("dataId+group+aaa"));
    }
    
    @Test
    void exportConfig() throws Exception {
        List<ConfigAllInfo> mockList = Collections.singletonList(mockConfigAllInfo());
        when(configInfoPersistService.findAllConfigInfo4Export("dataId", "group", "tenant", "appName",
                Collections.singletonList(1L))).thenReturn(mockList);
        ResponseEntity<byte[]> actual = configInnerHandler.exportConfig("dataId", "group", "tenant", "appName",
                Collections.singletonList(1L));
        assertNotNull(actual);
        assertEquals(HttpStatus.OK, actual.getStatusCode());
        assertTrue(actual.getHeaders().containsKey("Content-Disposition"));
    }
    
    @Test
    void importAndPublishConfigWithEmptyFile() throws NacosException {
        Result<Map<String, Object>> actual = configInnerHandler.importAndPublishConfig("srcUser", "tenant",
                SameConfigPolicy.OVERWRITE, null, "srcIp", "requestIpApp");
        assertEquals(ErrorCode.DATA_EMPTY.getCode(), actual.getCode());
    }
    
    @Test
    void importAndPublishConfigWithNonExistNamespace() throws NacosException {
        MultipartFile mockFile = Mockito.mock(MultipartFile.class);
        Result<Map<String, Object>> actual = configInnerHandler.importAndPublishConfig("srcUser", "tenant",
                SameConfigPolicy.OVERWRITE, mockFile, "srcIp", "requestIpApp");
        assertEquals(ErrorCode.NAMESPACE_NOT_EXIST.getCode(), actual.getCode());
    }
    
    @Test
    void importAndPublishConfigWithUnzipException() throws NacosException, IOException {
        MultipartFile mockFile = Mockito.mock(MultipartFile.class);
        when(mockFile.getBytes()).thenThrow(new IOException());
        Result<Map<String, Object>> actual = configInnerHandler.importAndPublishConfig("srcUser", "public",
                SameConfigPolicy.OVERWRITE, mockFile, "srcIp", "requestIpApp");
        assertEquals(ErrorCode.PARSING_DATA_FAILED.getCode(), actual.getCode());
    }
    
    @Test
    void importAndPublishConfigWithoutMetadata() throws NacosException {
        ZipUtils.UnZipResult unziped = mockZipFile(false, true, false, false);
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", "test".getBytes());
        try (MockedStatic<ZipUtils> zipUtilsMockedStatic = Mockito.mockStatic(ZipUtils.class)) {
            zipUtilsMockedStatic.when(() -> ZipUtils.unzip(eq(file.getBytes()))).thenReturn(unziped);
            Result<Map<String, Object>> actual = configInnerHandler.importAndPublishConfig("srcUser", "public",
                    SameConfigPolicy.OVERWRITE, file, "srcIp", "requestIpApp");
            assertEquals(ErrorCode.METADATA_ILLEGAL.getCode(), actual.getCode());
        }
    }
    
    @Test
    void importAndPublishConfigWithWrongMetadata() throws NacosException {
        ZipUtils.UnZipResult unziped = mockZipFile(true, false, false, false);
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", "test".getBytes());
        try (MockedStatic<ZipUtils> zipUtilsMockedStatic = Mockito.mockStatic(ZipUtils.class)) {
            zipUtilsMockedStatic.when(() -> ZipUtils.unzip(eq(file.getBytes()))).thenReturn(unziped);
            Result<Map<String, Object>> actual = configInnerHandler.importAndPublishConfig("srcUser", "public",
                    SameConfigPolicy.OVERWRITE, file, "srcIp", "requestIpApp");
            assertEquals(ErrorCode.METADATA_ILLEGAL.getCode(), actual.getCode());
        }
    }
    
    @Test
    void importAndPublishConfigWithEmptyData() throws NacosException {
        ZipUtils.UnZipResult unziped = mockZipFile(true, true, false, true);
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", "test".getBytes());
        try (MockedStatic<ZipUtils> zipUtilsMockedStatic = Mockito.mockStatic(ZipUtils.class)) {
            zipUtilsMockedStatic.when(() -> ZipUtils.unzip(eq(file.getBytes()))).thenReturn(unziped);
            Result<Map<String, Object>> actual = configInnerHandler.importAndPublishConfig("srcUser", "public",
                    SameConfigPolicy.OVERWRITE, file, "srcIp", "requestIpApp");
            assertEquals(ErrorCode.DATA_EMPTY.getCode(), actual.getCode());
        }
    }
    
    @Test
    void importAndPublishConfig() throws NacosException {
        ZipUtils.UnZipResult unziped = mockZipFile(true, true, false, false);
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", "test".getBytes());
        try (MockedStatic<ZipUtils> zipUtilsMockedStatic = Mockito.mockStatic(ZipUtils.class)) {
            zipUtilsMockedStatic.when(() -> ZipUtils.unzip(eq(file.getBytes()))).thenReturn(unziped);
            when(configInfoPersistService.batchInsertOrUpdate(any(), any(), any(), any(), any())).thenReturn(
                    Collections.singletonMap("dataId23456.json+group132", true));
            Result<Map<String, Object>> actual = configInnerHandler.importAndPublishConfig("srcUser", "public",
                    SameConfigPolicy.OVERWRITE, file, "srcIp", "requestIpApp");
            assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
            assertTrue(actual.getData().containsKey("dataId23456.json+group132"));
        }
    }
    
    @Test
    void importAndPublishConfigWithUnrecognizedItem() throws NacosException {
        ZipUtils.UnZipResult unziped = mockZipFile(true, true, true, false);
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", "test".getBytes());
        try (MockedStatic<ZipUtils> zipUtilsMockedStatic = Mockito.mockStatic(ZipUtils.class)) {
            zipUtilsMockedStatic.when(() -> ZipUtils.unzip(eq(file.getBytes()))).thenReturn(unziped);
            HashMap<String, Object> result = new HashMap<>();
            result.put("dataId23456.json+group132", true);
            when(configInfoPersistService.batchInsertOrUpdate(any(), any(), any(), any(), any())).thenReturn(result);
            Result<Map<String, Object>> actual = configInnerHandler.importAndPublishConfig("srcUser", "public",
                    SameConfigPolicy.OVERWRITE, file, "srcIp", "requestIpApp");
            assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
            assertTrue(actual.getData().containsKey("dataId23456.json+group132"));
            assertTrue(actual.getData().containsKey("unrecognizedCount"));
            assertEquals(3, actual.getData().get("unrecognizedCount"));
        }
    }
    
    private ZipUtils.UnZipResult mockZipFile(boolean containsMetadata, boolean correctMetadata,
            boolean withUnrecognizedItem, boolean emptyZip) {
        List<ZipUtils.ZipItem> zipItems = new ArrayList<>();
        String dataId = "dataId23456.json";
        String group = "group132";
        if (!emptyZip) {
            String content = "content1234";
            ZipUtils.ZipItem zipItem = new ZipUtils.ZipItem(group + "/" + dataId, content);
            zipItems.add(zipItem);
            if (withUnrecognizedItem) {
                zipItem = new ZipUtils.ZipItem("testGroup/testDataId", content);
                zipItems.add(zipItem);
                zipItem = new ZipUtils.ZipItem("illegalGroup/a/testDataId", content);
                zipItems.add(zipItem);
                zipItem = new ZipUtils.ZipItem("illegalGroup/testDataId", null);
                zipItems.add(zipItem);
            }
        }
        ConfigMetadata configMetadata = new ConfigMetadata();
        configMetadata.setMetadata(new ArrayList<>());
        if (containsMetadata) {
            ConfigMetadata.ConfigExportItem configExportItem = new ConfigMetadata.ConfigExportItem();
            configExportItem.setDataId(dataId);
            configExportItem.setGroup(group);
            configExportItem.setType(correctMetadata ? "json" : "");
            configExportItem.setAppName("appna123");
            configMetadata.getMetadata().add(configExportItem);
            if (withUnrecognizedItem) {
                configExportItem = new ConfigMetadata.ConfigExportItem();
                configExportItem.setDataId("testDataId");
                configExportItem.setGroup("illegalGroup");
                configExportItem.setType("json");
                configExportItem.setAppName("appna123");
                configMetadata.getMetadata().add(configExportItem);
            }
        }
        return new ZipUtils.UnZipResult(zipItems,
                new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA_NEW, YamlParserUtil.dumpObject(configMetadata)));
    }
    
    @Test
    void cloneConfigWithNoSelectedConfig() throws NacosException {
        Result<Map<String, Object>> actual = configInnerHandler.cloneConfig("srcUser", "public",
                Collections.emptyList(), SameConfigPolicy.OVERWRITE, "srcIp", "requestIpApp");
        assertEquals(ErrorCode.NO_SELECTED_CONFIG.getCode(), actual.getCode());
    }
    
    @Test
    void cloneConfigWithNamespaceNotExist() throws NacosException {
        SameNamespaceCloneConfigBean configBean = new SameNamespaceCloneConfigBean();
        Result<Map<String, Object>> actual = configInnerHandler.cloneConfig("srcUser", "tenant",
                Collections.singletonList(configBean), SameConfigPolicy.OVERWRITE, "srcIp", "requestIpApp");
        assertEquals(ErrorCode.NAMESPACE_NOT_EXIST.getCode(), actual.getCode());
    }
    
    @Test
    void cloneConfigWithDataEmpty() throws NacosException {
        SameNamespaceCloneConfigBean configBean = new SameNamespaceCloneConfigBean();
        configBean.setCfgId(1L);
        when(namespacePersistService.tenantInfoCountByTenantId("tenant")).thenReturn(1);
        Result<Map<String, Object>> actual = configInnerHandler.cloneConfig("srcUser", "tenant",
                Collections.singletonList(configBean), SameConfigPolicy.OVERWRITE, "srcIp", "requestIpApp");
        assertEquals(ErrorCode.DATA_EMPTY.getCode(), actual.getCode());
    }
    
    @Test
    void cloneConfig() throws NacosException {
        List<SameNamespaceCloneConfigBean> configBeansList = new ArrayList<>();
        SameNamespaceCloneConfigBean configBean = new SameNamespaceCloneConfigBean();
        configBean.setCfgId(1L);
        configBeansList.add(configBean);
        configBean = new SameNamespaceCloneConfigBean();
        configBean.setCfgId(1L);
        configBeansList.add(configBean);
        when(configInfoPersistService.findAllConfigInfo4Export(isNull(), isNull(), isNull(), isNull(),
                anyList())).thenReturn(Collections.singletonList(mockConfigAllInfo()));
        when(configInfoPersistService.batchInsertOrUpdate(any(), any(), any(), any(), any())).thenReturn(
                Collections.singletonMap("dataId23456.json+group132", true));
        Result<Map<String, Object>> actual = configInnerHandler.cloneConfig("srcUser", "public", configBeansList,
                SameConfigPolicy.OVERWRITE, "srcIp", "requestIpApp");
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(1, actual.getData().size());
    }

    @Test
    void removeBetaConfigWithGrayCompatibleModelAndOldTableVersion() {
        PropertyUtil.setGrayCompatibleModel(true);
        ReflectionTestUtils.setField(configInnerHandler, "oldTableVersion", true);
        assertTrue(configInnerHandler.removeBetaConfig("dataId", "group", "tenant", "remoteIp", "requestIpApp",
                "srcUser"));
        verify(configInfoGrayPersistService).removeConfigInfoGray("dataId", "group", "tenant", BetaGrayRule.TYPE_BETA,
                "remoteIp", "srcUser");
        verify(configMigrateService).removeConfigInfoGrayMigrate("dataId", "group", "tenant", BetaGrayRule.TYPE_BETA,
                "remoteIp", "srcUser");
        verify(configInfoBetaPersistService).removeConfigInfo4Beta("dataId", "group", "tenant");
    }

    @Test
    void removeBetaConfigWithGrayCompatibleModelAndLatestTableVersion() {
        PropertyUtil.setGrayCompatibleModel(true);
        ReflectionTestUtils.setField(configInnerHandler, "oldTableVersion", false);
        assertTrue(configInnerHandler.removeBetaConfig("dataId", "group", "tenant", "remoteIp", "requestIpApp",
                "srcUser"));
        verify(configInfoGrayPersistService).removeConfigInfoGray("dataId", "group", "tenant", BetaGrayRule.TYPE_BETA,
                "remoteIp", "srcUser");
        verify(configMigrateService).removeConfigInfoGrayMigrate("dataId", "group", "tenant", BetaGrayRule.TYPE_BETA,
                "remoteIp", "srcUser");
        verify(configInfoBetaPersistService, never()).removeConfigInfo4Beta("dataId", "group", "tenant");
    }

    @Test
    void removeBetaConfigWithoutGrayCompatibleModel() {
        PropertyUtil.setGrayCompatibleModel(false);
        assertTrue(configInnerHandler.removeBetaConfig("dataId", "group", "tenant", "remoteIp", "requestIpApp",
                "srcUser"));
        verify(configInfoGrayPersistService).removeConfigInfoGray("dataId", "group", "tenant", BetaGrayRule.TYPE_BETA,
                "remoteIp", "srcUser");
        verify(configMigrateService).removeConfigInfoGrayMigrate("dataId", "group", "tenant", BetaGrayRule.TYPE_BETA,
                "remoteIp", "srcUser");
        verify(configInfoBetaPersistService, never()).removeConfigInfo4Beta("dataId", "group", "tenant");
    }
    
    @Test
    void removeBetaConfigWithException() {
        doThrow(new NacosRuntimeException(0, "test")).when(configInfoGrayPersistService)
                .removeConfigInfoGray("dataId", "group", "tenant", BetaGrayRule.TYPE_BETA, "remoteIp", "srcUser");
        assertFalse(configInnerHandler.removeBetaConfig("dataId", "group", "tenant", "remoteIp", "requestIpApp",
                "srcUser"));
    }
    
    @Test
    void queryBetaConfigNonExist() throws NacosException {
        assertNull(configInnerHandler.queryBetaConfig("dataId", "group", "tenant"));
    }
    
    @Test
    void queryBetaConfigExist() throws NacosException {
        ConfigInfoGrayWrapper mockConfigInfo = new ConfigInfoGrayWrapper();
        mockConfigInfo.setId(1L);
        mockConfigInfo.setDataId("dataId");
        mockConfigInfo.setGroup("group");
        mockConfigInfo.setTenant("tenant");
        mockConfigInfo.setContent("content");
        mockConfigInfo.setType("type");
        mockConfigInfo.setAppName("appName");
        mockConfigInfo.setMd5("md5");
        mockConfigInfo.setEncryptedDataKey("");
        mockConfigInfo.setGrayName("beta");
        when(configInfoGrayPersistService.findConfigInfo4Gray("dataId", "group", "tenant", "beta")).thenReturn(
                mockConfigInfo);
        assertNotNull(configInnerHandler.queryBetaConfig("dataId", "group", "tenant"));
    }

    @Test
    void queryBetaConfigWithTypeFieldFromProductionConfig() throws NacosException {
        ConfigInfoGrayWrapper mockBetaConfigInfo = new ConfigInfoGrayWrapper();
        mockBetaConfigInfo.setId(1L);
        mockBetaConfigInfo.setDataId("dataId");
        mockBetaConfigInfo.setGroup("group");
        mockBetaConfigInfo.setTenant("tenant");
        mockBetaConfigInfo.setContent("content");
        mockBetaConfigInfo.setType(null);
        mockBetaConfigInfo.setAppName("appName");
        mockBetaConfigInfo.setMd5("md5");
        mockBetaConfigInfo.setEncryptedDataKey("");
        mockBetaConfigInfo.setGrayName("beta");
        when(configInfoGrayPersistService.findConfigInfo4Gray("dataId", "group", "tenant", "beta")).thenReturn(
                mockBetaConfigInfo);

        ConfigInfoWrapper mockConfigInfo = new ConfigInfoWrapper();
        mockConfigInfo.setId(1L);
        mockConfigInfo.setDataId("dataId");
        mockConfigInfo.setGroup("group");
        mockConfigInfo.setTenant("tenant");
        mockConfigInfo.setContent("content");
        mockConfigInfo.setType("type");
        mockConfigInfo.setAppName("appName");
        mockConfigInfo.setMd5("md5");
        mockConfigInfo.setEncryptedDataKey("");
        when(configInfoPersistService.findConfigInfo("dataId", "group", "tenant")).thenReturn(
                mockConfigInfo);

        ConfigGrayInfo grayInfo = configInnerHandler.queryBetaConfig("dataId", "group", "tenant");
        assertNotNull(grayInfo);
        assertEquals("type", grayInfo.getType());
    }
    
    private ConfigInfo mockConfigInfo() {
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(1L);
        configInfo.setDataId("dataId");
        configInfo.setGroup("group");
        configInfo.setTenant("tenant");
        configInfo.setContent("content");
        configInfo.setType("type");
        configInfo.setAppName("appName");
        configInfo.setMd5("md5");
        configInfo.setEncryptedDataKey("");
        return configInfo;
    }
    
    private ConfigAllInfo mockConfigAllInfo() {
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setId(1L);
        configAllInfo.setDataId("dataId");
        configAllInfo.setGroup("group");
        configAllInfo.setTenant("tenant");
        configAllInfo.setContent("content");
        configAllInfo.setType("type");
        configAllInfo.setAppName("appName");
        configAllInfo.setMd5("md5");
        configAllInfo.setEncryptedDataKey("");
        configAllInfo.setDesc("desc");
        configAllInfo.setCreateIp("createIp");
        configAllInfo.setCreateUser("createUser");
        configAllInfo.setCreateTime(System.currentTimeMillis());
        configAllInfo.setModifyTime(System.currentTimeMillis());
        return configAllInfo;
    }
}