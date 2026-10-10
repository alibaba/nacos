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
import com.alibaba.nacos.ai.service.search.AiResourceIndexMaintenanceService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Schedules index maintenance after a successful Hub metadata CAS.
 *
 * <p>Indexing is a derived, eventually consistent projection. A scheduler failure
 * must never turn a committed share/unshare into a reported transaction failure.
 * Periodic Hub namespace reconciliation repairs missed schedules.</p>
 */
@Service
public class HubSkillIndexNotifier {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(HubSkillIndexNotifier.class);
    
    private AiResourceIndexMaintenanceService maintenanceService =
            AiResourceIndexMaintenanceService.NOOP;
    
    /**
     * Injects the optional index maintenance runtime without requiring search to be enabled.
     *
     * @param service index maintenance scheduler
     */
    @Autowired(required = false)
    public void setMaintenanceService(AiResourceIndexMaintenanceService service) {
        if (service != null) {
            maintenanceService = service;
        }
    }
    
    /**
     * Schedule re-projection of the canonical current Hub Skill (or deletion if inactive).
     *
     * @param internalName stable Hub internal resource name
     */
    public void entryChanged(String internalName) {
        try {
            maintenanceService.schedule(AiConstants.Hub.NAMESPACE_ID,
                    AiResourceConstants.RESOURCE_TYPE_SKILL, internalName);
        } catch (RuntimeException failure) {
            LOGGER.warn("Hub Skill index schedule failed for {}", internalName, failure);
        }
    }
}
