/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.prompt;

import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptLabelVersionMapping;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class PromptClientOperationServiceImplTest {
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    private PromptClientOperationServiceImpl service;
    
    @BeforeEach
    void setUp() {
        service = new PromptClientOperationServiceImpl(configQueryChainService);
    }
    
    @Test
    void queryPromptShouldThrowInvalidParamWhenPromptKeyBlank() {
        assertThrows(NacosException.class, () -> service.queryPrompt("public", "", null, null, null));
    }
    
    @Test
    void queryPromptShouldThrowNotFoundWhenMetaMissing() {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
                .thenReturn(notFoundResponse());
        assertThrows(NacosException.class, () -> service.queryPrompt("public", "p1", null, null, null));
    }
    
    @Test
    void queryPromptShouldReturnPromptVersionInfoWhenLabelResolved() throws NacosException {
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setPromptKey("p1");
        mapping.setLabels(new HashMap<>());
        mapping.getLabels().put("prod", "1.0.0");
        mapping.setVersions(new ArrayList<>());
        mapping.getVersions().add("1.0.0");
        mapping.setLatestVersion("1.0.0");
        
        PromptVersionInfo versionInfo = new PromptVersionInfo();
        versionInfo.setVersion("1.0.0");
        versionInfo.setTemplate("hello");
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
                .thenReturn(foundResponse(mapping, "m-meta"))
                .thenReturn(foundResponse(versionInfo, "m-ver"));
        
        PromptVersionInfo actual = service.queryPrompt("public", "p1", null, "prod", null);
        
        assertEquals("p1", actual.getPromptKey());
        assertEquals("1.0.0", actual.getVersion());
        assertEquals("hello", actual.getTemplate());
        assertEquals("m-ver", actual.getMd5());
    }
    
    @Test
    void queryPromptShouldInvalidateMetaCacheWhenVersionDataMissing() throws Exception {
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setPromptKey("p1");
        mapping.setLabels(new HashMap<>());
        mapping.setVersions(new ArrayList<>());
        mapping.getVersions().add("1.0.0");
        mapping.setLatestVersion("1.0.0");
        String metaDataId = "p1.label-version-mapping.json";
        String versionDataId = "p1.1.0.0.json";
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenAnswer(invocation -> {
            ConfigQueryChainRequest request = invocation.getArgument(0);
            if (metaDataId.equals(request.getDataId())) {
                return foundResponse(mapping, "m-meta");
            }
            if (versionDataId.equals(request.getDataId())) {
                return notFoundResponse();
            }
            return notFoundResponse();
        });
        service.getPromptMeta("public", "p1");
        assertEquals(1, getMetaCache().size());

        assertThrows(NacosException.class, () -> service.queryPrompt("public", "p1", "1.0.0", null, null));
        
        assertTrue(getMetaCache().isEmpty());
    }
    
    @Test
    void getPromptMetaShouldReturnCloneAndUseCache() throws Exception {
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setPromptKey("p1");
        mapping.setLabels(new HashMap<>());
        mapping.getLabels().put("prod", "1.0.0");
        mapping.setVersions(new ArrayList<>());
        mapping.getVersions().add("1.0.0");
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
                .thenReturn(foundResponse(mapping, "m-meta"));
        
        PromptMetaInfo first = service.getPromptMeta("public", "p1");
        first.getLabels().put("gray", "2.0.0");
        
        PromptMetaInfo second = service.getPromptMeta("public", "p1");
        assertNotNull(second.getLabels().get("prod"));
        assertEquals(null, second.getLabels().get("gray"));
        verify(configQueryChainService, times(1)).handle(any(ConfigQueryChainRequest.class));
    }
    
    @Test
    void queryPromptShouldThrowNotModifiedWhenMd5UpToDate() throws NacosException {
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setPromptKey("p1");
        mapping.setLabels(new HashMap<>());
        mapping.setVersions(new ArrayList<>());
        mapping.getVersions().add("1.0.0");
        mapping.setLatestVersion("1.0.0");
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
                .thenReturn(foundResponse(mapping, "m-meta"));
        String groupKey = GroupKey2.getKey("p1.1.0.0.json", "nacos-ai-prompt", "public");
        try (MockedStatic<ConfigCacheService> mocked = mockStatic(ConfigCacheService.class)) {
            mocked.when(() -> ConfigCacheService.isUptodate(groupKey, "m1"))
                    .thenReturn(true);
            assertThrows(NacosException.class, () -> service.queryPrompt("public", "p1", "1.0.0", null, "m1"));
        }
    }
    
    @Test
    void getPromptMetaShouldReloadWhenCacheExpired() throws NacosException {
        String oldExpire = System.getProperty("nacos.prompt.meta.cache.expireSeconds");
        System.setProperty("nacos.prompt.meta.cache.expireSeconds", "0");
        try {
            PromptClientOperationServiceImpl expiredService = new PromptClientOperationServiceImpl(configQueryChainService);
            PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
            mapping.setPromptKey("p1");
            mapping.setLabels(new HashMap<>());
            mapping.setVersions(new ArrayList<>());
            when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
                    .thenReturn(foundResponse(mapping, "m1"))
                    .thenReturn(foundResponse(mapping, "m2"));
            
            expiredService.getPromptMeta("public", "p1");
            expiredService.getPromptMeta("public", "p1");
            
            verify(configQueryChainService, times(2)).handle(any(ConfigQueryChainRequest.class));
        } finally {
            if (oldExpire == null) {
                System.clearProperty("nacos.prompt.meta.cache.expireSeconds");
            } else {
                System.setProperty("nacos.prompt.meta.cache.expireSeconds", oldExpire);
            }
        }
    }
    
    @Test
    void invalidateMetaCacheShouldNoopWhenPromptKeyBlank() throws Exception {
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setPromptKey("p1");
        mapping.setLabels(new HashMap<>());
        mapping.setVersions(new ArrayList<>());
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
                .thenReturn(foundResponse(mapping, "m1"));
        service.getPromptMeta("public", "p1");
        assertEquals(1, getMetaCache().size());
        
        service.invalidateMetaCache("public", "");
        
        assertEquals(1, getMetaCache().size());
    }
    
    @Test
    void getPromptMetaShouldEvictWhenCacheReachMaxSize() throws Exception {
        String oldMax = System.getProperty("nacos.prompt.meta.cache.maxSize");
        System.setProperty("nacos.prompt.meta.cache.maxSize", "1");
        try {
            PromptClientOperationServiceImpl maxSizeService = new PromptClientOperationServiceImpl(configQueryChainService);
            when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenAnswer(invocation -> {
                ConfigQueryChainRequest req = invocation.getArgument(0);
                PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
                mapping.setPromptKey(req.getDataId().startsWith("p1") ? "p1" : "p2");
                mapping.setLabels(new HashMap<>());
                mapping.setVersions(new ArrayList<>());
                return foundResponse(mapping, "m");
            });
            
            maxSizeService.getPromptMeta("public", "p1");
            maxSizeService.getPromptMeta("public", "p2");
            
            Field field = PromptClientOperationServiceImpl.class.getDeclaredField("metaCache");
            field.setAccessible(true);
            Map<?, ?> cache = (Map<?, ?>) field.get(maxSizeService);
            assertEquals(1, cache.size());
        } finally {
            if (oldMax == null) {
                System.clearProperty("nacos.prompt.meta.cache.maxSize");
            } else {
                System.setProperty("nacos.prompt.meta.cache.maxSize", oldMax);
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMetaCache() throws Exception {
        Field field = PromptClientOperationServiceImpl.class.getDeclaredField("metaCache");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(service);
    }
    
    private ConfigQueryChainResponse foundResponse(Object content, String md5) {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        response.setContent(com.alibaba.nacos.common.utils.JacksonUtils.toJson(content));
        response.setMd5(md5);
        return response;
    }
    
    private ConfigQueryChainResponse notFoundResponse() {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        return response;
    }
}
