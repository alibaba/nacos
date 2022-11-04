package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.tps.key.ClientIpMonitorKey;
import com.alibaba.nacos.plugin.control.tps.key.ConnectionIdMonitorKey;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKeyMatcher;
import com.alibaba.nacos.plugin.control.tps.nacos.LocalSimpleCountBarrierCreator;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.RuleModel;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
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
public class TpsBarrier {
    
    private String pointName;
    
    private RuleBarrier pointBarrier;
    
    public List<RuleBarrier> patternBarriers = new ArrayList<>();
    
    public TpsBarrier(String pointName) {
        this.pointName = pointName;
        pointBarrier = ruleBarrierCreator.createRuleBarrier(pointName, "", TimeUnit.SECONDS, RuleModel.FUZZY.name());
    }
    
    static RuleBarrierCreator ruleBarrierCreator;
    
    static {
        String tpsBarrierCreator = ControlConfigs.getInstance().getTpsBarrierCreator();
        Collection<RuleBarrierCreator> loadedCreators = NacosServiceLoader.load(RuleBarrierCreator.class);
        for (RuleBarrierCreator barrierCreator : loadedCreators) {
            if (tpsBarrierCreator.equalsIgnoreCase(barrierCreator.name())) {
                Loggers.CONTROL.info("Found tps rule creator of name : {}", tpsBarrierCreator);
                ruleBarrierCreator = barrierCreator;
                break;
            }
        }
        if (ruleBarrierCreator == null) {
            Loggers.CONTROL.warn("Fail to found tps rule creator of name : {},use  default local simple creator",
                    tpsBarrierCreator);
            ruleBarrierCreator = LocalSimpleCountBarrierCreator.getInstance();
        }
        
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
                BarrierCheckRequest barrierCheckRequest = tpsCheckRequest.buildBarrierCheckRequest(monitorKey);
                if (MonitorKeyMatcher.match(patternRuleBarrier.getPattern(), monitorKey.build())) {
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
        
        //2.when pattern fail,rollback applied count of patterns.
        if (!patternSuccess) {
            rollbackTps(appliedBarriers);
            Loggers.TPS.warn("[{}]denied by pattern barrier ={},clientIp={},connectionId={},msg={}", pointName,
                    denyPatternRate.getName(), tpsCheckRequest.getClientIp(), tpsCheckRequest.getConnectionId(),
                    denyPatternRate.getLimitMsg());
            return new TpsCheckResponse(false, TpsResultCode.CHECK_DENY,
                    (denyPatternRate == null) ? "unknown" : ("denied by " + denyPatternRate.getLimitMsg()));
        }
        
        //3. check point rule
        BarrierCheckRequest pointCheckRequest = new BarrierCheckRequest();
        pointCheckRequest.setCount(tpsCheckRequest.getCount());
        pointCheckRequest.setPointName(this.pointName);
        pointCheckRequest.setTimestamp(tpsCheckRequest.getTimestamp());
        TpsCheckResponse pointCheckSuccess = pointBarrier.applyTps(pointCheckRequest);
        if (pointCheckSuccess.isSuccess()) {
            return pointCheckSuccess;
        } else {
            //3.1 when point rule fail,rollback applied count of patterns.
            rollbackTps(appliedBarriers);
            Loggers.TPS.warn("[{}]denied by point barrier ={},clientIp={},connectionId={},msg={}", pointName,
                    pointBarrier.getName(), tpsCheckRequest.getClientIp(), tpsCheckRequest.getConnectionId(),
                    pointBarrier.getLimitMsg());
            return new TpsCheckResponse(false, TpsResultCode.CHECK_DENY,
                    "deny by point rule," + pointBarrier.getLimitMsg());
        }
        
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
            if (tpsCheckRequest.getMonitorKeys() == null) {
                tpsCheckRequest.setMonitorKeys(new ArrayList<>());
            }
            
            if (!StringUtils.isBlank(tpsCheckRequest.getClientIp())) {
                tpsCheckRequest.getMonitorKeys().add(new ClientIpMonitorKey(tpsCheckRequest.getClientIp()));
                tpsCheckRequest.setClientIp(null);
            }
            if (!StringUtils.isBlank(tpsCheckRequest.getConnectionId())) {
                tpsCheckRequest.getMonitorKeys().add(new ConnectionIdMonitorKey(tpsCheckRequest.getConnectionId()));
                tpsCheckRequest.setConnectionId(null);
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
        Loggers.CONTROL.info("Apply tps control rule parse start,pointName=[{}]  ", this.getPointName());
        
        //1.reset all monitor point for null.
        if (newControlRule == null) {
            Loggers.CONTROL.info("Clear all tps control rule ,pointName=[{}]  ", this.getPointName());
            this.pointBarrier.clearLimitRule();
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
            
            this.pointBarrier.setMaxCount(newPointRule.getMaxCount());
            this.pointBarrier.setMonitorType(newPointRule.getMonitorType());
        }
        
        //3.check monitor key rules.
        Map<String, RuleDetail> newMonitorKeyRules = newControlRule.getMonitorKeyRule();
        //3.1 clear all monitor keys.
        if (newMonitorKeyRules == null || newMonitorKeyRules.isEmpty()) {
            Loggers.CONTROL.info("Clear point  control rule for monitorKeys, pointName=[{}]  ", this.getPointName());
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
                    Loggers.CONTROL
                            .info("Add  point  control rule for client ip ,pointName=[{}],monitorKey=[{}], new maxTps={}, new monitorType={}, ",
                                    this.getPointName(), newMonitorRule.getKey(),
                                    newMonitorRule.getValue().getMaxCount(),
                                    newMonitorRule.getValue().getMonitorType());
                    // add rule
                    RuleBarrier rateCounterWrapper = ruleBarrierCreator
                            .createRuleBarrier(newMonitorRule.getKey(), newRuleDetail.getPattern(),
                                    newRuleDetail.getPeriod(), newRuleDetail.getModel());
                    rateCounterWrapper.applyRuleDetail(newRuleDetail);
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
            this.patternBarriers = patternRateCounterMap.values().stream().collect(Collectors.toList());
        }
        
    }
}
