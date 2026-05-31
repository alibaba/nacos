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

package com.alibaba.nacos.ai.importer.operator;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.service.skills.SkillOperationService;
import com.alibaba.nacos.ai.service.skills.SkillUploadRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultStatus;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationStatus;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.importer.AiResourceImportConstants;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportPayloadKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SkillResourceOperator}.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class SkillResourceOperatorTest {
    
    @Mock
    private SkillOperationService skillOperationService;
    
    @Mock
    private AiResourceManager resourceManager;
    
    private SkillResourceOperator operator;
    
    @BeforeEach
    void setUp() {
        operator = new SkillResourceOperator(skillOperationService, resourceManager);
    }
    
    @Test
    void testValidateNewSkillIsValid() throws Exception {
        when(resourceManager.findMeta("public", "demo-skill", "skill")).thenReturn(null);
        
        AiResourceImportValidationItem result =
            operator.validate("public", artifact(), false);
        
        assertEquals(AiResourceImportValidationStatus.VALID, result.getStatus());
        assertEquals("demo-skill", result.getName());
        assertEquals("1.2.3", result.getVersion());
    }
    
    @Test
    void testValidateWorkingVersionWithoutOverwriteIsConflict() throws Exception {
        when(resourceManager.findMeta("public", "demo-skill", "skill"))
            .thenReturn(metaWithEditingVersion());
        
        AiResourceImportValidationItem result =
            operator.validate("public", artifact(), false);
        
        assertEquals(AiResourceImportValidationStatus.CONFLICT, result.getStatus());
        assertEquals("working_version", result.getConflictType());
    }
    
    @Test
    void testValidateExistingWithOverwriteIsWarning() throws Exception {
        when(resourceManager.findMeta("public", "demo-skill", "skill"))
            .thenReturn(metaWithEditingVersion());
        
        AiResourceImportValidationItem result = operator.validate("public", artifact(), true);
        
        assertEquals(AiResourceImportValidationStatus.WARNING, result.getStatus());
        assertEquals("existing", result.getConflictType());
        assertEquals(1, result.getWarnings().size());
    }
    
    @Test
    void testImportUsesSkillUploadService() throws Exception {
        when(skillOperationService.uploadSkillFromZip(any(SkillUploadRequest.class)))
            .thenReturn("demo-skill");
        
        AiResourceImportResultItem result = operator.importResource("public", artifact(), true);
        
        assertEquals(AiResourceImportResultStatus.SUCCESS, result.getStatus());
        assertEquals("demo-skill", result.getResourceName());
        verify(skillOperationService).uploadSkillFromZip(
            argThat(request -> "public".equals(request.getNamespaceId()) && request.isOverwrite()
                && "1.2.3".equals(request.getTargetVersion())
                && request.getCommitMsg() == null && request.getZipBytes() != null));
    }
    
    @Test
    void testImportSkipsWorkingVersionWithoutOverwrite() throws Exception {
        when(resourceManager.findMeta("public", "demo-skill", "skill"))
            .thenReturn(metaWithEditingVersion());
        
        AiResourceImportResultItem result = operator.importResource("public", artifact(), false);
        
        assertEquals(AiResourceImportResultStatus.SKIPPED, result.getStatus());
        assertEquals("demo-skill", result.getResourceName());
        assertEquals(1, result.getWarnings().size());
        verify(skillOperationService, never()).uploadSkillFromZip(any(SkillUploadRequest.class));
    }
    
    @Test
    void testImportSyncsSourceMetadata() throws Exception {
        AiResource meta = metaWithEditingVersion();
        when(skillOperationService.uploadSkillFromZip(any(SkillUploadRequest.class)))
            .thenReturn("demo-skill");
        when(resourceManager.findMeta("public", "demo-skill", "skill")).thenReturn(meta);
        AiResourceImportArtifact artifact = artifact();
        Map<String, String> metadata = new HashMap<>(2);
        metadata.put("source", "https://developers.cloudflare.com/.well-known/agent-skills");
        metadata.put("artifactUrl",
            "https://developers.cloudflare.com/.well-known/agent-skills/cloudflare.tar.gz");
        artifact.setSourceMetadata(metadata);
        
        operator.importResource("public", artifact, true);
        
        verify(resourceManager).syncImportedSource("public", meta,
            "https://developers.cloudflare.com/.well-known/agent-skills/cloudflare.tar.gz");
    }
    
    @Test
    void testValidateRejectsUnsupportedPayloadKind() {
        AiResourceImportArtifact artifact = artifact();
        artifact.setPayloadKind(AiResourceImportPayloadKind.MCP_DETAIL);
        
        assertThrows(Exception.class, () -> operator.validate("public", artifact, false));
    }
    
    @Test
    void testResourceType() {
        assertEquals(AiResourceImportConstants.RESOURCE_TYPE_SKILL, operator.resourceType());
    }
    
    private AiResource metaWithEditingVersion() {
        AiResource meta = new AiResource();
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setEditingVersion("1.0.0");
        meta.setVersionInfo(JacksonUtils.toJson(info));
        return meta;
    }
    
    private AiResourceImportArtifact artifact() {
        AiResourceImportArtifact artifact = new AiResourceImportArtifact();
        artifact.setResourceType(AiResourceImportConstants.RESOURCE_TYPE_SKILL);
        artifact.setExternalId("demo-skill");
        artifact.setName("demo-skill");
        artifact.setVersion("1.2.3");
        artifact.setPayloadKind(AiResourceImportPayloadKind.SKILL_ZIP);
        artifact.setPayload(skillZip());
        return artifact;
    }
    
    private byte[] skillZip() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("demo-skill/SKILL.md"));
            zip.write(("---\nname: demo-skill\ndescription: Demo skill\nversion: 1.2.3\n---\n"
                + "\nUse this skill.").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return output.toByteArray();
    }
}
