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
 * {@link SkillSpectorPluginConfig} unit test.
 *
 * @author Nacos
 */
class SkillSpectorPluginConfigTest {
    
    @Test
    void defaultsTest() {
        SkillSpectorPluginConfig config = SkillSpectorPluginConfig.fromMap(null);
        Map<String, String> values = config.toMap();
        
        assertEquals("skill-spector", config.getCommand());
        assertEquals(SkillSpectorPluginConfig.DEFAULT_ORDER, config.getOrder());
        assertEquals("90", values.get(SkillSpectorPluginConfig.ORDER));
        assertEquals("skill-spector", values.get(SkillSpectorPluginConfig.COMMAND));
        assertEquals("false", values.get(SkillSpectorPluginConfig.USE_LLM));
        assertEquals("", values.get(SkillSpectorPluginConfig.PROVIDER));
        assertEquals("", values.get(SkillSpectorPluginConfig.MODEL));
        assertEquals("", values.get(SkillSpectorPluginConfig.API_KEY));
        assertEquals("", values.get(SkillSpectorPluginConfig.BASE_URL));
        assertEquals("WARNING", values.get(SkillSpectorPluginConfig.LOG_LEVEL));
        assertEquals("50", values.get(SkillSpectorPluginConfig.RISK_SCORE_THRESHOLD));
        assertEquals("20", values.get(SkillSpectorPluginConfig.MAX_FINDINGS));
        assertFalse(config.getScanOptions().isUseLlm());
    }
    
    @Test
    void legacyAliasesTest() {
        Properties properties = new Properties();
        properties.setProperty(SkillSpectorPluginConfig.COMMAND_ALIAS_PATH, " /tmp/spector ");
        properties.setProperty(SkillSpectorPluginConfig.USE_LLM_ALIAS, "true");
        properties.setProperty(SkillSpectorPluginConfig.PROVIDER, " openai ");
        properties.setProperty(SkillSpectorPluginConfig.MODEL, " model ");
        properties.setProperty(SkillSpectorPluginConfig.API_KEY_ALIAS, " api-key ");
        properties.setProperty(SkillSpectorPluginConfig.BASE_URL_ALIAS, " https://example.com ");
        properties.setProperty(SkillSpectorPluginConfig.LOG_LEVEL_ALIAS, " DEBUG ");
        properties.setProperty(SkillSpectorPluginConfig.RISK_SCORE_THRESHOLD_ALIAS, "80");
        properties.setProperty(SkillSpectorPluginConfig.MAX_FINDINGS_ALIAS, "12");
        properties.setProperty(SkillSpectorPluginConfig.ORDER, "41");
        
        SkillSpectorPluginConfig config = SkillSpectorPluginConfig.fromMap(
            PluginConfigTestUtils.toMap(properties));
        Map<String, String> values = config.toMap();
        
        assertEquals("/tmp/spector", values.get(SkillSpectorPluginConfig.COMMAND));
        assertEquals("true", values.get(SkillSpectorPluginConfig.USE_LLM));
        assertEquals("openai", values.get(SkillSpectorPluginConfig.PROVIDER));
        assertEquals("model", values.get(SkillSpectorPluginConfig.MODEL));
        assertEquals("api-key", values.get(SkillSpectorPluginConfig.API_KEY));
        assertEquals("https://example.com", values.get(SkillSpectorPluginConfig.BASE_URL));
        assertEquals("DEBUG", values.get(SkillSpectorPluginConfig.LOG_LEVEL));
        assertEquals("80", values.get(SkillSpectorPluginConfig.RISK_SCORE_THRESHOLD));
        assertEquals("12", values.get(SkillSpectorPluginConfig.MAX_FINDINGS));
        assertEquals(41, config.getOrder());
        assertEquals("41", values.get(SkillSpectorPluginConfig.ORDER));
        assertTrue(config.getScanOptions().isUseLlm());
        
        Properties executableAlias = new Properties();
        executableAlias.setProperty(SkillSpectorPluginConfig.COMMAND_ALIAS_EXECUTABLE,
            "/tmp/executable-spector");
        executableAlias.setProperty(SkillSpectorPluginConfig.COMMAND_ALIAS_PATH,
            "/tmp/path-spector");
        assertEquals("/tmp/executable-spector",
            SkillSpectorPluginConfig.fromMap(PluginConfigTestUtils.toMap(executableAlias))
                .getCommand());
    }
    
