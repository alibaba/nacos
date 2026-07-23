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
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.resource.AiResourceFileReader;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.skills.SkillClientOperationService;
import com.alibaba.nacos.ai.service.skills.SkillQueryResult;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.airegistry.constant.ArdProtocolConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    
    @Mock
    private SkillClientOperationService skillClientOperationService;
    
    @Test
    void getShouldReturnCompleteSkillZip() throws Exception {
        Skill skill = skill();
        when(skillClientOperationService.querySkill("public", "demo", "1.0.0", null, null))
            .thenReturn(new SkillQueryResult(skill, "md5", "1.0.0"));
        ArdArtifactService service = service();
        
        ArdArtifact artifact = service.get("public", AiResourceConstants.RESOURCE_TYPE_SKILL,
            "demo", "1.0.0", null);
        
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE, artifact.getMediaType());
        assertEquals(Set.of("demo/SKILL.md", "demo/references/guide.md"),
            zipEntries((byte[]) artifact.getBody()));
    }
    
    @Test
    void getShouldPropagateSkillNotFound() throws Exception {
        when(skillClientOperationService.querySkill("public", "demo", "1.0.0", null, null))
            .thenThrow(new NacosApiException(NacosException.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND, "skill not found"));
        ArdArtifactService service = service();
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.get("public", AiResourceConstants.RESOURCE_TYPE_SKILL, "demo", "1.0.0",
                null));
        
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
    }
    
    private ArdArtifactService service() {
        return new ArdArtifactService(resourceManager, mcpServerOperationService, fileReader,
            skillClientOperationService);
    }
    
    private Skill skill() {
        Skill skill = new Skill();
        skill.setName("demo");
        skill.setSkillMd("# Demo");
        SkillResource resource = new SkillResource();
        resource.setName("guide.md");
        resource.setType("references");
        resource.setContent("Guide");
        skill.setResource(Map.of(resource.getResourceIdentifier(), resource));
        return skill;
    }
    
    private Set<String> zipEntries(byte[] bytes) throws Exception {
        Set<String> result = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                result.add(entry.getName());
            }
        }
        return result;
    }
}
