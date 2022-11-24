package com.alibaba.nacos.plugin.control.configs;

import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ControlConfigs {
    
    private static ControlConfigs instance = null;
    
    public static ControlConfigs getInstance() {
        if (instance == null) {
            try {
                instance = ApplicationUtils.getBean(ControlConfigs.class);
            } catch (Throwable throwable) {
                Loggers.CONTROL
                        .warn("Fail to get control configs bean from spring context,use default constructor instance");
                instance = new ControlConfigs();
            }
        }
        return instance;
    }
    
    public static void setInstance(ControlConfigs instance) {
        ControlConfigs.instance = instance;
    }
    
    @Value("${nacos.plugin.control.tps.enabled:false}")
    private boolean tpsEnabled = false;
    
    @Value("${nacos.plugin.control.tps.barrier.creator:localsimplecount}")
    private String tpsBarrierCreator = "localsimplecount";
    
    @Value("${nacos.plugin.control.tps.barrier.rule.creator:nacos}")
    private String tpsRuleBarrierCreator = "nacos";
    
    @Value("${nacos.plugin.control.connection.enabled:false}")
    private boolean connectionEnabled = false;
    
    @Value("${nacos.plugin.control.connection.manager:nacos}")
    private String connectionManager = "nacos";
    
    @Value("${nacos.plugin.control.rule.external.storage:internalconfigcenter}")
    private String ruleExternalStorage = "internalconfigcenter";
    
    @Value("${nacos.plugin.control.rule.parser:nacos}")
    private String ruleParser = "nacos";
    
    public boolean isTpsEnabled() {
        return tpsEnabled;
    }
    
    public void setTpsEnabled(boolean tpsEnabled) {
        this.tpsEnabled = tpsEnabled;
    }
    
    public boolean isConnectionEnabled() {
        return connectionEnabled;
    }
    
    public void setConnectionEnabled(boolean connectionEnabled) {
        this.connectionEnabled = connectionEnabled;
    }
    
    public String getTpsBarrierCreator() {
        return tpsBarrierCreator;
    }
    
    public void setTpsBarrierCreator(String tpsBarrierCreator) {
        this.tpsBarrierCreator = tpsBarrierCreator;
    }
    
    public String getTpsRuleBarrierCreator() {
        return tpsRuleBarrierCreator;
    }
    
    public void setTpsRuleBarrierCreator(String tpsRuleBarrierCreator) {
        this.tpsRuleBarrierCreator = tpsRuleBarrierCreator;
    }
    
    public String getRuleExternalStorage() {
        return ruleExternalStorage;
    }
    
    public void setRuleExternalStorage(String ruleExternalStorage) {
        this.ruleExternalStorage = ruleExternalStorage;
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