    @Test
    void canonicalValuesTakePriorityTest() {
        Properties properties = new Properties();
        properties.setProperty(SkillSpectorPluginConfig.COMMAND, "canonical");
        properties.setProperty(SkillSpectorPluginConfig.COMMAND_ALIAS_EXECUTABLE, "alias");
        properties.setProperty(SkillSpectorPluginConfig.USE_LLM, "false");
        properties.setProperty(SkillSpectorPluginConfig.USE_LLM_ALIAS, "true");
        properties.setProperty(SkillSpectorPluginConfig.API_KEY, " ");
        properties.setProperty(SkillSpectorPluginConfig.API_KEY_ALIAS, "alias-key");
        properties.setProperty(SkillSpectorPluginConfig.LOG_LEVEL, " ");
        properties.setProperty(SkillSpectorPluginConfig.LOG_LEVEL_ALIAS, "DEBUG");
        
        Map<String, String> values =
            SkillSpectorPluginConfig.fromMap(PluginConfigTestUtils.toMap(properties)).toMap();
        
        assertEquals("canonical", values.get(SkillSpectorPluginConfig.COMMAND));
        assertEquals("false", values.get(SkillSpectorPluginConfig.USE_LLM));
        assertEquals("", values.get(SkillSpectorPluginConfig.API_KEY));
        assertEquals("WARNING", values.get(SkillSpectorPluginConfig.LOG_LEVEL));
    }
    
    @Test
    void numericValuesPreserveBuilderNormalizationTest() {
        assertNumericValues("-1", "0", "0", "20");
        assertNumericValues("101", "101", "100", "100");
        assertNumericValues("invalid", "invalid", "50", "20");
    }
    
    private void assertNumericValues(String threshold, String maxFindings,
        String expectedThreshold, String expectedMaxFindings) {
        Properties properties = new Properties();
        properties.setProperty(SkillSpectorPluginConfig.RISK_SCORE_THRESHOLD, threshold);
        properties.setProperty(SkillSpectorPluginConfig.MAX_FINDINGS, maxFindings);
        
        Map<String, String> values = SkillSpectorPluginConfig.fromMap(
            PluginConfigTestUtils.toMap(properties)).toMap();
        
        assertEquals(expectedThreshold,
            values.get(SkillSpectorPluginConfig.RISK_SCORE_THRESHOLD));
        assertEquals(expectedMaxFindings, values.get(SkillSpectorPluginConfig.MAX_FINDINGS));
    }
    
    @Test
    void fromMapIgnoresNullEntriesTest() {
        Map<String, String> source = new HashMap<>();
        source.put(null, "ignored");
        source.put(SkillSpectorPluginConfig.COMMAND, null);
        source.put(SkillSpectorPluginConfig.MODEL, " model ");
        
        Map<String, String> values = SkillSpectorPluginConfig.fromMap(source).toMap();
        Map<String, String> defaults = SkillSpectorPluginConfig.fromMap(null).toMap();
        
        assertEquals("skill-spector", values.get(SkillSpectorPluginConfig.COMMAND));
        assertEquals("model", values.get(SkillSpectorPluginConfig.MODEL));
        assertEquals("skill-spector", defaults.get(SkillSpectorPluginConfig.COMMAND));
    }
    
    @Test
    void invalidOrderShouldBeRejectedTest() {
        Map<String, String> config = new HashMap<>();
        config.put(SkillSpectorPluginConfig.ORDER, "1.5");
        assertThrows(ArithmeticException.class,
            () -> SkillSpectorPluginConfig.fromMap(config));
        config.put(SkillSpectorPluginConfig.ORDER, "invalid");
        assertThrows(NumberFormatException.class,
            () -> SkillSpectorPluginConfig.fromMap(config));
    }
}
