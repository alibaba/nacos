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

package com.alibaba.nacos.naming.remote.handler;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.SubscribeServiceRequest;
import com.alibaba.nacos.api.naming.remote.response.SubscribeServiceResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.AsyncListenContext;
import com.alibaba.nacos.core.remote.NacosRemoteConstants;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Handler to handle subscribe service.
 *
 * @author liuzunfei
 * @author xiweng.yy
 */
@Component
public class SubscribeServiceRequestHandler extends RequestHandler<SubscribeServiceRequest> {
    
    @Autowired
    AsyncListenContext asyncListenContext;
    
    @Override
    public SubscribeServiceRequest parseBodyString(String bodyString) {
        return JacksonUtils.toObj(bodyString, SubscribeServiceRequest.class);
    }
    
    @Override
    public Response handle(Request request, RequestMeta meta) throws NacosException {
        SubscribeServiceRequest subRequest = (SubscribeServiceRequest) request;
        String serviceKey = UtilsAndCommons.assembleFullServiceName(subRequest.getNamespace(), subRequest.getServiceName());
        String connectionId = meta.getConnectionId();
        if (subRequest.isSubscribe()) {
            asyncListenContext.addListen(NacosRemoteConstants.LISTEN_CONTEXT_NAMING, serviceKey, connectionId);
        } else {
            asyncListenContext.removeListen(NacosRemoteConstants.LISTEN_CONTEXT_NAMING, serviceKey, connectionId);
        }
        return new SubscribeServiceResponse(ResponseCode.SUCCESS.getCode(), "success");
    }
    
    @Override
    public List<String> getRequestTypes() {
        return Lists.newArrayList(NamingRemoteConstants.SUBSCRIBE_SERVICE);
    }
}
