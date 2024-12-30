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

import com.alibaba.nacos.api.naming.remote.request.FuzzyWatchNotifyInitRequest;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.push.v2.NoRequiredRetryException;
import com.alibaba.nacos.naming.push.v2.PushConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Nacos naming fuzzy watch initial push execute task.
 *
 * @author tanyongquan
 */
public class FuzzyWatchInitExecuteTask extends AbstractExecuteTask {
    
    private final String taskKey;
    
    private final String clientId;
    
    private final String pattern;
    
    private final FuzzyWatchPushDelayTaskEngine delayTaskEngine;
    
    private final FuzzyWatchInitDelayTask delayTask;
    
    /**
     * Fuzzy watch origin push matched service size, if there is no failure while executing push, {@code originSize == latch}.
     * just use to record log after finish all push.
     */
    private final int originSize;
    
    private final int latch;
    
    /**
     * TODO set batch size from config.
     */
    private final int batchSize = 10;
    
    private int sendCount;
    
    private boolean isFinishInitTask;
    
    private boolean haveFailPush;
    
    public FuzzyWatchInitExecuteTask(String taskKey, String clientId, String pattern, int originSize,
            FuzzyWatchPushDelayTaskEngine delayTaskEngine, FuzzyWatchInitDelayTask delayTask, boolean isFinishInitTask) {
        this.taskKey = taskKey;
        this.clientId = clientId;
        this.pattern = pattern;
        this.delayTaskEngine = delayTaskEngine;
        this.delayTask = delayTask;
        this.originSize = originSize;
        this.latch = delayTask.getMatchedService().size();
        this.sendCount = 0;
        this.isFinishInitTask = isFinishInitTask;
        this.haveFailPush = false;
    }
    
    @Override
    public void run() {
        ClientManager clientManager = delayTaskEngine.getClientManager();
        Collection<Collection<String>> dividedServices = divideServiceByBatch(delayTask.getMatchedService());
        Client client = clientManager.getClient(clientId);
        if (null == client) {
            return;
        }
        if (!client.isWatchedPattern(pattern)) {
            return;
        }
        if (isFinishInitTask || delayTask.getMatchedService().isEmpty()) {
            // do not match any exist service, just finish init
            delayTaskEngine.getPushExecutor().doFuzzyWatchNotifyPushWithCallBack(clientId,
                    FuzzyWatchNotifyInitRequest.buildInitFinishRequest(pattern),
                    new FuzzyWatchInitPushCallback(clientId, null, originSize, true, haveFailPush));
        } else {
            for (Collection<String> batchData : dividedServices) {
                delayTaskEngine.getPushExecutor().doFuzzyWatchNotifyPushWithCallBack(clientId, FuzzyWatchNotifyInitRequest.buildInitRequest(
                                pattern, batchData),
                        new FuzzyWatchInitPushCallback(clientId, batchData, originSize, false, haveFailPush));
            }
        }
        
    }
    
    private Collection<Collection<String>> divideServiceByBatch(Collection<String> matchedService) {
        Collection<Collection<String>> result = new ArrayList<>();
        if (matchedService.isEmpty()) {
            return result;
        }
        Set<String> currentBatch = new HashSet<>();
        for (String groupedServiceName : matchedService) {
            currentBatch.add(groupedServiceName);
            if (currentBatch.size() >= this.batchSize) {
                result.add(currentBatch);
                currentBatch = new HashSet<>();
            }
        }
        if (!currentBatch.isEmpty()) {
            result.add(currentBatch);
        }
        return result;
    }
    
    private class FuzzyWatchInitPushCallback implements PushCallBack {
        
        private final String clientId;
        
        private final Collection<String> groupedServiceName;
        
        private final int originSize;
        
        /**
         * Record the push task execute start time.
         */
        private final long executeStartTime;
        
        private boolean isFinishInitTask;
        
        private boolean haveFailPush;
        
        private FuzzyWatchInitPushCallback(String clientId, Collection<String> groupedServiceName, int originSize,
                boolean isFinishInitTask, boolean haveFailPush) {
            this.clientId = clientId;
            this.groupedServiceName = groupedServiceName;
            this.originSize = originSize;
            this.executeStartTime = System.currentTimeMillis();
            this.isFinishInitTask = isFinishInitTask;
            this.haveFailPush = haveFailPush;
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
            
            if (isFinishInitTask) {
                Loggers.PUSH.info("[FUZZY-WATCH-INIT-COMPLETE] {}ms, all delay time {}ms for client {} watch init push finish,"
                                + " pattern {}, all push service size {}",
                        pushCostTimeForNetWork, pushCostTimeForAll, clientId, pattern, originSize);
            } else {
                Loggers.PUSH.info("[FUZZY-WATCH-PUSH-SUCC] {}ms, all delay time {}ms for client {}, pattern {}, push size {} : {}",
                        pushCostTimeForNetWork, pushCostTimeForAll, clientId, pattern, groupedServiceName.size(),
                        groupedServiceName);
                sendCount += groupedServiceName.size();
                // this task is an init push task(not finish notify), and with no failure in this task when executing push batched services
                if (!haveFailPush && sendCount >= latch) {
                    delayTaskEngine.addTask(taskKey, new FuzzyWatchInitDelayTask(taskKey, clientId, pattern, null,
                            originSize, PushConfig.getInstance().getPushTaskDelay(), true));
                }
            }
        }
        
        @Override
        public void onFail(Throwable e) {
            long pushCostTime = System.currentTimeMillis() - executeStartTime;
            Loggers.PUSH.error("[FUZZY-WATCH-PUSH-FAIL] {}ms, pattern {} match {} service: {}, reason={}, client={}", pushCostTime, pattern,
                    groupedServiceName.size(), groupedServiceName, e.getMessage(), clientId);
            setHaveFailPush(true);
            if (!(e instanceof NoRequiredRetryException)) {
                Loggers.PUSH.error("Reason detail: ", e);
                if (isFinishInitTask) {
                    delayTaskEngine.addTask(taskKey, new FuzzyWatchInitDelayTask(taskKey, clientId, pattern, null,
                            originSize, PushConfig.getInstance().getPushTaskRetryDelay(), true));
                } else {
                    delayTaskEngine.addTask(taskKey, new FuzzyWatchInitDelayTask(taskKey, clientId, pattern, groupedServiceName,
                            originSize, PushConfig.getInstance().getPushTaskRetryDelay(), false));
                }
                
            }
        }
    }
    
    private void setHaveFailPush(boolean haveFailPush) {
        this.haveFailPush = haveFailPush;
    }
}