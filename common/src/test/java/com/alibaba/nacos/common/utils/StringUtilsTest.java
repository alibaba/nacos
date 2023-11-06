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
 *
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
        Assert.assertNull(StringUtils.defaultIfEmpty("", null));
    }
    
    @Test
    public void testDefaultIfBlank() {
        Assert.assertEquals("NULL", StringUtils.defaultIfBlank(null, "NULL"));
        Assert.assertEquals("NULL", StringUtils.defaultIfBlank("", "NULL"));
        Assert.assertEquals("NULL", StringUtils.defaultIfBlank(" ", "NULL"));
        Assert.assertEquals("bat", StringUtils.defaultIfBlank("bat", "NULL"));
        Assert.assertNull(StringUtils.defaultIfBlank("", null));
    }
    
    @Test
    public void testDefaultEmptyIfBlank() {
        Assert.assertEquals("", StringUtils.defaultEmptyIfBlank(null));
        Assert.assertEquals("", StringUtils.defaultEmptyIfBlank(""));
        Assert.assertEquals("", StringUtils.defaultEmptyIfBlank(" "));
        Assert.assertEquals("bat", StringUtils.defaultEmptyIfBlank("bat"));
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
        Assert.assertFalse(StringUtils.startsWith("ABC", "ABCDEF"));
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
        Assert.assertArrayEquals(new String[] {"ab", "cd", "ef"}, StringUtils.split("ab cd ef", null));
        Assert.assertArrayEquals(new String[] {"ab", "cd", "ef"}, StringUtils.split("ab   cd ef", null));
        Assert.assertArrayEquals(new String[] {"ab", "cd", "ef"}, StringUtils.split("ab:cd:ef", ":"));
    }
    
    @Test
    public void testTokenizeToStringArray() {
        // Test case 1: Empty string
        String str1 = "";
        String delimiters1 = ",";
        boolean trimTokens1 = true;
        boolean ignoreEmptyTokens1 = false;
        String[] expected1 = new String[0];
        String[] result1 = StringUtils.tokenizeToStringArray(str1, delimiters1, trimTokens1, ignoreEmptyTokens1);
        Assert.assertArrayEquals(expected1, result1);
        
        // Test case 2: Null string
        String str2 = null;
        String delimiters2 = " ";
        boolean trimTokens2 = false;
        boolean ignoreEmptyTokens2 = true;
        String[] expected2 = new String[0];
        String[] result2 = StringUtils.tokenizeToStringArray(str2, delimiters2, trimTokens2, ignoreEmptyTokens2);
        Assert.assertArrayEquals(expected2, result2);
        
        // Test case 3: Single token
        String str3 = "Hello";
        String delimiters3 = ",";
        boolean trimTokens3 = true;
        boolean ignoreEmptyTokens3 = false;
        String[] expected3 = {"Hello"};
        String[] result3 = StringUtils.tokenizeToStringArray(str3, delimiters3, trimTokens3, ignoreEmptyTokens3);
        Assert.assertArrayEquals(expected3, result3);
        
        // Test case 4: Multiple tokens with trimming
        String str4 = "  Hello,  World,  ";
        String delimiters4 = ",";
        boolean trimTokens4 = true;
        boolean ignoreEmptyTokens4 = false;
        String[] expected4 = {"Hello", "World", ""};
        String[] result4 = StringUtils.tokenizeToStringArray(str4, delimiters4, trimTokens4, ignoreEmptyTokens4);
        Assert.assertArrayEquals(expected4, result4);
        
        // Test case 5: Multiple tokens with empty tokens ignored
        String str5 = "  ,Hello,  ,World,  ";
        String delimiters5 = ",";
        boolean trimTokens5 = true;
        boolean ignoreEmptyTokens5 = true;
        String[] expected5 = {"Hello", "World"};
        String[] result5 = StringUtils.tokenizeToStringArray(str5, delimiters5, trimTokens5, ignoreEmptyTokens5);
        Assert.assertArrayEquals(expected5, result5);
    }
    
    @Test
    public void testHasText() {
        // Test case 1: Empty string
        Assert.assertFalse(StringUtils.hasText(""));
        
        // Test case 2: String with whitespace only
        Assert.assertFalse(StringUtils.hasText("   "));
        
        // Test case 3: Null string
        Assert.assertFalse(StringUtils.hasText(null));
        
        // Test case 4: String with non-whitespace characters
        Assert.assertTrue(StringUtils.hasText("hello"));
        
        // Test case 5: String with both text and whitespace
        Assert.assertTrue(StringUtils.hasText(" hello "));
    }
    
    @Test
    public void testCleanPath() {
        // Test case 1: path with no length
        String path1 = "";
        String expected1 = "";
        Assert.assertEquals(expected1, StringUtils.cleanPath(path1));
        
        // Test case 2: normal path
        String path2 = "path/to/file";
        String expected2 = "path/to/file";
        Assert.assertEquals(expected2, StringUtils.cleanPath(path2));
        
        // Test case 3: path with Windows folder separator
        String path3 = "path\\to\\文件";
        String expected3 = "path/to/文件";
        Assert.assertEquals(expected3, StringUtils.cleanPath(path3));
        
        // Test case 4: path with dot
        String path4 = "path/..";
        String expected4 = "";
        Assert.assertEquals(expected4, StringUtils.cleanPath(path4));
        
        // Test case 5: path with top path
        String path5 = "path/../top";
        String expected5 = "top";
        Assert.assertEquals(expected5, StringUtils.cleanPath(path5));
        
        // Test case 6: path with multiple top path
        String path6 = "path/../../top";
        String expected6 = "../top";
        Assert.assertEquals(expected6, StringUtils.cleanPath(path6));
        
        // Test case 7: path with leading colon
        String path7 = "file:../top";
        String expected7 = "file:../top";
        Assert.assertEquals(expected7, StringUtils.cleanPath(path7));
        
        // Test case 8: path with leading slash
        String path8 = "file:/path/../file";
        String expected8 = "file:/file";
        Assert.assertEquals(expected8, StringUtils.cleanPath(path8));
        
        // Test case 9: path with empty prefix
        String path9 = "file:path/../file";
        String expected9 = "file:file";
        Assert.assertEquals(expected9, StringUtils.cleanPath(path9));
        
        // Test case 10: prefix contain separator
        String path10 = "file/:path/../file";
        String expected10 = "file/file";
        Assert.assertEquals(expected10, StringUtils.cleanPath(path10));
        
        // Test case 11: dot in file name
        String path11 = "file:/path/to/file.txt";
        String expected11 = "file:/path/to/file.txt";
        Assert.assertEquals(expected11, StringUtils.cleanPath(path11));
        
        // Test case 12: dot in path
        String path12 = "file:/path/./file.txt";
        String expected12 = "file:/path/file.txt";
        Assert.assertEquals(expected12, StringUtils.cleanPath(path12));
        
        // Test case 13: path with dot and slash
        String path13 = "file:aaa/../";
        String expected13 = "file:./";
        Assert.assertEquals(expected13, StringUtils.cleanPath(path13));
    }
    
    @Test
    public void testDelimitedListToStringArrayWithNull() {
        Assert.assertEquals(0, StringUtils.delimitedListToStringArray(null, ",", "").length);
        Assert.assertEquals(1, StringUtils.delimitedListToStringArray("a,b", null, "").length);
    }
    
    @Test
    public void testDelimitedListToStringArrayWithEmptyDelimiter() {
        String testCase = "a,b";
        String[] actual = StringUtils.delimitedListToStringArray(testCase, "", "");
        Assert.assertEquals(3, actual.length);
        Assert.assertEquals("a", actual[0]);
        Assert.assertEquals(",", actual[1]);
        Assert.assertEquals("b", actual[2]);
    }
    
    @Test
    public void testDeleteAny() {
        // Test case 1: inString is empty, charsToDelete is empty
        String inString1 = "";
        String charsToDelete1 = "";
        Assert.assertEquals("", StringUtils.deleteAny(inString1, charsToDelete1));
        
        // Test case 2: inString is empty, charsToDelete is not empty
        String inString2 = "";
        String charsToDelete2 = "abc";
        Assert.assertEquals("", StringUtils.deleteAny(inString2, charsToDelete2));
        
        // Test case 3: inString is not empty, charsToDelete is empty
        String inString3 = "abc";
        String charsToDelete3 = "";
        Assert.assertEquals("abc", StringUtils.deleteAny(inString3, charsToDelete3));
        
        // Test case 4: inString is not empty, charsToDelete is not empty
        String inString4 = "abc";
        String charsToDelete4 = "a";
        Assert.assertEquals("bc", StringUtils.deleteAny(inString4, charsToDelete4));
        
        // Test case 5: inString contains special characters
        String inString5 = "abc\n";
        String charsToDelete5 = "\n";
        Assert.assertEquals("abc", StringUtils.deleteAny(inString5, charsToDelete5));
    
        // Test case 6: inString not contains special characters
        String inString6 = "abc\n";
        String charsToDelete6 = "d";
        Assert.assertEquals("abc\n", StringUtils.deleteAny(inString6, charsToDelete6));
    }
    
    @Test
    public void testReplace() {
        // Test case 1: pattern is empty
        Assert.assertEquals("abc", StringUtils.replace("abc", "", "a"));
        
        // Test case 2: oldPattern less than newPattern
        Assert.assertEquals("aabc", StringUtils.replace("abc", "a", "aa"));
    
        // Test case 3: oldPattern more than newPattern
        Assert.assertEquals("dc", StringUtils.replace("abc", "ab", "d"));
    }
    
    @Test
    public void testApplyRelativePath() {
        // Test case 1
        String path1 = "/path/to/file";
        String relativePath1 = "subfolder/subfile";
        String expected1 = "/path/to/subfolder/subfile";
        String result1 = StringUtils.applyRelativePath(path1, relativePath1);
        Assert.assertEquals(expected1, result1);
        
        // Test case 2
        String path2 = "path/to/file";
        String relativePath2 = "subfolder/subfile";
        String expected2 = "path/to/subfolder/subfile";
        String result2 = StringUtils.applyRelativePath(path2, relativePath2);
        Assert.assertEquals(expected2, result2);
        
        // Test case 3
        String path3 = "/path/to/file";
        String relativePath3 = "/subfolder/subfile";
        String expected3 = "/path/to/subfolder/subfile";
        String result3 = StringUtils.applyRelativePath(path3, relativePath3);
        Assert.assertEquals(expected3, result3);
        
        //Test case 4
        String path4 = "file";
        String relativePath4 = "/subfolder/subfile";
        String expected4 = "/subfolder/subfile";
        String result4 = StringUtils.applyRelativePath(path4, relativePath4);
        Assert.assertEquals(expected4, result4);
    }
    
    @Test
    public void testGetFilename() {
        // Test case 1: null path
        String path1 = null;
        String result1 = StringUtils.getFilename(path1);
        Assert.assertNull(result1);
        
        // Test case 2: path without separator
        String path2 = "myFile.txt";
        String expectedResult2 = "myFile.txt";
        String result2 = StringUtils.getFilename(path2);
        Assert.assertEquals(expectedResult2, result2);
        
        // Test case 3: path with separator
        String path3 = "myPath/myFile.txt";
        String expectedResult3 = "myFile.txt";
        String result3 = StringUtils.getFilename(path3);
        Assert.assertEquals(expectedResult3, result3);
        
        // Test case 4: path with multiple separators
        String path4 = "myPath/subPath/myFile.txt";
        String expectedResult4 = "myFile.txt";
        String result4 = StringUtils.getFilename(path4);
        Assert.assertEquals(expectedResult4, result4);
    }
    
    @Test
    public void testCapitalize() {
        // Test for an empty string
        String str1 = "";
        Assert.assertEquals("", StringUtils.capitalize(str1));
        
        // Test for a single word string
        String str2 = "hello";
        Assert.assertEquals("Hello", StringUtils.capitalize(str2));
        
        // Test for a multiple word string
        String str3 = "hello world";
        Assert.assertEquals("Hello world", StringUtils.capitalize(str3));
        
        // Test for a string with special characters
        String str4 = "!@#$%^&*()";
        Assert.assertEquals("!@#$%^&*()", StringUtils.capitalize(str4));
        
        // Test for a string with numbers
        String str5 = "abc123";
        Assert.assertEquals("Abc123", StringUtils.capitalize(str5));
    }
}
