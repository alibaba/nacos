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

import com.alibaba.nacos.api.naming.remote.request.NamingFuzzyWatchSyncRequest;
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.push.v2.NoRequiredRetryException;
import com.alibaba.nacos.naming.push.v2.PushConfig;
import com.alibaba.nacos.common.task.BatchTaskCounter;
import static com.alibaba.nacos.api.common.Constants.FINISH_FUZZY_WATCH_INIT_NOTIFY;
import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_INIT_NOTIFY;

/**
 * Nacos naming fuzzy watch initial push execute task.
 *
 * @author tanyongquan
 */
public class FuzzyWatchSyncNotifyExecuteTask extends AbstractExecuteTask {
    
    private final String clientId;
    
    private final String pattern;
    
    private final FuzzyWatchPushDelayTaskEngine fuzzyWatchPushDelayTaskEngine;
    
    private final FuzzyWatchSyncNotifyTask delayTask;
    
    /**
     * Fuzzy watch origin push matched service size, if there is no failure while executing push, {@code originSize == latch}.
     * just use to record log after finish all push.
     */
    private final int originSize;
    
    /**
     * TODO set batch size from config.
     */
    
    public FuzzyWatchSyncNotifyExecuteTask( String clientId, String pattern,
            FuzzyWatchPushDelayTaskEngine fuzzyWatchPushDelayTaskEngine, FuzzyWatchSyncNotifyTask delayTask) {
        this.clientId = clientId;
        this.pattern = pattern;
        this.fuzzyWatchPushDelayTaskEngine = fuzzyWatchPushDelayTaskEngine;
        this.delayTask = delayTask;
        this.originSize = delayTask.getSyncServiceKeys().size();
    }
    
    @Override
    public void run() {
        
        NamingFuzzyWatchSyncRequest namingFuzzyWatchSyncRequest = NamingFuzzyWatchSyncRequest.buildSyncNotifyRequest(
                pattern, delayTask.getSyncType(), delayTask.getSyncServiceKeys(), delayTask.getTotalBatch(), delayTask.getCurrentBatch());
        fuzzyWatchPushDelayTaskEngine.getPushExecutor().doFuzzyWatchNotifyPushWithCallBack(clientId ,namingFuzzyWatchSyncRequest,
                new FuzzyWatchSyncNotifyExecuteTask.FuzzyWatchSyncNotifyCallback(namingFuzzyWatchSyncRequest, delayTask.getBatchTaskCounter()));
    }

    
    private class FuzzyWatchSyncNotifyCallback implements PushCallBack {
        
        private  NamingFuzzyWatchSyncRequest namingFuzzyWatchSyncRequest;
        
        /**
         * Record the push task execute start time.
         */
        private final long executeStartTime;
        
        private BatchTaskCounter batchTaskCounter ;
        
        private FuzzyWatchSyncNotifyCallback(NamingFuzzyWatchSyncRequest namingFuzzyWatchSyncRequest,
                BatchTaskCounter batchTaskCounter) {
            this.namingFuzzyWatchSyncRequest=namingFuzzyWatchSyncRequest;
            this.executeStartTime = System.currentTimeMillis();
            this.batchTaskCounter=batchTaskCounter;
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
            
            if (isFinishInitTask()) {
                Loggers.PUSH.info("[FUZZY-WATCH-SYNC-COMPLETE] {}ms, all delay time {}ms for client {} watch init push finish,"
                                + " pattern {}, all push service size {}",
                        pushCostTimeForNetWork, pushCostTimeForAll, clientId, pattern, originSize);
            } else {
                Loggers.PUSH.info("[FUZZY-WATCH-PUSH-SUCC] {}ms, all delay time {}ms for client {}, pattern {}, syncType={},push size {},currentBatch={}",
                        pushCostTimeForNetWork, pushCostTimeForAll, clientId, pattern,namingFuzzyWatchSyncRequest.getSyncType(), namingFuzzyWatchSyncRequest.getContexts().size(),namingFuzzyWatchSyncRequest.getCurrentBatch());
                // if total batch is success sync to client send
                if (isInitNotifyTask()){
                    batchTaskCounter.batchSuccess(namingFuzzyWatchSyncRequest.getCurrentBatch());
                    if (batchTaskCounter.batchCompleted()) {
                        fuzzyWatchPushDelayTaskEngine.addTask(System.currentTimeMillis(), new FuzzyWatchSyncNotifyTask(clientId, pattern, FINISH_FUZZY_WATCH_INIT_NOTIFY,null,
                                PushConfig.getInstance().getPushTaskDelay()));
                    }
                }
               
            }
        }
        
        private boolean isFinishInitTask(){
            return FINISH_FUZZY_WATCH_INIT_NOTIFY.equals(namingFuzzyWatchSyncRequest.getSyncType());
        }
    
        private boolean isInitNotifyTask(){
            return FUZZY_WATCH_INIT_NOTIFY.equals(namingFuzzyWatchSyncRequest.getSyncType());
        }
        
        @Override
        public void onFail(Throwable e) {
            long pushCostTime = System.currentTimeMillis() - executeStartTime;
            Loggers.PUSH.error("[FUZZY-WATCH-SYNC-PUSH-FAIL] {}ms, pattern {} match {} service: {}, currentBatch={},reason={}, client={}", pushCostTime, pattern,
                    namingFuzzyWatchSyncRequest.getContexts().size() ,namingFuzzyWatchSyncRequest.getCurrentBatch(), e.getMessage(), clientId);
            if (!(e instanceof NoRequiredRetryException)) {
                Loggers.PUSH.error("Reason detail: ", e);
                //resend request only
                FuzzyWatchSyncNotifyTask fuzzyWatchSyncNotifyTask = new FuzzyWatchSyncNotifyTask(clientId, pattern,
                        delayTask.getSyncType(), namingFuzzyWatchSyncRequest.getContexts(),
                        PushConfig.getInstance().getPushTaskRetryDelay());
                fuzzyWatchSyncNotifyTask.setBatchTaskCounter(batchTaskCounter);
                
                fuzzyWatchPushDelayTaskEngine.addTask(System.currentTimeMillis(),fuzzyWatchSyncNotifyTask);
                
            }
        }
    }
    
   
}