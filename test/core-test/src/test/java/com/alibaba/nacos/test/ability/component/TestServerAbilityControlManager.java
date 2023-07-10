package com.alibaba.nacos.test.ability.component;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.core.ability.control.ServerAbilityControlManager;

import java.util.HashMap;
import java.util.Map;

public class TestServerAbilityControlManager extends ServerAbilityControlManager {
    
    @Override
    protected Map<String, Boolean> initCurrentNodeAbilities() {
        Map<String, Boolean> map = new HashMap<>();
        map.put(AbilityKey.TEST_1.getName(), true);
        map.put(AbilityKey.TEST_2.getName(), false);
        return map;
    }
}
