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
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareOutcome;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Revokes an existing Skill share without touching the original source Skill.
 *
 * <p>Ordinary unshare requires the original source's WRITE privilege, even when
 * that source or version has been disabled. Administrative takedown instead
 * requires the independent Hub governance privilege and works without the
 * original source. Both operations commit via the repository's ID-pinned
 * strict metadata CAS, which also serializes against version adjustment.</p>
 */
@Service
public class HubSkillUnshareService {
    
    private static final String HUB_SOURCE = "hub";
    
    private final HubShareRepository repository;
    
    private final HubSkillEligibilityValidator eligibilityValidator;
    
    private final HubSkillAuthorizationService authorizationService;
    
    private final HubTakedownAuditService auditService;
    
    public HubSkillUnshareService(HubShareRepository repository,
        HubSkillEligibilityValidator eligibilityValidator,
        HubSkillAuthorizationService authorizationService,
        HubTakedownAuditService auditService) {
        this.repository = repository;
        this.eligibilityValidator = eligibilityValidator;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }
    
    /**
     * Cancel an existing share using the owner's current source WRITE rights.
     *
     * <p>Do not require the source to be PUBLIC, enabled or online. Only a
     * proven existing source identity plus source WRITE is needed.</p>
     *
     * @param request share ID and expected metadata version
     * @return disabled entry state
     * @throws NacosException if validation, authorization or CAS fails
     */
    public HubShareResult unshareSkill(HubUnshareRequest request) throws NacosException {
        HubSkillRequestValidator.validate(request);
        long entryId = request.getHubEntryId();
        long expected = request.getExpectedMetaVersion();
        AiResource entry = requireHubEntry(entryId);
        HubResourceReference reference = HubResourceReferenceJsonUtil.parse(entry.getExt());
        
        // Check the source owner/WRITE; never authorize against the Hub's system owner.
        AiResource source = eligibilityValidator.requireReferencedSource(reference);
        authorizationService.requireUnsharePermission(source);
        
        // A second read prevents a deleted-and-recreated source from retaining
        // permission merely by reusing its name.
        AiResource freshSource = eligibilityValidator.requireReferencedSource(reference);
        authorizationService.requireUnsharePermission(freshSource);
        return disableAuthorized(entry, reference, expected, false, null);
    }
    
    /**
     * Administratively take down a share, including one whose source vanished.
     *
     * <p>The reason is emitted in a Hub-specific trace event after a successful
     * state transition. S1 does not pretend this trace event is transactional
     * with the metadata CAS.</p>
     *
     * @param request share ID, expected metadata version and takedown reason
     * @return disabled entry state
     * @throws NacosException if governance permission or CAS fails
     */
    public HubShareResult forceUnshare(HubForceUnshareRequest request) throws NacosException {
        HubSkillRequestValidator.validate(request);
        // Must be checked before reading the entry: no source WRITE shortcut.
        authorizationService.requireForceUnsharePermission();
        AiResource entry = requireHubEntry(request.getHubEntryId());
        HubResourceReference reference = HubResourceReferenceJsonUtil.parse(entry.getExt());
        // Repeat at the mutation boundary; the controller is not an authorization boundary.
        authorizationService.requireForceUnsharePermission();
        return disableAuthorized(entry, reference, request.getExpectedMetaVersion(), true,
            request.getReason().trim());
    }
    
    private HubShareResult disableAuthorized(AiResource observed,
        HubResourceReference reference, long expected, boolean administrative, String reason)
        throws NacosException {
        if (AiResourceConstants.META_STATUS_DISABLE.equals(observed.getStatus())) {
            // A retry after a successful cancellation is safe: there is no state
            // mutation, even when its original metaVersion is now stale.
            AiResource current = requireHubEntry(observed.getId());
            HubResourceReference latestReference = HubResourceReferenceJsonUtil.parse(
                current.getExt());
            if (!AiResourceConstants.META_STATUS_DISABLE.equals(current.getStatus())
                || !sameSource(reference, latestReference)) {
                throw conflict("Hub Skill share changed concurrently; refresh and retry");
            }
            return buildResult(current, latestReference, HubShareOutcome.ALREADY_UNSHARED);
        }
        if (!AiResourceConstants.META_STATUS_ENABLE.equals(observed.getStatus())) {
            throw conflict("Hub Skill has an unknown sharing status");
        }
        if (!Objects.equals(observed.getMetaVersion(), expected)) {
            throw conflict("Hub Skill metaVersion is stale; refresh and retry");
        }
        if (!repository.disable(observed.getId(), expected)) {
            // Another unshare that won the CAS is equivalent to our requested
            // disabled final state. An adjust/reactivation is NOT equivalent.
            AiResource latest = requireHubEntry(observed.getId());
            HubResourceReference latestReference = HubResourceReferenceJsonUtil.parse(
                latest.getExt());
            if (AiResourceConstants.META_STATUS_DISABLE.equals(latest.getStatus())
                && sameSource(reference, latestReference)) {
                return buildResult(latest, latestReference, HubShareOutcome.ALREADY_UNSHARED);
            }
            throw conflict("Hub Skill share changed concurrently; refresh and retry");
        }
        
        // CAS committed: emit exactly one mutation audit (never audit no-op retries).
        auditService.recordUnshare(observed.getId(), reference, expected + 1L,
            administrative, reason);
        AiResource updated = requireHubEntry(observed.getId());
        if (!AiResourceConstants.META_STATUS_DISABLE.equals(updated.getStatus())
            || !Objects.equals(updated.getMetaVersion(), expected + 1L)) {
            throw conflict("Hub Skill changed after cancellation; refresh and retry");
        }
        return buildResult(updated, reference, administrative
            ? HubShareOutcome.FORCE_UNSHARED : HubShareOutcome.UNSHARED);
    }
    
    private AiResource requireHubEntry(long entryId) throws NacosException {
        AiResource entry = repository.findByEntryId(entryId);
        if (entry == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Hub Skill entry not found: " + entryId);
        }
        if (!Objects.equals(entry.getId(), entryId)
            || entry.getMetaVersion() == null || entry.getMetaVersion() <= 0
            || !AiConstants.Hub.NAMESPACE_ID.equals(entry.getNamespaceId())
            || !AiResourceConstants.RESOURCE_TYPE_SKILL.equals(entry.getType())
            || !HUB_SOURCE.equals(entry.getFrom())) {
            throw conflict("Hub Skill entry identity or metadata is invalid");
        }
        return entry;
    }
    
    private boolean sameSource(HubResourceReference left, HubResourceReference right) {
        return Objects.equals(left.getSourceNamespaceId(), right.getSourceNamespaceId())
            && Objects.equals(left.getSourceResourceId(), right.getSourceResourceId())
            && Objects.equals(left.getSourceName(), right.getSourceName());
    }
    
    private HubShareResult buildResult(AiResource entry, HubResourceReference reference,
        HubShareOutcome outcome) {
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
        result.setActive(false);
        return result;
    }
    
    private NacosApiException conflict(String message) {
        return new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
            message);
    }
}
