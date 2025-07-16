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

package com.alibaba.nacos.api.ability.register.impl;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdkClientAbilitiesTest {
    
    @Test
    void testGetStaticAbilities() {
        assertFalse(SdkClientAbilities.getStaticAbilities().isEmpty());
        assertTrue(SdkClientAbilities.getStaticAbilities().get(AbilityKey.SDK_CLIENT_FUZZY_WATCH));
        assertTrue(SdkClientAbilities.getStaticAbilities().get(AbilityKey.SDK_CLIENT_DISTRIBUTED_LOCK));
        assertTrue(SdkClientAbilities.getStaticAbilities().get(AbilityKey.SDK_MCP_REGISTRY));
    }
}