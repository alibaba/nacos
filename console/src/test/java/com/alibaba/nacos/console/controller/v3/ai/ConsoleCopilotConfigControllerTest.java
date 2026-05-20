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

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.copilot.config.CopilotAgentManager;
import com.alibaba.nacos.copilot.config.CopilotConfigStorage;
import com.alibaba.nacos.copilot.config.CopilotProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConsoleCopilotConfigControllerTest {
    
    @Mock
    private CopilotConfigStorage configStorage;
    
    @Mock
    private CopilotAgentManager agentManager;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
            new ConsoleCopilotConfigController(configStorage, agentManager)).build();
    }
    
    @Test
    void testGetConfigReturnsSimplifiedConfig() throws Exception {
        CopilotProperties config = new CopilotProperties();
        config.setApiKey("test-key");
        config.setModel("qwen-plus");
        config.setStudioUrl("http://studio.example.com");
        config.setStudioProject("TestProject");
        when(configStorage.getConfig()).thenReturn(config);
        
        MockHttpServletResponse response = mockMvc.perform(
            get("/v3/console/copilot/config"))
            .andExpect(status().isOk()).andReturn().getResponse();
        
        Result<CopilotProperties> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertNotNull(result.getData());
        assertEquals("test-key", result.getData().getApiKey());
        assertEquals("qwen-plus", result.getData().getModel());
        assertEquals("http://studio.example.com", result.getData().getStudioUrl());
        assertEquals("TestProject", result.getData().getStudioProject());
    }
    
    @Test
    void testGetConfigReturnsDefaultWhenNull() throws Exception {
        when(configStorage.getConfig()).thenReturn(null);
        
        MockHttpServletResponse response = mockMvc.perform(
            get("/v3/console/copilot/config"))
            .andExpect(status().isOk()).andReturn().getResponse();
        
        Result<CopilotProperties> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertNotNull(result.getData());
        assertNull(result.getData().getApiKey());
    }
    
    @Test
    void testSaveConfigSuccess() throws Exception {
        when(configStorage.getConfig()).thenReturn(null);
        when(configStorage.saveConfig(any(CopilotProperties.class))).thenReturn(true);
        
        String body = "{\"apiKey\":\"new-key\",\"model\":\"qwen-max\","
            + "\"studioUrl\":\"http://new.url\",\"studioProject\":\"Proj\"}";
        
        MockHttpServletResponse response = mockMvc.perform(
            post("/v3/console/copilot/config")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse();
        
        Result<Boolean> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertTrue(result.getData());
        verify(agentManager).refreshConfig();
    }
    
    @Test
    void testSaveConfigUpdatesExistingConfig() throws Exception {
        CopilotProperties existing = new CopilotProperties();
        existing.setApiKey("old-key");
        existing.setModel("old-model");
        when(configStorage.getConfig()).thenReturn(existing);
        when(configStorage.saveConfig(any(CopilotProperties.class))).thenReturn(true);
        
        String body = "{\"apiKey\":\"new-key\"}";
        
        mockMvc.perform(post("/v3/console/copilot/config")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        
        verify(configStorage).saveConfig(any(CopilotProperties.class));
        verify(agentManager).refreshConfig();
    }
    
    @Test
    void testSaveConfigReturnsFalseDoesNotRefresh() throws Exception {
        when(configStorage.getConfig()).thenReturn(null);
        when(configStorage.saveConfig(any(CopilotProperties.class))).thenReturn(false);
        
        String body = "{\"apiKey\":\"key\"}";
        
        MockHttpServletResponse response = mockMvc.perform(
            post("/v3/console/copilot/config")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse();
        
        Result<Boolean> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertFalse(result.getData());
        verify(agentManager, never()).refreshConfig();
    }
    
    @Test
    void testSaveConfigWithNullFieldsPreservesExisting() throws Exception {
        CopilotProperties existing = new CopilotProperties();
        existing.setApiKey("keep-this");
        existing.setModel("keep-model");
        existing.setStudioUrl("keep-url");
        existing.setStudioProject("keep-proj");
        when(configStorage.getConfig()).thenReturn(existing);
        when(configStorage.saveConfig(any(CopilotProperties.class))).thenReturn(true);
        
        String body = "{}";
        
        mockMvc.perform(post("/v3/console/copilot/config")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        
        verify(configStorage).saveConfig(existing);
    }
}
