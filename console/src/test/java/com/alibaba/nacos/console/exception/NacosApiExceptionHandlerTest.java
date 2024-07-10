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
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.console.controller.v2.NamespaceControllerV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(NacosApiExceptionHandler.class)
class NacosApiExceptionHandlerTest {
    
    private MockMvc mockMvc;
    
    @Autowired
    private WebApplicationContext context;
    
    @MockBean
    private NamespaceControllerV2 namespaceControllerV2;
    
    @BeforeEach
    void before() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }
    
    @Test
    void testNacosRunTimeExceptionHandler() throws Exception {
        // 设置NamespaceControllerV2的行为，使其抛出NacosRuntimeException并被NacosApiExceptionHandler捕获处理
        when(namespaceControllerV2.createNamespace(any())).thenThrow(new NacosRuntimeException(NacosException.INVALID_PARAM))
                .thenThrow(new NacosRuntimeException(NacosException.SERVER_ERROR)).thenThrow(new NacosRuntimeException(503));
        
        // 执行请求并验证响应码
        ResultActions resultActions = mockMvc.perform(post("/v2/console/namespace"));
        resultActions.andExpect(MockMvcResultMatchers.status().is(NacosException.INVALID_PARAM));
        
        // 执行请求并验证响应码
        ResultActions resultActions1 = mockMvc.perform(post("/v2/console/namespace"));
        resultActions1.andExpect(MockMvcResultMatchers.status().is(NacosException.SERVER_ERROR));
        
        // 执行请求并验证响应码
        ResultActions resultActions2 = mockMvc.perform(post("/v2/console/namespace"));
        resultActions2.andExpect(MockMvcResultMatchers.status().is(503));
    }
}
