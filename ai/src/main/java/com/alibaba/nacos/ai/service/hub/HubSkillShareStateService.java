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
import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.HubNamespaceUtils;
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubSkillShareState;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Returns exact optimistic-lock state only after verifying the caller's
 * SOURCE WRITE or Hub governance authorization. Never guess by display name.
 */
@Service
public class HubSkillShareStateService {
    
    private final AiResourceManager sourceManager;
    
    private final HubEntryStorage hubEntryStorage;
    
    private final HubSkillPermissionGuard permissions;
    
    public HubSkillShareStateService(AiResourceManager sourceManager,
        HubEntryStorage hubEntryStorage, HubSkillPermissionGuard permissions) {
        this.sourceManager = sourceManager;
        this.hubEntryStorage = hubEntryStorage;
        this.permissions = permissions;
    }
    
    /**
     * Inspect an existing share after verifying WRITE on the current source resource.
     *
     * @return share state, or null if the source has never been shared
     */
    public HubSkillShareState bySource(String namespaceId, String sourceName)
        throws NacosException {
        HubConfig.requireEnabled();
        if (StringUtils.isBlank(namespaceId) || StringUtils.isBlank(sourceName)) {
            throw invalid();
        }
        HubNamespaceUtils.rejectIfHubNamespace(namespaceId);
        try {
            AiResource source = requireSource(namespaceId, sourceName);
            permissions.requireSourceWrite(source);
            Long sourceId = source.getId();
            String internalName = HubResourceNameUtils.buildInternalName(sourceName, sourceId);
            AiResource entry = hubEntryStorage.findByInternalName(internalName);
            HubSkillShareState result = null;
            if (entry != null) {
                HubResourceReference reference = parseReference(entry);
                if (!Objects.equals(reference.getSourceNamespaceId(), namespaceId)
                    || !Objects.equals(reference.getSourceName(), sourceName)
                    || !Objects.equals(reference.getSourceResourceId(), sourceId)) {
                    throw notFound();
                }
                result = view(entry, reference);
            }
            // Re-read after storage I/O: an old source instance cannot authorize a replacement or a new owner.
            AiResource current = requireSource(namespaceId, sourceName);
            permissions.requireSourceWrite(current);
            if (!Objects.equals(sourceId, current.getId())) {
                throw notFound();
            }
            return result;
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }
    
    /** Inspect share state with existing Console administrator authority. */
    public HubSkillShareState byEntryForAdmin(Long hubEntryId) throws NacosException {
        HubConfig.requireEnabled();
        permissions.requireAdministrator();
        if (hubEntryId == null || hubEntryId <= 0) {
            throw invalid();
        }
        try {
            AiResource entry = hubEntryStorage.findByEntryId(hubEntryId);
            if (entry == null || !Objects.equals(entry.getId(), hubEntryId)) {
                throw notFound();
            }
            HubSkillShareState result = view(entry, parseReference(entry));
            permissions.requireAdministrator();
            return result;
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }
    
    private AiResource requireSource(String namespaceId, String sourceName) throws NacosException {
        AiResource source = sourceManager.findMeta(namespaceId, sourceName,
            AiResourceConstants.RESOURCE_TYPE_SKILL);
        if (source == null || source.getId() == null || source.getId() <= 0
            || !Objects.equals(namespaceId, source.getNamespaceId())
            || !Objects.equals(sourceName, source.getName())
            || !AiResourceConstants.RESOURCE_TYPE_SKILL.equals(source.getType())) {
            throw notFound();
        }
        return source;
    }
    
    private HubResourceReference parseReference(AiResource entry) throws NacosException {
        try {
            return HubResourceReferenceJsonUtil.parse(entry.getExt());
        } catch (NacosException | RuntimeException invalidReference) {
            throw notFound();
        }
    }
    
    private HubSkillShareState view(AiResource entry, HubResourceReference reference)
        throws NacosException {
        if (!AiConstants.Hub.NAMESPACE_ID.equals(entry.getNamespaceId())
            || entry.getId() == null || entry.getId() <= 0 || entry.getMetaVersion() == null
            || entry.getMetaVersion() <= 0 || !"hub".equals(entry.getFrom())
            || !AiResourceConstants.RESOURCE_TYPE_SKILL.equals(entry.getType())
            || !Objects.equals(entry.getName(), HubResourceNameUtils.buildInternalName(
                reference.getSourceName(), reference.getSourceResourceId()))
            || !(AiResourceConstants.META_STATUS_ENABLE.equals(entry.getStatus())
                || AiResourceConstants.META_STATUS_DISABLE.equals(entry.getStatus()))) {
            throw notFound();
        }
        HubSkillShareState result = new HubSkillShareState();
        result.setHubEntryId(entry.getId());
        result.setMetaVersion(entry.getMetaVersion());
        result.setSharedVersion(reference.getSourceVersion());
        result.setActive(AiResourceConstants.META_STATUS_ENABLE.equals(entry.getStatus()));
        return result;
    }
    
    private NacosApiException unavailable() {
        return new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
            "Hub Skill state service is unavailable");
    }
    
    private NacosApiException invalid() {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, "Invalid Hub Skill state request");
    }
    
    private NacosApiException notFound() {
        return new NacosApiException(NacosException.NOT_FOUND,
            ErrorCode.RESOURCE_NOT_FOUND, "Hub Skill state is unavailable");
    }
}
