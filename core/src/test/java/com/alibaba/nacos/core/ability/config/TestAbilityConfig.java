package com.alibaba.nacos.core.ability.config;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.common.ability.inter.AbilityHandlerRegistry;

import java.util.Set;

public class TestAbilityConfig extends AbilityConfigs {
    
    public TestAbilityConfig() {
        Set<AbilityKey> serverAbilityKeys = super.getServerAbilityKeys();
        serverAbilityKeys.add(AbilityKey.TEST_1);
        serverAbilityKeys.add(AbilityKey.TEST_2);
    }
    
    public void setAbilityControlManager(AbilityHandlerRegistry abilityHandlerRegistry) {
        super.setAbilityHandlerRegistry(abilityHandlerRegistry);
    }
}
