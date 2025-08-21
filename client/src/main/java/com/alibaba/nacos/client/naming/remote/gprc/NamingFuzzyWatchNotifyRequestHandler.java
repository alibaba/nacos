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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.remote.request.NamingFuzzyWatchChangeNotifyRequest;
import com.alibaba.nacos.api.naming.remote.request.NamingFuzzyWatchSyncRequest;
import com.alibaba.nacos.api.naming.remote.response.NamingFuzzyWatchChangeNotifyResponse;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.naming.cache.NamingFuzzyWatchContext;
import com.alibaba.nacos.client.naming.cache.NamingFuzzyWatchServiceListHolder;
import com.alibaba.nacos.client.naming.event.NamingFuzzyWatchNotifyEvent;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.ServerRequestHandler;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;

import java.util.Collection;

import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_RESOURCE_CHANGED;

/**
 * handle fuzzy watch request from server.
 *
 * @author shiyiyue
 */
public class NamingFuzzyWatchNotifyRequestHandler implements ServerRequestHandler {
    
    NamingFuzzyWatchServiceListHolder namingFuzzyWatchServiceListHolder;
    
    public NamingFuzzyWatchNotifyRequestHandler(NamingFuzzyWatchServiceListHolder namingFuzzyWatchServiceListHolder) {
        this.namingFuzzyWatchServiceListHolder = namingFuzzyWatchServiceListHolder;
        NotifyCenter.registerToPublisher(NamingFuzzyWatchNotifyEvent.class, 1000);
    }
    
    @Override
    public Response requestReply(Request request, Connection connection) {
        
        if (request instanceof NamingFuzzyWatchSyncRequest) {
            NamingFuzzyWatchSyncRequest watchNotifySyncRequest = (NamingFuzzyWatchSyncRequest) request;
            NamingFuzzyWatchContext namingFuzzyWatchContext = namingFuzzyWatchServiceListHolder.getFuzzyMatchContextMap()
                    .get(watchNotifySyncRequest.getGroupKeyPattern());
            if (namingFuzzyWatchContext != null) {
                Collection<NamingFuzzyWatchSyncRequest.Context> serviceKeys = watchNotifySyncRequest.getContexts();
                if (watchNotifySyncRequest.getSyncType().equals(Constants.FUZZY_WATCH_INIT_NOTIFY)
                        || watchNotifySyncRequest.getSyncType().equals(Constants.FUZZY_WATCH_DIFF_SYNC_NOTIFY)) {
                    for (NamingFuzzyWatchSyncRequest.Context serviceKey : serviceKeys) {
                        // may have a 'change event' sent to client before 'init event'
                        if (namingFuzzyWatchContext.addReceivedServiceKey(serviceKey.getServiceKey())) {
                            NotifyCenter.publishEvent(NamingFuzzyWatchNotifyEvent.build(
                                    namingFuzzyWatchServiceListHolder.getNotifierEventScope(),
                                    watchNotifySyncRequest.getGroupKeyPattern(), serviceKey.getServiceKey(),
                                    serviceKey.getChangedType(), watchNotifySyncRequest.getSyncType()));
                        }
                    }
                } else if (watchNotifySyncRequest.getSyncType().equals(Constants.FINISH_FUZZY_WATCH_INIT_NOTIFY)) {
                    namingFuzzyWatchContext.markInitializationComplete();
                }
            }
            
            return new NamingFuzzyWatchChangeNotifyResponse();
            
        } else if (request instanceof NamingFuzzyWatchChangeNotifyRequest) {
            NamingFuzzyWatchChangeNotifyRequest notifyChangeRequest = (NamingFuzzyWatchChangeNotifyRequest) request;
            String[] serviceKeyItems = NamingUtils.parseServiceKey(notifyChangeRequest.getServiceKey());
            String namespace = serviceKeyItems[0];
            String groupName = serviceKeyItems[1];
            String serviceName = serviceKeyItems[2];
            
            Collection<String> matchedPattern = FuzzyGroupKeyPattern.filterMatchedPatterns(
                    namingFuzzyWatchServiceListHolder.getFuzzyMatchContextMap().keySet(), serviceName, groupName,
                    namespace);
            String serviceChangeType = notifyChangeRequest.getChangedType();
            
            switch (serviceChangeType) {
                case Constants.ServiceChangedType.ADD_SERVICE:
                case Constants.ServiceChangedType.INSTANCE_CHANGED:
                    for (String pattern : matchedPattern) {
                        NamingFuzzyWatchContext namingFuzzyWatchContext = namingFuzzyWatchServiceListHolder.getFuzzyMatchContextMap()
                                .get(pattern);
                        if (namingFuzzyWatchContext != null && namingFuzzyWatchContext.addReceivedServiceKey(
                                ((NamingFuzzyWatchChangeNotifyRequest) request).getServiceKey())) {
                            //publish local service add event
                            NotifyCenter.publishEvent(NamingFuzzyWatchNotifyEvent.build(
                                    namingFuzzyWatchServiceListHolder.getNotifierEventScope(), pattern,
                                    notifyChangeRequest.getServiceKey(), Constants.ServiceChangedType.ADD_SERVICE,
                                    FUZZY_WATCH_RESOURCE_CHANGED));
                        }
                    }
                    break;
                case Constants.ServiceChangedType.DELETE_SERVICE:
                    for (String pattern : matchedPattern) {
                        NamingFuzzyWatchContext namingFuzzyWatchContext = namingFuzzyWatchServiceListHolder.getFuzzyMatchContextMap()
                                .get(pattern);
                        if (namingFuzzyWatchContext != null && namingFuzzyWatchContext.removeReceivedServiceKey(
                                notifyChangeRequest.getServiceKey())) {
                            NotifyCenter.publishEvent(NamingFuzzyWatchNotifyEvent.build(
                                    namingFuzzyWatchServiceListHolder.getNotifierEventScope(), pattern,
                                    notifyChangeRequest.getServiceKey(), Constants.ServiceChangedType.DELETE_SERVICE,
                                    FUZZY_WATCH_RESOURCE_CHANGED));
                        }
                    }
                    break;
                default:
                    break;
            }
            return new NamingFuzzyWatchChangeNotifyResponse();
        }
        return null;
    }
}
