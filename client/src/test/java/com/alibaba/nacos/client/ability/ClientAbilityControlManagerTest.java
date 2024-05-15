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

package com.alibaba.nacos.client.ability;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientAbilityControlManagerTest {
    
    ClientAbilityControlManager clientAbilityControlManager;
    
    @BeforeEach
    void setUp() {
        clientAbilityControlManager = new ClientAbilityControlManager();
    }
    
    @Test
    void testInitCurrentNodeAbilities() {
        Map<AbilityMode, Map<AbilityKey, Boolean>> actual = clientAbilityControlManager.initCurrentNodeAbilities();
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey(AbilityMode.SDK_CLIENT));
        // Current not define sdk ability.
        assertEquals(0, actual.get(AbilityMode.SDK_CLIENT).size());
    }
    
    @Test
    void testGetPriority() {
        assertEquals(0, clientAbilityControlManager.getPriority());
    }
}