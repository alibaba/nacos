/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * String utils.
 * @author zzq
 */
public class StringUtilsTest {
    
    @Test
    public void testNewStringForUtf8() {
        String abc = "abc";
        byte[] abcByte = abc.getBytes();
        Assert.assertEquals(abc, StringUtils.newStringForUtf8(abcByte));
    }
    
    @Test
    public void isBlank() {
        Assert.assertTrue(StringUtils.isBlank(null));
        Assert.assertTrue(StringUtils.isBlank(""));
        Assert.assertTrue(StringUtils.isBlank(" "));
        Assert.assertFalse(StringUtils.isBlank("bob"));
        Assert.assertFalse(StringUtils.isBlank("  bob  "));
    }
    
    @Test
    public void testIsNotBlank() {
        Assert.assertFalse(StringUtils.isNotBlank(null));
        Assert.assertFalse(StringUtils.isNotBlank(""));
        Assert.assertFalse(StringUtils.isNotBlank(" "));
        Assert.assertTrue(StringUtils.isNotBlank("bob"));
        Assert.assertTrue(StringUtils.isNotBlank("  bob  "));
    }
    
    @Test
    public void testIsNotEmpty() {
        Assert.assertFalse(StringUtils.isNotEmpty(null));
        Assert.assertFalse(StringUtils.isNotEmpty(""));
        Assert.assertTrue(StringUtils.isNotEmpty(" "));
        Assert.assertTrue(StringUtils.isNotEmpty("bob"));
        Assert.assertTrue(StringUtils.isNotEmpty("  bob  "));
    }
    
    @Test
    public void testIsEmpty() {
        Assert.assertTrue(StringUtils.isEmpty(null));
        Assert.assertTrue(StringUtils.isEmpty(""));
        Assert.assertFalse(StringUtils.isEmpty(" "));
        Assert.assertFalse(StringUtils.isEmpty("bob"));
        Assert.assertFalse(StringUtils.isEmpty("  bob  "));
    }
    
    @Test
    public void testDefaultIfEmpty() {
        Assert.assertEquals("NULL", StringUtils.defaultIfEmpty(null, "NULL"));
        Assert.assertEquals("NULL", StringUtils.defaultIfEmpty("", "NULL"));
        Assert.assertEquals(" ", StringUtils.defaultIfEmpty(" ", "NULL"));
        Assert.assertEquals("bat", StringUtils.defaultIfEmpty("bat", "NULL"));
        Assert.assertEquals(null, StringUtils.defaultIfEmpty("", null));
    }
    
    @Test
    public void testEquals() {
        Assert.assertTrue(StringUtils.equals(null, null));
        Assert.assertFalse(StringUtils.equals(null, "abc"));
        Assert.assertFalse(StringUtils.equals("abc", null));
        Assert.assertTrue(StringUtils.equals("abc", "abc"));
        Assert.assertFalse(StringUtils.equals("abc", "ABC"));
    }
    
    @Test
    public void trim() {
        Assert.assertNull(StringUtils.trim(null));
        Assert.assertEquals(StringUtils.EMPTY, StringUtils.trim(""));
        Assert.assertEquals(StringUtils.EMPTY, StringUtils.trim("     "));
        Assert.assertEquals("abc", StringUtils.trim("abc"));
        Assert.assertEquals("abc", StringUtils.trim("    abc    "));
    }
    
    @Test
    public void testSubstringBetween() {
        Assert.assertNull(StringUtils.substringBetween(null, "a", "b"));
        Assert.assertNull(StringUtils.substringBetween("a", null, "b"));
        Assert.assertNull(StringUtils.substringBetween("a", "b", null));
        Assert.assertNull(StringUtils.substringBetween(StringUtils.EMPTY, StringUtils.EMPTY, "]"));
        Assert.assertNull(StringUtils.substringBetween(StringUtils.EMPTY, "[", "]"));
        Assert.assertEquals(StringUtils.EMPTY,
                StringUtils.substringBetween("yabcz", StringUtils.EMPTY, StringUtils.EMPTY));
        Assert.assertEquals(StringUtils.EMPTY,
                StringUtils.substringBetween(StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY));
        Assert.assertEquals("b", StringUtils.substringBetween("wx[b]yz", "[", "]"));
        Assert.assertEquals("abc", StringUtils.substringBetween("yabcz", "y", "z"));
        Assert.assertEquals("abc", StringUtils.substringBetween("yabczyabcz", "y", "z"));
    }
    
    @Test
    public void testJoin() {
        ArrayList<Object> objects = new ArrayList<>();
        objects.add(null);
        Assert.assertNull(StringUtils.join(null, "a"));
        Assert.assertEquals(StringUtils.EMPTY, StringUtils.join(Arrays.asList(), "a"));
        Assert.assertEquals(StringUtils.EMPTY, StringUtils.join(objects, "a"));
        Assert.assertEquals("a;b;c", StringUtils.join(Arrays.asList("a", "b", "c"), ";"));
        Assert.assertEquals("abc", StringUtils.join(Arrays.asList("a", "b", "c"), null));
    }
    
    @Test
    public void escapeJavaScript() {
        //TODO
    }
    
