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

package com.alibaba.nacos.console.controller.v3;

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.console.proxy.HealthProxy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * ConsoleHealthControllerTest.
 *
 * @author zhangyukun on:2024/8/28
 */
@ExtendWith(MockitoExtension.class)
public class ConsoleHealthControllerTest {
    
    @Mock
    private HealthProxy healthProxy;
    
    @InjectMocks
    private ConsoleHealthController consoleHealthController;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(consoleHealthController).build();
    }
    
    @Test
    void testLiveness() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/health/liveness");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<String> result = new ObjectMapper().readValue(actualValue, new TypeReference<Result<String>>() {
        });
        
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testReadiness() throws Exception {
        when(healthProxy.checkReadiness()).thenReturn(Result.success("ready"));
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/health/readiness");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<String> result = new ObjectMapper().readValue(actualValue, new TypeReference<Result<String>>() {
        });
        
        assertEquals("ready", result.getData());
        assertEquals(200, response.getStatus());
    }
    
    @Test
    void testReadinessFail() throws Exception {
        when(healthProxy.checkReadiness()).thenReturn(Result.failure("fail"));
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/health/readiness");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<String> result = new ObjectMapper().readValue(actualValue, new TypeReference<Result<String>>() {
        });
        
        assertEquals("fail", result.getMessage());
        assertEquals(500, response.getStatus());
        
    }
}

