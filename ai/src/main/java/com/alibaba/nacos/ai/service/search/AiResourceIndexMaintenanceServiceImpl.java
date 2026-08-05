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
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default durable AI resource index maintenance scheduler.
 *
 * @author nacos
 */
@Service
@ConditionalOnAiResourceSearchEnabled
public class AiResourceIndexMaintenanceServiceImpl implements AiResourceIndexMaintenanceService {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(AiResourceIndexMaintenanceServiceImpl.class);
    
    private final AiResourceIndexTaskRepository taskRepository;
    
    private final AiResourceIndexEnhancementService enhancementService;
    
    public AiResourceIndexMaintenanceServiceImpl(AiResourceIndexTaskRepository taskRepository,
        AiResourceIndexEnhancementService enhancementService) {
        this.taskRepository = taskRepository;
        this.enhancementService = enhancementService;
    }
    
    @Override
    public boolean schedule(String namespaceId, String resourceType, String resourceName) {
        if (StringUtils.isBlank(resourceType) || StringUtils.isBlank(resourceName)) {
            return false;
        }
        try {
            taskRepository.schedule(namespaceId, resourceType, resourceName,
                enhancementService.requested());
            return true;
        } catch (Exception e) {
            LOGGER.warn(
                "Failed to schedule AI resource index maintenance for {}:{} in namespace {}",
                resourceType, resourceName, namespaceId, e);
            return false;
        }
    }
    
    @Override
    public boolean scheduleReconciliation(String namespaceId, String resourceType,
        String resourceName) {
        if (StringUtils.isBlank(resourceType) || StringUtils.isBlank(resourceName)) {
            return false;
        }
        try {
            taskRepository.scheduleReconciliation(namespaceId, resourceType, resourceName,
                enhancementService.requested());
            return true;
        } catch (Exception e) {
            LOGGER.warn(
                "Failed to schedule AI resource index reconciliation for {}:{} in namespace {}",
                resourceType, resourceName, namespaceId, e);
            return false;
        }
    }
}
