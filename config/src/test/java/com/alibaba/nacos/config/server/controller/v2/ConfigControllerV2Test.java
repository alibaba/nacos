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

package com.alibaba.nacos.config.server.controller.v2;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.controller.ConfigServletInner;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.core.auth.AuthFilter;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfigControllerV2Test {
    
    private static final String TEST_DATA_ID = "test";
    
    private static final String TEST_GROUP = "test";
    
    private static final String TEST_NAMESPACE_ID = "";
    
    private static final String TEST_NAMESPACE_ID_PUBLIC = "public";
    
    private static final String TEST_TAG = "";
    
    private static final String TEST_CONTENT = "test config";
    
    private static final String TEST_ENCRYPTED_DATA_KEY = "test_encrypted_data_key";
    
    @InjectMocks
    private AuthFilter authFilter;
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private ControllerMethodsCache controllerMethodsCache;
    
    private ConfigControllerV2 configControllerV2;
    
    private MockMvc mockmvc;
    
    @Mock
    private ConfigServletInner inner;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private ServletContext servletContext;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    private ConfigDetailService configDetailService;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        configDetailService = new ConfigDetailService(configInfoPersistService);
        configControllerV2 = new ConfigControllerV2(inner, configOperationService, configDetailService);
        mockmvc = MockMvcBuilders.standaloneSetup(configControllerV2).addFilter(authFilter).build();
        when(authConfigs.isAuthEnabled()).thenReturn(false);
    }
    
    @Test
    void testGetConfig() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Result<String> stringResult = Result.success(TEST_CONTENT);
        
        doAnswer(x -> {
            x.getArgument(1, HttpServletResponse.class).setStatus(200);
            x.getArgument(1, HttpServletResponse.class).setContentType(com.alibaba.nacos.common.http.param.MediaType.APPLICATION_JSON);
            x.getArgument(1, HttpServletResponse.class).getWriter().print(JacksonUtils.toJson(stringResult));
            return null;
        }).when(inner).doGetConfig(any(HttpServletRequest.class), any(HttpServletResponse.class), eq(TEST_DATA_ID), eq(TEST_GROUP),
                eq(TEST_NAMESPACE_ID), eq(TEST_TAG), eq(null), anyString(), eq(true));
        
        configControllerV2.getConfig(request, response, TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID, TEST_TAG);
        
        verify(inner).doGetConfig(eq(request), eq(response), eq(TEST_DATA_ID), eq(TEST_GROUP), eq(TEST_NAMESPACE_ID), eq(TEST_TAG),
                eq(null), anyString(), eq(true));
        JsonNode resNode = JacksonUtils.toObj(response.getContentAsString());
        Integer errCode = JacksonUtils.toObj(resNode.get("code").toString(), Integer.class);
        String actContent = JacksonUtils.toObj(resNode.get("data").toString(), String.class);
        assertEquals(200, response.getStatus());
        assertEquals(ErrorCode.SUCCESS.getCode(), errCode);
        assertEquals(TEST_CONTENT, actContent);
    }
    
    @Test
    void testPublishConfig() throws Exception {
        
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(TEST_DATA_ID);
        configForm.setGroup(TEST_GROUP);
        configForm.setNamespaceId(TEST_NAMESPACE_ID);
        configForm.setContent(TEST_CONTENT);
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        when(configOperationService.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class), anyString())).thenReturn(true);
        
        Result<Boolean> booleanResult = configControllerV2.publishConfig(configForm, request);
        
        verify(configOperationService).publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class), anyString());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), booleanResult.getCode());
        assertTrue(booleanResult.getData());
    }
    
    @Test
    void testPublishConfigWithEncryptedDataKey() throws Exception {
        
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(TEST_DATA_ID);
        configForm.setGroup(TEST_GROUP);
        configForm.setNamespaceId(TEST_NAMESPACE_ID);
        configForm.setContent(TEST_CONTENT);
        configForm.setEncryptedDataKey(TEST_ENCRYPTED_DATA_KEY);
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        when(configOperationService.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                eq(TEST_ENCRYPTED_DATA_KEY))).thenReturn(true);
        
        Result<Boolean> booleanResult = configControllerV2.publishConfig(configForm, request);
        
        verify(configOperationService).publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class), anyString());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), booleanResult.getCode());
        assertTrue(booleanResult.getData());
    }
    
    @Test
    void testPublishConfigWhenNameSpaceIsPublic() throws Exception {
        
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(TEST_DATA_ID);
        configForm.setGroup(TEST_GROUP);
        configForm.setNamespaceId(TEST_NAMESPACE_ID_PUBLIC);
        configForm.setContent(TEST_CONTENT);
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        when(configOperationService.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class), anyString())).thenAnswer(
                (Answer<Boolean>) invocation -> {
                    if (invocation.getArgument(0, ConfigForm.class).getNamespaceId().equals(TEST_NAMESPACE_ID)) {
                        return true;
                    }
                    return false;
                });
        
        Result<Boolean> booleanResult = configControllerV2.publishConfig(configForm, request);
        
        verify(configOperationService).publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class), anyString());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), booleanResult.getCode());
        assertTrue(booleanResult.getData());
    }
    
    @Test
    void testDeleteConfigWhenNameSpaceIsPublic() throws Exception {
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        when(configOperationService.deleteConfig(eq(TEST_DATA_ID), eq(TEST_GROUP), eq(TEST_NAMESPACE_ID), eq(TEST_TAG), any(),
                any())).thenReturn(true);
        Result<Boolean> booleanResult = configControllerV2.deleteConfig(request, TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID_PUBLIC,
                TEST_TAG);
        
        verify(configOperationService).deleteConfig(eq(TEST_DATA_ID), eq(TEST_GROUP), eq(TEST_NAMESPACE_ID), eq(TEST_TAG), any(), any());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), booleanResult.getCode());
        assertTrue(booleanResult.getData());
    }
    
    @Test
    void testDeleteConfig() throws Exception {
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        when(configOperationService.deleteConfig(eq(TEST_DATA_ID), eq(TEST_GROUP), eq(TEST_NAMESPACE_ID), eq(TEST_TAG), any(),
                any())).thenReturn(true);
        
        Result<Boolean> booleanResult = configControllerV2.deleteConfig(request, TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID, TEST_TAG);
        
        verify(configOperationService).deleteConfig(eq(TEST_DATA_ID), eq(TEST_GROUP), eq(TEST_NAMESPACE_ID), eq(TEST_TAG), any(), any());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), booleanResult.getCode());
        assertTrue(booleanResult.getData());
    }
    
    @Test
    void testGetConfigByDetail() throws Exception {
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
        
        when(configInfoPersistService.findConfigInfo4Page(1, 10, "test", "test", "", configAdvanceInfo)).thenReturn(page);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CONFIG_CONTROLLER_V2_PATH + "/searchDetail")
                .param("search", "accurate").param("dataId", "test").param("group", "test").param("appName", "").param("tenant", "")
                .param("config_tags", "").param("pageNo", "1").param("pageSize", "10").param("config_detail", "server.port");
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        JsonNode pageItemsNode = JacksonUtils.toObj(actualValue).get("pageItems");
        List resultList = JacksonUtils.toObj(pageItemsNode.toString(), List.class);
        ConfigInfo resConfigInfo = JacksonUtils.toObj(pageItemsNode.get(0).toString(), ConfigInfo.class);
        
        assertEquals(configInfoList.size(), resultList.size());
        assertEquals(configInfo.getDataId(), resConfigInfo.getDataId());
        assertEquals(configInfo.getGroup(), resConfigInfo.getGroup());
        assertEquals(configInfo.getContent(), resConfigInfo.getContent());
    }
    
    @Test
    void testGetConfigFuzzyByDetail() throws Exception {
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
        
        when(configInfoPersistService.findConfigInfoLike4Page(1, 10, "test", "test", "", configAdvanceInfo)).thenReturn(page);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CONFIG_CONTROLLER_V2_PATH + "/searchDetail")
                .param("search", "blur").param("dataId", "test").param("group", "test").param("appName", "").param("tenant", "")
                .param("config_tags", "").param("pageNo", "1").param("pageSize", "10").param("config_detail", "server.port");
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        JsonNode pageItemsNode = JacksonUtils.toObj(actualValue).get("pageItems");
        List resultList = JacksonUtils.toObj(pageItemsNode.toString(), List.class);
        ConfigInfo resConfigInfo = JacksonUtils.toObj(pageItemsNode.get(0).toString(), ConfigInfo.class);
        
        assertEquals(configInfoList.size(), resultList.size());
        assertEquals(configInfo.getDataId(), resConfigInfo.getDataId());
        assertEquals(configInfo.getGroup(), resConfigInfo.getGroup());
        assertEquals(configInfo.getContent(), resConfigInfo.getContent());
    }
    
    @Test
    void testGetConfigAuthFilter() throws Exception {
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        Method method = Arrays.stream(ConfigControllerV2.class.getMethods())
                .filter(m -> m.getName().equals("searchConfigByDetails")).findFirst().get();
        when(controllerMethodsCache.getMethod(any(HttpServletRequest.class))).thenReturn(method);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(
                        Constants.CONFIG_CONTROLLER_V2_PATH + "/searchDetail").param("search", "accurate")
                .param("dataId", "test").param("group", "test").param("appName", "").param("tenant", "")
                .param("config_tags", "").param("pageNo", "1").param("pageSize", "10")
                .param("config_detail", "server.port");
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertEquals(response.getErrorMessage(),
                "Invalid server identity key or value, Please make sure set `nacos.core.auth.server.identity.key`"
                        + " and `nacos.core.auth.server.identity.value`, or open `nacos.core.auth.enable.userAgentAuthWhite`");
    }
}
