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

package com.alibaba.nacos.plugin.config.apsect;

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.plugin.config.ConfigChangePluginManager;
import com.alibaba.nacos.plugin.config.configuration.ImportConfigCondition;
import com.alibaba.nacos.plugin.config.configuration.PublishOrUpdateConfigCondition;
import com.alibaba.nacos.plugin.config.configuration.RemoveConfigCondition;
import com.alibaba.nacos.plugin.config.constants.ConfigChangePointCutTypes;
import com.alibaba.nacos.plugin.config.handler.ConfigChangePluginHandler;
import com.alibaba.nacos.plugin.config.model.ConfigChangeRequest;
import com.alibaba.nacos.plugin.config.spi.ConfigChangeService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Config change pointcut aspect,which config change plugin services will pointcut.
 *
 * @author liyunfei
 */
@Aspect
@Component
public class ConfigChangeAspect {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigChangeAspect.class);
    
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
    
    /**
     * Publish or update config.
     */
    @ConditionalOnClass(PublishOrUpdateConfigCondition.class)
    @Around(CLIENT_INTERFACE_PUBLISH_CONFIG)
    Object publishOrUpdateConfigAround(ProceedingJoinPoint pjp, HttpServletRequest request,
            HttpServletResponse response, String dataId, String group, String tenant, String content, String tag,
            String appName, String srcUser, String configTags, String desc, String use, String effect, String type)
            throws Throwable {
        ConfigChangeRequest configChangeRequest = new ConfigChangeRequest(ConfigChangePointCutTypes.PUBLISH_BY_HTTP);
        configChangeRequest.setArg("dataId", dataId);
        configChangeRequest.setArg("group", group);
        configChangeRequest.setArg("tenant", tenant);
        configChangeRequest.setArg("content", content);
        configChangeRequest.setArg("tag", tag);
        configChangeRequest.setArg("requestIpApp", appName);
        //configChangeRequest.setArg("srcIp", srcUser);
        configChangeRequest.setArg("srcIp", RequestUtil.getRemoteIp(request));
        configChangeRequest.setArg("configTags", configTags);
        configChangeRequest.setArg("desc", desc);
        configChangeRequest.setArg("use", use);
        configChangeRequest.setArg("effect", effect);
        configChangeRequest.setArg("type", type);
        return configChangeServiceHandler(pjp, configChangeRequest.getRequestType(), configChangeRequest,
                "publishConfigAround");
    }
    
    /**
     * Remove config.
     */
    @Conditional(RemoveConfigCondition.class)
    @Around(CLIENT_INTERFACE_REMOVE_CONFIG)
    Object removeConfigByIdAround(ProceedingJoinPoint pjp, HttpServletRequest request, HttpServletResponse response,
            String dataId, String group, String tenant) throws Throwable {
        ConfigChangeRequest configChangeRequest = new ConfigChangeRequest(ConfigChangePointCutTypes.REMOVE_BY_HTTP);
        configChangeRequest.setArg("dataId", dataId);
        configChangeRequest.setArg("group", group);
        configChangeRequest.setArg("tenant", tenant);
        configChangeRequest.setArg("srcIp", RequestUtil.getRemoteIp(request));
        configChangeRequest.setArg("requestIpApp", RequestUtil.getAppName(request));
        configChangeRequest.setArg("use", RequestUtil.getSrcUserName(request));
        return configChangeServiceHandler(pjp, configChangeRequest.getRequestType(), configChangeRequest,
                "removeConfigByIdAround");
    }
    
    /**
     * Remove config by ids.
     */
    @Conditional(RemoveConfigCondition.class)
    @Around(CLIENT_INTERFACE_BATCH_REMOVE_CONFIG)
    public Object removeConfigByIdsAround(ProceedingJoinPoint pjp, HttpServletRequest request, List<Long> ids)
            throws Throwable {
        ConfigChangeRequest configChangeRequest = new ConfigChangeRequest(ConfigChangePointCutTypes.REMOVE_BATCH_HTTP);
        configChangeRequest.setArg("dataId", ids.toString());
        configChangeRequest.setArg("srcIp", RequestUtil.getRemoteIp(request));
        configChangeRequest.setArg("requestIpApp", RequestUtil.getAppName(request));
        configChangeRequest.setArg("use", RequestUtil.getSrcUserName(request));
        return configChangeServiceHandler(pjp, configChangeRequest.getRequestType(), configChangeRequest,
                "importConfigAround");
    }
    
    /**
     * Import config.
     */
    @Conditional(ImportConfigCondition.class)
    @Around(CLIENT_INTERFACE_IMPORT_CONFIG)
    public Object importConfigAround(ProceedingJoinPoint pjp, HttpServletRequest request, String srcUser,
            String namespace, SameConfigPolicy policy, MultipartFile file) throws Throwable {
        ConfigChangeRequest configChangeRequest = new ConfigChangeRequest(ConfigChangePointCutTypes.IMPORT_BY_HTTP);
        configChangeRequest.setArg("srcUser", srcUser);
        configChangeRequest.setArg("namespace", namespace);
        configChangeRequest.setArg("policy", policy);
        configChangeRequest.setArg("file", file);
        configChangeRequest.setArg("srcIp", RequestUtil.getRemoteIp(request));
        configChangeRequest.setArg("requestIpApp", RequestUtil.getAppName(request));
        configChangeRequest.setArg("use", RequestUtil.getSrcUserName(request));
        return configChangeServiceHandler(pjp, configChangeRequest.getRequestType(), configChangeRequest,
                "importConfigAround");
    }
    
    /**
     * Publish or update config.
     */
    @Conditional(PublishOrUpdateConfigCondition.class)
    @Around(CLIENT_INTERFACE_PUBLISH_CONFIG_RPC)
    Object publishConfigAroundRpc(ProceedingJoinPoint pjp, ConfigPublishRequest request, RequestMeta meta)
            throws Throwable {
        ConfigChangeRequest configChangeRequest = new ConfigChangeRequest(ConfigChangePointCutTypes.PUBLISH_BY_RPC);
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
        return configChangeServiceHandler(pjp, configChangeRequest.getRequestType(), configChangeRequest,
                "publishConfigAroundRpc");
    }
    
    /**
     * Remove config.
     */
    @Conditional(RemoveConfigCondition.class)
    @Around(CLIENT_INTERFACE_REMOVE_CONFIG_RPC)
    Object removeConfigAroundRpc(ProceedingJoinPoint pjp, ConfigRemoveRequest request, RequestMeta meta)
            throws Throwable {
        ConfigChangeRequest configChangeRequest = new ConfigChangeRequest(ConfigChangePointCutTypes.REMOVE_BY_RPC);
        configChangeRequest.setArg("dataId", request.getDataId());
        configChangeRequest.setArg("group", request.getGroup());
        configChangeRequest.setArg("tenant", request.getTenant());
        configChangeRequest.setArg("appName", request.getHeader("appName"));
        configChangeRequest.setArg("srcIp", meta.getClientIp());
        configChangeRequest.setArg("requestIpApp", request.getHeader("requestIpApp"));
        configChangeRequest.setArg("srcUser", request.getHeader("src_user"));
        configChangeRequest.setArg("use", request.getHeader("use"));
        return configChangeServiceHandler(pjp, configChangeRequest.getRequestType(), configChangeRequest,
                "removeConfigAroundRpc");
    }
    
    /**
     * Execute relevant config change plugin services.
     */
    Object configChangeServiceHandler(ProceedingJoinPoint pjp, ConfigChangePointCutTypes configChangeType,
            ConfigChangeRequest configChangeRequest, String pointcutMethodName) throws Throwable {
        PriorityQueue<ConfigChangeService> configChangeServicePriorityQueue = ConfigChangePluginManager
                .findPluginServiceQueueByPointcut(configChangeType);
        if (configChangeServicePriorityQueue == null || configChangeServicePriorityQueue.isEmpty()) {
            LOGGER.warn("no available plugin service to pointcut at [{}] {},can not execute the plugin service",
                    this.getClass(), pointcutMethodName);
            return pjp.proceed();
        }
        configChangeRequest.setArg("modifyTime", TimeUtils.getCurrentTimeStr());
        return ConfigChangePluginHandler.handle(configChangeServicePriorityQueue, pjp, configChangeRequest);
    }
    
}
