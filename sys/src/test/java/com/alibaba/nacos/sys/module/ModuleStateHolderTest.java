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

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModuleStateHolderTest {
    
    private Map<String, ModuleState> moduleStateMap;
    
    private ConfigurableEnvironment environment;
    
    @BeforeEach
    void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        moduleStateMap = (Map<String, ModuleState>) ReflectionTestUtils.getField(ModuleStateHolder.getInstance(),
                ModuleStateHolder.class, "moduleStates");
    }
    
    @AfterEach
    void tearDown() throws Exception {
    }
    
    @Test
    void testGetModuleState() {
        assertNotNull(ModuleStateHolder.getInstance().getModuleState("mock"));
    }
    
    @Test
    void testGetAllModuleStates() {
        assertEquals(3, ModuleStateHolder.getInstance().getAllModuleStates().size());
    }
    
    @Test
    void testGetStateValueByNameFound() {
        assertEquals("test", ModuleStateHolder.getInstance().getStateValueByName("mock", "test"));
        assertEquals("test", ModuleStateHolder.getInstance().getStateValueByName("mock", "test", "aaa"));
    }
    
    @Test
    void testGetStateValueByNameWithoutModuleState() {
        assertEquals("", ModuleStateHolder.getInstance().getStateValueByName("non-exist", "test"));
        assertEquals("aaa", ModuleStateHolder.getInstance().getStateValueByName("non-exist", "test", "aaa"));
    }
    
    @Test
    void testGetStateValueByNameForRebuildState() {
        int lastValue = ModuleStateHolder.getInstance().getStateValueByName("rebuild-mock", "re-test", 0);
        assertEquals(lastValue + 1, ModuleStateHolder.getInstance().getStateValueByName("rebuild-mock", "re-test", 0));
    }
    
    @Test
    void testGetStateValueByNameWithoutStateName() {
        assertEquals("", ModuleStateHolder.getInstance().getStateValueByName("mock", "non-exist"));
        assertEquals("aaa", ModuleStateHolder.getInstance().getStateValueByName("mock", "non-exist", "aaa"));
    }
    
    @Test
    void testSearchStateValue() {
        assertEquals("test", ModuleStateHolder.getInstance().searchStateValue("test", "aaa"));
        assertEquals("aaa", ModuleStateHolder.getInstance().searchStateValue("non-exist", "aaa"));
    }
    
}
