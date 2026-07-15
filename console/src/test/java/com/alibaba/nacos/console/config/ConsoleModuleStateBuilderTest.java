/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.ModuleState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleModuleStateBuilderTest {
    
    private ConfigurableEnvironment cachedEnvironment;
    
    private ConsoleModuleStateBuilder builder;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        MockEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        builder = new ConsoleModuleStateBuilder();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void build() {
        ModuleState state = builder.build();
        assertTrue((Boolean) state.getStates().get("console_ui_enabled"));
        assertEquals("next", state.getStates().get("console_ui_default"));
        assertTrue((Boolean) state.getStates().get("ai_enabled"));
        assertEquals(ConsoleModuleStateBuilder.CONSOLE_MODULE, state.getModuleName());
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.console.ui.enabled", "false");
        environment.setProperty("nacos.extension.ai.enabled", "false");
        EnvUtil.setEnvironment(environment);
        ModuleState disabledState = builder.build();
        assertFalse((Boolean) disabledState.getStates().get("console_ui_enabled"));
        assertFalse((Boolean) disabledState.getStates().get("ai_enabled"));
    }
    
    @Test
    void buildWithException() {
        try (MockedStatic<EnvUtil> mockedEnvUtil = Mockito.mockStatic(EnvUtil.class)) {
            mockedEnvUtil.when(() -> EnvUtil.getProperty("nacos.console.ui.enabled",
                Boolean.class, true)).thenThrow(new IllegalStateException("Mock exception"));
            ModuleState state = builder.build();
            assertEquals(ConsoleModuleStateBuilder.CONSOLE_MODULE, state.getModuleName());
            assertTrue(state.getStates().isEmpty());
        }
    }
}
