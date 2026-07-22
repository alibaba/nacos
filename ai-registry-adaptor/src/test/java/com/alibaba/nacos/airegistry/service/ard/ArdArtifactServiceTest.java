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

package com.alibaba.nacos.airegistry.service.ard;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.resource.AiResourceFileReader;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.airegistry.constant.ArdProtocolConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ArdArtifactService}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ArdArtifactServiceTest {
    
    @Mock
    private AiResourceManager resourceManager;
    
    @Mock
    private McpServerOperationService mcpServerOperationService;
    
    @Mock
    private AiResourceFileReader fileReader;
    
    @Test
    void getShouldReadSkillArtifactThroughResourceReader() throws Exception {
        mockOnlineResource(AiResourceConstants.RESOURCE_TYPE_SKILL, "demo", "1.0.0");
        when(fileReader.read(any(AiResourceVersion.class), eq("public"),
            eq(AiResourceConstants.RESOURCE_TYPE_SKILL), eq("demo"), eq("1.0.0"), eq("SKILL.md")))
            .thenReturn("# Demo".getBytes(StandardCharsets.UTF_8));
        ArdArtifactService service = service();
        
        ArdArtifact artifact = service.get("public", AiResourceConstants.RESOURCE_TYPE_SKILL,
            "demo", "1.0.0", null);
        
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_SKILL, artifact.getMediaType());
        assertEquals("# Demo", artifact.getBody());
    }
    
    @Test
    void getShouldReturnNotFoundWhenDeclaredArtifactContentIsMissing() throws Exception {
        mockOnlineResource(AiResourceConstants.RESOURCE_TYPE_SKILL, "demo", "1.0.0");
        when(fileReader.read(any(AiResourceVersion.class), eq("public"),
            eq(AiResourceConstants.RESOURCE_TYPE_SKILL), eq("demo"), eq("1.0.0"), eq("SKILL.md")))
            .thenReturn(null);
        ArdArtifactService service = service();
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.get("public", AiResourceConstants.RESOURCE_TYPE_SKILL, "demo", "1.0.0",
                null));
        
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
    }
    
    private ArdArtifactService service() {
        return new ArdArtifactService(resourceManager, mcpServerOperationService, fileReader);
    }
    
    private void mockOnlineResource(String resourceType, String resourceName,
        String resourceVersion) throws NacosException {
        AiResource meta = new AiResource();
        meta.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        when(resourceManager.findMeta("public", resourceName, resourceType)).thenReturn(meta);
        when(resourceManager.findVersion("public", resourceName, resourceType, resourceVersion))
            .thenReturn(version());
    }
    
    private AiResourceVersion version() {
        AiResourceVersion version = new AiResourceVersion();
        version.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        return version;
    }
}
