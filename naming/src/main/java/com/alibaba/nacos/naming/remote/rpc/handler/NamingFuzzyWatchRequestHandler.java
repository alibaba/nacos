/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.remote.rpc.handler;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.NamingFuzzyWatchRequest;
import com.alibaba.nacos.api.naming.remote.response.NamingFuzzyWatchResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Fuzzy watch service request handler.
 *
 * @author tanyongquan
 */
@Component("namingFuzzyWatchRequestHandler")
public class NamingFuzzyWatchRequestHandler extends RequestHandler<NamingFuzzyWatchRequest, NamingFuzzyWatchResponse> {
    
    public NamingFuzzyWatchRequestHandler() {
    }
    
    @Override
    @Secured(action = ActionTypes.READ)
    public NamingFuzzyWatchResponse handle(NamingFuzzyWatchRequest request, RequestMeta meta) throws NacosException {
        
        String groupKeyPattern = request.getGroupKeyPattern();
        
        switch (request.getWatchType()) {
            case NamingRemoteConstants.FUZZY_WATCH_SERVICE:
                NotifyCenter.publishEvent(new ClientOperationEvent.ClientFuzzyWatchEvent(groupKeyPattern, meta.getConnectionId(),request.getReceivedGroupKeys(),request.isInitializing()));
                return NamingFuzzyWatchResponse.buildSuccessResponse(NamingRemoteConstants.FUZZY_WATCH_SERVICE);
            case NamingRemoteConstants.CANCEL_FUZZY_WATCH_SERVICE:
                NotifyCenter.publishEvent(new ClientOperationEvent.ClientCancelFuzzyWatchEvent(groupKeyPattern, meta.getConnectionId()));
                return NamingFuzzyWatchResponse.buildSuccessResponse(NamingRemoteConstants.CANCEL_FUZZY_WATCH_SERVICE);
            default:
                throw new NacosException(NacosException.INVALID_PARAM,
                        String.format("Unsupported request type %s", request.getWatchType()));
        }
    }
}
