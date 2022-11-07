package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public abstract class SimpleCountRuleBarrier extends RuleBarrier {
    
    RateCounter fuzzyRateCounter;
    
    Map<String, RateCounter> protoKeyCounter;
    
    public SimpleCountRuleBarrier(String name, String pattern, TimeUnit period, String model) {
        super.setName(name);
        super.setPattern(pattern);
        super.setPeriod(period);
        super.setModel(model);
    }
    
    public abstract LocalSimpleCountRateCounter createSimpleCounter(String name, TimeUnit period);
    
    public RateCounter getFuzzyRaterCounter(String name, TimeUnit period) {
        if (fuzzyRateCounter == null) {
            this.fuzzyRateCounter = createSimpleCounter(name, period);
        }
        return fuzzyRateCounter;
    }
    
    public void reCreateRaterCounter(String name, TimeUnit period) {
        this.fuzzyRateCounter = createSimpleCounter(name, period);
        this.protoKeyCounter = new HashMap<>();
    }
    
    public RateCounter getProtoRaterCounter(String name, TimeUnit period, String key) {
        
        if (!isProtoModel()) {
            return getFuzzyRaterCounter(name, period);
        } else if (protoKeyCounter != null && protoKeyCounter.containsKey(key)) {
            return protoKeyCounter.get(key);
        } else {
            if (protoKeyCounter == null) {
                protoKeyCounter = new HashMap<>();
            }
            if (!protoKeyCounter.containsKey(key)) {
                protoKeyCounter.putIfAbsent(key, createSimpleCounter(this.getName(), this.getPeriod()));
            }
            return protoKeyCounter.get(key);
        }
    }
    
    private RateCounter getRateCounter(BarrierCheckRequest barrierCheckRequest) {
        if (!isProtoModel()) {
            return getFuzzyRaterCounter(this.getName(), this.getPeriod());
        } else {
            return getProtoRaterCounter(this.getName(), this.getPeriod(), barrierCheckRequest.getMonitorKey().getKey());
        }
    }
    
    @Override
    public TpsCheckResponse applyTps(BarrierCheckRequest barrierCheckRequest) {
        RateCounter currentRateCounter = getRateCounter(barrierCheckRequest);
        if (isMonitorType() || barrierCheckRequest.isMonitorOnly()) {
            boolean overLimit = false;
            if (currentRateCounter.getCount(barrierCheckRequest.getTimestamp()) + barrierCheckRequest.getCount() > this
                    .getMaxCount()) {
                overLimit = true;
            }
            currentRateCounter.add(barrierCheckRequest.getTimestamp(), barrierCheckRequest.getCount());
            return new TpsCheckResponse(true, overLimit ? TpsResultCode.PASS_BY_MONITOR : TpsResultCode.CHECK_PASS,
                    "success");
        } else {
            boolean success = currentRateCounter
                    .tryAdd(barrierCheckRequest.getTimestamp(), barrierCheckRequest.getCount(), this.getMaxCount());
            return new TpsCheckResponse(success, success ? TpsResultCode.CHECK_PASS : TpsResultCode.CHECK_DENY,
                    "success");
        }
        
    }
    
    
    @Override
    public void rollbackTps(BarrierCheckRequest barrierCheckRequest) {
        
        if (!isProtoModel()) {
            getFuzzyRaterCounter(this.getName(), this.getPeriod())
                    .minus(barrierCheckRequest.getTimestamp(), barrierCheckRequest.getCount());
        } else {
            getProtoRaterCounter(this.getName(), this.getPeriod(), barrierCheckRequest.getMonitorKey().getKey())
                    .minus(barrierCheckRequest.getTimestamp(), barrierCheckRequest.getCount());
            ;
        }
    }
    
    
    public void applyRuleDetail(RuleDetail ruleDetail) {
        
        if (!Objects.equals(this.getPeriod(), ruleDetail.getPeriod()) || !Objects
                .equals(this.getModel(), ruleDetail.getModel())) {
            this.setMaxCount(ruleDetail.getMaxCount());
            this.setMonitorType(ruleDetail.getMonitorType());
            this.setPeriod(ruleDetail.getPeriod());
            this.setModel(ruleDetail.getModel());
            reCreateRaterCounter(this.getName(), this.getPeriod());
        } else {
            this.setMaxCount(ruleDetail.getMaxCount());
            this.setMonitorType(ruleDetail.getMonitorType());
        }
    }
    
}
