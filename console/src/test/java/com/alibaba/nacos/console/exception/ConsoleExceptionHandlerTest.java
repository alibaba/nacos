/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.exception;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.controller.NamespaceController;
import com.alibaba.nacos.console.controller.v2.HealthControllerV2;
import com.alibaba.nacos.core.listener.startup.NacosStartUp;
import com.alibaba.nacos.core.listener.startup.NacosStartUpManager;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ExtendWith(SpringExtension.class)
@WebMvcTest(ConsoleExceptionHandlerTest.class)
class ConsoleExceptionHandlerTest {
    
    private MockMvc mockMvc;
    
    @Autowired
    private WebApplicationContext context;
    
    @MockitoBean
    private NamespaceController namespaceController;
    
    @MockitoBean
    private HealthControllerV2 healthControllerV2;
    
    @BeforeAll
    static void beforeAll() {
        NacosStartUpManager.start(NacosStartUp.CONSOLE_START_UP_PHASE);
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @BeforeEach
    void before() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }
    
    @Test
    void testNacosRunTimeExceptionHandler() throws Exception {
        // 执行请求并验证响应码
        mockControllerThrowException(new NacosRuntimeException(NacosException.INVALID_PARAM));
        ResultActions resultActions = mockMvc.perform(get("/v1/console/namespaces?show=all&namespaceId="));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosException.INVALID_PARAM));
        
        // 执行请求并验证响应码
        mockControllerThrowException(new NacosRuntimeException(NacosException.SERVER_ERROR));
        ResultActions resultActions1 = mockMvc.perform(get("/v1/console/namespaces?show=all&namespaceId="));
        resultActions1.andExpect(MockMvcResultMatchers.status().is(NacosException.SERVER_ERROR));
        
        // 执行请求并验证响应码
        mockControllerThrowException(new NacosRuntimeException(NacosApiException.OVER_THRESHOLD));
        ResultActions resultActions2 = mockMvc.perform(get("/v1/console/namespaces?show=all&namespaceId="));
        resultActions2.andExpect(MockMvcResultMatchers.status().is(NacosApiException.OVER_THRESHOLD));
    }
    
    @Test
    void handleIllegalArgumentException() throws Exception {
        mockControllerThrowException(new IllegalArgumentException("test"));
        ResultActions resultActions = mockMvc.perform(get("/v1/console/namespaces?show=all&namespaceId="));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosApiException.INVALID_PARAM));
    }
    
    @Test
    void handleAccessException() throws Exception {
        mockControllerThrowException(new AccessException("test"));
        ResultActions resultActions = mockMvc.perform(get("/v1/console/namespaces?show=all&namespaceId="));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosApiException.NO_RIGHT));
    }
    
    @Test
    void handleException() throws Exception {
        mockControllerThrowException(new RuntimeException("test"));
        ResultActions resultActions = mockMvc.perform(get("/v1/console/namespaces?show=all&namespaceId="));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosApiException.SERVER_ERROR));
    }
    
    @Test
    void handleExceptionForV2() throws Exception {
        doThrow(new RuntimeException("test")).when(healthControllerV2).liveness();
        ResultActions resultActions = mockMvc.perform(get("/v2/console/health/liveness"));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosApiException.SERVER_ERROR));
    }
    
    private void mockControllerThrowException(Exception exceptionClass) throws NacosException {
        doThrow(exceptionClass).when(namespaceController).getNamespace(anyString());
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