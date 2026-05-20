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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AgentSpecMaintainerServiceImpl.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class AgentSpecMaintainerServiceImplTest {
    
    @Mock
    private ClientHttpProxy clientHttpProxy;
    
    private AgentSpecMaintainerService agentSpecService;
    private AiMaintainerService aiMaintainerService;
    
    @BeforeEach
    void setUp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        aiMaintainerService = AiMaintainerFactory.createAiMaintainerService(properties);
        
        // Get the AgentSpecMaintainerService instance via reflection
        Field agentSpecServiceField =
            NacosAiMaintainerServiceImpl.class.getDeclaredField("agentSpecMaintainerService");
        agentSpecServiceField.setAccessible(true);
        agentSpecService =
            (AgentSpecMaintainerService) agentSpecServiceField.get(aiMaintainerService);
        
        // Inject mock ClientHttpProxy
        injectMockClientHttpProxy(agentSpecService);
    }
    
    private void injectMockClientHttpProxy(Object service)
        throws NoSuchFieldException, IllegalAccessException {
        Field contextField = AbstractAiDelegateMaintainerService.class.getDeclaredField("context");
        contextField.setAccessible(true);
        Object context = contextField.get(service);
        Field clientHttpProxyField =
            AiMaintainerHttpContext.class.getDeclaredField("clientHttpProxy");
        clientHttpProxyField.setAccessible(true);
        clientHttpProxyField.set(context, clientHttpProxy);
    }
    
    // ========== getAgentSpecDetail Tests ==========
    
    @Test
    @DisplayName("getAgentSpecDetail with editingVersion should return spec")
    void testGetAgentSpecDetailWithEditingVersion() throws NacosException {
        AgentSpecMeta meta = new AgentSpecMeta();
        meta.setEditingVersion("v1");
        
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName("testAgentSpec");
        
        HttpRestResult<String> metaResult = new HttpRestResult<>();
        metaResult.setData(JacksonUtils.toJson(Result.success(meta)));
        HttpRestResult<String> specResult = new HttpRestResult<>();
        specResult.setData(JacksonUtils.toJson(Result.success(agentSpec)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(metaResult, specResult);
        
        AgentSpec actual = agentSpecService.getAgentSpecDetail("public", "testAgentSpec");
        assertNotNull(actual);
        assertEquals("testAgentSpec", actual.getName());
    }
    
    @Test
    @DisplayName("getAgentSpecDetail with reviewingVersion should return spec")
    void testGetAgentSpecDetailWithReviewingVersion() throws NacosException {
        AgentSpecMeta meta = new AgentSpecMeta();
        meta.setReviewingVersion("v2");
        
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName("testAgentSpec");
        
        HttpRestResult<String> metaResult = new HttpRestResult<>();
        metaResult.setData(JacksonUtils.toJson(Result.success(meta)));
        HttpRestResult<String> specResult = new HttpRestResult<>();
        specResult.setData(JacksonUtils.toJson(Result.success(agentSpec)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(metaResult, specResult);
        
        AgentSpec actual = agentSpecService.getAgentSpecDetail("public", "testAgentSpec");
        assertNotNull(actual);
        assertEquals("testAgentSpec", actual.getName());
    }
    
    @Test
    @DisplayName("getAgentSpecDetail with latest label should return spec")
    void testGetAgentSpecDetailWithLatestLabel() throws NacosException {
        AgentSpecMeta meta = new AgentSpecMeta();
        Map<String, String> labels = new HashMap<>();
        labels.put("latest", "v3");
        meta.setLabels(labels);
        
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName("testAgentSpec");
        
        HttpRestResult<String> metaResult = new HttpRestResult<>();
        metaResult.setData(JacksonUtils.toJson(Result.success(meta)));
        HttpRestResult<String> specResult = new HttpRestResult<>();
        specResult.setData(JacksonUtils.toJson(Result.success(agentSpec)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(metaResult, specResult);
        
        AgentSpec actual = agentSpecService.getAgentSpecDetail("public", "testAgentSpec");
        assertNotNull(actual);
        assertEquals("testAgentSpec", actual.getName());
    }
    
    @Test
    @DisplayName("getAgentSpecDetail with null meta should return null")
    void testGetAgentSpecDetailWithNullMeta() throws NacosException {
        HttpRestResult<String> metaResult = new HttpRestResult<>();
        metaResult.setData(JacksonUtils.toJson(Result.success(null)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(metaResult);
        
        AgentSpec actual = agentSpecService.getAgentSpecDetail("public", "testAgentSpec");
        assertNull(actual);
    }
    
    @Test
    @DisplayName("getAgentSpecDetail with first available version should return spec")
    void testGetAgentSpecDetailWithFirstAvailableVersion() throws NacosException {
        AgentSpecMeta meta = new AgentSpecMeta();
        AgentSpecMeta.AgentSpecVersionSummary versionSummary =
            new AgentSpecMeta.AgentSpecVersionSummary();
        versionSummary.setVersion("v4");
        meta.setVersions(Collections.singletonList(versionSummary));
        
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName("testAgentSpec");
        
        HttpRestResult<String> metaResult = new HttpRestResult<>();
        metaResult.setData(JacksonUtils.toJson(Result.success(meta)));
        HttpRestResult<String> specResult = new HttpRestResult<>();
        specResult.setData(JacksonUtils.toJson(Result.success(agentSpec)));
        
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(metaResult, specResult);
        
        AgentSpec actual = agentSpecService.getAgentSpecDetail("public", "testAgentSpec");
        assertNotNull(actual);
        assertEquals("testAgentSpec", actual.getName());
    }
    
    // ========== Lifecycle API Tests ==========
    
    @Test
    @DisplayName("createDraft should return draft version")
    void testCreateDraftReturnsVersion() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("v1")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(mockRestResult);
        
        String actual = agentSpecService.createDraft("public", "testAgentSpec", "v0", null);
        assertEquals("v1", actual);
    }
    
    @Test
    @DisplayName("updateDraft with setAsLatest should execute")
    void testUpdateDraftWithSetAsLatest() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(mockRestResult);
        
        boolean actual = agentSpecService.updateDraft("public", "agentSpecCardJson", true);
        assertTrue(actual);
    }
    
    @Test
    @DisplayName("updateDraft with null setAsLatest should skip param")
    void testUpdateDraftWithNullSetAsLatest() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(mockRestResult);
        
        boolean actual = agentSpecService.updateDraft("public", "agentSpecCardJson", null);
        assertTrue(actual);
    }
    
    @Test
    @DisplayName("deleteDraft should return true")
    void testDeleteDraftReturnsTrue() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult
            .setData(JacksonUtils.toJson(new Result<>(ErrorCode.SUCCESS.getCode(), "ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(mockRestResult);
        
        boolean actual = agentSpecService.deleteDraft("public", "testAgentSpec");
        assertTrue(actual);
    }
    
    @Test
    @DisplayName("submit should return submitted version")
    void testSubmitReturnsVersion() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("v1")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(mockRestResult);
        
        String actual = agentSpecService.submit("public", "testAgentSpec", "v1");
        assertEquals("v1", actual);
    }
    
    @Test
    @DisplayName("publish should return true")
    void testPublishReturnsTrue() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult
            .setData(JacksonUtils.toJson(new Result<>(ErrorCode.SUCCESS.getCode(), "ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(mockRestResult);
        
        boolean actual = agentSpecService.publish("public", "testAgentSpec", "v1", true);
        assertTrue(actual);
    }
    
    @Test
    @DisplayName("redraft should return true")
    void testRedraftReturnsTrue() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult
            .setData(JacksonUtils.toJson(new Result<>(ErrorCode.SUCCESS.getCode(), "ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(mockRestResult);
        
        boolean actual = agentSpecService.redraft("public", "testAgentSpec", "v1");
        assertTrue(actual);
    }
    
    @Test
    @DisplayName("changeOnlineStatus online should return true")
    void testChangeOnlineStatusOnline() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult
            .setData(JacksonUtils.toJson(new Result<>(ErrorCode.SUCCESS.getCode(), "ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(mockRestResult);
        
        boolean actual =
            agentSpecService.changeOnlineStatus("public", "testAgentSpec", "PUBLIC", "v1", true);
        assertTrue(actual);
    }
    
    @Test
    @DisplayName("changeOnlineStatus offline should return true")
    void testChangeOnlineStatusOffline() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult
            .setData(JacksonUtils.toJson(new Result<>(ErrorCode.SUCCESS.getCode(), "ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(mockRestResult);
        
        boolean actual =
            agentSpecService.changeOnlineStatus("public", "testAgentSpec", "PRIVATE", "v1", false);
        assertTrue(actual);
    }
    
    @Test
    @DisplayName("uploadAgentSpecFromZip should return agentSpec name")
    void testUploadAgentSpecFromZip() throws NacosException {
        byte[] zipBytes = "test zip content".getBytes();
        
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("uploadedAgentSpec")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(mockRestResult);
        
        String actual = agentSpecService.uploadAgentSpecFromZip("public", zipBytes, false);
        assertEquals("uploadedAgentSpec", actual);
    }
}
