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

package com.alibaba.nacos.ai.plugin;

import com.alibaba.nacos.ai.config.AiEnabledFilter;
import com.alibaba.nacos.ai.config.AiPipelineModuleConfig;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import com.alibaba.nacos.api.plugin.PluginTypePolicy;
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * AI pipeline plugin type policy.
 *
 * @author Nacos
 */
public class AiPipelinePluginTypePolicy implements PluginTypePolicy {
    
    private static final String FUNCTION_MODE_PROPERTY = "nacos.functionMode";
    
    private static final String FUNCTION_MODE_AI = "ai";
    
    /**
     * Legacy AI pipeline startup chain property.
     *
     * @deprecated use per-implementation AI pipeline state instead. Planned for removal in Nacos
     *     4.0.0.
     */
    @Deprecated
    private static final String AI_PIPELINE_TYPE_PROPERTY = "nacos.plugin.ai-pipeline.type";
    
    @Override
    public PluginType getPluginType() {
        return PluginType.AI_PIPELINE;
    }
    
    @Override
    public boolean isLoadingEnabled(PluginTypeConfiguration configuration) {
        String functionMode = configuration.getProperty(FUNCTION_MODE_PROPERTY);
        boolean supportedMode = StringUtils.isEmpty(functionMode)
            || FUNCTION_MODE_AI.equals(functionMode);
        return supportedMode
            && configuration.getBooleanProperty(AiEnabledFilter.AI_ENABLED_KEY, true)
            && configuration.getBooleanProperty(AiPipelineModuleConfig.ENABLED_PROPERTY, true);
    }
    
    @Override
    public boolean isPluginEnabledByDefault(String pluginName,
        PluginTypeConfiguration configuration) {
        String implementationProperty =
            "nacos.plugin.ai-pipeline." + pluginName + ".enabled";
        if (configuration.containsProperty(implementationProperty)) {
            return configuration.getBooleanProperty(implementationProperty, true);
        }
        String configuredTypes = configuration.getProperty(AI_PIPELINE_TYPE_PROPERTY);
        if (StringUtils.isBlank(configuredTypes)) {
            return false;
        }
        for (String each : configuredTypes.split(",")) {
            if (pluginName.equals(each.trim())) {
                return true;
            }
        }
        return false;
    }
}
