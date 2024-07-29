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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * String utils.
 *
 * @author zzq
 */
class StringUtilsTest {
    
    @Test
    void testNewStringForUtf8() {
        String abc = "abc";
        byte[] abcByte = abc.getBytes();
        assertEquals(abc, StringUtils.newStringForUtf8(abcByte));
    }
    
    @Test
    void isBlank() {
        assertTrue(StringUtils.isBlank(null));
        assertTrue(StringUtils.isBlank(""));
        assertTrue(StringUtils.isBlank(" "));
        assertFalse(StringUtils.isBlank("bob"));
        assertFalse(StringUtils.isBlank("  bob  "));
    }
    
    @Test
    void testIsNotBlank() {
        assertFalse(StringUtils.isNotBlank(null));
        assertFalse(StringUtils.isNotBlank(""));
        assertFalse(StringUtils.isNotBlank(" "));
        assertTrue(StringUtils.isNotBlank("bob"));
        assertTrue(StringUtils.isNotBlank("  bob  "));
    }
    
    @Test
    void testIsNotEmpty() {
        assertFalse(StringUtils.isNotEmpty(null));
        assertFalse(StringUtils.isNotEmpty(""));
        assertTrue(StringUtils.isNotEmpty(" "));
        assertTrue(StringUtils.isNotEmpty("bob"));
        assertTrue(StringUtils.isNotEmpty("  bob  "));
    }
    
    @Test
    void testIsEmpty() {
        assertTrue(StringUtils.isEmpty(null));
        assertTrue(StringUtils.isEmpty(""));
        assertFalse(StringUtils.isEmpty(" "));
        assertFalse(StringUtils.isEmpty("bob"));
        assertFalse(StringUtils.isEmpty("  bob  "));
    }
    
    @Test
    void testDefaultIfEmpty() {
        assertEquals("NULL", StringUtils.defaultIfEmpty(null, "NULL"));
        assertEquals("NULL", StringUtils.defaultIfEmpty("", "NULL"));
        assertEquals(" ", StringUtils.defaultIfEmpty(" ", "NULL"));
        assertEquals("bat", StringUtils.defaultIfEmpty("bat", "NULL"));
        assertNull(StringUtils.defaultIfEmpty("", null));
    }
    
    @Test
    void testDefaultIfBlank() {
        assertEquals("NULL", StringUtils.defaultIfBlank(null, "NULL"));
        assertEquals("NULL", StringUtils.defaultIfBlank("", "NULL"));
        assertEquals("NULL", StringUtils.defaultIfBlank(" ", "NULL"));
        assertEquals("bat", StringUtils.defaultIfBlank("bat", "NULL"));
        assertNull(StringUtils.defaultIfBlank("", null));
    }
    
    @Test
    void testDefaultEmptyIfBlank() {
        assertEquals("", StringUtils.defaultEmptyIfBlank(null));
        assertEquals("", StringUtils.defaultEmptyIfBlank(""));
        assertEquals("", StringUtils.defaultEmptyIfBlank(" "));
        assertEquals("bat", StringUtils.defaultEmptyIfBlank("bat"));
    }
    
    @Test
    void testEquals() {
        assertTrue(StringUtils.equals(null, null));
        assertFalse(StringUtils.equals(null, "abc"));
        assertFalse(StringUtils.equals("abc", null));
        assertTrue(StringUtils.equals("abc", "abc"));
        assertFalse(StringUtils.equals("abc", "ABC"));
    }
    
    @Test
    void trim() {
        assertNull(StringUtils.trim(null));
        assertEquals(StringUtils.EMPTY, StringUtils.trim(""));
        assertEquals(StringUtils.EMPTY, StringUtils.trim("     "));
        assertEquals("abc", StringUtils.trim("abc"));
        assertEquals("abc", StringUtils.trim("    abc    "));
    }
    
