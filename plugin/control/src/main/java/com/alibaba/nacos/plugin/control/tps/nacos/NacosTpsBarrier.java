package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.key.ClientIpMonitorKey;
import com.alibaba.nacos.plugin.control.tps.key.ConnectionIdMonitorKey;
import com.alibaba.nacos.plugin.control.tps.key.MatchType;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKeyMatcher;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * tps barrier for tps point.
 */
public class NacosTpsBarrier extends TpsBarrier {
    
    public NacosTpsBarrier(String pointName) {
        super(pointName);
    }
    
    /**
     * apply tps.
     *
     * @param tpsCheckRequest tpsCheckRequest.
     * @return check current tps is allowed.
     */
    public TpsCheckResponse applyTps(TpsCheckRequest tpsCheckRequest) {
        
        Map<RuleBarrier, List<BarrierCheckRequest>> appliedBarriers = null;
        RuleBarrier denyPatternRate = null;
        boolean patternSuccess = true;
        List<RuleBarrier> patternBarriers = this.patternBarriers;
        
        buildIpOrConnectionIdKey(tpsCheckRequest);
        
        //1.check pattern barriers
        for (MonitorKey monitorKey : tpsCheckRequest.getMonitorKeys()) {
            
            for (RuleBarrier patternRuleBarrier : patternBarriers) {
                
                MatchType match = MonitorKeyMatcher.parse(patternRuleBarrier.getPattern(), monitorKey.build());
                if (match.isMatch()) {
                    BarrierCheckRequest barrierCheckRequest = tpsCheckRequest.buildBarrierCheckRequest(monitorKey);
                    TpsCheckResponse patternCheckResponse = patternRuleBarrier.applyTps(barrierCheckRequest);
                    if (patternCheckResponse.isSuccess()) {
                        if (appliedBarriers == null) {
                            appliedBarriers = new HashMap<>();
                        }
                        if (!appliedBarriers.containsKey(patternRuleBarrier)) {
                            appliedBarriers.putIfAbsent(patternRuleBarrier, new ArrayList<>());
                        }
                        
                        appliedBarriers.get(patternRuleBarrier).add(barrierCheckRequest);
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
        
        TpsCheckResponse tpsCheckResponse = null;
        boolean pointCheckSuccess = false;
        BarrierCheckRequest pointCheckRequest = null;
        //2.when pattern fail,rollback applied count of patterns.
        if (!patternSuccess) {
            tpsCheckResponse = new TpsCheckResponse(false, TpsResultCode.DENY_BY_PATTERN, "");
            String message = String
                    .format("[%s] pattern barrier [%s] check fail,monitorKeys=%s,msg=%s", super.getPointName(),
                            denyPatternRate.getRuleName(), tpsCheckRequest.getMonitorKeys(),
                            denyPatternRate.getLimitMsg());
            Loggers.TPS.warn(message);
            tpsCheckResponse.setMessage(message);
            
        } else {
            //3. check point rule
            pointCheckRequest = new BarrierCheckRequest();
            pointCheckRequest.setCount(tpsCheckRequest.getCount());
            pointCheckRequest.setPointName(super.getPointName());
            pointCheckRequest.setTimestamp(tpsCheckRequest.getTimestamp());
            tpsCheckResponse = super.getPointBarrier().applyTps(pointCheckRequest);
            if (!tpsCheckResponse.isSuccess()) {
                tpsCheckResponse.setCode(TpsResultCode.DENY_BY_POINT);
                String message = "pass by barrier,but denied by interceptor";
                tpsCheckResponse.setMessage(message);
            } else {
                pointCheckSuccess = true;
            }
        }
        
        if (!pointCheckSuccess) {
            rollbackTps(appliedBarriers);
        }
        
        return tpsCheckResponse;
    }
    
    private void rollbackTps(Map<RuleBarrier, List<BarrierCheckRequest>> appliedBarriers) {
        if (appliedBarriers == null || appliedBarriers.isEmpty()) {
            return;
        }
        for (Map.Entry<RuleBarrier, List<BarrierCheckRequest>> entries : appliedBarriers.entrySet()) {
            for (BarrierCheckRequest barrierCheckRequest : entries.getValue()) {
                entries.getKey().rollbackTps(barrierCheckRequest);
            }
        }
    }
    
    private void buildIpOrConnectionIdKey(TpsCheckRequest tpsCheckRequest) {
        if (!StringUtils.isBlank(tpsCheckRequest.getClientIp()) || !StringUtils
                .isBlank(tpsCheckRequest.getConnectionId())) {
            boolean clientIpMonitorKeyFound = false;
            boolean connectionIdMonitorKeyFound = false;
            if (tpsCheckRequest.getMonitorKeys() == null) {
                tpsCheckRequest.setMonitorKeys(new ArrayList<>());
            } else {
                for (MonitorKey monitorKey : tpsCheckRequest.getMonitorKeys()) {
                    if (monitorKey instanceof ClientIpMonitorKey) {
                        clientIpMonitorKeyFound = true;
                    }
                    if (monitorKey instanceof ConnectionIdMonitorKey) {
                        connectionIdMonitorKeyFound = true;
                    }
                }
            }
            
            if (!StringUtils.isBlank(tpsCheckRequest.getClientIp()) && !clientIpMonitorKeyFound) {
                tpsCheckRequest.getMonitorKeys().add(new ClientIpMonitorKey(tpsCheckRequest.getClientIp()));
            }
            if (!StringUtils.isBlank(tpsCheckRequest.getConnectionId()) && !connectionIdMonitorKeyFound) {
                tpsCheckRequest.getMonitorKeys().add(new ConnectionIdMonitorKey(tpsCheckRequest.getConnectionId()));
            }
            
        }
    }
    
    /**
     * apply rule.
     *
     * @param newControlRule newControlRule.
     */
    public synchronized void applyRule(TpsControlRule newControlRule) {
        Loggers.CONTROL.info("Apply tps control rule start,pointName=[{}]  ", this.getPointName());
        
        //1.reset all monitor point for null.
        if (newControlRule == null) {
            Loggers.CONTROL.info("Clear all tps control rule ,pointName=[{}]  ", this.getPointName());
            super.getPointBarrier().clearLimitRule();
            this.patternBarriers = new ArrayList<>();
            return;
        }
        
        //2.check point rule.
        RuleDetail newPointRule = newControlRule.getPointRule();
        if (newPointRule == null) {
            Loggers.CONTROL.info("Clear point  control rule ,pointName=[{}]  ", this.getPointName());
            this.pointBarrier.clearLimitRule();
        } else {
            Loggers.CONTROL.info("Update  point  control rule ,pointName=[{}],original maxTps={}, new maxTps={}"
                            + ",original monitorType={}, original monitorType={}, ", this.getPointName(),
                    this.pointBarrier.getMaxCount(), newPointRule.getMaxCount(), this.pointBarrier.getMonitorType(),
                    newPointRule.getMonitorType());
            this.pointBarrier.applyRuleDetail(newPointRule);
        }
        
        //3.check monitor key rules.
        Map<String, RuleDetail> newMonitorKeyRules = newControlRule.getMonitorKeyRule();
        //3.1 clear all monitor keys.
        if (newMonitorKeyRules == null || newMonitorKeyRules.isEmpty()) {
            Loggers.CONTROL.info("Clear point  control rule for monitorKeys, pointName=[{}]  ", this.getPointName());
            this.patternBarriers = new ArrayList<>();
        } else {
            Map<String, RuleBarrier> patternRateCounterMap = this.patternBarriers.stream()
                    .collect(Collectors.toMap(a -> a.getRuleName(), Function.identity(), (key1, key2) -> key1));
            
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
                    if (rateCounterWrapper.getOrder() == 0 && !rateCounterWrapper.getPattern()
                            .contains(Constants.ALL_PATTERN)) {
                        rateCounterWrapper.setOrder(1);
                    }
                } else {
                    Loggers.CONTROL
                            .info("pointName=[{}] ,Add  pattern control rule, name={}, pattern={}, new maxTps={}, new monitorType={} ",
                                    this.getPointName(), newMonitorRule.getKey(),
                                    newMonitorRule.getValue().getPattern(), newMonitorRule.getValue().getMaxCount(),
                                    newMonitorRule.getValue().getMonitorType());
                    // add rule
                    RuleBarrier rateCounterWrapper = ruleBarrierCreator
                            .createRuleBarrier(pointName, newMonitorRule.getKey(), newRuleDetail.getPattern(),
                                    newRuleDetail.getPeriod(), newRuleDetail.getModel());
                    rateCounterWrapper.applyRuleDetail(newRuleDetail);
                    if (rateCounterWrapper.getOrder() == 0 && !rateCounterWrapper.getPattern()
                            .contains(Constants.ALL_PATTERN)) {
                        rateCounterWrapper.setOrder(1);
                    }
                    patternRateCounterMap.put(newMonitorRule.getKey(), rateCounterWrapper);
                }
            }
            
            //delete rule.
            Iterator<Map.Entry<String, RuleBarrier>> currentPatternCounterIterator = patternRateCounterMap.entrySet()
                    .iterator();
            while (currentPatternCounterIterator.hasNext()) {
                Map.Entry<String, RuleBarrier> currentPattern = currentPatternCounterIterator.next();
                if (!newMonitorKeyRules.containsKey(currentPattern.getKey())) {
                    Loggers.CONTROL.info("Delete  point  control rule for pointName=[{}] ,monitorKey=[{}]",
                            this.getPointName(), currentPattern.getKey());
                    currentPatternCounterIterator.remove();
                }
            }
            //exact pattern has higher priority.
            this.patternBarriers = patternRateCounterMap.values().stream().collect(Collectors.toList());
        }
        
        Loggers.CONTROL.info("Apply tps control rule end,pointName=[{}]  ", this.getPointName());
        
    }
}
