/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */

package com.alibaba.nacos.config.server.remote;

import java.util.List;

import com.alibaba.nacos.api.config.remote.request.ConfigChangeListenRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRequestTypeConstants;
import com.alibaba.nacos.api.config.remote.response.ConfigChangeListenResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.core.remote.AsyncListenContext;
import com.alibaba.nacos.core.remote.NacosRemoteConstants;
import com.alibaba.nacos.core.remote.RequestHandler;

import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * config change listen request handler.
 * @author liuzunfei
 * @version $Id: ConfigChangeListenRequestHandler.java, v 0.1 2020年07月14日 10:11 AM liuzunfei Exp $
 */
@Component
public class ConfigChangeListenRequestHandler extends RequestHandler {
    
    @Autowired
    AsyncListenContext  asyncListenContext;
    
    @Override
    public Request parseBodyString(String bodyString) {
        return JacksonUtils.toObj(bodyString, ConfigChangeListenRequest.class);
    }

    @Override
    public Response handle(Request request, RequestMeta requestMeta) throws NacosException {
        ConfigChangeListenRequest configChangeListenRequest = (ConfigChangeListenRequest) request;
        String dataId = configChangeListenRequest.getDataId();
        String group = configChangeListenRequest.getGroup();
        String tenant = configChangeListenRequest.getTenant();
        String configKey = GroupKey2.getKey(dataId, group, tenant);
        String connectionId = requestMeta.getConnectionId();
        if (configChangeListenRequest.isCancelListen()) {
            asyncListenContext.removeListen(NacosRemoteConstants.LISTEN_CONTEXT_CONFIG, configKey, connectionId);
        } else {
            asyncListenContext.addListen(NacosRemoteConstants.LISTEN_CONTEXT_CONFIG, configKey, connectionId);
        }
        return new ConfigChangeListenResponse(200, "success");
    }

    @Override
    public List<String> getRequestTypes() {
        return Lists.newArrayList(ConfigRequestTypeConstants.CHANGE_LISTEN_CONFIG_OPERATION);
    }
}
