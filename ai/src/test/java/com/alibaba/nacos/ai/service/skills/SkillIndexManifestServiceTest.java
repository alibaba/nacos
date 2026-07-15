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

package com.alibaba.nacos.ai.service.skills;

import com.alibaba.nacos.ai.model.skills.SkillIndexManifest;
import com.alibaba.nacos.ai.service.SyncEffectService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link SkillIndexManifestService}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class SkillIndexManifestServiceTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String SKILL_NAME = "test-skill";
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private SyncEffectService syncEffectService;
    
    private SkillIndexManifestService manifestService;
    
    @BeforeEach
    void setUp() {
        manifestService =
            new SkillIndexManifestService(configQueryChainService, configOperationService,
                syncEffectService);
    }
    
    @Test
    void testQueryReturnsManifest() throws NacosException {
        SkillIndexManifest manifest = new SkillIndexManifest();
        Map<String, String> labels = new HashMap<>();
        labels.put("latest", "v2");
        manifest.setLabels(labels);
        Map<String, List<String>> versions = new HashMap<>();
        versions.put("v2", List.of("SKILL.md"));
        manifest.setVersions(versions);
        
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        response.setContent(JacksonUtils.toJson(manifest));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response);
        
        SkillIndexManifest result = manifestService.query(NAMESPACE_ID, SKILL_NAME);
        
        assertNotNull(result);
        assertEquals("v2", result.getLabels().get("latest"));
        assertEquals(1, result.getVersions().size());
    }
    
    @Test
    void testQueryReturnsNullWhenNotFound() throws NacosException {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response);
        
        SkillIndexManifest result = manifestService.query(NAMESPACE_ID, SKILL_NAME);
        
        assertNull(result);
    }
    
    @Test
    void testQueryReturnsNullOnException() throws NacosException {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenThrow(new RuntimeException("test error"));
        
        SkillIndexManifest result = manifestService.query(NAMESPACE_ID, SKILL_NAME);
        
        assertNull(result);
    }
    
    @Test
    void testLoadForUpdateReturnsNewManifestWhenNull() throws NacosException {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response);
        
        SkillIndexManifest result = manifestService.loadForUpdate(NAMESPACE_ID, SKILL_NAME);
        
        assertNotNull(result);
        assertNotNull(result.getLabels());
        assertNotNull(result.getVersions());
    }
    
    @Test
    void testLoadForUpdateInitializesNullMaps() throws NacosException {
        SkillIndexManifest manifest = new SkillIndexManifest();
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        response.setContent(JacksonUtils.toJson(manifest));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response);
        
        SkillIndexManifest result = manifestService.loadForUpdate(NAMESPACE_ID, SKILL_NAME);
        
        assertNotNull(result.getLabels());
        assertNotNull(result.getVersions());
    }
    
    @Test
    void testWrite() throws NacosException {
        SkillIndexManifest manifest = new SkillIndexManifest();
        manifest.setLabels(new HashMap<>());
        manifest.setVersions(new HashMap<>());
        
        manifestService.write(NAMESPACE_ID, SKILL_NAME, manifest);
        
        verify(configOperationService).publishConfig(any(), any(), any());
    }
    
    @Test
    void testDelete() throws NacosException {
        manifestService.delete(NAMESPACE_ID, SKILL_NAME);
        
        verify(configOperationService).deleteConfig(any(), any(), any(), any(), any(), any(),
            any());
    }
    
    // ========== resolveVersion static method tests ==========
    
    @Test
    void testResolveVersionWithNullManifest() {
        assertNull(SkillIndexManifestService.resolveVersion(null, null, null));
    }
    
    @Test
    void testResolveVersionWithEmptyVersions() {
        SkillIndexManifest manifest = new SkillIndexManifest();
        manifest.setVersions(new HashMap<>());
        assertNull(SkillIndexManifestService.resolveVersion(manifest, null, null));
    }
    
    @Test
    void testResolveVersionWithExplicitVersion() {
        SkillIndexManifest manifest = new SkillIndexManifest();
        Map<String, List<String>> versions = new HashMap<>();
        versions.put("v1", List.of("SKILL.md"));
        manifest.setVersions(versions);
        
        String result = SkillIndexManifestService.resolveVersion(manifest, "v1", null);
        assertEquals("v1", result);
    }
    
    @Test
    void testResolveVersionWithExplicitVersionNotFound() {
        SkillIndexManifest manifest = new SkillIndexManifest();
        Map<String, List<String>> versions = new HashMap<>();
        versions.put("v1", List.of("SKILL.md"));
        manifest.setVersions(versions);
        
        assertNull(SkillIndexManifestService.resolveVersion(manifest, "v99", null));
    }
    
    @Test
    void testResolveVersionByLabel() {
        SkillIndexManifest manifest = new SkillIndexManifest();
        Map<String, String> labels = new HashMap<>();
        labels.put("stable", "v2");
        manifest.setLabels(labels);
        Map<String, List<String>> versions = new HashMap<>();
        versions.put("v2", List.of("SKILL.md"));
        manifest.setVersions(versions);
        
        String result = SkillIndexManifestService.resolveVersion(manifest, null, "stable");
        assertEquals("v2", result);
    }
    
    @Test
    void testResolveVersionByLatestLabelDefault() {
        SkillIndexManifest manifest = new SkillIndexManifest();
        Map<String, String> labels = new HashMap<>();
        labels.put("latest", "v3");
        manifest.setLabels(labels);
        Map<String, List<String>> versions = new HashMap<>();
        versions.put("v3", List.of("SKILL.md"));
        manifest.setVersions(versions);
        
        String result = SkillIndexManifestService.resolveVersion(manifest, null, null);
        assertEquals("v3", result);
    }
    
    @Test
    void testResolveVersionByLabelNotFound() {
        SkillIndexManifest manifest = new SkillIndexManifest();
        Map<String, String> labels = new HashMap<>();
        labels.put("latest", "v99");
        manifest.setLabels(labels);
        Map<String, List<String>> versions = new HashMap<>();
        versions.put("v1", List.of("SKILL.md"));
        manifest.setVersions(versions);
        
        assertNull(SkillIndexManifestService.resolveVersion(manifest, null, null));
    }
}
