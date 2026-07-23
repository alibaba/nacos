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

package com.alibaba.nacos.ai.service.ard;

import com.alibaba.nacos.ai.config.ConditionalOnArdEnabled;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default durable ARD index maintenance scheduler.
 *
 * @author nacos
 */
@Service
@ConditionalOnArdEnabled
public class ArdIndexMaintenanceServiceImpl implements ArdIndexMaintenanceService {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(ArdIndexMaintenanceServiceImpl.class);
    
    private final ArdIndexTaskRepository taskRepository;
    
    public ArdIndexMaintenanceServiceImpl(ArdIndexTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }
    
    @Override
    public boolean schedule(String namespaceId, String resourceType, String resourceName) {
        if (StringUtils.isBlank(resourceType) || StringUtils.isBlank(resourceName)) {
            return false;
        }
        try {
            taskRepository.schedule(namespaceId, resourceType, resourceName);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to schedule ARD index maintenance for {}:{} in namespace {}",
                resourceType, resourceName, namespaceId, e);
            return false;
        }
    }
}
