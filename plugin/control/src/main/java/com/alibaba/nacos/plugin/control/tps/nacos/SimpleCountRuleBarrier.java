package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.TpsMetrics;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public abstract class SimpleCountRuleBarrier extends RuleBarrier {
    
    RateCounter rateCounter;
    
    public SimpleCountRuleBarrier(String pointName, String ruleName, String pattern, TimeUnit period, String model) {
        super.setPointName(pointName);
        super.setRuleName(ruleName);
        super.setPattern(pattern);
        super.setPeriod(period);
        super.setModel(model);
    }
    
    public abstract LocalSimpleCountRateCounter createSimpleCounter(String name, TimeUnit period);
    
    
    public void reCreateRaterCounter(String name, TimeUnit period) {
        this.rateCounter = createSimpleCounter(name, period);
    }
    
    private RateCounter getRateCounter(BarrierCheckRequest barrierCheckRequest) {
        return rateCounter;
    }
    
    @Override
    public TpsCheckResponse applyTps(BarrierCheckRequest barrierCheckRequest) {
        RateCounter currentRateCounter = getRateCounter(barrierCheckRequest);
        if (isMonitorType()) {
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
        
        TpsMetrics tpsMetrics = new TpsMetrics("", "", timeStamp, super.getPeriod());
        long totalPass = rateCounter.getCount(timeStamp);
        long totalDenied = rateCounter.getDeniedCount(timeStamp);
        if (totalPass <= 0 && totalDenied <= 0) {
            return null;
        }
        tpsMetrics.setCounter(new TpsMetrics.Counter(totalPass, totalDenied));
        return tpsMetrics;
        
    }
    
    @Override
    public void rollbackTps(BarrierCheckRequest barrierCheckRequest) {
        rateCounter.minus(barrierCheckRequest.getTimestamp(), barrierCheckRequest.getCount());
    }
    
    /**
     * apply rule detail.
     *
     * @param ruleDetail ruleDetail.
     */
    public void applyRuleDetail(RuleDetail ruleDetail) {
        
        if (!Objects.equals(this.getPeriod(), ruleDetail.getPeriod())) {
            this.setMaxCount(ruleDetail.getMaxCount());
            this.setMonitorType(ruleDetail.getMonitorType());
            this.setPeriod(ruleDetail.getPeriod());
            reCreateRaterCounter(this.getRuleName(), this.getPeriod());
        } else {
            this.setMaxCount(ruleDetail.getMaxCount());
            this.setMonitorType(ruleDetail.getMonitorType());
        }
    }
    
}
