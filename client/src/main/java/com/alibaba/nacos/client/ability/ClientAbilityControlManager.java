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

package com.alibaba.nacos.client.ability;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityMode;
import com.alibaba.nacos.api.ability.register.impl.SdkClientAbilities;
import com.alibaba.nacos.common.ability.AbstractAbilityControlManager;

import java.util.HashMap;
import java.util.Map;

/**.
 * @author Daydreamer
 * @description {@link AbstractAbilityControlManager} for nacos-client.
 * @date 2022/7/13 13:38
 **/
public class ClientAbilityControlManager extends AbstractAbilityControlManager {
    
    public ClientAbilityControlManager() {
    }
    
    @Override
    protected Map<AbilityMode, Map<AbilityKey, Boolean>> initCurrentNodeAbilities() {
        Map<AbilityMode, Map<AbilityKey, Boolean>> abilities = new HashMap<>(1);
        abilities.put(AbilityMode.SDK_CLIENT, SdkClientAbilities.getStaticAbilities());
        return abilities;
    }

    @Override
    public int getPriority() {
        // if server ability manager exist, you should choose the server one
        return 0;
    }
    
}
