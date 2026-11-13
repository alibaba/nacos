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
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Objects;

/**
 * S2-2: resolve one effective Hub Skill entry by its immutable database ID.
 *
 * <p>Only validates the Hub metadata, effective version pointer and immutable
 * Hub version descriptor. It deliberately does NOT check source status or the
 * caller's access rights. S2-3/S2-7 must perform those fresh checks before
 * publishing any DTO/content. Never expose this class as a direct HTTP handler.</p>
 *
 * <p>Reading the Hub row a second time detects pointer changes during the
 * version lookup. It is not a cross-namespace transaction; callers must check
 * the source and run {@link #requireUnchanged(ResolvedHubSkillReference)} as
 * a final revocation fence immediately before returning content.</p>
 *
 * @author nacos
 */
@Service
public class HubSkillReferenceResolver {
    
    private static final String HUB_NAMESPACE = AiConstants.Hub.NAMESPACE_ID;
    
    private static final String SKILL = AiResourceConstants.RESOURCE_TYPE_SKILL;
    
    private static final String HUB_FROM = "hub";
    
    private static final String HUB_OWNER = "nacos-ai-hub";
    
    private final HubShareRepository repository;
    
    public HubSkillReferenceResolver(HubShareRepository repository) {
        this.repository = repository;
    }
    
