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
import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.common.task.BatchTaskCounter;
import com.alibaba.nacos.naming.misc.Loggers;

import java.util.HashSet;
import java.util.Set;

/**
 * Nacos naming fuzzy watch initial push delay task.
 *
 * @author tanyongquan
 */
public class FuzzyWatchSyncNotifyTask extends AbstractDelayTask {
    
    private final String clientId;
    
    private final String pattern;
    
    private final Set<NamingFuzzyWatchSyncRequest.Context> syncServiceKeys;
    
    private final String syncType;
    
    private int totalBatch = 1;
    
    private int currentBatch = 1;
    
    private BatchTaskCounter batchTaskCounter;
    
    private long executeStartTime = System.currentTimeMillis();
    
    public FuzzyWatchSyncNotifyTask(String clientId, String pattern, String syncType,
            Set<NamingFuzzyWatchSyncRequest.Context> syncServiceKeys, long delay) {
        this.clientId = clientId;
        this.pattern = pattern;
        this.syncType = syncType;
        if (syncServiceKeys != null) {
            this.syncServiceKeys = syncServiceKeys;
        } else {
            this.syncServiceKeys = new HashSet<>();
        }
        setTaskInterval(delay);
        setLastProcessTime(System.currentTimeMillis());
    }
    
    public int getTotalBatch() {
        return totalBatch;
    }
    
    public void setTotalBatch(int totalBatch) {
        this.totalBatch = totalBatch;
    }
    
    public int getCurrentBatch() {
        return currentBatch;
    }
    
    public void setCurrentBatch(int currentBatch) {
        this.currentBatch = currentBatch;
    }
    
    @Override
    public void merge(AbstractDelayTask task) {
        if (!(task instanceof FuzzyWatchSyncNotifyTask)) {
            return;
        }
        FuzzyWatchSyncNotifyTask oldTask = (FuzzyWatchSyncNotifyTask) task;
        
        if (oldTask.getSyncServiceKeys() != null) {
            syncServiceKeys.addAll(oldTask.getSyncServiceKeys());
        }
        setLastProcessTime(Math.min(getLastProcessTime(), task.getLastProcessTime()));
        Loggers.PUSH.info("[FUZZY-WATCH-INIT-PUSH] Task merge for pattern {}", pattern);
    }
    
    public String getPattern() {
        return pattern;
    }
    
    public Set<NamingFuzzyWatchSyncRequest.Context> getSyncServiceKeys() {
        return syncServiceKeys;
    }
    
    public String getSyncType() {
        return syncType;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public BatchTaskCounter getBatchTaskCounter() {
        return batchTaskCounter;
    }
    
    public void setBatchTaskCounter(BatchTaskCounter batchTaskCounter) {
        this.batchTaskCounter = batchTaskCounter;
    }
    
    public long getExecuteStartTime() {
        return executeStartTime;
    }
}
