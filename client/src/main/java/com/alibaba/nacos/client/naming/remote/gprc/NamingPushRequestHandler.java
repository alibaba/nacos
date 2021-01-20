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

package com.alibaba.nacos.client.naming.remote.gprc;

import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;
import com.alibaba.nacos.api.naming.remote.response.NotifySubscriberResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.common.remote.client.ServerRequestHandler;

/**
 * Naming push request handler.
 *
 * @author xiweng.yy
 */
public class NamingPushRequestHandler implements ServerRequestHandler {
    
    private final ServiceInfoHolder serviceInfoHolder;
    
    public NamingPushRequestHandler(ServiceInfoHolder serviceInfoHolder) {
        this.serviceInfoHolder = serviceInfoHolder;
    }
    
    @Override
    public Response requestReply(Request request) {
        if (request instanceof NotifySubscriberRequest) {
            NotifySubscriberRequest notifyResponse = (NotifySubscriberRequest) request;
            serviceInfoHolder.processServiceInfo(notifyResponse.getServiceInfo());
            return new NotifySubscriberResponse();
        }
        return null;
    }
}
