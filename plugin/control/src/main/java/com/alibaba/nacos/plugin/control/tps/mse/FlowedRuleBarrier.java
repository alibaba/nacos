package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.TpsMetrics;
import com.alibaba.nacos.plugin.control.tps.interceptor.InterceptorHolder;
import com.alibaba.nacos.plugin.control.tps.interceptor.TpsInterceptor;
import com.alibaba.nacos.plugin.control.tps.nacos.SimpleCountRuleBarrier;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import org.springframework.beans.BeanUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public abstract class FlowedRuleBarrier extends SimpleCountRuleBarrier {
    
    RuleBarrier tpsBarrier;
    
    public FlowedRuleBarrier(String pointName, String ruleName, String pattern, TimeUnit period, String model) {
        super(pointName, ruleName, pattern, period, model);
        tpsBarrier = createRuleBarrier(pointName, ruleName, pattern, period, model);
    }
    
    abstract RuleBarrier createRuleBarrier(String pointName, String ruleName, String pattern, TimeUnit period,
            String model);
    
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
            TpsCheckResponse flowedCheck = super.applyTps(copy);
            if (!flowedCheck.isSuccess()) {
                tpsBarrier.rollbackTps(barrierCheckRequest);
            }
            return flowedCheck;
        } else {
            return rateCheck;
        }
    }
    
    @Override
    public TpsMetrics getMetrics(long timeStamp) {
        TpsMetrics tpsMetrics = tpsBarrier.getMetrics(timeStamp);
        if (tpsMetrics == null) {
            return null;
        }
        
        TpsMetrics flowMetrics = super.getMetrics(timeStamp);
        if (flowMetrics == null) {
            return tpsMetrics;
        }
        
        FlowCounter flowCounter = new FlowCounter(tpsMetrics.getCounter().getPassCount(),
                tpsMetrics.getCounter().getDeniedCount());
        flowCounter.setPassFlow(flowMetrics.getCounter().getPassCount());
        flowCounter.setDeniedFlow(flowMetrics.getCounter().getDeniedCount());
        tpsMetrics.setCounter(flowCounter);
        
        if (flowMetrics.getProtoKeyCounter() != null && !flowMetrics.getProtoKeyCounter().isEmpty()) {
            for (Map.Entry<String, TpsMetrics.Counter> flowedCounter : flowMetrics.getProtoKeyCounter().entrySet()) {
                if (tpsMetrics.getProtoKeyCounter() == null) {
                    tpsMetrics.setProtoKeyCounter(new HashMap<String, TpsMetrics.Counter>());
                }
                FlowCounter flowCounterNew = new FlowCounter(0, 0);
                
                TpsMetrics.Counter protoKeyTpsCounter = tpsMetrics.getProtoKeyCounter().get(flowedCounter.getKey());
                if (protoKeyTpsCounter != null) {
                    flowCounterNew.setPassCount(protoKeyTpsCounter.getPassCount());
                    flowCounterNew.setDeniedCount(protoKeyTpsCounter.getDeniedCount());
                }
                
                flowCounterNew.setPassFlow(flowedCounter.getValue().getPassCount());
                flowCounterNew.setDeniedFlow(flowedCounter.getValue().getDeniedCount());
                tpsMetrics.getProtoKeyCounter().put(flowedCounter.getKey(), flowCounterNew);
            }
            
        }
        return tpsMetrics;
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
    
    @Override
    public void applyRuleDetail(RuleDetail ruleDetail) {
        tpsBarrier.applyRuleDetail(ruleDetail);
        if (ruleDetail instanceof MseRuleDetail) {
            RuleDetail copy = new RuleDetail();
            BeanUtils.copyProperties(ruleDetail, copy);
            copy.setMaxCount(((MseRuleDetail) ruleDetail).getMaxFlow());
            super.setOrder(((MseRuleDetail) ruleDetail).getOrder());
            super.applyRuleDetail(copy);
            Collection<TpsInterceptor> interceptors = InterceptorHolder.getInterceptors();
            List<TpsInterceptor> pointerInterceptor = interceptors.stream()
                    .filter(a -> a.getPointName().equalsIgnoreCase(this.getPointName())).collect(Collectors.toList());
            Set<String> disabledInterceptors = ((MseRuleDetail) ruleDetail).getDisabledInterceptors();
            for (TpsInterceptor tpsInterceptor : pointerInterceptor) {
                if (disabledInterceptors != null && disabledInterceptors.contains(tpsInterceptor.getName())) {
                    tpsInterceptor.setDisabled(true);
                } else {
                    tpsInterceptor.setDisabled(false);
                }
            }
            
        }
    }
    
    @Override
    public void clearLimitRule() {
        tpsBarrier.clearLimitRule();
        super.clearLimitRule();
        Collection<TpsInterceptor> interceptors = InterceptorHolder.getInterceptors();
        interceptors.stream().forEach(a -> a.setDisabled(true));
    }
}
