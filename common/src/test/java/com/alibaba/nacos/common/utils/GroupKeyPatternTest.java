/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

import java.util.HashSet;
import java.util.Set;

/**
 * GroupKeyPatternUtilsTest.
 *
 * @author stone-98
 * @date 2024/3/19
 */
public class GroupKeyPatternTest {
    
    @Test
    public void testGetGroupKeyPatternWithNamespace() {
        String dataIdPattern = "examplePattern*";
        String group = "exampleGroup";
        String namespace = "exampleNamespace";
        
        String groupKeyPattern = GroupKeyPattern.generateFuzzyListenGroupKeyPattern(dataIdPattern, group, namespace);
        
        Assert.assertEquals("exampleNamespace>>exampleGroup@@examplePattern*", groupKeyPattern);
    }
    
    @Test
    public void testGetGroupKeyPatternWithoutNamespace() {
        String dataIdPattern = "examplePattern*";
        String group = "exampleGroup";
        
        String groupKeyPattern = GroupKeyPattern.generateFuzzyListenGroupKeyPattern(dataIdPattern, group);
        
        Assert.assertEquals("exampleGroup@@examplePattern*", groupKeyPattern);
    }
    
    @Test
    public void testIsMatchPatternWithNamespace() {
        String groupKey = "examplePattern+exampleGroup+exampleNamespace";
        String groupKeyPattern = "exampleNamespace>>exampleGroup@@examplePattern*";
        
        boolean result = GroupKeyPattern.isMatchPatternWithNamespace(groupKey, groupKeyPattern);
        
        Assert.assertTrue(result);
    }
    
    @Test
    public void testIsMatchPatternWithoutNamespace() {
        String groupKey = "examplePattern+exampleGroup+exampleNamespace";
        String groupKeyPattern = "exampleNamespace>>exampleGroup@@*";
        
        boolean result = GroupKeyPattern.isMatchPatternWithoutNamespace(groupKey, groupKeyPattern);
        
        Assert.assertTrue(result);
    }
    
    @Test
    public void testIsMatchPatternWithoutNamespaceWithDataIdPrefix() {
        String groupKey = "examplePattern+exampleGroup+exampleNamespace";
        String groupKeyPattern = "exampleNamespace>>exampleGroup@@examplePattern*";
        
        boolean result = GroupKeyPattern.isMatchPatternWithoutNamespace(groupKey, groupKeyPattern);
        
        Assert.assertTrue(result);
    }
    
    @Test
    public void testGetConfigMatchedPatternsWithoutNamespace() {
        String dataId = "exampleDataId";
        String group = "exampleGroup";
        Set<String> groupKeyPatterns = new HashSet<>();
        groupKeyPatterns.add("exampleGroup@@exampleDataId*");
        groupKeyPatterns.add("exampleGroup@@exampleDataI*");
        
        Set<String> matchedPatterns = GroupKeyPattern.getConfigMatchedPatternsWithoutNamespace(dataId, group,
                groupKeyPatterns);
        
        Assert.assertEquals(2, matchedPatterns.size());
        Assert.assertTrue(matchedPatterns.contains("exampleGroup@@exampleDataId*"));
        Assert.assertTrue(matchedPatterns.contains("exampleGroup@@exampleDataI*"));
    }
    
    @Test
    public void testGetNamespace() {
        String groupKeyPattern = "exampleNamespace>>exampleGroup@@examplePattern";
        
        String namespace = GroupKeyPattern.getNamespace(groupKeyPattern);
        
        Assert.assertEquals("exampleNamespace", namespace);
    }
    
    @Test
    public void testGetGroup() {
        String groupKeyPattern = "exampleNamespace>>exampleGroup@@examplePattern";
        
        String group = GroupKeyPattern.getGroup(groupKeyPattern);
        
        Assert.assertEquals("exampleGroup", group);
    }
    
    @Test
    public void testGetDataIdPattern() {
        String groupKeyPattern = "exampleNamespace>>exampleGroup@@examplePattern";
        
        String dataIdPattern = GroupKeyPattern.getDataIdPattern(groupKeyPattern);
        
        Assert.assertEquals("examplePattern", dataIdPattern);
    }
    
    @Test
    public void testGetPatternRemovedNamespace() {
        String groupKeyPattern = "exampleNamespace>>exampleGroup@@examplePattern";
        
        String patternRemovedNamespace = GroupKeyPattern.getPatternRemovedNamespace(groupKeyPattern);
        
        Assert.assertEquals("exampleGroup@@examplePattern", patternRemovedNamespace);
    }
}

