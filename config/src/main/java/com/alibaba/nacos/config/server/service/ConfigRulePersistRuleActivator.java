package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.config.server.remote.ConfigPublishRequestHandler;
import com.alibaba.nacos.config.server.remote.ConfigQueryRequestHandler;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.control.ruleactivator.PersistRuleActivator;
import org.springframework.beans.factory.annotation.Autowired;

public class ConfigRulePersistRuleActivator implements PersistRuleActivator {
    
    @Autowired
    private ConfigQueryRequestHandler configQueryRequestHandler;
    
    @Autowired
    private ConfigPublishRequestHandler configPublishRequestHandler;
    
    public static final String RULE_CONFIG_NAMESPACE = System.getenv("nacos_control_rule_config_namespace");
    
    public static final String DATA_ID_TPS_CONTROL_RULE = "nacos.internal.tps.control_rule_";
    
    public static final String DATA_ID_CONNECTION_LIMIT_RULE = "nacos.internal.connection.limit.rule";
    
    public static final String NACOS_GROUP = "nacos";
    
    
    private ConfigPublishResponse publishConfig(String dataId, String group, String tenant, String content)
            throws NacosException {
        ConfigPublishRequest configPublishRequest = new ConfigPublishRequest();
        configPublishRequest.setDataId(dataId);
        configPublishRequest.setGroup(group);
        configPublishRequest.setTenant(tenant);
        configPublishRequest.setContent(content);
        return configPublishRequestHandler.handle(configPublishRequest, new RequestMeta());
        
    }
    
    private String loadConfig(String dataId, String group, String tenant) throws NacosException {
        ConfigQueryRequest queryRequest = new ConfigQueryRequest();
        queryRequest.setDataId(dataId);
        queryRequest.setGroup(group);
        queryRequest.setTenant(tenant);
        RequestMeta meta = new RequestMeta();
        meta.setClientIp(NetUtils.localIP());
        ConfigQueryResponse handle = configQueryRequestHandler.handle(queryRequest, meta);
        if (handle == null) {
            throw new NacosException(NacosException.SERVER_ERROR, "load local config fail,response is null");
        }
        if (handle.isSuccess()) {
            return handle.getContent();
        } else if (handle.getErrorCode() == ConfigQueryResponse.CONFIG_NOT_FOUND) {
            return null;
        } else {
            Loggers.REMOTE.error("connection limit rule load fail,errorCode={}", handle.getErrorCode());
            throw new NacosException(NacosException.SERVER_ERROR,
                    "load local config fail,error code=" + handle.getErrorCode());
        }
    }
    
    @Override
    public void saveConnectionRule(String ruleContent) throws Exception {
        publishConfig(DATA_ID_CONNECTION_LIMIT_RULE, NACOS_GROUP, RULE_CONFIG_NAMESPACE, ruleContent);
    }
    
    @Override
    public String getConnectionRule() {
        try {
            return loadConfig(DATA_ID_CONNECTION_LIMIT_RULE, NACOS_GROUP, RULE_CONFIG_NAMESPACE);
        } catch (NacosException nacosException) {
            return null;
        }
    }
    
    @Override
    public void saveTpsRule(String pointName, String ruleContent) throws Exception {
        publishConfig(DATA_ID_TPS_CONTROL_RULE + pointName, NACOS_GROUP, RULE_CONFIG_NAMESPACE, ruleContent);
    }
    
    @Override
    public String getTpsRule(String pointName) {
        try {
            return loadConfig(DATA_ID_TPS_CONTROL_RULE + pointName, NACOS_GROUP, RULE_CONFIG_NAMESPACE);
        } catch (NacosException nacosException) {
            return null;
        }
    }
}
