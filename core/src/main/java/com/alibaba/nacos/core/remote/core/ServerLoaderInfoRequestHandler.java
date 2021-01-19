/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.ServerLoaderInfoRequest;
import com.alibaba.nacos.api.remote.response.ServerLoaderInfoResponse;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * request handler to handle server loader info.
 *
 * @author liuzunfei
 * @version $Id: ServerLoaderInfoRequestHandler.java, v 0.1 2020年09月03日 2:51 PM liuzunfei Exp $
 */
@Component
public class ServerLoaderInfoRequestHandler extends RequestHandler<ServerLoaderInfoRequest, ServerLoaderInfoResponse> {
    
    @Autowired
    private ConnectionManager connectionManager;
    
    @Override
    public ServerLoaderInfoResponse handle(ServerLoaderInfoRequest request, RequestMeta meta) throws NacosException {
        ServerLoaderInfoResponse serverLoaderInfoResponse = new ServerLoaderInfoResponse();
        serverLoaderInfoResponse.putMetricsValue("conCount", String.valueOf(connectionManager.currentClientsCount()));
        Map<String, String> filter = new HashMap<String, String>(2);
        filter.put(RemoteConstants.LABEL_SOURCE, RemoteConstants.LABEL_SOURCE_SDK);
        serverLoaderInfoResponse
                .putMetricsValue("sdkConCount", String.valueOf(connectionManager.currentClientsCount(filter)));
        serverLoaderInfoResponse.putMetricsValue("limitRule", JacksonUtils.toJson(connectionManager.getConnectionLimitRule()));
        serverLoaderInfoResponse.putMetricsValue("load", String.valueOf(EnvUtil.getLoad()));
        serverLoaderInfoResponse.putMetricsValue("cpu", String.valueOf(EnvUtil.getCPU()));
        
        return serverLoaderInfoResponse;
    }
    
}