    @Test
    void testSubstringBetween() {
        assertNull(StringUtils.substringBetween(null, "a", "b"));
        assertNull(StringUtils.substringBetween("a", null, "b"));
        assertNull(StringUtils.substringBetween("a", "b", null));
        assertNull(StringUtils.substringBetween(StringUtils.EMPTY, StringUtils.EMPTY, "]"));
        assertNull(StringUtils.substringBetween(StringUtils.EMPTY, "[", "]"));
        assertEquals(StringUtils.EMPTY, StringUtils.substringBetween("yabcz", StringUtils.EMPTY, StringUtils.EMPTY));
        assertEquals(StringUtils.EMPTY, StringUtils.substringBetween(StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY));
        assertEquals("b", StringUtils.substringBetween("wx[b]yz", "[", "]"));
        assertEquals("abc", StringUtils.substringBetween("yabcz", "y", "z"));
        assertEquals("abc", StringUtils.substringBetween("yabczyabcz", "y", "z"));
    }
    
    @Test
    void testJoin() {
        ArrayList<Object> objects = new ArrayList<>();
        objects.add(null);
        assertNull(StringUtils.join(null, "a"));
        assertEquals(StringUtils.EMPTY, StringUtils.join(Arrays.asList(), "a"));
        assertEquals(StringUtils.EMPTY, StringUtils.join(objects, "a"));
        assertEquals("a;b;c", StringUtils.join(Arrays.asList("a", "b", "c"), ";"));
        assertEquals("abc", StringUtils.join(Arrays.asList("a", "b", "c"), null));
    }
    
    @Test
    void testContainsIgnoreCase() {
        assertFalse(StringUtils.containsIgnoreCase(null, "1"));
        assertFalse(StringUtils.containsIgnoreCase("abc", null));
        assertTrue(StringUtils.containsIgnoreCase(StringUtils.EMPTY, StringUtils.EMPTY));
        assertTrue(StringUtils.containsIgnoreCase("abc", StringUtils.EMPTY));
        assertTrue(StringUtils.containsIgnoreCase("abc", "a"));
        assertFalse(StringUtils.containsIgnoreCase("abc", "z"));
        assertTrue(StringUtils.containsIgnoreCase("abc", "A"));
        assertFalse(StringUtils.containsIgnoreCase("abc", "Z"));
    }
    
    @Test
    void testContains() {
        assertFalse(StringUtils.contains(null, "1"));
        assertFalse(StringUtils.contains("abc", null));
        assertTrue(StringUtils.contains(StringUtils.EMPTY, StringUtils.EMPTY));
        assertTrue(StringUtils.contains("abc", StringUtils.EMPTY));
        assertTrue(StringUtils.contains("abc", "a"));
        assertFalse(StringUtils.contains("abc", "z"));
        assertFalse(StringUtils.contains("abc", "A"));
        assertFalse(StringUtils.contains("abc", "Z"));
    }
    
    @Test
    void testIsNoneBlank() {
        assertFalse(StringUtils.isNoneBlank(null));
        assertFalse(StringUtils.isNoneBlank(null, "foo"));
        assertFalse(StringUtils.isNoneBlank(null, null));
        assertFalse(StringUtils.isNoneBlank("", "bar"));
        assertFalse(StringUtils.isNoneBlank("bob", ""));
        assertFalse(StringUtils.isNoneBlank("  bob  ", null));
        assertFalse(StringUtils.isNoneBlank(" ", "bar"));
        assertTrue(StringUtils.isNoneBlank("foo", "bar"));
    }
    
    @Test
    void isAnyBlank() {
        assertTrue(StringUtils.isAnyBlank(null));
        assertTrue(StringUtils.isAnyBlank(null, "foo"));
        assertTrue(StringUtils.isAnyBlank(null, null));
        assertTrue(StringUtils.isAnyBlank("", "bar"));
        assertTrue(StringUtils.isAnyBlank("bob", ""));
        assertTrue(StringUtils.isAnyBlank("  bob  ", null));
        assertTrue(StringUtils.isAnyBlank(" ", "bar"));
        assertFalse(StringUtils.isAnyBlank("foo", "bar"));
    }
    
    @Test
    void testStartsWith() {
        assertTrue(StringUtils.startsWith(null, null));
        assertFalse(StringUtils.startsWith(null, "abc"));
        assertFalse(StringUtils.startsWith("abcdef", null));
        assertTrue(StringUtils.startsWith("abcdef", "abc"));
        assertFalse(StringUtils.startsWith("ABCDEF", "abc"));
        assertFalse(StringUtils.startsWith("ABC", "ABCDEF"));
    }
    
