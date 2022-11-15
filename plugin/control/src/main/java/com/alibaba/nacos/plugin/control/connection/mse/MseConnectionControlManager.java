package com.alibaba.nacos.plugin.control.connection.mse;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.connection.ConnectionMetricsCollector;
import com.alibaba.nacos.plugin.control.connection.mse.interceptor.ConnectionInterceptor;
import com.alibaba.nacos.plugin.control.connection.mse.interceptor.InterceptResult;
import com.alibaba.nacos.plugin.control.connection.mse.interceptor.InterceptorHolder;
import com.alibaba.nacos.plugin.control.connection.nacos.NacosConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckCode;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;
import com.alibaba.nacos.plugin.control.event.mse.ConnectionDeniedEvent;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MseConnectionControlManager extends NacosConnectionControlManager {
    
    @Override
    public String getName() {
        return "mse";
    }
    
    @Override
    public void applyConnectionLimitRule(ConnectionLimitRule connectionLimitRule) {
        super.applyConnectionLimitRule(connectionLimitRule);
        if (connectionLimitRule instanceof MseConnectionLimitRule) {
            Set<String> disabledInterceptors = ((MseConnectionLimitRule) connectionLimitRule).getDisabledInterceptors();
            Collection<ConnectionInterceptor> interceptors = InterceptorHolder.getInterceptors();
            for (ConnectionInterceptor tpsInterceptor : interceptors) {
                if (disabledInterceptors != null && disabledInterceptors.contains(tpsInterceptor.getName())) {
                    tpsInterceptor.setDisabled(true);
                } else {
                    tpsInterceptor.setDisabled(false);
                }
            }
            
            
        }
    }
    
    
    private ConnectionCheckResponse checkInternal(ConnectionCheckRequest connectionCheckRequest) {
        
        ConnectionCheckResponse connectionCheckResponse = new ConnectionCheckResponse();
        //limit rule check.
        if (this.connectionLimitRule != null) {
            String appName = connectionCheckRequest.getAppName();
            String clientIp = connectionCheckRequest.getClientIp();
            
            Map<String, Integer> metricsIpCount = super.metricsCollectorList.stream()
                    .collect(Collectors.toMap(ConnectionMetricsCollector::getName, a -> a.getCountForIp(clientIp)));
            
            int totalCountOfIp = metricsIpCount.values().stream().mapToInt(Integer::intValue).sum();
            
            //client ip limit check.
            int countLimitOfIp = connectionLimitRule.getCountLimitOfIp(clientIp);
            if (countLimitOfIp < 0) {
                countLimitOfIp = connectionLimitRule.getCountLimitOfApp(appName);
            }
            if (countLimitOfIp >= 0) {
                if (totalCountOfIp >= countLimitOfIp) {
                    connectionCheckResponse.setCheckCode(ConnectionCheckCode.DENY_BY_IP_OVER);
                    connectionCheckResponse.setMessage(
                            "Specific ip check over limit,max allowed count is " + countLimitOfIp
                                    + ",current count detail is " + metricsIpCount.toString() + "，monitorMode="
                                    + isMonitorMode());
                    Loggers.CONNECTION.warn("Specific ip or app ip limit ,maxCount allowed is {}"
                                    + ",clientIp={},appName={},source={},labels={}", countLimitOfIp,
                            connectionCheckRequest.getClientIp(), connectionCheckRequest.getAppName(),
                            connectionCheckRequest.getSource(), connectionCheckRequest.getLabels());
                    
                    return connectionCheckResponse;
                } else {
                    connectionCheckResponse.setCheckCode(ConnectionCheckCode.PASS_BY_IP);
                    connectionCheckResponse.setSuccess(true);
                    return connectionCheckResponse;
                }
            }
            
            //default client ip limit check
            int countLimitPerClientIpDefault = connectionLimitRule.getCountLimitPerClientIpDefault();
            if (countLimitPerClientIpDefault > 0 && totalCountOfIp >= countLimitPerClientIpDefault) {
                connectionCheckResponse.setCheckCode(ConnectionCheckCode.DENY_BY_IP_OVER);
                connectionCheckResponse.setMessage(
                        "deny by default ip check model,max allowed count is " + countLimitPerClientIpDefault
                                + ",current count detail is " + metricsIpCount.toString());
                Loggers.CONNECTION
                        .warn("connection denied by default ip limit ,maxCount allowed is  {},clientIp={},appName={},source={},labels={}",
                                countLimitPerClientIpDefault, connectionCheckRequest.getClientIp(),
                                connectionCheckRequest.getAppName(), connectionCheckRequest.getSource(),
                                connectionCheckRequest.getLabels());
                return connectionCheckResponse;
            }
            
            int totalCountLimit = connectionLimitRule.getCountLimit();
            Map<String, Integer> metricsTotalCount = metricsCollectorList.stream().collect(
                    Collectors.toMap(ConnectionMetricsCollector::getName, ConnectionMetricsCollector::getTotalCount));
            int totalCount = metricsTotalCount.values().stream().mapToInt(Integer::intValue).sum();
            //total count check model
            if (totalCountLimit >= 0 && totalCount >= totalCountLimit) {
                //deny;
                connectionCheckResponse.setCheckCode(ConnectionCheckCode.DENY_BY_TOTAL_OVER);
                connectionCheckResponse.setMessage(
                        "total count over limit,max allowed count is " + totalCountLimit + ",current count detail is "
                                + metricsTotalCount.toString());
                Loggers.CONNECTION.warn("total count over limit,max allowed count is {},current count detail is {}"
                                + ",detail={},clientIp={},appName={},source={},labels={}", totalCountLimit,
                        metricsTotalCount.toString(), connectionCheckRequest.getClientIp(),
                        connectionCheckRequest.getAppName(), connectionCheckRequest.getSource(),
                        connectionCheckRequest.getLabels());
                return connectionCheckResponse;
            }
            
            connectionCheckResponse.setSuccess(true);
            connectionCheckResponse.setCheckCode(ConnectionCheckCode.PASS_BY_TOTAL);
            connectionCheckResponse.setMessage("check pass");
            return connectionCheckResponse;
        } else {
            connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_SKIP);
            connectionCheckResponse.setSuccess(true);
            return connectionCheckResponse;
        }
        
    }
    
    private boolean isMonitorMode() {
        return (super.connectionLimitRule instanceof MseConnectionLimitRule
                && !((MseConnectionLimitRule) super.connectionLimitRule).isInterceptMode());
    }
    
    /**
     * check connection allowed.
     *
     * @param connectionCheckRequest connectionCheckRequest.
     * @return
     */
    public ConnectionCheckResponse check(ConnectionCheckRequest connectionCheckRequest) {
        
        ConnectionCheckResponse connectionCheckResponse = new ConnectionCheckResponse();
        
        //1.interceptor pre interceptor
        Collection<ConnectionInterceptor> interceptors = InterceptorHolder.getInterceptors();
        for (ConnectionInterceptor connectionInterceptor : interceptors) {
            if (connectionInterceptor.isDisabled()) {
                continue;
            }
            InterceptResult intercept = connectionInterceptor.preIntercept(connectionCheckRequest);
            if (intercept.equals(InterceptResult.CHECK_PASS)) {
                connectionCheckResponse.setCheckCode(ConnectionCheckCode.PASS_BY_PRE_INTERCEPT);
                connectionCheckResponse.setSuccess(true);
                connectionCheckResponse.setMessage("passed by pre interceptor :" + connectionInterceptor.getName());
                return connectionCheckResponse;
            } else if (intercept.equals(InterceptResult.CHECK_DENY)) {
                connectionCheckResponse.setCheckCode(ConnectionCheckCode.DENY_BY_PRE_INTERCEPT);
                connectionCheckResponse.setSuccess(false);
                String message = String
                        .format("denied by pre interceptor %s  ,clientIp=%s,appName=%s,source=%s,labels=%s",
                                connectionInterceptor.getName(), connectionCheckRequest.getClientIp(),
                                connectionCheckRequest.getAppName(), connectionCheckRequest.getSource(),
                                connectionCheckRequest.getLabels());
                connectionCheckResponse.setMessage(message);
                Loggers.CONNECTION.warn(message);
                ConnectionDeniedEvent connectionDeniedEvent = new ConnectionDeniedEvent(connectionCheckRequest,
                        connectionCheckResponse.getCheckCode(), message);
                if (isMonitorMode()) {
                    connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_SKIP);
                    connectionCheckResponse.setSuccess(true);
                    connectionDeniedEvent.setMonitorModel(true);
                }
                NotifyCenter.publishEvent(connectionDeniedEvent);
                
                return connectionCheckResponse;
            }
        }
        
        //2.check for rule
        connectionCheckResponse = checkInternal(connectionCheckRequest);
        boolean originalSuccess = connectionCheckResponse.isSuccess();
        String originalMsg = connectionCheckResponse.getMessage();
        ConnectionCheckCode originalConnectionCheckCode = connectionCheckResponse.getCheckCode();
        
        //3.post interceptor.
        InterceptResult interceptResult = postIntercept(connectionCheckRequest, connectionCheckResponse);
        if (originalSuccess && InterceptResult.CHECK_DENY == interceptResult) {
            //pass->deny
            connectionCheckResponse.setCheckCode(ConnectionCheckCode.DENY_BY_POST_INTERCEPT);
            connectionCheckResponse.setSuccess(false);
            String message = String
                    .format("over turned, denied by post interceptor ,clientIp=%s,appName=%s,source=%s,labels=%s",
                            connectionCheckRequest.getClientIp(), connectionCheckRequest.getAppName(),
                            connectionCheckRequest.getSource(), connectionCheckRequest.getLabels());
            connectionCheckResponse.setMessage(message);
        } else if (!originalSuccess && InterceptResult.CHECK_PASS == interceptResult) {
            //deny->pass
            connectionCheckResponse.setSuccess(true);
            connectionCheckResponse.setCheckCode(ConnectionCheckCode.PASS_BY_POST_INTERCEPT);
            String message = String
                    .format("over turned, passed by post interceptor ,clientIp=%s,appName=%s,source=%s,labels=%s",
                            connectionCheckRequest.getClientIp(), connectionCheckRequest.getAppName(),
                            connectionCheckRequest.getSource(), connectionCheckRequest.getLabels());
            connectionCheckResponse.setMessage(message);
        } else {
            //not changed
            connectionCheckResponse.setCheckCode(originalConnectionCheckCode);
            connectionCheckResponse.setSuccess(originalSuccess);
            connectionCheckResponse.setMessage(originalMsg);
        }
        
        if (!connectionCheckResponse.isSuccess()) {
            ConnectionDeniedEvent connectionDeniedEvent = new ConnectionDeniedEvent(connectionCheckRequest,
                    connectionCheckResponse.getCheckCode(), connectionCheckResponse.getMessage());
            boolean monitorMode = isMonitorMode();
            if (monitorMode) {
                //pass by monitor.
                connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_SKIP);
                connectionCheckResponse.setSuccess(true);
                connectionDeniedEvent.setMonitorModel(true);
            }
            NotifyCenter.publishEvent(connectionDeniedEvent);
            
        }
        
        return connectionCheckResponse;
        
    }
    
    private InterceptResult postIntercept(ConnectionCheckRequest connectionCheckRequest,
            ConnectionCheckResponse connectionCheckResponse) {
        for (ConnectionInterceptor connectionInterceptor : InterceptorHolder.getInterceptors()) {
            InterceptResult intercept = connectionInterceptor
                    .postIntercept(connectionCheckRequest, connectionCheckResponse);
            if (InterceptResult.CHECK_PASS.equals(intercept)) {
                String message = String.format("pass by interceptor %s  ,clientIp=%s,appName=%s,source=%s,labels=%s",
                        connectionInterceptor.getName(), connectionCheckRequest.getClientIp(),
                        connectionCheckRequest.getAppName(), connectionCheckRequest.getSource(),
                        connectionCheckRequest.getLabels());
                Loggers.CONNECTION.info(message);
                return intercept;
            } else if (InterceptResult.CHECK_DENY.equals(intercept)) {
                String message = String.format("denied by interceptor %s ,clientIp=%s,appName=%s,source=%s,labels=%s",
                        connectionInterceptor.getName(), connectionCheckRequest.getClientIp(),
                        connectionCheckRequest.getAppName(), connectionCheckRequest.getSource(),
                        connectionCheckRequest.getLabels());
                Loggers.CONNECTION.warn(message);
                return intercept;
            }
        }
        
        return InterceptResult.CHECK_SKIP;
    }
}
