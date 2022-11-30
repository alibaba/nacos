package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.event.mse.TpsRequestDeniedEvent;
import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.mse.key.ClientIpMonitorKey;
import com.alibaba.nacos.plugin.control.tps.mse.key.ConnectionIdMonitorKey;
import com.alibaba.nacos.plugin.control.tps.mse.key.MatchType;
import com.alibaba.nacos.plugin.control.tps.mse.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.mse.key.MonitorKeyMatcher;
import com.alibaba.nacos.plugin.control.tps.mse.interceptor.InterceptResult;
import com.alibaba.nacos.plugin.control.tps.mse.interceptor.InterceptorHolder;
import com.alibaba.nacos.plugin.control.tps.mse.interceptor.TpsInterceptor;
import com.alibaba.nacos.plugin.control.tps.nacos.NacosTpsBarrier;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
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
public class MseTpsBarrier extends NacosTpsBarrier {
    
    protected List<MseRuleBarrier> patternBarriers = new ArrayList<>();
    
    public MseTpsBarrier(String pointName) {
        super(pointName);
        this.pointBarrier = MseRuleBarrierCreator.getInstance()
                .createRuleBarrier(pointName, pointName, TimeUnit.SECONDS);
    }
    
    /**
     * apply tps.
     *
     * @param tpsCheckRequest tpsCheckRequest.
     * @return check current tps is allowed.
     */
    public TpsCheckResponse applyTps(TpsCheckRequest tpsCheckRequest) {
        
        if (!(tpsCheckRequest instanceof MseTpsCheckRequest)) {
            MseTpsCheckRequest mseTpsCheckRequest = new MseTpsCheckRequest();
            BeanUtils.copyProperties(tpsCheckRequest, mseTpsCheckRequest);
            tpsCheckRequest = mseTpsCheckRequest;
        }
        
        MseTpsCheckRequest mseTpsCheckRequest = (MseTpsCheckRequest) tpsCheckRequest;
        boolean preInterceptPassed = false;
        //1.pre interceptor.
        for (TpsInterceptor tpsInterceptor : InterceptorHolder.getInterceptors()) {
            if (tpsInterceptor.getPointName().equalsIgnoreCase(mseTpsCheckRequest.getPointName()) && !tpsInterceptor
                    .isDisabled()) {
                InterceptResult intercept = tpsInterceptor.preIntercept(mseTpsCheckRequest);
                if (InterceptResult.CHECK_PASS.equals(intercept)) {
                    preInterceptPassed = true;
                } else if (InterceptResult.CHECK_DENY.equals(intercept)) {
                    boolean monitorType = getPointBarrier().isMonitorType();
                    
                    Loggers.TPS
                            .warn("[{}]denied by pre interceptor ={},clientIp={},connectionId={},keys={},monitorType={}",
                                    mseTpsCheckRequest.getPointName(), tpsInterceptor.getName(),
                                    mseTpsCheckRequest.getClientIp(), mseTpsCheckRequest.getConnectionId(),
                                    mseTpsCheckRequest.getMonitorKeys(), monitorType);
                    TpsCheckResponse tpsCheckResponse = new TpsCheckResponse(false,
                            MseTpsResultCode.PASS_BY_PRE_INTERCEPTOR,
                            "deny by pre interceptor :" + tpsInterceptor.getName());
                    TpsRequestDeniedEvent tpsRequestDeniedEvent = new TpsRequestDeniedEvent(mseTpsCheckRequest,
                            tpsCheckResponse.getCode(), tpsCheckResponse.getMessage());
                    if (monitorType) {
                        tpsCheckResponse.setSuccess(true);
                        tpsRequestDeniedEvent.setMonitorModel(true);
                    }
                    NotifyCenter.publishEvent(tpsRequestDeniedEvent);
                    return tpsCheckResponse;
                }
            }
        }
        
        Map<RuleBarrier, List<BarrierCheckRequest>> appliedBarriers = null;
        MseRuleBarrier denyPatternRate = null;
        boolean patternSuccess = true;
        List<MseRuleBarrier> patternBarriers = this.patternBarriers;
        
        buildIpOrConnectionIdKey(mseTpsCheckRequest);
        
        boolean patternExactMatchPassed = false;
        //1.check pattern barriers
        if (!CollectionUtils.isEmpty(mseTpsCheckRequest.getMonitorKeys())) {
            for (MseRuleBarrier patternRuleBarrier : patternBarriers) {
                for (MonitorKey monitorKey : mseTpsCheckRequest.getMonitorKeys()) {
                    
                    MatchType match = MonitorKeyMatcher.parse(patternRuleBarrier.getPattern(), monitorKey.build());
                    if (match.isMatch()) {
                        MseBarrierCheckRequest barrierCheckRequest = mseTpsCheckRequest
                                .buildBarrierCheckRequest(monitorKey);
                        barrierCheckRequest.setMonitorOnly(preInterceptPassed || patternExactMatchPassed);
                        TpsCheckResponse patternCheckResponse = patternRuleBarrier.applyTps(barrierCheckRequest);
                        if (patternCheckResponse.isSuccess()) {
                            if (appliedBarriers == null) {
                                appliedBarriers = new HashMap<>();
                            }
                            if (!appliedBarriers.containsKey(patternRuleBarrier)) {
                                appliedBarriers.putIfAbsent(patternRuleBarrier, new ArrayList<>());
                            }
                            
                            appliedBarriers.get(patternRuleBarrier).add(barrierCheckRequest);
                            if (MatchType.EXACT.equals(match) && !preInterceptPassed) {
                                patternExactMatchPassed = true;
                                Loggers.TPS
                                        .info("[{}]pass by exact pattern ={},barrier ={},clientIp={},connectionId={},monitorKey={}",
                                                pointName, patternRuleBarrier.getPattern(),
                                                patternRuleBarrier.getRuleName(), mseTpsCheckRequest.getClientIp(),
                                                mseTpsCheckRequest.getConnectionId(), monitorKey);
                            }
                            
                            if (TpsResultCode.PASS_BY_MONITOR == (patternCheckResponse.getCode())
                                    && !preInterceptPassed) {
                                TpsRequestDeniedEvent tpsRequestDeniedEvent = new TpsRequestDeniedEvent(
                                        mseTpsCheckRequest, patternCheckResponse.getCode(),
                                        "pattern tps over limit ,but pass by monitor type.");
                                tpsRequestDeniedEvent.setMonitorModel(true);
                                NotifyCenter.publishEvent(tpsRequestDeniedEvent);
                            }
                            
                        } else {
                            TpsRequestDeniedEvent tpsRequestDeniedEvent = new TpsRequestDeniedEvent(mseTpsCheckRequest,
                                    patternCheckResponse.getCode(), patternCheckResponse.getMessage());
                            tpsRequestDeniedEvent.setMonitorModel(true);
                            NotifyCenter.publishEvent(tpsRequestDeniedEvent);
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
        }
        
        TpsCheckResponse tpsCheckResponse = null;
        boolean pointCheckSuccess = false;
        MseBarrierCheckRequest pointCheckRequest = null;
        //2.when pattern fail,rollback applied count of patterns.
        if (!patternSuccess) {
            tpsCheckResponse = new TpsCheckResponse(false, MseTpsResultCode.DENY_BY_PATTERN, "");
            String message = String
                    .format("[%s] pattern barrier [%s],pattern=[%s], check fail,monitorKeys=%s,msg=%s", pointName,
                            denyPatternRate.getRuleName(), denyPatternRate.pattern, mseTpsCheckRequest.getMonitorKeys(),
                            denyPatternRate.getLimitMsg());
            Loggers.TPS.warn(message);
            tpsCheckResponse.setMessage(message);
            
        } else {
            //3. check point rule
            pointCheckRequest = new MseBarrierCheckRequest();
            pointCheckRequest.setCount(mseTpsCheckRequest.getCount());
            pointCheckRequest.setPointName(this.pointName);
            pointCheckRequest.setTimestamp(mseTpsCheckRequest.getTimestamp());
            pointCheckRequest.setFlow(mseTpsCheckRequest.getFlow());
            pointCheckRequest.setMonitorOnly(patternExactMatchPassed || preInterceptPassed);
            tpsCheckResponse = pointBarrier.applyTps(pointCheckRequest);
            if (!tpsCheckResponse.isSuccess()) {
                tpsCheckResponse.setCode(TpsResultCode.DENY_BY_POINT);
                String message = "denied by point,msg=" + pointBarrier.getLimitMsg();
                tpsCheckResponse.setMessage(message);
            } else {
                pointCheckSuccess = true;
                if (TpsResultCode.PASS_BY_MONITOR == tpsCheckResponse.getCode()) {
                    TpsRequestDeniedEvent tpsRequestDeniedEvent = new TpsRequestDeniedEvent(mseTpsCheckRequest,
                            tpsCheckResponse.getCode(), "point tps over limit ,but pass by monitor type.");
                    tpsRequestDeniedEvent.setMonitorModel(true);
                    NotifyCenter.publishEvent(tpsRequestDeniedEvent);
                }
            }
        }
        
        //pre intercept has higher priority  than post interceptor.
        if (preInterceptPassed) {
            tpsCheckResponse.setCode(MseTpsResultCode.PASS_BY_PRE_INTERCEPTOR);
            return tpsCheckResponse;
        }
        
        if (patternExactMatchPassed) {
            tpsCheckResponse.setCode(MseTpsResultCode.PASS_BY_PATTERN);
        }
        
        boolean originalCheckSuccess = tpsCheckResponse.isSuccess();
        final int originalCheck = tpsCheckResponse.getCode();
        String originalMsg = tpsCheckResponse.getMessage();
        
        InterceptResult interceptResult = postInterceptor(mseTpsCheckRequest, tpsCheckResponse);
        if (originalCheckSuccess && InterceptResult.CHECK_DENY.equals(interceptResult)) {
            //pass -> deny
            rollbackTps(appliedBarriers);
            if (pointCheckSuccess && pointCheckRequest != null) {
                pointBarrier.rollbackTps(pointCheckRequest);
            }
            tpsCheckResponse.setSuccess(false);
            tpsCheckResponse.setMessage("denied by post interceptor");
            tpsCheckResponse.setCode(MseTpsResultCode.DENY_BY_POST_INTERCEPTOR);
            return tpsCheckResponse;
            
        } else if (!originalCheckSuccess && InterceptResult.CHECK_PASS.equals(interceptResult)) {
            //deny -> pass
            tpsCheckResponse.setSuccess(true);
            tpsCheckResponse.setMessage("passed by post interceptor");
            tpsCheckResponse.setCode(MseTpsResultCode.PASS_BY_POST_INTERCEPTOR);
            return tpsCheckResponse;
        }
        
        //not over turned
        tpsCheckResponse.setSuccess(originalCheckSuccess);
        tpsCheckResponse.setMessage(originalMsg);
        tpsCheckResponse.setCode(originalCheck);
        if (!tpsCheckResponse.isSuccess()) {
            rollbackTps(appliedBarriers);
            NotifyCenter.publishEvent(
                    new TpsRequestDeniedEvent(mseTpsCheckRequest, tpsCheckResponse.getCode(), originalMsg));
        }
        return tpsCheckResponse;
    }
    
    private InterceptResult postInterceptor(MseTpsCheckRequest tpsCheckRequest, TpsCheckResponse tpsCheckResponse) {
        for (TpsInterceptor tpsInterceptor : InterceptorHolder.getInterceptors()) {
            if (tpsInterceptor.getPointName().equals(tpsCheckRequest.getPointName())) {
                InterceptResult intercept = tpsInterceptor.postIntercept(tpsCheckRequest, tpsCheckResponse);
                if (intercept.equals(InterceptResult.CHECK_PASS)) {
                    Loggers.TPS.warn("[{}] pass by interceptor ={},keys={}", tpsCheckRequest.getPointName(),
                            tpsInterceptor.getName(), tpsCheckRequest.getMonitorKeys());
                    return InterceptResult.CHECK_PASS;
                } else if (intercept.equals(InterceptResult.CHECK_DENY)) {
                    boolean monitorType = getPointBarrier().isMonitorType();
                    
                    String message = String.format("[%s] denied by interceptor =%s,keys=%s,monitorType=%s",
                            tpsCheckRequest.getPointName(), tpsInterceptor.getName(), tpsCheckRequest.getMonitorKeys(),
                            monitorType);
                    Loggers.TPS.warn(message);
                    TpsRequestDeniedEvent tpsRequestDeniedEvent = new TpsRequestDeniedEvent(tpsCheckRequest,
                            MseTpsResultCode.DENY_BY_POST_INTERCEPTOR, message);
                    if (getPointBarrier().isMonitorType()) {
                        tpsRequestDeniedEvent.setMonitorModel(true);
                    }
                    
                    NotifyCenter.publishEvent(tpsRequestDeniedEvent);
                    
                    return monitorType ? InterceptResult.CHECK_SKIP : InterceptResult.CHECK_DENY;
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
    
    private void buildIpOrConnectionIdKey(MseTpsCheckRequest tpsCheckRequest) {
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
    
    public RuleBarrier getPointBarrier() {
        return pointBarrier;
    }
    
    public List<MseRuleBarrier> getPatternBarriers() {
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
        
        if (!(newControlRule instanceof MseTpsControlRule)) {
            MseTpsControlRule mseTpsControlRule = new MseTpsControlRule();
            BeanUtils.copyProperties(newControlRule, mseTpsControlRule);
            newControlRule = mseTpsControlRule;
        }
        
        MseTpsControlRule mseNewControlRule = (MseTpsControlRule) newControlRule;
        
        Loggers.CONTROL.info("Apply tps control rule start,pointName=[{}]  ", this.getPointName());
        
        //1.reset all monitor point for null.
        if (mseNewControlRule == null) {
            Loggers.CONTROL.info("Clear all tps control rule ,pointName=[{}]  ", this.getPointName());
            this.pointBarrier.clearLimitRule();
            this.patternBarriers = new ArrayList<>();
            return;
        }
        
        //2.check point rule.
        RuleDetail newPointRule = mseNewControlRule.getPointRule();
        if (newPointRule == null) {
            Loggers.CONTROL.info("Clear point  control rule ,pointName=[{}]  ", this.getPointName());
            this.pointBarrier.clearLimitRule();
        } else {
            Loggers.CONTROL.info("Update  point  control rule ,pointName=[{}],original maxTps={}, new maxTps={}"
                            + ",original monitorType={}, original monitorType={}, ", this.getPointName(),
                    this.pointBarrier.getMaxCount(), newPointRule.getMaxCount(), this.pointBarrier.getMonitorType(),
                    newPointRule.getMonitorType());
            newPointRule.setRuleName(pointName);
            this.pointBarrier.applyRuleDetail(newPointRule);
        }
        
        //3.check monitor key rules.
        Map<String, MseRuleDetail> newMonitorKeyRules = mseNewControlRule.getMonitorKeyRule();
        //3.1 clear all monitor keys.
        if (newMonitorKeyRules == null || newMonitorKeyRules.isEmpty()) {
            Loggers.CONTROL.info("Clear point  control rule for monitorKeys, pointName=[{}]  ", this.getPointName());
            this.patternBarriers = new ArrayList<>();
        } else {
            Map<String, MseRuleBarrier> patternRateCounterMap = this.patternBarriers.stream()
                    .collect(Collectors.toMap(a -> a.getRuleName(), Function.identity(), (key1, key2) -> key1));
            
            for (Map.Entry<String, MseRuleDetail> newMonitorRule : newMonitorKeyRules.entrySet()) {
                if (newMonitorRule.getValue() == null) {
                    continue;
                }
                if (newMonitorRule.getValue().getPattern() == null) {
                    newMonitorRule.getValue().setPattern(newMonitorRule.getKey());
                }
                
                MseRuleDetail newRuleDetail = newMonitorRule.getValue();
                if (newRuleDetail.getPeriod() == null) {
                    newRuleDetail.setPeriod(TimeUnit.SECONDS);
                }
                
                if (newRuleDetail.getModel() == null) {
                    newRuleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
                }
                
                //update rule.
                if (patternRateCounterMap.containsKey(newMonitorRule.getKey())) {
                    RuleBarrier rateCounterWrapper = patternRateCounterMap.get(newMonitorRule.getKey());
                    rateCounterWrapper.applyRuleDetail(newRuleDetail);
                } else {
                    Loggers.CONTROL
                            .info("pointName=[{}] ,Add  pattern control rule, name={}, pattern={}, new maxTps={}, new monitorType={} ",
                                    this.getPointName(), newMonitorRule.getKey(),
                                    newMonitorRule.getValue().getPattern(), newMonitorRule.getValue().getMaxCount(),
                                    newMonitorRule.getValue().getMonitorType());
                    // add rule
                    MseRuleBarrier rateCounterWrapper = (MseRuleBarrier) MseRuleBarrierCreator.getInstance().
                            createRuleBarrier(pointName, newMonitorRule.getKey(), newRuleDetail.getPeriod());
                    rateCounterWrapper.applyRuleDetail(newRuleDetail);
                    patternRateCounterMap.put(newMonitorRule.getKey(), rateCounterWrapper);
                }
            }
            
            //delete rule.
            Iterator<Map.Entry<String, MseRuleBarrier>> currentPatternCounterIterator = patternRateCounterMap.entrySet()
                    .iterator();
            while (currentPatternCounterIterator.hasNext()) {
                Map.Entry<String, MseRuleBarrier> currentPattern = currentPatternCounterIterator.next();
                if (!newMonitorKeyRules.containsKey(currentPattern.getKey())) {
                    Loggers.CONTROL.info("Delete  point  control rule for pointName=[{}] ,monitorKey=[{}]",
                            this.getPointName(), currentPattern.getKey());
                    currentPatternCounterIterator.remove();
                }
            }
            //exact pattern has higher priority.
            this.patternBarriers = patternRateCounterMap.values().stream()
                    .sorted(Comparator.comparing(MseRuleBarrier::getOrder, Comparator.naturalOrder()))
                    .collect(Collectors.toList());
        }
        
        Loggers.CONTROL.info("Apply tps control rule end,pointName=[{}]  ", this.getPointName());
        
    }
}
