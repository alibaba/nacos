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

public class FlowedRuleBarrier extends ProtoModelRuleBarrier {
    
    RuleBarrier tpsBarrier;
    
    RuleBarrier flowBarrier;
    
    public FlowedRuleBarrier(String pointName, String ruleName, TimeUnit period) {
        super(pointName, ruleName, period);
        tpsBarrier = new ProtoModelRuleBarrier(pointName, ruleName, period);
        flowBarrier = new ProtoModelRuleBarrier(pointName, ruleName, period);
    }
    
    @Override
    public String getLimitMsg() {
        return String.format("[period:%s,maxCount:%s,maxFlow:%s]", getPeriod(), tpsBarrier.getMaxCount(),
                flowBarrier.getMaxCount());
    }
    
    @Override
    public TpsCheckResponse applyTps(BarrierCheckRequest barrierCheckRequest) {
        
        //tps check
        TpsCheckResponse rateCheck = (tpsBarrier).applyTps(barrierCheckRequest);
        
        //flow tps check.
        if (rateCheck.isSuccess() && barrierCheckRequest instanceof MseBarrierCheckRequest
                && ((MseBarrierCheckRequest) barrierCheckRequest).getFlow() > 0) {
            MseBarrierCheckRequest copy = new MseBarrierCheckRequest();
            BeanUtils.copyProperties(barrierCheckRequest, copy);
            copy.setCount(((MseBarrierCheckRequest) barrierCheckRequest).getFlow());
            TpsCheckResponse flowedCheck = flowBarrier.applyTps(copy);
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
        tpsMetrics.setPointName(this.getPointName());
        tpsMetrics.setPeriod(this.getPeriod());
        
        MseTpsMetrics flowMetrics = (MseTpsMetrics) flowBarrier.getMetrics(timeStamp);
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
            MseBarrierCheckRequest copy = new MseBarrierCheckRequest();
            BeanUtils.copyProperties(barrierCheckRequest, copy);
            copy.setCount(((MseBarrierCheckRequest) barrierCheckRequest).getFlow());
            flowBarrier.rollbackTps(copy);
        }
    }
    
    @Override
    public void applyRuleDetail(RuleDetail ruleDetail) {
        if (!(ruleDetail instanceof MseRuleDetail)) {
            return;
        }
        MseRuleDetail mseRuleDetail = (MseRuleDetail) ruleDetail;
        tpsBarrier.applyRuleDetail(mseRuleDetail);
        MseRuleDetail copy = new MseRuleDetail();
        BeanUtils.copyProperties(ruleDetail, copy);
        copy.setMaxCount(((MseRuleDetail) ruleDetail).getMaxFlow());
        flowBarrier.applyRuleDetail(copy);
        this.setModel(((MseRuleDetail) ruleDetail).getModel());
        this.setMonitorType(ruleDetail.getMonitorType());
        this.setPeriod(ruleDetail.getPeriod());
        this.setMaxCount(ruleDetail.getMaxCount());
    
    }
    
    @Override
    public void clearLimitRule() {
        tpsBarrier.clearLimitRule();
        flowBarrier.clearLimitRule();
        Collection<TpsInterceptor> interceptors = InterceptorHolder.getInterceptors();
        interceptors.stream().forEach(a -> a.setDisabled(true));
    }
}