    /**
     * Resolve the currently active, pinned Hub reference without touching the source.
     *
     * @param hubEntryId Hub resource's generated primary key, not its internal name
     * @return immutable server-only snapshot for subsequent authorization and source checks
     * @throws NacosException if the entry is absent, inactive or internally inconsistent
     */
    public ResolvedHubSkillReference resolve(Long hubEntryId) throws NacosException {
        HubSkillReadRequestValidator.validateEntryId(hubEntryId);
        try {
            return resolveEntry(hubEntryId);
        } catch (RuntimeException failedRead) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Hub Skill read service unavailable");
        }
    }
    
    private ResolvedHubSkillReference resolveEntry(long hubEntryId) throws NacosException {
        AiResource entry = snapshotMeta(repository.findByEntryId(hubEntryId));
        requireActiveEntry(entry, hubEntryId);
        HubResourceReference reference = requireCurrentReference(entry);
        
        // This S1 repository method validates an existing immutable Hub version;
        // it must never call ensureReferenceVersion (which may INSERT on a read).
        AiResourceVersion version;
        try {
            version = repository.requireReferenceVersion(hubEntryId, reference);
        } catch (NacosException invalidVersion) {
            if (invalidVersion.getErrCode() >= NacosException.SERVER_ERROR) {
                throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                    "Hub Skill reference validation unavailable");
            }
            throw unavailable();
        }
        requireMatchingVersion(entry, version, reference);
        
        // Detect adjust/unshare between metadata and pinned-version reads.
        ResolvedHubSkillReference publication =
            new ResolvedHubSkillReference(entry, version, reference);
        AiResource latest = repository.findByEntryId(hubEntryId);
        if (!sameMeta(entry, latest)) {
            throw unavailable();
        }
        return publication;
    }
    
    /**
     * Fail when the effective Hub pointer changed since a previous resolution.
     *
     * <p>Call this AFTER fetching source content, followed by fresh source
     * state/authorization checks. It cannot by itself prevent a concurrent
     * source deletion, revoke a user's permissions, or grant Hub visibility.</p>
     *
     * @param expected snapshot previously obtained by {@link #resolve(Long)}
     * @throws NacosException when a newer share, adjustment or unshare is observed
     */
    public void requireUnchanged(ResolvedHubSkillReference expected) throws NacosException {
        if (expected == null || !expected.samePublication(resolve(expected.getHubEntryId()))) {
            throw unavailable();
        }
    }
    
    private void requireActiveEntry(AiResource entry, long expectedId) throws NacosException {
        if (entry == null || !Objects.equals(entry.getId(), expectedId)
            || !HUB_NAMESPACE.equals(entry.getNamespaceId())
            || !SKILL.equals(entry.getType())
            || !HUB_FROM.equals(entry.getFrom())
            || !HUB_OWNER.equals(entry.getOwner())
            || !VisibilityConstants.SCOPE_PUBLIC.equals(entry.getScope())
            || !AiResourceConstants.META_STATUS_ENABLE.equals(entry.getStatus())
            || entry.getMetaVersion() == null || entry.getMetaVersion() <= 0) {
            throw unavailable();
        }
    }
    
    private HubResourceReference requireCurrentReference(AiResource entry)
        throws NacosException {
        try {
            HubResourceReference reference = HubResourceReferenceJsonUtil.parse(entry.getExt());
            if (HUB_NAMESPACE.equals(reference.getSourceNamespaceId())
                || AiResourceConstants.LABEL_LATEST.equalsIgnoreCase(reference.getSourceVersion())
                || !reference.getSourceVersion().equals(reference.getSourceVersion().trim())
                || !Objects.equals(entry.getName(), HubResourceNameUtils.buildInternalName(
                    reference.getSourceName(), reference.getSourceResourceId()))) {
                throw unavailable();
            }
            ResourceVersionInfo info = JacksonUtils.toObj(entry.getVersionInfo(),
                ResourceVersionInfo.class);
            if (info == null || info.getLabels() == null
                || !Objects.equals(info.getLabels().get(AiResourceConstants.LABEL_LATEST),
                    reference.getSourceVersion())) {
                throw unavailable();
            }
            return reference;
        } catch (NacosException | RuntimeException invalidReference) {
            // Malformed stored descriptors never reach a public Hub read response.
            throw unavailable();
        }
    }
    
    private void requireMatchingVersion(AiResource meta, AiResourceVersion version,
        HubResourceReference reference) throws NacosException {
        if (version == null || version.getId() == null || version.getId() <= 0
            || !HUB_NAMESPACE.equals(version.getNamespaceId())
            || !SKILL.equals(version.getType())
            || !HUB_OWNER.equals(version.getAuthor())
            || !Objects.equals(meta.getName(), version.getName())
            || !Objects.equals(reference.getSourceVersion(), version.getVersion())
            || !AiResourceConstants.VERSION_STATUS_ONLINE.equals(version.getStatus())) {
            throw unavailable();
        }
        try {
            HubResourceReference stored = HubResourceReferenceJsonUtil.parse(version.getStorage());
            if (!sameReference(stored, reference)) {
                throw unavailable();
            }
        } catch (NacosException | RuntimeException invalidStorage) {
            throw unavailable();
        }
    }
    
    private boolean sameReference(HubResourceReference one, HubResourceReference two) {
        return Objects.equals(one.getSourceNamespaceId(), two.getSourceNamespaceId())
            && Objects.equals(one.getSourceResourceId(), two.getSourceResourceId())
            && Objects.equals(one.getSourceName(), two.getSourceName())
            && Objects.equals(one.getSourceVersionId(), two.getSourceVersionId())
            && Objects.equals(one.getSourceVersion(), two.getSourceVersion());
    }
    
    private boolean sameMeta(AiResource one, AiResource two) {
        return two != null && Objects.equals(one.getId(), two.getId())
            && Objects.equals(one.getMetaVersion(), two.getMetaVersion())
            && Objects.equals(one.getNamespaceId(), two.getNamespaceId())
            && Objects.equals(one.getName(), two.getName())
            && Objects.equals(one.getType(), two.getType())
            && Objects.equals(one.getFrom(), two.getFrom())
            && Objects.equals(one.getOwner(), two.getOwner())
            && Objects.equals(one.getScope(), two.getScope())
            && Objects.equals(one.getStatus(), two.getStatus())
            && Objects.equals(one.getExt(), two.getExt())
            && Objects.equals(one.getVersionInfo(), two.getVersionInfo())
            && Objects.equals(one.getDesc(), two.getDesc())
            && Objects.equals(one.getBizTags(), two.getBizTags());
    }
    
    private AiResource snapshotMeta(AiResource persisted) {
        if (persisted == null) {
            return null;
        }
        AiResource snapshot = new AiResource();
        BeanUtils.copyProperties(persisted, snapshot);
        if (persisted.getGmtCreate() != null) {
            snapshot.setGmtCreate(new Timestamp(persisted.getGmtCreate().getTime()));
        }
        if (persisted.getGmtModified() != null) {
            snapshot.setGmtModified(new Timestamp(persisted.getGmtModified().getTime()));
        }
        return snapshot;
    }
    
    private NacosApiException unavailable() {
        // Return one indistinguishable response for absent, inactive, and corrupt
        // Hub entries. Never include the raw ext, storage or source identifiers.
        return new NacosApiException(NacosException.NOT_FOUND,
            ErrorCode.RESOURCE_NOT_FOUND, "Hub Skill not found or unavailable");
    }
}
