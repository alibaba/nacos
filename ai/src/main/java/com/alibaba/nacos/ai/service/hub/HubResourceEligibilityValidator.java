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
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.HubNamespaceUtils;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Common identity and publication checks for typed Hub adapters. This class does not authorize
 * callers, read content, resolve labels, or establish MCP management migration readiness.
 */
@Service
public class HubResourceEligibilityValidator {
    
    private final AiResourceManager resourceManager;
    
    public HubResourceEligibilityValidator(AiResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }
    
    /** Load a real source row, optionally requiring its immutable ID, before authorization. */
    public AiResource requireSource(HubResourceType type, String namespaceId, String name,
        Long expectedId) throws NacosException {
        Objects.requireNonNull(type, "type");
        if (StringUtils.isBlank(namespaceId) || StringUtils.isBlank(name)
            || HubNamespaceUtils.isHubNamespace(namespaceId)
            || (expectedId != null && expectedId <= 0)) {
            throw invalid("A real source namespace, resource name and valid identity are required");
        }
        AiResource source = resourceManager.findMeta(namespaceId, name, type.resourceType());
        if (source == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Source resource not found");
        }
        if (source.getId() == null || source.getId() <= 0
            || !namespaceId.equals(source.getNamespaceId()) || !name.equals(source.getName())
            || !type.resourceType().equals(source.getType())
            || (expectedId != null && !expectedId.equals(source.getId()))) {
            throw conflict("Source resource identity changed");
        }
        return source;
    }
    
    /**
     * Reload the source and pin one exact online version. Callers must check permissions before
     * using the result and revalidate the same reference around publication or content access.
     */
    public HubResourceReference requireShareableVersion(HubResourceType type, AiResource previous,
        String version) throws NacosException {
        if (previous == null || previous.getId() == null || previous.getId() <= 0
            || !type.resourceType().equals(previous.getType())) {
            throw invalid("Source metadata must belong to the requested resource type");
        }
        if (StringUtils.isBlank(version) || !version.equals(version.trim())
            || AiResourceConstants.LABEL_LATEST.equalsIgnoreCase(version)) {
            throw invalid("A concrete source version is required; labels are not accepted");
        }
        AiResource current = requireSource(type, previous.getNamespaceId(), previous.getName(),
            previous.getId());
        if (!AiResourceConstants.META_STATUS_ENABLE.equals(current.getStatus())) {
            throw conflict("Source resource must be enabled to share");
        }
        // Prompt has no public/private management operation. Explicit, authorized sharing
        // must not require changing its generic metadata scope or invent a new scope API.
        if (type != HubResourceType.PROMPT
            && !VisibilityConstants.SCOPE_PUBLIC.equals(current.getScope())) {
            throw conflict("Source resource must be PUBLIC to share");
        }
        AiResourceVersion row = resourceManager.findVersion(current.getNamespaceId(),
            current.getName(), type.resourceType(), version);
        if (row == null || row.getId() == null || row.getId() <= 0
            || !current.getNamespaceId().equals(row.getNamespaceId())
            || !current.getName().equals(row.getName())
            || !type.resourceType().equals(row.getType()) || !version.equals(row.getVersion())
            || !AiResourceConstants.VERSION_STATUS_ONLINE.equals(row.getStatus())) {
            throw conflict("An exact online source version is required");
        }
        return HubResourceReferenceJsonUtil.build(current.getNamespaceId(), current.getId(),
            current.getName(), row.getId(), row.getVersion());
    }
    
    /** Reject same-name recreation or version-row replacement when resolving a pinned share. */
    public AiResource requirePinnedSource(HubResourceType type, HubResourceReference expected)
        throws NacosException {
        HubResourceReferenceJsonUtil.validate(expected);
        try {
            AiResource source = requireSource(type, expected.getSourceNamespaceId(),
                expected.getSourceName(), expected.getSourceResourceId());
            HubResourceReference current = requireShareableVersion(type, source,
                expected.getSourceVersion());
            if (!Objects.equals(expected.getSourceVersionId(), current.getSourceVersionId())) {
                throw conflict("Pinned source version identity changed");
            }
            return source;
        } catch (NacosException | RuntimeException sourceFailure) {
            if (sourceFailure instanceof RuntimeException
                || ((NacosException) sourceFailure).getErrCode() >= NacosException.SERVER_ERROR) {
                NacosApiException failure = new NacosApiException(NacosException.SERVER_ERROR,
                    ErrorCode.SERVER_ERROR, "Hub source validation unavailable");
                failure.initCause(sourceFailure);
                throw failure;
            }
            throw sourceFailure;
        }
    }
    
    private NacosApiException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
    
    private NacosApiException conflict(String message) {
        return new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT, message);
    }
}
