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

package com.alibaba.nacos.plugin.ai.pipeline.spi.impl;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SkillScannerPluginConfig} unit test.
 *
 * @author Nacos
 */
class SkillScannerPluginConfigTest {
    
    @Test
    void defaultsTest() {
        SkillScannerPluginConfig config = SkillScannerPluginConfig.fromMap(null);
        Map<String, String> values = config.toMap();
        
        assertEquals("skill-scanner", config.getCommand());
        assertEquals(SkillScannerPluginConfig.DEFAULT_ORDER, config.getOrder());
        assertEquals("100", values.get(SkillScannerPluginConfig.ORDER));
        assertEquals("skill-scanner", values.get(SkillScannerPluginConfig.COMMAND));
        assertEquals("false", values.get(SkillScannerPluginConfig.USE_LLM));
        assertEquals("", values.get(SkillScannerPluginConfig.LLM_API_KEY));
        assertEquals("", values.get(SkillScannerPluginConfig.LLM_MODEL));
        assertEquals("", values.get(SkillScannerPluginConfig.LLM_PROVIDER));
        assertEquals("false", values.get(SkillScannerPluginConfig.ENABLE_META));
        assertFalse(config.getScanOptions().isUseLlm());
    }
    
    @Test
    void legacyAliasesTest() {
        Properties properties = new Properties();
        properties.setProperty(SkillScannerPluginConfig.COMMAND_ALIAS_PATH, " /tmp/scanner ");
        properties.setProperty(SkillScannerPluginConfig.USE_LLM_ALIAS, "true");
        properties.setProperty(SkillScannerPluginConfig.LLM_API_KEY_ALIAS, " api-key ");
        properties.setProperty(SkillScannerPluginConfig.LLM_MODEL_ALIAS, " model ");
        properties.setProperty(SkillScannerPluginConfig.LLM_PROVIDER_ALIAS, " provider ");
        properties.setProperty(SkillScannerPluginConfig.ENABLE_META_ALIAS, "true");
        properties.setProperty(SkillScannerPluginConfig.ORDER, "42");
        
        SkillScannerPluginConfig config = SkillScannerPluginConfig.fromMap(
            PluginConfigTestUtils.toMap(properties));
        Map<String, String> values = config.toMap();
        
        assertEquals("/tmp/scanner", values.get(SkillScannerPluginConfig.COMMAND));
        assertEquals("true", values.get(SkillScannerPluginConfig.USE_LLM));
        assertEquals("api-key", values.get(SkillScannerPluginConfig.LLM_API_KEY));
        assertEquals("model", values.get(SkillScannerPluginConfig.LLM_MODEL));
        assertEquals("provider", values.get(SkillScannerPluginConfig.LLM_PROVIDER));
        assertEquals("true", values.get(SkillScannerPluginConfig.ENABLE_META));
        assertEquals(42, config.getOrder());
        assertEquals("42", values.get(SkillScannerPluginConfig.ORDER));
        assertTrue(config.getScanOptions().isUseLlm());
        assertTrue(config.getScanOptions().isEnableMeta());
        
        Properties executableAlias = new Properties();
        executableAlias.setProperty(SkillScannerPluginConfig.COMMAND_ALIAS_EXECUTABLE,
            "/tmp/executable-scanner");
        assertEquals("/tmp/executable-scanner",
            SkillScannerPluginConfig.fromMap(PluginConfigTestUtils.toMap(executableAlias))
                .getCommand());
    }
    
    @Test
    void canonicalValuesTakePriorityTest() {
        Properties properties = new Properties();
        properties.setProperty(SkillScannerPluginConfig.COMMAND, "canonical");
        properties.setProperty(SkillScannerPluginConfig.COMMAND_ALIAS_EXECUTABLE, "alias");
        properties.setProperty(SkillScannerPluginConfig.USE_LLM, "false");
        properties.setProperty(SkillScannerPluginConfig.USE_LLM_ALIAS, "true");
        properties.setProperty(SkillScannerPluginConfig.LLM_API_KEY, " ");
        properties.setProperty(SkillScannerPluginConfig.LLM_API_KEY_ALIAS, "alias-key");
        
        Map<String, String> values =
            SkillScannerPluginConfig.fromMap(PluginConfigTestUtils.toMap(properties)).toMap();
        
        assertEquals("canonical", values.get(SkillScannerPluginConfig.COMMAND));
        assertEquals("false", values.get(SkillScannerPluginConfig.USE_LLM));
        assertEquals("", values.get(SkillScannerPluginConfig.LLM_API_KEY));
    }
    
    @Test
    void fromMapIgnoresNullEntriesTest() {
        Map<String, String> source = new HashMap<>();
        source.put(null, "ignored");
        source.put(SkillScannerPluginConfig.COMMAND, null);
        source.put(SkillScannerPluginConfig.LLM_MODEL, " model ");
        
        Map<String, String> values = SkillScannerPluginConfig.fromMap(source).toMap();
        Map<String, String> defaults = SkillScannerPluginConfig.fromMap(null).toMap();
        
        assertEquals("skill-scanner", values.get(SkillScannerPluginConfig.COMMAND));
        assertEquals("model", values.get(SkillScannerPluginConfig.LLM_MODEL));
        assertEquals("skill-scanner", defaults.get(SkillScannerPluginConfig.COMMAND));
    }
    
    @Test
    void invalidOrderShouldBeRejectedTest() {
        Map<String, String> config = new HashMap<>();
        config.put(SkillScannerPluginConfig.ORDER, "1.5");
        assertThrows(ArithmeticException.class,
            () -> SkillScannerPluginConfig.fromMap(config));
        config.put(SkillScannerPluginConfig.ORDER, "invalid");
        assertThrows(NumberFormatException.class,
            () -> SkillScannerPluginConfig.fromMap(config));
    }
}
