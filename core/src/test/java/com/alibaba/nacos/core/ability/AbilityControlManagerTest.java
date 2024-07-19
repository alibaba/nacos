/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.ability;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityMode;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AbilityControlManagerTest {
    
    private TestServerAbilityControlManager serverAbilityControlManager = new TestServerAbilityControlManager();
    
    @BeforeEach
    void inject() {
        Map<String, Boolean> newTable = new HashMap<>();
        newTable.put(AbilityKey.SERVER_TEST_1.getName(), true);
        serverAbilityControlManager.setCurrentSupportingAbility(newTable);
    }
    
    @Test
    void testCurrentNodeAbility() {
        Set<String> keySet = serverAbilityControlManager.getCurrentNodeAbilities(AbilityMode.SERVER).keySet();
        // diable all
        keySet.forEach(key -> serverAbilityControlManager.disableCurrentNodeAbility(AbilityKey.getEnum(AbilityMode.SERVER, key)));
        // get all
        keySet.forEach(key -> {
            assertNotEquals(AbilityStatus.SUPPORTED,
                    serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.getEnum(AbilityMode.SERVER, key)));
        });
        // enable all
        keySet.forEach(key -> serverAbilityControlManager.enableCurrentNodeAbility(AbilityKey.getEnum(AbilityMode.SERVER, key)));
        // get all
        keySet.forEach(key -> {
            assertEquals(AbilityStatus.SUPPORTED,
                    serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.getEnum(AbilityMode.SERVER, key)));
        });
    }
    
}


