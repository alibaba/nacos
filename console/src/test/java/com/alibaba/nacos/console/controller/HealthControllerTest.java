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

package com.alibaba.nacos.console.controller;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.naming.controllers.OperatorController;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.http.HttpServletRequest;

import static org.mockito.ArgumentMatchers.any;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class HealthControllerTest {
    
    @InjectMocks
    private HealthController healthController;
    
    @Mock
    private PersistService persistService;
    
    @Mock
    private OperatorController apiCommands;
    
    private MockMvc mockmvc;
    
    @Before
    public void setUp() {
        mockmvc = MockMvcBuilders.standaloneSetup(healthController).build();
    }
    
    @Test
    public void testLiveness() throws Exception {
        String url = "/v1/console/health/liveness";
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(url);
        Assert.assertEquals(200, mockmvc.perform(builder).andReturn().getResponse().getStatus());
    }
    
    @Test
    public void testReadiness() throws Exception {
        String url = "/v1/console/health/readiness";
        
        Mockito.when(persistService.configInfoCount(any(String.class))).thenReturn(0);
        Mockito.when(apiCommands.metrics(any(HttpServletRequest.class))).thenReturn(JacksonUtils.createEmptyJsonNode());
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(url);
        Assert.assertEquals(200, mockmvc.perform(builder).andReturn().getResponse().getStatus());
        
        // Config and Naming are not in readiness
        Mockito.when(persistService.configInfoCount(any(String.class)))
                .thenThrow(new RuntimeException("HealthControllerTest.testReadiness"));
        Mockito.when(apiCommands.metrics(any(HttpServletRequest.class)))
                .thenThrow(new RuntimeException("HealthControllerTest.testReadiness"));
        builder = MockMvcRequestBuilders.get(url);
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        Assert.assertEquals(500, response.getStatus());
        Assert.assertEquals("Config and Naming are not in readiness", response.getContentAsString());
        
        // Config is not in readiness
        Mockito.when(persistService.configInfoCount(any(String.class)))
                .thenThrow(new RuntimeException("HealthControllerTest.testReadiness"));
        Mockito.when(apiCommands.metrics(any(HttpServletRequest.class))).thenReturn(JacksonUtils.createEmptyJsonNode());
        response = mockmvc.perform(builder).andReturn().getResponse();
        Assert.assertEquals(500, response.getStatus());
        Assert.assertEquals("Config is not in readiness", response.getContentAsString());
        
        // Naming is not in readiness
        Mockito.when(persistService.configInfoCount(any(String.class))).thenReturn(0);
        Mockito.when(apiCommands.metrics(any(HttpServletRequest.class)))
                .thenThrow(new RuntimeException("HealthControllerTest.testReadiness"));
        builder = MockMvcRequestBuilders.get(url);
        response = mockmvc.perform(builder).andReturn().getResponse();
        Assert.assertEquals(500, response.getStatus());
        Assert.assertEquals("Naming is not in readiness", response.getContentAsString());
    }
}
