package com.alibaba.nacos.plugin.control.connection;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.connection.interceptor.ConnectionInterceptor;
import com.alibaba.nacos.plugin.control.connection.interceptor.InterceptorHolder;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckCode;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;

import java.util.List;

/**
 * abstract tps control manager
 */
public class ConnectionControlManager {
    
    
    private ConnectionLimitRule connectionLimitRule;
    
    List<ConnectionMetricsCollector> metricsCollectorList;
    
    public ConnectionControlManager() {
        NacosServiceLoader.load(ConnectionMetricsCollector.class);
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
        
        //interceptor check for allowed and denyde without check count.
        List<ConnectionInterceptor> interceptors = InterceptorHolder.getInterceptors();
        for (ConnectionInterceptor connectionInterceptor : interceptors) {
        
        }
        ConnectionCheckResponse connectionCheckResponse = new ConnectionCheckResponse();
        if (this.connectionLimitRule != null) {
            String appName = connectionCheckRequest.getAppName();
            String clientIp = connectionCheckRequest.getClientIp();
            int totalCount = metricsCollectorList.stream().mapToInt(a -> a.getTotalCount()).sum();
            int totalCountOfIp = metricsCollectorList.stream().mapToInt(a -> a.getCountForIp(clientIp)).sum();
            
            //client ip limit check model;
            int countLimitOfIp = connectionLimitRule.getCountLimitOfIp(clientIp);
            if (countLimitOfIp < 0) {
                countLimitOfIp = connectionLimitRule.getCountLimitOfApp(appName);
            }
            if (countLimitOfIp > 0) {
                if (totalCountOfIp >= countLimitOfIp) {
                    connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_DENY);
                    connectionCheckResponse.setMessage(
                            "deny by total ip check model,count over limit,max allowed  count is " + totalCountOfIp);
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
                connectionCheckResponse.setMessage("deny by total ip check model,count over limit,max allowed count is "
                        + countLimitPerClientIpDefault);
                return connectionCheckResponse;
            }
            
            //total count check model
            if (connectionLimitRule.getCountLimit() >= 0 && totalCount >= connectionLimitRule.getCountLimit()) {
                //deny;
                connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_DENY);
                connectionCheckResponse.setMessage("deny by total count overlimit");
                return connectionCheckResponse;
            }
            
            connectionCheckResponse.setSuccess(true);
            connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_SKIP);
            connectionCheckResponse.setMessage("check skip");
            return connectionCheckResponse;
        } else {
            connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_SKIP);
            connectionCheckResponse.setSuccess(true);
            return connectionCheckResponse;
        }
        
        
    }
}
