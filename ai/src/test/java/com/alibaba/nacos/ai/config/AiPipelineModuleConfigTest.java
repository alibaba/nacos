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

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AiPipelineModuleConfig}.
 *
 * @author Nacos
 */
class AiPipelineModuleConfigTest {
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void shouldEnablePipelineByDefault() {
        EnvUtil.setEnvironment(new MockEnvironment());
        try (MockedStatic<NotifyCenter> ignored = Mockito.mockStatic(NotifyCenter.class)) {
            AiPipelineModuleConfig config = new AiPipelineModuleConfig();
            assertTrue(config.isEnabled());
            assertEquals("AiPipelineModuleConfig{enabled=true}", config.printConfig());
        }
    }
    
    @Test
    void shouldRefreshModuleSwitchFromEnvironment() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty(AiPipelineModuleConfig.ENABLED_PROPERTY, "false");
        EnvUtil.setEnvironment(environment);
        try (MockedStatic<NotifyCenter> ignored = Mockito.mockStatic(NotifyCenter.class)) {
            AiPipelineModuleConfig config = new AiPipelineModuleConfig();
            assertFalse(config.isEnabled());
            environment.setProperty(AiPipelineModuleConfig.ENABLED_PROPERTY, "true");
            config.getConfigFromEnv();
            assertTrue(config.isEnabled());
        }
    }
}
