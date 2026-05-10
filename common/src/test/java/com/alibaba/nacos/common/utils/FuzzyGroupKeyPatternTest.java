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

package com.alibaba.nacos.common.utils;

import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern.GroupKeyState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for FuzzyGroupKeyPattern.
 *
 * @author stone-98
 * @date 2024/3/19
 */
public class FuzzyGroupKeyPatternTest {
    
    @Test
    @DisplayName("generatePattern should create correct pattern string")
    public void testGetGroupKeyPattern() {
        String dataIdPattern = "examplePattern*";
        String group = "exampleGroup";
        String namespace = "exampleNamespace";
        
        String groupKeyPattern =
            FuzzyGroupKeyPattern.generatePattern(dataIdPattern, group, namespace);
        
        assertEquals("exampleNamespace>>exampleGroup>>examplePattern*", groupKeyPattern);
    }
    
    @Test
    @DisplayName("generatePattern with blank resourcePattern should throw exception")
    void testGeneratePatternWithBlankResourcePatternShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> {
            FuzzyGroupKeyPattern.generatePattern("", "group", "namespace");
        });
    }
    
    @Test
    @DisplayName("generatePattern with blank groupPattern should throw exception")
    void testGeneratePatternWithBlankGroupPatternShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> {
            FuzzyGroupKeyPattern.generatePattern("pattern", "", "namespace");
        });
    }
    
    @Test
    @DisplayName("generatePattern with blank namespace should use default namespace")
    void testGeneratePatternWithBlankNamespaceShouldUseDefault() {
        String result = FuzzyGroupKeyPattern.generatePattern("pattern", "group", "");
        assertTrue(result.startsWith("public>>"));
    }
    
    @Test
    @DisplayName("filterMatchedPatterns with empty patterns should return empty set")
    void testFilterMatchedPatternsEmptyPatternsShouldReturnEmptySet() {
        Set<String> result = FuzzyGroupKeyPattern.filterMatchedPatterns(
            Collections.emptySet(), "resource", "group", "namespace");
        assertTrue(result.isEmpty());
    }
    
    @Test
    @DisplayName("filterMatchedPatterns with matching patterns should return matched")
    void testFilterMatchedPatternsShouldReturnMatched() {
        Set<String> patterns = new HashSet<>();
        patterns.add("namespace>>group>>pattern*");
        patterns.add("namespace>>group>>other*");
        
        Set<String> result = FuzzyGroupKeyPattern.filterMatchedPatterns(
            patterns, "patternTest", "group", "namespace");
        
        assertEquals(1, result.size());
        assertTrue(result.contains("namespace>>group>>pattern*"));
    }
    
    @Test
    @DisplayName("matchPattern with accurate match should return true")
    void testMatchPatternWithAccurateMatchShouldReturnTrue() {
        String pattern = "namespace>>group>>exactPattern";
        assertTrue(
            FuzzyGroupKeyPattern.matchPattern(pattern, "exactPattern", "group", "namespace"));
    }
    
    @Test
    @DisplayName("matchPattern with star pattern should return true")
    void testMatchPatternWithStarPatternShouldReturnTrue() {
        String pattern = "namespace>>group>>*";
        assertTrue(FuzzyGroupKeyPattern.matchPattern(pattern, "anything", "group", "namespace"));
    }
    
    @Test
    @DisplayName("matchPattern with prefix pattern should return true")
    void testMatchPatternWithPrefixPatternShouldReturnTrue() {
        String pattern = "namespace>>group>>prefix*";
        assertTrue(FuzzyGroupKeyPattern.matchPattern(pattern, "prefixTest", "group", "namespace"));
        assertFalse(FuzzyGroupKeyPattern.matchPattern(pattern, "otherTest", "group", "namespace"));
    }
    
    @Test
    @DisplayName("matchPattern with suffix pattern should return true")
    void testMatchPatternWithSuffixPatternShouldReturnTrue() {
        String pattern = "namespace>>group>>*suffix";
        assertTrue(FuzzyGroupKeyPattern.matchPattern(pattern, "testsuffix", "group", "namespace"));
        assertFalse(FuzzyGroupKeyPattern.matchPattern(pattern, "testother", "group", "namespace"));
    }
    
    @Test
    @DisplayName("matchPattern with contains pattern should return true")
    void testMatchPatternWithContainsPatternShouldReturnTrue() {
        String pattern = "namespace>>group>>*middle*";
        assertTrue(
            FuzzyGroupKeyPattern.matchPattern(pattern, "testmiddlevalue", "group", "namespace"));
        assertFalse(FuzzyGroupKeyPattern.matchPattern(pattern, "testvalue", "group", "namespace"));
    }
    
    @Test
    @DisplayName("matchPattern with blank namespace should use default")
    void testMatchPatternWithBlankNamespaceShouldUseDefault() {
        String pattern = "public>>group>>pattern*";
        assertTrue(FuzzyGroupKeyPattern.matchPattern(pattern, "patternTest", "group", null));
    }
    
    @Test
    @DisplayName("getNamespaceFromPattern should return namespace")
    void testGetNamespaceFromPatternShouldReturnNamespace() {
        String pattern = "myNamespace>>myGroup>>myPattern";
        assertEquals("myNamespace", FuzzyGroupKeyPattern.getNamespaceFromPattern(pattern));
    }
    
    @Test
    @DisplayName("diffGroupKeys with empty sets should return empty list")
    void testDiffGroupKeysEmptySetsShouldReturnEmptyList() {
        List<GroupKeyState> result = FuzzyGroupKeyPattern.diffGroupKeys(
            Collections.emptySet(), Collections.emptySet());
        assertTrue(result.isEmpty());
    }
    
    @Test
    @DisplayName("diffGroupKeys with add keys should return add states")
    void testDiffGroupKeysWithAddKeysShouldReturnAddStates() {
        Set<String> basedKeys = new HashSet<>(Arrays.asList("key1", "key2"));
        Set<String> followedKeys = new HashSet<>(Arrays.asList("key1"));
        
        List<GroupKeyState> result = FuzzyGroupKeyPattern.diffGroupKeys(basedKeys, followedKeys);
        
        assertEquals(1, result.size());
        assertTrue(result.get(0).isExist());
        assertEquals("key2", result.get(0).getGroupKey());
    }
    
    @Test
    @DisplayName("diffGroupKeys with remove keys should return remove states")
    void testDiffGroupKeysWithRemoveKeysShouldReturnRemoveStates() {
        Set<String> basedKeys = new HashSet<>(Arrays.asList("key1"));
        Set<String> followedKeys = new HashSet<>(Arrays.asList("key1", "key2"));
        
        List<GroupKeyState> result = FuzzyGroupKeyPattern.diffGroupKeys(basedKeys, followedKeys);
        
        assertEquals(1, result.size());
        assertFalse(result.get(0).isExist());
        assertEquals("key2", result.get(0).getGroupKey());
    }
    
    // ========== GroupKeyState Inner Class Tests ==========
    
    @Test
    @DisplayName("GroupKeyState constructor should set fields correctly")
    void testGroupKeyStateConstructorShouldSetFields() {
        GroupKeyState state = new GroupKeyState("testKey", true);
        assertEquals("testKey", state.getGroupKey());
        assertTrue(state.isExist());
    }
    
    @Test
    @DisplayName("GroupKeyState setters should update fields")
    void testGroupKeyStateSettersShouldUpdateFields() {
        GroupKeyState state = new GroupKeyState("initialKey", false);
        state.setGroupKey("newKey");
        state.setExist(true);
        assertEquals("newKey", state.getGroupKey());
        assertTrue(state.isExist());
    }
    
    @Test
    @DisplayName("GroupKeyState isExist should return correct value")
    void testGroupKeyStateIsExistShouldReturnCorrectValue() {
        GroupKeyState stateTrue = new GroupKeyState("key", true);
        GroupKeyState stateFalse = new GroupKeyState("key", false);
        assertTrue(stateTrue.isExist());
        assertFalse(stateFalse.isExist());
    }
}
