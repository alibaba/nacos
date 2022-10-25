/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.config.util;

import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.plugin.config.ConfigChangeNotifyInfoBuilder;
import com.alibaba.nacos.plugin.config.constants.ConfigChangePointCutTypes;
import com.alibaba.nacos.plugin.config.model.ConfigChangeNotifyInfo;
import com.alibaba.nacos.plugin.config.model.ConfigChangeRequest;
import com.alibaba.nacos.plugin.config.model.ConfigChangeResponse;

import java.util.HashMap;

/**
 * Handle the params of ConfigChangeResponse and ConfigChangeRequest.
 *
 * @author liyunfei
 */
public class ConfigChangeParamUtil {
    
    /**
     * Convert to the {@link ConfigChangeNotifyInfo},which notify other by webhook.
     */
    public static ConfigChangeNotifyInfo convertRequestToNotifyInfo(ConfigChangeRequest configChangeRequest,
            ConfigChangeResponse configChangeResponse) {
        ConfigChangePointCutTypes configChangePointCutTypes = configChangeRequest.getRequestType();
        HashMap<String, Object> requestArgs = configChangeRequest.getRequestArgs();
        final String action = configChangePointCutTypes.value();
        final Boolean rs = convertResponseToBoolean(configChangeResponse);
        final String modifyTime = (String) requestArgs.get("modifyTime");
        final String srcUser = (String) requestArgs.get("srcUser");
        final String use = (String) requestArgs.get("use");
        final String appName = (String) requestArgs.get("appName");
        final String srcIp = (String) requestArgs.get("srcIp");
        ConfigChangeNotifyInfo configChangeNotifyInfo = new ConfigChangeNotifyInfo(action, rs, modifyTime);
        ConfigChangeNotifyInfoBuilder configChangeNotifyInfoBuilder = ConfigChangeNotifyInfoBuilder.newBuilder();
        configChangeNotifyInfoBuilder = configChangeNotifyInfoBuilder.basicInfo(action, rs, modifyTime)
                .sourceInfo(srcUser, srcIp, use, appName);
        switch (configChangePointCutTypes) {
            case PUBLISH_BY_HTTP:
            case PUBLISH_BY_RPC: {
                final String dataId = (String) requestArgs.get("dataId");
                final String group = (String) requestArgs.get("group");
                final String tenant = (String) requestArgs.get("tenant");
                final String content = (String) requestArgs.get("content");
                final String type = (String) requestArgs.get("type");
                final String tag = (String) requestArgs.get("tag");
                final String configTags = (String) requestArgs.get("configTags");
                final String effect = (String) requestArgs.get("effect");
                final String desc = (String) requestArgs.get("desc");
                configChangeNotifyInfo = configChangeNotifyInfoBuilder
                        .publishOrUpdateInfo(dataId, group, tenant, content, type, tag, configTags, effect, desc)
                        .build();
                break;
            }
            case REMOVE_BATCH_HTTP:
            case REMOVE_BY_HTTP: {
                final String dataId = (String) requestArgs.get("dataId");
                configChangeNotifyInfo = configChangeNotifyInfoBuilder.removeInfo(dataId).build();
                break;
            }
            case IMPORT_BY_HTTP: {
                final String namespace = (String) requestArgs.get("namespace");
                final SameConfigPolicy policy = (SameConfigPolicy) requestArgs.get("policy");
                configChangeNotifyInfo = configChangeNotifyInfoBuilder.importInfo(namespace, policy).build();
                break;
            }
            default: break;
        }
        final String msg = configChangeResponse.getMsg();
        if (msg != null) {
            configChangeNotifyInfo.setErrorMsg(msg);
        }
        return configChangeNotifyInfo;
    }
    
    /**
     * Convert to Boolean response,which to is used by before plugin service.
     */
    public static boolean convertResponseToBoolean(ConfigChangeResponse configChangeResponse) {
        ConfigChangePointCutTypes configChangePointCutTypes = configChangeResponse.getResponseType();
        boolean flag = true;
        switch (configChangePointCutTypes) {
            // Http type
            case PUBLISH_BY_HTTP:
            case REMOVE_BY_HTTP:
            case REMOVE_BATCH_HTTP:
            case IMPORT_BY_HTTP: {
                flag = convertBeforeHttpResponse(configChangeResponse);
                break;
            }
            // Rpc type
            case PUBLISH_BY_RPC:
            case REMOVE_BY_RPC: {
                flag = convertBeforeRpcResponse(configChangeResponse);
                break;
            }
            default: break;
        }
        return flag;
    }
    
    /**
     * Convert http resp to bool type,which is convenient to read.
     */
    public static boolean convertBeforeHttpResponse(ConfigChangeResponse configChangeResponse) {
        Object retVal = configChangeResponse.getRetVal();
        if (retVal == null) {
            return true;
        }
        if (retVal instanceof Boolean) {
            return (Boolean) retVal;
        }
        if (retVal instanceof RestResult) {
            return ((RestResult<?>) retVal).ok();
        }
        return true;
    }
    
    /**
     * Convert rpc resp to bool type,which is convenient to read.
     */
    public static boolean convertBeforeRpcResponse(ConfigChangeResponse configChangeResponse) {
        Object retVal = configChangeResponse.getRetVal();
        if (retVal == null) {
            return true;
        }
        if (retVal instanceof Response) {
            return ((Response) retVal).isSuccess();
        }
        return true;
    }
}
