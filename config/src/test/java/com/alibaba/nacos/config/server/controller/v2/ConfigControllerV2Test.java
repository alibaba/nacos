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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.controller.ConfigServletInner;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConfigControllerV2Test {
    
    private ConfigControllerV2 configControllerV2;
    
    @Mock
    private ConfigServletInner inner;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    private static final String TEST_DATA_ID = "test";
    
    private static final String TEST_GROUP = "test";
    
    private static final String TEST_NAMESPACE_ID = "";
    
    private static final String TEST_NAMESPACE_ID_PUBLIC = "public";
    
    private static final String TEST_TAG = "";
    
    private static final String TEST_CONTENT = "test config";
    
    @Before
    public void setUp() {
        configControllerV2 = new ConfigControllerV2(inner, configOperationService);
    }
    
    @Test
    public void testGetConfig() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Result<String> stringResult = Result.success(TEST_CONTENT);
        
        doAnswer(x -> {
            x.getArgument(1, HttpServletResponse.class).setStatus(200);
            x.getArgument(1, HttpServletResponse.class)
                    .setContentType(com.alibaba.nacos.common.http.param.MediaType.APPLICATION_JSON);
            x.getArgument(1, HttpServletResponse.class).getWriter().print(JacksonUtils.toJson(stringResult));
            return null;
        }).when(inner).doGetConfig(any(HttpServletRequest.class), any(HttpServletResponse.class), eq(TEST_DATA_ID),
                eq(TEST_GROUP), eq(TEST_NAMESPACE_ID), eq(TEST_TAG), eq(null), anyString(), eq(true));
        
        configControllerV2.getConfig(request, response, TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID, TEST_TAG);
        
        verify(inner).doGetConfig(eq(request), eq(response), eq(TEST_DATA_ID), eq(TEST_GROUP), eq(TEST_NAMESPACE_ID),
                eq(TEST_TAG), eq(null), anyString(), eq(true));
        JsonNode resNode = JacksonUtils.toObj(response.getContentAsString());
        Integer errCode = JacksonUtils.toObj(resNode.get("code").toString(), Integer.class);
        String actContent = JacksonUtils.toObj(resNode.get("data").toString(), String.class);
        assertEquals(200, response.getStatus());
        assertEquals(ErrorCode.SUCCESS.getCode(), errCode);
        assertEquals(TEST_CONTENT, actContent);
    }
    
    @Test
    public void testPublishConfig() throws Exception {
        
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(TEST_DATA_ID);
        configForm.setGroup(TEST_GROUP);
        configForm.setNamespaceId(TEST_NAMESPACE_ID);
        configForm.setContent(TEST_CONTENT);
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        when(configOperationService.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                anyString())).thenReturn(true);
        
        Result<Boolean> booleanResult = configControllerV2.publishConfig(configForm, request);
        
        verify(configOperationService).publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class), anyString());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), booleanResult.getCode());
        assertEquals(true, booleanResult.getData());
    }
    
    @Test
    public void testPublishConfigWhenNameSpaceIsPublic() throws Exception {
        
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(TEST_DATA_ID);
        configForm.setGroup(TEST_GROUP);
        configForm.setNamespaceId(TEST_NAMESPACE_ID_PUBLIC);
        configForm.setContent(TEST_CONTENT);
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        when(configOperationService.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                anyString())).thenAnswer((Answer<Boolean>) invocation -> {
                    if (invocation.getArgument(0, ConfigForm.class).getNamespaceId().equals(TEST_NAMESPACE_ID)) {
                        return true;
                    }
                    return false;
                });
        
        Result<Boolean> booleanResult = configControllerV2.publishConfig(configForm, request);
        
        verify(configOperationService).publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class), anyString());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), booleanResult.getCode());
        assertEquals(true, booleanResult.getData());
    }
    
    @Test
    public void testDeleteConfigWhenNameSpaceIsPublic() throws Exception {
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        when(configOperationService.deleteConfig(eq(TEST_DATA_ID), eq(TEST_GROUP), eq(TEST_NAMESPACE_ID), eq(TEST_TAG),
                any(), any())).thenReturn(true);
        Result<Boolean> booleanResult = configControllerV2.deleteConfig(request, TEST_DATA_ID, TEST_GROUP,
                TEST_NAMESPACE_ID_PUBLIC, TEST_TAG);
        
        verify(configOperationService).deleteConfig(eq(TEST_DATA_ID), eq(TEST_GROUP), eq(TEST_NAMESPACE_ID),
                eq(TEST_TAG), any(), any());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), booleanResult.getCode());
        assertEquals(true, booleanResult.getData());
    }
    
    @Test
    public void testDeleteConfig() throws Exception {
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        when(configOperationService.deleteConfig(eq(TEST_DATA_ID), eq(TEST_GROUP), eq(TEST_NAMESPACE_ID), eq(TEST_TAG),
                any(), any())).thenReturn(true);
        
        Result<Boolean> booleanResult = configControllerV2.deleteConfig(request, TEST_DATA_ID, TEST_GROUP,
                TEST_NAMESPACE_ID, TEST_TAG);
        
        verify(configOperationService).deleteConfig(eq(TEST_DATA_ID), eq(TEST_GROUP), eq(TEST_NAMESPACE_ID),
                eq(TEST_TAG), any(), any());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), booleanResult.getCode());
        assertEquals(true, booleanResult.getData());
    }
}
