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

package com.alibaba.nacos.core.control;

import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SpringValueConfigsInitializer} unit test.
 *
 * @author shiyiyue
 */
class SpringValueConfigsInitializerTest {
    
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testInitializeWithDefaultProperties() {
        ControlConfigs configs = new ControlConfigs();
        SpringValueConfigsInitializer initializer = new SpringValueConfigsInitializer();
        initializer.initialize(configs);
        assertEquals("nacos", configs.getConnectionRuntimeEjector());
        assertEquals(EnvUtil.getNacosHome(), configs.getLocalRuleStorageBaseDir());
    }
    
    @Test
    void testInitializeWithCustomConnectionRuntimeEjector() {
        environment.setProperty("nacos.plugin.control.connection.runtime.ejector", "custom");
        ControlConfigs configs = new ControlConfigs();
        SpringValueConfigsInitializer initializer = new SpringValueConfigsInitializer();
        initializer.initialize(configs);
        assertEquals("custom", configs.getConnectionRuntimeEjector());
    }
    
    @Test
    void testInitializeWithLocalRuleStorageBaseDir() {
        environment.setProperty("nacos.plugin.control.rule.local.basedir", "/custom/rules");
        ControlConfigs configs = new ControlConfigs();
        SpringValueConfigsInitializer initializer = new SpringValueConfigsInitializer();
        initializer.initialize(configs);
        assertEquals("/custom/rules", configs.getLocalRuleStorageBaseDir());
    }
    
    @Test
    void testInitializeWithRuleExternalStorageAndControlManagerType() {
        environment.setProperty("nacos.plugin.control.rule.external.storage", "mysql");
        environment.setProperty("nacos.plugin.control.manager.type", "local");
        ControlConfigs configs = new ControlConfigs();
        SpringValueConfigsInitializer initializer = new SpringValueConfigsInitializer();
        initializer.initialize(configs);
        assertEquals("mysql", configs.getRuleExternalStorage());
        assertEquals("local", configs.getControlManagerType());
    }
}
