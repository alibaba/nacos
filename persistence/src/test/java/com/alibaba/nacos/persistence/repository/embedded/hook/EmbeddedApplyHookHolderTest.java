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

package com.alibaba.nacos.persistence.repository.embedded.hook;

import com.alibaba.nacos.consistency.entity.WriteRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmbeddedApplyHookHolderTest {
    
    Set<EmbeddedApplyHook> cached;
    
    @BeforeEach
    void setUp() {
        cached = new HashSet<>(EmbeddedApplyHookHolder.getInstance().getAllHooks());
        EmbeddedApplyHookHolder.getInstance().getAllHooks().clear();
    }
    
    @AfterEach
    void tearDown() {
        EmbeddedApplyHookHolder.getInstance().getAllHooks().clear();
        EmbeddedApplyHookHolder.getInstance().getAllHooks().addAll(cached);
    }
    
    @Test
    void testRegister() {
        assertEquals(0, EmbeddedApplyHookHolder.getInstance().getAllHooks().size());
        EmbeddedApplyHook mockHook = new EmbeddedApplyHook() {
            @Override
            public void afterApply(WriteRequest log) {
            
            }
        };
        assertEquals(1, EmbeddedApplyHookHolder.getInstance().getAllHooks().size());
        assertEquals(mockHook, EmbeddedApplyHookHolder.getInstance().getAllHooks().iterator().next());
    }
}