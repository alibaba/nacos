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

package com.alibaba.nacos.client.naming.remote.gprc.redo;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.remote.request.NamingFuzzyWatchRequest;
import com.alibaba.nacos.api.naming.remote.response.NamingFuzzyWatchResponse;
import com.alibaba.nacos.client.naming.cache.NamingFuzzyWatchServiceListHolder;
import com.alibaba.nacos.client.naming.cache.NamingFuzzyWatchContext;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.common.task.AbstractExecuteTask;

import java.util.Iterator;
import java.util.Map;

import static com.alibaba.nacos.api.common.Constants.WATCH_TYPE_CANCEL_WATCH;
import static com.alibaba.nacos.api.common.Constants.WATCH_TYPE_WATCH;

public class FuzzyWatchSyncTask extends AbstractExecuteTask {
    
    private NamingFuzzyWatchServiceListHolder namingFuzzyWatchServiceListHolder;
    
    private final NamingGrpcClientProxy clientProxy;
    
    public FuzzyWatchSyncTask(NamingFuzzyWatchServiceListHolder namingFuzzyWatchServiceListHolder,NamingGrpcClientProxy clientProxy ){
        this.namingFuzzyWatchServiceListHolder = namingFuzzyWatchServiceListHolder;
        this.clientProxy=clientProxy;
    }
    
    @Override
    public void run() {
        Iterator<Map.Entry<String, NamingFuzzyWatchContext>> iterator = namingFuzzyWatchServiceListHolder.getFuzzyMatchContextMap()
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
                    namingFuzzyWatchRequest.setWatchType(WATCH_TYPE_WATCH);
                }else{
                    namingFuzzyWatchRequest.setWatchType(WATCH_TYPE_CANCEL_WATCH);
                }
                try {
                    NamingFuzzyWatchResponse namingFuzzyWatchResponse = clientProxy.fuzzyWatchRequest(
                            namingFuzzyWatchRequest);
                    if (namingFuzzyWatchResponse.isSuccess()&&WATCH_TYPE_CANCEL_WATCH.equals(namingFuzzyWatchRequest.getWatchType())){
                        namingFuzzyWatchServiceListHolder.removePatternMatchCache(namingFuzzyWatchRequest.getGroupKeyPattern());
                    }
                } catch (NacosException e) {
                    throw new RuntimeException(e);
                }
                
            }
        }
    
    }
}
