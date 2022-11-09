package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.event.TpsRequestDeniedEvent;
import com.alibaba.nacos.plugin.control.tps.interceptor.InterceptResult;
import com.alibaba.nacos.plugin.control.tps.interceptor.InterceptorHolder;
import com.alibaba.nacos.plugin.control.tps.interceptor.TpsInterceptor;
import com.alibaba.nacos.plugin.control.tps.key.ClientIpMonitorKey;
import com.alibaba.nacos.plugin.control.tps.key.ConnectionIdMonitorKey;
import com.alibaba.nacos.plugin.control.tps.key.MatchType;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
        
        //pre interceptor.
        for (TpsInterceptor tpsInterceptor : InterceptorHolder.getInterceptors()) {
            if (tpsInterceptor.getPointName().equalsIgnoreCase(tpsCheckRequest.getPointName())) {
                InterceptResult intercept = tpsInterceptor.preIntercept(tpsCheckRequest);
                if (intercept.equals(InterceptResult.CHECK_PASS)) {
                    return new TpsCheckResponse(true, TpsResultCode.CHECK_PASS,
                            "pass by interceptor :" + tpsInterceptor.getName());
                } else if (intercept.equals(InterceptResult.CHECK_DENY)) {
                    Loggers.TPS.warn("[{}]denied by interceptor ={},clientIp={},connectionId={},keys={}",
                            tpsCheckRequest.getPointName(), tpsInterceptor.getName(), tpsCheckRequest.getClientIp(),
                            tpsCheckRequest.getConnectionId(), tpsCheckRequest.getMonitorKeys());
                    return new TpsCheckResponse(false, TpsResultCode.CHECK_DENY,
                            "deny by interceptor :" + tpsInterceptor.getName());
                }
            }
        }
        
        Map<RuleBarrier, List<BarrierCheckRequest>> appliedBarriers = null;
        RuleBarrier denyPatternRate = null;
        boolean patternSuccess = true;
        List<RuleBarrier> patternBarriers = this.patternBarriers;
        
        buildIpOrConnectionIdKey(tpsCheckRequest);
        boolean monitorOnly = false;
        //1.check pattern barriers
        for (MonitorKey monitorKey : tpsCheckRequest.getMonitorKeys()) {
            
            for (RuleBarrier patternRuleBarrier : patternBarriers) {
                
                MatchType match = MonitorKeyMatcher.parse(patternRuleBarrier.getPattern(), monitorKey.build());
                if (match.isMatch()) {
                    BarrierCheckRequest barrierCheckRequest = tpsCheckRequest.buildBarrierCheckRequest(monitorKey);
                    barrierCheckRequest.setMonitorOnly(monitorOnly);
                    TpsCheckResponse patternCheckResponse = patternRuleBarrier.applyTps(barrierCheckRequest);
                    if (patternCheckResponse.isSuccess()) {
                        if (appliedBarriers == null) {
                            appliedBarriers = new HashMap<>();
                        }
                        if (!appliedBarriers.containsKey(patternRuleBarrier)) {
                            appliedBarriers.putIfAbsent(patternRuleBarrier, new ArrayList<>());
                        }
                        
                        appliedBarriers.get(patternRuleBarrier).add(barrierCheckRequest);
                        if (MatchType.EXACT.equals(match)) {
                            monitorOnly = true;
                            Loggers.TPS
                                    .info("[{}]pass by exact pattern ={},barrier ={},clientIp={},connectionId={},monitorKey={}",
                                            pointName, patternRuleBarrier.getPattern(),
                                            patternRuleBarrier.getRuleName(), tpsCheckRequest.getClientIp(),
                                            tpsCheckRequest.getConnectionId(), monitorKey);
                        }
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
            TpsCheckResponse tpsCheckResponse = new TpsCheckResponse(false, TpsResultCode.CHECK_DENY,
                    (denyPatternRate == null) ? "unknown" : ("denied by " + denyPatternRate.getLimitMsg()));
            InterceptResult interceptResult = postInterceptor(tpsCheckRequest, tpsCheckResponse);
            if (interceptResult.equals(InterceptResult.CHECK_PASS)) {
                //denied by pattern ,but passed by interceptor.
                tpsCheckResponse.setSuccess(true);
                tpsCheckResponse
                        .setMessage("denied by pattern " + denyPatternRate.getRuleName() + ", but pass by interceptor");
                return tpsCheckResponse;
            } else {
                rollbackTps(appliedBarriers);
                Loggers.TPS.warn("[{}]denied by pattern barrier [{}],monitorKeys={},msg={}", pointName,
                        denyPatternRate.getRuleName(), tpsCheckRequest.getMonitorKeys(), denyPatternRate.getLimitMsg());
                NotifyCenter.publishEvent(new TpsRequestDeniedEvent(tpsCheckRequest, denyPatternRate.getLimitMsg()));
                
                return new TpsCheckResponse(false, TpsResultCode.CHECK_DENY,
                        (denyPatternRate == null) ? "unknown" : ("denied by " + denyPatternRate.getLimitMsg()));
            }
            
        }
        
        //3. check point rule
        BarrierCheckRequest pointCheckRequest = new BarrierCheckRequest();
        pointCheckRequest.setCount(tpsCheckRequest.getCount());
        pointCheckRequest.setPointName(this.pointName);
        pointCheckRequest.setTimestamp(tpsCheckRequest.getTimestamp());
        pointCheckRequest.setMonitorOnly(monitorOnly);
        TpsCheckResponse pointCheck = pointBarrier.applyTps(pointCheckRequest);
        InterceptResult interceptResult = postInterceptor(tpsCheckRequest, pointCheck);
        //point check pass,post interceptor not deny,return success
        if (pointCheck.isSuccess() && !interceptResult.equals(InterceptResult.CHECK_DENY)) {
            return pointCheck;
        } else if (pointCheck.isSuccess() && interceptResult.equals(InterceptResult.CHECK_DENY)) {
            //point check pass,post interceptor  deny,return false
            pointBarrier.rollbackTps(pointCheckRequest);
            rollbackTps(appliedBarriers);
            pointCheck.setSuccess(false);
            pointCheck.setCode(TpsResultCode.CHECK_DENY);
            String message = "pass by barrier,but denied by interceptor";
            pointCheck.setMessage(message);
            NotifyCenter.publishEvent(new TpsRequestDeniedEvent(tpsCheckRequest, message));
            return pointCheck;
        } else if (!pointCheck.isSuccess() && interceptResult.equals(InterceptResult.CHECK_PASS)) {
            //point check denied,but post interceptor pass,return true
            pointCheck.setSuccess(true);
            pointCheck.setCode(TpsResultCode.PASS_BY_INTERCEPTOR);
            pointCheck.setMessage("denied by point " + pointBarrier.getRuleName() + ", but pass by interceptor");
            return pointCheck;
        } else {
            //point check denied,and post interceptor not pass.
            rollbackTps(appliedBarriers);
            Loggers.TPS.warn("[{}]denied by point barrier ={},clientIp={},connectionId={},msg={}", pointName,
                    pointBarrier.getRuleName(), tpsCheckRequest.getClientIp(), tpsCheckRequest.getConnectionId(),
                    pointBarrier.getLimitMsg());
            NotifyCenter.publishEvent(new TpsRequestDeniedEvent(tpsCheckRequest, pointBarrier.getLimitMsg()));
            return pointCheck;
        }
        
    }
    
    private InterceptResult postInterceptor(TpsCheckRequest tpsCheckRequest, TpsCheckResponse tpsCheckResponse) {
        for (TpsInterceptor tpsInterceptor : InterceptorHolder.getInterceptors()) {
            if (tpsInterceptor.getPointName().equals(tpsCheckRequest.getPointName())) {
                InterceptResult intercept = tpsInterceptor.postIntercept(tpsCheckRequest, tpsCheckResponse);
                if (intercept.equals(InterceptResult.CHECK_PASS)) {
                    Loggers.TPS.warn("[{}]pass by interceptor ={},keys={}", tpsCheckRequest.getPointName(),
                            tpsInterceptor.getName(), tpsCheckRequest.getMonitorKeys());
                    return InterceptResult.CHECK_PASS;
                } else if (intercept.equals(InterceptResult.CHECK_DENY)) {
                    Loggers.TPS.warn("[{}]denied by interceptor ={},keys={}", tpsCheckRequest.getPointName(),
                            tpsInterceptor.getName(), tpsCheckRequest.getMonitorKeys());
                    return InterceptResult.CHECK_DENY;
                }
            }
        }
        return InterceptResult.CHECK_SKIP;
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
    
    public RuleBarrier getPointBarrier() {
        return pointBarrier;
    }
    
    public List<RuleBarrier> getPatternBarriers() {
        return patternBarriers;
    }
    
    public String getPointName() {
        return pointName;
    }
    
    /**
     * apply rule.
     *
     * @param newControlRule newControlRule.
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
                            .info("pointName=[{}] ,Add  point  control rule for pattern , name={}, pattern={}, new maxTps={}, new monitorType={} ",
                                    this.getPointName(), newMonitorRule.getKey(),
                                    newMonitorRule.getValue().getPattern(), newMonitorRule.getValue().getMaxCount(),
                                    newMonitorRule.getValue().getMonitorType());
                    // add rule
                    RuleBarrier rateCounterWrapper = ruleBarrierCreator
                            .createRuleBarrier(newMonitorRule.getKey(), newRuleDetail.getPattern(),
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
            this.patternBarriers = patternRateCounterMap.values().stream()
                    .sorted(Comparator.comparing(RuleBarrier::getOrder, Comparator.reverseOrder()))
                    .collect(Collectors.toList());
        }
        
    }
}
