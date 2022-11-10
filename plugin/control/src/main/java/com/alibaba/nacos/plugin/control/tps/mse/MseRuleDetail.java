package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;

import java.util.Set;

public class MseRuleDetail extends RuleDetail {
    
    long maxFlow = -1;
    
    int order = 0;
    
    Set<String> disabledInterceptors;
    
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
    
    public Set<String> getDisabledInterceptors() {
        return disabledInterceptors;
    }
    
    public void setDisabledInterceptors(Set<String> disabledInterceptors) {
        this.disabledInterceptors = disabledInterceptors;
    }
}
