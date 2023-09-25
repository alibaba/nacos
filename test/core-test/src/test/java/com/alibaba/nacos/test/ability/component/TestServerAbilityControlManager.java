package com.alibaba.nacos.test.ability.component;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.core.ability.control.ServerAbilityControlManager;

import java.util.HashMap;
import java.util.Map;

public class TestServerAbilityControlManager extends ServerAbilityControlManager {
    
    @Override
    protected Map<AbilityKey, Boolean> initCurrentNodeAbilities() {
        Map<AbilityKey, Boolean> map = new HashMap<>();
        map.put(AbilityKey.SERVER_TEST_1, true);
        map.put(AbilityKey.SERVER_TEST_2, false);
        return map;
    }
}
