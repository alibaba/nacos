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
 *
 */

package com.alibaba.nacos.ai.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link HubResourceNameUtils}.
 *
 */
class HubResourceNameUtilsTest {
    
    @Test
    void testBuildInternalName() {
        String result = HubResourceNameUtils.buildInternalName("find-skills", 101L);
        
        assertEquals("find-skills-101", result);
    }
    
    @Test
    void testSameSourceNameWithDifferentResourceIds() {
        String first = HubResourceNameUtils.buildInternalName("find-skills", 101L);
        String second = HubResourceNameUtils.buildInternalName("find-skills", 201L);
        
        assertEquals("find-skills-101", first);
        assertEquals("find-skills-201", second);
        assertNotEquals(first, second);
    }
    
    @Test
    void testLongSourceNameShouldBeTruncated() {
        String sourceName = "a".repeat(300);
        
        String result = HubResourceNameUtils.buildInternalName(sourceName, 101L);
        
        assertEquals(HubResourceNameUtils.MAX_RESOURCE_NAME_LENGTH,
                result.codePointCount(0, result.length()));
        assertTrue(result.endsWith("-101"));
    }
    
    @Test
    void testUnicodeSourceNameShouldNotBeBroken() {
        String sourceName = "😀".repeat(300);
        
        String result = HubResourceNameUtils.buildInternalName(sourceName, 101L);
        
        assertEquals(HubResourceNameUtils.MAX_RESOURCE_NAME_LENGTH,
                result.codePointCount(0, result.length()));
        assertTrue(result.endsWith("-101"));
    }
    
    @Test
    void testBlankSourceNameShouldFail() {
        assertThrows(IllegalArgumentException.class,
                () -> HubResourceNameUtils.buildInternalName("", 101L));
    }
    
    @Test
    void testNullSourceResourceIdShouldFail() {
        assertThrows(IllegalArgumentException.class,
                () -> HubResourceNameUtils.buildInternalName("find-skills", null));
    }
    
    @Test
    void testNonPositiveSourceResourceIdShouldFail() {
        assertThrows(IllegalArgumentException.class,
                () -> HubResourceNameUtils.buildInternalName("find-skills", 0L));
    }
}
