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
import com.alibaba.nacos.api.naming.remote.request.NamingFuzzyWatchRequest;
import com.alibaba.nacos.api.naming.remote.response.NamingFuzzyWatchResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.index.NamingFuzzyWatchContextService;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import org.springframework.stereotype.Component;

import static com.alibaba.nacos.api.common.Constants.WATCH_TYPE_CANCEL_WATCH;
import static com.alibaba.nacos.api.common.Constants.WATCH_TYPE_WATCH;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT;

/**
 * Fuzzy watch service request handler.
 *
 * @author tanyongquan
 */
@Component("namingFuzzyWatchRequestHandler")
public class NamingFuzzyWatchRequestHandler extends RequestHandler<NamingFuzzyWatchRequest, NamingFuzzyWatchResponse> {
    
    private NamingFuzzyWatchContextService namingFuzzyWatchContextService;
    
    public NamingFuzzyWatchRequestHandler(NamingFuzzyWatchContextService namingFuzzyWatchContextService) {
        this.namingFuzzyWatchContextService = namingFuzzyWatchContextService;
        NotifyCenter.registerToPublisher(ClientOperationEvent.ClientFuzzyWatchEvent.class, 1000);
        
    }
    
    @Override
    @Secured(action = ActionTypes.READ)
    public NamingFuzzyWatchResponse handle(NamingFuzzyWatchRequest request, RequestMeta meta) throws NacosException {
        
        String groupKeyPattern = request.getGroupKeyPattern();
        switch (request.getWatchType()) {
            case WATCH_TYPE_WATCH:
                try {
                    namingFuzzyWatchContextService.syncFuzzyWatcherContext(groupKeyPattern, meta.getConnectionId());
                    NotifyCenter.publishEvent(
                            new ClientOperationEvent.ClientFuzzyWatchEvent(groupKeyPattern, meta.getConnectionId(),
                                    request.getReceivedGroupKeys(), request.isInitializing()));
                } catch (NacosException nacosException) {
                    NamingFuzzyWatchResponse namingFuzzyWatchResponse = new NamingFuzzyWatchResponse();
                    namingFuzzyWatchResponse.setErrorInfo(nacosException.getErrCode(), nacosException.getErrMsg());
                    return namingFuzzyWatchResponse;
                }
                
                boolean reachToUpLimit = namingFuzzyWatchContextService.reachToUpLimit(groupKeyPattern);
                if (reachToUpLimit) {
                    NamingFuzzyWatchResponse namingFuzzyWatchResponse = new NamingFuzzyWatchResponse();
                    namingFuzzyWatchResponse.setErrorInfo(FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT.getCode(),
                            FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT.getMsg());
                    return namingFuzzyWatchResponse;
                }
                
                return NamingFuzzyWatchResponse.buildSuccessResponse();
            case WATCH_TYPE_CANCEL_WATCH:
                namingFuzzyWatchContextService.removeFuzzyWatchContext(groupKeyPattern, meta.getConnectionId());
                return NamingFuzzyWatchResponse.buildSuccessResponse();
            default:
                throw new NacosException(NacosException.INVALID_PARAM,
                        String.format("Unsupported request type %s", request.getWatchType()));
        }
    }
}
