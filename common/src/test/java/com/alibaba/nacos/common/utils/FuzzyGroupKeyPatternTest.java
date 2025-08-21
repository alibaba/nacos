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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GroupKeyPatternUtilsTest.
 *
 * @author stone-98
 * @date 2024/3/19
 */
public class FuzzyGroupKeyPatternTest {
    
    @Test
    public void testGetGroupKeyPattern() {
        String dataIdPattern = "examplePattern*";
        String group = "exampleGroup";
        String namespace = "exampleNamespace";
        
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern(dataIdPattern, group, namespace);
        
        assertEquals("exampleNamespace>>exampleGroup>>examplePattern*", groupKeyPattern);
    }
    
}

