package com.alibaba.nacos.plugin.control.connection;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.JacksonUtils;
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
import com.alibaba.nacos.plugin.control.ruleactivator.RuleParserProxy;
import com.alibaba.nacos.plugin.control.ruleactivator.RuleStorageProxy;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * connection control manager.
 */
public abstract class ConnectionControlManager {
    
    protected ConnectionLimitRule connectionLimitRule;
    
    protected Collection<ConnectionMetricsCollector> metricsCollectorList;
    
    public abstract String getName();
    
    public ConnectionControlManager() {
        metricsCollectorList = NacosServiceLoader.load(ConnectionMetricsCollector.class);
        Loggers.CONTROL.info("Load connection metrics collector,size={},{}", metricsCollectorList.size(),
                metricsCollectorList);
        initConnectionRule();
    }
    
    private void initConnectionRule() {
        RuleStorageProxy ruleStorageProxy = RuleStorageProxy.getInstance();
        String localRuleContent = ruleStorageProxy.getLocalDiskStorage().getConnectionRule();
        if (StringUtils.isNotBlank(localRuleContent)) {
            Loggers.CONTROL.info("Found local disk connection rule content on start up,value  ={}", localRuleContent);
        } else if (ruleStorageProxy.getExternalDiskStorage() != null
                && ruleStorageProxy.getExternalDiskStorage().getConnectionRule() != null) {
            localRuleContent = ruleStorageProxy.getExternalDiskStorage().getConnectionRule();
            if (StringUtils.isNotBlank(localRuleContent)) {
                Loggers.CONTROL
                        .info("Found persist disk connection rule content on start up ,value  ={}", localRuleContent);
            }
        }
        
        if (StringUtils.isNotBlank(localRuleContent)) {
            connectionLimitRule = RuleParserProxy.getInstance().parseConnectionRule(localRuleContent);
            Loggers.CONTROL.info("init connection rule end");
            
        } else {
            Loggers.CONTROL.info("No connection rule content found ,use default empty rule ");
            connectionLimitRule = new ConnectionLimitRule();
        }
    }
    
    public ConnectionLimitRule getConnectionLimitRule() {
        return connectionLimitRule;
    }
    
    public abstract void applyConnectionLimitRule(ConnectionLimitRule connectionLimitRule);
    
    /**
     * check connection allowed.
     *
     * @param connectionCheckRequest connectionCheckRequest.
     * @return
     */
    public abstract ConnectionCheckResponse check(ConnectionCheckRequest connectionCheckRequest);
    
    /**
     * post intercept.
     *
     * @param connectionCheckRequest
     * @param connectionCheckResponse
     * @return
     */
    public abstract InterceptResult postIntercept(ConnectionCheckRequest connectionCheckRequest,
            ConnectionCheckResponse connectionCheckResponse);
    
}
