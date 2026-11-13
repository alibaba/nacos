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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.HubNamespaceUtils;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.springframework.stereotype.Service;

/**
 * Resolves source Skill identity and qualifies an explicitly selected source version for Hub sharing.
 *
 * <p>This class does not authorize the caller and must not be exposed as an unauthenticated
 * read API. S1 callers should load the source, check source WRITE, qualify the version,
 * re-check source WRITE on the returned fresh metadata, then verify both again before
 * committing a Hub mutation.</p>
 */
@Service
public class HubSkillEligibilityValidator {
    
    private final AiResourceManager resourceManager;
    
    public HubSkillEligibilityValidator(AiResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }
    
    /**
     * Resolve source metadata for a share request before the caller checks source WRITE.
     * This does not disclose any source details to the HTTP caller by itself.
     *
     * @param request a validated S1 share request
     * @return source metadata; not yet qualified for sharing
     * @throws NacosException if request, source, or expected resource identity is invalid
     */
    public AiResource requireSourceForShare(HubShareSkillRequest request) throws NacosException {
        HubSkillRequestValidator.validate(request);
        return loadSource(request.getSourceNamespaceId(), request.getSourceName(),
            request.getExpectedSourceResourceId());
    }
    
    /**
     * Reload and validate the source identity recorded in a Hub reference.
     *
     * <p>Deliberately does not require PUBLIC, enable, or online. This is also used when
     * authorizing an unshare after a source was disabled or its version went offline.</p>
     *
     * @param reference stored Hub reference
     * @return matching source metadata
     * @throws NacosException if the reference is invalid or source identity changed
     */
    public AiResource requireReferencedSource(HubResourceReference reference)
        throws NacosException {
        HubResourceReferenceJsonUtil.validate(reference);
        return loadSource(reference.getSourceNamespaceId(), reference.getSourceName(),
            reference.getSourceResourceId());
    }
    
    /**
     * Reload the same source identity and verify PUBLIC, enable, and exact online version.
     *
     * <p>No label resolution is performed. Returned IDs are taken from actual database rows,
     * not from client values or parsed version strings.</p>
     *
     * @param previouslyLoadedSource source metadata retrieved before the permission check
     * @param exactVersion explicit version string (not "latest")
     * @return source metadata, version row and validated fixed Hub reference
     * @throws NacosException if source changed identity or is not shareable
     */
    public HubEligibleSkillVersion requireShareableVersion(AiResource previouslyLoadedSource,
        String exactVersion) throws NacosException {
        if (previouslyLoadedSource == null || previouslyLoadedSource.getId() == null
            || previouslyLoadedSource.getId() <= 0) {
            throw invalid("Source Skill metadata or resource ID is invalid");
        }
        requiredExactVersion(exactVersion);
        AiResource current = loadSource(previouslyLoadedSource.getNamespaceId(),
            previouslyLoadedSource.getName(), previouslyLoadedSource.getId());
        if (!VisibilityConstants.SCOPE_PUBLIC.equals(current.getScope())) {
            throw conflict("Source Skill must be PUBLIC to share");
        }
        if (!AiResourceConstants.META_STATUS_ENABLE.equals(current.getStatus())) {
            throw conflict("Source Skill must be enabled to share");
        }
        AiResourceVersion version = resourceManager.findVersion(current.getNamespaceId(),
            current.getName(), AiResourceConstants.RESOURCE_TYPE_SKILL, exactVersion);
        if (version == null) {
            throw notFound("Source Skill version not found: " + exactVersion);
        }
        if (version.getId() == null || version.getId() <= 0
            || !AiResourceConstants.RESOURCE_TYPE_SKILL.equals(version.getType())
            || !current.getName().equals(version.getName())
            || !current.getNamespaceId().equals(version.getNamespaceId())
            || !exactVersion.equals(version.getVersion())) {
            throw conflict("Source Skill version identity is inconsistent");
        }
        if (!AiResourceConstants.VERSION_STATUS_ONLINE.equals(version.getStatus())) {
            throw conflict("Source Skill version must be online to share");
        }
        HubResourceReference reference = HubResourceReferenceJsonUtil.build(
            current.getNamespaceId(), current.getId(), current.getName(),
            version.getId(), version.getVersion());
        return new HubEligibleSkillVersion(current, version, reference);
    }
    
    private AiResource loadSource(String namespaceId, String name, Long expectedId)
        throws NacosException {
        if (StringUtils.isBlank(namespaceId) || StringUtils.isBlank(name)) {
            throw invalid("Source namespace and name are required");
        }
        if (HubNamespaceUtils.isHubNamespace(namespaceId)) {
            throw invalid("Hub namespace cannot be a source namespace");
        }
        if (expectedId != null && expectedId <= 0) {
            throw invalid("Expected source resource ID must be positive");
        }
        AiResource meta = resourceManager.findMeta(namespaceId, name,
            AiResourceConstants.RESOURCE_TYPE_SKILL);
        if (meta == null) {
            throw notFound("Source Skill not found: " + name);
        }
        if (meta.getId() == null || meta.getId() <= 0
            || !AiResourceConstants.RESOURCE_TYPE_SKILL.equals(meta.getType())
            || !name.equals(meta.getName()) || !namespaceId.equals(meta.getNamespaceId())) {
            throw conflict("Source Skill metadata identity is inconsistent");
        }
        if (expectedId != null && !expectedId.equals(meta.getId())) {
            throw conflict("Source Skill identity changed; refuse name-only rebind");
        }
        return meta;
    }
    
    private void requiredExactVersion(String version) throws NacosException {
        if (StringUtils.isBlank(version) || !version.equals(version.trim())
            || AiResourceConstants.LABEL_LATEST.equalsIgnoreCase(version)) {
            throw invalid("A concrete source version is required; labels are not accepted");
        }
    }
    
    private NacosApiException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
    
    private NacosApiException notFound(String message) {
        return new NacosApiException(NacosException.NOT_FOUND,
            ErrorCode.RESOURCE_NOT_FOUND, message);
    }
    
    private NacosApiException conflict(String message) {
        return new NacosApiException(NacosException.CONFLICT,
            ErrorCode.RESOURCE_CONFLICT, message);
    }
}