    @Test
    void testStartsWithIgnoreCase() {
        assertTrue(StringUtils.startsWithIgnoreCase(null, null));
        assertFalse(StringUtils.startsWithIgnoreCase(null, "abc"));
        assertFalse(StringUtils.startsWithIgnoreCase("abcdef", null));
        assertTrue(StringUtils.startsWithIgnoreCase("abcdef", "abc"));
        assertTrue(StringUtils.startsWithIgnoreCase("ABCDEF", "abc"));
    }
    
    @Test
    void testDeleteWhitespace() {
        assertNull(StringUtils.deleteWhitespace(null));
        assertEquals(StringUtils.EMPTY, StringUtils.deleteWhitespace(""));
        assertEquals("abc", StringUtils.deleteWhitespace("abc"));
        assertEquals("abc", StringUtils.deleteWhitespace("   ab  c  "));
    }
    
    @Test
    void testEqualsIgnoreCase() {
        assertTrue(StringUtils.equalsIgnoreCase(null, null));
        assertFalse(StringUtils.equalsIgnoreCase(null, "abc"));
        assertFalse(StringUtils.equalsIgnoreCase("abc", null));
        assertTrue(StringUtils.equalsIgnoreCase("abc", "abc"));
        assertTrue(StringUtils.equalsIgnoreCase("abc", "ABC"));
    }
    
    @Test
    void testSplit() {
        assertNull(StringUtils.split(null, ","));
        assertArrayEquals(new String[0], StringUtils.split("", ","));
        assertArrayEquals(new String[] {"ab", "cd", "ef"}, StringUtils.split("ab cd ef", null));
        assertArrayEquals(new String[] {"ab", "cd", "ef"}, StringUtils.split("ab   cd ef", null));
        assertArrayEquals(new String[] {"ab", "cd", "ef"}, StringUtils.split("ab:cd:ef", ":"));
    }
    
    @Test
    void testTokenizeToStringArray() {
        // Test case 1: Empty string
        String str1 = "";
        String delimiters1 = ",";
        boolean trimTokens1 = true;
        boolean ignoreEmptyTokens1 = false;
        String[] expected1 = new String[0];
        String[] result1 = StringUtils.tokenizeToStringArray(str1, delimiters1, trimTokens1, ignoreEmptyTokens1);
        assertArrayEquals(expected1, result1);
        
        // Test case 2: Null string
        String str2 = null;
        String delimiters2 = " ";
        boolean trimTokens2 = false;
        boolean ignoreEmptyTokens2 = true;
        String[] expected2 = new String[0];
        String[] result2 = StringUtils.tokenizeToStringArray(str2, delimiters2, trimTokens2, ignoreEmptyTokens2);
        assertArrayEquals(expected2, result2);
        
        // Test case 3: Single token
        String str3 = "Hello";
        String delimiters3 = ",";
        boolean trimTokens3 = true;
        boolean ignoreEmptyTokens3 = false;
        String[] expected3 = {"Hello"};
        String[] result3 = StringUtils.tokenizeToStringArray(str3, delimiters3, trimTokens3, ignoreEmptyTokens3);
        assertArrayEquals(expected3, result3);
        
        // Test case 4: Multiple tokens with trimming
        String str4 = "  Hello,  World,  ";
        String delimiters4 = ",";
        boolean trimTokens4 = true;
        boolean ignoreEmptyTokens4 = false;
        String[] expected4 = {"Hello", "World", ""};
        String[] result4 = StringUtils.tokenizeToStringArray(str4, delimiters4, trimTokens4, ignoreEmptyTokens4);
        assertArrayEquals(expected4, result4);
        
        // Test case 5: Multiple tokens with empty tokens ignored
        String str5 = "  ,Hello,  ,World,  ";
        String delimiters5 = ",";
        boolean trimTokens5 = true;
        boolean ignoreEmptyTokens5 = true;
        String[] expected5 = {"Hello", "World"};
        String[] result5 = StringUtils.tokenizeToStringArray(str5, delimiters5, trimTokens5, ignoreEmptyTokens5);
        assertArrayEquals(expected5, result5);
    }
    
