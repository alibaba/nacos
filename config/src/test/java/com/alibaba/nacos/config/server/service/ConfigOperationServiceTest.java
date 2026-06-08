/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.model.gray.GrayRule;
import com.alibaba.nacos.config.server.model.gray.GrayRuleManager;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ConfigServiceTest.
 *
 * @author dongyafei
 * @date 2022/8/11
 */

@ExtendWith(MockitoExtension.class)
class ConfigOperationServiceTest {
    
    private ConfigOperationService configOperationService;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    @BeforeEach
    void setUp() throws Exception {
        EnvUtil.setEnvironment(new StandardEnvironment());
        this.configOperationService =
            new ConfigOperationService(configInfoPersistService, configInfoGrayPersistService);
    }
    
    @Test
    void testPublishConfigBeta() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        
        configRequestInfo.setCasMd5("");
        configForm.setTag("");
        
        // if betaIps is not blank and casMd5 is blank
        configRequestInfo.setBetaIps("test-betaIps");
        
        when(configInfoGrayPersistService.insertOrUpdateGray(any(ConfigInfo.class), eq("beta"),
            anyString(),
            eq(configRequestInfo.getSrcIp()), eq(configForm.getSrcUser())))
            .thenReturn(new ConfigOperateResult());
        Boolean eResult = configOperationService.publishConfig(configForm, configRequestInfo, "");
        assertTrue(eResult);
        
    }
    
    @Test
    void testPublishConfigBetaCas() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        
        configRequestInfo.setCasMd5("casMd5");
        configForm.setTag("");
        
        // if betaIps is not blank and casMd5 is not blank
        configRequestInfo.setBetaIps("test-betaIps");
        configRequestInfo.setCasMd5("test casMd5");
        
        when(configInfoGrayPersistService.insertOrUpdateGrayCas(any(ConfigInfo.class), eq("beta"),
            anyString(),
            eq(configRequestInfo.getSrcIp()), eq(configForm.getSrcUser())))
            .thenReturn(new ConfigOperateResult());
        Boolean fResult = configOperationService.publishConfig(configForm, configRequestInfo, "");
        assertTrue(fResult);
    }
    
    @Test
    void testPublishConfigBetaCasWithMd5Set() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        String expectedCasMd5 = "test-cas-md5-value";
        configRequestInfo.setBetaIps("test-betaIps");
        configRequestInfo.setCasMd5(expectedCasMd5);
        configForm.setTag("");
        
        // Use ArgumentCaptor to capture the ConfigInfo object passed to insertOrUpdateGrayCas
        ArgumentCaptor<ConfigInfo> configInfoCaptor = ArgumentCaptor.forClass(ConfigInfo.class);
        
        when(configInfoGrayPersistService.insertOrUpdateGrayCas(configInfoCaptor.capture(),
            eq("beta"), anyString(),
            eq(configRequestInfo.getSrcIp()), eq(configForm.getSrcUser())))
            .thenReturn(new ConfigOperateResult());
        
        Boolean result = configOperationService.publishConfig(configForm, configRequestInfo, "");
        
        assertTrue(result);
        
        // Verify that the md5 field of ConfigInfo is correctly set to the casMd5 value
        ConfigInfo capturedConfigInfo = configInfoCaptor.getValue();
        assertEquals(expectedCasMd5, capturedConfigInfo.getMd5(),
            "ConfigInfo's md5 should be set to casMd5 value");
        assertEquals("test", capturedConfigInfo.getDataId());
        assertEquals("test", capturedConfigInfo.getGroup());
        assertEquals("test content", capturedConfigInfo.getContent());
    }
    
    @Test
    void testPublishConfigTag() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        
        configRequestInfo.setCasMd5("");
        String tag = "testTag";
        configForm.setTag(tag);
        
        when(configInfoGrayPersistService.insertOrUpdateGray(any(ConfigInfo.class),
            eq("tag_" + tag), anyString(),
            eq(configRequestInfo.getSrcIp()), eq(configForm.getSrcUser())))
            .thenReturn(new ConfigOperateResult());
        Boolean cResult = configOperationService.publishConfig(configForm, configRequestInfo, "");
        assertTrue(cResult);
        
    }
    
    @Test
    void testPublishConfigTagCas() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        
        configRequestInfo.setCasMd5("casMd5");
        String tag = "testTag";
        configForm.setTag(tag);
        
        when(configInfoGrayPersistService.insertOrUpdateGrayCas(any(ConfigInfo.class),
            eq("tag_" + tag), anyString(),
            eq(configRequestInfo.getSrcIp()), eq(configForm.getSrcUser())))
            .thenReturn(new ConfigOperateResult());
        Boolean dResult = configOperationService.publishConfig(configForm, configRequestInfo, "");
        assertTrue(dResult);
    }
    
    @Test
    void testPublishConfigTagCasWithMd5Set() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        String expectedCasMd5 = "test-cas-md5-value";
        String tag = "testTag";
        configRequestInfo.setCasMd5(expectedCasMd5);
        configForm.setTag(tag);
        
        // Use ArgumentCaptor to capture the ConfigInfo object passed to insertOrUpdateGrayCas
        ArgumentCaptor<ConfigInfo> configInfoCaptor = ArgumentCaptor.forClass(ConfigInfo.class);
        
        when(configInfoGrayPersistService.insertOrUpdateGrayCas(configInfoCaptor.capture(),
            eq("tag_" + tag), anyString(),
            eq(configRequestInfo.getSrcIp()), eq(configForm.getSrcUser())))
            .thenReturn(new ConfigOperateResult());
        
        Boolean result = configOperationService.publishConfig(configForm, configRequestInfo, "");
        
        assertTrue(result);
        
        // Verify that the md5 field of ConfigInfo is correctly set to the casMd5 value
        ConfigInfo capturedConfigInfo = configInfoCaptor.getValue();
        assertEquals(expectedCasMd5, capturedConfigInfo.getMd5(),
            "ConfigInfo's md5 should be set to casMd5 value");
        assertEquals("test", capturedConfigInfo.getDataId());
        assertEquals("test", capturedConfigInfo.getGroup());
        assertEquals("test content", capturedConfigInfo.getContent());
    }
    
    @Test
    void testPublishConfig() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        
        // if betaIps is blank, tag is blank and casMd5 is blank
        when(configInfoPersistService.insertOrUpdate(any(), any(), any(ConfigInfo.class), any()))
            .thenReturn(
                new ConfigOperateResult());
        Boolean aResult = configOperationService.publishConfig(configForm, configRequestInfo, "");
        verify(configInfoPersistService).insertOrUpdate(any(), any(), any(ConfigInfo.class), any());
        assertTrue(aResult);
        
        // if betaIps is blank, tag is blank and casMd5 is not blank
        configRequestInfo.setCasMd5("test casMd5");
        when(configInfoPersistService.insertOrUpdateCas(any(), any(), any(ConfigInfo.class), any()))
            .thenReturn(
                new ConfigOperateResult());
        Boolean bResult = configOperationService.publishConfig(configForm, configRequestInfo, "");
        verify(configInfoPersistService).insertOrUpdateCas(any(), any(), any(ConfigInfo.class),
            any());
        assertTrue(bResult);
        configRequestInfo.setCasMd5("");
    }
    
    @Test
    void testPublishConfigNormalizesNullNamespaceIdToDefault() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setNamespaceId(null);
        configForm.setContent("test content");
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        ArgumentCaptor<ConfigInfo> configInfoCaptor = ArgumentCaptor.forClass(ConfigInfo.class);
        when(configInfoPersistService.insertOrUpdate(any(), any(), configInfoCaptor.capture(),
            any()))
            .thenReturn(new ConfigOperateResult());
        
        Boolean result = configOperationService.publishConfig(configForm, configRequestInfo, "");
        
        assertTrue(result);
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, configInfoCaptor.getValue().getTenant());
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, configForm.getNamespaceId());
    }
    
    @Test
    void testPublishConfigNormalizesBlankNamespaceIdThroughNamespaceUtil() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setNamespaceId("   ");
        configForm.setContent("test content");
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        ArgumentCaptor<ConfigInfo> configInfoCaptor = ArgumentCaptor.forClass(ConfigInfo.class);
        when(configInfoPersistService.insertOrUpdate(any(), any(), configInfoCaptor.capture(),
            any()))
            .thenReturn(new ConfigOperateResult());
        
        Boolean result = configOperationService.publishConfig(configForm, configRequestInfo, "");
        
        assertTrue(result);
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, configInfoCaptor.getValue().getTenant());
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, configForm.getNamespaceId());
    }
    
    @Test
    void testPublishConfigNormalizesExplicitEmptyNamespaceIdToDefault() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setNamespaceId("");
        configForm.setContent("test content");
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        ArgumentCaptor<ConfigInfo> configInfoCaptor = ArgumentCaptor.forClass(ConfigInfo.class);
        when(configInfoPersistService.insertOrUpdate(any(), any(), configInfoCaptor.capture(),
            any()))
            .thenReturn(new ConfigOperateResult());
        
        Boolean result = configOperationService.publishConfig(configForm, configRequestInfo, "");
        
        assertTrue(result);
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, configInfoCaptor.getValue().getTenant());
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, configForm.getNamespaceId());
    }
    
    @Test
    void testUpdateForExistTrue() throws Exception {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("testDataId");
        configForm.setGroup("testGroup");
        configForm.setNamespaceId("testNamespaceId");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setSrcType("http");
        configRequestInfo.setSrcIp("1.1.1.1");
        
        when(configInfoPersistService.insertOrUpdate(anyString(), isNull(), any(ConfigInfo.class),
            anyMap()))
            .thenReturn(new ConfigOperateResult(true));
        
        Boolean result =
            configOperationService.publishConfig(configForm, configRequestInfo, "encryptedKey");
        assertTrue(result);
        verify(configInfoPersistService, times(1)).insertOrUpdate(anyString(), isNull(),
            any(ConfigInfo.class), anyMap());
    }
    
    @Test
    void testAddConfigInfoSuccess() throws Exception {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("testDataId");
        configForm.setGroup("testGroup");
        configForm.setNamespaceId("testNamespaceId");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setSrcType("http");
        configRequestInfo.setSrcIp("1.1.1.1");
        configRequestInfo.setUpdateForExist(false);
        
        when(configInfoPersistService.addConfigInfo(anyString(), isNull(), any(ConfigInfo.class),
            anyMap()))
            .thenReturn(new ConfigOperateResult(true));
        
        Boolean result =
            configOperationService.publishConfig(configForm, configRequestInfo, "encryptedKey");
        
        assertTrue(result);
        verify(configInfoPersistService, times(1)).addConfigInfo(anyString(), isNull(),
            any(ConfigInfo.class), anyMap());
    }
    
    @Test
    void testAddConfigInfoThrowsException() {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("testDataId");
        configForm.setGroup("testGroup");
        configForm.setNamespaceId("testNamespaceId");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setSrcType("http");
        configRequestInfo.setSrcIp("1.1.1.1");
        configRequestInfo.setUpdateForExist(false);
        
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setDataId("testDataId");
        configInfo.setGroup("testGroup");
        configInfo.setTenant("testNamespaceId");
        
        when(configInfoPersistService.addConfigInfo(eq("1.1.1.1"), isNull(), eq(configInfo),
            anyMap()))
            .thenThrow(new DataIntegrityViolationException("Duplicate entry"));
        
        NacosException exception = assertThrows(NacosException.class, () -> {
            configOperationService.publishConfig(configForm, configRequestInfo, "encryptedKey");
        });
        
        String expectedMessage =
            "config already exist, dataId: testDataId, group: testGroup, namespaceId: testNamespaceId";
        assertEquals(expectedMessage, exception.getMessage());
        verify(configInfoPersistService, times(1)).addConfigInfo(anyString(), isNull(),
            eq(configInfo), anyMap());
    }
    
    @Test
    void testDeleteConfig() {
        
        // if tag is blank
        Boolean aResult =
            configOperationService.deleteConfig("test", "test", "", "", "1.1.1.1", "test", "http");
        verify(configInfoPersistService).removeConfigInfo(eq("test"), eq("test"),
            eq(Constants.DEFAULT_NAMESPACE_ID), any(), any());
        assertTrue(aResult);
        // if tag is not blank
        Boolean bResult = configOperationService.deleteConfig("test", "test", "", "test", "1.1.1.1",
            "test", "http");
        assertTrue(bResult);
    }
    
    @Test
    void testDeleteConfigNormalizesNullNamespaceIdToDefault() {
        Boolean result = configOperationService.deleteConfig("test", "test", null, "", "1.1.1.1",
            "test", "http");
        verify(configInfoPersistService).removeConfigInfo(eq("test"), eq("test"),
            eq(Constants.DEFAULT_NAMESPACE_ID), any(), any());
        assertTrue(result);
    }
    
    @Test
    void testPublishConfigCasFailure() {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setCasMd5("old-md5");
        
        when(configInfoPersistService.insertOrUpdateCas(any(), any(),
            any(ConfigInfo.class), any()))
            .thenReturn(new ConfigOperateResult(false));
        
        assertThrows(NacosApiException.class,
            () -> configOperationService.publishConfig(configForm,
                configRequestInfo, ""));
    }
    
    @Test
    void testDeleteConfigWithGrayName() {
        Boolean result = configOperationService.deleteConfig(
            "test", "test", "", "beta", "1.1.1.1", "user", "http");
        assertTrue(result);
        verify(configInfoGrayPersistService).removeConfigInfoGray(
            eq("test"), eq("test"), eq(Constants.DEFAULT_NAMESPACE_ID), eq("beta"), any(), any());
    }
    
    @Test
    void testGetConfigAdvanceInfo() {
        ConfigForm configForm = new ConfigForm();
        configForm.setConfigTags("tag1,tag2");
        configForm.setDesc("desc");
        configForm.setUse("use");
        configForm.setEffect("effect");
        configForm.setType("json");
        configForm.setSchema("schema");
        
        Map<String, Object> info =
            configOperationService.getConfigAdvanceInfo(configForm);
        assertEquals("tag1,tag2", info.get("config_tags"));
        assertEquals("desc", info.get("desc"));
        assertEquals("json", info.get("type"));
    }
    
    @Test
    void testGetConfigAdvanceInfoWithNulls() {
        ConfigForm configForm = new ConfigForm();
        Map<String, Object> info =
            configOperationService.getConfigAdvanceInfo(configForm);
        assertNotNull(info);
        assertEquals(0, info.size());
    }
    
    @Test
    void testPublishConfigWithIstioTags() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        configForm.setConfigTags("virtual-service");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setSrcIp("1.1.1.1");
        
        when(configInfoPersistService.insertOrUpdate(any(), any(),
            any(ConfigInfo.class), any()))
            .thenReturn(new ConfigOperateResult());
        
        Boolean result = configOperationService.publishConfig(configForm,
            configRequestInfo, "");
        assertTrue(result);
    }
    
    @Test
    void testPublishConfigBetaCasFailure() {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        configForm.setTag("");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setBetaIps("test-betaIps");
        configRequestInfo.setCasMd5("old-md5");
        
        when(configInfoGrayPersistService.insertOrUpdateGrayCas(
            any(ConfigInfo.class), eq("beta"), anyString(),
            any(), any()))
            .thenReturn(new ConfigOperateResult(false));
        
        assertThrows(NacosApiException.class,
            () -> configOperationService.publishConfig(configForm,
                configRequestInfo, ""));
    }
    
    @Test
    void testPublishConfigBetaGrayVersionOverMaxCount() {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        configForm.setTag("");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setBetaIps("test-betaIps");
        
        List<String> existingGrays = Arrays.asList(
            "g1", "g2", "g3", "g4", "g5",
            "g6", "g7", "g8", "g9", "g10");
        when(configInfoGrayPersistService.findConfigInfoGrays(
            anyString(), anyString(), any()))
            .thenReturn(existingGrays);
        
        assertThrows(NacosApiException.class,
            () -> configOperationService.publishConfig(configForm,
                configRequestInfo, ""));
    }
    
    @Test
    void testPublishConfigTagCasFailure() {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        configForm.setTag("myTag");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setCasMd5("old-md5");
        
        when(configInfoGrayPersistService.insertOrUpdateGrayCas(
            any(ConfigInfo.class), eq("tag_myTag"), anyString(),
            any(), any()))
            .thenReturn(new ConfigOperateResult(false));
        
        assertThrows(NacosApiException.class,
            () -> configOperationService.publishConfig(configForm,
                configRequestInfo, ""));
    }
    
    @Test
    void testPublishConfigGrayVersionInvalid() {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        configForm.setTag("");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setBetaIps("1.1.1.1");
        
        try (MockedStatic<GrayRuleManager> mockedStatic =
            Mockito.mockStatic(GrayRuleManager.class)) {
            mockedStatic.when(() -> GrayRuleManager.constructGrayRule(any()))
                .thenReturn(null);
            assertThrows(NacosApiException.class,
                () -> configOperationService.publishConfig(configForm,
                    configRequestInfo, ""));
        }
    }
    
    @Test
    void testPublishConfigGrayRuleFormatInvalid() {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        configForm.setTag("");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setBetaIps("1.1.1.1");
        
        GrayRule mockGrayRule = Mockito.mock(GrayRule.class);
        when(mockGrayRule.isValid()).thenReturn(false);
        
        try (MockedStatic<GrayRuleManager> mockedStatic =
            Mockito.mockStatic(GrayRuleManager.class)) {
            mockedStatic.when(() -> GrayRuleManager.constructGrayRule(any()))
                .thenReturn(mockGrayRule);
            assertThrows(NacosApiException.class,
                () -> configOperationService.publishConfig(configForm,
                    configRequestInfo, ""));
        }
    }
    
    @Test
    void testPublishConfigBetaGrayNameAlreadyExists() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        configForm.setTag("");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setBetaIps("1.1.1.1");
        
        List<String> existingGrays = Arrays.asList("beta", "g2", "g3");
        when(configInfoGrayPersistService.findConfigInfoGrays(
            anyString(), anyString(), any()))
            .thenReturn(existingGrays);
        when(configInfoGrayPersistService.insertOrUpdateGray(
            any(ConfigInfo.class), eq("beta"), anyString(), any(), any()))
            .thenReturn(new ConfigOperateResult());
        
        Boolean result = configOperationService.publishConfig(
            configForm, configRequestInfo, "");
        assertTrue(result);
    }
}
