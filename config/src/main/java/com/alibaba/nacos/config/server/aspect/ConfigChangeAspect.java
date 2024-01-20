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

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigRemoveResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.config.server.configuration.ConfigChangeConfigs;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
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
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Locale;

/**
 * Config change pointcut aspect,which config change plugin services will pointcut.
 *
 * @author liyunfei
 */
@Aspect
@Component
public class ConfigChangeAspect {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigChangeAspect.class);
    
    private static final Integer DEFAULT_BEFORE_LIST_CAPACITY = 2;
    
    private static final Integer DEFAULT_AFTER_LIST_CAPACITY = 1;
    
    private static final String ENABLED = "enabled";
    
    /**
     * Publish or update config through http.
     */
    private static final String CLIENT_INTERFACE_PUBLISH_CONFIG =
            "execution(* com.alibaba.nacos.config.server.controller.ConfigController.publishConfig(..)) "
                    + "&& args(request,response,dataId,group,tenant,content,tag,appName,srcUser,configTags,desc,use,effect,type,..) "
                    + "&& @annotation(org.springframework.web.bind.annotation.PostMapping)";
    
    /**
     * Publish or update config through rpc.
     */
    private static final String CLIENT_INTERFACE_PUBLISH_CONFIG_RPC =
            "execution(* com.alibaba.nacos.core.remote.RequestHandler.handleRequest(..)) "
                    + "&& target(com.alibaba.nacos.config.server.remote.ConfigPublishRequestHandler) "
                    + "&& args(request,meta)";
    
    /**
     * Remove config by id through http.
     */
    private static final String CLIENT_INTERFACE_REMOVE_CONFIG =
            "execution(* com.alibaba.nacos.config.server.controller.ConfigController.deleteConfig(..))"
                    + " && args(request,response,dataId,group,tenant,..)";
    
    /**
     * Remove config by ids through http.
     */
    private static final String CLIENT_INTERFACE_BATCH_REMOVE_CONFIG =
            "execution(* com.alibaba.nacos.config.server.controller.ConfigController.deleteConfigs(..))"
                    + " && args(request,ids)";
    
    /**
     * Remove config through rpc.
     */
    @SuppressWarnings("checkstyle:linelength")
    private static final String CLIENT_INTERFACE_REMOVE_CONFIG_RPC =
            "execution(* com.alibaba.nacos.core.remote.RequestHandler.handleRequest(..)) "
                    + " && target(com.alibaba.nacos.config.server.remote.ConfigRemoveRequestHandler)"
                    + " && args(request,meta)";
    
    /**
     * Import file through http.
     */
    private static final String CLIENT_INTERFACE_IMPORT_CONFIG =
            "execution(* com.alibaba.nacos.config.server.controller.ConfigController.importAndPublishConfig(..)) "
                    + "&& args(request,srcUser,namespace,policy,file)";
    
    private final ConfigChangeConfigs configChangeConfigs;
    
    private ConfigChangePluginManager configChangeManager;
    
    public ConfigChangeAspect(ConfigChangeConfigs configChangeConfigs) {
        this.configChangeConfigs = configChangeConfigs;
        configChangeManager = ConfigChangePluginManager.getInstance();
    }
    
    /**
     * Publish or update config.
     */
    @Around(CLIENT_INTERFACE_PUBLISH_CONFIG)
    Object publishOrUpdateConfigAround(ProceedingJoinPoint pjp, HttpServletRequest request,
            HttpServletResponse response, String dataId, String group, String tenant, String content, String tag,
            String appName, String srcUser, String configTags, String desc, String use, String effect, String type)
            throws Throwable {
        final ConfigChangePointCutTypes configChangePointCutType = ConfigChangePointCutTypes.PUBLISH_BY_HTTP;
        final List<ConfigChangePluginService> pluginServices = getPluginServices(
                configChangePointCutType);
        // didn't enabled or add relative plugin
        if (pluginServices.isEmpty()) {
            return pjp.proceed();
        }
        ConfigChangeRequest configChangeRequest = new ConfigChangeRequest(configChangePointCutType);
        configChangeRequest.setArg("dataId", dataId);
        configChangeRequest.setArg("group", group);
        configChangeRequest.setArg("tenant", tenant);
        configChangeRequest.setArg("content", content);
        configChangeRequest.setArg("tag", tag);
        configChangeRequest.setArg("requestIpApp", appName);
        configChangeRequest.setArg("srcIp", RequestUtil.getRemoteIp(request));
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
    @Around(CLIENT_INTERFACE_REMOVE_CONFIG)
    Object removeConfigByIdAround(ProceedingJoinPoint pjp, HttpServletRequest request, HttpServletResponse response,
            String dataId, String group, String tenant) throws Throwable {
        final ConfigChangePointCutTypes configChangePointCutType = ConfigChangePointCutTypes.REMOVE_BY_HTTP;
        final List<ConfigChangePluginService> pluginServices = getPluginServices(
                configChangePointCutType);
        // didn't enabled or add relative plugin
        if (pluginServices.isEmpty()) {
            return pjp.proceed();
        }
        ConfigChangeRequest configChangeRequest = new ConfigChangeRequest(configChangePointCutType);
        configChangeRequest.setArg("dataId", dataId);
        configChangeRequest.setArg("group", group);
        configChangeRequest.setArg("tenant", tenant);
        configChangeRequest.setArg("srcIp", RequestUtil.getRemoteIp(request));
        configChangeRequest.setArg("requestIpApp", RequestUtil.getAppName(request));
        configChangeRequest.setArg("use", RequestUtil.getSrcUserName(request));
        return configChangeServiceHandle(pjp, pluginServices, configChangeRequest);
    }
    
    /**
     * Remove config by ids.
     */
    @Around(CLIENT_INTERFACE_BATCH_REMOVE_CONFIG)
    public Object removeConfigByIdsAround(ProceedingJoinPoint pjp, HttpServletRequest request, List<Long> ids)
            throws Throwable {
        final ConfigChangePointCutTypes configChangePointCutType = ConfigChangePointCutTypes.REMOVE_BATCH_HTTP;
        final List<ConfigChangePluginService> pluginServices = getPluginServices(
                configChangePointCutType);
        // didn't enabled or add relative plugin
        if (pluginServices.isEmpty()) {
            return pjp.proceed();
        }
        ConfigChangeRequest configChangeRequest = new ConfigChangeRequest(configChangePointCutType);
        configChangeRequest.setArg("dataId", ids.toString());
        configChangeRequest.setArg("srcIp", RequestUtil.getRemoteIp(request));
        configChangeRequest.setArg("requestIpApp", RequestUtil.getAppName(request));
        configChangeRequest.setArg("use", RequestUtil.getSrcUserName(request));
        return configChangeServiceHandle(pjp, pluginServices, configChangeRequest);
    }
    
    /**
     * Import config.
     */
    @Around(CLIENT_INTERFACE_IMPORT_CONFIG)
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
     * Publish or update config.
     */
    @Around(CLIENT_INTERFACE_PUBLISH_CONFIG_RPC)
    Object publishConfigAroundRpc(ProceedingJoinPoint pjp, ConfigPublishRequest request, RequestMeta meta)
            throws Throwable {
        final ConfigChangePointCutTypes configChangePointCutType = ConfigChangePointCutTypes.PUBLISH_BY_RPC;
        final List<ConfigChangePluginService> pluginServices = getPluginServices(
                configChangePointCutType);
        // didn't enabled or add relative plugin
        if (pluginServices.isEmpty()) {
            return pjp.proceed();
        }
        ConfigChangeRequest configChangeRequest = new ConfigChangeRequest(configChangePointCutType);
        configChangeRequest.setArg("dataId", request.getDataId());
        configChangeRequest.setArg("group", request.getGroup());
        configChangeRequest.setArg("tenant", request.getTenant());
        configChangeRequest.setArg("content", request.getContent());
        configChangeRequest.setArg("type", request.getAdditionParam("type"));
        configChangeRequest.setArg("tag", request.getAdditionParam("tag"));
        configChangeRequest.setArg("configTags", request.getAdditionParam("config_tags"));
        configChangeRequest.setArg("desc", request.getAdditionParam("desc"));
        configChangeRequest.setArg("effect", request.getAdditionParam("effect"));
        configChangeRequest.setArg("appName", request.getAdditionParam("appName"));
        configChangeRequest.setArg("srcIp", meta.getClientIp());
        configChangeRequest.setArg("requestIpApp", request.getAdditionParam("requestIpApp"));
        configChangeRequest.setArg("srcUser", request.getAdditionParam("src_user"));
        configChangeRequest.setArg("use", request.getAdditionParam("use"));
        return configChangeServiceHandle(pjp, pluginServices, configChangeRequest);
    }
    
    /**
     * Remove config.
     */
    @Around(CLIENT_INTERFACE_REMOVE_CONFIG_RPC)
    Object removeConfigAroundRpc(ProceedingJoinPoint pjp, ConfigRemoveRequest request, RequestMeta meta)
            throws Throwable {
        final ConfigChangePointCutTypes configChangePointCutType = ConfigChangePointCutTypes.REMOVE_BY_RPC;
        final List<ConfigChangePluginService> pluginServices = getPluginServices(
                configChangePointCutType);
        // didn't enabled or add relative plugin
        if (pluginServices.isEmpty()) {
            return pjp.proceed();
        }
        ConfigChangeRequest configChangeRequest = new ConfigChangeRequest(configChangePointCutType);
        configChangeRequest.setArg("dataId", request.getDataId());
        configChangeRequest.setArg("group", request.getGroup());
        configChangeRequest.setArg("tenant", request.getTenant());
        configChangeRequest.setArg("appName", request.getHeader("appName"));
        configChangeRequest.setArg("srcIp", meta.getClientIp());
        configChangeRequest.setArg("requestIpApp", request.getHeader("requestIpApp"));
        configChangeRequest.setArg("srcUser", request.getHeader("src_user"));
        configChangeRequest.setArg("use", request.getHeader("use"));
        return configChangeServiceHandle(pjp, pluginServices, configChangeRequest);
    }
    
    /**
     * Execute relevant config change plugin services.
     */
    private Object configChangeServiceHandle(ProceedingJoinPoint pjp,
            List<ConfigChangePluginService> configChangePluginServiceList,
            ConfigChangeRequest configChangeRequest) {
        configChangeRequest.setArg("modifyTime", TimeUtils.getCurrentTimeStr());
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
            configChangeRequest.setArg("pluginProperties", properties);
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
            // some of controller did'nt design error msg resp
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
