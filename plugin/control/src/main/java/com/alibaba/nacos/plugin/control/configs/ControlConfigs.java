package com.alibaba.nacos.plugin.control.configs;

import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ControlConfigs {
    
    private static ControlConfigs instance = null;
    
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
    
    @Value("${nacos.plugin.control.connection.manager:nacos}")
    private String connectionManager = "nacos";
    
    @Value("${nacos.plugin.control.rule.external.activator:internalconfigcenter}")
    private String ruleExternalActivator = "internalconfigcenter";
    
    @Value("${nacos.plugin.control.rule.parser:nacos}")
    private String ruleParser = "nacos";
    
    public String getTpsBarrierCreator() {
        return tpsBarrierCreator;
    }
    
    public void setTpsBarrierCreator(String tpsBarrierCreator) {
        this.tpsBarrierCreator = tpsBarrierCreator;
    }
    
    public String getRuleExternalActivator() {
        return ruleExternalActivator;
    }
    
    public void setRuleExternalActivator(String ruleExternalActivator) {
        this.ruleExternalActivator = ruleExternalActivator;
    }
    
    public String getRuleParser() {
        return ruleParser;
    }
    
    public void setRuleParser(String ruleParser) {
        this.ruleParser = ruleParser;
    }
    
    public String getConnectionManager() {
        return connectionManager;
    }
    
    public void setConnectionManager(String connectionManager) {
        this.connectionManager = connectionManager;
    }
}
