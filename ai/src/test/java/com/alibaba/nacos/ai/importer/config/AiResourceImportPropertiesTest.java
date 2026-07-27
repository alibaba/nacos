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

package com.alibaba.nacos.ai.importer.config;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResourceImportPropertiesTest {
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testStandardSwitchTakesPrecedenceAndLoadsOtherModuleFlags() {
        Properties raw = new Properties();
        raw.setProperty(AiResourceImportProperties.ENABLED_PROPERTY, " true ");
        raw.setProperty(AiResourceImportProperties.LEGACY_ENABLED_PROPERTY, "false");
        raw.setProperty(AiResourceImportProperties.LEGACY_MCP_API_ENABLED_PROPERTY, "true");
        raw.setProperty(AiResourceImportProperties.ALLOW_USER_URL_PROPERTY, " true ");
        
        AiResourceImportProperties properties = AiResourceImportProperties.load(raw);
        
        assertTrue(properties.isEnabled());
        assertTrue(properties.isLegacyMcpImportApiEnabled());
        assertTrue(properties.isAllowUserUrl());
    }
    
    @Test
    void testLegacySwitchAndDefaults() {
        Properties raw = new Properties();
        raw.setProperty(AiResourceImportProperties.LEGACY_ENABLED_PROPERTY, "true");
        assertTrue(AiResourceImportProperties.resolveEnabled(raw));
        assertTrue(AiResourceImportProperties.load(raw).isEnabled());
        
        assertFalse(AiResourceImportProperties.resolveEnabled(null));
        AiResourceImportProperties defaults = AiResourceImportProperties.load(null);
        assertFalse(defaults.isEnabled());
        assertFalse(defaults.isLegacyMcpImportApiEnabled());
        assertFalse(defaults.isAllowUserUrl());
    }
    
    @Test
    void testLoadFromEnvironmentAndAccessors() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty(AiResourceImportProperties.ENABLED_PROPERTY, "true");
        EnvUtil.setEnvironment(environment);
        
        AiResourceImportProperties properties =
            AiResourceImportProperties.loadFromEnvironment();
        assertTrue(properties.isEnabled());
        
        properties.setEnabled(false);
        properties.setLegacyMcpImportApiEnabled(true);
        properties.setAllowUserUrl(true);
        assertFalse(properties.isEnabled());
        assertTrue(properties.isLegacyMcpImportApiEnabled());
        assertTrue(properties.isAllowUserUrl());
    }
}
