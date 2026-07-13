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

package com.alibaba.nacos.copilot.config;

import com.alibaba.nacos.common.utils.StringUtils;
import io.agentscope.core.model.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registry of available Copilot model providers.
 *
 * @author nacos
 */
@Component
public class CopilotModelProviderRegistry {
    
    private static final String DEFAULT_PROVIDER = "DashScope";
    
    private final Map<String, CopilotModelProvider> providers;
    
    @Autowired
    public CopilotModelProviderRegistry(List<CopilotModelProvider> providerList) {
        providers = new LinkedHashMap<>();
        for (CopilotModelProvider provider : providerList) {
            String key = normalize(provider.getName());
            if (providers.putIfAbsent(key, provider) != null) {
                throw new IllegalStateException(
                    "Duplicate Copilot provider: " + provider.getName());
            }
        }
    }
    
    public List<CopilotProviderMetadata> getProviderMetadata() {
        return providers.values().stream().map(CopilotModelProvider::getMetadata).toList();
    }
    
    public void validate(CopilotProperties config) {
        resolve(config.getProvider()).validate(config);
    }
    
    /**
     * Create a model from the provider selected in the configuration.
     *
     * @param config Copilot configuration
     * @param apiKey provider API key
     * @return configured AgentScope model
     */
    public Model createModel(CopilotProperties config, String apiKey) {
        CopilotModelProvider provider = resolve(config.getProvider());
        provider.validate(config);
        return provider.createModel(config, apiKey);
    }
    
    private CopilotModelProvider resolve(String name) {
        String effectiveName = StringUtils.isBlank(name) ? DEFAULT_PROVIDER : name;
        CopilotModelProvider provider = providers.get(normalize(effectiveName));
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported Copilot provider: " + effectiveName);
        }
        return provider;
    }
    
    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
