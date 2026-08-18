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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.search.vector.AiResourceVectorIndexRouter;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import com.alibaba.nacos.api.plugin.PluginTypePolicy;
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * AI resource vector index plugin type policy.
 *
 * @author nacos
 */
public class AiVectorPluginTypePolicy implements PluginTypePolicy {
    
    @Override
    public PluginType getPluginType() {
        return PluginType.AI_VECTOR;
    }
    
    @Override
    public boolean isActive(PluginTypeConfiguration configuration) {
        return configuration.getBooleanProperty(Constants.AI_RESOURCE_SEARCH_ENABLED_KEY, true);
    }
    
    @Override
    public boolean isPluginEnabledByDefault(String pluginName,
        PluginTypeConfiguration configuration) {
        String implementationProperty =
            "nacos.plugin.ai-vector." + pluginName + ".enabled";
        if (configuration.containsProperty(implementationProperty)) {
            return configuration.getBooleanProperty(implementationProperty, true);
        }
        String provider = configuration.getProperty(AiResourceVectorIndexRouter.KEY_VECTOR_PROVIDER,
            AiResourceVectorIndexRouter.DEFAULT_VECTOR_PROVIDER);
        return pluginName.equals(StringUtils.isBlank(provider)
            ? AiResourceVectorIndexRouter.DEFAULT_VECTOR_PROVIDER : provider.trim());
    }
    
    @Override
    public String getSelectionProperty() {
        return AiResourceVectorIndexRouter.KEY_VECTOR_PROVIDER;
    }
    
    @Override
    public String getActivationDescription() {
        return "AI resource search uses the configured vector provider when available";
    }
}
