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
        HashMap res = new HashMap<>();
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
