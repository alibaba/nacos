package com.alibaba.nacos.plugin.control.connection.nacos;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.ConnectionMetricsCollector;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckCode;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;

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
    public void applyConnectionLimitRule(ConnectionControlRule connectionControlRule) {
        super.connectionControlRule = connectionControlRule;
        Loggers.CONTROL.info("Connection control rule updated to ->" + (this.connectionControlRule == null ? null
                : JacksonUtils.toJson(this.connectionControlRule)));
        
    }
    
    @Override
    public ConnectionCheckResponse check(ConnectionCheckRequest connectionCheckRequest) {
        
        ConnectionCheckResponse connectionCheckResponse = new ConnectionCheckResponse();
        //limit rule check.
        if (this.connectionControlRule != null) {
            
            int totalCountLimit = connectionControlRule.getCountLimit();
            Map<String, Integer> metricsTotalCount = metricsCollectorList.stream().collect(
                    Collectors.toMap(ConnectionMetricsCollector::getName, ConnectionMetricsCollector::getTotalCount));
            int totalCount = metricsTotalCount.values().stream().mapToInt(Integer::intValue).sum();
            //total count check model
            if (totalCountLimit >= 0 && totalCount >= totalCountLimit) {
                //deny;
                connectionCheckResponse.setCode(ConnectionCheckCode.DENY_BY_TOTAL_OVER);
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
            connectionCheckResponse.setCode(ConnectionCheckCode.PASS_BY_TOTAL);
            connectionCheckResponse.setMessage("check pass");
            return connectionCheckResponse;
        } else {
            connectionCheckResponse.setCode(ConnectionCheckCode.CHECK_SKIP);
            connectionCheckResponse.setSuccess(true);
            return connectionCheckResponse;
        }
        
    }
    
}