    @Test
    void testHasText() {
        // Test case 1: Empty string
        assertFalse(StringUtils.hasText(""));
        
        // Test case 2: String with whitespace only
        assertFalse(StringUtils.hasText("   "));
        
        // Test case 3: Null string
        assertFalse(StringUtils.hasText(null));
        
        // Test case 4: String with non-whitespace characters
        assertTrue(StringUtils.hasText("hello"));
        
        // Test case 5: String with both text and whitespace
        assertTrue(StringUtils.hasText(" hello "));
    }
    
    @Test
    void testCleanPath() {
        // Test case 1: path with no length
        String path1 = "";
        String expected1 = "";
        assertEquals(expected1, StringUtils.cleanPath(path1));
        
        // Test case 2: normal path
        String path2 = "path/to/file";
        String expected2 = "path/to/file";
        assertEquals(expected2, StringUtils.cleanPath(path2));
        
        // Test case 3: path with Windows folder separator
        String path3 = "path\\to\\文件";
        String expected3 = "path/to/文件";
        assertEquals(expected3, StringUtils.cleanPath(path3));
        
        // Test case 4: path with dot
        String path4 = "path/..";
        String expected4 = "";
        assertEquals(expected4, StringUtils.cleanPath(path4));
        
        // Test case 5: path with top path
        String path5 = "path/../top";
        String expected5 = "top";
        assertEquals(expected5, StringUtils.cleanPath(path5));
        
        // Test case 6: path with multiple top path
        String path6 = "path/../../top";
        String expected6 = "../top";
        assertEquals(expected6, StringUtils.cleanPath(path6));
        
        // Test case 7: path with leading colon
        String path7 = "file:../top";
        String expected7 = "file:../top";
        assertEquals(expected7, StringUtils.cleanPath(path7));
        
        // Test case 8: path with leading slash
        String path8 = "file:/path/../file";
        String expected8 = "file:/file";
        assertEquals(expected8, StringUtils.cleanPath(path8));
        
        // Test case 9: path with empty prefix
        String path9 = "file:path/../file";
        String expected9 = "file:file";
        assertEquals(expected9, StringUtils.cleanPath(path9));
        
        // Test case 10: prefix contain separator
        String path10 = "file/:path/../file";
        String expected10 = "file/file";
        assertEquals(expected10, StringUtils.cleanPath(path10));
        
        // Test case 11: dot in file name
        String path11 = "file:/path/to/file.txt";
        String expected11 = "file:/path/to/file.txt";
        assertEquals(expected11, StringUtils.cleanPath(path11));
        
        // Test case 12: dot in path
        String path12 = "file:/path/./file.txt";
        String expected12 = "file:/path/file.txt";
        assertEquals(expected12, StringUtils.cleanPath(path12));
        
        // Test case 13: path with dot and slash
        String path13 = "file:aaa/../";
        String expected13 = "file:./";
        assertEquals(expected13, StringUtils.cleanPath(path13));
    }
    
    @Test
    void testDelimitedListToStringArrayWithNull() {
        assertEquals(0, StringUtils.delimitedListToStringArray(null, ",", "").length);
        assertEquals(1, StringUtils.delimitedListToStringArray("a,b", null, "").length);
    }
    
    @Test
    void testDelimitedListToStringArrayWithEmptyDelimiter() {
        String testCase = "a,b";
        String[] actual = StringUtils.delimitedListToStringArray(testCase, "", "");
        assertEquals(3, actual.length);
        assertEquals("a", actual[0]);
        assertEquals(",", actual[1]);
        assertEquals("b", actual[2]);
    }
    