    @Test
    public void testContainsIgnoreCase() {
        Assert.assertFalse(StringUtils.containsIgnoreCase(null, "1"));
        Assert.assertFalse(StringUtils.containsIgnoreCase("abc", null));
        Assert.assertTrue(StringUtils.containsIgnoreCase(StringUtils.EMPTY, StringUtils.EMPTY));
        Assert.assertTrue(StringUtils.containsIgnoreCase("abc", StringUtils.EMPTY));
        Assert.assertTrue(StringUtils.containsIgnoreCase("abc", "a"));
        Assert.assertFalse(StringUtils.containsIgnoreCase("abc", "z"));
        Assert.assertTrue(StringUtils.containsIgnoreCase("abc", "A"));
        Assert.assertFalse(StringUtils.containsIgnoreCase("abc", "Z"));
    }
    
    @Test
    public void testContains() {
        Assert.assertFalse(StringUtils.contains(null, "1"));
        Assert.assertFalse(StringUtils.contains("abc", null));
        Assert.assertTrue(StringUtils.contains(StringUtils.EMPTY, StringUtils.EMPTY));
        Assert.assertTrue(StringUtils.contains("abc", StringUtils.EMPTY));
        Assert.assertTrue(StringUtils.contains("abc", "a"));
        Assert.assertFalse(StringUtils.contains("abc", "z"));
        Assert.assertFalse(StringUtils.contains("abc", "A"));
        Assert.assertFalse(StringUtils.contains("abc", "Z"));
    }
    
    @Test
    public void testIsNoneBlank() {
        Assert.assertFalse(StringUtils.isNoneBlank(null));
        Assert.assertFalse(StringUtils.isNoneBlank(null, "foo"));
        Assert.assertFalse(StringUtils.isNoneBlank(null, null));
        Assert.assertFalse(StringUtils.isNoneBlank("", "bar"));
        Assert.assertFalse(StringUtils.isNoneBlank("bob", ""));
        Assert.assertFalse(StringUtils.isNoneBlank("  bob  ", null));
        Assert.assertFalse(StringUtils.isNoneBlank(" ", "bar"));
        Assert.assertTrue(StringUtils.isNoneBlank("foo", "bar"));
    }
    
    @Test
    public void isAnyBlank() {
        Assert.assertTrue(StringUtils.isAnyBlank(null));
        Assert.assertTrue(StringUtils.isAnyBlank(null, "foo"));
        Assert.assertTrue(StringUtils.isAnyBlank(null, null));
        Assert.assertTrue(StringUtils.isAnyBlank("", "bar"));
        Assert.assertTrue(StringUtils.isAnyBlank("bob", ""));
        Assert.assertTrue(StringUtils.isAnyBlank("  bob  ", null));
        Assert.assertTrue(StringUtils.isAnyBlank(" ", "bar"));
        Assert.assertFalse(StringUtils.isAnyBlank("foo", "bar"));
    }
    
    @Test
    public void testStartsWith() {
        Assert.assertTrue(StringUtils.startsWith(null, null));
        Assert.assertFalse(StringUtils.startsWith(null, "abc"));
        Assert.assertFalse(StringUtils.startsWith("abcdef", null));
        Assert.assertTrue(StringUtils.startsWith("abcdef", "abc"));
        Assert.assertFalse(StringUtils.startsWith("ABCDEF", "abc"));
    }
    
    @Test
    public void testStartsWithIgnoreCase() {
        Assert.assertTrue(StringUtils.startsWithIgnoreCase(null, null));
        Assert.assertFalse(StringUtils.startsWithIgnoreCase(null, "abc"));
        Assert.assertFalse(StringUtils.startsWithIgnoreCase("abcdef", null));
        Assert.assertTrue(StringUtils.startsWithIgnoreCase("abcdef", "abc"));
        Assert.assertTrue(StringUtils.startsWithIgnoreCase("ABCDEF", "abc"));
    }
    
    @Test
    public void testDeleteWhitespace() {
        Assert.assertNull(StringUtils.deleteWhitespace(null));
        Assert.assertEquals(StringUtils.EMPTY, StringUtils.deleteWhitespace(""));
        Assert.assertEquals("abc", StringUtils.deleteWhitespace("abc"));
        Assert.assertEquals("abc", StringUtils.deleteWhitespace("   ab  c  "));
    }
    
    @Test
    public void testEqualsIgnoreCase() {
        Assert.assertTrue(StringUtils.equalsIgnoreCase(null, null));
        Assert.assertFalse(StringUtils.equalsIgnoreCase(null, "abc"));
        Assert.assertFalse(StringUtils.equalsIgnoreCase("abc", null));
        Assert.assertTrue(StringUtils.equalsIgnoreCase("abc", "abc"));
        Assert.assertTrue(StringUtils.equalsIgnoreCase("abc", "ABC"));
    }
    
    @Test
    public void testSplit() {
        Assert.assertNull(StringUtils.split(null, ","));
        Assert.assertArrayEquals(new String[0], StringUtils.split("", ","));
        Assert.assertArrayEquals(new String[]{"ab", "cd", "ef"}, StringUtils.split("ab cd ef", null));
        Assert.assertArrayEquals(new String[]{"ab", "cd", "ef"}, StringUtils.split("ab   cd ef", null));
        Assert.assertArrayEquals(new String[]{"ab", "cd", "ef"}, StringUtils.split("ab:cd:ef", ":"));
    }
}
