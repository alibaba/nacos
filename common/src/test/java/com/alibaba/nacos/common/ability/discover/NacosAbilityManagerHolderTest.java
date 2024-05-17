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

package com.alibaba.nacos.common.ability.discover;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class NacosAbilityManagerHolderTest {
    
    @BeforeEach
    void setUp() throws Exception {
        NacosAbilityManagerHolder.getInstance();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        Field abilityControlManagerField = NacosAbilityManagerHolder.class.getDeclaredField("abstractAbilityControlManager");
        abilityControlManagerField.setAccessible(true);
        abilityControlManagerField.set(null, null);
    }
    
    @Test
    void testGetInstance() {
        assertNotNull(NacosAbilityManagerHolder.getInstance());
    }
    
    @Test
    void testGetInstanceByType() {
        assertNotNull(NacosAbilityManagerHolder.getInstance(HigherMockAbilityManager.class));
    }
    
    @Test
    void testGetInstanceByWrongType() {
        assertThrows(ClassCastException.class, () -> {
            assertNotNull(NacosAbilityManagerHolder.getInstance(LowerMockAbilityManager.class));
        });
    }
}