    @Test
    void testDeleteAny() {
        // Test case 1: inString is empty, charsToDelete is empty
        String inString1 = "";
        String charsToDelete1 = "";
        assertEquals("", StringUtils.deleteAny(inString1, charsToDelete1));
        
        // Test case 2: inString is empty, charsToDelete is not empty
        String inString2 = "";
        String charsToDelete2 = "abc";
        assertEquals("", StringUtils.deleteAny(inString2, charsToDelete2));
        
        // Test case 3: inString is not empty, charsToDelete is empty
        String inString3 = "abc";
        String charsToDelete3 = "";
        assertEquals("abc", StringUtils.deleteAny(inString3, charsToDelete3));
        
        // Test case 4: inString is not empty, charsToDelete is not empty
        String inString4 = "abc";
        String charsToDelete4 = "a";
        assertEquals("bc", StringUtils.deleteAny(inString4, charsToDelete4));
        
        // Test case 5: inString contains special characters
        String inString5 = "abc\n";
        String charsToDelete5 = "\n";
        assertEquals("abc", StringUtils.deleteAny(inString5, charsToDelete5));
        
        // Test case 6: inString not contains special characters
        String inString6 = "abc\n";
        String charsToDelete6 = "d";
        assertEquals("abc\n", StringUtils.deleteAny(inString6, charsToDelete6));
    }
    
    @Test
    void testReplace() {
        // Test case 1: pattern is empty
        assertEquals("abc", StringUtils.replace("abc", "", "a"));
        
        // Test case 2: oldPattern less than newPattern
        assertEquals("aabc", StringUtils.replace("abc", "a", "aa"));
        
        // Test case 3: oldPattern more than newPattern
        assertEquals("dc", StringUtils.replace("abc", "ab", "d"));
    }
    
    @Test
    void testApplyRelativePath() {
        // Test case 1
        String path1 = "/path/to/file";
        String relativePath1 = "subfolder/subfile";
        String expected1 = "/path/to/subfolder/subfile";
        String result1 = StringUtils.applyRelativePath(path1, relativePath1);
        assertEquals(expected1, result1);
        
        // Test case 2
        String path2 = "path/to/file";
        String relativePath2 = "subfolder/subfile";
        String expected2 = "path/to/subfolder/subfile";
        String result2 = StringUtils.applyRelativePath(path2, relativePath2);
        assertEquals(expected2, result2);
        
        // Test case 3
        String path3 = "/path/to/file";
        String relativePath3 = "/subfolder/subfile";
        String expected3 = "/path/to/subfolder/subfile";
        String result3 = StringUtils.applyRelativePath(path3, relativePath3);
        assertEquals(expected3, result3);
        
        //Test case 4
        String path4 = "file";
        String relativePath4 = "/subfolder/subfile";
        String expected4 = "/subfolder/subfile";
        String result4 = StringUtils.applyRelativePath(path4, relativePath4);
        assertEquals(expected4, result4);
    }
    
    @Test
    void testGetFilename() {
        // Test case 1: null path
        String path1 = null;
        String result1 = StringUtils.getFilename(path1);
        assertNull(result1);
        
        // Test case 2: path without separator
        String path2 = "myFile.txt";
        String expectedResult2 = "myFile.txt";
        String result2 = StringUtils.getFilename(path2);
        assertEquals(expectedResult2, result2);
        
        // Test case 3: path with separator
        String path3 = "myPath/myFile.txt";
        String expectedResult3 = "myFile.txt";
        String result3 = StringUtils.getFilename(path3);
        assertEquals(expectedResult3, result3);
        
        // Test case 4: path with multiple separators
        String path4 = "myPath/subPath/myFile.txt";
        String expectedResult4 = "myFile.txt";
        String result4 = StringUtils.getFilename(path4);
        assertEquals(expectedResult4, result4);
    }
    
    @Test
    void testCapitalize() {
        // Test for an empty string
        String str1 = "";
        assertEquals("", StringUtils.capitalize(str1));
        
        // Test for a single word string
        String str2 = "hello";
        assertEquals("Hello", StringUtils.capitalize(str2));
        
        // Test for a multiple word string
        String str3 = "hello world";
        assertEquals("Hello world", StringUtils.capitalize(str3));
        
        // Test for a string with special characters
        String str4 = "!@#$%^&*()";
        assertEquals("!@#$%^&*()", StringUtils.capitalize(str4));
        
        // Test for a string with numbers
        String str5 = "abc123";
        assertEquals("Abc123", StringUtils.capitalize(str5));
    }
}
