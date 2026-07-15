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

package com.alibaba.nacos.plugin.ai.pipeline.spi.impl;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SkillScannerScanOptions} unit test.
 *
 * @author qiacheng.cxy
 */
class SkillScannerScanOptionsTest {
    
    @Test
    void fromPropertiesEmptyTest() {
        SkillScannerScanOptions o = SkillScannerScanOptions.fromProperties(new Properties());
        assertFalse(o.isUseLlm());
        assertFalse(o.isEnableMeta());
        assertNull(o.getLlmProvider());
        Map<String, String> env = new HashMap<>();
        o.applyLlmEnvironment(env);
        assertTrue(env.isEmpty());
    }
    
    @Test
    void fromPropertiesLlmTest() {
        Properties p = new Properties();
        p.setProperty(SkillScannerScanOptions.PROP_USE_LLM, "true");
        p.setProperty(SkillScannerScanOptions.PROP_LLM_API_KEY, "your_api_key");
        p.setProperty(SkillScannerScanOptions.PROP_LLM_MODEL, "qwen-max");
        p.setProperty(SkillScannerScanOptions.PROP_LLM_PROVIDER, "openai");
        p.setProperty(SkillScannerScanOptions.PROP_ENABLE_META, "true");
        
        SkillScannerScanOptions o = SkillScannerScanOptions.fromProperties(p);
        assertTrue(o.isUseLlm());
        assertTrue(o.isEnableMeta());
        assertEquals("openai", o.getLlmProvider());
        
        Map<String, String> env = new HashMap<>();
        o.applyLlmEnvironment(env);
        assertEquals("your_api_key", env.get("SKILL_SCANNER_LLM_API_KEY"));
        assertEquals("qwen-max", env.get("SKILL_SCANNER_LLM_MODEL"));
    }
}
