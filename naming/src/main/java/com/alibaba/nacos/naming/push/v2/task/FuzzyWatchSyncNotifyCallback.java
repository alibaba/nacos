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

import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.common.task.BatchTaskCounter;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.push.v2.NoRequiredRetryException;
import com.alibaba.nacos.naming.push.v2.PushConfig;

import static com.alibaba.nacos.api.common.Constants.FINISH_FUZZY_WATCH_INIT_NOTIFY;
import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_INIT_NOTIFY;
import static com.alibaba.nacos.naming.push.v2.task.FuzzyWatchPushDelayTaskEngine.getTaskKey;

class FuzzyWatchSyncNotifyCallback implements PushCallBack {
    
    private FuzzyWatchSyncNotifyTask fuzzyWatchSyncNotifyTask;
    
    private BatchTaskCounter batchTaskCounter;
    
    private FuzzyWatchPushDelayTaskEngine fuzzyWatchPushDelayTaskEngine;
    
    FuzzyWatchSyncNotifyCallback(FuzzyWatchSyncNotifyTask fuzzyWatchSyncNotifyTask, BatchTaskCounter batchTaskCounter,
            FuzzyWatchPushDelayTaskEngine fuzzyWatchPushDelayTaskEngine) {
        this.batchTaskCounter = batchTaskCounter;
        this.fuzzyWatchPushDelayTaskEngine = fuzzyWatchPushDelayTaskEngine;
        this.fuzzyWatchSyncNotifyTask = fuzzyWatchSyncNotifyTask;
    }
    
    @Override
    public long getTimeout() {
        return PushConfig.getInstance().getPushTaskTimeout();
    }
    
    @Override
    public void onSuccess() {
        
        long now = System.currentTimeMillis();
        if (isFinishInitTask()) {
            Loggers.PUSH.info(
                    "[fuzzy watch] init notify finish push success  ,clientId={}, pattern ={},total cost time={}ms",
                    fuzzyWatchSyncNotifyTask.getClientId(), fuzzyWatchSyncNotifyTask.getPattern(),
                    (now - fuzzyWatchSyncNotifyTask.getExecuteStartTime()));
        } else {
            Loggers.PUSH.info(
                    "[fuzzy watch] sync notify task success, pattern {}, syncType={},clientId={},current batch size {},currentBatch={},totalBatch={}",
                    fuzzyWatchSyncNotifyTask.getPattern(), fuzzyWatchSyncNotifyTask.getSyncType(),
                    fuzzyWatchSyncNotifyTask.getClientId(), fuzzyWatchSyncNotifyTask.getSyncServiceKeys().size(),
                    fuzzyWatchSyncNotifyTask.getCurrentBatch());
            // if total batch is success sync to client send
            if (isInitNotifyTask()) {
                Loggers.PUSH.info(
                        "[fuzzy watch] init notify push success  ,clientId={}, pattern ={} ,currentBatch={},totalBatch={}",
                        fuzzyWatchSyncNotifyTask.getClientId(), fuzzyWatchSyncNotifyTask.getPattern(),
                        fuzzyWatchSyncNotifyTask.getCurrentBatch(), fuzzyWatchSyncNotifyTask.getTotalBatch());
                batchTaskCounter.batchSuccess(fuzzyWatchSyncNotifyTask.getCurrentBatch());
                if (batchTaskCounter.batchCompleted()) {
                    Loggers.PUSH.info(
                            "[fuzzy watch] init notify all batch finish ,clientId={}, pattern ={},start notify init finish task",
                            fuzzyWatchSyncNotifyTask.getClientId(), fuzzyWatchSyncNotifyTask.getPattern());
                    FuzzyWatchSyncNotifyTask fuzzyWatchSyncNotifyTaskFinish = new FuzzyWatchSyncNotifyTask(
                            fuzzyWatchSyncNotifyTask.getClientId(), fuzzyWatchSyncNotifyTask.getPattern(),
                            FINISH_FUZZY_WATCH_INIT_NOTIFY, null, PushConfig.getInstance().getPushTaskDelay());
                    fuzzyWatchPushDelayTaskEngine.addTask(getTaskKey(fuzzyWatchSyncNotifyTaskFinish),
                            fuzzyWatchSyncNotifyTaskFinish);
                }
            }
        }
    }
    
    private boolean isFinishInitTask() {
        return FINISH_FUZZY_WATCH_INIT_NOTIFY.equals(fuzzyWatchSyncNotifyTask.getSyncType());
    }
    
    private boolean isInitNotifyTask() {
        return FUZZY_WATCH_INIT_NOTIFY.equals(fuzzyWatchSyncNotifyTask.getSyncType());
    }
    
    @Override
    public void onFail(Throwable e) {
        Loggers.PUSH.warn("[fuzzy watch] sync notify fail, pattern {} ,clientId ={},currentBatch={},totalBatch={}",
                fuzzyWatchSyncNotifyTask.getPattern(), fuzzyWatchSyncNotifyTask.getClientId(),
                fuzzyWatchSyncNotifyTask.getCurrentBatch(), fuzzyWatchSyncNotifyTask.getTotalBatch(), e);
        if (!(e instanceof NoRequiredRetryException)) {
            Loggers.PUSH.warn("[fuzzy watch] reschedule this task to engine");
            fuzzyWatchPushDelayTaskEngine.addTask(getTaskKey(fuzzyWatchSyncNotifyTask), fuzzyWatchSyncNotifyTask);
        }
    }
}
