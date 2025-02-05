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
import com.alibaba.nacos.config.server.configuration.ConfigChangeConfigs;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.RequestUtil;
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
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Locale;

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
    
    private static final String SCR_TYPE_HTTP = "http";
    
    private static final String SCR_TYPE_RPC = "rpc";
    
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
    
    /**
     * Batch delete config by ids.
     */
    private static final String BATCH_DELETE_CONFIG =
            "execution(* com.alibaba.nacos.config.server.service.ConfigOperationService.deleteConfigs(..))";
        
    /**
     * Import file.
     */
    private static final String IMPORT_CONFIG =
            "execution(* com.alibaba.nacos.config.server.controller.ConfigController.importAndPublishConfig(..)) "
                    + "&& args(request,srcUser,namespace,policy,file)";
    
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
        
        ConfigChangePointCutTypes configChangePointCutType = null;
        if (SCR_TYPE_HTTP.equals(scrType)) {
            // via console or api calls
            configChangePointCutType = ConfigChangePointCutTypes.PUBLISH_BY_HTTP;  
        } else if (SCR_TYPE_RPC.equals(scrType)) {
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
        final String tag = (String) args[3];
        final String srcUser = (String) args[4];
        String scrType = (String) args[5];
        
        ConfigChangePointCutTypes configChangePointCutType = null;
        if (SCR_TYPE_HTTP.equals(scrType)) {
            // via console or api calls
            configChangePointCutType = ConfigChangePointCutTypes.PUBLISH_BY_HTTP;
        } else if (SCR_TYPE_RPC.equals(scrType)) {
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
        configChangeRequest.setArg("srcUser", srcUser);
        configChangeRequest.setArg("tag", tag);
        configChangeRequest.setArg("modifyTime", TimeUtils.getCurrentTimeStr());
        return configChangeServiceHandle(pjp, pluginServices, configChangeRequest);
    }
    
    /**
     * Delete config by ids.
     */
    @Around(BATCH_DELETE_CONFIG)
    public Object removeConfigByIdsAround(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        List<Integer> ids = (List<Integer>) args[0];
        String srcIp = (String) args[1];
        final String srcUser = (String) args[2];
        
        final ConfigChangePointCutTypes configChangePointCutType = ConfigChangePointCutTypes.REMOVE_BATCH_HTTP;
        final List<ConfigChangePluginService> pluginServices = getPluginServices(configChangePointCutType);
        // didn't enabled or add relative plugin
        if (pluginServices.isEmpty()) {
            return pjp.proceed();
        }
        
        ConfigChangeRequest configChangeRequest = new ConfigChangeRequest(configChangePointCutType);
        configChangeRequest.setArg("dataId", ids.toString());
        configChangeRequest.setArg("srcIp", srcIp);
        configChangeRequest.setArg("srcUser", srcUser);
        return configChangeServiceHandle(pjp, pluginServices, configChangeRequest);
    }
    
    /**
     * Import config.
     */
    @Around(IMPORT_CONFIG)
    public Object importConfigAround(ProceedingJoinPoint pjp, HttpServletRequest request, String srcUser,
            String namespace, SameConfigPolicy policy, MultipartFile file) throws Throwable {
        final ConfigChangePointCutTypes configChangePointCutType = ConfigChangePointCutTypes.IMPORT_BY_HTTP;
        final List<ConfigChangePluginService> pluginServices = getPluginServices(
                configChangePointCutType);
        // didn't enabled or add relative plugin
        if (pluginServices.isEmpty()) {
            return pjp.proceed();
        }
        ConfigChangeRequest configChangeRequest = new ConfigChangeRequest(configChangePointCutType);
        configChangeRequest.setArg("srcUser", srcUser);
        configChangeRequest.setArg("namespace", namespace);
        configChangeRequest.setArg("policy", policy);
        configChangeRequest.setArg("file", file);
        configChangeRequest.setArg("srcIp", RequestUtil.getRemoteIp(request));
        configChangeRequest.setArg("requestIpApp", RequestUtil.getAppName(request));
        configChangeRequest.setArg("use", RequestUtil.getSrcUserName(request));
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
