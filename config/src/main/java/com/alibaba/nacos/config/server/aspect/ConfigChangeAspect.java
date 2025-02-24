/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.config.server.aspect;

import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigRemoveResponse;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.configuration.ConfigChangeConfigs;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.TagGrayRule;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.plugin.config.ConfigChangePluginManager;
import com.alibaba.nacos.plugin.config.constants.ConfigChangeConstants;
import com.alibaba.nacos.plugin.config.constants.ConfigChangeExecuteTypes;
import com.alibaba.nacos.plugin.config.constants.ConfigChangePointCutTypes;
import com.alibaba.nacos.plugin.config.model.ConfigChangeRequest;
import com.alibaba.nacos.plugin.config.model.ConfigChangeResponse;
import com.alibaba.nacos.plugin.config.spi.ConfigChangePluginService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static com.alibaba.nacos.config.server.constant.Constants.HTTP;

/**
 * Config change pointcut aspect,which config change plugin services will pointcut.
 *
 * @author Nacos
 */
@Aspect
@Component
public class ConfigChangeAspect {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigChangeAspect.class);
    
    private static final Integer DEFAULT_BEFORE_LIST_CAPACITY = 2;
    
    private static final Integer DEFAULT_AFTER_LIST_CAPACITY = 1;
    
    private static final String ENABLED = "enabled";
    
    /**
     * Publish config.
     */
    private static final String PUBLISH_CONFIG =
            "execution(* com.alibaba.nacos.config.server.service.ConfigOperationService.publishConfig(..))";
    
    /**
     * Delete config.
     */
    private static final String DELETE_CONFIG =
            "execution(* com.alibaba.nacos.config.server.service.ConfigOperationService.deleteConfig(..))";
    
    private final ConfigChangeConfigs configChangeConfigs;
    
    public ConfigChangeAspect(ConfigChangeConfigs configChangeConfigs) {
        this.configChangeConfigs = configChangeConfigs;
    }
    
    /**
     * Publish or update config.
     */
    @Around(PUBLISH_CONFIG)
    Object publishOrUpdateConfigAround(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        ConfigForm configForm = (ConfigForm) args[0];
        ConfigRequestInfo configRequestInfo = (ConfigRequestInfo) args[1];
        final String dataId = configForm.getDataId();
        final String group = configForm.getGroup();
        final String namespaceId = configForm.getNamespaceId();
        final String content = configForm.getContent();
        final String desc = configForm.getDesc();
        final String use = configForm.getUse();
        final String effect = configForm.getEffect();
        final String type = configForm.getType();
        final String tag = configForm.getTag();
        final String configTags = configForm.getConfigTags();
        final String requestIpApp = configRequestInfo.getRequestIpApp();
        final String scrIp = configRequestInfo.getSrcIp();
        final String scrType = configRequestInfo.getSrcType();
        final String betaIps = configRequestInfo.getBetaIps();
        String grayName = null;
        String grayRuleExp = null;
        if (StringUtils.isNotBlank(betaIps)) {
            grayName =  BetaGrayRule.TYPE_BETA;
            grayRuleExp = betaIps;
        } else if (StringUtils.isNotBlank(tag)) {
            grayName = TagGrayRule.TYPE_TAG + "_" + configForm.getTag();
            grayRuleExp = tag;
        }
        
        ConfigChangePointCutTypes configChangePointCutType = null;
        if (HTTP.equals(scrType)) {
            // via console or api calls
            configChangePointCutType = ConfigChangePointCutTypes.PUBLISH_BY_HTTP;  
        } else {
            // via sdk rpc calls
            configChangePointCutType = ConfigChangePointCutTypes.PUBLISH_BY_RPC;
        }
        final List<ConfigChangePluginService> pluginServices = getPluginServices(
                configChangePointCutType);
        // didn't enabled or add relative plugin
        if (pluginServices.isEmpty()) {
            return pjp.proceed();
        }
        
        ConfigChangeRequest configChangeRequest = new ConfigChangeRequest(configChangePointCutType);
        configChangeRequest.setArg("dataId", dataId);
        configChangeRequest.setArg("group", group);
        configChangeRequest.setArg("namespaceId", namespaceId);
        configChangeRequest.setArg("content", content);
        configChangeRequest.setArg("tag", tag);
        configChangeRequest.setArg("requestIpApp", requestIpApp);
        configChangeRequest.setArg("srcIp", scrIp);
        configChangeRequest.setArg("configTags", configTags);
        configChangeRequest.setArg("desc", desc);
        configChangeRequest.setArg("use", use);
        configChangeRequest.setArg("effect", effect);
        configChangeRequest.setArg("type", type);
        configChangeRequest.setArg("grayName", grayName);
        configChangeRequest.setArg("grayRuleExp", grayRuleExp);
        return configChangeServiceHandle(pjp, pluginServices, configChangeRequest);
    }
    
    /**
     * Remove config.
     */
    @Around(DELETE_CONFIG)
    Object removeConfigByIdAround(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        final String dataId = (String) args[0];
        final String group = (String) args[1];
        final String namespaceId = (String) args[2];
        final String grayName = (String) args[3];
        final String srcIp = (String) args[4];
        final String srcUser = (String) args[5];
        final String scrType = (String) args[6];
        
        ConfigChangePointCutTypes configChangePointCutType = null;
        if (HTTP.equals(scrType)) {
            // via console or api calls
            configChangePointCutType = ConfigChangePointCutTypes.PUBLISH_BY_HTTP;
        } else {
            // via sdk rpc calls
            configChangePointCutType = ConfigChangePointCutTypes.PUBLISH_BY_RPC;
        }
        final List<ConfigChangePluginService> pluginServices = getPluginServices(configChangePointCutType);
        // didn't enabled or add relative plugin
        if (pluginServices.isEmpty()) {
            return pjp.proceed();
        }
        
        ConfigChangeRequest configChangeRequest = new ConfigChangeRequest(configChangePointCutType);
        configChangeRequest.setArg("dataId", dataId);
        configChangeRequest.setArg("group", group);
        configChangeRequest.setArg("namespaceId", namespaceId);
        configChangeRequest.setArg("srcIp", srcIp);
        configChangeRequest.setArg("srcUser", srcUser);
        configChangeRequest.setArg("grayName", grayName);
        configChangeRequest.setArg("modifyTime", TimeUtils.getCurrentTimeStr());
        return configChangeServiceHandle(pjp, pluginServices, configChangeRequest);
    }
    
    /**
     * Execute relevant config change plugin services.
     */
    private Object configChangeServiceHandle(ProceedingJoinPoint pjp,
            List<ConfigChangePluginService> configChangePluginServiceList,
            ConfigChangeRequest configChangeRequest) {
        ConfigChangePointCutTypes handleType = configChangeRequest.getRequestType();
        ConfigChangeResponse configChangeResponse = new ConfigChangeResponse(handleType);
        // default success,when before plugin service verify failed , set false
        configChangeResponse.setSuccess(true);

        List<ConfigChangePluginService> beforeExecutePluginServices = new ArrayList<>(DEFAULT_BEFORE_LIST_CAPACITY);
        List<ConfigChangePluginService> afterExecutePluginServices = new ArrayList<>(DEFAULT_AFTER_LIST_CAPACITY);
        
        Object retVal = null;
        Object[] args = pjp.getArgs();
        configChangeRequest.setArg(ConfigChangeConstants.ORIGINAL_ARGS, args);
        
        for (ConfigChangePluginService ccs : configChangePluginServiceList) {
            if (!isEnabled(ccs)) {
                continue;
            }
            if (ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE.equals(ccs.executeType())) {
                beforeExecutePluginServices.add(ccs);
            } else {
                afterExecutePluginServices.add(ccs);
            }
        }
        
        // before plugin service execute
        for (ConfigChangePluginService ccs : beforeExecutePluginServices) {
            final String serviceType = ccs.getServiceType().toLowerCase(Locale.ROOT);
            final Properties properties = configChangeConfigs.getPluginProperties(serviceType);
            configChangeRequest.setArg(ConfigChangeConstants.PLUGIN_PROPERTIES, properties);
            ccs.execute(configChangeRequest, configChangeResponse);
            if (null != configChangeResponse.getArgs()) {
                // update args by filter with whitelist
                args = configChangeResponse.getArgs();
            }
            // prevent execute next before plugins service
            if (!configChangeResponse.isSuccess()) {
                retVal = wrapErrorResp(configChangeResponse);
                break;
            }
        }
        
        try {
            // if validate failed,skipped directly
            if (configChangeResponse.isSuccess()) {
                retVal = pjp.proceed(args);
            }
        } catch (Throwable e) {
            LOGGER.warn("config change join point failed {}", e.getMessage());
            configChangeResponse.setMsg("config change join point fail" + e.getMessage());
            retVal = wrapErrorResp(configChangeResponse);
        }
        
        // after plugin service execute
        ConfigExecutor.executeAsyncConfigChangePluginTask(() -> {
            for (ConfigChangePluginService ccs : afterExecutePluginServices) {
                try {
                    final String serviceType = ccs.getServiceType().toLowerCase(Locale.ROOT);
                    final Properties properties = configChangeConfigs.getPluginProperties(serviceType);
                    configChangeRequest.setArg(ConfigChangeConstants.PLUGIN_PROPERTIES, properties);
                    ccs.execute(configChangeRequest, configChangeResponse);
                } catch (Throwable throwable) {
                    LOGGER.warn("execute async plugin services failed {}", throwable.getMessage());
                }
            }
        });
        
        return retVal;
    }
    
    private List<ConfigChangePluginService> getPluginServices(
            ConfigChangePointCutTypes configChangePointCutType) {
        List<ConfigChangePluginService> pluginServicePriorityList = ConfigChangePluginManager
                .findPluginServicesByPointcut(configChangePointCutType);
        if (pluginServicePriorityList == null) {
            return new ArrayList<>();
        }
        for (ConfigChangePluginService each : pluginServicePriorityList) {
            if (isEnabled(each)) {
                return pluginServicePriorityList;
            }
        }
        return new ArrayList<>();
    }
    
    private boolean isEnabled(ConfigChangePluginService configChangePluginService) {
        Properties serviceConfigProperties = configChangeConfigs
                .getPluginProperties(configChangePluginService.getServiceType());
        return Boolean.parseBoolean(serviceConfigProperties.getProperty(ENABLED));
    }
    
    private Object wrapErrorResp(ConfigChangeResponse configChangeResponse) {
        Object retVal = null;
        switch (configChangeResponse.getResponseType()) {
            // some of controller didn't design error msg resp
            case IMPORT_BY_HTTP:
            case REMOVE_BATCH_HTTP:
            case REMOVE_BY_HTTP:
            case PUBLISH_BY_HTTP: {
                retVal = RestResultUtils.failed(configChangeResponse.getMsg());
                break;
            }
            case PUBLISH_BY_RPC: {
                retVal = ConfigPublishResponse
                        .buildFailResponse(ResponseCode.FAIL.getCode(), configChangeResponse.getMsg());
                break;
            }
            case REMOVE_BY_RPC: {
                retVal = ConfigRemoveResponse.buildFailResponse(configChangeResponse.getMsg());
                break;
            }
            default: {
                // ignore
            }
        }
        return retVal;
    }
}
