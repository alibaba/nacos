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

package com.alibaba.nacos.core.ability.control;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.register.impl.ServerAbilities;
import com.alibaba.nacos.common.ability.AbstractAbilityControlManager;
import com.alibaba.nacos.core.ability.config.AbilityConfigs;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**.
 * @author Daydreamer
 * @description {@link AbstractAbilityControlManager} for nacos-server.
 * @date 2022/7/13 21:14
 **/
public class ServerAbilityControlManager extends AbstractAbilityControlManager {
    
    public ServerAbilityControlManager() {
    }
    
    @Override
    protected Map<String, Boolean> initCurrentNodeAbilities() {
        // static abilities
        Map<String, Boolean> staticAbilities = AbilityKey.mapStr(ServerAbilities.getStaticAbilities());
        // all function server can support
        Set<String> abilityKeys = staticAbilities.keySet();
        Map<String, Boolean> abilityTable = new HashMap<>(abilityKeys.size());
        // if not define in config, then load from ServerAbilities
        Set<String> unIncludedInConfig = new HashSet<>();
        abilityKeys.forEach(abilityKey -> {
            String key = AbilityConfigs.PREFIX + abilityKey;
            try {
                Boolean property = EnvUtil.getProperty(key, Boolean.class);
                // if not null
                if (property != null) {
                    abilityTable.put(abilityKey, property);
                } else {
                    unIncludedInConfig.add(abilityKey);
                }
            } catch (Exception e) {
                // from ServerAbilities
                unIncludedInConfig.add(abilityKey);
            }
        });
        // load from ServerAbilities
        unIncludedInConfig.forEach(abilityKey -> abilityTable.put(abilityKey, staticAbilities.get(abilityKey)));
        return abilityTable;
    }
    
    @Override
    public int getPriority() {
        return 1;
    }

}
