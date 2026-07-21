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
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import com.alibaba.nacos.api.plugin.PluginTypePolicy;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * AI storage plugin type policy.
 *
 * @author Nacos
 */
public class AiStoragePluginTypePolicy implements PluginTypePolicy {
    
    private static final String FUNCTION_MODE_PROPERTY = "nacos.functionMode";
    
    private static final String FUNCTION_MODE_AI = "ai";
    
    private Set<String> requiredPluginNames =
        Collections.singleton(NacosConfigAiResourceStorage.TYPE);
    
    private boolean supportedMode = true;
    
    @Override
    public void initialize(PluginTypeConfiguration configuration) {
        String functionMode = configuration.getProperty(FUNCTION_MODE_PROPERTY);
        supportedMode = StringUtils.isEmpty(functionMode) || FUNCTION_MODE_AI.equals(functionMode);
        Set<String> result = new LinkedHashSet<>();
        result.add(resolveProvider(configuration,
            Constants.Prompt.PROMPT_STORAGE_PROVIDER_CONFIG_KEY));
        result.add(resolveProvider(configuration,
            Constants.Skills.SKILL_STORAGE_PROVIDER_CONFIG_KEY));
        result.add(resolveProvider(configuration,
            Constants.AgentSpecs.AGENTSPEC_STORAGE_PROVIDER_CONFIG_KEY));
        requiredPluginNames = Collections.unmodifiableSet(result);
    }
    
    @Override
    public PluginType getPluginType() {
        return PluginType.AI_STORAGE;
    }
    
    @Override
    public boolean isActive(PluginTypeConfiguration configuration) {
        return supportedMode
            && configuration.getBooleanProperty(AiEnabledFilter.AI_ENABLED_KEY, true);
    }
    
    @Override
    public boolean supportsPreRefreshValidation() {
        return false;
    }
    
    @Override
    public Set<String> getRequiredPluginNames(PluginTypeConfiguration configuration) {
        return requiredPluginNames;
    }
    
    @Override
    public String getSelectionProperty() {
        return Constants.Prompt.PROMPT_STORAGE_PROVIDER_CONFIG_KEY + ", "
            + Constants.Skills.SKILL_STORAGE_PROVIDER_CONFIG_KEY + ", "
            + Constants.AgentSpecs.AGENTSPEC_STORAGE_PROVIDER_CONFIG_KEY;
    }
    
    @Override
    public String getActivationDescription() {
        return "the AI module requires the configured Prompt, Skill, and AgentSpec storage providers";
    }
    
    private String resolveProvider(PluginTypeConfiguration configuration, String property) {
        String provider = configuration.getProperty(property);
        return StringUtils.isBlank(provider) ? NacosConfigAiResourceStorage.TYPE : provider.trim();
    }
}
