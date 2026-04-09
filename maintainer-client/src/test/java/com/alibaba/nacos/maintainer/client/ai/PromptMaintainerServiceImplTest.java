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
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PromptMaintainerServiceImpl.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class PromptMaintainerServiceImplTest {

    @Mock
    private ClientHttpProxy clientHttpProxy;

    private PromptMaintainerService promptService;
    private AiMaintainerService aiMaintainerService;

    @BeforeEach
    void setUp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        aiMaintainerService = AiMaintainerFactory.createAiMaintainerService(properties);

        // Get the PromptMaintainerService instance via reflection
        Field promptServiceField = NacosAiMaintainerServiceImpl.class.getDeclaredField("promptMaintainerService");
        promptServiceField.setAccessible(true);
        promptService = (PromptMaintainerService) promptServiceField.get(aiMaintainerService);

        // Inject mock ClientHttpProxy
        injectMockClientHttpProxy(promptService);
    }

    private void injectMockClientHttpProxy(Object service) throws NoSuchFieldException, IllegalAccessException {
        Field contextField = AbstractAiDelegateMaintainerService.class.getDeclaredField("context");
        contextField.setAccessible(true);
        Object context = contextField.get(service);
        Field clientHttpProxyField = AiMaintainerHttpContext.class.getDeclaredField("clientHttpProxy");
        clientHttpProxyField.setAccessible(true);
        clientHttpProxyField.set(context, clientHttpProxy);
    }

    // ========== Query Operations Tests ==========

    @Test
    @DisplayName("listPrompts with all params should return paged result")
    void listPrompts_withAllParams_shouldReturnPagedResult() throws NacosException {
        Page<PromptMetaSummary> page = new Page<>();
        page.setPagesAvailable(1);
        page.setTotalCount(1);
        page.setPageNumber(1);
        page.setPageItems(Collections.singletonList(new PromptMetaSummary()));

        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        Page<PromptMetaSummary> actual = promptService.listPrompts("public", "testPrompt", "search", "tags", 1, 10);
        assertEquals(page.getPageNumber(), actual.getPageNumber());
        assertEquals(page.getTotalCount(), actual.getTotalCount());
        assertEquals(1, actual.getPageItems().size());
    }

    @Test
    @DisplayName("listPrompts with null bizTags should handle gracefully")
    void listPrompts_withNullBizTags_shouldHandleGracefully() throws NacosException {
        Page<PromptMetaSummary> page = new Page<>();
        page.setTotalCount(0);
        page.setPageNumber(1);
        page.setPageItems(Collections.emptyList());

        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        Page<PromptMetaSummary> actual = promptService.listPrompts("public", "testPrompt", null, null, 1, 10);
        assertEquals(0, actual.getTotalCount());
    }

    @Test
    @DisplayName("deletePrompt should return true on success")
    void deletePrompt_shouldReturnTrueOnSuccess() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(true)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        boolean actual = promptService.deletePrompt("public", "testPrompt");
        assertTrue(actual);
    }

    @Test
    @DisplayName("deletePrompt should return false on failure")
    void deletePrompt_shouldReturnFalseOnFailure() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(false)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        boolean actual = promptService.deletePrompt("public", "testPrompt");
        assertFalse(actual);
    }

    @Test
    @DisplayName("listPromptVersions should return paged versions")
    void listPromptVersions_shouldReturnPagedVersions() throws NacosException {
        Page<PromptVersionSummary> page = new Page<>();
        page.setPagesAvailable(1);
        page.setTotalCount(1);
        page.setPageNumber(1);
        page.setPageItems(Collections.singletonList(new PromptVersionSummary()));

        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(page)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        Page<PromptVersionSummary> actual = promptService.listPromptVersions("public", "testPrompt", 1, 10);
        assertEquals(page.getPageNumber(), actual.getPageNumber());
        assertEquals(1, actual.getPageItems().size());
    }

    // ========== Lifecycle API Tests ==========

    @Test
    @DisplayName("getPromptGovernanceDetail should return meta info")
    void getPromptGovernanceDetail_shouldReturnMetaInfo() throws NacosException {
        PromptMetaInfo metaInfo = new PromptMetaInfo();
        metaInfo.setPromptKey("testPrompt");

        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(metaInfo)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        PromptMetaInfo actual = promptService.getPromptGovernanceDetail("public", "testPrompt");
        assertNotNull(actual);
        assertEquals("testPrompt", actual.getPromptKey());
    }

    @Test
    @DisplayName("getVersionDetail should return version info")
    void getVersionDetail_shouldReturnVersionInfo() throws NacosException {
        PromptVersionInfo versionInfo = new PromptVersionInfo();
        versionInfo.setVersion("v1");

        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(versionInfo)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        PromptVersionInfo actual = promptService.getVersionDetail("public", "testPrompt", "v1");
        assertNotNull(actual);
        assertEquals("v1", actual.getVersion());
    }

    @Test
    @DisplayName("createDraft with all params should return version")
    void createDraft_withAllParams_shouldReturnVersion() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("v1")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        String actual = promptService.createDraft("public", "testPrompt", "v0", "v1", 
                "template", "variables", "commitMsg", "description", "tags");
        assertEquals("v1", actual);
    }

    @Test
    @DisplayName("createDraft with null optional params should still create")
    void createDraft_withNullOptionalParams_shouldStillCreate() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("v1")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        String actual = promptService.createDraft("public", "testPrompt", null, null, 
                null, null, null, null, null);
        assertEquals("v1", actual);
    }

    @Test
    @DisplayName("updateDraft should execute successfully")
    void updateDraft_shouldExecuteSuccessfully() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        promptService.updateDraft("public", "testPrompt", "template", "variables", "commitMsg");
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }

    @Test
    @DisplayName("deleteDraft should execute successfully")
    void deleteDraft_shouldExecuteSuccessfully() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        promptService.deleteDraft("public", "testPrompt");
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }

    @Test
    @DisplayName("submit should return submitted version")
    void submit_shouldReturnSubmittedVersion() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("v1")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        String actual = promptService.submit("public", "testPrompt", "v1");
        assertEquals("v1", actual);
    }

    @Test
    @DisplayName("submit with null version should use editing version")
    void submit_withNullVersion_shouldUseEditingVersion() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("editing-version")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        String actual = promptService.submit("public", "testPrompt", null);
        assertEquals("editing-version", actual);
    }

    @Test
    @DisplayName("publish with updateLatestLabel true should execute")
    void publish_withUpdateLatestLabelTrue_shouldExecute() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        promptService.publish("public", "testPrompt", "v1", true);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }

    @Test
    @DisplayName("publish with null updateLatestLabel should skip param")
    void publish_withNullUpdateLatestLabel_shouldSkipParam() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        promptService.publish("public", "testPrompt", "v1", null);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }

    @Test
    @DisplayName("forcePublish should execute force publish path")
    void forcePublish_shouldExecuteForcePublishPath() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        promptService.forcePublish("public", "testPrompt", "v1", true);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }

    @Test
    @DisplayName("changeOnlineStatus online should call online path")
    void changeOnlineStatus_online_shouldCallOnlinePath() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        promptService.changeOnlineStatus("public", "testPrompt", "v1", true);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }

    @Test
    @DisplayName("changeOnlineStatus offline should call offline path")
    void changeOnlineStatus_offline_shouldCallOfflinePath() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        promptService.changeOnlineStatus("public", "testPrompt", "v1", false);
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }

    @Test
    @DisplayName("updateLabels should execute successfully")
    void updateLabels_shouldExecuteSuccessfully() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        promptService.updateLabels("public", "testPrompt", "labels");
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }

    @Test
    @DisplayName("updateDescription should execute successfully")
    void updateDescription_shouldExecuteSuccessfully() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        promptService.updateDescription("public", "testPrompt", "description");
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }

    @Test
    @DisplayName("updateBizTags should execute successfully")
    void updateBizTags_shouldExecuteSuccessfully() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        promptService.updateBizTags("public", "testPrompt", "bizTags");
        verify(clientHttpProxy, times(1)).executeSyncHttpRequest(any(HttpRequest.class));
    }

    // ========== Deprecated API Tests ==========

    @Test
    @DisplayName("getPromptMeta deprecated should return meta info")
    void getPromptMeta_deprecated_shouldReturnMetaInfo() throws NacosException {
        PromptMetaInfo metaInfo = new PromptMetaInfo();
        metaInfo.setPromptKey("testPrompt");

        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(metaInfo)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        PromptMetaInfo actual = promptService.getPromptMeta("public", "testPrompt");
        assertNotNull(actual);
        assertEquals("testPrompt", actual.getPromptKey());
    }

    @Test
    @DisplayName("queryPromptDetail deprecated should return version info")
    void queryPromptDetail_deprecated_shouldReturnVersionInfo() throws NacosException {
        PromptVersionInfo versionInfo = new PromptVersionInfo();
        versionInfo.setVersion("v1");

        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(versionInfo)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        PromptVersionInfo actual = promptService.queryPromptDetail("public", "testPrompt", "v1", "latest");
        assertNotNull(actual);
        assertEquals("v1", actual.getVersion());
    }

    @Test
    @DisplayName("bindLabel deprecated should return true")
    void bindLabel_deprecated_shouldReturnTrue() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(true)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        boolean actual = promptService.bindLabel("public", "testPrompt", "latest", "v1");
        assertTrue(actual);
    }

    @Test
    @DisplayName("unbindLabel deprecated should return true")
    void unbindLabel_deprecated_shouldReturnTrue() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(true)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        boolean actual = promptService.unbindLabel("public", "testPrompt", "latest");
        assertTrue(actual);
    }

    @Test
    @DisplayName("publishPrompt deprecated should return true")
    void publishPrompt_deprecated_shouldReturnTrue() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(true)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        boolean actual = promptService.publishPrompt("public", "testPrompt", "v1", 
                "template", "commitMsg", "description", "bizTags");
        assertTrue(actual);
    }

    @Test
    @DisplayName("updatePromptMetadata deprecated should return true")
    void updatePromptMetadata_deprecated_shouldReturnTrue() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(true)));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(mockRestResult);

        boolean actual = promptService.updatePromptMetadata("public", "testPrompt", "description", "bizTags");
        assertTrue(actual);
    }
}