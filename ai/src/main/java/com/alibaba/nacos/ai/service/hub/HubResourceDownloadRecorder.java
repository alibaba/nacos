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

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Record a successful shared transfer against Hub rows only; never dispatch Skill-only events. */
@Service
public class HubResourceDownloadRecorder {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(HubResourceDownloadRecorder.class);
    
    private final HubEntryStorage storage;
    
    private final AiResourceVersionPersistService versions;
    
    public HubResourceDownloadRecorder(HubEntryStorage storage,
        AiResourceVersionPersistService versions) {
        this.storage = storage;
        this.versions = versions;
    }
    
    /** Count one completed content response, including its exact historical reference version. */
    public void record(HubResourceType type, long entryId, String version) {
        try {
            AiResource entry = storage.findByEntryId(type, entryId);
            if (entry == null) {
                return;
            }
            AiResourceVersion row = versions.find(AiConstants.Hub.NAMESPACE_ID, entry.getName(),
                type.resourceType(), version);
            if (row == null || row.getId() == null) {
                return;
            }
            if (!storage.incrementDownloads(type, entryId, row.getId(), entry.getName(), 1)) {
                LOGGER.warn(
                    "Shared transfer completed but Hub count identity no longer exists, entryId={}",
                    entryId);
            }
        } catch (RuntimeException unavailable) {
            // Never turn already-loaded content into an apparent transfer failure, prompting a
            // user retry. Both counters are committed together or rolled back by the storage.
            LOGGER.warn("Shared transfer completed but Hub count could not be recorded, entryId={}",
                entryId, unavailable);
        }
    }
}
