package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.TpsMetrics;
import com.alibaba.nacos.plugin.control.tps.nacos.LocalSimpleCountRateCounter;
import com.alibaba.nacos.plugin.control.tps.nacos.RateCounter;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.RuleModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ProtoModelRuleBarrier extends RuleBarrier {
    
    RateCounter fuzzyRateCounter;
    
    Map<String, RateCounter> protoKeyCounter;
    
    private String model;
    
    public ProtoModelRuleBarrier(String pointName, String ruleName, TimeUnit period) {
        super.setPointName(pointName);
        super.setPeriod(period);
        super.setRuleName(ruleName);
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public boolean isProtoModel() {
        return RuleModel.PROTO.name().equalsIgnoreCase(this.model);
    }
    
    public RateCounter createCounter(String name, TimeUnit period) {
        return new LocalSimpleCountRateCounter(name, period);
    }
    
    public RateCounter getFuzzyRaterCounter(String ruleName, TimeUnit period) {
        if (fuzzyRateCounter == null) {
            this.fuzzyRateCounter = createCounter(ruleName, period);
        }
        return fuzzyRateCounter;
    }
    
    public void reCreateRaterCounter(String name, TimeUnit period) {
        this.fuzzyRateCounter = createCounter(name, period);
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
                protoKeyCounter.putIfAbsent(key, createCounter(name, this.getPeriod()));
            }
            return protoKeyCounter.get(key);
        }
    }
    
    private RateCounter getRateCounter(MseBarrierCheckRequest barrierCheckRequest) {
        if (!isProtoModel()) {
            return getFuzzyRaterCounter(this.getRuleName(), this.getPeriod());
        } else {
            return getProtoRaterCounter(this.getRuleName(), this.getPeriod(),
                    barrierCheckRequest.getMonitorKey().getKey());
        }
    }
    
    @Override
    public String getBarrierName() {
        return "proto";
    }
    
    @Override
    public TpsCheckResponse applyTps(BarrierCheckRequest barrierCheckRequest) {
        RateCounter currentRateCounter = getRateCounter((MseBarrierCheckRequest) barrierCheckRequest);
        boolean monitorOnly = ((MseBarrierCheckRequest) barrierCheckRequest).isMonitorOnly();
        if (isMonitorType() || monitorOnly) {
            boolean overLimit = false;
            
            long addResult = currentRateCounter.add(barrierCheckRequest.getTimestamp(), barrierCheckRequest.getCount());
            if (addResult > this.getMaxCount()) {
                overLimit = true;
            }
            
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
            MseTpsMetrics tpsMetrics = new MseTpsMetrics("", "", timeStamp, super.getPeriod());
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
            MseTpsMetrics tpsMetrics = new MseTpsMetrics("", "", timeStamp, super.getPeriod());
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
            getProtoRaterCounter(this.getRuleName(), this.getPeriod(),
                    ((MseBarrierCheckRequest) barrierCheckRequest).getMonitorKey().getKey())
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
        MseRuleDetail mseRuleDetail = (MseRuleDetail) ruleDetail;
        this.model = ((MseRuleDetail) ruleDetail).getModel();
        
        if (!Objects.equals(this.getPeriod(), ruleDetail.getPeriod()) || !Objects
                .equals(this.getModel(), mseRuleDetail.getModel())) {
            
            reCreateRaterCounter(this.getRuleName(), this.getPeriod());
        }
        this.setMaxCount(ruleDetail.getMaxCount());
        this.setMonitorType(ruleDetail.getMonitorType());
        this.setPeriod(ruleDetail.getPeriod());
        
    }
    
}
