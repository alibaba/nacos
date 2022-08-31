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
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.ConfigChangePublisher;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.plugin.config.ConfigChangePluginManager;
import com.alibaba.nacos.plugin.config.configuration.AspectEnableCondition;
import com.alibaba.nacos.plugin.config.configuration.ImportConfigCondition;
import com.alibaba.nacos.plugin.config.configuration.PublishOrUpdateConfigCondition;
import com.alibaba.nacos.plugin.config.configuration.RemoveConfigCondition;
import com.alibaba.nacos.plugin.config.handler.ConfigChangePluginHandler;
import com.alibaba.nacos.plugin.config.spi.ConfigChangeService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * config change pointcut aspect.
 *
 * @author liyunfei
 */
@ConditionalOnClass(AspectEnableCondition.class)
@Aspect
@Component
public class ConfigChangeAspect {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigChangeAspect.class);
    
    //FIXME 发布与更新无需判断
    @Autowired
    PersistService persistService;
    
    /**
     * Publish config http.
     */
    private static final String CLIENT_INTERFACE_PUBLISH_CONFIG =
            "execution(* com.alibaba.nacos.config.server.controller.ConfigController.publishConfig(..)) "
                    + "&& args(request,response,dataId,group,tenant,content,..) "
                    + "&& @annotation(org.springframework.web.bind.annotation.PostMapping)";
    
    /**
     * Publish config rpc.
     */
    private static final String CLIENT_INTERFACE_PUBLISH_CONFIG_RPC =
            "execution(* com.alibaba.nacos.core.remote.RequestHandler.handleRequest(..)) "
                    + "&& target(com.alibaba.nacos.config.server.remote.ConfigPublishRequestHandler) "
                    + "&& args(request,meta)";
    
    /**
     * Remove config by id.
     */
    private static final String CLIENT_INTERFACE_REMOVE_CONFIG =
            "execution(* com.alibaba.nacos.config.server.controller.ConfigController.deleteConfig(..))"
                    + " && args(request,response,dataId,group,tenant,..)";
    
    /**
     * Remove config by ids.
     */
    private static final String CLIENT_INTERFACE_BATCH_REMOVE_CONFIG =
            "execution(* com.alibaba.nacos.config.server.controller.ConfigController.deleteConfigs(..))";
    
    /**
     * Remove config rpc.
     */
    @SuppressWarnings("checkstyle:linelength")
    private static final String CLIENT_INTERFACE_REMOVE_CONFIG_RPC =
            "execution(* com.alibaba.nacos.core.remote.RequestHandler.handleRequest(..)) "
                    + " && target(com.alibaba.nacos.config.server.remote.ConfigRemoveRequestHandler)"
                    + " && args(request,meta)";
    
    /**
     * import file.
     */
    private static final String CLIENT_INTERFACE_IMPORT_CONFIG =
            "execution(* com.alibaba.nacos.config.server.controller.ConfigController.importAndPublishConfig(..)) "
                    + "&& args(request,srcUser,namespace,policy,file)";
    
    /**
     * publish or update config.
     */
    @ConditionalOnClass(PublishOrUpdateConfigCondition.class)
    @Around(CLIENT_INTERFACE_PUBLISH_CONFIG)
    Object publishConfigAround(ProceedingJoinPoint pjp, HttpServletRequest request, HttpServletResponse response,
            String dataId, String group, String tenant, String content) throws Throwable {
        // publish - update ;
        ConfigAllInfo configAllInfo = persistService.findConfigAllInfo(dataId, group, tenant);
        //FIXME 枚举
        String handleType = "publish";
        Map<String, Object> additionInfo = new HashMap<>(2);
        if (configAllInfo != null) {
            handleType = "update";
            additionInfo.put("oldConfigAllInfo", configAllInfo);
        }
        PriorityQueue<ConfigChangeService> configChangeServicePriorityQueue = ConfigChangePluginManager
                .findPluginServiceQueueByPointcut(handleType);
        if (configChangeServicePriorityQueue == null) {
            LOGGER.warn("no available plugin service to pointcut at publishConfigAround,can not execute the plugin service");
            return pjp.proceed();
        }
        return ConfigChangePluginHandler.handle(configChangeServicePriorityQueue, pjp, handleType, additionInfo);
    }
    
    /**
     * remove config.
     */
    @Conditional(RemoveConfigCondition.class)
    @Around(CLIENT_INTERFACE_REMOVE_CONFIG)
    Object removeConfigByIdAround(ProceedingJoinPoint pjp, HttpServletRequest request, HttpServletResponse response,
            String dataId, String group, String tenant) throws Throwable {
        PriorityQueue<ConfigChangeService> configChangeServicePriorityQueue = ConfigChangePluginManager
                .findPluginServiceQueueByPointcut("remove");
        if (configChangeServicePriorityQueue == null) {
            LOGGER.warn("no available plugin service to pointcut at removeConfigByIdAround,can not execute the plugin service");
            return pjp.proceed();
        }
        return ConfigChangePluginHandler.handle(configChangeServicePriorityQueue, pjp, "remove", null);
    }
    
    /**
     * remove config by ids.
     */
    @Conditional(RemoveConfigCondition.class)
    @Around(CLIENT_INTERFACE_BATCH_REMOVE_CONFIG)
    public Object removeConfigByIdsAround(ProceedingJoinPoint pjp) throws Throwable {
        PriorityQueue<ConfigChangeService> configChangeServicePriorityQueue = ConfigChangePluginManager
                .findPluginServiceQueueByPointcut("remove");
        if (configChangeServicePriorityQueue == null) {
            LOGGER.warn("no available plugin service to pointcut at removeConfigByIdAround,can not execute the plugin service");
            return pjp.proceed();
        }
        
        String clientIp = RequestUtil.getRemoteIp((HttpServletRequest) pjp.getArgs()[0]);
        final Timestamp time = TimeUtils.getCurrentTime();
        List<ConfigInfo> configInfoList = persistService
                .removeConfigInfoByIds((List<Long>) pjp.getArgs()[1], clientIp, null);
        List<String> ids = new ArrayList<>();
        for (ConfigInfo configInfo : configInfoList) {
            ConfigChangePublisher.notifyConfigChange(
                    new ConfigDataChangeEvent(false, configInfo.getDataId(), configInfo.getGroup(),
                            configInfo.getTenant(), time.getTime()));
            ConfigTraceService
                    .logPersistenceEvent(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant(), null,
                            time.getTime(), clientIp, ConfigTraceService.PERSISTENCE_EVENT_REMOVE, null);
            ids.add(configInfo.getDataId());
        }
        Map<String, Object> additionInfo = new HashMap<>(2);
        additionInfo.put("ids", ids.toString());
        return ConfigChangePluginHandler.handle(configChangeServicePriorityQueue, pjp, "remove", additionInfo);
    }
    
    /**
     * import config.
     */
    @Conditional(ImportConfigCondition.class)
    @Around(CLIENT_INTERFACE_IMPORT_CONFIG)
    public Object importConfigAround(ProceedingJoinPoint pjp, HttpServletRequest request, String srcUser, String namespace,
            SameConfigPolicy policy, MultipartFile file) throws Throwable {
        PriorityQueue<ConfigChangeService> configChangeServicePriorityQueue = ConfigChangePluginManager
                .findPluginServiceQueueByPointcut("import");
        if (configChangeServicePriorityQueue == null) {
            LOGGER.error("no available plugin service to pointcut at importConfigAround,can not execute the plugin service");
            return pjp.proceed();
        }
        return ConfigChangePluginHandler.handle(configChangeServicePriorityQueue, pjp, "import", null);
    }
    
    @Conditional(PublishOrUpdateConfigCondition.class)
    @Around(CLIENT_INTERFACE_PUBLISH_CONFIG_RPC)
    Object publishConfigAroundRpc(ProceedingJoinPoint pjp, ConfigPublishRequest request, RequestMeta meta)
            throws Throwable {
        PriorityQueue<ConfigChangeService> configChangeServicePriorityQueue = ConfigChangePluginManager
                .findPluginServiceQueueByPointcut("publish");
        if (configChangeServicePriorityQueue == null) {
            LOGGER.error("no available plugin service to pointcut at publishConfigAroundRpc,can not execute the plugin service");
            return pjp.proceed();
        }
        return ConfigChangePluginHandler.handle(configChangeServicePriorityQueue, pjp, "publish", null);
    }
    
    @Conditional(RemoveConfigCondition.class)
    @Around(CLIENT_INTERFACE_REMOVE_CONFIG_RPC)
    Object removeConfigAroundRpc(ProceedingJoinPoint pjp, ConfigRemoveRequest request, RequestMeta meta)
            throws Throwable {
        PriorityQueue<ConfigChangeService> configChangeServicePriorityQueue = ConfigChangePluginManager
                .findPluginServiceQueueByPointcut("remove");
        if (configChangeServicePriorityQueue == null) {
            LOGGER.error("no available plugin service to pointcut at removeConfigAroundRpc,can not execute the plugin service");
            return pjp.proceed();
        }
        return ConfigChangePluginHandler.handle(configChangeServicePriorityQueue, pjp, "remove", null);
    }
}
