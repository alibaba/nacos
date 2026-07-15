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

package com.alibaba.nacos.ai.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PromptVersionUtils}.
 *
 * @author nacos
 */
class PromptVersionUtilsTest {
    
    @Test
    void testIsValidVersion() {
        assertTrue(PromptVersionUtils.isValidVersion("1.0.0"));
        assertTrue(PromptVersionUtils.isValidVersion("10.20.30"));
        assertFalse(PromptVersionUtils.isValidVersion(null));
        assertFalse(PromptVersionUtils.isValidVersion(""));
        assertFalse(PromptVersionUtils.isValidVersion("1.0"));
        assertFalse(PromptVersionUtils.isValidVersion("v1.0.0"));
    }
    
    @Test
    void testCompareVersion() {
        assertEquals(0, PromptVersionUtils.compareVersion("1.0.0", "1.0.0"));
        assertTrue(PromptVersionUtils.compareVersion("1.2.0", "1.1.9") > 0);
        assertTrue(PromptVersionUtils.compareVersion("2.0.0", "10.0.0") < 0);
    }
    
    @Test
    void testCompareVersionRejectsInvalidFormat() {
        assertThrows(IllegalArgumentException.class,
            () -> PromptVersionUtils.compareVersion("bad", "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> PromptVersionUtils.compareVersion("1.0.0", "bad"));
    }
    
    @Test
    void testIsVersionGreater() {
        assertTrue(PromptVersionUtils.isVersionGreater("1.0.0", null));
        assertTrue(PromptVersionUtils.isVersionGreater("1.0.0", ""));
        assertTrue(PromptVersionUtils.isVersionGreater("1.0.0", "bad"));
        assertTrue(PromptVersionUtils.isVersionGreater("1.0.1", "1.0.0"));
        assertFalse(PromptVersionUtils.isVersionGreater("bad", "1.0.0"));
        assertFalse(PromptVersionUtils.isVersionGreater("1.0.0", "1.0.0"));
        assertFalse(PromptVersionUtils.isVersionGreater("1.0.0", "1.0.1"));
    }
    
    @Test
    void testDataIdConversion() {
        assertEquals("greeting.json", PromptVersionUtils.buildDataId("greeting"));
        assertEquals("greeting", PromptVersionUtils.extractPromptKey("greeting.json"));
        assertEquals("greeting.txt", PromptVersionUtils.extractPromptKey("greeting.txt"));
        assertEquals(null, PromptVersionUtils.extractPromptKey(null));
        assertEquals(null, PromptVersionUtils.extractPromptKey(""));
    }
}
