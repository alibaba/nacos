package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class SimpleCountRuleBarrier extends RuleBarrier {
    
    RateCounter rateCounter;
    
    public SimpleCountRuleBarrier(String name, String pattern, TimeUnit period) {
        super.setName(name);
        super.setPattern(pattern);
        super.setPeriod(period);
        this.rateCounter = new SimpleCountRateCounter(name, period);
    }
    
    @Override
    public boolean applyTps(TpsCheckRequest tpsCheckRequest) {
        if (isMonitorType()) {
            if (rateCounter.getCount(tpsCheckRequest.getTimestamp()) + tpsCheckRequest.getCount() > this
                    .getMaxCount()) {
                //TODO log
            }
            rateCounter.add(tpsCheckRequest.getTimestamp(), tpsCheckRequest.getCount());
            return true;
        } else {
            boolean success = rateCounter
                    .tryAdd(tpsCheckRequest.getTimestamp(), tpsCheckRequest.getCount(), this.getMaxCount());
            if (!success) {
                //TODO log
            }
            return success;
        }
        
    }
    
    @Override
    public void rollbackTps(TpsCheckRequest tpsCheckRequest) {
        rateCounter.minus(tpsCheckRequest.getTimestamp(), tpsCheckRequest.getCount());
    }
    
    public RateCounter getRateCounter() {
        return rateCounter;
    }
    
    public void applyRuleDetail(RuleDetail ruleDetail) {
        
        if (!Objects.equals(this.getPeriod(), ruleDetail.getPeriod()) || !Objects
                .equals(this.getModel(), ruleDetail.getModel())) {
            this.setMaxCount(ruleDetail.getMaxCount());
            this.setMonitorType(ruleDetail.getMonitorType());
            this.setPeriod(ruleDetail.getPeriod());
            this.rateCounter = new SimpleCountRateCounter(this.getName(), this.getPeriod());
        } else {
            this.setMaxCount(ruleDetail.getMaxCount());
            this.setMonitorType(ruleDetail.getMonitorType());
        }
    }
    
    public void clearLimitRule() {
        //super.maxCount = -1;
        // TODO
    }
}
