package com.alibaba.nacos.plugin.control.connection;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.connection.interceptor.ConnectionInterceptor;
import com.alibaba.nacos.plugin.control.connection.interceptor.InterceptResult;
import com.alibaba.nacos.plugin.control.connection.interceptor.InterceptorHolder;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckCode;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;

import java.util.Collection;
import java.util.List;
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
            if (countLimitOfIp > 0) {
                if (totalCountOfIp >= countLimitOfIp) {
                    connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_DENY);
                    connectionCheckResponse.setMessage(
                            "deny by specific ip check model,max allowed count is " + countLimitOfIp
                                    + ",current count detail is " + metricsIpCount.toString());
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
                return connectionCheckResponse;
            }
            
            //total count check model
            if (connectionLimitRule.getCountLimit() >= 0 && totalCount >= connectionLimitRule.getCountLimit()) {
                //deny;
                connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_DENY);
                connectionCheckResponse.setMessage(
                        "deny by total count limit,max allowed count is " + connectionLimitRule.getCountLimit()
                                + ",current count detail is " + metricsTotalCount.toString());
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
