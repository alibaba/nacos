package com.alibaba.nacos.client.naming.remote.gprc;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.remote.request.AbstractFuzzyWatchNotifyRequest;
import com.alibaba.nacos.api.naming.remote.request.FuzzyWatchNotifyChangeRequest;
import com.alibaba.nacos.api.naming.remote.request.FuzzyWatchNotifyInitRequest;
import com.alibaba.nacos.api.naming.remote.response.NotifyFuzzyWatcherResponse;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.naming.cache.FuzzyWatchServiceListHolder;
import com.alibaba.nacos.client.naming.cache.NamingFuzzyWatchContext;
import com.alibaba.nacos.client.naming.event.FuzzyWatchNotifyEvent;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.ServerRequestHandler;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;

import java.util.Collection;

public class NamingFuzzyWatchRequestHandler implements ServerRequestHandler {
    
    
    FuzzyWatchServiceListHolder fuzzyWatchServiceListHolder;
    
    public NamingFuzzyWatchRequestHandler(FuzzyWatchServiceListHolder fuzzyWatchServiceListHolder){
        this.fuzzyWatchServiceListHolder=fuzzyWatchServiceListHolder;
    }

    @Override
    public Response requestReply(Request request, Connection connection) {
        if (request instanceof FuzzyWatchNotifyInitRequest) {
            FuzzyWatchNotifyInitRequest watchNotifyInitRequest = (FuzzyWatchNotifyInitRequest) request;
            NamingFuzzyWatchContext namingFuzzyWatchContext = fuzzyWatchServiceListHolder.getFuzzyMatchContextMap().get(watchNotifyInitRequest.getPattern());
            if (namingFuzzyWatchContext!=null){
                Collection<String> serviceKeys = watchNotifyInitRequest.getServiceKeys();
                if (watchNotifyInitRequest.getChangedType().equals(Constants.FUZZY_WATCH_INIT_NOTIFY)){
                    for (String serviceKey : serviceKeys) {
                        // may have a 'change event' sent to client before 'init event'
                        if (namingFuzzyWatchContext.addReceivedServiceKey(serviceKey)) {
                            NotifyCenter.publishEvent(FuzzyWatchNotifyEvent.build(fuzzyWatchServiceListHolder.getNotifierEventScope(),
                                    watchNotifyInitRequest.getPattern(),serviceKey, watchNotifyInitRequest.getChangedType()));
                        }
                    }
                }else if(watchNotifyInitRequest.getChangedType().equals(Constants.FINISH_FUZZY_WATCH_INIT_NOTIFY)){
                    namingFuzzyWatchContext.markInitializationComplete();
                }
            }
            
            return new NotifyFuzzyWatcherResponse();
        
        } else if (request instanceof FuzzyWatchNotifyChangeRequest) {
            FuzzyWatchNotifyChangeRequest notifyChangeRequest = (FuzzyWatchNotifyChangeRequest) request;
            String[] serviceKeyItems = NamingUtils.parseServiceKey(notifyChangeRequest.getServiceKey());
            String namespace=serviceKeyItems[0];
            String groupName=serviceKeyItems[1];
            String serviceName=serviceKeyItems[2];
        
            Collection<String> matchedPattern = FuzzyGroupKeyPattern.filterMatchedPatterns(fuzzyWatchServiceListHolder.getFuzzyMatchContextMap().keySet(),namespace,groupName,serviceName);
            String serviceChangeType = notifyChangeRequest.getChangedType();
        
            switch (serviceChangeType) {
                case Constants.ServiceChangedType.ADD_SERVICE:
                case Constants.ServiceChangedType.INSTANCE_CHANGED:
                    for (String pattern : matchedPattern) {
                        NamingFuzzyWatchContext namingFuzzyWatchContext = fuzzyWatchServiceListHolder.getFuzzyMatchContextMap().get(pattern);
                        if (namingFuzzyWatchContext != null && namingFuzzyWatchContext.getReceivedServiceKeys().add(((FuzzyWatchNotifyChangeRequest) request).getServiceKey())) {
                            //publish local service add event
                            NotifyCenter.publishEvent(
                                    FuzzyWatchNotifyEvent.build(fuzzyWatchServiceListHolder.getNotifierEventScope(),
                                            pattern,notifyChangeRequest.getServiceKey(),Constants.ServiceChangedType.ADD_SERVICE));
                        }
                    }
                    break;
                case Constants.ServiceChangedType.DELETE_SERVICE:
                    for (String pattern : matchedPattern) {
                        NamingFuzzyWatchContext namingFuzzyWatchContext = fuzzyWatchServiceListHolder.getFuzzyMatchContextMap().get(pattern);
                        if (namingFuzzyWatchContext != null && namingFuzzyWatchContext.getReceivedServiceKeys().remove(notifyChangeRequest.getServiceKey())) {
                            NotifyCenter.publishEvent(
                                    FuzzyWatchNotifyEvent.build(fuzzyWatchServiceListHolder.getNotifierEventScope(),
                                            pattern, notifyChangeRequest.getServiceKey(),Constants.ServiceChangedType.DELETE_SERVICE));
                        }
                    }
                    break;
                default:
                    break;
            }
            return new NotifyFuzzyWatcherResponse();
        }
        return null;
    }
}
