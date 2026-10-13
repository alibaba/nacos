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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.config.ConditionalOnAiResourceSearchEnabled;
import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.hub.HubSkillReferenceResolver;
import com.alibaba.nacos.ai.service.hub.HubSkillSourceReadValidator;
import com.alibaba.nacos.ai.service.hub.ResolvedHubSkillReference;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Map;

/**
 * Reconcile derived Hub search entries with source-of-truth publications.
 * A stale document can never authorize a read: the Hub-specific isCurrent gate
 * independently performs validation on every search result.
 */
@Service
@ConditionalOnAiResourceSearchEnabled
public class HubSkillIndexReconciliationService {
    
    private static final String HUB = AiConstants.Hub.NAMESPACE_ID;
    private static final String SKILL = AiResourceConstants.RESOURCE_TYPE_SKILL;
    private static final int BATCH = 256;
    
    private final HubEntryStorage storage;
    private final AiResourceSearchRepository index;
    private final HubSkillReferenceResolver resolver;
    private final HubSkillSourceReadValidator sourceValidator;
    private AiResourceIndexMaintenanceService maintenance = AiResourceIndexMaintenanceService.NOOP;
    
    private AiResourceIndexService indexService;
    
    public HubSkillIndexReconciliationService(HubEntryStorage storage,
        AiResourceSearchRepository index, HubSkillReferenceResolver resolver,
        HubSkillSourceReadValidator sourceValidator) {
        this.storage = storage;
        this.index = index;
        this.resolver = resolver;
        this.sourceValidator = sourceValidator;
    }
    
    @Autowired(required = false)
    public void setMaintenance(AiResourceIndexMaintenanceService maintenance) {
        if (maintenance != null) {
            this.maintenance = maintenance;
        }
    }
    
    /** Inject the canonical index remover, including optional vector indexes. */
    @Autowired
    public void setIndexService(AiResourceIndexService indexService) {
        this.indexService = indexService;
    }
    
    /** Periodic repair; when Spring scheduling is enabled, it runs every 10 minutes. */
    @Scheduled(initialDelayString = "60000",
        fixedDelayString = "${nacos.ai.hub.skill.index.reconcile-delay-ms:600000}")
    public void reconcile() throws NacosException {
        // 1. Remove orphaned, disabled, tampered, stale-version or source-revoked documents.
        long after = 0L;
        while (true) {
            List<AiResourceSearchDocument> batch = index.scanEntries(HUB,
                Collections.singletonList(SKILL), after, BATCH);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (AiResourceSearchDocument document : batch) {
                if (document == null || document.getId() == null || document.getId() <= after
                    || !HUB.equals(document.getNamespaceId())
                    || !SKILL.equals(document.getResourceType())) {
                    throw new IllegalStateException("Invalid Hub search-index scan order");
                }
                after = document.getId();
                if (!validIndexedPublication(document)) {
                    // This is an index write, not a change to the source or Hub resource.
                    if (indexService == null) {
                        index.deleteByResourceVersion(HUB, SKILL, document.getResourceName(),
                            document.getResourceVersion());
                    } else {
                        indexService.deleteResourceVersion(HUB, SKILL, document.getResourceName(),
                            document.getResourceVersion());
                    }
                }
            }
            if (batch.size() < BATCH) {
                break;
            }
        }
        // 2. Discover valid Hub entries missing from the derived search index.
        long upper = storage.maxEntryId();
        long cursor = 0L;
        while (cursor < upper) {
            Long next = storage.nextEntryId(cursor, upper);
            if (next == null) {
                break;
            }
            if (next <= cursor || next > upper) {
                throw new IllegalStateException("Invalid Hub entry scan order");
            }
            long end = Math.min(upper, next > Long.MAX_VALUE - 255L
                ? Long.MAX_VALUE : next + 255L);
            List<AiResource> entries = storage.entriesInIdRange(next, end);
            if (entries == null) {
                throw new IllegalStateException("Null Hub entry scan");
            }
            for (AiResource entry : entries) {
                if (entry != null && entry.getId() != null && validPublication(entry)) {
                    AiResourceSearchDocument indexed = index.findEntry(HUB, SKILL, entry.getName());
                    if (indexed == null || !validIndexedPublication(indexed)) {
                        maintenance.scheduleReconciliation(HUB, SKILL, entry.getName());
                    }
                }
            }
            cursor = end;
        }
    }
    
    private boolean validPublication(AiResource entry) throws NacosException {
        try {
            ResolvedHubSkillReference reference = resolver.resolve(entry.getId());
            sourceValidator.requireLiveSourceForIndex(reference);
            resolver.requireUnchanged(reference);
            return Objects.equals(entry.getName(), reference.getInternalName());
        } catch (NacosException failure) {
            if (failure.getErrCode() == NacosException.NOT_FOUND) {
                return false;
            }
            throw failure;
        }
    }
    
    private boolean validIndexedPublication(AiResourceSearchDocument document)
        throws NacosException {
        try {
            // A current pending projection may be receiving vectors; keep it for the active task.
            if (!AiResourceSearchConstants.STATUS_ENABLED.equals(document.getStatus())
                && !AiResourceSearchConstants.STATUS_PENDING.equals(document.getStatus())) {
                return false;
            }
            AiResource entry = storage.findByInternalName(document.getResourceName());
            if (entry == null || !validPublication(entry)) {
                return false;
            }
            ResolvedHubSkillReference reference = resolver.resolve(entry.getId());
            return Objects.equals(document.getResourceVersion(), reference.getSourceVersion())
                && Objects.equals(document.getDisplayName(), reference.getSourceName())
                && safeProjection(document, reference);
        } catch (NacosException failure) {
            if (failure.getErrCode() == NacosException.NOT_FOUND) {
                return false;
            }
            throw failure;
        }
    }
    
    private boolean safeProjection(AiResourceSearchDocument document,
        ResolvedHubSkillReference reference) {
        try {
            Map<?, ?> metadata = JacksonUtils.toObj(document.getMetadata(), Map.class);
            return metadata != null && "HUB".equals(metadata.get("origin"))
                && Long.toString(reference.getHubEntryId()).equals(metadata.get("hubEntryId"))
                && Long.toString(reference.getHubMetaVersion())
                    .equals(metadata.get("hubMetaVersion"))
                && !metadata.containsKey("sourceNamespaceId")
                && !metadata.containsKey("sourceResourceId")
                && !metadata.containsKey("sourceVersionId") && !metadata.containsKey("sourceName")
                && !metadata.containsKey("sourceVersion")
                && !metadata.containsKey("hubResourceName");
        } catch (RuntimeException malformed) {
            return false;
        }
    }
}
