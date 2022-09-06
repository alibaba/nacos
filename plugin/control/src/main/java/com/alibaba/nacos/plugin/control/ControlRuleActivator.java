package com.alibaba.nacos.plugin.control;

import com.alibaba.nacos.plugin.control.capacity.CapacityControlManager;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.TpsControlRule;

import java.util.Map;
import java.util.Set;

/**
 * control rule activator.
 */
public abstract class ControlRuleActivator {
    
    TpsControlManager tpsControlManager;
    
    ConnectionControlManager connectionControlManager;
    
    CapacityControlManager capacityControlManager;
    
    public ControlRuleActivator injectTpsControl(TpsControlManager tpsControlManager) {
        this.tpsControlManager = tpsControlManager;
        return this;
    }
    
    public ControlRuleActivator injectConnectionControl(ConnectionControlManager connectionControlManager) {
        this.connectionControlManager = connectionControlManager;
        return this;
    }
    
    public ControlRuleActivator injectTpsControl(CapacityControlManager capacityControlManager) {
        this.capacityControlManager = capacityControlManager;
        return this;
    }
    
    public void initTpsRule() {
        Map<String, TpsControlRule> tpsControlRuleMap = loadInitTpsRules();
        Set<Map.Entry<String, TpsControlRule>> entries = tpsControlRuleMap.entrySet();
        for (Map.Entry<String, TpsControlRule> entry : entries) {
            tpsControlManager.applyTpsRule(entry.getKey(), entry.getValue());
        }
    }
    
    void applyTpsRule(TpsControlRule tpsControlRule) {
        tpsControlManager.applyTpsRule(tpsControlRule.getPointName(), tpsControlRule);
    }
    
    void deleteTpsRule(String pointName) {
        tpsControlManager.applyTpsRule(pointName, null);
    }
    
    
    public abstract Map<String, TpsControlRule> loadInitTpsRules();
    
    
}
