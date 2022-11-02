package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import org.springframework.beans.BeanUtils;

import java.util.concurrent.TimeUnit;

public abstract class FlowedRuleBarrier extends RuleBarrier {
    
    RuleBarrier tpsBarrier;
    
    RuleBarrier flowedBarrier;
    
    public FlowedRuleBarrier(String name, String pattern, TimeUnit period, String model) {
        tpsBarrier = createRuleBarrier(name, pattern, period, model);
        flowedBarrier = createRuleBarrier(name, pattern, period, model);
    }
    
    abstract RuleBarrier createRuleBarrier(String name, String pattern, TimeUnit period, String model);
    
    @Override
    public String getLimitMsg() {
        return String.format("[pattern:%s,period:%s,maxCount:%s,maxFlow:%s]", getPattern(), getPeriod(),
                tpsBarrier.getMaxCount(), this.getMaxCount());
    }
    
    @Override
    public TpsCheckResponse applyTps(BarrierCheckRequest barrierCheckRequest) {
        TpsCheckResponse rateCheck = tpsBarrier.applyTps(barrierCheckRequest);
        if (rateCheck.isSuccess() && barrierCheckRequest instanceof FlowedBarrierCheckRequest) {
            BarrierCheckRequest copy = new BarrierCheckRequest();
            BeanUtils.copyProperties(barrierCheckRequest, copy);
            copy.setCount(((FlowedBarrierCheckRequest) barrierCheckRequest).getFlow());
            return flowedBarrier.applyTps(copy);
        } else {
            return rateCheck;
        }
    }
    
    @Override
    public void rollbackTps(BarrierCheckRequest barrierCheckRequest) {
        tpsBarrier.rollbackTps(barrierCheckRequest);
        if (barrierCheckRequest instanceof FlowedBarrierCheckRequest) {
            BarrierCheckRequest copy = new BarrierCheckRequest();
            BeanUtils.copyProperties(barrierCheckRequest, copy);
            copy.setCount(((FlowedBarrierCheckRequest) barrierCheckRequest).getFlow());
            flowedBarrier.rollbackTps(copy);
        }
    }
    
    
    public void applyRuleDetail(RuleDetail ruleDetail) {
        tpsBarrier.applyRuleDetail(ruleDetail);
        if (ruleDetail instanceof FlowedRuleDetail) {
            RuleDetail copy = new RuleDetail();
            BeanUtils.copyProperties(ruleDetail, copy);
            copy.setMaxCount(((FlowedRuleDetail) ruleDetail).getMaxFlow());
            flowedBarrier.applyRuleDetail(copy);
        }
    }
    
    public void clearLimitRule() {
        tpsBarrier.clearLimitRule();
        super.clearLimitRule();
    }
    
    @Override
    public String getName() {
        return "flowedsimplecount";
    }
}
