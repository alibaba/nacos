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
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.console.proxy.config.ConfigProxy;
import com.alibaba.nacos.copilot.config.CopilotAgentManager;
import com.alibaba.nacos.copilot.config.CopilotProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
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
    
    private static final String DATA_ID = "copilot-config.json";
    
    private static final String GROUP = "nacos-copilot";
    
    private static final String NAMESPACE = "public";
    
    @Mock
    private CopilotAgentManager agentManager;
    
    @Mock
    private ConfigProxy configProxy;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        ConsoleCopilotConfigController controller =
            new ConsoleCopilotConfigController(agentManager, configProxy);
        ReflectionTestUtils.setField(controller, "configNamespace", NAMESPACE);
        mockMvc = MockMvcBuilders.standaloneSetup(
            controller).build();
    }
    
    @Test
    void testGetConfigReturnsSimplifiedConfig() throws Exception {
        CopilotProperties config = new CopilotProperties();
        config.setApiKey("test-key");
        config.setModel("qwen-plus");
        config.setStudioUrl("http://studio.example.com");
        config.setStudioProject("TestProject");
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE))
            .thenReturn(configDetail(config));
        
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
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE)).thenReturn(null);
        
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
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE)).thenReturn(null);
        when(configProxy.publishConfig(any(), any())).thenReturn(true);
        
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
        
        ArgumentCaptor<ConfigForm> configFormCaptor = ArgumentCaptor.forClass(ConfigForm.class);
        ArgumentCaptor<ConfigRequestInfo> requestInfoCaptor =
            ArgumentCaptor.forClass(ConfigRequestInfo.class);
        verify(configProxy).publishConfig(configFormCaptor.capture(), requestInfoCaptor.capture());
        assertEquals(DATA_ID, configFormCaptor.getValue().getDataId());
        assertEquals(GROUP, configFormCaptor.getValue().getGroup());
        assertEquals(NAMESPACE, configFormCaptor.getValue().getNamespaceId());
        assertEquals("nacos-copilot", configFormCaptor.getValue().getAppName());
        assertEquals("system", configFormCaptor.getValue().getSrcUser());
        assertEquals("Copilot configuration", configFormCaptor.getValue().getDesc());
        assertEquals("json", configFormCaptor.getValue().getType());
        assertEquals("http", requestInfoCaptor.getValue().getSrcType());
    }
    
    @Test
    void testSaveConfigUpdatesExistingConfig() throws Exception {
        CopilotProperties existing = new CopilotProperties();
        existing.setApiKey("old-key");
        existing.setModel("old-model");
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE))
            .thenReturn(configDetail(existing));
        when(configProxy.publishConfig(any(), any())).thenReturn(true);
        
        String body = "{\"apiKey\":\"new-key\"}";
        
        mockMvc.perform(post("/v3/console/copilot/config")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        
        verify(configProxy).publishConfig(any(), any());
        verify(agentManager).refreshConfig();
    }
    
    @Test
    void testSaveConfigReturnsFalseDoesNotRefresh() throws Exception {
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE)).thenReturn(null);
        when(configProxy.publishConfig(any(), any())).thenReturn(false);
        
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
    void testSaveConfigWithExplicitNullFieldsPreservesExisting() throws Exception {
        CopilotProperties existing = new CopilotProperties();
        existing.setApiKey("keep-this");
        existing.setModel("keep-model");
        existing.setStudioUrl("keep-url");
        existing.setStudioProject("keep-proj");
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE))
            .thenReturn(configDetail(existing));
        when(configProxy.publishConfig(any(), any())).thenReturn(true);
        
        String body = "{\"apiKey\":null,\"model\":null,\"studioUrl\":null,"
            + "\"studioProject\":null}";
        
        mockMvc.perform(post("/v3/console/copilot/config")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        
        ArgumentCaptor<ConfigForm> captor = ArgumentCaptor.forClass(ConfigForm.class);
        verify(configProxy).publishConfig(captor.capture(), any());
        CopilotProperties actual = JacksonUtils.toObj(captor.getValue().getContent(),
            CopilotProperties.class);
        assertEquals("keep-this", actual.getApiKey());
        assertEquals("keep-model", actual.getModel());
        assertEquals("keep-url", actual.getStudioUrl());
        assertEquals("keep-proj", actual.getStudioProject());
    }
    
    @Test
    void testGetConfigReturnsDefaultWhenReadFails() throws Exception {
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE))
            .thenThrow(new RuntimeException("read failed"));
        
        MockHttpServletResponse response = mockMvc.perform(
            get("/v3/console/copilot/config"))
            .andExpect(status().isOk()).andReturn().getResponse();
        
        Result<CopilotProperties> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertNotNull(result.getData());
        assertNull(result.getData().getApiKey());
    }
    
    private ConfigDetailInfo configDetail(CopilotProperties config) {
        ConfigDetailInfo result = new ConfigDetailInfo();
        result.setContent(JacksonUtils.toJson(config));
        return result;
    }
}
