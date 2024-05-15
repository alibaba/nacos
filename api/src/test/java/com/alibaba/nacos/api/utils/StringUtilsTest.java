/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringUtilsTest {
    
    @Test
    void testIsEmpty() {
        assertTrue(StringUtils.isEmpty(null));
        assertTrue(StringUtils.isEmpty(""));
        assertFalse(StringUtils.isEmpty(" "));
        assertFalse(StringUtils.isEmpty("bob"));
        assertFalse(StringUtils.isEmpty("  bob  "));
    }
    
    @Test
    void testIsBlank() {
        assertTrue(StringUtils.isBlank(null));
        assertTrue(StringUtils.isBlank(""));
        assertTrue(StringUtils.isBlank(" "));
        assertFalse(StringUtils.isBlank("bob"));
        assertFalse(StringUtils.isBlank("  bob  "));
    }
    
    @Test
    void testTrim() {
        assertNull(StringUtils.trim(null));
        assertEquals(StringUtils.EMPTY, StringUtils.trim(""));
        assertEquals(StringUtils.EMPTY, StringUtils.trim("     "));
        assertEquals("abc", StringUtils.trim("abc"));
        assertEquals("abc", StringUtils.trim("    abc    "));
    }
    
    @Test
    void testEquals() {
        assertTrue(StringUtils.equals(null, null));
        assertFalse(StringUtils.equals(null, "abc"));
        assertFalse(StringUtils.equals("abc", null));
        assertTrue(StringUtils.equals("abc", "abc"));
        assertFalse(StringUtils.equals("abc", "ABC"));
        assertTrue(StringUtils.equals(new StringBuilder("abc"), "abc"));
        assertFalse(StringUtils.equals(new StringBuilder("ABC"), "abc"));
    }
    
    @Test
    void testRegionMatchesEqualsCaseSensitive() {
        assertTrue(StringUtils.regionMatches("abc", false, 0, "xabc", 1, 3));
        
    }
    
    @Test
    void testRegionMatchesEqualsCaseInsensitive() {
        assertTrue(StringUtils.regionMatches("abc", true, 0, "xabc", 1, 3));
        assertTrue(StringUtils.regionMatches("abc", true, 0, "xAbc", 1, 3));
    }
    
    @Test
    void testRegionMatchesNotEqualsCaseSensitive() {
        assertFalse(StringUtils.regionMatches("abc", false, 0, "xAbc", 1, 3));
        assertFalse(StringUtils.regionMatches("abc", false, 0, "xCbc", 1, 3));
        
    }
    
    @Test
    void testRegionMatchesNotEqualsCaseInsensitive() {
        assertFalse(StringUtils.regionMatches("abc", true, 0, "xCab", 1, 3));
    }
    
    @Test
    void testRegionMatchesEqualsCaseSensitiveForNonString() {
        assertTrue(StringUtils.regionMatches(new StringBuilder("abc"), false, 0, "xabc", 1, 3));
        
    }
    
    @Test
    void testRegionMatchesEqualsCaseInsensitiveForNonString() {
        assertTrue(StringUtils.regionMatches(new StringBuilder("abc"), true, 0, "xabc", 1, 3));
        assertTrue(StringUtils.regionMatches(new StringBuilder("abc"), true, 0, "xAbc", 1, 3));
    }
    
    @Test
    void testRegionMatchesNotEqualsCaseSensitiveForNonString() {
        assertFalse(StringUtils.regionMatches(new StringBuilder("abc"), false, 0, "xAbc", 1, 3));
        assertFalse(StringUtils.regionMatches(new StringBuilder("abc"), false, 0, "xCbc", 1, 3));
        
    }
    
    @Test
    void testRegionMatchesNotEqualsCaseInsensitiveForNonString() {
        assertFalse(StringUtils.regionMatches(new StringBuilder("abc"), true, 0, "xCab", 1, 3));
    }
}
