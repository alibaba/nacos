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
import com.alibaba.nacos.api.exception.NacosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Repairs active Hub metadata orphaned by source deletion, version withdrawal,
 * PRIVATE scope, interrupted lifecycle operations, or missed cross-node events.
 * This is independent of the optional derived search-index reconciler.
 *
 * <p>Only the effective Hub metadata is CAS-disabled; immutable Hub version
 * records are retained for audit and cannot become an ordinary Skill ZIP.</p>
 */
@Service
public class HubSkillOrphanReconciliationService {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(HubSkillOrphanReconciliationService.class);
    
    private static final long SCAN_WINDOW = 256L;
    
    private final HubEntryStorage storage;
    
    private final HubSkillReferenceResolver resolver;
    
    private final HubSkillSourceReadValidator sourceValidator;
    
    private final HubSkillPublicationInvalidator invalidator;
    
    /**
     * Create reconciliation independently of the optional search index.
     *
     * @param storage Hub metadata storage
     * @param resolver pinned reference resolver
     * @param sourceValidator source state validator
     * @param invalidator publication invalidator
     */
    public HubSkillOrphanReconciliationService(HubEntryStorage storage,
        HubSkillReferenceResolver resolver, HubSkillSourceReadValidator sourceValidator,
        HubSkillPublicationInvalidator invalidator) {
        this.storage = storage;
        this.resolver = resolver;
        this.sourceValidator = sourceValidator;
        this.invalidator = invalidator;
    }
    
    /** Scheduled on each server; concurrent executions are serialized by the Hub row CAS. */
    @Scheduled(initialDelayString = "${nacos.ai.hub.skill.orphan.initial-delay-ms:60000}",
        fixedDelayString = "${nacos.ai.hub.skill.orphan.reconcile-delay-ms:300000}")
    public void scheduledReconcile() {
        try {
            Report report = reconcile();
            if (report.revoked > 0) {
                LOGGER.warn("Hub orphan reconciliation revoked {} out of {} observed entries",
                    report.revoked, report.examined);
            }
        } catch (Exception failed) {
            LOGGER.error("Hub orphan reconciliation failed; Hub reads remain fail-closed", failed);
        }
    }
    
    /** Manually invocable for admin diagnostics / tests, without exposing raw source identifiers. */
    public Report reconcile() throws NacosException {
        long maxId = storage.maxEntryId();
        long cursor = 0L;
        long examined = 0L;
        long revoked = 0L;
        while (cursor < maxId) {
            Long next = storage.nextEntryId(cursor, maxId);
            if (next == null) {
                break;
            }
            if (next <= cursor || next > maxId) {
                throw new IllegalStateException("Hub orphan scan did not advance");
            }
            long upper = Math.min(maxId, next > Long.MAX_VALUE - (SCAN_WINDOW - 1L)
                ? Long.MAX_VALUE : next + SCAN_WINDOW - 1L);
            List<AiResource> batch = storage.entriesInIdRange(next, upper);
            if (batch == null) {
                throw new IllegalStateException("Hub orphan scan returned null batch");
            }
            long previous = next - 1L;
            for (AiResource entry : batch) {
                if (entry == null || entry.getId() == null || entry.getId() <= previous
                    || entry.getId() > upper) {
                    throw new IllegalStateException("Hub orphan scan returned invalid row order");
                }
                previous = entry.getId();
                examined++;
                if (AiResourceConstants.META_STATUS_ENABLE.equals(entry.getStatus())
                    && invalidator.disableIf(entry.getId(), current -> !isLive(current))) {
                    revoked++;
                }
            }
            cursor = upper;
        }
        return new Report(examined, revoked);
    }
    
    private boolean isLive(AiResource entry) throws NacosException {
        try {
            ResolvedHubSkillReference snapshot = resolver.resolve(entry.getId());
            // A concurrent adjust may leave the scanned row stale. Compare
            // the precise version first and let the CAS retry validate again.
            if (snapshot.getHubMetaVersion() != entry.getMetaVersion()
                || !snapshot.getInternalName().equals(entry.getName())) {
                return false;
            }
            sourceValidator.requireLiveSourceForIndex(snapshot);
            resolver.requireUnchanged(snapshot);
            return true;
        } catch (NacosException absentOrOffline) {
            if (absentOrOffline.getErrCode() == NacosException.NOT_FOUND) {
                return false;
            }
            // An unavailable backend is not proof of an orphan. Stop this scan
            // without permanently withdrawing a potentially healthy publication.
            throw absentOrOffline;
        }
    }
    
    public static final class Report {
        
        private final long examined;
        
        private final long revoked;
        
        /**
         * Create a completed reconciliation report.
         *
         * @param examined observed entries
         * @param revoked publications disabled by this scan
         */
        public Report(long examined, long revoked) {
            this.examined = examined;
            this.revoked = revoked;
        }
        
        public long getExamined() {
            return examined;
        }
        
        public long getRevoked() {
            return revoked;
        }
    }
}
