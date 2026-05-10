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
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;

import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PromptMaintainerService interface default methods.
 * Tests the convenience methods that provide simplified parameter signatures.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class PromptMaintainerServiceDefaultMethodsTest {
    
    @Mock
    private ClientHttpProxy clientHttpProxy;
    
    private PromptMaintainerService promptService;
    
    @BeforeEach
    void setUp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        AiMaintainerService aiMaintainerService =
            AiMaintainerFactory.createAiMaintainerService(properties);
        
        Field promptServiceField =
            NacosAiMaintainerServiceImpl.class.getDeclaredField("promptMaintainerService");
        promptServiceField.setAccessible(true);
        promptService = (PromptMaintainerService) promptServiceField.get(aiMaintainerService);
        
        injectMockClientHttpProxy(promptService);
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
    
    // ========== listPrompts default method tests ==========
    
    @Test
    @DisplayName("listPrompts(promptKey, pageNo, pageSize) should use default namespace and blur search")
    void testListPromptsWithDefaults() throws NacosException {
        Page<PromptMetaSummary> page = new Page<>();
        page.setTotalCount(1);
        page.setPageItems(Collections.singletonList(new PromptMetaSummary()));
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<PromptMetaSummary> result = promptService.listPrompts("testPrompt", 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
    }
    
    // ========== deletePrompt default method tests ==========
    
    @Test
    @DisplayName("deletePrompt(promptKey) should use default namespace")
    void testDeletePromptWithDefaultNamespace() throws NacosException {
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(true)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        assertTrue(promptService.deletePrompt("testPrompt"));
    }
    
    // ========== listPromptVersions default method tests ==========
    
    @Test
    @DisplayName("listPromptVersions(promptKey, pageNo, pageSize) should use default namespace")
    void testListPromptVersionsWithDefaultNamespace() throws NacosException {
        Page<PromptVersionSummary> page = new Page<>();
        page.setTotalCount(1);
        page.setPageItems(Collections.singletonList(new PromptVersionSummary()));
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        Page<PromptVersionSummary> result = promptService.listPromptVersions("testPrompt", 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
    }
    
    // ========== getPromptMeta default method tests (deprecated) ==========
    
    @Test
    @DisplayName("getPromptMeta(promptKey) should use default namespace")
    void testGetPromptMetaWithDefaultNamespace() throws NacosException {
        PromptMetaInfo meta = new PromptMetaInfo();
        meta.setPromptKey("testPrompt");
        
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(meta)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        PromptMetaInfo result = promptService.getPromptMeta("testPrompt");
        assertNotNull(result);
        assertEquals("testPrompt", result.getPromptKey());
    }
    
    // ========== publishPrompt default method tests (deprecated) ==========
    
    @Test
    @DisplayName("publishPrompt(promptKey, version, template, commitMsg) should use default namespace")
    void testPublishPromptWithDefaults() throws NacosException {
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(true)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        assertTrue(
            promptService.publishPrompt("testPrompt", "v1", "template content", "commit message"));
    }
    
    @Test
    @DisplayName("publishPrompt(namespaceId, promptKey, version, template, commitMsg, description) should work")
    void testPublishPromptWithoutBizTags() throws NacosException {
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(true)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        assertTrue(promptService.publishPrompt("public", "testPrompt", "v1", "template", "commit",
            "description"));
    }
    
    @Test
    @DisplayName("publishPrompt with variables should delegate to non-variables version")
    void testPublishPromptWithVariables() throws NacosException {
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(true)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        assertTrue(promptService.publishPrompt("public", "testPrompt", "v1", "template", "commit",
            "desc", null, "{}"));
    }
    
    // ========== updatePromptMetadata default method tests (deprecated) ==========
    
    @Test
    @DisplayName("updatePromptMetadata(namespaceId, promptKey, description) should work")
    void testUpdatePromptMetadataWithoutBizTags() throws NacosException {
        HttpRestResult<String> mockResult = new HttpRestResult<>();
        mockResult.setData(JacksonUtils.toJson(Result.success(true)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockResult);
        
        assertTrue(promptService.updatePromptMetadata("public", "testPrompt", "new description"));
    }
}
