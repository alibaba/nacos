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
import com.alibaba.nacos.ai.utils.SkillZipParser;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultStatus;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationStatus;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.importer.AiResourceImportConstants;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportPayloadKind;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

/**
 * Applies imported Skill ZIP artifacts through the current Skill upload flow.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
@Service
public class SkillResourceOperator implements AiResourceOperator {
    
    private static final String CONFLICT_TYPE_WORKING_VERSION = "working_version";
    
    private static final String CONFLICT_TYPE_EXISTING = "existing";
    
    private static final String METADATA_ARTIFACT_URL = "artifactUrl";
    
    private static final String METADATA_SOURCE = "source";
    
    private static final String WORKING_VERSION_SKIP_MESSAGE =
        "Skipped because a working version (editing/reviewing) already exists.";
    
    private final SkillOperationService skillOperationService;
    
    private final AiResourceManager resourceManager;
    
    public SkillResourceOperator(SkillOperationService skillOperationService,
        AiResourceManager resourceManager) {
        this.skillOperationService = skillOperationService;
        this.resourceManager = resourceManager;
    }
    
    @Override
    public String resourceType() {
        return AiResourceImportConstants.RESOURCE_TYPE_SKILL;
    }
    
    @Override
    public AiResourceImportValidationItem validate(String namespaceId,
        AiResourceImportArtifact artifact, boolean overwriteExisting) throws NacosException {
        Skill skill = parseSkill(namespaceId, artifact);
        AiResource meta = resourceManager.findMeta(namespaceId, skill.getName(), resourceType());
        AiResourceImportValidationItem result = baseValidationItem(artifact, skill);
        result.setExists(meta != null);
        if (meta == null) {
            result.setStatus(AiResourceImportValidationStatus.VALID);
            return result;
        }
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        if (hasWorkingVersion(info) && !overwriteExisting) {
            result.setStatus(AiResourceImportValidationStatus.CONFLICT);
            result.setConflictType(CONFLICT_TYPE_WORKING_VERSION);
            result.setErrors(Collections.singletonList(
                "There is already a working version (editing/reviewing), enable overwrite to import."));
            return result;
        }
        result.setStatus(AiResourceImportValidationStatus.WARNING);
        result.setConflictType(CONFLICT_TYPE_EXISTING);
        result.setWarnings(Collections.singletonList(resolveExistingWarning(info)));
        return result;
    }
    
    @Override
    public AiResourceImportResultItem importResource(String namespaceId,
        AiResourceImportArtifact artifact, boolean overwriteExisting) throws NacosException {
        Skill skill = parseSkill(namespaceId, artifact);
        AiResource meta = resourceManager.findMeta(namespaceId, skill.getName(), resourceType());
        if (!overwriteExisting && hasWorkingVersion(AiResourceManager.requireVersionInfo(meta))) {
            return skippedWorkingVersionItem(artifact, skill);
        }
        String version = resolveVersion(artifact);
        SkillUploadRequest request = SkillUploadRequest.builder()
            .namespaceId(namespaceId)
            .zipBytes(artifact.getPayload())
            .overwrite(overwriteExisting)
            .targetVersion(version)
            .build();
        String skillName = skillOperationService.uploadSkillFromZip(request);
        syncSource(namespaceId, skillName, artifact);
        AiResourceImportResultItem result = new AiResourceImportResultItem();
        result.setExternalId(artifact.getExternalId());
        result.setResourceName(skillName);
        result.setVersion(version);
        result.setStatus(AiResourceImportResultStatus.SUCCESS);
        return result;
    }
    
    private AiResourceImportResultItem skippedWorkingVersionItem(
        AiResourceImportArtifact artifact, Skill skill) {
        AiResourceImportResultItem result = new AiResourceImportResultItem();
        result.setExternalId(artifact.getExternalId());
        result.setResourceName(skill.getName());
        result.setVersion(resolveVersion(artifact));
        result.setStatus(AiResourceImportResultStatus.SKIPPED);
        result.setWarnings(Collections.singletonList(WORKING_VERSION_SKIP_MESSAGE));
        return result;
    }
    
    private void syncSource(String namespaceId, String skillName,
        AiResourceImportArtifact artifact) {
        String source = resolveSource(artifact);
        if (StringUtils.isBlank(source)) {
            return;
        }
        AiResource meta = resourceManager.findMeta(namespaceId, skillName, resourceType());
        resourceManager.syncImportedSource(namespaceId, meta, source);
    }
    
    private String resolveSource(AiResourceImportArtifact artifact) {
        Map<String, String> metadata = artifact.getSourceMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        String artifactUrl = metadata.get(METADATA_ARTIFACT_URL);
        if (StringUtils.isNotBlank(artifactUrl)) {
            return artifactUrl;
        }
        return metadata.get(METADATA_SOURCE);
    }
    
    private boolean hasWorkingVersion(ResourceVersionInfo info) {
        return StringUtils.isNotBlank(info.getEditingVersion())
            || StringUtils.isNotBlank(info.getReviewingVersion());
    }
    
    private String resolveExistingWarning(ResourceVersionInfo info) {
        if (StringUtils.isNotBlank(info.getEditingVersion())) {
            return "Existing editing draft will be overwritten.";
        }
        if (StringUtils.isNotBlank(info.getReviewingVersion())) {
            return "A new draft will be created while another version is under review.";
        }
        return "Existing skill will receive a new draft version.";
    }
    
    private AiResourceImportValidationItem baseValidationItem(AiResourceImportArtifact artifact,
        Skill skill) {
        AiResourceImportValidationItem result = new AiResourceImportValidationItem();
        result.setExternalId(artifact.getExternalId());
        result.setName(skill.getName());
        result.setVersion(resolveVersion(artifact));
        return result;
    }
    
    private Skill parseSkill(String namespaceId, AiResourceImportArtifact artifact)
        throws NacosException {
        requireSkillZipArtifact(artifact);
        Skill skill = SkillZipParser.parseSkillFromZip(artifact.getPayload(), namespaceId);
        if (skill == null || StringUtils.isBlank(skill.getName())) {
            throw invalid("Skill import artifact must contain a valid skill name.");
        }
        return skill;
    }
    
    private void requireSkillZipArtifact(AiResourceImportArtifact artifact) throws NacosException {
        if (artifact == null) {
            throw invalid("Skill import artifact must not be null.");
        }
        if (artifact.getPayloadKind() != AiResourceImportPayloadKind.SKILL_ZIP
            && artifact.getPayloadKind() != AiResourceImportPayloadKind.BYTES) {
            throw invalid("Skill import artifact payload kind is unsupported.");
        }
        if (artifact.getPayload() == null || artifact.getPayload().length == 0) {
            throw invalid("Skill import artifact zip payload must not be empty.");
        }
    }
    
    private String resolveVersion(AiResourceImportArtifact artifact) {
        if (StringUtils.isNotBlank(artifact.getVersion())) {
            return artifact.getVersion();
        }
        return SkillZipParser.resolveVersionFromZip(artifact.getPayload());
    }
    
    private NacosException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
}
