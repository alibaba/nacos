package com.alibaba.nacos.plugin.control.connection;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.connection.interceptor.ConnectionInterceptor;
import com.alibaba.nacos.plugin.control.connection.interceptor.InterceptResult;
import com.alibaba.nacos.plugin.control.connection.interceptor.InterceptorHolder;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckCode;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;
import com.alibaba.nacos.plugin.control.event.ConnectionDeniedEvent;
import com.alibaba.nacos.plugin.control.event.TpsRequestDeniedEvent;
import com.alibaba.nacos.plugin.control.ruleactivator.LocalDiskRuleActivator;
import com.alibaba.nacos.plugin.control.ruleactivator.PersistRuleActivatorProxy;
import com.alibaba.nacos.plugin.control.ruleactivator.RuleParserProxy;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * connection control manager
 */
public class ConnectionControlManager {
    
    private ConnectionLimitRule connectionLimitRule;
    
    Collection<ConnectionMetricsCollector> metricsCollectorList;
    
    public ConnectionControlManager() {
        metricsCollectorList = NacosServiceLoader.load(ConnectionMetricsCollector.class);
        Loggers.CONTROL.info("Load connection metrics collector,size={},{}", metricsCollectorList.size(),
                metricsCollectorList);
        String localRuleContent = LocalDiskRuleActivator.INSTANCE.getConnectionRule();
        if (StringUtils.isNotBlank(localRuleContent)) {
            Loggers.CONTROL.info("Found local disk connection rule content ,value  ={}", localRuleContent);
        } else if (PersistRuleActivatorProxy.getInstance() != null
                && PersistRuleActivatorProxy.getInstance().getConnectionRule() != null) {
            localRuleContent = PersistRuleActivatorProxy.getInstance().getConnectionRule();
            if (StringUtils.isNotBlank(localRuleContent)) {
                Loggers.CONTROL.info("Found persist disk connection rule content ,value  ={}", localRuleContent);
            }
        }
        
        if (StringUtils.isNotBlank(localRuleContent)) {
            connectionLimitRule = RuleParserProxy.getInstance().parseConnectionRule(localRuleContent);
        } else {
            Loggers.CONTROL.info("No connection rule content found ,use default empty rule ");
            connectionLimitRule = new ConnectionLimitRule();
        }
        
    }
    
    public ConnectionLimitRule getConnectionLimitRule() {
        return connectionLimitRule;
    }
    
    public void setConnectionLimitRule(ConnectionLimitRule connectionLimitRule) {
        this.connectionLimitRule = connectionLimitRule;
    }
    
