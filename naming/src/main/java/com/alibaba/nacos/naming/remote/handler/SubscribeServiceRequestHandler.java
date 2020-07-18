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
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.SubscribeServiceRequest;
import com.alibaba.nacos.api.naming.remote.response.SubscribeServiceResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.remote.AsyncListenContext;
import com.alibaba.nacos.core.remote.NacosRemoteConstants;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.core.ServiceInfoGenerator;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.RemotePushService;
import com.google.common.collect.Lists;
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
    
    private final AsyncListenContext asyncListenContext;
    
    private final ServiceInfoGenerator serviceInfoGenerator;
    
    private final RemotePushService remotePushService;
    
    public SubscribeServiceRequestHandler(AsyncListenContext asyncListenContext,
            ServiceInfoGenerator serviceInfoGenerator, RemotePushService remotePushService) {
        this.asyncListenContext = asyncListenContext;
        this.serviceInfoGenerator = serviceInfoGenerator;
        this.remotePushService = remotePushService;
    }
    
    @Override
    public SubscribeServiceRequest parseBodyString(String bodyString) {
        return JacksonUtils.toObj(bodyString, SubscribeServiceRequest.class);
    }
    
    @Override
    public Response handle(Request request, RequestMeta meta) throws NacosException {
        SubscribeServiceRequest subscribeServiceRequest = (SubscribeServiceRequest) request;
        String namespaceId = subscribeServiceRequest.getNamespace();
        String serviceName = subscribeServiceRequest.getServiceName();
        String serviceKey = UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName);
        String connectionId = meta.getConnectionId();
        ServiceInfo serviceInfo = serviceInfoGenerator
                .generateServiceInfo(namespaceId, serviceName, StringUtils.EMPTY, false, meta.getClientIp());
        Subscriber subscriber = new Subscriber(meta.getClientIp(), "", "unknown", meta.getClientIp(), namespaceId,
                serviceName);
        if (subscribeServiceRequest.isSubscribe()) {
            asyncListenContext.addListen(NacosRemoteConstants.LISTEN_CONTEXT_NAMING, serviceKey, connectionId);
            remotePushService.registerSubscribeForService(serviceKey, subscriber);
        } else {
            asyncListenContext.removeListen(NacosRemoteConstants.LISTEN_CONTEXT_NAMING, serviceKey, connectionId);
            remotePushService.removeSubscribeForService(serviceKey, subscriber);
        }
        return new SubscribeServiceResponse(ResponseCode.SUCCESS.getCode(), "success", serviceInfo);
    }
    
    @Override
    public List<String> getRequestTypes() {
        return Lists.newArrayList(NamingRemoteConstants.SUBSCRIBE_SERVICE);
    }
}
