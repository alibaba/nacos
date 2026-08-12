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

package com.alibaba.nacos.ai.storage;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiResourceStorageUtilsTest {
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
    }
    
    @AfterEach
    void tearDown() {
        System.clearProperty(Constants.AI_STORAGE_PROVIDER_CONFIG_KEY);
        System.clearProperty(Constants.Skills.SKILL_STORAGE_PROVIDER_CONFIG_KEY);
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void testResolveProviderUsesGlobalConfiguration() {
        System.setProperty(Constants.AI_STORAGE_PROVIDER_CONFIG_KEY, " external ");
        
        assertEquals("external", AiResourceStorageUtils.resolveProvider(
            Constants.Skills.SKILL_STORAGE_PROVIDER_CONFIG_KEY,
            NacosConfigAiResourceStorage.TYPE));
    }
    
    @Test
    void testResolveProviderPrefersResourceCompatibilityConfiguration() {
        System.setProperty(Constants.AI_STORAGE_PROVIDER_CONFIG_KEY, "global-store");
        System.setProperty(Constants.Skills.SKILL_STORAGE_PROVIDER_CONFIG_KEY, "skill-store");
        
        assertEquals("skill-store", AiResourceStorageUtils.resolveProvider(
            Constants.Skills.SKILL_STORAGE_PROVIDER_CONFIG_KEY,
            NacosConfigAiResourceStorage.TYPE));
    }
    
    @Test
    void testResolveProviderUsesDefault() {
        assertEquals(NacosConfigAiResourceStorage.TYPE,
            AiResourceStorageUtils.resolveProvider(
                Constants.Skills.SKILL_STORAGE_PROVIDER_CONFIG_KEY,
                NacosConfigAiResourceStorage.TYPE));
    }
}
