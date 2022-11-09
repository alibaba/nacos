package com.alibaba.nacos.plugin.control.configs;

import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ControlConfigs {
    
    private static  ControlConfigs instance = null;
    
    public static ControlConfigs getInstance() {
        if (instance == null) {
            instance = ApplicationUtils.getBean(ControlConfigs.class);
        }
        return instance;
    }
    
    public static void setInstance(ControlConfigs instance) {
        ControlConfigs.instance = instance;
    }
    
    @Value("${nacos.plugin.control.tps.barrier.creator:localsimplecount}")
    private String tpsBarrierCreator = "localsimplecount";
    
    @Value("${nacos.plugin.control.rule.persist.activator:internalconfigcenter}")
    private String rulePersistActivator = "internalconfigcenter";
    
    @Value("${nacos.plugin.control.rule.parser:default}")
    private String ruleParser = "default";
    
    public String getTpsBarrierCreator() {
        return tpsBarrierCreator;
    }
    
    public void setTpsBarrierCreator(String tpsBarrierCreator) {
        this.tpsBarrierCreator = tpsBarrierCreator;
    }
    
    public String getRulePersistActivator() {
        return rulePersistActivator;
    }
    
    public void setRulePersistActivator(String rulePersistActivator) {
        this.rulePersistActivator = rulePersistActivator;
    }
    
    public String getRuleParser() {
        return ruleParser;
    }
    
    public void setRuleParser(String ruleParser) {
        this.ruleParser = ruleParser;
    }
}
