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

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityMode;
import com.alibaba.nacos.common.ability.AbstractAbilityControlManager;

import java.util.HashMap;
import java.util.Map;

public class HigherMockAbilityManager extends AbstractAbilityControlManager {
    
    @Override
    protected Map<AbilityMode, Map<AbilityKey, Boolean>> initCurrentNodeAbilities() {
        Map<AbilityKey, Boolean> abilities = new HashMap<>();
        abilities.put(AbilityKey.SERVER_TEST_1, true);
        Map<AbilityMode, Map<AbilityKey, Boolean>> result = new HashMap<>();
        result.put(AbilityMode.SERVER, abilities);
        return result;
    }
    
    @Override
    public int getPriority() {
        return 100;
    }
}
