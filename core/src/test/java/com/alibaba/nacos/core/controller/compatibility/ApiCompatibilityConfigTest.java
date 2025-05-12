/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.controller.compatibility;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiCompatibilityConfigTest {
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
        ApiCompatibilityConfig config = ApiCompatibilityConfig.getInstance();
        config.setConsoleApiCompatibility(false);
        config.setClientApiCompatibility(true);
        config.setAdminApiCompatibility(true);
    }
    
    @Test
    void testGetInstance() {
        ApiCompatibilityConfig instance1 = ApiCompatibilityConfig.getInstance();
        ApiCompatibilityConfig instance2 = ApiCompatibilityConfig.getInstance();
        assertEquals(instance1, instance2);
    }
    
    @Test
    void testGetConfigWithDefaultValues() {
        ApiCompatibilityConfig config = ApiCompatibilityConfig.getInstance();
        config.getConfigFromEnv();
        assertTrue(config.isClientApiCompatibility());
        assertFalse(config.isConsoleApiCompatibility());
        assertFalse(config.isAdminApiCompatibility());
        assertEquals(
                "ApiCompatibilityConfig{clientApiCompatibility=true, consoleApiCompatibility=false, adminApiCompatibility=false}",
                config.printConfig());
    }
    
    @Test
    void testGetConfigWithCustomValues() {
        MockEnvironment properties = new MockEnvironment();
        properties.setProperty(ApiCompatibilityConfig.CLIENT_API_COMPATIBILITY_KEY, "false");
        properties.setProperty(ApiCompatibilityConfig.CONSOLE_API_COMPATIBILITY_KEY, "true");
        properties.setProperty(ApiCompatibilityConfig.ADMIN_API_COMPATIBILITY_KEY, "true");
        EnvUtil.setEnvironment(properties);
        
        ApiCompatibilityConfig config = ApiCompatibilityConfig.getInstance();
        config.getConfigFromEnv();
        
        assertFalse(config.isClientApiCompatibility());
        assertTrue(config.isConsoleApiCompatibility());
        assertTrue(config.isAdminApiCompatibility());
        assertEquals(
                "ApiCompatibilityConfig{clientApiCompatibility=false, consoleApiCompatibility=true, adminApiCompatibility=true}",
                config.printConfig());
    }
}