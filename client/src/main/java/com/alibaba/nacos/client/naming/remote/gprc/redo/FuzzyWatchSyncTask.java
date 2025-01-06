package com.alibaba.nacos.client.naming.remote.gprc.redo;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.NamingFuzzyWatchRequest;
import com.alibaba.nacos.api.naming.remote.response.NamingFuzzyWatchResponse;
import com.alibaba.nacos.client.naming.cache.FuzzyWatchServiceListHolder;
import com.alibaba.nacos.client.naming.cache.NamingFuzzyWatchContext;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.common.task.AbstractExecuteTask;

import java.util.Iterator;
import java.util.Map;

public class FuzzyWatchSyncTask extends AbstractExecuteTask {
    
    private FuzzyWatchServiceListHolder fuzzyWatchServiceListHolder;
    
    private final NamingGrpcClientProxy clientProxy;
    
    public FuzzyWatchSyncTask(FuzzyWatchServiceListHolder fuzzyWatchServiceListHolder,NamingGrpcClientProxy clientProxy ){
        this.fuzzyWatchServiceListHolder=fuzzyWatchServiceListHolder;
        this.clientProxy=clientProxy;
    }
    
    @Override
    public void run() {
        Iterator<Map.Entry<String, NamingFuzzyWatchContext>> iterator = fuzzyWatchServiceListHolder.getFuzzyMatchContextMap()
                .entrySet().iterator();
        while (iterator.hasNext()){
            Map.Entry<String, NamingFuzzyWatchContext> next = iterator.next();
            while(!next.getValue().isConsistentWithServer()){
                NamingFuzzyWatchContext namingFuzzyWatchContext = next.getValue();
                NamingFuzzyWatchRequest namingFuzzyWatchRequest=new NamingFuzzyWatchRequest();
                namingFuzzyWatchRequest.setInitializing(namingFuzzyWatchContext.isInitializing());
                namingFuzzyWatchRequest.setNamespace(clientProxy.getNamespaceId());
                namingFuzzyWatchRequest.setReceivedGroupKeys(namingFuzzyWatchContext.getReceivedServiceKeys());
                namingFuzzyWatchRequest.setGroupKeyPattern(next.getKey());
                if (namingFuzzyWatchContext.isDiscard()&&namingFuzzyWatchContext.getNamingFuzzyWatchers().isEmpty()){
                    namingFuzzyWatchRequest.setWatchType(NamingRemoteConstants.CANCEL_FUZZY_WATCH_SERVICE);
                }else{
                    namingFuzzyWatchRequest.setWatchType(NamingRemoteConstants.FUZZY_WATCH_SERVICE);
                }
                try {
                    NamingFuzzyWatchResponse namingFuzzyWatchResponse = clientProxy.fuzzyWatchRequest(
                            namingFuzzyWatchRequest);
                    if (namingFuzzyWatchResponse.isSuccess()&&NamingRemoteConstants.CANCEL_FUZZY_WATCH_SERVICE.equals(namingFuzzyWatchRequest.getWatchType())){
                        fuzzyWatchServiceListHolder.removePatternMatchCache(namingFuzzyWatchRequest.getGroupKeyPattern());
                    }
                } catch (NacosException e) {
                    throw new RuntimeException(e);
                }
                
            }
        }
    
    }
}
