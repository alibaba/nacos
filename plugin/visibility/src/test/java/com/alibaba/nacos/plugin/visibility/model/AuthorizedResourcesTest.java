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

package com.alibaba.nacos.plugin.visibility.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizedResourcesTest {
    
    @Test
    void testAccessorsAndDefaultResources() {
        AuthorizedResources resources = new AuthorizedResources();
        
        assertTrue(resources.getResources().isEmpty());
        resources.setResourceType("skill");
        resources.setResources(Arrays.asList("a", "b"));
        
        assertEquals("skill", resources.getResourceType());
        assertEquals(Arrays.asList("a", "b"), resources.getResources());
    }
    
    @Test
    void testVisibilityQueryContextAccessorsAndBasePredicateValues() {
        VisibilityQueryContext context = new VisibilityQueryContext();
        
        context.setNamespaceId("namespace");
        context.setResourceType("skill");
        
        assertEquals("namespace", context.getNamespaceId());
        assertEquals("skill", context.getResourceType());
        assertEquals(BaseVisibilityPredicate.ALL, BaseVisibilityPredicate.valueOf("ALL"));
        assertEquals(BaseVisibilityPredicate.PUBLIC_AND_OWNER, BaseVisibilityPredicate.values()[3]);
    }
}
