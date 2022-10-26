package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.common.spi.NacosServiceLoader;

import java.util.Collection;

public class PersistRuleActivatorProxy {
    
    private static PersistRuleActivator INSTANCE = null;
    
    static {
        Collection<PersistRuleActivator> persistRuleActivators = NacosServiceLoader.load(PersistRuleActivator.class);
        INSTANCE = persistRuleActivators.isEmpty() ? null : null;
    }
    
    public static PersistRuleActivator getInstance() {
        return INSTANCE;
    }
    
    public PersistRuleActivatorProxy() {
    
    }
}
