package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
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
    public String getLimitMsg() {
        return String.format("[pattern:%s,period:%s,maxCount:%s,maxFlow:%s]", getPattern(), getPeriod(), getMaxCount(),
                maxFlow);
    }
    
    @Override
    public TpsCheckResponse applyTps(TpsCheckRequest tpsCheckRequest) {
        TpsCheckResponse rateCheck = super.applyTps(tpsCheckRequest);
        if (rateCheck.isSuccess() && tpsCheckRequest instanceof FlowedTpsCheckRequest) {
            if (isMonitorType()) {
                boolean overLimit = false;
                if (maxFlow > 0 &&
                        flowCounter.getCount(tpsCheckRequest.getTimestamp()) + ((FlowedTpsCheckRequest) tpsCheckRequest)
                                .getFlow() > maxFlow) {
                    overLimit = true;
                }
                
                flowCounter.add(tpsCheckRequest.getTimestamp(), ((FlowedTpsCheckRequest) tpsCheckRequest).getFlow());
                return new TpsCheckResponse(true, overLimit ? TpsResultCode.PASS_BY_MONITOR : TpsResultCode.CHECK_PASS,
                        "success");
            } else {
                boolean flowedSuccess = flowCounter
                        .tryAdd(tpsCheckRequest.getTimestamp(), ((FlowedTpsCheckRequest) tpsCheckRequest).getFlow(),
                                maxFlow);
                TpsResultCode tpsResultCode = TpsResultCode.CHECK_PASS;
                
                if (!flowedSuccess) {
                    super.rollbackTps(tpsCheckRequest);
                    tpsResultCode = TpsResultCode.CHECK_DENY;
                }
                return new TpsCheckResponse(flowedSuccess, tpsResultCode,
                        flowedSuccess ? "success" : "deny by flowed limit");
            }
            
        } else {
            return rateCheck;
        }
        
    }
    
    @Override
    public void rollbackTps(TpsCheckRequest tpsCheckRequest) {
        super.rollbackTps(tpsCheckRequest);
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
