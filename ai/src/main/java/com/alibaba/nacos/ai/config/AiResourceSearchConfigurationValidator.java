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

package com.alibaba.nacos.ai.config;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.agent.AgentSearchMode;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Validates cross-feature constraints for the shared AI Resource Search runtime.
 *
 * @author nacos
 */
@Component
public class AiResourceSearchConfigurationValidator {
    
    private final Environment environment;
    
    public AiResourceSearchConfigurationValidator(Environment environment) {
        this.environment = environment;
    }
    
    /**
     * Reject ARD activation without its required shared Search Core.
     */
    @PostConstruct
    public void validate() {
        boolean ardEnabled = environment.getProperty(Constants.ARD_ENABLED_KEY,
            Boolean.class, false);
        boolean searchEnabled = environment.getProperty(Constants.AI_RESOURCE_SEARCH_ENABLED_KEY,
            Boolean.class, true);
        if (ardEnabled && !searchEnabled) {
            throw new IllegalStateException("`" + Constants.ARD_ENABLED_KEY
                + "=true` requires `" + Constants.AI_RESOURCE_SEARCH_ENABLED_KEY + "=true`");
        }
        String radSearchMode = environment.getProperty(
            Constants.Agent.RAD_SEARCH_MODE_CONFIG_KEY, AgentSearchMode.AUTO.name());
        try {
            AgentSearchMode.parse(radSearchMode);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalStateException("`" + Constants.Agent.RAD_SEARCH_MODE_CONFIG_KEY
                + "` must be AUTO, INDEX, or SCAN");
        }
    }
}
