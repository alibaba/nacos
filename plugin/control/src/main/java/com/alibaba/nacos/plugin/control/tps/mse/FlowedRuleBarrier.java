package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.TpsMetrics;
import com.alibaba.nacos.plugin.control.tps.mse.interceptor.InterceptorHolder;
import com.alibaba.nacos.plugin.control.tps.mse.interceptor.TpsInterceptor;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import org.springframework.beans.BeanUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public abstract class FlowedRuleBarrier extends ProtoModelRuleBarrier {
    
    RuleBarrier tpsBarrier;
    
    public FlowedRuleBarrier(String pointName, String ruleName, TimeUnit period) {
        super(pointName, ruleName, period);
        tpsBarrier = createRuleBarrier(pointName, ruleName, period);
    }
    
    abstract RuleBarrier createRuleBarrier(String pointName, String ruleName, TimeUnit period);
    
    @Override
    public String getLimitMsg() {
        return String.format("[period:%s,maxCount:%s,maxFlow:%s]", getPeriod(), tpsBarrier.getMaxCount(),
                this.getMaxCount());
    }
    
    @Override
    public TpsCheckResponse applyTps(BarrierCheckRequest barrierCheckRequest) {
        
        TpsCheckResponse rateCheck = tpsBarrier.applyTps(barrierCheckRequest);
        if (rateCheck.isSuccess() && barrierCheckRequest instanceof MseBarrierCheckRequest
                && ((MseBarrierCheckRequest) barrierCheckRequest).getFlow() > 0) {
            MseBarrierCheckRequest copy = new MseBarrierCheckRequest();
            BeanUtils.copyProperties(barrierCheckRequest, copy);
            copy.setCount(((MseBarrierCheckRequest) barrierCheckRequest).getFlow());
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
        MseTpsMetrics tpsMetrics = (MseTpsMetrics) tpsBarrier.getMetrics(timeStamp);
        if (tpsMetrics == null) {
            return null;
        }
        
        MseTpsMetrics flowMetrics = (MseTpsMetrics) super.getMetrics(timeStamp);
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
        if (barrierCheckRequest instanceof MseBarrierCheckRequest) {
            BarrierCheckRequest copy = new BarrierCheckRequest();
            BeanUtils.copyProperties(barrierCheckRequest, copy);
            copy.setCount(((MseBarrierCheckRequest) barrierCheckRequest).getFlow());
            super.rollbackTps(copy);
        }
    }
    
    @Override
    public void applyRuleDetail(RuleDetail ruleDetail) {
        MseRuleDetail mseRuleDetail = (MseRuleDetail) ruleDetail;
        if (!Objects.equals(this.getPeriod(), ruleDetail.getPeriod()) || !Objects
                .equals(this.getModel(), mseRuleDetail.getModel())) {
            this.setMaxCount(ruleDetail.getMaxCount());
            this.setMonitorType(ruleDetail.getMonitorType());
            this.setPeriod(ruleDetail.getPeriod());
            this.setModel(mseRuleDetail.getModel());
            reCreateRaterCounter(this.getRuleName(), this.getPeriod());
        } else {
            this.setMaxCount(ruleDetail.getMaxCount());
            this.setMonitorType(ruleDetail.getMonitorType());
        }
        MseRuleDetail copy = new MseRuleDetail();
        BeanUtils.copyProperties(ruleDetail, copy);
        copy.setMaxCount(((MseRuleDetail) ruleDetail).getMaxFlow());
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
    
    @Override
    public void clearLimitRule() {
        tpsBarrier.clearLimitRule();
        super.clearLimitRule();
        Collection<TpsInterceptor> interceptors = InterceptorHolder.getInterceptors();
        interceptors.stream().forEach(a -> a.setDisabled(true));
    }
}
