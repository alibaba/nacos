package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

import java.util.HashMap;
import java.util.Map;

public class MseTpsControlRule extends TpsControlRule {
    
    private String pointName;
    
    private MseRuleDetail pointRule;
    
    /**
     * rule name,rule detail.
     */
    private Map<String, MseRuleDetail> monitorKeyRule = new HashMap<>();
    
    public String getPointName() {
        return pointName;
    }
    
    public void setPointName(String pointName) {
        this.pointName = pointName;
    }
    
    public RuleDetail getPointRule() {
        return pointRule;
    }
    
    public void setPointRule(MseRuleDetail pointRule) {
        this.pointRule = pointRule;
    }
    
    public Map<String, MseRuleDetail> getMonitorKeyRule() {
        return monitorKeyRule;
    }
    
    public void setMonitorKeyRule(Map<String, MseRuleDetail> monitorKeyRule) {
        this.monitorKeyRule = monitorKeyRule;
    }
    
    @Override
    public String toString() {
        return "TpsControlRule{" + "pointName='" + pointName + '\'' + ", pointRule=" + pointRule + ", monitorKeyRule="
                + monitorKeyRule + '}';
    }
}
