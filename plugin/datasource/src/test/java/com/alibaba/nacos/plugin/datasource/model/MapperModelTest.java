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

package com.alibaba.nacos.plugin.datasource.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperModelTest {
    
    @Test
    void testMapperContextParametersAndPaging() {
        MapperContext context = new MapperContext(10, 20);
        
        context.putWhereParameter("id", 1L);
        context.putUpdateParameter("name", "nacos");
        context.putContextParameter("needContent", "true");
        context.setStartRow(30);
        context.setPageSize(40);
        
        assertEquals(1L, context.getWhereParameter("id"));
        assertEquals("nacos", context.getUpdateParameter("name"));
        assertEquals("true", context.getContextParameter("needContent"));
        assertEquals(30, context.getStartRow());
        assertEquals(40, context.getPageSize());
        assertTrue(context.toString().contains("whereParamMap"));
        assertEquals(context, context);
        assertNotEquals(context, new MapperContext());
    }
    
    @Test
    void testMapperResultAccessors() {
        MapperResult result = new MapperResult("SELECT 1", Collections.singletonList(1));
        
        assertEquals("SELECT 1", result.getSql());
        assertEquals(Collections.singletonList(1), result.getParamList());
        assertTrue(result.toString().contains("SELECT 1"));
        assertEquals(result, result);
        assertNotEquals(result, new MapperResult());
        
        result.setSql("SELECT ?");
        result.setParamList(Arrays.asList("a", "b"));
        assertEquals("SELECT ?", result.getSql());
        assertEquals(Arrays.asList("a", "b"), result.getParamList());
        assertSame(result.getParamList(), result.getParamList());
    }
}
