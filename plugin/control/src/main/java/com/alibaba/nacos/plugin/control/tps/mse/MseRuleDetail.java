package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;

import java.util.Set;

public class MseRuleDetail extends RuleDetail {
    
    private String pattern;
    
    boolean printLog = false;
    
    long maxFlow = -1;
    
    int order = 0;
    
    public static final String MODEL_FUZZY = "FUZZY";
    
    public static final String MODEL_PROTO = "PROTO";
    
    String model = MODEL_FUZZY;
    
    Set<String> disabledInterceptors;
    
    public String getPattern() {
        return pattern;
    }
    
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
    
    public boolean isPrintLog() {
        return printLog;
    }
    
    public void setPrintLog(boolean printLog) {
        this.printLog = printLog;
    }
    
    public long getMaxFlow() {
        return maxFlow;
    }
    
    public void setMaxFlow(long maxFlow) {
        this.maxFlow = maxFlow;
    }
    
    public int getOrder() {
        return order;
    }
    
    public void setOrder(int order) {
        this.order = order;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public Set<String> getDisabledInterceptors() {
        return disabledInterceptors;
    }
    
    public void setDisabledInterceptors(Set<String> disabledInterceptors) {
        this.disabledInterceptors = disabledInterceptors;
    }
}
