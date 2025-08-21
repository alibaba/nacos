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

package com.alibaba.nacos.core.namespace.filter;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NamespaceValidationConfig} unit test.
 *
 * @author FangYuan
 * @since 2025-08-13 13:42:00
 */
class NamespaceValidationConfigTest {

    @Test
    void testGetConfigFromEnvWithDefaultValue() throws ReflectiveOperationException {
        MockEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);

        Constructor<NamespaceValidationConfig> declaredConstructor = NamespaceValidationConfig.class.getDeclaredConstructor();
        declaredConstructor.setAccessible(true);
        NamespaceValidationConfig config = declaredConstructor.newInstance();

        assertFalse(config.isNamespaceValidationEnabled());
    }

    @Test
    void testGetConfigFromEnvWithDisabled() throws ReflectiveOperationException {
        MockEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        environment.setProperty("nacos.core.namespace.validation.enabled", String.valueOf(false));

        Constructor<NamespaceValidationConfig> declaredConstructor = NamespaceValidationConfig.class.getDeclaredConstructor();
        declaredConstructor.setAccessible(true);
        NamespaceValidationConfig config = declaredConstructor.newInstance();

        assertFalse(config.isNamespaceValidationEnabled());
    }

    @Test
    void testGetConfigFromEnvWithEnabled() throws ReflectiveOperationException {
        MockEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        environment.setProperty("nacos.core.namespace.validation.enabled", String.valueOf(true));

        Constructor<NamespaceValidationConfig> declaredConstructor = NamespaceValidationConfig.class.getDeclaredConstructor();
        declaredConstructor.setAccessible(true);
        NamespaceValidationConfig config = declaredConstructor.newInstance();

        assertTrue(config.isNamespaceValidationEnabled());
    }

    @Test
    void testPrintConfig() {
        NamespaceValidationConfig config = NamespaceValidationConfig.getInstance();
        String configStr = config.printConfig();

        assertTrue(configStr.contains("NamespaceValidationConfig"));
        assertTrue(configStr.contains("namespaceValidationEnabled"));
    }
}