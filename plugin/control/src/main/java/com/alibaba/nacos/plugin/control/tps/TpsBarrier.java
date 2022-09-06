package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKeyMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * tps barrier for tps point.
 */
public class TpsBarrier {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TpsBarrier.class);
    
    private String pointName;
    
    private RateCounterWrapper pointRateCounter;
    
    public List<RateCounterWrapper> patternRateCounters = new ArrayList<>();
    
    private TpsControlRule tpsControlRule;
    
    public TpsBarrier() {
        pointRateCounter = new RateCounterWrapper("", "",
                RateCountCreatorLoader.getInstance().getRateCountCreator().createRateCount(TimeUnit.SECONDS));
    }
    
    /**
     * apply tps.
     *
     * @param tpsCheckRequest tpsCheckRequest.
     * @return check current tps is allowed.
     */
    public TpsCheckResponse applyTps(TpsCheckRequest tpsCheckRequest) {
        
        long now = System.currentTimeMillis();
        
        List<RateCounterWrapper> appliedRateCounters = null;
        RateCounterWrapper denyPatternRate = null;
        boolean patternSuccess = true;
        //check pattern
        for (RateCounterWrapper rateCounterWrapper : patternRateCounters) {
            
            if (MonitorKeyMatcher.match(rateCounterWrapper.getPattern(), tpsCheckRequest.monitorKeys.get(0).getKey())) {
                boolean success = rateCounterWrapper.tryAdd(now, 1, rateCounterWrapper.getMaxCount());
                if (success) {
                    if (appliedRateCounters == null) {
                        appliedRateCounters = new ArrayList<>();
                    }
                    appliedRateCounters.add(rateCounterWrapper);
                } else {
                    patternSuccess = false;
                    denyPatternRate = rateCounterWrapper;
                    
                }
            }
        }
        // when pattern fail,rollback applied count of patterns.
        if (!patternSuccess && appliedRateCounters != null && !appliedRateCounters.isEmpty()) {
            for (RateCounterWrapper rateCounterWrapper : appliedRateCounters) {
                rateCounterWrapper.minus(now, 1);
            }
        }
        
        if (!patternSuccess) {
            return new TpsCheckResponse(false, (denyPatternRate == null) ? "unknown" : (denyPatternRate.getName()));
        }
        
        long maxCount = 111;
        boolean pointCheckSuccess = pointRateCounter.tryAdd(now, 1, maxCount);
        if (pointCheckSuccess) {
            return new TpsCheckResponse(true, "success");
        } else {
            return new TpsCheckResponse(false, "deny by point rule,maxTps=" + maxCount);
        }
        
    }
    
    private void buildIpOrConnectionIdKey(TpsCheckRequest tpsCheckRequest) {
        if (!StringUtils.isBlank(tpsCheckRequest.getClientIp()) || !StringUtils
                .isBlank(tpsCheckRequest.getConnectionId())) {
            if (tpsCheckRequest.monitorKeys == null) {
                tpsCheckRequest.monitorKeys = new ArrayList<>();
            }
            
        }
    }
    
    public String getPointName() {
        return pointName;
    }
    
    public void setPointName(String pointName) {
        this.pointName = pointName;
    }
    
    /**
     * @param newControlRule
     */
    public synchronized void applyRule(TpsControlRule newControlRule) {
        LOGGER.info("Apply tps control rule parse start,pointName=[{}]  ", this.getPointName());
        
        //1.reset all monitor point for null.
        if (newControlRule == null) {
            LOGGER.info("Clear all tps control rule ,pointName=[{}]  ", this.getPointName());
            this.pointRateCounter.clearLimitRule();
            this.patternRateCounters.clear();
            return;
        }
        
        //2.check point rule.
        TpsControlRule.RuleDetail newPointRule = newControlRule.getPointRule();
        if (newPointRule == null) {
            LOGGER.info("Clear point  control rule ,pointName=[{}]  ", this.getPointName());
            this.pointRateCounter.clearLimitRule();
        } else {
            LOGGER.info("Update  point  control rule ,pointName=[{}],original maxTps={}, new maxTps={}"
                            + ",original monitorType={}, original monitorType={}, ", this.getPointName(),
                    this.pointRateCounter.getMaxCount(), newPointRule.maxCount, this.pointRateCounter.getMonitorType(),
                    newPointRule.monitorType);
            
            this.pointRateCounter.setMaxCount(newPointRule.maxCount);
            this.pointRateCounter.setMonitorType(newPointRule.monitorType);
        }
        
        //3.check monitor key rules.
        Map<String, TpsControlRule.RuleDetail> newMonitorKeyRules = newControlRule.getMonitorKeyRule();
        //3.1 clear all monitor keys.
        if (newMonitorKeyRules == null || newMonitorKeyRules.isEmpty()) {
            LOGGER.info("Clear point  control rule for monitorKeys, pointName=[{}]  ", this.getPointName());
            this.patternRateCounters.clear();
        } else {
            Map<String, RateCounterWrapper> patternRateCounterMap = this.patternRateCounters.stream()
                    .collect(Collectors.toMap(a -> a.getName(), Function.identity(), (key1, key2) -> key1));
            
            for (Map.Entry<String, TpsControlRule.RuleDetail> newMonitorRule : newMonitorKeyRules.entrySet()) {
                if (newMonitorRule.getValue() == null) {
                    continue;
                }
                if (newMonitorRule.getValue().getPattern() == null) {
                    newMonitorRule.getValue().setPattern(newMonitorRule.getKey());
                }
                
                TpsControlRule.RuleDetail newRuleDetail = newMonitorRule.getValue();
                if (newRuleDetail.period == null) {
                    newRuleDetail.period = TimeUnit.SECONDS;
                }
                
                if (newRuleDetail.model == null) {
                    newRuleDetail.model = TpsControlRule.RuleDetail.MODEL_FUZZY;
                }
                
                //update rule.
                if (patternRateCounterMap.containsKey(newMonitorRule.getKey())) {
                    RateCounterWrapper rateCounterWrapper = patternRateCounterMap.get(newMonitorRule.getKey());
                    rateCounterWrapper.applyRuleDetail(newRuleDetail);
                    
                } else {
                    LOGGER.info(
                            "Add  point  control rule for client ip ,pointName=[{}],monitorKey=[{}], new maxTps={}, new monitorType={}, ",
                            this.getPointName(), newMonitorRule.getKey(), newMonitorRule.getValue().maxCount,
                            newMonitorRule.getValue().monitorType);
                    // add rule
                    RateCounterWrapper rateCounterWrapper = new RateCounterWrapper(newMonitorRule.getKey(),
                            newRuleDetail.getPattern(), RateCountCreatorLoader.getInstance().getRateCountCreator()
                            .createRateCount(newRuleDetail.period));
                    rateCounterWrapper.setMaxCount(newRuleDetail.maxCount);
                    rateCounterWrapper.setMonitorType(newRuleDetail.monitorType);
                    patternRateCounterMap.put(newMonitorRule.getKey(), rateCounterWrapper);
                }
            }
            
            //delete rule.
            Iterator<Map.Entry<String, RateCounterWrapper>> currentPatternCounterIterator = patternRateCounterMap
                    .entrySet().iterator();
            while (currentPatternCounterIterator.hasNext()) {
                Map.Entry<String, RateCounterWrapper> currentPattern = currentPatternCounterIterator.next();
                if (!newMonitorKeyRules.containsKey(currentPattern.getKey())) {
                    LOGGER.info("Delete  point  control rule for pointName=[{}] ,monitorKey=[{}]", this.getPointName(),
                            currentPattern.getKey());
                    currentPatternCounterIterator.remove();
                }
            }
            
        }
        
    }
    
}

