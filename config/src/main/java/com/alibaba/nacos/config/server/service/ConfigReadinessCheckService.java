/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.core.cluster.health.AbstractModuleHealthChecker;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.stereotype.Service;

/**
 * Readiness check service for config module.
 *
 * @author xiweng.yy
 */
@Service
public class ConfigReadinessCheckService extends AbstractModuleHealthChecker {
    
    private final ConfigInfoPersistService configInfoPersistService;
    
    public ConfigReadinessCheckService(ConfigInfoPersistService configInfoPersistService) {
        this.configInfoPersistService = configInfoPersistService;
    }
    
    @Override
    public boolean readiness() {
        // check db
        try {
            configInfoPersistService.configInfoCount("");
            return true;
        } catch (Exception e) {
            Loggers.CLUSTER.error("Config health check fail.", e);
        }
        return false;
    }
    
    @Override
    public String getModuleName() {
        return Constants.Config.CONFIG_MODULE;
    }
}
