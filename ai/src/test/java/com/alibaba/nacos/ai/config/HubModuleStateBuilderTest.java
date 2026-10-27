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

import com.alibaba.nacos.sys.env.DeploymentType;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.ModuleStateBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verify SPI discovery and live state reporting use the same effective switch. */
class HubModuleStateBuilderTest {
    
    private ConfigurableEnvironment previous;
    
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        previous = EnvUtil.getEnvironment();
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(previous);
    }
    
    @Test
    void defaultsToEnabledAndReflectsEnvironmentChangesWithoutCachedState() {
        HubModuleStateBuilder builder = new HubModuleStateBuilder();
        assertTrue(HubConfig.isEnabled());
        assertEquals(true, builder.build().getStates().get(HubConfig.ENABLED_STATE));
        assertFalse(builder.isCacheable());
        environment.setProperty(HubConfig.ENABLED_PROPERTY, "false");
        assertFalse(HubConfig.isEnabled());
        assertEquals(false, builder.build().getStates().get(HubConfig.ENABLED_STATE));
        environment.setProperty(HubConfig.ENABLED_PROPERTY, "true");
        assertEquals(true, builder.build().getStates().get(HubConfig.ENABLED_STATE));
    }
    
    @Test
    void retainsCompatibilityDefaultBeforeEnvironmentInitialization() {
        EnvUtil.setEnvironment(null);
        assertTrue(HubConfig.isEnabled());
    }
    
    @Test
    void isSpiRegisteredOnlyForServerDeployments() {
        assertTrue(ServiceLoader.load(ModuleStateBuilder.class).stream()
            .anyMatch(provider -> provider.type() == HubModuleStateBuilder.class));
        HubModuleStateBuilder builder = new HubModuleStateBuilder();
        assertTrue(builder.isMatchDeployment(DeploymentType.SERVER));
        assertTrue(builder.isMatchDeployment(DeploymentType.MERGED));
        assertFalse(builder.isMatchDeployment(DeploymentType.CONSOLE));
    }
    
}
