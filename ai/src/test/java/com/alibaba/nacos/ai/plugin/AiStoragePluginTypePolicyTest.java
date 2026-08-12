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
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiStoragePluginTypePolicyTest {
    
    private final AiStoragePluginTypePolicy policy = new AiStoragePluginTypePolicy();
    
    @Test
    void testTypeAndDiagnostics() {
        assertEquals(PluginType.AI_STORAGE, policy.getPluginType());
        assertFalse(policy.supportsPreRefreshValidation());
        assertTrue(policy.getActivationDescription().contains("AI Resource"));
        assertTrue(policy.getSelectionProperty()
            .contains(Constants.AI_STORAGE_PROVIDER_CONFIG_KEY));
        assertTrue(policy.getSelectionProperty()
            .contains(Constants.Prompt.PROMPT_STORAGE_PROVIDER_CONFIG_KEY));
        assertTrue(policy.getSelectionProperty()
            .contains(Constants.Skills.SKILL_STORAGE_PROVIDER_CONFIG_KEY));
        assertTrue(policy.getSelectionProperty()
            .contains(Constants.AgentSpecs.AGENTSPEC_STORAGE_PROVIDER_CONFIG_KEY));
        assertTrue(policy.getSelectionProperty()
            .contains(Constants.Agent.AGENT_STORAGE_PROVIDER_CONFIG_KEY));
    }
    
    @Test
    void testActiveByFunctionModeAndModuleSwitch() {
        MapConfiguration configuration = new MapConfiguration();
        policy.initialize(configuration);
        assertTrue(policy.isActive(configuration));
        
        configuration.setProperty(AiEnabledFilter.AI_ENABLED_KEY, "false");
        assertFalse(policy.isActive(configuration));
        configuration.setProperty(AiEnabledFilter.AI_ENABLED_KEY, "true");
        assertTrue(policy.isActive(configuration));
        configuration.setProperty("nacos.functionMode", "config");
        assertTrue(policy.isActive(configuration));
        
        AiStoragePluginTypePolicy configOnlyPolicy = new AiStoragePluginTypePolicy();
        configOnlyPolicy.initialize(configuration);
        assertFalse(configOnlyPolicy.isActive(configuration));
        
        configuration.setProperty("nacos.functionMode", "ai");
        AiStoragePluginTypePolicy aiOnlyPolicy = new AiStoragePluginTypePolicy();
        aiOnlyPolicy.initialize(configuration);
        assertTrue(aiOnlyPolicy.isActive(configuration));
    }
    
    @Test
    void testDefaultRequiredProviderIsDeduplicated() {
        MapConfiguration configuration = new MapConfiguration();
        policy.initialize(configuration);
        Set<String> required = policy.getRequiredPluginNames(configuration);
        
        assertEquals(1, required.size());
        assertTrue(required.contains(NacosConfigAiResourceStorage.TYPE));
    }
    
    @Test
    void testGlobalRequiredProvider() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty(Constants.AI_STORAGE_PROVIDER_CONFIG_KEY, " external ");
        policy.initialize(configuration);
        
        assertEquals(Set.of("external"), policy.getRequiredPluginNames(configuration));
    }
    
    @Test
    void testResourceOverrideAndGlobalFallback() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty(Constants.AI_STORAGE_PROVIDER_CONFIG_KEY, "global-store");
        configuration.setProperty(Constants.Prompt.PROMPT_STORAGE_PROVIDER_CONFIG_KEY,
            "prompt-store");
        policy.initialize(configuration);
        
        assertEquals(Set.of("global-store", "prompt-store"),
            policy.getRequiredPluginNames(configuration));
    }
    
    @Test
    void testRequiredProvidersByResourceDomain() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty(Constants.AI_STORAGE_PROVIDER_CONFIG_KEY,
            "global-store");
        configuration.setProperty(Constants.Prompt.PROMPT_STORAGE_PROVIDER_CONFIG_KEY,
            " prompt-store ");
        configuration.setProperty(Constants.Skills.SKILL_STORAGE_PROVIDER_CONFIG_KEY,
            "skill-store");
        configuration.setProperty(Constants.AgentSpecs.AGENTSPEC_STORAGE_PROVIDER_CONFIG_KEY,
            "agentspec-store");
        configuration.setProperty(Constants.Agent.AGENT_STORAGE_PROVIDER_CONFIG_KEY,
            "agent-store");
        policy.initialize(configuration);
        
        Set<String> required = policy.getRequiredPluginNames(configuration);
        assertEquals(4, required.size());
        assertTrue(required.contains("prompt-store"));
        assertTrue(required.contains("skill-store"));
        assertTrue(required.contains("agentspec-store"));
        assertTrue(required.contains("agent-store"));
        assertFalse(required.contains("global-store"));
        configuration.setProperty(Constants.Prompt.PROMPT_STORAGE_PROVIDER_CONFIG_KEY,
            "changed-store");
        assertFalse(policy.getRequiredPluginNames(configuration).contains("changed-store"));
    }
    
    private static class MapConfiguration implements PluginTypeConfiguration {
        
        private final Map<String, String> properties = new HashMap<>();
        
        void setProperty(String key, String value) {
            properties.put(key, value);
        }
        
        @Override
        public String getProperty(String key) {
            return properties.get(key);
        }
        
        @Override
        public boolean containsProperty(String key) {
            return properties.containsKey(key);
        }
    }
}
