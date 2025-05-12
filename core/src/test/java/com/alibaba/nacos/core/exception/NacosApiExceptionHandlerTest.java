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

package com.alibaba.nacos.core.exception;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.controller.v3.NamespaceControllerV3;
import com.alibaba.nacos.core.listener.startup.NacosStartUp;
import com.alibaba.nacos.core.listener.startup.NacosStartUpManager;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(NacosApiExceptionHandler.class)
@EnableWebMvc
class NacosApiExceptionHandlerTest {
    
    private MockMvc mockMvc;
    
    @Autowired
    private WebApplicationContext context;
    
    @MockitoBean
    private NamespaceControllerV3 namespaceControllerV2;
    
    @BeforeAll
    static void beforeAll() {
        NacosStartUpManager.start(NacosStartUp.CORE_START_UP_PHASE);
    }
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }
    
    @Test
    void testNacosExceptionHandler() throws Exception {
        mockControllerThrowException(new NacosException(NacosException.INVALID_PARAM, "test"));
        ResultActions resultActions = mockMvc.perform(post("/v3/admin/core/namespace"));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosException.INVALID_PARAM));
        resultActions.andExpect(new NacosResultErrorCodeMatcher(ErrorCode.SERVER_ERROR.getCode()));
    }
    
    @Test
    void testNacosApiExceptionHandler() throws Exception {
        mockControllerThrowException(
                new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING, "test"));
        ResultActions resultActions = mockMvc.perform(post("/v3/admin/core/namespace"));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosException.INVALID_PARAM));
        resultActions.andExpect(new NacosResultErrorCodeMatcher(ErrorCode.PARAMETER_MISSING.getCode()));
    }
    
    @Test
    void testNacosRunTimeExceptionHandler() throws Exception {
        // 设置NamespaceControllerV2的行为，使其抛出NacosRuntimeException并被NacosApiExceptionHandler捕获处理
        mockControllerThrowException(new NacosRuntimeException(NacosException.INVALID_PARAM));
        ResultActions resultActions = mockMvc.perform(post("/v3/admin/core/namespace"));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosException.INVALID_PARAM));
        resultActions.andExpect(new NacosResultErrorCodeMatcher(ErrorCode.SERVER_ERROR.getCode()));
        
        mockControllerThrowException(new NacosRuntimeException(NacosException.SERVER_ERROR));
        ResultActions resultActions1 = mockMvc.perform(post("/v3/admin/core/namespace"));
        resultActions1.andExpect(MockMvcResultMatchers.status().is(NacosException.SERVER_ERROR));
        resultActions.andExpect(new NacosResultErrorCodeMatcher(ErrorCode.SERVER_ERROR.getCode()));
        
        mockControllerThrowException(new NacosRuntimeException(NacosApiException.OVER_THRESHOLD));
        ResultActions resultActions2 = mockMvc.perform(post("/v3/admin/core/namespace"));
        resultActions2.andExpect(MockMvcResultMatchers.status().is(NacosApiException.OVER_THRESHOLD));
        resultActions.andExpect(new NacosResultErrorCodeMatcher(ErrorCode.SERVER_ERROR.getCode()));
    }
    
    @Test
    void handleHttpMessageNotReadableException() throws Exception {
        mockControllerThrowException(new HttpMessageNotReadableException("test", (HttpInputMessage) null));
        ResultActions resultActions = mockMvc.perform(post("/v3/admin/core/namespace"));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosApiException.INVALID_PARAM));
        resultActions.andExpect(new NacosResultErrorCodeMatcher(ErrorCode.PARAMETER_MISSING.getCode()));
    }
    
    @Test
    void handleHttpMessageConversionException() throws Exception {
        mockControllerThrowException(new HttpMessageConversionException("test"));
        ResultActions resultActions = mockMvc.perform(post("/v3/admin/core/namespace"));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosApiException.INVALID_PARAM));
        resultActions.andExpect(new NacosResultErrorCodeMatcher(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode()));
    }
    
    @Test
    void handleNumberFormatException() throws Exception {
        mockControllerThrowException(new NumberFormatException("test"));
        ResultActions resultActions = mockMvc.perform(post("/v3/admin/core/namespace"));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosApiException.INVALID_PARAM));
        resultActions.andExpect(new NacosResultErrorCodeMatcher(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode()));
    }
    
    @Test
    void handleIllegalArgumentException() throws Exception {
        mockControllerThrowException(new IllegalArgumentException("test"));
        ResultActions resultActions = mockMvc.perform(post("/v3/admin/core/namespace"));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosApiException.INVALID_PARAM));
        resultActions.andExpect(new NacosResultErrorCodeMatcher(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode()));
    }
    
    @Test
    void handleMissingServletRequestParameterException() throws Exception {
        mockControllerThrowException(new MissingServletRequestParameterException("test", "test"));
        ResultActions resultActions = mockMvc.perform(post("/v3/admin/core/namespace"));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosApiException.INVALID_PARAM));
        resultActions.andExpect(new NacosResultErrorCodeMatcher(ErrorCode.PARAMETER_MISSING.getCode()));
    }
    
    @Test
    void handleHttpMediaTypeException() throws Exception {
        mockControllerThrowException(new HttpMediaTypeNotSupportedException("test"));
        ResultActions resultActions = mockMvc.perform(post("/v3/admin/core/namespace"));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosApiException.INVALID_PARAM));
        resultActions.andExpect(new NacosResultErrorCodeMatcher(ErrorCode.MEDIA_TYPE_ERROR.getCode()));
    }
    
    @Test
    void handleAccessException() throws Exception {
        mockControllerThrowException(new AccessException("test"));
        ResultActions resultActions = mockMvc.perform(post("/v3/admin/core/namespace"));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosApiException.NO_RIGHT));
        resultActions.andExpect(new NacosResultErrorCodeMatcher(ErrorCode.ACCESS_DENIED.getCode()));
    }
    
    @Test
    void handleDataAccessException() throws Exception {
        mockControllerThrowException(new DataIntegrityViolationException("test"));
        ResultActions resultActions = mockMvc.perform(post("/v3/admin/core/namespace"));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosApiException.SERVER_ERROR));
        resultActions.andExpect(new NacosResultErrorCodeMatcher(ErrorCode.DATA_ACCESS_ERROR.getCode()));
    }
    
    @Test
    void handleOtherException() throws Exception {
        mockControllerThrowException(new Exception("test"));
        ResultActions resultActions = mockMvc.perform(post("/v3/admin/core/namespace"));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosApiException.SERVER_ERROR));
        resultActions.andExpect(new NacosResultErrorCodeMatcher(ErrorCode.SERVER_ERROR.getCode()));
    }
    
    private void mockControllerThrowException(Exception exceptionClass) throws Exception {
        doThrow(exceptionClass).when(namespaceControllerV2).createNamespace(any());
    }
    
    private static class NacosResultErrorCodeMatcher implements ResultMatcher {
        
        private final int errorCode;
        
        private NacosResultErrorCodeMatcher(int errorCode) {
            this.errorCode = errorCode;
        }
        
        @Override
        public void match(MvcResult result) throws Exception {
            String resultJson = result.getResponse().getContentAsString();
            Result actualResult = JacksonUtils.toObj(resultJson, Result.class);
            assertEquals(errorCode, actualResult.getCode());
        }
    }
}