class RateCounterWrapper extends RateCounter {
    
    private String name;
    
    private String pattern;
    
    private long maxCount;
    
    /**
     * monitor/intercept.
     */
    private String monitorType = MonitorType.MONITOR.type;
    
    private RateCounter rateCounter;
    
    private String model;
    
    public RateCounterWrapper(String name, String pattern, RateCounter rateCounter) {
        this.name = name;
        this.pattern = pattern;
        this.rateCounter = rateCounter;
    }
    
    public void applyRuleDetail(TpsControlRule.RuleDetail ruleDetail) {
        
        if (!Objects.equals(this.rateCounter.getPeriod(), ruleDetail.period) || !Objects
                .equals(this.getModel(), ruleDetail.model)) {
            this.setMaxCount(ruleDetail.maxCount);
            this.setMonitorType(ruleDetail.monitorType);
        } else {
            this.setMaxCount(ruleDetail.maxCount);
            this.setMonitorType(ruleDetail.monitorType);
        }
    }
    
    public void clearLimitRule() {
        this.maxCount = -1;
    }
    
    public long getMaxCount() {
        return maxCount;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public void setMaxCount(long maxCount) {
        this.maxCount = maxCount;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getPattern() {
        return pattern;
    }
    
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
    
    public RateCounter getRateCounter() {
        return rateCounter;
    }
    
    public void setRateCounter(RateCounter rateCounter) {
        this.rateCounter = rateCounter;
    }
    
    public void add(long timestamp, long count) {
        rateCounter.add(timestamp, count);
    }
    
    public boolean tryAdd(long timestamp, long count, long upLimit) {
        return rateCounter.tryAdd(timestamp, count, upLimit);
    }
    
    public void minus(long timestamp, long count) {
        rateCounter.minus(timestamp, count);
    }
    
    @Override
    public long getCount(long timestamp) {
        return rateCounter.getCount(timestamp);
    }
    
    public String getMonitorType() {
        return monitorType;
    }
    
    public void setMonitorType(String monitorType) {
        this.monitorType = monitorType;
    }
}
