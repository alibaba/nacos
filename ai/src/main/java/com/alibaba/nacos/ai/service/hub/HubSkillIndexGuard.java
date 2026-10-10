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
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Canonical eligibility gate for the Hub-only search index.
 *
 * <p>Never index an internal Hub row solely because its metadata is enabled.
 * The exact source row, version row and both stored descriptors must still be
 * valid. This is a source-state check, not a user authorization decision; read
 * and import endpoints must separately authorize each request.</p>
 */
@Service
public class HubSkillIndexGuard {
    
    private final AiResourceManager resourceManager;
    
    private final HubEntryStorage hubEntryStorage;
    
    public HubSkillIndexGuard(AiResourceManager resourceManager,
            HubEntryStorage hubEntryStorage) {
        this.resourceManager = resourceManager;
        this.hubEntryStorage = hubEntryStorage;
    }
    
    /**
     * Find a Hub row by its canonical name, constrained to {@code c_from=hub}.
     *
     * @param internalName internal Hub name
     * @return Hub entry or null
     */
    public AiResource findHubByInternalName(String internalName) {
        return hubEntryStorage.findByInternalName(internalName);
    }
    
    /**
     * Check the effective pinned Hub pointer and its exact live source.
     *
     * @param hubMeta canonical Hub metadata
     * @param hubVersion canonical pinned Hub version row
     * @return true only when eligible to publish into Hub search
     */
    public boolean isIndexable(AiResource hubMeta, AiResourceVersion hubVersion) {
        try {
            return checkIndexable(hubMeta, hubVersion);
        } catch (Exception ignored) {
            // A malformed or disappearing source can never become publicly indexable.
            return false;
        }
    }
    
    private boolean checkIndexable(AiResource hubMeta, AiResourceVersion hubVersion)
            throws Exception {
        if (hubMeta == null || hubVersion == null || hubMeta.getId() == null
                || !AiConstants.Hub.NAMESPACE_ID.equals(hubMeta.getNamespaceId())
                || !AiResourceConstants.RESOURCE_TYPE_SKILL.equals(hubMeta.getType())
                || !"hub".equals(hubMeta.getFrom())
                || !AiResourceConstants.META_STATUS_ENABLE.equals(hubMeta.getStatus())
                || !VisibilityConstants.SCOPE_PUBLIC.equals(hubMeta.getScope())
                || !AiConstants.Hub.NAMESPACE_ID.equals(hubVersion.getNamespaceId())
                || !AiResourceConstants.RESOURCE_TYPE_SKILL.equals(hubVersion.getType())
                || !AiResourceConstants.VERSION_STATUS_ONLINE.equals(hubVersion.getStatus())
                || !Objects.equals(hubMeta.getName(), hubVersion.getName())) {
            return false;
        }
        AiResource canonical = hubEntryStorage.findByEntryId(hubMeta.getId());
        if (canonical == null || !Objects.equals(canonical.getId(), hubMeta.getId())
                || !Objects.equals(canonical.getMetaVersion(), hubMeta.getMetaVersion())
                || !Objects.equals(canonical.getExt(), hubMeta.getExt())
                || !Objects.equals(canonical.getStatus(), hubMeta.getStatus())) {
            return false;
        }
        HubResourceReference metaReference = HubResourceReferenceJsonUtil.parse(hubMeta.getExt());
        HubResourceReference versionReference =
                HubResourceReferenceJsonUtil.parse(hubVersion.getStorage());
        if (!sameReference(metaReference, versionReference)
                || AiConstants.Hub.NAMESPACE_ID.equals(metaReference.getSourceNamespaceId())
                || !Objects.equals(hubMeta.getName(), HubResourceNameUtils.buildInternalName(
                metaReference.getSourceName(), metaReference.getSourceResourceId()))
                || !Objects.equals(hubVersion.getVersion(), metaReference.getSourceVersion())) {
            return false;
        }
        ResourceVersionInfo info = JacksonUtils.toObj(hubMeta.getVersionInfo(),
                ResourceVersionInfo.class);
        if (info == null || info.getLabels() == null
                || !Objects.equals(info.getLabels().get(AiResourceConstants.LABEL_LATEST),
                metaReference.getSourceVersion())) {
            return false;
        }
        AiResource source = resourceManager.findMeta(metaReference.getSourceNamespaceId(),
                metaReference.getSourceName(), AiResourceConstants.RESOURCE_TYPE_SKILL);
        if (source == null || !Objects.equals(source.getId(), metaReference.getSourceResourceId())
                || !AiResourceConstants.META_STATUS_ENABLE.equals(source.getStatus())
                || !VisibilityConstants.SCOPE_PUBLIC.equals(source.getScope())) {
            return false;
        }
        AiResourceVersion sourceVersion = resourceManager.findVersion(
                metaReference.getSourceNamespaceId(), metaReference.getSourceName(),
                AiResourceConstants.RESOURCE_TYPE_SKILL, metaReference.getSourceVersion());
        return sourceVersion != null
                && Objects.equals(sourceVersion.getId(), metaReference.getSourceVersionId())
                && AiResourceConstants.VERSION_STATUS_ONLINE.equals(sourceVersion.getStatus());
    }
    
    private boolean sameReference(HubResourceReference left, HubResourceReference right) {
        return Objects.equals(left.getSourceNamespaceId(), right.getSourceNamespaceId())
                && Objects.equals(left.getSourceResourceId(), right.getSourceResourceId())
                && Objects.equals(left.getSourceName(), right.getSourceName())
                && Objects.equals(left.getSourceVersionId(), right.getSourceVersionId())
                && Objects.equals(left.getSourceVersion(), right.getSourceVersion());
    }
}
