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

import org.junit.Assert;
import org.junit.Test;

public class StringUtilsTest {
    
    @Test
    public void testIsEmpty() {
        Assert.assertTrue(StringUtils.isEmpty(null));
        Assert.assertTrue(StringUtils.isEmpty(""));
        Assert.assertFalse(StringUtils.isEmpty(" "));
        Assert.assertFalse(StringUtils.isEmpty("bob"));
        Assert.assertFalse(StringUtils.isEmpty("  bob  "));
    }
    
    @Test
    public void testIsBlank() {
        Assert.assertTrue(StringUtils.isBlank(null));
        Assert.assertTrue(StringUtils.isBlank(""));
        Assert.assertTrue(StringUtils.isBlank(" "));
        Assert.assertFalse(StringUtils.isBlank("bob"));
        Assert.assertFalse(StringUtils.isBlank("  bob  "));
    }
    
    @Test
    public void testTrim() {
        Assert.assertNull(StringUtils.trim(null));
        Assert.assertEquals(StringUtils.EMPTY, StringUtils.trim(""));
        Assert.assertEquals(StringUtils.EMPTY, StringUtils.trim("     "));
        Assert.assertEquals("abc", StringUtils.trim("abc"));
        Assert.assertEquals("abc", StringUtils.trim("    abc    "));
    }
    
    @Test
    public void testEquals() {
        Assert.assertTrue(StringUtils.equals(null, null));
        Assert.assertFalse(StringUtils.equals(null, "abc"));
        Assert.assertFalse(StringUtils.equals("abc", null));
        Assert.assertTrue(StringUtils.equals("abc", "abc"));
        Assert.assertFalse(StringUtils.equals("abc", "ABC"));
        Assert.assertTrue(StringUtils.equals(new StringBuilder("abc"), "abc"));
        Assert.assertFalse(StringUtils.equals(new StringBuilder("ABC"), "abc"));
    }
    
    @Test
    public void testRegionMatchesEqualsCaseSensitive() {
        Assert.assertTrue(StringUtils.regionMatches("abc", false, 0, "xabc", 1, 3));
    
    }
    
    @Test
    public void testRegionMatchesEqualsCaseInsensitive() {
        Assert.assertTrue(StringUtils.regionMatches("abc", true, 0, "xabc", 1, 3));
        Assert.assertTrue(StringUtils.regionMatches("abc", true, 0, "xAbc", 1, 3));
    }
    
    @Test
    public void testRegionMatchesNotEqualsCaseSensitive() {
        Assert.assertFalse(StringUtils.regionMatches("abc", false, 0, "xAbc", 1, 3));
        Assert.assertFalse(StringUtils.regionMatches("abc", false, 0, "xCbc", 1, 3));
        
    }
    
    @Test
    public void testRegionMatchesNotEqualsCaseInsensitive() {
        Assert.assertFalse(StringUtils.regionMatches("abc", true, 0, "xCab", 1, 3));
    }
    
    @Test
    public void testRegionMatchesEqualsCaseSensitiveForNonString() {
        Assert.assertTrue(StringUtils.regionMatches(new StringBuilder("abc"), false, 0, "xabc", 1, 3));
        
    }
    
    @Test
    public void testRegionMatchesEqualsCaseInsensitiveForNonString() {
        Assert.assertTrue(StringUtils.regionMatches(new StringBuilder("abc"), true, 0, "xabc", 1, 3));
        Assert.assertTrue(StringUtils.regionMatches(new StringBuilder("abc"), true, 0, "xAbc", 1, 3));
    }
    
    @Test
    public void testRegionMatchesNotEqualsCaseSensitiveForNonString() {
        Assert.assertFalse(StringUtils.regionMatches(new StringBuilder("abc"), false, 0, "xAbc", 1, 3));
        Assert.assertFalse(StringUtils.regionMatches(new StringBuilder("abc"), false, 0, "xCbc", 1, 3));
        
    }
    
    @Test
    public void testRegionMatchesNotEqualsCaseInsensitiveForNonString() {
        Assert.assertFalse(StringUtils.regionMatches(new StringBuilder("abc"), true, 0, "xCab", 1, 3));
    }
}
