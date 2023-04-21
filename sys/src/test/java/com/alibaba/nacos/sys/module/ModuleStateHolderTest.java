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

package com.alibaba.nacos.sys.module;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ModuleStateHolderTest {
    
    private Map<String, ModuleState> moduleStateMap;
    
    @Before
    public void setUp() throws Exception {
        moduleStateMap = (Map<String, ModuleState>) ReflectionTestUtils
                .getField(ModuleStateHolder.getInstance(), ModuleStateHolder.class, "moduleStates");
    }
    
    @After
    public void tearDown() throws Exception {
    }
    
    @Test
    public void testGetModuleState() {
        assertNotNull(ModuleStateHolder.getInstance().getModuleState("mock"));
    }
    
    @Test
    public void testGetAllModuleStates() {
        assertEquals(2, ModuleStateHolder.getInstance().getAllModuleStates().size());
    }
    
    @Test
    public void testGetStateValueByNameFound() {
        assertEquals("test", ModuleStateHolder.getInstance().getStateValueByName("mock", "test"));
        assertEquals("test", ModuleStateHolder.getInstance().getStateValueByName("mock", "test", "aaa"));
    }
    
    @Test
    public void testGetStateValueByNameWithoutModuleState() {
        assertEquals("", ModuleStateHolder.getInstance().getStateValueByName("non-exist", "test"));
        assertEquals("aaa", ModuleStateHolder.getInstance().getStateValueByName("non-exist", "test", "aaa"));
    }
    
    @Test
    public void testGetStateValueByNameWithoutStateName() {
        assertEquals("", ModuleStateHolder.getInstance().getStateValueByName("mock", "non-exist"));
        assertEquals("aaa", ModuleStateHolder.getInstance().getStateValueByName("mock", "non-exist", "aaa"));
    }
    
    @Test
    public void testSearchStateValue() {
        assertEquals("test", ModuleStateHolder.getInstance().searchStateValue("test", "aaa"));
        assertEquals("aaa", ModuleStateHolder.getInstance().searchStateValue("non-exist", "aaa"));
    }
    
}