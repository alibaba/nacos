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

import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.console.model.ai.ConsoleCopilotConfig;
import com.alibaba.nacos.console.proxy.config.ConfigProxy;
import com.alibaba.nacos.copilot.config.CopilotAgentManager;
import com.alibaba.nacos.copilot.config.CopilotProperties;
import com.alibaba.nacos.copilot.connection.ConnectionSource;
import com.alibaba.nacos.copilot.connection.CopilotConnectionInfo;
import com.alibaba.nacos.copilot.connection.CopilotConnectionStatus;
import com.alibaba.nacos.copilot.connection.CopilotModelConnection;
import com.alibaba.nacos.copilot.connection.CredentialSource;
import com.alibaba.nacos.copilot.connection.SelectionSource;
import com.alibaba.nacos.core.exception.NacosApiExceptionHandler;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
            controller).setControllerAdvice(new NacosApiExceptionHandler()).build();
    }
    
    @Test
    void testGetConfigMasksStoredApiKey() throws Exception {
        CopilotProperties stored = new CopilotProperties();
        stored.setApiKey("test-api-key-1234");
        stored.setModel("qwen-plus");
        stored.setStudioUrl("http://studio.example.com");
        stored.setStudioProject("TestProject");
        stored.setSelectedConnection("legacy");
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE))
            .thenReturn(configDetail(stored));
        when(agentManager.getConnectionStatus()).thenReturn(statusOf("legacy"));
        
        ConsoleCopilotConfig result = getConfig();
        
        assertEquals("te******34", result.getApiKey());
        assertNotEquals("test-api-key-1234", result.getApiKey());
        assertEquals("qwen-plus", result.getModel());
        assertEquals("http://studio.example.com", result.getStudioUrl());
        assertEquals("TestProject", result.getStudioProject());
        assertEquals("legacy", result.getSelectedConnection());
        assertEquals("legacy", result.getActiveConnection());
        assertEquals(1, result.getConnections().size());
        assertEquals("legacy", result.getConnections().get(0).getName());
    }
    
    @Test
    void testGetConfigReportsDeploymentOverriddenSelection() throws Exception {
        CopilotProperties stored = new CopilotProperties();
        stored.setSelectedConnection("alpha");
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE))
            .thenReturn(configDetail(stored));
        CopilotModelConnection alpha = deploymentConnection("alpha", true, true, null);
        CopilotModelConnection beta = deploymentConnection("beta", true, true, null);
        when(agentManager.getConnectionStatus()).thenReturn(new CopilotConnectionStatus(
            Arrays.asList(CopilotConnectionInfo.from(alpha), CopilotConnectionInfo.from(beta)),
            "beta", "beta", SelectionSource.DEPLOYMENT));
        
        ConsoleCopilotConfig result = getConfig();
        
        assertEquals("alpha", result.getSelectedConnection());
        assertEquals("beta", result.getEffectiveSelectedConnection());
        assertEquals(SelectionSource.DEPLOYMENT, result.getSelectedConnectionSource());
        assertEquals("beta", result.getActiveConnection());
    }
    
    @Test
    void testGetConfigReturnsDefaultWhenNull() throws Exception {
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE)).thenReturn(null);
        when(agentManager.getConnectionStatus()).thenReturn(statusOf(null));
        
        ConsoleCopilotConfig result = getConfig();
        
        assertNotNull(result);
        assertNull(result.getApiKey());
        assertEquals("qwen-turbo", result.getModel());
        assertEquals("NacosCopilot", result.getStudioProject());
        assertNull(result.getActiveConnection());
    }
    
    @Test
    void testGetConfigReturnsDefaultWhenReadFails() throws Exception {
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE))
            .thenThrow(new RuntimeException("read failed"));
        when(agentManager.getConnectionStatus()).thenReturn(statusOf(null));
        
        ConsoleCopilotConfig result = getConfig();
        
        assertNotNull(result);
        assertNull(result.getApiKey());
        assertEquals("qwen-turbo", result.getModel());
    }
    
    @Test
    void testSaveConfigSuccess() throws Exception {
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE)).thenReturn(null);
        when(configProxy.publishConfig(any(), any())).thenReturn(true);
        
        String body = "{\"apiKey\":\"new-key-1234\",\"model\":\"qwen-max\","
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
        Map<String, Object> saved = savedConfig();
        assertEquals("new-key-1234", saved.get("apiKey"));
        assertEquals("qwen-max", saved.get("model"));
    }
    
    @Test
    void testSaveConfigKeepsMaskedApiKey() throws Exception {
        CopilotProperties stored = new CopilotProperties();
        stored.setApiKey("keep-me-1234");
        stored.setModel("old-model");
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE))
            .thenReturn(configDetail(stored));
        when(configProxy.publishConfig(any(), any())).thenReturn(true);
        
        saveConfig("{\"apiKey\":\"ke******34\",\"model\":\"new-model\"}");
        
        Map<String, Object> saved = savedConfig();
        assertEquals("keep-me-1234", saved.get("apiKey"));
        assertEquals("new-model", saved.get("model"));
        assertFalse(saved.containsKey("apiKeyCleared"));
        verify(agentManager).refreshConfig();
    }
    
    @Test
    void testSaveConfigReplacesApiKey() throws Exception {
        CopilotProperties stored = new CopilotProperties();
        stored.setApiKey("old-key-1234");
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE))
            .thenReturn(configDetail(stored));
        when(configProxy.publishConfig(any(), any())).thenReturn(true);
        
        saveConfig("{\"apiKey\":\"new-key-5678\"}");
        
        Map<String, Object> saved = savedConfig();
        assertEquals("new-key-5678", saved.get("apiKey"));
        assertFalse(saved.containsKey("apiKeyCleared"));
    }
    
    @Test
    void testSaveConfigClearsApiKeyAndBlocksEnvironmentFallback() throws Exception {
        CopilotProperties stored = new CopilotProperties();
        stored.setApiKey("old-key-1234");
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE))
            .thenReturn(configDetail(stored));
        when(configProxy.publishConfig(any(), any())).thenReturn(true);
        
        saveConfig("{\"apiKey\":\"\"}");
        
        Map<String, Object> saved = savedConfig();
        assertEquals("", saved.get("apiKey"));
        assertEquals(Boolean.TRUE, saved.get("apiKeyCleared"));
        verify(agentManager).refreshConfig();
    }
    
    @Test
    void testSaveConfigEmptyApiKeyWithoutStoredKeyKeepsEnvironmentFallback() throws Exception {
        CopilotProperties stored = new CopilotProperties();
        stored.setModel("qwen-plus");
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE))
            .thenReturn(configDetail(stored));
        when(configProxy.publishConfig(any(), any())).thenReturn(true);
        
        saveConfig("{\"apiKey\":\"\"}");
        
        Map<String, Object> saved = savedConfig();
        assertFalse(saved.containsKey("apiKeyCleared"));
        assertNull(saved.get("apiKey"));
        assertEquals("qwen-plus", saved.get("model"));
    }
    
    @Test
    void testSaveConfigPreservesFieldsAbsentFromRequest() throws Exception {
        CopilotProperties stored = new CopilotProperties();
        stored.setApiKey("keep-me-1234");
        stored.setModel("keep-model");
        stored.setStudioUrl("keep-url");
        stored.setStudioProject("keep-proj");
        stored.setSelectedConnection("keep-connection");
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE))
            .thenReturn(configDetail(stored));
        when(configProxy.publishConfig(any(), any())).thenReturn(true);
        
        saveConfig("{\"model\":\"new-model\"}");
        
        Map<String, Object> saved = savedConfig();
        assertEquals("keep-me-1234", saved.get("apiKey"));
        assertEquals("new-model", saved.get("model"));
        assertEquals("keep-url", saved.get("studioUrl"));
        assertEquals("keep-proj", saved.get("studioProject"));
        assertEquals("keep-connection", saved.get("selectedConnection"));
    }
    
    @Test
    void testSaveConfigWritesTrimmedSelectedConnection() throws Exception {
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE)).thenReturn(null);
        when(configProxy.publishConfig(any(), any())).thenReturn(true);
        when(agentManager.getConnectionStatus()).thenReturn(
            statusOfConnections(deploymentConnection("prod-qwen", true, true, null)));
        
        saveConfig("{\"selectedConnection\":\"  prod-qwen  \"}");
        
        assertEquals("prod-qwen", savedConfig().get("selectedConnection"));
    }
    
    @Test
    void testSaveConfigStoresCanonicalSelectedConnectionName() throws Exception {
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE)).thenReturn(null);
        when(configProxy.publishConfig(any(), any())).thenReturn(true);
        when(agentManager.getConnectionStatus()).thenReturn(
            statusOfConnections(deploymentConnection("prod-qwen", true, true, null)));
        
        saveConfig("{\"selectedConnection\":\"PROD-QWEN\"}");
        
        assertEquals("prod-qwen", savedConfig().get("selectedConnection"));
    }
    
    @Test
    void testSaveConfigRejectsUnknownSelectedConnection() throws Exception {
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE)).thenReturn(null);
        when(agentManager.getConnectionStatus()).thenReturn(
            statusOfConnections(deploymentConnection("prod-qwen", true, true, null)));
        
        mockMvc.perform(post("/v3/console/copilot/config")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"selectedConnection\":\"missing\"}"))
            .andExpect(status().isBadRequest());
        
        verify(configProxy, never()).publishConfig(any(), any());
        verify(agentManager, never()).refreshConfig();
    }
    
    @Test
    void testSaveConfigRejectsDisabledSelectedConnection() throws Exception {
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE)).thenReturn(null);
        when(agentManager.getConnectionStatus()).thenReturn(
            statusOfConnections(deploymentConnection("prod-qwen", false, true, null)));
        
        mockMvc.perform(post("/v3/console/copilot/config")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"selectedConnection\":\"prod-qwen\"}"))
            .andExpect(status().isBadRequest());
        
        verify(configProxy, never()).publishConfig(any(), any());
    }
    
    @Test
    void testSaveConfigRejectsUnavailableSelectedConnection() throws Exception {
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE)).thenReturn(null);
        when(agentManager.getConnectionStatus()).thenReturn(
            statusOfConnections(deploymentConnection("prod-qwen", true, false, "no provider")));
        
        mockMvc.perform(post("/v3/console/copilot/config")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"selectedConnection\":\"prod-qwen\"}"))
            .andExpect(status().isBadRequest());
        
        verify(configProxy, never()).publishConfig(any(), any());
    }
    
    @Test
    void testGetConfigExposesActualCredentialSource() throws Exception {
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE)).thenReturn(null);
        CopilotModelConnection connection = CopilotModelConnection.builder()
            .name("legacy")
            .providerId("dashscope")
            .model("qwen-plus")
            .source(ConnectionSource.LEGACY)
            .apiKey("env-key")
            .credentialSource(CredentialSource.ENVIRONMENT)
            .build();
        when(agentManager.getConnectionStatus())
            .thenReturn(statusOfConnections(connection));
        
        ConsoleCopilotConfig result = getConfig();
        
        assertEquals(CredentialSource.ENVIRONMENT,
            result.getConnections().get(0).getActualSource());
        assertTrue(result.getConnections().get(0).isApiKeyConfigured());
    }
    
    @Test
    void testSaveConfigClearsSelectedConnection() throws Exception {
        CopilotProperties stored = new CopilotProperties();
        stored.setSelectedConnection("prod-qwen");
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE))
            .thenReturn(configDetail(stored));
        when(configProxy.publishConfig(any(), any())).thenReturn(true);
        
        saveConfig("{\"selectedConnection\":\"   \"}");
        
        assertFalse(savedConfig().containsKey("selectedConnection"));
    }
    
    @Test
    void testSaveConfigWithExplicitNullFieldsPreservesExisting() throws Exception {
        CopilotProperties existing = new CopilotProperties();
        existing.setApiKey("keep-this-key");
        existing.setModel("keep-model");
        existing.setStudioUrl("keep-url");
        existing.setStudioProject("keep-proj");
        existing.setSelectedConnection("keep-connection");
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE))
            .thenReturn(configDetail(existing));
        when(configProxy.publishConfig(any(), any())).thenReturn(true);
        
        String body = "{\"apiKey\":null,\"model\":null,\"studioUrl\":null,"
            + "\"studioProject\":null,\"selectedConnection\":null}";
        saveConfig(body);
        
        Map<String, Object> saved = savedConfig();
        assertEquals("keep-this-key", saved.get("apiKey"));
        assertEquals("keep-model", saved.get("model"));
        assertEquals("keep-url", saved.get("studioUrl"));
        assertEquals("keep-proj", saved.get("studioProject"));
        assertEquals("keep-connection", saved.get("selectedConnection"));
    }
    
    @Test
    void testSaveConfigReturnsFalseDoesNotRefresh() throws Exception {
        when(configProxy.getConfigDetail(DATA_ID, GROUP, NAMESPACE)).thenReturn(null);
        when(configProxy.publishConfig(any(), any())).thenReturn(false);
        
        MockHttpServletResponse response = mockMvc.perform(
            post("/v3/console/copilot/config")
                .contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"key-1234\"}"))
            .andExpect(status().isOk()).andReturn().getResponse();
        
        Result<Boolean> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertFalse(result.getData());
        verify(agentManager, never()).refreshConfig();
    }
    
    private ConsoleCopilotConfig getConfig() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
            get("/v3/console/copilot/config"))
            .andExpect(status().isOk()).andReturn().getResponse();
        Result<ConsoleCopilotConfig> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertNotNull(result.getData());
        return result.getData();
    }
    
    private void saveConfig(String body) throws Exception {
        mockMvc.perform(post("/v3/console/copilot/config")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
    }
    
    private Map<String, Object> savedConfig() throws Exception {
        ArgumentCaptor<ConfigForm> captor = ArgumentCaptor.forClass(ConfigForm.class);
        verify(configProxy).publishConfig(captor.capture(), any());
        Map<String, Object> saved = JacksonUtils.toObj(captor.getValue().getContent(),
            new TypeReference<>() {
            });
        assertNotNull(saved);
        return saved;
    }
    
    private CopilotConnectionStatus statusOf(String activeConnection) {
        CopilotModelConnection connection = CopilotModelConnection.builder()
            .name("legacy")
            .providerId("dashscope")
            .displayName("Legacy DashScope")
            .model("qwen-plus")
            .source(ConnectionSource.LEGACY)
            .priority(100)
            .apiKey("configured-key")
            .build();
        return new CopilotConnectionStatus(
            Collections.singletonList(CopilotConnectionInfo.from(connection)), activeConnection);
    }
    
    private CopilotConnectionStatus statusOfConnections(CopilotModelConnection... connections) {
        List<CopilotConnectionInfo> infos = Arrays.stream(connections)
            .map(CopilotConnectionInfo::from).collect(Collectors.toList());
        return new CopilotConnectionStatus(infos, null);
    }
    
    private CopilotModelConnection deploymentConnection(String name, boolean enabled,
        boolean available, String unavailableReason) {
        return CopilotModelConnection.builder()
            .name(name)
            .providerId("dashscope")
            .displayName(name)
            .model("qwen-max")
            .source(ConnectionSource.DEPLOYMENT)
            .priority(10)
            .apiKey("configured-key")
            .enabled(enabled)
            .available(available)
            .unavailableReason(unavailableReason)
            .build();
    }
    
    private ConfigDetailInfo configDetail(CopilotProperties config) {
        ConfigDetailInfo result = new ConfigDetailInfo();
        result.setContent(JacksonUtils.toJson(config));
        return result;
    }
}
