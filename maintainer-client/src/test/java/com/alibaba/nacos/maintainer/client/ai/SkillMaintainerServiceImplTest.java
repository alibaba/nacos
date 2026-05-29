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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SkillMaintainerServiceImpl.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class SkillMaintainerServiceImplTest {
    
    @Mock
    private ClientHttpProxy clientHttpProxy;
    
    private SkillMaintainerService skillService;
    private AiMaintainerService aiMaintainerService;
    
    @BeforeEach
    void setUp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        aiMaintainerService = AiMaintainerFactory.createAiMaintainerService(properties);
        
        // Get the SkillMaintainerService instance via reflection
        Field skillServiceField =
            NacosAiMaintainerServiceImpl.class.getDeclaredField("skillMaintainerService");
        skillServiceField.setAccessible(true);
        skillService = (SkillMaintainerService) skillServiceField.get(aiMaintainerService);
        
        // Inject mock ClientHttpProxy
        injectMockClientHttpProxy(skillService);
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
    
    // ========== Lifecycle API Tests ==========
    
    @Test
    @DisplayName("createDraft with targetVersion should return version")
    void testCreateDraftWithTargetVersion() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("v1")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(mockRestResult);
        
        String actual =
            skillService.createDraft("public", "testSkill", "v0", "v1", "skillCardJson");
        assertEquals("v1", actual);
    }
    
    @Test
    @DisplayName("createDraft with null skillCard should still create")
    void testCreateDraftWithNullSkillCard() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("v1")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(mockRestResult);
        
        String actual = skillService.createDraft("public", "testSkill", null, null, null);
        assertEquals("v1", actual);
    }
    
    @Test
    @DisplayName("uploadSkillFromZip with targetVersion and commitMsg should include params")
    void testUploadSkillFromZipWithTargetVersionAndCommitMsg() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("test-skill")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(mockRestResult);
        
        String actual = skillService.uploadSkillFromZip("public", "zip".getBytes(), true,
            "v2", "upload commit");
        
        assertEquals("test-skill", actual);
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(clientHttpProxy).executeSyncHttpRequest(requestCaptor.capture());
        HttpRequest request = requestCaptor.getValue();
        assertEquals("true", request.getParamValues().get("overwrite"));
        assertEquals("v2", request.getParamValues().get("targetVersion"));
        assertEquals("upload commit", request.getParamValues().get("commitMsg"));
        assertTrue(request.isFileUpload());
    }
    
    @Test
    @DisplayName("updateDraft with setAsLatest should return true")
    void testUpdateDraftWithSetAsLatest() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult
            .setData(JacksonUtils.toJson(new Result<>(ErrorCode.SUCCESS.getCode(), "ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(mockRestResult);
        
        boolean actual = skillService.updateDraft("public", "skillCardJson", true);
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
        
        boolean actual = skillService.deleteDraft("public", "testSkill");
        assertTrue(actual);
    }
    
    @Test
    @DisplayName("submit should return submitted version")
    void testSubmitReturnsVersion() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success("v1")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(mockRestResult);
        
        String actual = skillService.submit("public", "testSkill", "v1");
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
        
        boolean actual = skillService.publish("public", "testSkill", "v1", true);
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
        
        boolean actual = skillService.redraft("public", "testSkill", "v1");
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
            skillService.changeOnlineStatus("public", "testSkill", "PUBLIC", "v1", true);
        assertTrue(actual);
    }
    
    @Test
    @DisplayName("changeOnlineStatus with null scope should handle gracefully")
    void testChangeOnlineStatusWithNullScope() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult
            .setData(JacksonUtils.toJson(new Result<>(ErrorCode.SUCCESS.getCode(), "ok")));
        when(clientHttpProxy.executeSyncHttpRequest(any(HttpRequest.class)))
            .thenReturn(mockRestResult);
        
        boolean actual = skillService.changeOnlineStatus("public", "testSkill", null, "v1", false);
        assertTrue(actual);
    }
}
