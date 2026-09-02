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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.search.AgentSearchIndexProjector;
import com.alibaba.nacos.ai.service.search.AiResourceSearchReadinessService;
import com.alibaba.nacos.ai.service.search.AiResourceSearchRepository;
import com.alibaba.nacos.ai.service.search.AiResourceSearchTypeHandlerRegistry;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
@Component
public class A2aMigrationSearchReadinessGate {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(A2aMigrationSearchReadinessGate.class);
    
    private AiResourceSearchReadinessService readinessService;
    
    private AiResourceSearchRepository repository;
    
    private AiResourceSearchTypeHandlerRegistry typeHandlerRegistry;
    
    @Autowired(required = false)
    public void setReadinessService(AiResourceSearchReadinessService readinessService) {
        this.readinessService = readinessService;
    }
    
    @Autowired(required = false)
    public void setRepository(AiResourceSearchRepository repository) {
        this.repository = repository;
    }
    
    @Autowired(required = false)
    public void setTypeHandlerRegistry(
        AiResourceSearchTypeHandlerRegistry typeHandlerRegistry) {
        this.typeHandlerRegistry = typeHandlerRegistry;
    }
    
    /**
     * Verify Search readiness and the current persisted document for every historical Agent.
     *
     * @param sourceNames complete historical identities by Namespace
     * @return whether the one-time Search cutover gate is satisfied
     */
    public boolean isReady(Map<String, Set<String>> sourceNames) {
        if (!searchEnabled()) {
            return true;
        }
        if (sourceNames == null) {
            return false;
        }
        if (sourceNames.values().stream().allMatch(Set::isEmpty)) {
            return true;
        }
        if (readinessService == null || repository == null || typeHandlerRegistry == null
            || !readinessService.isReady(Constants.Agent.RESOURCE_TYPE_AGENT,
                AgentSearchIndexProjector.PROJECTION_VERSION)) {
            return false;
        }
        try {
            for (Map.Entry<String, Set<String>> namespace : sourceNames.entrySet()) {
                for (String agentName : namespace.getValue()) {
                    AiResourceSearchDocument document = repository.findEntry(
                        namespace.getKey(), Constants.Agent.RESOURCE_TYPE_AGENT, agentName);
                    if (document == null || !typeHandlerRegistry.isCurrent(document)) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to validate migrated Agent Search projections", e);
            return false;
        }
    }
    
    private boolean searchEnabled() {
        return Boolean.parseBoolean(EnvUtil.getProperty(
            Constants.AI_RESOURCE_SEARCH_ENABLED_KEY, "true"));
    }
}
