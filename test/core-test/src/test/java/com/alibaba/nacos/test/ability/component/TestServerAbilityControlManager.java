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

package com.alibaba.nacos.test.ability.component;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityMode;
import com.alibaba.nacos.core.ability.control.ServerAbilityControlManager;

import java.util.HashMap;
import java.util.Map;

public class TestServerAbilityControlManager extends ServerAbilityControlManager {
    
    @Override
    protected Map<AbilityMode, Map<AbilityKey, Boolean>> initCurrentNodeAbilities() {
        Map<AbilityKey, Boolean> map = new HashMap<>();
        map.put(AbilityKey.SERVER_TEST_1, true);
        map.put(AbilityKey.SERVER_TEST_2, false);
        HashMap<AbilityMode, Map<AbilityKey, Boolean>> res = new HashMap<>();
        res.put(AbilityMode.SERVER, map);
        
        Map<AbilityKey, Boolean> map1 = new HashMap<>();
        map1.put(AbilityKey.SDK_CLIENT_TEST_1, true);
        res.put(AbilityMode.SDK_CLIENT, map1);
        
        Map<AbilityKey, Boolean> map2 = new HashMap<>();
        map2.put(AbilityKey.CLUSTER_CLIENT_TEST_1, true);
        res.put(AbilityMode.CLUSTER_CLIENT, map2);
        return res;
    }
}
