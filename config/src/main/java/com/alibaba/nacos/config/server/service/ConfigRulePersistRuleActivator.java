package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.remote.ConfigPublishRequestHandler;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.control.ruleactivator.PersistRuleActivator;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

public class ConfigRulePersistRuleActivator implements PersistRuleActivator {
    
    private ConfigPublishRequestHandler configPublishRequestHandler;
    
    private PersistService persistService;
    
    public static final String RULE_CONFIG_NAMESPACE = System.getenv("nacos_control_rule_config_namespace");
    
    public static final String DATA_ID_TPS_CONTROL_RULE = "nacos.internal.tps.control_rule_";
    
    public static final String DATA_ID_CONNECTION_LIMIT_RULE = "nacos.internal.connection.limit.rule";
    
    public static final String NACOS_GROUP = "nacos";
    
    public ConfigRulePersistRuleActivator() {
        configPublishRequestHandler = ApplicationUtils.getBean(ConfigPublishRequestHandler.class);
        persistService = ApplicationUtils.getBean(PersistService.class);
    }
    
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
        
        ConfigInfoWrapper configInfo = persistService.findConfigInfo(dataId, group, tenant);
        
        if (configInfo != null) {
            return configInfo.getContent();
        } else {
            Loggers.REMOTE.warn(" rule content not found ,dataId={},group={},tenant={}", dataId, group, tenant);
           return null;
        }
    }
    
    @Override
    public String getName() {
        return "internalconfigcenter";
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
