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

package com.alibaba.nacos.plugin.config.impl;

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigRemoveResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.plugin.config.impl.webhook.WebHookCloudEventStrategy;
import com.alibaba.nacos.plugin.config.impl.webhook.WebHookNotifyStrategy;
import com.alibaba.nacos.plugin.config.impl.webhook.WebhookStrategyManager;
import com.alibaba.nacos.plugin.config.model.ConfigChangeHandleReport;
import com.alibaba.nacos.plugin.config.model.ConfigChangeNotifyInfo;
import com.alibaba.nacos.plugin.config.spi.AbstractWebHookPluginService;
import com.alibaba.nacos.plugin.config.util.ConfigPropertyUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * NacosWebHookPluginService.
 *
 * @author liyunfei
 */
public class NacosWebHookPluginService extends AbstractWebHookPluginService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosWebHookPluginService.class);
    
    private static final String WEBHOOK_TYPE = ConfigPropertyUtil.getWebHookType();
    
    private static final String WEBHOOK_URL = ConfigPropertyUtil.getWebHookUrl();
    
    private static final int PUBLISH_UPDATE_ARGS_LENGTH = 15;
    
    private static final int REMOVE_ARGS_LENGTH = 6;
    
    private static final int RPC_ARGS_LENGTH = 2;
    
    private static final int IMPORT_ARGS_LENGTH = 4;
    
    private static final String UPDATE_POINT = "update";
    
    private static WebHookNotifyStrategy notifyStrategy;
    
    private final int maxContent = 10 * 1024;
    
    static {
        Optional<WebHookNotifyStrategy> webHookNotifyStrategyOptional = WebhookStrategyManager.getInstance()
                .findStrategyByName(WEBHOOK_TYPE);
        if (webHookNotifyStrategyOptional.isPresent()) {
            notifyStrategy = webHookNotifyStrategyOptional.get();
        } else {
            LOGGER.warn("webhook plugin service not find type {} webhook strategy,will load default type cloudevent",
                    WEBHOOK_TYPE);
            notifyStrategy = new WebHookCloudEventStrategy();
        }
    }
    
    @Override
    public Object execute(ProceedingJoinPoint pjp, ConfigChangeHandleReport configChangeHandleReport) {
        Object[] args = pjp.getArgs();
        // base config info
        String dataId = null;
        String group = null;
        String content = null;
        String tenant = null;
        String type = null;
        String tag = null;
        String desc = null;
        String srcIp = null;
        String requestIpApp = null;
        String scrName = null;
        
        if (args.length == PUBLISH_UPDATE_ARGS_LENGTH || args.length == REMOVE_ARGS_LENGTH) {
            
            HttpServletRequest request = (HttpServletRequest) args[0];
            srcIp = RequestUtil.getRemoteIp(request);
            requestIpApp = RequestUtil.getAppName(request);
            scrName = RequestUtil.getSrcUserName(request);
            
            dataId = (String) args[2];
            group = (String) args[3];
            content = (String) args[5];
            
            if (content != null && content.length() > maxContent) {
                String warning = "warning:[content is big]";
                content = warning + content.substring(0, maxContent - warning.length());
            }
        }
        
        if (args.length == PUBLISH_UPDATE_ARGS_LENGTH) {
            tenant = (String) args[4];
            tag = (String) args[9];
            desc = (String) args[10];
            type = (String) args[13];
        }
        
        String pointType = configChangeHandleReport.getPointType();
        
        ConfigChangeNotifyInfo configChangeNotifyInfo = new ConfigChangeNotifyInfo(pointType,
                System.currentTimeMillis(), dataId, group);
        configChangeNotifyInfo.setSrcIp(srcIp);
        configChangeNotifyInfo.setRequestIp(requestIpApp);
        configChangeNotifyInfo.setScrName(scrName);
        
        Map<String, String> contentItem = new HashMap<>(2);
        contentItem.put("newValue", content);
        Map<String, String> tenantItem = new HashMap<>(2);
        tenantItem.put("newValue", tenant);
        Map<String, String> tagItem = new HashMap<>(2);
        tagItem.put("newValue", tag);
        Map<String, String> typeItem = new HashMap<>(2);
        typeItem.put("newValue", type);
        Map<String, String> descItem = new HashMap<>(2);
        descItem.put("newValue", desc);
        
        if (UPDATE_POINT.equals(pointType)) {
            Map<String, Object> additionInfo = configChangeHandleReport.getAdditionInfo();
            ConfigAllInfo configAllInfo = (ConfigAllInfo) additionInfo.get("oldConfigAllInfo");
            contentItem.put("oldValue", configAllInfo.getContent());
            tenantItem.put("oldValue", configAllInfo.getTenant());
            tagItem.put("oldValue", configAllInfo.getConfigTags());
            typeItem.put("oldValue", configAllInfo.getType());
            descItem.put("oldValue", configAllInfo.getDesc());
        }
        configChangeNotifyInfo.setContentItem(contentItem);
        configChangeNotifyInfo.setDescItem(descItem);
        configChangeNotifyInfo.setTagItem(tagItem);
        configChangeNotifyInfo.setTenantItem(tenantItem);
        configChangeNotifyInfo.setTypeItem(typeItem);
        
        if (configChangeNotifyInfo.getDataId() == null) {
            if (args.length == RPC_ARGS_LENGTH) {
                
                if (args[0] instanceof HttpServletRequest) {
                    Map<String, Object> additionInfo = configChangeHandleReport.getAdditionInfo();
                    String ids = (String) additionInfo.get("ids");
                    configChangeNotifyInfo.setDataId(ids);
                }
                
                if (args[0] instanceof ConfigPublishRequest) {
                    ConfigPublishRequest request = (ConfigPublishRequest) args[0];
                    RequestMeta meta = (RequestMeta) args[1];
                    configChangeNotifyInfo.setDataId(request.getDataId());
                    configChangeNotifyInfo.setScrName(request.getAdditionParam("src_user"));
                    configChangeNotifyInfo.setRequestIp(request.getAdditionParam("requestIpApp"));
                    configChangeNotifyInfo.setAppName(request.getAdditionParam("appName"));
                    configChangeNotifyInfo.setSrcIp(meta.getClientIp());
                    contentItem.put("newValue", request.getContent());
                    configChangeNotifyInfo.setContentItem(contentItem);
                }
                
                if (args[0] instanceof ConfigRemoveRequest) {
                    ConfigRemoveRequest request = (ConfigRemoveRequest) args[0];
                    RequestMeta meta = (RequestMeta) args[1];
                    configChangeNotifyInfo.setDataId(request.getDataId());
                    configChangeNotifyInfo.setSrcIp(meta.getClientIp());
                }
                
            } else if (args.length == IMPORT_ARGS_LENGTH) {
                // import file
            }
        }
        
        Object retVal = configChangeHandleReport.getRetVal();
        try {
            
            if (retVal == null) {
                retVal = pjp.proceed();
            }
            
            if (retVal instanceof RestResult) {
                RestResult restResult = (RestResult) retVal;
                configChangeNotifyInfo.setRetVal("success");
                if (!restResult.ok()) {
                    configChangeNotifyInfo.setRetVal("failed:" + restResult.getMessage());
                }
            }
            
            if (retVal instanceof Boolean) {
                configChangeNotifyInfo.setRetVal("success");
                if (!(Boolean) retVal) {
                    configChangeNotifyInfo.setRetVal("failed:" + configChangeHandleReport.getMsg());
                }
            }
            
            // RPC
            if (retVal instanceof ConfigPublishResponse) {
                ConfigPublishResponse response = (ConfigPublishResponse) retVal;
                configChangeNotifyInfo.setRetVal("success");
                if (!response.isSuccess()) {
                    configChangeNotifyInfo.setRetVal("failed:" + response.getMessage());
                }
            }
            
            if (retVal instanceof ConfigRemoveResponse) {
                ConfigRemoveResponse response = (ConfigRemoveResponse) retVal;
                configChangeNotifyInfo.setRetVal("success");
                if (!response.isSuccess()) {
                    configChangeNotifyInfo.setRetVal("failed:" + response.getMessage());
                }
            }
            
        } catch (Throwable e) {
            LOGGER.error("execute webhook plugin service failed {}", e.getMessage());
            configChangeNotifyInfo.setRetVal("failed:" + e.getMessage());
        }
        
        ConfigExecutor.executeAsyncNotify(() -> notifyConfigChange(configChangeNotifyInfo, WEBHOOK_URL));
        return retVal;
    }
    
    @Override
    public void notifyConfigChange(ConfigChangeNotifyInfo configChangeNotifyInfo, String pushUrl) {
        notifyStrategy.notifyConfigChange(configChangeNotifyInfo, pushUrl);
    }
    
    @Override
    public String getImplWay() {
        return "nacos";
    }
}
