package com.alibaba.nacos.plugin.control.connection.nacos;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.ConnectionMetricsCollector;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckCode;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * connection control manager.
 */
public class NacosConnectionControlManager extends ConnectionControlManager {
    
    @Override
    public String getName() {
        return "nacos";
    }
    
    public NacosConnectionControlManager() {
        super();
    }
    
    @Override
    public void applyConnectionLimitRule(ConnectionLimitRule connectionLimitRule) {
        super.connectionLimitRule = connectionLimitRule;
        Loggers.CONTROL.info("Connection control rule updated to ->" + (this.connectionLimitRule == null ? null
                : JacksonUtils.toJson(this.connectionLimitRule)));
        
    }
    
    @Override
    public ConnectionCheckResponse check(ConnectionCheckRequest connectionCheckRequest) {
        
        if (!ControlConfigs.getInstance().isConnectionEnabled()) {
            ConnectionCheckResponse connectionCheckResponse = new ConnectionCheckResponse();
            connectionCheckResponse.setSuccess(true);
            connectionCheckResponse.setCheckCode(ConnectionCheckCode.CHECK_SKIP);
            connectionCheckResponse.setMessage("connection check not enabled.");
            return connectionCheckResponse;
        }
        
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
                                    + ",current count detail is " + metricsIpCount.toString());
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
    
}
