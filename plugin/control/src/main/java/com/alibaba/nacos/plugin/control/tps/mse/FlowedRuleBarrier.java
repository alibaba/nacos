package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.TpsMetrics;
import com.alibaba.nacos.plugin.control.tps.nacos.SimpleCountRuleBarrier;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import org.springframework.beans.BeanUtils;

import java.util.concurrent.TimeUnit;

public abstract class FlowedRuleBarrier extends SimpleCountRuleBarrier {
    
    RuleBarrier tpsBarrier;
    
    public FlowedRuleBarrier(String ruleName, String pattern, TimeUnit period, String model) {
        super(ruleName, pattern, period, model);
        tpsBarrier = createRuleBarrier(ruleName, pattern, period, model);
    }
    
    abstract RuleBarrier createRuleBarrier(String ruleName, String pattern, TimeUnit period, String model);
    
    @Override
    public String getLimitMsg() {
        return String.format("[pattern:%s,period:%s,maxCount:%s,maxFlow:%s]", getPattern(), getPeriod(),
                tpsBarrier.getMaxCount(), this.getMaxCount());
    }
    
    @Override
    public TpsCheckResponse applyTps(BarrierCheckRequest barrierCheckRequest) {
        TpsCheckResponse rateCheck = tpsBarrier.applyTps(barrierCheckRequest);
        if (rateCheck.isSuccess() && barrierCheckRequest instanceof FlowedBarrierCheckRequest
                && ((FlowedBarrierCheckRequest) barrierCheckRequest).getFlow() > 0) {
            BarrierCheckRequest copy = new BarrierCheckRequest();
            BeanUtils.copyProperties(barrierCheckRequest, copy);
            copy.setCount(((FlowedBarrierCheckRequest) barrierCheckRequest).getFlow());
            return super.applyTps(copy);
        } else {
            return rateCheck;
        }
    }
    
    @Override
    public TpsMetrics getMetrics(long timeStamp) {
        return tpsBarrier.getMetrics(timeStamp);
    }
    
    @Override
    public void rollbackTps(BarrierCheckRequest barrierCheckRequest) {
        tpsBarrier.rollbackTps(barrierCheckRequest);
        if (barrierCheckRequest instanceof FlowedBarrierCheckRequest) {
            BarrierCheckRequest copy = new BarrierCheckRequest();
            BeanUtils.copyProperties(barrierCheckRequest, copy);
            copy.setCount(((FlowedBarrierCheckRequest) barrierCheckRequest).getFlow());
            super.rollbackTps(copy);
        }
    }
    
    public void applyRuleDetail(RuleDetail ruleDetail) {
        tpsBarrier.applyRuleDetail(ruleDetail);
        if (ruleDetail instanceof FlowedRuleDetail) {
            RuleDetail copy = new RuleDetail();
            BeanUtils.copyProperties(ruleDetail, copy);
            copy.setMaxCount(((FlowedRuleDetail) ruleDetail).getMaxFlow());
            super.setOrder(((FlowedRuleDetail) ruleDetail).getOrder());
            super.applyRuleDetail(copy);
        }
    }
    
    public void clearLimitRule() {
        tpsBarrier.clearLimitRule();
        super.clearLimitRule();
    }
}
