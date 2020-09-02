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
import com.alibaba.nacos.api.naming.remote.request.SubscribeServiceRequest;
import com.alibaba.nacos.api.naming.remote.response.SubscribeServiceResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.core.ServiceInfoGenerator;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.RemotePushService;
import com.alibaba.nacos.naming.remote.RemotingConnectionHolder;
import org.springframework.stereotype.Component;

/**
 * Handler to handle subscribe service.
 *
 * @author liuzunfei
 * @author xiweng.yy
 */
@Component
public class SubscribeServiceRequestHandler extends RequestHandler<SubscribeServiceRequest, SubscribeServiceResponse> {
    
    private final ServiceInfoGenerator serviceInfoGenerator;
    
    private final RemotePushService remotePushService;
    
    private final RemotingConnectionHolder remotingConnectionHolder;
    
    public SubscribeServiceRequestHandler(ServiceInfoGenerator serviceInfoGenerator,
            RemotePushService remotePushService, RemotingConnectionHolder remotingConnectionHolder) {
        this.serviceInfoGenerator = serviceInfoGenerator;
        this.remotePushService = remotePushService;
        this.remotingConnectionHolder = remotingConnectionHolder;
    }
    
    @Override
    public SubscribeServiceResponse handle(SubscribeServiceRequest request, RequestMeta meta) throws NacosException {
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
            remotePushService.registerSubscribeForService(serviceKey, subscriber, connectionId);
            remotingConnectionHolder.getRemotingConnection(connectionId)
                    .addNewSubscriber(namespaceId, serviceName, subscriber);
        } else {
            remotePushService.removeSubscribeForService(serviceKey, subscriber);
            remotingConnectionHolder.getRemotingConnection(connectionId)
                    .removeSubscriber(namespaceId, serviceName, subscriber);
        }
        return new SubscribeServiceResponse(ResponseCode.SUCCESS.getCode(), "success", serviceInfo);
    }
    
}
