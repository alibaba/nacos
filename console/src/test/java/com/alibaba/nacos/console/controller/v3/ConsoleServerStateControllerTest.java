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

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.console.proxy.ServerStateProxy;
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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ConsoleServerStateControllerTest.
 *
 * @author zhangyukun on:2024/9/5
 */
@ExtendWith(MockitoExtension.class)
public class ConsoleServerStateControllerTest {
    
    @Mock
    private ServerStateProxy serverStateProxy;
    
    @InjectMocks
    private ConsoleServerStateController consoleServerStateController;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(consoleServerStateController).build();
    }
    
    @Test
    void testServerState() throws Exception {
        
        Map<String, String> state = new HashMap<>();
        state.put("state", "OK");
        
        when(serverStateProxy.getServerState()).thenReturn(state);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/server/state")
                .contentType(MediaType.APPLICATION_JSON);
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Map<String, String> result = new ObjectMapper().readValue(actualValue, new TypeReference<>() {
        });
        
        assertEquals("OK", result.get("state"));
    }
    
    @Test
    void testGetAnnouncement() throws Exception {
        when(serverStateProxy.getAnnouncement(anyString())).thenReturn("Test Announcement");
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/server/announcement")
                .param("language", "zh-CN");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<String> result = new ObjectMapper().readValue(actualValue, new TypeReference<>() {
        });
        
        assertEquals("Test Announcement", result.getData());
    }
    
    @Test
    void testGetAnnouncementWithUnsupportedLanguage() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/server/announcement")
                .param("language", "zh-TW");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<String> result = new ObjectMapper().readValue(actualValue, new TypeReference<>() {
        });
        assertEquals(ErrorCode.SERVER_ERROR.getCode(), result.getCode());
        assertEquals("Unsupported language: zh-TW", result.getMessage());
    }
    
    @Test
    void testGetConsoleUiGuide() throws Exception {
        when(serverStateProxy.getConsoleUiGuide()).thenReturn("Test Guide");
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/server/guide");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<String> result = new ObjectMapper().readValue(actualValue, new TypeReference<>() {
        });
        
        assertEquals("Test Guide", result.getData());
    }
}

