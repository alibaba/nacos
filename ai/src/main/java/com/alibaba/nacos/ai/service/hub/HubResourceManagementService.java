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

import com.alibaba.nacos.ai.config.HubConfig;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceShareState;
import com.alibaba.nacos.api.ai.model.hub.HubShareResourceRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Typed management facade; public directory reads never expose this mutation state. */
@Service
public class HubResourceManagementService {
    
    private final HubResourcePublicationService publications;
    private final HubResourceEligibilityValidator eligibility;
    private final HubResourcePermissionGuard permissions;
    private final HubEntryStorage storage;
    
    public HubResourceManagementService(HubResourcePublicationService publications,
        HubResourceEligibilityValidator eligibility, HubResourcePermissionGuard permissions,
        HubEntryStorage storage) {
        this.publications = publications;
        this.eligibility = eligibility;
        this.permissions = permissions;
        this.storage = storage;
    }
    
    /** Share one concrete source version using source READ and WRITE. */
    public HubShareResult share(String resourceType, HubShareResourceRequest request)
        throws NacosException {
        return publications.share(type(resourceType), request.getSourceNamespaceId(),
            request.getSourceName(),
            request.getSourceVersion(), request.getExpectedSourceResourceId());
    }
    
    /** Adjust the sole shared version through the publication CAS. */
    public HubShareResult adjust(String resourceType, HubAdjustShareRequest request)
        throws NacosException {
        return publications.adjust(type(resourceType), request.getHubEntryId(),
            request.getExpectedMetaVersion(),
            request.getTargetVersion());
    }
    
    /** Revoke a share using source WRITE, including after source private/offline transitions. */
    public HubShareResult unshare(String resourceType, HubUnshareRequest request)
        throws NacosException {
        return publications.unshare(type(resourceType), request.getHubEntryId(),
            request.getExpectedMetaVersion());
    }
    
    /** Revoke a share using independent administrator authority. */
    public HubShareResult forceUnshare(String resourceType, HubForceUnshareRequest request)
        throws NacosException {
        return publications.forceUnshare(type(resourceType), request.getHubEntryId(),
            request.getExpectedMetaVersion(), request.getReason());
    }
    
    /** Return source-authorized state; never require the source still be shareable for cleanup. */
    public HubResourceShareState sourceState(String resourceType, String namespaceId, String name)
        throws NacosException {
        HubConfig.requireEnabled();
        HubResourceType type = type(resourceType);
        AiResource source = eligibility.requireSource(type, namespaceId, name, null);
        permissions.requireSourceWrite(type, source);
        AiResource entry = storage.findByInternalName(type,
            HubResourceNameUtils.buildInternalName(name, source.getId()));
        HubResourceShareState result = null;
        if (entry != null) {
            HubResourceReference reference = HubResourceReferenceJsonUtil.parse(entry.getExt());
            if (!Objects.equals(reference.getSourceResourceId(), source.getId())
                || !Objects.equals(reference.getSourceNamespaceId(), namespaceId)
                || !Objects.equals(reference.getSourceName(), name)) {
                throw unavailable();
            }
            result = view(type, entry, reference);
        }
        AiResource current = eligibility.requireSource(type, namespaceId, name, source.getId());
        permissions.requireSourceWrite(type, current);
        return result;
    }
    
    /** Return administrator-only state even when a removed source no longer exists. */
    public HubResourceShareState governanceState(String resourceType, Long entryId)
        throws NacosException {
        HubConfig.requireEnabled();
        HubResourceType type = type(resourceType);
        permissions.requireAdministrator();
        if (entryId == null || entryId <= 0) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Invalid Hub entry ID");
        }
        AiResource entry = storage.findByEntryId(type, entryId);
        if (entry == null || !entryId.equals(entry.getId())) {
            throw unavailable();
        }
        HubResourceShareState result =
            view(type, entry, HubResourceReferenceJsonUtil.parse(entry.getExt()));
        permissions.requireAdministrator();
        return result;
    }
    
    private HubResourceShareState view(HubResourceType type, AiResource entry,
        HubResourceReference reference)
        throws NacosException {
        if (!AiConstants.Hub.NAMESPACE_ID.equals(entry.getNamespaceId())
            || !type.resourceType().equals(entry.getType()) || !"hub".equals(entry.getFrom())
            || !"nacos-ai-hub".equals(entry.getOwner()) || !"PUBLIC".equals(entry.getScope())
            || entry.getId() == null || entry.getId() <= 0 || entry.getMetaVersion() == null
            || entry.getMetaVersion() <= 0
            || !("enable".equals(entry.getStatus()) || "disable".equals(entry.getStatus()))
            || !Objects.equals(entry.getName(), HubResourceNameUtils.buildInternalName(
                reference.getSourceName(), reference.getSourceResourceId()))) {
            throw unavailable();
        }
        HubResourceShareState result = new HubResourceShareState();
        result.setHubEntryId(entry.getId());
        result.setMetaVersion(entry.getMetaVersion());
        result.setSharedVersion(reference.getSourceVersion());
        result.setActive("enable".equals(entry.getStatus()));
        return result;
    }
    
    private HubResourceType type(String value) throws NacosApiException {
        for (HubResourceType type : HubResourceType.values()) {
            if (type != HubResourceType.SKILL && type.resourceType().equals(value)) {
                return type;
            }
        }
        throw new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, "Unsupported Hub resource type");
    }
    
    private NacosApiException unavailable() {
        return new NacosApiException(NacosException.NOT_FOUND,
            ErrorCode.RESOURCE_NOT_FOUND, "Hub resource state is unavailable");
    }
}
