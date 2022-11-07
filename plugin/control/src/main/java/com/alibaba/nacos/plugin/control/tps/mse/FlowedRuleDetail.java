package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;

public class FlowedRuleDetail extends RuleDetail {
    
    long maxFlow = -1;
    
    int order = 0;
    
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
}