    /**
     * check connection allowed.
     *
     * @param connectionCheckRequest
     * @return
     */
    public ConnectionCheckResponse check(ConnectionCheckRequest connectionCheckRequest) {
        
        ConnectionCheckResponse connectionCheckResponse = new ConnectionCheckResponse();
        
        //interceptor check for allowed and denied without check count.
        Collection<ConnectionInterceptor> interceptors = InterceptorHolder.getInterceptors();
        for (ConnectionInterceptor connectionInterceptor : interceptors) {
            InterceptResult intercept = connectionInterceptor.intercept(connectionCheckRequest);
            if (intercept.equals(InterceptResult.CHECK_PASS)) {
                connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_PASS);
                connectionCheckResponse.setSuccess(true);
                connectionCheckResponse.setMessage("passed by interceptor :" + connectionInterceptor.getName());
                return connectionCheckResponse;
            } else if (intercept.equals(InterceptResult.CHECK_DENY)) {
                connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_DENY);
                connectionCheckResponse.setSuccess(false);
                connectionCheckResponse.setMessage("denied by interceptor :" + connectionInterceptor.getName());
                Loggers.CONNECTION.warn("connection denied ,clientIp={},appName={},source={},labels={}",
                        connectionCheckRequest.getClientIp(), connectionCheckRequest.getAppName(),
                        connectionCheckRequest.getSource(), connectionCheckRequest.getLabels());
                NotifyCenter.publishEvent(new ConnectionDeniedEvent(connectionCheckRequest,
                        "denied by interceptor :" + connectionInterceptor.getName()));
                
                return connectionCheckResponse;
            }
        }
        
        //limit rule check.
        if (this.connectionLimitRule != null) {
            String appName = connectionCheckRequest.getAppName();
            String clientIp = connectionCheckRequest.getClientIp();
            
            Map<String, Integer> metricsTotalCount = metricsCollectorList.stream().collect(
                    Collectors.toMap(ConnectionMetricsCollector::getName, ConnectionMetricsCollector::getTotalCount));
            
            Map<String, Integer> metricsIpCount = metricsCollectorList.stream()
                    .collect(Collectors.toMap(ConnectionMetricsCollector::getName, a -> a.getCountForIp(clientIp)));
            
            int totalCount = metricsTotalCount.values().stream().mapToInt(Integer::intValue).sum();
            int totalCountOfIp = metricsIpCount.values().stream().mapToInt(Integer::intValue).sum();
            
            //client ip limit check model;
            int countLimitOfIp = connectionLimitRule.getCountLimitOfIp(clientIp);
            if (countLimitOfIp < 0) {
                countLimitOfIp = connectionLimitRule.getCountLimitOfApp(appName);
            }
            if (countLimitOfIp >= 0) {
                if (totalCountOfIp >= countLimitOfIp) {
                    connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_DENY);
                    connectionCheckResponse.setMessage(
                            "deny by specific ip check model,max allowed count is " + countLimitOfIp
                                    + ",current count detail is " + metricsIpCount.toString());
                    Loggers.CONNECTION
                            .warn("connection denied by specific ip or app ip limit ,maxCount allowed is  {},clientIp={},appName={},source={},labels={}",
                                    countLimitOfIp, connectionCheckRequest.getClientIp(),
                                    connectionCheckRequest.getAppName(), connectionCheckRequest.getSource(),
                                    connectionCheckRequest.getLabels());
                    NotifyCenter.publishEvent(new ConnectionDeniedEvent(connectionCheckRequest,
                            "connection denied by specific ip or app ip limit ,maxCount allowed is  "
                                    + countLimitOfIp));
                    return connectionCheckResponse;
                } else {
                    connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_PASS);
                    connectionCheckResponse.setSuccess(true);
                    return connectionCheckResponse;
                }
            }
            
            //default client ip limit check model;
            int countLimitPerClientIpDefault = connectionLimitRule.getCountLimitPerClientIpDefault();
            if (countLimitPerClientIpDefault > 0 && totalCountOfIp >= countLimitPerClientIpDefault) {
                connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_DENY);
                connectionCheckResponse.setMessage(
                        "deny by default ip check model,max allowed count is " + countLimitPerClientIpDefault
                                + ",current count detail is " + metricsIpCount.toString());
                Loggers.CONNECTION
                        .warn("connection denied by default ip limit ,maxCount allowed is  {},clientIp={},appName={},source={},labels={}",
                                countLimitPerClientIpDefault, connectionCheckRequest.getClientIp(),
                                connectionCheckRequest.getAppName(), connectionCheckRequest.getSource(),
                                connectionCheckRequest.getLabels());
                NotifyCenter.publishEvent(new ConnectionDeniedEvent(connectionCheckRequest,
                        "connection denied by default ip limit ,maxCount allowed is  " + countLimitPerClientIpDefault));
                return connectionCheckResponse;
            }
            
            int totalCountLimit = connectionLimitRule.getCountLimit();
            //total count check model
            if (totalCountLimit >= 0 && totalCount >= totalCountLimit) {
                //deny;
                connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_DENY);
                connectionCheckResponse.setMessage("deny by total count limit,max allowed count is " + totalCountLimit
                        + ",current count detail is " + metricsTotalCount.toString());
                Loggers.CONNECTION
                        .warn("connection denied by total count  limit ,maxCount allowed is  {},detail={},clientIp={},appName={},source={},labels={}",
                                totalCountLimit, metricsTotalCount.toString(), connectionCheckRequest.getClientIp(),
                                connectionCheckRequest.getAppName(), connectionCheckRequest.getSource(),
                                connectionCheckRequest.getLabels());
                NotifyCenter.publishEvent(new ConnectionDeniedEvent(connectionCheckRequest,
                        "connection denied by total count  limit ,maxCount allowed is " + totalCountLimit));
                return connectionCheckResponse;
            }
            
            connectionCheckResponse.setSuccess(true);
            connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_PASS);
            connectionCheckResponse.setMessage("check pass");
            return connectionCheckResponse;
        } else {
            connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_SKIP);
            connectionCheckResponse.setSuccess(true);
            return connectionCheckResponse;
        }
        
        
    }
}
