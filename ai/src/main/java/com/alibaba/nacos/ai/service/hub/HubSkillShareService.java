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
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.model.hub.HubShareOutcome;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Application service for sharing one exact source Skill version.
 *
 * <p>Only the share path is implemented in S1-5. S1-6/S1-7 will compose this
 * with adjust/unshare in {@link HubSkillService}; no incomplete service implementation is registered as an HTTP entry
 * point in S1-5.</p>
 *
 * <p>Source permission and eligibility are checked before reservation and
 * immediately before the publication CAS. Reads/imports must still verify the current source state: cross-namespace
 * source revocation cannot be made atomic with the Hub metadata CAS.</p>
 */
@Service
public class HubSkillShareService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(HubSkillShareService.class);
    
    private final HubSkillEligibilityValidator eligibilityValidator;
    
    private final HubSkillAuthorizationService authorizationService;
    
    private final HubShareRepository repository;
    
    public HubSkillShareService(HubSkillEligibilityValidator eligibilityValidator,
            HubSkillAuthorizationService authorizationService, HubShareRepository repository) {
        this.eligibilityValidator = eligibilityValidator;
        this.authorizationService = authorizationService;
        this.repository = repository;
    }
    
    /**
     * Share a concrete, online, PUBLIC source Skill version.
     *
     * <p>An active share of the same version is idempotent. An active share
     * of a different version is a conflict and requires adjustShare. A disabled entry is reactivated using its original
     * Hub Entry ID.</p>
     *
     * @param request source namespace/name, concrete version and optional source ID
     * @return stable Hub entry identity and operation result
     * @throws NacosException on invalid source, permission or concurrent conflict
     */
    public HubShareResult shareSkill(HubShareSkillRequest request) throws NacosException {
        HubSkillRequestValidator.validate(request);
        AiResource initialSource = eligibilityValidator.requireSourceForShare(request);
        authorizationService.requireSharePermission(initialSource);
        HubEligibleSkillVersion eligible = eligibilityValidator.requireShareableVersion(initialSource,
                request.getSourceVersion());
        authorizationService.requireSharePermission(eligible.getSourceMeta());
        HubResourceReference reference = eligible.getReference();
        
        // The repository's unique source key includes c_from='hub'. Reservation
        // is idempotent even if another node is creating this entry concurrently.
        AiResource entry = repository.reserveEntry(reference, eligible.getSourceMeta().getDesc());
        requireValidEntry(entry);
        
        if (isActive(entry)) {
            return alreadyShared(entry, reference, initialSource);
        }
        requireDisabled(entry);
        boolean hadPreviousVersion = hasPreviousVersion(entry);
        long expectedVersion = entry.getMetaVersion();
        
        // This step must NEVER mutate an already-existing version's storage.
        repository.ensureReferenceVersion(entry.getId(), reference);
        revalidate(initialSource, reference);
        
        // An operation may have changed the entry during version preparation.
        // Never automatically roll forward a stale meta_version.
        if (!repository.publish(entry.getId(), expectedVersion, reference)) {
            AiResource latest = requireCurrentEntry(entry.getId());
            if (isActive(latest) && sameEffectiveVersion(latest, reference)) {
                return alreadyShared(latest, reference, initialSource);
            }
            throw conflict("Hub Skill share changed concurrently; refresh and retry");
        }
        
        // Recheck the source after the CAS as a best-effort revocation fence.
        // If source revalidation fails, deactivate ONLY the version we published.
        try {
            revalidateEligibility(initialSource, reference);
        } catch (NacosException | RuntimeException invalidated) {
            rollbackOwnPublication(entry.getId(), expectedVersion + 1L, reference);
            throw invalidated;
        }
        AiResource published = requireCurrentEntry(entry.getId());
        if (!isActive(published) || !sameEffectiveVersion(published, reference)) {
            throw conflict("Hub Skill share changed during publication");
        }
        HubShareOutcome outcome = hadPreviousVersion ? HubShareOutcome.REACTIVATED : HubShareOutcome.CREATED;
        return result(published, reference, outcome);
    }
    
    private HubShareResult alreadyShared(AiResource entry, HubResourceReference requested, AiResource originalSource)
            throws NacosException {
        if (!sameEffectiveVersion(entry, requested)) {
            throw conflict("Skill is already shared at a different version; use adjustShare");
        }
        // Also validate the immutable version row; don't silently accept an
        // active metadata pointer with a different underlying sourceVersionId.
        repository.requireReferenceVersion(entry.getId(), requested);
        revalidate(originalSource, requested);
        AiResource confirmed = requireCurrentEntry(entry.getId());
        if (!isActive(confirmed) || !sameEffectiveVersion(confirmed, requested)) {
            throw conflict("Hub Skill share changed during idempotent read");
        }
        return result(confirmed, requested, HubShareOutcome.ALREADY_SHARED);
    }
    
    private void revalidate(AiResource originalSource, HubResourceReference expected) throws NacosException {
        HubEligibleSkillVersion latest = revalidateEligibility(originalSource, expected);
        authorizationService.requireSharePermission(latest.getSourceMeta());
    }
    
    private HubEligibleSkillVersion revalidateEligibility(AiResource originalSource, HubResourceReference expected)
            throws NacosException {
        HubEligibleSkillVersion latest = eligibilityValidator.requireShareableVersion(originalSource,
                expected.getSourceVersion());
        if (!sameReference(latest.getReference(), expected)) {
            throw conflict("Source Skill or version identity changed during sharing");
        }
        return latest;
    }
    
    private void rollbackOwnPublication(long id, long ourPublishedVersion, HubResourceReference reference) {
        // Do not mask the original source validation failure if repair fails.
        // S2/S4 read-time eligibility checks and reconciliation remain required.
        try {
            AiResource current = repository.findByEntryId(id);
            if (current != null && isActive(current) && Objects.equals(current.getMetaVersion(), ourPublishedVersion)
                    && sameEffectiveVersion(current, reference)) {
                if (!repository.disable(id, ourPublishedVersion)) {
                    LOGGER.warn("Hub share post-publication compensation lost CAS, entryId={}", id);
                }
            }
        } catch (Exception failure) {
            // Preserve the original eligibility failure. S2/S4 must also reject
            // stale references at read time and reconcile unreachable entries.
            LOGGER.warn("Hub share post-publication compensation failed, entryId={}", id, failure);
        }
    }
    
    private AiResource requireCurrentEntry(long id) throws NacosException {
        AiResource entry = repository.findByEntryId(id);
        requireValidEntry(entry);
        return entry;
    }
    
    private void requireValidEntry(AiResource entry) throws NacosException {
        if (entry == null || entry.getId() == null || entry.getId() <= 0 || entry.getMetaVersion() == null
                || entry.getMetaVersion() <= 0) {
            throw conflict("Hub Skill entry is missing or has invalid metadata");
        }
    }
    
    private void requireDisabled(AiResource entry) throws NacosException {
        if (!AiResourceConstants.META_STATUS_DISABLE.equals(entry.getStatus())) {
            throw conflict("Hub Skill entry has unsupported status");
        }
    }
    
    private boolean isActive(AiResource entry) {
        return AiResourceConstants.META_STATUS_ENABLE.equals(entry.getStatus());
    }
    
    private boolean hasPreviousVersion(AiResource entry) throws NacosException {
        ResourceVersionInfo info = readVersionInfo(entry);
        return info.getLabels() != null && info.getLabels().get(AiResourceConstants.LABEL_LATEST) != null;
    }
    
    private boolean sameEffectiveVersion(AiResource entry, HubResourceReference requested) throws NacosException {
        HubResourceReference current = HubResourceReferenceJsonUtil.parse(entry.getExt());
        ResourceVersionInfo info = readVersionInfo(entry);
        return sameReference(current, requested) && info.getLabels() != null && Objects.equals(
                info.getLabels().get(AiResourceConstants.LABEL_LATEST), requested.getSourceVersion());
    }
    
    private ResourceVersionInfo readVersionInfo(AiResource entry) throws NacosException {
        try {
            ResourceVersionInfo info = JacksonUtils.toObj(entry.getVersionInfo(), ResourceVersionInfo.class);
            if (info == null || info.getLabels() == null) {
                throw conflict("Hub Skill version pointer is missing or corrupt");
            }
            return info;
        } catch (NacosException e) {
            throw e;
        } catch (RuntimeException e) {
            throw conflict("Hub Skill version pointer is invalid");
        }
    }
    
    private boolean sameReference(HubResourceReference left, HubResourceReference right) {
        return left != null && right != null && Objects.equals(left.getSourceNamespaceId(),
                right.getSourceNamespaceId()) && Objects.equals(left.getSourceResourceId(), right.getSourceResourceId())
                && Objects.equals(left.getSourceName(), right.getSourceName()) && Objects.equals(
                left.getSourceVersionId(), right.getSourceVersionId()) && Objects.equals(left.getSourceVersion(),
                right.getSourceVersion());
    }
    
    private HubShareResult result(AiResource entry, HubResourceReference reference, HubShareOutcome outcome) {
        HubShareResult result = new HubShareResult();
        result.setHubEntryId(entry.getId());
        result.setDisplayName(reference.getSourceName());
        result.setSourceNamespaceId(reference.getSourceNamespaceId());
        result.setSourceResourceId(reference.getSourceResourceId());
        result.setSourceName(reference.getSourceName());
        result.setSourceVersionId(reference.getSourceVersionId());
        result.setSourceVersion(reference.getSourceVersion());
        result.setMetaVersion(entry.getMetaVersion());
        result.setOutcome(outcome);
        result.setActive(true);
        return result;
    }
    
    private NacosApiException conflict(String message) {
        return new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT, message);
    }
}
