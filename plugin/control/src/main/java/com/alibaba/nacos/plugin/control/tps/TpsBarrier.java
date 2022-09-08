package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.plugin.control.tps.key.ClientIpMonitorKey;
import com.alibaba.nacos.plugin.control.tps.key.ConnectionIdMonitorKey;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKeyMatcher;
import com.alibaba.nacos.plugin.control.tps.nacos.SimpleCountRuleBarrier;
import com.alibaba.nacos.plugin.control.tps.nacos.SimpleCountRuleBarrierCreator;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * tps barrier for tps point.
 */
public class TpsBarrier {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TpsBarrier.class);
    
    private String pointName;
    
    private RuleBarrier ruleBarrier;
    
    public List<RuleBarrier> patternBarriers = new ArrayList<>();
    
    public TpsBarrier(String pointName) {
        this.pointName = pointName;
        ruleBarrier = getRuleBarrierCreator().createRateCount(pointName, "", TimeUnit.SECONDS);
    }
    
    public RuleBarrierCreator getRuleBarrierCreator() {
        //SPI TODO
        return SimpleCountRuleBarrierCreator.getInstance();
    }
    
    /**
     * apply tps.
     *
     * @param tpsCheckRequest tpsCheckRequest.
     * @return check current tps is allowed.
     */
    public TpsCheckResponse applyTps(TpsCheckRequest tpsCheckRequest) {
        
        List<RuleBarrier> appliedBarriers = null;
        RuleBarrier denyPatternRate = null;
        boolean patternSuccess = true;
        List<RuleBarrier> patternBarriers = this.patternBarriers;
        
        buildIpOrConnectionIdKey(tpsCheckRequest);
        
        //1.check pattern barriers
        for (RuleBarrier patternRuleBarrier : patternBarriers) {
            
            for (MonitorKey monitorKey : tpsCheckRequest.getMonitorKeys()) {
                if (MonitorKeyMatcher.match(patternRuleBarrier.getPattern(), monitorKey.build())) {
                    boolean success = patternRuleBarrier.applyTps(tpsCheckRequest);
                    if (success) {
                        if (appliedBarriers == null) {
                            appliedBarriers = new ArrayList<>();
                        }
                        appliedBarriers.add(patternRuleBarrier);
                    } else {
                        patternSuccess = false;
                        denyPatternRate = patternRuleBarrier;
                        break;
                        
                    }
                }
            }
            if (!patternSuccess) {
                break;
            }
            
        }
        
        //2.when pattern fail,rollback applied count of patterns.
        if (!patternSuccess) {
            rollbackTps(appliedBarriers, tpsCheckRequest);
        }
        
        if (!patternSuccess) {
            return new TpsCheckResponse(false, (denyPatternRate == null) ? "unknown" : (denyPatternRate.getName()));
        }
        
        long maxCount = ruleBarrier.getMaxCount();
        boolean pointCheckSuccess = ruleBarrier.applyTps(tpsCheckRequest);
        if (pointCheckSuccess) {
            return new TpsCheckResponse(true, "success");
        } else {
            rollbackTps(appliedBarriers, tpsCheckRequest);
            return new TpsCheckResponse(false, "deny by point rule,maxTps=" + maxCount);
        }
        
    }
    
    private void rollbackTps(List<RuleBarrier> patternBarriers, TpsCheckRequest tpsCheckRequest) {
        if (patternBarriers == null || patternBarriers.isEmpty()) {
            return;
        }
        for (RuleBarrier rateCounterWrapper : patternBarriers) {
            rateCounterWrapper.rollbackTps(tpsCheckRequest);
        }
    }
    
    private void buildIpOrConnectionIdKey(TpsCheckRequest tpsCheckRequest) {
        if (!StringUtils.isBlank(tpsCheckRequest.getClientIp()) || !StringUtils
                .isBlank(tpsCheckRequest.getConnectionId())) {
            if (tpsCheckRequest.getMonitorKeys() == null) {
                tpsCheckRequest.setMonitorKeys(new ArrayList<>());
            }
            
            if (!StringUtils.isBlank(tpsCheckRequest.getClientIp())) {
                tpsCheckRequest.getMonitorKeys().add(new ClientIpMonitorKey(tpsCheckRequest.getClientIp()));
            }
            if (!StringUtils.isBlank(tpsCheckRequest.getConnectionId())) {
                tpsCheckRequest.getMonitorKeys().add(new ConnectionIdMonitorKey(tpsCheckRequest.getConnectionId()));
            }
            
        }
    }
    
    public String getPointName() {
        return pointName;
    }
    
    /**
     * @param newControlRule
     */
    public synchronized void applyRule(TpsControlRule newControlRule) {
        LOGGER.info("Apply tps control rule parse start,pointName=[{}]  ", this.getPointName());
        
        //1.reset all monitor point for null.
        if (newControlRule == null) {
            LOGGER.info("Clear all tps control rule ,pointName=[{}]  ", this.getPointName());
            this.ruleBarrier.clearLimitRule();
            this.patternBarriers = new ArrayList<>();
            return;
        }
        
        //2.check point rule.
        RuleDetail newPointRule = newControlRule.getPointRule();
        if (newPointRule == null) {
            LOGGER.info("Clear point  control rule ,pointName=[{}]  ", this.getPointName());
            this.ruleBarrier.clearLimitRule();
        } else {
            LOGGER.info("Update  point  control rule ,pointName=[{}],original maxTps={}, new maxTps={}"
                            + ",original monitorType={}, original monitorType={}, ", this.getPointName(),
                    this.ruleBarrier.getMaxCount(), newPointRule.getMaxCount(), this.ruleBarrier.getMonitorType(),
                    newPointRule.getMonitorType());
            
            this.ruleBarrier.setMaxCount(newPointRule.getMaxCount());
            this.ruleBarrier.setMonitorType(newPointRule.getMonitorType());
        }
        
        //3.check monitor key rules.
        Map<String, RuleDetail> newMonitorKeyRules = newControlRule.getMonitorKeyRule();
        //3.1 clear all monitor keys.
        if (newMonitorKeyRules == null || newMonitorKeyRules.isEmpty()) {
            LOGGER.info("Clear point  control rule for monitorKeys, pointName=[{}]  ", this.getPointName());
            this.patternBarriers = new ArrayList<>();
        } else {
            Map<String, RuleBarrier> patternRateCounterMap = this.patternBarriers.stream()
                    .collect(Collectors.toMap(a -> a.getName(), Function.identity(), (key1, key2) -> key1));
            
            for (Map.Entry<String, RuleDetail> newMonitorRule : newMonitorKeyRules.entrySet()) {
                if (newMonitorRule.getValue() == null) {
                    continue;
                }
                if (newMonitorRule.getValue().getPattern() == null) {
                    newMonitorRule.getValue().setPattern(newMonitorRule.getKey());
                }
                
                RuleDetail newRuleDetail = newMonitorRule.getValue();
                if (newRuleDetail.getPeriod() == null) {
                    newRuleDetail.setPeriod(TimeUnit.SECONDS);
                }
                
                if (newRuleDetail.getModel() == null) {
                    newRuleDetail.setModel(RuleDetail.MODEL_FUZZY);
                }
                
                //update rule.
                if (patternRateCounterMap.containsKey(newMonitorRule.getKey())) {
                    RuleBarrier rateCounterWrapper = patternRateCounterMap.get(newMonitorRule.getKey());
                    rateCounterWrapper.applyRuleDetail(newRuleDetail);
                    
                } else {
                    LOGGER.info(
                            "Add  point  control rule for client ip ,pointName=[{}],monitorKey=[{}], new maxTps={}, new monitorType={}, ",
                            this.getPointName(), newMonitorRule.getKey(), newMonitorRule.getValue().getMaxCount(),
                            newMonitorRule.getValue().getMonitorType());
                    // add rule
                    RuleBarrier rateCounterWrapper = new SimpleCountRuleBarrier(newMonitorRule.getKey(),
                            newRuleDetail.getPattern(), newRuleDetail.getPeriod());
                    rateCounterWrapper.setMaxCount(newRuleDetail.getMaxCount());
                    rateCounterWrapper.setMonitorType(newRuleDetail.getMonitorType());
                    patternRateCounterMap.put(newMonitorRule.getKey(), rateCounterWrapper);
                }
            }
            
            //delete rule.
            Iterator<Map.Entry<String, RuleBarrier>> currentPatternCounterIterator = patternRateCounterMap.entrySet()
                    .iterator();
            while (currentPatternCounterIterator.hasNext()) {
                Map.Entry<String, RuleBarrier> currentPattern = currentPatternCounterIterator.next();
                if (!newMonitorKeyRules.containsKey(currentPattern.getKey())) {
                    LOGGER.info("Delete  point  control rule for pointName=[{}] ,monitorKey=[{}]", this.getPointName(),
                            currentPattern.getKey());
                    currentPatternCounterIterator.remove();
                }
            }
            this.patternBarriers = patternRateCounterMap.values().stream().collect(Collectors.toList());
        }
        
    }
}
