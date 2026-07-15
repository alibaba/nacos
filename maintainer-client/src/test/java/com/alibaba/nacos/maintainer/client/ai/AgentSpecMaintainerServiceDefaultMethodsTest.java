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
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecBasicInfo;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
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
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AgentSpecMaintainerService interface default methods.
 * Tests the convenience methods that provide simplified parameter signatures.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class AgentSpecMaintainerServiceDefaultMethodsTest {
    
    @Mock
    private ClientHttpProxy clientHttpProxy;
    
    private AgentSpecMaintainerService agentSpecService;
    
    @BeforeEach
    void setUp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        AiMaintainerService aiMaintainerService =
            AiMaintainerFactory.createAiMaintainerService(properties);
        
        Field agentSpecServiceField =
            NacosAiMaintainerServiceImpl.class.getDeclaredField("agentSpecMaintainerService");
        agentSpecServiceField.setAccessible(true);
        agentSpecService =
            (AgentSpecMaintainerService) agentSpecServiceField.get(aiMaintainerService);
        
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
    
    // ========== getAgentSpecDetail default method tests ==========
    
    @Test
    @DisplayName("getAgentSpecDetail(agentSpecName) should use default namespace")
    void testGetAgentSpecDetailWithDefaultNamespace() throws NacosException {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName("testAgentSpec");
        AgentSpecMeta meta = new AgentSpecMeta();
        meta.setEditingVersion("v1");
        
        HttpRestResult<String> metaResult = new HttpRestResult<>();
        metaResult.setData(JacksonUtils.toJson(Result.success(meta)));
        HttpRestResult<String> specResult = new HttpRestResult<>();
        specResult.setData(JacksonUtils.toJson(Result.success(agentSpec)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(metaResult,
            specResult);
        
        AgentSpec result = agentSpecService.getAgentSpecDetail("testAgentSpec");
        assertNotNull(result);
        assertEquals("testAgentSpec", result.getName());
    }
    
    // ========== getAgentSpecVersionDetail default method tests ==========
    
    @Test
    @DisplayName("getAgentSpecVersionDetail(agentSpecName, version) should use default namespace")
    void testGetAgentSpecVersionDetailWithDefaultNamespace() throws NacosException {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName("testAgentSpec");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(agentSpec)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        AgentSpec result = agentSpecService.getAgentSpecVersionDetail("testAgentSpec", "v1");
        assertNotNull(result);
        assertEquals("testAgentSpec", result.getName());
    }
    
    // ========== getAgentSpecVersionMeta default method tests ==========
    
    @Test
    @DisplayName("getAgentSpecVersionMeta(agentSpecName, version) should use default namespace")
    void testGetAgentSpecVersionMetaWithDefaultNamespace() throws NacosException {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName("testAgentSpec");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(agentSpec)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        AgentSpec result = agentSpecService.getAgentSpecVersionMeta("testAgentSpec", "v1");
        assertNotNull(result);
        assertEquals("testAgentSpec", result.getName());
    }
    
    // ========== deleteAgentSpec default method tests ==========
    
    @Test
    @DisplayName("deleteAgentSpec(agentSpecName) should use default namespace")
    void testDeleteAgentSpecWithDefaultNamespace() throws NacosException {
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        boolean result = agentSpecService.deleteAgentSpec("testAgentSpec");
        assertTrue(result);
    }
    
    // ========== listAgentSpecs default method tests ==========
    
    @Test
    @DisplayName("listAgentSpecs(agentSpecName, pageNo, pageSize) should use default namespace and blur search")
    void testListAgentSpecsWithDefaults() throws NacosException {
        Page<AgentSpecBasicInfo> page = new Page<>();
        page.setTotalCount(1);
        page.setPageItems(Collections.singletonList(new AgentSpecBasicInfo()));
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<AgentSpecBasicInfo> result = agentSpecService.listAgentSpecs("testAgentSpec", 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
    }
    
    // ========== listAgentSpecAdminItems default method tests ==========
    
    @Test
    @DisplayName("listAgentSpecAdminItems with orderBy/owner/scope should delegate to simpler version")
    void testListAgentSpecAdminItemsWithFilters() throws NacosException {
        Page<AgentSpecSummary> page = new Page<>();
        page.setTotalCount(0);
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<AgentSpecSummary> result = agentSpecService.listAgentSpecAdminItems("public", "test",
            "blur", null, null, null, 1, 10);
        assertNotNull(result);
    }
    
    // ========== uploadAgentSpecFromZip default method tests ==========
    
    @Test
    @DisplayName("uploadAgentSpecFromZip(namespaceId, zipBytes) should not overwrite")
    void testUploadAgentSpecFromZipWithNamespace() throws NacosException {
        byte[] zipBytes = "test zip content".getBytes();
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("uploadedAgentSpec")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        String result = agentSpecService.uploadAgentSpecFromZip("public", zipBytes);
        assertEquals("uploadedAgentSpec", result);
    }
    
    @Test
    @DisplayName("uploadAgentSpecFromZip(zipBytes) should use default namespace and not overwrite")
    void testUploadAgentSpecFromZipWithDefaults() throws NacosException {
        byte[] zipBytes = "test zip content".getBytes();
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success("uploadedAgentSpec")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        String result = agentSpecService.uploadAgentSpecFromZip(zipBytes);
        assertEquals("uploadedAgentSpec", result);
    }
    
    private static void assertTrue(boolean result) {
        org.junit.jupiter.api.Assertions.assertTrue(result);
    }
}
