package com.alibaba.nacos.plugin.control.tps.diamond;

import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.mse.FlowedTpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.nacos.RateCounter;
import com.alibaba.nacos.plugin.control.tps.nacos.SimpleCountRateCounter;
import com.alibaba.nacos.plugin.control.tps.nacos.SimpleCountRuleBarrier;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class FlowedRedisCountRuleBarrier extends SimpleCountRuleBarrier {
    
    
    private long maxFlow;
    
    RateCounter flowCounter;
    
    public FlowedRedisCountRuleBarrier(String name, String pattern, TimeUnit period) {
        super(name, pattern, period);
        this.flowCounter = new SimpleCountRateCounter(name, period);
    }
    
    @Override
    public TpsCheckResponse applyTps(TpsCheckRequest tpsCheckRequest) {
        return super.applyTps(tpsCheckRequest);
    }
    
    @Override
    public void rollbackTps(TpsCheckRequest tpsCheckRequest) {
    
    }
    
    
    public void applyRuleDetail(RuleDetail ruleDetail) {
        
        if (!Objects.equals(this.getPeriod(), ruleDetail.getPeriod()) || !Objects
                .equals(this.getModel(), ruleDetail.getModel())) {
            this.setMaxCount(ruleDetail.getMaxCount());
            this.setMonitorType(ruleDetail.getMonitorType());
        } else {
            this.setMaxCount(ruleDetail.getMaxCount());
            this.setMonitorType(ruleDetail.getMonitorType());
        }
    }
    
    public void clearLimitRule() {
        super.clearLimitRule();
        this.maxFlow = -1;
    }
    
    
    public long getMaxFlow() {
        return maxFlow;
    }
    
    public void setMaxFlow(long maxFlow) {
        this.maxFlow = maxFlow;
    }
}
