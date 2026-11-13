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
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Internal, user-independent, ID-pinned publication invalidation.
 *
 * <p>Every retry reloads the canonical Hub row and reevaluates the predicate.
 * A concurrent adjust, re-share or administrator takedown can never be reverted
 * by a stale disable. This class never mutates a source Skill or the Hub version row.</p>
 */
@Service
public class HubSkillPublicationInvalidator {
    
    private static final int MAX_CAS_ATTEMPTS = 12;
    
    private final HubEntryStorage storage;
    
    private final HubSkillIndexNotifier notifier;
    
    /**
     * Create the publication invalidator.
     *
     * @param storage Hub metadata storage
     * @param notifier derived index notifier
     */
    public HubSkillPublicationInvalidator(HubEntryStorage storage, HubSkillIndexNotifier notifier) {
        this.storage = storage;
        this.notifier = notifier;
    }
    
    /**
     * Disable an active Hub entry only if the *current* row matches the supplied
     * server-side reason for invalidation. Return false when absent/already
     * disabled/changed to a healthy publication.
     */
    public boolean disableIf(long hubEntryId, InvalidationReason shouldDisable)
        throws NacosException {
        if (hubEntryId <= 0 || shouldDisable == null) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Hub invalidation requires an entry and reason");
        }
        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            AiResource current;
            try {
                current = storage.findByEntryId(hubEntryId);
            } catch (RuntimeException storageFailure) {
                throw storageUnavailable(storageFailure);
            }
            if (current == null) {
                return false;
            }
            requireHubIdentity(current, hubEntryId);
            if (AiResourceConstants.META_STATUS_DISABLE.equals(current.getStatus())) {
                return false;
            }
            if (!AiResourceConstants.META_STATUS_ENABLE.equals(current.getStatus())) {
                throw new NacosApiException(NacosException.SERVER_ERROR,
                    ErrorCode.SERVER_ERROR, "Invalid Hub publication status");
            }
            // Re-evaluation, not a cached decision, is the key CAS-race fence.
            if (!shouldDisable.test(current)) {
                return false;
            }
            AiResource next = new AiResource();
            next.setStatus(AiResourceConstants.META_STATUS_DISABLE);
            next.setDesc(current.getDesc());
            next.setBizTags(current.getBizTags());
            next.setExt(current.getExt());
            next.setVersionInfo(current.getVersionInfo());
            boolean updated;
            try {
                updated = storage.updateMetaCas(current, current.getMetaVersion(), next);
            } catch (RuntimeException storageFailure) {
                throw storageUnavailable(storageFailure);
            }
            if (updated) {
                notifier.entryChanged(current.getName());
                return true;
            }
        }
        // Do not report a successful lifecycle transition if the publication
        // could not be revoked. The source mutation must stop in its pre-fence.
        throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
            "Hub publication changed repeatedly; retry source lifecycle operation");
    }
    
    private NacosException storageUnavailable(RuntimeException cause) {
        NacosException error = new NacosApiException(NacosException.SERVER_ERROR,
            ErrorCode.SERVER_ERROR, "Hub publication storage unavailable");
        error.initCause(cause);
        return error;
    }
    
    /** A revocation decision may fail when its authoritative storage is unavailable. */
    @FunctionalInterface
    public interface InvalidationReason {
        
        /**
         * Decide against the current publication, without changing it.
         *
         * @param current current Hub metadata
         * @return whether this publication must be disabled
         * @throws NacosException if the reason cannot be evaluated
         */
        boolean test(AiResource current) throws NacosException;
    }
    
    private void requireHubIdentity(AiResource entry, long requestedId) throws NacosException {
        if (!Objects.equals(entry.getId(), requestedId)
            || !AiConstants.Hub.NAMESPACE_ID.equals(entry.getNamespaceId())
            || !AiResourceConstants.RESOURCE_TYPE_SKILL.equals(entry.getType())
            || !"hub".equals(entry.getFrom()) || entry.getMetaVersion() == null
            || entry.getMetaVersion() <= 0 || entry.getName() == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Hub publication identity is inconsistent");
        }
    }
}
