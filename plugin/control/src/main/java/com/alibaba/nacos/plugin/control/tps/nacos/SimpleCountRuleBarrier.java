package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.TpsMetrics;
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
    
    public SimpleCountRuleBarrier(String pointName, String ruleName, String pattern, TimeUnit period, String model) {
        super.setPointName(pointName);
        super.setRuleName(ruleName);
        super.setPattern(pattern);
        super.setPeriod(period);
        super.setModel(model);
    }
    
    public abstract LocalSimpleCountRateCounter createSimpleCounter(String name, TimeUnit period);
    
    public RateCounter getFuzzyRaterCounter(String ruleName, TimeUnit period) {
        if (fuzzyRateCounter == null) {
            this.fuzzyRateCounter = createSimpleCounter(ruleName, period);
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
                protoKeyCounter.putIfAbsent(key, createSimpleCounter(this.getRuleName(), this.getPeriod()));
            }
            return protoKeyCounter.get(key);
        }
    }
    
    private RateCounter getRateCounter(BarrierCheckRequest barrierCheckRequest) {
        if (!isProtoModel()) {
            return getFuzzyRaterCounter(this.getRuleName(), this.getPeriod());
        } else {
            return getProtoRaterCounter(this.getRuleName(), this.getPeriod(),
                    barrierCheckRequest.getMonitorKey().getKey());
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
            return new TpsCheckResponse(true, overLimit ? TpsResultCode.PASS_BY_MONITOR : TpsResultCode.PASS_BY_POINT,
                    "success");
        } else {
            boolean success = currentRateCounter
                    .tryAdd(barrierCheckRequest.getTimestamp(), barrierCheckRequest.getCount(), this.getMaxCount());
            return new TpsCheckResponse(success, success ? TpsResultCode.PASS_BY_POINT : TpsResultCode.DENY_BY_POINT,
                    "success");
        }
        
    }
    
    long trimTimeStamp(long timeStamp) {
        if (this.getPeriod() == TimeUnit.SECONDS) {
            timeStamp = RateCounter.getTrimMillsOfSecond(timeStamp);
        } else if (this.getPeriod() == TimeUnit.MINUTES) {
            timeStamp = RateCounter.getTrimMillsOfMinute(timeStamp);
        } else if (this.getPeriod() == TimeUnit.HOURS) {
            timeStamp = RateCounter.getTrimMillsOfHour(timeStamp);
        } else {
            //second default
            timeStamp = RateCounter.getTrimMillsOfSecond(timeStamp);
        }
        return timeStamp;
    }
    
    @Override
    public TpsMetrics getMetrics(long timeStamp) {
        timeStamp = trimTimeStamp(timeStamp);
        if (protoKeyCounter != null && !protoKeyCounter.isEmpty()) {
            TpsMetrics tpsMetrics = new TpsMetrics("", "", timeStamp, super.getPeriod());
            Map<String, TpsMetrics.Counter> protoMetrics = new HashMap<>();
            for (Map.Entry<String, RateCounter> protoCounter : protoKeyCounter.entrySet()) {
                long protoPassCount = protoCounter.getValue().getCount(timeStamp);
                long protoDeniedCount = protoCounter.getValue().getDeniedCount(timeStamp);
                if (protoPassCount <= 0 && protoDeniedCount <= 0) {
                    continue;
                }
                protoMetrics.put(protoCounter.getKey(),
                        new TpsMetrics.Counter(protoCounter.getValue().getCount(timeStamp),
                                protoCounter.getValue().getDeniedCount(timeStamp)));
            }
            
            tpsMetrics.setProtoKeyCounter(protoMetrics);
            long totalPass = protoMetrics.values().stream().mapToLong(TpsMetrics.Counter::getPassCount).sum();
            long totalDenied = protoMetrics.values().stream().mapToLong(TpsMetrics.Counter::getDeniedCount).sum();
            if (totalPass <= 0 && totalDenied <= 0) {
                return null;
            }
            tpsMetrics.setCounter(new TpsMetrics.Counter(totalPass, totalDenied));
            return tpsMetrics;
            
        } else if (fuzzyRateCounter != null) {
            TpsMetrics tpsMetrics = new TpsMetrics("", "", timeStamp, super.getPeriod());
            long totalPass = fuzzyRateCounter.getCount(timeStamp);
            long totalDenied = fuzzyRateCounter.getDeniedCount(timeStamp);
            if (totalPass <= 0 && totalDenied <= 0) {
                return null;
            }
            tpsMetrics.setCounter(new TpsMetrics.Counter(totalPass, totalDenied));
            return tpsMetrics;
        } else {
            return null;
        }
    }
    
    @Override
    public void rollbackTps(BarrierCheckRequest barrierCheckRequest) {
        
        if (!isProtoModel()) {
            getFuzzyRaterCounter(this.getRuleName(), this.getPeriod())
                    .minus(barrierCheckRequest.getTimestamp(), barrierCheckRequest.getCount());
        } else {
            getProtoRaterCounter(this.getRuleName(), this.getPeriod(), barrierCheckRequest.getMonitorKey().getKey())
                    .minus(barrierCheckRequest.getTimestamp(), barrierCheckRequest.getCount());
            ;
        }
    }
    
    /**
     * apply rule detail.
     *
     * @param ruleDetail ruleDetail.
     */
    public void applyRuleDetail(RuleDetail ruleDetail) {
        
        if (!Objects.equals(this.getPeriod(), ruleDetail.getPeriod()) || !Objects
                .equals(this.getModel(), ruleDetail.getModel())) {
            this.setMaxCount(ruleDetail.getMaxCount());
            this.setMonitorType(ruleDetail.getMonitorType());
            this.setPeriod(ruleDetail.getPeriod());
            this.setModel(ruleDetail.getModel());
            reCreateRaterCounter(this.getRuleName(), this.getPeriod());
        } else {
            this.setMaxCount(ruleDetail.getMaxCount());
            this.setMonitorType(ruleDetail.getMonitorType());
        }
    }
    
}
