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
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareOutcome;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Switches an active Hub Skill entry to an explicitly selected source version.
 *
 * <p>Only the existing Hub entry's effective pointer changes; neither the
 * original source resource nor any existing Hub version row is modified.
 * Version preparation is insert-only. A single strict metadata CAS commits the
 * new pointer. All authorization uses the actual source Skill rather than the
 * system-owned Hub entry.</p>
 *
 * <p>The current source can be revoked concurrently with a Hub CAS. Read and
 * import paths must still revalidate live source eligibility. Post-commit
 * validation here is a best-effort compensating guard, not an atomic
 * cross-namespace transaction.</p>
 */
@Service
public class HubSkillAdjustService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(HubSkillAdjustService.class);
    
    private final HubShareRepository repository;
    
    private final HubSkillEligibilityValidator eligibilityValidator;
    
    private final HubSkillAuthorizationService authorizationService;
    
    public HubSkillAdjustService(HubShareRepository repository,
        HubSkillEligibilityValidator eligibilityValidator,
        HubSkillAuthorizationService authorizationService) {
        this.repository = repository;
        this.eligibilityValidator = eligibilityValidator;
        this.authorizationService = authorizationService;
    }
    
    /**
     * Update the pinned version without changing the stable Hub entry ID.
     *
     * <p>Both a stale metaVersion and a simultaneous unshare cause HTTP 409.
     * This method never retries a lost CAS against a newer metaVersion, even
     * when the competing operation selected the same target version.</p>
     *
     * @param request entry ID, concrete target version and expected metaVersion
     * @return adjusted or unchanged share with its new/current metaVersion
     * @throws NacosException on authorization, qualification or CAS failure
     */
    public HubShareResult adjustShare(HubAdjustShareRequest request) throws NacosException {
        HubConfig.requireEnabled();
        HubSkillRequestValidator.validate(request);
        long id = request.getHubEntryId();
        long expected = request.getExpectedMetaVersion();
        
        AiResource entry = requireEntry(id);
        HubResourceReference currentReference = HubResourceReferenceJsonUtil.parse(entry.getExt());
        
        // Authorize on the real source before disclosing any version or status details.
        AiResource source = eligibilityValidator.requireReferencedSource(currentReference);
        authorizationService.requireAdjustPermission(source);
        requireActivePointer(entry, currentReference);
        requireExpectedVersion(entry, expected);
        
        HubEligibleSkillVersion target = eligibilityValidator.requireShareableVersion(source,
            request.getTargetVersion());
        authorizationService.requireAdjustPermission(target.getSourceMeta());
        HubResourceReference targetReference = target.getReference();
        requireSameSource(currentReference, targetReference);
        
        // A version string must never be rebound to a new source version ID,
        // even if its previously published Hub version row is missing.
        if (Objects.equals(currentReference.getSourceVersion(),
            targetReference.getSourceVersion())) {
            if (!sameReference(currentReference, targetReference)) {
                throw conflict("Source Skill version identity changed; refuse same-version rebind");
            }
            // An unchanged operation validates existing rows without repairing them.
            repository.requireReferenceVersion(id, targetReference);
            revalidate(source, targetReference);
            AiResource confirmed = requireEntry(id);
            requireActivePointer(confirmed, targetReference);
            requireExpectedVersion(confirmed, expected);
            return result(confirmed, targetReference, HubShareOutcome.UNCHANGED);
        }
        
        // A prepared version must be immutable. A same-version/different-ID
        // collision is rejected by the S1-4 repository (never overwritten).
        repository.ensureReferenceVersion(id, targetReference);
        revalidate(source, targetReference);
        
        // One atomic ID + expectedMetaVersion CAS is the publication point.
        if (!repository.publish(id, expected, targetReference)) {
            throw conflict("Hub Skill share changed concurrently; refresh and retry");
        }
        
        // Best-effort revocation fence. Only disable our own newly published
        // pointer if it is still at exactly expected+1; never undo a competing
        // adjust or unshare that happened after our CAS.
        try {
            revalidate(source, targetReference);
        } catch (NacosException | RuntimeException invalidated) {
            disableOwnPublication(id, expected + 1L, targetReference);
            throw invalidated;
        }
        
        AiResource confirmed = requireEntry(id);
        requireActivePointer(confirmed, targetReference);
        if (!Objects.equals(confirmed.getMetaVersion(), expected + 1L)) {
            throw conflict("Hub Skill share changed after adjustment");
        }
        return result(confirmed, targetReference, HubShareOutcome.ADJUSTED);
    }
    
    private void revalidate(AiResource source, HubResourceReference expected)
        throws NacosException {
        HubConfig.requireEnabled();
        HubEligibleSkillVersion fresh = eligibilityValidator.requireShareableVersion(source,
            expected.getSourceVersion());
        requireSameSource(expected, fresh.getReference());
        if (!sameReference(expected, fresh.getReference())) {
            throw conflict("Source Skill version identity changed during adjustment");
        }
        authorizationService.requireAdjustPermission(fresh.getSourceMeta());
    }
    
    private void disableOwnPublication(long id, long committedVersion,
        HubResourceReference published) {
        try {
            AiResource observed = repository.findByEntryId(id);
            if (observed != null && Objects.equals(observed.getMetaVersion(), committedVersion)
                && AiResourceConstants.META_STATUS_ENABLE.equals(observed.getStatus())
                && sameEffectivePointer(observed, published)) {
                if (!repository.disable(id, committedVersion)) {
                    LOGGER.warn("Hub adjust compensation lost CAS, entryId={}", id);
                }
            }
        } catch (Exception failure) {
            LOGGER.warn("Hub adjust compensation failed, entryId={}", id, failure);
        }
    }
    
    private AiResource requireEntry(long id) throws NacosException {
        AiResource entry = repository.findByEntryId(id);
        if (entry == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Hub Skill entry not found: " + id);
        }
        if (entry.getId() == null || entry.getId() != id || entry.getMetaVersion() == null
            || entry.getMetaVersion() <= 0 || !AiConstants.Hub.NAMESPACE_ID.equals(
                entry.getNamespaceId())
            || !AiResourceConstants.RESOURCE_TYPE_SKILL.equals(
                entry.getType())
            || !"hub".equals(entry.getFrom())) {
            throw conflict("Hub Skill entry identity or metadata is invalid");
        }
        return entry;
    }
    
    private void requireActivePointer(AiResource entry, HubResourceReference reference)
        throws NacosException {
        if (!AiResourceConstants.META_STATUS_ENABLE.equals(entry.getStatus())
            || !sameEffectivePointer(entry, reference)) {
            throw conflict("Hub Skill is not actively shared or its version pointer is invalid");
        }
    }
    
    private boolean sameEffectivePointer(AiResource entry, HubResourceReference expected)
        throws NacosException {
        HubResourceReference stored = HubResourceReferenceJsonUtil.parse(entry.getExt());
        if (!sameReference(stored, expected)) {
            return false;
        }
        try {
            ResourceVersionInfo info = JacksonUtils.toObj(entry.getVersionInfo(),
                ResourceVersionInfo.class);
            return info != null && info.getLabels() != null
                && Objects.equals(info.getLabels().get(AiResourceConstants.LABEL_LATEST),
                    expected.getSourceVersion());
        } catch (RuntimeException e) {
            throw conflict("Hub Skill version pointer is invalid");
        }
    }
    
    private void requireExpectedVersion(AiResource entry, long expected)
        throws NacosException {
        if (!Objects.equals(entry.getMetaVersion(), expected)) {
            throw conflict("Hub Skill metaVersion is stale; refresh and retry");
        }
    }
    
    private void requireSameSource(HubResourceReference oldRef,
        HubResourceReference nextRef) throws NacosException {
        if (!Objects.equals(oldRef.getSourceNamespaceId(), nextRef.getSourceNamespaceId())
            || !Objects.equals(oldRef.getSourceResourceId(), nextRef.getSourceResourceId())
            || !Objects.equals(oldRef.getSourceName(), nextRef.getSourceName())) {
            throw conflict("Hub entry cannot be rebound to a different source Skill");
        }
    }
    
    private boolean sameReference(HubResourceReference left, HubResourceReference right) {
        return Objects.equals(left.getSourceNamespaceId(), right.getSourceNamespaceId())
            && Objects.equals(left.getSourceResourceId(), right.getSourceResourceId())
            && Objects.equals(left.getSourceName(), right.getSourceName())
            && Objects.equals(left.getSourceVersionId(), right.getSourceVersionId())
            && Objects.equals(left.getSourceVersion(), right.getSourceVersion());
    }
    
    private HubShareResult result(AiResource entry, HubResourceReference ref,
        HubShareOutcome outcome) {
        HubShareResult result = new HubShareResult();
        result.setHubEntryId(entry.getId());
        result.setDisplayName(ref.getSourceName());
        result.setSourceNamespaceId(ref.getSourceNamespaceId());
        result.setSourceResourceId(ref.getSourceResourceId());
        result.setSourceName(ref.getSourceName());
        result.setSourceVersionId(ref.getSourceVersionId());
        result.setSourceVersion(ref.getSourceVersion());
        result.setMetaVersion(entry.getMetaVersion());
        result.setOutcome(outcome);
        result.setActive(true);
        return result;
    }
    
    private NacosApiException conflict(String message) {
        return new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
            message);
    }
}
