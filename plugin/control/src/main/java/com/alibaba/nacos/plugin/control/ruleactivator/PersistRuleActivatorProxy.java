package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.common.spi.NacosServiceLoader;

import java.util.Collection;

public class PersistRuleActivatorProxy {
    
    private static PersistRuleActivator INSTANCE = new PersistRuleActivator() {
        @Override
        public void saveConnectionRule(String ruleContent) throws Exception {
        
        }
        
        @Override
        public String getConnectionRule() {
            return null;
        }
        
        @Override
        public void saveTpsRule(String pointName, String ruleContent) throws Exception {
        
        }
        
        @Override
        public String getTpsRule(String pointName) {
            return null;
        }
    };
    
    public static PersistRuleActivator getInstace() {
        return INSTANCE;
    }
    
    
    public PersistRuleActivatorProxy() {
        Collection<PersistRuleActivator> load = NacosServiceLoader.load(PersistRuleActivator.class);
        
    }
}
