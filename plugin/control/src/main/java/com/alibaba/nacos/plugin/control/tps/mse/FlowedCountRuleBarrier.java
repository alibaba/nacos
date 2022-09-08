package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.nacos.RateCounter;
import com.alibaba.nacos.plugin.control.tps.nacos.SimpleCountRateCounter;
import com.alibaba.nacos.plugin.control.tps.nacos.SimpleCountRuleBarrier;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class FlowedCountRuleBarrier extends SimpleCountRuleBarrier {
    
    
    private long maxFlow;
    
    RateCounter flowCounter;
    
    public FlowedCountRuleBarrier(String name, String pattern, TimeUnit period) {
        super(name, pattern, period);
        this.flowCounter = new SimpleCountRateCounter(name, period);
        
    }
    
    @Override
    public boolean applyTps(TpsCheckRequest tpsCheckRequest) {
        boolean rateCheck = getRateCounter()
                .tryAdd(tpsCheckRequest.getTimestamp(), tpsCheckRequest.getCount(), this.getMaxCount());
        if (rateCheck && tpsCheckRequest instanceof FlowedTpsCheckRequest) {
            flowCounter.tryAdd(tpsCheckRequest.getTimestamp(), ((FlowedTpsCheckRequest) tpsCheckRequest).getFlow(),
                    maxFlow);
        } else {
            return rateCheck;
        }
        return false;
    }
    
    @Override
    public void rollbackTps(TpsCheckRequest tpsCheckRequest) {
        getRateCounter().minus(tpsCheckRequest.getTimestamp(), tpsCheckRequest.getCount());
        if (tpsCheckRequest instanceof FlowedTpsCheckRequest) {
            flowCounter.minus(tpsCheckRequest.getTimestamp(), ((FlowedTpsCheckRequest) tpsCheckRequest).getFlow());
        }
        
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
        if (ruleDetail instanceof FlowedRuleDetail) {
            this.maxFlow = ((FlowedRuleDetail) ruleDetail).maxFlow;
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
    
    @Override
    public String getName() {
        return "flowedsimplecount";
    }
}
