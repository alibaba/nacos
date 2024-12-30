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

package com.alibaba.nacos.naming.push.v2.task;

import com.alibaba.nacos.api.naming.remote.request.FuzzyWatchNotifyChangeRequest;
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.index.ClientFuzzyWatchIndexesManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.push.v2.NoRequiredRetryException;
import com.alibaba.nacos.naming.push.v2.PushConfig;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Nacos naming fuzzy watch notify service change push execute task.
 *
 * @author tanyongquan
 */
public class FuzzyWatchNotifyChangeExecuteTask extends AbstractExecuteTask {
    
    private final Service service;
    
    private final FuzzyWatchPushDelayTaskEngine delayTaskEngine;
    
    private final FuzzyWatchNotifyChangeDelayTask delayTask;
    
    public FuzzyWatchNotifyChangeExecuteTask(Service service, FuzzyWatchPushDelayTaskEngine delayTaskEngine,
            FuzzyWatchNotifyChangeDelayTask delayTask) {
        this.service = service;
        this.delayTaskEngine = delayTaskEngine;
        this.delayTask = delayTask;
    }
    
    @Override
    public void run() {
        try {
            ClientManager clientManager = delayTaskEngine.getClientManager();
            String changedType = delayTask.getChangedType();
            for (String clientId : getWatchTargetClientIds()) {
                Client client = clientManager.getClient(clientId);
                if (null == client) {
                    continue;
                }
                delayTaskEngine.getPushExecutor().doFuzzyWatchNotifyPushWithCallBack(clientId, new FuzzyWatchNotifyChangeRequest(
                                service.getNamespace(), service.getName(), service.getGroup(), changedType),
                        new WatchNotifyPushCallback(clientId, changedType));
            }
        } catch (Exception e) {
            Loggers.PUSH.error("Fuzzy watch notify task for service" + service.getGroupedServiceName() + " execute failed ", e);
            delayTaskEngine.addTask(service, new FuzzyWatchNotifyChangeDelayTask(service, delayTask.getChangedType(), 1000L));
        }
    }
    
    /**
     * get watch notify client id.
     *
     * @return A set of ClientID need to be notified
     */
    private Set<String> getWatchTargetClientIds() {
        if (!delayTask.isPushToAll()) {
            return delayTask.getTargetClients();
        }
        Set<String> watchNotifyClientIds = new HashSet<>(16);
        ClientFuzzyWatchIndexesManager indexesManager = delayTaskEngine.getClientFuzzyWatchIndexesManager();
        // get match result from index
        Collection<String> matchedPatterns = indexesManager.getServiceMatchedPatterns(service);
        
        for (String eachPattern : matchedPatterns) {
            // for every matched pattern, get client id which watching this pattern
            Collection<String> clientIDs = indexesManager.getAllClientFuzzyWatchedPattern(eachPattern);
            if (clientIDs == null || clientIDs.isEmpty()) {
                // find there is nobody watch this pattern anymore (lazy remove)
                indexesManager.removeWatchPatternMatchIndex(service, eachPattern);
                continue;
            }
            watchNotifyClientIds.addAll(clientIDs);
        }
        return watchNotifyClientIds;
    }
    
    private class WatchNotifyPushCallback implements PushCallBack {
        
        private final String clientId;
        
        private final String serviceChangedType;
        
        /**
         * Record the push task execute start time.
         */
        private final long executeStartTime;
        
        private WatchNotifyPushCallback(String clientId, String serviceChangedType) {
            this.clientId = clientId;
            this.serviceChangedType = serviceChangedType;
            this.executeStartTime = System.currentTimeMillis();
        }
        
        @Override
        public long getTimeout() {
            return PushConfig.getInstance().getPushTaskTimeout();
        }
        
        @Override
        public void onSuccess() {
            long pushFinishTime = System.currentTimeMillis();
            long pushCostTimeForNetWork = pushFinishTime - executeStartTime;
            long pushCostTimeForAll = pushFinishTime - delayTask.getLastProcessTime();
            
            Loggers.PUSH.info("[WATCH-PUSH-SUCC] {}ms, all delay time {}ms for client {}, service {}, changed type {} ",
                    pushCostTimeForNetWork, pushCostTimeForAll, clientId, service.getGroupedServiceName(), serviceChangedType);
        }
        
        @Override
        public void onFail(Throwable e) {
            long pushCostTime = System.currentTimeMillis() - executeStartTime;
            Loggers.PUSH.error("[WATCH-PUSH-FAIL] {}ms, service {}, changed type {}, reason={}, client={}", pushCostTime,
                    service.getGroupedServiceName(), serviceChangedType, e.getMessage(), clientId);
            if (!(e instanceof NoRequiredRetryException)) {
                Loggers.PUSH.error("Reason detail: ", e);
                delayTaskEngine.addTask(service, new FuzzyWatchNotifyChangeDelayTask(service,
                        serviceChangedType, PushConfig.getInstance().getPushTaskDelay(), clientId));
            }
        }
    }
}
