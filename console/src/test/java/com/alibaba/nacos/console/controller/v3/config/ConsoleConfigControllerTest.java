/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.console.controller.v3.config;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.config.model.ConfigGrayInfo;
import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.model.form.ConfigFormV3;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.console.proxy.config.ConfigProxy;
import com.alibaba.nacos.core.auth.AuthFilter;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ConsoleConfigControllerTest {
    
    private static final String TEST_DATA_ID = "test";
    
    private static final String TEST_GROUP = "test";
    
    private static final String TEST_NAMESPACE_ID = "";
    
    private static final String TEST_TAG = "";
    
    private static final String TEST_CONTENT = "test config";
    
    @InjectMocks
    private AuthFilter authFilter;
    
    @Mock
    private NacosAuthConfig authConfig;
    
    private ConsoleConfigController consoleConfigController;
    
    private MockMvc mockmvc;
    
    @Mock
    private ConfigProxy configProxy;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        consoleConfigController = new ConsoleConfigController(configProxy);
        mockmvc = MockMvcBuilders.standaloneSetup(consoleConfigController).addFilter(authFilter).build();
        when(authConfig.isAuthEnabled()).thenReturn(false);
    }
    
    @Test
    void testGetConfigDetail() throws Exception {
        ConfigDetailInfo configAllInfo = new ConfigDetailInfo();
        configAllInfo.setDataId("testDataId");
        configAllInfo.setGroupName("testGroup");
        configAllInfo.setContent("testContent");
        
        when(configProxy.getConfigDetail("testDataId", "testGroup", "testNamespace")).thenReturn(configAllInfo);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/cs/config")
                .param("dataId", "testDataId").param("groupName", "testGroup").param("namespaceId", "testNamespace");
        
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<ConfigDetailInfo> result = JacksonUtils.toObj(actualValue,
                new TypeReference<Result<ConfigDetailInfo>>() {
                });
        ConfigDetailInfo resultConfigAllInfo = result.getData();
        
        assertEquals("testDataId", resultConfigAllInfo.getDataId());
        assertEquals("testGroup", resultConfigAllInfo.getGroupName());
        assertEquals("testContent", resultConfigAllInfo.getContent());
    }
    
    @Test
    void testPublishConfig() throws Exception {
        
        ConfigFormV3 configForm = new ConfigFormV3();
        configForm.setDataId(TEST_DATA_ID);
        configForm.setGroupName(TEST_GROUP);
        configForm.setNamespaceId(TEST_NAMESPACE_ID);
        configForm.setContent(TEST_CONTENT);
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        when(configProxy.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class))).thenReturn(true);
        
        Result<Boolean> booleanResult = consoleConfigController.publishConfig(request, configForm);
        
        verify(configProxy).publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class));
        
        assertEquals(ErrorCode.SUCCESS.getCode(), booleanResult.getCode());
        assertTrue(booleanResult.getData());
    }
    
    @Test
    void testDeleteConfig() throws Exception {
        
        when(configProxy.deleteConfig(eq(TEST_DATA_ID), eq(TEST_GROUP), eq(Constants.DEFAULT_NAMESPACE_ID),
                eq(TEST_TAG), any(), any())).thenReturn(true);
        
        ConfigFormV3 configForm = new ConfigFormV3();
        configForm.setDataId(TEST_DATA_ID);
        configForm.setGroupName(TEST_GROUP);
        configForm.setNamespaceId(TEST_NAMESPACE_ID);
        configForm.setTag(TEST_TAG);
        MockHttpServletRequest request = new MockHttpServletRequest();
        Result<Boolean> booleanResult = consoleConfigController.deleteConfig(request, configForm);
        
        verify(configProxy).deleteConfig(eq(TEST_DATA_ID), eq(TEST_GROUP), eq(Constants.DEFAULT_NAMESPACE_ID),
                eq(TEST_TAG), any(), any());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), booleanResult.getCode());
        assertTrue(booleanResult.getData());
    }
    
    @Test
    void testBatchDeleteConfigs() throws Exception {
        String clientIp = "127.0.0.1";
        String srcUser = "testUser";
        
        Mockito.mockStatic(RequestUtil.class);
        when(RequestUtil.getRemoteIp(any(HttpServletRequest.class))).thenReturn(clientIp);
        when(RequestUtil.getSrcUserName(any(HttpServletRequest.class))).thenReturn(srcUser);
        List<Long> ids = Arrays.asList(1L, 2L, 3L);
        
        when(configProxy.batchDeleteConfigs(eq(ids), eq(clientIp), eq(srcUser))).thenReturn(true);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete("/v3/console/cs/config/batchDelete")
                .param("ids", "1,2,3").header("X-Real-IP", clientIp).header("X-Forwarded-For", clientIp);
        
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        int actualStatus = response.getStatus();
        
        assertEquals(200, actualStatus);
        
        String responseBody = response.getContentAsString();
        Result<Boolean> actualResult = new ObjectMapper().readValue(responseBody, new TypeReference<Result<Boolean>>() {
        });
        
        assertTrue(actualResult.getData());
        assertEquals(ErrorCode.SUCCESS.getCode(), actualResult.getCode());
        
        verify(configProxy).batchDeleteConfigs(eq(ids), eq(clientIp), eq(srcUser));
    }
    
    @Test
    void testGetConfigList() throws Exception {
        
        List<ConfigBasicInfo> configInfoList = new ArrayList<>();
        ConfigBasicInfo configInfo = new ConfigBasicInfo();
        configInfo.setDataId("testDataId");
        configInfo.setGroupName("testGroup");
        configInfoList.add(configInfo);
        
        Page<ConfigBasicInfo> page = new Page<>();
        page.setTotalCount(15);
        page.setPageNumber(1);
        page.setPagesAvailable(2);
        page.setPageItems(configInfoList);
        
        when(configProxy.getConfigList(eq(1), eq(10), eq("testDataId"), eq("testGroup"), eq("public"),
                anyMap())).thenReturn(page);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/cs/config/list")
                .param("dataId", "testDataId").param("groupName", "testGroup").param("appName", "testApp")
                .param("type", "text").param("namespaceId", "").param("configTags", "testTag").param("pageNo", "1")
                .param("pageSize", "10");
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<Page<ConfigBasicInfo>> result = JacksonUtils.toObj(actualValue, new TypeReference<>() {
        });
        
        Page<ConfigBasicInfo> pageResult = result.getData();
        List<ConfigBasicInfo> resultList = pageResult.getPageItems();
        ConfigBasicInfo resConfigInfo = resultList.get(0);
        
        assertEquals(configInfoList.size(), resultList.size());
        assertEquals(configInfo.getDataId(), resConfigInfo.getDataId());
        assertEquals(configInfo.getGroupName(), resConfigInfo.getGroupName());
    }
    
    @Test
    void testGetConfigListByContent() throws Exception {
        List<ConfigBasicInfo> configInfoList = new ArrayList<>();
        ConfigBasicInfo configInfo = new ConfigBasicInfo();
        configInfo.setDataId("test");
        configInfo.setGroupName("test");
        configInfoList.add(configInfo);
        
        Page<ConfigBasicInfo> page = new Page<>();
        page.setTotalCount(15);
        page.setPageNumber(1);
        page.setPagesAvailable(2);
        page.setPageItems(configInfoList);
        
        when(configProxy.getConfigListByContent(eq("blur"), eq(1), eq(10), eq("test"), eq("test"), eq("public"),
                anyMap())).thenReturn(page);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/cs/config/searchDetail")
                .param("dataId", "test").param("groupName", "test").param("appName", "testApp").param("namespaceId", "")
                .param("configTags", "testTag").param("configDetail", "server.port").param("search", "blur")
                .param("type", "text").param("pageNo", "1").param("pageSize", "10");
        
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<Page<ConfigBasicInfo>> result = JacksonUtils.toObj(actualValue, new TypeReference<>() {
        });
        
        Page<ConfigBasicInfo> pageResult = result.getData();
        List<ConfigBasicInfo> resultList = pageResult.getPageItems();
        ConfigBasicInfo resConfigInfo = resultList.get(0);
        
        assertEquals(configInfoList.size(), resultList.size());
        assertEquals(configInfo.getDataId(), resConfigInfo.getDataId());
        assertEquals(configInfo.getGroupName(), resConfigInfo.getGroupName());
    }
    
    @Test
    void getListeners() throws Exception {
        ConfigListenerInfo configListenerInfo = new ConfigListenerInfo();
        when(configProxy.getListeners(eq("test"), eq("test"), eq("public"), eq(false))).thenReturn(configListenerInfo);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/cs/config/listener")
                .param("dataId", "test").param("groupName", "test").param("namespaceId", "")
                .param("aggregation", "false");
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        Result<ConfigListenerInfo> result = JacksonUtils.toObj(actualValue, new TypeReference<>() {
        });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }
    
    @Test
    void getAllSubClientConfigByIp() throws Exception {
        ConfigListenerInfo configListenerInfo = new ConfigListenerInfo();
        when(configProxy.getAllSubClientConfigByIp(eq("127.0.0.1"), eq(true), eq("namespaceId"), eq(false))).thenReturn(
                configListenerInfo);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/cs/config/listener/ip")
                .param("ip", "127.0.0.1").param("all", "true").param("aggregation", "false");
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        Result<ConfigListenerInfo> result = JacksonUtils.toObj(actualValue, new TypeReference<>() {
        });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }
    
    @Test
    void testExportConfigV2() throws Exception {
        String dataId = "dataId2.json";
        String group = "group2";
        String tenant = "tenant234";
        String appname = "appname2";
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId(dataId);
        configAllInfo.setGroup(group);
        configAllInfo.setTenant(tenant);
        configAllInfo.setAppName(appname);
        configAllInfo.setContent("content1234");
        List<ConfigAllInfo> dataList = new ArrayList<>();
        dataList.add(configAllInfo);
        
        byte[] serializedData = new ObjectMapper().writeValueAsBytes(dataList);
        ResponseEntity<byte[]> responseEntity = new ResponseEntity<>(serializedData, HttpStatus.OK);
        
        Mockito.when(
                        configProxy.exportConfigV2(eq(dataId), eq(group), eq(tenant), eq(appname), eq(Arrays.asList(1L, 2L))))
                .thenReturn(responseEntity);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/cs/config/export2")
                .param("exportV2", "true").param("dataId", dataId).param("groupName", group).param("tenant", tenant)
                .param("appName", appname).param("ids", "1,2");
        
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        int actualStatus = response.getStatus();
        
        assertEquals(200, actualStatus);
        
    }
    
    @Test
    void testImportAndPublishConfig() throws Exception {
        String srcUser = "testUser";
        String namespaceId = "testNamespace";
        SameConfigPolicy policy = SameConfigPolicy.ABORT;
        String srcIp = "127.0.0.1";
        String requestIpApp = null;
        
        MockMultipartFile mockFile = new MockMultipartFile("file", "test-config.yaml", "text/yaml",
                "config-content".getBytes());
        
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("success", true);
        Result<Map<String, Object>> expectedResult = Result.success(expectedResponse);
        
        Mockito.when(
                configProxy.importAndPublishConfig(eq(srcUser), eq(namespaceId), eq(policy), eq(mockFile), eq(srcIp),
                        eq(requestIpApp))).thenReturn(expectedResult);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart("/v3/console/cs/config/import")
                .file(mockFile).param("srcUser", "").param("namespaceId", namespaceId)
                .param("policy", policy.toString()).header("X-Real-IP", srcIp).header("X-Forwarded-For", srcIp)
                .header("X-App-Name", requestIpApp != null ? requestIpApp : "");
        
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        int actualStatus = response.getStatus();
        
        assertEquals(200, actualStatus);
        
        verify(configProxy).importAndPublishConfig(any(), eq(namespaceId), eq(policy), eq(mockFile), eq(srcIp),
                eq(requestIpApp));
    }
    
    @Test
    void testCloneConfig() throws Exception {
        SameNamespaceCloneConfigBean sameNamespaceCloneConfigBean = new SameNamespaceCloneConfigBean();
        sameNamespaceCloneConfigBean.setCfgId(1L);
        sameNamespaceCloneConfigBean.setDataId("testDataId");
        sameNamespaceCloneConfigBean.setGroup("testGroup");
        List<SameNamespaceCloneConfigBean> configBeansList = new ArrayList<>();
        configBeansList.add(sameNamespaceCloneConfigBean);
        
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("status", "success");
        Result<Map<String, Object>> expectedResult = Result.success(expectedResponse);
        
        when(configProxy.cloneConfig(eq("testUser"), eq("testNamespace"),
                argThat(new ArgumentMatcher<List<SameNamespaceCloneConfigBean>>() {
                    @Override
                    public boolean matches(List<SameNamespaceCloneConfigBean> argument) {
                        return argument != null && argument.size() == 1 && "testDataId".equals(
                                argument.get(0).getDataId()) && "testGroup".equals(argument.get(0).getGroup())
                                && 1L == argument.get(0).getCfgId();
                    }
                }), eq(SameConfigPolicy.ABORT), eq("127.0.0.1"), eq(null) // 这里模拟可能为null的情况
        )).thenReturn(expectedResult);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post("/v3/console/cs/config/clone")
                .param("srcUser", "").param("targetNamespaceId", "testNamespace").param("policy", "ABORT")
                .content(new ObjectMapper().writeValueAsString(configBeansList)).contentType(MediaType.APPLICATION_JSON)
                .header("X-Real-IP", "127.0.0.1").header("X-Forwarded-For", "127.0.0.1");
        
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        int actualStatus = response.getStatus();
        
        assertEquals(200, actualStatus);
        
        verify(configProxy).cloneConfig(any(), eq("testNamespace"),
                argThat(new ArgumentMatcher<List<SameNamespaceCloneConfigBean>>() {
                    @Override
                    public boolean matches(List<SameNamespaceCloneConfigBean> argument) {
                        return argument != null && argument.size() == 1 && "testDataId".equals(
                                argument.get(0).getDataId()) && "testGroup".equals(argument.get(0).getGroup())
                                && 1L == argument.get(0).getCfgId();
                    }
                }), eq(SameConfigPolicy.ABORT), eq("127.0.0.1"), eq(null));
    }
    
    @Test
    void testStopBeta() throws Exception {
        // Mock configuration
        String dataId = "testDataId";
        String group = "testGroup";
        String namespaceId = "testNamespaceId";
        when(configProxy.removeBetaConfig(anyString(), anyString(), anyString(), any(), any(), any())).thenReturn(true);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete("/v3/console/cs/config/beta")
                .param("dataId", dataId).param("groupName", group).param("namespaceId", namespaceId);
        
        // Execute and validate response
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        Result<Boolean> result = new ObjectMapper().readValue(actualValue, new TypeReference<>() {
        });
        assertEquals(200, response.getStatus());
        assertTrue(result.getData());
    }
    
    @Test
    void testStopBetaFailure() throws Exception {
        // Mock configuration
        String dataId = "testDataId";
        String group = "testGroup";
        String namespaceId = "testNamespaceId";
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete("/v3/console/cs/config/beta")
                .param("dataId", dataId).param("groupName", group).param("namespaceId", namespaceId);
        
        // Execute and validate response
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        Result<Boolean> result = new ObjectMapper().readValue(actualValue, new TypeReference<>() {
        });
        assertEquals(200, response.getStatus());
        assertFalse(result.getData());
    }
    
    @Test
    void testQueryBetaSuccess() throws Exception {
        // Mock configuration for successful response
        String dataId = "testDataId";
        String group = "testGroup";
        
        ConfigGrayInfo mockConfigInfo = new ConfigGrayInfo();
        mockConfigInfo.setDataId(dataId);
        mockConfigInfo.setGroupName(group);
        when(configProxy.queryBetaConfig(anyString(), anyString(), anyString())).thenReturn(mockConfigInfo);
        String namespaceId = "testNamespaceId";
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/cs/config/beta")
                .param("dataId", dataId).param("groupName", group).param("namespaceId", namespaceId);
        
        // Execute and validate response
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<ConfigGrayInfo> result = new ObjectMapper().readValue(actualValue, new TypeReference<>() {
        });
        
        assertEquals(200, response.getStatus());
        assertEquals(dataId, result.getData().getDataId());
        assertEquals(group, result.getData().getGroupName());
    }
}
