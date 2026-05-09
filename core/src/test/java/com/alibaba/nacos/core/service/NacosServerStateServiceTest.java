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
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.alibaba.nacos.core.service;

import com.alibaba.nacos.sys.module.ModuleState;
import com.alibaba.nacos.sys.module.ModuleStateHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * NacosServerStateService unit test.
 */
@ExtendWith(MockitoExtension.class)
class NacosServerStateServiceTest {
    
    @Test
    void getServerStateEmpty() {
        try (MockedStatic<ModuleStateHolder> mocked = mockStatic(ModuleStateHolder.class)) {
            ModuleStateHolder holder = org.mockito.Mockito.mock(ModuleStateHolder.class);
            mocked.when(ModuleStateHolder::getInstance).thenReturn(holder);
            when(holder.getAllModuleStates()).thenReturn(Collections.emptySet());
            
            NacosServerStateService service = new NacosServerStateService();
            Map<String, String> state = service.getServerState();
            
            assertNotNull(state);
            assertEquals(0, state.size());
        }
    }
    
    @Test
    void getServerStateWithModules() {
        try (MockedStatic<ModuleStateHolder> mocked = mockStatic(ModuleStateHolder.class)) {
            ModuleStateHolder holder = org.mockito.Mockito.mock(ModuleStateHolder.class);
            mocked.when(ModuleStateHolder::getInstance).thenReturn(holder);
            
            ModuleState moduleState = new ModuleState("test-module");
            moduleState.newState("key1", "value1");
            moduleState.newState("key2", 100);
            moduleState.newState("key3", null);
            
            Set<ModuleState> states = new HashSet<>();
            states.add(moduleState);
            when(holder.getAllModuleStates()).thenReturn(states);
            
            NacosServerStateService service = new NacosServerStateService();
            Map<String, String> state = service.getServerState();
            
            assertNotNull(state);
            assertEquals(3, state.size());
            assertEquals("value1", state.get("key1"));
            assertEquals("100", state.get("key2"));
            assertNull(state.get("key3"));
        }
    }
}
