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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SkillSpectorScanOptions} unit test.
 *
 * @author nacos
 */
class SkillSpectorScanOptionsTest {
    
    @Test
    void fromPropertiesEmptyTest() {
        SkillSpectorScanOptions options = SkillSpectorScanOptions.fromProperties(new Properties());
        
        assertFalse(options.isUseLlm());
        assertEquals(50, options.getRiskScoreThreshold());
        assertEquals(20, options.getMaxFindings());
        Map<String, String> env = new HashMap<>();
        options.applyLlmEnvironment(env);
        assertTrue(env.isEmpty());
    }
    
    @Test
    void fromPropertiesWithKebabAliasesTest() {
        Properties properties = new Properties();
        properties.setProperty(SkillSpectorScanOptions.PROP_USE_LLM_KEBAB, "true");
        properties.setProperty(SkillSpectorScanOptions.PROP_RISK_SCORE_THRESHOLD_KEBAB, "80");
        properties.setProperty(SkillSpectorScanOptions.PROP_MAX_FINDINGS_KEBAB, "12");
        properties.setProperty(SkillSpectorScanOptions.PROP_PROVIDER, "openai");
        properties.setProperty(SkillSpectorScanOptions.PROP_MODEL, "gpt-test");
        properties.setProperty(SkillSpectorScanOptions.PROP_API_KEY_KEBAB, "configured-key");
        properties.setProperty(SkillSpectorScanOptions.PROP_BASE_URL_KEBAB,
            "https://example.com/v1");
        
        SkillSpectorScanOptions options = SkillSpectorScanOptions.fromProperties(properties);
        
        assertTrue(options.isUseLlm());
        assertEquals(80, options.getRiskScoreThreshold());
        assertEquals(12, options.getMaxFindings());
        Map<String, String> env = new HashMap<>();
        options.applyLlmEnvironment(env);
        assertEquals("openai", env.get("SKILLSPECTOR_PROVIDER"));
        assertEquals("gpt-test", env.get("SKILLSPECTOR_MODEL"));
        assertEquals("configured-key", env.get("OPENAI_API_KEY"));
        assertEquals("https://example.com/v1", env.get("OPENAI_BASE_URL"));
    }
    
    @Test
    void maxFindingsShouldBeCappedAtLimitTest() {
        Properties properties = new Properties();
        properties.setProperty(SkillSpectorScanOptions.PROP_MAX_FINDINGS_KEBAB, "1000");
        
        SkillSpectorScanOptions options = SkillSpectorScanOptions.fromProperties(properties);
        
        assertEquals(100, options.getMaxFindings());
    }
    
    @Test
    void environmentApiKeyHasPriorityTest() {
        Properties properties = new Properties();
        properties.setProperty(SkillSpectorScanOptions.PROP_USE_LLM, "true");
        properties.setProperty(SkillSpectorScanOptions.PROP_PROVIDER, "anthropic");
        properties.setProperty(SkillSpectorScanOptions.PROP_API_KEY, "configured-key");
        
        SkillSpectorScanOptions options = SkillSpectorScanOptions.fromProperties(properties);
        Map<String, String> env = new HashMap<>();
        env.put("ANTHROPIC_API_KEY", "env-key");
        
        options.applyLlmEnvironment(env);
        
        assertEquals("env-key", env.get("ANTHROPIC_API_KEY"));
    }
    
    @Test
    void genericApiKeyMapsToProviderKeyTest() {
        Properties properties = new Properties();
        properties.setProperty(SkillSpectorScanOptions.PROP_USE_LLM, "true");
        properties.setProperty(SkillSpectorScanOptions.PROP_PROVIDER, "nv_inference");
        
        SkillSpectorScanOptions options = SkillSpectorScanOptions.fromProperties(properties);
        Map<String, String> env = new HashMap<>();
        env.put("SKILLSPECTOR_API_KEY", "generic-key");
        
        options.applyLlmEnvironment(env);
        
        assertEquals("generic-key", env.get("NVIDIA_INFERENCE_KEY"));
    }
    
    @Test
    void defaultProviderMapsApiKeyToNvidiaTest() {
        Properties properties = new Properties();
        properties.setProperty(SkillSpectorScanOptions.PROP_USE_LLM, "true");
        properties.setProperty(SkillSpectorScanOptions.PROP_API_KEY, "configured-key");
        
        SkillSpectorScanOptions options = SkillSpectorScanOptions.fromProperties(properties);
        Map<String, String> env = new HashMap<>();
        
        options.applyLlmEnvironment(env);
        
        assertEquals("configured-key", env.get("NVIDIA_INFERENCE_KEY"));
    }
}
