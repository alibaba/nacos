package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.nacos.LocalSimpleCountRuleBarrier;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import org.springframework.beans.BeanUtils;

import java.util.concurrent.TimeUnit;

public class FlowedLocalSimpleCountRuleBarrier extends LocalSimpleCountRuleBarrier {
    
    LocalSimpleCountRuleBarrier tpsSimpleCountRuleBarrier;
    
    public FlowedLocalSimpleCountRuleBarrier(String name, String pattern, TimeUnit period, String model) {
        super(name, pattern, period, model);
        tpsSimpleCountRuleBarrier = new LocalSimpleCountRuleBarrier(name, pattern, period, model);
    }
    
    @Override
    public String getLimitMsg() {
        return String.format("[pattern:%s,period:%s,maxCount:%s,maxFlow:%s]", getPattern(), getPeriod(),
                tpsSimpleCountRuleBarrier.getMaxCount(), getMaxCount());
    }
    
    @Override
    public TpsCheckResponse applyTps(BarrierCheckRequest barrierCheckRequest) {
        TpsCheckResponse rateCheck = tpsSimpleCountRuleBarrier.applyTps(barrierCheckRequest);
        if (rateCheck.isSuccess() && barrierCheckRequest instanceof FlowedBarrierCheckRequest) {
            BarrierCheckRequest copy = new BarrierCheckRequest();
            BeanUtils.copyProperties(barrierCheckRequest, copy);
            copy.setCount(((FlowedBarrierCheckRequest) barrierCheckRequest).getFlow());
            return super.applyTps(copy);
        } else {
            return rateCheck;
        }
    }
    
    @Override
    public void rollbackTps(BarrierCheckRequest barrierCheckRequest) {
        tpsSimpleCountRuleBarrier.rollbackTps(barrierCheckRequest);
        if (barrierCheckRequest instanceof FlowedBarrierCheckRequest) {
            BarrierCheckRequest copy = new BarrierCheckRequest();
            BeanUtils.copyProperties(barrierCheckRequest, copy);
            copy.setCount(((FlowedBarrierCheckRequest) barrierCheckRequest).getFlow());
            super.rollbackTps(copy);
        }
    }
    
    
    public void applyRuleDetail(RuleDetail ruleDetail) {
        tpsSimpleCountRuleBarrier.applyRuleDetail(ruleDetail);
        if (ruleDetail instanceof FlowedRuleDetail) {
            RuleDetail copy = new RuleDetail();
            BeanUtils.copyProperties(ruleDetail, copy);
            copy.setMaxCount(((FlowedRuleDetail) ruleDetail).getMaxFlow());
            super.applyRuleDetail(copy);
        }
    }
    
    public void clearLimitRule() {
        tpsSimpleCountRuleBarrier.clearLimitRule();
        super.clearLimitRule();
    }
    
    @Override
    public String getName() {
        return "flowedsimplecount";
    }
}
