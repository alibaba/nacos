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

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.console.proxy.config.ConfigProxy;
import com.alibaba.nacos.core.auth.AuthFilter;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import javax.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ConsoleConfigControllerTest.
 *
 * @author zhangyukun on:2024/8/20
 */
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
    private AuthConfigs authConfigs;
    
    private ConsoleConfigController consoleConfigController;
    
    private MockMvc mockmvc;
    
    @Mock
    private ConfigProxy configProxy;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        consoleConfigController = new ConsoleConfigController(configProxy);
        mockmvc = MockMvcBuilders.standaloneSetup(consoleConfigController).addFilter(authFilter).build();
        when(authConfigs.isAuthEnabled()).thenReturn(false);
    }
    
    @Test
    void testGetConfigDetail() throws Exception {
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId("testDataId");
        configAllInfo.setGroup("testGroup");
        configAllInfo.setContent("testContent");
        
        when(configProxy.getConfigDetail("testDataId", "testGroup", "testNamespace")).thenReturn(configAllInfo);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/cs/config")
                .param("dataId", "testDataId").param("group", "testGroup").param("namespaceId", "testNamespace");
        
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<ConfigAllInfo> result = JacksonUtils.toObj(actualValue, new TypeReference<Result<ConfigAllInfo>>() {
        });
        ConfigAllInfo resultConfigAllInfo = result.getData();
        
        assertEquals("testDataId", resultConfigAllInfo.getDataId());
        assertEquals("testGroup", resultConfigAllInfo.getGroup());
        assertEquals("testContent", resultConfigAllInfo.getContent());
    }
    
    @Test
    void testPublishConfig() throws Exception {
        
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(TEST_DATA_ID);
        configForm.setGroup(TEST_GROUP);
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
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        when(configProxy.deleteConfig(eq(TEST_DATA_ID), eq(TEST_GROUP), eq(TEST_NAMESPACE_ID), eq(TEST_TAG), any(),
                any())).thenReturn(true);
        
        Result<Boolean> booleanResult = consoleConfigController.deleteConfig(request, TEST_DATA_ID, TEST_GROUP,
                TEST_NAMESPACE_ID, TEST_TAG);
        
        verify(configProxy).deleteConfig(eq(TEST_DATA_ID), eq(TEST_GROUP), eq(TEST_NAMESPACE_ID), eq(TEST_TAG), any(),
                any());
        
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
        
        List<ConfigInfo> configInfoList = new ArrayList<>();
        ConfigInfo configInfo = new ConfigInfo("testDataId", "testGroup", "testContent");
        configInfoList.add(configInfo);
        
        Page<ConfigInfo> page = new Page<>();
        page.setTotalCount(15);
        page.setPageNumber(1);
        page.setPagesAvailable(2);
        page.setPageItems(configInfoList);
        
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        configAdvanceInfo.put("appName", "testApp");
        configAdvanceInfo.put("config_tags", "testTag");
        
        when(configProxy.getConfigList(1,
                10,
                "testDataId",
                "testGroup",
                "",
                configAdvanceInfo
        )).thenReturn(page);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/cs/config/list")
                .param("dataId", "testDataId").param("group", "testGroup").param("appName", "testApp")
                .param("namespaceId", "").param("config_tags", "testTag").param("pageNo", "1").param("pageSize", "10");
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<Page<ConfigInfo>> result = JacksonUtils.toObj(actualValue,
                new TypeReference<Result<Page<ConfigInfo>>>() {
                });
        
        Page<ConfigInfo> pageResult = result.getData();
        List<ConfigInfo> resultList = pageResult.getPageItems();
        ConfigInfo resConfigInfo = resultList.get(0);
        
        assertEquals(configInfoList.size(), resultList.size());
        assertEquals(configInfo.getDataId(), resConfigInfo.getDataId());
        assertEquals(configInfo.getGroup(), resConfigInfo.getGroup());
        assertEquals(configInfo.getContent(), resConfigInfo.getContent());
    }
    
    @Test
    void testGetConfigListByContent() throws Exception {
        List<ConfigInfo> configInfoList = new ArrayList<>();
        ConfigInfo configInfo = new ConfigInfo("test", "test", "test");
        configInfoList.add(configInfo);
        
        Page<ConfigInfo> page = new Page<>();
        page.setTotalCount(15);
        page.setPageNumber(1);
        page.setPagesAvailable(2);
        page.setPageItems(configInfoList);
        Map<String, Object> configAdvanceInfo = new HashMap<>(8);
        configAdvanceInfo.put("content", "server.port");
        
        when(configProxy.getConfigListByContent("blur", 1, 10, "test", "test", "", configAdvanceInfo)).thenReturn(page);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/cs/config/searchDetail")
                .param("dataId", "test").param("group", "test").param("appName", "").param("namespaceId", "")
                .param("config_tags", "").param("config_detail", "server.port").param("search", "blur")
                .param("pageNo", "1").param("pageSize", "10");
        
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<Page<ConfigInfo>> result = JacksonUtils.toObj(actualValue,
                new TypeReference<Result<Page<ConfigInfo>>>() {
                });
        
        Page<ConfigInfo> pageResult = result.getData();
        List<ConfigInfo> resultList = pageResult.getPageItems();
        ConfigInfo resConfigInfo = resultList.get(0);
        
        assertEquals(configInfoList.size(), resultList.size());
        assertEquals(configInfo.getDataId(), resConfigInfo.getDataId());
        assertEquals(configInfo.getGroup(), resConfigInfo.getGroup());
        assertEquals(configInfo.getContent(), resConfigInfo.getContent());
    }
    
    @Test
    void testExportConfig() throws Exception {
        
        String dataId = "dataId1.json";
        String group = "group2";
        String tenant = "tenant234";
        String appname = "appname2";
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId(dataId);
        configAllInfo.setGroup(group);
        configAllInfo.setTenant(tenant);
        configAllInfo.setContent("content45678");
        configAllInfo.setAppName(appname);
        List<ConfigAllInfo> dataList = new ArrayList<>();
        dataList.add(configAllInfo);
    
        byte[] serializedData = new ObjectMapper().writeValueAsBytes(dataList);
        ResponseEntity<byte[]> responseEntity = new ResponseEntity<>(serializedData, HttpStatus.OK);
    
        Mockito.when(
                        configProxy.exportConfig(eq(dataId), eq(group), eq(tenant), eq(appname), eq(Arrays.asList(1L, 2L))))
                .thenReturn(responseEntity);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/cs/config/export")
                .param("dataId", dataId).param("group", group).param("tenant", tenant).param("appName", appname)
                .param("ids", "1,2");
    
        int actualValue = mockmvc.perform(builder).andReturn().getResponse().getStatus();
        assertEquals(200, actualValue);
    
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
                .param("exportV2", "true").param("dataId", dataId).param("group", group).param("tenant", tenant)
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
                .file(mockFile).param("src_user", srcUser).param("namespaceId", namespaceId)
                .param("policy", policy.toString()).header("X-Real-IP", srcIp).header("X-Forwarded-For", srcIp)
                .header("X-App-Name", requestIpApp != null ? requestIpApp : "");
        
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        int actualStatus = response.getStatus();
        
        assertEquals(200, actualStatus);
        
        verify(configProxy).importAndPublishConfig(eq(srcUser), eq(namespaceId), eq(policy), eq(mockFile), eq(srcIp),
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
                .param("src_user", "testUser").param("namespaceId", "testNamespace").param("policy", "ABORT")
                .content(new ObjectMapper().writeValueAsString(configBeansList)).contentType(MediaType.APPLICATION_JSON)
                .header("X-Real-IP", "127.0.0.1").header("X-Forwarded-For", "127.0.0.1");
        
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        int actualStatus = response.getStatus();
        
        assertEquals(200, actualStatus);
        
        verify(configProxy).cloneConfig(eq("testUser"), eq("testNamespace"),
                argThat(new ArgumentMatcher<List<SameNamespaceCloneConfigBean>>() {
                    @Override
                    public boolean matches(List<SameNamespaceCloneConfigBean> argument) {
                        return argument != null && argument.size() == 1 && "testDataId".equals(
                                argument.get(0).getDataId()) && "testGroup".equals(argument.get(0).getGroup())
                                && 1L == argument.get(0).getCfgId();
                    }
                }), eq(SameConfigPolicy.ABORT), eq("127.0.0.1"), eq(null));
    }
}
